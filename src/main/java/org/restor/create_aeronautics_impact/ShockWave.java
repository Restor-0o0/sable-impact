package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.doubles.DoubleArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
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

    // Reading the material out from a break to decide which way to cut it happens once per crack, on the
    // server tick, before any wave of its own exists to own a cache. Dropped every tick with the rest of the
    // per-tick state, since a chunk held across ticks is a chunk that may have been unloaded.
    private static final ChunkCache MEASURE = new ChunkCache();
    private static final BlockPos.MutableBlockPos MEASURING = new BlockPos.MutableBlockPos();

    private static long tick = Long.MIN_VALUE;
    private static int brokenThisTick;

    /** What each body has left to spend this tick, so its hundreds of contacts share one crash. */
    private static final Int2DoubleMap RESERVOIR = new Int2DoubleOpenHashMap();

    /** How many cracks each body has been given this tick. A build comes apart in pieces, not per contact. */
    private static final Int2IntMap FRACTURES = new Int2IntOpenHashMap();

    /** How many waves each hull has set going this tick, which is what keeps a landing from being all wave. */
    private static final Int2IntMap WAVES = new Int2IntOpenHashMap();

    /** When each body's reservoir was last drawn from, so a crash can be one crash across several ticks. */
    private static final Int2LongMap TOUCHED = new Int2LongOpenHashMap();

    /** What {@link #strike} found: something it destroyed, nothing at all, or something it could not pass. */
    private static final int BLOCKED = -1;
    private static final int EMPTY = 0;
    private static final int BROKEN = 1;

    /**
     * Something the shock was not strong enough to break, and went through instead.
     *
     * <p>The whole of stress mode is in the existence of this fourth answer. A budgeted wave has only three:
     * it can afford a block, or the block is nothing, or the branch ends - and "I cannot afford this" and
     * "this is the edge of the world" are the same outcome to it, which is why a wave used to stop dead at
     * the first bulkhead it could not pay for. A shock that is measured rather than spent has somewhere else
     * to be. It goes through, weaker, and finds the windows on the far side.
     */
    private static final int SURVIVED = 2;

    private final ServerLevel level;
    private final Vector3d worldImpact;
    private final double impactVelocity;
    private final boolean contraption;
    private final double toughness;

    /** The axis a crack is cut across, or null for an ordinary wave spreading every way at once. */
    private final @Nullable Direction.Axis crack;

    /** Where on that axis the cut started, which is what {@code fractureWander} is measured against. */
    private final int plane;

    /** Whether this wave is measured against what it meets rather than paid out of a purse. Cracks never
        are: a seam is a designed cut through a build and its length is meant to be bought, not survived. */
    private final boolean stress;

    private final ChunkCache chunks = new ChunkCache();

    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();

    // What is still arriving at each entry of the frontier. Under stress this is the wave: it is not a
    // total to be divided among the blocks ahead but a strength each of them is measured against on its
    // own, so two branches out of one block both leave carrying all of it.
    private final DoubleArrayFIFOQueue intensities = new DoubleArrayFIFOQueue();

    // What a block reached through this one costs, over and above its material. Carried per entry rather
    // than derived from a depth, because breadth-first order makes it a multiply per block either way.
    private final DoubleArrayFIFOQueue distances = new DoubleArrayFIFOQueue();

    // How much nothing a crack has crossed to get here, so a seam can leave the deck, cross the hold and
    // come out through the floor without being free to wander off into open plotgrid forever.
    private final IntArrayFIFOQueue gaps = new IntArrayFIFOQueue();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private double budget;
    private int broken;

    /** Blocks this walk may take before its purse is allowed to stop it. Cracks only; see fractureFloor. */
    private int free;

    /** How much of the shock left the last block {@link #strike} looked at, whether or not it broke it. */
    private double passed;

    /** Blocks looked at rather than broken, which under stress is the only thing bounding the walk. */
    private int scanned;

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
        this.stress = crack == null && tuning.stress();
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
        rollTick(level.getGameTime(), tuning);

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

        // Before the wave cap below, and drawing nothing. The fragile pass is not a share of the crash, it
        // is a consequence of it, and a build whose waves are all spent still loses its windows.
        broken += GlassRun.spread(level, origin, worldImpact, impactVelocity,
                contraption, bodyId, tuning, deadline);

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
        final double contact = ImpactResolver.shockEnergy(overshoot, tuning.shockMinOvershoot(), scale);
        final double energy = Math.max(contact, draw(bodyId, contraption, kinetic,
                tuning.shockContactShare()));
        if (energy <= 0.0) {
            return broken;
        }

        // Priced off the whole crash rather than off this contact's share of it, and deliberately so. The
        // share exists to keep one contact from buying one enormous sphere, which is a statement about how
        // much work may be done. An intensity is not work: every contact of the same landing is the same
        // crash arriving, and dividing its strength by how many faces happened to touch would make a hull
        // that lands flat softer than one that lands on a corner.
        final double intensity = Math.max(contact, kinetic) * tuning.intensityScale();

        return broken + start(level, origin, worldImpact, impactVelocity,
                contraption, null, energy, intensity, tuning, deadline);
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
     * the one contact that happened to be reported first. Each takes the next axis down out of {@link
     * CrackPlane}, so two cuts cross rather than repeat, and neither of them is ever made across the axis the
     * build is thin along.
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
        // Counted against the build being cut rather than against the one that arrived. A crash has two
        // sides and both of them come apart, so keying the ration to the striker meant a ship landing on a
        // plate spent both of the plate's cuts on itself and the plate was left whole with a hole in it.
        final int key = reservoirKey(cutBodyId(level, origin, bodyId), true);
        final int already = FRACTURES.get(key);
        if (already >= count) {
            return 0;
        }

        final double total = draw(bodyId, contraption, kinetic, tuning.fractureShare() / count);
        if (total <= 0.0) {
            return 0;
        }
        FRACTURES.put(key, already + 1);

        return start(level, origin, worldImpact, impactVelocity, true,
                aim(level, origin, bodyId, already, tuning), total, 0.0, tuning, deadline);
    }

    /**
     * Which axis this cut is made across.
     *
     * <p>Measured out of the build rather than dealt in turn, because the axis a thing is thin along is the
     * one axis it cannot be parted across - see {@link CrackPlane}, which does the choosing. All this does is
     * follow the material out from the break in each of the three directions and hand over how far it got.
     *
     * <p>The same gap a crack is allowed to cross is allowed here, so a hull measures as the length of its
     * hull rather than as the thickness of the one plate the break happened to be in.
     */
    private static Direction.Axis aim(final ServerLevel level,
                                      final BlockPos origin,
                                      final int bodyId,
                                      final int already,
                                      final ImpactConfig.Tuning tuning) {
        final Direction.Axis[] axes = Direction.Axis.values();
        if (!tuning.fractureAim()) {
            return axes[Math.floorMod(bodyId + already, axes.length)];
        }

        final int scan = tuning.fractureScan();
        final int gap = tuning.fractureGap();
        return axes[CrackPlane.normal(
                reach(level, origin, Direction.Axis.X, scan, gap),
                reach(level, origin, Direction.Axis.Y, scan, gap),
                reach(level, origin, Direction.Axis.Z, scan, gap),
                tuning.fractureMinRun(), already)];
    }

    /** How many blocks of the build lie on the line through {@code origin} along {@code axis}, both ways. */
    private static int reach(final ServerLevel level, final BlockPos origin,
                             final Direction.Axis axis, final int scan, final int gap) {
        return 1 + along(level, origin, axis, 1, scan, gap)
                + along(level, origin, axis, -1, scan, gap);
    }

    /** One direction of that line, ending at the edge of the build, of the scan, or of what is loaded. */
    private static int along(final ServerLevel level, final BlockPos origin, final Direction.Axis axis,
                             final int step, final int scan, final int gap) {
        int solid = 0;
        int empty = 0;
        for (int offset = 1; offset <= scan; offset++) {
            final int moved = step * offset;
            MEASURING.set(
                    origin.getX() + (axis == Direction.Axis.X ? moved : 0),
                    origin.getY() + (axis == Direction.Axis.Y ? moved : 0),
                    origin.getZ() + (axis == Direction.Axis.Z ? moved : 0));
            final BlockState state = MEASURE.stateIfLoaded(level, MEASURING);
            if (state == null) {
                break;
            }
            if (state.isAir()) {
                if (++empty > gap) {
                    break;
                }
                continue;
            }
            empty = 0;
            solid++;
        }
        return solid;
    }

    /** The build the cut is being made in, which is not always the one that did the hitting. */
    private static int cutBodyId(final ServerLevel level, final BlockPos origin, final int bodyId) {
        final ServerSubLevel owner = BuildDamage.owner(level, origin);
        return owner == null ? bodyId : owner.getRuntimeId();
    }

    /** Sets one walk going from the block that broke, and parks it if it cannot finish inside the tick. */
    private static int start(final ServerLevel level,
                             final BlockPos origin,
                             final Vector3d worldImpact,
                             final double impactVelocity,
                             final boolean contraption,
                             final @Nullable Direction.Axis crack,
                             final double energy,
                             final double intensity,
                             final ImpactConfig.Tuning tuning,
                             final long deadline) {
        if (energy <= 0.0 || brokenThisTick >= tuning.shockMaxPerTick()) {
            return 0;
        }
        final ShockWave wave = new ShockWave(level, origin, worldImpact, impactVelocity,
                contraption, crack, tuning);
        wave.budget = energy;
        wave.free = crack == null ? 0 : Math.max(0, tuning.fractureFloor());
        wave.seen.add(wave.key(origin));
        wave.frontier.enqueue(origin.asLong());
        wave.distances.enqueue(1.0);
        wave.intensities.enqueue(intensity);
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
        rollTick(level.getGameTime(), tuning);

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
        TOUCHED.put(key, tick);
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
        final double floor = tuning.stressFloor();

        while (!this.frontier.isEmpty()) {
            // Under stress nothing is bought, so an exhausted purse is not what ends the walk - the
            // intensity floor and the scan ceiling are, and a wave that is still strong is still going.
            if (this.broken >= perImpact || spent()) {
                return true;
            }
            if (this.stress && this.scanned >= tuning.stressMaxScan()) {
                return true;
            }
            if (brokenThisTick >= tuning.shockMaxPerTick() || System.nanoTime() > deadline) {
                return false;
            }

            final long from = this.frontier.dequeueLong();
            final double distance = this.distances.dequeueDouble();
            final double arriving = this.intensities.dequeueDouble();
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

                this.scanned++;
                final int met = strike(this.cursor, distance, arriving, tuning);
                if (met == BLOCKED) {
                    continue;
                }
                if (met == SURVIVED) {
                    // The one branch that costs a block and breaks nothing, and the reason the mode reads
                    // as a shock rather than as an appetite: the deck holds, and the crash is now behind it.
                    if (this.passed > floor) {
                        this.frontier.enqueue(this.cursor.asLong());
                        this.distances.enqueue(further);
                        this.intensities.enqueue(this.passed);
                        this.gaps.enqueue(0);
                    }
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
                    this.intensities.enqueue(arriving);
                    this.gaps.enqueue(gap + 1);
                    continue;
                }
                this.broken++;
                brokenThisTick++;
                if (!this.stress || this.passed > floor) {
                    this.frontier.enqueue(this.cursor.asLong());
                    this.distances.enqueue(further);
                    this.intensities.enqueue(this.stress ? this.passed : arriving);
                    this.gaps.enqueue(0);
                }
                seal(distance, arriving, tuning);

                if (this.broken >= perImpact || spent()) {
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
     * Whether the purse is empty, which for a crack is not the same as being finished.
     *
     * <p>A cut that stops halfway has split nothing - it is a notch, and the build it is in is still one
     * build. So a crack is given a number of blocks it may take before the price of them is looked at at
     * all, and only past that does running out of energy end it. A wave has no such allowance: a wave that
     * runs out of energy has done exactly what it was meant to do.
     */
    private boolean spent() {
        return !this.stress && this.budget <= 0.0 && this.free <= 0;
    }

    /**
     * Takes the block on the plane as well as the one the seam wandered onto.
     *
     * <p>Without this a wandering seam is not a cut at all, and the reason is Sable's rather than ours. It
     * decides what is still one build by neighbours including the diagonals, so two columns whose missing
     * block sits at depths one apart are still joined <em>through</em> the seam - the block left in the first
     * column touches the block left in the second across the corner. Every column of a drifted seam is like
     * that, which is how a build could be cut end to end and stay in one piece with a groove in it.
     *
     * <p>Taking the plane block too puts back the guarantee the drift removed: whatever the seam does, the
     * whole of the plane it started on is gone wherever the crack reached, and nothing can be traced across a
     * plane that is not there. What the drift is left doing is what it was wanted for - widening the cut
     * unevenly, so the edges of the two pieces are ragged rather than sawn.
     */
    private void seal(final double distance, final double intensity, final ImpactConfig.Tuning tuning) {
        if (this.crack == null || this.cursor.get(this.crack) == this.plane) {
            return;
        }
        switch (this.crack) {
            case X -> this.cursor.setX(this.plane);
            case Y -> this.cursor.setY(this.plane);
            case Z -> this.cursor.setZ(this.plane);
        }
        if (strike(this.cursor, distance, intensity, tuning) == BROKEN) {
            this.broken++;
            brokenThisTick++;
        }
    }

    /**
     * Lets a crack step a block off its own plane, so what it leaves is a seam rather than a saw cut.
     *
     * <p>The block on the plane is taken as well - see {@link #seal} - so the drift widens the cut rather
     * than moving it, and a column the seam has wandered across loses two blocks instead of one.
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
    private int strike(final BlockPos pos, final double distance, final double intensity,
                       final ImpactConfig.Tuning tuning) {
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
            this.passed = intensity;
            return EMPTY;
        }
        if (this.stress) {
            return measure(pos, state, profile, intensity, tuning);
        }

        final double price = ImpactResolver.shockCost(
                profile.resistance() * this.toughness, tuning.shockCost(), distance)
                * (this.crack == null ? 1.0 : tuning.fractureCost());
        if (price > this.budget && this.free <= 0) {
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
        if (this.free > 0) {
            this.free--;
        }
        return BROKEN;
    }

    /**
     * The same block, weighed instead of bought.
     *
     * <p>What arrives is compared against what the block can take, and one of two things happens - neither
     * of which ends the walk. If the block fails, the shock loses that block's strength and carries the
     * excess on. If it holds, the shock loses nothing at all and carries a fraction of itself through,
     * decided by what the block is made of rather than by how far it has come.
     *
     * <p>That second case is worth being clear about, because it is the whole difference. A budgeted wave
     * that cannot afford a bulkhead has spent nothing on it and still stops, which is not a shock hitting a
     * strong wall - it is a shock hitting the end of a price list. Here the wall is a wall: the crash runs
     * along it, through it, and out the other side into whatever was cheaper to break.
     */
    private int measure(final BlockPos pos, final BlockState state, final BlockProfile profile,
                        final double intensity, final ImpactConfig.Tuning tuning) {
        final Failure failure = profile.failure();
        final double threshold = ImpactResolver.stressThreshold(
                profile.resistance() * this.toughness,
                tuning.threshold(failure),
                backing(pos, tuning));

        if (intensity <= threshold) {
            this.passed = ImpactResolver.stressPassed(intensity, threshold, false,
                    tuning.transmit(failure), tuning.stressPassOn());
            return SURVIVED;
        }

        final BlockPos broken = pos.immutable();
        if (this.contraption) {
            if (!BlockScatter.shatterContraptionBlock(this.level, broken, state, this.worldImpact,
                    this.impactVelocity, profile.resistance())) {
                // The build has spent its allowance. The shock is not over, it simply may not spend itself
                // here, so it is stopped rather than let through onto blocks it is not allowed to touch.
                this.passed = 0.0;
                return BLOCKED;
            }
        } else {
            BlockScatter.shatter(this.level, broken, state, this.worldImpact,
                    this.impactVelocity, profile.resistance());
        }
        this.passed = ImpactResolver.stressPassed(intensity, threshold, true,
                tuning.transmit(failure), tuning.stressPassOn());
        return BROKEN;
    }

    /** How much of its strength the block has where it stands, counting what holds it. Six reads, skipped
        entirely when the weight is zero - the answer is 1 either way. */
    private double backing(final BlockPos pos, final ImpactConfig.Tuning tuning) {
        final double weight = tuning.stressBacking();
        if (weight <= 0.0) {
            return 1.0;
        }
        int solid = 0;
        final BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        for (final Direction direction : Direction.values()) {
            beside.set(pos.getX(), pos.getY(), pos.getZ());
            beside.move(direction);
            final BlockState state = stateIfLoaded(beside);
            if (state != null && !state.isAir()) {
                solid++;
            }
        }
        return ImpactResolver.stressBacking(solid, weight);
    }

    /**
     * A wave spreads through a build that may be at the edge of what is loaded, and asking the level for a
     * block outside that would load the chunk from inside the break pass. A miss stays a miss.
     */
    private @Nullable BlockState stateIfLoaded(final BlockPos pos) {
        return this.chunks.stateIfLoaded(this.level, pos);
    }

    /**
     * Rolls the per-tick counters, and decides whether a crash is one tick long or one crash long.
     *
     * <p>Clearing the reservoir every tick was the quiet half of "the wreck keeps detonating after it has
     * landed". A hull that hits the ground at speed is still moving on the next tick and the one after, and
     * every one of those ticks it is a heavy fast body touching the ground - so every one of them refilled
     * the reservoir and bought a fresh set of waves out of energy the build had already spent. Under
     * {@code oneCrash} the build's own reservoir is filled once and drawn down until the build has been
     * quiet for as long as its damage budget needs, which makes both of them agree on what one crash is.
     *
     * <p>Terrain is still refilled per tick, on purpose. A hull ploughing a hillside is meant to keep
     * ploughing it for as long as it is moving, and the ground has no build to protect.
     */
    private static void rollTick(final long now, final ImpactConfig.Tuning tuning) {
        if (now == tick) {
            return;
        }
        tick = now;
        brokenThisTick = 0;
        MEASURE.forget();
        FRACTURES.clear();
        WAVES.clear();

        if (!tuning.shockOneCrash()) {
            RESERVOIR.clear();
            TOUCHED.clear();
            return;
        }
        final ObjectIterator<Int2DoubleMap.Entry> entries = RESERVOIR.int2DoubleEntrySet().iterator();
        while (entries.hasNext()) {
            final int key = entries.next().getIntKey();
            if ((key & 1) == 0 || now - TOUCHED.get(key) > tuning.protectRestTicks()) {
                TOUCHED.remove(key);
                entries.remove();
            }
        }
    }
}
