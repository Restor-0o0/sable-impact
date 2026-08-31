package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.world.level.block.Block;
import org.restor.create_aeronautics_impact.ImpactCallback;
import org.restor.create_aeronautics_impact.ImpactConfig;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Block.class)
public abstract class BlockMixin implements BlockWithSubLevelCollisionCallback {

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        if (ImpactConfig.SPEC.isLoaded()
                && ((Block) (Object) this).getExplosionResistance()
                        >= ImpactConfig.tuning().indestructibleResistance()) {
            return null;
        }
        return ImpactCallback.INSTANCE;
    }
}
