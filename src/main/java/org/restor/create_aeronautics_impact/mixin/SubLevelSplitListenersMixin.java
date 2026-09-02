package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;

/**
 * Reaches Sable's list of split listeners.
 *
 * <p>Sable tells everyone who asked before it assembles a piece of a build into a build of its own, and Create
 * Aeronautics is one of the things that asked. A split this mod makes itself is the same event and has to
 * announce itself the same way, or an addon's idea of what a contraption is made of parts company with the
 * blocks.
 */
@Mixin(SubLevelHeatMapManager.class)
public interface SubLevelSplitListenersMixin {

    @Accessor("LISTENERS")
    static Collection<SubLevelHeatMapManager.SplitListener> create_aeronautics_impact$listeners() {
        throw new AssertionError();
    }
}
