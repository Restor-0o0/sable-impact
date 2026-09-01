package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The one real answer to a crash costing a whole second of server time.
 *
 * <p>The mod's own passes were never the cost. They were measured, repeatedly, at hundredths of a
 * millisecond in ticks that took over a second, which meant the second was being spent inside something the
 * mod called rather than inside the mod. It was: Sable keeps a bounding box per plot chunk, and removing a
 * block that sits on any face of that box makes it rebuild the box by scanning every non-empty section of
 * the chunk, block by block. Four thousand and ninety-six reads per section, and a build tall enough to fill
 * eight of them is thirty-two thousand reads - <em>per broken block</em>.
 *
 * <p>Which is exactly the shape of a hull losing its keel. Every block of the bottom course is on the box's
 * own minimum face, so every single one of them triggers a full rescan, and the rescan after the last one is
 * the only rescan whose answer survives. Five hundred blocks in a tick is sixteen million block reads to
 * arrive at a box that one scan would have given.
 *
 * <p>So the shrink is deferred. During the break pass a chunk that loses a block is written down instead of
 * rescanned, and at the end of the pass each chunk that lost anything is scanned once. The result is the
 * same box: the scan reads the chunk's final contents either way, and it does not care in what order or how
 * many times the blocks left it.
 *
 * <p>Growth is not deferred, because growth is cheap - a placed block widens the box by comparison, with no
 * scan - and because a box that is too small is a box that is wrong, while a box that is briefly too large
 * is merely conservative. That asymmetry is what makes this safe: for the length of the pass every plot's
 * bounds are a superset of the truth, which is the direction nothing downstream minds.
 */
public final class BoundsBatch {

    private static final Set<PlotBounds> CHUNKS = new LinkedHashSet<>();
    private static final Set<LevelPlot> PLOTS = new LinkedHashSet<>();

    private static boolean batching;

    private BoundsBatch() {
    }

    /**
     * Opens a batch for the caller's pass. Paired with {@link #close()} in a finally, always.
     *
     * <p>Never nests: the break pass is the only caller and it runs once per level tick on the server
     * thread. A second open while one is running would be a bug, and re-opening rather than counting keeps
     * it a visible one.
     */
    public static void open(final ImpactConfig.Tuning tuning) {
        batching = tuning.batchBounds();
    }

    /** Runs every rebuild the pass put off, then stops deferring. Safe to call when nothing was deferred. */
    public static void close() {
        batching = false;
        if (CHUNKS.isEmpty() && PLOTS.isEmpty()) {
            return;
        }

        final long start = ImpactStats.mark();
        for (final PlotBounds chunk : CHUNKS) {
            chunk.create_aeronautics_impact$rebuildBounds();
        }
        for (final LevelPlot plot : PLOTS) {
            plot.updateBoundingBox();
        }
        ImpactStats.addBounds(CHUNKS.size() + PLOTS.size());
        ImpactStats.since(ImpactStats.Phase.BOUNDS, start);

        CHUNKS.clear();
        PLOTS.clear();
    }

    /**
     * Offered every plot-chunk block change Sable handles.
     *
     * @return whether this batch has taken responsibility for the chunk's box, so the caller must not
     *         update it itself.
     */
    public static boolean deferChunk(final Object holder,
                                     final BlockState oldState,
                                     final BlockState newState) {
        // Only a removal, and only one that actually removes something. Everything else either widens the
        // box, which is cheap and correct at once, or leaves it alone.
        if (!batching || !(holder instanceof final PlotBounds bounds)
                || oldState.isAir() || !newState.isAir()) {
            return false;
        }
        CHUNKS.add(bounds);
        ImpactStats.addBoundsDeferred();
        return true;
    }

    /**
     * Offered every plot block change Sable handles.
     *
     * <p>Deferred alongside the chunk it belongs to and for the same reason, though this one is cheap on its
     * own: it walks the plot's loaded chunks and asks each for the box it is holding. What makes it wrong to
     * leave running during a batch is not its cost but its input - during the pass those boxes are the stale
     * ones, so every call it makes is arithmetic on numbers that are about to change.
     */
    public static boolean deferPlot(final LevelPlot plot) {
        if (!batching) {
            return false;
        }
        PLOTS.add(plot);
        ImpactStats.addBoundsDeferred();
        return true;
    }
}
