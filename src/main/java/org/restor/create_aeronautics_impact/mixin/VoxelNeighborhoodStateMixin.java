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

@Mixin(VoxelNeighborhoodState.class)
public class VoxelNeighborhoodStateMixin {

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

    // Keeps interior culling working if the head injection above ever fails to apply.
    // Purely an optimisation, so a Sable version that reshapes getState should cost the merge, not the game.
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
