package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Damage a block carries between impacts, so that a hit which is not quite enough leaves something behind
 * instead of nothing at all.
 *
 * <p>A single threshold answers "did this break" with no memory, which makes a wall either untouched or gone
 * and makes the speed either side of that threshold the only thing that ever mattered. Accumulating means a
 * ram that is marginally too slow still gets somewhere by hitting the same wall repeatedly, and it means the
 * player can see it getting somewhere: the same crack overlay vanilla uses for mining.
 *
 * <p>Damage fades, or a build shuffling against a cliff for ten minutes would eventually flatten it.
 */
public final class CrackTracker {

    /** Ticks between decay passes. Also the amount of healing one pass is worth. */
    private static final int HEAL_INTERVAL = 20;

    private static final Map<ServerLevel, CrackTracker> LEVELS = new WeakHashMap<>();

    private final Long2ObjectMap<Crack> cracks = new Long2ObjectOpenHashMap<>();
    private long healedAt = Long.MIN_VALUE;
    private long budgetTick = Long.MIN_VALUE;
    private int overlaysThisTick;
    private int spallsThisTick;

    private static final class Crack {
        private double damage;
        private int stage = -1;
    }

    private CrackTracker() {
    }

    /**
     * Records a hit that was hard enough to break the block outright under the old all-or-nothing rule.
     *
     * @param overshoot how far past the block's break speed the impact was, as a ratio
     * @param visible   whether the crack overlay is worth sending, which contraption blocks living out in the
     *                  plot grid are not - the player is looking at the rendered contraption, not at them
     * @return whether the block is finished and should be shattered now
     */
    public static boolean hit(final ServerLevel level,
                              final BlockPos pos,
                              final double overshoot,
                              final boolean visible,
                              final ImpactConfig.Tuning tuning) {
        if (!cracking(tuning)) {
            return true;
        }

        final CrackTracker tracker = LEVELS.computeIfAbsent(level, ignored -> new CrackTracker());
        tracker.rollTick(level.getGameTime());
        final double dealt = ImpactResolver.crackDamage(overshoot, tuning.crackResilience());
        return tracker.apply(level, pos, dealt, visible, 1.0, true, tuning);
    }

    /**
     * Records what winning an impact cost the side that won it.
     *
     * <p>Not a hit: the winner was never hit hard enough to break, it is paying for what it did. The share is
     * a fraction of a break rather than a multiple of one, so this can only ever wear something down over many
     * impacts and never destroy it outright. With cracking off there is nowhere to put the damage, so the
     * winner comes through untouched rather than being destroyed on the spot.
     *
     * @return whether the winner has now taken enough and should be shattered too
     */
    public static boolean wear(final ServerLevel level,
                               final BlockPos pos,
                               final double share,
                               final boolean visible,
                               final ImpactConfig.Tuning tuning) {
        if (!cracking(tuning) || share <= 0.0) {
            return false;
        }

        final CrackTracker tracker = LEVELS.computeIfAbsent(level, ignored -> new CrackTracker());
        tracker.rollTick(level.getGameTime());
        // Never outright: wear that cannot be written down anywhere is wear the block simply does not take.
        // Reading it as a break instead would have a full crack map turn the first scratch a winner picked up
        // into the thing that destroyed it, which is the opposite of what wearing something down means.
        return tracker.apply(level, pos, share / tuning.crackResilience(), visible, 1.0, false, tuning);
    }

    /**
     * Spreads a fraction of a finished break into the six blocks around it. Nothing here can break a block on
     * its own, so a crater edge crumbles into cracked stone and stops rather than running away through the
     * hillside one neighbour at a time.
     */
    public static void spall(final ServerLevel level,
                             final BlockPos pos,
                             final boolean visible,
                             final ImpactConfig.Tuning tuning) {
        if (!cracking(tuning) || tuning.crackSpall() <= 0.0) {
            return;
        }

        final CrackTracker tracker = LEVELS.get(level);
        if (tracker == null) {
            return;
        }
        tracker.rollTick(level.getGameTime());
        if (tracker.spallsThisTick >= tuning.maxCrackEffectsPerTick()) {
            return;
        }
        tracker.spallsThisTick++;

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (final Direction direction : Direction.values()) {
            cursor.setWithOffset(pos, direction);
            final BlockState state = stateIfLoaded(level, cursor);
            if (state == null || state.isAir() || BlockProfile.of(level, cursor, state).indestructible()) {
                continue;
            }
            tracker.apply(level, cursor, tuning.crackSpall(), visible, tuning.crackSpallCeiling(),
                    false, tuning);
        }
    }

    public static void tick(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        final CrackTracker tracker = LEVELS.get(level);
        if (tracker != null) {
            tracker.rollTick(level.getGameTime());
            tracker.heal(level, tuning);
        }
    }

    private static boolean cracking(final ImpactConfig.Tuning tuning) {
        return tuning.crackBlocks() && tuning.crackResilience() > 1.0;
    }

    /**
     * @param unrecorded what to answer when the damage has nowhere to be kept - because it finishes the block
     *                   on its own, or because there is no room left to remember it. A hit falls back to
     *                   all-or-nothing there; anything that is only ever a fraction of a break does not.
     * @return whether the block reached {@code breakAt} and should be shattered now.
     */
    private boolean apply(final ServerLevel level,
                          final BlockPos pos,
                          final double dealt,
                          final boolean visible,
                          final double breakAt,
                          final boolean unrecorded,
                          final ImpactConfig.Tuning tuning) {
        final long key = pos.asLong();
        Crack crack = this.cracks.get(key);

        if (crack == null) {
            if (dealt >= breakAt || this.cracks.size() >= tuning.maxCrackedBlocks()) {
                return unrecorded;
            }
            crack = new Crack();
            this.cracks.put(key, crack);
        }

        crack.damage += dealt;
        if (crack.damage >= breakAt) {
            if (breakAt >= 1.0) {
                clearOverlay(level, pos, key, crack);
                this.cracks.remove(key);
                return true;
            }
            crack.damage = breakAt;
        }

        if (visible) {
            sendOverlay(level, pos, key, crack, tuning);
        }
        return false;
    }

    private void heal(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        final long now = level.getGameTime();
        if (now - this.healedAt < HEAL_INTERVAL || this.cracks.isEmpty()) {
            return;
        }
        this.healedAt = now;

        final double recovered = (double) HEAL_INTERVAL / Math.max(1, tuning.crackHealTicks());
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final ObjectIterator<Long2ObjectMap.Entry<Crack>> entries =
                this.cracks.long2ObjectEntrySet().iterator();

        while (entries.hasNext()) {
            final Long2ObjectMap.Entry<Crack> entry = entries.next();
            final long key = entry.getLongKey();
            final Crack crack = entry.getValue();
            crack.damage -= recovered;
            cursor.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));

            final BlockState state = stateIfLoaded(level, cursor);
            if (crack.damage <= 0.0 || state == null || state.isAir()) {
                clearOverlay(level, cursor, key, crack);
                entries.remove();
                continue;
            }
            if (crack.stage >= 0) {
                sendOverlay(level, cursor, key, crack, tuning);
            }
        }
    }

    /**
     * Every overlay is a packet to everyone in range, and a hull ploughing a hillside cracks blocks by the
     * hundred, so the ration is the same idea as the one on break particles: the first ones are seen and the
     * rest catch up on a later tick, since the damage they represent is kept either way.
     */
    private void sendOverlay(final ServerLevel level,
                             final BlockPos pos,
                             final long key,
                             final Crack crack,
                             final ImpactConfig.Tuning tuning) {
        final int stage = (int) Math.clamp((long) (crack.damage * 10.0), 0L, 9L);
        if (stage == crack.stage || this.overlaysThisTick >= tuning.maxCrackEffectsPerTick()) {
            return;
        }
        this.overlaysThisTick++;
        crack.stage = stage;
        level.destroyBlockProgress(breakerId(key), pos, stage);
    }

    private static void clearOverlay(final ServerLevel level, final BlockPos pos, final long key, final Crack crack) {
        if (crack.stage >= 0) {
            crack.stage = -1;
            level.destroyBlockProgress(breakerId(key), pos, -1);
        }
    }

    /**
     * Damage outlives the moment it was dealt, so it can outlive the chunk as well - a contraption's plot
     * unloads the tick after it is gone. Asking the level for that block would load it back, and this runs
     * over thousands of entries at once, so a miss has to stay a miss.
     */
    private static @Nullable BlockState stateIfLoaded(final ServerLevel level, final BlockPos pos) {
        final LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private void rollTick(final long now) {
        if (now != this.budgetTick) {
            this.budgetTick = now;
            this.overlaysThisTick = 0;
            this.spallsThisTick = 0;
        }
    }

    /**
     * Vanilla keys crack overlays by the id of whoever is mining, so two blocks sharing a key would keep
     * erasing each other's. Entity ids are handed out upwards from zero, so the negatives are free.
     */
    private static int breakerId(final long key) {
        final int mixed = (int) (key ^ (key >>> 32));
        return Integer.MIN_VALUE | (mixed & 0x7FFFFFFF);
    }
}
