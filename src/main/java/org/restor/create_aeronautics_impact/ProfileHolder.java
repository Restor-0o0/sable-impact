package org.restor.create_aeronautics_impact;

import org.jetbrains.annotations.Nullable;

/**
 * A slot on every {@link net.minecraft.world.level.block.state.BlockState} for its {@link BlockProfile}.
 *
 * <p>Block states are singletons baked once at registry time, so a profile derived from one is derivable
 * exactly once. It used to live in a map keyed by the state; the map was correct and still cost a hash and a
 * bucket walk on a path that runs seven times per block for every block of every collider remesh. A field on
 * the state itself costs a read.
 */
public interface ProfileHolder {

    @Nullable BlockProfile create_aeronautics_impact$profile();

    void create_aeronautics_impact$profile(BlockProfile profile);
}
