package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Asks whether a point in a sub-level's plot is inside one of its blocks, which is the single most repeated
 * question in the sweeper: carving alone asks it up to nine times per step of a rewind, for every step, for
 * every candidate block in the slab.
 *
 * <p>Doing that through {@code ServerLevel.getBlockState} allocated a {@link BlockPos} per ask and walked the
 * chunk source every time - and worse, that path will generate a missing chunk on the spot, so the one tick
 * the plot is not resident costs a worldgen instead of a miss. Here the position is mutable, the last few
 * chunks are held, and a repeated ask is answered from a table of the blocks already resolved this tick.
 *
 * <p>That table earns its keep because of how the asks arrive rather than how many there are. A rewind probes
 * nine corners of a cube half a block wide, then steps two thirds of a block and probes nine more - so
 * consecutive asks land in the same handful of blocks over and over, and remembering only the previous one
 * caught almost none of it.
 */
final class PlotProbe {

    private static final int CHUNKS = 4;

    /**
      * Sized to hold a whole hull rather than a corner of one. A build twenty blocks across is eight thousand
      * blocks and the questions arrive from all over it, so a table small enough to wrap kept evicting the
      * answers it was about to be asked for again. This is a hundred kilobytes and it is asked millions of
      * times a tick.
      */
    private static final int MEMO = 16384;

    // No real block packs to this, so it cannot collide with a genuine entry.
    private static final long MEMO_EMPTY = Long.MIN_VALUE;

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final long[] chunkKeys = new long[CHUNKS];
    private final LevelChunk[] chunks = new LevelChunk[CHUNKS];
    private final long[] memoKeys = new long[MEMO];
    private final BlockState[] memoState = new BlockState[MEMO];

    private ServerLevel level;
    private int minY;
    private int maxY;

    private BlockState lastSolid;
    private long lastSolidPos;

    /** Held state is only valid for as long as no chunk can have been unloaded, so it lasts one tick. */
    void reset(final ServerLevel level) {
        this.level = level;
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight() - 1;
        Arrays.fill(this.chunkKeys, Long.MIN_VALUE);
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.memoKeys, MEMO_EMPTY);
        Arrays.fill(this.memoState, null);
        this.lastSolid = null;
    }

    /**
     * The hull block that answered the last positive {@link #solidAt}, and where it sits.
     *
     * <p>Callers above this ask whether the hull is somewhere and then have to decide which of the two
     * materials gives way, and until now only one of them was known: the terrain block was in hand and the
     * hull was a yes or a no. The answer was already resolved to make the yes, so handing it back costs
     * nothing and turns every one-sided test into a contest.
     */
    @Nullable BlockState lastSolid() {
        return this.lastSolid;
    }

    long lastSolidPos() {
        return this.lastSolidPos;
    }

    boolean solidAt(final double x, final double y, final double z) {
        final int blockY = Mth.floor(y);
        if (this.level == null || blockY < this.minY || blockY > this.maxY) {
            return false;
        }

        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final long key = BlockPos.asLong(blockX, blockY, blockZ);
        final int slot = (int) HashCommon.mix(key) & (MEMO - 1);

        final BlockState state;
        if (this.memoKeys[slot] == key) {
            state = this.memoState[slot];
        } else {
            state = resolve(blockX, blockY, blockZ);
            this.memoKeys[slot] = key;
            this.memoState[slot] = state;
        }

        if (state == null) {
            return false;
        }
        this.lastSolid = state;
        this.lastSolidPos = key;
        return true;
    }

    /**
     * The block at a plot coordinate, or null where there is nothing solid to hit.
     *
     * <p>Null covers three cases the callers treat the same way: an unloaded chunk, air, and anything the
     * profile calls passable. The chunk is fetched with {@code getChunkNow} because this is reached from the
     * physics thread, where loading one would be both a stall and a place to deadlock.
     */
    private @Nullable BlockState resolve(final int x, final int y, final int z) {
        final LevelChunk chunk = chunkAt(x >> 4, z >> 4);
        if (chunk == null) {
            return null;
        }

        this.cursor.set(x, y, z);
        final BlockState state = chunk.getBlockState(this.cursor);
        if (state.isAir()) {
            return null;
        }
        return BlockProfile.of(this.level, this.cursor, state).passable() ? null : state;
    }

    /**
     * The chunk, through a small direct-mapped cache.
     *
     * <p>A rewind walks a straight line through one plot, so consecutive probes land in the same chunk over
     * and over. Nulls are cached too - a plot's neighbours are mostly nothing, and looking them up repeatedly
     * to be told so again is the same cost as looking up a real one.
     */
    private @Nullable LevelChunk chunkAt(final int chunkX, final int chunkZ) {
        final long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        final int slot = (int) (key ^ (key >>> 32)) & (CHUNKS - 1);

        if (this.chunkKeys[slot] == key) {
            return this.chunks[slot];
        }

        final LevelChunk chunk = this.level.getChunkSource().getChunkNow(chunkX, chunkZ);
        this.chunkKeys[slot] = key;
        this.chunks[slot] = chunk;
        return chunk;
    }
}
