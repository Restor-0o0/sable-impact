package org.restor.create_aeronautics_impact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactResolverTest {

    private static final double MIN_SPEED = 1.0;
    private static final double SCALE = 1.5;
    private static final double BLAST_FACTOR = 0.25;
    private static final double HARDNESS_WEIGHT = 1.0;
    private static final double REFERENCE_PRESSURE = 400.0;
    private static final double EXPONENT = 0.5;

    private static double breakSpeedOf(double destroySpeed, double blastResistance) {
        return ImpactResolver.breakSpeed(
                ImpactResolver.resistance(destroySpeed, blastResistance, BLAST_FACTOR, HARDNESS_WEIGHT), MIN_SPEED, SCALE);
    }

    private static double compressedBreakSpeedOf(double destroySpeed, double blastResistance) {
        return ImpactResolver.breakSpeed(
                ImpactResolver.compress(
                        ImpactResolver.resistance(destroySpeed, blastResistance, BLAST_FACTOR, HARDNESS_WEIGHT), EXPONENT),
                MIN_SPEED, SCALE);
    }

    @Test
    void compressionKeepsTheHardnessOrderingIntact() {
        double dirt = compressedBreakSpeedOf(0.5, 0.5);
        double stone = compressedBreakSpeedOf(1.5, 6.0);
        double deepslate = compressedBreakSpeedOf(3.0, 6.0);
        double obsidian = compressedBreakSpeedOf(50.0, 1200.0);

        assertTrue(dirt < stone);
        assertTrue(stone < deepslate);
        assertTrue(deepslate < obsidian);
    }

    @Test
    void compressionPullsObsidianIntoReachableSpeeds() {
        // Uncompressed, obsidian's 1200 blast resistance asks for hundreds of m/s that nothing ever reaches.
        assertTrue(breakSpeedOf(50.0, 1200.0) > 400.0);
        assertTrue(compressedBreakSpeedOf(50.0, 1200.0) < 40.0);
    }

    @Test
    void compressionLeavesSoftBlocksRoughlyWhereTheyWere() {
        assertTrue(Math.abs(compressedBreakSpeedOf(0.5, 0.5) - breakSpeedOf(0.5, 0.5)) < 0.5);
    }

    @Test
    void anExponentOfOneChangesNothing() {
        assertEquals(300.0, ImpactResolver.compress(300.0, 1.0));
    }

    @Test
    void indestructibleBlocksStayIndestructibleUnderCompression() {
        assertTrue(Double.isInfinite(ImpactResolver.compress(Double.POSITIVE_INFINITY, EXPONENT)));
    }

    @Test
    void unbreakableBlocksHaveInfiniteResistance() {
        assertTrue(Double.isInfinite(ImpactResolver.resistance(-1.0, 3600000.0, BLAST_FACTOR, HARDNESS_WEIGHT)));
    }

    @Test
    void blastResistanceCanDominateHardness() {
        assertEquals(300.0, ImpactResolver.resistance(50.0, 1200.0, BLAST_FACTOR, HARDNESS_WEIGHT));
    }

    @Test
    void softTerrainBreaksFarBelowStone() {
        assertTrue(breakSpeedOf(0.1, 0.1) < breakSpeedOf(1.5, 6.0));
    }

    @Test
    void snowGivesWayAtLowSpeedButStoneDoesNot() {
        double snow = breakSpeedOf(0.1, 0.1);
        double stone = breakSpeedOf(1.5, 6.0);

        assertTrue(ImpactResolver.shouldBreak(2.0, snow, false, false));
        assertFalse(ImpactResolver.shouldBreak(2.0, stone, false, false));
    }

    @Test
    void indestructibleAndExhaustedBudgetAlwaysResist() {
        assertFalse(ImpactResolver.shouldBreak(500.0, 1.0, true, false));
        assertFalse(ImpactResolver.shouldBreak(500.0, 1.0, false, true));
    }

    @Test
    void impactDirectionDoesNotChangeTheOutcome() {
        double dirt = breakSpeedOf(0.5, 0.5);
        assertEquals(
                ImpactResolver.shouldBreak(9.0, dirt, false, false),
                ImpactResolver.shouldBreak(-9.0, dirt, false, false));
    }

    @Test
    void debrisOnlyFliesWhenTheImpactOvershootsResistance() {
        assertEquals(0.0, ImpactResolver.scatterSpeed(1.0, 6.0, 0.25));
        assertTrue(ImpactResolver.scatterSpeed(20.0, 6.0, 0.25) > 0.0);
    }

    @Test
    void debrisSpeedIsCapped() {
        assertEquals(2.0, ImpactResolver.scatterSpeed(10000.0, 1.0, 0.25));
    }

    private static double massFactorOf(double mass, int contactBlocks) {
        return ImpactResolver.massFactor(
                ImpactResolver.contactPressure(mass, contactBlocks), REFERENCE_PRESSURE, 1.0, 0.5, 3.0);
    }

    @Test
    void spreadingTheSameMassOverMoreBlocksLowersPressure() {
        assertTrue(ImpactResolver.contactPressure(8000.0, 4)
                > ImpactResolver.contactPressure(8000.0, 40));
    }

    @Test
    void contactAreaIsNeverLessThanOneBlock() {
        assertEquals(ImpactResolver.contactPressure(500.0, 1), ImpactResolver.contactPressure(500.0, 0));
    }

    @Test
    void massFactorIsNeutralAtTheReferencePressure() {
        assertEquals(1.0, massFactorOf(REFERENCE_PRESSURE * 3, 3), 1.0E-9);
    }

    @Test
    void massFactorIsClampedBothWays() {
        assertEquals(3.0, massFactorOf(1.0E9, 1));
        assertEquals(0.5, massFactorOf(1.0, 64));
    }

    @Test
    void zeroSensitivityDisablesTheMassEffect() {
        assertEquals(1.0, ImpactResolver.massFactor(1.0E6, REFERENCE_PRESSURE, 0.0, 0.5, 3.0));
    }

    @Test
    void unknownMassLeavesTheBreakSpeedAlone() {
        double stone = breakSpeedOf(1.5, 6.0);
        assertEquals(stone, ImpactResolver.effectiveBreakSpeed(stone, massFactorOf(0.0, 4), MIN_SPEED));
    }

    private static final double INDESTRUCTIBLE_AT = 1000.0;

    /**
     * Built the way {@link BlockProfile#side} builds one: toughness reaches the speed needed to beat the block
     * and nothing else, so it can make a build hold together without making its material count as the harder
     * of the two.
     */
    private static ImpactResolver.Side side(double destroySpeed, double blastResistance, double toughness) {
        double resistance = ImpactResolver.resistance(destroySpeed, blastResistance, BLAST_FACTOR, HARDNESS_WEIGHT);
        return new ImpactResolver.Side(
                resistance,
                ImpactResolver.breakSpeed(resistance * toughness, MIN_SPEED, SCALE),
                blastResistance >= INDESTRUCTIBLE_AT || Double.isInfinite(resistance));
    }

    /**
     * The same block, weighed the way the shipped defaults weigh it. Vanilla hardness is a mining stat and
     * calls an oak log tougher than stone; blast resistance is the one that ranks them the way a crash does,
     * which is what the shipped weights lean on and what the constants at the top of this class - chosen to
     * make the arithmetic elsewhere easy to read - deliberately do not.
     */
    private static ImpactResolver.Side material(double destroySpeed, double blastResistance, double toughness) {
        double resistance = ImpactResolver.compress(
                ImpactResolver.resistance(destroySpeed, blastResistance, 0.35, 0.5), EXPONENT);
        return new ImpactResolver.Side(
                resistance, ImpactResolver.breakSpeed(resistance * toughness, MIN_SPEED, SCALE), false);
    }

    private static ImpactResolver.Side oak(double toughness) {
        return material(2.0, 3.0, toughness);
    }

    private static ImpactResolver.Side rock(double toughness) {
        return material(1.5, 6.0, toughness);
    }

    @Test
    @DisplayName("a wooden hull loses its own blocks against stone however tough builds are made")
    void toughnessCannotMakeWoodOutrankStone() {
        for (double toughness = 1.0; toughness <= 8.0; toughness += 0.5) {
            assertEquals(ImpactResolver.Victim.OTHER,
                    ImpactResolver.victim(60.0, rock(1.0), oak(toughness), false),
                    "toughness " + toughness + " had oak eating stone");
        }
    }

    @Test
    @DisplayName("toughness still buys a build the speed it takes to lose a block")
    void toughnessStillProtectsAgainstSlowKnocks() {
        assertTrue(oak(4.0).breakSpeed() > oak(1.0).breakSpeed());
        assertEquals(ImpactResolver.Victim.NONE, ImpactResolver.victim(2.0, rock(1.0), oak(4.0), false));
    }

    @Test
    @DisplayName("winning against your own equal costs you as much as it cost them")
    void wearIsPricedByHowCloseTheContestWas() {
        assertEquals(1.0, ImpactResolver.wear(stone(1.0), stone(1.0)), 1.0e-9);
        assertTrue(ImpactResolver.wear(stone(1.0), dirt(1.0)) < 1.0);
        assertTrue(ImpactResolver.wear(obsidian(1.0), stone(1.0))
                < ImpactResolver.wear(stone(1.0), dirt(1.0)));
    }

    @Test
    @DisplayName("winning can never cost more than the block that lost was worth")
    void wearIsCappedAtAFullBreak() {
        assertEquals(1.0, ImpactResolver.wear(dirt(1.0), obsidian(1.0)), 1.0e-9);
        assertEquals(1.0, ImpactResolver.wear(new ImpactResolver.Side(0.0, 0.0, false), stone(1.0)), 1.0e-9);
    }

    private static ImpactResolver.Side dirt(double toughness) {
        return side(0.5, 0.5, toughness);
    }

    private static ImpactResolver.Side stone(double toughness) {
        return side(1.5, 6.0, toughness);
    }

    private static ImpactResolver.Side obsidian(double toughness) {
        return side(50.0, 1200.0, toughness);
    }

    @Test
    void dirtContraptionCrumblesAgainstObsidianInsteadOfCuttingThroughIt() {
        assertEquals(ImpactResolver.Victim.OTHER,
                ImpactResolver.victim(6.0, obsidian(1.0), dirt(1.5), false));
    }

    @Test
    void stoneContraptionStillPloughsThroughDirt() {
        assertEquals(ImpactResolver.Victim.HIT,
                ImpactResolver.victim(6.0, dirt(1.0), stone(1.5), false));
    }

    @Test
    void equalMaterialsFavourTheTerrain() {
        assertEquals(ImpactResolver.Victim.HIT,
                ImpactResolver.victim(6.0, stone(1.0), stone(1.0), false));
    }

    @Test
    @DisplayName("with nothing to choose between the materials, toughness decides which side gives way")
    void toughnessDecidesOtherwiseEvenMatchups() {
        assertEquals(ImpactResolver.Victim.OTHER,
                ImpactResolver.victim(6.0, stone(1.0), stone(0.1), false));
    }

    @Test
    void anIndestructibleContraptionBlockPushesTheDamageOntoTheTerrain() {
        assertEquals(ImpactResolver.Victim.HIT,
                ImpactResolver.victim(6.0, dirt(1.0), side(-1.0, 3600000.0, 1.0), false));
    }

    @Test
    void twoIndestructibleSidesJustBounce() {
        assertEquals(ImpactResolver.Victim.NONE,
                ImpactResolver.victim(500.0, obsidian(1.0), obsidian(1.0), false));
    }

    @Test
    void aGentleTouchBreaksNeitherSide() {
        assertEquals(ImpactResolver.Victim.NONE,
                ImpactResolver.victim(1.0, dirt(1.0), stone(1.5), false));
    }

    @Test
    void anExhaustedBudgetSparesBothSides() {
        assertEquals(ImpactResolver.Victim.NONE,
                ImpactResolver.victim(500.0, dirt(1.0), dirt(1.0), true));
    }

    @Test
    void withContraptionBreakingOffOnlyTerrainIsAtRisk() {
        assertEquals(ImpactResolver.Victim.HIT,
                ImpactResolver.victim(6.0, dirt(1.0), null, false));
        assertEquals(ImpactResolver.Victim.NONE,
                ImpactResolver.victim(6.0, obsidian(1.0), null, false));
    }

    @Test
    void theSofterContraptionLosesRegardlessOfWhichSideTheCallbackFiredOn() {
        assertEquals(ImpactResolver.Victim.OTHER,
                ImpactResolver.victim(6.0, stone(1.5), dirt(1.5), false));
        assertEquals(ImpactResolver.Victim.HIT,
                ImpactResolver.victim(6.0, dirt(1.5), stone(1.5), false));
    }

    @Test
    void twoIdenticalContraptionsEachLoseTheirOwnBlock() {
        assertEquals(ImpactResolver.Victim.HIT,
                ImpactResolver.victim(6.0, stone(1.5), stone(1.5), false));
    }

    @Test
    void immovableTerrainIsTheInfiniteMassLimitOfATwoBodyHit() {
        assertEquals(5000.0, ImpactResolver.reducedMass(5000.0, Double.POSITIVE_INFINITY));
        assertEquals(5000.0, ImpactResolver.reducedMass(5000.0, 0.0));
        assertTrue(ImpactResolver.reducedMass(5000.0, 1.0E9) > 4999.0);
    }

    @Test
    void twoEqualContraptionsHitWithHalfTheEffectiveMass() {
        assertEquals(500.0, ImpactResolver.reducedMass(1000.0, 1000.0));
    }

    @Test
    void reducedMassIsSymmetricAndNeverExceedsEitherBody() {
        assertEquals(ImpactResolver.reducedMass(800.0, 12000.0), ImpactResolver.reducedMass(12000.0, 800.0), 1.0E-9);
        assertTrue(ImpactResolver.reducedMass(800.0, 12000.0) < 800.0);
        assertEquals(0.0, ImpactResolver.reducedMass(0.0, 12000.0));
    }

    @Test
    void rammingAnotherContraptionHitsSofterThanRammingTheGround() {
        double stone = breakSpeedOf(1.5, 6.0);
        double intoTerrain = ImpactResolver.effectiveBreakSpeed(
                stone, massFactorOf(ImpactResolver.reducedMass(9000.0, Double.POSITIVE_INFINITY), 4), MIN_SPEED);
        double intoShip = ImpactResolver.effectiveBreakSpeed(
                stone, massFactorOf(ImpactResolver.reducedMass(9000.0, 9000.0), 4), MIN_SPEED);

        assertTrue(intoTerrain < intoShip);
    }

    // Sable weighs a plain block at 1 kg and stone or obsidian at 2 kg, so a whole contraption lands in the
    // hundreds, not the tens of thousands. These pin the shipped referencePressure to that real scale.
    private static final double SABLE_REFERENCE_PRESSURE = 12.0;
    private static final double OBSIDIAN_BLOCK_MASS = 2.0;

    private static double sableMassFactor(double mass, int contactBlocks) {
        return ImpactResolver.massFactor(
                ImpactResolver.contactPressure(mass, contactBlocks), SABLE_REFERENCE_PRESSURE, 1.0, 0.5, 3.0);
    }

    @Test
    void aSolidObsidianRamDrivesItsBreakSpeedDownNotUp() {
        double rod = 4 * 4 * 16 * OBSIDIAN_BLOCK_MASS;

        assertTrue(sableMassFactor(rod, 4 * 4) > 1.0);
    }

    @Test
    void theSameMassSpreadIntoAPlateIsPenalisedInstead() {
        double plate = 16 * 16 * 1 * OBSIDIAN_BLOCK_MASS;

        assertTrue(sableMassFactor(plate, 16 * 16) < 1.0);
    }

    @Test
    void obsidianRamReachesObsidianTerrain() {
        double terrain = compressedBreakSpeedOf(50.0, 1200.0);
        double rod = 4 * 4 * 16 * OBSIDIAN_BLOCK_MASS;
        double needed = ImpactResolver.effectiveBreakSpeed(terrain, sableMassFactor(rod, 4 * 4), MIN_SPEED);

        assertTrue(needed < 20.0, "a dedicated obsidian ram should crack obsidian below 20 m/s, needed " + needed);
    }

    @Test
    void heavyCompactContraptionBreaksStoneThatALightSprawlingOneCannot() {
        double stone = breakSpeedOf(1.5, 6.0);
        double heavy = ImpactResolver.effectiveBreakSpeed(stone, massFactorOf(24000.0, 4), MIN_SPEED);
        double light = ImpactResolver.effectiveBreakSpeed(stone, massFactorOf(2000.0, 40), MIN_SPEED);

        assertTrue(heavy < light);
        assertTrue(ImpactResolver.shouldBreak(3.0, heavy, false, false));
        assertFalse(ImpactResolver.shouldBreak(3.0, light, false, false));
    }

    // Sable pulls at 11 m/s^2, so this is everything a hull gains by settling the height of one block -
    // the speed a contraption arrives at simply by being assembled over a gap.
    private static final double SHIPPED_BLAST_FACTOR = 0.35;
    private static final double SHIPPED_HARDNESS_WEIGHT = 0.5;
    private static final double SHIPPED_MIN_SPEED = 6.0;
    private static final double SHIPPED_SCALE = 1.8;
    private static final double SHIPPED_CRUSH_SPEED = 3.8;
    private static final double SHIPPED_MASS_FACTOR_MAX = 6.0;
    private static final double ONE_BLOCK_FALL = Math.sqrt(2.0 * 11.0);

    private static double shippedBreakSpeedOf(double destroySpeed, double blastResistance, double massFactor) {
        return ImpactResolver.effectiveBreakSpeed(
                ImpactResolver.breakSpeed(
                        ImpactResolver.compress(
                                ImpactResolver.resistance(destroySpeed, blastResistance,
                                        SHIPPED_BLAST_FACTOR, SHIPPED_HARDNESS_WEIGHT), EXPONENT),
                        SHIPPED_MIN_SPEED, SHIPPED_SCALE),
                massFactor,
                SHIPPED_MIN_SPEED,
                SHIPPED_CRUSH_SPEED);
    }

    @Test
    void noAmountOfMassPushesABlockBelowTheFloor() {
        double stone = breakSpeedOf(1.5, 6.0);

        assertTrue(ImpactResolver.effectiveBreakSpeed(stone, 1000.0, MIN_SPEED) >= MIN_SPEED);
    }

    @Test
    void anOrdinaryBuildSettlingOntoTheGroundBreaksNothing() {
        // Anything that is not concentrating real weight onto a small footprint.
        double dirt = shippedBreakSpeedOf(0.5, 0.5, 1.0);

        assertFalse(ImpactResolver.shouldBreak(ONE_BLOCK_FALL, dirt, false, false),
                "a normal contraption dropping one block onto dirt must not break it");
    }

    @Test
    void aHeavyCompactHullPressesIntoGroundItMerelySettlesOnto() {
        double dirt = shippedBreakSpeedOf(0.5, 0.5, SHIPPED_MASS_FACTOR_MAX);

        assertTrue(ImpactResolver.shouldBreak(ONE_BLOCK_FALL, dirt, false, false),
                "an obsidian ball has to leave a dent in soft ground, not rest on it like foam");
    }

    /**
     * The crater is what stops the crater growing. Mass factor is weight over contact area, so every layer a
     * hull sinks puts more of itself onto terrain and cuts its own pressure; the depth it reaches is the
     * depth at which the ground bears it. Nothing else bounds this, so if the feedback ever stopped working
     * a heavy build would dig to bedrock the moment it was assembled.
     */
    @Test
    void sinkingStopsOnceTheHullHasSpreadItsWeightOverEnoughGround() {
        double spread = shippedBreakSpeedOf(0.5, 0.5, 1.4);

        assertFalse(ImpactResolver.shouldBreak(ONE_BLOCK_FALL, spread, false, false));
    }

    @Test
    void harderGroundBearsAHullSooner() {
        double dirt = shippedBreakSpeedOf(0.5, 0.5, 2.0);
        double stone = shippedBreakSpeedOf(1.5, 6.0, 2.0);

        assertTrue(ImpactResolver.shouldBreak(ONE_BLOCK_FALL, dirt, false, false));
        assertFalse(ImpactResolver.shouldBreak(ONE_BLOCK_FALL, stone, false, false));
    }

    @Test
    void restingWeightNeverCracksObsidian() {
        double obsidian = shippedBreakSpeedOf(50.0, 1200.0, SHIPPED_MASS_FACTOR_MAX);

        assertFalse(ImpactResolver.shouldBreak(ONE_BLOCK_FALL, obsidian, false, false));
    }

    @Test
    void aRealDropStillSmashesTheGround() {
        double stone = shippedBreakSpeedOf(1.5, 6.0, 1.0);

        assertTrue(ImpactResolver.shouldBreak(ONE_BLOCK_FALL * 3.0, stone, false, false));
    }

    @Test
    void theFloorDoesNotFlattenTheMaterialsAboveIt() {
        assertTrue(shippedBreakSpeedOf(0.5, 0.5, 3.0) < shippedBreakSpeedOf(1.5, 6.0, 3.0));
        assertTrue(shippedBreakSpeedOf(1.5, 6.0, 3.0) < shippedBreakSpeedOf(50.0, 1200.0, 3.0));
    }

    @Test
    void aFastRamPloughsStraightThrough() {
        assertTrue(ImpactResolver.punchesThrough(30.0, 4.0, 2.5));
    }

    @Test
    void aRamThatOnlyJustBreaksTheBlockStillPaysForIt() {
        assertFalse(ImpactResolver.punchesThrough(5.0, 4.0, 2.5));
    }

    @Test
    void punchThroughIgnoresTheSignOfTheImpact() {
        assertEquals(
                ImpactResolver.punchesThrough(30.0, 4.0, 2.5),
                ImpactResolver.punchesThrough(-30.0, 4.0, 2.5));
    }

    @Test
    void aRatioOfOneMakesEveryBreakFree() {
        assertTrue(ImpactResolver.punchesThrough(0.1, 100.0, 1.0));
    }

    @Test
    void slowingDownEventuallyBuriesTheRam() {
        double stone = compressedBreakSpeedOf(1.5, 6.0);
        double needed = ImpactResolver.effectiveBreakSpeed(stone, sableMassFactor(4 * 4 * 16 * OBSIDIAN_BLOCK_MASS, 4 * 4), MIN_SPEED);

        assertTrue(ImpactResolver.punchesThrough(20.0, needed, 2.5));
        assertFalse(ImpactResolver.punchesThrough(2.0, needed, 2.5));
        assertTrue(ImpactResolver.shouldBreak(2.0, needed, false, false),
                "the block still gives way at 2 m/s, the hull just does not get the contact for free");
    }

    private static final double SHIPPED_CRACK_RESILIENCE = 3.0;

    @Test
    void aResilienceOfOneRestoresAllOrNothingBreaking() {
        assertEquals(1.0, ImpactResolver.crackDamage(1.0, 1.0));
        assertEquals(1.0, ImpactResolver.crackDamage(50.0, 1.0));
    }

    @Test
    void theWeakestImpactThatBreaksAnythingStillTakesAFullShare() {
        // Nothing below the break speed ever reaches the cracker, so an overshoot under one is a rounding
        // wobble, not a softer hit - reading it as one would hand out free chips at any speed at all.
        assertEquals(ImpactResolver.crackDamage(1.0, SHIPPED_CRACK_RESILIENCE),
                ImpactResolver.crackDamage(0.6, SHIPPED_CRACK_RESILIENCE));
    }

    @Test
    void aBlockHitAtItsBreakSpeedSurvivesExactlyAsManyHitsAsItsResilience() {
        double damage = 0.0;
        int hits = 0;
        while (damage < 1.0) {
            damage += ImpactResolver.crackDamage(1.0, SHIPPED_CRACK_RESILIENCE);
            hits++;
        }
        assertEquals(3, hits);
    }

    @Test
    void aRealCrashStillBreaksBlocksInOneHit() {
        assertTrue(ImpactResolver.crackDamage(SHIPPED_CRACK_RESILIENCE, SHIPPED_CRACK_RESILIENCE) >= 1.0);
        assertTrue(ImpactResolver.crackDamage(20.0, SHIPPED_CRACK_RESILIENCE) >= 1.0);
    }

    @Test
    void aHarderHitCostsProportionallyFewerOfThem() {
        // Cracking splits a break that was already earned across several hits and never adds up to more than
        // one break's worth, so how much a block can take does not depend on how it is spread out.
        for (double overshoot : new double[]{1.0, 1.5, 2.0, 3.0, 7.5}) {
            double perHit = ImpactResolver.crackDamage(overshoot, SHIPPED_CRACK_RESILIENCE);
            int hits = (int) Math.ceil(SHIPPED_CRACK_RESILIENCE / overshoot);
            assertTrue(perHit * hits >= 1.0);
            assertTrue(perHit * (hits - 1) < 1.0);
        }
    }

    private static final double SHIPPED_CRUSH_PRESSURE_SCALE = 70.0;

    /** Sable weighs a plain block at 1 and stone or obsidian at 2. */
    private static final double PLAIN_BLOCK_MASS = 1.0;

    private static double crushStrengthOf(double destroySpeed, double blastResistance) {
        return ImpactResolver.crushStrength(
                ImpactResolver.compress(
                        ImpactResolver.resistance(destroySpeed, blastResistance,
                                SHIPPED_BLAST_FACTOR, SHIPPED_HARDNESS_WEIGHT), EXPONENT),
                SHIPPED_CRUSH_PRESSURE_SCALE);
    }

    private static double dirtStrength() {
        return crushStrengthOf(0.5, 0.5);
    }

    private static double stoneStrength() {
        return crushStrengthOf(1.5, 6.0);
    }

    private static double oakLogStrength() {
        return crushStrengthOf(2.0, 2.0);
    }

    /** Blocks a build has to spread itself over before the ground under it stops giving way. */
    private static int settledFootprint(double mass, double strength) {
        int support = 1;
        while (mass / support > strength && support < 1_000_000) {
            support++;
        }
        return support;
    }

    @Test
    void anOrdinaryHullRestsOnWhateverItLandsOn() {
        // A ten-by-ten stone-shelled ship: about six hundred blocks at two apiece, set down on its footprint.
        double resting = 1200.0 / 100;
        assertTrue(resting < dirtStrength());
        assertTrue(resting < stoneStrength());
    }

    @Test
    void aSphereRestingOnATreePulpsIt() {
        // Thirty blocks across and solid obsidian, balanced on the handful of logs holding it up.
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertTrue(mass / 6 > oakLogStrength());
        // Still true once it has spread over every log a canopy could put under it.
        assertTrue(mass / 60 > oakLogStrength());
    }

    private static final double SHIPPED_CRUSH_SPREAD = 3.0;

    /** The share of a load handed straight down, the rest of it going to the four sides. */
    private static final double SHIPPED_CRUSH_DOWN_SHARE = 0.6;
    private static final int SHIPPED_CRUSH_DEPTH = 8;
    private static final double SHIPPED_CRUSH_SHEAR = 0.5;

    private static final double SHIPPED_CRUSH_SEAT = 0.15;
    private static final double SHIPPED_BREAK_DRAG_MASS = 2.0;
    private static final double SHIPPED_BREAK_DRAG_MAX = 0.12;

    /** Load per bearing block, once sideways contact has taken its share of the footprint. */
    private static double crushPressure(double mass, int bearing, int flank) {
        return mass / (bearing + flank * SHIPPED_CRUSH_SHEAR);
    }

    /**
     * The same, with the share of its own shadow a build is credited with carrying it.
     *
     * <p>Contact counted a tick at a time is not a footprint. The same boulder reads as touching six blocks
     * while it rolls and seven hundred while it sits, and it weighs the same either way - so the reading on
     * its own says a rolling build presses a hundred times harder than a parked one, and the ground under
     * one that is going somewhere is worth a hundred times less than the ground under one that is not.
     */
    private static double seatedPressure(double mass, int bearing, int flank, double width, double depth) {
        double seat = width * depth * SHIPPED_CRUSH_SEAT;
        return mass / Math.max(bearing + flank * SHIPPED_CRUSH_SHEAR, Math.max(seat, 1.0));
    }

    /** Layers a load eats through before something under them holds it, as one crush pass would. */
    /**
     * Blocks the load gets through before it runs out, counting from the one the hull is touching.
     *
     * <p>It loses the same share at every block rather than being divided by how far it has come, because it
     * is handed on from block to block rather than read off a table - each one gives the next whatever it has
     * left, which is what makes the reach fall out of the overshoot instead of having to be configured.
     */
    private static int crushedReach(double pressure, double strength, double spread) {
        double load = pressure;
        int reached = 0;
        while (reached < SHIPPED_CRUSH_DEPTH && load > strength) {
            reached++;
            load = load * ImpactResolver.crushLoadAt(1.0, 1, spread) * SHIPPED_CRUSH_DOWN_SHARE;
        }
        return reached;
    }

    private static int crushedReach(double pressure, double strength) {
        return crushedReach(pressure, strength, SHIPPED_CRUSH_SPREAD);
    }

    @Test
    void aBoulderOnATreeTakesTheTrunkAndNotJustTheTopLog() {
        // Not the whole trunk in one pass any more: the load is divided as it fans out rather than handed on
        // whole, so it runs out within a few blocks of what it is touching. A moving hull is asked every
        // tick, which is what takes the rest of the trunk - a log at a time rather than all at once.
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertTrue(crushedReach(mass / 6, oakLogStrength()) >= 2);
        // A whole canopy underneath is still nowhere near enough to make it a surface graze.
        assertTrue(crushedReach(mass / 60, oakLogStrength()) >= 2);
    }

    @Test
    void groundThatHoldsAtTheSurfaceIsNeverDugInto() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertEquals(0, crushedReach(mass / SPHERE_CROSS_SECTION, stoneStrength()));
    }

    @Test
    void aLoadThatOnlyJustBreaksTheSurfaceStopsShortOfTheCap() {
        double pressure = stoneStrength() * 2.0;
        int depth = crushedReach(pressure, stoneStrength());
        assertTrue(depth > 0);
        assertTrue(depth < SHIPPED_CRUSH_DEPTH);
    }

    @Test
    void withoutSpreadTheSameLoadCarriesFurther() {
        // Turning the loss off does not turn the thinning off, because fanning out divides the load on its
        // own. That is the part that cannot be configured away: weight handed to five blocks is five shares.
        double pressure = stoneStrength() * 2.0;
        assertTrue(crushedReach(pressure, stoneStrength(), 0.0)
                > crushedReach(pressure, stoneStrength()));
    }

    @Test
    void howFarTheDamageCarriesIsDecidedByHowFarOverYieldTheLoadWas() {
        // The two ends of the range this pass has to cover with one rule. An ordinary hull sitting on soft
        // ground marks the ground it is sitting on and nothing else.
        assertEquals(1, crushedReach(dirtStrength() * 1.2, dirtStrength()));
        // A thirty-block obsidian ball wedged in a canopy is an order of magnitude past what wood bears even
        // through the shear discount, and stopping that at the branches it happens to be touching is what
        // left a forest standing underneath it.
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        double wedged = crushPressure(mass, 6, 40) * SHIPPED_CRUSH_SHEAR;
        assertTrue(wedged / oakLogStrength() > 5.0);
        assertTrue(crushedReach(wedged, oakLogStrength()) > crushedReach(dirtStrength() * 1.2, dirtStrength()));
    }

    @Test
    void aRollingBoulderIsNotReadAsAHundredTimesHeavierThanAParkedOne() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        double parked = seatedPressure(mass, SPHERE_CROSS_SECTION, 0, 30.0, 30.0);
        double rolling = seatedPressure(mass, 6, 0, 30.0, 30.0);

        // What it used to be, and the reason the same sphere that dents a field when it stops cut a trench
        // through the whole of one on the way there.
        assertTrue(crushPressure(mass, 6, 0) / parked > 100.0);
        assertTrue(rolling / parked < 10.0);
        assertTrue(rolling > parked);
    }

    @Test
    void aRollingBoulderScoresTheGroundRatherThanTrenchingIt() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertTrue(crushedReach(crushPressure(mass, 6, 0), dirtStrength()) >= 3);
        assertEquals(1, crushedReach(seatedPressure(mass, 6, 0, 30.0, 30.0), dirtStrength()));
    }

    @Test
    void aSeatedBoulderStillGoesThroughTheTreesItRollsInto() {
        // Wedged against a trunk rather than over it, so the load arrives through the shear discount.
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertTrue(seatedPressure(mass, 0, 6, 30.0, 30.0) * SHIPPED_CRUSH_SHEAR > oakLogStrength());
    }

    @Test
    void aSeatIsAFloorAndNeverLiftsAHullOffWhatItIsActuallyOn() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertEquals(crushPressure(mass, SPHERE_CROSS_SECTION, 0),
                seatedPressure(mass, SPHERE_CROSS_SECTION, 0, 30.0, 30.0));
    }

    @Test
    void aHullPloughingTerrainIsSlowedRatherThanStopped() {
        // A small hull meeting a few hundred blocks in one tick, which is an ordinary tick of ploughing.
        double mass = 494.0;
        double speed = 12.0;
        double momentum = SHIPPED_BREAK_DRAG_MASS * speed * 250;

        // What it used to do: the whole of the hull's motion, gone between two frames.
        assertEquals(speed, Math.min(momentum / mass, speed));
        assertEquals(speed * SHIPPED_BREAK_DRAG_MAX,
                ImpactResolver.speedLost(momentum, mass, speed, SHIPPED_BREAK_DRAG_MAX));
    }

    @Test
    void whatABlockTakesOutOfAHullIsWhatItWasMadeOf() {
        double stone = ImpactResolver.breakDrag(1.449, 12.0, SHIPPED_BREAK_DRAG_MASS);
        double dirt = ImpactResolver.breakDrag(0.7, 12.0, SHIPPED_BREAK_DRAG_MASS);
        assertTrue(stone > dirt * 2.0);
        assertEquals(2.0 * stone, ImpactResolver.breakDrag(1.449, 24.0, SHIPPED_BREAK_DRAG_MASS));
    }

    @Test
    void aBlockThatCostsNothingToBreakSlowsNothingDown() {
        assertEquals(0.0, ImpactResolver.breakDrag(0.0, 60.0, SHIPPED_BREAK_DRAG_MASS));
        assertEquals(0.0, ImpactResolver.breakDrag(1.449, 60.0, 0.0));
        assertEquals(0.0, ImpactResolver.breakDrag(1.449, Double.NaN, SHIPPED_BREAK_DRAG_MASS));
    }

    @Test
    void aHullThatBarelyClippedSomethingStillOnlyPaysForWhatItHit() {
        // The cap is a ceiling and not a rate: light contact costs what it costs.
        double lost = ImpactResolver.speedLost(
                SHIPPED_BREAK_DRAG_MASS * 12.0 * 2, 494.0, 12.0, SHIPPED_BREAK_DRAG_MAX);
        assertTrue(lost < 12.0 * SHIPPED_BREAK_DRAG_MAX);
        assertTrue(lost > 0.0);
    }

    @Test
    void aHullPloughingOnComesToRestAnyway() {
        double speed = 12.0;
        for (int tick = 0; tick < 40 && speed > 0.05; tick++) {
            speed -= ImpactResolver.speedLost(
                    SHIPPED_BREAK_DRAG_MASS * speed * 250, 494.0, speed, SHIPPED_BREAK_DRAG_MAX);
        }
        assertTrue(speed < 0.2);
    }

    @Test
    void groundStrongEnoughToBearTheHullIsNotTouchedAtAll() {
        assertEquals(0, crushedReach(stoneStrength() * 0.9, stoneStrength()));
    }

    @Test
    void loadOnlyEverThinsGoingDown() {
        double pressure = 1000.0;
        for (int depth = 1; depth < 32; depth++) {
            assertTrue(ImpactResolver.crushLoadAt(pressure, depth, SHIPPED_CRUSH_SPREAD)
                    < ImpactResolver.crushLoadAt(pressure, depth - 1, SHIPPED_CRUSH_SPREAD));
        }
        assertEquals(pressure, ImpactResolver.crushLoadAt(pressure, 0, SHIPPED_CRUSH_SPREAD));
    }

    private static final double SPHERE_RADIUS = 15.0;
    private static final int SPHERE_BLOCKS = (int) (4.0 / 3.0 * Math.PI * SPHERE_RADIUS * SPHERE_RADIUS * SPHERE_RADIUS);
    private static final int SPHERE_CROSS_SECTION = (int) (Math.PI * SPHERE_RADIUS * SPHERE_RADIUS);

    @Test
    void aSpherePunchesThroughDirtAndSettlesIntoStone() {
        // The dent a build leaves is however much of the ground it takes to hold it up, so what stops it is
        // running out of its own underside to spread over - which stone gives it and dirt does not.
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertTrue(settledFootprint(mass, stoneStrength()) <= SPHERE_CROSS_SECTION);
        assertTrue(settledFootprint(mass, dirtStrength()) > SPHERE_CROSS_SECTION);
    }

    @Test
    void sinkingStopsRatherThanRunningAway() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        int settled = settledFootprint(mass, stoneStrength());
        assertTrue(mass / settled <= stoneStrength());
        assertTrue(mass / (settled - 1) > stoneStrength());
    }

    @Test
    void aPillarStoodOnItsEndSinksWhereTheSameOneLaidFlatDoesNot() {
        double mass = 20 * OBSIDIAN_BLOCK_MASS;
        assertTrue(mass / 1 > dirtStrength());
        assertFalse(mass / 20 > dirtStrength());
        // A shadow one block across has no share worth crediting, so the seat leaves this exactly as it was.
        assertTrue(seatedPressure(mass, 1, 0, 1.0, 1.0) > dirtStrength());
        assertFalse(seatedPressure(mass, 20, 0, 20.0, 1.0) > dirtStrength());
    }

    @Test
    void weightAloneIsNotEnoughWithoutTheFootprintToConcentrateIt() {
        // A raft is heavier than the pillar and marks nothing, which is the whole point of measuring pressure
        // rather than mass: a barge does not sink into a field and a fencepost does.
        double raft = 400 * PLAIN_BLOCK_MASS;
        assertFalse(raft / 400 > dirtStrength());
        assertTrue(20 * OBSIDIAN_BLOCK_MASS > raft / 400);
    }

    @Test
    void anIndestructibleBlockCarriesAnyLoad() {
        assertTrue(Double.isInfinite(
                ImpactResolver.crushStrength(Double.POSITIVE_INFINITY, SHIPPED_CRUSH_PRESSURE_SCALE)));
        assertFalse(Double.MAX_VALUE
                > ImpactResolver.crushStrength(Double.POSITIVE_INFINITY, SHIPPED_CRUSH_PRESSURE_SCALE));
    }

    @Test
    void aBlockLoadedPastItsStrengthTakesDamageInProportion() {
        double strength = stoneStrength();
        assertEquals(1.0, ImpactResolver.crushOvershoot(strength, strength));
        assertEquals(2.0, ImpactResolver.crushOvershoot(strength * 2.0, strength));
        // A hundred times over its strength is not a slow crack, it is gone this pass.
        assertTrue(ImpactResolver.crackDamage(
                ImpactResolver.crushOvershoot(strength * 100.0, strength), SHIPPED_CRACK_RESILIENCE) >= 1.0);
    }

    private static final double SHIPPED_INDESTRUCTIBLE_RESISTANCE = 100000.0;
    private static final double BEDROCK_BLAST_RESISTANCE = 3_600_000.0;
    private static final double OBSIDIAN_BLAST_RESISTANCE = 1200.0;

    @Test
    void whatIsPermanentIsMarkedByHardnessRatherThanByBlastResistance() {
        // Bedrock and barriers carry a negative hardness and are out of reach on that alone, so the blast
        // threshold is free to sit high enough that it never catches anything a player built with.
        assertTrue(Double.isInfinite(
                ImpactResolver.resistance(-1.0, BEDROCK_BLAST_RESISTANCE, BLAST_FACTOR, HARDNESS_WEIGHT)));
        assertTrue(BEDROCK_BLAST_RESISTANCE >= SHIPPED_INDESTRUCTIBLE_RESISTANCE);
    }

    @Test
    void obsidianIsHardRatherThanPermanent() {
        // Obsidian, netherite, anvils and enchanting tables all sit at 1200. A threshold that caught them
        // would quietly make every one of them immune, which is a different claim from being very tough.
        assertFalse(OBSIDIAN_BLAST_RESISTANCE >= SHIPPED_INDESTRUCTIBLE_RESISTANCE);
        assertFalse(Double.isInfinite(
                ImpactResolver.resistance(50.0, OBSIDIAN_BLAST_RESISTANCE, BLAST_FACTOR, HARDNESS_WEIGHT)));
        assertTrue(crushStrengthOf(50.0, OBSIDIAN_BLAST_RESISTANCE) > stoneStrength());
    }

    private static double leafStrength() {
        return crushStrengthOf(0.2, 0.2);
    }

    private static double ironStrength() {
        return crushStrengthOf(5.0, 6.0);
    }

    @Test
    void whatBearsALoadIsOrderedByWhatItIsMadeOfRatherThanByHowSlowItIsToMine() {
        assertTrue(leafStrength() < dirtStrength());
        assertTrue(dirtStrength() < oakLogStrength());
        assertTrue(oakLogStrength() < stoneStrength());
        assertTrue(stoneStrength() < ironStrength());
        assertTrue(ironStrength() < crushStrengthOf(50.0, OBSIDIAN_BLAST_RESISTANCE));
    }

    @Test
    void miningHardnessAloneWouldHaveWoodOutlastStone() {
        // Vanilla is comparing an axe against a pickaxe there, which says nothing about carrying a boulder.
        // Left at full weight it is the whole reason a build heavy enough to crater stone came to rest on a
        // forest instead of going through it.
        double oakByHardness = ImpactResolver.resistance(2.0, 2.0, 0.25, 1.0);
        double stoneByHardness = ImpactResolver.resistance(1.5, 6.0, 0.25, 1.0);
        assertTrue(oakByHardness > stoneByHardness);

        assertTrue(ImpactResolver.resistance(2.0, 2.0, SHIPPED_BLAST_FACTOR, SHIPPED_HARDNESS_WEIGHT)
                < ImpactResolver.resistance(1.5, 6.0, SHIPPED_BLAST_FACTOR, SHIPPED_HARDNESS_WEIGHT));
    }

    @Test
    void theStrengthDialMovesEveryThresholdByTheSameAmount() {
        assertEquals(SHIPPED_CRUSH_PRESSURE_SCALE / 2.0,
                ImpactResolver.eased(SHIPPED_CRUSH_PRESSURE_SCALE, 2.0));
        assertEquals(SHIPPED_MIN_SPEED / 2.0, ImpactResolver.eased(SHIPPED_MIN_SPEED, 2.0));
        assertEquals(SHIPPED_MIN_SPEED, ImpactResolver.eased(SHIPPED_MIN_SPEED, 1.0));
    }

    @Test
    void theStrengthDialLeavesTheOrderingOfTerrainAlone() {
        double strength = 3.0;
        assertTrue(ImpactResolver.eased(dirtStrength(), strength)
                < ImpactResolver.eased(oakLogStrength(), strength));
        assertTrue(ImpactResolver.eased(oakLogStrength(), strength)
                < ImpactResolver.eased(stoneStrength(), strength));
        // And every one of them by the same ratio, which is what makes it one dial rather than a rebalance.
        assertEquals(dirtStrength() / stoneStrength(),
                ImpactResolver.eased(dirtStrength(), strength) / ImpactResolver.eased(stoneStrength(), strength),
                1.0e-9);
    }

    @Test
    void nothingPermanentIsTalkedOutOfBeingPermanent() {
        assertTrue(Double.isInfinite(ImpactResolver.eased(Double.POSITIVE_INFINITY, 50.0)));
    }

    @Test
    void aStrengthOfZeroIsIgnoredRatherThanDividingByIt() {
        assertEquals(SHIPPED_MIN_SPEED, ImpactResolver.eased(SHIPPED_MIN_SPEED, 0.0));
        assertEquals(SHIPPED_MIN_SPEED, ImpactResolver.eased(SHIPPED_MIN_SPEED, -1.0));
    }

    @Test
    void turningTheDialUpIsWhatGetsAHullThroughATreeItWasHangingOn() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        // Wedged in a canopy: a handful of logs beneath it, branches all round its waist taking the rest.
        double sideways = crushPressure(mass, 6, 40) * SHIPPED_CRUSH_SHEAR;

        assertTrue(sideways > oakLogStrength());
        assertTrue(sideways > ImpactResolver.eased(oakLogStrength(), 2.0));
        // Whatever the dial is set to, it never gets the hull through what it is meant to rest on.
        assertFalse(mass / SPHERE_CROSS_SECTION > ImpactResolver.eased(
                crushStrengthOf(50.0, OBSIDIAN_BLAST_RESISTANCE), 2.0));
    }
    @Test
    void aBoulderCaughtOnBranchesShearsThemOffRatherThanHanging() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        // Six logs under it and forty branches around its waist, which is what catching a canopy looks like.
        double pressure = crushPressure(mass, 6, 40);
        assertTrue(pressure * SHIPPED_CRUSH_SHEAR > oakLogStrength());
    }

    @Test
    void sidewaysContactTakesLoadOffWhatIsUnderneath() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        assertTrue(crushPressure(mass, 100, 200) < crushPressure(mass, 100, 0));
    }

    @Test
    void groundBesideASettledHullIsNotShearedAway() {
        double mass = SPHERE_BLOCKS * OBSIDIAN_BLOCK_MASS;
        double pressure = crushPressure(mass, SPHERE_CROSS_SECTION, 100);
        assertFalse(pressure * SHIPPED_CRUSH_SHEAR > dirtStrength());
    }

}
