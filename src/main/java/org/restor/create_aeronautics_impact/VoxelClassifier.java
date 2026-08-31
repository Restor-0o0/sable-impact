package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * A drop-in replacement for Sable's voxel classification, which every collider remesh runs once per block.
 *
 * <p>Sable's version allocates seven {@code ChunkPos} and six {@code BlockPos} per block just to decide
 * whether a neighbour is in the same chunk, and it resolves solidity through a pair of
 * {@code Int2BooleanOpenHashMap} caches that are neither allocation-free nor safe to touch from more than one
 * thread. Remeshing a single chunk section runs this four thousand times.
 *
 * <p>It also folds in the interior-culling decision. Sable asks whether a block keeps its own voxel and then
 * separately asks whether all six neighbours are solid; those are the same six lookups, so doing them once
 * halves the work as well.
 */
public final class VoxelClassifier {

    private VoxelClassifier() {
    }

    /** The classification, or {@code null} when config is not up yet and Sable should answer instead. */
    public static @Nullable VoxelNeighborhoodState classify(final LevelAccelerator level,
                                                            final BlockPos pos,
                                                            @Nullable final LevelChunk chunk) {
        final long started = ImpactStats.markVoxel();
        try {
            return classified(level, pos, chunk);
        } finally {
            ImpactStats.sinceVoxel(started);
        }
    }

    /**
     * The classification proper.
     *
     * <p>Split from {@link #classify} only so the timing wrapper has a single return to measure.
     */
    private static @Nullable VoxelNeighborhoodState classified(final LevelAccelerator level,
                                                               final BlockPos pos,
                                                               @Nullable final LevelChunk chunk) {
        if (!ImpactConfig.SPEC.isLoaded()) {
            return null;
        }

        final BlockState state = chunk == null ? level.getBlockState(pos) : level.getBlockState(chunk, pos);
        if (VoxelNeighborhoodState.isLiquid(state)) {
            return VoxelNeighborhoodState.CORNER;
        }

        final BlockSubLevelCollisionCallback callback =
                BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        final BlockProfile profile = BlockProfile.of(level, pos, state);
        final boolean cullable = callback == ImpactCallback.INSTANCE
                && !profile.fragile()
                && ImpactConfig.cullInteriorVoxels();

        if (callback != null && !cullable) {
            return VoxelNeighborhoodState.CORNER;
        }
        if (!profile.voxelSolid()) {
            return VoxelNeighborhoodState.EMPTY;
        }
        if (!profile.voxelFullBlock()) {
            return VoxelNeighborhoodState.CORNER;
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean allSolid = true;
        boolean cornerSolid = true;
        int bothSidesCount = 0;

        for (int axis = 0; axis < 3; axis++) {
            final boolean negative = solidFullBlock(level, cursor, pos, axis, -1);
            final boolean positive = solidFullBlock(level, cursor, pos, axis, 1);

            if (!negative || !positive) {
                allSolid = false;
            }
            if (negative && positive) {
                cornerSolid = false;
                bothSidesCount++;
            }
        }

        if (allSolid) {
            return VoxelNeighborhoodState.INTERIOR;
        }
        // Buried is the only case a claimed block gives its voxel up in; anything with an exposed side is the
        // first thing a hull can touch and has to be met individually.
        if (cullable) {
            return VoxelNeighborhoodState.CORNER;
        }
        if (bothSidesCount == 1) {
            return VoxelNeighborhoodState.EDGE;
        }
        return cornerSolid ? VoxelNeighborhoodState.CORNER : VoxelNeighborhoodState.FACE;
    }

    /** Whether the neighbour one step along this axis is a solid full block, for the burial test. */
    private static boolean solidFullBlock(final LevelAccelerator level,
                                          final BlockPos.MutableBlockPos cursor,
                                          final BlockPos pos,
                                          final int axis,
                                          final int sign) {
        cursor.set(
                pos.getX() + (axis == 0 ? sign : 0),
                pos.getY() + (axis == 1 ? sign : 0),
                pos.getZ() + (axis == 2 ? sign : 0));

        final BlockState neighbour = level.getBlockState(cursor);
        if (neighbour.isAir()) {
            return false;
        }
        final BlockProfile profile = BlockProfile.of(level, cursor, neighbour);
        return profile.voxelSolid() && profile.voxelFullBlock();
    }
}
