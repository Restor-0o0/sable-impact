package org.restor.create_aeronautics_impact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
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

    /**
     * Breaks a terrain block, throwing it clear as debris if this tick can still afford one.
     *
     * <p>Debris flies away from where it was hit rather than along the hull's travel: the impact point is
     * inside the hull, so away from it is out of the hole, which is where the block has anywhere to go.
     */
    public static void shatter(final ServerLevel level,
                               final BlockPos pos,
                               final BlockState state,
                               final Vector3d impactPosition,
                               final double impactVelocity,
                               final double resistance) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final double speed = ImpactResolver.scatterSpeed(
                impactVelocity, resistance, tuning.scatterVelocityScale());

        if (!scatters(level, state, speed, tuning.scatterChance(), tuning)) {
            clear(level, pos, tuning.dropItems());
            return;
        }

        throwOut(level, FallingBlockEntity.fall(level, pos, state),
                escapeDirection(pos, impactPosition, level), speed, tuning);
    }

    /**
     * The same for a block belonging to a contraption, which lives in the plotgrid rather than in the world.
     *
     * @param plotPos             where the block actually is, tens of thousands of blocks out in the
     *                            plotgrid, which is what the entity has to be created from.
     * @param worldImpactPosition where the player saw the crash, which is where it is then moved to.
     */
    public static void shatterContraptionBlock(final ServerLevel level,
                                               final BlockPos plotPos,
                                               final BlockState state,
                                               final Vector3d worldImpactPosition,
                                               final double impactVelocity,
                                               final double resistance) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final double speed = ImpactResolver.scatterSpeed(
                impactVelocity, resistance, tuning.scatterVelocityScale());

        if (!scatters(level, state, speed, tuning.contraptionScatterChance(), tuning)) {
            clear(level, plotPos, tuning.dropItems());
            return;
        }

        // The block lives in the plotgrid tens of thousands of blocks away, so the debris is spawned
        // there and then moved to where the player actually saw the crash.
        final FallingBlockEntity debris = FallingBlockEntity.fall(level, plotPos, state);
        debris.moveTo(worldImpactPosition.x, worldImpactPosition.y + 0.5, worldImpactPosition.z);
        debris.setStartPos(BlockPos.containing(worldImpactPosition.x, worldImpactPosition.y, worldImpactPosition.z));

        throwOut(level, debris, new Vec3(
                level.random.nextDouble() - 0.5,
                level.random.nextDouble() * 0.5,
                level.random.nextDouble() - 0.5).normalize(), speed, tuning);
    }

    /**
     * Sends one piece of debris on its way, and claims it for this mod on the way out.
     *
     * <p>Vanilla's own drop is turned off because the decision has moved: a block that cannot be placed where
     * it landed is not finished, it is looking for room, and only {@link #settle} gets to say it ran out.
     */
    private static void throwOut(final ServerLevel level,
                                 final FallingBlockEntity debris,
                                 final Vec3 direction,
                                 final double speed,
                                 final ImpactConfig.Tuning tuning) {
        debris.setDeltaMovement(direction.x * speed,
                direction.y * speed + tuning.scatterUpwardKick(),
                direction.z * speed);
        debris.dropItem = false;
        if (tuning.debrisDamagePerBlock() > 0.0) {
            debris.setHurtsEntities((float) tuning.debrisDamagePerBlock(), tuning.debrisDamageMax());
        }
        ((DebrisHolder) debris).create_aeronautics_impact$debris(true);
    }

    /**
     * Puts a piece of debris somewhere it will fit, once vanilla has decided it will not fit where it landed.
     *
     * <p>Called from the falling block's own tick, so it happens at most once per piece and only for the ones
     * that failed - which is a small share of a crash. The search is a widening shell around where it came
     * down, lowest position of each shell first, so wreckage settles rather than stacking.
     */
    public static void settle(final ServerLevel level, final BlockPos landed, final BlockState state) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final BlockPos room = room(level, landed, state, tuning);
        if (room != null) {
            level.setBlock(room, state, tuning.blockUpdates() ? Block.UPDATE_ALL : QUIET_PLACEMENT);
            return;
        }
        if (tuning.dropWhenLost() && level.isLoaded(landed)) {
            Block.popResource(level, landed, new ItemStack(state.getBlock()));
        }
    }

    /** The nearest position to {@code landed} this block can occupy, or null if there is none in reach. */
    @Nullable
    private static BlockPos room(final ServerLevel level,
                                 final BlockPos landed,
                                 final BlockState state,
                                 final ImpactConfig.Tuning tuning) {
        final int reach = tuning.landingSearch();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int shell = 0; shell <= reach; shell++) {
            for (int dy = -shell; dy <= shell; dy++) {
                for (int dx = -shell; dx <= shell; dx++) {
                    for (int dz = -shell; dz <= shell; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != shell) {
                            continue;
                        }
                        cursor.set(landed.getX() + dx, landed.getY() + dy, landed.getZ() + dz);
                        if (fits(level, cursor, state, tuning)) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Whether this block can be written at this position: inside the world, in a chunk that is actually
     * loaded, into something that gives way, and standing on something if it is being asked to.
     */
    private static boolean fits(final ServerLevel level,
                                final BlockPos pos,
                                final BlockState state,
                                final ImpactConfig.Tuning tuning) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.isLoaded(pos)) {
            return false;
        }
        if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) {
            return false;
        }
        return !tuning.landingNeedsFloor() || !FallingBlock.isFree(level.getBlockState(pos.below()));
    }

    /** A placement nobody needs to hear about: the clients are told, the neighbours are not. */
    private static final int QUIET_PLACEMENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * What a silent removal tells the world about itself when {@code blockUpdates} is off.
     *
     * <p>The clients are still told, so nothing goes stale on screen; the neighbour notification and the
     * shape update are what is skipped, and those are the expensive half.
     */
    private static final int QUIET_REMOVAL =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /**
     * Removes a block, spending a break effect on it while this tick still has any to spend. Past that the
     * removal is silent, which also means it drops nothing - mass destruction that drops its items buries the
     * server in item entities long before the missing particles are noticed.
     */
    public static void clear(final ServerLevel level, final BlockPos pos, final boolean drop) {
        rollOver(level);
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        if (effects < tuning.maxBreakEffectsPerTick()) {
            effects++;
            level.destroyBlock(pos, drop);
            return;
        }
        if (tuning.blockUpdates()) {
            level.removeBlock(pos, false);
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET_REMOVAL);
    }

    /**
     * Whether this break gets to be a falling block, and books the ration slot if so.
     *
     * <p>Block entities are excluded outright: a falling block entity carries no block entity data, so
     * throwing a chest would quietly empty it.
     */
    private static boolean scatters(final ServerLevel level,
                                    final BlockState state,
                                    final double speed,
                                    final double chance,
                                    final ImpactConfig.Tuning tuning) {
        if (speed <= 0.05
                || state.hasBlockEntity()
                || !state.getFluidState().isEmpty()
                || level.random.nextDouble() >= chance) {
            return false;
        }

        rollOver(level);
        if (scattered >= tuning.maxScatterPerTick()) {
            return false;
        }
        scattered++;
        return true;
    }

    /** Resets both rations when the game time moves on. Shared across levels; the counters are per tick. */
    private static void rollOver(final ServerLevel level) {
        final long now = level.getGameTime();
        if (now != tick) {
            tick = now;
            scattered = 0;
            effects = 0;
        }
    }

    /** Away from the impact point, jittered, so a wall does not come apart in one flat sheet. */
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
