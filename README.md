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
  pane of stone; the face of a mountain is a mountain. Same material, different answer.
- **Crushing.** A landed or low-flying hull presses down on what is under it. Weight spreads through the
  ground, thin terrain gives, and blocks squeezed sideways pop out.
- **Cracking.** Damage that does not finish a block is remembered and shown as vanilla break progress, and
  heals if it is left alone.
- **Boring.** Above a speed threshold the hull cuts a tunnel rather than skidding, giving up a share of its
  momentum for every block removed.
- **A tick budget.** The whole sweep runs under a wall-clock ceiling and gives up cleanly when it runs out,
  and the fleet is served round-robin so no hull starves.

Blocks removed are dropped, scattered as falling blocks, or removed silently, depending on how much of the
tick's effect ration is left.

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
| `backingWeight` | `0.6` | How much of a terrain block's strength is on loan from what holds it in place. Contraptions are exempt. |
| `backingReach` | `3` | How many blocks behind a struck face count towards holding it up. A gap ends the count rather than being skipped. |
| `backingBeside` | `0.25` | What one block beside a struck face is worth against one block of depth behind it. |

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

### Breaking, drops and debris

| Option | Default | |
|---|---|---|
| `maxBlocksPerTick` | `512` | Hard cap on blocks destroyed by impacts per level per tick. |
| `dropItems` | `false` | Whether shattered blocks drop their items. |
| `scatterChance` | `0.2` | Fraction of broken blocks that fly off as falling-block entities. The most expensive thing an impact can do. |
| `maxScatterPerTick` | `32` | Hard cap on debris entities per level per tick. Blocks past it still break. |
| `scatterVelocityScale` | `0.25` | How fast debris is thrown, relative to how far the impact overshot. |
| `maxBreakEffectsPerTick` | `24` | Hard cap on breaks that play sound and particles. Breaks past it are silent and drop nothing. |

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

→ `create_aeronautics_impact-1.0.0-fast.jar`, listed as *Create Aeronautics Impact (Fast)*. Same mod id and
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

- **`v1.0.0`** — the release build.
- **`v1.0.0-fast`** — the same mod shipped with performance-first defaults. See [`FAST.md`](FAST.md) on that
  branch for what each one costs. Same mod id, so only one of the two jars can be installed at a time.

[`DEVELOPMENT.md`](DEVELOPMENT.md) is the guide for working on the mod itself: file map, entry
points, invariants and how to add a setting. `AERO_IMPACT_DESIGN.md` documents the model the code
implements.
