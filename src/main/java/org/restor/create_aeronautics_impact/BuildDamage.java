package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * How much of one build this mod is allowed to destroy, and where the pieces of it actually are.
 *
 * <p>Everything else here decides whether a block <em>should</em> break. Nothing decided how much of one
 * structure may break at once, and the answer turned out to be all of it. A wave and a collapse both walk
 * outwards through whatever is touching, so a single contact on a hollow hull reaches the whole skin - the
 * bottom of a ship is one course of material, and a pass that takes the lowest course of every column takes
 * the ship. What the player sees is not a crash but a corrosion: the hull peels off in a chain from wherever
 * it was touched, and a build that merely landed hard is gone.
 *
 * <p>So a build has a damage budget, and this is where it is kept: how much has been taken from it this tick,
 * how much since the crash began, and how long ago that was. It is asked once per block, immediately before
 * the block is destroyed, by every path in the mod that can destroy one - so a wave, a collapse, a crush and
 * an ordinary break all draw on the same allowance rather than each having their own.
 *
 * <p>It is also the only thing standing between this mod and a hard crash in the library underneath it.
 * Sable splits a sub-level when destroying blocks leaves it in disconnected pieces, and the split is queued
 * rather than immediate; if what is left is annihilated before the split runs, the split assembles into a
 * plot whose sub-level has already been removed and the server dies with
 * {@code Sub-level assembly attempted inside plot of already removed sub-level}. Nothing on this side can
 * make that safe, but a build that is never taken apart faster than a few hundred blocks a tick gives the
 * split time to happen, and a build that is removed mid-pass is noticed here and left alone at once.
 */
public final class BuildDamage {

    private static final Map<ServerLevel, Map<ServerSubLevel, Ledger>> LEDGERS = new WeakHashMap<>();

    /** One build's allowance. Kept per level so a level going away takes its builds with it. */
    private static final class Ledger {
        private int thisTick;
        private int thisImpact;
        private long tick = Long.MIN_VALUE;
    }

    // Blocks arrive in walks - a wave spreads, a collapse runs columns - so consecutive questions are nearly
    // always about the same plot. One slot answers the great majority of them without touching the map.
    private static long cachedChunk = Long.MIN_VALUE;
    private static @Nullable ServerSubLevel cachedOwner;
    private static long cachedTick = Long.MIN_VALUE;

    private BuildDamage() {
    }

    /**
     * Which build owns this position, or null if it is ordinary world terrain.
     *
     * <p>A plot is a whole chunk column of the plotgrid, so the lookup is by chunk and a build the size of a
     * ship answers from one slot for the whole of a pass across it.
     */
    public static @Nullable ServerSubLevel owner(final ServerLevel level, final BlockPos pos) {
        final long now = level.getGameTime();
        final long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (now == cachedTick && key == cachedChunk) {
            return cachedOwner;
        }

        final SubLevel found = Sable.HELPER.getContaining(level, pos);
        cachedOwner = found instanceof final ServerSubLevel server ? server : null;
        cachedChunk = key;
        cachedTick = now;
        return cachedOwner;
    }

    /**
     * Where a block of a build actually is in the world.
     *
     * <p>This is the whole of the debris problem. A build's blocks live tens of thousands of blocks out in
     * the plotgrid, so every path that broke one used the contact point for the debris instead - and since a
     * wave breaks hundreds of blocks from one contact, every one of those pieces was created at the same
     * point. That is the fountain: a hull comes apart all over and the wreckage of it pours out of a single
     * spot. The build's own pose is what turns a plot position back into the place the player is looking at.
     *
     * @param fallback where to put it if the build has gone, which is the contact point as before.
     */
    public static Vector3d where(@Nullable final ServerSubLevel subLevel,
                                 final BlockPos plotPos,
                                 final Vector3d fallback,
                                 final Vector3d dest) {
        if (subLevel == null || subLevel.isRemoved()) {
            return dest.set(fallback);
        }
        dest.set(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        subLevel.logicalPose().transformPosition(dest, dest);
        return dest.isFinite() ? dest : dest.set(fallback);
    }

    /**
     * Draws one block from a build's allowance, and says whether there was one to draw.
     *
     * <p>Terrain has no allowance and never refuses. A build that Sable has already removed refuses whatever
     * the settings say, because writing into its plot is what kills the server.
     *
     * <p>The impact allowance is what separates a crash from a corrosion. It is reset once the build has been
     * left alone for {@code restTicks}, so a ship that lands, comes apart, settles and is later flown into a
     * cliff gets a fresh one - but a ship that lands once cannot lose more than one crash's worth of itself,
     * however many contacts that landing reports and however many waves and fronts they arm.
     */
    public static boolean take(final ServerLevel level,
                               @Nullable final ServerSubLevel subLevel,
                               final ImpactConfig.Tuning tuning) {
        if (subLevel == null) {
            return true;
        }
        if (subLevel.isRemoved()) {
            return false;
        }
        if (!tuning.protectBuilds()) {
            return true;
        }

        final long now = level.getGameTime();
        final Ledger ledger = LEDGERS.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(subLevel, ignored -> new Ledger());

        if (ledger.tick != now) {
            if (now - ledger.tick > tuning.protectRestTicks()) {
                ledger.thisImpact = 0;
            }
            ledger.tick = now;
            ledger.thisTick = 0;
        }
        if (ledger.thisTick >= tuning.protectMaxPerTick()
                || ledger.thisImpact >= tuning.protectMaxPerImpact()) {
            return false;
        }

        ledger.thisTick++;
        ledger.thisImpact++;
        return true;
    }

    /**
     * Drops the ledgers of builds that are gone or have been quiet long enough to have no history worth
     * keeping. Called once a tick from the break pass, which is the only thing that ever writes here.
     */
    public static void sweep(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        final Map<ServerSubLevel, Ledger> builds = LEDGERS.get(level);
        if (builds == null || builds.isEmpty()) {
            return;
        }

        final long now = level.getGameTime();
        final Iterator<Map.Entry<ServerSubLevel, Ledger>> entries = builds.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<ServerSubLevel, Ledger> entry = entries.next();
            if (entry.getKey().isRemoved()
                    || now - entry.getValue().tick > tuning.protectRestTicks()) {
                entries.remove();
            }
        }
        if (builds.isEmpty()) {
            LEDGERS.remove(level);
        }
    }
}
