package org.restor.create_aeronautics_impact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CrackPlaneTest {

    private static final int MIN_RUN = 3;

    @Test
    void aMastIsCutAcrossItsLength() {
        assertEquals(CrackPlane.Y, CrackPlane.normal(2, 30, 2, MIN_RUN, 0));
    }

    @Test
    void aPlateIsCutAcrossItsWidthAndNeverAcrossItsThickness() {
        assertEquals(CrackPlane.X, CrackPlane.normal(40, 1, 36, MIN_RUN, 0));
        assertEquals(CrackPlane.Z, CrackPlane.normal(40, 1, 36, MIN_RUN, 1));
    }

    @Test
    void aPlateCutTwiceComesApartInFourRatherThanLosingItsMiddle() {
        int first = CrackPlane.normal(40, 1, 36, MIN_RUN, 0);
        int second = CrackPlane.normal(40, 1, 36, MIN_RUN, 1);
        int third = CrackPlane.normal(40, 1, 36, MIN_RUN, 2);

        assertNotEquals(first, second);
        assertNotEquals(CrackPlane.Y, first);
        assertNotEquals(CrackPlane.Y, second);
        assertEquals(first, third);
    }

    @Test
    void aHullIsCutAmidshipsFirstAndAlongItsBeamSecond() {
        assertEquals(CrackPlane.Z, CrackPlane.normal(12, 8, 60, MIN_RUN, 0));
        assertEquals(CrackPlane.X, CrackPlane.normal(12, 8, 60, MIN_RUN, 1));
        assertEquals(CrackPlane.Y, CrackPlane.normal(12, 8, 60, MIN_RUN, 2));
    }

    @Test
    void aLumpHasToBeCutSomehowSoTheShortestRunIsStillTaken() {
        assertEquals(CrackPlane.Y, CrackPlane.normal(1, 2, 1, MIN_RUN, 0));
        assertEquals(CrackPlane.Y, CrackPlane.normal(1, 2, 1, MIN_RUN, 1));
    }

    @Test
    void tiesKeepAxisOrderSoTheAnswerDoesNotWander() {
        assertEquals(CrackPlane.X, CrackPlane.normal(20, 20, 20, MIN_RUN, 0));
        assertEquals(CrackPlane.Y, CrackPlane.normal(20, 20, 20, MIN_RUN, 1));
        assertEquals(CrackPlane.Z, CrackPlane.normal(20, 20, 20, MIN_RUN, 2));
    }

    @Test
    void aRunExactlyAtTheMinimumIsStillWorthCuttingAcross() {
        assertEquals(CrackPlane.X, CrackPlane.normal(9, 3, 1, MIN_RUN, 0));
        assertEquals(CrackPlane.Y, CrackPlane.normal(9, 3, 1, MIN_RUN, 1));
        assertEquals(CrackPlane.X, CrackPlane.normal(9, 3, 1, MIN_RUN, 2));
    }

    @Test
    void aMinimumOfZeroLetsEveryAxisBeCutAcross() {
        assertEquals(CrackPlane.Z, CrackPlane.normal(1, 2, 3, 0, 0));
        assertEquals(CrackPlane.Y, CrackPlane.normal(1, 2, 3, 0, 1));
        assertEquals(CrackPlane.X, CrackPlane.normal(1, 2, 3, 0, 2));
    }

    @Test
    void aLaterCrackWrapsWithinWhatIsEligibleRatherThanFallingOntoTheThinAxis() {
        for (int nth = 0; nth < 8; nth++) {
            assertNotEquals(CrackPlane.Y, CrackPlane.normal(40, 1, 36, MIN_RUN, nth));
        }
    }
}
