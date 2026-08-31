package org.restor.create_aeronautics_impact.mixin;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.world.level.block.Block;
import org.restor.create_aeronautics_impact.ImpactCallback;
import org.restor.create_aeronautics_impact.ImpactConfig;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Claims every block in the game for {@link ImpactCallback}.
 *
 * <p>Sable asks each block for a collision callback and normally gets one only from the handful that ship
 * with one, which is why the world does not answer a hull back. Answering it means claiming all of them.
 *
 * <p>The exception is blocks over {@code indestructibleResistance}, which are handed back with no callback
 * at all. That is not a shortcut for "do not break this": a block with no callback is one Sable can merge
 * into its neighbours, so the mod's own backstop for permanent blocks also makes them cheaper than they
 * were before it was installed.
 */
@Mixin(Block.class)
public abstract class BlockMixin implements BlockWithSubLevelCollisionCallback {

    /** @return the mod's callback, or null for blocks past {@code indestructibleResistance}. */
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
