package org.restor.create_aeronautics_impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A shock is measured against what it hits rather than paid out of a purse")
final class StressTest {

    private static final double BRITTLE = 0.15;
    private static final double DUCTILE = 2.0;
    private static final double STRUCTURAL = 1.0;

    private static final double BRITTLE_TRANSMIT = 0.15;
    private static final double STRUCTURAL_TRANSMIT = 0.8;

    private static final double PASS_ON = 0.6;
    private static final double BACKING = 0.25;
    private static final double FLOOR = 0.05;

    /** Roughly what the live numbers make of these once the resistance curve has been applied. */
    private static final double GLASS = 0.55;
    private static final double STONE = 1.449;
    private static final double OBSIDIAN = 6.0;

    /** A bulkhead the material table has priced above any crash the shipped numbers can produce. */
    private static final double BULKHEAD = 40.0;

    /** A four-thousand-block stone ship at twenty metres a second, at the shipped intensityScale. */
    private static final double CRASH = 32.0;

    /** A wing clipping a wall. */
    private static final double BUMP = 1.0;

    private static double threshold(final double resistance, final double mode) {
        return ImpactResolver.stressThreshold(resistance, mode, 1.0);
    }

    /** Whether a shock of this strength gets through this block, and how much of it does. */
    private static double through(final double intensity, final double resistance,
                                  final double mode, final double transmit) {
        final double threshold = threshold(resistance, mode);
        return ImpactResolver.stressPassed(intensity, threshold, intensity > threshold,
                transmit, PASS_ON);
    }

    /** How many blocks of one material in a straight line a shock of this strength takes out. */
    private static int depth(final double intensity, final double resistance,
                             final double mode, final double transmit) {
        double left = intensity;
        int broken = 0;
        while (broken < 10_000 && left > FLOOR) {
            final double threshold = threshold(resistance, mode);
            final boolean breaks = left > threshold;
            left = ImpactResolver.stressPassed(left, threshold, breaks, transmit, PASS_ON);
            if (!breaks) {
                return broken;
            }
            broken++;
        }
        return broken;
    }

    @Test
    @DisplayName("what a block can take depends on how it fails, not only on how hard it is to mine")
    void theSameHardnessFailsThreeWays() {
        assertTrue(threshold(STONE, BRITTLE) < threshold(STONE, STRUCTURAL));
        assertTrue(threshold(STONE, STRUCTURAL) < threshold(STONE, DUCTILE));
    }

    @Test
    @DisplayName("a bump takes the windows and leaves the walls")
    void theSmallCrashIsSelective() {
        assertTrue(BUMP > threshold(GLASS, BRITTLE));
        assertTrue(BUMP < threshold(STONE, STRUCTURAL));
    }

    @Test
    @DisplayName("a big crash cannot buy its way through a bulkhead the way a budget could")
    void strengthIsNotForSale() {
        assertEquals(0, depth(CRASH, BULKHEAD, DUCTILE, STRUCTURAL_TRANSMIT));
        assertTrue(depth(CRASH * 1_000.0, BULKHEAD, DUCTILE, STRUCTURAL_TRANSMIT) > 0,
                "enough is still enough; it is the ratio that decides, not the purse");
        assertTrue(depth(CRASH, OBSIDIAN, DUCTILE, STRUCTURAL_TRANSMIT)
                < depth(CRASH, STONE, STRUCTURAL, STRUCTURAL_TRANSMIT),
                "and what it can break it gets much less of");
    }

    @Test
    @DisplayName("a wall it cannot break is not the end of the crash")
    void theShockGoesThroughWhatHolds() {
        final double past = through(CRASH, BULKHEAD, DUCTILE, STRUCTURAL_TRANSMIT);
        assertTrue(past > 0.0);
        assertTrue(past > threshold(GLASS, BRITTLE),
                "the deck holds and the glass behind it does not");
    }

    @Test
    @DisplayName("holding costs the block nothing, so what it lets through ignores its strength")
    void survivingIsNotSpending() {
        assertEquals(through(BUMP, STONE, STRUCTURAL, STRUCTURAL_TRANSMIT),
                through(BUMP, OBSIDIAN, STRUCTURAL, STRUCTURAL_TRANSMIT), 1.0e-9);
    }

    @Test
    @DisplayName("breaking a block takes its strength out of the shock")
    void failingIsSpending() {
        final double threshold = threshold(STONE, STRUCTURAL);
        assertEquals((CRASH - threshold) * PASS_ON,
                through(CRASH, STONE, STRUCTURAL, STRUCTURAL_TRANSMIT), 1.0e-9);
    }

    @Test
    @DisplayName("a shock runs much further through glass than through stone")
    void theWindowsGoFirstAndFurthest() {
        assertTrue(depth(CRASH, GLASS, BRITTLE, BRITTLE_TRANSMIT)
                > depth(CRASH, STONE, STRUCTURAL, STRUCTURAL_TRANSMIT));
    }

    @Test
    @DisplayName("glass that survives does not carry the shock on the way a girder does")
    void brittleConductsBadly() {
        assertTrue(through(BUMP * 0.05, GLASS, BRITTLE, BRITTLE_TRANSMIT)
                < through(BUMP * 0.05, STONE, STRUCTURAL, STRUCTURAL_TRANSMIT));
    }

    @Test
    @DisplayName("a block is as strong as what holds it")
    void nothingBehindItIsWeaker() {
        assertEquals(1.0, ImpactResolver.stressBacking(6, BACKING), 1.0e-9);
        assertEquals(1.0 - BACKING, ImpactResolver.stressBacking(0, BACKING), 1.0e-9);
        assertTrue(ImpactResolver.stressBacking(1, BACKING) < ImpactResolver.stressBacking(5, BACKING));
    }

    @Test
    @DisplayName("turning the neighbour count off leaves every block at its own strength")
    void zeroWeightIsNoBacking() {
        assertEquals(1.0, ImpactResolver.stressBacking(0, 0.0), 1.0e-9);
        assertEquals(1.0, ImpactResolver.stressBacking(6, 0.0), 1.0e-9);
    }

    @Test
    @DisplayName("a block priced at nothing still stops something")
    void thereIsAlwaysAFloor() {
        assertTrue(threshold(0.0, 0.0) > 0.0);
        assertTrue(threshold(Double.NaN, STRUCTURAL) > 0.0);
        assertTrue(ImpactResolver.stressThreshold(-5.0, -5.0, -5.0) > 0.0);
    }

    @Test
    @DisplayName("nothing sensible comes of nothing")
    void aShockThatIsNotThereGoesNowhere() {
        assertEquals(0.0, ImpactResolver.stressPassed(0.0, 1.0, true, 1.0, 1.0));
        assertEquals(0.0, ImpactResolver.stressPassed(Double.NaN, 1.0, false, 1.0, 1.0));
        assertEquals(0.0, ImpactResolver.stressPassed(5.0, 1.0, false, 0.0, 1.0));
        assertEquals(0.0, ImpactResolver.stressPassed(5.0, 1.0, true, 1.0, 0.0));
    }
}
