package org.restor.create_aeronautics_impact;

/**
 * Which way a build parts, given how far its material runs in each of the three directions.
 *
 * <p>A crack is a plane, and a plane is named by the axis it is cut across. Which axis that is was the one
 * thing nothing ever looked at: the cuts were dealt out in turn, X then Y then Z, on the reasoning that two
 * cracks on different axes are two pieces rather than the same cut made twice. That is true of a solid lump
 * and false of everything anybody builds, because the axis a thing is thin along is the one axis it cannot
 * be cut across. Cutting a mast across its own length parts it in two; cutting it along its length splits it
 * into two half-masts still joined at both ends, which is not a break, and a ship that flies into it is
 * stopped by a mast that is still there. Cutting a one-block plate across its width parts it; cutting it
 * across its thickness means the plane <em>is</em> the plate, and what the crack does then is chew a hole out
 * of the middle of it and stop when it has spent its energy.
 *
 * <p>So the axis is measured rather than dealt. The material is followed out from the break in all three
 * directions, and the axis it runs furthest along is the one the cut is made across - which is the same
 * answer in every case above and, on a hull, is the cut amidships that leaves the stern behind.
 *
 * <p>A second crack takes the next axis down, so a plate cut across its length is then cut across its width
 * and comes apart in four. What is never taken is an axis the build barely extends along at all: that is the
 * bite out of the plate, and one is enough of those.
 *
 * <p>Kept free of Minecraft on purpose, like {@link ImpactResolver} and {@link LoadPath}, so the choosing can
 * be tested without one.
 */
public final class CrackPlane {

    /** Axis indices, in {@code Direction.Axis} order, which is what the caller turns the answer back into. */
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;

    private static final int AXES = 3;

    private CrackPlane() {
    }

    /**
     * The axis to cut across, as an index into the three.
     *
     * @param minRun how far the material has to run along an axis before a cut across that axis is a cut
     *               rather than a hole. Below it the plane lies in the face of the thing instead of through
     *               it. Ignored entirely if no axis clears it, since a lump has to be cut somehow.
     * @param nth    which crack this is for the build, counting from zero. Each takes the next axis down, so
     *               successive cuts cross rather than repeat, and it wraps within what is eligible rather
     *               than falling back onto the thin axis the first two were avoiding.
     */
    public static int normal(final int runX, final int runY, final int runZ,
                             final int minRun, final int nth) {
        final int[] runs = {runX, runY, runZ};
        final int[] ranked = {X, Y, Z};

        for (int index = 1; index < AXES; index++) {
            for (int at = index; at > 0 && runs[ranked[at]] > runs[ranked[at - 1]]; at--) {
                final int swap = ranked[at];
                ranked[at] = ranked[at - 1];
                ranked[at - 1] = swap;
            }
        }

        int eligible = 0;
        while (eligible < AXES && runs[ranked[eligible]] >= minRun) {
            eligible++;
        }
        if (eligible == 0) {
            eligible = 1;
        }
        return ranked[Math.floorMod(nth, eligible)];
    }
}
