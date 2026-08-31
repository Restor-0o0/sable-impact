# Create Aeronautics Impact

A NeoForge 1.21.1 addon for [Sable](https://maven.ryanhcode.dev/) and Create: Aeronautics.

Physics bodies in Sable collide with the world, but the world does not answer back: a two-hundred-tonne
airship meets a birch tree and stops. This mod makes the terrain lose. A hull that hits something decides,
per block, which of the two gives way — and then ploughs, bores, crushes or shears its way through, paying
speed for every block it takes.

## What it does

- **Impact.** Every contact is a two-sided contest between the block struck and the block striking it,
  settled on material strength, closing speed and mass. The loser breaks; the winner takes wear.
- **Backing.** A block is only as strong as what is behind and beside it. A stone wall one block thick is a
  pane of stone; the face of a mountain is a mountain. Same material, different answer. Contraptions are
  read the same way on their own weight, so a hollow shell comes apart at the skin and a solid build does
  not.
- **Crushing.** A landed or low-flying hull presses down on what is under it. Weight spreads through the
  ground, thin terrain gives, and blocks squeezed sideways pop out.
- **Cracking.** Damage that does not finish a block is remembered and shown as vanilla break progress, and
  heals if it is left alone.
- **Boring.** Above a speed threshold the hull cuts a tunnel rather than skidding, giving up a share of its
  momentum for every block removed.
- **Debris.** Broken blocks are thrown clear as falling blocks and go looking for somewhere to sit when they
  land, instead of vanishing the moment the spot they came down in happens to be taken. See
  [Debris](#debris-what-flies-and-where-it-lands).
- **A tick budget.** The whole sweep runs under a wall-clock ceiling and gives up cleanly when it runs out,
  and the fleet is served round-robin so no hull starves.

Blocks removed are thrown as debris, dropped, or removed silently, depending on how much of the tick's
rations is left.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228+ |
| Sable | 2.0.1 – 2.x |
| Sable Companion | 1.6.0+ (ships inside Sable) |
| Create: Aeronautics | optional, 1.3.x |

Server side is where all of this happens. The jar is needed on the client as well, because it is what
Sable's own installation asks for, but nothing in it runs there.

## Configuration

Everything is a server config, written to `<world>/serverconfig/create_aeronautics_impact-server.toml` the
first time a world loads. That means settings travel with the save rather than with the installation, and a
new world starts from the defaults again. Editing the file while the server is running is picked up without
a restart; the caches that depend on it are dropped and rebuilt on the next tick.

Every option carries its full explanation as a comment in the generated file, including why the default is
the default. What follows is the map — the reference tables at the end list every key.

### Where to start

Four settings between them cover most of what people want to change:

- **`impactStrength`** — one multiplier over the whole force model. `2` makes everything roughly twice as
  destructive, `0.5` half. It deliberately does not touch `minImpactSpeed`, `carveMinSpeed` or
  `contraptionBlockToughness`, because scaling those breaks things rather than tuning them.
- **`backingWeight`** — how much of a block's strength is on loan from its surroundings. `0` gives every
  block its plain material strength wherever it stands, which makes a garden wall as tough as a cliff.
  `hullBackingWeight` is the same dial for the contraption's own blocks, and is what decides whether a
  hollow build lands like the solid one it is shaped like.
- **`crushBlocks`** — whether weight alone destroys terrain, with no impact needed. Turning it off makes the
  mod purely about collisions and is the single largest saving available.
- **`maxTickMillis`** — the wall-clock ceiling on everything this mod does per tick. Lower it first if the
  server is struggling.

### Material overrides

`materialOverrides` is a table for saying what vanilla's own numbers do not.

Block strength is otherwise derived from mining hardness and blast resistance, because those two are the
only stats every block in every mod actually has. What they are not is a considered opinion: an author picks
them to place a block on a pickaxe tier, so the immovable heart of a machine can easily come out softer than
gravel. This is where a pack says otherwise — per block, per tag, or per mod — without anyone recompiling
anything.

```toml
materialOverrides = [
    "minecraft:obsidian      resistance=6",
    "#minecraft:leaves       soft=true",
    "create:*                scale=1.5",
    "mekanism:*              indestructible=false",
    "*                       scale=0.8",
]
```

An entry is a **selector**, then one or more **settings**, separated by spaces or commas.

**Selectors**

| Form | Matches |
|---|---|
| `modid:path` | exactly that block |
| `#modid:path` | every block in that block tag |
| `modid:*` | every block from that mod |
| `*` | every block |

**Settings**

| Setting | Value | Effect |
|---|---|---|
| `resistance` | number | The block's strength outright, replacing the derivation from vanilla stats. |
| `scale` | number | A multiplier on that strength, derived or given. `2` doubles it. |
| `indestructible` | `true` / `false` | `true` is never broken by anything this mod does. `false` takes that away from a block that inherited it from `indestructibleResistance`. |
| `fragile` | `true` / `false` | `true` shatters the block at `fragileTrigger` rather than weighing it against the hull, the way leaves and glass do. |
| `soft` | `true` / `false` | `true` makes hulls pass through the block and clear it the way they clear undergrowth. It gives up its collider to do so, so a hull is not stopped by something it is about to mow down. |

`resistance` is on the scale the model works in *after* the hardness range has been compressed, not the
vanilla one: roughly `0.6` for dirt, `1.0` for wood, `1.4` for stone, `3` for obsidian. Turn on
`logPerformance` and hit something to see live numbers, or work in `scale` instead and let the derivation
find the ballpark for you.

**How entries combine.** A block may be caught by several at once, and the most specific entry to name a
given setting is the one that decides it: an exact id beats a tag, a tag beats a namespace, a namespace
beats `*`, and among equals the entry written later wins. Settings are read one at a time rather than as a
group, so a namespace rule setting `scale` and an exact rule setting `fragile` both apply to the same block.

```toml
materialOverrides = [
    "create:*                        scale=2",          # every Create block is twice as tough
    "create:brass_casing             scale=1",          # except this one, back to normal
    "#c:storage_blocks/netherite     indestructible=true",
]
```

**Failure is quiet by design.** A rule naming a block or tag that no installed mod provides is not an
error — it simply never matches — which is what lets one config file cover a pack whose mod list changes.
Anything that does not parse is dropped with a line in the log and the rest of the file is honoured.

Tag rules are rebuilt whenever tags are, so a datapack reload takes effect immediately.

### Performance

The mod is designed to give ground rather than to stop working. `maxTickMillis` is the real limit and
everything else is a way of staying under it; `adaptiveDetail` is what decides to finish roughly instead of
finishing half of it exactly, and it climbs back to full detail the moment there is room.

Ordered by how much they save, and by how visible the cost is:

| Change | Saves | Costs |
|---|---|---|
| `blockUpdates = false` | The most, by a wide margin. | Nothing tidies up after a silent removal: torches on a fresh tunnel wall stay floating, sand does not fall until poked, redstone does not recalculate. |
| `movingCrushInterval = 4` | A great deal on landed or low-flying builds. | A boulder rides the treetops for a moment at a time before the canopy under it gives. |
| `maxContactsPerTick = 2048` | A great deal on hulls flush against terrain. | A very heavy pile-up chews through terrain more slowly than it should for as long as it lasts. |
| `sweepFinestDetail = 1` | Close to a third of the sweep. | Holes come out rounder and wider than the thing that made them. |
| `backingMemoTicks = 4` | A lot on hulls with thousands of contacts. | A block briefly holds as though the hillside behind it were still there. |
| `maxQuietTicks = 60` | A lot on worlds full of parked airships. | A tower thrown up under a hovering build goes unnoticed for up to three seconds. |
| `clearSoftBlocks = false` | A swept slab per axis on every moving hull. | Grass and flowers stay standing where a hull has been. Nothing is stopped by them either way. |
| `crushBlocks = false` | The whole weight model. | Stationary and slow-moving builds stop marking the ground at all. |

`logPerformance = true` prints this mod's share of the tick to the log every five seconds, broken down by
pass. It is worth turning on exactly once: when the game hitches, it is what says whether the hitch is here
or somewhere else in the pack.

## Debris: what flies, and where it lands

A crash that leaves a clean hole does not look like a crash. This chapter is about the other half of an
impact: the blocks that come *out* of the hole, how many of them there are, how far they go, and whether
they are still there afterwards.

Every setting named here lives in the `[debris]` section of the config file.

```toml
[debris]
	scatterChance = 0.5
	contraptionScatterChance = 0.85
	maxScatterPerTick = 96
	scatterVelocityScale = 0.25
	scatterUpwardKick = 0.15
	landingSearch = 2
	landingNeedsFloor = true
	lifetimeTicks = 200
	dropWhenLost = true
	damagePerBlock = 0.0
	damageMax = 40
```

### How many blocks fly

A broken block has two possible ends: it is thrown as a piece of debris, or it is simply gone. Three
settings decide which, and they are asked in this order.

**`scatterChance`** — the fraction of broken **terrain** blocks that are thrown. `1.0` throws everything the
per-tick cap can still afford, which is what to set if a crater should be ringed by what came out of it. `0`
turns terrain debris off and leaves clean holes.

**`contraptionScatterChance`** — the same fraction for a **contraption's own** blocks, and the reason the two
are separate settings. They are not the same wish. A hillside that keeps its rubble is scenery, and there is
an enormous amount of it; a ship shedding its hull is the thing the player is actually watching, and there
are far fewer of those blocks. A piece of hull that vanishes reads as the mod failing to do anything, so this
one is set higher by default and is the one to raise first.

**`maxScatterPerTick`** — the hard cap, and the number that keeps the two above from being a server killer. A
hull ploughing a hillside breaks blocks by the hundred, and each one turned into debris is an entity that has
to fall, land, write a block back and be sent to every client in range. Blocks past the cap still break —
they just vanish rather than fly. Raise the chances *and* this together, or the chances alone will do very
little.

Two kinds of block are never thrown, whatever these are set to: blocks with a block entity — a chest thrown
as debris would quietly empty itself — and anything holding a fluid.

### How far they go

**`scatterVelocityScale`** — how hard a piece is thrown, measured against how far the impact overshot what
the block could take. A block that barely lost drops at its own feet; one hit far harder than it could stand
is flung. Raising this widens the field the wreckage ends up spread over. Much past `1.0` blocks are thrown
far enough to land clear of the crash and stop reading as part of it.

**`scatterUpwardKick`** — a flat upward push given to every piece on top of whatever direction the impact
threw it. Without some of this, a block broken by a downward hit — which is most of them, since most crashes
are landings — is driven straight back into the ground and settles roughly where it stood, which looks like
nothing happened to it at all.

### Where they land

This is the part vanilla does badly, and the reason wreckage used to disappear.

A vanilla falling block has exactly one position it is willing to occupy: the block it happens to be standing
in the moment it touches down. If anything is already there — the wall it was thrown against, the slab it
rolled onto, the hole it just came out of — it gives up, becomes an item, and is gone. That is a perfectly
good rule for gravel, which falls straight down a column it has just vacated. It is a bad one for a block
that was thrown sideways into a hillside.

**`landingSearch`** — how far a piece may look for somewhere else to put itself when that happens. The search
is a widening shell around where it came down, lowest position of each shell first, so wreckage settles
downward and piles rather than stacking. At `2` almost everything finds a home within a block or two of where
it landed. It is not free — each step out is a shell of positions to test — but it is only ever paid by the
pieces that *failed* to land, which is a small share of a crash. `0` restores vanilla's behaviour outright.

**`landingNeedsFloor`** — whether a spot found by that search has to have something solid under it. On,
debris piles up on the ground and against walls the way rubble does. Off, a piece takes the first free
position it finds, which fills in overhangs and leaves blocks standing in mid-air.

**`lifetimeTicks`** — how long a piece may stay in the air before it is made to come down wherever it has got
to. `200` is ten seconds, far longer than anything thrown by an impact needs; it is a backstop against debris
flung out over an ocean or off a cliff ticking for as long as the chunk stays loaded. `0` leaves vanilla's own
limit as the only one.

**`dropWhenLost`** — what becomes of a piece that found nowhere at all to be placed. On it drops as an item,
off it is gone. This is only reached once the search has already failed, so it is a handful of blocks per
crash rather than all of them — though a crash inside a cave with this on can still leave a lot of items on
the floor.

### What they do on the way down

**`damagePerBlock`** and **`damageMax`** — fall damage debris deals to whatever it lands on, per block
fallen and in total, exactly the way an anvil does. `0` — the default — makes debris harmless to walk under.
Anything much above `0.5` makes standing near a crash lethal, which is honest, and is also how a player loses
an inventory to scenery.

### If it is costing too much

In order of how much they save:

| Change | Effect |
|---|---|
| `maxScatterPerTick` down | The one real limit. Everything else only changes what is competing for these slots. |
| `contraptionScatterChance` down | Fewer hull pieces. Keep this above `scatterChance`; it is the debris that is actually being looked at. |
| `landingSearch = 0` | Drops the search entirely. Wreckage goes back to disappearing when it lands somewhere occupied. |
| `dropWhenLost = false` | No items from failed landings. Worth it on a server where a crash site turns into a carpet of drops. |
| `lifetimeTicks` down | Fewer entities alive at once when debris is being thrown a long way. |

## Reference

### Impact

| Option | Default | |
|---|---|---|
| `impactStrength` | `1.0` | Master multiplier over the whole force model. |
| `minImpactSpeed` | `6.0` | Closing speed (m/s) below which nothing is ever broken. A noise floor: below it a contraption would dig its own grave as it settles. |
| `fragileTrigger` | `4.0` | Speed (m/s) above which a fragile block is handed back to Sable to shatter on its own terms. |
| `breakContraptionBlocks` | `true` | Whether a contraption loses its own blocks when it rams terrain harder than they are. |
| `contraptionBlockToughness` | `1.5` | Multiplier on a contraption block's strength when weighed against terrain. |
| `impactWear` | `1.0` | What winning an impact costs the winner, scaled by how evenly matched the two were. Needs `crackBlocks`. |

### Material strength

How a block's strength is derived from its vanilla stats, before `materialOverrides` gets a say.

| Option | Default | |
|---|---|---|
| `hardnessScale` | `1.8` | How much resistance raises the speed needed to break a block. |
| `explosionResistanceFactor` | `0.35` | Weight of blast resistance in the derivation. Of the two vanilla numbers this is the one that describes structure. |
| `hardnessWeight` | `0.5` | Weight of mining hardness. At `1.0` vanilla rates an oak log above stone, so wood ends up holding up boulders. |
| `resistanceExponent` | `0.5` | Compresses the range. Vanilla spreads blocks over three orders of magnitude; `1.0` puts obsidian hundreds of m/s out of reach. |
| `indestructibleResistance` | `100000.0` | Blast resistance at or above which a block is never broken. A backstop for modded permanent blocks — bedrock and barriers are already handled. |

### Mass

| Option | Default | |
|---|---|---|
| `massSensitivity` | `1.0` | How strongly mass per contact block eases the speed needed to break terrain. `0` disables it. |
| `referencePressure` | `12.0` | Mass (kg) per contact block at which a contraption is neither helped nor hindered by its weight. |
| `massFactorMin` | `0.5` | Lower clamp on the mass factor. Below `1` a light, sprawling build struggles. |
| `massFactorMax` | `6.0` | Upper clamp. Above `1` a heavy, compact build punches through denser terrain. |
| `crushSpeed` | `3.8` | The lowest speed (m/s) any contraption can break terrain at, however heavy. Mass drags `minImpactSpeed` down towards this. |

### Backing

| Option | Default | |
|---|---|---|
| `backingWeight` | `0.6` | How much of a terrain block's strength is on loan from what holds it in place. |
| `backingReach` | `3` | How many blocks behind a struck face count towards holding it up. A gap ends the count rather than being skipped. |
| `backingBeside` | `0.25` | What one block beside a struck face is worth against one block of depth behind it. |
| `hullBackingWeight` | `0.5` | The same, for a contraption's own blocks. `0` restores the old reading, where every block of a hull was as strong as if the whole build were behind it. |
| `hullBackingReach` | `3` | How deep a hull has to be packed to land as solid material. Anything thinner than this gives at the skin. |

### Crushing

| Option | Default | |
|---|---|---|
| `crushBlocks` | `true` | Let a contraption destroy what it stands on by weight alone, with no impact needed. |
| `crushPressureScale` | `70.0` | How much load a block bears per point of its strength before it is crushed. Higher means sturdier ground. |
| `crushInterval` | `4` | Ticks between crush passes for a build standing still. Settling is meant to be slow. |
| `movingCrushInterval` | `1` | The same for a build that is moving. Doubled automatically at the coarsest detail rung. |
| `crushSpan` | `32` | How far above its own underside a build looks for what is holding it up. |
| `crushSeat` | `0.15` | The share of the ground a build covers that counts as carrying it, however few blocks report contact this tick. `0` turns a rolling build into a digger. |
| `crushShear` | `0.5` | How much of a block's load a sideways contact counts for, against one bearing weight from above. |
| `crushDepth` | `8` | How many blocks a load travels through the terrain. A gap stops it. |
| `crushSpread` | `3.0` | How much of the load is lost per block travelled, on top of the loss from being spread. |
| `crushDownShare` | `0.6` | The share of a load handed to the block directly below. |
| `crushSideShare` | `0.1` | The share handed to each of the four sideways neighbours. Four of these plus `crushDownShare` should come to one. |
| `crushLeadTicks` | `3.0` | Ticks of travel the pass looks ahead, so ground is answered as the hull arrives over it. |
| `crushDisplace` | `true` | Shove crushed blocks aside instead of destroying them, so a furrow has banks. Blocks with nowhere to go still break. |
| `crushDisplaceReach` | `3` | How far a shoved block may be carried looking for room. |
| `maxCrushPerTick` | `256` | Cap on blocks crushed per contraption per pass, counted separately for underneath and flanks. |
| `crushScanBudget` | `65536` | Blocks one crush pass may examine. Separate from `sweepScanBudget` on purpose. |

### Cracking

| Option | Default | |
|---|---|---|
| `crackBlocks` | `true` | Let blocks remember earlier damage instead of being either untouched or gone. |
| `crackResilience` | `3.0` | How many impacts at exactly a block's break speed it survives. `1.0` restores all-or-nothing behaviour. |
| `crackHealTicks` | `300` | Ticks a fully cracked block takes to recover if nothing hits it again. |
| `crackSpall` | `0.3` | Damage dealt to the six blocks around one that has just been destroyed. `0` disables it. |
| `crackSpallCeiling` | `0.95` | How far spall alone may crack a block, as a share of what breaking it takes. At `1` a crater eats the mountain. |
| `maxCrackedBlocks` | `4096` | How many part-damaged blocks are remembered per level. |
| `maxCrackEffectsPerTick` | `64` | Cap on crack overlays sent per level per tick. Damage past it is still recorded, just shown later. |

### Momentum

| Option | Default | |
|---|---|---|
| `boreMinSpeed` | `20.0` | Speed above which a hull shears the sides of the hole it is making rather than cutting a hole its own shape. |
| `boreShare` | `0.5` | How much of the impact a block beside the hull's path feels, against one in it. `0` turns shearing off. |
| `punchThrough` | `false` | Whether a hard enough impact drops the contact as well as the block. On, a hull yanked hard enough disappears into the ground. |
| `punchThroughRatio` | `2.5` | With `punchThrough` on, how far an impact must overshoot before the contact is dropped too. |
| `breakDragMass` | `2.0` | Mass (kg) a contraption must drag up to its own speed per block punched clean through. `0` is free digging. |
| `breakDragMax` | `0.12` | The largest share of its speed a contraption may lose to breaking blocks in one tick. `1` restores dead stops. |

### Breaking and drops

| Option | Default | |
|---|---|---|
| `maxBlocksPerTick` | `512` | Hard cap on blocks destroyed by impacts per level per tick. |
| `dropItems` | `false` | Whether shattered blocks drop their items. |
| `maxBreakEffectsPerTick` | `24` | Hard cap on breaks that play sound and particles. Breaks past it are silent and drop nothing. |

### Debris

Everything under `[debris]`. The chapter on it is [above](#debris-what-flies-and-where-it-lands).

| Option | Default | |
|---|---|---|
| `scatterChance` | `0.5` | Fraction of broken **terrain** blocks that fly off as debris rather than simply vanishing. |
| `contraptionScatterChance` | `0.85` | The same for a **contraption's own** blocks. |
| `maxScatterPerTick` | `96` | Hard cap on debris entities per level per tick. Blocks past it still break, they just vanish. |
| `scatterVelocityScale` | `0.25` | How hard debris is thrown, relative to how far the impact overshot the block. |
| `scatterUpwardKick` | `0.15` | A flat upward push added to every piece, so a downward hit does not drive its debris into the floor. |
| `landingSearch` | `2` | How far a piece may look for somewhere to put itself when it cannot be placed where it landed. `0` is vanilla behaviour. |
| `landingNeedsFloor` | `true` | Whether such a spot has to have something solid under it. |
| `lifetimeTicks` | `200` | How long a piece may stay in the air before it is made to come down wherever it is. `0` leaves only vanilla's limit. |
| `dropWhenLost` | `true` | What becomes of a piece that found nowhere at all: an item, or nothing. |
| `damagePerBlock` | `0.0` | Fall damage debris deals to what it lands on, per block fallen, the way an anvil does. `0` is harmless. |
| `damageMax` | `40` | Ceiling on that damage from any one piece. |

### Sweeps

| Option | Default | |
|---|---|---|
| `carveThroughTerrain` | `true` | Break the terrain a fast contraption is about to move into, instead of waiting for a contact the solver will not report. |
| `carveMinSpeed` | `8.0` | Speed (m/s) above which a contraption carves its own path. |
| `carveLookaheadTicks` | `2.0` | How many ticks of travel carving looks ahead. One tick is the least that can work, which is why it does not. |
| `carveMaxBlocks` | `512` | Cap on blocks carved per contraption per tick. |
| `clearSoftBlocks` | `true` | Sweep away grass, flowers, vines and cobwebs, which have no collision box and are otherwise swallowed silently. |
| `softSweepInterval` | `2` | Ticks between soft-block sweeps. |
| `softSweepMinSpeed` | `0.5` | Contraptions slower than this are not swept, so parked builds cost nothing. |
| `softSweepMaxBlocks` | `256` | Cap on soft blocks cleared per contraption per sweep. |
| `clearOverlaps` | `true` | Free contraptions that have ended up inside terrain. Two solids in the same place raise no contacts, so nothing else can. |
| `overlapSweepInterval` | `10` | Ticks between overlap sweeps. |
| `displaceOverlaps` | `true` | Shove overlapping blocks into free space instead of destroying them. Cheaper, and what a solid object actually does. |
| `overlapSweepMaxBlocks` | `512` | Cap on overlapping blocks cleared per contraption per sweep. |
| `stuckGraceTicks` | `60` | How long a wedged hull is given to free itself on centre-overlap alone before the sweep starts widening. |
| `grindStuckTicks` | `400` | How long a hull stays buried before it starts grinding its own blocks away instead. Set very high to let buried hulls stay buried. |

### Performance

| Option | Default | |
|---|---|---|
| `maxTickMillis` | `6.0` | How long this mod may spend inside one server tick before it stops and picks the rest up on the next. |
| `adaptiveDetail` | `true` | Whether a sweep that keeps running out of time may do less work rather than simply stopping where it ran out. |
| `sweepScanBudget` | `24576` | Blocks all sweeps for one contraption may examine per tick. Sweep cost grows with the cube of a build's size. |
| `sweepFinestDetail` | `0` | The finest rung the sweep may sample at, `0`–`3`. Higher is cheaper and rounder. |
| `coarseSweepTravel` | `2.0` | Travel per window, in blocks, past which a hull is swept coarsely whatever the server is doing. |
| `maxQuietTicks` | `20` | The longest a hull with nothing near it may be left unswept. |
| `backingMemoTicks` | `1` | How many ticks a backing reading is kept for. |
| `maxContactsPerTick` | `0` | How many contacts are examined per tick before the rest are waved through. `0` is unlimited. |
| `blockUpdates` | `true` | Whether a silent removal still notifies its neighbours. Off is the largest single saving here. |
| `cullInteriorVoxels` | `true` | Let Sable merge fully buried blocks back into their neighbours. Off, no terrain anywhere is ever merged. |
| `logPerformance` | `false` | Print this mod's share of the server tick to the log every five seconds. |

### Materials

| Option | Default | |
|---|---|---|
| `materialOverrides` | `[]` | Per-block, per-tag and per-mod strength and behaviour overrides. See [Material overrides](#material-overrides). |

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Sable and Sable Companion are resolved from
`https://maven.ryanhcode.dev/releases` and are compile-only — neither is bundled.

Unit tests cover the pure decision maths (`ImpactResolver`, `SweepDetail`, backing):

```
./gradlew test
```

To build a named variant — the same mod with a different set of config defaults — pass `build_variant`. It
suffixes both the jar file and the name shown in the mod list, so a player can tell which one they installed
without opening it:

```
./gradlew build -Pbuild_variant=fast
```

→ `create_aeronautics_impact-1.1.0-fast.jar`, listed as *Create Aeronautics Impact (Fast)*. Same mod id and
same version, so only one variant can be installed at a time.

For a release, build every variant in one run:

```
.\build-all.ps1
```

Both jars land in `dist/`, and the script reads each one back to report the name, version and licence it
actually shipped with. Every branch is built in a throwaway git worktree, so it never touches your working
copy and never leaves you on another branch — which also means it builds what is committed, and warns
about uncommitted changes rather than shipping a jar without them.

`-Offline` passes `--offline` to Gradle, `-SkipTests` drops the test run, `-OutDir <path>` collects the jars
somewhere else.

## License

[MIT](LICENSE). Include it in any modpack, public or private, without asking.

Sable itself is under the Polyform Shield License and is not bundled here — this mod only compiles against
its API and resolves it from the author's maven at build time.

## Branches

- **`release`** — the release build.
- **`fast`** — the same mod shipped with performance-first defaults. See [`FAST.md`](FAST.md) on that
  branch for what each one costs. Same mod id, so only one of the two jars can be installed at a time.

[`DEVELOPMENT.md`](DEVELOPMENT.md) is the guide for working on the mod itself: file map, entry
points, invariants and how to add a setting. `AERO_IMPACT_DESIGN.md` documents the model the code
implements.
