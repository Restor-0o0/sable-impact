package org.restor.create_aeronautics_impact;

/**
 * How much of itself a sweep is allowed to be, as a ladder of rungs it climbs down under load.
 *
 * <p>A time limit on its own can only say when to stop, and a pass that keeps hitting one keeps stopping in
 * the same place: the near side of the hull is cleared every tick and the far side is never reached at all.
 * Terrain would rather be finished roughly than half-finished exactly, so running out of time is read here as
 * a request for less work rather than for more time.
 *
 * <p>The one thing giving ground must never do is let a hull through a block. Sample spacing and probe width
 * therefore move together: each step of a rewind asks about a cube twice the clip across, so a rung that
 * spreads the steps widens the cube to match and the steps still overlap. Coarser is blurrier - the worst it
 * can do is break something the hull only came near.
 */
public final class SweepDetail {

    /** Rungs, finest first. */
    public static final int LEVELS = 4;

    /** Rewind samples per block travelled. */
    private static final double[] DENSITY = {2.0, 1.45, 1.45, 1.25};

    /** Half the width of the cube each sample asks about. */
    private static final double[] CLIP = {0.40, 0.45, 0.45, 0.50};

    private static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 96;

    /** The shipped default finest rung, and what the ladder reads as until the config has been through it. */
    public static final int FINEST_DEFAULT = 0;

    /**
     * The shipped default travel per lookahead window past which a hull is swept coarsely whatever the
     * server is doing.
     *
     * <p>Precision costs the most exactly where it is worth the least. A hull creeping into a wall wants an
     * answer good to half a block, because half a block is most of what it will do this second; a hull
     * arriving at sixty metres a second covers six blocks in the same window and is going to leave a hole
     * either way. Cost, meanwhile, runs the other way: the faster it goes the longer the swept region and the
     * longer each rewind, so the tick that can least afford the detail is the one being charged the most
     * for it.
     *
     * <p>Two blocks of travel is twenty metres a second, comfortably above anything under its own power and
     * comfortably below anything that has been falling for a while.
     */
    public static final double COARSE_TRAVEL_DEFAULT = 2.0;

    private static volatile int finest = FINEST_DEFAULT;
    private static volatile double coarseTravel = COARSE_TRAVEL_DEFAULT;

    /**
     * Takes the two settings that shape the ladder from the config.
     *
     * <p>Pushed in rather than read out because this class is pure arithmetic and deliberately mentions
     * nothing from Minecraft or NeoForge, which is what lets it be unit tested without a game around it.
     */
    public static void configure(final int finestRung, final double coarseAt) {
        finest = Math.clamp(finestRung, 0, LEVELS - 1);
        coarseTravel = coarseAt;
    }

    private SweepDetail() {
    }

    /**
     * Half the width of the cube one sample asks about, at this rung.
     *
     * <p>Paired with {@link #steps}: spacing and width are two halves of one decision, because samples that
     * stop overlapping stop being a swept path and start being a row of holes with gaps between them.
     */
    public static double clip(final int level) {
        return CLIP[rung(level)];
    }

    /**
     * How many samples a rewind over this much travel is broken into, at this rung.
     *
     * <p>Clamped at both ends. The floor is what keeps a hull that has barely moved from being asked about
     * once and missing the block it is halfway into; the ceiling is what keeps something falling at terminal
     * velocity from turning one tick into a hundred probes.
     */
    public static int steps(final double travel, final int level) {
        return Math.clamp((long) Math.ceil(travel * DENSITY[rung(level)]), MIN_STEPS, MAX_STEPS);
    }

    /**
     * Which rung a hull travelling this far is swept at, given how much room the server has.
     *
     * <p>Only the two resolution rungs are reachable this way. Speed is a reason to sample the path more
     * thinly, not a reason to stop carving sideways or to stop clearing grass - those are answers to the
     * server being busy, and a fast hull on an idle server should still get all of them.
     */
    public static int resolution(final int level, final double travel) {
        return Math.max(rung(level), travel >= coarseTravel ? 1 : 0);
    }

    /**
     * Whether only the direction the hull is actually going gets carved.
     *
     * <p>The two lateral passes are for a hull moving diagonally into a corner and each costs about what the
     * first one does, so they are the obvious thing to drop before resolution goes any lower.
     */
    public static boolean leadingAxisOnly(final int level) {
        return level >= 2;
    }

    /** Whether grass clearing still runs, and weight is answered every tick rather than every other. */
    public static boolean cosmetic(final int level) {
        return level < 3;
    }

    /**
     * The rung a request for this level actually lands on.
     *
     * <p>Everything public here goes through it, so the floor from {@code sweepFinestDetail} is applied in
     * one place and cannot be walked around by asking for a level directly.
     */
    private static int rung(final int level) {
        return Math.clamp(level, finest, LEVELS - 1);
    }
}
