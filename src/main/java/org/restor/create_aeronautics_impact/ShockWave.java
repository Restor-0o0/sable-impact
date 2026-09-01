package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.doubles.DoubleArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
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
 * <p>That energy buys two different pictures, and a crash is both of them. A <b>wave</b> spreads out of the
 * impact in every direction and eats what it can reach, which is what happens to the part that hit. A
 * <b>crack</b> is one block wide and runs clean through the build, which is what happens to the rest of it -
 * things this size come apart along a line rather than dissolving, and the half that separates is still a
 * half rather than a cloud. {@code fractureShare} is how the crash is divided between the two.
 *
 * <p>The walk covers whatever is touching, in the grid it started in - a contraption's plot is surrounded by
 * empty plotgrid, so a wave that begins in a hull cannot leave it, and one that begins in terrain cannot
 * climb into a hull. Anything too big for one tick is put down and picked up on the next, which is both what
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

    /** How many cracks each body has been given this tick. A build comes apart in pieces, not per contact. */
    private static final Int2IntMap FRACTURES = new Int2IntOpenHashMap();

    /** How many waves each hull has set going this tick, which is what keeps a landing from being all wave. */
    private static final Int2IntMap WAVES = new Int2IntOpenHashMap();

    /** What {@link #strike} found: something it destroyed, nothing at all, or something it could not pass. */
    private static final int BLOCKED = -1;
    private static final int EMPTY = 0;
    private static final int BROKEN = 1;

    private final ServerLevel level;
    private final Vector3d worldImpact;
    private final double impactVelocity;
    private final boolean contraption;
    private final double toughness;

    /** The axis a crack is cut across, or null for an ordinary wave spreading every way at once. */
    private final @Nullable Direction.Axis crack;

    /** Where on that axis the cut started, which is what {@code fractureWander} is measured against. */
    private final int plane;

    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();

    // What a block reached through this one costs, over and above its material. Carried per entry rather
    // than derived from a depth, because breadth-first order makes it a multiply per block either way.
    private final DoubleArrayFIFOQueue distances = new DoubleArrayFIFOQueue();

    // How much nothing a crack has crossed to get here, so a seam can leave the deck, cross the hold and
    // come out through the floor without being free to wander off into open plotgrid forever.
    private final IntArrayFIFOQueue gaps = new IntArrayFIFOQueue();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private double budget;
    private int broken;

    /** The tick a parked wave is given up on, so a wreck stops shedding blocks once it has stopped moving. */
    private long expires;

    private ShockWave(final ServerLevel level,
                      final BlockPos origin,
                      final Vector3d worldImpact,
                      final double impactVelocity,
                      final boolean contraption,
                      final @Nullable Direction.Axis crack,
                      final ImpactConfig.Tuning tuning) {
        this.level = level;
        this.worldImpact = new Vector3d(worldImpact);
        this.impactVelocity = impactVelocity;
        this.contraption = contraption;
        this.toughness = contraption ? tuning.contraptionBlockToughness() : 1.0;
        this.crack = crack;
        this.plane = crack == null ? 0 : origin.get(crack);
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

        // Two gates, and they answer different questions. The overshoot asks whether the hit was hard for
        // what it hit - a battleship resting its weight on a wall is carrying just as much energy as one
        // flying into it, and only one of those is a crash. The speed asks whether anything happened at all,
        // and is the one that lets a build be landed and moved: a ship weighs tonnes, so at walking pace it
        // is already carrying enough energy for every other number here to level it.
        if (overshoot <= tuning.shockMinOvershoot() || Math.abs(impactVelocity) < tuning.shockMinSpeed()) {
            return 0;
        }
        if (brokenThisTick >= tuning.shockMaxPerTick()) {
            return 0;
        }

        int broken = cleave(level, origin, worldImpact, impactVelocity,
                contraption, bodyId, kinetic, tuning, deadline);

        // Asked before the energy is drawn, so what a refused contact would have spent stays in the
        // reservoir for the cracks. A hull landing flat reports contacts in the hundreds and the contact
        // side of a shock is priced per contact rather than out of the reservoir, so without a cap on how
        // many of them may open a wave the crash is the impact point eating outwards in rings until there
        // is no build left, which is the one thing it must not look like. Hulls only - a hull ploughing a
        // hillside is meant to plough it.
        if (contraption
                && WAVES.merge(reservoirKey(bodyId, true), 1, Integer::sum) > tuning.shockMaxWaves()) {
            return broken;
        }

        final double scale = contraption ? tuning.hullShockScale() : tuning.terrainShockScale();
        final double energy = Math.max(
                ImpactResolver.shockEnergy(overshoot, tuning.shockMinOvershoot(), scale),
                draw(bodyId, contraption, kinetic, tuning.shockContactShare()));
        if (energy <= 0.0) {
            return broken;
        }
        return broken + start(level, origin, worldImpact, impactVelocity,
                contraption, null, energy, tuning, deadline);
    }

    /**
     * Opens one of this build's cracks, if it has any left this tick.
     *
     * <p>A few per body rather than one per contact, and that is the whole point of it. A landing reports
     * contacts in the hundreds and they are all the same crash; a hull cut in three hundred places is not in
     * pieces, it is gravel. Two cuts is a build in three parts, which is what a thing this size does when it
     * comes down.
     *
     * <p>But one cut per contact rather than all of them at the first, so that the cuts start where the build
     * was actually touched - two different corners of the face that landed, rather than two seams crossing at
     * the one contact that happened to be reported first. Each takes a different axis, so two cuts are two
     * pieces rather than the same cut made twice.
     *
     * <p>Contraptions only. A crack through terrain is a canyon, and nobody asked for a canyon.
     */
    private static int cleave(final ServerLevel level,
                              final BlockPos origin,
                              final Vector3d worldImpact,
                              final double impactVelocity,
                              final boolean contraption,
                              final int bodyId,
                              final double kinetic,
                              final ImpactConfig.Tuning tuning,
                              final long deadline) {
        final int count = tuning.fractureCount();
        if (!contraption || !tuning.fracture() || count <= 0 || tuning.fractureShare() <= 0.0) {
            return 0;
        }
        final int key = reservoirKey(bodyId, true);
        final int already = FRACTURES.get(key);
        if (already >= count) {
            return 0;
        }

        final double total = draw(bodyId, contraption, kinetic, tuning.fractureShare() / count);
        if (total <= 0.0) {
            return 0;
        }
        FRACTURES.put(key, already + 1);

        final Direction.Axis[] axes = Direction.Axis.values();
        return start(level, origin, worldImpact, impactVelocity, true,
                axes[Math.floorMod(bodyId + already, axes.length)], total, tuning, deadline);
    }

    /** Sets one walk going from the block that broke, and parks it if it cannot finish inside the tick. */
    private static int start(final ServerLevel level,
                             final BlockPos origin,
                             final Vector3d worldImpact,
                             final double impactVelocity,
                             final boolean contraption,
                             final @Nullable Direction.Axis crack,
                             final double energy,
                             final ImpactConfig.Tuning tuning,
                             final long deadline) {
        if (energy <= 0.0 || brokenThisTick >= tuning.shockMaxPerTick()) {
            return 0;
        }
        final ShockWave wave = new ShockWave(level, origin, worldImpact, impactVelocity,
                contraption, crack, tuning);
        wave.budget = energy;
        wave.seen.add(wave.key(origin));
        wave.frontier.enqueue(origin.asLong());
        wave.distances.enqueue(1.0);
        wave.gaps.enqueue(0);
        return wave.run(tuning, deadline) ? wave.broken : park(level, wave, tuning);
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
            if (!tuning.shockBlocks()
                    || level.getGameTime() > wave.expires
                    || wave.run(tuning, deadline)) {
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
        final int key = reservoirKey(bodyId, contraption);
        final double left = RESERVOIR.getOrDefault(key, kinetic);
        final double taken = left * Math.clamp(share, 0.0, 1.0);
        RESERVOIR.put(key, left - taken);
        return taken;
    }

    private static int reservoirKey(final int bodyId, final boolean contraption) {
        return bodyId * 2 + (contraption ? 1 : 0);
    }

    /**
     * Keeps an unfinished wave for the next tick.
     *
     * @return what it broke this tick, so the caller counts it either way.
     */
    private static int park(final ServerLevel level, final ShockWave wave,
                            final ImpactConfig.Tuning tuning) {
        wave.expires = level.getGameTime() + tuning.shockMaxTicks();
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
     * The walk proper: breadth first out of its frontier, every block it destroys taken out of one budget
     * they all share, until it can no longer afford the next.
     *
     * <p>Breadth first is what makes the shared budget behave: it spends on what is nearest the impact first
     * and only reaches further once that is gone, which is both the right picture and the reason the cost of
     * distance below is enough to bound it.
     *
     * <p>A crack is the same walk with two directions taken away from it and a little slop allowed on the
     * third, which is all a fracture surface is.
     *
     * @return whether the walk is finished, as opposed to merely out of time or out of tick.
     */
    private boolean run(final ImpactConfig.Tuning tuning, final long deadline) {
        final int perImpact = tuning.shockMaxPerImpact();
        final double falloff = Math.clamp(
                this.crack == null ? tuning.shockFalloff() : tuning.fractureFalloff(), 0.1, 1.0);
        final int wander = this.crack == null ? 0 : tuning.fractureWander();
        final int maxGap = this.crack == null ? 0 : tuning.fractureGap();

        while (!this.frontier.isEmpty()) {
            if (this.broken >= perImpact || this.budget <= 0.0) {
                return true;
            }
            if (brokenThisTick >= tuning.shockMaxPerTick() || System.nanoTime() > deadline) {
                return false;
            }

            final long from = this.frontier.dequeueLong();
            final double distance = this.distances.dequeueDouble();
            final int gap = this.gaps.dequeueInt();
            final double further = distance / falloff;

            for (final Direction direction : Direction.values()) {
                if (this.crack != null && direction.getAxis() == this.crack) {
                    continue;
                }
                this.cursor.set(BlockPos.getX(from), BlockPos.getY(from), BlockPos.getZ(from));
                this.cursor.move(direction);
                drift(wander);

                final long key = key(this.cursor);
                if (!this.seen.add(key)) {
                    continue;
                }

                final int met = strike(this.cursor, distance, tuning);
                if (met == BLOCKED) {
                    continue;
                }
                if (met == EMPTY) {
                    // Nothing here to cut, but a crack is a surface and a build is mostly rooms. Crossing
                    // costs nothing and buys no distance either - the seam is no further into the material
                    // on the other side of a hold than it was on this one.
                    if (gap >= maxGap) {
                        continue;
                    }
                    this.frontier.enqueue(this.cursor.asLong());
                    this.distances.enqueue(distance);
                    this.gaps.enqueue(gap + 1);
                    continue;
                }
                this.broken++;
                brokenThisTick++;
                this.frontier.enqueue(this.cursor.asLong());
                this.distances.enqueue(further);
                this.gaps.enqueue(0);

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
     * Lets a crack step a block off its own plane, so what it leaves is a seam rather than a saw cut.
     *
     * <p>One block per column of the plane either way, whatever the drift, which is what keeps the cut a cut:
     * every column the crack reaches is missing exactly one block, so nothing can be traced through it from
     * one side to the other however the seam wanders.
     */
    private void drift(final int wander) {
        if (this.crack == null || wander <= 0) {
            return;
        }
        final int step = this.level.random.nextInt(3) - 1;
        if (step == 0) {
            return;
        }
        final Direction off = Direction.get(step > 0
                ? Direction.AxisDirection.POSITIVE
                : Direction.AxisDirection.NEGATIVE, this.crack);
        this.cursor.move(off);
        if (Math.abs(this.cursor.get(this.crack) - this.plane) > wander) {
            this.cursor.move(off.getOpposite());
        }
    }

    /**
     * What tells two visits apart. For a wave that is the block; for a crack it is the column of the plane,
     * so a seam that has already wandered past a column does not come back for it at another depth.
     */
    private long key(final BlockPos pos) {
        if (this.crack == null) {
            return pos.asLong();
        }
        return switch (this.crack) {
            case X -> BlockPos.asLong(this.plane, pos.getY(), pos.getZ());
            case Y -> BlockPos.asLong(pos.getX(), this.plane, pos.getZ());
            case Z -> BlockPos.asLong(pos.getX(), pos.getY(), this.plane);
        };
    }

    /**
     * One block met by the walk: destroyed and the remainder returned, or left standing and the branch ended.
     *
     * <p>Anything the hull would have gone through rather than hit - undergrowth, water - counts as nothing
     * being there at all. A wave is carried by what is solid and stops; a crack is a surface and crosses.
     * Letting a wave cross a lake would have one contact on a shoreline take the far bank as well.
     *
     * <p>A crack pays a fraction of what a wave does for the same block, because the two are buying different
     * shapes. A wave's price buys a sphere; at that price a crack buys a disc a few blocks across, which is a
     * scratch. A cut that stops halfway has split nothing, so it is priced to cross what it started on.
     *
     * @return {@link #BROKEN}, {@link #EMPTY} or {@link #BLOCKED}.
     */
    private int strike(final BlockPos pos, final double distance, final ImpactConfig.Tuning tuning) {
        final BlockState state = stateIfLoaded(pos);
        if (state == null) {
            return BLOCKED;
        }
        if (state.isAir()) {
            return EMPTY;
        }

        final BlockProfile profile = BlockProfile.of(this.level, pos, state);
        if (profile.indestructible()) {
            return BLOCKED;
        }
        if (profile.passable()) {
            return EMPTY;
        }

        final double price = ImpactResolver.shockCost(
                profile.resistance() * this.toughness, tuning.shockCost(), distance)
                * (this.crack == null ? 1.0 : tuning.fractureCost());
        if (price > this.budget) {
            return BLOCKED;
        }
        this.budget -= price;

        final BlockPos broken = pos.immutable();
        if (this.contraption) {
            if (!BlockScatter.shatterContraptionBlock(this.level, broken, state, this.worldImpact,
                    this.impactVelocity, profile.resistance())) {
                // The build has spent its allowance. Refunding the price and calling this solid is what
                // stops the wave here rather than letting it walk on through a hull it may not touch.
                this.budget += price;
                return BLOCKED;
            }
        } else {
            BlockScatter.shatter(this.level, broken, state, this.worldImpact,
                    this.impactVelocity, profile.resistance());
        }
        return BROKEN;
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
            FRACTURES.clear();
            WAVES.clear();
        }
    }
}
