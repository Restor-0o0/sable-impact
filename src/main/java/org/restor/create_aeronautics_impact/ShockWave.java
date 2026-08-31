package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.doubles.DoubleArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * What a hard enough impact does to the blocks the broken one was attached to.
 *
 * <p>Every other decision in this mod is made about a contact, and a contact is a face. A hull that lands on
 * its belly reports contacts along its belly, so the belly is the only thing that can ever be found to break -
 * which is right for a scrape along a cliff and plainly wrong for a fall, where a stone hull dropped three
 * hundred blocks loses its floor and keeps its walls and looks like it was set down rather than dropped.
 *
 * <p>So a break that overshot badly enough hands what it had left over to the blocks around it, and they
 * spend it on their own resistance and pass on the remainder. The wave walks whatever is touching, in the
 * grid it started in - a contraption's plot is surrounded by empty plotgrid, so a wave that begins in a hull
 * cannot leave it, and one that begins in terrain cannot climb into a hull.
 *
 * <p><b>Runs from the level tick, after the physics step</b>, for the same reason breaking does: it writes
 * blocks, and writing a block re-bakes colliders through the library the step is holding.
 */
public final class ShockWave {

    private static long tick = Long.MIN_VALUE;
    private static int brokenThisTick;

    private final ServerLevel level;
    private final ImpactConfig.Tuning tuning;
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
    private final DoubleArrayFIFOQueue energies = new DoubleArrayFIFOQueue();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private ShockWave(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        this.level = level;
        this.tuning = tuning;
    }

    /**
     * Sends a shock out from a block that has just been destroyed.
     *
     * @param overshoot how far past its break speed that block was hit, which is the only measure of the hit
     *                  that means the same thing through ice and through obsidian
     * @param deadline  the break pass's own nanoTime deadline; a wave stops at it and is not resumed, because
     *                  a shock is a moment and half of one finished a tick later is not that moment
     * @return how many blocks the wave broke, for the tick's own count
     */
    public static int spread(final ServerLevel level,
                             final BlockPos origin,
                             final Vector3d worldImpact,
                             final double impactVelocity,
                             final double overshoot,
                             final boolean contraption,
                             final ImpactConfig.Tuning tuning,
                             final long deadline) {
        if (!tuning.shockBlocks()) {
            return 0;
        }

        final double scale = contraption ? tuning.hullShockScale() : tuning.terrainShockScale();
        final double energy = ImpactResolver.shockEnergy(overshoot, tuning.shockMinOvershoot(), scale);
        if (energy <= 0.0) {
            return 0;
        }

        rollTick(level.getGameTime());
        if (brokenThisTick >= tuning.shockMaxPerTick()) {
            return 0;
        }

        return new ShockWave(level, tuning)
                .run(origin, worldImpact, impactVelocity, energy, contraption, deadline);
    }

    /**
     * The wave proper: breadth first out of the origin, each block paying its own resistance out of what
     * reached it and passing the remainder on.
     *
     * <p>Breadth first rather than by remaining energy, which would be the accurate thing and is not worth a
     * priority queue per impact: the two only disagree about the order blocks at the same distance go in, and
     * they all go.
     */
    private int run(final BlockPos origin,
                    final Vector3d worldImpact,
                    final double impactVelocity,
                    final double energy,
                    final boolean contraption,
                    final long deadline) {
        this.seen.add(origin.asLong());
        this.frontier.enqueue(origin.asLong());
        this.energies.enqueue(energy);

        final int perImpact = this.tuning.shockMaxPerImpact();
        final double toughness = contraption ? this.tuning.contraptionBlockToughness() : 1.0;
        int broken = 0;

        while (!this.frontier.isEmpty()) {
            if (broken >= perImpact || brokenThisTick >= this.tuning.shockMaxPerTick()) {
                break;
            }
            // Once per block rather than once per batch: the wave is bounded by the caps above in the
            // ordinary case, and by this one when a tick has already spent itself on the contacts.
            if (System.nanoTime() > deadline) {
                break;
            }

            final long from = this.frontier.dequeueLong();
            final double carried = this.energies.dequeueDouble();

            for (final Direction direction : Direction.values()) {
                this.cursor.set(BlockPos.getX(from), BlockPos.getY(from), BlockPos.getZ(from));
                this.cursor.move(direction);
                final long key = this.cursor.asLong();
                if (!this.seen.add(key)) {
                    continue;
                }

                final double left = strike(this.cursor, carried, toughness, worldImpact,
                        impactVelocity, contraption);
                if (left <= 0.0) {
                    continue;
                }
                broken++;
                brokenThisTick++;
                this.frontier.enqueue(key);
                this.energies.enqueue(left);

                if (broken >= perImpact || brokenThisTick >= this.tuning.shockMaxPerTick()) {
                    return broken;
                }
            }
        }

        return broken;
    }

    /**
     * One block met by the wave: destroyed and the remainder returned, or left standing and the branch ended.
     *
     * <p>Anything the hull would have gone through rather than hit - undergrowth, water - ends the branch
     * without being touched. A shock is carried by what is solid; letting it cross a lake would have one
     * contact on a shoreline take the far bank as well.
     *
     * @return the energy this block's own neighbours are hit with, or 0 when the wave stops here.
     */
    private double strike(final BlockPos pos,
                          final double energy,
                          final double toughness,
                          final Vector3d worldImpact,
                          final double impactVelocity,
                          final boolean contraption) {
        final BlockState state = stateIfLoaded(pos);
        if (state == null || state.isAir()) {
            return 0.0;
        }

        final BlockProfile profile = BlockProfile.of(this.level, pos, state);
        if (profile.indestructible() || profile.passable()) {
            return 0.0;
        }

        final double resistance = profile.resistance() * toughness;
        final double left = ImpactResolver.shockStep(
                energy, resistance, this.tuning.shockCost(), this.tuning.shockFalloff());
        if (left <= 0.0) {
            return 0.0;
        }

        final BlockPos broken = pos.immutable();
        if (contraption) {
            BlockScatter.shatterContraptionBlock(this.level, broken, state, worldImpact,
                    impactVelocity, profile.resistance());
        } else {
            BlockScatter.shatter(this.level, broken, state, worldImpact,
                    impactVelocity, profile.resistance());
        }
        return left;
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
        }
    }
}
