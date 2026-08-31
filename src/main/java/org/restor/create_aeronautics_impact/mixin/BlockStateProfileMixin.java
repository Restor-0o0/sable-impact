package org.restor.create_aeronautics_impact.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.restor.create_aeronautics_impact.BlockProfile;
import org.restor.create_aeronautics_impact.ProfileHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateProfileMixin implements ProfileHolder {

    @Unique
    private BlockProfile create_aeronautics_impact$cachedProfile;

    @Override
    public BlockProfile create_aeronautics_impact$profile() {
        return this.create_aeronautics_impact$cachedProfile;
    }

    @Override
    public void create_aeronautics_impact$profile(final BlockProfile profile) {
        this.create_aeronautics_impact$cachedProfile = profile;
    }
}
