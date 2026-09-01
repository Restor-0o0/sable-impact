package org.restor.create_aeronautics_impact;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Every setting the mod has, and the per-tick snapshot the rest of the code actually reads.
 *
 * <p>Registered as a {@code SERVER} config, so it lives in the save rather than in the installation: a world
 * carries its own tuning, and a new world starts from the defaults. NeoForge watches the file, so an edit
 * while the server is running arrives as a reload event.
 *
 * <p><b>Adding a setting.</b> Declare a {@code public static final ModConfigSpec.*Value} field between the
 * others - declaration order is the order they appear in the generated toml, so put it beside the ones it
 * relates to - then add a field to {@link Tuning} and read it in {@link Tuning#read()}. The record's
 * component order and the constructor call in {@code read()} must line up; they are positional, and the
 * compiler will only catch a mismatch if the types happen to differ.
 *
 * <p>Skip {@code Tuning} only for a value that is not read per tick: {@code cullInteriorVoxels} is asked
 * once per remesh from a thread that has no tick, and {@code sweepFinestDetail} and {@code materialOverrides}
 * are pushed into the classes that need them from {@code read()} rather than being carried through it.
 *
 * <p>The comment on a setting is the whole of its documentation for a server owner, so it is worth saying
 * what the trade is and why the default is the default, not only what the number does.
 */
public final class ImpactConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue IMPACT_STRENGTH = BUILDER
            .comment("Master dial on how hard everything in this mod hits. 1.0 is the tuning every other value",
                    "here was chosen against; 2 makes the whole system twice as destructive, 0.5 half.",
                    "It divides the thresholds rather than multiplying the forces - hardnessScale, crushSpeed,",
                    "crushPressureScale and crackResilience all come down together, and the mass factor ceiling",
                    "comes up - so nothing inside the model is rebalanced against anything else. Terrain keeps",
                    "its ordering, a compact heavy build keeps its edge over a sprawling light one, and settling",
                    "keeps its relationship with ramming. Reach for this first; the values below are for",
                    "changing the shape of the model, not its overall force.",
                    "Three are deliberately left out of it. minImpactSpeed is a noise floor rather than a",
                    "threshold - it is what keeps a contraption from breaking the ground it was assembled over,",
                    "and dividing it would make every build dig its own grave. carveMinSpeed says where the",
                    "physics solver stops reporting contacts, which no amount of force changes. And",
                    "contraptionBlockToughness is a ratio between the two sides of a collision rather than a",
                    "limit on either, so scaling it would only trade terrain for hulls.")
            .defineInRange("impactStrength", 1.0, 0.05, 50.0);

    public static final ModConfigSpec.DoubleValue MIN_IMPACT_SPEED = BUILDER
            .comment("Impact speed along the collision normal (m/s) below which nothing is ever broken, not",
                    "even for a heavy contraption. Sable pulls at 11 m/s^2, so a hull dropping the height of a",
                    "single block lands at 4.7 m/s; anything under that has to be out of reach or a contraption",
                    "digs its own grave the moment it is assembled, one block of settling at a time.")
            .defineInRange("minImpactSpeed", 6.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue FRAGILE_TRIGGER = BUILDER
            .comment("Speed (m/s) above which a fragile block - leaves, ice, glass, anything Sable tags as",
                    "fragile, and anything given fragile=true in materialOverrides - is handed back to Sable to",
                    "be shattered on its own terms rather than weighed against the hull.",
                    "It sits below minImpactSpeed on purpose. Claiming every block in the world for this mod",
                    "took Sable's own fragile handling away, and a pane of glass that survives because the ship",
                    "hitting it was going too slowly to trigger an impact reads as a bug rather than as physics.",
                    "Raise it to make fragile blocks behave like everything else; lower it to have them break on",
                    "the gentlest touch.")
            .defineInRange("fragileTrigger", 4.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue HARDNESS_SCALE = BUILDER
            .comment("How much a block's resistance raises the speed needed to break it. Higher = terrain resists more.")
            .defineInRange("hardnessScale", 1.8, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue EXPLOSION_RESISTANCE_FACTOR = BUILDER
            .comment("Weight of blast resistance when deriving a block's resistance from its vanilla stats.",
                    "Of the two numbers vanilla offers, this is the one that actually describes how much abuse a",
                    "block absorbs, so it leads for everything but the softest terrain.")
            .defineInRange("explosionResistanceFactor", 0.35, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HARDNESS_WEIGHT = BUILDER
            .comment("Weight of mining hardness in the same derivation. The larger of the two weighted numbers",
                    "wins, and mining hardness is a claim about pickaxes rather than about structure: vanilla",
                    "rates an oak log above stone on it, which at full weight leaves a boulder heavy enough to",
                    "sink into stone sitting politely on top of a forest. At 0.5 the ordering comes out",
                    "leaves, dirt, wood, stone, iron, obsidian, which is the one worth having.",
                    "Set to 1.0 for the old behaviour, where hardness leads for anything that is not stone.")
            .defineInRange("hardnessWeight", 0.5, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue RESISTANCE_EXPONENT = BUILDER
            .comment("Compresses the vanilla hardness range before it becomes a break speed. Vanilla spreads blocks",
                    "over three orders of magnitude (dirt 0.5, stone 1.5, obsidian 300), which at 1.0 puts obsidian",
                    "hundreds of m/s out of reach. 0.5 keeps the ordering while landing everything in a usable band.")
            .defineInRange("resistanceExponent", 0.5, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue INDESTRUCTIBLE_RESISTANCE = BUILDER
            .comment("Blocks with at least this much blast resistance are never broken. A backstop for modded",
                    "blocks meant to be permanent, and only that: vanilla marks bedrock and barriers with a",
                    "negative hardness, which is already read as unbreakable without consulting this at all.",
                    "Set low enough it starts catching things it should not - obsidian, netherite, anvils and",
                    "enchanting tables all sit at 1200, three orders of magnitude below bedrock.")
            .defineInRange("indestructibleResistance", 100000.0, 0.0, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue MASS_SENSITIVITY = BUILDER
            .comment("How strongly a contraption's mass-per-contact-block eases the speed needed to break terrain. 0 disables the effect.")
            .defineInRange("massSensitivity", 1.0, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue REFERENCE_PRESSURE = BUILDER
            .comment("Mass (kg) per contact block at which a contraption is neither helped nor hindered by its weight.",
                    "Sable weighs a plain block at 1 kg and stone/obsidian at 2 kg, so a solid ram carries roughly",
                    "2 kg per block of depth behind the contact face. 12 makes a six-block-deep stone ram neutral.")
            .defineInRange("referencePressure", 12.0, 0.1, 1.0E9);

    public static final ModConfigSpec.DoubleValue MASS_FACTOR_MIN = BUILDER
            .comment("Lower clamp on the mass factor. Below 1 a light, sprawling contraption struggles to break terrain.")
            .defineInRange("massFactorMin", 0.5, 0.01, 1.0);

    public static final ModConfigSpec.DoubleValue CRUSH_SPEED = BUILDER
            .comment("Lowest speed (m/s) a contraption can break terrain at, however much it weighs. Mass drags",
                    "minImpactSpeed down towards this, so a build heavy enough crushes what it settles onto",
                    "instead of only breaking what it is thrown at - which is the only way weight is felt at all,",
                    "since compressing the hardness range leaves most blocks barely above the floor anyway.",
                    "Set this equal to minImpactSpeed to go back to weight easing nothing but material hardness.",
                    "Below about 4.7 a heavy build starts sinking into ground it merely settles onto, which is",
                    "the point: that is the speed one block of free fall delivers. It does not sink forever,",
                    "because the deeper it goes the more of it bears on terrain, and mass factor is weight over",
                    "contact area - the crater it makes is what it takes to hold it up. Lower means deeper.")
            .defineInRange("crushSpeed", 3.8, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MASS_FACTOR_MAX = BUILDER
            .comment("Upper clamp on the mass factor. Above 1 a heavy, compact contraption punches through denser terrain.")
            .defineInRange("massFactorMax", 6.0, 1.0, 100.0);

    public static final ModConfigSpec.BooleanValue CRUSH_BLOCKS = BUILDER
            .comment("Let a contraption destroy what it is standing on by weight alone, with no impact needed.",
                    "Everything else in this mod is driven by how fast something was hit, which leaves a",
                    "stationary build weightless: a solid obsidian sphere thirty blocks across can sit on a forest",
                    "or roll over it without marking it, because a resting contact carries no speed. This is the",
                    "other half - load per block of contact, against how much load the block can take.")
            .define("crushBlocks", true);

    public static final ModConfigSpec.DoubleValue CRUSH_PRESSURE_SCALE = BUILDER
            .comment("How much load a block bears per point of its resistance before it is crushed. Higher means",
                    "sturdier ground. Sable weighs a plain block at 1 and stone or obsidian at 2, so the load is",
                    "roughly the block count of the contraption divided by how many blocks it is resting on.",
                    "At the default an ordinary hull rests on anything, an obsidian sphere thirty blocks across",
                    "pulps trees and dirt and settles into stone, and a pillar stood on its end sinks where the",
                    "same pillar laid flat does not.")
            .defineInRange("crushPressureScale", 70.0, 0.1, 1.0E6);

    public static final ModConfigSpec.IntValue CRUSH_INTERVAL = BUILDER
            .comment("Ticks between crush passes for a contraption that is standing still. Crushing is what makes",
                    "a build sink, and a sink that resolves in one tick is a build teleporting into a hole, so",
                    "settling is meant to be slow. A moving one runs every tick regardless: it meets new ground",
                    "each time, and whatever it was resting on is behind it before a slow pass comes round, which",
                    "is how something far too heavy for a forest ends up rolling along the top of one.")
            .defineInRange("crushInterval", 4, 1, 100);

    public static final ModConfigSpec.IntValue MOVING_CRUSH_INTERVAL = BUILDER
            .comment("The same cadence for a contraption that is moving, which is the expensive case: a column",
                    "scan plus a handful of probes for every block anywhere near the build, every time.",
                    "1 answers weight every tick a hull is over new ground, which is what keeps something far",
                    "too heavy for a forest from rolling along the top of one. Raising it is the largest single",
                    "saving available on a landed or low-flying build, and what it costs is exactly that: a",
                    "boulder rides the treetops for a moment at a time before the canopy under it gives.",
                    "Doubled automatically while adaptiveDetail has the sweep at its coarsest rung.")
            .defineInRange("movingCrushInterval", 1, 1, 40);

    public static final ModConfigSpec.IntValue CRUSH_SPAN = BUILDER
            .comment("How many blocks above its own underside a contraption looks for what is holding it up.",
                    "Anything already buried is held up along its whole submerged flank, not just at the lowest",
                    "point, and counting only the lowest point would read the load as far higher than it is and",
                    "dig a hole that never bottoms out. It wants to cover the whole height of the build: a",
                    "thirty-block sphere touches trees at its equator, fifteen blocks above its underside.")
            .defineInRange("crushSpan", 32, 1, 512);

    public static final ModConfigSpec.IntValue CRUSH_SCAN_BUDGET = BUILDER
            .comment("Blocks one crush pass may examine. Separate from sweepScanBudget on purpose: a pass that",
                    "runs out of budget cannot tell what is holding the build up, so it does nothing at all, and",
                    "sharing a ceiling with the sweeps would have a busy tick elsewhere silently switch crushing",
                    "off. Raise it if very large builds stop crushing; the pass only runs every crushInterval.")
            .defineInRange("crushScanBudget", 65536, 1024, 4194304);

    public static final ModConfigSpec.DoubleValue MAX_TICK_MILLIS = BUILDER
            .comment("How long this mod may spend inside one server tick before it stops and picks the rest up",
                    "on the next one. A tick is fifty milliseconds, so anything approaching that is a stutter",
                    "however well spent it was.",
                    "It is spent twice over: once by the sweep, which decides what breaks, and once by the",
                    "tick that does the breaking, which runs after the physics step has returned and cannot",
                    "share a deadline with something that ran before it. Budget for twice this number.",
                    "The block budgets below cannot do this job on their own. They count work in blocks, and a",
                    "block is not a fixed price - reading one out of a loaded section is a few dozen nanoseconds",
                    "and asking the physics engine whether a hull covers it is a thousand times that. A budget",
                    "in blocks is therefore a guess at a duration, and the guess is wrong by whichever of those",
                    "two the pass turned out to be doing. This is the same limit stated in the units that",
                    "actually matter, and it applies whatever the work turns out to be.",
                    "Lower stutters less and lets fast hulls travel further into terrain before it is cleared.")
            .defineInRange("maxTickMillis", 6.0, 0.5, 50.0);

    public static final ModConfigSpec.BooleanValue ADAPTIVE_DETAIL = BUILDER
            .comment("Whether a sweep that keeps running out of its share of the tick is allowed to do less",
                    "work rather than simply stopping where it ran out.",
                    "Stopping where the time went is the wrong shape of answer for terrain: the pass halts in",
                    "the same place every tick and the far side of the hull is never reached at all, so a",
                    "boulder clears the ground in front of it and ploughs the rest. Doing less means finishing",
                    "roughly instead of finishing half of it exactly.",
                    "It gives ground in order: the swept path is sampled more thinly and more widely, then only",
                    "the direction the hull is travelling is carved instead of all three, then grass clearing",
                    "and unwedging stand down and weight is answered every other tick. It climbs back the",
                    "moment there is room, and never coarsens far enough for a hull to pass through a block.",
                    "Off pins it at full detail, which is quieter terrain and longer ticks under load.")
            .define("adaptiveDetail", true);

    public static final ModConfigSpec.DoubleValue BORE_MIN_SPEED = BUILDER
            .comment("The speed above which a contraption shears the sides of the hole it is making rather",
                    "than cutting a hole its own shape.",
                    "This is the same trade the swept path is sampled on, taken to its conclusion. A fast hull",
                    "is swept coarsely because precision is worth least where it costs most, and coarse",
                    "sampling misses blocks the hull only grazes - so the cheap answer is also the wrong one by",
                    "a block or so at the edges. Widening the hole puts that error on the side it belongs on:",
                    "something arriving at sixty metres a second does not leave a neat cast of itself.",
                    "Raise it out of reach to turn shearing off entirely.")
            .defineInRange("boreMinSpeed", 20.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BORE_SHARE = BUILDER
            .comment("How much of the impact a block beside the hull's path feels, against one in it.",
                    "This is what keeps the widening honest rather than making every hole twice the size. The",
                    "wall blocks are held to the same test as everything else, at a fraction of the speed, so",
                    "a hull tearing through soil takes the sides with it and the same hull glancing off stone",
                    "leaves them standing. Zero turns shearing off.")
            .defineInRange("boreShare", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CRUSH_SEAT = BUILDER
            .comment("The share of the ground a build covers that counts as carrying it, however few blocks",
                    "happen to report contact on any one tick. A boulder rolling across a field touches maybe",
                    "half a dozen blocks at a time, and reading that as its whole weight resting on half a dozen",
                    "blocks is what had it ploughing a trench through everything it crossed - the same boulder",
                    "parked on the same field spreads that weight over seven hundred and dents the grass.",
                    "Neither reading is right on its own. The contact patch really is small, and it really does",
                    "not carry the load alone: the ground under it is a solid, and a solid moves what is beside",
                    "it. This is how much of that is credited.",
                    "0 goes back to counting only what was touched, and turns a rolling build into a digger.",
                    "1 spreads every load over the build's whole shadow, and nothing ever sinks into anything.",
                    "Note this is a floor rather than a cap: a build that really is resting on more than this",
                    "is read as resting on what it is resting on.")
            .defineInRange("crushSeat", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CRUSH_SHEAR = BUILDER
            .comment("How much of a block's load a sideways contact counts for, against one bearing the weight",
                    "from directly above. A boulder settling into a treetop ends up held as much by the branches",
                    "around its flanks as by anything under it, and those branches are being pushed rather than",
                    "compressed - a lighter job than holding the thing up, but not one a twig gets to do to a",
                    "hillside of obsidian. 0 ignores sideways contact entirely, which leaves a build free to hang",
                    "on whatever it happens to brush against.")
            .defineInRange("crushShear", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CRUSH_DEPTH = BUILDER
            .comment("How many blocks the load travels through the terrain away from where the hull is touching",
                    "it. Weight does not stop at the block it is resting on: a boulder dropped on a tree does not",
                    "shave the top log off and sit on the stump, it takes the trunk with it, and the branches",
                    "either side of the trunk with that. Load only travels through blocks - a gap stops it - so",
                    "this follows the shape of what is being crushed instead of carving a sphere out of it.",
                    "Most of the time crushSpread runs the load out long before this does; it is a backstop,",
                    "and the one that matters if crushSpread is turned down to nothing.")
            .defineInRange("crushDepth", 8, 1, 64);

    public static final ModConfigSpec.DoubleValue CRUSH_SPREAD = BUILDER
            .comment("How much of the load is lost with each block it travels, on top of the loss from being",
                    "spread. Weight handed on is divided between the block below and the four beside it rather",
                    "than given to each of them whole, so it thins quickly sideways and travels mostly straight",
                    "down - which is why a footing works, why a heavy thing sinks to a depth rather than to",
                    "bedrock, and why the damage follows what the hull is standing on instead of hollowing out",
                    "a ball around it. This decides how far past the contact the damage reaches: something",
                    "barely over the ground's strength marks only what it touches, while something two hundred",
                    "times over it drives a few blocks down and a block or so out. That is the difference",
                    "between a ship denting a field and a boulder settling into one, and it comes out of the",
                    "same number rather than needing to be asked for.",
                    "0 turns this extra loss off, at which point the spread alone bounds how far it carries.")
            .defineInRange("crushSpread", 3.0, 0.0, 8.0);

    public static final ModConfigSpec.DoubleValue CRUSH_DOWN_SHARE = BUILDER
            .comment("The share of a load handed to the block directly below, against the share each of the four",
                    "sides gets. This and crushSideShare should come to one between them, because a load that is",
                    "spread is divided rather than copied - handing every neighbour the whole thing multiplies it",
                    "by four per layer, which hollows out everything within reach of a boulder that touched two",
                    "blocks. Nothing is ever handed upwards: weight travels down through what is carrying it and",
                    "leans on what is beside that, it does not climb back over the thing pressing it down.",
                    "Higher makes the damage a narrow shaft; lower makes it a shallow bowl.")
            .defineInRange("crushDownShare", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CRUSH_SIDE_SHARE = BUILDER
            .comment("The share each of the four sideways neighbours gets. Four of these plus crushDownShare is",
                    "the whole load, so 0.1 spends 0.4 sideways and loses nothing.")
            .defineInRange("crushSideShare", 0.1, 0.0, 0.25);

    public static final ModConfigSpec.DoubleValue CRUSH_LEAD_TICKS = BUILDER
            .comment("Ticks of travel the crush pass looks ahead along a moving hull's own velocity, so that",
                    "ground is answered as the hull arrives over it rather than after it has gone past. Its",
                    "effect is bounded internally, so a very fast hull does not turn the pass into a wide carve.")
            .defineInRange("crushLeadTicks", 3.0, 0.0, 16.0);

    public static final ModConfigSpec.BooleanValue CRUSH_DISPLACE = BUILDER
            .comment("Whether a block giving way under a weight is shoved aside rather than destroyed. Something",
                    "heavy pressing into ground does not make the ground disappear, it moves it: the furrow a",
                    "boulder leaves has banks either side of it, and those banks are what came out of the",
                    "furrow. A block goes to the nearest free spot outward from the hull, preferring one with",
                    "something under it so the spoil heaps up rather than hangs. Only when there is nowhere for",
                    "it to go does it break, so a hull working into a solid face still gets through.")
            .define("crushDisplace", true);

    public static final ModConfigSpec.IntValue CRUSH_DISPLACE_REACH = BUILDER
            .comment("How far a shoved block may be carried looking for room. Too small and a wide furrow has",
                    "nowhere left to put anything and goes back to eating terrain; too large and spoil lands",
                    "well away from what moved it.")
            .defineInRange("crushDisplaceReach", 3, 1, 16);

    public static final ModConfigSpec.IntValue MAX_CRUSH_PER_TICK = BUILDER
            .comment("Cap on blocks crushed per contraption per pass, counted separately for what is under the",
                    "build and what it is wedged against. Separately because the two compete otherwise: a build",
                    "sinking into soft ground digs greedily downwards, and a shared cap would have that starve",
                    "the branches at its flanks - which are the things actually holding it up.")
            .defineInRange("maxCrushPerTick", 256, 0, 8192);

    public static final ModConfigSpec.BooleanValue BREAK_CONTRAPTION_BLOCKS = BUILDER
            .comment("Whether a contraption loses its own blocks when it rams terrain harder than they are.")
            .define("breakContraptionBlocks", true);

    public static final ModConfigSpec.DoubleValue CONTRAPTION_BLOCK_TOUGHNESS = BUILDER
            .comment("Multiplier on a contraption block's resistance when it is weighed against the terrain it hit. "
                    + "Above 1 makes builds hold together better than the raw material would suggest.")
            .defineInRange("contraptionBlockToughness", 1.5, 0.01, 100.0);

    public static final ModConfigSpec.DoubleValue BACKING_WEIGHT = BUILDER
            .comment("How much of a terrain block's strength is on loan from what is holding it in place rather",
                    "than from what it is made of. At 0 a pane of stone hung in the air is as hard to get",
                    "through as the face of a mountain, which is what makes a wooden hull either bounce off a",
                    "garden wall or eat a cliff - there is no material setting that gets both right. At 0.6 a",
                    "one-block wall keeps a little over half its strength, so wood goes through a shed and stops",
                    "dead against a hillside. Contraptions are exempt: a hull is one rigid body and its blocks",
                    "really are carrying each other.")
            .defineInRange("backingWeight", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.IntValue BACKING_REACH = BUILDER
            .comment("How many blocks behind a struck face count towards holding it up, and therefore how thick",
                    "a wall has to be before it reads as a hillside rather than as a panel. A gap ends the count",
                    "rather than being skipped past, so a facade with air behind it is a facade.",
                    "At 3 a wooden hull goes through two blocks of stone and stops against three. Raise it for a",
                    "world where only real mass stops anything; lower it to make thin walls matter.")
            .defineInRange("backingReach", 3, 1, 8);

    public static final ModConfigSpec.DoubleValue BACKING_BESIDE = BUILDER
            .comment("What one block beside a struck face is worth against one block of depth behind it. Depth",
                    "carries most of the load because that is the direction it actually travels; the lateral",
                    "four are what keep one block in a wall from reading as free-standing. 0 counts depth alone.")
            .defineInRange("backingBeside", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HULL_BACKING_WEIGHT = BUILDER
            .comment("The same reading applied to a contraption's own blocks: how much of one block's strength",
                    "comes from the rest of the build standing behind it rather than from what it is made of.",
                    "A hull is one rigid body, which is the argument for exempting it - but it is also the",
                    "reason a hollow build lands like a solid one. A wooden shell one block thick has nothing",
                    "behind its skin, and at 0 it meets the ground with the same numbers a wooden cube of the",
                    "same footprint does: a few blocks go where it touched and the rest rides down intact.",
                    "Above 0 the skin of a hollow build is the weakest part of it and comes apart first, while",
                    "a densely packed one is barely touched - which is the difference between the two that the",
                    "material numbers cannot express.",
                    "Set to 0 for the old behaviour, where a contraption block is always fully backed. 1 makes a",
                    "free-standing block worth nothing at all, which is a build made of eggshell.")
            .defineInRange("hullBackingWeight", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HULL_BACKING_REACH = BUILDER
            .comment("How deep into a build the load is traced before it counts as fully supported. Kept apart",
                    "from backingReach because the two are asking about different things: terrain is asking",
                    "whether it is a wall or a hillside, and a build is asking how much of itself is in the way.",
                    "At 3 a shell three blocks thick lands like solid material and anything thinner gives.")
            .defineInRange("hullBackingReach", 3, 1, 8);

    public static final ModConfigSpec.DoubleValue IMPACT_WEAR = BUILDER
            .comment("What winning an impact costs the winner, as a share of how evenly matched the two sides",
                    "were. Punching through something nearly as strong as you wears you down nearly as fast as",
                    "it wears down; ploughing soil costs nothing worth counting. This is what stops a hull from",
                    "coming out of a wall it demolished without a scratch. Needs crackBlocks; 0 turns it off.")
            .defineInRange("impactWear", 1.0, 0.0, 4.0);

    public static final ModConfigSpec.BooleanValue CRACK_BLOCKS = BUILDER
            .comment("Let blocks remember what earlier impacts did to them instead of being either untouched or",
                    "gone. A hit hard enough to break a block outright under the old rule instead deals damage,",
                    "the block shows the vanilla mining cracks as it takes it, and it shatters once it is full.",
                    "This is what makes ramming the same wall twice mean something, and what makes a ram that is",
                    "marginally too slow leave a mark rather than nothing at all.")
            .define("crackBlocks", true);

    public static final ModConfigSpec.DoubleValue CRACK_RESILIENCE = BUILDER
            .comment("How many impacts at exactly a block's break speed it survives. An impact this many times",
                    "past the break speed still destroys it in one, so a real crash looks the way it always did",
                    "and only marginal hits crack. 1.0 turns cracking off entirely and restores the old",
                    "all-or-nothing behaviour exactly.")
            .defineInRange("crackResilience", 3.0, 1.0, 20.0);

    public static final ModConfigSpec.IntValue CRACK_HEAL_TICKS = BUILDER
            .comment("Ticks a fully cracked block takes to recover completely if nothing hits it again. Without",
                    "this a build shuffling against a cliff for ten minutes would eventually flatten it.")
            .defineInRange("crackHealTicks", 300, 20, 24000);

    public static final ModConfigSpec.DoubleValue CRACK_SPALL = BUILDER
            .comment("Damage dealt to the six blocks around one that has just been destroyed. Spall can never",
                    "finish a block off on its own, so a crater edge crumbles into cracked stone and stops",
                    "instead of running away through the hillside one neighbour at a time. 0 disables it.")
            .defineInRange("crackSpall", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CRACK_SPALL_CEILING = BUILDER
            .comment("How far spall alone may crack a block, as a share of what breaking it takes. Below 1 a",
                    "crater edge crumbles to cracked stone and stops instead of running away through the",
                    "hillside one neighbour at a time. At 1 it can finish blocks off, and does: each one it",
                    "finishes spalls its own neighbours, and the crater eats the mountain.")
            .defineInRange("crackSpallCeiling", 0.95, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MAX_CRACKED_BLOCKS = BUILDER
            .comment("How many part-damaged blocks are remembered per level at once. Blocks past this cap go",
                    "back to breaking or surviving outright rather than being remembered.")
            .defineInRange("maxCrackedBlocks", 4096, 0, 262144);

    public static final ModConfigSpec.IntValue MAX_CRACK_EFFECTS_PER_TICK = BUILDER
            .comment("Cap on crack overlays updated per level per tick, and on breaks that are allowed to spall.",
                    "Each overlay is a packet to every player in range and a hull ploughing a hillside cracks",
                    "blocks by the hundred. Damage past the cap is still recorded, it just becomes visible on a",
                    "later tick.")
            .defineInRange("maxCrackEffectsPerTick", 64, 0, 4096);

    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TICK = BUILDER
            .comment("Hard cap on blocks destroyed by impacts per level per tick. A wide contraption touches many",
                    "blocks at once across several physics sub-steps; too low a cap makes the rest of the hull",
                    "bounce off terrain it should have ploughed through. A large build lands hundreds of blocks",
                    "on the ground at once, so a cap of a couple of hundred is spent before most of its face has",
                    "been looked at and the rest of it rides on terrain that should have given way.")
            .defineInRange("maxBlocksPerTick", 512, 1, 8192);

    public static final ModConfigSpec.IntValue MAX_BREAK_EFFECTS_PER_TICK = BUILDER
            .comment("Hard cap on breaks per level per tick that play the block-break sound and particles. Every",
                    "one of those is a packet to every player in range, and a hull ploughing terrain produces",
                    "hundreds per tick. Breaks past the cap are silent - the crash still looks like a crash",
                    "because the first ones are not. Silent breaks drop nothing regardless of dropItems.")
            .defineInRange("maxBreakEffectsPerTick", 24, 0, 4096);

    static {
        BUILDER.comment("What a broken block does on its way out: whether it flies, how hard it is thrown, and",
                        "where it comes to rest. A block that is not thrown is simply gone, so what is set here",
                        "is the difference between a crash that scatters wreckage and one that leaves a clean",
                        "hole. It is also the most expensive part of the mod - a piece of debris is a ticking",
                        "entity that has to fall, land and write a block back - so all of it is rationed.",
                        "Terrain and contraptions are asked separately, because they are not the same wish: a",
                        "hillside that keeps its rubble is scenery, a ship shedding its hull is the crash.")
                .push("debris");
    }

    public static final ModConfigSpec.DoubleValue SCATTER_CHANCE = BUILDER
            .comment("Fraction of broken terrain blocks that fly off as debris instead of simply vanishing.",
                    "1.0 throws everything the per-tick cap can afford, which is what to reach for if a crater",
                    "should be surrounded by what came out of it. 0 turns terrain debris off.")
            .defineInRange("scatterChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CONTRAPTION_SCATTER_CHANCE = BUILDER
            .comment("The same for a contraption's own blocks. Higher than terrain by default: a ship losing its",
                    "hull is the thing being watched, there are far fewer of these blocks than there is ground",
                    "being ploughed, and a piece of hull that vanishes reads as the mod failing to do anything",
                    "rather than as a break.")
            .defineInRange("contraptionScatterChance", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MAX_SCATTER_PER_TICK = BUILDER
            .comment("Hard cap on debris entities spawned per level per tick. Blocks past the cap still break,",
                    "they just vanish instead of flying. This is the number that keeps the two chances above",
                    "from being a server killer: a hull ploughing a hillside breaks blocks by the hundred, and",
                    "each one turned into an entity has to fall, land, and be sent to every client in range.")
            .defineInRange("maxScatterPerTick", 96, 0, 4096);

    public static final ModConfigSpec.DoubleValue SCATTER_VELOCITY_SCALE = BUILDER
            .comment("How fast debris is thrown, relative to how much the impact overshot the block's resistance.",
                    "Raising it widens the field the wreckage ends up spread over; much past 1.0 blocks are",
                    "thrown far enough to land clear of the crash and stop looking like part of it.")
            .defineInRange("scatterVelocityScale", 0.25, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SCATTER_UPWARD_KICK = BUILDER
            .comment("A flat upward push given to every piece of debris on top of the direction the impact threw",
                    "it. Without some of this a block broken by a downward hit is driven straight back into the",
                    "ground and settles where it stood, which looks like nothing happened to it.")
            .defineInRange("scatterUpwardKick", 0.15, 0.0, 2.0);

    public static final ModConfigSpec.IntValue LANDING_SEARCH = BUILDER
            .comment("How far a piece of debris may look for somewhere to put itself when it comes down",
                    "somewhere it cannot be placed - into a wall, onto a slab, back into the hole it was thrown",
                    "out of. A vanilla falling block gives up there and becomes an item or nothing at all, and",
                    "that is why wreckage disappears. At 2 almost everything finds a home within a block or two",
                    "of where it landed. Each step out is a shell of positions to test, so this is not free:",
                    "0 restores vanilla's behaviour outright.")
            .defineInRange("landingSearch", 2, 0, 8);

    public static final ModConfigSpec.BooleanValue LANDING_NEEDS_FLOOR = BUILDER
            .comment("Whether a spot found by that search has to have something solid under it. On, debris piles",
                    "up on the ground and against walls the way rubble does. Off, it takes the first free",
                    "position it finds, which fills overhangs in and leaves blocks standing in mid-air.")
            .define("landingNeedsFloor", true);

    public static final ModConfigSpec.IntValue LIFETIME_TICKS = BUILDER
            .comment("How long a piece of debris may stay in the air before it is made to come down wherever it",
                    "has got to, in ticks. 200 is ten seconds, far longer than anything thrown by an impact",
                    "needs, and is a backstop against debris flung out over an ocean ticking for as long as the",
                    "chunk stays loaded. 0 leaves vanilla's own limit as the only one.")
            .defineInRange("lifetimeTicks", 200, 0, 6000);

    public static final ModConfigSpec.BooleanValue DROP_WHEN_LOST = BUILDER
            .comment("What becomes of a piece of debris that found nowhere at all to be placed: on, it drops as",
                    "an item, off, it is gone. Only reached once the search above has failed, so this is a",
                    "handful of blocks per crash rather than all of them - though a crash in a cave with this",
                    "on can still leave a lot of items lying about.")
            .define("dropWhenLost", true);

    public static final ModConfigSpec.DoubleValue DEBRIS_DAMAGE_PER_BLOCK = BUILDER
            .comment("Fall damage debris deals to what it lands on, per block fallen, the way an anvil does. 0",
                    "makes it harmless. Much above 0.5 makes standing anywhere near a crash lethal, which is",
                    "honest and is also how a player loses an inventory to scenery.")
            .defineInRange("damagePerBlock", 0.0, 0.0, 10.0);

    public static final ModConfigSpec.IntValue DEBRIS_DAMAGE_MAX = BUILDER
            .comment("Ceiling on that damage from any one piece of debris, however far it fell.")
            .defineInRange("damageMax", 40, 0, 1000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("How far past the face an impact is felt. Everything else in this mod decides one",
                        "contact, and a contact is a face - a hull that lands on its belly reports contacts",
                        "along its belly and nowhere else, so without this the belly is the only thing that can",
                        "ever break, however far the thing fell. A shock is the energy the contact had left",
                        "over once it had broken its own block, handed to the blocks around it: it spreads",
                        "through whatever is touching, pays each block's resistance out of itself as it goes,",
                        "and dies when there is nothing left. It is what makes a stone hull dropped from",
                        "orbit come apart instead of losing a floor, and it is the single most destructive",
                        "thing here - the ceilings below are not decoration.")
                .push("shock");
    }

    public static final ModConfigSpec.BooleanValue SHOCK_BLOCKS = BUILDER
            .comment("Whether an impact is felt past the block it broke at all. Off, only blocks actually in",
                    "contact ever break, which is cheap, entirely predictable, and leaves a hull that hits the",
                    "ground at terminal velocity looking like it was set down on it.")
            .define("shockBlocks", true);

    public static final ModConfigSpec.DoubleValue SHOCK_MIN_OVERSHOOT = BUILDER
            .comment("How far past a block's break speed an impact has to be before it sends a shock at all,",
                    "as a ratio. Below this a break stays where it happened. This is the setting that keeps",
                    "ordinary scraping, ploughing and landing from shaking builds apart: raise it and only",
                    "outright crashes propagate, lower it towards 1 and every block broken anywhere sends a",
                    "wave through whatever it was attached to.")
            .defineInRange("minOvershoot", 2.0, 1.0, 1000.0);

    public static final ModConfigSpec.DoubleValue HULL_SHOCK_SCALE = BUILDER
            .comment("Contact-side energy a contraption's own blocks pass on per unit of overshoot past",
                    "minOvershoot. A wave gets the larger of this and what kineticScale below makes of the",
                    "whole body, so on any real crash the kinetic answer is in charge and this one decides",
                    "the small end: a wing clipping a tower, one block driven into a wall. A build made of",
                    "something soft spends less per block, so the same number goes much further through a",
                    "wooden ship than through a stone one.")
            .defineInRange("hullScale", 8.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue TERRAIN_SHOCK_SCALE = BUILDER
            .comment("The same for the world's own blocks, which is a different wish and so a separate number.",
                    "A crash that takes a bite out of a hillside is a crater; one that keeps going is a hull",
                    "that has dug itself a tunnel and a landscape that does not come back. Kept low by",
                    "default for that reason - raise it for demolition, set it to 0 to leave terrain reading",
                    "contacts only.")
            .defineInRange("terrainScale", 1.5, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue SHOCK_KINETIC_SCALE = BUILDER
            .comment("Shock energy per kilojoule the striking body is actually carrying, which is the number",
                    "that decides whether a crash is survivable. hullScale prices a shock off one contact,",
                    "and one contact is a poor witness: a hull that comes down on a corner reports that",
                    "corner, and the solver stops the whole body dead before its remaining ten thousand",
                    "blocks touch anything - so the corner is all that ever breaks, however big and however",
                    "fast the thing was. Kinetic energy knows the difference. It is quadratic in speed, it",
                    "counts the whole build rather than the part that touched first, and it is drawn from",
                    "once per body per tick so a landing that reports six hundred contacts is still one",
                    "crash. Sable weighs a block at one or two kilograms, so a four-thousand-block stone",
                    "ship is about eight tonnes: at 1.0 it survives a landing at twelve metres a second",
                    "missing a tenth of itself, and arrives at sixty as a heap. 0 leaves shocks priced on",
                    "the contact alone, which is the 1.2.0 behaviour.")
            .defineInRange("kineticScale", 1.0, 0.0, 1.0E6);

    public static final ModConfigSpec.DoubleValue SHOCK_CONTACT_SHARE = BUILDER
            .comment("The largest share of a crash's remaining energy any one contact may spend. A landing",
                    "reports its contacts all along the face that touched, and this is what decides whether",
                    "the damage looks like that or like one point. At 1 the first contact to be processed",
                    "takes the whole crash and levels a sphere around itself, which reads as the build having",
                    "been shot rather than dropped - and worse, one sphere of ten thousand blocks cannot be",
                    "broken inside one tick, so it crawls outwards over the next second like something eating",
                    "the wreck from the middle. Lower spreads the same total over the whole contact face as",
                    "several smaller waves that each finish in the tick they started.",
                    "Nothing is lost either way: what one contact does not take is left for the next.")
            .defineInRange("perContactShare", 0.2, 0.01, 1.0);

    public static final ModConfigSpec.DoubleValue SHOCK_COST = BUILDER
            .comment("What one block's resistance costs the wave passing through it. Higher makes material",
                    "matter more: an obsidian bulkhead stops a wave that ran the length of a wooden deck.",
                    "Lower makes the shock care about distance alone, and every material comes apart alike.")
            .defineInRange("cost", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue SHOCK_FALLOFF = BUILDER
            .comment("The share of its purchasing power a wave keeps for every block it travels. A block one",
                    "step out costs its material; one fifty steps out costs that divided by falloff fifty",
                    "times over. This is what keeps a large crash from being spent arbitrarily far from",
                    "where it happened: the wreck levels what is near it before it reaches for what is far.",
                    "At 0.98 a hundred-block hull still comes apart end to end on a bad enough fall; at 0.9",
                    "the damage stays within about twenty blocks of the impact whatever the fall was.")
            .defineInRange("falloff", 0.98, 0.1, 1.0);

    public static final ModConfigSpec.IntValue SHOCK_MAX_PER_IMPACT = BUILDER
            .comment("Ceiling on blocks one shock may break over its whole life. A wave that runs into the",
                    "per-tick ceiling below is not cancelled, it is put down and picked up on the next tick,",
                    "so this is a total and not a rate: it is what stops a single crash from being allowed to",
                    "eat an unbounded amount, however many ticks it is given.")
            .defineInRange("maxBlocksPerImpact", 8192, 0, 262144);

    public static final ModConfigSpec.IntValue SHOCK_MAX_PER_TICK = BUILDER
            .comment("Ceiling on blocks all shocks together may break per level per tick. A hull landing flat",
                    "reports hundreds of contacts in one tick and every one of them may send a wave, so this",
                    "rather than maxBlocksPerImpact is what stands between a big crash and the tick budget.",
                    "Waves stopped by it resume next tick, so what this really sets is how fast a wreck comes",
                    "apart rather than how much of it does. Separate from the root maxBlocksPerTick, which",
                    "counts only what contacts themselves broke.")
            .defineInRange("maxBlocksPerTick", 6144, 0, 262144);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec.BooleanValue DROP_ITEMS = BUILDER
            .comment("Whether shattered blocks drop their items.")
            .define("dropItems", false);

    public static final ModConfigSpec.BooleanValue PUNCH_THROUGH = BUILDER
            .comment("Whether a hard enough impact drops the contact as well as the block, letting the hull",
                    "carry on into the next layer instead of being stopped by what it just destroyed.",
                    "This is what decides whether a crash looks like a crash. Off, the solver stops the whole",
                    "body dead against the first block it breaks - a hundred-block hull comes down on one",
                    "corner, that corner reports the impact, and the other ten thousand blocks never touch",
                    "anything at all. On, the block gives way, takes its own share of the hull's momentum with",
                    "it (see breakDragMass) and the rest of the build carries on into the ground, which is how",
                    "the whole of it gets to meet the whole of the ground.",
                    "The cost is a hull yanked hard enough burying itself: each layer is met at nearly the",
                    "speed of the last, so nothing stops it until it runs out. breakDragMass and breakDragMax",
                    "are what stand against that, and punchThroughRatio is what keeps ordinary landings out",
                    "of it entirely.")
            .define("punchThrough", true);

    public static final ModConfigSpec.DoubleValue PUNCH_THROUGH_RATIO = BUILDER
            .comment("With punchThrough on, how far an impact has to overshoot a block's break speed before the",
                    "contact is dropped as well as the block. Below this the block still shatters but the hull is",
                    "still pushed back by it, so a ram bleeds speed as it digs.")
            .defineInRange("punchThroughRatio", 2.5, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue BREAK_DRAG_MASS = BUILDER
            .comment("Mass (kg) a contraption has to drag up to its own speed for every point of resistance of",
                    "every block it punches clean through. This is how much momentum a block gets to absorb on",
                    "its way out, and it is priced by material: crossing a mountain costs several times what",
                    "crossing a wheat field does, which a flat price could not say.",
                    "It is also the only thing slowing a ram that is fast enough to be waved past the terrain.",
                    "Without it gravity keeps adding speed, every next layer is easier than the last, and the",
                    "hull tunnels to bedrock. Higher = terrain grabs harder, 0 = free digging.")
            .defineInRange("breakDragMass", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BREAK_DRAG_MAX = BUILDER
            .comment("The largest share of its speed a contraption may lose to breaking blocks in one tick.",
                    "Without a ceiling here the drag above is a per-block cost with nothing stopping it from",
                    "adding up past the whole of the hull's motion in a single tick, and it does: a hull",
                    "ploughing terrain meets hundreds of blocks at once, and paying for all of them at once",
                    "stops it dead. Then gravity picks it up again, it ploughs again, and it stops again - which",
                    "is not a hull being slowed down by the ground, it is a hull stuttering, and it reads as the",
                    "game hitching even though the server is keeping perfect time.",
                    "The energy is not refunded, only spread: a hull that has run into more than it can pay for",
                    "goes on paying on the following ticks, and comes to rest just the same, over about a second",
                    "rather than between two frames.",
                    "1 removes the ceiling and restores the dead stops. Low values make terrain feel like water.")
            .defineInRange("breakDragMax", 0.25, 0.01, 1.0);

    public static final ModConfigSpec.BooleanValue CULL_INTERIOR_VOXELS = BUILDER
            .comment("Let Sable merge fully buried blocks back into their neighbours.",
                    "A block carrying a collision callback can never be merged, because a merged collider has no",
                    "single block position to report the hit against - and this mod puts a callback on every block.",
                    "Left off, that means no terrain anywhere is ever merged, which is by far the largest cost the",
                    "mod adds. A block walled in on all six sides cannot be the first thing a hull touches, so it",
                    "gives its voxel up; Sable re-derives the six neighbours of every block change, so the moment",
                    "a hull breaks in, the new tunnel wall gets its voxels back before the next step.")
            .define("cullInteriorVoxels", true);

    public static final ModConfigSpec.BooleanValue CARVE_THROUGH_TERRAIN = BUILDER
            .comment("Break the terrain a fast contraption is about to move into, instead of waiting for the",
                    "contact that reports it. Past roughly a block of travel per step the solver stops raising a",
                    "contact for every block on the way through, so a fast hull passes into terrain that was never",
                    "reported and therefore never broken - and ends up buried in solid ground it cannot push out of.",
                    "Carving applies the same break rules and the same drag the contact path would have.")
            .define("carveThroughTerrain", true);

    public static final ModConfigSpec.DoubleValue CARVE_MIN_SPEED = BUILDER
            .comment("Speed (m/s) above which a contraption carves its own path. Above it the solver stops",
                    "reporting every block on the way, so without carving a hull comes to rest inside the ground.",
                    "It matters well below that too, though, and for a different reason: a contact is reported at",
                    "the moment of touching, which is already too late to stop the hull being bounced off what it",
                    "touched. Breaking a stride ahead of the hull is what stops a boulder rolling down a slope",
                    "being turned ninety degrees by a tree that was about to give way anyway.")
            .defineInRange("carveMinSpeed", 8.0, 1.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CARVE_LOOKAHEAD_TICKS = BUILDER
            .comment("How many ticks of travel carving looks ahead. One tick is what the hull is about to cross",
                    "and is therefore the least that can work, which is exactly why it does not: a hull under",
                    "thrust or gravity arrives slightly faster than last tick's velocity said it would, and",
                    "anything it overshoots into is uncarved rock met at full speed - the one case the solver",
                    "has no contact for. Higher clears more of the path and costs more per sweep.")
            .defineInRange("carveLookaheadTicks", 2.0, 1.0, 8.0);

    public static final ModConfigSpec.IntValue CARVE_MAX_BLOCKS = BUILDER
            .comment("Cap on blocks carved per contraption per tick.")
            .defineInRange("carveMaxBlocks", 512, 1, 8192);

    public static final ModConfigSpec.BooleanValue CLEAR_SOFT_BLOCKS = BUILDER
            .comment("Sweep away blocks a contraption flies straight through. Grass, flowers, vines, cobwebs and",
                    "similar have no collision box, so they never produce an impact and the hull silently swallows",
                    "them instead of mowing them down.")
            .define("clearSoftBlocks", true);

    public static final ModConfigSpec.IntValue SOFT_SWEEP_INTERVAL = BUILDER
            .comment("Ticks between soft-block sweeps. Higher is cheaper and slightly less precise.")
            .defineInRange("softSweepInterval", 2, 1, 20);

    public static final ModConfigSpec.DoubleValue SOFT_SWEEP_MIN_SPEED = BUILDER
            .comment("Contraptions slower than this (m/s) are not swept, so parked builds cost nothing.")
            .defineInRange("softSweepMinSpeed", 0.5, 0.0, 100.0);

    public static final ModConfigSpec.IntValue SOFT_SWEEP_MAX_BLOCKS = BUILDER
            .comment("Cap on soft blocks cleared per contraption per sweep.")
            .defineInRange("softSweepMaxBlocks", 256, 1, 8192);

    public static final ModConfigSpec.BooleanValue CLEAR_OVERLAPS = BUILDER
            .comment("Free contraptions that have ended up inside terrain. Two solids in the same place is not",
                    "something the solver can push apart, so a hull thrown or dragged into a wall hard enough",
                    "wedges itself there, raising no contacts at all and therefore breaking nothing. This clears",
                    "the terrain a hull block is genuinely overlapping, which is the only way back out.")
            .define("clearOverlaps", true);

    public static final ModConfigSpec.IntValue OVERLAP_SWEEP_INTERVAL = BUILDER
            .comment("Ticks between overlap sweeps. Only contraptions that were moving in the last two seconds",
                    "and have since stopped dead are checked, which is what being wedged looks like.")
            .defineInRange("overlapSweepInterval", 10, 1, 100);

    public static final ModConfigSpec.BooleanValue DISPLACE_OVERLAPS = BUILDER
            .comment("Shove overlapping blocks into the nearest free space instead of destroying them. A hull",
                    "wedged in a hillside pushes the dirt out of its way and piles it up around itself, which is",
                    "both what a solid object does and cheaper than breaking - nothing shatters, nothing drops",
                    "and no debris is spawned. Blocks with nowhere to go are broken as before.")
            .define("displaceOverlaps", true);

    public static final ModConfigSpec.IntValue OVERLAP_SWEEP_MAX_BLOCKS = BUILDER
            .comment("Cap on overlapping blocks cleared per contraption per sweep.")
            .defineInRange("overlapSweepMaxBlocks", 512, 1, 8192);

    public static final ModConfigSpec.IntValue STUCK_GRACE_TICKS = BUILDER
            .comment("How long a wedged hull is given to free itself on centre-overlap alone before the sweep",
                    "starts widening. Past this it stops asking whether a block's centre is inside the hull and",
                    "starts asking whether the two merely share space, which is what a genuine wedge looks like:",
                    "the solver holds the pair a third of a block apart and calls it resolved, no contact is",
                    "ever raised, and the centre test that frees everything else finds nothing to free.")
            .defineInRange("stuckGraceTicks", 60, 1, 6000);

    public static final ModConfigSpec.IntValue GRIND_STUCK_TICKS = BUILDER
            .comment("How long a hull stays buried before it stops being the thing that gets its way.",
                    "Everything above this assumes the terrain is what has to move, which runs out of answers",
                    "the moment the terrain cannot be moved: a hull jolted into bedrock is dug at forever and",
                    "freed never, and forever is what the player experiences as the build being gone. So past a",
                    "long enough wait the hull grinds its own blocks away against whatever it is buried in - a",
                    "wreck is a worse outcome than flying away and a far better one than a statue.",
                    "Only where a block's centre is swallowed whole, which is a burial rather than a hull parked",
                    "against a wall. Set it very high to turn grinding off and let buried hulls stay buried.")
            .defineInRange("grindStuckTicks", 400, 1, 1728000);

    public static final ModConfigSpec.IntValue SWEEP_SCAN_BUDGET = BUILDER
            .comment("Ceiling on the work all sweeps together may do in one tick, counted in block reads",
                    "rather than in blocks: walking a block of the swept region costs one, and each step of the",
                    "rewind that decides whether the hull passes through it costs one more. Counting blocks",
                    "instead hid the rewind entirely, and a single pass could spend most of a second inside one",
                    "tick. Sweep cost grows with the cube of a contraption's size, so without a shared ceiling",
                    "one very large build eats the tick on its own - and a server running behind looks exactly",
                    "like weak gravity, because everything in the world falls in slow motion. Work skipped here",
                    "is picked up on the next sweep, not lost.")
            .defineInRange("sweepScanBudget", 24576, 1024, 1048576);

    public static final ModConfigSpec.IntValue SWEEP_FINEST_DETAIL = BUILDER
            .comment("The finest rung the sweep is allowed to sample at, from 0 to 3. Rung 0 asks about the",
                    "hull's path twice per block travelled; rung 1 asks about it once and a half and widens the",
                    "cube each sample covers to match, so the samples still overlap and nothing gets through.",
                    "What is lost is the shape of the hole: a hull passing a block corner-on is caught by a",
                    "wider probe that also catches the block beside it, so the tunnel comes out rounder and",
                    "wider than the thing that made it. Raising this to 1 is close to a third off the sweep.")
            .defineInRange("sweepFinestDetail", 0, 0, 3);

    public static final ModConfigSpec.DoubleValue COARSE_SWEEP_TRAVEL = BUILDER
            .comment("Travel per lookahead window, in blocks, past which a hull is swept coarsely whatever the",
                    "server is doing. Precision costs the most exactly where it is worth the least: a hull",
                    "creeping into a wall wants an answer good to half a block, and one arriving at sixty metres",
                    "a second is going to leave a hole either way while costing far more to sample.",
                    "Two blocks is about twenty metres a second - above anything under its own power, below",
                    "anything that has been falling for a while.")
            .defineInRange("coarseSweepTravel", 2.0, 0.25, 64.0);

    public static final ModConfigSpec.IntValue MAX_QUIET_TICKS = BUILDER
            .comment("The longest a hull with nothing near it may be left unswept. The window is already bounded",
                    "by how far the hull could travel before it could reach anything; this cap is only there for",
                    "what the reading cannot know about - ground generated underneath it, a player building up",
                    "to meet it. Stretching it is nearly free on a world full of parked airships, and what it",
                    "costs is that a tower thrown up under a hovering build goes unnoticed for that long.")
            .defineInRange("maxQuietTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue BACKING_MEMO_TICKS = BUILDER
            .comment("How many ticks a backing reading is kept for. What is behind a block is the slowest-changing",
                    "thing there is, except where a hull is currently eating it - which is precisely where this",
                    "is asked. A stale reading says a block still has a hillside behind it a moment after the",
                    "hillside went, so the block holds when it should have given; the next refresh corrects it.",
                    "What is bought is that a hull with thousands of contacts a tick pays for the look once every",
                    "few ticks instead of every one.")
            .defineInRange("backingMemoTicks", 1, 1, 40);

    public static final ModConfigSpec.IntValue MAX_CONTACTS_PER_TICK = BUILDER
            .comment("How many contacts are examined per tick before the rest are waved through, or 0 for no",
                    "limit. maxBlocksPerTick already caps how much is destroyed, and destruction was never the",
                    "whole cost: a hull flush against a hillside reports thousands of contacts a tick, and every",
                    "one is a pair of block states, a profile, a plot resolve and a look at what holds the",
                    "terrain up, spent to conclude that this square metre of stone is the same as the last one.",
                    "Contacts past the ceiling are not examined and so are not broken either. The hull is still",
                    "stopped by them, so what it costs is that a very heavy pile-up chews through terrain more",
                    "slowly than it should for as long as it lasts, rather than costing the tick.")
            .defineInRange("maxContactsPerTick", 0, 0, 1000000);

    public static final ModConfigSpec.BooleanValue BLOCK_UPDATES = BUILDER
            .comment("Whether a silent removal - one past maxBreakEffectsPerTick - still tells the blocks around",
                    "it that it has gone. Setting a block to air normally costs far more than the write: every",
                    "neighbour is notified, which in a loaded-up pack is redstone, light, block entities and",
                    "every mod with an opinion about the block next door. It is by a wide margin the most",
                    "expensive thing this mod does.",
                    "Off, the clients are still told, so nothing goes stale on screen; what is skipped is the",
                    "tidying up. A torch on the wall of a fresh tunnel stays floating, sand above the hole does",
                    "not fall until something else pokes it, and redstone beside the crater does not recalculate.",
                    "The blocks are gone either way.")
            .define("blockUpdates", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MATERIAL_OVERRIDES = BUILDER
            .comment("Per-block settings, for saying what vanilla's own numbers do not.",
                    "Everything else here derives a block's strength from mining hardness and blast resistance,",
                    "because those two are the only stats every block in every mod actually has. What they are",
                    "not is a considered opinion: an author picks them to place a block on a pickaxe tier, so",
                    "the immovable heart of a machine can easily come out softer than gravel.",
                    "An entry is a selector followed by settings, separated by spaces or commas:",
                    "  \"minecraft:obsidian      resistance=6\"",
                    "  \"#minecraft:leaves       soft=true\"",
                    "  \"create:*                scale=1.5\"",
                    "  \"mekanism:*              indestructible=false\"",
                    "Selectors are an exact block id, a block tag written with a leading #, every block in a",
                    "namespace written namespace:*, or every block at all written *.",
                    "Settings are:",
                    "  resistance=N     the block's strength outright, replacing the vanilla derivation. The",
                    "                   scale is the one the model works in after the hardness range has been",
                    "                   compressed: about 0.6 for dirt, 1.0 for wood, 1.4 for stone, 3 for",
                    "                   obsidian. Turn on logPerformance and hit something to see live numbers.",
                    "  scale=N          a multiplier on that strength, derived or given. 2 doubles it.",
                    "  indestructible=  true is never broken by anything this mod does; false takes that away",
                    "                   from a block that inherited it from indestructibleResistance.",
                    "  fragile=         true shatters the block at fragileTrigger rather than weighing it",
                    "                   against the hull, the way leaves and glass do.",
                    "  soft=            true makes hulls pass through the block and clear it the way they clear",
                    "                   undergrowth. It gives up its collider to do so, so a hull is not stopped",
                    "                   by something it is about to mow down.",
                    "A block may be caught by several entries at once, and the most specific one to name a given",
                    "setting decides it: an exact id beats a tag, a tag beats a namespace, a namespace beats *,",
                    "and among equals the entry written later wins. Settings are read one at a time, so a",
                    "namespace rule setting scale and an exact rule setting fragile both apply.",
                    "A rule naming a block or tag no installed mod provides never matches and is not an error,",
                    "which is what lets one file cover a pack whose mod list changes. Anything that does not",
                    "parse is dropped with a line in the log and the rest of the file is honoured.")
            .defineListAllowEmpty("materialOverrides", List.of(),
                    () -> "minecraft:obsidian resistance=6", entry -> entry instanceof String);

    public static final ModConfigSpec.BooleanValue LOG_PERFORMANCE = BUILDER
            .comment("Print this mod's own share of the server tick to the log every five seconds. Off by",
                    "default and worth turning on exactly once: when the game hitches, this is what says whether",
                    "the hitch is here or somewhere else in the pack.")
            .define("logPerformance", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Tuning cached;

    private ImpactConfig() {
    }

    /**
     * Drops the snapshot and every profile derived from it.
     *
     * <p>Called on config load, reload and unload, and on {@code TagsUpdatedEvent} - a material rule written
     * against a tag means nothing until the tag has been populated, and a datapack reload can repopulate it.
     */
    public static void invalidate() {
        cached = null;
        BlockProfile.clearCache();
    }

    /**
     * This tick's settings, read from the spec on first use after every {@link #invalidate()}.
     *
     * <p>Not safe to call before the config has loaded, which is why the two callers that can run that early
     * - the block mixin and the voxel classifier - check {@code SPEC.isLoaded()} first.
     */
    public static Tuning tuning() {
        Tuning current = cached;
        if (current == null) {
            current = Tuning.read();
            cached = current;
        }
        return current;
    }

    /**
     * Read straight off the spec rather than through {@link Tuning}, because it is asked from the collider
     * remesh, which happens off the server thread and outside any tick.
     */
    public static boolean cullInteriorVoxels() {
        return SPEC.isLoaded() && CULL_INTERIOR_VOXELS.get();
    }

    /**
     * A tick's worth of config, read once instead of once per contact. Every field below is touched on
     * the hot collision path, where each {@code ConfigValue.get()} is a map lookup behind a cache check.
     *
     * <p>{@code impactStrength} is folded in here rather than at the point of use, so it applies once and
     * cannot be forgotten by a caller. A field read off this record is therefore not necessarily the number
     * written in the file.
     */
    public record Tuning(double minImpactSpeed,
                         double hardnessScale,
                         double explosionResistanceFactor,
                         double hardnessWeight,
                         double resistanceExponent,
                         double indestructibleResistance,
                         double massSensitivity,
                         double referencePressure,
                         double massFactorMin,
                         double crushSpeed,
                         double massFactorMax,
                         boolean crushBlocks,
                         double crushPressureScale,
                         int crushInterval,
                         int crushSpan,
                         int crushScanBudget,
                         double crushSeat,
                         double crushShear,
                         int crushDepth,
                         double crushSpread,
                         boolean crushDisplace,
                         int crushDisplaceReach,
                         int maxCrushPerTick,
                         boolean breakContraptionBlocks,
                         double contraptionBlockToughness,
                         double backingWeight,
                         double impactWear,
                         boolean crackBlocks,
                         double crackResilience,
                         int crackHealTicks,
                         double crackSpall,
                         int maxCrackedBlocks,
                         int maxCrackEffectsPerTick,
                         int maxBlocksPerTick,
                         double scatterChance,
                         double scatterVelocityScale,
                         int maxScatterPerTick,
                         int maxBreakEffectsPerTick,
                         boolean dropItems,
                         boolean punchThrough,
                         double punchThroughRatio,
                         double breakDragMass,
                         double breakDragMax,
                         double fragileTrigger,
                         int movingCrushInterval,
                         double crushDownShare,
                         double crushSideShare,
                         double crushLeadTicks,
                         int backingReach,
                         double backingBeside,
                         double crackSpallCeiling,
                         double carveLookaheadTicks,
                         int stuckGraceTicks,
                         int grindStuckTicks,
                         int maxQuietTicks,
                         int backingMemoTicks,
                         int maxContactsPerTick,
                         boolean blockUpdates,
                         double hullBackingWeight,
                         int hullBackingReach,
                         double contraptionScatterChance,
                         double scatterUpwardKick,
                         int landingSearch,
                         boolean landingNeedsFloor,
                         int lifetimeTicks,
                         boolean dropWhenLost,
                         double debrisDamagePerBlock,
                         int debrisDamageMax,
                         boolean shockBlocks,
                         double shockMinOvershoot,
                         double hullShockScale,
                         double terrainShockScale,
                         double shockKineticScale,
                         double shockContactShare,
                         double shockCost,
                         double shockFalloff,
                         int shockMaxPerImpact,
                         int shockMaxPerTick) {

        /**
         * Reads the whole spec once, applying {@code impactStrength} to the thresholds it eases on the way.
         */
        static Tuning read() {
            // Two of the settings do not belong to any one tick. The sweep ladder is pure arithmetic with
            // nothing from the game in it, so it is told rather than asked; the material table is parsed text,
            // so it is built once here rather than per block.
            SweepDetail.configure(SWEEP_FINEST_DETAIL.get(), COARSE_SWEEP_TRAVEL.get());
            MaterialOverrides.reload(MATERIAL_OVERRIDES.get());

            final double strength = IMPACT_STRENGTH.get();
            return new Tuning(
                    MIN_IMPACT_SPEED.get(),
                    ImpactResolver.eased(HARDNESS_SCALE.get(), strength),
                    EXPLOSION_RESISTANCE_FACTOR.get(),
                    HARDNESS_WEIGHT.get(),
                    RESISTANCE_EXPONENT.get(),
                    INDESTRUCTIBLE_RESISTANCE.get(),
                    MASS_SENSITIVITY.get(),
                    REFERENCE_PRESSURE.get(),
                    MASS_FACTOR_MIN.get(),
                    ImpactResolver.eased(CRUSH_SPEED.get(), strength),
                    MASS_FACTOR_MAX.get() * Math.max(1.0, strength),
                    CRUSH_BLOCKS.get(),
                    ImpactResolver.eased(CRUSH_PRESSURE_SCALE.get(), strength),
                    CRUSH_INTERVAL.get(),
                    CRUSH_SPAN.get(),
                    CRUSH_SCAN_BUDGET.get(),
                    CRUSH_SEAT.get(),
                    CRUSH_SHEAR.get(),
                    CRUSH_DEPTH.get(),
                    CRUSH_SPREAD.get(),
                    CRUSH_DISPLACE.get(),
                    CRUSH_DISPLACE_REACH.get(),
                    MAX_CRUSH_PER_TICK.get(),
                    BREAK_CONTRAPTION_BLOCKS.get(),
                    CONTRAPTION_BLOCK_TOUGHNESS.get(),
                    BACKING_WEIGHT.get(),
                    IMPACT_WEAR.get(),
                    CRACK_BLOCKS.get(),
                    ImpactResolver.eased(CRACK_RESILIENCE.get(), strength),
                    CRACK_HEAL_TICKS.get(),
                    CRACK_SPALL.get(),
                    MAX_CRACKED_BLOCKS.get(),
                    MAX_CRACK_EFFECTS_PER_TICK.get(),
                    MAX_BLOCKS_PER_TICK.get(),
                    SCATTER_CHANCE.get(),
                    SCATTER_VELOCITY_SCALE.get(),
                    MAX_SCATTER_PER_TICK.get(),
                    MAX_BREAK_EFFECTS_PER_TICK.get(),
                    DROP_ITEMS.get(),
                    PUNCH_THROUGH.get(),
                    PUNCH_THROUGH_RATIO.get(),
                    BREAK_DRAG_MASS.get(),
                    BREAK_DRAG_MAX.get(),
                    FRAGILE_TRIGGER.get(),
                    MOVING_CRUSH_INTERVAL.get(),
                    CRUSH_DOWN_SHARE.get(),
                    CRUSH_SIDE_SHARE.get(),
                    CRUSH_LEAD_TICKS.get(),
                    BACKING_REACH.get(),
                    BACKING_BESIDE.get(),
                    CRACK_SPALL_CEILING.get(),
                    CARVE_LOOKAHEAD_TICKS.get(),
                    STUCK_GRACE_TICKS.get(),
                    GRIND_STUCK_TICKS.get(),
                    MAX_QUIET_TICKS.get(),
                    BACKING_MEMO_TICKS.get(),
                    MAX_CONTACTS_PER_TICK.get(),
                    BLOCK_UPDATES.get(),
                    HULL_BACKING_WEIGHT.get(),
                    HULL_BACKING_REACH.get(),
                    CONTRAPTION_SCATTER_CHANCE.get(),
                    SCATTER_UPWARD_KICK.get(),
                    LANDING_SEARCH.get(),
                    LANDING_NEEDS_FLOOR.get(),
                    LIFETIME_TICKS.get(),
                    DROP_WHEN_LOST.get(),
                    DEBRIS_DAMAGE_PER_BLOCK.get(),
                    DEBRIS_DAMAGE_MAX.get(),
                    SHOCK_BLOCKS.get(),
                    SHOCK_MIN_OVERSHOOT.get(),
                    HULL_SHOCK_SCALE.get() * Math.max(1.0, strength),
                    TERRAIN_SHOCK_SCALE.get() * Math.max(1.0, strength),
                    SHOCK_KINETIC_SCALE.get() * Math.max(1.0, strength),
                    SHOCK_CONTACT_SHARE.get(),
                    SHOCK_COST.get(),
                    SHOCK_FALLOFF.get(),
                    SHOCK_MAX_PER_IMPACT.get(),
                    SHOCK_MAX_PER_TICK.get());
        }

        /** The lowest speed at which any block, however soft and however heavy the ram, could give way. */
        public double breakSpeedFloor() {
            return Math.min(this.minImpactSpeed, this.crushSpeed);
        }
    }
}
