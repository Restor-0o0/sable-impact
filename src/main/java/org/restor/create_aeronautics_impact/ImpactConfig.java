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

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("The master switch. Off, this mod does nothing at all: no contact is examined, no block",
                    "is destroyed, no sweep runs, nothing is crushed or carved, and Sable's colliders are",
                    "built exactly as they would be without the jar installed.",
                    "This is what makes it possible to keep the mod installed and have a world behave as",
                    "though it were not. The config is per world - it lives in <world>/serverconfig - so one",
                    "world can be off while another is on, and neither needs the jar removed.",
                    "It is read live, so it takes effect on the tick after the file is saved.")
            .define("enabled", true);

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
            .defineInRange("contraptionBlockToughness", 3.0, 0.01, 100.0);

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
            .defineInRange("scatterChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CONTRAPTION_SCATTER_CHANCE = BUILDER
            .comment("The same for a contraption's own blocks. Higher than terrain by default: a ship losing its",
                    "hull is the thing being watched, there are far fewer of these blocks than there is ground",
                    "being ploughed, and a piece of hull that vanishes reads as the mod failing to do anything",
                    "rather than as a break.",
                    "Kept well under 1 all the same. Every block that flies is a block travelling away from the",
                    "wreck, and a wreck that throws all of itself is a fountain rather than a crash - what is",
                    "wanted is a few pieces turning through the air over a heap that is mostly still there.")
            .defineInRange("contraptionScatterChance", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue SETTLE = BUILDER
            .comment("Whether blocks that did not fly are put back down near where they broke instead of being",
                    "deleted. Off is how this mod behaved before 1.3: a crash leaves a clean hole and nothing",
                    "on the ground to show for it.")
            .define("settle", true);

    public static final ModConfigSpec.DoubleValue SETTLE_SHARE = BUILDER
            .comment("Fraction of the blocks that did not fly which are put back down near where they broke",
                    "instead of vanishing. This is what decides whether a crash leaves wreckage or leaves a",
                    "hole. A block that settles is pushed clear of what broke it, falls to the first thing",
                    "solid under it within settleDrop, and is written there - no entity, no ticking, no",
                    "physics, which is why this can be turned up to where throwing debris never could.",
                    "At the default most of a ruined build is still lying where it came down, and terrain a",
                    "hull ploughed through is piled beside the furrow rather than deleted. 0 restores the old",
                    "behaviour of a clean hole and nothing to show for it.")
            .defineInRange("settleShare", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SETTLE_DROP = BUILDER
            .comment("How far a settling block may fall looking for something to rest on, in blocks. It stops at",
                    "the first spot that has something solid beneath it, so this is the depth of the hole it is",
                    "willing to fill rather than a distance it is thrown. Low leaves wreckage perched where it",
                    "broke; high has a hull broken over a ravine posting its blocks to the bottom of it.")
            .defineInRange("settleDrop", 6, 0, 32);

    public static final ModConfigSpec.IntValue SETTLE_SPREAD = BUILDER
            .comment("How wide the heap of a contraption's own settled blocks is, in blocks. A hull's blocks live",
                    "out in the plotgrid and only the crash itself has a place in the world, so unlike terrain",
                    "they have no position of their own to fall from and are spread over a disc around the",
                    "impact instead. Too small and a ship comes down as a tower of its own blocks; too large",
                    "and the wreck is a thin film over the landscape rather than a pile.")
            .defineInRange("settleSpread", 4, 0, 32);

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
            .defineInRange("scatterVelocityScale", 0.12, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SCATTER_UPWARD_KICK = BUILDER
            .comment("A flat upward push given to every piece of debris on top of the direction the impact threw",
                    "it. Without some of this a block broken by a downward hit is driven straight back into the",
                    "ground and settles where it stood, which looks like nothing happened to it.")
            .defineInRange("scatterUpwardKick", 0.08, 0.0, 2.0);

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

    public static final ModConfigSpec.EnumValue<DebrisMode> DEBRIS_MODE = BUILDER
            .comment("What a broken block turns into on its way out.",
                    "FALL   - it falls from where it stood, like sand. Nothing is thrown, so a wreck comes",
                    "         down as a wreck instead of erupting, and a piece is on the ground in about the",
                    "         time it takes to fall the height it broke from rather than flying for a second",
                    "         first. This is what a building does.",
                    "THROW  - it is thrown clear of the impact, which is the pre-1.6 behaviour. Right for a",
                    "         cannon shot and for a hull ploughing through a hillside; wrong for a hull",
                    "         folding, where it reads as an explosion under the floor.",
                    "SETTLE - no falling block at all: the block is written straight back down onto the heap.",
                    "         That is one block change instead of an entity that has to fall, land, write a",
                    "         block anyway and be tracked by every client in range, so it is much the cheapest",
                    "         of the three and much the dullest.")
            .defineEnum("mode", DebrisMode.FALL);

    public static final ModConfigSpec.IntValue MAX_SETTLE_PER_TICK = BUILDER
            .comment("Ceiling on blocks all settling together may write back per level per tick.",
                    "settleShare sends the great majority of a crash down this path and until now nothing",
                    "bounded it: a wreck shedding four thousand blocks in a tick wrote four thousand blocks",
                    "back, each with a full neighbour update behind it if blockUpdates is on, and the tick",
                    "that did it took over a second. What is refused past this is gone rather than heaped,",
                    "which costs a thinner pile and buys back the frame.")
            .defineInRange("maxSettlePerTick", 256, 0, 32768);

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
            .defineInRange("hullScale", 3.0, 0.0, 1000.0);

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

    public static final ModConfigSpec.DoubleValue SHOCK_MIN_SPEED = BUILDER
            .comment("The speed (m/s) below which an impact is not a crash and sends no shock at all, whatever",
                    "it hit and however heavy it is. This is the guard that lets a build be landed and moved:",
                    "a ship is thousands of kilograms, so at walking pace it is already carrying more energy",
                    "than a stick of dynamite, and every other number here would happily spend it.",
                    "It does two things at once. Nothing under it propagates, so setting a dirigible down or",
                    "nudging it into place cannot fold it up. And above it the crash is priced on the speed",
                    "over this rather than on the speed, so a landing a little too fast loses a few blocks",
                    "rather than crossing a line into losing hundreds.",
                    "Deliberately not touched by impactStrength: that dial is for how hard crashes are, and",
                    "this is the line between a crash and ordinary use.")
            .defineInRange("minSpeed", 8.0, 0.0, 1000.0);

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

    public static final ModConfigSpec.IntValue SHOCK_MAX_WAVES = BUILDER
            .comment("How many waves one contraption may set going in one tick, however many contacts it",
                    "reports. The contact side of a shock is priced per contact and is not drawn from the",
                    "kinetic reservoir, so without this a hull landing flat buys a full wave for each of the",
                    "several hundred contacts it reports, and what the player sees is the impact point eating",
                    "outwards in rings for tick after tick until the whole build is gone. That is also what",
                    "leaves nothing for the cracks to split, since a build already eaten cannot come apart.",
                    "A handful is enough to cover the face that landed. Raise it for a crash that pulverises",
                    "more of the build, lower it towards 1 for a single clean crater and the rest in pieces.",
                    "Terrain is not capped: a hull ploughing a hillside is meant to plough it.")
            .defineInRange("maxHullWaves", 1, 0, 4096);

    public static final ModConfigSpec.BooleanValue FRACTURE = BUILDER
            .comment("Whether a crash also splits a build along cracks, rather than only eating it outwards from",
                    "the impact. Off is the pre-1.4 behaviour, waves only, and the energy cracking would have",
                    "taken goes back to them.")
            .define("fracture", true);

    public static final ModConfigSpec.DoubleValue FRACTURE_SHARE = BUILDER
            .comment("The share of a crash's energy that goes into splitting a build rather than pulverising it.",
                    "A shock spent on its own spreads out of the impact in every direction, so what it leaves",
                    "is a build with a bite taken out of it - which is what a crash does to the part that hit",
                    "and is nothing like what happens to the rest. Real things come apart along a line: the",
                    "back half of the ship separates and goes its own way, still a ship.",
                    "So some of the energy is spent instead on cracks - one block wide, running clean through",
                    "the build from the impact, cutting it into pieces rather than eating it. The rest still",
                    "goes to the ordinary wave, and the two together are the crash: a wrecked, cratered end",
                    "where it hit, and the rest of the hull in a couple of large pieces.",
                    "Whether a piece that has been cut free then flies off on its own is Sable's decision, not",
                    "this mod's - all this does is make sure nothing is still holding it on.",
                    "0 is the old behaviour, waves only. 1 stops cratering and only ever cleaves.")
            .defineInRange("fractureShare", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.IntValue FRACTURE_COUNT = BUILDER
            .comment("How many cracks one crash may open, at most. They are opened once per build per tick by",
                    "the first contact hard enough to earn them, not once per contact - a landing reports",
                    "hundreds of contacts and they are all the same crash, and a hull cut in three hundred",
                    "places is not in pieces, it is gravel. Two cuts is a build in three parts.",
                    "0 turns cracking off as surely as fractureShare 0 does, and gives its energy back to the",
                    "ordinary wave.")
            .defineInRange("fractureCount", 2, 0, 8);

    public static final ModConfigSpec.DoubleValue FRACTURE_FALLOFF = BUILDER
            .comment("What a crack keeps of its purchasing power per block travelled, the way falloff does for a",
                    "wave. Much closer to 1, because the two want opposite things: a wave has to be stopped",
                    "from reaching across the map, and a crack is no use at all unless it reaches the far side",
                    "of the build. Lower it and cracks turn back into short gashes near the impact.")
            .defineInRange("fractureFalloff", 0.995, 0.1, 1.0);

    public static final ModConfigSpec.IntValue FRACTURE_WANDER = BUILDER
            .comment("How far a crack may drift off the flat plane it started on, in blocks. At 0 a build is cut",
                    "as though by a saw, which is legible and looks like nothing that has ever broken. Each",
                    "step the crack may wander a block along its own normal, up to this, so what it leaves is a",
                    "ragged seam.",
                    "The block on the plane is taken as well as the one the seam wandered onto, so the drift",
                    "widens the cut rather than moving it. It has to be: Sable decides what is still one build",
                    "by neighbours including the diagonals, so a seam that merely moves is one two columns can",
                    "still be traced across through the corner - which is a build cut end to end and still in",
                    "one piece with a groove in it. Wandering therefore costs the blocks it wanders over, and",
                    "0 is a clean saw cut and the cheapest.")
            .defineInRange("fractureWander", 2, 0, 16);

    public static final ModConfigSpec.IntValue FRACTURE_GAP = BUILDER
            .comment("How many blocks of nothing a crack may cross before it gives up, in blocks.",
                    "This is the setting that decides whether cracks work at all on a real build. A wave is",
                    "carried by what is solid and has no business crossing a room; a crack is a surface, and",
                    "a ship is a shell around air. At 0 a crack stops at the first cavity, which means it cuts",
                    "solid plates in two and does nothing whatever to a hull - it dies an inch inside the skin",
                    "it entered through. Bridging lets the same seam come out of the deck, cross the hold and",
                    "carry on through the floor, which is the cut that actually separates anything.",
                    "It is bounded so a crack cannot wander off into open plotgrid forever, and crossing a gap",
                    "is free: nothing is broken there because there was nothing there.")
            .defineInRange("fractureGap", 6, 0, 64);

    public static final ModConfigSpec.DoubleValue FRACTURE_COST = BUILDER
            .comment("What a crack pays for a block, as a fraction of what a wave pays for the same block.",
                    "Cheap on purpose, and this is the other half of making cracks work. A wave is a volume",
                    "and a crack is a surface: the wave's price buys a sphere, and at that price the same",
                    "energy buys a disc a few blocks across, which is a scratch rather than a cut. A crack",
                    "that stops halfway is worth nothing at all - it has to reach the far side of the build",
                    "or it has not split anything - so it is priced to cross what it starts on.",
                    "1.0 makes cracking cost exactly what pulverising does, which in practice turns it off.")
            .defineInRange("fractureCost", 0.05, 0.001, 1.0);

    public static final ModConfigSpec.BooleanValue FRACTURE_AIM = BUILDER
            .comment("Whether a crack is cut across the way the build actually runs, rather than across whichever",
                    "axis came up next.",
                    "A crack is a plane and a plane is named by the axis it is cut across, and until 1.9.1 that",
                    "axis was dealt out in turn - X, then Y, then Z - so that two cracks would cross rather than",
                    "repeat. On a solid lump that is fine. On anything anybody builds it is wrong two times in",
                    "three, because the axis a thing is thin along is the one axis it cannot be parted across.",
                    "A mast cut across its length falls in two; a mast cut along its length is two half-masts",
                    "still joined at both ends, and the ship that flew into it is stopped by a mast that is",
                    "still there. A one-block plate cut across its width parts; cut across its thickness the",
                    "plane is the plate, and what the crack does is chew a hole out of the middle of it and stop",
                    "when the energy runs out.",
                    "On, the material is followed out from the break in all three directions and the cut is made",
                    "across whichever it runs furthest along, which is the mast's length, the plate's width and,",
                    "on a hull, the cut amidships that leaves the stern behind. Off restores the pre-1.9.1",
                    "behaviour exactly.")
            .define("fractureAim", true);

    public static final ModConfigSpec.IntValue FRACTURE_SCAN = BUILDER
            .comment("How far the crack looks along each axis to decide which way the build runs, in blocks.",
                    "Three lines of this many reads per cut, and a cut is a couple per build per tick, so it is",
                    "cheap at any sane value. It only has to be long enough to tell a mast from a deck: past the",
                    "point where one axis is plainly the longest, more reach changes nothing. Gaps are crossed",
                    "on the way, on the same allowance a crack itself gets, so a hull measures as the length of",
                    "the hull rather than as the thickness of the one plate the break happened to be in.")
            .defineInRange("fractureScan", 48, 1, 512);

    public static final ModConfigSpec.IntValue FRACTURE_MIN_RUN = BUILDER
            .comment("How far the build has to run along an axis before a cut across that axis counts as a cut,",
                    "in blocks. Below it the plane lies in the face of the thing rather than through it, which",
                    "is a bite rather than a break, and no number of them ever separates anything.",
                    "Cracks after the first take the next longest axis down, so a plate is cut across its length",
                    "and then across its width and comes apart in four - but never across its thickness, however",
                    "many cracks it is given. Raise it and cracks are only ever made across the long dimensions",
                    "of a build; at 1 nothing is excluded and the second cut on a plate is the old bite again.")
            .defineInRange("fractureMinRun", 3, 1, 64);

    public static final ModConfigSpec.IntValue FRACTURE_FLOOR = BUILDER
            .comment("How many blocks a crack may take before the price of them is looked at at all.",
                    "fractureCost already makes cuts cheap, but cheap is not the same as certain, and a cut that",
                    "stops halfway has split nothing: what it leaves is a notch, and the build it is in is still",
                    "one build with a groove in it. This is the guarantee that a cut which was worth starting is",
                    "worth finishing - past it the energy has to be there as before.",
                    "It is not a licence to destroy: everything else still applies, so the build's own damage",
                    "allowance under [protect], the per-tick ceiling and the per-impact ceiling all stop a crack",
                    "exactly as they did. Set it to 0 for the pre-1.9.1 behaviour, where a crack is only ever as",
                    "long as the crash could pay for.")
            .defineInRange("fractureFloor", 128, 0, 8192);

    public static final ModConfigSpec.DoubleValue HULL_SHARE = BUILDER
            .comment("The share of a shock a Create: Aeronautics structure passes on through itself, against",
                    "what terrain passes on. Terrain is a hillside and comes apart the way a hillside does.",
                    "A build is a made thing: it has frames and skins and joints, and what those are for is",
                    "exactly to carry a blow somewhere other than through the block next to it. A wave that",
                    "spends its whole purse inside a hull is what turns a scrape into a build shedding its",
                    "decks, and no aircraft that has ever landed badly has done that.",
                    "So the wave a build passes through itself is the fraction of the one terrain would get.",
                    "The cracks are not touched by this - a crack is the break we want, and it is priced",
                    "separately under fractureCost. What this quiets is the crumbling around it.",
                    "1.0 is the pre-1.9.2 behaviour, where a hull was treated as a hillside.")
            .defineInRange("hullShare", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HULL_MIN_SPEED = BUILDER
            .comment("How hard a Create: Aeronautics structure has to be hit before a shock runs through it at",
                    "all, in blocks per second. Below it the contact still cracks - the plane through the",
                    "impact is cut, glass still goes - but nothing spreads outwards from the break.",
                    "This is what a build is allowed to shrug off. A belly touching a treetop, a mast catching",
                    "a mast, a landing that was merely rough: those leave a hole where they touched and a",
                    "seam running out of it, which is what they should leave. Set it to 0 and every contact",
                    "past the shock's own minSpeed spreads, which is 1.9.1 and is why builds sloughed.")
            .defineInRange("hullMinSpeed", 20.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue FRACTURE_NECK = BUILDER
            .comment("How far either side of the impact a crack may move to find a weaker place to break, in",
                    "blocks. 0 cuts through the contact itself, which is 1.9.1.",
                    "Things do not break where they are hit, they break where they are weakest, and on a build",
                    "the two are rarely the same block. An obsidian mast with a wooden gondola on the end of",
                    "it does not crack down the mast when the gondola clips a hill - the gondola shears off at",
                    "its joint, because that ring of wood is the least material holding the most weight. This",
                    "is how far the crack is allowed to travel along its own axis looking for that ring.")
            .defineInRange("fractureNeck", 12, 0, 128);

    public static final ModConfigSpec.IntValue FRACTURE_NECK_SPAN = BUILDER
            .comment("How wide a window each candidate break is weighed across, in blocks either way. A plane's",
                    "strength is what is standing in it, so this is how much of the plane is sampled to find",
                    "that out. Wider is a truer answer and costs its square in block reads.")
            .defineInRange("fractureNeckSpan", 6, 1, 32);

    public static final ModConfigSpec.DoubleValue FRACTURE_NECK_BIAS = BUILDER
            .comment("What a candidate break pays for every block it sits away from the impact, as a share of",
                    "what cutting at the impact itself would have cost. This keeps the search honest: a crack is",
                    "still something that happened where the crash happened, and without a price on distance",
                    "the weakest plane in the whole reach wins even when the blow landed nowhere near it.",
                    "Raise it and cracks stay at the contact; lower it and they hunt further for the joint.")
            .defineInRange("fractureNeckBias", 0.08, 0.0, 10.0);

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

    public static final ModConfigSpec.IntValue SHOCK_MAX_TICKS = BUILDER
            .comment("How long a wave too big for one tick may keep going, in ticks, before what is left of it",
                    "is dropped.",
                    "Waves are put down and picked up across ticks so the tick budget is never blown, which is",
                    "correct and which, on a crash big enough, is also what makes a wreck keep shedding blocks",
                    "in rings for a minute after it has stopped moving. A collapse is over in a second; past",
                    "that the build is not coming apart any more, it is decaying, and nothing about it reads",
                    "as the crash that caused it.",
                    "Two seconds is about as long as anything should still be visibly falling. Raise it to let",
                    "very large wrecks finish what they started, at the price of that tail.")
            .defineInRange("maxTicks", 40, 1, 1200);

    public static final ModConfigSpec.BooleanValue SHOCK_ONE_CRASH = BUILDER
            .comment("Whether a build's kinetic shock is drawn once per crash rather than once per tick.",
                    "A crash is not an instant. A hull hitting the ground at speed is still moving on the",
                    "next tick and the one after, and each of those ticks it is a heavy fast body touching",
                    "the ground - so each of them refills the kinetic reservoir and buys a fresh set of",
                    "waves out of energy the build no longer has. What that produces is a wreck that goes on",
                    "detonating for as long as it is sliding, which is where 'it keeps eating itself after",
                    "it has stopped' comes from far more than any single wave does.",
                    "On, the reservoir is filled once and spent down across the whole crash, and it refills",
                    "only after the build has been left alone for [protect] restTicks - the same rest that",
                    "hands back the damage budget, so a crash is one event to both. A build that stops and",
                    "is flown into a cliff a minute later gets a full one again.",
                    "Only the build's own side works this way. Terrain is refilled per tick on purpose: a",
                    "hull ploughing a hillside is meant to keep ploughing it for as long as it is moving.")
            .define("oneCrash", true);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("How a build comes down once it has landed, which is a different question from how a",
                        "crash destroys it. Everything under [shock] spends the energy of the impact, and that",
                        "gets the crater right and the rest of the build wrong: a wave spreads out of the point",
                        "that touched in every direction at once, so what the player watches is a hull being",
                        "eaten in rings from one corner. A real structure is held up by its own floor, and when",
                        "the floor at one end goes the rest folds into the hole - from that end towards the far",
                        "one, under its own weight, and over within a second or two.",
                        "So a collapse is not an energy model. A hard landing arms a failure front at the",
                        "contact; the front walks out through the build at a fixed speed and takes the floor",
                        "out of every column it passes, deepest at the contact and tapering to a single course",
                        "at the rim. Nothing is pushed - the build is simply no longer standing on anything.",
                        "Where it lands it hits again, which arms the next front, and it comes down one storey",
                        "at a time the way buildings do.")
                .push("collapse");
    }

    public static final ModConfigSpec.BooleanValue COLLAPSE = BUILDER
            .comment("Whether a landed build folds under its own weight at all. Off is the pre-1.5 behaviour,",
                    "where the only thing that ever destroys a build is the energy of the crash and a wreck",
                    "comes apart in rings from the point of impact.")
            .define("collapse", true);

    public static final ModConfigSpec.IntValue COLLAPSE_SPEED = BUILDER
            .comment("How far the failure front travels through the build per tick, in blocks.",
                    "This is the pace of the whole thing and it is a speed rather than a budget on purpose:",
                    "what a collapse costs has nothing to do with how fast the front is moving, so this can be",
                    "set by how it should look. At the default a build sixty blocks across is done folding in",
                    "about half a second. Lower it for a slow, groaning failure that spreads visibly outwards;",
                    "raise it and the whole footprint gives way at once.")
            .defineInRange("speed", 8, 1, 64);

    public static final ModConfigSpec.IntValue COLLAPSE_REACH = BUILDER
            .comment("How far from the contact the failure spreads, in blocks, and so how much of a build one",
                    "landing can bring down. Past this the build is untouched and holds itself up.",
                    "It is also what the taper is measured against, so raising it does not only reach further,",
                    "it makes the fold shallower over that whole distance.")
            .defineInRange("reach", 16, 1, 512);

    public static final ModConfigSpec.IntValue COLLAPSE_BITE = BUILDER
            .comment("How many courses of floor a column loses directly under the contact, tapering to one at",
                    "reach. This is the fold: the end that landed drops by this much and the far end barely",
                    "moves, so the build comes down into its own wreckage rather than settling flat.",
                    "On a hollow build a course is not a layer of the scan but a layer of material - the rooms",
                    "in between are stepped over - so 3 means a ship loses its keel and the two decks above it",
                    "at the point of impact, and its keel alone at the bow.",
                    "1 removes the taper and with it the fold: the whole footprint drops by one course, evenly.")
            .defineInRange("bite", 3, 1, 32);

    public static final ModConfigSpec.IntValue COLLAPSE_DEPTH = BUILDER
            .comment("How tall a column is searched for that material, in blocks, measured up from the contact.",
                    "It has to clear the tallest room in the build or a column whose floor is the far side of",
                    "a hold will find nothing and that part will not fail. It costs a block lookup per step of",
                    "every column, so it is also the largest single cost here.")
            .defineInRange("depth", 24, 1, 256);

    public static final ModConfigSpec.IntValue COLLAPSE_DROP = BUILDER
            .comment("How far below the contact the search starts, in blocks. A hull touches down on whatever",
                    "hangs lowest, which is rarely the floor of the columns around it: without this a keel",
                    "that dips below the point that touched is never found and the build fails one course too",
                    "high, taking a deck out from under itself while standing on an intact bottom.")
            .defineInRange("drop", 4, 0, 64);

    public static final ModConfigSpec.IntValue COLLAPSE_COOLDOWN = BUILDER
            .comment("Ticks after one front finishes before the same build may be given another.",
                    "A build is touching what it fell on for the whole time it is falling into it, so without",
                    "a pause every tick of the descent would arm a fresh collapse and the build would be gone",
                    "before it had visibly moved. The pause is what makes the storeys separate events.")
            .defineInRange("cooldown", 10, 0, 200);

    public static final ModConfigSpec.IntValue COLLAPSE_MAX_PER_TICK = BUILDER
            .comment("Ceiling on blocks all collapses together may drop per level per tick. Unlike a wave, what",
                    "this stops is not resumed later - the front carries on from where it is next tick and the",
                    "columns it skipped stay standing, which on a collapse is a hole in the wreckage rather",
                    "than a wave that never arrives.")
            .defineInRange("maxBlocksPerTick", 2048, 0, 262144);

    public static final ModConfigSpec.DoubleValue COLLAPSE_MIN_SPEED = BUILDER
            .comment("How fast a build has to be going for the landing to bring it down at all, in blocks per",
                    "second.",
                    "Its own gate rather than the wave's, because the two want very different answers: a wave",
                    "at walking pace chips whatever it touched, and a collapse at walking pace takes the floor",
                    "out from under a build that was being parked. This is the difference between a hard",
                    "landing and a crash, and it is the first setting to raise if a build seems to come apart",
                    "under its own landing gear.")
            .defineInRange("minSpeed", 28.0, 0.0, 1000.0);

    public static final ModConfigSpec.BooleanValue COLLAPSE_FIT = BUILDER
            .comment("Whether a collapse is sized to the landing that armed it rather than to reach alone.",
                    "Without this every collapse is the same collapse: the full square of reach in every",
                    "direction, however little of the build actually came down on anything and however",
                    "narrowly the speed cleared minSpeed. That is a build grazing a fence post and losing a",
                    "floor thirty blocks across, which is the single worst thing this mod has ever done.",
                    "With it on the front is cut to whichever is smaller - what the build was measured to be",
                    "touching, plus margin, or what the speed above minSpeed has earned by fullSpeed - and",
                    "never smaller than minReach. A build that lands flat on a plateau still collapses across",
                    "everything it landed on, because that is what it was touching.",
                    "false is the pre-1.9.2 behaviour.")
            .define("fit", true);

    public static final ModConfigSpec.IntValue COLLAPSE_MARGIN = BUILDER
            .comment("How far past the edge of what was touched a fitted collapse is allowed to run, in blocks.",
                    "A floor does not stop being damaged exactly where the contact stopped, and the contacts",
                    "themselves are only the ones that were reported this tick. This is the difference",
                    "between the footprint and the crater.")
            .defineInRange("margin", 2, 0, 64);

    public static final ModConfigSpec.IntValue COLLAPSE_MIN_REACH = BUILDER
            .comment("The smallest a fitted collapse may be cut to, in blocks either way. A collapse that",
                    "reaches nothing is a collapse that did not happen, and a single reported contact is",
                    "still a build putting its weight through one place.")
            .defineInRange("minReach", 2, 0, 512);

    public static final ModConfigSpec.DoubleValue COLLAPSE_FULL_SPEED = BUILDER
            .comment("The speed at which a fitted collapse is allowed its whole reach, in blocks per second.",
                    "Between minSpeed and this the front grows from minReach to reach, so a landing that",
                    "barely cleared the gate takes barely anything and a fall that arrived at terminal",
                    "velocity takes the lot. This is the whole difference between a hard landing and a crash,",
                    "expressed as a slope rather than as a switch.")
            .defineInRange("fullSpeed", 30.0, 0.1, 1000.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Telling a build that it is now two builds.",
                        "Sable decides what is still one structure by walking its blocks, and it walks them a",
                        "few hundred at a time so that the walk never costs a tick. That is the right trade",
                        "for a build losing a block to a pickaxe and the wrong one for a build losing a",
                        "thousand blocks to a crash: the connection is severed on the first tick and noticed",
                        "on the fortieth, and what is on the screen in between is a wreck cut clean through",
                        "the middle, hanging together, held up by a search that has not caught up yet.",
                        "So a build this mod has damaged is walked harder, for a few ticks, until the search",
                        "settles. It costs what it is given and no more, and the moment the answer is in it",
                        "costs nothing at all.")
                .push("split");
    }

    public static final ModConfigSpec.BooleanValue RESOLVE_SPLITS = BUILDER
            .comment("Whether a build this mod has broken is walked harder until it knows what it is.",
                    "Turn it off and separation still happens, on Sable's own schedule, which on a wreck of",
                    "any size is tens of seconds after the fact.")
            .define("resolve", true);

    public static final ModConfigSpec.IntValue SPLIT_ROUNDS = BUILDER
            .comment("How many extra passes of the connectivity search a damaged build is given per tick. One",
                    "pass is what Sable itself runs in a tick, so 24 is a build resolving about twenty-four",
                    "times sooner. The search is self-limiting: once it has its answer the passes cost",
                    "nothing, so this is a ceiling and not a workload.")
            .defineInRange("rounds", 24, 0, 4096);

    public static final ModConfigSpec.IntValue SPLIT_TICKS = BUILDER
            .comment("How long a build stays on the hurried list after the last block it lost, in ticks. A",
                    "wreck keeps coming apart for a while after the landing, and each new severance wants",
                    "finding as promptly as the first.")
            .defineInRange("ticks", 200, 0, 24000);

    public static final ModConfigSpec.DoubleValue SPLIT_MILLIS = BUILDER
            .comment("Ceiling on what all of this may cost per level per tick, in milliseconds. Whatever the",
                    "round count says, the work stops here and picks up next tick.")
            .defineInRange("millis", 3.0, 0.0, 50.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Taking a build apart when it has stopped being one, and when what is left of it",
                        "cannot hold itself up.",
                        "Splitting above assumes Sable's connectivity search is right and merely slow, and",
                        "for a build losing a block to a pickaxe it is. After a crash it is not. The search",
                        "is an incremental distance field, not a flood fill from scratch: a block that goes",
                        "away is compared against the heat its neighbours were left with, and a neighbour",
                        "only becomes the root of a new region when no other neighbour of it is nearer the",
                        "old root. On a hull losing a thousand blocks in four ticks that heat describes a",
                        "build that no longer exists, and the answer it settles on is that nothing came",
                        "apart - not late, but wrongly, and running it again cannot change it. What is on",
                        "the screen is a wreck cut clean in half with daylight through the cut, flying in",
                        "formation with itself.",
                        "So the question is asked here instead, the one way that cannot be wrong: walk the",
                        "blocks and see what is reachable from what. A build that comes back in more than",
                        "one piece is handed to Sable's own assembly, piece by piece, exactly as Sable's own",
                        "split would have handed it.",
                        "The other half is about builds that have not come apart and have no business still",
                        "being in one piece: the deck plank bridging a hull broken almost in half, the two",
                        "blocks a gantry is pivoting on. Connectivity has nothing to say about those, since",
                        "touching is a yes or a no and three blocks touch as firmly as three hundred.")
                .push("sever");
    }

    public static final ModConfigSpec.BooleanValue SEVER_SEPARATE = BUILDER
            .comment("Whether a build found to be in more than one piece is actually taken apart into that",
                    "many builds. Turn it off and separation is left entirely to Sable, which after a crash",
                    "means a wreck that stays whole for as long as it exists.")
            .define("separate", true);

    public static final ModConfigSpec.BooleanValue SEVER_LIGAMENT = BUILDER
            .comment("Whether a joint too thin to carry what hangs off it is broken. This is the difference",
                    "between a hull that is cracked and a hull that has come in two.")
            .define("ligament", true);

    public static final ModConfigSpec.BooleanValue SEVER_DIAGONALS = BUILDER
            .comment("Whether two blocks touching along an edge alone count as joined. True is Sable's own",
                    "rule, and keeping it means this pass only ever finds separations Sable agrees with.",
                    "False is stricter than Sable: a build held together by corners comes apart, which",
                    "looks right and will separate builds their makers meant to hold.")
            .define("diagonals", true);

    public static final ModConfigSpec.IntValue SEVER_INTERVAL = BUILDER
            .comment("Ticks between two passes over the same build. A wreck keeps coming apart for a while",
                    "after the landing, and this is how often that is noticed.")
            .defineInRange("interval", 20, 1, 1200);

    public static final ModConfigSpec.IntValue SEVER_VOLUME = BUILDER
            .comment("The largest build, as the volume of its bounding box in blocks, that is looked at at",
                    "all. The walk is one pass over that volume and costs a byte of memory per block of it.")
            .defineInRange("volume", 1048576, 8, 67108864);

    public static final ModConfigSpec.IntValue SEVER_PIECES = BUILDER
            .comment("How many pieces of one build may be assembled into builds of their own in one pass.",
                    "The largest piece always keeps the build it was already in, so the client has a body to",
                    "trace the new ones back to; the rest wait for the next pass.")
            .defineInRange("pieces", 4, 1, 64);

    public static final ModConfigSpec.IntValue SEVER_MIN_SIDE = BUILDER
            .comment("The fewest blocks a side of a cut may have and still count as a side. Below it a cut",
                    "is not a build coming in half, it is a corner being trimmed off an intact one.")
            .defineInRange("minSide", 24, 1, 1000000);

    public static final ModConfigSpec.IntValue SEVER_NECK = BUILDER
            .comment("The most blocks a cross-section may have and still be treated as a joint rather than",
                    "as the body of the build. A hull's cross-section is hundreds of blocks and is dismissed",
                    "on its count alone, which is what keeps this cheap.")
            .defineInRange("neck", 24, 1, 4096);

    public static final ModConfigSpec.DoubleValue SEVER_CARRY = BUILDER
            .comment("How many blocks of build one point of a joint's resistance holds up. Resistance is the",
                    "same measure of strength every break in this mod is priced against, so a joint is thin",
                    "by what it is made of and not by how many blocks it has: four of obsidian outweigh",
                    "forty of wood. Raise it and thinner joints stand; lower it and builds come apart at",
                    "every waist.")
            .defineInRange("carry", 40.0, 0.1, 1000000.0);

    public static final ModConfigSpec.DoubleValue SEVER_MILLIS = BUILDER
            .comment("Ceiling on what all of this may cost per level per tick, in milliseconds. Builds not",
                    "reached inside it are looked at on a later tick.")
            .defineInRange("millis", 2.0, 0.0, 50.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("What a structure is standing on, and what happens to it when that stops being true.",
                        "Every other pass in this mod prices a block against the thing that hit it, which is",
                        "the whole of an impact and none of a structure. A gantry on two legs takes its whole",
                        "weight through the legs, and nothing here had ever heard of them: the legs were never",
                        "loaded, so they never buckled, and the deck they held stayed up in the air after the",
                        "middle of it was blown out, hanging on nothing like a branch.",
                        "So the blocks around a disturbance are pulled into a box, the weight a build is",
                        "resting on them with is added at the contact, and every block's load is routed down to",
                        "whatever is actually holding it - stacking straight up is free, hanging sideways is",
                        "not. Anything carrying more than it can, and anything being carried by nothing at all,",
                        "gives way; the box is solved again with it gone, and that repeats until nothing more",
                        "falls. The legs go first, then what they were holding, in the same breath.",
                        "It runs on terrain. A build pressing on another build is Rapier's to settle, and it",
                        "already does.")
                .push("bearing");
    }

    public static final ModConfigSpec.BooleanValue BEARING = BUILDER
            .comment("Whether the world carries weight at all. Off is the pre-1.9 behaviour: terrain breaks",
                    "only where something touched it, and whatever is left over hangs where it was.")
            .define("bearing", true);

    public static final ModConfigSpec.DoubleValue BEARING_BLOCK_WEIGHT = BUILDER
            .comment("What one block weighs, in the same units as the load a build presses with.",
                    "Sable weighs a plain block at 1 kg, so 1.0 is the honest reading and a structure's own",
                    "mass is comparable with the mass of whatever lands on it. Lower it to have the world",
                    "hold up better under itself while still failing under a ship.")
            .defineInRange("blockWeight", 1.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BEARING_PRESSURE_SCALE = BUILDER
            .comment("How much load a block carries per point of its resistance before it gives way.",
                    "The same shape as the crush pass's own scale and a separate number on purpose: that one",
                    "is a hull grinding across ground, this one is a pillar under a roof, and they are not",
                    "the same test. Raise it and structures stand under more; lower it and they fold.",
                    "The default is set so that solid rock never fails under rock - the deepest column the",
                    "box can see weighs about forty and stone carries near five hundred - while a build",
                    "putting thousands of kilogrammes through two pillars takes them out at once.")
            .defineInRange("pressureScale", 400.0, 0.1, 100000.0);

    public static final ModConfigSpec.IntValue BEARING_SPAN = BUILDER
            .comment("How far a load may travel sideways or downwards on its way to the ground.",
                    "Stacking straight up costs nothing, so a column of any height stands; every sideways or",
                    "downward tie costs one of these. Past the last one the block is called unsupported and",
                    "comes down, which is what makes an overhang finite and a cantilever fall off.")
            .defineInRange("span", 24, 0, 512);

    public static final ModConfigSpec.BooleanValue BEARING_HANGING = BUILDER
            .comment("Whether blocks with no route to the ground at all fall.",
                    "This is the branch a build was left hanging on. Off leaves floating leftovers in place",
                    "and keeps only the overload check, which is the halfway house if the falling is too much.")
            .define("hanging", true);

    public static final ModConfigSpec.BooleanValue BEARING_REST = BUILDER
            .comment("Whether a build's weight is put on the world even when nothing is moving.",
                    "The load comes from the crush pass, which measures it every tick a build is touching",
                    "anything - so a hull parked on a roof loads the walls under it and, if they are not up to",
                    "it, brings them down without ever having to hit them. Off means only what is destroyed",
                    "disturbs anything, and a build resting on a structure is weightless to it.")
            .define("rest", true);

    public static final ModConfigSpec.IntValue BEARING_MARGIN = BUILDER
            .comment("How far outside the sixteen-block region the box reaches sideways.",
                    "Whatever is left standing on the wall of the box is anchored there rather than dropped,",
                    "because the structure carries on outside and the box cannot see how. The margin is what",
                    "keeps that wall away from the part being judged.")
            .defineInRange("margin", 4, 0, 32);

    public static final ModConfigSpec.IntValue BEARING_DROP = BUILDER
            .comment("How far below the region the box reaches, which is how far down it can see the legs.",
                    "The deepest of the three margins on purpose: what holds a structure up is underneath it,",
                    "and a box that stops short of the ground anchors the structure to its own floor and finds",
                    "everything comfortably supported.")
            .defineInRange("drop", 20, 0, 128);

    public static final ModConfigSpec.IntValue BEARING_RISE = BUILDER
            .comment("How far above the region the box reaches. Small, because what is overhead is load, and",
                    "load re-queues its own region as soon as it starts falling.")
            .defineInRange("rise", 8, 0, 128);

    public static final ModConfigSpec.IntValue BEARING_ROUNDS = BUILDER
            .comment("How many times one box may break something and be solved again in a single visit.",
                    "This is the difference between a collapse and a drizzle: each round takes out everything",
                    "that failed, then asks where the load goes now that it is gone. One round is a single",
                    "layer coming off; a dozen is the whole thing coming down while you watch.")
            .defineInRange("rounds", 8, 1, 64);

    public static final ModConfigSpec.IntValue BEARING_INTERVAL = BUILDER
            .comment("How many ticks apart two solves may be, at the closest. The cost of a solve is the box,",
                    "not the damage, so this is the main dial for what the pass costs at all.")
            .defineInRange("interval", 4, 1, 200);

    public static final ModConfigSpec.IntValue BEARING_REGIONS_PER_TICK = BUILDER
            .comment("How many regions one solve may work through before leaving the rest for later.")
            .defineInRange("regionsPerTick", 2, 1, 64);

    public static final ModConfigSpec.IntValue BEARING_MAX_REGIONS = BUILDER
            .comment("How many regions may be waiting at once. Past this a disturbance is dropped rather than",
                    "queued: a wreck that has already filled the queue is going to be revisited anyway, and",
                    "the queue is not where a backlog should be allowed to live.")
            .defineInRange("maxRegions", 96, 1, 4096);

    public static final ModConfigSpec.IntValue BEARING_MAX_PER_TICK = BUILDER
            .comment("How many blocks the whole pass may drop in one tick, across every region it visits.")
            .defineInRange("maxPerTick", 384, 0, 100000);

    public static final ModConfigSpec.DoubleValue BEARING_FALL_SPEED = BUILDER
            .comment("How hard a block that lost its support is thrown. Zero lets it drop and heap where it",
                    "stood, which is what a structure coming apart under its own weight looks like; anything",
                    "above it starts to read as a demolition charge.")
            .defineInRange("fallSpeed", 0.0, 0.0, 1000.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("How much of one build this mod may destroy, which is a different question from whether",
                        "any given block should break.",
                        "Nothing used to ask it, and the answer turned out to be all of it. A wave and a",
                        "collapse both walk outwards through whatever is touching, so one contact on a hollow",
                        "hull reaches the whole skin: the bottom of a ship is a single course of material, and",
                        "a pass that takes the lowest course of every column takes the ship. What that looks",
                        "like is not a crash but a corrosion - the hull peeling off in a chain from wherever it",
                        "was touched, seconds after a landing that should only have dented it.",
                        "So a build has a damage budget. Every path in the mod that can destroy one of its",
                        "blocks draws on the same allowance, so a crash is one crash however many contacts,",
                        "waves and fronts it sets off.",
                        "It is also what stands between this mod and a hard crash in Sable. Sable splits a",
                        "sub-level when destroying blocks leaves it in disconnected pieces, and it queues that",
                        "split rather than running it at once; annihilating what is left before the split runs",
                        "kills the server with 'Sub-level assembly attempted inside plot of already removed",
                        "sub-level'. Taking a build apart over several ticks instead of in one gives the split",
                        "the time it needs.")
                .push("protect");
    }

    public static final ModConfigSpec.BooleanValue PROTECT = BUILDER
            .comment("Whether builds have a damage budget at all. Off is the pre-1.6 behaviour, where a single",
                    "landing could take a whole hull apart and occasionally took the server with it.")
            .define("protect", true);

    public static final ModConfigSpec.IntValue PROTECT_MAX_PER_TICK = BUILDER
            .comment("The most blocks one build may lose in one tick, to everything this mod does put together.",
                    "This is the pace of a wreck rather than its size: lower it and a build comes apart over",
                    "more ticks without losing any less in the end, which is both easier to watch and easier",
                    "on the tick. It is also the number that keeps Sable's queued split ahead of the damage.")
            .defineInRange("maxPerTick", 256, 1, 65536);

    public static final ModConfigSpec.IntValue PROTECT_MAX_PER_IMPACT = BUILDER
            .comment("The most blocks one build may lose to one crash, however long that crash goes on.",
                    "This is the size of a wreck. A build that lands hard loses a crater and the structure",
                    "around it and then stops, instead of carrying on until there is nothing left to walk",
                    "through - which is the whole difference between a ship that crashed and a ship that",
                    "dissolved. Raise it for catastrophes, lower it for dents.")
            .defineInRange("maxPerImpact", 3000, 1, 1000000);

    public static final ModConfigSpec.IntValue PROTECT_REST_TICKS = BUILDER
            .comment("How long a build has to be left alone before the crash it was in counts as over and its",
                    "allowance is handed back, in ticks.",
                    "Too short and one long grinding crash is scored as several and the build is eaten anyway;",
                    "too long and a ship that crashed, was repaired and flew into a cliff a minute later is",
                    "still paying for the first one. Two seconds is longer than any single impact keeps",
                    "breaking things.")
            .defineInRange("restTicks", 40, 1, 12000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Whether a shock is measured against what it hits, rather than paid for out of a purse.",
                        "This is the difference between a crash that is big and a crash that is strong, and",
                        "everything under [shock] only knows the first. A wave there carries an amount of",
                        "energy and every block it meets has a price; it buys what it can afford and stops",
                        "when it runs out. Which means a large enough crash breaks obsidian exactly as",
                        "readily as it breaks glass - it simply gets less of it - and a small one breaks",
                        "nothing at all rather than breaking the windows. Neither is what a crash looks like.",
                        "Under stress the wave carries an intensity instead, and a block carries a strength.",
                        "If what arrives is greater, the block fails; if it is not, the block holds and the",
                        "shock goes through it weakened. Nothing is bought, so nothing can be outspent: a",
                        "wall of obsidian is not expensive, it is a wall, and a hull that would have eaten",
                        "through it now runs along it instead and takes out what is behind.",
                        "The budgets under [shock] are all still in force. They stopped being the physics and",
                        "went back to being what they were meant to be - a ceiling on how much work one tick",
                        "may do.")
                .push("stress");
    }

    public static final ModConfigSpec.BooleanValue STRESS = BUILDER
            .comment("Whether shocks are resolved by strength rather than by budget. Off is the pre-1.7",
                    "behaviour exactly: waves priced per block out of an energy purse, no failure modes, and",
                    "no fragile pass. Everything else in this section does nothing while it is off.")
            .define("stress", true);

    public static final ModConfigSpec.DoubleValue STRESS_INTENSITY_SCALE = BUILDER
            .comment("Intensity per kilojoule the crash is carrying, which is the one number that decides how",
                    "hard a given crash is under stress. Everything else here is a ratio against it.",
                    "It is worth knowing the sizes involved. A four-thousand-block stone ship is about eight",
                    "tonnes, so at twenty metres a second it arrives with roughly 1600 kJ: at 0.02 that is an",
                    "intensity of 32 against stone at about 1.4, which is a dozen or so courses of stone",
                    "before it is spent and a great deal further through anything softer. A nudge into a wall",
                    "at 50 kJ is an intensity of 1, which takes the glass and leaves the stone - which is the",
                    "whole point of the mode.",
                    "Raise it and crashes get more violent without getting any wider reach through strong",
                    "material; the falloff under [shock] is what governs reach.")
            .defineInRange("intensityScale", 0.02, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BRITTLE_THRESHOLD = BUILDER
            .comment("What a brittle block's strength is worth against a shock, as a multiple of its ordinary",
                    "resistance. Glass, ice, panes, terracotta - things that are not soft but that shatter",
                    "rather than deform. Far below 1 because being hard to mine and being hard to shatter are",
                    "unrelated properties, and every number this mod had before conflated them.")
            .defineInRange("brittleThreshold", 0.15, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue DUCTILE_THRESHOLD = BUILDER
            .comment("The same for material that bends before it breaks - metal, wood, wool, chains. It gives",
                    "way at a much higher shock than its mining hardness suggests, because a shock is not a",
                    "pickaxe: a steel plate dents where stone of the same hardness cracks through.",
                    "This is what lets a metal-framed ship keep its frame and lose its skin.")
            .defineInRange("ductileThreshold", 2.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue STRUCTURAL_THRESHOLD = BUILDER
            .comment("The same for everything else - stone, concrete, earth, the ordinary mass of a build.",
                    "Left at 1 so its resistance means what it always meant and the other two modes are read",
                    "as departures from it.")
            .defineInRange("structuralThreshold", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue BRITTLE_TRANSMIT = BUILDER
            .comment("How much of a shock gets through a brittle block that did not break, as a fraction.",
                    "Low: glass that survives a shock survives it by not carrying it. This is what stops a",
                    "wave from travelling along a window as though it were a girder.")
            .defineInRange("brittleTransmit", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue DUCTILE_TRANSMIT = BUILDER
            .comment("The same through ductile material. Middling - metal and wood carry a shock well, which",
                    "is exactly why a hull with a steel keel rings end to end when it lands on one end.")
            .defineInRange("ductileTransmit", 0.55, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STRUCTURAL_TRANSMIT = BUILDER
            .comment("The same through structural material, which carries a shock best of all. High enough",
                    "that a stone bulkhead too strong to break is not the end of the crash but a thing the",
                    "crash passes through on its way to whatever is behind it - and that is the behaviour",
                    "the whole mode exists for: the deck holds, the glass on the far side of it does not.")
            .defineInRange("structuralTransmit", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STRESS_PASS_ON = BUILDER
            .comment("How much of what is left after a block fails carries on past it, as a fraction.",
                    "A block that breaks takes its own strength out of the shock; this decides how much of",
                    "the excess the next block sees. Below 1 because breaking a block is not free even when",
                    "the shock could afford it - it is where the energy goes.",
                    "Note that this is applied after the subtraction and the transmit fractions above are",
                    "applied instead of it, so a wave dies faster through what it destroys than through what",
                    "holds. That is the right way round: rubble does not conduct.")
            .defineInRange("passOn", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STRESS_BACKING = BUILDER
            .comment("How much of a block's strength comes from being held by its neighbours rather than from",
                    "what it is made of, as a fraction of the whole.",
                    "A tile out of its frame is easier to knock out than the same stone in a mountain, and on",
                    "a hull the blocks with nothing behind them are the skin - the surface a crash ought to",
                    "lose first and the one it was losing last. At 0.25 a block with all six neighbours is at",
                    "full strength and a lone plate hanging in the air is at three quarters of it.",
                    "0 disables the neighbour count entirely, which is a little cheaper and a little duller.")
            .defineInRange("backing", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STRESS_FLOOR = BUILDER
            .comment("The intensity below which a shock stops travelling. Without it a wave attenuates towards",
                    "zero without ever reaching it and keeps walking through material it can no longer harm,",
                    "which costs a great deal and does nothing.",
                    "Raise it to keep waves tight around what they actually broke.")
            .defineInRange("floor", 0.05, 0.0, 100.0);

    public static final ModConfigSpec.IntValue STRESS_MAX_SCAN = BUILDER
            .comment("How many blocks one wave may look at over its whole life, whether or not it breaks them.",
                    "Under stress a wave no longer pays for the blocks it fails to break, so the break",
                    "ceilings under [shock] stop bounding its walk - a shock running down a corridor of",
                    "material it cannot touch is free, and this is what makes it finite. It should be several",
                    "times maxBlocksPerImpact; a wave that hits it is a wave that has gone wandering.")
            .defineInRange("maxScan", 24000, 0, 1000000);

    public static final ModConfigSpec.BooleanValue GLASS_RUN = BUILDER
            .comment("Whether the windows go out along the whole length of the ship.",
                    "It is the single most recognisable thing about a crash of this size and a shock wave",
                    "cannot produce it: a wave that reached far enough to take the glass at the far end would",
                    "have taken every deck between here and there on the way, because reach and selectivity",
                    "are the same setting to it. So the fragile blocks get a pass of their own, running much",
                    "further than the wave, costing nothing per block it passes through, and breaking only",
                    "what shatters.",
                    "It travels through material rather than through space - air ends a branch - so it follows",
                    "the decks and bulkheads instead of leaping across the sky to a greenhouse next door.")
            .define("glass", true);

    public static final ModConfigSpec.IntValue GLASS_REACH = BUILDER
            .comment("How far through a build the fragile pass runs, in blocks. This is meant to be most of a",
                    "large ship rather than a neighbourhood of the impact: the whole point is that the far",
                    "end loses its windows.")
            .defineInRange("glassReach", 64, 0, 512);

    public static final ModConfigSpec.IntValue GLASS_SCAN_BUDGET = BUILDER
            .comment("How many blocks one fragile pass may read before it gives up. A fill through solid",
                    "material is cheap per block and there can be a great many blocks, so this is what keeps",
                    "a run through a mountain from costing a tick. It is a read budget and not a break one:",
                    "a pass that spends it all on stone and finds no glass has done nothing wrong, only",
                    "nothing useful.")
            .defineInRange("glassScanBudget", 20000, 0, 1000000);

    public static final ModConfigSpec.IntValue GLASS_MAX_PER_IMPACT = BUILDER
            .comment("The most fragile blocks one pass may take out. A greenhouse is a lot of panes.")
            .defineInRange("glassMaxPerImpact", 512, 0, 65536);

    public static final ModConfigSpec.IntValue GLASS_MAX_RUNS = BUILDER
            .comment("How many fragile passes one body may set going per tick. A landing is one crash however",
                    "many contacts it reports, and the fill reaches the whole build from any of them, so the",
                    "second run finds the windows the first one already broke and is pure cost. Kept above 1",
                    "only because a build split in two by the crash has two halves to run through.")
            .defineInRange("glassMaxRuns", 2, 0, 256);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("What the mod is allowed to do to make itself cheaper, none of which changes what",
                        "breaks. These exist as switches rather than as plain behaviour because each one",
                        "reaches into something outside this mod, and a Sable release that reshapes what they",
                        "reach into should cost a frame rate and not a world.")
                .push("optimize");
    }

    public static final ModConfigSpec.BooleanValue BATCH_BOUNDS = BUILDER
            .comment("Whether Sable's plot bounding boxes are rebuilt once at the end of a break pass instead",
                    "of once per block removed.",
                    "This is the largest single cost in a crash and none of it belongs to this mod. Sable",
                    "keeps a bounding box per plot chunk, and removing a block that sits on a face of that",
                    "box makes it rebuild the box by scanning every non-empty section of the chunk in full -",
                    "four thousand block reads apiece. On a hull losing its keel every block removed is on a",
                    "face, so five hundred blocks in a tick is millions of reads, and that, rather than",
                    "anything the mod itself does, is the second the game stops for on a big crash.",
                    "The rebuild depends only on what the chunk ends up containing, so running it once after",
                    "the pass gives the identical box. Only shrinking is deferred; a box that has to grow",
                    "grows at once, because that is cheap and because a box briefly too large is wrong in the",
                    "direction nothing minds.",
                    "Off restores the stock behaviour, and stock behaviour is correct - only slow.")
            .define("batchBounds", true);

    public static final ModConfigSpec.BooleanValue CACHE_CHUNKS = BUILDER
            .comment("Whether the passes that walk through a build remember the chunk they were last in.",
                    "A wave, a collapse and a fragile pass all read blocks one step at a time along a path,",
                    "and consecutive steps are almost always in the same chunk - so looking the chunk up",
                    "again per block is a hash lookup per block for an answer that has not changed. Cheap,",
                    "dull, and entirely safe: the cache lives for the length of one pass and holds a chunk",
                    "the pass is already holding open.")
            .define("cacheChunks", true);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Guards this mod puts on Sable, against states a crash reaches and Sable does not",
                        "expect. Nothing here changes what breaks. They are switches because each one is a",
                        "check bolted onto somebody else's code, and a Sable release that fixes the same",
                        "thing properly should be able to have this one taken back out.")
                .push("compat");
    }

    public static final ModConfigSpec.BooleanValue GUARD_REMOVED_SPLITS = BUILDER
            .comment("Whether a sub-level Sable has already removed is allowed to go on splitting itself.",
                    "When the last of a build's mass goes, Sable destroys the plot and marks the sub-level",
                    "removed on the spot - but the list it is removed from is only swept after every",
                    "sub-level in it has ticked. A build emptied by this mod is emptied outside that tick,",
                    "so on the following one the dead sub-level ticks once more, its connectivity flood-fill",
                    "finishes, and it tries to assemble the pieces it found into fresh sub-levels inside a",
                    "plot that is no longer there. Sable answers that with 'Sub-level assembly attempted",
                    "inside plot of already removed sub-level', which is a crashed world.",
                    "On, the flood-fill is skipped for a sub-level already marked removed - which is what",
                    "the sweep a few lines further on is about to do to it in any case.",
                    "Off restores the stock behaviour, crash included.")
            .define("guardRemovedSplits", true);

    public static final ModConfigSpec.BooleanValue GUARD_DEAD_BODY_READS = BUILDER
            .comment("Whether Sable's autosave is allowed to ask a destroyed rigid body how fast it is going.",
                    "Same ordering fault as the one above, at the other end. A sub-level whose mass this mod",
                    "has finished off has its Rapier body destroyed at once, but it stays in the container's",
                    "list until the sweep - and an autosave landing in that window walks the list and writes",
                    "every sub-level out, velocities included. Rapier answers a read on a destroyed body with",
                    "'Body has been removed', thrown out of the save, which takes the server with it.",
                    "On, a destroyed body reports standing still, which is the truth about it and what the",
                    "serializer would have written anyway had the sweep gone first.",
                    "Off restores the stock behaviour, crash included.")
            .define("guardDeadBodyReads", true);

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

    public static final ModConfigSpec.BooleanValue SOFT_BREAK_CONTACT = BUILDER
            .comment("Whether a block that is breaking anyway stops pushing back on what broke it.",
                    "Blocks are removed after the physics step, so below punchThroughRatio the solver spends",
                    "the rest of the step resolving a contact against a wall that is already gone - the build",
                    "is bounced off something that will not exist by the time the bounce is over. That is the",
                    "hop a stone hull makes when it settles onto ground it is grinding through, and no amount",
                    "of tuning the break itself can remove it, because the break is not what pushed.",
                    "On, a break drops its contact whatever the speed, and the momentum the block would have",
                    "returned as a bounce is taken away as drag instead (see breakDragMass): the hull is still",
                    "slowed by what it destroyed, it is just not thrown back up by it.",
                    "Off restores the old behaviour, where only impacts past punchThroughRatio drop a contact.")
            .define("softBreakContact", true);

    public static final ModConfigSpec.DoubleValue REBOUND = BUILDER
            .comment("How much of the speed a build has picked up away from what it just broke it is allowed",
                    "to keep, as a fraction. 0 takes all of it; 1 leaves the solver's answer alone.",
                    "This is the spring. A landing is resolved as a stack of contacts against blocks that are",
                    "about to be removed, and a solver's whole job is to push overlapping things apart, so a",
                    "build that has driven itself a block into the ground is pushed a block back out of it -",
                    "and it is pushed hardest where it is deepest, which is a shove off one corner rather than",
                    "a lift. That is the hop, and it is where being turned on your side comes from too. None",
                    "of it is a break: it is what the solver did to the contacts this mod declined to remove,",
                    "and nothing about how blocks break can reach it.",
                    "Only speed pointing away from what was broken is taken, and only on ticks something did",
                    "break, so a build still falls under its own weight, still ploughs, still climbs off a",
                    "hillside under power. It just does not come back up off what it landed on.")
            .defineInRange("rebound", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue REBOUND_SPIN = BUILDER
            .comment("The same for spin: how much of its rotation a build keeps on a tick it broke something.",
                    "The bounce is off whichever corner is deepest, so what it mostly buys is not height but",
                    "rotation - a hull settling onto ground it is grinding through ends up on its side, and",
                    "then lands on a face that was never built to be landed on. Taking the linear part away",
                    "removes what causes it, this removes what is left.",
                    "Halved per tick by default, so a crash still topples and rolls, just over the second it",
                    "takes to come to rest rather than in a single frame. 1 leaves the solver's answer alone.")
            .defineInRange("reboundSpin", 0.5, 0.0, 1.0);

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
        return enabled() && CULL_INTERIOR_VOXELS.get();
    }

    /**
     * Read straight off the spec for the same reason again: it is asked from Sable's own sub-level tick,
     * which runs before this mod's tick listener does and so before a {@code Tuning} exists for the tick.
     */
    public static boolean guardRemovedSplits() {
        return enabled() && GUARD_REMOVED_SPLITS.get();
    }

    /**
     * Read straight off the spec for the third time: it is asked from Sable's serializer, which runs from
     * the world save and so from no tick of this mod's at all.
     */
    public static boolean guardDeadBodyReads() {
        return enabled() && GUARD_DEAD_BODY_READS.get();
    }

    /**
     * Whether this mod is doing anything at all in this world. Read straight off the spec for the same
     * reason: it is asked from the remesh as well as from the tick, and it has to answer before the spec is
     * loaded, which is the state a world is in while it is still starting up.
     */
    public static boolean enabled() {
        return SPEC.isLoaded() && ENABLED.get();
    }

    /**
     * A tick's worth of config, read once instead of once per contact. Every field below is touched on
     * the hot collision path, where each {@code ConfigValue.get()} is a map lookup behind a cache check.
     *
     * <p>{@code impactStrength} is folded in here rather than at the point of use, so it applies once and
     * cannot be forgotten by a caller. A field read off this record is therefore not necessarily the number
     * written in the file.
     */
    /** What a broken block turns into on its way out. See {@code [debris] mode}. */
    public enum DebrisMode {
        FALL,
        THROW,
        SETTLE
    }

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
                         boolean softBreakContact,
                         double rebound,
                         double reboundSpin,
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
                         boolean settle,
                         double settleShare,
                         int settleDrop,
                         int settleSpread,
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
                         double shockMinSpeed,
                         double shockContactShare,
                         int shockMaxWaves,
                         boolean fracture,
                         double fractureShare,
                         int fractureCount,
                         double fractureFalloff,
                         int fractureWander,
                         int fractureGap,
                         double fractureCost,
                         boolean fractureAim,
                         int fractureScan,
                         int fractureMinRun,
                         int fractureFloor,
                         double hullShare,
                         double hullMinSpeed,
                         int fractureNeck,
                         int fractureNeckSpan,
                         double fractureNeckBias,
                         double shockCost,
                         double shockFalloff,
                         int shockMaxPerImpact,
                         int shockMaxPerTick,
                         int shockMaxTicks,
                         boolean collapse,
                         int collapseSpeed,
                         int collapseReach,
                         int collapseBite,
                         int collapseDepth,
                         int collapseDrop,
                         int collapseCooldown,
                         int collapseMaxPerTick,
                         DebrisMode debrisMode,
                         int maxSettlePerTick,
                         double collapseMinSpeed,
                         boolean collapseFit,
                         int collapseMargin,
                         int collapseMinReach,
                         double collapseFullSpeed,
                         boolean resolveSplits,
                         int splitRounds,
                         int splitTicks,
                         double splitMillis,
                         boolean severSeparate,
                         boolean severLigament,
                         boolean severDiagonals,
                         int severInterval,
                         int severVolume,
                         int severPieces,
                         int severMinSide,
                         int severNeck,
                         double severCarry,
                         double severMillis,
                         boolean bearing,
                         double bearingBlockWeight,
                         double bearingPressureScale,
                         int bearingSpan,
                         boolean bearingHanging,
                         boolean bearingRest,
                         int bearingMargin,
                         int bearingDrop,
                         int bearingRise,
                         int bearingRounds,
                         int bearingInterval,
                         int bearingRegionsPerTick,
                         int bearingMaxRegions,
                         int bearingMaxPerTick,
                         double bearingFallSpeed,
                         boolean protectBuilds,
                         int protectMaxPerTick,
                         int protectMaxPerImpact,
                         int protectRestTicks,
                         boolean shockOneCrash,
                         boolean stress,
                         double intensityScale,
                         double brittleThreshold,
                         double ductileThreshold,
                         double structuralThreshold,
                         double brittleTransmit,
                         double ductileTransmit,
                         double structuralTransmit,
                         double stressPassOn,
                         double stressBacking,
                         double stressFloor,
                         int stressMaxScan,
                         boolean glass,
                         int glassReach,
                         int glassScanBudget,
                         int glassMaxPerImpact,
                         int glassMaxRuns,
                         boolean batchBounds,
                         boolean cacheChunks) {

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
                    SOFT_BREAK_CONTACT.get(),
                    REBOUND.get(),
                    REBOUND_SPIN.get(),
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
                    SETTLE.get(),
                    SETTLE_SHARE.get(),
                    SETTLE_DROP.get(),
                    SETTLE_SPREAD.get(),
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
                    SHOCK_MIN_SPEED.get(),
                    SHOCK_CONTACT_SHARE.get(),
                    SHOCK_MAX_WAVES.get(),
                    FRACTURE.get(),
                    FRACTURE_SHARE.get(),
                    FRACTURE_COUNT.get(),
                    FRACTURE_FALLOFF.get(),
                    FRACTURE_WANDER.get(),
                    FRACTURE_GAP.get(),
                    FRACTURE_COST.get(),
                    FRACTURE_AIM.get(),
                    FRACTURE_SCAN.get(),
                    FRACTURE_MIN_RUN.get(),
                    FRACTURE_FLOOR.get(),
                    HULL_SHARE.get(),
                    HULL_MIN_SPEED.get(),
                    FRACTURE_NECK.get(),
                    FRACTURE_NECK_SPAN.get(),
                    FRACTURE_NECK_BIAS.get(),
                    SHOCK_COST.get(),
                    SHOCK_FALLOFF.get(),
                    SHOCK_MAX_PER_IMPACT.get(),
                    SHOCK_MAX_PER_TICK.get(),
                    SHOCK_MAX_TICKS.get(),
                    COLLAPSE.get(),
                    COLLAPSE_SPEED.get(),
                    COLLAPSE_REACH.get(),
                    COLLAPSE_BITE.get(),
                    COLLAPSE_DEPTH.get(),
                    COLLAPSE_DROP.get(),
                    COLLAPSE_COOLDOWN.get(),
                    COLLAPSE_MAX_PER_TICK.get(),
                    DEBRIS_MODE.get(),
                    MAX_SETTLE_PER_TICK.get(),
                    COLLAPSE_MIN_SPEED.get(),
                    COLLAPSE_FIT.get(),
                    COLLAPSE_MARGIN.get(),
                    COLLAPSE_MIN_REACH.get(),
                    COLLAPSE_FULL_SPEED.get(),
                    RESOLVE_SPLITS.get(),
                    SPLIT_ROUNDS.get(),
                    SPLIT_TICKS.get(),
                    SPLIT_MILLIS.get(),
                    SEVER_SEPARATE.get(),
                    SEVER_LIGAMENT.get(),
                    SEVER_DIAGONALS.get(),
                    SEVER_INTERVAL.get(),
                    SEVER_VOLUME.get(),
                    SEVER_PIECES.get(),
                    SEVER_MIN_SIDE.get(),
                    SEVER_NECK.get(),
                    SEVER_CARRY.get(),
                    SEVER_MILLIS.get(),
                    BEARING.get(),
                    BEARING_BLOCK_WEIGHT.get(),
                    BEARING_PRESSURE_SCALE.get(),
                    BEARING_SPAN.get(),
                    BEARING_HANGING.get(),
                    BEARING_REST.get(),
                    BEARING_MARGIN.get(),
                    BEARING_DROP.get(),
                    BEARING_RISE.get(),
                    BEARING_ROUNDS.get(),
                    BEARING_INTERVAL.get(),
                    BEARING_REGIONS_PER_TICK.get(),
                    BEARING_MAX_REGIONS.get(),
                    BEARING_MAX_PER_TICK.get(),
                    BEARING_FALL_SPEED.get(),
                    PROTECT.get(),
                    PROTECT_MAX_PER_TICK.get(),
                    PROTECT_MAX_PER_IMPACT.get(),
                    PROTECT_REST_TICKS.get(),
                    SHOCK_ONE_CRASH.get(),
                    STRESS.get(),
                    STRESS_INTENSITY_SCALE.get() * Math.max(1.0, strength),
                    ImpactResolver.eased(BRITTLE_THRESHOLD.get(), strength),
                    ImpactResolver.eased(DUCTILE_THRESHOLD.get(), strength),
                    ImpactResolver.eased(STRUCTURAL_THRESHOLD.get(), strength),
                    BRITTLE_TRANSMIT.get(),
                    DUCTILE_TRANSMIT.get(),
                    STRUCTURAL_TRANSMIT.get(),
                    STRESS_PASS_ON.get(),
                    STRESS_BACKING.get(),
                    STRESS_FLOOR.get(),
                    STRESS_MAX_SCAN.get(),
                    GLASS_RUN.get(),
                    GLASS_REACH.get(),
                    GLASS_SCAN_BUDGET.get(),
                    GLASS_MAX_PER_IMPACT.get(),
                    GLASS_MAX_RUNS.get(),
                    BATCH_BOUNDS.get(),
                    CACHE_CHUNKS.get());
        }

        /** The lowest speed at which any block, however soft and however heavy the ram, could give way. */
        public double breakSpeedFloor() {
            return Math.min(this.minImpactSpeed, this.crushSpeed);
        }

        /** What the block's own strength is multiplied by, given how it fails. */
        public double threshold(final Failure failure) {
            return switch (failure) {
                case BRITTLE -> this.brittleThreshold;
                case DUCTILE -> this.ductileThreshold;
                case STRUCTURAL -> this.structuralThreshold;
            };
        }

        /** What a block of this kind lets through when it does not break. */
        public double transmit(final Failure failure) {
            return switch (failure) {
                case BRITTLE -> this.brittleTransmit;
                case DUCTILE -> this.ductileTransmit;
                case STRUCTURAL -> this.structuralTransmit;
            };
        }
    }
}
