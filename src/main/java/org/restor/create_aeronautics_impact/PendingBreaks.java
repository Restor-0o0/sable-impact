package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Impacts are reported from inside Rapier's solver step, and setting a block makes Sable re-bake colliders
 * through that same native library. Doing it there hangs the server thread outright: the step still owns the
 * physics world when the block change asks it for a new voxel collider. So a hit only writes down what it
 * wants broken, and the level tick does the breaking once the step has returned.
 *
 * <p>Waiting also makes the contact honest. The block is still standing for the rest of the step, so a hull
 * that did not earn a free pass through it is pushed back by it exactly once, instead of finding the terrain
 * gone from under it mid-step and accelerating into the next layer.
 */
public final class PendingBreaks {

    /**
     * A block that lost its impact and is to be shattered on the tick.
     *
     * <p>{@code state} is kept so the tick can check the block is still the one that was hit: a queue filled
     * over a whole physics step can easily name a block something else has removed since.
     */
    private record Break(BlockPos pos,
                         BlockState state,
                         Vector3d impact,
                         double impactVelocity,
                         double resistance,
                         double overshoot,
                         boolean contraption,
                         int bodyId,
                         double kinetic) {
    }

    /**
     * A block that won its impact and is to be worn down for it, with {@code share} being the accumulated
     * fraction of a break owed for this tick.
     */
    private record Wear(BlockPos pos,
                        BlockState state,
                        Vector3d impact,
                        double impactVelocity,
                        double resistance,
                        double share,
                        boolean contraption) {

        /** The same block owing more. A record, so adding to it is making another one. */
        private Wear plus(final double more) {
            return new Wear(this.pos, this.state, this.impact, this.impactVelocity,
                    this.resistance, this.share + more, this.contraption);
        }
    }

    /** One tick's worth of queued work for one level: what breaks, what wears, and what each hull owes. */
    private static final class Bucket {
        private final List<Break> breaks = new ArrayList<>();

        // Keyed by packed position rather than by the block position itself. A busy tick reports thousands of
        // contacts and all but a handful of them are the same block being hit again, so paying for an
        // immutable copy to ask the question was most of what this class allocated.
        private final LongOpenHashSet claimed = new LongOpenHashSet();
        private final Long2ObjectMap<Wear> worn = new Long2ObjectOpenHashMap<>();
        private final Map<ServerSubLevel, Double> drag = new HashMap<>();
        private final Map<ServerSubLevel, Bounce> rebounds = new HashMap<>();
    }

    /**
     * Where a body broke things this tick, averaged, which is the direction it must not be thrown from.
     *
     * <p>Averaged rather than taken from one contact because a landing touches down along a whole face, and
     * the single contact that happened to be reported first is off at one end of it. The mean of them is the
     * middle of what the build came down on, which is what "away" has to be measured from if the answer is
     * to be a lift rather than a shove off a corner.
     */
    private static final class Bounce {
        private final Vector3d sum = new Vector3d();
        private int count;

        private void add(final Vector3dc where) {
            this.sum.add(where);
            this.count++;
        }

        private Vector3d mean() {
            return this.sum.div(this.count, new Vector3d());
        }
    }

    private static final Map<ServerLevel, Bucket> LEVELS = new WeakHashMap<>();

    /** How many of each kind of leftover a tick may hand to the next one. */
    private static final int MAX_CARRIED = 4096;

    private PendingBreaks() {
    }

    /** @return whether this call is the one that claimed the block, so the caller counts it once. */
    public static boolean queue(final ServerLevel level,
                                final BlockPos pos,
                                final BlockState state,
                                final Vector3d worldImpact,
                                final double impactVelocity,
                                final double resistance,
                                final double overshoot,
                                final boolean contraption,
                                final int bodyId,
                                final double kinetic) {
        final Bucket bucket = LEVELS.computeIfAbsent(level, ignored -> new Bucket());
        if (!bucket.claimed.add(pos.asLong())) {
            return false;
        }
        bucket.breaks.add(new Break(pos.immutable(), state, new Vector3d(worldImpact),
                impactVelocity, resistance, overshoot, contraption, bodyId, kinetic));
        return true;
    }

    /**
     * Records what a block spent surviving an impact it won. Kept per block rather than per contact, because
     * a hull ploughing a wall wins against the same block from dozens of contacts in one tick and the wear is
     * one tick's worth however many times it was reported.
     */
    public static void wear(final ServerLevel level,
                            final BlockPos pos,
                            final BlockState state,
                            final Vector3d worldImpact,
                            final double impactVelocity,
                            final double resistance,
                            final double share,
                            final boolean contraption) {
        if (share <= 0.0 || Double.isNaN(share)) {
            return;
        }
        final Bucket bucket = LEVELS.computeIfAbsent(level, ignored -> new Bucket());
        final long key = pos.asLong();
        final Wear existing = bucket.worn.get(key);
        bucket.worn.put(key, existing != null
                ? existing.plus(share)
                : new Wear(pos.immutable(), state, new Vector3d(worldImpact), impactVelocity, resistance,
                        share, contraption));
    }

    /**
     * Books momentum to be taken off a hull once the step is over.
     *
     * <p>Summed per hull rather than applied per block, because braking reads the hull's current velocity and
     * doing that mid-step would be reading a number the solver is still writing.
     */
    public static void drag(final ServerLevel level,
                            @Nullable final ServerSubLevel subLevel,
                            final double momentum) {
        if (subLevel == null || momentum <= 0.0 || Double.isNaN(momentum)) {
            return;
        }
        LEVELS.computeIfAbsent(level, ignored -> new Bucket()).drag.merge(subLevel, momentum, Double::sum);
    }

    /**
     * Single-player fires this for the client level too, on the render thread. Everything below belongs to
     * the server thread - the queue is filled there, and the blocks and bodies it names are server-side.
     */
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }
        if (!ImpactConfig.enabled()) {
            // Whatever was queued before the switch was thrown is dropped rather than carried, so turning
            // the mod off in a live world stops it inside a tick instead of finishing what it had started.
            LEVELS.remove(level);
            return;
        }

        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final boolean timed = ImpactStats.enabled();
        if (timed) {
            ImpactStats.frame(level);
        }
        CrackTracker.tick(level, tuning);
        BuildDamage.sweep(level, tuning);

        final long started = System.nanoTime();
        final long deadline = started + (long) (ImpactConfig.MAX_TICK_MILLIS.get() * 1.0e6);
        BoundsBatch.open(tuning);
        try {
            breakPass(level, tuning, started, deadline, timed);
        } finally {
            BoundsBatch.close();
        }
        Splitter.resolve(level, tuning);
    }

    /**
     * Everything this tick destroys, inside one open batch.
     *
     * <p>Split out from the tick handler for the sake of that batch and nothing else. What it brackets is
     * every path in the mod that can remove a block, so the bounding boxes Sable would otherwise rebuild
     * once per removal are rebuilt once at the end of all of them - and the try/finally is the whole reason
     * it is worth a method: a batch left open by an exception would defer work that never gets done, which
     * is a build whose collision shape has stopped matching its blocks.
     */
    private static void breakPass(final ServerLevel level,
                                  final ImpactConfig.Tuning tuning,
                                  final long started,
                                  final long deadline,
                                  final boolean timed) {
        int broken = Collapse.tick(level, tuning, deadline);
        broken += ShockWave.resume(level, tuning, deadline);
        broken += Bearing.tick(level, tuning, deadline);

        final Bucket bucket = LEVELS.remove(level);
        if (bucket == null) {
            if (timed) {
                ImpactStats.addBreaks(System.nanoTime() - started, broken);
            }
            return;
        }

        int next = 0;
        while (next < bucket.breaks.size()) {
            if (System.nanoTime() > deadline) {
                break;
            }
            final Break pending = bucket.breaks.get(next++);
            if (level.getBlockState(pending.pos) != pending.state) {
                continue;
            }
            // A contraption block sits out in the plot grid, tens of thousands of blocks from anything the
            // player is looking at, so there is nowhere for its crack overlay to be seen.
            final boolean visible = !pending.contraption;
            if (!CrackTracker.hit(level, pending.pos, pending.overshoot, visible, tuning)) {
                continue;
            }
            if (!shatter(level, pending.pos, pending.state, pending.impact, pending.impactVelocity,
                    pending.resistance, pending.contraption)) {
                continue;
            }
            CrackTracker.spall(level, pending.pos, visible, tuning);
            broken++;
            broken += ShockWave.spread(level, pending.pos, pending.impact, pending.impactVelocity,
                    pending.overshoot, pending.contraption, pending.bodyId, pending.kinetic,
                    tuning, deadline);
        }

        final Iterator<Wear> pendingWear = bucket.worn.values().iterator();
        while (pendingWear.hasNext()) {
            if (System.nanoTime() > deadline) {
                break;
            }
            final Wear worn = pendingWear.next();
            pendingWear.remove();
            // Something already breaking this tick has nothing left to wear down.
            if (bucket.claimed.contains(worn.pos.asLong())
                    || level.getBlockState(worn.pos) != worn.state) {
                continue;
            }
            final boolean visible = !worn.contraption;
            if (!CrackTracker.wear(level, worn.pos, worn.share, visible, tuning)) {
                continue;
            }
            if (!shatter(level, worn.pos, worn.state, worn.impact, worn.impactVelocity,
                    worn.resistance, worn.contraption)) {
                continue;
            }
            CrackTracker.spall(level, worn.pos, visible, tuning);
            broken++;
        }

        applyDrag(bucket.drag);
        applyRebound(bucket.rebounds, tuning);
        carry(level, bucket, next);

        if (timed) {
            ImpactStats.addBreaks(System.nanoTime() - started, broken);
        }
    }

    /**
     * Hands whatever the tick ran out of time for to the next one.
     *
     * <p>The queue is filled during the physics step and drained after it, so nothing else is holding a
     * bucket for this level at this point and the leftovers keep their place at the front of it. Carrying
     * rather than dropping is what makes the deadline above safe to enforce: a block the hull went through
     * is still broken, one tick later, instead of surviving a hit it should not have.
     *
     * <p>Only up to a ceiling, because carrying is a queue and a queue that is filled faster than it drains
     * is a leak. Past that the oldest are kept and the rest let go - a hull that reports more damage in a
     * tick than the server can pay out in a tick has already left the block standing whatever this does.
     */
    private static void carry(final ServerLevel level, final Bucket bucket, final int from) {
        final List<Break> breaks = bucket.breaks.subList(
                from, Math.min(bucket.breaks.size(), from + MAX_CARRIED));
        if (breaks.isEmpty() && bucket.worn.isEmpty()) {
            return;
        }

        final Bucket carried = new Bucket();
        carried.breaks.addAll(breaks);
        for (final Break pending : breaks) {
            carried.claimed.add(pending.pos.asLong());
        }
        for (final Wear worn : bucket.worn.values()) {
            if (carried.worn.size() >= MAX_CARRIED) {
                break;
            }
            carried.worn.put(worn.pos.asLong(), worn);
        }
        LEVELS.put(level, carried);
    }

    /**
     * Routes to the world or plotgrid break path, which differ in where the debris entity is created and in
     * whether the break can be refused - a build has a damage allowance and the world does not.
     *
     * @return whether the block was actually destroyed.
     */
    private static boolean shatter(final ServerLevel level,
                                   final BlockPos pos,
                                   final BlockState state,
                                   final Vector3d impact,
                                   final double impactVelocity,
                                   final double resistance,
                                   final boolean contraption) {
        if (contraption) {
            return BlockScatter.shatterContraptionBlock(level, pos, state, impact, impactVelocity, resistance);
        }
        BlockScatter.shatter(level, pos, state, impact, impactVelocity, resistance);
        return true;
    }

    /**
     * Notes that this body broke something here, so the tick can take back whatever it is thrown away with.
     *
     * <p>Called from inside the step, so like everything else here it only writes something down.
     */
    public static void rebound(final ServerLevel level,
                               @Nullable final ServerSubLevel subLevel,
                               final Vector3d worldImpact) {
        if (subLevel == null) {
            return;
        }
        LEVELS.computeIfAbsent(level, ignored -> new Bucket())
                .rebounds.computeIfAbsent(subLevel, ignored -> new Bounce())
                .add(worldImpact);
    }

    /** Pays out every hull's accumulated drag for the tick. */
    private static void applyDrag(final Map<ServerSubLevel, Double> drag) {
        for (final Map.Entry<ServerSubLevel, Double> entry : drag.entrySet()) {
            brake(entry.getKey(), entry.getValue());
        }
    }

    /** Takes {@code momentum} out of the hull along its own heading. Safe to call on the server tick only. */
    public static void brake(final ServerSubLevel subLevel, final double momentum) {
        if (subLevel == null || subLevel.isRemoved() || momentum <= 0.0 || Double.isNaN(momentum)) {
            return;
        }

        final RigidBodyHandle body = RigidBodyHandle.of(subLevel);
        if (body == null || !body.isValid()) {
            return;
        }

        final Vector3d velocity = new Vector3d();
        body.getLinearVelocity(velocity);
        final double speed = velocity.length();
        if (Double.isNaN(speed) || speed < 1.0e-4) {
            return;
        }

        // Braking is done in velocity, not impulse. An impulse is only as good as the mass we divide it by,
        // and that mass is our own reading of Sable's tracker - if it disagrees with what Rapier is actually
        // simulating, the same impulse that should have slowed the hull sends it back the way it came. Taking
        // speed off directly cannot do that: the worst it can do is stop the hull dead.
        final double hullMass = massOf(subLevel);
        if (hullMass <= 0.0) {
            return;
        }

        // Capped as a share of what the hull has rather than taken outright. The drag is priced per block
        // and a hull ploughing terrain meets hundreds at once, so the honest total routinely exceeds all the
        // motion there is - and taking all of it is a hull that stops dead, is picked back up by gravity,
        // ploughs, and stops dead again, twenty times a second. That is not resistance, it is a stutter, and
        // it is the one thing that looks like the game hitching while the server is keeping perfect time.
        final double lost = ImpactResolver.speedLost(
                momentum, hullMass, speed, ImpactConfig.tuning().breakDragMax());
        body.addLinearAndAngularVelocity(velocity.mul(-lost / speed), NO_SPIN);
    }

    private static final Vector3d NO_SPIN = new Vector3d();

    /** Takes the spring out of every body that broke something this tick. */
    private static void applyRebound(final Map<ServerSubLevel, Bounce> bounces,
                                     final ImpactConfig.Tuning tuning) {
        if (tuning.rebound() >= 1.0 && tuning.reboundSpin() >= 1.0) {
            return;
        }
        for (final Map.Entry<ServerSubLevel, Bounce> entry : bounces.entrySet()) {
            settle(entry.getKey(), entry.getValue().mean(), tuning);
        }
    }

    /**
     * Takes back the speed the solver gave a body away from what it broke, and some of the spin with it.
     *
     * <p>Nothing about how blocks break can reach this. Blocks are removed after the step, so for the whole
     * of the step the build is resolved against what it is destroying, and pushing overlapping things apart
     * is the one thing a solver is for: a hull that has driven a block into the ground is pushed a block back
     * out of it, hardest where it went deepest, which is a shove off a corner rather than a lift. The result
     * is a build that hops and turns over, and it is the same result whether the blocks under it survived.
     *
     * <p>So it is undone here, in velocity, where the whole tick's answer is visible at once - and only along
     * the way out. A build still falls under its own weight, still ploughs forward into what it is cutting,
     * still climbs off a hillside under power; every one of those is motion towards what it broke or across
     * it. What it may not do is come back up off it.
     */
    private static void settle(final ServerSubLevel subLevel,
                               final Vector3d impact,
                               final ImpactConfig.Tuning tuning) {
        if (subLevel.isRemoved()) {
            return;
        }
        final RigidBodyHandle body = RigidBodyHandle.of(subLevel);
        if (body == null || !body.isValid()) {
            return;
        }
        final MassData mass = subLevel.getMassTracker();
        final Vector3dc centre = mass == null || mass.isInvalid() ? null : mass.getCenterOfMass();
        if (centre == null) {
            return;
        }

        final Vector3d away = subLevel.logicalPose()
                .transformPosition(new Vector3d(centre), new Vector3d())
                .sub(impact);
        final double reach = away.length();
        if (Double.isNaN(reach) || reach < 1.0e-4) {
            return;
        }
        away.div(reach);

        final Vector3d velocity = body.getLinearVelocity(new Vector3d());
        final double separating = velocity.dot(away);
        final Vector3d held = Double.isNaN(separating) || separating <= 0.0
                ? NO_SPIN
                : away.mul(-separating * (1.0 - tuning.rebound()));

        final Vector3d spin = body.getAngularVelocity(new Vector3d());
        if (Double.isNaN(spin.lengthSquared())) {
            spin.set(0.0);
        }
        body.addLinearAndAngularVelocity(held, spin.mul(-(1.0 - tuning.reboundSpin())));
    }

    /** The hull's mass, or zero when Sable's tracker has nothing usable - which reads as "do not brake". */
    private static double massOf(final ServerSubLevel subLevel) {
        final MassData mass = subLevel.getMassTracker();
        return mass == null || mass.isInvalid() ? 0.0 : mass.getMass();
    }
}
