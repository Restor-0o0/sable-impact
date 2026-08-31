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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
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

    private record Break(BlockPos pos,
                         BlockState state,
                         Vector3d impact,
                         double impactVelocity,
                         double resistance,
                         double overshoot,
                         boolean contraption) {
    }

    private record Wear(BlockPos pos,
                        BlockState state,
                        Vector3d impact,
                        double impactVelocity,
                        double resistance,
                        double share,
                        boolean contraption) {

        private Wear plus(final double more) {
            return new Wear(this.pos, this.state, this.impact, this.impactVelocity,
                    this.resistance, this.share + more, this.contraption);
        }
    }

    private static final class Bucket {
        private final List<Break> breaks = new ArrayList<>();

        // Keyed by packed position rather than by the block position itself. A busy tick reports thousands of
        // contacts and all but a handful of them are the same block being hit again, so paying for an
        // immutable copy to ask the question was most of what this class allocated.
        private final LongOpenHashSet claimed = new LongOpenHashSet();
        private final Long2ObjectMap<Wear> worn = new Long2ObjectOpenHashMap<>();
        private final Map<ServerSubLevel, Double> drag = new HashMap<>();
    }

    private static final Map<ServerLevel, Bucket> LEVELS = new WeakHashMap<>();

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
                                final boolean contraption) {
        final Bucket bucket = LEVELS.computeIfAbsent(level, ignored -> new Bucket());
        if (!bucket.claimed.add(pos.asLong())) {
            return false;
        }
        bucket.breaks.add(new Break(pos.immutable(), state, new Vector3d(worldImpact),
                impactVelocity, resistance, overshoot, contraption));
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

        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final boolean timed = ImpactStats.enabled();
        if (timed) {
            ImpactStats.frame(level);
        }
        CrackTracker.tick(level, tuning);

        final Bucket bucket = LEVELS.remove(level);
        if (bucket == null) {
            return;
        }

        final long started = timed ? System.nanoTime() : 0L;
        int broken = 0;

        for (final Break pending : bucket.breaks) {
            if (level.getBlockState(pending.pos) != pending.state) {
                continue;
            }
            // A contraption block sits out in the plot grid, tens of thousands of blocks from anything the
            // player is looking at, so there is nowhere for its crack overlay to be seen.
            final boolean visible = !pending.contraption;
            if (!CrackTracker.hit(level, pending.pos, pending.overshoot, visible, tuning)) {
                continue;
            }
            shatter(level, pending.pos, pending.state, pending.impact, pending.impactVelocity,
                    pending.resistance, pending.contraption);
            CrackTracker.spall(level, pending.pos, visible, tuning);
            broken++;
        }

        for (final Wear worn : bucket.worn.values()) {
            // Something already breaking this tick has nothing left to wear down.
            if (bucket.claimed.contains(worn.pos.asLong())
                    || level.getBlockState(worn.pos) != worn.state) {
                continue;
            }
            final boolean visible = !worn.contraption;
            if (!CrackTracker.wear(level, worn.pos, worn.share, visible, tuning)) {
                continue;
            }
            shatter(level, worn.pos, worn.state, worn.impact, worn.impactVelocity,
                    worn.resistance, worn.contraption);
            CrackTracker.spall(level, worn.pos, visible, tuning);
            broken++;
        }

        applyDrag(bucket.drag);

        if (timed) {
            ImpactStats.addBreaks(System.nanoTime() - started, broken);
        }
    }

    private static void shatter(final ServerLevel level,
                                final BlockPos pos,
                                final BlockState state,
                                final Vector3d impact,
                                final double impactVelocity,
                                final double resistance,
                                final boolean contraption) {
        if (contraption) {
            BlockScatter.shatterContraptionBlock(level, pos, state, impact, impactVelocity, resistance);
        } else {
            BlockScatter.shatter(level, pos, state, impact, impactVelocity, resistance);
        }
    }

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

    private static double massOf(final ServerSubLevel subLevel) {
        final MassData mass = subLevel.getMassTracker();
        return mass == null || mass.isInvalid() ? 0.0 : mass.getMass();
    }
}
