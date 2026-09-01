package org.restor.create_aeronautics_impact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * The last chunk a scanning pass looked at, kept.
 *
 * <p>Waves and collapse fronts read blocks by the thousand and read them in walks, so all but a few of those
 * reads land in the chunk the read before them landed in. Going through the level for each meant a chunk
 * source lookup and a map probe per block to be handed back the object we had a moment ago; remembering it
 * turns the common case into a comparison of two longs.
 *
 * <p>Holding a chunk across a break pass is safe because a chunk answers for its own current contents: the
 * blocks this mod is removing are removed <em>through</em> this object, so nothing here can go stale within
 * a tick. What it must not do is outlive the pass, which is why every user of this owns one rather than
 * sharing a static.
 */
public final class ChunkCache {

    private @Nullable ServerLevel level;
    private long chunkKey = Long.MIN_VALUE;
    private @Nullable LevelChunk chunk;

    /**
     * The block at {@code pos}, or null when its chunk is not loaded.
     *
     * <p>A miss stays a miss. Every caller of this is walking outwards from an impact and may well reach the
     * edge of what is loaded, and asking the level properly would generate terrain from inside a break pass.
     */
    public @Nullable BlockState stateIfLoaded(final ServerLevel forLevel, final BlockPos pos) {
        final int x = pos.getX() >> 4;
        final int z = pos.getZ() >> 4;
        if (!ImpactConfig.tuning().cacheChunks()) {
            final LevelChunk direct = forLevel.getChunkSource().getChunkNow(x, z);
            return direct == null ? null : direct.getBlockState(pos);
        }

        final long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        if (this.chunk == null || key != this.chunkKey || forLevel != this.level) {
            this.level = forLevel;
            this.chunkKey = key;
            this.chunk = forLevel.getChunkSource().getChunkNow(x, z);
        }
        return this.chunk == null ? null : this.chunk.getBlockState(pos);
    }

    /** Drops what is held, for a user that is being parked between ticks rather than used again at once. */
    public void forget() {
        this.level = null;
        this.chunk = null;
        this.chunkKey = Long.MIN_VALUE;
    }
}
