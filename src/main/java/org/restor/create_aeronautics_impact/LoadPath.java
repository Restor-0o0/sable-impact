package org.restor.create_aeronautics_impact;

import java.util.Arrays;

/**
 * Where the weight in a piece of structure goes, and what is left holding nothing up.
 *
 * <p>Everything else in this mod prices a block against the thing that hit it. That is the whole of an
 * impact and none of a structure: a platform on two legs, hit hard enough to lose its middle, keeps standing
 * because nothing ever asked what the legs were carrying or what the ends were resting on. The blocks left
 * over hang in the air on nothing, which is ordinary for Minecraft and absurd under a wreck.
 *
 * <p>So this answers the other question. Given a box of cells - some solid, some of them held up by
 * something outside the box - it works out for every solid cell <b>which way its weight leaves</b>, and adds
 * the weight up along those routes. A cell's load is its own weight plus everything that routes through it,
 * which is why the legs end up carrying the platform and the platform does not end up carrying the legs.
 *
 * <p>The routing is a breadth-first search out from the anchors, and it is deliberately not a physical one.
 * Straight up costs nothing, because a column stacks on itself; sideways and downwards cost a step, because
 * those are ties rather than stacks and {@code span} is how many of them a load may take before the cell is
 * called hanging. Everything the search never reaches is hanging by definition - connected to nothing that
 * goes anywhere, which is the block left in mid-air.
 *
 * <p>What comes out is two statements per cell, and the caller decides what either is worth: how much it is
 * carrying, against how much it can carry; and whether it is being carried at all. Breaking a cell and
 * solving again is how a failure spreads, since the load it was carrying has to leave some other way and
 * usually cannot.
 *
 * <p>Kept free of Minecraft on purpose, like {@link ImpactResolver} and {@link SweepDetail}, so the
 * arithmetic can be tested without one. Buffers are grown and reused rather than reallocated, because this
 * runs on a box of some tens of thousands of cells and does so from the server tick.
 */
public final class LoadPath {

    /** A cell with no route out: either it is an anchor, or nothing reached it. */
    public static final int NOWHERE = -1;

    private static final boolean[] NO_FLAGS = new boolean[0];
    private static final double[] NO_NUMBERS = new double[0];
    private static final int[] NO_INDICES = new int[0];

    private int sx;
    private int sy;
    private int sz;
    private int count;

    private boolean[] solid = NO_FLAGS;
    private boolean[] anchored = NO_FLAGS;
    private double[] weight = NO_NUMBERS;
    private double[] capacity = NO_NUMBERS;
    private double[] resting = NO_NUMBERS;
    private double[] load = NO_NUMBERS;
    private int[] route = NO_INDICES;
    private int[] steps = NO_INDICES;
    private int[] order = NO_INDICES;
    private int[] wave = NO_INDICES;
    private int[] next = NO_INDICES;

    private int reached;

    /**
     * Clears the box and sizes it, growing the buffers only when the new box will not fit the old ones.
     *
     * <p>Only the first {@code count} entries are ever read, so a shrink costs nothing and a grow costs one
     * allocation that the next few thousand solves will not have to make again.
     */
    public void reset(final int sx, final int sy, final int sz) {
        this.sx = Math.max(0, sx);
        this.sy = Math.max(0, sy);
        this.sz = Math.max(0, sz);
        this.count = this.sx * this.sy * this.sz;
        this.reached = 0;

        if (this.solid.length < this.count) {
            this.solid = new boolean[this.count];
            this.anchored = new boolean[this.count];
            this.weight = new double[this.count];
            this.capacity = new double[this.count];
            this.resting = new double[this.count];
            this.load = new double[this.count];
            this.route = new int[this.count];
            this.steps = new int[this.count];
            this.order = new int[this.count];
            this.wave = new int[this.count];
            this.next = new int[this.count];
        }

        Arrays.fill(this.solid, 0, this.count, false);
        Arrays.fill(this.anchored, 0, this.count, false);
        Arrays.fill(this.resting, 0, this.count, 0.0);
    }

    /** The cell at these coordinates. Laid out so that one level of the box is contiguous. */
    public int index(final int x, final int y, final int z) {
        return x + this.sx * (z + this.sz * y);
    }

    public int size() {
        return this.count;
    }

    /**
     * Makes a cell solid, with what it weighs and what it can carry before it gives way.
     *
     * <p>An anchored cell is one held up by something the box does not contain - the ground below its floor,
     * bedrock, a chunk that is not loaded. Every route ends at one of those, and a box with none of them has
     * a structure standing on nothing, which is exactly what it will be told.
     */
    public void set(final int at, final double weight, final double capacity, final boolean anchored) {
        this.solid[at] = true;
        this.anchored[at] = anchored;
        this.weight[at] = Double.isNaN(weight) ? 0.0 : Math.max(0.0, weight);
        this.capacity[at] = Double.isNaN(capacity) ? 0.0 : Math.max(0.0, capacity);
    }

    /** Adds load that is already standing on a cell, which is how a build's own weight enters the box. */
    public void press(final int at, final double load) {
        if (load > 0.0 && !Double.isNaN(load)) {
            this.resting[at] += load;
        }
    }

    /** Takes a cell out, because it has just been destroyed and the next solve must route around it. */
    public void remove(final int at) {
        this.solid[at] = false;
        this.anchored[at] = false;
        this.resting[at] = 0.0;
    }

    public boolean solid(final int at) {
        return this.solid[at];
    }

    /** Whether the last solve found this cell a way to the ground. False is a block hanging in the air. */
    public boolean carried(final int at) {
        return this.steps[at] >= 0;
    }

    /** What the cell is carrying, itself included, after the last solve. */
    public double load(final int at) {
        return this.load[at];
    }

    public double capacity(final int at) {
        return this.capacity[at];
    }

    /** How far past what it can carry the cell has been loaded, or zero while it holds. */
    public double overload(final int at) {
        final double over = this.load[at] - this.capacity[at];
        return Double.isNaN(over) || over <= 0.0 ? 0.0 : over;
    }

    /**
     * Finds every cell's route to the ground and adds the weight up along it.
     *
     * <p>Two passes over the box and nothing else. The first is the search: anchors go into the wave, a cell
     * reached straight up is added to the same wave because that step is free, and a cell reached sideways
     * or downwards goes into the next one because that step is not. Since a wave is finished before the wave
     * after it starts, a cell is settled the first time it is reached and never revisited, and the order the
     * cells came out in has every cell after the one it routes through.
     *
     * <p>Which is what makes the second pass a single loop backwards: walk the order in reverse, and by the
     * time a cell is reached everything routing through it has already handed its total over.
     *
     * @param span how many sideways or downward steps a load may take on its way to an anchor. Past that the
     *             cell is left uncarried, which is the cantilever that is too long to be one.
     */
    public void solve(final int span) {
        Arrays.fill(this.steps, 0, this.count, -1);
        Arrays.fill(this.route, 0, this.count, NOWHERE);
        this.reached = 0;

        int inWave = 0;
        for (int at = 0; at < this.count; at++) {
            this.load[at] = this.resting[at];
            if (this.solid[at] && this.anchored[at]) {
                this.steps[at] = 0;
                this.wave[inWave++] = at;
            }
        }

        final int levelStride = this.sx * this.sz;
        for (int step = 0; inWave > 0; step++) {
            int inNext = 0;
            for (int cursor = 0; cursor < inWave; cursor++) {
                final int at = this.wave[cursor];
                this.order[this.reached++] = at;

                final int y = at / levelStride;
                final int rest = at - y * levelStride;
                final int z = rest / this.sx;
                final int x = rest - z * this.sx;

                if (y + 1 < this.sy) {
                    final int above = at + levelStride;
                    if (this.solid[above] && this.steps[above] < 0) {
                        this.steps[above] = step;
                        this.route[above] = at;
                        this.wave[inWave++] = above;
                    }
                }

                if (step >= span) {
                    continue;
                }
                if (y > 0) {
                    inNext = tie(at, at - levelStride, step, this.next, inNext);
                }
                if (x > 0) {
                    inNext = tie(at, at - 1, step, this.next, inNext);
                }
                if (x + 1 < this.sx) {
                    inNext = tie(at, at + 1, step, this.next, inNext);
                }
                if (z > 0) {
                    inNext = tie(at, at - this.sx, step, this.next, inNext);
                }
                if (z + 1 < this.sz) {
                    inNext = tie(at, at + this.sx, step, this.next, inNext);
                }
            }

            final int[] swap = this.wave;
            this.wave = this.next;
            this.next = swap;
            inWave = inNext;
        }

        for (int position = this.reached - 1; position >= 0; position--) {
            final int at = this.order[position];
            this.load[at] += this.weight[at];
            final int to = this.route[at];
            if (to != NOWHERE) {
                this.load[to] += this.load[at];
            }
        }
    }

    /** One paid step: a neighbour that hangs off this cell rather than standing on it. */
    private int tie(final int from, final int to, final int step, final int[] into, final int filled) {
        if (!this.solid[to] || this.steps[to] >= 0) {
            return filled;
        }
        this.steps[to] = step + 1;
        this.route[to] = from;
        into[filled] = to;
        return filled + 1;
    }
}
