package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Arrays;

/**
 * Reads what is holding a terrain block in place, so that a block's strength can be part of where it is and
 * not only of what it is made of. The reading is turned into a multiplier by
 * {@link ImpactResolver#support} and {@link ImpactResolver#backed}; everything here is the looking.
 *
 * <p>A block resists by pushing back, and it can only push back as hard as whatever is behind it lets it:
 * take the mountain away and the same stone is a tile that pops out of its frame.
 *
 * <p>Terrain only. A contraption is one rigid body and its blocks really are carrying each other, so there is
 * nothing here for them to lose.
 *
 * <p>Unloaded chunks and the void outside the world count as solid. Nothing should get weaker because the
 * server happened not to be looking.
 */
public final class Backing {

    /**
     * Slots in the memo. A power of two, because the slot is masked out of the hash rather than divided.
     *
     * <p>Open addressing with no probing: a collision overwrites. Both blocks were about to be answered
     * either way, so the cost of a collision is one repeated look rather than a wrong answer.
     */
    private static final int MEMO = 16384;

    private static final long[] KEYS = new long[MEMO];
    private static final double[] HELD = new double[MEMO];
    private static ResourceKey<Level> memoDimension;
    private static long memoTick = Long.MIN_VALUE;

    private Backing() {
    }

    /**
     * Reads the backing of a block struck from a point in world space.
     *
     * <p>The contact sits on the face that was hit, so the way from it to the block's centre is the way the
     * load is going, and the axis it leans on hardest is the one to look along. A contact that lands on the
     * centre itself says nothing about direction and is answered as fully backed.
     */
    public static double of(final ServerLevel level, final BlockPos pos,
                            final double fromX, final double fromY, final double fromZ) {
        final double weight = ImpactConfig.tuning().backingWeight();
        if (weight <= 0.0) {
            return 1.0;
        }

        final double dx = pos.getX() + 0.5 - fromX;
        final double dy = pos.getY() + 0.5 - fromY;
        final double dz = pos.getZ() + 0.5 - fromZ;
        final double ax = Math.abs(dx);
        final double ay = Math.abs(dy);
        final double az = Math.abs(dz);

        final int axis = ax >= ay ? (ax >= az ? 0 : 2) : (ay >= az ? 1 : 2);
        final double along = axis == 0 ? dx : axis == 1 ? dy : dz;
        if (Math.abs(along) < 1.0e-6) {
            return 1.0;
        }
        return read(level, pos, axis, along > 0.0 ? 1 : -1, weight);
    }

    /**
     * The memoised reading for one block and one direction.
     *
     * <p>The key mixes position, axis and sign, so the same block struck from two sides is two answers - it
     * genuinely has different amounts of terrain behind each face.
     */
    private static double read(final ServerLevel level, final BlockPos pos,
                               final int axis, final int sign, final double weight) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        roll(level, tuning.backingMemoTicks());

        final long key = HashCommon.mix(pos.asLong() * 6L + axis * 2L + (sign > 0 ? 1L : 0L));
        final int slot = (int) key & (MEMO - 1);
        if (KEYS[slot] == key) {
            return HELD[slot];
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int reach = tuning.backingReach();
        final double held = ImpactResolver.backed(ImpactResolver.support(
                behind(level, cursor, pos, axis, sign, reach), beside(level, cursor, pos, axis),
                reach, tuning.backingBeside()), weight);
        KEYS[slot] = key;
        HELD[slot] = held;
        return held;
    }

    /**
     * How many solid blocks stand in an unbroken line behind the struck face, up to {@code reach}.
     *
     * <p>This is the number that decides whether something is a wall or a hillside.
     */
    private static int behind(final ServerLevel level, final BlockPos.MutableBlockPos cursor,
                              final BlockPos pos, final int axis, final int sign, final int reach) {
        int count = 0;
        for (int step = 1; step <= reach; step++) {
            offset(cursor, pos, axis, sign * step);
            // A gap ends the count rather than skipping past it: what is behind a hole is not holding
            // anything up in front of it.
            if (!solid(level, cursor)) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * How many of the four blocks around the struck one, on the two axes that are not the load's, are solid.
     *
     * <p>Worth much less than depth, and there for one case: a single block still set in a wall is not
     * free-standing, even with nothing behind it.
     */
    private static int beside(final ServerLevel level, final BlockPos.MutableBlockPos cursor,
                              final BlockPos pos, final int axis) {
        int count = 0;
        for (int other = 0; other < 3; other++) {
            if (other == axis) {
                continue;
            }
            for (int sign = -1; sign <= 1; sign += 2) {
                offset(cursor, pos, other, sign);
                if (solid(level, cursor)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Moves the shared cursor {@code step} blocks along one axis from {@code pos}. */
    private static void offset(final BlockPos.MutableBlockPos cursor, final BlockPos pos,
                               final int axis, final int step) {
        cursor.set(pos.getX() + (axis == 0 ? step : 0),
                pos.getY() + (axis == 1 ? step : 0),
                pos.getZ() + (axis == 2 ? step : 0));
    }

    /**
     * Whether this position counts as holding something up.
     *
     * <p>Deliberately generous at the edges: outside the build height and inside an unloaded chunk both count
     * as solid, because nothing should get weaker on account of the server not having looked. The chunk is
     * fetched with {@code getChunkNow} for the same reason - this runs from the physics thread, and a load
     * here would be both a stall and a place to deadlock.
     */
    private static boolean solid(final ServerLevel level, final BlockPos.MutableBlockPos cursor) {
        if (cursor.getY() < level.getMinBuildHeight() || cursor.getY() >= level.getMaxBuildHeight()) {
            return true;
        }
        final LevelChunk chunk = level.getChunkSource()
                .getChunkNow(cursor.getX() >> 4, cursor.getZ() >> 4);
        if (chunk == null) {
            return true;
        }
        final BlockState state = chunk.getBlockState(cursor);
        if (state.isAir()) {
            return false;
        }
        return !BlockProfile.of(level, cursor, state).passable();
    }

    /**
     * Answers are kept for as long as {@code backingMemoTicks} allows. At one tick, which is the default,
     * that is the tick that produced them and no longer: a hull ploughing a wall asks about the same handful
     * of blocks from thousands of contacts, and the wall it is asking about is being taken apart as it asks.
     */
    private static void roll(final ServerLevel level, final int ticks) {
        final long now = level.getGameTime();
        if (level.dimension() == memoDimension && now >= memoTick && now - memoTick < ticks) {
            return;
        }
        memoTick = now;
        memoDimension = level.dimension();
        Arrays.fill(KEYS, 0L);
    }
}
