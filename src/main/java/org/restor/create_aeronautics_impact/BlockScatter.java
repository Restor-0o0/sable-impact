package org.restor.create_aeronautics_impact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * Turning blocks into debris is the part of an impact that is seen, and it is also the part that costs.
 * A falling block is a ticking entity that lands and writes a block back, and a break effect is a packet to
 * every player in range; a hull ploughing terrain produces hundreds of both per tick. Both are therefore
 * rationed per tick: the first handful of breaks look expensive, the rest of them are quiet, and an impact
 * that levels a hillside costs about what an impact that chips it does.
 */
public final class BlockScatter {

    private static long tick = Long.MIN_VALUE;
    private static int scattered;
    private static int effects;

    private BlockScatter() {
    }

    public static void shatter(final ServerLevel level,
                               final BlockPos pos,
                               final BlockState state,
                               final Vector3d impactPosition,
                               final double impactVelocity,
                               final double resistance) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final double speed = ImpactResolver.scatterSpeed(
                impactVelocity, resistance, tuning.scatterVelocityScale());

        if (!scatters(level, state, speed, tuning)) {
            clear(level, pos, tuning.dropItems());
            return;
        }

        final Vec3 dir = escapeDirection(pos, impactPosition, level);
        final FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, state);
        debris.setDeltaMovement(dir.x * speed, dir.y * speed + 0.15, dir.z * speed);
    }

    public static void shatterContraptionBlock(final ServerLevel level,
                                               final BlockPos plotPos,
                                               final BlockState state,
                                               final Vector3d worldImpactPosition,
                                               final double impactVelocity,
                                               final double resistance) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final double speed = ImpactResolver.scatterSpeed(
                impactVelocity, resistance, tuning.scatterVelocityScale());

        if (!scatters(level, state, speed, tuning)) {
            clear(level, plotPos, tuning.dropItems());
            return;
        }

        // The block lives in the plotgrid tens of thousands of blocks away, so the debris is spawned
        // there and then moved to where the player actually saw the crash.
        final FallingBlockEntity debris = FallingBlockEntity.fall(level, plotPos, state);
        debris.moveTo(worldImpactPosition.x, worldImpactPosition.y + 0.5, worldImpactPosition.z);
        debris.setStartPos(BlockPos.containing(worldImpactPosition.x, worldImpactPosition.y, worldImpactPosition.z));

        final Vec3 dir = new Vec3(
                level.random.nextDouble() - 0.5,
                level.random.nextDouble() * 0.5,
                level.random.nextDouble() - 0.5).normalize();
        debris.setDeltaMovement(dir.x * speed, dir.y * speed + 0.15, dir.z * speed);
    }

    /**
     * Removes a block, spending a break effect on it while this tick still has any to spend. Past that the
     * removal is silent, which also means it drops nothing - mass destruction that drops its items buries the
     * server in item entities long before the missing particles are noticed.
     */
    public static void clear(final ServerLevel level, final BlockPos pos, final boolean drop) {
        rollOver(level);
        if (effects < ImpactConfig.tuning().maxBreakEffectsPerTick()) {
            effects++;
            level.destroyBlock(pos, drop);
            return;
        }
        level.removeBlock(pos, false);
    }

    private static boolean scatters(final ServerLevel level,
                                    final BlockState state,
                                    final double speed,
                                    final ImpactConfig.Tuning tuning) {
        if (speed <= 0.05
                || state.hasBlockEntity()
                || !state.getFluidState().isEmpty()
                || level.random.nextDouble() >= tuning.scatterChance()) {
            return false;
        }

        rollOver(level);
        if (scattered >= tuning.maxScatterPerTick()) {
            return false;
        }
        scattered++;
        return true;
    }

    private static void rollOver(final ServerLevel level) {
        final long now = level.getGameTime();
        if (now != tick) {
            tick = now;
            scattered = 0;
            effects = 0;
        }
    }

    private static Vec3 escapeDirection(final BlockPos pos, final Vector3d impactPosition, final ServerLevel level) {
        final Vec3 away = new Vec3(
                pos.getX() + 0.5 - impactPosition.x,
                pos.getY() + 0.5 - impactPosition.y,
                pos.getZ() + 0.5 - impactPosition.z);

        final Vec3 base = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
        final double jitter = 0.4;
        return base.add(
                (level.random.nextDouble() - 0.5) * jitter,
                (level.random.nextDouble() - 0.5) * jitter,
                (level.random.nextDouble() - 0.5) * jitter);
    }
}
