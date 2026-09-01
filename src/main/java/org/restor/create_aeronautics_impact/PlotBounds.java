package org.restor.create_aeronautics_impact;

/**
 * A plot chunk that can be told to work out its bounding box again, later.
 *
 * <p>Sable's {@code PlotChunkHolder} keeps the box privately and rebuilds it by scanning the chunk, and the
 * rebuild is {@code protected}. This is what a mixin adds to it so the rebuild can be asked for from
 * outside, which is the whole of what {@link BoundsBatch} needs.
 */
public interface PlotBounds {

    /** Scans the chunk and replaces its bounding box. Expensive; call once per chunk per tick at most. */
    void create_aeronautics_impact$rebuildBounds();
}
