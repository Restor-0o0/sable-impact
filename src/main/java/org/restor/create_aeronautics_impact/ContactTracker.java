package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * How many blocks of a contraption are touching the world, per sub-level, per tick.
 *
 * <p>Mass on its own says nothing about whether something will break the ground: a hull is heavy because it
 * is large, and a large hull spreads that weight over a large footprint. What decides the outcome is load per
 * block of contact, so the contact count is the denominator the whole mass model is built on.
 *
 * <p>It cannot be measured up front. Contacts arrive one callback at a time over several physics sub-steps,
 * so the count for the current tick is only complete once the tick is over - by which point every decision
 * that needed it has been made. So the previous tick's total stands in, which is right often enough: contact
 * area changes over the course of a landing, not between two frames.
 */
final class ContactTracker {

    private final Int2ObjectMap<int[]> counts = new Int2ObjectOpenHashMap<>();
    private long tick = Long.MIN_VALUE;

    /**
     * Counts one contact and answers with the best estimate of the sub-level's contact area available now.
     *
     * <p>Never zero: a first-ever contact would otherwise divide the hull's whole mass by nothing.
     */
    int recordAndEstimateArea(final int subLevelId, final long gameTime) {
        if (gameTime != this.tick) {
            rollOver(gameTime);
        }

        int[] entry = this.counts.get(subLevelId);
        if (entry == null) {
            entry = new int[2];
            this.counts.put(subLevelId, entry);
        }
        entry[1]++;

        // A full tick of contacts is a better area estimate than the partial count so far,
        // so lean on the previous tick until this one has any history to offer.
        return Math.max(1, entry[0] > 0 ? entry[0] : entry[1]);
    }

    /**
     * Moves this tick's tallies into the previous-tick slot and drops sub-levels that reported nothing.
     *
     * <p>Dropping them is what keeps this from being a leak: sub-level ids are handed out per contraption and
     * a world can churn through them, so an entry that goes quiet for a tick is forgotten rather than kept
     * against the chance that the same id comes back.
     */
    private void rollOver(final long gameTime) {
        this.tick = gameTime;
        final ObjectIterator<int[]> values = this.counts.values().iterator();
        while (values.hasNext()) {
            final int[] entry = values.next();
            entry[0] = entry[1];
            entry[1] = 0;
            if (entry[0] == 0) {
                values.remove();
            }
        }
    }
}
