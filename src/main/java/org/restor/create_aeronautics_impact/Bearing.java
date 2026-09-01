package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runs {@link LoadPath} over the world, so that what a wreck leaves standing is only what can stand.
 *
 * <p>The crush pass in {@link HullSweeper} answers one question well: how hard is this hull pressing on the
 * blocks it is touching. It answers it by spreading that pressure downwards and outwards a few blocks and
 * letting it fade, which is right for the ground directly under a landing and wrong for everything a
 * structure is made of. A gantry on two legs takes the whole weight through the legs however far away they
 * are, and the crush pass has never heard of them; the deck between them, once it loses its middle, stays up
 * on nothing at all, because nothing ever asked what was holding it.
 *
 * <p>So this is the second question, asked about the world instead of about the hull. Blocks are pulled into
 * a box, the load a build is resting on them with is added at the contact, {@link LoadPath} routes every
 * block's weight to whatever is holding it, and two answers come back: blocks carrying more than they can,
 * and blocks being carried by nothing. Both give way, the box is solved again with them gone, and the round
 * repeats until nothing more falls. That is the equilibrium - the legs buckle under the load put on them, the
 * deck they were holding follows them down, and it happens in the same breath rather than as a drizzle spread
 * over the next minute.
 *
 * <p>Work is queued by region rather than by block, because a crater is thousands of breaks in a handful of
 * sixteen-block cubes and solving the cube once is the whole point. A region is only looked at again if the
 * last look broke something in it, which is what stops a settled wreck from being re-solved forever, and a
 * break re-queues its own region, which is what lets a failure climb out of the cube it started in.
 *
 * <p>Terrain only. Everything that queues a region here - the crush pass, a terrain shatter - deals in world
 * blocks, so nothing ever names a coordinate out in the plot grid, and contraption against contraption stays
 * where it belongs, with the physics engine that is already solving it.
 */
public final class Bearing {

    private static final Map<ServerLevel, Bearing> LEVELS = new WeakHashMap<>();

    private static final LoadPath PATH = new LoadPath();
    private static final LongArrayList FALLING = new LongArrayList();
    private static final Vector3d FROM_ABOVE = new Vector3d();

    /** What a block can carry when nothing is ever going to break it. */
    private static final double IMMOVABLE = Double.MAX_VALUE;

    private final LongOpenHashSet pending = new LongOpenHashSet();
    private final Long2DoubleOpenHashMap resting = new Long2DoubleOpenHashMap();
    private final Long2DoubleOpenHashMap staging = new Long2DoubleOpenHashMap();
    private final ChunkCache chunks = new ChunkCache();
    private long nextSolve;

    private Bearing() {
        this.resting.defaultReturnValue(0.0);
        this.staging.defaultReturnValue(0.0);
    }

    /**
     * Notes that a block has changed, so the region holding it is worth solving again.
     *
     * <p>Called for every terrain block this mod destroys, from wherever it destroys it. The block itself is
     * not interesting - what is interesting is that whatever was standing on it is now standing on one block
     * less, and only the solve knows whether that mattered.
     */
    public static void disturb(final ServerLevel level, final BlockPos pos) {
        if (!ImpactConfig.SPEC.isLoaded()) {
            return;
        }
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        if (!tuning.bearing()) {
            return;
        }
        final Bearing bearing = LEVELS.computeIfAbsent(level, ignored -> new Bearing());
        if (bearing.pending.size() < tuning.bearingMaxRegions()) {
            bearing.pending.add(regionOf(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /**
     * Records how hard a build is pressing on one world block, whether or not it is moving.
     *
     * <p>Seeded from the crush pass, which has already worked out the build's mass, the blocks carrying it
     * and how much of it each of them is taking. That number is a weight standing on the ground, and a weight
     * standing on the ground is exactly what the routing wants - so a build simply parked on a roof loads the
     * walls under it and, if they are not up to it, brings them down without ever having to hit anything.
     *
     * <p>Kept in a staging map that the next solve takes over wholesale, because a build that has flown off
     * is no longer pressing on anything and the reading has to expire with it rather than linger.
     */
    public static void rest(final ServerLevel level, final long block, final double load) {
        if (!(load > 0.0) || !ImpactConfig.SPEC.isLoaded()) {
            return;
        }
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        if (!tuning.bearing() || !tuning.bearingRest()) {
            return;
        }
        final Bearing bearing = LEVELS.computeIfAbsent(level, ignored -> new Bearing());
        bearing.staging.addTo(block, load);
        if (bearing.pending.size() < tuning.bearingMaxRegions()) {
            bearing.pending.add(regionOf(BlockPos.getX(block), BlockPos.getY(block), BlockPos.getZ(block)));
        }
    }

    /**
     * Solves as many queued regions as the tick can pay for.
     *
     * @return how many blocks fell, for the tick's own count.
     */
    public static int tick(final ServerLevel level, final ImpactConfig.Tuning tuning, final long deadline) {
        final Bearing bearing = LEVELS.get(level);
        if (bearing == null) {
            return 0;
        }
        if (!tuning.bearing()) {
            bearing.pending.clear();
            bearing.staging.clear();
            bearing.resting.clear();
            return 0;
        }

        if (!bearing.staging.isEmpty() || !bearing.resting.isEmpty()) {
            bearing.resting.clear();
            bearing.resting.putAll(bearing.staging);
            bearing.staging.clear();
        }

        final long now = level.getGameTime();
        if (now < bearing.nextSolve || bearing.pending.isEmpty()) {
            return 0;
        }
        bearing.nextSolve = now + Math.max(1, tuning.bearingInterval());

        int budget = tuning.bearingMaxPerTick();
        int broken = 0;
        for (int region = 0; region < tuning.bearingRegionsPerTick() && budget > 0; region++) {
            if (bearing.pending.isEmpty() || System.nanoTime() > deadline) {
                break;
            }
            final long key = bearing.pending.iterator().nextLong();
            bearing.pending.remove(key);

            final int fell = bearing.settle(level, key, tuning, budget, deadline);
            broken += fell;
            budget -= fell;
        }

        bearing.chunks.forget();
        return broken;
    }

    private static long regionOf(final int x, final int y, final int z) {
        return BlockPos.asLong(x >> 4, y >> 4, z >> 4);
    }

    /**
     * Fills the box around one region, then breaks and re-solves until nothing more gives.
     *
     * <p>The box is the region plus a margin, and the margin is not symmetric: it reaches much further down
     * than up, because what holds a structure is underneath it and the legs are the point. Whatever is left
     * standing on the walls of the box is anchored there rather than dropped - the structure carries on
     * outside and the box cannot see how, so the conservative reading is the only honest one.
     */
    private int settle(final ServerLevel level,
                       final long region,
                       final ImpactConfig.Tuning tuning,
                       final int budget,
                       final long deadline) {
        final int rx = BlockPos.getX(region);
        final int ry = BlockPos.getY(region);
        final int rz = BlockPos.getZ(region);

        final int floor = Math.max(level.getMinBuildHeight(), (ry << 4) - tuning.bearingDrop());
        final int roof = Math.min(level.getMaxBuildHeight() - 1, (ry << 4) + 15 + tuning.bearingRise());
        if (roof < floor) {
            return 0;
        }

        final int margin = tuning.bearingMargin();
        final int x0 = (rx << 4) - margin;
        final int z0 = (rz << 4) - margin;
        final int sx = 16 + margin * 2;
        final int sz = 16 + margin * 2;
        final int sy = roof - floor + 1;

        PATH.reset(sx, sy, sz);

        final double weight = tuning.bearingBlockWeight();
        final double scale = tuning.bearingPressureScale();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int loose = 0;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    cursor.set(x0 + x, floor + y, z0 + z);
                    final BlockState state = this.chunks.stateIfLoaded(level, cursor);
                    final boolean edge = y == 0 || x == 0 || x == sx - 1 || z == 0 || z == sz - 1;

                    // Nothing is known about a chunk that is not here, so it is treated as ground: a wreck at
                    // the edge of what is loaded should not come down because the view ran out.
                    if (state == null) {
                        PATH.set(PATH.index(x, y, z), 0.0, IMMOVABLE, true);
                        continue;
                    }

                    final BlockProfile profile = BlockProfile.of(level, cursor, state);
                    if (state.isAir() || profile.passable()) {
                        continue;
                    }

                    if (profile.indestructible() || edge) {
                        PATH.set(PATH.index(x, y, z), weight, IMMOVABLE, true);
                        continue;
                    }
                    PATH.set(PATH.index(x, y, z), weight,
                            ImpactResolver.crushStrength(profile.resistance(), scale), false);
                    loose++;
                }
            }
        }

        if (loose == 0) {
            return 0;
        }

        for (final Long2DoubleMap.Entry press : this.resting.long2DoubleEntrySet()) {
            final long key = press.getLongKey();
            final int x = BlockPos.getX(key) - x0;
            final int y = BlockPos.getY(key) - floor;
            final int z = BlockPos.getZ(key) - z0;
            if (x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz) {
                PATH.press(PATH.index(x, y, z), press.getDoubleValue());
            }
        }

        final int span = tuning.bearingSpan();
        final boolean hanging = tuning.bearingHanging();
        int left = budget;
        int broken = 0;

        for (int round = 0; round < tuning.bearingRounds() && left > 0; round++) {
            PATH.solve(span);

            FALLING.clear();
            for (int y = 0; y < sy && FALLING.size() < left; y++) {
                for (int z = 0; z < sz && FALLING.size() < left; z++) {
                    for (int x = 0; x < sx && FALLING.size() < left; x++) {
                        final int at = PATH.index(x, y, z);
                        if (!PATH.solid(at) || PATH.capacity(at) >= IMMOVABLE) {
                            continue;
                        }
                        if (PATH.overload(at) > 0.0 || (hanging && !PATH.carried(at))) {
                            FALLING.add(BlockPos.asLong(x0 + x, floor + y, z0 + z));
                        }
                    }
                }
            }
            if (FALLING.isEmpty()) {
                break;
            }

            for (int index = 0; index < FALLING.size(); index++) {
                final long key = FALLING.getLong(index);
                PATH.remove(PATH.index(BlockPos.getX(key) - x0,
                        BlockPos.getY(key) - floor, BlockPos.getZ(key) - z0));
                if (fell(level, BlockPos.of(key), tuning)) {
                    broken++;
                    left--;
                }
            }

            // Whatever came down had a region of its own, and the next tick is where it is looked at - so a
            // failure walks out of this cube instead of stopping at its wall.
            for (int index = 0; index < FALLING.size(); index++) {
                final long key = FALLING.getLong(index);
                if (this.pending.size() < tuning.bearingMaxRegions()) {
                    this.pending.add(regionOf(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key)));
                }
            }

            if (System.nanoTime() > deadline) {
                break;
            }
        }

        return broken;
    }

    /**
     * Lets one block go.
     *
     * <p>Handed to the same debris path as everything else, with the impact placed directly overhead and no
     * speed behind it, so that the escape direction comes out downwards and the piece drops and heaps rather
     * than being flung. A structure coming apart under its own weight has no direction to be thrown in.
     */
    private boolean fell(final ServerLevel level, final BlockPos at, final ImpactConfig.Tuning tuning) {
        final BlockState state = level.getBlockState(at);
        if (state.isAir()) {
            return false;
        }
        final BlockProfile profile = BlockProfile.of(level, at, state);
        if (profile.indestructible() || profile.passable()) {
            return false;
        }

        FROM_ABOVE.set(at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5);
        BlockScatter.shatter(level, at, state, FROM_ABOVE, tuning.bearingFallSpeed(), profile.resistance());
        CrackTracker.spall(level, at, true, tuning);
        return true;
    }
}
