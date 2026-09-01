package org.restor.create_aeronautics_impact;

/**
 * Where along its own axis a crack should actually be made, given what is standing in the way at each place
 * it could be made.
 *
 * <p>{@link CrackPlane} says which way to cut. This says where. Until 1.9.2 the answer was always "through the
 * contact", which is where a crack is least likely to be: things do not break where they are hit, they break
 * where they are weakest, and on anything built out of more than one material the two are hardly ever the
 * same block. An obsidian mast with a wooden gondola on the end of it, clipped on the gondola, does not crack
 * down the mast - the gondola shears off at its joint, because the ring of wood around that joint is the least
 * material carrying the most weight. Cutting at the contact instead put a seam through the middle of the
 * gondola and left it hanging off the mast in two halves, which is not what anybody has ever watched happen.
 *
 * <p>So each plane within reach of the contact is weighed by what is standing in it - the summed resistance of
 * the blocks in a window of that plane, which is a cross-section's strength as directly as this mod measures
 * anything - and the cut is made at the lightest one. A plane with nothing in it is not a candidate: an empty
 * cross-section is not a weak place to cut, it is a place that is already cut, and past the end of the thing.
 * Neither is one with something unbreakable in it, since a cut that cannot be finished is a notch.
 *
 * <p>Distance costs, and that is what keeps the answer honest. A crack is still something that happened where
 * the crash happened; without a price on travelling, the weakest plane anywhere in reach wins even when the
 * blow landed nowhere near it, and builds would come apart at their thinnest point no matter where they were
 * touched. The price is a share of what cutting at the contact itself would have cost, so it means the same
 * thing on a wooden glider as on an obsidian dreadnought - and, more to the point, it is not thrown off by the
 * one enormous cross-section somewhere in reach that is the very thing being avoided. An average would be: put
 * an obsidian spar within reach of a wooden hull and the average section is the spar, and every step away from
 * the contact would be priced as though it cost obsidian to take.
 *
 * <p>Kept free of Minecraft on purpose, like {@link CrackPlane} and {@link ImpactResolver}, so the choosing can
 * be tested without one.
 */
public final class CrackNeck {

    private CrackNeck() {
    }

    /**
     * The offset from the contact, along the axis being cut across, of the weakest place to cut.
     *
     * @param costs what each candidate plane is holding, from the furthest one behind the contact to the
     *              furthest one in front of it, with the contact's own plane in the middle. Must be of odd
     *              length. {@link Double#POSITIVE_INFINITY} for a plane that is not a candidate at all -
     *              empty, or holding something that cannot be broken.
     * @param bias  what a candidate pays per block of distance from the contact, as a share of what cutting
     *              at the contact itself would have cost - or, if the contact's own plane cannot be cut at
     *              all, of what the average candidate is holding. {@code 0} takes the weakest plane in reach
     *              however far away it is; large values never leave the contact.
     * @return the offset, or {@code 0} if nothing in reach can be cut at all.
     */
    public static int weakest(final double[] costs, final double bias) {
        final int reach = (costs.length - 1) / 2;

        double reference = costs[reach];
        if (reference == Double.POSITIVE_INFINITY) {
            double sum = 0.0;
            int candidates = 0;
            for (final double cost : costs) {
                if (cost < Double.POSITIVE_INFINITY) {
                    sum += cost;
                    candidates++;
                }
            }
            if (candidates == 0) {
                return 0;
            }
            reference = sum / candidates;
        }

        int best = 0;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int index = 0; index < costs.length; index++) {
            if (costs[index] == Double.POSITIVE_INFINITY) {
                continue;
            }
            final int offset = index - reach;
            final double score = costs[index] + bias * reference * Math.abs(offset);
            if (score < bestScore
                    || (score == bestScore && Math.abs(offset) < Math.abs(best))) {
                bestScore = score;
                best = offset;
            }
        }
        return best;
    }
}
