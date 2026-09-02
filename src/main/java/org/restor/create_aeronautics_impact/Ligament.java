package org.restor.create_aeronautics_impact;

/**
 * Whether a build is hanging on something too thin to hold it, and if so where.
 *
 * <p>Sable decides what is still one structure by asking whether its blocks touch, and touching is a yes or a
 * no. Nothing in it has any notion of a joint being too weak to carry what is on the other side of it, so a
 * container ship broken almost in half amidships goes on being one ship for as long as three deck blocks
 * bridge the gap, and what the player watches is a thousand tonnes of hull pivoting about a plank.
 *
 * <p>The measurement is a walk outwards from one end of the build, which sorts its blocks into layers by how
 * many steps away they are. That sorting has a property worth the whole of this class: every step joins
 * blocks in the same layer or in neighbouring ones, so <em>taking a whole layer out provably separates
 * everything before it from everything after it</em>. There is no need to search for a cut - each layer is
 * already one, and the only question is which of them is failing.
 *
 * <p>A layer fails when what it is made of cannot hold what hangs off it. What it is made of is the summed
 * resistance of its blocks, which is the same measure of strength every other pass in this mod prices a break
 * against; what hangs off it is the lighter of the two sides, since that is the load the joint actually
 * carries. Thin <em>by strength</em>, not by block count: four blocks of obsidian outweigh forty of wood.
 *
 * <p>Kept free of Minecraft on purpose, like {@link CrackNeck} and {@link CrackPlane}, so the arithmetic can
 * be tested without one.
 */
public final class Ligament {

    private Ligament() {
    }

    /**
     * Which layer gives way, or {@code -1} if none of them does.
     *
     * @param size     how many blocks are in each layer, from the end the walk started at.
     * @param strength what each layer is made of, as summed resistance.
     *                 {@link Double#POSITIVE_INFINITY} for a layer that is not a candidate at all - too wide
     *                 to be a joint, or holding something that cannot be broken.
     * @param minSide  the fewest blocks a side may have and still count as a side. Below it a cut is not a
     *                 build coming in half, it is a corner being trimmed off an intact one.
     * @param carry    how many blocks of build one point of resistance holds up. The dial the whole judgement
     *                 turns on: higher lets thinner joints stand.
     */
    public static int fails(final int[] size,
                            final double[] strength,
                            final int minSide,
                            final double carry) {
        if (size.length != strength.length || size.length < 3 || carry <= 0.0) {
            return -1;
        }

        int total = 0;
        for (final int count : size) {
            total += count;
        }

        int best = -1;
        double worst = 1.0;
        int bestSize = Integer.MAX_VALUE;

        int before = 0;
        for (int layer = 0; layer < size.length; layer++) {
            final int after = total - before - size[layer];
            final int here = before;
            before += size[layer];

            if (strength[layer] == Double.POSITIVE_INFINITY
                    || here < minSide || after < minSide) {
                continue;
            }
            final double capacity = strength[layer] * carry;
            final double overload = capacity <= 0.0
                    ? Double.POSITIVE_INFINITY
                    : Math.min(here, after) / capacity;
            if (overload > worst || (best >= 0 && overload == worst && size[layer] < bestSize)) {
                worst = overload;
                best = layer;
                bestSize = size[layer];
            }
        }
        return best;
    }
}
