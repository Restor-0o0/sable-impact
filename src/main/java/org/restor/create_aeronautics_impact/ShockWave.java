package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.doubles.DoubleArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a hard enough impact does to the blocks the broken one was attached to.
 *
 * <p>Every other decision in this mod is made about a contact, and a contact is a face. A hull that comes
 * down on one corner reports that corner, breaks what is around it, and is stopped dead by the solver before
 * the other ten thousand blocks of it touch anything - so without this class the corner is the whole crash,
 * however big and however fast the thing was.
 *
 * <p>So a break hands what the impact had left over to the blocks around it, and they spend it on their own
 * resistance and pass on the remainder. How much there is to hand over is asked twice: once of the contact,
 * which is a poor witness to anything larger than itself, and once of the body's kinetic energy, which is
 * what the crash actually has to spend and knows the difference between a boulder and a battleship. The
 * larger answer wins, and the kinetic one is drawn from a reservoir refilled once per body per tick, so a
 * landing that reports six hundred contacts is still one crash.
 *
 * <p>The wave walks whatever is touching, in the grid it started in - a contraption's plot is surrounded by
 * empty plotgrid, so a wave that begins in a hull cannot leave it, and one that begins in terrain cannot
 * climb into a hull. A wave too big for one tick is put down and picked up on the next, which is both what
 * keeps the tick honest and what makes a large wreck come apart over a second rather than in a single frame.
 *
 * <p><b>Runs from the level tick, after the physics step</b>, for the same reason breaking does: it writes
 * blocks, and writing a block re-bakes colliders through the library the step is holding.
 */
public final class ShockWave {

    /** How many unfinished waves one level may be carrying. Past this the oldest are simply dropped. */
    private static final int MAX_RUNNING = 64;

    private static final Map<ServerLevel, List<ShockWave>> RUNNING = new WeakHashMap<>();

    private static long tick = Long.MIN_VALUE;
    private static int brokenThisTick;

    /** What each body has left to spend this tick, so its hundreds of contacts share one crash. */
    private static final Int2DoubleMap RESERVOIR = new Int2DoubleOpenHashMap();

    private final ServerLevel level;
    private final Vector3d worldImpact;
    private final double impactVelocity;
    private final boolean contraption;
    private final double toughness;

    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();

    // What a block reached through this one costs, over and above its material. Carried per entry rather
    // than derived from a depth, because breadth-first order makes it a multiply per block either way.
    private final DoubleArrayFIFOQueue distances = new DoubleArrayFIFOQueue();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private double budget;
    private int broken;

    private ShockWave(final ServerLevel level,
                      final Vector3d worldImpact,
                      final double impactVelocity,
                      final boolean contraption,
                      final ImpactConfig.Tuning tuning) {
        this.level = level;
        this.worldImpact = new Vector3d(worldImpact);
        this.impactVelocity = impactVelocity;
        this.contraption = contraption;
        this.toughness = contraption ? tuning.contraptionBlockToughness() : 1.0;
    }

    /**
     * Sends a shock out from a block that has just been destroyed.
     *
     * @param overshoot how far past its break speed that block was hit, which is the only measure of the hit
     *                  that means the same thing through ice and through obsidian
     * @param bodyId    the striking sub-level, which is what the kinetic reservoir is kept per
     * @param kinetic   what that body is carrying, already priced in shock energy
     * @param deadline  the break pass's own nanoTime deadline
     * @return how many blocks the wave broke, for the tick's own count
     */
    public static int spread(final ServerLevel level,
                             final BlockPos origin,
                             final Vector3d worldImpact,
                             final double impactVelocity,
                             final double overshoot,
                             final boolean contraption,
                             final int bodyId,
                             final double kinetic,
                             final ImpactConfig.Tuning tuning,
                             final long deadline) {
        if (!tuning.shockBlocks()) {
            return 0;
        }
        rollTick(level.getGameTime());

        // The gate is the contact's, even when the energy is the body's: a battleship resting its weight on
        // a wall is carrying just as much as one flying into it, and only one of those is a crash.
        if (overshoot <= tuning.shockMinOvershoot()) {
            return 0;
        }

        final double scale = contraption ? tuning.hullShockScale() : tuning.terrainShockScale();
        final double energy = Math.max(
                ImpactResolver.shockEnergy(overshoot, tuning.shockMinOvershoot(), scale),
                draw(bodyId, contraption, kinetic, tuning.shockContactShare()));
        if (energy <= 0.0 || brokenThisTick >= tuning.shockMaxPerTick()) {
            return 0;
        }

        final ShockWave wave = new ShockWave(level, worldImpact, impactVelocity, contraption, tuning);
        wave.budget = energy;
        wave.seen.add(origin.asLong());
        wave.frontier.enqueue(origin.asLong());
        wave.distances.enqueue(1.0);
        return wave.run(tuning, deadline) ? wave.broken : park(level, wave);
    }

    /**
     * Carries on the waves the last tick ran out of room for, oldest first.
     *
     * <p>Called before the tick's own breaks rather than after, so a wreck that is still coming apart is not
     * queued behind whatever it has newly scraped on the way down.
     */
    public static int resume(final ServerLevel level,
                             final ImpactConfig.Tuning tuning,
                             final long deadline) {
        final List<ShockWave> running = RUNNING.get(level);
        if (running == null || running.isEmpty()) {
            return 0;
        }
        rollTick(level.getGameTime());

        int broken = 0;
        final Iterator<ShockWave> waves = running.iterator();
        while (waves.hasNext()) {
            final ShockWave wave = waves.next();
            final int before = wave.broken;
            if (!tuning.shockBlocks() || wave.run(tuning, deadline)) {
                waves.remove();
            }
            broken += wave.broken - before;
            if (System.nanoTime() > deadline) {
                break;
            }
        }
        if (running.isEmpty()) {
            RUNNING.remove(level);
        }
        return broken;
    }

    /**
     * Takes a body's kinetic energy out of the reservoir, which is refilled the first time it is asked each
     * tick. A crash reports its contacts in the hundreds and they are all the same crash; without this every
     * one of them would be worth the whole body's energy over again.
     *
     * <p>The hull and the ground each get a draw of their own, because they are two different things the same
     * crash does. Sharing one would make it a race - the ship that levelled the hill it landed on would come
     * away without a scratch, purely because the ground's contact happened to be reported first.
     *
     * <p>No single contact may take all of it either. A landing reports contacts all along the face that
     * touched, and handing the whole crash to whichever of them was processed first buys one enormous sphere
     * around one arbitrary block - a build that reads as having been shot rather than dropped. Capping the
     * draw leaves the rest for the others, so the same total arrives as several waves spread over the face
     * the build actually landed on.
     */
    private static double draw(final int bodyId, final boolean contraption,
                               final double kinetic, final double share) {
        if (kinetic <= 0.0) {
            return 0.0;
        }
        final int key = bodyId * 2 + (contraption ? 1 : 0);
        final double left = RESERVOIR.getOrDefault(key, kinetic);
        final double taken = left * Math.clamp(share, 0.0, 1.0);
        RESERVOIR.put(key, left - taken);
        return taken;
    }

    /**
     * Keeps an unfinished wave for the next tick.
     *
     * @return what it broke this tick, so the caller counts it either way.
     */
    private static int park(final ServerLevel level, final ShockWave wave) {
        final List<ShockWave> running = RUNNING.computeIfAbsent(level, ignored -> new ArrayList<>());
        // A crash that outruns this is one where the oldest waves have long since covered the ground the
        // newest are still working through, so the newest are the ones worth keeping.
        while (running.size() >= MAX_RUNNING) {
            running.remove(0);
        }
        running.add(wave);
        return wave.broken;
    }

    /**
     * The wave proper: breadth first out of its frontier, every block it destroys taken out of one budget
     * they all share, until it can no longer afford the next.
     *
     * <p>Breadth first is what makes the shared budget behave: it spends on what is nearest the impact first
     * and only reaches further once that is gone, which is both the right picture and the reason the cost of
     * distance below is enough to bound it.
     *
     * @return whether the wave is finished, as opposed to merely out of time or out of tick.
     */
    private boolean run(final ImpactConfig.Tuning tuning, final long deadline) {
        final int perImpact = tuning.shockMaxPerImpact();
        final double falloff = Math.clamp(tuning.shockFalloff(), 0.1, 1.0);

        while (!this.frontier.isEmpty()) {
            if (this.broken >= perImpact || this.budget <= 0.0) {
                return true;
            }
            if (brokenThisTick >= tuning.shockMaxPerTick() || System.nanoTime() > deadline) {
                return false;
            }

            final long from = this.frontier.dequeueLong();
            final double distance = this.distances.dequeueDouble();
            final double further = distance / falloff;

            for (final Direction direction : Direction.values()) {
                this.cursor.set(BlockPos.getX(from), BlockPos.getY(from), BlockPos.getZ(from));
                this.cursor.move(direction);
                final long key = this.cursor.asLong();
                if (!this.seen.add(key)) {
                    continue;
                }

                if (!strike(this.cursor, distance, tuning)) {
                    continue;
                }
                this.broken++;
                brokenThisTick++;
                this.frontier.enqueue(key);
                this.distances.enqueue(further);

                if (this.broken >= perImpact || this.budget <= 0.0) {
                    return true;
                }
                if (brokenThisTick >= tuning.shockMaxPerTick()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * One block met by the wave: destroyed and the remainder returned, or left standing and the branch ended.
     *
     * <p>Anything the hull would have gone through rather than hit - undergrowth, water - ends the branch
     * without being touched. A shock is carried by what is solid; letting it cross a lake would have one
     * contact on a shoreline take the far bank as well.
     *
     * @return whether the block was destroyed, and so whether the wave carries on through it.
     */
    private boolean strike(final BlockPos pos, final double distance, final ImpactConfig.Tuning tuning) {
        final BlockState state = stateIfLoaded(pos);
        if (state == null || state.isAir()) {
            return false;
        }

        final BlockProfile profile = BlockProfile.of(this.level, pos, state);
        if (profile.indestructible() || profile.passable()) {
            return false;
        }

        final double price = ImpactResolver.shockCost(
                profile.resistance() * this.toughness, tuning.shockCost(), distance);
        if (price > this.budget) {
            return false;
        }
        this.budget -= price;

        final BlockPos broken = pos.immutable();
        if (this.contraption) {
            BlockScatter.shatterContraptionBlock(this.level, broken, state, this.worldImpact,
                    this.impactVelocity, profile.resistance());
        } else {
            BlockScatter.shatter(this.level, broken, state, this.worldImpact,
                    this.impactVelocity, profile.resistance());
        }
        return true;
    }

    /**
     * A wave spreads through a build that may be at the edge of what is loaded, and asking the level for a
     * block outside that would load the chunk from inside the break pass. A miss stays a miss.
     */
    private @Nullable BlockState stateIfLoaded(final BlockPos pos) {
        final LevelChunk chunk = this.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static void rollTick(final long now) {
        if (now != tick) {
            tick = now;
            brokenThisTick = 0;
            RESERVOIR.clear();
        }
    }
}
