package org.restor.create_aeronautics_impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A hard enough impact is felt past the block it broke")
final class ShockWaveTest {

    private static final double MIN_OVERSHOOT = 2.0;
    private static final double SCALE = 8.0;
    private static final double COST = 1.0;
    private static final double FALLOFF = 0.94;

    /** Roughly what the live numbers make of these once the resistance curve has been applied. */
    private static final double WOOD = 1.025;
    private static final double STONE = 1.449;

    private static double energy(final double overshoot) {
        return ImpactResolver.shockEnergy(overshoot, MIN_OVERSHOOT, SCALE);
    }

    /** How many blocks of one material a wave gets through before it dies. */
    private static int reach(final double overshoot, final double resistance) {
        double left = energy(overshoot);
        int blocks = 0;
        while (left > 0.0 && blocks < 10_000) {
            left = ImpactResolver.shockStep(left, resistance, COST, FALLOFF);
            if (left > 0.0) {
                blocks++;
            }
        }
        return blocks;
    }

    @Test
    @DisplayName("scraping sends nothing")
    void aGlancingBreakStaysWhereItHappened() {
        assertEquals(0.0, energy(1.0));
        assertEquals(0.0, energy(MIN_OVERSHOOT));
        assertTrue(energy(MIN_OVERSHOOT + 0.01) > 0.0);
    }

    @Test
    @DisplayName("falling from orbit sends a great deal")
    void aCrashCarriesFurtherThanACrash() {
        assertTrue(reach(20.0, STONE) > reach(5.0, STONE));
        assertTrue(reach(20.0, STONE) > 10);
    }

    @Test
    @DisplayName("a wave crosses soft material further than hard")
    void materialIsWhatTheWaveSpends() {
        assertTrue(reach(15.0, WOOD) > reach(15.0, STONE));
    }

    @Test
    @DisplayName("distance alone stops a wave that costs nothing")
    void nothingRunsForever() {
        assertTrue(reach(1000.0, 0.0) < 400);
    }

    @Test
    @DisplayName("a wave that keeps everything it has still ends on material")
    void withoutFalloffMaterialIsTheOnlyBrake() {
        double left = energy(20.0);
        int blocks = 0;
        while (left > 0.0) {
            left = ImpactResolver.shockStep(left, STONE, COST, 1.0);
            blocks++;
        }
        assertEquals((int) Math.ceil(energy(20.0) / STONE), blocks);
    }

    @Test
    @DisplayName("a block that costs more than is left holds")
    void theWaveStopsAtWhatItCannotAfford() {
        assertEquals(0.0, ImpactResolver.shockStep(1.0, 100.0, COST, FALLOFF));
        assertEquals(0.0, ImpactResolver.shockStep(0.0, 0.0, COST, FALLOFF));
    }

    @Test
    @DisplayName("turning the dial off turns the shock off")
    void zeroScaleIsNoShock() {
        assertEquals(0.0, ImpactResolver.shockEnergy(100.0, MIN_OVERSHOOT, 0.0));
    }
}
