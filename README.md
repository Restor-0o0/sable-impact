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
  energy spreads through whatever the broken block was attached to, so a hull dropped from height comes
  apart instead of losing the floor it landed on. See
  [Shock](#shock-when-an-impact-is-felt-past-the-block-it-broke).
- **Stress.** What that shock breaks is decided by strength against strength rather than by what it can
  afford, so a wall too strong to break is something the crash passes *through* on its way to the glass
  behind it - and the windows go out along the whole length of a ship whose decks held. See
  [Stress](#stress-strength-against-strength-not-price-against-purse).
- **Load bearing.** Weight in the world is routed to whatever is holding it up rather than spread around
  where it landed, so the legs under a platform take the ship that was dropped on it, buckle under it, and
  bring down the deck they were holding - and nothing is left hanging in the air on a structure that is no
  longer there. It needs no impact: a build simply parked on a roof loads the walls under it. See
  [Load bearing](#load-bearing-what-holds-the-world-up).
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

### Editing them in game

The file is not the only way in. Pause the game and there is a small icon in the top right corner of the
menu; it opens NeoForge's own configuration screen, built from the same spec that writes the file, so every
option is there with its comment as a tooltip and its range enforced by the widget. The same screen is on
the *Mods* list, under *Create Aeronautics Impact* → *Config*. Changes are written straight to the save's
toml and picked up on the next tick, exactly as an edit by hand would be.

There is deliberately no button on the title screen, though several mods put one there. This config lives in
the save, so with no world loaded there is nothing to edit and NeoForge greys the screen out; a button whose
only possible answer is "load a world first" is worse than no button. On a dedicated server the screen is
read-only for the same reason — the file the client would be editing is not the one the server is using.

### Turning it off

**`enabled = false`** is the master switch, and it is the whole of the mod: no contact is examined, no block
is destroyed, no sweep runs, nothing is crushed or carved, and Sable builds its colliders exactly as it would
without the jar installed.

Because the config is a *server* config it lives inside the save, so this is per world. A world that has to
behave as though the mod were not installed can be set to `enabled = false` once and left that way, while
every other world on the same installation stays as it was. Nothing needs to be removed from `mods/`, and it
is read live — flipping it takes effect on the next tick.

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
| `failure` | `brittle` / `ductile` / `structural` | How the block gives way under a shock, overriding what its sound implied. See [Stress](#stress-strength-against-strength-not-price-against-purse). |

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
| `maxFallQuietTicks = 200` | A lot on anything in a long fall, on top of what `freeFallQuiet` already saves. | Ground generated under a falling build goes unnoticed for up to ten seconds, which is most of a fall from the build limit. |
| `clearSoftBlocks = false` | A swept slab per axis on every moving hull. | Grass and flowers stay standing where a hull has been. Nothing is stopped by them either way. |
| `crushBlocks = false` | The whole weight model. | Stationary and slow-moving builds stop marking the ground at all. |

One saving is not in that table because it is on by default and there is no reason to turn it off:
`optimize.batchBounds`. It is worth knowing about anyway, because it is larger than everything above it put
together. Sable keeps a bounding box per plot chunk and rebuilds it, by scanning every non-empty section of
that chunk block by block, each time a block on one of its faces is removed - and on a hull losing its keel
every removed block is on a face. Five hundred blocks in a tick is on the order of sixteen million block
reads, none of which are this mod's own work and all of which show up as this mod's hitch. Batching them into
one rebuild per chunk at the end of the pass gives the identical box. `false` restores Sable's own behaviour,
which is correct and slow.

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
	maxHullWaves = 1
	fracture = true
	fractureShare = 0.85
	fractureCount = 2
	fractureFalloff = 0.995
	fractureWander = 2
	fractureGap = 6
	fractureCost = 0.05
	fractureAim = true
	fractureScan = 48
	fractureMinRun = 3
	fractureFloor = 128
	hullShare = 0.25
	hullMinSpeed = 20.0
	fractureNeck = 12
	fractureNeckSpan = 6
	fractureNeckBias = 0.08
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

`maxHullWaves` caps how many waves one contraption may open in a tick regardless, and it is the setting to
reach for if a crash reads as the build being eaten rather than broken. The contact side of a shock is priced
per contact and is *not* drawn from the reservoir, so a hull landing flat gets a full wave for every one of
its several hundred contacts; what that looks like is the impact point spreading outwards in rings, tick
after tick, until there is no build left. Capping it also leaves something for the cracks to work on - a
build already eaten cannot come apart into anything.

Terrain is not capped. A hull ploughing a hillside is meant to plough it.

### What a build is allowed to shrug off

Terrain is a hillside and comes apart the way a hillside does. A build is a made thing, and what its frames
and skins and joints are *for* is to carry a blow somewhere other than into the block next to it. Until 1.9.2
this mod did not know the difference: a wave through a hull was priced exactly like a wave through a cliff,
and a ship's belly brushing a treetop shed a deck.

`hullMinSpeed` is the floor under all of it. Below it a contraption still cracks — the plane through the
contact is cut, the glass still goes — but **nothing spreads outwards from the break**. A belly touching a
treetop, a mast catching a mast, a landing that was merely rough: those leave a hole where they touched and a
seam running out of it, which is what they should leave. It is a separate number from `minSpeed` because
`minSpeed` is about terrain too, and terrain has no business shrugging anything off.

`hullShare` is what is left of a blow that did clear the floor, once the build has carried it: the wave a
structure passes through itself is that fraction of the one terrain would get, in both what it can afford and
how hard it arrives. The cracks are not touched by it — a crack is the break that was wanted, and it is priced
on its own under `fractureCost`. What `hullShare` quiets is the crumbling around the crack.

`contraptionBlockToughness` is the third leg of this, and it is the oldest: how much stronger a build's own
blocks are than the same blocks in the ground. It went from `1.5` to `3.0` in 1.9.2 for the same reason as the
rest — a plank in a hull is part of a hull, and a plank in a floor is a plank.

Set `hullShare = 1.0`, `hullMinSpeed = 0.0` and `contraptionBlockToughness = 1.5` for the pre-1.9.2 behaviour,
where a hull was treated as a hillside.

### Waves and cracks

A wave spends its energy in every direction at once, so what it leaves is a bite taken out of the build. That
is the right picture for the part that actually hit, and it is nothing like what happens to the rest of it.
Things this size do not dissolve - they come apart along a line. The back half of the ship separates and goes
its own way, still a ship.

So the crash is divided between two things. `fracture` turns cracks on, and `fractureShare` is how much of
the crash goes into them instead of into the wave: seams one block wide that run clean through the build from
the impact, cutting it into pieces rather than eating it. The rest still goes to the ordinary wave. Together
they are the crash - a wrecked, cratered end where it came down, and the rest of the hull in a couple of
large pieces.

`fractureCount` is how many cracks one crash may open, and they are opened **once per contact, up to that
many per build per tick**. Not once per contact without limit: a landing reports contacts in the hundreds and
they are all the same crash, and a hull cut in three hundred places is not in pieces, it is gravel. Two cuts
is a build in three parts. Spreading them over separate contacts is what makes them look like breaks rather
than a diagram: each cut starts where the build was actually touched, so two cuts are two seams from two
corners of the face that landed rather than two planes crossing at whichever contact was reported first. Each
takes a different axis as well, so two cuts really are two pieces rather than the same cut made twice.

The count is kept against **the build being cut**, not the one that arrived. A crash has two sides and both of
them come apart; keeping it against the striker meant a ship landing on a plate spent both of the plate's cuts
on its own hull, and the plate was left whole with a hole in it.

#### Where the cut is made

Which way, first. A crack is a plane, and a plane is named by the axis it is cut across. Until 1.9.1 that
axis was dealt out in turn - X, then Y, then Z - so that two cracks would cross rather than repeat. On a solid lump that is fine. On
anything anybody builds it is wrong two times in three, because **the axis a thing is thin along is the one
axis it cannot be parted across**.

A mast cut across its length falls in two. A mast cut *along* its length is two half-masts still joined at both
ends, which is not a break at all - and the ship that flew into it is stopped dead by a mast that is still
there, wedges on it and tips over. A one-block plate cut across its width parts; cut across its thickness the
plane *is* the plate, and what the crack does is chew a square hole out of the middle of it and stop when the
energy runs out, leaving the plate in one piece.

So with `fractureAim` on the axis is measured instead of dealt. The material is followed out from the break in
all three directions - `fractureScan` blocks at most, crossing the same gaps a crack itself may cross - and
the cut is made across whichever it runs furthest along. That is the mast's length, the plate's width, and on
a hull the cut amidships that leaves the stern behind. A second crack takes the next axis down, so a plate cut
across its length is then cut across its width and comes apart in four.

`fractureMinRun` is what is never taken: an axis the build barely extends along at all. Below it the plane lies
in the face of the thing rather than through it, which is the bite out of the plate, and no number of those
ever separates anything. Cracks after the first wrap around within what is left eligible rather than falling
back onto the thin axis the first two were avoiding.

`fractureFloor` is how many blocks a crack may take before the price of them is looked at at all. `fractureCost`
already makes cuts cheap, but cheap is not the same as certain, and a cut that stops halfway has split nothing:
what it leaves is a notch, and the build it is in is still one build with a groove in it. This is the guarantee
that a cut worth starting is worth finishing. It is not a licence to destroy - the build's damage allowance
under `[protect]`, the per-tick ceiling and the per-impact ceiling all stop a crack exactly as they did.

Set `fractureAim = false` and `fractureFloor = 0` for the pre-1.9.1 behaviour: cracks dealt by rotation and
only ever as long as the crash could pay for.

`fractureNeck` is how far along that axis the crack may travel from the contact looking for a better place to
break, and it exists because **things do not break where they are hit, they break where they are weakest**. On
anything built out of one material those are near enough the same block. On anything built out of two they are
not, and the difference is the whole of what a crash looks like. An obsidian mast with a wooden gondola on the
end of it, clipped on the gondola, does not crack down the mast: the gondola shears off at its joint, because
the ring of wood around that joint is the least material carrying the most weight. Cutting at the contact put a
seam through the middle of the gondola and left the halves of it hanging on the mast.

So every plane within reach is weighed by what is standing in it — the summed resistance of the blocks in a
window of it, `fractureNeckSpan` blocks either way, which is a cross-section's strength as directly as this mod
measures anything — and the cut is made at the lightest one. Thin *by strength*, not by block count: four
blocks of obsidian outweigh forty of wood, and it is the forty that give.

Two kinds of plane are not candidates at all. One holding something unbreakable, because a cut that cannot be
finished is a notch; and one holding nothing, because an empty cross-section is not a weak place to cut, it is
a place already cut, past the end of the thing.

`fractureNeckBias` is what a candidate pays per block of distance from the contact, as a share of what cutting
at the contact itself would have cost. Without it the weakest plane anywhere in reach wins even when the blow
landed nowhere near it, and builds would come apart at their thinnest point wherever they were touched. The
price is set against the contact rather than against an average on purpose: put an obsidian spar within reach
of a wooden hull and the *average* section is the spar, and every step away from the contact would be priced as
though it cost obsidian to take.

Set `fractureNeck = 0` for the pre-1.9.2 behaviour, where the cut was always made through the contact.

`fractureGap` is how many blocks of nothing a crack may cross before it gives up, and on a real build it is
the setting that decides whether cracks work at all. A wave is carried by what is solid and has no business
crossing a room. A crack is a surface, and a ship is a shell around air: at `0` a seam entering the hull dies
an inch inside the skin it came through, which cuts solid plates in two very convincingly and does nothing
whatever to anything with a room in it. Bridging lets the same seam come out of the deck, cross the hold and
carry on through the floor, which is the cut that actually separates something. Crossing is free - there was
nothing there to break - and bounded, so a crack cannot wander off into open plotgrid forever.

`fractureCost` is what a crack pays for a block, as a fraction of what a wave pays for the same one, and it
is the other half of making cracks work. The two are buying different shapes. A wave's price buys a sphere;
at that same price a crack buys a disc a few blocks across, which is a scratch. And a cut that stops halfway
has split nothing at all - it is only a cut if it reaches the far side - so it is priced cheap enough to
cross what it started on. Lower it to have cracks reach further; `1.0` makes cracking cost exactly what
pulverising does, which in practice turns it off.

`fractureWander` is how far a crack may drift off the flat plane it started on. At `0` a build is cut as
though by a saw, which is legible and looks like nothing that has ever broken; at the default the seam
wanders a block at a time and comes out ragged.

A wandering seam takes the block on its plane as well as the one it wandered onto, and it has to. Sable decides
what is still one build by neighbours *including the diagonals*, so two columns whose missing block sits at
depths one apart are still joined through the seam - the block left in the first touches the block left in the
second across the corner. Every column of a drifted seam is like that, which is how a build could be cut end to
end and stay in one piece with a groove in it. Taking the plane block too means that whatever the seam does,
the whole of the plane it started on is gone wherever the crack reached, and nothing can be traced across a
plane that is not there. The drift is left doing what it was wanted for: widening the cut unevenly, so the
edges of the two pieces are ragged rather than sawn. It costs the blocks it wanders over, and `0` is the
cheapest.

`fractureFalloff` is the crack's own version of `falloff` below, and it is set much closer to `1` because the
two want opposite things: a wave has to be stopped from reaching across the map, and a crack is no use at all
unless it reaches the far side of the build.

Cracks are for contraptions only. A crack through terrain is a canyon.

One thing this cannot do on its own: whether a piece that has been cut free then *flies off* as a body of its
own is Sable's decision, not this mod's. All a crack does is make sure nothing is still holding the piece on.

Set `fracture = false` for the old behaviour - waves only, and the energy that would have gone into cracks
handed back to them.

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

### If a build peels instead of breaking

The hull coming off in a chain from one contact, several seconds after a landing, is one build losing more
than a crash's worth of itself. `maxPerImpact` is the setting that bounds it and `maxPerTick` is the one that
slows it down enough to watch. If it starts from a landing that should not have been a crash at all, raise
`collapse.minSpeed`; if it spreads outwards through the hull rather than downwards, lower `shock.kineticScale`
or `maxTicks`.

### If a wreck keeps shedding blocks long after it has stopped

That tail is the wave, not the collapse. Waves are put down and picked up across ticks so the tick budget is
never blown, which is correct, and which on a big enough crash means a wreck is still coming apart in rings a
minute later. `maxTicks` is the wall: past it whatever is left of a wave is dropped. Lower it to cut the tail
shorter, and lower `maxHullWaves` towards `1` so there are fewer waves to carry in the first place.

If what is left after that is a build that lands, stops and then dissolves rather than folding, the setting
that decides the difference is `collapse`.

### If a crash eats the build instead of splitting it

This is the shape of the damage rather than the amount of it, and three settings decide it. Lower
`maxHullWaves` towards `1`, so the crash is one crater instead of one per contact. Lower `fractureCost`, so
a crack reaches the far side of the build instead of stopping inside it. Raise `fractureGap` if the build is
mostly rooms, so a seam can cross them. Only then raise `fractureShare`, which just moves energy between two
things that are both already working.

### If a build hops off what it landed on

This is not a break at all and nothing in the shock or the drops can reach it. Blocks are not removed until
after the physics step, so for the whole of the step the build is resolved against the wall it is destroying,
and pushing overlapping things apart is the one thing a solver is for: a hull that has driven itself a block
into the ground is pushed a block back out of it, hardest where it went deepest. That is a shove off one
corner rather than a lift, which is why the build comes back up at an angle and can end the tick on its side.

`rebound` takes the outward part of that speed back, and `reboundSpin` takes back some of the rotation it
came with. Both fire only on ticks where the build actually broke something, and only along the way out — a
build still falls under its own weight, still ploughs forward into what it is cutting, still climbs off a
hillside under power. `softBreakContact` above is the same problem attacked at the contact, and it helps, but
it cannot finish the job: the contacts it does not remove are still resolved, and a lopsided set of them is
exactly what the torque comes from.

Set both to `1.0` for the pre-1.4.1 behaviour.

### If structures are still too solid

In order: raise `kineticScale`, raise `falloff` towards `0.99`, lower `cost`, then lower `minOvershoot`. If
the crash is clearly breaking far more than shows up, the cap that is biting is `maxBlocksPerTick` here or
`maxTickMillis` at the root - and because waves resume now, the symptom of that is a wreck that comes apart
slowly rather than one that stops half-done.

Bear in mind that a shock only starts where a contact already broke something. If nothing at all is breaking,
this chapter is not the problem - see [Where to start](#where-to-start).

## Stress: strength against strength, not price against purse

A shock as the last chapter describes it *buys* blocks. It carries an amount of energy, every block has a
price, and it takes what it can afford. That is a perfectly good model of how much damage a crash does, and
a poor model of what it does it to - because a purse large enough buys anything. A crash big enough to level
a stone deck buys the obsidian bulkhead behind it too; it simply gets less of it. And a crash too small to
afford the cheapest block on the list leaves the windows in.

Neither is what happens. What decides whether something breaks is not how much the crash has left, it is
whether what arrives at that block is stronger than that block.

So under `[stress]` the wave carries an *intensity* rather than a budget, and each block it meets carries a
*strength*. If the intensity is greater, the block fails. If it is not, the block holds - and the shock is
not over. It goes through, weaker, and keeps looking.

That last sentence is the whole chapter. A budgeted wave that meets a wall it cannot pay for stops, because
"too expensive" and "the end of the world" are the same answer to it. A measured one has somewhere else to
be: it runs along the bulkhead, through it, and out into the glass on the far side. Which is why a hull now
loses its windows down its whole length and keeps the deck that stopped the wave.

### How hard a crash is

One number sets the scale: `intensityScale`, in intensity per kilojoule the striking body is carrying.

Some sizes, because they are not obvious. Sable weighs a plain block at about a kilogram and stone at two, so
a four-thousand-block stone ship is roughly eight tonnes. At twenty metres a second it arrives with about
1600 kJ, which at the shipped `0.02` is an intensity of **32**. Stone stands at about `1.4`. So that crash
takes five or six courses of stone in a straight line - a hole several metres across, since the wave spreads
in every direction at once - and a great deal further through anything softer.

A wing clipping a tower is more like 50 kJ, so an intensity of **1**. That is under stone and over glass, and
what it does is take out the windows and scuff the paint. Nothing else in this mod could tell those two
crashes apart except by how much they were allowed to spend.

Raising `intensityScale` makes crashes more violent. It does not make them reach further through material
they could already break - `falloff` and the ceilings under `[shock]` still govern that.

### How a block fails

Vanilla has no property that says what a block is made of. Hardness is a pickaxe tier and blast resistance is
an explosion table, and neither can tell a steel plate from a slab of stone of the same nominal toughness.
What every block does have, in every mod, is the sound it makes when you walk on it - and an author who gave
their block the metal sound stated what it is made of far more deliberately than they ever chose its
hardness. So that is what the mode is derived from, and `materialOverrides ... failure=` is how a pack says
otherwise.

| Mode | What it is | Threshold | Passes on when it holds |
|---|---|---|---|
| `brittle` | Glass, ice, panes, anything already marked `fragile` | `brittleThreshold`, far below 1 | `brittleTransmit`, very little - glass does not conduct a shock |
| `ductile` | Metal, copper, chains, wood, wool | `ductileThreshold`, well above 1 - it bends first | `ductileTransmit`, a good deal - a steel keel rings end to end |
| `structural` | Stone, concrete, earth, everything else | `structuralThreshold`, `1.0` by definition | `structuralTransmit`, the most - a deck carries a shock well |

Being hard to mine and being hard to shatter are unrelated properties, and every number this mod had before
1.7 conflated them. Obsidian is genuinely tough; a glass pane with the same blast resistance would not be.

### What a block costs the shock

A block that **fails** takes its own strength out of the shock and hands the excess on, multiplied by
`passOn`. A block that **holds** takes nothing at all and hands on a fraction of the whole, decided by its
`...Transmit`. Note which way round that is: a wave dies faster through what it destroys than through what
stands. Rubble does not conduct.

A shock below `floor` has stopped. Without that a wave attenuates towards zero without ever arriving, and
spends its time walking through material it can no longer harm.

`backing` is how much of a block's strength comes from being held rather than from what it is made of. A tile
out of its frame is easier to knock out than the same stone in a mountain, and on a hull the blocks with
nothing behind them are the skin - the surface a crash ought to lose first and the one it was losing last. At
the shipped `0.25` a fully surrounded block is at full strength and a lone plate hanging in the air is at
three quarters of it. `0` switches the neighbour count off entirely.

### The windows, and only the windows

"It hit the ground and the glass went out along its whole length" is the most recognisable thing about a
crash of this size, and no wave can produce it. A wave that reached the far end would have eaten every deck
between here and there on the way, because reach and selectivity are the same setting to it.

So the fragile blocks get a pass of their own. It runs `glassReach` blocks - meant to be most of a large
ship, not a neighbourhood of the impact - costs nothing per block it passes through, and breaks only what
shatters. It travels *through material*, so air ends a branch: it follows the decks and bulkheads instead of
leaping across the sky to a greenhouse next door.

`glassScanBudget` bounds what it reads, which is the real cost, and `glassMaxPerImpact` bounds what it
breaks. `glassMaxRuns` is per body per tick and wants to stay small - the fill reaches the whole build from
any contact, so a second run mostly finds windows the first one has already taken.

### If it is too destructive, or not destructive enough

- **Everything comes apart too easily.** Lower `intensityScale`. It is the master dial and nothing else here
  needs touching first.
- **Strong material feels papery.** Raise `structuralThreshold`, or price the material properly with
  `materialOverrides ... resistance=`. A threshold is a multiplier on that number, not a replacement for it.
- **The crash stops at the first wall again.** Raise the `...Transmit` numbers. At `0` a block that holds is
  a dead end, which is exactly the pre-1.7 behaviour.
- **Windows survive at the far end.** Raise `glassReach`, then `glassScanBudget` - a pass that runs out of
  reads stops quietly.
- **It is spending too long in the wave.** Lower `maxScan`, or raise `floor`. Under stress the break ceilings
  no longer bound the walk, because a shock passing through material it cannot break is free.
- **Put it all back.** `stress = false` restores the budgeted wave exactly, and everything in this chapter
  stops applying.

## Collapse: what a build does once it has landed

Everything above spends the energy of the crash, and that gets the crater right and the rest of the build
wrong. A wave spreads out of the point that touched in every direction at once, so what the player watches is
a hull being eaten in rings from one corner; and since a wave too big for one tick is put down and picked up
on the next, the eating carries on long after the thing has stopped moving. A large structure does not do
that. A large structure is held up by its own floor, and when the floor at one end is gone the rest of it
folds into the hole — from that end towards the far one, under its own weight, and over in a second or two.

So a collapse is not an energy model at all. A hard enough landing **arms a failure front** at the contact,
and the front then walks outwards through the build at a fixed number of blocks per tick regardless of what
the crash was carrying. What it does where it passes is take out the floor: the lowest courses of material in
each column, measured along whichever way is down. Nothing is pushed afterwards — the build is simply no
longer standing on anything, and gravity is more convincing than any impulse this mod could apply.

`collapseSpeed` is that pace, in blocks of front per tick. It is a speed rather than a budget on purpose:
what a collapse costs has nothing to do with how fast the front travels, so it can be set by how it should
look. At the default the front has crossed its whole reach in two ticks. `collapseReach` is how far the
failure spreads before the build holds itself up again, and it is deliberately short — a fold is a section
of a ship going down, not the ship. Anything past it is the wave's business.

`collapseMinSpeed` is how hard the build has to have arrived for any of this to happen. It is a separate
number from the wave's `minSpeed` because the two want very different answers: a wave at walking pace chips
whatever it touched, and a collapse at walking pace takes the floor out from under a ship that was being
parked. This is the first setting to raise if a build seems to come apart under its own landing gear.

`collapseBite` is the shape of it, and the shape is the whole point. A column directly under the contact
loses this many courses, tapering to a single course at the rim — so the end that landed drops by three or
four blocks and the far end barely moves, and the build tilts into its own wreckage instead of settling flat.
Where it lands it hits again, which arms the next front, and it comes down one storey at a time the way
buildings do. Set it to `1` and the taper goes with it: the whole footprint drops by one course, evenly,
which is a building being lowered rather than one collapsing.

On a hollow build — and every build is hollow — a course means a course of *material*, not a layer of the
scan. The column is read from below the contact upwards and the rooms in between are stepped over, so
`collapseBite = 3` costs a ship its keel and the two decks above it at the point of impact, and its keel
alone at the bow. `collapseDepth` is how tall that read is and has to clear the tallest room in the build,
or a column whose floor is on the far side of a hold finds nothing and that part of the build does not fail.
`collapseDrop` is how far below the contact it starts, because a hull touches down on whatever hangs lowest
and that is rarely the floor of the columns around it.

`collapseCooldown` is the pause between one front finishing and the same build being given another. A build
is touching what it fell on for the whole time it is falling into it, so without a pause every tick of the
descent would arm a fresh collapse and the build would be gone before it had visibly moved.

#### How big one is

Until 1.9.2 the answer was "the same size every time". A front took the whole of `reach` in every direction
whatever had happened, so a build that clipped a fence post lost a floor thirty blocks across, and a belly
that grazed a treetop shed its decks. That is not a collapse, it is a punishment for touching anything.

A collapse is a build failing under its own weight, and how much of it fails depends on two things: how much
of it was bearing on something, and how hard it arrived. `fit` measures both. The contacts of a landing are
all reported before the front takes its first step, so they are gathered as they come in and the footprint
they cover — `margin` blocks wider, for the contacts that were not reported and the damage that does not stop
exactly where the contact did — is one bound on the front. The other is what the speed earned: `minReach` at
the speed the gate opened at, the whole of `reach` at `fullSpeed`, and a straight line between. **The smaller
of the two wins.**

So a build can land flat and fast across a plateau and lose everything it landed on, or hit one place at
terminal velocity and lose that one place, but nothing gets to lose a floor it was never over. `collapse.minSpeed`
went from `14` to `28` in the same release, which is the other half of it: a hard landing and a crash are
different events, and only one of them takes the floor out.

Set `fit = false` for the pre-1.9.2 behaviour.

This is deliberately crude, and it is crude in one specific way: **down is rounded to whichever of the six
directions is nearest world down** at the moment of the hit, so the whole pass is axis-aligned and a column
is a straight line. A build lands more or less the way it was flying, and one that has rolled far enough for
that answer to be wrong has larger problems than which way its columns run.

However far the front reaches, it cannot take more of the build than the build's own allowance permits —
see [Protection](#protection-how-much-of-a-build-one-crash-may-take), which is what stands between a fold and
a hull peeling off in a chain.

A landing and a ram are told apart by where the build was touched: the front is armed only by a contact
**below the build's own centre of mass**, which is the cheapest thing that separates them. A ship that comes
down on something is hit under its centre; one that scrapes a wall is hit level with it, and one that clips
an arch overhead is hit above it. Only the first has lost the floor it was standing on. A ram into a cliff
still comes apart — that is the wave's job, and the wave has no such scruples — it just does not fold.

Set `collapse = false` for the pre-1.5 behaviour, where the only thing that ever destroys a build is the
energy of the crash.

## Splitting: telling a build that it is now two builds

Sable decides what is still one structure by flood-filling it, and it spends a fixed few hundred steps a tick
on that so the walk never costs anybody a frame. For a build losing a block to a pickaxe that is exactly the
right trade. For a build losing a thousand blocks to a crash it is the wrong one, and the difference is
visible: the connection is severed on the first tick and found on the fortieth, and what is on the screen in
between is **a wreck cut cleanly through the middle, hanging in the air in one piece**, held up by a search
that has not caught up with what happened to it.

Nothing was wrong with the severing. Every removal this mod makes goes through the level, and Sable's own
block-change hook sees all of them. What was wrong was the rate. So a build this mod has damaged is put on a
list, and its flood-fill is run extra times for a while.

`rounds` is how many extra passes a damaged build gets per tick — one pass being what Sable itself runs in a
tick, so the default is a build resolving about twenty-four times sooner. It is a ceiling and not a workload:
the search is self-limiting, and the moment it has its answer every further round returns immediately. A build
that has come apart pays for the finding once and then costs nothing until it is hit again.

`ticks` is how long a build stays on that list after the last block it lost, because a wreck keeps coming
apart for a while after the landing and each new severance wants finding as promptly as the first. `millis` is
the wall-clock ceiling on all of it per level per tick, whatever the round count says.

Set `resolve = false` and separation still happens, on Sable's own schedule, which on a wreck of any size is
tens of seconds after the fact.

## Severance: walking the blocks and asking outright

Splitting above assumes Sable's connectivity search is right and merely slow. For a build losing a block to a
pickaxe it is. After a crash it is not, and no amount of hurrying it helps, because what it needs is not more
time.

The search is not a flood fill from scratch. It is an incremental distance field: every block carries how far
it is from the root of its region, and when a block is removed its neighbours are compared against the heat
they were left with. A neighbour becomes the root of a new region only when no other neighbour of it is nearer
the old root - a deliberately conservative test, and a correct one when blocks go one at a time, because then
the heat around the hole is still true. On a hull losing a thousand blocks in four ticks it is not. The field
describes a build that no longer exists, every candidate root fails the test against a neighbour that is
itself already gone, and the search **finishes**, with `splitComplete` set and nothing found. Not late. Wrong.
Running it again returns the same answer, because it is the same field. That is the wreck cut clean in half
with daylight through the cut, flying in formation with itself.

So the question is asked here instead, the one way that cannot be wrong: read the build's blocks into a grid
and see what is reachable from what. It costs one pass over the bounding box and one byte per block of it,
it is exact, and it has no memory to be stale. A build that comes back in more than one piece is handed to
Sable's own assembly, piece by piece, exactly as Sable's own split would have handed it: the largest piece
keeps the build it was already in, the split listeners are told first, pose and velocity carry over, and a
piece that turns out to weigh nothing is destroyed rather than left as a ghost. Two blocks touching along an
edge count as joined, because that is Sable's own rule and keeping it means this pass only ever forces
separations Sable would agree with. `diagonals = false` is stricter than Sable and will part builds their
makers meant to hold.

The other half is the opposite problem: builds that have not come apart and have no business still being in
one piece. Connectivity has nothing to say about a hull broken almost through and bridged by one deck plank,
because touching is a yes or a no and three blocks touch as firmly as three hundred. So the walk is kept as
layers - every block one step further from the far end of the build than the last - which makes each layer a
cross-section, and removing a whole one provably parts everything before it from everything after it. A layer
wider than `neck` is the body of the build and is dismissed on its count alone, which is what keeps this
cheap. What is left is priced: the layer's summed resistance times `carry` is how many blocks it can hold,
against the block count of the lighter side hanging off it. The worst-overloaded joint goes, ties broken
toward the thinner one, and on a later pass the separation half finds the two halves and makes them two
builds.

`carry` is the dial that matters. Resistance is the same measure of strength every break in this mod is
priced against, so a joint is thin by what it is made of and not by how many blocks it has: at the default
`40`, two oak planks hold about a hundred and forty blocks and four of obsidian hold five thousand. Raise it
and thinner joints stand; lower it and builds come apart at every waist.

`minSide` is what stops a corner being trimmed off an intact build and called a cut in half, `volume` is the
largest build looked at at all, `pieces` is how many new builds one pass may make, and `interval` is how often
the same build is asked again, because a wreck keeps coming apart for a while after the landing. `millis` is
the wall-clock ceiling on all of it per level per tick.

Set `separate = false` and `ligament = false` for the pre-1.9.3 behaviour, where whether a wreck is one build
or two is entirely Sable's answer to give.

## Falling costs nothing

A hull with two hundred blocks of air under it has nothing for any pass in this mod to do. Crushing finds no
ground, carving finds no terrain, the soft sweep finds no grass. All of them find that out separately, and
all of them find it out again next tick.

So it is asked once, from the heightmap: if the top of every column within reach is below the hull's floor,
there is nothing under it - and nothing above it either, since by definition nothing is above a column's top.
The hull is then left entirely alone for as long as that reading can be trusted, which is until it could have
crossed the drop below or drifted off the edge of what was read.

Those two bounds are wildly unequal, and until 1.9.4 the wrong one was binding. A hull falling at thirty
metres a second drifts barely at all, so the drop was worth five seconds and the four-block margin was worth
one - and the margin won, so a fall of two hundred blocks paid for five readings where one would have done.
Now the margin is chosen against the fall: wide enough that the drop below runs out first, and no wider than
it is worth, which is half the geometric mean of the footprint. Past that point the reading costs more, as
the square of the margin, than the time it buys, which is linear in it. So a ship gets a wide reading and a
cart keeps the narrow one it always had.

A hull that is falling is also capped separately from one that is hovering (`maxFallQuietTicks` against
`maxQuietTicks`), because what the cap guards against is ground appearing where the reading said there was
none - which is a real risk under a build somebody is living on and very nearly none under one going past at
terminal velocity.

Nothing about what breaks changes. It is the same answer, arrived at three to five times more cheaply on
anything in a long fall. Set `freeFallQuiet = false` for the pre-1.9.4 behaviour.

## Anchoring: a wreck that stays in the world long enough to finish coming apart

Sable will only tick a build in chunks the server is ticking blocks in. That is simulation distance, not
render distance, and the two are set separately - so a build can be well inside what you can see and outside
what the server is willing to run.

A build that crosses that line is not paused. It is serialised out whole into a holding chunk, taken out of
the container, and its plot torn down; from the outside the ship is simply gone, and the server goes on
sending movement packets for a thing the client no longer has. When a chunk under it comes back, all of it is
read in again in a single call, on the server thread, inside the level tick. For a large build that is a stall
of several seconds, ending with the ship reappearing exactly where it should have been all along - which is
what a hull vanishing for five seconds and popping back looks like from the inside.

For a parked airship nobody is looking at, that is the right trade and this should stay out of it. In the ten
seconds after a crash it is the wrong one twice over. Those are the ten seconds this mod spends removing
blocks, walking connectivity and assembling halves, and a wreck written out in the middle of that comes back
with every bit of it still to do, having paid a stall to get there. They are also when a build is likeliest to
cross the line at all, because it is falling away from whoever is watching it.

So a build this mod has damaged keeps a chunk ticket on the columns under it, at block-ticking level, which is
the level Sable's own test asks about. It is refreshed for as long as the build keeps losing blocks and for
`ticks` afterwards, and then it is not. The ticket carries a lifespan of its own, shorter than the window it
serves, so nothing here can leave a chunk held open behind it - through a crash, a config reload, or a bug in
this file. Stop refreshing and it is gone in two seconds regardless.

Only ground that is already loaded is held. This matters more than it sounds like it should: a chunk ticket at
block-ticking level does not merely keep a chunk that is there, it generates one that is not - that is all
`/forceload` is. A wreck falling out over unvisited terrain would drag in every column it passed over, and 1.9.5
did exactly that. The generation costs more than the stall it was avoiding, and it costs it on the server
thread, so ticket levels propagate slower, so Sable's test fails sooner, so builds vanish at a *shorter*
distance than with no anchoring at all. Since 1.9.6 every column is checked first and skipped if it is not
already resident. A wreck over unloaded ground is Sable's problem again, handled the way it always was.

A build wider than `chunks` columns is left alone entirely, on the grounds that holding that much of the world
open costs more than the stall it would have saved, and no more than `builds` of them are anchored at once -
past that the ones still losing blocks are kept and the rest let go. Set `hold = false` for the pre-1.9.5
behaviour, vanishing included.

## Load bearing: what holds the world up

Everything above prices a block against the thing that hit it. That is the whole of an impact and none of a
structure.

Stand a platform on two pillars and drop a ship on it. Every pass in this mod does its job: the deck under
the ship breaks, the shock runs out through what was attached to it, the debris falls. The pillars are never
touched, so nothing ever happens to them - and the ends of the deck they were holding are not touched either,
so they stay exactly where they were, hanging in the air on nothing at all, like a plank left on a branch.
The ship was heavy enough to punch a hole in the middle of the structure and not heavy enough to matter to
the legs, which is not a thing weight does.

So there is a second question, and it is asked about the world rather than about the impact: **where does the
weight go**.

### Routing, not spreading

The crush pass already asks something that sounds similar - how hard is this hull pressing on the blocks it
is touching - and answers it by pushing that pressure down and out a few blocks and letting it fade. That is
right for the ground under a landing, where the answer really is a bulb of pressure that peters out, and
useless for a structure, where the answer is a pillar forty blocks away.

This pass routes instead. Blocks around a disturbance are pulled into a box; every solid block in it is given
a route to whatever is holding it up, and its weight is added along that route. Stacking straight up costs
nothing - a column stands on itself, however tall. Hanging sideways or downwards costs a step, and `span` is
how many of those a load may take before it has run out of structure to travel through.

Two answers come back, and both are failures:

- **Overloaded.** The block is carrying more than its material can carry. This is the pillar buckling under
  the ship that was put on the deck above it.
- **Unsupported.** The routing never reached it at all: there is no path from this block to anything holding
  it up. This is the plank on the branch.

Everything that failed gives way, the box is solved again with it gone, and the round repeats. That is the
part that matters most: the load that was going through the pillar has to leave some other way, and usually
there is no other way, so the deck comes down in the same breath as the legs rather than as a drizzle of one
block a second for the next minute. `rounds` is how far that is allowed to run in a single visit.

### Weight at rest

None of this needs an impact. The load a build is pressing onto the world is measured by the crush pass every
tick a build is touching anything at all, moving or parked, and it is that number - kilogrammes on a block,
the same units Sable weighs builds in - that is fed in at the contact. So a ship set down gently on a roof
loads the walls under it exactly as hard as its own mass says, and if the walls are not up to it they come
down without the ship ever having hit anything.

Set `rest = false` to have only destruction disturb anything, and a build resting on a structure weigh
nothing to it.

### What it costs, and what it will not touch

Work is queued by region - a sixteen-block cube - and not by block, because a crater is thousands of breaks
in a handful of cubes and solving the cube once is the entire point. A region is looked at again only if the
last look broke something in it, so a wreck that has settled stops costing anything. A block that falls
queues its own region, which is how a failure climbs out of the cube it started in and walks up a tower.

The box is the region plus a margin, and the margin is deliberately lopsided: `drop` reaches far below,
because what holds a structure up is underneath it and the legs are the whole point, while `rise` is small,
because anything overhead will queue its own region the moment it starts moving. Whatever is still standing
against the wall of the box is anchored there rather than dropped - the structure carries on outside and the
box cannot see how, so the conservative reading is the only honest one. The same goes for a chunk that is not
loaded: unknown is treated as ground.

It runs on terrain. A build pressing on another build is Rapier's problem and Rapier is already solving it;
nothing here ever names a coordinate out in the plot grid.

`pressureScale` is the calibration. It is set so that solid rock never fails under rock - the deepest column
the box can see weighs about forty, and stone carries near five hundred - while a build putting thousands of
kilogrammes through two pillars takes them out at once. Lower it and structures fold under less; raise it and
only the unsupported ones ever come down. Set `bearing = false` for the pre-1.9 behaviour, where terrain
breaks only where something touched it and the leftovers hang where they were.

## Protection: how much of a build one crash may take

Everything above decides whether a given block should break. Nothing decided how much of one structure may
break altogether, and the answer turned out to be all of it.

Both a wave and a collapse walk outwards through whatever is touching, and on a hollow hull that is the skin.
The bottom of a ship is a single course of material, so a pass that takes the lowest course of every column
takes the whole bottom of the ship — and each place it lands afterwards starts the next one. What the player
sees is not a crash but a corrosion: the hull peeling away in a chain from wherever it was touched, seconds
after a landing that should only have dented it.

So a build has a damage budget, and every path in this mod that can destroy one of its blocks draws on the
same allowance. One crash is one crash, however many contacts, waves and fronts it sets off.

```toml
[protect]
	protect = true
	maxPerTick = 256
	maxPerImpact = 3000
	restTicks = 40
```

**`maxPerImpact`** is the size of a wreck: the most blocks one build may lose to one crash, however long the
crash goes on. A ship that comes down hard loses a crater and the structure around it and then stops, instead
of continuing until there is nothing left to walk through. Raise it for catastrophes, lower it for dents.

**`maxPerTick`** is the pace of one. Lower it and the same build comes apart over more ticks without losing
any less in the end, which is both easier to watch and much easier on the tick. Nothing is skipped: a wave
stopped here picks up where it was, and a collapse front resumes at the column it stopped on rather than
redoing the ring.

**`restTicks`** is how long a build has to be left alone before the crash it was in counts as over and its
allowance is handed back. Too short and one long grinding crash is scored as several, and the build is eaten
anyway; too long and a ship that crashed, was repaired and flew into a cliff a minute later is still paying
for the first one.

There is a second reason this exists. Sable splits a sub-level when destroying blocks leaves it in
disconnected pieces, and it queues that split rather than running it at once; annihilating what is left
before the split runs takes the server down with `Sub-level assembly attempted inside plot of already removed
sub-level`. Nothing on this side can make that safe outright, but a build that is never taken apart faster
than a few hundred blocks a tick gives the split the time it needs, and a build Sable has already removed is
noticed and left alone from that moment.

`protect = false` is the pre-1.6 behaviour, where a single landing could take a whole hull apart.

## Debris: what flies, and where it lands

A crash that leaves a clean hole does not look like a crash. This chapter is about the other half of an
impact: the blocks that come *out* of the hole, how many of them there are, how far they go, and whether
they are still there afterwards.

Every setting named here lives in the `[debris]` section of the config file.

```toml
[debris]
	mode = "FALL"
	scatterChance = 0.25
	contraptionScatterChance = 0.3
	settle = true
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
	maxSettlePerTick = 256
```

### What becomes of a broken block

A broken block has three possible ends, asked in this order: it is **thrown** as a piece of debris, it
**settles** somewhere near where it broke, or it is gone.

Only the first of those costs anything real. Thrown debris is a falling block entity — it ticks, it falls, it
writes a block back, and every client in range is told about it — so it is rationed hard and only ever a few
dozen blocks a tick. Settling is a block change and nothing else, which is why nearly everything can do it.

**`mode`** — what a piece that is *not* simply gone actually does, and the setting that decides whether a
crash reads as a structure failing or as a bomb going off under it.

| `mode` | |
|---|---|
| `FALL` | It falls from where it stood, like sand. Nothing is thrown. A wreck comes down as a wreck, and a piece is on the ground in about the time it takes to fall the height it broke from. This is what a building does, and it is the default. |
| `THROW` | It is thrown clear of the impact, which is how this mod behaved before 1.6. Right for a cannon shot and for a hull ploughing through a hillside; wrong for a hull folding, where several hundred pieces all leaving one point at once is an eruption. |
| `SETTLE` | No falling block at all — the block is written straight back down onto the heap. One block change instead of an entity that has to fall, land, write a block anyway and be tracked by every client in range. Much the cheapest of the three, and much the dullest. |

Under `FALL` the two chances below still decide *which* blocks get a falling block of their own rather than
being heaped, and `maxScatterPerTick` still caps them. What changes is only that a piece is let go rather
than launched.

**`scatterChance`** — the fraction of broken **terrain** blocks that are thrown. `0` turns terrain debris off.

**`contraptionScatterChance`** — the same fraction for a **contraption's own** blocks, and the reason the two
are separate settings. They are not the same wish. A hillside that keeps its rubble is scenery, and there is
an enormous amount of it; a ship shedding its hull is the thing the player is actually watching. Both are
kept well under `1` all the same: every block that flies is a block leaving the wreck, and a wreck that
throws all of itself is a fountain rather than a crash. What is wanted is a few pieces turning through the
air over a heap that is mostly still there.

**`settle`** — the switch over the whole of it. Off, a broken block that did not fly is simply gone, which
is how this mod behaved before 1.3.

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

**`settleSpread`** — how wide the heap of a *contraption's* settled blocks is, as a jitter around where the
block itself was. Too small and a ship comes down as a tower of itself; too large and the wreck is a thin
film over the landscape rather than a pile.

Before 1.6 that disc was drawn around the *contact point* rather than around each block, because a hull's
blocks live out in the plotgrid and only the crash appeared to have a place in the world. It does not: the
build's pose puts every one of its blocks exactly where the player is looking at it, and that is what is used
now. What the old reading produced was the fountain and the waterfall — one contact breaks hundreds of blocks
all over a hull, and every piece of that hull was created at the same spot, so an entire airship arrived as a
puddle under one corner of itself.

**`maxSettlePerTick`** — the cap on blocks all settling together may write back per tick, and the number that
was missing. `settleShare` sends the great majority of a crash down this path and nothing bounded it: a wreck
shedding four thousand blocks in a tick wrote four thousand blocks back, each with a neighbour update behind
it, and the tick that did it took over a second. Blocks refused past the cap are gone rather than heaped,
which costs a thinner pile and buys back the frame.

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
| `mode = "SETTLE"` | The largest single saving here. No debris entity is created at all, so nothing ticks, falls or is tracked; a crash becomes block changes and nothing else. |
| `maxSettlePerTick` down | The other one. This is where the bulk of a crash goes, and until 1.6 it was unbounded. |
| `maxScatterPerTick` down | The hard limit on thrown pieces. Everything else only changes what is competing for these slots. |
| `contraptionScatterChance` down | Fewer hull pieces flying. What they were going to cost is not saved by `settleShare` — a settled block is just a block change. |
| `landingSearch = 0` | Drops the search entirely. Wreckage goes back to disappearing when it lands somewhere occupied. |
| `dropWhenLost = false` | No items from failed landings. Worth it on a server where a crash site turns into a carpet of drops. |
| `lifetimeTicks` down | Fewer entities alive at once when debris is being thrown a long way. |

## Reference

### Impact

| Option | Default | |
|---|---|---|
| `enabled` | `true` | The master switch. `false` and this mod does nothing at all, in this world only. |
| `impactStrength` | `1.0` | Master multiplier over the whole force model. |
| `minImpactSpeed` | `6.0` | Closing speed (m/s) below which nothing is ever broken. A noise floor: below it a contraption would dig its own grave as it settles. |
| `fragileTrigger` | `4.0` | Speed (m/s) above which a fragile block is handed back to Sable to shatter on its own terms. |
| `breakContraptionBlocks` | `true` | Whether a contraption loses its own blocks when it rams terrain harder than they are. |
| `contraptionBlockToughness` | `3.0` | Multiplier on a contraption block's strength when weighed against terrain. |
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
| `maxHullWaves` | `1` | How many waves one contraption may open in a tick, however many contacts it reports. What stops a crash from eating the build outwards in rings. Terrain is not capped. |
| `fracture` | `true` | Whether a crash also splits a build along cracks. `false` is waves only. |
| `fractureShare` | `0.85` | The share of a crash spent cutting the build into pieces rather than eating a hole in it. |
| `fractureCount` | `2` | How many cracks one crash may open, one per contact, up to this many per build per tick. |
| `fractureFalloff` | `0.995` | What a crack keeps of its purchasing power per block travelled. Near `1`: a crack is no use unless it crosses the build. |
| `fractureWander` | `2` | How far a crack may drift off its plane, in blocks. The plane block is taken as well as the wandered one, so the cut widens rather than moves. `0` cuts like a saw and is the cheapest. |
| `fractureGap` | `6` | How many blocks of nothing a crack may cross. `0` stops it at the first cavity, which on a hull means it stops immediately. |
| `fractureCost` | `0.05` | What a crack pays for a block, against what a wave pays for the same one. Lower reaches further; `1.0` turns cracking off in practice. |
| `fractureAim` | `true` | Whether the cut is made across the way the build actually runs, rather than across whichever axis came up next. `false` is the pre-1.9.1 rotation, which cuts a mast lengthwise and bites a hole in a plate. |
| `fractureScan` | `48` | How far the crack looks along each axis to work out which way the build runs, in blocks. Three lines of reads per cut. |
| `fractureMinRun` | `3` | How far the build has to run along an axis before a cut across it is a cut rather than a bite. `1` excludes nothing and brings the bite back. |
| `fractureFloor` | `128` | Blocks a crack may take before its purse is consulted, so a cut worth starting is worth finishing. `0` is the pre-1.9.1 behaviour. Everything under `[protect]` still applies. |
| `hullShare` | `0.25` | The share of a shock a contraption passes on through itself, against what terrain passes on. What its frames and joints are for. `1.0` is the pre-1.9.2 behaviour, where a hull was a hillside. |
| `hullMinSpeed` | `20.0` | Speed (m/s) below which a contraption cracks and loses its glass but nothing spreads. What a build is allowed to shrug off. `0` is the pre-1.9.2 behaviour. |
| `fractureNeck` | `12` | How far along its axis a crack may travel to find a weaker cross-section, in blocks. `0` cuts through the contact, which is 1.9.1. |
| `fractureNeckSpan` | `6` | How wide a window each candidate section is weighed across, either way. Wider is truer and costs its square in block reads. |
| `fractureNeckBias` | `0.08` | What a candidate pays per block of distance from the contact, as a share of what cutting at the contact would have cost. Higher keeps cracks at the impact; `0` hunts for the joint however far off it is. |
| `cost` | `1.0` | What one block's resistance costs the budget. Higher makes material matter more. |
| `falloff` | `0.98` | The share of its purchasing power a wave keeps per block travelled. What bounds its reach. |
| `maxBlocksPerImpact` | `8192` | Ceiling on blocks one shock may break. |
| `maxBlocksPerTick` | `6144` | Ceiling on blocks all shocks together may break per level per tick. |
| `maxTicks` | `40` | How long a wave too big for one tick may keep going before what is left of it is dropped. What stands between a big crash and a wreck that sheds blocks for a minute. |
| `oneCrash` | `true` | Whether a build's kinetic energy is drawn once per crash rather than once per tick. `false` refills it every tick the build is still moving, which is a wreck that keeps detonating as it slides. Terrain is refilled per tick either way. |

### Stress

Section `[stress]`. See [Stress](#stress-strength-against-strength-not-price-against-purse).

| Option | Default | |
|---|---|---|
| `stress` | `true` | Whether shocks are resolved by strength rather than by budget. `false` is the pre-1.7 wave exactly, and everything below stops applying. |
| `intensityScale` | `0.02` | Intensity per kilojoule the crash is carrying. The master dial for how violent a crash is. |
| `brittleThreshold` | `0.15` | What a brittle block's strength is worth against a shock, as a multiple of its resistance. |
| `ductileThreshold` | `2.0` | The same for material that bends before it breaks. |
| `structuralThreshold` | `1.0` | The same for everything else, left at `1` so the other two read as departures from it. |
| `brittleTransmit` | `0.15` | What gets through a brittle block that held. Low: glass does not conduct a shock. |
| `ductileTransmit` | `0.55` | The same through metal and wood. |
| `structuralTransmit` | `0.8` | The same through stone. High, so a deck too strong to break is a thing the crash goes through rather than the end of it. |
| `passOn` | `0.6` | What is left of the excess after a block fails. Below `1` because breaking a block is where the energy goes. |
| `backing` | `0.25` | How much of a block's strength comes from its neighbours rather than its material. `0` disables the neighbour count. |
| `floor` | `0.05` | The intensity below which a shock has stopped. |
| `maxScan` | `24000` | Blocks one wave may look at over its whole life. Under stress this rather than the break ceilings is what bounds the walk. |
| `glass` | `true` | Whether the fragile blocks get their own long pass. |
| `glassReach` | `64` | How far through a build that pass runs, in blocks. |
| `glassScanBudget` | `20000` | Blocks one pass may read before giving up. The real cost of it. |
| `glassMaxPerImpact` | `512` | The most fragile blocks one pass may take out. |
| `glassMaxRuns` | `2` | How many passes one body may start per tick. Wants to stay small. |

### Optimisation

Section `[optimize]`. Neither of these changes what breaks.

| Option | Default | |
|---|---|---|
| `batchBounds` | `true` | Rebuild Sable's plot bounding boxes once at the end of a break pass instead of once per block removed. The single largest saving in this mod, by a very wide margin. |
| `cacheChunks` | `true` | Let the passes that walk through a build remember the chunk they were last in. |

### Compatibility

Section `[compat]`. Guards this mod puts on Sable, against states a crash reaches and Sable does not expect.
None of them change what breaks. They are switches because each one is a check bolted onto somebody else's
code, and a Sable release that fixes the same thing properly should be able to have this one taken back out.

| Option | Default | |
|---|---|---|
| `guardDeadBodyReads` | `true` | Whether Sable's autosave may ask a destroyed rigid body how fast it is going. Same fault as the row below, at the other end: a build this mod empties has its physics body destroyed at once but stays in the container's list until the sweep, and an autosave landing in that window serialises it, velocities included. Rapier answers a read on a destroyed body by throwing, out of `ServerLevel.save`. On, a destroyed body reports standing still, which is both true and what would have been written had the sweep gone first. `false` restores the stock behaviour, crash included. |
| `guardRemovedSplits` | `true` | Whether a sub-level Sable has already removed is allowed to go on splitting itself. Sable marks a build removed the moment its last mass goes, but only collects removed builds once every build has ticked; a build this mod empties is emptied after that sweep has run for the tick, so the dead build gets one more tick of its own, finishes its connectivity check and tries to assemble what it found inside a plot that no longer exists. Sable answers that by throwing, on the server thread, which ends the world. `false` restores the stock behaviour, crash included. |

### Anchoring

Section `[anchor]`. See [Anchoring](#anchoring-a-wreck-that-stays-in-the-world-long-enough-to-finish-coming-apart).

| Option | Default | |
|---|---|---|
| `hold` | `true` | Whether a build this mod has damaged keeps the chunks under it ticking until it has settled. `false` is the pre-1.9.5 behaviour. |
| `ticks` | `200` | How long a build stays anchored after the last block it lost. |
| `chunks` | `64` | The most chunk columns one build may hold. A wider build is left alone. |
| `builds` | `16` | The most builds one dimension may anchor at once. Over that, the ones damaged most recently are kept. |

### Collapse

Section `[collapse]`. See [Collapse](#collapse-what-a-build-does-once-it-has-landed).

| Option | Default | |
|---|---|---|
| `collapse` | `true` | Whether a landed build folds under its own weight at all. `false` is the pre-1.5 behaviour. |
| `speed` | `8` | How far the failure front travels through the build per tick, in blocks. The pace of the whole thing. |
| `reach` | `16` | How far from the contact the failure spreads, and what the taper is measured against. A fold is a section of a ship going down, not the ship. |
| `bite` | `3` | Courses of floor a column loses directly under the contact, tapering to one at `reach`. This is the fold; `1` removes it. |
| `depth` | `24` | How tall a column is searched for that material. Must clear the tallest room in the build. The largest single cost here. |
| `drop` | `4` | How far below the contact the search starts, because a hull touches down on whatever hangs lowest. |
| `cooldown` | `10` | Ticks before the same build may be given another front. What makes the storeys separate events. |
| `maxBlocksPerTick` | `2048` | Ceiling on blocks all collapses together may drop per level per tick. What this stops is not resumed later. |
| `minSpeed` | `28.0` | Speed (m/s) below which a landing does not fold the build at all. Separate from `shock.minSpeed`, and the difference between a hard landing and a crash. |
| `fit` | `true` | Whether the front is cut to the footprint that actually landed and the speed it landed at, rather than always taking the whole of `reach`. `false` is the pre-1.9.2 behaviour. |
| `margin` | `2` | How far past the edge of what was touched a fitted front may run, in blocks. The difference between the footprint and the crater. |
| `minReach` | `2` | The smallest a fitted front may be cut to. A collapse that reaches nothing did not happen. |
| `fullSpeed` | `30.0` | Speed (m/s) at which a fitted front is allowed the whole of `reach`. Between this and `minSpeed` it grows from `minReach`. |

### Splitting

Section `[split]`. See [Splitting](#splitting-telling-a-build-that-it-is-now-two-builds).

| Option | Default | |
|---|---|---|
| `resolve` | `true` | Whether a build this mod has broken is walked harder until it knows what it is. `false` leaves separation on Sable's own schedule, which on a wreck is tens of seconds. |
| `rounds` | `24` | Extra passes of the connectivity search a damaged build gets per tick. A ceiling, not a workload: a finished search costs nothing. |
| `ticks` | `200` | How long a build stays on the hurried list after the last block it lost. |
| `millis` | `3.0` | Wall-clock ceiling on all of it per level per tick. |

### Severance

Section `[sever]`. See [Severance](#severance-walking-the-blocks-and-asking-outright).

| Option | Default | |
|---|---|---|
| `separate` | `true` | Whether a build found to be in more than one piece is actually taken apart into that many builds. `false` leaves it to Sable, which after a crash means a wreck that stays whole for as long as it exists. |
| `ligament` | `true` | Whether a joint too thin to carry what hangs off it is broken. The difference between a hull that is cracked and a hull that has come in two. |
| `diagonals` | `true` | Whether two blocks touching along an edge alone count as joined. `true` is Sable's own rule. `false` is stricter than Sable. |
| `interval` | `20` | Ticks between two passes over the same build. |
| `volume` | `1048576` | The largest build, as the volume of its bounding box, that is looked at at all. Costs a byte per block of it. |
| `pieces` | `4` | How many pieces of one build may become builds of their own in one pass. The largest piece always keeps the original. |
| `minSide` | `24` | The fewest blocks a side of a cut may have and still count as a side. Below it a cut is a corner being trimmed off. |
| `neck` | `24` | The most blocks a cross-section may have and still be treated as a joint rather than as the body of the build. |
| `carry` | `40.0` | How many blocks one point of a joint's resistance holds up. Two oak planks hold about a hundred and forty. |
| `millis` | `2.0` | Wall-clock ceiling on all of it per level per tick. |

### Load bearing

Section `[bearing]`. See [Load bearing](#load-bearing-what-holds-the-world-up).

| Option | Default | |
|---|---|---|
| `bearing` | `true` | Whether the world carries weight at all. `false` is the pre-1.9 behaviour: terrain breaks only where something touched it, and the leftovers hang where they were. |
| `blockWeight` | `1.0` | What one block weighs, in the same units a build presses with. Sable weighs a plain block at 1 kg, so `1.0` is the honest reading. |
| `pressureScale` | `400.0` | Load a block carries per point of its resistance. Set so rock never fails under rock while two pillars fail under a ship. The main dial. |
| `span` | `24` | How many sideways or downward steps a load may take on its way to the ground. Up is free. Past the last one the block is unsupported and falls. |
| `hanging` | `true` | Whether blocks with no route to the ground at all fall. `false` keeps only the overload check, which is the halfway house. |
| `rest` | `true` | Whether a build's weight loads the world when nothing is moving. `false` makes a parked build weightless to what it is parked on. |
| `margin` | `4` | How far outside the region the box reaches sideways. Keeps the anchored wall of the box away from the part being judged. |
| `drop` | `20` | How far below the region the box reaches, which is how far down it can see the legs. The deepest margin on purpose. |
| `rise` | `8` | How far above the region the box reaches. Small, because what is overhead queues its own region as soon as it moves. |
| `rounds` | `8` | How many times one box may break something and be solved again in a single visit. The difference between a collapse and a drizzle. |
| `interval` | `4` | Ticks between solves, at the closest. The cost is the box, not the damage, so this is the main dial for what the pass costs. |
| `regionsPerTick` | `2` | How many regions one solve works through before leaving the rest for later. |
| `maxRegions` | `96` | How many regions may be waiting at once. Past this a disturbance is dropped rather than queued. |
| `maxPerTick` | `384` | How many blocks the whole pass may drop in one tick, across every region it visits. |
| `fallSpeed` | `0.0` | How hard a block that lost its support is thrown. Zero lets it drop and heap where it stood. |

### Protection

Section `[protect]`. See [Protection](#protection-how-much-of-a-build-one-crash-may-take).

| Option | Default | |
|---|---|---|
| `protect` | `true` | Whether builds have a damage allowance at all. `false` is the pre-1.6 behaviour, where one landing could take a whole hull apart. |
| `maxPerTick` | `256` | The most blocks one build may lose in one tick, to everything this mod does together. The pace of a wreck. |
| `maxPerImpact` | `3000` | The most blocks one build may lose to one crash. The size of a wreck. |
| `restTicks` | `40` | How long a build must be left alone before the crash counts as over and its allowance is handed back. |

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
| `softBreakContact` | `true` | Whether a block that is breaking anyway stops pushing back. Blocks are removed after the physics step, so without this the rest of the step is spent bouncing the build off a wall that is already gone — the hop a hull makes settling onto ground it is grinding through. What it would have been pushed back with is taken as drag instead. |
| `breakDragMass` | `2.0` | Mass (kg) a contraption must drag up to its own speed per point of resistance of every block punched clean through. `0` is free digging. |
| `breakDragMax` | `0.25` | The largest share of its speed a contraption may lose to breaking blocks in one tick. `1` restores dead stops. |
| `rebound` | `0.0` | How much of the speed a build has picked up *away* from what it just broke it keeps. `0` takes all of it, so a crash presses down under its own weight instead of hopping back off the impact. `1.0` restores the old behaviour. |
| `reboundSpin` | `0.5` | How much of its spin a build keeps on a tick it broke something. The bounce comes off whichever corner went deepest, so what it mostly buys is rotation — this is what stops a hull ending up on its side. `1.0` restores the old behaviour. |

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
| `mode` | `"FALL"` | What a broken block does: `FALL` lets it drop from where it stood, `THROW` flings it clear of the impact (pre-1.6 behaviour), `SETTLE` skips the entity entirely and writes it straight back down. |
| `scatterChance` | `0.25` | Fraction of broken **terrain** blocks that fly off as debris rather than simply vanishing. |
| `contraptionScatterChance` | `0.3` | The same for a **contraption's own** blocks. |
| `settle` | `true` | Whether blocks that did not fly are put back down near where they broke instead of deleted. `false` is the pre-1.3 behaviour. |
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
| `maxSettlePerTick` | `256` | Cap on blocks all settling together may write back per level per tick. Where the bulk of a crash goes, and what bounds its cost. |

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
| `freeFallQuiet` | `true` | Whether that reading is sized against the fall rather than fixed at four blocks. See [Falling costs nothing](#falling-costs-nothing). `false` is the pre-1.9.4 behaviour. |
| `quietMargin` | `24` | The widest that reading may be taken, in blocks to either side of the hull. A ceiling, not a target. |
| `maxFallQuietTicks` | `100` | The cap on the window for a hull that is falling, as against one hovering or parked. |
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

→ `create_aeronautics_impact-1.8.0-fast.jar`, listed as *Create Aeronautics Impact (Fast)*. Same mod id and
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
