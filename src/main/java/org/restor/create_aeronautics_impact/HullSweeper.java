package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleans up the three states the contact pipeline cannot report on its own.
 *
 * <p>Blocks with no collision shape - grass, flowers, vines, torches, cobwebs - are simply not there as
 * far as physics is concerned, so a hull swallows them whole instead of mowing them down.
 *
 * <p>Past about a block of travel per solver step, the terrain a hull crosses stops producing a contact for
 * every block on the way. Those blocks are never reported, so nothing ever breaks them, and the hull comes
 * to rest inside solid ground. Carving breaks what the hull is about to move into, on the same terms the
 * contact would have.
 *
 * <p>Weight is the case none of the contact machinery can see at all. Every threshold in this mod is a speed,
 * so a build that has stopped moving weighs nothing and can settle onto a forest and leave it standing - and a
 * build that is moving is still only ever answered where it touches, which is one layer deep. Crushing asks
 * the other question, what the load on a block is against what that block can hold, and follows the answer
 * down through whatever is underneath.
 *
 * <p>Terrain a hull has ended up inside anyway is the last case: two solids sharing the same space is not a
 * state the solver can resolve, so a contraption dragged or thrown into a wall just sits there, wedged,
 * raising no contacts and therefore breaking nothing.
 */
public final class HullSweeper {

    private static final int MAX_SLAB_THICKNESS = 8;
    private static final int MAX_CARVE_REACH = 32;
    private static final int MAX_BLOCKS_SCANNED = 65536;

    /** How far off a hull block a world block's centre may sit and still count as swept through. */
    private static final double CLIP = 0.4;

    /**
     * The same tolerance for a hull that is already stuck, where the question is not what it is about to hit
     * but what it is standing in. Tighter, because a resting contact leaves a hair of penetration and calling
     * that an overlap would have a parked build slowly eat the ground it is parked on.
     */
    private static final double WEDGE_CLIP = 0.3;

    /**
     * How far around a block's top face the crush pass looks for the hull that is pressing on it.
     *
     * <p>Asking whether the hull fills the whole cell above answers a different question - whether the two are
     * squarely face to face - and almost nothing ever is. A hull sits at whatever angle it came to rest at, so
     * it meets the ground along an edge or a corner, and a test that wants the cell above filled reads every
     * one of those as untouched: the block bearing the entire weight of the build counts for nothing and the
     * load is spread over whatever happened to line up.
     */
    private static final double SUPPORT_CLIP = 0.3;

    /** How far past a block's own faces the crush pass looks for a hull leaning on it from the side. */
    private static final double TOUCH_REACH = 0.8;

    /** Low above high, so the caller's loop runs zero times without needing to be told. */
    private static final long EMPTY_RANGE = (1L << 32);

    private static final int CONTACT_NONE = 0;
    private static final int CONTACT_FLANK = 1;
    private static final int CONTACT_BEARING = 2;

    /** Ceiling on the crush lead, so something moving very fast does not turn the pass into a wide carve. */
    private static final double CRUSH_LEAD_MAX = 4.0;

    /** How far a hull's centre has to shift before it counts as having gone anywhere. */
    private static final double MOVED = 0.5;

    /** How long a hull has to stay put before it is treated as wedged rather than merely slow. */
    private static final int STILL_TICKS = 10;

    private static final int FORGET_TICKS = 600;

    /**
     * How far around a hull the clearance reading has to hold before the hull may be left alone, and the
     * longest it may be left alone for.
     *
     * <p>The margin is what makes the answer good for more than the instant it was taken: a hull with four
     * blocks of nothing on every side of it cannot reach anything until it has crossed four blocks. The cap
     * is for everything the reading cannot know about - ground generated underneath, a player building up to
     * meet it - and puts a second on how long any of that goes unnoticed.
     */
    private static final int CLEAR_MARGIN = 4;

    /**
     * Blocks per tick of descent credited to a hull on top of the speed it already has.
     *
     * <p>A window opened while something is barely moving has to survive it picking up speed, and gravity is
     * what it picks up. Half a block a tick covers the whole of the longest window several times over.
     */
    private static final double QUIET_FALL_ALLOWANCE = 0.6;

    /** The same allowance sideways, for a hull under power rather than under gravity. */
    private static final double QUIET_DRIFT_ALLOWANCE = 0.1;

    /** How long a position that took part in a shove is left alone afterwards. */
    private static final int SHOVE_COOLDOWN = 60;

    /** How far clear of the hull a shoved block has to land. */
    private static final double SHOVE_CLEARANCE = 1.0;

    /**
     * Positions that took part in a shove recently, against the tick they are free again.
     *
     * <p>Shoving a block out from under a hull leaves a gap; the hull settles into the gap; the block is now
     * beside a hull that has moved, so the next pass shoves it back where it came from. The two of them then
     * trade places for as long as anyone watches, and the hull hops between the two heights that go with it.
     *
     * <p>Letting a position take part in one shove and then holding it for a while ends that. The second time
     * round the block simply gives way, which is an outcome rather than an oscillation.
     */
    private static final Long2LongOpenHashMap SHOVED = new Long2LongOpenHashMap();

    static {
        SHOVED.defaultReturnValue(Long.MIN_VALUE);
    }

    private static final Int2ObjectMap<Tracked> TRACKED = new Int2ObjectOpenHashMap<>();

    private static final PlotProbe PROBE = new PlotProbe();

    /**
     * The fleet in the order this tick serves it. Reused rather than rebuilt so that rotating the starting
     * point costs nothing.
     */
    private static final List<ServerSubLevel> ORDER = new ArrayList<>();

    /**
     * Scratch for the crush pass. Every hull that has landed anywhere runs it, and the overwhelming majority
     * of those are resting on ground that holds them comfortably, so the pass usually walks a few thousand
     * blocks to conclude that nothing happens. Reusing the buffer keeps that costing nothing.
     */
    private static final LongArrayList SUPPORT = new LongArrayList();

    /** Scratch for blocks the hull is pressed against rather than resting on. Reused for the same reason. */
    private static final LongArrayList FLANK = new LongArrayList();

    /** Scratch for blocks about to become one of the other two, which is the whole problem with a moving hull. */
    private static final LongArrayList AHEAD = new LongArrayList();

    private static final LongArrayList QUEUE = new LongArrayList();
    private static final DoubleArrayList QUEUE_LOAD = new DoubleArrayList();
    private static final IntArrayList QUEUE_STEP = new IntArrayList();
    private static final BooleanArrayList QUEUE_UNDER = new BooleanArrayList();
    private static final LongOpenHashSet QUEUED = new LongOpenHashSet();

    private static long scanTick = Long.MIN_VALUE;
    private static int sweptThisTick;
    private static int carvedThisTick;
    private static int scanBudget;
    private static long deadline;
    private static boolean spent;
    private static int workSinceClock;
    private static int idleChecks;
    private static int detail;
    private static int overruns;
    private static int easyTicks;
    private static long tickNanos;

    /**
     * How much work may go by between two readings of the clock.
     *
     * <p>A probe is a couple of hundred nanoseconds and reading the clock is about a fortieth of that, so
     * this many units is a few tens of microseconds of drift past the deadline - fine against a limit stated
     * in whole milliseconds, and cheap enough that checking is not itself worth avoiding.
     */
    private static final int CLOCK_STRIDE = 256;

    /**
     * What one block destruction is charged against the scan budget.
     *
     * <p>A budget counted in blocks is not a budget in time. Walking a slab is a section read per block; a
     * destroyBlock in a loaded-up pack is neighbour updates, light, and every mod with an opinion about the
     * block next door, and it comes out around a millisecond apiece. The carve loop was bounded only by how
     * many blocks it was allowed to break, so a tick that found five hundred of them spent most of a second
     * on them without ever asking the clock. Pricing a break high enough gets it asked.
     *
     * <p>The price is what a break costs against the block budget; the clock is read after every one of them
     * regardless - see {@link Budget#spendBreak()}. A quarter of a stride was still four destroyBlocks
     * between two readings, and four of them in a pack where one can cascade into a couple of hundred
     * milliseconds is the quarter-second overrun the log kept showing against a six millisecond limit.
     */
    private static final int BREAK_COST = CLOCK_STRIDE / 4;

    private static final int MAX_DETAIL = SweepDetail.LEVELS - 1;

    /**
     * The heightmap every column scan starts from.
     *
     * <p>Not {@code MOTION_BLOCKING}, which counts a fluid as the top of its column: an ocean reads as ground
     * at sea level, so a hull over open water was never once quiet on the way down and the crush pass began
     * its columns at the waves and walked the whole depth to reach the seabed. This one is the top block that
     * something could stand on, which is the same thing the rest of the mod means by being in the way.
     */
    private static final Heightmap.Types GROUND = Heightmap.Types.OCEAN_FLOOR;

    private HullSweeper() {
    }

    /**
     * Whether this tick's share of the clock is gone.
     *
     * <p>Every budget below this counts blocks, and blocks are not a price. Reading one out of a section that
     * is already in memory is a handful of nanoseconds; asking the physics engine whether a hull covers a
     * point is a thousand times that, and both of them are one unit. So a pass that spent its allowance on
     * the cheap kind finished in under a millisecond and the same allowance spent on the dear kind took a
     * fifth of a second - which is what the worst ticks in the log were, and no combination of block counts
     * would have caught it, because the number of blocks was never the thing that varied.
     *
     * <p>Stated in time instead, the limit means what it was always meant to mean. Nothing here is urgent
     * enough to be worth a stutter: a pass cut short leaves terrain standing for another tick, and a tick is
     * fifty milliseconds.
     *
     * <p>The clock is read once per stride of work rather than once per ask, because reading it is itself the
     * same order of cost as the cheap kind of work being counted, and the answer sticks once given so that a
     * pass cut short stays cut short instead of resuming on the next unchecked ask.
     *
     * <p>Per stride of <em>work</em>, and that is the whole of the difference. Counting asks instead meant
     * the limit bound in inverse proportion to how badly it was needed: a pass that walked ten thousand cheap
     * blocks asked ten thousand times and was measured constantly, while a pass that spent the same tick on a
     * hundred blocks with a hundred probes apiece asked a hundred times and was never measured at all. The
     * second is the one that takes a quarter of a second. A bare ask still counts for something so that a
     * loop doing work this does not see cannot run unchecked either.
     */
    private static boolean overtime() {
        if (spent) {
            return true;
        }
        if (workSinceClock < CLOCK_STRIDE && ++idleChecks < CLOCK_STRIDE) {
            return false;
        }
        workSinceClock = 0;
        idleChecks = 0;
        spent = System.nanoTime() > deadline;
        return spent;
    }

    /**
     * Sets how much of itself the next tick's sweep is allowed to be.
     *
     * <p>Every limit above this one is a ceiling: it says when to stop, and a pass that keeps hitting it
     * keeps stopping in the same place, leaving the far side of the hull uncleared tick after tick. That is
     * the wrong shape of answer for terrain, where finishing roughly beats finishing half of it exactly.
     *
     * <p>So the ceiling being hit twice running is read as a request for less work rather than more time, and
     * the pass is coarsened until it fits: first the rewind is sampled more thinly and more widely, then only
     * the direction the hull is actually going is carved instead of all three, then the cosmetic sweeps stand
     * down and weight is answered every other tick. Climbing back up wants a hundred quiet ticks against two
     * busy ones, because the cost of being wrong in that direction is a stutter and the cost of being wrong
     * in this one is slightly blunter carving.
     */
    private static void adapt(final long nanos) {
        if (!ImpactConfig.ADAPTIVE_DETAIL.get()) {
            detail = 0;
            return;
        }

        if (spent) {
            easyTicks = 0;
            if (++overruns >= 2 && detail < MAX_DETAIL) {
                detail++;
                overruns = 0;
            }
            return;
        }
        if (nanos * 3 < (long) (ImpactConfig.MAX_TICK_MILLIS.get() * 1.0e6)) {
            overruns = 0;
            if (++easyTicks >= 100 && detail > 0) {
                detail--;
                easyTicks = 0;
            }
        }
    }

    /**
     * Everything the solver will not report on its own, once per level tick.
     *
     * <p>Sable only calls {@link ImpactCallback} where a collider actually meets one, which leaves three
     * things unaccounted for: terrain a fast hull passes clean through between two steps, terrain a landed
     * hull is resting its weight on, and terrain a hull has somehow ended up inside. This is where those are
     * found - by looking at the ground around each hull rather than by waiting to be told.
     *
     * <p>Single-player fires this for the client level too; only server levels are swept.
     */
    public static void onLevelTick(final LevelTickEvent.Post event) {
        final Level level = event.getLevel();
        if (!(level instanceof final ServerLevel serverLevel) || !ImpactConfig.enabled()) {
            return;
        }

        final long started = System.nanoTime();
        final int before = carvedThisTick + sweptThisTick;
        sweep(serverLevel);
        final long took = System.nanoTime() - started;
        // Every level ticks separately and they share one budget, so the cost is added up here and judged
        // once, at the rollover inside the sweep. Judging it here instead let a three-level world count one
        // overrun three times and coarsen itself within a single tick.
        tickNanos += took;

        if (ImpactStats.enabled()) {
            // The counters reset at the tick boundary, which happens inside the call.
            ImpactStats.addSweep(took, Math.max(0, carvedThisTick + sweptThisTick - before));
            ImpactStats.noteDetail(detail);
        }
    }

    /**
     * One level's hulls, in order of how likely they are to need the work.
     *
     * <p>Also where the tick boundary is handled - the rations, the clock, and the detail level the whole
     * sweeper runs at are rolled over here rather than in the tick listener, because several levels share
     * one budget and it has to be judged once for all of them.
     *
     * <p>A hull with nothing but air around it is dropped for a while rather than re-examined every tick:
     * that is the ordinary case for anything in flight, and it is the difference between the sweeper costing
     * nothing and it costing a scan per hull per tick forever.
     */
    private static void sweep(final ServerLevel serverLevel) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final boolean clearSoft = ImpactConfig.CLEAR_SOFT_BLOCKS.get();
        final boolean clearOverlaps = ImpactConfig.CLEAR_OVERLAPS.get();
        final boolean carve = ImpactConfig.CARVE_THROUGH_TERRAIN.get();
        final boolean crush = tuning.crushBlocks() && tuning.maxCrushPerTick() > 0;
        if (!clearSoft && !clearOverlaps && !carve && !crush) {
            return;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }

        PROBE.reset(serverLevel);

        final long now = serverLevel.getGameTime();
        if (now != scanTick) {
            scanTick = now;
            sweptThisTick = 0;
            carvedThisTick = 0;
            scanBudget = ImpactConfig.SWEEP_SCAN_BUDGET.get();
            adapt(tickNanos);
            tickNanos = 0L;
            deadline = System.nanoTime() + (long) (ImpactConfig.MAX_TICK_MILLIS.get() * 1.0e6);
            spent = false;
            workSinceClock = 0;
            idleChecks = 0;
        }

        final int softInterval = ImpactConfig.SOFT_SWEEP_INTERVAL.get();
        final int overlapInterval = ImpactConfig.OVERLAP_SWEEP_INTERVAL.get();
        final double minSpeed = ImpactConfig.SOFT_SWEEP_MIN_SPEED.get();
        final double carveSpeed = ImpactConfig.CARVE_MIN_SPEED.get();
        final boolean drop = ImpactConfig.DROP_ITEMS.get();

        ORDER.clear();
        ORDER.addAll(container.getAllSubLevels());
        final int fleet = ORDER.size();

        // Where in the fleet this tick starts. The loop below gives up the moment the tick's time is gone,
        // and the collection hands them over in the same order every tick, so a world with more hulls than
        // one tick can serve was serving the same few of them forever and never once looking at the rest.
        final int first = fleet == 0 ? 0 : (int) Math.floorMod(now, fleet);

        for (int step = 0; step < fleet; step++) {
            final ServerSubLevel subLevel = ORDER.get((first + step) % fleet);
            if (subLevel.isRemoved()) {
                continue;
            }
            if (overtime()) {
                break;
            }

            final Vector3d velocity = subLevel.latestLinearVelocity;
            final double speed = velocity.length();
            if (Double.isNaN(speed)) {
                continue;
            }

            final int id = subLevel.getRuntimeId();
            final boolean moving = speed >= minSpeed;
            final BoundingBox3dc bounds = subLevel.boundingBox();
            final double centreX = (bounds.minX() + bounds.maxX()) * 0.5;
            final double centreY = (bounds.minY() + bounds.maxY()) * 0.5;
            final double centreZ = (bounds.minZ() + bounds.maxZ()) * 0.5;

            Tracked tracked = TRACKED.get(id);
            if (tracked == null) {
                tracked = new Tracked(now, centreX, centreY, centreZ);
                TRACKED.put(id, tracked);
            }
            tracked.lastSeen = now;

            if (tracked.displacedFrom(centreX, centreY, centreZ) > MOVED) {
                tracked.moved(now, centreX, centreY, centreZ);
            }

            // A hull that is getting nowhere is never quiet, whatever the heightmap says about the ground
            // under it. The window below is an optimisation for something crossing open air and its whole
            // warrant is that the hull will have moved on before it expires - one that has not moved in half
            // a second will not have. It is either parked, which costs nothing to look at, or it is caught on
            // something the heightmap does not record: a canopy, a fence, a slab, anything that does not
            // block motion and so never sets a column's height. That is exactly the hull that needs digging
            // out, and skipping it renewed the skip every time, so it hung there for good.
            final boolean gettingNowhere = now - tracked.lastMoved >= STILL_TICKS;
            if (!gettingNowhere) {
                if (now < tracked.quietUntil) {
                    ImpactStats.addHull(true);
                    continue;
                }
                final int quiet = quietTicks(serverLevel, bounds, velocity);
                if (quiet > 0) {
                    tracked.quietUntil = now + quiet;
                    ImpactStats.addHull(true);
                    continue;
                }
            }
            ImpactStats.addHull(false);

            if (carve && speed >= carveSpeed) {
                final long started = ImpactStats.mark();
                carve(serverLevel, subLevel, velocity, speed, ImpactConfig.CARVE_MAX_BLOCKS.get());
                ImpactStats.since(ImpactStats.Phase.CARVE, started);
            }
            // Weight is not something a hull only has while parked. A boulder rolling down a hillside presses
            // on the canopy under it exactly as hard as one sitting on it does, and gating this on speed left
            // the rolling one with nothing but its contact layer - the top of each tree and no more.
            //
            // A moving one also needs answering every tick rather than every fourth. It is over something new
            // each time, so a slow pass keeps arriving after the fact, and the build rides along the top of
            // whatever it should be going through. Only a settling one has nowhere to go but down.
            final long moves = Math.max(1, tuning.movingCrushInterval());
            final long crushEvery = moving
                    ? (SweepDetail.cosmetic(detail) ? moves : moves * 2L)
                    : tuning.crushInterval();
            if (crush && (now + id) % crushEvery == 0L) {
                final long started = ImpactStats.mark();
                crush(serverLevel, subLevel, bounds, velocity, moving, clearSoft, massOf(subLevel), tuning);
                ImpactStats.since(ImpactStats.Phase.CRUSH, started);
            }
            // Offsetting each hull's sweep phase by its own id keeps a fleet of them from all scanning on
            // the same tick and spiking it.
            if (clearSoft && SweepDetail.cosmetic(detail) && moving && (now + id) % softInterval == 0L) {
                final long started = ImpactStats.mark();
                sweepSoft(serverLevel, subLevel, velocity, speed, softInterval,
                        ImpactConfig.SOFT_SWEEP_MAX_BLOCKS.get(), drop);
                ImpactStats.since(ImpactStats.Phase.SOFT, started);
            }

            // Wedged is about getting nowhere, not about being slow. A hull buried in terrain is shoved by
            // every overlapping block at once and jitters hard, so a speed test calls it moving and never
            // touches it - which is exactly the hull that never comes out on its own. And unlike everything
            // else competing for the tick, this one is a rescue rather than a refinement: the rest can be
            // skipped and picked up next tick, while a hull nobody digs out stays where it is for good. So it
            // is neither gated on the detail rung nor given up on after a while - it only slows down.
            final long still = now - tracked.lastMoved;
            if (clearOverlaps && still >= STILL_TICKS) {
                final boolean deep = still > tuning.stuckGraceTicks();
                final boolean desperate = still > tuning.grindStuckTicks();
                // Each attempt costs more and reaches further than the one before it, so each runs less often
                // than the one before it. The last one runs for as long as the hull sits there, which is why
                // it has to be cheap in the steady state: a parked build looks the same as a buried one from
                // here, and the difference only shows up in what the sweep finds.
                final int interval = desperate
                        ? overlapInterval * 8
                        : deep ? overlapInterval * 4 : overlapInterval;
                // Freeing something counts as progress, so a deep burial keeps being dug at rather than
                // being abandoned partway through on a timer.
                if ((now + id) % interval == 0L) {
                    final long started = ImpactStats.mark();
                    final int freed = sweepOverlaps(serverLevel, subLevel, bounds,
                            ImpactConfig.OVERLAP_SWEEP_MAX_BLOCKS.get(), drop, deep, desperate);
                    ImpactStats.since(ImpactStats.Phase.OVERLAP, started);
                    if (freed > 0) {
                        tracked.lastMoved = now;
                    }
                }
            }
        }

        ORDER.clear();

        if (now % 100L == 0L) {
            TRACKED.values().removeIf(entry -> now - entry.lastSeen > FORGET_TICKS);
            SHOVED.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        }
    }

    /**
     * How many ticks a hull can be left entirely alone, because there is nothing anywhere near it.
     *
     * <p>Everything below this exists to answer questions about a hull and the terrain around it, and a hull
     * dropped from a height has no terrain around it for the whole of a long fall. Each pass has its own
     * cheap way of finding that out and they all find it out again every tick: the crush pass reads a
     * heightmap for every column under the build, carving walks a swept slab per axis, and all of it comes
     * back empty a few hundred times on the way down. Asking once and then keeping quiet costs one reading
     * per second of falling instead of twenty.
     *
     * <p>The heightmap is the whole trick. It gives the top of each column, so a hull whose floor is above
     * every top in reach has nothing below it and - because there is nothing above a column's top by
     * definition - nothing above it either.
     *
     * <p>A hull nowhere near anything is also a hull nothing can be gained by working on, so this is not
     * only cheaper but the same answer. What it must not do is be wrong, hence the margin and the cap: the
     * reading has to hold across every tick it is trusted for, and it is never trusted for long.
     *
     * @return ticks the hull may be skipped for, or 0 to do the work now.
     */
    private static int quietTicks(final ServerLevel level,
                                  final BoundingBox3dc bounds,
                                  final Vector3d velocity) {
        final int floor = (int) Math.floor(bounds.minY());
        if (floor <= level.getMinBuildHeight()) {
            return 0;
        }

        final int minX = (int) Math.floor(bounds.minX()) - CLEAR_MARGIN;
        final int maxX = (int) Math.floor(bounds.maxX()) + CLEAR_MARGIN;
        final int minZ = (int) Math.floor(bounds.minZ()) - CLEAR_MARGIN;
        final int maxZ = (int) Math.floor(bounds.maxZ()) + CLEAR_MARGIN;

        // A landed build has ground under its middle, so asking there first is what keeps this from being a
        // second full column scan on top of the one the crush pass already does.
        final int midX = (minX + maxX) >> 1;
        final int midZ = (minZ + maxZ) >> 1;
        final LevelChunk middle = level.getChunkSource().getChunkNow(midX >> 4, midZ >> 4);
        if (middle == null || middle.getHeight(GROUND, midX, midZ) >= floor) {
            return 0;
        }

        int gap = Integer.MAX_VALUE;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                final LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                // Ground that is not loaded is ground this cannot speak for.
                if (chunk == null) {
                    return 0;
                }

                final int x0 = Math.max(minX, cx << 4);
                final int x1 = Math.min(maxX, (cx << 4) + 15);
                final int z0 = Math.max(minZ, cz << 4);
                final int z1 = Math.min(maxZ, (cz << 4) + 15);

                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        final int surface = chunk.getHeight(GROUND, x, z);
                        if (surface >= floor) {
                            return 0;
                        }
                        gap = Math.min(gap, floor - surface);
                    }
                }

                // This runs before a hull has any budget of its own and once per hull per tick, so a fleet
                // parked over a wide footprint was reading a column apiece for nothing the tick could see.
                workSinceClock += (x1 - x0 + 1) * (z1 - z0 + 1);
                if (overtime()) {
                    return 0;
                }
            }
        }

        if (gap == Integer.MAX_VALUE) {
            return 0;
        }

        // The two ways out of the clear space are not the same size, and treating them as one made the whole
        // thing useless exactly where it was wanted: a hull dropped from a height falls fast and drifts
        // barely at all, so a margin of four blocks against its speed bought back a single tick out of a
        // fall of two hundred. Downwards it has the whole gap to cross; sideways it has the margin.
        final double falling = Math.max(0.0, -velocity.y) / 20.0 + QUIET_FALL_ALLOWANCE;
        final double drifting = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) / 20.0
                + QUIET_DRIFT_ALLOWANCE;
        final double ticks = Math.min(gap / falling, CLEAR_MARGIN / drifting);
        return (int) Math.min(ImpactConfig.tuning().maxQuietTicks(), ticks);
    }

    /**
     * Breaks what the hull is about to occupy. The prediction is the whole point: testing where the hull is
     * now would only ever find blocks it is already stuck inside, which is the state this exists to avoid.
     *
     * <p>Both materials are weighed, not just the terrain. Asking only whether the terrain gives way is the
     * same question a wrecking ball asks, and it is the wrong one for a build that is mostly whatever the
     * player had a lot of: a sphere of dirt was eating its way through stone and coming out the far side
     * intact, because the stone was never asked to compare itself against anything. The contact path has
     * always run the contest properly and this is the same contest, so the two now disagree about nothing.
     */
    private static void carve(final ServerLevel level,
                              final ServerSubLevel subLevel,
                              final Vector3d velocity,
                              final double speed,
                              final int maxCleared) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final BoundingBox3dc bounds = subLevel.boundingBox();
        final Budget budget = new Budget(maxCleared, true);
        final Vector3d point = new Vector3d();
        final Vector3d origin = new Vector3d();
        final double window = ImpactConfig.tuning().carveLookaheadTicks() / 20.0;
        final double travel = speed * window;
        final int rung = SweepDetail.resolution(detail, travel);
        final double clip = SweepDetail.clip(rung);
        final int steps = SweepDetail.steps(travel, rung);
        final int leading = dominantAxis(velocity);
        final double boreSpeed = speed >= ImpactConfig.BORE_MIN_SPEED.get()
                ? speed * ImpactConfig.BORE_SHARE.get()
                : 0.0;
        final BlockPos.MutableBlockPos wall = new BlockPos.MutableBlockPos();
        final double mass = massOf(subLevel);
        final boolean breakHull = tuning.breakContraptionBlocks();
        final double toughness = tuning.contraptionBlockToughness();
        final double hullBacking = tuning.hullBackingWeight();
        final int hullReach = tuning.hullBackingReach();
        final double wearShare = tuning.impactWear();
        final BlockPos.MutableBlockPos plot = new BlockPos.MutableBlockPos();
        final List<BlockPos> hits = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        final List<BlockProfile> profiles = new ArrayList<>();
        final List<BlockState> hullStates = new ArrayList<>();
        final LongArrayList hullAt = new LongArrayList();
        double momentum = 0.0;
        int broken = 0;

        // One step of the rewind, carried into the hull's own frame once instead of once per block. It has
        // nothing to do with which axis is being swept, so all three share it - and share the answers it
        // gives, which is what the set below is for. The slabs overlap wherever two of them meet at a corner,
        // and a block in that overlap used to be rewound once per pass for the same conclusion.
        final Vector3d localStep = subLevel.logicalPose()
                .transformNormalInverse(new Vector3d(velocity).mul(-window / steps));
        final LongOpenHashSet tested = new LongOpenHashSet();

        for (int axis = 0; axis < 3; axis++) {
            if (SweepDetail.leadingAxisOnly(detail) && axis != leading) {
                continue;
            }
            final double component = velocity.get(axis);
            if (Math.abs(component) < 0.25 * speed) {
                continue;
            }

            hits.clear();
            states.clear();
            profiles.clear();
            hullStates.clear();
            hullAt.clear();

            final Slab slab = sweptRegion(bounds, axis, velocity, window);
            forEachBlock(level, budget, slab, (cursor, state) -> {
                final BlockProfile profile = BlockProfile.of(level, cursor, state);
                if (profile.indestructible() || profile.passable()) {
                    return;
                }
                if (!tested.add(cursor.asLong())) {
                    return;
                }
                if (!willOccupy(subLevel, localStep, point, cursor, steps, clip, budget)) {
                    return;
                }
                hits.add(cursor.immutable());
                states.add(state);
                profiles.add(profile);
                hullStates.add(PROBE.lastSolid());
                hullAt.add(PROBE.lastSolidPos());
                budget.cleared++;
            });

            if (hits.isEmpty()) {
                continue;
            }

            // Everything the hull is about to meet at once is what it spreads its weight over, which is the
            // same reading of contact area the reported-contact path takes - a wide face bites less deeply.
            final double massFactor = ImpactResolver.massFactor(
                    ImpactResolver.contactPressure(mass, hits.size()),
                    tuning.referencePressure(), tuning.massSensitivity(),
                    tuning.massFactorMin(), tuning.massFactorMax());

            final double kinetic = ImpactResolver.shockKinetic(
                    mass, speed, tuning.shockKineticScale(), tuning.shockMinSpeed());
            final int bodyId = subLevel.getRuntimeId();

            for (int index = 0; index < hits.size(); index++) {
                // The deadline only, not the block cap: the cap was already spent gathering these hits, and
                // a pass that finds what the hull is about to go through and then refuses to break any of it
                // is worse than one that never looked.
                if (overtime()) {
                    break;
                }

                final BlockPos pos = hits.get(index);
                final BlockState state = states.get(index);
                final BlockProfile profile = profiles.get(index);

                // Half a block back along the heading, which is both where the debris is thrown from and the
                // face the hull is arriving at - so the same point answers what is behind this block.
                origin.set(velocity).mul(-0.5 / speed)
                        .add(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

                final ImpactResolver.Side side = profile.side(massFactor, 1.0,
                        Backing.of(level, pos, origin.x, origin.y, origin.z));
                final BlockState hullState = hullStates.get(index);
                ImpactResolver.Side face = null;
                if (breakHull && hullState != null) {
                    // Deeper into the hull is further along the walk that found this block, so the direction
                    // the sweep was already going is the direction the load goes.
                    plot.set(hullAt.getLong(index));
                    face = BlockProfile.of(level, plot, hullState).side(massFactor, toughness,
                            Backing.along(level, plot, localStep.x(), localStep.y(), localStep.z(),
                                    hullBacking, hullReach));
                }

                final ImpactResolver.Victim victim = ImpactResolver.victim(speed, side, face, false);
                if (victim == ImpactResolver.Victim.NONE) {
                    continue;
                }

                if (victim == ImpactResolver.Victim.OTHER) {
                    // The cursor is already on the hull block, put there to weigh it up a moment ago. The
                    // debris is spawned at the terrain block instead, which is where the crash looks like it
                    // happened - the hull block itself is out in the plot grid.
                    if (PendingBreaks.queue(level, plot, hullState, origin, speed,
                            face.resistance(), overshoot(speed, face), true, bodyId, kinetic)) {
                        PendingBreaks.wear(level, pos, state, origin, speed, side.resistance(),
                                ImpactResolver.wear(side, face) * wearShare, false);
                        momentum += ImpactResolver.breakDrag(
                                face.resistance(), speed, tuning.breakDragMass());
                        broken++;
                        budget.spendBreak();
                    }
                    continue;
                }

                BlockScatter.shatter(level, pos, state, origin, speed, profile.resistance());
                if (face != null) {
                    PendingBreaks.wear(level, plot, hullState, origin, speed, face.resistance(),
                            ImpactResolver.wear(face, side) * wearShare, true);
                }
                momentum += ImpactResolver.breakDrag(
                        side.resistance(), speed, tuning.breakDragMass());
                broken++;
                budget.spendBreak();

                if (boreSpeed > 0.0) {
                    final int sheared = shearWalls(level, pos, axis, wall, origin, velocity, speed,
                            boreSpeed, massFactor, budget);
                    momentum += ImpactResolver.breakDrag(
                            side.resistance(), boreSpeed, tuning.breakDragMass()) * sheared;
                    broken += sheared;
                }
            }

            if (budget.exhausted()) {
                break;
            }
        }

        // Once for the tick rather than once per axis. Braking is capped as a share of the hull's speed, so
        // three calls took three bites out of what was meant to be one, and a hull moving diagonally was
        // slowed half again as hard as the same hull moving straight.
        PendingBreaks.brake(subLevel, momentum);
        ImpactStats.addCarved(broken);
    }

    /**
     * Destroys whatever the hull is standing on that cannot hold its weight.
     *
     * <p>The load is the hull's mass spread over every column bearing it, so it falls as the footprint grows,
     * and the footprint grows as the hull sinks. That is the whole model and it is self-limiting by
     * construction: a build settles until the ground it has spread itself over is enough to hold it, and then
     * stops, having dug exactly the hole its own weight called for. It is also why shape matters more than
     * weight - a pillar stood on its end is carried by one column and drives itself into the ground, while the
     * same pillar laid flat is carried by twenty and rests on grass.
     *
     * <p>The contact is only where the load enters. From there it travels through the terrain, block to
     * block, losing a share of itself at each one, and destroys whatever it is still strong enough to destroy
     * when it arrives. The surface is not what carries a load - the structure behind it is. A boulder rolled
     * onto a tree does not shave the top log off and settle on the stump; the trunk fails as a trunk, and the
     * branches fail with it.
     *
     * <p>Which is also the answer to the question this pass kept getting wrong. A hull that weighs twenty-six
     * thousand and touches seven blocks is loading each of them two hundred times past what wood can take,
     * and stopping at those seven threw away every bit of that: a thirty-block boulder nibbled a dozen blocks
     * a tick out of a forest it should have been clearing. Everything above yield now goes somewhere, and how
     * far it reaches falls out of how far over yield it was. Something barely heavy enough marks only what it
     * is touching; nothing needs a separate rule for being enormous.
     *
     * <p>Load travels through blocks and not through gaps, so the spread follows whatever is being crushed
     * rather than carving a ball out of the landscape - a tree conducts it the length of its trunk, and the
     * air beside the tree conducts nothing. The same attenuation is what stops a weight heavy enough to break
     * the first block from breaking every block beneath it down to bedrock.
     *
     * <p>Not everything holding a build up is underneath it. Something settling into a canopy ends up caught
     * on the branches around its waist, wedged rather than supported, and a model that only ever looks down
     * cannot see any of that: the build hangs there on whatever it happened to brush against, which is exactly
     * what a thing far too heavy for a tree should not be able to do.
     *
     * <p>Which is also why a column is walked through rather than answered by its topmost block. That block
     * shields what is under it from a weight pressing down, and from nothing else - a branch with a leaf above
     * it is still a branch a hull can hang off sideways, and a tree is mostly made of exactly that.
     */
    private static void crush(final ServerLevel level,
                              final ServerSubLevel subLevel,
                              final BoundingBox3dc bounds,
                              final Vector3d velocity,
                              final boolean moving,
                              final boolean clearSoft,
                              final double mass,
                              final ImpactConfig.Tuning tuning) {
        if (mass <= 0.0) {
            return;
        }

        final double shear = tuning.crushShear();
        final boolean drop = tuning.dropItems();
        final BoundingBox3ic plot = subLevel.getPlot().getBoundingBox();
        final Vector3d probe = new Vector3d();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        SUPPORT.clear();
        FLANK.clear();
        AHEAD.clear();

        // Where the hull will be shortly, which is the only place a rolling build's next support can come
        // from. Answering only for what it touches right now means every pass arrives to find the thing
        // already carried by something else, which is what riding along the top of a forest is made of.
        final double lead = moving
                ? Math.min(tuning.crushLeadTicks() / 20.0,
                        CRUSH_LEAD_MAX / Math.max(velocity.length(), 1.0e-6))
                : 0.0;
        final boolean leading = lead > 0.0;
        final double leadX = velocity.x * lead;
        final double leadY = velocity.y * lead;
        final double leadZ = velocity.z * lead;

        // A block the hull is leaning on can sit just outside the footprint its own bounds describe, and one
        // it is about to lean on sits a whole step of travel outside it.
        final int minX = (int) Math.floor(bounds.minX() + Math.min(0.0, leadX)) - 1;
        final int maxX = (int) Math.floor(bounds.maxX() + Math.max(0.0, leadX)) + 1;
        final int minZ = (int) Math.floor(bounds.minZ() + Math.min(0.0, leadZ)) - 1;
        final int maxZ = (int) Math.floor(bounds.maxZ() + Math.max(0.0, leadZ)) + 1;
        final int floorY = (int) Math.floor(bounds.minY() + Math.min(0.0, leadY));
        final int ceiling = Math.min(
                Math.min((int) Math.floor(bounds.maxY() + Math.max(0.0, leadY)),
                        floorY + tuning.crushSpan()),
                level.getMaxBuildHeight() - 1);
        final int bottom = Math.max(floorY - 1, level.getMinBuildHeight());

        // A world column crosses the hull's own box along a straight line, because only the position of that
        // box changes with height and not its orientation. Clipping the line against the box says outright
        // which stretch of the column could touch the hull - and for the great majority of them the answer is
        // none of it, at the price of one transform. Walking each column from the surface down to the hull's
        // floor instead meant reading the whole height of the build out of the world for every square it
        // covers: a boulder sat on a slope was pulling thirty thousand blocks back a tick to conclude that a
        // few dozen of them were anywhere near it.
        final Vector3d up = subLevel.logicalPose().transformNormalInverse(new Vector3d(0.0, 1.0, 0.0));
        // The lead probe asks about a point up to a step of travel away, so the clip has to allow for it.
        final double margin = TOUCH_REACH + Math.sqrt(leadX * leadX + leadY * leadY + leadZ * leadZ);
        final double[] slope = {up.x, up.y, up.z};
        final double[] near = {plot.minX() - margin, plot.minY() - margin, plot.minZ() - margin};
        final double[] far = {plot.maxX() + 1.0 + margin, plot.maxY() + 1.0 + margin, plot.maxZ() + 1.0 + margin};

        int scanned = 0;
        boolean truncated = false;

        scan:
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                final long range = touchRange(subLevel, probe, slope, near, far, x, z);
                final int lowest = Math.max(bottom, (int) (range >> 32));
                final int highest = (int) range;
                if (lowest > highest) {
                    continue;
                }

                final LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }

                // A hull in flight has nothing but air under it for a hundred blocks, and reading every one
                // of those back would be the whole cost of this pass. The heightmap answers where the
                // column's first candidate is without touching it.
                final int surface = chunk.getHeight(GROUND, x, z);
                if (surface < lowest) {
                    continue;
                }

                for (int y = Math.min(Math.min(ceiling, surface), highest); y >= lowest; y--) {
                    if (++scanned > tuning.crushScanBudget() || overtime()) {
                        truncated = true;
                        break scan;
                    }
                    cursor.set(x, y, z);
                    final BlockState state = chunk.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    final int met = contactAt(subLevel, plot, probe, x + 0.5, y + 0.5, z + 0.5);

                    // A canopy is leaves, and leaves have a collision box, so a boulder really does get held
                    // up by one. Nothing about that is load bearing - the crush model has no weight small
                    // enough for a leaf to carry - so what the hull is inside of simply goes, free and
                    // uncapped. Leaving it to the soft sweep meant clearing only what was ahead along the
                    // travel, and a hull sat on a treetop is held up by what is underneath it.
                    final BlockProfile above = BlockProfile.of(level, cursor, state);
                    if (above.passable()) {
                        if (above.soft() && clearSoft && met != CONTACT_NONE) {
                            BlockScatter.clear(level, cursor, drop);
                        }
                        continue;
                    }

                    if (met == CONTACT_BEARING) {
                        SUPPORT.add(BlockPos.asLong(x, y, z));
                        // This column's load has found its path down; the descent takes it from here.
                        break;
                    }
                    if (met == CONTACT_FLANK && shear > 0.0) {
                        FLANK.add(BlockPos.asLong(x, y, z));
                    } else if (leading && contactAt(subLevel, plot, probe,
                            x + 0.5 - leadX, y + 0.5 - leadY, z + 0.5 - leadZ) != CONTACT_NONE) {
                        AHEAD.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }

        ImpactStats.addCrushScan(scanned);

        // A truncated scan found only some of what is holding the build up, which reads as a load higher than
        // the real one, and acting on that would have it punch through terrain that is comfortably carrying it.
        if (truncated) {
            return;
        }

        // Sideways contact carries its share both ways: it is loaded less than something underneath, and it
        // takes that much off everything else. Otherwise a hull wedged against half a hillside would read as
        // resting on the four blocks it happens to be squarely on top of.
        //
        // What is about to carry it counts the same way. Leaving it out was meant to keep the pressure that
        // clears it from being thinned, and instead it divided a hull's whole weight by whatever it happened
        // to be brushing: a sphere bounding down a wooded slope found no support, one flank and a hundred
        // blocks ahead, and read as pressing thirty thousand tonnes onto half a block. Nothing survives that,
        // which is why the ground came away in craters.
        //
        // The floor underneath is the same statement made once more: no hull concentrates its weight on less
        // than a single block, however glancing the contact that found it.
        if (SUPPORT.isEmpty() && FLANK.isEmpty() && AHEAD.isEmpty()) {
            return;
        }

        // How much of the build's own shadow is credited as carrying it whatever the probes found. Contact
        // counted tick by tick swings by two orders of magnitude between a build parked on a field and the
        // same build rolling across it, and its weight does not - so the rolling one was reading as pressing
        // thousands of times harder than the parked one and cut a trench wherever it went.
        final double seat = (bounds.maxX() - bounds.minX()) * (bounds.maxZ() - bounds.minZ())
                * tuning.crushSeat();

        final double footprint = Math.max(
                SUPPORT.size() + (FLANK.size() + AHEAD.size()) * shear, Math.max(seat, 1.0));
        final double pressure = mass / footprint;
        final double spread = tuning.crushSpread();
        final int reach = tuning.crushDepth();
        final int cap = tuning.maxCrushPerTick();
        final double sideways = pressure * shear;

        QUEUE.clear();
        QUEUE_LOAD.clear();
        QUEUE_STEP.clear();
        QUEUE_UNDER.clear();
        QUEUED.clear();

        for (int index = 0; index < SUPPORT.size(); index++) {
            enqueue(SUPPORT.getLong(index), pressure, 0, true);
        }
        for (int index = 0; index < FLANK.size(); index++) {
            enqueue(FLANK.getLong(index), sideways, 0, false);
        }
        for (int index = 0; index < AHEAD.size(); index++) {
            enqueue(AHEAD.getLong(index), sideways, 0, false);
        }

        final int seeded = QUEUE.size();
        final int minY = level.getMinBuildHeight();
        final int maxY = level.getMaxBuildHeight() - 1;
        final double attenuation = ImpactResolver.crushLoadAt(1.0, 1, spread);

        // What the build presses down on and what it is wedged against get a cap each, because they compete
        // otherwise. Ground is large and joined up, so a shared cap goes entirely on digging downwards and
        // the branches at the flanks - the things actually holding a rolling boulder up - never come up.
        int under = 0;
        int side = 0;

        for (int head = 0; head < QUEUE.size() && (under < cap || side < cap); head++) {
            final boolean bearing = QUEUE_UNDER.getBoolean(head);
            if ((bearing ? under : side) >= cap) {
                continue;
            }
            final long key = QUEUE.getLong(head);
            final double load = QUEUE_LOAD.getDouble(head);
            final int step = QUEUE_STEP.getInt(head);
            final int x = BlockPos.getX(key);
            final int y = BlockPos.getY(key);
            final int z = BlockPos.getZ(key);

            final LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) {
                continue;
            }
            cursor.set(x, y, z);
            final BlockState state = chunk.getBlockState(cursor);
            // A gap is where the load runs out of anything to travel through. Without this the spread would
            // jump across open air and chew a sphere out of the landscape instead of following the structure.
            if (state.isAir()) {
                continue;
            }

            final BlockProfile profile = BlockProfile.of(level, cursor, state);
            final boolean gaveWay = profile.passable()
                    || crushOne(level, bounds, cursor, state, profile, load, bearing, drop, tuning);
            if (!gaveWay) {
                // It held, so it is now the thing carrying this much, and nothing behind it feels any of it.
                continue;
            }
            if (!profile.passable()) {
                if (bearing) {
                    under++;
                } else {
                    side++;
                }
            }

            if (step >= reach) {
                continue;
            }
            final double onwards = load * attenuation;
            for (final Direction direction : Direction.values()) {
                if (direction == Direction.UP) {
                    continue;
                }
                final int nx = x + direction.getStepX();
                final int ny = y + direction.getStepY();
                final int nz = z + direction.getStepZ();
                if (ny < minY || ny > maxY) {
                    continue;
                }
                final double share = direction == Direction.DOWN
                        ? tuning.crushDownShare()
                        : tuning.crushSideShare();
                enqueue(BlockPos.asLong(nx, ny, nz), onwards * share, step + 1, bearing);
            }
        }

        if (ImpactStats.enabled()) {
            ImpactStats.addCrush(mass, footprint, pressure, under, side, seeded);
        }
    }

    /**
     * The stretch of a world column that can touch the hull, as an inclusive pair of block heights packed
     * high half first. An empty range comes back with its low above its high, so a loop over it does nothing.
     *
     * <p>Carried out in the hull's own frame, where its box is axis aligned and the clip is three divisions.
     * The column is a straight line there because the pose's rotation does not depend on height: the point
     * one block higher in the world is the same point plus the hull-frame image of straight up.
     */
    private static long touchRange(final ServerSubLevel subLevel,
                                   final Vector3d scratch,
                                   final double[] slope,
                                   final double[] near,
                                   final double[] far,
                                   final int x, final int z) {
        scratch.set(x + 0.5, 0.5, z + 0.5);
        subLevel.logicalPose().transformPositionInverse(scratch, scratch);

        double low = Double.NEGATIVE_INFINITY;
        double high = Double.POSITIVE_INFINITY;

        for (int axis = 0; axis < 3; axis++) {
            final double origin = scratch.get(axis);
            final double along = slope[axis];
            if (Math.abs(along) < 1.0e-9) {
                if (origin < near[axis] || origin > far[axis]) {
                    return EMPTY_RANGE;
                }
                continue;
            }
            final double first = (near[axis] - origin) / along;
            final double second = (far[axis] - origin) / along;
            low = Math.max(low, Math.min(first, second));
            high = Math.min(high, Math.max(first, second));
            if (low > high) {
                return EMPTY_RANGE;
            }
        }

        return ((long) (int) Math.ceil(low) << 32) | ((int) Math.floor(high) & 0xFFFFFFFFL);
    }

    /**
     * Adds one block to the crush frontier, once.
     *
     * <p>Four parallel lists rather than a queue of objects: this is filled and drained every crush pass over
     * every block under every landed build, and the object per entry was the allocation the pass made most of.
     * {@code QUEUED} is what keeps the load from going round a ring of blocks forever.
     */
    private static void enqueue(final long key, final double load, final int step, final boolean under) {
        if (!QUEUED.add(key)) {
            return;
        }
        QUEUE.add(key);
        QUEUE_LOAD.add(load);
        QUEUE_STEP.add(step);
        QUEUE_UNDER.add(under);
    }

    /** @return whether the block gave way, which is also whether the load carries past it. */
    private static boolean crushOne(final ServerLevel level,
                                    final BoundingBox3dc bounds,
                                    final BlockPos pos,
                                    final BlockState state,
                                    final BlockProfile profile,
                                    final double load,
                                    final boolean bearing,
                                    final boolean drop,
                                    final ImpactConfig.Tuning tuning) {
        final double weight = tuning.backingWeight();

        // What the block is worth with nothing at all holding it up, which is the least it can ever be worth.
        // Anything that holds at that reading holds full stop, and the great majority of ground does, so the
        // look at what is actually behind it is spared for the few blocks where the answer could go either
        // way. Terrain that gives under weight is terrain that was thin, and being thin is not a property of
        // the material - a stone slab bridging a ravine is stone, and it is also going to drop you.
        final double floor = ImpactResolver.crushStrength(
                profile.resistance() * ImpactResolver.backed(0.0, weight), tuning.crushPressureScale());
        if (load <= floor) {
            return false;
        }

        final double backing = weight <= 0.0 ? 1.0 : Backing.of(level, pos,
                bearing ? pos.getX() + 0.5 : (bounds.minX() + bounds.maxX()) * 0.5,
                bearing ? pos.getY() + 1.5 : (bounds.minY() + bounds.maxY()) * 0.5,
                bearing ? pos.getZ() + 0.5 : (bounds.minZ() + bounds.maxZ()) * 0.5);

        final double strength = ImpactResolver.crushStrength(
                profile.resistance() * backing, tuning.crushPressureScale());
        if (load <= strength) {
            return false;
        }
        if (!CrackTracker.hit(level, pos, ImpactResolver.crushOvershoot(load, strength), true, tuning)) {
            return false;
        }

        if (tuning.crushDisplace()
                && displace(level, bounds, pos, state, tuning.crushDisplaceReach())) {
            return true;
        }

        // Nothing is thrown clear: a crush has no direction to throw it in, which is exactly what makes ground
        // give way under a weight look like subsidence rather than an explosion.
        BlockScatter.clear(level, pos, drop);
        CrackTracker.spall(level, pos, true, tuning);
        return true;
    }

    /**
     * Shoves a block out of the way instead of destroying it.
     *
     * <p>Ground pushed aside by something heavy has to go somewhere: it heaps up at the edge of the furrow.
     * So a spot with something under it is taken over one left hanging, and the search runs outward from the
     * hull rather than in any direction at all, which keeps the heap beside the track instead of back under
     * it. Failing both, the block breaks as it used to - displacing is what is tried, not what is promised.
     *
     * <p>Anything holding a block entity is left to break. Carrying one across means carrying its contents,
     * and a chest that arrives empty is worse than a chest that was crushed.
     *
     * @return whether the block found somewhere to go.
     */
    private static boolean displace(final ServerLevel level,
                                    final BoundingBox3dc bounds,
                                    final BlockPos pos,
                                    final BlockState state,
                                    final int reach) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }

        final long now = level.getGameTime();
        if (SHOVED.get(pos.asLong()) > now) {
            return false;
        }

        final double awayX = pos.getX() + 0.5 - (bounds.minX() + bounds.maxX()) * 0.5;
        final double awayZ = pos.getZ() + 0.5 - (bounds.minZ() + bounds.maxZ()) * 0.5;
        final Direction primary = Math.abs(awayX) >= Math.abs(awayZ)
                ? (awayX >= 0.0 ? Direction.EAST : Direction.WEST)
                : (awayZ >= 0.0 ? Direction.SOUTH : Direction.NORTH);
        final Direction[] order = {primary, primary.getClockWise(), primary.getCounterClockWise()};

        BlockPos hanging = null;
        for (int step = 1; step <= reach; step++) {
            for (final Direction direction : order) {
                for (int lift = 0; lift <= 1; lift++) {
                    final BlockPos spot = pos.offset(
                            direction.getStepX() * step, lift, direction.getStepZ() * step);
                    if (!vacant(level, bounds, spot) || SHOVED.get(spot.asLong()) > now
                            || !outward(bounds, pos, spot)) {
                        continue;
                    }
                    if (!level.getBlockState(spot.below()).canBeReplaced()) {
                        move(level, pos, spot, state);
                        return true;
                    }
                    if (hanging == null) {
                        hanging = spot;
                    }
                }
            }
        }

        if (hanging == null) {
            return false;
        }
        move(level, pos, hanging, state);
        return true;
    }

    /** Whether a shoved block could actually go here: replaceable, and clear of the hull that shoved it. */
    private static boolean vacant(final ServerLevel level, final BoundingBox3dc bounds, final BlockPos spot) {
        // Inside the hull is not somewhere a block can go: it would be crushed again on the next pass, which
        // is a block teleporting under the very thing that pushed it out. Brushing the hull is not good
        // enough either - the hull is moving, and a spot it is against now is a spot it is over next tick.
        if (spot.getX() + 1 > bounds.minX() - SHOVE_CLEARANCE && spot.getX() < bounds.maxX() + SHOVE_CLEARANCE
                && spot.getY() + 1 > bounds.minY() - SHOVE_CLEARANCE
                && spot.getY() < bounds.maxY() + SHOVE_CLEARANCE
                && spot.getZ() + 1 > bounds.minZ() - SHOVE_CLEARANCE
                && spot.getZ() < bounds.maxZ() + SHOVE_CLEARANCE) {
            return false;
        }
        return level.getBlockState(spot).canBeReplaced();
    }

    /**
     * Whether the spot is further from the hull than the block is now, measured flat.
     *
     * <p>The search tries the two directions either side of the outward one as well, and those are square to
     * it - so on their own they let a block slide along the hull rather than out from under it. A block that
     * only ever moves sideways is a block the next pass finds in the way again from the other side.
     */
    private static boolean outward(final BoundingBox3dc bounds, final BlockPos pos, final BlockPos spot) {
        final double centreX = (bounds.minX() + bounds.maxX()) * 0.5;
        final double centreZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        final double fromX = pos.getX() + 0.5 - centreX;
        final double fromZ = pos.getZ() + 0.5 - centreZ;
        final double toX = spot.getX() + 0.5 - centreX;
        final double toZ = spot.getZ() + 0.5 - centreZ;
        return toX * toX + toZ * toZ > fromX * fromX + fromZ * fromZ;
    }

    /**
     * Moves a block out of the hull's way, and puts both ends on cooldown.
     *
     * <p>The cooldown is what stops a shove becoming a loop: without it the hull meets the block again in its
     * new spot next tick and shoves it back, and the pair trade places twenty times a second.
     */
    private static void move(final ServerLevel level,
                             final BlockPos from,
                             final BlockPos to,
                             final BlockState state) {
        level.setBlock(to, state, Block.UPDATE_ALL);
        level.removeBlock(from, false);

        final long free = level.getGameTime() + SHOVE_COOLDOWN;
        SHOVED.put(from.asLong(), free);
        SHOVED.put(to.asLong(), free);
    }

    /** The hull's mass, or zero when Sable's tracker has nothing usable. */
    private static double massOf(final ServerSubLevel subLevel) {
        final MassData mass = subLevel.getMassTracker();
        return mass == null || mass.isInvalid() ? 0.0 : mass.getMass();
    }

    /**
     * Whether the hull passes through this block during the window the rewind covers.
     *
     * <p>Walked in the hull's own frame rather than the world's. Only the hull's position changes over the
     * window, not its orientation, so the whole rewind is one straight line of equal steps once the block has
     * been carried across - which turns a pose transform per step into a vector add per step.
     *
     * <p>The bounds test in front of it is what actually pays, though. Most of a swept slab is nowhere near
     * the hull: a sphere fills barely three quarters of the box drawn round it, and the corners of that box
     * were each costing a full rewind to conclude nothing. One comparison against the plot answers those.
     *
     * <p>Each step is charged to the budget as it is taken, because a step is what the pass actually costs -
     * nine block reads against a block read for walking the slab. Charging only for the walk let one pass run
     * a million probes inside a single tick, and a tick that takes most of a second is a boulder falling in
     * slow motion. Charging up front instead would spend the whole budget on ground the hull never nears.
     */
    private static boolean willOccupy(final ServerSubLevel subLevel,
                                      final Vector3dc localStep,
                                      final Vector3d scratch,
                                      final BlockPos pos,
                                      final int steps,
                                      final double clip,
                                      final Budget budget) {
        subLevel.logicalPose().transformPositionInverse(
                scratch.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), scratch);

        final long range = clipSteps(subLevel.getPlot().getBoundingBox(), scratch, localStep, steps, clip);
        final int first = (int) (range >> 32);
        final int last = (int) range;
        if (first > last) {
            return false;
        }

        scratch.add(localStep.x() * first, localStep.y() * first, localStep.z() * first);
        for (int step = first; step <= last; step++) {
            budget.spend();
            if (sweepsLocal(scratch, clip)) {
                return true;
            }
            // The deadline is asked here and not only in the walk outside, because this is where the tick
            // actually goes: one block of the walk is one read, and one block of this is a hundred probes.
            if (overtime()) {
                return false;
            }
            scratch.add(localStep);
        }
        return false;
    }

    /**
     * Takes the sides of the hole out with the block in the middle of it.
     *
     * <p>The pass in front of this one asks whether the hull passes through a block and answers a yes or a
     * no, which is the right question for something arriving slowly and the wrong one for something arriving
     * fast: at speed the swept path is sampled thinly enough that a block the hull only grazes reads as a
     * miss, and the same speed is what makes that block's survival look absurd. Both are answered by widening
     * the hole rather than by sampling it more finely - the cheap reading and the physical one point the
     * same way.
     *
     * <p>Only sideways, because forwards is next tick's problem and it will be swept then anyway. And at a
     * share of the impact rather than all of it, so this stays a property of what is being torn through: soil
     * beside the path goes with it and stone beside the path does not.
     */
    private static int shearWalls(final ServerLevel level,
                                  final BlockPos pos,
                                  final int axis,
                                  final BlockPos.MutableBlockPos wall,
                                  final Vector3d origin,
                                  final Vector3d velocity,
                                  final double speed,
                                  final double wallSpeed,
                                  final double massFactor,
                                  final Budget budget) {
        int sheared = 0;

        for (int other = 0; other < 3; other++) {
            if (other == axis) {
                continue;
            }
            for (int sign = -1; sign <= 1; sign += 2) {
                if (budget.cleared >= budget.maxCleared || overtime()) {
                    return sheared;
                }

                wall.set(pos.getX() + (other == 0 ? sign : 0),
                        pos.getY() + (other == 1 ? sign : 0),
                        pos.getZ() + (other == 2 ? sign : 0));

                final BlockState state = level.getBlockState(wall);
                if (state.isAir()) {
                    continue;
                }
                final BlockProfile profile = BlockProfile.of(level, wall, state);
                if (profile.indestructible() || profile.passable()) {
                    continue;
                }
                final ImpactResolver.Side face = profile.side(massFactor, 1.0);
                if (!ImpactResolver.shouldBreak(wallSpeed, face.breakSpeed(), face.indestructible(), false)) {
                    continue;
                }

                origin.set(velocity).mul(-0.5 / speed)
                        .add(wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() + 0.5);
                BlockScatter.shatter(level, wall, state, origin, wallSpeed, profile.resistance());
                budget.cleared++;
                budget.spendBreak();
                sheared++;
            }
        }

        return sheared;
    }

    /** How far past its break speed the block was hit, which is how fast it accumulates damage. */
    private static double overshoot(final double speed, final ImpactResolver.Side side) {
        final double breakSpeed = side.breakSpeed();
        return breakSpeed <= 0.0 ? Double.MAX_VALUE : Math.abs(speed) / breakSpeed;
    }

    /**
     * The axis the hull is mostly travelling along, as 0, 1 or 2.
     *
     * <p>Carving sweeps one axis rather than three. A hull moving diagonally is still going somewhere in
     * particular, and the slab is grown along the other two axes to cover the drift - see
     * {@link #sweptRegion} - so picking one costs coverage nothing and costs two thirds less to scan.
     */
    private static int dominantAxis(final Vector3dc velocity) {
        final double x = Math.abs(velocity.x());
        final double y = Math.abs(velocity.y());
        final double z = Math.abs(velocity.z());
        if (x >= y) {
            return x >= z ? 0 : 2;
        }
        return y >= z ? 1 : 2;
    }

    /**
     * Which of the rewind's steps land near enough to the blocks the sub-level is built out of to be worth
     * asking about, as an inclusive pair packed high half first. A first above a last means none of them.
     *
     * <p>The rewind is a straight line of equal steps, so the stretch of it that crosses the plot is three
     * divisions away - and outside that stretch there is nothing to find, because there is nothing there. A
     * yes-or-no answer for the line as a whole, which is what this replaced, kept every step of a line that
     * grazed a corner: a fast hull steps ninety-six times per block, and the great majority of those were
     * being spent to the side of a plot the line only barely reaches.
     */
    private static long clipSteps(final BoundingBox3ic plot,
                                  final Vector3dc from,
                                  final Vector3dc step,
                                  final int steps,
                                  final double clip) {
        double low = 1.0;
        double high = steps;

        // Plot bounds are inclusive block coordinates, so the far face is one block past the maximum.
        for (int axis = 0; axis < 3; axis++) {
            final double origin = from.get(axis);
            final double along = step.get(axis);
            final double near = (axis == 0 ? plot.minX() : axis == 1 ? plot.minY() : plot.minZ()) - clip;
            final double far = (axis == 0 ? plot.maxX() : axis == 1 ? plot.maxY() : plot.maxZ())
                    + 1.0 + clip;

            if (Math.abs(along) < 1.0e-9) {
                if (origin < near || origin > far) {
                    return EMPTY_RANGE;
                }
                continue;
            }
            final double entry = (near - origin) / along;
            final double exit = (far - origin) / along;
            low = Math.max(low, Math.min(entry, exit));
            high = Math.min(high, Math.max(entry, exit));
            if (low > high) {
                return EMPTY_RANGE;
            }
        }

        return ((long) (int) Math.ceil(low) << 32) | ((int) Math.floor(high) & 0xFFFFFFFFL);
    }

    /**
     * Clears grass, flowers and the like out of the faces the hull is driving into.
     *
     * <p>Purely cosmetic - none of it would stop anything - and that is the point: without it a build taxiing
     * across a meadow drags a lawn along with it, because Sable gives these no collider and they simply pass
     * through. Only the leading faces are looked at, so the cost follows the swept area rather than the hull.
     */
    private static void sweepSoft(final ServerLevel level,
                                  final ServerSubLevel subLevel,
                                  final Vector3d velocity,
                                  final double speed,
                                  final int interval,
                                  final int maxCleared,
                                  final boolean drop) {
        final BoundingBox3dc bounds = subLevel.boundingBox();
        final Budget budget = new Budget(maxCleared);
        final Vector3d local = new Vector3d();

        // Only the faces the contraption is actually driving into can meet anything new, so the cost stays
        // proportional to the swept area rather than to the whole hull volume.
        for (int axis = 0; axis < 3; axis++) {
            final double component = velocity.get(axis);
            if (Math.abs(component) < 0.25 * speed) {
                continue;
            }

            final Slab slab = leadingSlab(bounds, axis, component, slabThickness(component, interval));
            forEachBlock(level, budget, slab, (cursor, state) -> {
                final BlockProfile profile = BlockProfile.of(level, cursor, state);
                if (profile.indestructible() || !profile.soft()) {
                    return;
                }
                if (occupies(subLevel, local.set(
                        cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5))) {
                    BlockScatter.clear(level, cursor, drop);
                    budget.cleared++;
                }
            });

            if (budget.exhausted()) {
                return;
            }
        }
    }

    /**
     * Clears or shoves aside the terrain a hull has ended up inside of.
     *
     * <p>{@code deep} is the second attempt, for a hull the centre test could not free. It counts any shared
     * space as an overlap rather than only a block centre swallowed whole, and it ignores everything below the
     * hull's underside - that is the ground it is standing on, and a test wide enough to catch a genuine wedge
     * is also wide enough to start eating a landing pad.
     *
     * <p>{@code desperate} is the third and last, and it changes which side is expected to give way. See
     * {@code grindStuckTicks}.
     */
    private static int sweepOverlaps(final ServerLevel level,
                                     final ServerSubLevel subLevel,
                                     final BoundingBox3dc bounds,
                                     final int maxCleared,
                                     final boolean drop,
                                     final boolean deep,
                                     final boolean desperate) {
        final Budget budget = new Budget(maxCleared);
        final Vector3d local = new Vector3d();
        final boolean displace = ImpactConfig.DISPLACE_OVERLAPS.get();
        final boolean grind = desperate && ImpactConfig.tuning().breakContraptionBlocks();
        final BlockPos.MutableBlockPos plot = new BlockPos.MutableBlockPos();
        final double centreX = (bounds.minX() + bounds.maxX()) * 0.5;
        final double centreY = (bounds.minY() + bounds.maxY()) * 0.5;
        final double centreZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        final double floor = bounds.minY();

        forEachBlock(level, budget, new Slab(
                (int) Math.floor(bounds.minX()), (int) Math.floor(bounds.maxX()),
                (int) Math.floor(bounds.minY()), (int) Math.floor(bounds.maxY()),
                (int) Math.floor(bounds.minZ()), (int) Math.floor(bounds.maxZ())), (cursor, state) -> {
            final BlockProfile profile = BlockProfile.of(level, cursor, state);
            if (deep && cursor.getY() + 0.5 < floor) {
                return;
            }
            // Nothing is ever wedged in a lake, and draining one around a hull that stopped there would be
            // the pass answering a problem it does not have.
            if (profile.fluid()) {
                return;
            }
            if (profile.indestructible()) {
                if (grind && occupies(subLevel, local.set(
                        cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5))
                        && grindHull(level, plot, drop)) {
                    budget.cleared++;
                }
                return;
            }
            if (!overlaps(subLevel, local.set(
                    cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5), deep)) {
                return;
            }
            if (!displace || !shove(level, subLevel, cursor, state, centreX, centreY, centreZ, local, deep)) {
                BlockScatter.clear(level, cursor, drop);
            }
            budget.cleared++;
        });

        return budget.cleared;
    }

    /**
     * Takes the hull block that a piece of immovable terrain is buried in.
     *
     * <p>Which block that is comes free with the test that just said the two are in each other: the probe
     * resolved it to answer, so it is asked here rather than searched for.
     *
     * @return whether anything was destroyed, which is false when the hull is immovable there too - a stone
     *         wedged in bedrock has nothing left to give and is simply left alone.
     */
    private static boolean grindHull(final ServerLevel level,
                                     final BlockPos.MutableBlockPos plot,
                                     final boolean drop) {
        final BlockState hull = PROBE.lastSolid();
        if (hull == null) {
            return false;
        }
        plot.set(PROBE.lastSolidPos());
        if (BlockProfile.of(level, plot, hull).indestructible()) {
            return false;
        }
        BlockScatter.clear(level, plot, drop);
        return true;
    }

    /**
     * A block centre landing inside a solid hull block means half a block of penetration or more, which is far
     * past any slop the solver leaves and cannot be a hull merely resting against it. That is the test worth
     * making first, because it is the one that has no false positives to trade against.
     */
    private static boolean overlaps(final ServerSubLevel subLevel, final Vector3d worldPoint, final boolean deep) {
        return deep ? sweeps(subLevel, worldPoint, WEDGE_CLIP) : occupies(subLevel, worldPoint);
    }

    /**
     * Moves an overlapping block one step into free space instead of destroying it, preferring the direction
     * that leads out of the hull. A block shoved aside costs nothing in particles or debris, and a hull
     * buried in a wall crawls out through terrain that slumps around it rather than one it has eaten.
     */
    private static boolean shove(final ServerLevel level,
                                 final ServerSubLevel subLevel,
                                 final BlockPos pos,
                                 final BlockState state,
                                 final double centreX,
                                 final double centreY,
                                 final double centreZ,
                                 final Vector3d scratch,
                                 final boolean deep) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }

        final Direction outward = Direction.getNearest(
                pos.getX() + 0.5 - centreX, pos.getY() + 0.5 - centreY, pos.getZ() + 0.5 - centreZ);
        final BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt <= 6; attempt++) {
            final Direction direction = attempt == 0 ? outward : Direction.from3DDataValue(attempt - 1);
            if (attempt > 0 && (direction == outward || direction == outward.getOpposite())) {
                continue;
            }

            target.setWithOffset(pos, direction);
            if (!level.getBlockState(target).isAir()
                    || level.isOutsideBuildHeight(target)
                    || overlaps(subLevel, scratch.set(
                            target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5), deep)) {
                continue;
            }

            final BlockPos landed = target.immutable();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(landed, state, Block.UPDATE_CLIENTS);
            return true;
        }

        return false;
    }

    /**
     * The volume the hull is about to sweep into along {@code axis}, which is outside its bounding box, not
     * the leading face of it.
     *
     * <p>The two axes that are not the leading one are widened by their own drift rather than left at the
     * hull's current extent. They have to be: a hull heading mostly along X but also a quarter as fast along
     * Y still crosses a block a second per second that way, and a slab that stops at the hull's present Y
     * simply does not contain the terrain it is about to be in. That terrain is never a candidate, never
     * breaks, and the hull goes through it.
     */
    private static Slab sweptRegion(final BoundingBox3dc bounds,
                                    final int axis,
                                    final Vector3d velocity,
                                    final double window) {
        final int[] min = {
                (int) Math.floor(bounds.minX()),
                (int) Math.floor(bounds.minY()),
                (int) Math.floor(bounds.minZ())};
        final int[] max = {
                (int) Math.floor(bounds.maxX()),
                (int) Math.floor(bounds.maxY()),
                (int) Math.floor(bounds.maxZ())};

        for (int along = 0; along < 3; along++) {
            final double component = velocity.get(along);
            final int drift = Math.clamp((long) Math.ceil(Math.abs(component) * window), 0, MAX_CARVE_REACH);

            if (along != axis) {
                if (component > 0.0) {
                    max[along] += drift;
                } else {
                    min[along] -= drift;
                }
                continue;
            }

            final int reach = Math.clamp(drift + 1, 1, MAX_CARVE_REACH);
            if (component > 0.0) {
                min[along] = max[along];
                max[along] = max[along] + reach;
            } else {
                max[along] = min[along];
                min[along] = min[along] - reach;
            }
        }

        return new Slab(min[0], max[0], min[1], max[1], min[2], max[2]);
    }

    /** The outermost {@code thickness} blocks of the hull's box on the face it is driving into. */
    private static Slab leadingSlab(final BoundingBox3dc bounds,
                                    final int axis,
                                    final double component,
                                    final int thickness) {
        final int minX = (int) Math.floor(bounds.minX());
        final int minY = (int) Math.floor(bounds.minY());
        final int minZ = (int) Math.floor(bounds.minZ());
        final int maxX = (int) Math.floor(bounds.maxX());
        final int maxY = (int) Math.floor(bounds.maxY());
        final int maxZ = (int) Math.floor(bounds.maxZ());
        final boolean forward = component > 0.0;

        return new Slab(
                axis == 0 ? (forward ? maxX - thickness + 1 : minX) : minX,
                axis == 0 ? (forward ? maxX : minX + thickness - 1) : maxX,
                axis == 1 ? (forward ? maxY - thickness + 1 : minY) : minY,
                axis == 1 ? (forward ? maxY : minY + thickness - 1) : maxY,
                axis == 2 ? (forward ? maxZ - thickness + 1 : minZ) : minZ,
                axis == 2 ? (forward ? maxZ : minZ + thickness - 1) : maxZ);
    }

    /**
     * How deep the leading slab has to be to cover everything the hull will cross before it is looked at
     * again, plus one for the blocks it is already in.
     */
    private static int slabThickness(final double component, final int interval) {
        final double travelled = Math.abs(component) * interval / 20.0;
        return Math.clamp((long) Math.ceil(travelled) + 1L, 1, MAX_SLAB_THICKNESS);
    }

    /**
     * Walks the region a section at a time. A hull in flight sits in mostly empty space, and an all-air
     * section answers for its 4096 blocks in one call instead of 4096 chunk lookups.
     */
    private static void forEachBlock(final ServerLevel level,
                                     final Budget budget,
                                     final Slab slab,
                                     final BlockVisitor visitor) {
        final int minY = Math.max(slab.minY(), level.getMinBuildHeight());
        final int maxY = Math.min(slab.maxY(), level.getMaxBuildHeight() - 1);
        if (minY > maxY || slab.minX() > slab.maxX() || slab.minZ() > slab.maxZ()) {
            return;
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int firstSection = level.getSectionIndex(minY);
        final int lastSection = level.getSectionIndex(maxY);

        for (int cx = slab.minX() >> 4; cx <= slab.maxX() >> 4; cx++) {
            for (int cz = slab.minZ() >> 4; cz <= slab.maxZ() >> 4; cz++) {
                final LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }

                final int x0 = Math.max(slab.minX(), cx << 4);
                final int x1 = Math.min(slab.maxX(), (cx << 4) + 15);
                final int z0 = Math.max(slab.minZ(), cz << 4);
                final int z1 = Math.min(slab.maxZ(), (cz << 4) + 15);

                for (int index = firstSection; index <= lastSection; index++) {
                    if (index < 0 || index >= chunk.getSections().length) {
                        continue;
                    }
                    final LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir()) {
                        continue;
                    }

                    final int base = level.getSectionYFromSectionIndex(index) << 4;
                    final int y0 = Math.max(minY, base);
                    final int y1 = Math.min(maxY, base + 15);

                    // A section stores its blocks with x running fastest, so x is the loop that runs
                    // fastest here too.
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                budget.spend();
                                if (budget.exhausted()) {
                                    return;
                                }
                                final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (state.isAir()) {
                                    continue;
                                }
                                cursor.set(x, y, z);
                                visitor.visit(cursor, state);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether a hull block sits dead on this world point.
     *
     * <p>The cheap point test, for the soft sweep where a near miss costs a flower rather than a hole in the
     * ground. Anything that decides whether terrain breaks uses {@link #sweeps} instead.
     *
     * <p>Overwrites {@code worldPoint} with the hull-local coordinate; the callers pass scratch vectors.
     */
    private static boolean occupies(final ServerSubLevel subLevel, final Vector3d worldPoint) {
        subLevel.logicalPose().transformPositionInverse(worldPoint, worldPoint);
        return PROBE.solidAt(worldPoint.x, worldPoint.y, worldPoint.z);
    }

    /**
     * How the hull meets one block: over it, against it, or not at all.
     *
     * <p>Both answers come off one pose transform and share their early outs, because this is the hot path of
     * the crush pass by a wide margin - it runs on every solid block anywhere near a landed build, and the
     * overwhelmingly common answer is that the hull is nowhere near this particular one.
     *
     * <p>Over and against are worth telling apart: a block underneath is carrying the weight and hands it on
     * downwards, while one at the side is being pushed rather than compressed, and takes its load through
     * {@code crushShear} instead.
     *
     * <p>The plot test in front of the probes is what makes the common answer cheap. A column scan covers the
     * whole box drawn round the hull plus a step of travel, and most of that box is not hull at all, so ten
     * probes were being spent to conclude nothing about blocks the build is nowhere near.
     */
    private static int contactAt(final ServerSubLevel subLevel,
                                 final BoundingBox3ic plot,
                                 final Vector3d scratch,
                                 final double wx, final double wy, final double wz) {
        scratch.set(wx, wy, wz);
        subLevel.logicalPose().transformPositionInverse(scratch, scratch);
        final double px = scratch.x;
        final double py = scratch.y;
        final double pz = scratch.z;

        // Plot bounds are inclusive block coordinates, so the far face is one block past the maximum.
        if (px < plot.minX() - TOUCH_REACH || px > plot.maxX() + 1.0 + TOUCH_REACH
                || py < plot.minY() - TOUCH_REACH || py > plot.maxY() + 1.0 + TOUCH_REACH
                || pz < plot.minZ() - TOUCH_REACH || pz > plot.maxZ() + 1.0 + TOUCH_REACH) {
            return CONTACT_NONE;
        }

        if (PROBE.solidAt(px, py + TOUCH_REACH, pz)) {
            return CONTACT_BEARING;
        }
        // A hull that came to rest at an angle meets the top face off centre, and asking only about the point
        // straight up reads every one of those as untouched.
        for (int corner = 0; corner < 4; corner++) {
            if (PROBE.solidAt(
                    px + ((corner & 1) == 0 ? -SUPPORT_CLIP : SUPPORT_CLIP),
                    py + TOUCH_REACH,
                    pz + ((corner & 2) == 0 ? -SUPPORT_CLIP : SUPPORT_CLIP))) {
                return CONTACT_BEARING;
            }
        }

        if (PROBE.solidAt(px, py, pz)
                || PROBE.solidAt(px - TOUCH_REACH, py, pz)
                || PROBE.solidAt(px + TOUCH_REACH, py, pz)
                || PROBE.solidAt(px, py, pz - TOUCH_REACH)
                || PROBE.solidAt(px, py, pz + TOUCH_REACH)) {
            return CONTACT_FLANK;
        }
        return CONTACT_NONE;
    }

    /**
     * Whether the hull sweeps through the block whose centre is {@code worldPoint}, rather than whether that
     * centre lands dead inside a hull block. The distinction is the whole difference between carving and
     * tunnelling: an obsidian rod is one or two blocks across, so most of what it ploughs through it clips off
     * axis, and a point test finds nothing to break there. The rod flies on and only starts breaking where it
     * finally happens to line up, which reads as it having spawned underground.
     */
    private static boolean sweeps(final ServerSubLevel subLevel, final Vector3d worldPoint, final double clip) {
        return sweepsLocal(subLevel.logicalPose().transformPositionInverse(worldPoint, worldPoint), clip);
    }

    /**
     * The same test in the hull's own frame, for callers that have already transformed the point.
     *
     * <p>Nine probes: the centre, then the eight corners of a cube of half-width {@code clip} around it.
     * Corners rather than face centres because a hull edge crossing the block diagonally misses all six faces.
     */
    private static boolean sweepsLocal(final Vector3dc local, final double clip) {
        if (PROBE.solidAt(local.x(), local.y(), local.z())) {
            return true;
        }
        for (int corner = 0; corner < 8; corner++) {
            if (PROBE.solidAt(
                    local.x() + ((corner & 1) == 0 ? -clip : clip),
                    local.y() + ((corner & 2) == 0 ? -clip : clip),
                    local.z() + ((corner & 4) == 0 ? -clip : clip))) {
                return true;
            }
        }
        return false;
    }

    /**
     * What {@link #forEachBlock} calls per block.
     *
     * <p>The cursor is reused between calls, so anything kept past the call has to be copied.
     */
    @FunctionalInterface
    private interface BlockVisitor {
        void visit(BlockPos.MutableBlockPos pos, BlockState state);
    }

    /** A block-aligned box to scan, inclusive at both ends on all three axes. */
    private record Slab(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    /**
     * What is remembered about one hull between ticks: where it was, when it last actually moved, and how
     * long it has earned the right to be left alone for.
     *
     * <p>Keyed by runtime id in {@code TRACKED} and dropped after {@link #FORGET_TICKS} without being seen.
     */
    private static final class Tracked {
        private long lastMoved;
        private long lastSeen;
        private long quietUntil;
        private double x;
        private double y;
        private double z;

        private Tracked(final long now, final double x, final double y, final double z) {
            this.lastMoved = now;
            this.lastSeen = now;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** How far the hull is from where it was last recorded, which is what {@link #MOVED} is compared to. */
        private double displacedFrom(final double x, final double y, final double z) {
            final double dx = x - this.x;
            final double dy = y - this.y;
            final double dz = z - this.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** Records a new position and resets the stillness clock. */
        private void moved(final long now, final double x, final double y, final double z) {
            this.lastMoved = now;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Carving draws on its own per-tick allowance rather than sharing one with the sweeps. Sharing sounds
     * fairer and is not: the sweeps are cosmetic and carving is the only thing standing between a fast hull
     * and open terrain, so a tick spent mowing grass must never be the reason something tunnels.
     */
    private static final class Budget {
        private final int maxCleared;
        private final boolean carving;
        private int scanned;
        private int cleared;

        private Budget(final int maxCleared) {
            this(maxCleared, false);
        }

        private Budget(final int maxCleared, final boolean carving) {
            this.maxCleared = maxCleared;
            this.carving = carving;
        }

        /** Charges one block read. */
        private void spend() {
            spend(1);
        }

        /**
         * Charges {@code amount} block reads to this budget and to the tick's.
         *
         * <p>Both, because the per-sweep limit stops one hull eating the tick and the shared counters stop
         * fifty hulls doing it between them. {@code workSinceClock} is what decides when the clock is next
         * worth reading at all.
         */
        private void spend(final int amount) {
            this.scanned += amount;
            workSinceClock += amount;
            if (this.carving) {
                carvedThisTick += amount;
            } else {
                sweptThisTick += amount;
            }
        }

        /**
         * Charges one block destruction, and makes the next ask read the clock rather than count towards it.
         *
         * <p>A stride is a bargain struck between the cost of reading the clock and the cost of the work
         * being measured, and a break is on the wrong side of it: it is the one operation here that is
         * itself worth more than the reading, sometimes by three orders of magnitude, because what a
         * destroyBlock ends up doing belongs to whatever else in the pack has an opinion about that block.
         * So it is not averaged in with the block reads - it is measured on its own, and the pass can
         * overrun by one of them rather than by a strideful.
         */
        private void spendBreak() {
            spend(BREAK_COST);
            workSinceClock = CLOCK_STRIDE;
        }

        /**
         * Whether this sweep should stop: it has cleared its quota, read too much, run the tick's allowance
         * out, or the tick itself is over time.
         */
        private boolean exhausted() {
            return this.cleared >= this.maxCleared
                    || this.scanned > MAX_BLOCKS_SCANNED
                    || (this.carving ? carvedThisTick : sweptThisTick) > scanBudget
                    || overtime();
        }
    }
}
