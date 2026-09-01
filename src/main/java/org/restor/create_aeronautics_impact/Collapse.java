package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a build does to itself once it has landed: it falls down.
 *
 * <p>Everything else in this mod destroys blocks with the energy of the crash, which gets the crater right and
 * the rest of the build wrong. A wave spends outwards from the impact in every direction at once, so what the
 * player watches is a hull being eaten in rings from the point that touched - and since a wave too big for one
 * tick is put down and picked up on the next, the eating goes on long after the thing has stopped moving. That
 * is not what a large structure does when it comes down. A large structure is held up by its own floor, and
 * when the floor at one end is gone the rest of it folds into the hole, from that end towards the far one,
 * and it is over in a second or two.
 *
 * <p>So this is not an energy model at all. A hard enough landing arms a <b>failure front</b> at the contact,
 * and the front then walks outwards through the build at a fixed number of blocks per tick regardless of what
 * the crash was carrying. What it does where it passes is take out the floor: the lowest blocks of each column
 * measured along whichever way is down, deepest at the contact and tapering to a single course at the rim.
 * Nothing pushes the build afterwards - it is simply no longer standing on anything, and gravity, which was
 * always going to be more convincing than an impulse, does the rest.
 *
 * <p>That taper is the whole of the shape. The end that landed loses three or four courses and drops by that
 * much; the far end loses one and barely moves; so the build tilts into its own wreckage and comes down
 * towards the contact rather than settling flat. Where it lands it hits again, which arms the next front, and
 * a building comes down one storey at a time the way buildings do.
 *
 * <p>It is deliberately crude, because it has to be quick and because being exactly right is worth nothing
 * here. Down is rounded to whichever of the six directions is nearest world down at the moment of the hit, so
 * the whole pass is axis-aligned and a column is a straight line; a column is scanned once, top to bottom,
 * with no notion of what is holding up what. On a hollow build - and every build is hollow - the scan simply
 * skips the rooms and takes the courses it finds, which is why a ship loses its keel and its decks in that
 * order rather than losing the hold it does not have.
 *
 * <p><b>Runs from the level tick, after the physics step</b>, for the same reason breaking does: it writes
 * blocks, and writing a block re-bakes colliders through the library the step is holding.
 */
public final class Collapse {

    private static final Map<ServerLevel, Map<ServerSubLevel, Collapse>> RUNNING = new WeakHashMap<>();

    private final ServerSubLevel subLevel;

    /** Where it was hit, in the build's own plot space, which is where all of its blocks are. */
    private final BlockPos origin;

    /** Which way the build was leaning when it landed, rounded to an axis. Columns run along this. */
    private final Direction down;
    private final Direction across;
    private final Direction along;

    private final double impactVelocity;

    /** The next ring of columns to fail, as a distance from the contact. */
    private int ring;

    /** How far into that ring it got, so a ring interrupted by the build's allowance is resumed, not redone. */
    private int column;

    /** Set when the build's damage allowance refused a block, which ends the front's tick where it stands. */
    private boolean stalled;

    /** The tick this build may be given another front, so one landing is one collapse. */
    private long idleUntil = Long.MIN_VALUE;

    private final ChunkCache chunks = new ChunkCache();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Vector3d where = new Vector3d();

    private Collapse(final ServerSubLevel subLevel, final BlockPos origin,
                     final Direction down, final double impactVelocity) {
        this.subLevel = subLevel;
        this.origin = origin.immutable();
        this.impactVelocity = impactVelocity;
        this.down = down;
        final Direction.Axis vertical = down.getAxis();
        this.across = vertical == Direction.Axis.X ? Direction.UP : Direction.EAST;
        this.along = vertical == Direction.Axis.Z ? Direction.UP : Direction.SOUTH;
    }

    /**
     * Arms a front at a contact hard enough to have knocked the floor out.
     *
     * <p>Called from inside the physics step, so it does nothing but remember where and when: one map lookup
     * for the overwhelming majority of contacts, which arrive at a build already coming down.
     *
     * <p>One front at a time per build. A landing reports contacts in the hundreds and they are all the same
     * landing; and the cooldown afterwards is what makes the storeys separate events rather than one long
     * grind, since the build is still touching what it fell on the whole time it is falling into it.
     */
    public static void impact(final ServerLevel level,
                              @Nullable final ServerSubLevel subLevel,
                              final BlockPos plotPos,
                              final double impactVelocity,
                              final ImpactConfig.Tuning tuning) {
        if (subLevel == null || !tuning.collapse() || subLevel.isRemoved()
                || Math.abs(impactVelocity) < tuning.collapseMinSpeed()) {
            return;
        }

        final Map<ServerSubLevel, Collapse> builds =
                RUNNING.computeIfAbsent(level, ignored -> new HashMap<>());
        final Collapse running = builds.get(subLevel);
        if (running != null && (!running.finished(tuning) || level.getGameTime() < running.idleUntil)) {
            return;
        }

        final Direction down = plotDown(subLevel);
        if (!underside(subLevel, plotPos, down)) {
            return;
        }
        builds.put(subLevel, new Collapse(subLevel, plotPos, down, impactVelocity));
    }

    /**
     * Whether the build was hit somewhere it could plausibly have been standing on.
     *
     * <p>The front is armed from a contact, and a contact does not say whether the build landed on something
     * or flew into it. This is the cheapest thing that tells them apart: a build that comes down is touched
     * below its own centre of mass, one that scrapes a wall is touched level with it, and one that clips an
     * arch overhead is touched above it. Only the first has lost the floor it was standing on, and only the
     * first should fold.
     *
     * <p>Both the centre and the contact are already in the build's own coordinates, so this is a subtraction
     * and a sign - which matters, because it runs from inside the physics step. A build whose mass Sable
     * cannot presently vouch for is allowed to fold; refusing on a missing number would turn the collapse off
     * exactly when a build is coming apart fastest.
     */
    private static boolean underside(final ServerSubLevel subLevel,
                                     final BlockPos plotPos,
                                     final Direction down) {
        final MassData mass = subLevel.getMassTracker();
        final Vector3dc centre = mass == null || mass.isInvalid() ? null : mass.getCenterOfMass();
        if (centre == null) {
            return true;
        }
        return down.getStepX() * (plotPos.getX() + 0.5 - centre.x())
                + down.getStepY() * (plotPos.getY() + 0.5 - centre.y())
                + down.getStepZ() * (plotPos.getZ() + 0.5 - centre.z()) > 0.0;
    }

    /**
     * Advances every front this level is carrying.
     *
     * <p>Run before the tick's own breaks rather than after: a build already coming down is the thing the
     * player is watching, and making it wait behind whatever it has newly scraped is how a collapse turns
     * back into a drizzle.
     *
     * @return how many blocks fell, for the tick's own count.
     */
    public static int tick(final ServerLevel level,
                           final ImpactConfig.Tuning tuning,
                           final long deadline) {
        final Map<ServerSubLevel, Collapse> builds = RUNNING.get(level);
        if (builds == null || builds.isEmpty()) {
            return 0;
        }

        final long now = level.getGameTime();
        int broken = 0;
        int budget = tuning.collapseMaxPerTick();

        final Iterator<Map.Entry<ServerSubLevel, Collapse>> entries = builds.entrySet().iterator();
        while (entries.hasNext()) {
            final Collapse collapse = entries.next().getValue();
            if (collapse.subLevel.isRemoved()) {
                entries.remove();
                continue;
            }
            if (collapse.finished(tuning)) {
                // Kept past the end of the front, because while it is here it is the cooldown.
                if (now >= collapse.idleUntil) {
                    entries.remove();
                }
                continue;
            }
            if (budget <= 0 || System.nanoTime() > deadline) {
                continue;
            }

            final int fell = collapse.advance(level, tuning, budget, deadline);
            broken += fell;
            budget -= fell;
            if (collapse.finished(tuning)) {
                collapse.idleUntil = now + tuning.collapseCooldown();
            }
        }

        if (builds.isEmpty()) {
            RUNNING.remove(level);
        }
        return broken;
    }

    private boolean finished(final ImpactConfig.Tuning tuning) {
        return this.ring > tuning.collapseReach();
    }

    /** Walks the front out by this tick's share of rings, whole rings at a time. */
    private int advance(final ServerLevel level,
                        final ImpactConfig.Tuning tuning,
                        final int budget,
                        final long deadline) {
        this.stalled = false;
        int broken = 0;
        for (int step = 0; step < tuning.collapseSpeed() && !finished(tuning); step++) {
            if (broken >= budget || System.nanoTime() > deadline) {
                break;
            }
            broken += fail(level, this.ring, tuning);
            if (this.stalled) {
                break;
            }
            this.ring++;
        }
        return broken;
    }

    /**
     * One ring of columns, square rather than round.
     *
     * <p>Chebyshev distance because it needs no arithmetic to enumerate and no square root to test, and
     * because the difference between a square front and a round one is invisible under a collapsing building.
     */
    private int fail(final ServerLevel level, final int distance, final ImpactConfig.Tuning tuning) {
        final int columns = distance == 0 ? 1 : 8 * distance;
        int broken = 0;
        while (this.column < columns) {
            broken += columnAt(level, distance, this.column, tuning);
            if (this.stalled) {
                return broken;
            }
            this.column++;
        }
        this.column = 0;
        return broken;
    }

    /**
     * The nth column of a ring, counted rather than nested, so a ring the build's allowance cut short can be
     * picked up next tick at the column it stopped on. Redoing the whole ring instead would find the courses
     * it already took gone and take the next ones down, which is the build eating itself twice.
     *
     * <p>The two sides of the square are laid out first and the two edges between them are interleaved, which
     * only has to be some fixed order, not a pretty one.
     */
    private int columnAt(final ServerLevel level, final int distance, final int index,
                         final ImpactConfig.Tuning tuning) {
        if (distance == 0) {
            return column(level, 0, 0, distance, tuning);
        }
        final int side = 2 * distance + 1;
        if (index < side) {
            return column(level, index - distance, -distance, distance, tuning);
        }
        if (index < 2 * side) {
            return column(level, index - side - distance, distance, distance, tuning);
        }
        final int edge = index - 2 * side;
        return column(level, (edge & 1) == 0 ? -distance : distance,
                1 - distance + (edge >> 1), distance, tuning);
    }

    /**
     * Takes the floor out from under one column of the build.
     *
     * <p>Scanned from below the contact upwards, so a keel that dips under the point that touched is found
     * rather than missed, and air is stepped over rather than stopping the scan: what is wanted is the first
     * few courses of material in this column, and on a hollow build those are separated by the rooms.
     *
     * <p>How many courses is the taper, and the taper is the fold. Directly under the contact the column
     * loses everything it can and that end of the build drops by the whole of it; at the rim it loses one and
     * that end stays where it was.
     */
    private int column(final ServerLevel level,
                       final int a,
                       final int b,
                       final int distance,
                       final ImpactConfig.Tuning tuning) {
        final int bite = Math.max(1, (int) Math.round(
                tuning.collapseBite() * (1.0 - (double) distance / Math.max(1, tuning.collapseReach()))));

        this.cursor.set(this.origin);
        this.cursor.move(this.across, a);
        this.cursor.move(this.along, b);
        this.cursor.move(this.down, tuning.collapseDrop());

        final Direction up = this.down.getOpposite();
        final int depth = tuning.collapseDepth() + tuning.collapseDrop();
        int broken = 0;

        for (int step = 0; step < depth && broken < bite; step++, this.cursor.move(up)) {
            final BlockState state = this.chunks.stateIfLoaded(level, this.cursor);
            if (state == null) {
                break;
            }
            if (state.isAir()) {
                continue;
            }

            final BlockProfile profile = BlockProfile.of(level, this.cursor, state);
            if (profile.indestructible()) {
                break;
            }
            if (profile.passable()) {
                continue;
            }

            final BlockPos fell = this.cursor.immutable();
            this.where.set(fell.getX() + 0.5, fell.getY() + 0.5, fell.getZ() + 0.5);
            this.subLevel.logicalPose().transformPosition(this.where, this.where);
            if (!BlockScatter.shatterContraptionBlock(level, fell, state, this.where,
                    this.impactVelocity, profile.resistance())) {
                this.stalled = true;
                break;
            }
            broken++;
        }
        return broken;
    }

    /**
     * Which way is down, in the build's own coordinates, rounded to the nearest of the six.
     *
     * <p>Rounding is the accuracy this trades for speed, and it costs very little: a build lands more or less
     * the way it was flying, and one that has rolled far enough for the answer to be wrong has bigger
     * problems than which way its columns run.
     */
    private static Direction plotDown(final ServerSubLevel subLevel) {
        final Vector3d local = subLevel.logicalPose()
                .transformNormalInverse(new Vector3d(0.0, -1.0, 0.0), new Vector3d());
        if (!local.isFinite()) {
            return Direction.DOWN;
        }
        if (Math.abs(local.x) >= Math.abs(local.y) && Math.abs(local.x) >= Math.abs(local.z)) {
            return local.x < 0.0 ? Direction.WEST : Direction.EAST;
        }
        if (Math.abs(local.y) >= Math.abs(local.z)) {
            return local.y < 0.0 ? Direction.DOWN : Direction.UP;
        }
        return local.z < 0.0 ? Direction.NORTH : Direction.SOUTH;
    }

}
