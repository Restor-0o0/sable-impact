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
    private static final double FALLOFF = 0.98;

    /** Roughly what the live numbers make of these once the resistance curve has been applied. */
    private static final double WOOD = 1.025;
    private static final double STONE = 1.449;

    /** Sable weighs a plain block at 1 kg and stone at 2 kg, so a hull is a few tonnes at most. */
    private static final double SHIP = 8_000.0;
    private static final double BOULDER = 200.0;

    private static double energy(final double overshoot) {
        return ImpactResolver.shockEnergy(overshoot, MIN_OVERSHOOT, SCALE);
    }

    private static double price(final double resistance, final int distance, final double falloff) {
        return ImpactResolver.shockCost(resistance, COST, Math.pow(1.0 / falloff, distance));
    }

    /** How deep one straight run of a material gets, which is as far as a budget ever reaches. */
    private static int depth(final double budget, final double resistance, final double falloff) {
        double left = budget;
        int blocks = 0;
        while (blocks < 10_000) {
            final double step = price(resistance, blocks, falloff);
            if (step > left) {
                return blocks;
            }
            left -= step;
            blocks++;
        }
        return blocks;
    }

    /** How much a wave breaks spreading through solid material in every direction, shell by shell. */
    private static int spread(final double budget, final double resistance) {
        double left = budget;
        int broken = 0;
        for (int distance = 0; distance < 1_000; distance++) {
            final double step = price(resistance, distance, FALLOFF);
            final int shell = 4 * (distance + 1) * (distance + 1) + 2;
            final int afford = (int) Math.min(shell, Math.floor(left / step));
            broken += afford;
            left -= afford * step;
            if (afford < shell) {
                break;
            }
        }
        return broken;
    }

    /** How much of itself a build of this many blocks loses, capped at all of it. */
    private static int lost(final double mass, final double speed, final int blocks) {
        return Math.min(blocks, spread(ImpactResolver.shockKinetic(mass, speed, 1.0), STONE));
    }

    @Test
    @DisplayName("scraping sends nothing")
    void aGlancingBreakStaysWhereItHappened() {
        assertEquals(0.0, energy(1.0));
        assertEquals(0.0, energy(MIN_OVERSHOOT));
        assertTrue(energy(MIN_OVERSHOOT + 0.01) > 0.0);
    }

    @Test
    @DisplayName("falling from orbit carries further than clipping a hill")
    void aWorseCrashCarriesFurther() {
        assertTrue(depth(energy(20.0), STONE, FALLOFF) > depth(energy(5.0), STONE, FALLOFF));
        assertTrue(depth(energy(20.0), STONE, FALLOFF) > 10);
    }

    @Test
    @DisplayName("a wave crosses soft material further than hard")
    void materialIsWhatTheWaveSpends() {
        assertTrue(depth(energy(15.0), WOOD, FALLOFF) > depth(energy(15.0), STONE, FALLOFF));
    }

    @Test
    @DisplayName("distance alone stops a wave that costs nothing")
    void nothingRunsForever() {
        assertTrue(depth(energy(1000.0), 0.0, FALLOFF) < 500);
    }

    @Test
    @DisplayName("a wave that pays no more for distance is bounded by material alone")
    void withoutFalloffMaterialIsTheOnlyBrake() {
        assertEquals((int) (energy(20.0) / STONE), depth(energy(20.0), STONE, 1.0));
    }

    @Test
    @DisplayName("a block that costs more than is left holds")
    void theWaveStopsAtWhatItCannotAfford() {
        assertEquals(0, depth(1.0, 100.0, FALLOFF));
        assertTrue(ImpactResolver.shockCost(0.0, COST, 1.0) > 0.0);
    }

    @Test
    @DisplayName("what a crash has to spend is the body's, not the contact's")
    void aBattleshipIsNotABoulder() {
        assertTrue(ImpactResolver.shockKinetic(SHIP, 60.0, 1.0)
                > ImpactResolver.shockKinetic(BOULDER, 60.0, 1.0));
    }

    @Test
    @DisplayName("speed counts twice, the way it does in a real crash")
    void twiceAsFastIsFourTimesTheCrash() {
        assertEquals(4.0 * ImpactResolver.shockKinetic(SHIP, 30.0, 1.0),
                ImpactResolver.shockKinetic(SHIP, 60.0, 1.0), 1.0e-6);
    }

    @Test
    @DisplayName("a ship dropped from height is a heap, one set down is not")
    void theFallIsWhatDecidesIt() {
        assertEquals(4_000, lost(SHIP, 60.0, 4_000));
        assertTrue(lost(SHIP, 12.0, 4_000) < 4_000 / 2);
        assertTrue(lost(SHIP, 12.0, 4_000) > 0);
    }

    @Test
    @DisplayName("nothing sensible comes of nothing")
    void massAndSpeedHaveToBeReal() {
        assertEquals(0.0, ImpactResolver.shockKinetic(0.0, 60.0, 1.0));
        assertEquals(0.0, ImpactResolver.shockKinetic(SHIP, 0.0, 1.0));
        assertEquals(0.0, ImpactResolver.shockKinetic(Double.POSITIVE_INFINITY, 60.0, 1.0));
        assertEquals(0.0, ImpactResolver.shockKinetic(SHIP, Double.NaN, 1.0));
        assertEquals(0.0, ImpactResolver.shockKinetic(SHIP, 60.0, 0.0));
    }

    @Test
    @DisplayName("turning the dial off turns the shock off")
    void zeroScaleIsNoShock() {
        assertEquals(0.0, ImpactResolver.shockEnergy(100.0, MIN_OVERSHOOT, 0.0));
    }
}
