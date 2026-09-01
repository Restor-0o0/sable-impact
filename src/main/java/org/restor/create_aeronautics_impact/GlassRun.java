package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * The windows, and only the windows.
 *
 * <p>"The ship hit the ground and the glass went out along its whole length" is the single most recognisable
 * thing about a crash of this size, and the shock wave cannot produce it. A wave that reached far enough to
 * take the glass at the far end would have taken every deck between here and there on the way, because a
 * wave has one budget and one price list and cannot afford to be selective. Reach and selectivity are the
 * same setting to it.
 *
 * <p>So the fragile blocks get a pass of their own: a flood fill through the structure that reaches several
 * times as far as the wave, costs nothing per block it passes, and breaks only what shatters. It is cheap
 * for the same reason it is right - it never has to decide anything. A pane is in reach or it is not.
 *
 * <p>It travels through material rather than through space, which is what keeps it both bounded and
 * sensible: a shock runs along the hull, not across the sky, so air ends a branch. On a build that is mostly
 * rooms this means the fill follows the decks and bulkheads, which is exactly the path a real one takes.
 */
public final class GlassRun {

    /** How many fills each body may set going per tick. A landing is one crash however many contacts. */
    private static final Int2IntMap RUNS = new Int2IntOpenHashMap();

    private static long tick = Long.MIN_VALUE;

    private GlassRun() {
    }

    /**
     * Runs one fill out of a block that has just broken.
     *
     * <p>Finished within the tick or not at all. Unlike a wave there is nothing to park: the fill is bounded
     * by its own scan budget rather than by an energy that might have been worth carrying over, and half a
     * fill next tick would be windows going out in two batches a tick apart for no reason anyone can see.
     *
     * @return how many fragile blocks it took out.
     */
    public static int spread(final ServerLevel level,
                             final BlockPos origin,
                             final Vector3d worldImpact,
                             final double impactVelocity,
                             final boolean contraption,
                             final int bodyId,
                             final ImpactConfig.Tuning tuning,
                             final long deadline) {
        if (!tuning.stress() || !tuning.glass() || tuning.glassReach() <= 0) {
            return 0;
        }

        final long now = level.getGameTime();
        if (now != tick) {
            tick = now;
            RUNS.clear();
        }
        if (RUNS.merge(bodyId, 1, Integer::sum) > tuning.glassMaxRuns()) {
            return 0;
        }

        final ChunkCache chunks = new ChunkCache();
        final LongOpenHashSet seen = new LongOpenHashSet();
        final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        seen.add(origin.asLong());
        frontier.enqueue(origin.asLong());

        final int reach = tuning.glassReach();
        int scanned = 0;
        int broken = 0;

        while (!frontier.isEmpty()) {
            if (scanned >= tuning.glassScanBudget() || broken >= tuning.glassMaxPerImpact()) {
                break;
            }
            // Checked per block rather than per level of the fill: this shares the break pass's deadline
            // with everything else, and a fill that has found a greenhouse can break a great many things
            // between two checks of a coarser clock.
            if ((scanned & 63) == 0 && System.nanoTime() > deadline) {
                break;
            }

            final long from = frontier.dequeueLong();
            cursor.set(BlockPos.getX(from), BlockPos.getY(from), BlockPos.getZ(from));

            for (final Direction direction : Direction.values()) {
                cursor.set(BlockPos.getX(from), BlockPos.getY(from), BlockPos.getZ(from));
                cursor.move(direction);
                if (chebyshev(origin, cursor) > reach || !seen.add(cursor.asLong())) {
                    continue;
                }

                scanned++;
                final BlockState state = chunks.stateIfLoaded(level, cursor);
                if (state == null || state.isAir()) {
                    continue;
                }

                final BlockProfile profile = BlockProfile.of(level, cursor, state);
                if (profile.indestructible() || profile.passable()) {
                    continue;
                }

                // Carried by whatever is solid, whether or not it is what the fill is looking for. A pane
                // three decks away is reached through the decks; it is only the breaking that is selective.
                frontier.enqueue(cursor.asLong());
                if (!profile.fragile()) {
                    continue;
                }

                final BlockPos pane = cursor.immutable();
                if (contraption) {
                    if (!BlockScatter.shatterContraptionBlock(level, pane, state, worldImpact,
                            impactVelocity, profile.resistance())) {
                        break;
                    }
                } else {
                    BlockScatter.shatter(level, pane, state, worldImpact, impactVelocity,
                            profile.resistance());
                }
                broken++;
                if (broken >= tuning.glassMaxPerImpact()) {
                    break;
                }
            }
        }

        ImpactStats.addGlass(scanned, broken);
        return broken;
    }

    private static int chebyshev(final BlockPos from, final BlockPos to) {
        return Math.max(Math.abs(to.getX() - from.getX()),
                Math.max(Math.abs(to.getY() - from.getY()), Math.abs(to.getZ() - from.getZ())));
    }
}
