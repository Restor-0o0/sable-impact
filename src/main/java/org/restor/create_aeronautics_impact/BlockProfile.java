package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything about a block that a collision needs but that never changes between collisions. Resolving it
 * per contact meant a vanilla hardness lookup, a blast-resistance lookup and a dozen config reads for every
 * one of the hundreds of contacts a hull makes each physics step.
 */
public record BlockProfile(int generation,
                           double resistance,
                           boolean indestructible,
                           boolean fragile,
                           boolean soft,
                           boolean fluid,
                           boolean voxelSolid,
                           boolean voxelFullBlock) {

    // Only reachable where the mixin did not apply, which in practice means a unit test.
    private static final Map<BlockState, BlockProfile> FALLBACK = new ConcurrentHashMap<>();

    private static volatile int currentGeneration;

    /**
     * Drops every cached profile by moving the generation on.
     *
     * <p>Nothing is walked and nothing is freed: a profile from an older generation fails its own check on
     * the next read and is rebuilt in place. That matters because the cache lives on block states, which
     * this class has no list of and no way to enumerate.
     */
    public static void clearCache() {
        currentGeneration++;
        FALLBACK.clear();
    }

    /**
     * The profile for a block state, built once per state per config generation.
     *
     * <p>{@code level} and {@code pos} are only consulted while building. Every property here is a function
     * of the state alone - the shape lookups take a position because vanilla's signatures do, not because
     * the answer varies by it - which is what makes one profile per state correct.
     */
    public static BlockProfile of(final BlockGetter level, final BlockPos pos, final BlockState state) {
        final int wanted = currentGeneration;

        if (state instanceof final ProfileHolder holder) {
            final BlockProfile cached = holder.create_aeronautics_impact$profile();
            if (cached != null && cached.generation == wanted) {
                return cached;
            }
            final BlockProfile built = build(wanted, level, pos, state);
            holder.create_aeronautics_impact$profile(built);
            return built;
        }

        final BlockProfile cached = FALLBACK.get(state);
        if (cached != null && cached.generation == wanted) {
            return cached;
        }
        final BlockProfile built = build(wanted, level, pos, state);
        FALLBACK.put(state, built);
        return built;
    }

    /**
     * Derives a profile: vanilla stats through {@link ImpactResolver}, then whatever
     * {@link MaterialOverrides} has to say on top.
     */
    private static BlockProfile build(final int generation,
                                      final BlockGetter level, final BlockPos pos, final BlockState state) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final MaterialOverrides.Rule rule = MaterialOverrides.of(state);
        final double blastResistance = state.getBlock().getExplosionResistance();

        final double raw = ImpactResolver.resistance(
                state.getDestroySpeed(level, pos), blastResistance,
                tuning.explosionResistanceFactor(), tuning.hardnessWeight());

        final boolean air = state.isAir();
        final boolean emptyShape = !air && state.getCollisionShape(level, pos).isEmpty();
        // Only a block that is nothing but fluid. A waterlogged stair still has its stair to answer for.
        final boolean fluid = emptyShape && !state.getFluidState().isEmpty();
        // A block the config has called soft is one hulls are meant to go through, so it also gives up the
        // collider that would otherwise stop them - being swept away by something you have already bounced
        // off is not what anybody meant by it.
        final boolean soft = !air && rule.soft(emptyShape && !fluid);

        return new BlockProfile(
                generation,
                rule.resistance(ImpactResolver.compress(raw, tuning.resistanceExponent())),
                rule.indestructible(
                        blastResistance >= tuning.indestructibleResistance() || Double.isInfinite(raw)),
                rule.fragile(isFragile(state)),
                soft,
                fluid,
                !air && !soft && (!emptyShape || state.getBlock() instanceof MovingPistonBlock),
                !air && !soft && state.isCollisionShapeFullBlock(level, pos));
    }

    /**
     * Whether the hull goes through this rather than meeting it.
     *
     * <p>Fluids and undergrowth are both nothing to hit, and are one category everywhere the question is
     * whether something is in the way. They part company only over what to do about it: grass in the path is
     * cleared, and a lake is not.
     *
     * <p>Water needs saying explicitly because vanilla rates it the way it rates everything - by how long it
     * takes to mine - and that is a hundred, which put it between iron and obsidian and had a hull entering a
     * lake at speed lose the contest against every cubic metre of it on the way down.
     */
    public boolean passable() {
        return this.soft || this.fluid;
    }

    /**
     * Sable's {@code sable:fragile} property type, resolved by name.
     *
     * <p>Sable exposes it as a Veil {@code RegistryObject}, which is not on this mod's compile classpath;
     * the registry itself is, so the type is looked up by name once and reused.
     *
     * <p>A holder class rather than a static field, so the lookup happens on first use rather than at class
     * load. This class can be loaded from a config event, which happens before Sable's registry is
     * populated, and a null there would be baked in for the rest of the session.
     */
    private static final class Fragile {
        private static final PhysicsBlockPropertyTypes.PhysicsBlockPropertyType<?> TYPE = resolve();

        /** @return the registered type, or null if Sable's registry is not up yet. */
        private static PhysicsBlockPropertyTypes.PhysicsBlockPropertyType<?> resolve() {
            try {
                return PhysicsBlockPropertyTypes.getPropertyType(
                        ResourceLocation.fromNamespaceAndPath("sable", "fragile"));
            } catch (final RuntimeException notRegistered) {
                return null;
            }
        }
    }

    /**
     * Whether Sable itself considers this block fragile, before {@code materialOverrides} gets a say.
     *
     * <p>False whenever Sable's registry is not up, which is the honest answer: without it there is no
     * fragile handling to hand the block back to.
     */
    public static boolean isFragile(final BlockState state) {
        if (Fragile.TYPE == null) {
            return false;
        }
        return Boolean.TRUE.equals(((BlockStateExtension) state).sable$getProperty(Fragile.TYPE));
    }

    /** The block as one side of an impact, fully backed - which is what a contraption's own blocks are. */
    public ImpactResolver.Side side(final double massFactor, final double toughness) {
        return side(massFactor, toughness, 1.0);
    }

    /**
     * The block as one side of an impact, given how hard it is being leant on and what is holding it up.
     *
     * <p>The two multipliers deliberately do not reach the same place. Backing is a statement about this
     * block - a tile out of its frame really is easier to knock out than the same stone in a mountain - so it
     * scales the strength that gets compared and the speed needed to beat it alike. Toughness is not: it is a
     * thumb on the scale for builds, and letting it into the compared strength is what had a wooden hull
     * counting as harder than the stone it was hitting, so wood came through a wall unmarked and the stone
     * went. It sets how fast a hull has to be going to lose a block, and takes no part in deciding which of
     * the two materials was the weaker one.
     */
    public ImpactResolver.Side side(final double massFactor, final double toughness, final double backing) {
        final ImpactConfig.Tuning tuning = ImpactConfig.tuning();
        final double held = this.resistance * backing;
        return new ImpactResolver.Side(
                held,
                ImpactResolver.effectiveBreakSpeed(
                        ImpactResolver.breakSpeed(
                                held * toughness, tuning.minImpactSpeed(), tuning.hardnessScale()),
                        massFactor,
                        tuning.minImpactSpeed(),
                        tuning.crushSpeed()),
                this.indestructible);
    }
}
