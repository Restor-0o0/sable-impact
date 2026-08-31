package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.callback.FragileBlockCallback;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Arrays;

public final class ImpactCallback implements BlockSubLevelCollisionCallback {

    public static final ImpactCallback INSTANCE = new ImpactCallback();

    private static final Vector3d NO_TANGENT_MOTION = new Vector3d();
    private static final int PLOT_CACHE = 8;

    private final ContactTracker contacts = new ContactTracker();
    private final long[] plotKeys = new long[PLOT_CACHE];
    private final ServerSubLevel[] plots = new ServerSubLevel[PLOT_CACHE];

    private long budgetTick = Long.MIN_VALUE;
    private int destroyedThisTick;
    private int examinedThisTick;

    private ImpactCallback() {
        forgetPlots();
    }

    @Override
    public CollisionResult sable$onCollision(final BlockPos hitBlockPos,
                                             @Nullable final BlockPos otherHitBlockPos,
                                             final Vector3d impactPosition,
                                             final double impactVelocity) {
        final long started = ImpactStats.mark();
        try {
            return collide(hitBlockPos, otherHitBlockPos, impactPosition, impactVelocity);
        } finally {
            ImpactStats.since(ImpactStats.Phase.CONTACT, started);
        }
    }

    private CollisionResult collide(final BlockPos hitBlockPos,
                                    @Nullable final BlockPos otherHitBlockPos,
                                    final Vector3d impactPosition,
                                    final double impactVelocity) {
        if (!ImpactConfig.SPEC.isLoaded()) {
            return CollisionResult.NONE;
        }

        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();

        // Resting and sliding contacts make up the overwhelming majority of what the pipeline reports, and
        // they carry almost no normal velocity. Rejecting them here keeps every lookup below off the hot path.
        if (Math.abs(impactVelocity) < Math.min(tuning.breakSpeedFloor(), tuning.fragileTrigger())) {
            return CollisionResult.NONE;
        }

        final SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null) {
            return CollisionResult.NONE;
        }

        final ServerLevel level = system.getLevel();
        rollTick(level.getGameTime());
        // Past the ceiling contacts are not examined, so they are not broken either. The hull is still
        // stopped by them, because a contact this mod declines is one Sable resolves the ordinary way.
        final int ceiling = tuning.maxContactsPerTick();
        if (ceiling > 0 && ++this.examinedThisTick > ceiling) {
            return CollisionResult.NONE;
        }

        final BlockState hitState = level.getBlockState(hitBlockPos);
        if (hitState.isAir()) {
            return CollisionResult.NONE;
        }

        final BlockProfile hitProfile = BlockProfile.of(level, hitBlockPos, hitState);

        // Sable normally routes leaves, ice and the rest of #sable:fragile through its own callback. Claiming
        // every block for this mod took that away, so hand those back rather than shattering them by mass.
        if (hitProfile.fragile()) {
            return FragileBlockCallback.INSTANCE
                    .sable$onCollision(hitBlockPos, otherHitBlockPos, impactPosition, impactVelocity);
        }

        // A null other side means the opposing body is not a sub-level, i.e. this is a contraption
        // block striking the static world. That contact is handled from the terrain block's own
        // callback, where both sides are known at once.
        if (otherHitBlockPos == null) {
            return CollisionResult.NONE;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(otherHitBlockPos)) {
            return CollisionResult.NONE;
        }

        final ServerSubLevel otherSubLevel = resolveSubLevel(container, otherHitBlockPos);
        if (otherSubLevel == null) {
            return CollisionResult.NONE;
        }

        final boolean hitIsContraption = container.inBounds(hitBlockPos);
        final ServerSubLevel hitSubLevel = hitIsContraption ? resolveSubLevel(container, hitBlockPos) : null;
        if (hitIsContraption
                && (hitSubLevel == null || hitSubLevel.getRuntimeId() == otherSubLevel.getRuntimeId())) {
            return CollisionResult.NONE;
        }

        final boolean breakContraptions = tuning.breakContraptionBlocks();
        if (hitIsContraption && !breakContraptions) {
            return CollisionResult.NONE;
        }

        final BlockState otherState = level.getBlockState(otherHitBlockPos);
        if (otherState.isAir()) {
            return CollisionResult.NONE;
        }

        final double toughness = tuning.contraptionBlockToughness();
        final double massFactor = massFactor(tuning, hitSubLevel, otherSubLevel, level.getGameTime());

        // A contraption is one rigid body and its blocks really are holding each other up, so only terrain
        // is asked what is behind it.
        final double backing = hitIsContraption
                ? 1.0
                : Backing.of(level, hitBlockPos, impactPosition.x, impactPosition.y, impactPosition.z);

        final ImpactResolver.Side hit =
                hitProfile.side(massFactor, hitIsContraption ? toughness : 1.0, backing);
        final ImpactResolver.Side other = breakContraptions
                ? BlockProfile.of(level, otherHitBlockPos, otherState).side(massFactor, toughness)
                : null;

        final ImpactResolver.Victim victim =
                ImpactResolver.victim(impactVelocity, hit, other, budgetExhausted(tuning));
        if (victim == ImpactResolver.Victim.NONE) {
            return CollisionResult.NONE;
        }

        // impactPosition arrives in the plot space of whichever sub-level owns the hit block, and in
        // world space when that block is plain terrain.
        final Vector3d worldImpact = hitSubLevel == null
                ? impactPosition
                : hitSubLevel.logicalPose().transformPosition(impactPosition, new Vector3d());

        final double wearShare = tuning.impactWear();

        if (victim == ImpactResolver.Victim.OTHER) {
            if (PendingBreaks.queue(level, otherHitBlockPos, otherState, worldImpact,
                    impactVelocity, other.resistance(), overshoot(impactVelocity, other), true)) {
                this.destroyedThisTick++;
                PendingBreaks.wear(level, hitBlockPos, hitState, worldImpact, impactVelocity,
                        hit.resistance(), ImpactResolver.wear(hit, other) * wearShare, hitIsContraption);
            }
            // The hit block held, so it keeps its collision and stops the other body as usual.
            return CollisionResult.NONE;
        }

        // Dropping the contact as well as the block is what lets a ram plough instead of bouncing, but it
        // also means the terrain took nothing out of it, so every next layer is met at the same speed and the
        // hull walks itself down into the ground. Keeping it costs the ploughing and buys back a hull that
        // hits terrain and stops on it.
        final boolean punchThrough = tuning.punchThrough()
                && ImpactResolver.punchesThrough(impactVelocity, hit.breakSpeed(), tuning.punchThroughRatio());

        if (PendingBreaks.queue(level, hitBlockPos, hitState, worldImpact,
                impactVelocity, hit.resistance(), overshoot(impactVelocity, hit), hitIsContraption)) {
            this.destroyedThisTick++;
            if (other != null) {
                PendingBreaks.wear(level, otherHitBlockPos, otherState, worldImpact, impactVelocity,
                        other.resistance(), ImpactResolver.wear(other, hit) * wearShare, true);
            }
            if (punchThrough) {
                final double momentum = tuning.breakDragMass() * Math.abs(impactVelocity);
                PendingBreaks.drag(level, otherSubLevel, momentum);
                PendingBreaks.drag(level, hitSubLevel, momentum);
            }
        }

        return punchThrough ? new CollisionResult(NO_TANGENT_MOTION, true) : CollisionResult.NONE;
    }

    /** How far past its break speed the block was hit, which is how fast it accumulates damage. */
    private static double overshoot(final double impactVelocity, final ImpactResolver.Side side) {
        final double breakSpeed = side.breakSpeed();
        return breakSpeed <= 0.0 ? Double.MAX_VALUE : Math.abs(impactVelocity) / breakSpeed;
    }

    /**
     * Whether a block has to keep its own voxel in Sable's collider meshing. Sable grants that to any block
     * carrying a collision callback, and this mod carries one on every block, which stops terrain from ever
     * merging. A block walled in on all six sides cannot be the first thing a hull touches, so it can go back
     * to being merged; breaking a neighbour re-classifies it and it gets its voxel back.
     */
    public static boolean needsOwnVoxel(final BlockState state, final BlockGetter level, final BlockPos pos) {
        final long started = ImpactStats.markVoxel();
        try {
            return keepsVoxel(state, level, pos);
        } finally {
            ImpactStats.sinceVoxel(started);
        }
    }

    private static boolean keepsVoxel(final BlockState state, final BlockGetter level, final BlockPos pos) {
        final BlockSubLevelCollisionCallback callback = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        if (callback == null) {
            return false;
        }
        if (callback != INSTANCE) {
            return true;
        }
        if (!ImpactConfig.SPEC.isLoaded() || !ImpactConfig.cullInteriorVoxels()) {
            return true;
        }
        final BlockProfile profile = BlockProfile.of(level, pos, state);
        if (profile.fragile()) {
            return true;
        }
        // Sable classifies anything short of a solid full block on its own terms before the neighbourhood is
        // ever consulted, so claiming those costs six lookups to arrive at the answer it already had.
        if (!profile.voxelSolid() || !profile.voxelFullBlock()) {
            return false;
        }
        return !isWalledIn(level, pos);
    }

    private static boolean isWalledIn(final BlockGetter level, final BlockPos pos) {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (final Direction direction : Direction.values()) {
            cursor.setWithOffset(pos, direction);
            final BlockState neighbour = level.getBlockState(cursor);
            if (neighbour.isAir()) {
                return false;
            }
            final BlockProfile profile = BlockProfile.of(level, cursor, neighbour);
            if (!profile.voxelSolid() || !profile.voxelFullBlock()) {
                return false;
            }
        }
        return true;
    }

    private double massFactor(final ImpactConfig.Tuning tuning,
                              @Nullable final ServerSubLevel hitSubLevel,
                              final ServerSubLevel otherSubLevel,
                              final long gameTime) {
        final double strikingMass = massOf(otherSubLevel);
        if (strikingMass <= 0.0) {
            return 1.0;
        }

        // Static terrain is the infinite-mass limit of the two-body case.
        final double opposingMass = hitSubLevel == null ? Double.POSITIVE_INFINITY : massOf(hitSubLevel);

        int contactArea = this.contacts.recordAndEstimateArea(otherSubLevel.getRuntimeId(), gameTime);
        if (hitSubLevel != null) {
            contactArea = Math.max(
                    contactArea, this.contacts.recordAndEstimateArea(hitSubLevel.getRuntimeId(), gameTime));
        }

        return ImpactResolver.massFactor(
                ImpactResolver.contactPressure(
                        ImpactResolver.reducedMass(strikingMass, opposingMass), contactArea),
                tuning.referencePressure(),
                tuning.massSensitivity(),
                tuning.massFactorMin(),
                tuning.massFactorMax());
    }

    private static double massOf(final ServerSubLevel subLevel) {
        final MassData mass = subLevel.getMassTracker();
        return mass == null || mass.isInvalid() ? 0.0 : mass.getMass();
    }

    /**
     * A sub-level owns a whole plot, so consecutive contacts on the same body resolve to the same one over
     * and over. Remembering the last few spares both the lookup and the {@link ChunkPos} it needs, which at
     * a few thousand contacts a tick is the allocation this callback makes most of.
     */
    private @Nullable ServerSubLevel resolveSubLevel(final ServerSubLevelContainer container,
                                                     final BlockPos pos) {
        final long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        final int slot = (int) (key ^ (key >>> 32)) & (PLOT_CACHE - 1);
        if (this.plotKeys[slot] == key) {
            return this.plots[slot];
        }

        final LevelPlot plot = container.getPlot(new ChunkPos(pos));
        final SubLevel subLevel = plot == null ? null : plot.getSubLevel();
        final ServerSubLevel resolved = subLevel instanceof ServerSubLevel server ? server : null;

        this.plotKeys[slot] = key;
        this.plots[slot] = resolved;
        return resolved;
    }

    private void forgetPlots() {
        Arrays.fill(this.plotKeys, Long.MIN_VALUE);
        Arrays.fill(this.plots, null);
    }

    private void rollTick(final long now) {
        if (now != this.budgetTick) {
            this.budgetTick = now;
            this.destroyedThisTick = 0;
            this.examinedThisTick = 0;
            forgetPlots();
        }
    }

    private boolean budgetExhausted(final ImpactConfig.Tuning tuning) {
        return this.destroyedThisTick >= tuning.maxBlocksPerTick();
    }
}
