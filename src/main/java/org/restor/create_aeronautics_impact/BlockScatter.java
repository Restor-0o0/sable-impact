package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 *
 * <p>Which leaves the great majority of a crash with nothing to show for itself, and a wreck that mostly
 * deletes itself is the wrong picture twice over - the mass has to go somewhere, and a crater with nothing
 * around it reads as a hole rather than as damage. So a block that does not get to fly is not thrown away:
 * it is pushed clear of whatever broke it, dropped to the first thing solid underneath, and written back
 * down. No entity, no ticking, nothing to send but the block change, which is why it can be done to nearly
 * everything where throwing debris could only ever be done to a few dozen blocks a tick.
 */
public final class BlockScatter {

    private static long tick = Long.MIN_VALUE;
    private static int scattered;
    private static int settled;
    private static int effects;

    /** Scratch for a block's own world position. The break pass is the server thread and nothing else. */
    private static final Vector3d WHERE = new Vector3d();

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

        final Vec3 away = escapeDirection(pos, impactPosition, level);
        if (!scatters(level, state, speed, tuning.scatterChance(), tuning)) {
            final BlockPos room = settling(level, pos, away, state, tuning);
            clear(level, pos, room == null && tuning.dropItems());
            place(level, room, state, tuning);
            return;
        }

        throwOut(level, FallingBlockEntity.fall(level, pos, state), away, launch(speed, tuning), tuning);
    }

    /**
     * How hard a piece is actually thrown, which is not the same as how hard it was hit.
     *
     * <p>The speed of the impact decides whether a block flies at all - it is what the scatter chance and the
     * material are weighed against - but only {@code THROW} spends it on velocity. Under {@code FALL} the
     * piece is let go where it stood and gravity has it, which is what a structure coming apart looks like;
     * throwing every piece of a hull clear of the point that touched is what made a fold read as a bomb.
     */
    private static double launch(final double speed, final ImpactConfig.Tuning tuning) {
        return tuning.debrisMode() == ImpactConfig.DebrisMode.THROW ? speed : 0.0;
    }

    /**
     * The same for a block belonging to a contraption, which lives in the plotgrid rather than in the world.
     *
     * <p>Two things separate it from terrain. The build has a damage allowance and this is where the block is
     * drawn from it, so a break refused here is a break that never happens - which is what the return value
     * is for, since the caller has a budget of its own to keep honest.
     *
     * <p>And the block has no world position: it is tens of thousands of blocks out in the plotgrid, and
     * every path that broke one used to hand the debris the contact point instead. Since one contact breaks
     * hundreds of blocks all over a hull, every piece of that hull was created at the same spot - which is
     * the fountain, and the waterfall, and the pile of a whole airship in a puddle under one corner of it.
     * The build's pose puts each block back where the player is actually looking at it.
     *
     * @param plotPos             where the block is in the plotgrid, which is what the entity is created from.
     * @param worldImpactPosition where the crash happened, used only if the build has gone in the meantime.
     * @return whether the block was actually destroyed.
     */
    public static boolean shatterContraptionBlock(final ServerLevel level,
                                                  final BlockPos plotPos,
                                                  final BlockState state,
                                                  final Vector3d worldImpactPosition,
                                                  final double impactVelocity,
                                                  final double resistance) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final ServerSubLevel build = BuildDamage.owner(level, plotPos);
        if (!BuildDamage.take(level, build, tuning)) {
            return false;
        }

        final double speed = ImpactResolver.scatterSpeed(
                impactVelocity, resistance, tuning.scatterVelocityScale());
        final Vector3d where = BuildDamage.where(build, plotPos, worldImpactPosition, WHERE);

        if (!scatters(level, state, speed, tuning.contraptionScatterChance(), tuning)) {
            final BlockPos room = heaping(level, where, state, tuning);
            clear(level, plotPos, room == null && tuning.dropItems());
            place(level, room, state, tuning);
            return true;
        }

        final FallingBlockEntity debris = FallingBlockEntity.fall(level, plotPos, state);
        debris.moveTo(where.x, where.y, where.z);
        debris.setStartPos(BlockPos.containing(where.x, where.y, where.z));

        throwOut(level, debris, new Vec3(
                level.random.nextDouble() - 0.5,
                level.random.nextDouble() * 0.5,
                level.random.nextDouble() - 0.5).normalize(), launch(speed, tuning), tuning);
        return true;
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
        if (speed > 0.0) {
            debris.setDeltaMovement(direction.x * speed,
                    direction.y * speed + tuning.scatterUpwardKick(),
                    direction.z * speed);
        }
        debris.dropItem = false;
        if (tuning.debrisDamagePerBlock() > 0.0) {
            debris.setHurtsEntities((float) tuning.debrisDamagePerBlock(), tuning.debrisDamageMax());
        }
        ((DebrisHolder) debris).create_aeronautics_impact$debris(true);
    }

    /**
     * Where a broken terrain block comes to rest, or null if it has nowhere to go and is simply gone.
     *
     * <p>Pushed out of the hole first. The impact point is inside the hull, so away from it is out of the
     * excavation, which is the one direction the block is not going to be dug out of again a tick later -
     * and it is what puts the spoil beside the furrow the way a plough does, rather than back in it.
     */
    private static @Nullable BlockPos settling(final ServerLevel level,
                                               final BlockPos pos,
                                               final Vec3 away,
                                               final BlockState state,
                                               final ImpactConfig.Tuning tuning) {
        if (!heaps(level, tuning)) {
            return null;
        }
        final BlockPos pushed = pos.offset(
                (int) Math.round(away.x * 1.5),
                (int) Math.round(away.y * 1.5),
                (int) Math.round(away.z * 1.5));
        return pushed.equals(pos) ? null : ground(level, pushed, state, tuning);
    }

    /**
     * The same for a contraption's own block, which has no world position to be pushed out of and so is
     * spread over a disc around the crash. Landing on the heap that is already there is the point: each
     * block stops on top of the last, and what accumulates is a pile of the ship where the ship came down.
     */
    private static @Nullable BlockPos heaping(final ServerLevel level,
                                              final Vector3d worldImpact,
                                              final BlockState state,
                                              final ImpactConfig.Tuning tuning) {
        if (!heaps(level, tuning)) {
            return null;
        }
        final double spread = tuning.settleSpread();
        return ground(level, BlockPos.containing(
                worldImpact.x + (level.random.nextDouble() - 0.5) * 2.0 * spread,
                worldImpact.y + 1.0,
                worldImpact.z + (level.random.nextDouble() - 0.5) * 2.0 * spread), state, tuning);
    }

    /**
     * Drops a block from where it was let go to the first thing solid under it, within reach.
     *
     * <p>Stopping at the floor rather than taking the first free position is what makes a heap a heap.
     * Anything else fills in the overhangs and leaves wreckage standing in the air where it happened to be
     * when it stopped being part of something.
     *
     * <p>And a block that finds no floor at all inside its reach is not settled anywhere: it is a block that
     * came off something in mid-air, and the honest answer to where it goes is down. Wreckage nailed to the
     * sky at the altitude the ship broke up at is worse than wreckage that is simply missing.
     */
    private static @Nullable BlockPos ground(final ServerLevel level,
                                             final BlockPos from,
                                             final BlockState state,
                                             final ImpactConfig.Tuning tuning) {
        if (!open(level, from)) {
            return null;
        }
        final BlockPos.MutableBlockPos cursor = from.mutable();
        boolean landed = false;
        for (int step = 0; step < tuning.settleDrop(); step++) {
            cursor.move(Direction.DOWN);
            if (!open(level, cursor)) {
                cursor.move(Direction.UP);
                landed = true;
                break;
            }
        }
        if (!landed && FallingBlock.isFree(level.getBlockState(cursor.below()))) {
            return null;
        }
        return state.canSurvive(level, cursor) ? cursor.immutable() : null;
    }

    /** Somewhere a block could be written: inside the world, in a loaded chunk, into something that gives way. */
    private static boolean open(final ServerLevel level, final BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight()
                && pos.getY() < level.getMaxBuildHeight()
                && level.isLoaded(pos)
                && level.getBlockState(pos).canBeReplaced();
    }

    /** Writes a settled block back, if it found anywhere at all to settle. */
    private static void place(final ServerLevel level,
                              final @Nullable BlockPos room,
                              final BlockState state,
                              final ImpactConfig.Tuning tuning) {
        if (room != null && level.getBlockState(room).canBeReplaced()) {
            level.setBlock(room, state, tuning.blockUpdates() ? Block.UPDATE_ALL : QUIET_PLACEMENT);
        }
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
     * Whether this break gets to be heaped back onto the world, and books the ration slot if so.
     *
     * <p>Settling is one block change against a falling block's entity, so it was left unrationed and
     * settleShare sends the great majority of a crash through it. That is thousands of writes in the tick a
     * wreck comes down, each of them a neighbour update, and it is where the frame went - the mod's own
     * measured work was a fraction of a millisecond in ticks that took a second and a half. The slot is
     * booked before the ground is searched for rather than after, because the search is most of the cost.
     */
    private static boolean heaps(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        if (!tuning.settle() || tuning.settleShare() <= 0.0
                || level.random.nextDouble() >= tuning.settleShare()) {
            return false;
        }
        rollOver(level);
        if (settled >= tuning.maxSettlePerTick()) {
            return false;
        }
        settled++;
        return true;
    }

    /**
     * Whether this break gets to be a falling block, and books the ration slot if so.
     *
     * <p>Block entities are excluded outright: a falling block entity carries no block entity data, so
     * throwing a chest would quietly empty it. SETTLE excludes everything, which is the cheap setting: there
     * is no entity to tick, land and write a block anyway, only the block change at the end of it.
     */
    private static boolean scatters(final ServerLevel level,
                                    final BlockState state,
                                    final double speed,
                                    final double chance,
                                    final ImpactConfig.Tuning tuning) {
        if (tuning.debrisMode() == ImpactConfig.DebrisMode.SETTLE) {
            return false;
        }
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
            settled = 0;
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
