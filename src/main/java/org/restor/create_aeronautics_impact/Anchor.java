package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeping the ground under a build loaded for as long as the build is standing on it.
 *
 * <p>Sable will only tick a build in chunks the server is ticking blocks in - simulation distance, not render
 * distance. A build that drifts past that edge is not paused; it is serialised out whole into a holding chunk,
 * taken out of the container, and its plot torn down. Everything in it stops existing for the client, which is
 * what a hull vanishing mid-air looks like from the outside. When a chunk under it comes back the whole build
 * is read in again by {@code SubLevelSerializer.fullyLoad}, on the server thread, inside the level tick, in one
 * go - which for a big ship is the several-second stall that ends with the ship reappearing.
 *
 * <p>Two things make that much worse than it sounds. The first is that the two distances are set separately and
 * chunks are commonly loaded well past the last one that block-ticks, so the edge a build falls off is invisible
 * - the terrain is right there on screen, and the ship on it is not. The second is that Sable does not unload
 * one build. {@code moveToUnloaded} walks {@code getLoadingDependencyChain}, the transitive closure of
 * bounding-box overlap, and serialises out everything it reaches. Builds parked touching each other are one
 * cluster, and one column under any of them failing takes the whole cluster out in the same tick. That is why
 * activating one build can make every other build near it disappear at once, and why the thing that falls onto
 * where they were falls through: they are not in the world any more, so there is nothing to hit. They come back
 * at the pose they were saved at, which by then is inside whatever landed on top of them.
 *
 * <p>So this holds a chunk ticket on the columns under a build, at block-ticking level, which is the same level
 * Sable's own test asks about. The ticket has a lifespan of its own, so nothing here can leak a force-loaded
 * chunk through a crash or a missed removal: stop refreshing it and it is gone within two seconds, whatever
 * happened to the code that took it out.
 *
 * <p>Keep, never fetch. A region ticket at block-ticking level does not merely hold a chunk that is there; on
 * one that is not, it is the whole of what {@code /forceload} does, and the chunk is generated. A wreck falling
 * out over unvisited ground would then pull in every column it passed over, and the generation that follows
 * puts the server further behind than the stall it was avoiding - which pushes ticket levels out slower, which
 * makes Sable's test fail sooner, which unloads builds at a shorter distance than before any of this. So every
 * column is checked against {@code getChunkNow} first and skipped if it is not already resident. What is not
 * loaded is not this mod's to load; a build over unloaded ground goes back to being Sable's problem, handled
 * the way it always was.
 */
public final class Anchor {

    /**
     * Two seconds, refreshed every tick a build is still held. This is the safety net rather than the
     * mechanism - what decides how long a build is anchored is how long it stays on the list below.
     */
    private static final TicketType<UUID> WRECK =
            TicketType.create("create_aeronautics_impact_wreck", UUID::compareTo, 40);

    /** Block-ticking is level 32, which is a region radius of one. Exactly what Sable's test asks for. */
    private static final int RADIUS = 1;

    private static final Map<ServerLevel, Map<ServerSubLevel, Long>> HELD = new WeakHashMap<>();

    private static final ObjectArrayList<ServerSubLevel> ORDER = new ObjectArrayList<>();
    private static final ObjectOpenHashSet<ServerSubLevel> ANCHORED = new ObjectOpenHashSet<>();
    private static final LongOpenHashSet COLUMNS = new LongOpenHashSet();

    private Anchor() {
    }

    /**
     * Notes that a build has just lost a block. Called from {@link BuildDamage#take}, which every destroying
     * path in the mod already goes through.
     */
    public static void damaged(final ServerLevel level, final ServerSubLevel subLevel) {
        HELD.computeIfAbsent(level, ignored -> new HashMap<>()).put(subLevel, level.getGameTime());
    }

    /**
     * Refreshes the tickets under everything worth holding, and forgets the builds that have gone quiet.
     *
     * <p>Called at the end of the break pass, after the tick's block writes, because the bounding boxes this
     * reads are the ones those writes moved.
     */
    public static void resolve(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        final Map<ServerSubLevel, Long> recent = HELD.get(level);
        if (recent != null) {
            final long now = level.getGameTime();
            final Iterator<Map.Entry<ServerSubLevel, Long>> entries = recent.entrySet().iterator();
            while (entries.hasNext()) {
                final Map.Entry<ServerSubLevel, Long> entry = entries.next();
                if (entry.getKey().isRemoved() || now - entry.getValue() > tuning.anchorTicks()) {
                    entries.remove();
                }
            }
            if (recent.isEmpty()) {
                HELD.remove(level);
            }
        }

        if (!tuning.anchor()) {
            return;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        ORDER.clear();
        if (recent != null && !recent.isEmpty()) {
            ORDER.addAll(recent.keySet());
            // Damaged first, most recently damaged of those first, because a build in the middle of coming
            // apart is the one a reload costs the most and the one the caps should spend themselves on.
            ORDER.sort(Comparator.comparingLong((final ServerSubLevel held) -> recent.get(held)).reversed());
        }
        final int damaged = ORDER.size();
        if (tuning.anchorAll()) {
            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (!subLevel.isRemoved() && (recent == null || !recent.containsKey(subLevel))) {
                    ORDER.add(subLevel);
                }
            }
        }
        if (ORDER.isEmpty()) {
            return;
        }

        ANCHORED.clear();
        COLUMNS.clear();
        int builds = 0;
        for (int index = 0; index < ORDER.size(); index++) {
            final ServerSubLevel subLevel = ORDER.get(index);
            if (ANCHORED.contains(subLevel)) {
                continue;
            }
            if (builds >= tuning.anchorBuilds() || COLUMNS.size() >= tuning.anchorTotal()) {
                break;
            }
            // A cluster is taken whole or not at all. Half of one is worth nothing: Sable unloads the closure,
            // so a member left unheld takes the anchored ones out with it the moment it fails the test.
            for (final ServerSubLevel member : cluster(subLevel, tuning.anchorChain() && index < damaged)) {
                if (ANCHORED.add(member) && hold(level, member, tuning)) {
                    builds++;
                }
            }
        }
        ORDER.clear();
        ANCHORED.clear();
        ImpactStats.addAnchored(builds, COLUMNS.size());
    }

    /**
     * The set Sable would serialise out together if this one failed its test, or just this one.
     *
     * <p>Only worth paying for on a build that is actually coming apart. When everything in the level is being
     * anchored anyway the closure is already covered, and walking it per build per tick is a broadphase query
     * apiece for an answer that changes nothing.
     */
    private static Iterable<ServerSubLevel> cluster(final ServerSubLevel subLevel, final boolean chain) {
        return chain ? SubLevelHelper.getLoadingDependencyChain(subLevel) : List.of(subLevel);
    }

    private static boolean hold(final ServerLevel level, final ServerSubLevel subLevel,
                                final ImpactConfig.Tuning tuning) {
        final BoundingBox3dc bounds = subLevel.boundingBox();
        final int minX = Mth.floor(bounds.minX() - 1.0) >> 4;
        final int maxX = Mth.floor(bounds.maxX() + 1.0) >> 4;
        final int minZ = Mth.floor(bounds.minZ() - 1.0) >> 4;
        final int maxZ = Mth.floor(bounds.maxZ() + 1.0) >> 4;

        // A build wide enough to need more columns than this is one that holding still would cost more than
        // dropping, so it is left to Sable to handle the way it always did.
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > tuning.anchorChunks()) {
            return false;
        }

        final UUID id = subLevel.getUniqueId();
        boolean any = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                final long column = ChunkPos.asLong(x, z);
                if (COLUMNS.size() >= tuning.anchorTotal() && !COLUMNS.contains(column)) {
                    continue;
                }
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    continue;
                }
                level.getChunkSource().addRegionTicket(WRECK, new ChunkPos(x, z), RADIUS, id);
                COLUMNS.add(column);
                any = true;
            }
        }
        return any;
    }
}
