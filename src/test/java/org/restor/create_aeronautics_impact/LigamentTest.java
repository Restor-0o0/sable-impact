package org.restor.create_aeronautics_impact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LigamentTest {

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double CARRY = 40.0;

    private static final int[] HULL = {100, 100, 100, 3, 100, 100, 100};
    private static final double[] PLANKS = {INF, INF, INF, 5.0, INF, INF, INF};

    @Test
    void aHullHangingOnThreePlanksComesInTwoThere() {
        assertEquals(3, Ligament.fails(HULL, PLANKS, 24, CARRY));
    }

    @Test
    void theSameThreeBlocksOfObsidianHoldIt() {
        double[] obsidian = {INF, INF, INF, 105.0, INF, INF, INF};

        assertEquals(-1, Ligament.fails(HULL, obsidian, 24, CARRY));
    }

    @Test
    void aJointStrongEnoughForWhatIsOnItIsNotAFailure() {
        assertEquals(-1, Ligament.fails(HULL, PLANKS, 24, 200.0));
    }

    @Test
    void theTipOfABowIsThinAndCarriesNothing() {
        int[] tapered = {1, 2, 3, 4, 100, 100, 100};
        double[] wood = {2.0, 3.0, 5.0, 7.0, INF, INF, INF};

        assertEquals(-1, Ligament.fails(tapered, wood, 24, CARRY));
    }

    @Test
    void aJointThatCannotBeBrokenIsNoCandidate() {
        double[] bedrock = {INF, INF, INF, INF, INF, INF, INF};

        assertEquals(-1, Ligament.fails(HULL, bedrock, 24, CARRY));
    }

    @Test
    void theWorstOfTwoFailingJointsIsTheOneThatGoes() {
        int[] barbell = {200, 2, 60, 2, 400};
        double[] strength = {INF, 4.0, INF, 4.0, INF};

        assertEquals(3, Ligament.fails(barbell, strength, 24, CARRY));
    }

    @Test
    void betweenTwoEquallyOverloadedJointsTheThinnerOneGoes() {
        int[] symmetric = {200, 6, 60, 2, 200};
        double[] strength = {INF, 4.0, INF, 4.0, INF};

        assertEquals(3, Ligament.fails(symmetric, strength, 24, CARRY));
    }

    @Test
    void aJointOfNothingAtAllHoldsNothingAtAll() {
        double[] hollow = {INF, INF, INF, 0.0, INF, INF, INF};

        assertEquals(3, Ligament.fails(HULL, hollow, 24, CARRY));
    }

    @Test
    void aBuildWithNoRoomForTwoSidesAndAJointBetweenThemIsLeftAlone() {
        assertEquals(-1, Ligament.fails(new int[] {100, 3}, new double[] {INF, 5.0}, 1, CARRY));
    }

    @Test
    void carryingNothingIsNotADialAnybodyMeantToTurn() {
        assertEquals(-1, Ligament.fails(HULL, PLANKS, 24, 0.0));
    }
}
