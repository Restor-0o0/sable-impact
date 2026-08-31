package org.restor.create_aeronautics_impact;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

final class ContactTracker {

    private final Int2ObjectMap<int[]> counts = new Int2ObjectOpenHashMap<>();
    private long tick = Long.MIN_VALUE;

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
