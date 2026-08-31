package org.restor.create_aeronautics_impact;

import net.neoforged.neoforge.common.ModConfigSpec;

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

    public static final ModConfigSpec.DoubleValue SCATTER_CHANCE = BUILDER
            .comment("Fraction of broken blocks that fly off as falling-block entities instead of just shattering.",
                    "Each one is a ticking entity that lands and writes a block back, so this is the single most",
                    "expensive thing an impact can do; maxScatterPerTick is what keeps it bounded.")
            .defineInRange("scatterChance", 0.2, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MAX_SCATTER_PER_TICK = BUILDER
            .comment("Hard cap on debris entities spawned per level per tick. Blocks past the cap still break,",
                    "they just vanish instead of flying.")
            .defineInRange("maxScatterPerTick", 32, 0, 4096);

    public static final ModConfigSpec.IntValue MAX_BREAK_EFFECTS_PER_TICK = BUILDER
            .comment("Hard cap on breaks per level per tick that play the block-break sound and particles. Every",
                    "one of those is a packet to every player in range, and a hull ploughing terrain produces",
                    "hundreds per tick. Breaks past the cap are silent - the crash still looks like a crash",
                    "because the first ones are not. Silent breaks drop nothing regardless of dropItems.")
            .defineInRange("maxBreakEffectsPerTick", 24, 0, 4096);

    public static final ModConfigSpec.DoubleValue SCATTER_VELOCITY_SCALE = BUILDER
            .comment("How fast debris is thrown, relative to how much the impact overshot the block's resistance.")
            .defineInRange("scatterVelocityScale", 0.25, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue DROP_ITEMS = BUILDER
            .comment("Whether shattered blocks drop their items.")
            .define("dropItems", false);

    public static final ModConfigSpec.BooleanValue PUNCH_THROUGH = BUILDER
            .comment("Whether a hard enough impact drops the contact as well as the block, letting the hull",
                    "carry on into the next layer untouched. It is what makes a ram plough, and it is also what",
                    "makes a contraption yanked hard enough disappear into the ground: each layer it reaches is",
                    "still fast enough to be waved past, so nothing ever stops it until it runs out of speed",
                    "somewhere inside the terrain. Off, terrain always pushes back and a hull hits it instead.")
            .define("punchThrough", false);

    public static final ModConfigSpec.DoubleValue PUNCH_THROUGH_RATIO = BUILDER
            .comment("With punchThrough on, how far an impact has to overshoot a block's break speed before the",
                    "contact is dropped as well as the block. Below this the block still shatters but the hull is",
                    "still pushed back by it, so a ram bleeds speed as it digs.")
            .defineInRange("punchThroughRatio", 2.5, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue BREAK_DRAG_MASS = BUILDER
            .comment("Mass (kg) a contraption has to drag up to its own speed for every block it punches",
                    "clean through. This is the only thing slowing a ram that is fast enough to be waved past",
                    "the terrain: without it gravity keeps adding speed, every next layer is easier than the",
                    "last, and the hull tunnels to bedrock. Higher = terrain grabs harder, 0 = free digging.")
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
            .defineInRange("breakDragMax", 0.12, 0.01, 1.0);

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

    public static final ModConfigSpec.BooleanValue LOG_PERFORMANCE = BUILDER
            .comment("Print this mod's own share of the server tick to the log every five seconds. Off by",
                    "default and worth turning on exactly once: when the game hitches, this is what says whether",
                    "the hitch is here or somewhere else in the pack.")
            .define("logPerformance", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Tuning cached;

    private ImpactConfig() {
    }

    public static void invalidate() {
        cached = null;
        BlockProfile.clearCache();
    }

    public static Tuning tuning() {
        Tuning current = cached;
        if (current == null) {
            current = Tuning.read();
            cached = current;
        }
        return current;
    }

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
                         double breakDragMax) {

        static Tuning read() {
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
                    BREAK_DRAG_MAX.get());
        }

        /** The lowest speed at which any block, however soft and however heavy the ram, could give way. */
        public double breakSpeedFloor() {
            return Math.min(this.minImpactSpeed, this.crushSpeed);
        }
    }
}
