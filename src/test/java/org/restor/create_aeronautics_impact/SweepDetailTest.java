package org.restor.create_aeronautics_impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Giving ground when the tick runs short")
final class SweepDetailTest {

    /** Anything above about a hundred metres a second is a projectile, not a vehicle. */
    private static final double FASTEST = 6.0;

    @Test
    @DisplayName("no rung, however coarse, lets a hull step past a block")
    void everyRungKeepsItsSamplesOverlapping() {
        for (int level = 0; level < SweepDetail.LEVELS; level++) {
            final double width = SweepDetail.clip(level) * 2.0;
            for (double travel = 0.1; travel <= FASTEST; travel += 0.1) {
                final double spacing = travel / SweepDetail.steps(travel, level);
                assertTrue(spacing <= width,
                        "level " + level + " travelling " + travel + " leaves a gap of "
                                + (spacing - width) + " between samples");
            }
        }
    }

    @Test
    @DisplayName("each rung down is cheaper than the one above it")
    void coarseningActuallySavesWork() {
        final double travel = 4.0;
        int previous = Integer.MAX_VALUE;
        for (int level = 0; level < SweepDetail.LEVELS; level++) {
            final int steps = SweepDetail.steps(travel, level);
            assertTrue(steps <= previous, "level " + level + " samples more than the level above it");
            previous = steps;
        }
        assertTrue(SweepDetail.steps(travel, SweepDetail.LEVELS - 1) < SweepDetail.steps(travel, 0));
    }

    @Test
    @DisplayName("a hull barely moving is still swept, and one moving impossibly fast is still bounded")
    void stepCountStaysInsideItsLimits() {
        assertEquals(2, SweepDetail.steps(0.0, 0));
        assertEquals(2, SweepDetail.steps(0.001, SweepDetail.LEVELS - 1));
        assertTrue(SweepDetail.steps(1000.0, 0) <= 96);
    }

    @Test
    @DisplayName("scope is given up before resolution goes any lower")
    void theLadderDropsBreadthBeforeFidelity() {
        assertFalse(SweepDetail.leadingAxisOnly(0));
        assertFalse(SweepDetail.leadingAxisOnly(1));
        assertTrue(SweepDetail.leadingAxisOnly(2));

        // The rung that drops the lateral passes samples no more thinly than the one before it - one thing
        // is given up at a time, so a rung that helped can be climbed back without also undoing the other.
        assertEquals(SweepDetail.steps(4.0, 1), SweepDetail.steps(4.0, 2));
        assertEquals(SweepDetail.clip(1), SweepDetail.clip(2));
    }

    @Test
    @DisplayName("grass stands down only at the last rung")
    void cosmeticWorkSurvivesUntilTheEnd() {
        for (int level = 0; level < SweepDetail.LEVELS - 1; level++) {
            assertTrue(SweepDetail.cosmetic(level), "level " + level + " gave up cosmetic work too early");
        }
        assertFalse(SweepDetail.cosmetic(SweepDetail.LEVELS - 1));
    }

    @Test
    @DisplayName("a fast hull is swept coarsely on an idle server, because that is where detail is worth least")
    void speedCoarsensTheSweepOnItsOwnAccount() {
        assertEquals(0, SweepDetail.resolution(0, 0.5));
        assertEquals(1, SweepDetail.resolution(0, 4.0));
        assertTrue(SweepDetail.steps(4.0, SweepDetail.resolution(0, 4.0)) < SweepDetail.steps(4.0, 0));
    }

    @Test
    @DisplayName("speed thins the sampling and nothing else")
    void speedNeverGivesUpScope() {
        for (double travel = 0.1; travel <= FASTEST; travel += 0.1) {
            final int rung = SweepDetail.resolution(0, travel);
            assertFalse(SweepDetail.leadingAxisOnly(rung),
                    "travelling " + travel + " stopped carving sideways on an idle server");
            assertTrue(SweepDetail.cosmetic(rung),
                    "travelling " + travel + " stood the cosmetic sweep down on an idle server");
        }
    }

    @Test
    @DisplayName("a busy server still coarsens a slow hull further than speed alone would")
    void loadAndSpeedTakeWhicheverIsCoarser() {
        assertEquals(SweepDetail.LEVELS - 1, SweepDetail.resolution(SweepDetail.LEVELS - 1, 0.1));
        assertEquals(SweepDetail.LEVELS - 1, SweepDetail.resolution(SweepDetail.LEVELS - 1, 6.0));
    }

    @Test
    @DisplayName("the rung speed picks still cannot let a hull step past a block")
    void theSpeedRungKeepsTheSameGuarantee() {
        for (double travel = 0.1; travel <= FASTEST; travel += 0.1) {
            final int rung = SweepDetail.resolution(0, travel);
            final double spacing = travel / SweepDetail.steps(travel, rung);
            assertTrue(spacing <= SweepDetail.clip(rung) * 2.0,
                    "travelling " + travel + " left a gap between samples");
        }
    }

    @Test
    @DisplayName("a level from outside the ladder is answered by the nearest rung rather than thrown")
    void unknownLevelsClampRatherThanCrash() {
        assertEquals(SweepDetail.clip(0), SweepDetail.clip(-3));
        assertEquals(SweepDetail.clip(SweepDetail.LEVELS - 1), SweepDetail.clip(99));
        assertEquals(SweepDetail.steps(4.0, 0), SweepDetail.steps(4.0, -1));
    }
}
