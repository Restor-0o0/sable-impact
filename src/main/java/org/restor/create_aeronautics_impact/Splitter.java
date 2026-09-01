package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Telling a build that it is now two builds, on the tick it became true rather than a minute later.
 *
 * <p>Sable decides what is still one structure by flood-filling it, and it spends a fixed few hundred steps a
 * tick on that so the walk never costs anybody a frame. For a build losing a block to a pickaxe that is
 * exactly the right trade. For a build losing a thousand blocks to a crash it is the wrong one, and the
 * difference is visible: the connection is severed on the first tick and found on the fortieth, and what is
 * on the screen in between is a wreck cut cleanly through the middle, hanging in the air in one piece, held
 * up by a search that has not caught up with what happened to it.
 *
 * <p>Nothing was wrong with the severing - every removal this mod makes goes through the level and Sable's
 * own block-change hook sees all of them. What was wrong was the rate. So a build this mod has damaged is put
 * on a list and its flood-fill is run extra times for a while, inside a millisecond budget, and that is the
 * whole of it. The work is Sable's own, unchanged, and it is self-limiting: the moment the search has its
 * answer every further round returns immediately, so the ceiling here is a ceiling and not a workload. A
 * build that has come apart pays for the finding once and then costs nothing until it is hit again.
 *
 * <p>It goes through {@code tick()} rather than around it, so the guard that stops an already-removed
 * sub-level from assembling out of a destroyed plot still applies to every round of it.
 */
public final class Splitter {

    private static final Map<ServerLevel, Map<ServerSubLevel, Long>> DAMAGED = new WeakHashMap<>();

    private Splitter() {
    }

    /**
     * Notes that a build has just lost a block, so its connectivity is worth hurrying. Called once per
     * destroyed block from {@link BuildDamage#take}, which every destroying path in the mod already goes
     * through.
     */
    public static void damaged(final ServerLevel level, final ServerSubLevel subLevel) {
        DAMAGED.computeIfAbsent(level, ignored -> new HashMap<>()).put(subLevel, level.getGameTime());
    }

    /**
     * Runs the extra rounds for every build damaged recently, and forgets the ones that have gone quiet.
     *
     * <p>Called from the break pass once the block writes for the tick are finished and the bounds are
     * consistent again, because a round of this can assemble a new sub-level out of what it finds.
     */
    public static void resolve(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        final Map<ServerSubLevel, Long> builds = DAMAGED.get(level);
        if (builds == null || builds.isEmpty()) {
            return;
        }

        final long now = level.getGameTime();
        final boolean run = tuning.resolveSplits()
                && tuning.splitRounds() > 0
                && SableConfig.SUB_LEVEL_SPLITTING.getAsBoolean();
        final long deadline = System.nanoTime() + (long) (tuning.splitMillis() * 1.0e6);

        final Iterator<Map.Entry<ServerSubLevel, Long>> entries = builds.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<ServerSubLevel, Long> entry = entries.next();
            final ServerSubLevel subLevel = entry.getKey();
            if (subLevel.isRemoved() || now - entry.getValue() > tuning.splitTicks()) {
                entries.remove();
                continue;
            }
            if (!run || System.nanoTime() >= deadline) {
                continue;
            }
            for (int round = 0; round < tuning.splitRounds(); round++) {
                subLevel.getHeatMapManager().tick();
                if (subLevel.isRemoved() || System.nanoTime() >= deadline) {
                    break;
                }
            }
        }
        if (builds.isEmpty()) {
            DAMAGED.remove(level);
        }
    }
}
