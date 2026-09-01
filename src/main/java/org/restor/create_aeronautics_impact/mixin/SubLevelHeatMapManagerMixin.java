package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.restor.create_aeronautics_impact.ImpactConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a sub-level Sable has already removed from splitting itself into new ones.
 *
 * <p>Sable marks a sub-level removed the moment its last mass goes, from inside the block change that took
 * it, but the container only sweeps removed sub-levels after every sub-level in it has ticked. A build this
 * mod empties is emptied from the level tick, after that sweep has run for the tick, so the dead sub-level
 * gets one more tick of its own before it is collected. In it the connectivity flood-fill finishes and tries
 * to assemble what it found inside a plot that has already been destroyed, and Sable answers an assembly
 * inside a removed plot by throwing - on the server thread, out of the level tick, which ends the world.
 *
 * <p>Cancelling the tick is the whole fix, and it takes nothing away: the sweep a few lines later in the
 * same container tick is about to remove this sub-level regardless, so the split it is part-way through has
 * no sub-level left to belong to either way.
 */
@Mixin(SubLevelHeatMapManager.class)
public abstract class SubLevelHeatMapManagerMixin {

    @Shadow
    @Final
    private ServerSubLevel subLevel;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void create_aeronautics_impact$skipRemoved(final CallbackInfo callback) {
        if (this.subLevel.isRemoved() && ImpactConfig.guardRemovedSplits()) {
            callback.cancel();
        }
    }
}
