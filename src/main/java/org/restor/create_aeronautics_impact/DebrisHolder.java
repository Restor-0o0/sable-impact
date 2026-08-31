package org.restor.create_aeronautics_impact;

/**
 * A flag on a {@link net.minecraft.world.entity.item.FallingBlockEntity} saying this mod threw it.
 *
 * <p>Falling blocks are vanilla's own entity and most of them are gravel doing what gravel does. Only the
 * ones an impact threw are allowed the landing this mod gives them, and only those are worth the cost of
 * looking for somewhere to put them - so the entity has to be able to say which it is.
 *
 * <p>The flag is not saved with the entity. A piece of debris still in the air when the world is unloaded
 * comes back as an ordinary falling block, which is the right answer for a crash nobody is watching any more.
 */
public interface DebrisHolder {

    boolean create_aeronautics_impact$debris();

    void create_aeronautics_impact$debris(boolean debris);
}
