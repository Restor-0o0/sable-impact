package org.restor.create_aeronautics_impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("What is holding a block up is part of how hard it is to get through")
final class BackingTest {

    private static final double WEIGHT = 0.6;

    /** Roughly what the live numbers make of these once the resistance curve has been applied. */
    private static final double WOOD = 1.025;
    private static final double STONE = 1.449;

    private static double support(final int behind, final int beside) {
        return ImpactResolver.support(
                behind, beside, ImpactResolver.BACKING_REACH, ImpactResolver.BACKING_BESIDE);
    }

    private static double wall(final int behind) {
        return STONE * ImpactResolver.backed(support(behind, 4), WEIGHT);
    }

    @Test
    @DisplayName("a block buried in a massif keeps everything its material is worth")
    void aBuriedBlockIsAtFullStrength() {
        assertEquals(1.0,
                ImpactResolver.backed(support(ImpactResolver.BACKING_REACH, 4), WEIGHT), 1.0e-9);
    }

    @Test
    @DisplayName("a block with nothing around it keeps only what the material is worth on its own")
    void aLooseBlockKeepsOnlyItsMaterial() {
        assertEquals(1.0 - WEIGHT, ImpactResolver.backed(support(0, 0), WEIGHT), 1.0e-9);
    }

    @Test
    @DisplayName("every block of depth is worth more than every block of depth before it was not")
    void supportRisesWithDepth() {
        double previous = -1.0;
        for (int behind = 0; behind <= ImpactResolver.BACKING_REACH; behind++) {
            final double support = support(behind, 4);
            assertTrue(support > previous, behind + " deep supports no better than " + (behind - 1));
            previous = support;
        }
    }

    @Test
    @DisplayName("wood goes through a thin stone wall and stops against a thick one")
    void thicknessDecidesTheContestWoodCannotWinOnMaterial() {
        assertTrue(WOOD > wall(0), "wood lost to a wall one block thick");
        assertTrue(WOOD > wall(1), "wood lost to a wall two blocks thick");
        assertTrue(WOOD < wall(2), "wood went through three blocks of stone");
        assertTrue(WOOD < wall(ImpactResolver.BACKING_REACH), "wood went through a hillside");
    }

    @Test
    @DisplayName("turning backing off puts every block back to its material alone")
    void zeroWeightRestoresTheOldReading() {
        for (int behind = 0; behind <= ImpactResolver.BACKING_REACH; behind++) {
            assertEquals(1.0, ImpactResolver.backed(support(behind, 0), 0.0), 1.0e-9);
        }
    }

    @Test
    @DisplayName("support and its weight are both answered inside their own range whatever they are given")
    void nonsenseInputsAreClamped() {
        assertEquals(1.0, support(99, 99), 1.0e-9);
        assertEquals(0.0, support(0, 0), 1.0e-9);
        assertEquals(1.0, ImpactResolver.backed(4.0, WEIGHT), 1.0e-9);
        assertEquals(0.0, ImpactResolver.backed(-1.0, 1.0), 1.0e-9);
        assertEquals(1.0, ImpactResolver.backed(0.0, -1.0), 1.0e-9);
    }
}
