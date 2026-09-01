package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.create_aeronautics_impact.BoundsBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sends the two bounding-box updates Sable does per block change through {@code BoundsBatch}.
 *
 * <p>Both redirects are {@code require = 0}: a Sable release that reshapes this method costs the batching
 * and nothing else, and the mod carries on doing what it did before - correctly, and a great deal slower
 * during a large crash.
 */
@Mixin(SableCommonEvents.class)
public class SableCommonEventsMixin {

    @Redirect(
            require = 0,
            method = "handleBlockChange",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/plot/PlotChunkHolder;handleBlockChange"
                            + "(IIILnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private static void create_aeronautics_impact$batchChunkBounds(final PlotChunkHolder holder,
                                                                   final int x,
                                                                   final int y,
                                                                   final int z,
                                                                   final BlockState oldState,
                                                                   final BlockState newState) {
        if (!BoundsBatch.deferChunk(holder, oldState, newState)) {
            holder.handleBlockChange(x, y, z, oldState, newState);
        }
    }

    @Redirect(
            require = 0,
            method = "handleBlockChange",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;updateBoundingBox()V"))
    private static void create_aeronautics_impact$batchPlotBounds(final LevelPlot plot) {
        if (!BoundsBatch.deferPlot(plot)) {
            plot.updateBoundingBox();
        }
    }
}
