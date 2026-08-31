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

    public static void clearCache() {
        currentGeneration++;
        FALLBACK.clear();
    }

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

    // Sable exposes sable:fragile as a Veil RegistryObject, which is not on this mod's compile classpath.
    // The registry itself is, so the type is resolved by name once and reused.
    // Resolved on first use rather than in a static initialiser: this class can be loaded from a config
    // event, which happens before Sable's registry is populated.
    private static final class Fragile {
        private static final PhysicsBlockPropertyTypes.PhysicsBlockPropertyType<?> TYPE = resolve();

        private static PhysicsBlockPropertyTypes.PhysicsBlockPropertyType<?> resolve() {
            try {
                return PhysicsBlockPropertyTypes.getPropertyType(
                        ResourceLocation.fromNamespaceAndPath("sable", "fragile"));
            } catch (final RuntimeException notRegistered) {
                return null;
            }
        }
    }

    public static boolean isFragile(final BlockState state) {
        if (Fragile.TYPE == null) {
            return false;
        }
        return Boolean.TRUE.equals(((BlockStateExtension) state).sable$getProperty(Fragile.TYPE));
    }

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
