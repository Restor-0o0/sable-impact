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
- **Shock.** A crash hard enough is felt past the blocks it happened to touch. The striking body's kinetic
  energy becomes a budget that spreads through whatever the broken block was attached to, spending each
  block's resistance as it goes, so a hull dropped from height comes apart instead of losing the floor it
  landed on. See [Shock](#shock-when-an-impact-is-felt-past-the-block-it-broke).
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

## Shock: when an impact is felt past the block it broke

Every other decision in this mod is made about a *contact*, and a contact is a face. A hull that lands on its
belly reports contacts along its belly and nowhere else, so without this chapter the belly is the only thing
that can ever break - however far the thing fell. That is right for a scrape along a cliff and plainly wrong
for a drop: a stone hull that falls three hundred blocks does not lose its floor and keep its walls.

A shock is a *budget*. The crash has so much to spend; every block it destroys takes its own resistance out of
that, and it stops when it can no longer afford the next one. The wave spreads out from the break through
whatever is touching, nearest blocks first, and stays in the grid it started in - a contraption's plot is
surrounded by empty plotgrid, so a wave that begins in a hull cannot leave it, and one that begins in terrain
cannot climb into a hull.

Where the budget comes from is the important part, and it is asked twice. Once of the contact, which knows how
hard *this* face was hit and nothing else. And once of the striking body's kinetic energy - half its mass
times its speed squared - which is what the crash actually has to spend and which knows the difference between
a boulder and a battleship. The larger answer wins. The kinetic one is drawn from a reservoir refilled once
per body per tick, so a landing that reports six hundred contacts is still one crash and not six hundred of
them; the hull and the ground each get a draw, because wrecking both is what one crash does.

A wave too big for one tick is put down and picked up on the next, so a large wreck comes apart over about a
second rather than in a single frame.

This is the most destructive thing in the mod. The ceilings at the end of this chapter are not decoration.

Every setting named here lives in the `[shock]` section of the config file.

```toml
[shock]
	shockBlocks = true
	minOvershoot = 2.0
	hullScale = 3.0
	terrainScale = 1.5
	kineticScale = 1.0
	minSpeed = 8.0
	perContactShare = 0.2
	fractureShare = 0.6
	fractureCount = 2
	fractureFalloff = 0.995
	fractureWander = 2
	cost = 1.0
	falloff = 0.98
	maxBlocksPerImpact = 8192
	maxBlocksPerTick = 6144
```

### When a shock happens at all

`minOvershoot` is how far past a block's break speed the impact had to be before anything is passed on, as a
ratio. At the default of `2.0` a block broken by an impact going twice the speed it takes to break it sends
nothing; at three times it starts to.

This is the setting that separates a crash from ordinary use, and it gates the kinetic budget as well as the
contact one - a battleship resting its weight against a wall is carrying just as much energy as one flying
into it, and only one of those is a crash. Ploughing a hillside, scraping a wall, settling onto the ground:
all of those break blocks, and all of them are meant to leave the rest of the build alone. Raise
`minOvershoot` and only outright crashes propagate. Lower it towards `1` and every block broken anywhere sends
a wave through whatever it was attached to, which is a build that sheds hull every time it brushes a tree.

`minSpeed` is the second gate, and it is the important one if a build has ever folded up under you while you
were flying it carefully. `minOvershoot` is a *ratio* - it asks whether the hit was hard for what was hit -
and a ratio has no idea how fast anything was going. A ship is several tonnes; at walking pace it is already
carrying more energy than a stick of dynamite, and every other number in this chapter would happily spend it.
So below `minSpeed` nothing propagates at all, whatever the overshoot says and whatever the build weighs.
**Setting a dirigible down, docking it, nudging it a block sideways: none of those can crack it.**

It does a second job above the line as well. The crash is priced on the speed *over* `minSpeed` rather than
on the speed, and priced quadratically, so a landing a little too fast costs a few blocks rather than
crossing a threshold into costing hundreds. That is what to raise if things still come apart too readily at
low speed, and it is deliberately the one number here `impactStrength` does not touch - that dial is for how
hard crashes are, and this is the line between a crash and ordinary use.

`shockBlocks = false` turns the whole chapter off. Only blocks actually in contact ever break then, which is
cheap, entirely predictable, and leaves a hull that hits the ground at terminal velocity looking like it was
set down on it.

### How much there is to spend

`kineticScale` is the one that scales with the crash. It is shock energy per kilojoule the striking body is
actually carrying, and it is what makes a big fast thing come apart entirely where a small slow one chips.
Sable weighs a plain block at one kilogram and stone at two, so a four-thousand-block stone ship is about
eight tonnes: at the default `1.0` it survives a landing at twelve metres a second missing a few hundred
blocks, and arrives at sixty as a heap. Set it to `0` and shocks are priced on the contact alone.

`hullScale` and `terrainScale` are the contact-side price, in energy per unit of overshoot past
`minOvershoot`. They are what decides a crash too small for its kinetic energy to matter - a wing clipping a
tower, a single block driven into a wall - and they are two numbers because those are two different wishes.
`hullScale` is how much of itself a build loses that way; `terrainScale` is the same for the world's blocks
and is kept low, because a wave that keeps going through terrain is a hull digging itself a tunnel.

The larger of the two answers is what the wave gets, so on any real crash `kineticScale` is the one in charge
and the other two only matter at the small end. **If structures are still too solid, raise `kineticScale`.**

`impactStrength` multiplies all three, so the shock keeps step with everything else when that dial moves.

`perContactShare` is how much of what is left any one contact may take. A landing does not touch the ground
at a point - it reports contacts all along the face that came down, and the crash belongs to all of them. At
`1.0` the first one to be handled takes the whole thing and levels a sphere around itself, which looks less
like a build that fell over than like one that was shot; and because a sphere of ten thousand blocks cannot
be broken inside one tick, what you actually watch is the wreck being eaten outward from a point over the
following second. At the default `0.2` the same total arrives as several smaller waves spread along the face
that hit, each finishing about when it started. Nothing is lost either way - what one contact leaves is
there for the next.

### Waves and cracks

A wave spends its energy in every direction at once, so what it leaves is a bite taken out of the build. That
is the right picture for the part that actually hit, and it is nothing like what happens to the rest of it.
Things this size do not dissolve - they come apart along a line. The back half of the ship separates and goes
its own way, still a ship.

So the crash is divided between two things. `fractureShare` is how much of it goes into **cracks** instead of
into the wave: seams one block wide that run clean through the build from the impact, cutting it into pieces
rather than eating it. The rest still goes to the ordinary wave. Together they are the crash - a wrecked,
cratered end where it came down, and the rest of the hull in a couple of large pieces.

`fractureCount` is how many cracks one crash may open, and they are opened **once per build per tick**, by
the first contact hard enough to earn them. Not once per contact: a landing reports contacts in the hundreds
and they are all the same crash, and a hull cut in three hundred places is not in pieces, it is gravel. Two
cuts is a build in three parts. Each crack takes a different axis, so two cuts really are two pieces rather
than the same cut made twice.

`fractureWander` is how far a crack may drift off the flat plane it started on. At `0` a build is cut as
though by a saw, which is legible and looks like nothing that has ever broken; at the default the seam
wanders a block at a time and comes out ragged. It costs nothing either way - a crack removes exactly one
block per column of its plane however much it wanders, which is also what guarantees the cut is a cut and not
a decoration.

`fractureFalloff` is the crack's own version of `falloff` below, and it is set much closer to `1` because the
two want opposite things: a wave has to be stopped from reaching across the map, and a crack is no use at all
unless it reaches the far side of the build.

Cracks are for contraptions only. A crack through terrain is a canyon.

One thing this cannot do on its own: whether a piece that has been cut free then *flies off* as a body of its
own is Sable's decision, not this mod's. All a crack does is make sure nothing is still holding the piece on.

Set `fractureShare` or `fractureCount` to `0` for the old behaviour - waves only, and the energy that would
have gone into cracks handed back to them.

### How far it travels

`cost` is what one block's resistance costs the budget. Higher makes material matter more: an obsidian
bulkhead eats a wave that would have run the length of a wooden deck. Lower makes every material come apart
alike, and the crash is priced by size alone.

`falloff` is the share of its purchasing power a wave keeps for every block it travels. A block one step out
costs its material; one fifty steps out costs that same material divided by `falloff` fifty times over. This
is what keeps a large crash from being spent arbitrarily far from where it happened - the wreck levels what is
near it before it reaches for what is far, and no budget buys unlimited range. At the default `0.98` a
hundred-block hull still comes apart end to end on a bad enough fall; at `0.9` the damage stays within about
twenty blocks of the impact whatever the fall was.

Because a wave spreads in every direction at once, what it can reach grows as the cube of how far it got. A
budget that buys a straight run of two hundred blocks levels a sphere of some eight thousand, which is why
these two numbers move the result much harder than they look like they should.

A block that is passable - undergrowth, water - ends that branch of the wave without being touched. A shock is
carried by what is solid; letting it cross a lake would have one contact on a shoreline take the far bank
with it.

### What it is allowed to cost

`maxBlocksPerImpact` is the ceiling on what one shock may break before it is called finished. At the default
`8192` it is deliberately out of the way of anything but a genuinely enormous wreck; lower it if you want the
energy budget overruled by a flat number.

`maxBlocksPerTick` is the ceiling on what all shocks together may break per level per tick, and on a real
crash it is this rather than `maxBlocksPerImpact` that stands between the impact and the tick budget: a hull
landing flat reports hundreds of contacts in one tick and every one of them may send a wave. It is counted
separately from the root `maxBlocksPerTick`, which counts only what the contacts themselves broke.

Shocks also run under the same `maxTickMillis` deadline as the rest of the break pass. Unlike the 1.2.0
behaviour, a wave cut short by either ceiling is *not* thrown away: it is kept and carried on next tick, up to
sixty-four waves per level at a time. That is what lets a large crash spend its whole budget without spending
it all in one frame.

### If structures are still too solid

In order: raise `kineticScale`, raise `falloff` towards `0.99`, lower `cost`, then lower `minOvershoot`. If
the crash is clearly breaking far more than shows up, the cap that is biting is `maxBlocksPerTick` here or
`maxTickMillis` at the root - and because waves resume now, the symptom of that is a wreck that comes apart
slowly rather than one that stops half-done.

Bear in mind that a shock only starts where a contact already broke something. If nothing at all is breaking,
this chapter is not the problem - see [Where to start](#where-to-start).

## Debris: what flies, and where it lands

A crash that leaves a clean hole does not look like a crash. This chapter is about the other half of an
impact: the blocks that come *out* of the hole, how many of them there are, how far they go, and whether
they are still there afterwards.

Every setting named here lives in the `[debris]` section of the config file.

```toml
[debris]
	scatterChance = 0.25
	contraptionScatterChance = 0.3
	settleShare = 0.85
	settleDrop = 6
	settleSpread = 4
	maxScatterPerTick = 96
	scatterVelocityScale = 0.12
	scatterUpwardKick = 0.08
	landingSearch = 2
	landingNeedsFloor = true
	lifetimeTicks = 200
	dropWhenLost = true
	damagePerBlock = 0.0
	damageMax = 40
```

### What becomes of a broken block

A broken block has three possible ends, asked in this order: it is **thrown** as a piece of debris, it
**settles** somewhere near where it broke, or it is gone.

Only the first of those costs anything real. Thrown debris is a falling block entity — it ticks, it falls, it
writes a block back, and every client in range is told about it — so it is rationed hard and only ever a few
dozen blocks a tick. Settling is a block change and nothing else, which is why nearly everything can do it.

**`scatterChance`** — the fraction of broken **terrain** blocks that are thrown. `0` turns terrain debris off.

**`contraptionScatterChance`** — the same fraction for a **contraption's own** blocks, and the reason the two
are separate settings. They are not the same wish. A hillside that keeps its rubble is scenery, and there is
an enormous amount of it; a ship shedding its hull is the thing the player is actually watching. Both are
kept well under `1` all the same: every block that flies is a block leaving the wreck, and a wreck that
throws all of itself is a fountain rather than a crash. What is wanted is a few pieces turning through the
air over a heap that is mostly still there.

**`settleShare`** — the fraction of everything that did *not* fly which is put back down instead of vanishing,
and the setting that decides whether a crash leaves wreckage or leaves a hole. A settling block is pushed
clear of whatever broke it, falls to the first solid thing under it within `settleDrop`, and is written
there. At the default most of a ruined build is still lying where it came down, and terrain a hull ploughed
through is piled beside the furrow rather than deleted. `0` restores the old behaviour: a clean hole and
nothing to show for it.

A block that finds no floor at all within reach does not settle — a hull that comes apart in mid-air has
nothing under it, and wreckage nailed to the sky at the altitude the ship broke up at is worse than wreckage
that is simply missing. Only the pieces that were *thrown* survive a breakup with nothing beneath it, which
is what they are for.

**`settleDrop`** — how far a settling block may fall looking for a floor. It is the depth of hole it is
willing to fill, not a distance it is thrown. Low leaves wreckage perched where it broke; high has a hull
broken over a ravine posting its blocks to the bottom of it.

**`settleSpread`** — how wide the heap of a *contraption's* settled blocks is. A hull's blocks live out in the
plotgrid and have no position of their own in the world — only the crash does — so unlike terrain they are
spread over a disc around the impact. Too small and a ship comes down as a tower of itself; too large and the
wreck is a thin film over the landscape rather than a pile.

**`maxScatterPerTick`** — the hard cap on thrown debris, and the number that keeps the two chances above from
being a server killer. Blocks past the cap are not lost — they fall through to settling like any other block
that did not fly.

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
| `contraptionScatterChance` down | Fewer hull pieces flying. What they were going to cost is not saved by `settleShare` — a settled block is just a block change. |
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

### Shock

Section `[shock]`. See [Shock](#shock-when-an-impact-is-felt-past-the-block-it-broke).

| Option | Default | |
|---|---|---|
| `shockBlocks` | `true` | Whether an impact is felt past the block it broke at all. |
| `minOvershoot` | `2.0` | How far past a block's break speed an impact has to be before it sends a shock, as a ratio. |
| `hullScale` | `3.0` | Contact-side energy a contraption's own blocks pass on per unit of overshoot past `minOvershoot`. Decides the crashes too small for kinetic energy to matter. Counted per contact, so this is the one to lower if a build dissolves. |
| `terrainScale` | `1.5` | The same for the world's own blocks. Kept low: a wave that keeps going through terrain is a tunnel. |
| `kineticScale` | `1.0` | Energy per kilojoule the striking body is carrying. The main dial for how thoroughly a build comes apart. |
| `minSpeed` | `8.0` | The speed (m/s) below which nothing is a crash and no shock is sent, whatever was hit. The guard that lets a build be landed and moved. Not touched by `impactStrength`. |
| `perContactShare` | `0.2` | The largest share of a crash's remaining energy one contact may spend. Low spreads the damage along the face that hit; `1.0` gives it all to one point. |
| `fractureShare` | `0.6` | The share of a crash spent cutting the build into pieces rather than eating a hole in it. `0` is waves only. |
| `fractureCount` | `2` | How many cracks one crash may open, once per build per tick rather than once per contact. |
| `fractureFalloff` | `0.995` | What a crack keeps of its purchasing power per block travelled. Near `1`: a crack is no use unless it crosses the build. |
| `fractureWander` | `2` | How far a crack may drift off its plane, in blocks. `0` cuts like a saw. |
| `cost` | `1.0` | What one block's resistance costs the budget. Higher makes material matter more. |
| `falloff` | `0.98` | The share of its purchasing power a wave keeps per block travelled. What bounds its reach. |
| `maxBlocksPerImpact` | `8192` | Ceiling on blocks one shock may break. |
| `maxBlocksPerTick` | `6144` | Ceiling on blocks all shocks together may break per level per tick. |

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
| `punchThrough` | `true` | Whether a block that breaks gets out of the hull's way instead of stopping it. Off, the whole build is stopped dead by the first block it breaks. |
| `punchThroughRatio` | `2.5` | With `punchThrough` on, how far an impact must overshoot before the contact is dropped too. |
| `breakDragMass` | `2.0` | Mass (kg) a contraption must drag up to its own speed per point of resistance of every block punched clean through. `0` is free digging. |
| `breakDragMax` | `0.25` | The largest share of its speed a contraption may lose to breaking blocks in one tick. `1` restores dead stops. |

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
| `scatterChance` | `0.25` | Fraction of broken **terrain** blocks that fly off as debris rather than simply vanishing. |
| `contraptionScatterChance` | `0.3` | The same for a **contraption's own** blocks. |
| `settleShare` | `0.85` | Fraction of the blocks that did *not* fly which are put back down nearby instead of vanishing. The difference between wreckage and a hole. |
| `settleDrop` | `6` | How far a settling block may fall looking for something to rest on. |
| `settleSpread` | `4` | How wide the heap of a contraption's settled blocks is. Its blocks live in the plotgrid, so only the crash has a place in the world. |
| `maxScatterPerTick` | `96` | Hard cap on debris entities per level per tick. Blocks past it fall through to settling. |
| `scatterVelocityScale` | `0.12` | How hard debris is thrown, relative to how far the impact overshot the block. |
| `scatterUpwardKick` | `0.08` | A flat upward push added to every piece, so a downward hit does not drive its debris into the floor. |
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

→ `create_aeronautics_impact-1.3.0-fast.jar`, listed as *Create Aeronautics Impact (Fast)*. Same mod id and
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
