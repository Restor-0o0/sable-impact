package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import org.restor.create_aeronautics_impact.PlotBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Opens Sable's plot-chunk bounding box rebuild to {@code BoundsBatch}.
 *
 * <p>Adds nothing and changes nothing: the rebuild is Sable's own, run when Sable would have run it, merely
 * once at the end of a break pass instead of once per block removed during it.
 */
@Mixin(PlotChunkHolder.class)
public abstract class PlotChunkHolderMixin implements PlotBounds {

    @Shadow
    protected abstract void buildBoundingBox();

    @Override
    public void create_aeronautics_impact$rebuildBounds() {
        this.buildBoundingBox();
    }
}
