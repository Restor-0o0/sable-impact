package org.restor.create_aeronautics_impact;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What this mod costs the server tick, on demand.
 *
 * <p>A hitch is easy to feel and hard to attribute: a modpack this size has a dozen things that could be
 * responsible, and guessing between them is how tuning turns into superstition. Turning this on prints the
 * mod's own share of the tick, so the question of whether it is the one stalling is answered rather than
 * argued about.
 */
public final class ImpactStats {

    private static final Logger LOG = LoggerFactory.getLogger("create_aeronautics_impact");
    private static final int WINDOW_TICKS = 100;

    /** What counts as a tick worth naming. Two full ticks of budget, so ordinary jitter stays out of it. */
    private static final long SLOW_TICK_NANOS = 100L * 1_000_000L;

    private static long windowStart = Long.MIN_VALUE;
    private static long sweepNanos;
    private static long breakNanos;
    private static long worstTickNanos;
    private static long scanned;
    private static long broken;
    private static int busyTicks;
    private static double crushMass;
    private static double crushFootprint;
    private static double crushPressure;
    private static int crushUnder;
    private static int crushSide;
    private static int crushContacts;
    private static long lastFrameNanos = Long.MIN_VALUE;
    private static long mineAtLastFrame;
    private static long worstServerNanos;
    private static long worstServerMineNanos;
    private static int slowTicks;
    private static int chunksAtWindowStart;
    private static int hullsWorked;
    private static int hullsQuiet;
    private static long worstMineNanos;
    private static int worstDetail;
    /**
     * Timed apart, and two of them off the server thread.
     *
     * <p>The first four are this mod's own passes and are the whole of what the sweep timer sees. The last
     * two are not: they are this mod's code running inside Sable's time, called per contact from the solver
     * step and per block from a collider remesh. Neither ever appeared in the sweep figure, so a log saying
     * this mod cost a hundredth of a millisecond in the worst tick was answering a narrower question than the
     * one being asked - and remeshing in particular happens on whichever thread Sable meshes on, which is why
     * these are adders rather than plain fields.
     */
    private static final LongAdder[] PHASE_NANOS = adders(Phase.values().length);
    private static final LongAdder[] PHASE_CALLS = adders(Phase.values().length);

    /** A filled array, because {@code new LongAdder[n]} is an array of nulls and every slot is written to. */
    private static LongAdder[] adders(final int count) {
        final LongAdder[] made = new LongAdder[count];
        Arrays.setAll(made, index -> new LongAdder());
        return made;
    }
    private static long crushScanned;
    private static long carveBroken;
    private static long boundsDeferred;
    private static long boundsRebuilt;
    private static long glassScanned;
    private static long glassBroken;
    private static long anchorBuilds;
    private static long anchorColumns;
    private static int anchorPeak;

    /**
     * The four jobs a sweep does, timed apart.
     *
     * <p>One number for the lot of them says the mod is or is not the problem and nothing else, and by the
     * time that number is worth looking at the next question is always which part of it. These are not
     * alternatives to each other - a hull ploughing terrain runs all four in the same tick - so the split
     * has to come from the clock rather than from reasoning about which one must have been the expensive one.
     */
    public enum Phase {
        CARVE,
        CRUSH,
        SOFT,
        OVERLAP,
        /** Deciding what a contact does to the blocks either side of it, inside the solver's own step. */
        CONTACT,
        /** Deciding whether a block keeps its own collider voxel, inside a remesh. */
        VOXEL,
        /** Handing Sable back the plot bounds a whole break pass put off, once instead of thousands. */
        BOUNDS
    }

    private ImpactStats() {
    }

    /** @return a start time, or 0 when nothing is being measured and the clock should not be read at all. */
    public static long mark() {
        return enabled() ? System.nanoTime() : 0L;
    }

    /** Charges the time since {@code start} to a phase, and counts the call. A zero start is not measured. */
    public static void since(final Phase phase, final long start) {
        if (start != 0L) {
            PHASE_NANOS[phase.ordinal()].add(System.nanoTime() - start);
            PHASE_CALLS[phase.ordinal()].increment();
        }
    }

    /**
     * The voxel path, sampled rather than timed outright.
     *
     * <p>A remesh classifies every block in a section, so this runs thousands of times where the contact
     * path runs once, and two clock reads apiece would be a measurable share of the thing being measured -
     * a diagnostic that creates the stall it was added to find is worse than no diagnostic. Every call is
     * counted, one in sixty-four is timed, and the timing is scaled back up.
     */
    private static final int VOXEL_SAMPLE = 64;
    private static int voxelSeen;

    /** @return a start time on one call in {@link #VOXEL_SAMPLE}, or 0 on the rest. Always counts the call. */
    public static long markVoxel() {
        if (!enabled()) {
            return 0L;
        }
        PHASE_CALLS[Phase.VOXEL.ordinal()].increment();
        return (++voxelSeen & (VOXEL_SAMPLE - 1)) == 0 ? System.nanoTime() : 0L;
    }

    /** Charges a sampled voxel check, scaled back up to stand for the calls that were not timed. */
    public static void sinceVoxel(final long start) {
        if (start != 0L) {
            PHASE_NANOS[Phase.VOXEL.ordinal()].add((System.nanoTime() - start) * VOXEL_SAMPLE);
        }
    }

    /** The coarsest the sweeper had to get during the window, so a slow window says whether it gave ground. */
    public static void noteDetail(final int level) {
        if (level > worstDetail) {
            worstDetail = level;
        }
    }

    /**
     * Bounding-box rebuilds a break pass put off, and how many it actually ran at the end of it.
     *
     * <p>The two figures beside each other are the whole argument for batching them, and the reason it was
     * worth going looking: each deferred rebuild is a full scan of every non-empty section of a plot chunk,
     * so the difference between the two numbers is not a saving of a few microseconds but of tens of
     * thousands of block reads apiece. A tick that defers two thousand and rebuilds three is the mod
     * refusing to spend a second.
     */
    public static void addBoundsDeferred() {
        if (enabled()) {
            boundsDeferred++;
        }
    }

    /** The rebuilds a flush actually ran, which is the number the deferred count has to be read against. */
    public static void addBounds(final int rebuilt) {
        if (enabled()) {
            boundsRebuilt += rebuilt;
        }
    }

    /**
     * What the anchor is holding, this tick.
     *
     * <p>Both numbers are summed over the window and reported per tick, which for something refreshed every
     * tick reads as an average of how many builds and how much ground were held at once. The peak is kept
     * beside them because the average hides the moment worth knowing about: a wreck breaking into a dozen
     * pieces holds thirty times what the same wreck held whole, and only for a second or two.
     */
    public static void addAnchored(final int builds, final int columns) {
        if (enabled()) {
            anchorBuilds += builds;
            anchorColumns += columns;
            anchorPeak = Math.max(anchorPeak, columns);
        }
    }

    /**
     * What the fragile pass looked at and what it took out.
     *
     * <p>Kept apart because they answer different complaints. A high break count with a low scan is the
     * pass working; a high scan with no breaks is a fill wandering through a hull that has no windows in it,
     * which is the failure mode {@code glassScanBudget} exists to bound.
     */
    public static void addGlass(final int scanned, final int broken) {
        if (enabled()) {
            glassScanned += scanned;
            glassBroken += broken;
        }
    }

    /** Blocks the carve pass actually broke, as against the ones crush merely looked at. */
    public static void addCarved(final int blocks) {
        if (enabled()) {
            carveBroken += blocks;
        }
    }

    /**
     * Blocks the crush pass read.
     *
     * <p>The one figure that says whether crushing is being asked to look at more ground than it should be:
     * crush reads a whole footprint every pass and breaks almost none of it, so its cost tracks this and not
     * the break count beside it.
     */
    public static void addCrushScan(final int blocks) {
        if (enabled()) {
            crushScanned += blocks;
        }
    }

    /**
     * Whether anything here is being recorded.
     *
     * <p>Checked before every clock read rather than only at the log, so with {@code logPerformance} off the
     * instrumentation costs a boolean and no {@code nanoTime} calls at all.
     */
    public static boolean enabled() {
        return ImpactConfig.SPEC.isLoaded() && ImpactConfig.LOG_PERFORMANCE.get();
    }

    /** One hull, one tick: either it had terrain around it or it was left alone for having none. */
    public static void addHull(final boolean quiet) {
        if (!enabled()) {
            return;
        }
        if (quiet) {
            hullsQuiet++;
        } else {
            hullsWorked++;
        }
    }

    /** One sweep, timed. A sweep that read no blocks does not count as a tick the mod was active on. */
    public static void addSweep(final long nanos, final int scannedBlocks) {
        sweepNanos += nanos;
        scanned += scannedBlocks;
        if (nanos > worstTickNanos) {
            worstTickNanos = nanos;
        }
        if (scannedBlocks > 0) {
            busyTicks++;
        }
    }

    /** The break-applying pass, timed. Separate from the sweep because it runs after the physics step. */
    public static void addBreaks(final long nanos, final int brokenBlocks) {
        breakNanos += nanos;
        broken += brokenBlocks;
    }

    /**
     * Records what one crush pass concluded, keeping the heaviest build seen in the window.
     *
     * <p>Everything about crushing is a comparison between two numbers nobody can see - what a build weighs
     * spread over what is holding it, against what those blocks can take - and every question about it so far
     * has come down to guessing which of the two is wrong. The heaviest hull is the one being asked about.
     */
    public static void addCrush(final double mass,
                                final double footprint,
                                final double pressure,
                                final int under,
                                final int side,
                                final int contacts) {
        if (mass < crushMass) {
            return;
        }
        crushMass = mass;
        crushFootprint = footprint;
        crushPressure = pressure;
        crushUnder = under;
        crushSide = side;
        crushContacts = contacts;
    }

    /**
     * Times the whole tick alongside this mod's share of it.
     *
     * <p>Called once per server tick, so the gap between two calls is the tick. Which makes the comparison
     * the point: a stall that shows as five milliseconds here and a second on the clock is not this mod's,
     * and no amount of tuning here will move it. The loaded chunk count rides along because the usual answer
     * to a stall that arrives whenever something crosses into new ground is terrain being generated for it.
     */
    public static void frame(final ServerLevel level) {
        final long gameTime = level.getGameTime();
        final long now = System.nanoTime();
        final long mine = sweepNanos + breakNanos;
        if (lastFrameNanos != Long.MIN_VALUE) {
            final long elapsed = now - lastFrameNanos;
            // Tracked for every tick and not only the worst one. The mod's share of the slowest tick answers
            // whether the mod caused that tick and nothing else - a window can hold a two hundred millisecond
            // stall of this mod's own making and still report a hundredth of a millisecond, because some
            // other tick in it was slower still and that is the one being described.
            final long mineThisTick = mine - mineAtLastFrame;
            if (mineThisTick > worstMineNanos) {
                worstMineNanos = mineThisTick;
            }
            if (elapsed >= SLOW_TICK_NANOS) {
                slowTicks++;
                if (elapsed > worstServerNanos) {
                    worstServerNanos = elapsed;
                    worstServerMineNanos = mineThisTick;
                }
            }
        }
        lastFrameNanos = now;
        mineAtLastFrame = mine;

        if (windowStart == Long.MIN_VALUE) {
            windowStart = gameTime;
            chunksAtWindowStart = level.getChunkSource().getLoadedChunksCount();
            return;
        }
        if (gameTime - windowStart < WINDOW_TICKS) {
            return;
        }

        final double ticks = gameTime - windowStart;
        LOG.info("impact: sweep {}ms/tick (worst {}ms), breaks {}ms/tick, {} blocks scanned/tick, "
                        + "{} broken/tick, active on {}/{} ticks, {} hull-ticks worked and {} skipped as clear",
                millis(sweepNanos / ticks), millis(worstTickNanos), millis(breakNanos / ticks),
                Math.round(scanned / ticks), Math.round(broken / ticks), busyTicks, (long) ticks,
                hullsWorked, hullsQuiet);

        LOG.info("impact: carve {}ms/tick, crush {}ms/tick, soft {}ms/tick, overlaps {}ms/tick, "
                        + "{} blocks read by crush/tick, {} carved/tick, coarsened to level {}",
                millis(PHASE_NANOS[Phase.CARVE.ordinal()].sum() / ticks),
                millis(PHASE_NANOS[Phase.CRUSH.ordinal()].sum() / ticks),
                millis(PHASE_NANOS[Phase.SOFT.ordinal()].sum() / ticks),
                millis(PHASE_NANOS[Phase.OVERLAP.ordinal()].sum() / ticks),
                Math.round(crushScanned / ticks), Math.round(carveBroken / ticks), worstDetail);

        LOG.info("impact: inside sable - {} contacts/tick costing {}ms/tick, "
                        + "{} voxel checks/tick costing {}ms/tick",
                Math.round(PHASE_CALLS[Phase.CONTACT.ordinal()].sum() / ticks),
                millis(PHASE_NANOS[Phase.CONTACT.ordinal()].sum() / ticks),
                Math.round(PHASE_CALLS[Phase.VOXEL.ordinal()].sum() / ticks),
                millis(PHASE_NANOS[Phase.VOXEL.ordinal()].sum() / ticks));

        if (boundsDeferred > 0 || glassScanned > 0) {
            LOG.info("impact: plot bounds {} rebuilds deferred into {} run, costing {}ms/tick; "
                            + "fragile pass read {} blocks/tick and took {}/tick",
                    boundsDeferred, boundsRebuilt,
                    millis(PHASE_NANOS[Phase.BOUNDS.ordinal()].sum() / ticks),
                    Math.round(glassScanned / ticks), Math.round(glassBroken / ticks));
        }

        if (anchorColumns > 0) {
            LOG.info("impact: anchoring held {} builds and {} chunk columns per tick, peaking at {} columns",
                    Math.round(anchorBuilds / ticks), Math.round(anchorColumns / ticks), anchorPeak);
        }

        if (crushMass > 0.0) {
            LOG.info("impact: crush mass {}, footprint {} blocks, pressure {}, {} contacts, {} under and {} side crushed",
                    Math.round(crushMass), String.format("%.1f", crushFootprint),
                    String.format("%.1f", crushPressure), crushContacts, crushUnder, crushSide);
        }

        if (slowTicks > 0) {
            final int chunks = level.getChunkSource().getLoadedChunksCount();
            LOG.info("impact: slowest tick {}ms of which this mod {}ms; this mod's own worst tick {}ms; "
                            + "{} ticks over {}ms in {}; loaded chunks {} ({})",
                    millis(worstServerNanos), millis(worstServerMineNanos), millis(worstMineNanos), slowTicks,
                    millis(SLOW_TICK_NANOS), (long) ticks, chunks,
                    chunks - chunksAtWindowStart >= 0
                            ? "+" + (chunks - chunksAtWindowStart)
                            : String.valueOf(chunks - chunksAtWindowStart));
        }

        windowStart = gameTime;
        chunksAtWindowStart = level.getChunkSource().getLoadedChunksCount();
        worstServerNanos = 0L;
        worstServerMineNanos = 0L;
        slowTicks = 0;
        mineAtLastFrame = 0L;
        crushMass = 0.0;
        crushFootprint = 0.0;
        crushPressure = 0.0;
        crushUnder = 0;
        crushSide = 0;
        crushContacts = 0;
        sweepNanos = 0L;
        breakNanos = 0L;
        worstTickNanos = 0L;
        scanned = 0L;
        broken = 0L;
        busyTicks = 0;
        hullsWorked = 0;
        hullsQuiet = 0;
        crushScanned = 0L;
        carveBroken = 0L;
        boundsDeferred = 0L;
        boundsRebuilt = 0L;
        glassScanned = 0L;
        glassBroken = 0L;
        anchorBuilds = 0L;
        anchorColumns = 0L;
        anchorPeak = 0;
        worstMineNanos = 0L;
        worstDetail = 0;
        for (int phase = 0; phase < PHASE_NANOS.length; phase++) {
            PHASE_NANOS[phase].reset();
            PHASE_CALLS[phase].reset();
        }
    }

    /** Nanoseconds as milliseconds to two places, since every figure in the log is quoted that way. */
    private static String millis(final double nanos) {
        return String.format("%.2f", nanos / 1.0e6);
    }
}
