package org.restor.create_aeronautics_impact;

/**
 * The whole decision model, as arithmetic.
 *
 * <p>Every question this mod asks - whether a block breaks, which of two sides gives way, what that costs the
 * winner, how much load terrain bears before it gives - is answered here, and answered as a static function
 * of numbers that were passed in.
 *
 * <p><b>This class must never reference Minecraft, NeoForge or Sable.</b> Not as a matter of taste: the JVM
 * verifies a class by loading every type it mentions, so a single import of a game type would pull the whole
 * of Minecraft into the unit tests and there would be no unit tests. That is also why every value it needs
 * from the config arrives as a parameter rather than being read from {@link ImpactConfig} - the call sites
 * are wordier for it, and the model stays testable without a game around it.
 *
 * <p>The same rule applies to {@link SweepDetail}, and to anything else the tests reach.
 */
public final class ImpactResolver {

    private ImpactResolver() {
    }

    /**
     * How much punishment a block takes, derived from the two numbers vanilla gives every block.
     *
     * <p>The two disagree about wood. Mining hardness calls an oak log tougher than stone, because it is
     * slow to chop and stone is quick to pick; blast resistance calls it three times softer, because it is.
     * Nothing here is a pickaxe, so the mining number is only a hint at how solid a block is and gets
     * weighted down accordingly - otherwise a boulder that sinks into bedrock-grade stone comes to rest on a
     * tree, which is the wrong way round in every sense that matters.
     */
    public static double resistance(double destroySpeed, double explosionResistance,
                                    double explosionResistanceFactor, double hardnessWeight) {
        if (destroySpeed < 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(destroySpeed * hardnessWeight, explosionResistance * explosionResistanceFactor);
    }

    /**
     * Applies the global strength dial to one of the numbers standing between a contraption and the block it
     * is pushing on.
     *
     * <p>Every path here ends in the same shape of comparison - something the hull brings, against something
     * the block can take - and the numbers on the block's side of it are all thresholds. Dividing them
     * together moves the whole system at once without disturbing any ratio inside it: terrain keeps its
     * ordering, a heavy build keeps its advantage over a light one, and crushing keeps its relationship with
     * ramming. One dial instead of six that have to be kept in step by hand.
     */
    public static double eased(double threshold, double strength) {
        if (strength <= 0.0 || threshold <= 0.0 || Double.isInfinite(threshold)) {
            return threshold;
        }
        return threshold / strength;
    }

    /**
     * The momentum one block takes out of the body that punched through it.
     *
     * <p>Scaled by what was broken rather than flat. A block is not a fixed lump of mass in the way: it is
     * something that has to be broken first, and what it costs to break is what it is made of. A flat price
     * had a hull cross a mountain and a hull cross a wheat field at the same rate, which is the one thing
     * everyone watching can see is wrong.
     *
     * <p>This is also what makes punching through safe to leave on. A contact this mod drops is one the
     * solver never resolves, so the terrain would otherwise take nothing at all out of the hull and every
     * next layer would be met at the speed of the last - a hull that tunnels to bedrock rather than one that
     * digs in and stops.
     */
    public static double breakDrag(final double resistance, final double speed, final double dragMass) {
        if (dragMass <= 0.0 || resistance <= 0.0) {
            return 0.0;
        }
        final double v = Math.abs(speed);
        return Double.isNaN(v) || Double.isInfinite(v) ? 0.0 : dragMass * resistance * v;
    }

    /**
     * How much speed a hull gives up to the blocks it just broke.
     *
     * <p>The drag is priced per block, and a hull ploughing terrain meets hundreds of them in one tick, so
     * the honest total is routinely more motion than the hull has. Handing that over at once is not a hull
     * being slowed by the ground - it is a hull stopping dead, being picked up by gravity, ploughing, and
     * stopping dead again, which is a stutter rather than a deceleration. The cap spreads the same debt over
     * the ticks that follow: nothing is refunded, and a hull that ran into more than it could pay for still
     * comes to rest, over about a second instead of between two frames.
     */
    public static double speedLost(final double momentum,
                                   final double hullMass,
                                   final double speed,
                                   final double maxShare) {
        if (momentum <= 0.0 || hullMass <= 0.0 || speed <= 0.0) {
            return 0.0;
        }
        return Math.min(momentum / hullMass, speed * maxShare);
    }

    /**
     * Pulls the vanilla resistance range in towards itself. Vanilla spans dirt at 0.5 and obsidian at 300,
     * and feeding that straight into a break speed puts obsidian at hundreds of m/s. An exponent below one
     * keeps the ordering intact while landing the whole range inside speeds a contraption can actually reach.
     */
    public static double compress(double resistance, double exponent) {
        if (exponent >= 1.0 || resistance <= 0.0 || Double.isInfinite(resistance)) {
            return resistance;
        }
        return Math.pow(resistance, exponent);
    }

    /**
     * The speed needed to break a block of this strength, before mass gets a say.
     *
     * <p>Deliberately affine rather than proportional: the floor is added, not multiplied, so a block of no
     * strength at all still cannot be broken by a contraption settling onto it.
     */
    public static double breakSpeed(double resistance, double minImpactSpeed, double hardnessScale) {
        return minImpactSpeed + hardnessScale * resistance;
    }

    /**
     * A contraption's mass spread over the blocks of it that are actually touching something.
     *
     * <p>The denominator is what makes weight mean anything: a hull is heavy because it is large, and a large
     * hull lands on a lot of ground. What breaks terrain is a lot of mass on a little of it.
     */
    public static double contactPressure(double mass, int contactBlocks) {
        if (mass <= 0.0) {
            return 0.0;
        }
        return mass / Math.max(1, contactBlocks);
    }

    /**
     * The load a block bears before it gives way, in the same units {@link #contactPressure} is measured in:
     * mass carried per block of contact.
     *
     * <p>This is the half of an impact that speed cannot express. A hull at rest still weighs what it weighs,
     * and a thing heavy enough on a small enough footprint destroys what is under it without moving at all -
     * which is the difference between a ram and a press, and why a leaning tower falls over rather than
     * standing on a flower.
     */
    public static double crushStrength(double resistance, double pressureScale) {
        if (Double.isInfinite(resistance) || pressureScale <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, resistance) * pressureScale;
    }

    /**
     * The share of the load still pressing on a block that many layers below the surface bearing it.
     *
     * <p>Weight does not stop at the block it is resting on, and it does not arrive at the one underneath
     * undiminished either: each layer hands it to a wider patch of the one below. That is why a footing works,
     * and it is the difference between a heavy thing sinking to a depth and a heavy thing sinking to bedrock.
     */
    public static double crushLoadAt(double pressure, int depth, double spread) {
        if (depth <= 0 || spread <= 0.0) {
            return pressure;
        }
        return pressure / (1.0 + depth * spread);
    }

    /** How far past its crush strength a block is loaded, which is how fast it accumulates damage. */
    public static double crushOvershoot(double pressure, double strength) {
        if (strength <= 0.0) {
            return pressure > 0.0 ? Double.MAX_VALUE : 0.0;
        }
        return pressure / strength;
    }

    /**
     * How much easier this contraption's weight makes breaking things, as a multiplier around one.
     *
     * <p>Clamped at both ends, and the lower clamp is the one that matters: without it a sprawling, hollow
     * build would be arbitrarily bad at breaking anything, which reads as the mod being broken rather than
     * as the build being light.
     */
    public static double massFactor(double contactPressure, double referencePressure, double sensitivity,
                                    double minFactor, double maxFactor) {
        if (contactPressure <= 0.0 || referencePressure <= 0.0 || sensitivity <= 0.0) {
            return 1.0;
        }
        return Math.clamp(Math.pow(contactPressure / referencePressure, sensitivity), minFactor, maxFactor);
    }

    /** Mass eases the hardness a block owes to its material, but not the floor underneath it. */
    public static double effectiveBreakSpeed(double breakSpeed, double massFactor, double floor) {
        return effectiveBreakSpeed(breakSpeed, massFactor, floor, floor);
    }

    /**
     * Weight buys two separate things, and without the second one it buys almost nothing. Compressing the
     * vanilla hardness range leaves most blocks only a metre or two per second above the floor, so easing
     * the material term alone moves a break speed by fractions however heavy the contraption is - which is
     * what makes a mountain of a build feel no different from a raft.
     *
     * <p>So mass also presses the floor itself down, towards {@code crushFloor}: something heavy enough
     * crushes what it settles onto rather than waiting to be thrown at it. Only downwards, though. The floor
     * is what stops a contraption digging its own grave from the moment it is assembled, and a light,
     * sprawling build has no business needing more speed than a dense one to break the same dirt.
     */
    public static double effectiveBreakSpeed(double breakSpeed, double massFactor, double floor, double crushFloor) {
        if (massFactor <= 0.0) {
            return breakSpeed;
        }
        final double easedFloor = Math.clamp(floor / massFactor, Math.min(crushFloor, floor), floor);
        return easedFloor + Math.max(0.0, breakSpeed - floor) / massFactor;
    }

    /**
     * How much of a block's life one qualifying impact takes. An impact only ever reaches this having already
     * passed the break speed, so the softest hit that can happen at all is still worth a full share of the
     * damage - {@code resilience} is exactly how many of those a block survives. Hitting it that many times
     * harder finishes it in one, which is what keeps a real crash looking like a crash rather than a chore.
     */
    public static double crackDamage(double overshoot, double resilience) {
        if (resilience <= 1.0) {
            return 1.0;
        }
        return Math.max(overshoot, 1.0) / resilience;
    }

    /**
     * Whether one side of a contact breaks, given how fast it was hit.
     *
     * <p>{@code budgetExhausted} is folded in here rather than checked by the caller so that running out of
     * tick and being too slow take the same path out: a contact that is declined for either reason is one
     * Sable resolves the ordinary way, and the hull is stopped by it either way.
     */
    public static boolean shouldBreak(double impactVelocity, double breakSpeed, boolean indestructible, boolean budgetExhausted) {
        if (indestructible || budgetExhausted) {
            return false;
        }
        return Math.abs(impactVelocity) >= breakSpeed;
    }

    /**
     * Which side of a contact gives way. Named after Sable's own callback parameters: {@code HIT} is
     * the block the callback sits on, {@code OTHER} is the block on the opposing body.
     */
    public enum Victim {
        NONE,
        HIT,
        OTHER
    }

    /**
     * Effective mass of a two-body impact. As the opposing mass grows without bound this tends to
     * {@code mass}, so immovable terrain is just the limiting case of a contraption-on-contraption hit.
     */
    public static double reducedMass(double mass, double otherMass) {
        if (mass <= 0.0) {
            return 0.0;
        }
        if (otherMass <= 0.0 || Double.isInfinite(otherMass)) {
            return mass;
        }
        return (mass * otherMass) / (mass + otherMass);
    }

    /**
     * What winning an impact costs the winner, as a share of a full break.
     *
     * <p>Something gives way because it was the weaker of the two, not because the other one was unharmed,
     * and a hull that reduces a wall to rubble and comes out without a scratch is the tell that only one side
     * of the contact was ever being modelled. Priced by how close the contest was: a hull ramming something
     * nearly as strong as itself pays nearly a block for every block it takes, and one ploughing soil pays
     * almost nothing. Capped at a full break, so the loser can never cost the winner more than itself.
     */
    public static double wear(final Side winner, final Side loser) {
        final double strength = winner.resistance();
        if (strength <= 0.0 || Double.isInfinite(strength)) {
            return strength <= 0.0 ? 1.0 : 0.0;
        }
        return Math.clamp(loser.resistance() / strength, 0.0, 1.0);
    }

    /**
     * One side of a contact, reduced to the three numbers that decide it.
     *
     * <p>The two are not the same question and are not interchangeable. {@code resistance} is what the block
     * is made of and decides <em>which</em> of the pair gives way; {@code breakSpeed} is what it takes to
     * beat it and decides <em>whether</em> either does. Contraption toughness is deliberately in the second
     * and not the first - see {@link BlockProfile#side(double, double, double)}.
     *
     * @param resistance     material strength, after compression, backing and any override.
     * @param breakSpeed     the speed needed to beat it, after toughness and mass.
     * @param indestructible whether it is never broken, whatever the numbers say.
     */
    public record Side(double resistance, double breakSpeed, boolean indestructible) {

        /** Whether this side breaks at this closing speed, ignoring the tick budget. */
        boolean yieldsTo(double impactVelocity) {
            return shouldBreak(impactVelocity, this.breakSpeed, this.indestructible, false);
        }
    }

    /**
     * Which of the two sides of a contact gives way, or neither.
     *
     * <p>A null {@code other} means the opposing body is not a sub-level - terrain being hit by a hull - and
     * only the struck block is at risk. Otherwise the weaker material is offered up first, and the other is
     * only considered if the weaker one survived: exactly one block can break per contact, because breaking
     * both would let two hulls pass through each other.
     */
    public static Victim victim(double impactVelocity, Side hit, Side other, boolean budgetExhausted) {
        if (budgetExhausted) {
            return Victim.NONE;
        }

        if (other == null) {
            return hit.yieldsTo(impactVelocity) ? Victim.HIT : Victim.NONE;
        }

        // The softer material gives way. A tie on material is settled on what it takes to break each,
        // which is where a build's toughness comes in - so ramming a wall of your own build material still
        // digs instead of eating the contraption, and a build tuned to be flimsy loses to one anyway.
        if (weaker(other, hit)) {
            if (other.yieldsTo(impactVelocity)) {
                return Victim.OTHER;
            }
            return hit.yieldsTo(impactVelocity) ? Victim.HIT : Victim.NONE;
        }

        if (hit.yieldsTo(impactVelocity)) {
            return Victim.HIT;
        }
        return other.yieldsTo(impactVelocity) ? Victim.OTHER : Victim.NONE;
    }

    /**
     * Whether a break also costs the contact. Dropping every contact the moment a block gives way means
     * nothing ever slows a ram down and it sinks through terrain; keeping every one means it bounces off
     * rubble it already destroyed. Only impacts well past the break speed are let through for free, so a
     * ram digs while it is fast and comes to rest once the terrain has taken enough out of it.
     */
    public static boolean punchesThrough(double impactVelocity, double breakSpeed, double punchThroughRatio) {
        if (punchThroughRatio <= 1.0) {
            return true;
        }
        return Math.abs(impactVelocity) >= breakSpeed * punchThroughRatio;
    }

    /** The shipped default for how far behind a struck face {@link Backing} looks for the load bearer. */
    public static final int BACKING_REACH = 3;

    /** The shipped default for what one block beside a struck face is worth against one block behind it. */
    public static final double BACKING_BESIDE = 0.25;

    /**
     * The share of its neighbours' support a block has, from nothing at all to fully buried.
     *
     * <p>Depth carries most of it because that is the direction the load actually travels. The lateral four
     * are worth a fraction each so that one block in a wall does not read the same as one hung in the air -
     * a wall is held together, it is just not held up.
     */
    public static double support(final int behind, final int beside,
                                 final int reach, final double besideWeight) {
        return Math.clamp((behind + beside * besideWeight) / (Math.max(1, reach) + 1.0), 0.0, 1.0);
    }

    /**
     * What that support is worth on the material, given how much of a block's strength is on loan from it.
     *
     * <p>Material alone says a pane of stone hung in the air is exactly as hard to get through as the face of
     * a mountain, which is the reading that makes a wooden hull either bounce off a garden wall or eat a
     * cliff - there is no setting of the material numbers that gets both right, because the difference
     * between the two is not the material.
     */
    public static double backed(final double support, final double weight) {
        final double share = Math.clamp(weight, 0.0, 1.0);
        return 1.0 - share + share * Math.clamp(support, 0.0, 1.0);
    }

    /**
     * Which of two sides is the softer material, with break speed as the tie-break.
     *
     * <p>The tie-break is what makes a build's own toughness matter when it rams a wall of the material it is
     * made of: same resistance, and the side that needs less speed to break is the one that does.
     */
    private static boolean weaker(final Side side, final Side than) {
        if (side.resistance() != than.resistance()) {
            return side.resistance() < than.resistance();
        }
        return side.breakSpeed() < than.breakSpeed();
    }

    /**
     * How fast a broken block is thrown, from how far the impact overshot what the block could take.
     *
     * <p>Capped outright. Debris is a falling block entity, and one launched at the speed a real crash
     * carries lands hundreds of blocks away in someone else's chunks.
     */
    public static double scatterSpeed(double impactVelocity, double resistance, double scatterVelocityScale) {
        double excess = Math.abs(impactVelocity) - resistance;
        if (excess <= 0.0) {
            return 0.0;
        }
        return Math.min(excess * scatterVelocityScale, 2.0);
    }

    /**
     * How much of a shock an impact sends into the body it just broke a block off.
     *
     * <p>Everything else in this class decides one contact, and a contact is a face: a hull that lands on its
     * belly reports contacts along its belly and nowhere else, so the belly is all that can ever break. That
     * is right for a scrape and wrong for a fall - a stone tube dropped three hundred blocks does not lose
     * its underside and keep its walls, it comes apart, because the energy does not stay at the face it
     * arrived through. This is the part that leaves it: what the contact had left over after breaking its own
     * block, as something the neighbouring blocks then have to absorb.
     *
     * <p>Priced off the overshoot rather than the speed, because the overshoot is already the ratio between
     * what arrived and what the material could take - the same fall through ice and through obsidian is not
     * the same shock. Below the threshold there is none: a hull nudging a wall at twice the speed it takes to
     * chip it should chip it, not ring like a bell.
     */
    /**
     * The least a block may cost a shock, whatever its material says. Without it a wave crossing something
     * free would buy an unbounded number of blocks with any budget at all.
     */
    private static final double SHOCK_FLOOR = 0.05;

    /**
     * The shock a moving body is worth, from the energy it is actually carrying.
     *
     * <p>{@link #shockEnergy} prices a shock off one contact, and one contact is a poor witness to a crash: a
     * hull that comes down on a single corner reports one contact, breaks what is around it, and is stopped
     * dead by the solver before its remaining ten thousand blocks touch anything. Nothing about that first
     * contact knows the difference between a boulder and a battleship, so nothing about the wreck does either.
     *
     * <p>The body's kinetic energy does know. It is what the crash actually has to spend, it is quadratic in
     * speed the way a real one is, and it scales with the whole build rather than with the corner that
     * happened to touch first - so a huge fast thing comes apart entirely and a small slow one chips.
     *
     * <p>Expressed in kilojoules, because Sable weighs a block at one or two kilograms and a whole ship is
     * therefore a few tonnes: joules would make the scale a number with four leading zeroes, and megajoules
     * would make every crash in the game round to nothing.
     *
     * <p>{@code floorSpeed} is what the speed is measured from, and it is the difference between a mod that
     * models crashes and one that punishes moving at all. A ship is heavy enough that a walking pace is
     * thousands of joules, so an energy measured from zero has a build that was nudged sideways shedding
     * hull. Measuring from the floor instead makes the whole thing start at a speed rather than fade in from
     * one: below it a crash is worth nothing at all, and just above it the curve leaves the ground gently
     * before turning into the quadratic everybody expects.
     */
    public static double shockKinetic(double mass, double speed, double scale, double floorSpeed) {
        if (scale <= 0.0 || mass <= 0.0 || Double.isNaN(mass) || Double.isInfinite(mass)) {
            return 0.0;
        }
        final double v = Math.abs(speed);
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        final double over = v - Math.max(0.0, floorSpeed);
        return over <= 0.0 ? 0.0 : scale * 0.5 * mass * over * over / 1.0e3;
    }

    public static double shockEnergy(double overshoot, double minOvershoot, double scale) {
        if (scale <= 0.0 || Double.isNaN(overshoot) || overshoot <= minOvershoot) {
            return 0.0;
        }
        final double excess = Math.min(overshoot, 1.0e6) - Math.max(0.0, minOvershoot);
        return excess * scale;
    }

    /**
     * What one block costs the wave that is breaking it.
     *
     * <p>A shock is a budget, not a signal: the crash has so much to spend, every block it destroys takes its
     * own resistance out of that, and it stops when it can no longer afford the next one. The alternative -
     * giving each branch of the wave whatever the last block left it - reads as the more physical of the two
     * and is not, because a wave that forks six ways hands each fork the whole remainder and so creates
     * energy at every block it passes. What that costs is not accuracy but control: reach ends up going as
     * the logarithm of the energy, and a hull dropped from three hundred blocks breaks a third again as much
     * as one dropped from ten.
     *
     * <p>{@code distance} is what keeps a big budget from being spent arbitrarily far away. Each block out
     * from the impact costs more than the last, so a wave with a great deal to spend levels what is near it
     * before it reaches for what is far, and no budget buys unlimited range.
     */
    public static double shockCost(double resistance, double cost, double distance) {
        final double material = Math.max(0.0, resistance) * Math.max(0.0, cost);
        return Math.max(SHOCK_FLOOR, material) * Math.max(1.0, distance);
    }

    /**
     * The least a block may ask of a shock that is measuring itself against strengths rather than budgets.
     * Without it a material the config has priced at nothing would stop nothing and cost nothing, which is
     * an unbounded walk rather than a free one.
     */
    private static final double STRESS_FLOOR = 0.02;

    /**
     * What a block can take before it fails, which under stress is the only number that decides anything.
     *
     * <p>The difference between this and {@link #shockCost} is the whole of phase three. A cost is something
     * a wave pays out of a purse, so a wave with a large enough purse breaks obsidian exactly as readily as
     * glass and simply gets less of it - which is why a big crash used to eat everything within reach
     * indiscriminately. A threshold cannot be outspent. Either what arrives here is stronger than what is
     * here, or it is not, and no amount of energy elsewhere in the crash changes the answer.
     *
     * @param resistance    the block's own strength, after compression and any override.
     * @param modeThreshold what its failure mode multiplies that by - a fraction for brittle, several times
     *                      for ductile.
     * @param backing       how well the block is held by what is around it, at 1 for fully surrounded.
     */
    public static double stressThreshold(double resistance, double modeThreshold, double backing) {
        final double held = Math.max(0.0, resistance)
                * Math.max(0.0, modeThreshold)
                * Math.max(0.0, backing);
        return Double.isNaN(held) ? STRESS_FLOOR : Math.max(STRESS_FLOOR, held);
    }

    /**
     * How much of the shock arriving at a block leaves it on the far side.
     *
     * <p>This is the other half of phase three, and the half that produces the behaviour actually asked for.
     * A block that fails takes its threshold out of what arrived and passes the excess on; a block that
     * holds passes on a share of the whole, <em>having spent nothing</em>. That second case is what lets a
     * shock run the length of a hull: a bulkhead too strong to break is not the end of the crash, it is a
     * thing the crash goes through on its way to the glass behind it.
     *
     * <p>Both are multiplied down by the material, which is attenuation by what was travelled through rather
     * than by how far. Sixteen blocks of wool and sixteen blocks of steel stop being the same distance.
     */
    public static double stressPassed(double intensity, double threshold, boolean broke,
                                      double transmit, double passOn) {
        if (!(intensity > 0.0)) {
            return 0.0;
        }
        final double left = broke
                ? (intensity - Math.max(0.0, threshold)) * Math.max(0.0, passOn)
                : intensity * Math.max(0.0, transmit);
        return Double.isNaN(left) || left <= 0.0 ? 0.0 : left;
    }

    /**
     * How much of its nominal strength a block actually has where it stands.
     *
     * <p>A tile out of its frame is easier to knock out than the same stone in a mountain, and on a hull the
     * blocks with nothing behind them are the skin - which is the surface a crash ought to lose first and
     * the one it was losing last. Counting the solid neighbours is the cheapest statement of that there is,
     * and at a weight of zero the whole thing costs nothing and changes nothing.
     *
     * @param solidNeighbours how many of the six are not air, from 0 to 6.
     * @param weight          how much of the strength is contributed by backing rather than by material.
     */
    public static double stressBacking(int solidNeighbours, double weight) {
        final double support = Math.clamp(solidNeighbours / 6.0, 0.0, 1.0);
        final double share = Math.clamp(weight, 0.0, 1.0);
        return 1.0 - share + share * support;
    }
}
