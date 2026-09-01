package org.restor.create_aeronautics_impact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrackNeckTest {

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double BIAS = 0.08;

    @Test
    void aWoodenSphereShearsAtItsObsidianJointRatherThanThroughItsMiddle() {
        double[] sphere = {80.0, 80.0, 80.0, 80.0, 80.0, 60.0, 40.0, 24.0, 4800.0};

        assertEquals(3, CrackNeck.weakest(sphere, BIAS));
    }

    @Test
    void theObsidianItselfIsNeverTheThingThatBreaks() {
        double[] rod = {4800.0, 4800.0, 4800.0, 24.0, 4800.0};

        assertEquals(1, CrackNeck.weakest(rod, BIAS));
    }

    @Test
    void anEvenSectionIsCutWhereItWasHit() {
        double[] slab = {60.0, 60.0, 60.0, 60.0, 60.0};

        assertEquals(0, CrackNeck.weakest(slab, BIAS));
    }

    @Test
    void aWeakPlaceTooFarAwayIsNotWorthTravellingTo() {
        double[] far = {1.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0};

        assertEquals(0, CrackNeck.weakest(far, 0.5));
        assertEquals(-3, CrackNeck.weakest(far, 0.1));
    }

    @Test
    void withoutABiasTheWeakestPlaceInReachWinsHowFarAwayItIs() {
        double[] far = {1.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0};

        assertEquals(-3, CrackNeck.weakest(far, 0.0));
    }

    @Test
    void anEmptySectionIsAPlaceAlreadyCutRatherThanAWeakOne() {
        double[] past = {INF, INF, 40.0, 30.0, INF};

        assertEquals(1, CrackNeck.weakest(past, BIAS));
    }

    @Test
    void aSectionHoldingSomethingUnbreakableIsNoCandidate() {
        double[] bedrock = {INF, 90.0, 90.0, 90.0, INF};

        assertEquals(0, CrackNeck.weakest(bedrock, BIAS));
    }

    @Test
    void nothingCuttableInReachLeavesTheCutWhereTheBlowLanded() {
        double[] solid = {INF, INF, INF, INF, INF};

        assertEquals(0, CrackNeck.weakest(solid, BIAS));
    }

    @Test
    void aContactThatCannotBeCutStillPricesDistanceOffWhatIsAround() {
        double[] joint = {70.0, 70.0, INF, 70.0, 70.0};

        assertEquals(-1, CrackNeck.weakest(joint, BIAS));
    }

    @Test
    void tiesAreBrokenTowardsTheBlow() {
        double[] symmetric = {50.0, 20.0, 90.0, 20.0, 50.0};

        assertEquals(-1, CrackNeck.weakest(symmetric, BIAS));
    }

    @Test
    void aReachOfNoneIsTheContactAndNothingElse() {
        double[] only = {12.0};

        assertEquals(0, CrackNeck.weakest(only, BIAS));
    }
}
