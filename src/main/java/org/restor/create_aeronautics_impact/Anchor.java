package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeping the ground under a wreck loaded for as long as the wreck is still coming apart.
 *
 * <p>Sable will only tick a build in chunks the server is ticking blocks in - simulation distance, not render
 * distance. A build that drifts past that edge is not paused; it is serialised out whole into a holding chunk,
 * taken out of the container, and its plot torn down. Everything in it stops existing for the client, which is
 * what a hull vanishing mid-air looks like from the outside. When a chunk under it comes back the whole build
 * is read in again by {@code SubLevelSerializer.fullyLoad}, on the server thread, inside the level tick, in one
 * go - which for a big ship is the several-second stall that ends with the ship reappearing.
 *
 * <p>That is a fine trade for a parked airship nobody is looking at. It is a bad one in the ten seconds after a
 * crash, because those are exactly the ten seconds this mod is removing blocks, walking connectivity and
 * assembling halves - and a build serialised out in the middle of that comes back with all of it still to do,
 * having spent a stall to get there. It is also the window in which a wreck is most likely to cross the line,
 * since it is falling away from wherever the player is standing.
 *
 * <p>So a build this mod has damaged keeps a chunk ticket on the columns under it, at block-ticking level,
 * which is the same level Sable's own test asks about. The ticket has a lifespan of its own, so nothing here
 * can leak a force-loaded chunk through a crash or a missed removal: stop refreshing it and it is gone within
 * two seconds, whatever happened to the code that took it out.
 *
 * <p>Keep, never fetch. A region ticket at block-ticking level does not merely hold a chunk that is there; on
 * one that is not, it is the whole of what {@code /forceload} does, and the chunk is generated. A wreck falling
 * out over unvisited ground would then pull in every column it passed over, and the generation that follows
 * puts the server further behind than the stall it was avoiding - which pushes ticket levels out slower, which
 * makes Sable's test fail sooner, which unloads builds at a shorter distance than before any of this. So every
 * column is checked against {@code getChunkNow} first and skipped if it is not already resident. What is not
 * loaded is not this mod's to load; a wreck over unloaded ground goes back to being Sable's problem, handled
 * the way it always was.
 */
public final class Anchor {

    /**
     * Two seconds, refreshed every tick a build is still held. This is the safety net rather than the
     * mechanism - what decides how long a wreck is anchored is how long it stays on the list below.
     */
    private static final TicketType<UUID> WRECK =
            TicketType.create("create_aeronautics_impact_wreck", UUID::compareTo, 40);

    /** Block-ticking is level 32, which is a region radius of one. Exactly what Sable's test asks for. */
    private static final int RADIUS = 1;

    private static final Map<ServerLevel, Map<ServerSubLevel, Long>> HELD = new WeakHashMap<>();

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
     * Refreshes the tickets for every build damaged recently, and drops the ones that have gone quiet.
     *
     * <p>Called at the end of the break pass, after the tick's block writes, because the bounding box this
     * reads is the one those writes moved.
     */
    public static void resolve(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        final Map<ServerSubLevel, Long> builds = HELD.get(level);
        if (builds == null || builds.isEmpty()) {
            return;
        }

        final long now = level.getGameTime();
        final List<ServerSubLevel> live = new ArrayList<>(builds.size());
        final Iterator<Map.Entry<ServerSubLevel, Long>> entries = builds.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<ServerSubLevel, Long> entry = entries.next();
            if (entry.getKey().isRemoved() || now - entry.getValue() > tuning.anchorTicks()) {
                entries.remove();
                continue;
            }
            live.add(entry.getKey());
        }
        if (builds.isEmpty()) {
            HELD.remove(level);
        }
        if (!tuning.anchor() || live.isEmpty()) {
            return;
        }

        // A battle drops more wrecks than a crash does, and the ones still losing blocks are the ones a stall
        // would cost the most. Sorting only when the cap actually bites keeps this off the common path.
        if (live.size() > tuning.anchorBuilds()) {
            live.sort(Comparator.comparingLong((final ServerSubLevel held) -> builds.get(held)).reversed());
        }

        int anchored = 0;
        int columns = 0;
        for (final ServerSubLevel subLevel : live) {
            if (anchored >= tuning.anchorBuilds()) {
                break;
            }
            final int held = hold(level, subLevel, tuning.anchorChunks());
            if (held > 0) {
                anchored++;
                columns += held;
            }
        }
        ImpactStats.addAnchored(anchored, columns);
    }

    private static int hold(final ServerLevel level, final ServerSubLevel subLevel, final int budget) {
        final BoundingBox3dc bounds = subLevel.boundingBox();
        final int minX = Mth.floor(bounds.minX() - 1.0) >> 4;
        final int maxX = Mth.floor(bounds.maxX() + 1.0) >> 4;
        final int minZ = Mth.floor(bounds.minZ() - 1.0) >> 4;
        final int maxZ = Mth.floor(bounds.maxZ() + 1.0) >> 4;

        // A build wide enough to need more columns than this is one that holding still would cost more than
        // dropping, so it is left to Sable to handle the way it always did.
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > budget) {
            return 0;
        }

        final UUID id = subLevel.getUniqueId();
        int held = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    continue;
                }
                level.getChunkSource().addRegionTicket(WRECK, new ChunkPos(x, z), RADIUS, id);
                held++;
            }
        }
        return held;
    }
}
