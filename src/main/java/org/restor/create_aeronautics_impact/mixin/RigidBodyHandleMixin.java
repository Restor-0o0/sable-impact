package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Vector3d;
import org.restor.create_aeronautics_impact.ImpactConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a destroyed body answer that it is standing still, instead of throwing at whoever asked.
 *
 * <p>The other half of {@link SubLevelHeatMapManagerMixin}, and the same fault behind it. When this mod
 * takes the last of a build's mass, Sable destroys the plot and the Rapier body with it, but the sub-level
 * stays in the container's list until the next sweep. Anything walking that list in the meantime is walking
 * over a dead body - and the world autosave does exactly that, serialising every sub-level it finds along
 * with its linear and angular velocity. Rapier answers a read on a destroyed body by throwing, the throw
 * comes out of {@code ServerLevel.save}, and the server goes down mid-save.
 *
 * <p>Cancelling the save for those sub-levels was the other option and is worse: the write is what keeps the
 * serialization pointer honest, and skipping it leaves stale data behind at the old one. A velocity of zero
 * is both harmless and true, and it is what the serializer would have written if the sweep had run first.
 */
@Mixin(RigidBodyHandle.class)
public abstract class RigidBodyHandleMixin {

    @Shadow
    @Final
    private PhysicsPipelineBody body;

    @Inject(method = {"getLinearVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                      "getAngularVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void create_aeronautics_impact$deadBodyIsStill(final Vector3d destination,
                                                           final CallbackInfoReturnable<Vector3d> callback) {
        if (this.body != null && this.body.isRemoved() && ImpactConfig.guardDeadBodyReads()) {
            callback.setReturnValue(destination == null ? new Vector3d() : destination.zero());
        }
    }
}
