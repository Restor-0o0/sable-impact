package org.restor.create_aeronautics_impact.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.restor.create_aeronautics_impact.BlockProfile;
import org.restor.create_aeronautics_impact.ProfileHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every block state a field to keep its {@link BlockProfile} in.
 *
 * <p>See {@link ProfileHolder} for why the field exists rather than a map. The mixin holds no logic of its
 * own: the generation counter that decides when a cached profile has gone stale lives in
 * {@link BlockProfile}, so nothing here needs to know when the config changed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateProfileMixin implements ProfileHolder {

    /** Not volatile: a stale read costs one rebuild, and the profile is a function of the state alone. */
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
