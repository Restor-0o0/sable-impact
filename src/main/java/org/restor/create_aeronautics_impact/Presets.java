package org.restor.create_aeronautics_impact;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.Map;

import static org.restor.create_aeronautics_impact.ImpactConfig.*;

/**
 * What the two preset dials actually change.
 *
 * <p>A preset only ever speaks for a setting that is still at its default. The moment a server owner writes
 * their own number into the file that number wins, whatever either preset says, so a preset is a different
 * set of defaults rather than a lock on the file.
 *
 * <p>The performance preset gives absolute values; the destruction preset gives factors, and a factor is
 * applied on top of whatever the performance preset settled on. That is what lets the two be chosen
 * independently: a fast server can still be a brutal one.
 */
final class Presets {

    private static final Map<ModConfigSpec.ConfigValue<?>, Object> BALANCED = balanced();
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> FAST = fast();
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> GENTLE = gentle();
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> LIGHT = light();
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> HEAVY = heavy();
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> DEVASTATING = devastating();

    private Presets() {
    }

    /** The value a setting left at its default takes under the active presets. */
    static Object resolve(final ModConfigSpec.ConfigValue<?> value, final Object base) {
        final Object chosen = performance(PERFORMANCE_PRESET.get()).getOrDefault(value, base);
        final Object factor = destruction(DESTRUCTION_PRESET.get()).get(value);
        if (factor == null) {
            return chosen;
        }
        if (factor instanceof Boolean) {
            return factor;
        }
        final double scale = (Double) factor;
        if (chosen instanceof Integer whole) {
            return (int) Math.max(1, Math.round(whole * scale));
        }
        if (chosen instanceof Double real) {
            return real * scale;
        }
        return chosen;
    }

    /** The multiplier the destruction preset puts on {@code impactStrength}, touched or not. */
    static double strength(final DestructionPreset preset) {
        return switch (preset) {
            case GENTLE -> 0.4;
            case LIGHT -> 0.7;
            case NORMAL -> 1.0;
            case HEAVY -> 1.6;
            case DEVASTATING -> 3.0;
        };
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> performance(final PerformancePreset preset) {
        return switch (preset) {
            case QUALITY -> Map.of();
            case BALANCED -> BALANCED;
            case FAST -> FAST;
        };
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> destruction(final DestructionPreset preset) {
        return switch (preset) {
            case GENTLE -> GENTLE;
            case LIGHT -> LIGHT;
            case NORMAL -> Map.of();
            case HEAVY -> HEAVY;
            case DEVASTATING -> DEVASTATING;
        };
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> balanced() {
        final Map<ModConfigSpec.ConfigValue<?>, Object> map = new HashMap<>();
        map.put(CONTACT_DETAIL, ContactDetail.RIM);
        map.put(MAX_CONTACTS_PER_TICK, 4096);
        map.put(BACKING_MEMO_TICKS, 2);
        map.put(MOVING_CRUSH_INTERVAL, 2);
        map.put(MAX_QUIET_TICKS, 40);
        map.put(MAX_FALL_QUIET_TICKS, 200);
        map.put(MAX_SCATTER_PER_TICK, 64);
        map.put(SHOCK_MAX_TICKS, 30);
        map.put(STRESS_FLOOR, 0.08);
        map.put(STRESS_MAX_SCAN, 16000);
        map.put(GLASS_SCAN_BUDGET, 12000);
        map.put(SPLIT_MILLIS, 2.0);
        map.put(SEVER_MILLIS, 1.5);
        map.put(BEARING_INTERVAL, 6);
        map.put(ANCHOR_TOTAL, 160);
        return Map.copyOf(map);
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> fast() {
        final Map<ModConfigSpec.ConfigValue<?>, Object> map = new HashMap<>();
        map.put(CONTACT_DETAIL, ContactDetail.RIM);
        map.put(MOVING_CRUSH_INTERVAL, 4);
        map.put(SCATTER_CHANCE, 0.15);
        map.put(CONTRAPTION_SCATTER_CHANCE, 0.2);
        map.put(SETTLE_SHARE, 0.5);
        map.put(MAX_SCATTER_PER_TICK, 32);
        map.put(LANDING_SEARCH, 1);
        map.put(DEBRIS_MODE, DebrisMode.SETTLE);
        map.put(MAX_SETTLE_PER_TICK, 128);
        map.put(HULL_SHOCK_SCALE, 1.5);
        map.put(TERRAIN_SHOCK_SCALE, 0.5);
        map.put(SHOCK_KINETIC_SCALE, 0.4);
        map.put(FRACTURE_COUNT, 1);
        map.put(FRACTURE_WANDER, 0);
        map.put(FRACTURE_GAP, 3);
        map.put(FRACTURE_SCAN, 24);
        map.put(FRACTURE_FLOOR, 64);
        map.put(FRACTURE_NECK, 6);
        map.put(FRACTURE_NECK_SPAN, 3);
        map.put(SHOCK_FALLOFF, 0.9);
        map.put(SHOCK_MAX_PER_IMPACT, 2048);
        map.put(SHOCK_MAX_PER_TICK, 2048);
        map.put(SHOCK_MAX_TICKS, 20);
        map.put(COLLAPSE_DEPTH, 12);
        map.put(COLLAPSE_MAX_PER_TICK, 768);
        map.put(SPLIT_ROUNDS, 8);
        map.put(SPLIT_MILLIS, 1.5);
        map.put(SEVER_INTERVAL, 40);
        map.put(SEVER_VOLUME, 262144);
        map.put(SEVER_NECK, 12);
        map.put(SEVER_MILLIS, 1.0);
        map.put(BEARING_MARGIN, 2);
        map.put(BEARING_DROP, 12);
        map.put(BEARING_RISE, 4);
        map.put(BEARING_ROUNDS, 3);
        map.put(BEARING_INTERVAL, 10);
        map.put(BEARING_REGIONS_PER_TICK, 1);
        map.put(BEARING_MAX_PER_TICK, 192);
        map.put(PROTECT_MAX_PER_TICK, 128);
        map.put(PROTECT_MAX_PER_IMPACT, 1500);
        map.put(STRESS_INTENSITY_SCALE, 0.012);
        map.put(STRESS_FLOOR, 0.15);
        map.put(STRESS_MAX_SCAN, 8000);
        map.put(GLASS_REACH, 32);
        map.put(GLASS_SCAN_BUDGET, 6000);
        map.put(GLASS_MAX_PER_IMPACT, 192);
        map.put(GLASS_MAX_RUNS, 1);
        map.put(ANCHOR_TICKS, 100);
        map.put(ANCHOR_CHUNKS, 24);
        map.put(ANCHOR_BUILDS, 10);
        map.put(ANCHOR_TOTAL, 96);
        map.put(CLEAR_SOFT_BLOCKS, false);
        map.put(SWEEP_FINEST_DETAIL, 1);
        map.put(MAX_QUIET_TICKS, 60);
        map.put(MAX_FALL_QUIET_TICKS, 300);
        map.put(BACKING_MEMO_TICKS, 4);
        map.put(MAX_CONTACTS_PER_TICK, 2048);
        map.put(BLOCK_UPDATES, false);
        return Map.copyOf(map);
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> gentle() {
        final Map<ModConfigSpec.ConfigValue<?>, Object> map = new HashMap<>();
        map.put(CRUSH_BLOCKS, false);
        map.put(PUNCH_THROUGH, false);
        map.put(FRACTURE, false);
        map.put(COLLAPSE, false);
        map.put(SHOCK_KINETIC_SCALE, 0.3);
        map.put(HULL_SHOCK_SCALE, 0.5);
        map.put(TERRAIN_SHOCK_SCALE, 0.4);
        map.put(STRESS_INTENSITY_SCALE, 0.4);
        map.put(PROTECT_MAX_PER_IMPACT, 0.2);
        map.put(SCATTER_CHANCE, 0.5);
        map.put(CONTRAPTION_SCATTER_CHANCE, 0.5);
        return Map.copyOf(map);
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> light() {
        final Map<ModConfigSpec.ConfigValue<?>, Object> map = new HashMap<>();
        map.put(SHOCK_KINETIC_SCALE, 0.6);
        map.put(HULL_SHOCK_SCALE, 0.7);
        map.put(TERRAIN_SHOCK_SCALE, 0.7);
        map.put(STRESS_INTENSITY_SCALE, 0.7);
        map.put(FRACTURE_COUNT, 0.5);
        map.put(PROTECT_MAX_PER_IMPACT, 0.5);
        return Map.copyOf(map);
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> heavy() {
        final Map<ModConfigSpec.ConfigValue<?>, Object> map = new HashMap<>();
        map.put(SHOCK_KINETIC_SCALE, 1.6);
        map.put(HULL_SHOCK_SCALE, 1.4);
        map.put(TERRAIN_SHOCK_SCALE, 1.6);
        map.put(STRESS_INTENSITY_SCALE, 1.4);
        map.put(PROTECT_MAX_PER_IMPACT, 2.0);
        map.put(PROTECT_MAX_PER_TICK, 1.5);
        map.put(MAX_BLOCKS_PER_TICK, 1.5);
        return Map.copyOf(map);
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> devastating() {
        final Map<ModConfigSpec.ConfigValue<?>, Object> map = new HashMap<>();
        map.put(SHOCK_KINETIC_SCALE, 3.0);
        map.put(HULL_SHOCK_SCALE, 2.0);
        map.put(TERRAIN_SHOCK_SCALE, 2.5);
        map.put(STRESS_INTENSITY_SCALE, 2.0);
        map.put(FRACTURE_COUNT, 1.5);
        map.put(PROTECT_MAX_PER_IMPACT, 5.0);
        map.put(PROTECT_MAX_PER_TICK, 2.0);
        map.put(MAX_BLOCKS_PER_TICK, 2.0);
        map.put(SHOCK_MAX_PER_IMPACT, 2.0);
        map.put(COLLAPSE_MIN_SPEED, 0.75);
        return Map.copyOf(map);
    }
}
