package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.restor.create_aeronautics_impact.ImpactCallback;
import org.restor.create_aeronautics_impact.VoxelClassifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes Sable's per-block voxel classification through {@link VoxelClassifier}.
 *
 * <p>Both injections are {@code require = 0}, so a Sable release that reshapes {@code getState} costs the
 * merge rather than the game: the mod carries on with Sable's own classification, slower and correct.
 *
 * <p>The two are not alternatives. The head injection replaces the whole thing and is the fast path; the
 * redirect below it is a narrower fallback that keeps interior culling working - by far the larger of the
 * two savings - even if the head injection is the one that fails to apply.
 */
@Mixin(VoxelNeighborhoodState.class)
public class VoxelNeighborhoodStateMixin {

    /** Answers the whole classification where {@link VoxelClassifier} can, and falls through where it cannot. */
    @Inject(require = 0, method = "getState", at = @At("HEAD"), cancellable = true)
    private static void create_aeronautics_impact$classifyFast(
            final LevelAccelerator level,
            final BlockPos pos,
            final LevelChunk chunk,
            final CallbackInfoReturnable<VoxelNeighborhoodState> callback) {
        final VoxelNeighborhoodState fast = VoxelClassifier.classify(level, pos, chunk);
        if (fast != null) {
            callback.setReturnValue(fast);
        }
    }

    /**
     * Keeps interior culling working if the head injection above ever fails to apply.
     *
     * <p>Purely an optimisation, so a Sable version that reshapes {@code getState} should cost the merge,
     * not the game.
     */
    @Redirect(
            require = 0,
            method = "getState",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback;"
                            + "hasCallback(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean create_aeronautics_impact$keepBuriedBlocksMerged(final BlockState state,
                                                                           final LevelAccelerator level,
                                                                           final BlockPos pos,
                                                                           final LevelChunk chunk) {
        return ImpactCallback.needsOwnVoxel(state, level, pos);
    }
}
