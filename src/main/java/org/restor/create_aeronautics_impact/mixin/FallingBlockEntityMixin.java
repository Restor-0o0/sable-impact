package org.restor.create_aeronautics_impact.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.create_aeronautics_impact.BlockScatter;
import org.restor.create_aeronautics_impact.DebrisHolder;
import org.restor.create_aeronautics_impact.ImpactConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives debris thrown by an impact somewhere to land.
 *
 * <p>A vanilla falling block has one spot it is willing to occupy: the block it happens to be standing in
 * when it touches down. If something is already there - the wall it was thrown against, the slab it rolled
 * onto, the hole it came out of - it gives up, turns into an item and is gone. That is fine for gravel, which
 * falls straight down into a column it just vacated, and it is why wreckage from a crash disappears.
 *
 * <p>So the landing is watched rather than replaced. Vanilla is left to place the block wherever it can, and
 * only when it could not does {@link BlockScatter#settle} go looking nearby.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin implements DebrisHolder {

    @Unique
    private boolean create_aeronautics_impact$debris;

    @Override
    public boolean create_aeronautics_impact$debris() {
        return this.create_aeronautics_impact$debris;
    }

    @Override
    public void create_aeronautics_impact$debris(final boolean debris) {
        this.create_aeronautics_impact$debris = debris;
    }

    /**
     * The one place vanilla writes the block back. A write that took means the landing worked and there is
     * nothing left to do, so the entity stops being this mod's business before it removes itself. A write
     * that did not is left claimed, and is picked up below like any other failed landing.
     */
    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock("
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean create_aeronautics_impact$landed(final Level level,
                                                     final BlockPos pos,
                                                     final BlockState state,
                                                     final int flags,
                                                     final Operation<Boolean> original) {
        final boolean placed = original.call(level, pos, state, flags);
        if (placed) {
            this.create_aeronautics_impact$debris = false;
        }
        return placed;
    }

    /**
     * Everything else that ends a piece of debris: the spot was taken, the block could not survive there, or
     * it has been in the air longer than it is allowed to be. All of them are the same question - where does
     * this block actually go - and it is asked once, here, after vanilla has had its turn.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void create_aeronautics_impact$settle(final CallbackInfo callback) {
        if (!this.create_aeronautics_impact$debris) {
            return;
        }

        final FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!(self.level() instanceof final ServerLevel level)) {
            return;
        }

        if (self.isRemoved()) {
            this.create_aeronautics_impact$debris = false;
            BlockScatter.settle(level, self.blockPosition(), self.getBlockState());
            return;
        }

        final int lifetime = ImpactConfig.tuning().lifetimeTicks();
        if (lifetime > 0 && self.time >= lifetime) {
            this.create_aeronautics_impact$debris = false;
            BlockScatter.settle(level, self.blockPosition(), self.getBlockState());
            self.discard();
        }
    }
}
