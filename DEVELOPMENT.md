# Development

Orientation for someone arriving at this codebase. [`README.md`](README.md) is for people who install the
mod; [`AERO_IMPACT_DESIGN.md`](AERO_IMPACT_DESIGN.md) is the design document the model was built from. This
file is about where things are and why they are shaped the way they are.

Every class carries javadoc explaining its own job. This is the map, not the manual.

## What the mod actually is

Sable simulates physics bodies. Its blocks collide with the world, but the world does not respond: nothing
in the terrain has any reason to break. This mod supplies the response — it decides, per contact, which of
the two blocks gives way, and then applies that decision after the physics step has returned.

Everything below follows from three facts:

1. **The mod has no update loop of its own.** It runs inside things the game and Sable already call.
2. **Most of what it is asked is asked thousands of times per tick.** The hot paths are written around
   deciding *not* to look.
3. **Nothing may be written to the world from inside a physics step.** Setting a block re-bakes colliders
   through the same native library that is mid-step, which hangs the server thread.

## The three entry points

Nothing here starts anything. `CreateAeronauticsImpact` registers the config and some listeners, and that is
the whole of the bootstrap. The work arrives from three directions:

| Entry point | Called by | When | Runs on |
|---|---|---|---|
| `ImpactCallback.sable$onCollision` | Sable's solver, via `BlockMixin` | every block-vs-body contact | server thread, **inside the physics step** |
| `ImpactCallback.needsOwnVoxel` | Sable's mesher, via `VoxelNeighborhoodStateMixin` | every block of every collider remesh | **whichever thread Sable meshes on** |
| `HullSweeper.onLevelTick` / `PendingBreaks.onLevelTick` | NeoForge `LevelTickEvent.Post` | once per level per tick | server thread, between steps |

The first is the only one that sees a real collision. The second is a performance hook and never breaks
anything. The third is where every write to the world happens.

## Data flow of one broken block

```
Sable solver step
  └─ BlockMixin.sable$getCallback  ──▶ ImpactCallback (claimed for every block in the game)
       └─ ImpactCallback.collide
            ├─ BlockProfile.of(state)      what each side is made of  (cached per BlockState)
            ├─ Backing.of(level, pos)      what is holding the terrain up
            ├─ ContactTracker              how many blocks this hull has on the ground
            └─ ImpactResolver.victim(...)  ──▶ which side loses
                 └─ PendingBreaks.queue / .wear / .drag        (written down, not applied)

… step returns …

LevelTickEvent.Post
  ├─ PendingBreaks.onLevelTick
  │    ├─ CrackTracker.hit / .wear      accumulated damage; may say "not yet"
  │    ├─ BlockScatter.shatter          the block finally leaves the world
  │    ├─ CrackTracker.spall            neighbours take a fraction
  │    └─ PendingBreaks.brake           the hull pays for it in speed
  └─ HullSweeper.onLevelTick            everything the solver never reported
```

The gap in the middle is the important part. A contact only ever writes into `PendingBreaks`; the tick
drains it. That is what keeps the block standing for the rest of the step, so a hull that did not earn a free
pass is stopped by it exactly once instead of finding the ground gone mid-step.

## File map

`src/main/java/org/restor/create_aeronautics_impact/`

**Decision model — no game types, unit tested**

| File | |
|---|---|
| `ImpactResolver.java` | The whole model as arithmetic: break speeds, which side loses, mass factors, wear, drag, backing. |
| `SweepDetail.java` | The detail ladder the sweeper coarsens itself down: sample spacing and clip width per rung. |

**Reading the world**

| File | |
|---|---|
| `BlockProfile.java` | Everything the mod needs to know about a block state, derived once and cached on the state. |
| `MaterialOverrides.java` | The `materialOverrides` config table, parsed, and the rule lookup for one state. |
| `Backing.java` | How much terrain stands behind and beside a struck face. Memoised, read from the physics thread. |
| `PlotProbe.java` | "Is this point inside one of the hull's blocks?" — the single most repeated question in the mod. |
| `ProfileHolder.java` | The interface `BlockStateProfileMixin` implements, so a state can hold its own profile. |

**Acting on it**

| File | |
|---|---|
| `ImpactCallback.java` | One contact, decided. The hot path. |
| `HullSweeper.java` | Carving, crushing, soft clearing and un-wedging — everything the solver will not report. The largest file by far. |
| `PendingBreaks.java` | The queue between the physics step and the tick, and the hull braking. |
| `BlockScatter.java` | Removing a block: drops, falling-block debris, particles, and the per-tick rations on all of it. |
| `CrackTracker.java` | Damage a block carries between impacts, and the vanilla break overlay for it. |
| `VoxelClassifier.java` | Sable's per-block collider classification, done faster. Purely an optimisation. |
| `ContactTracker.java` | Contact-block counts per sub-level, which is the denominator of the whole mass model. |

**Plumbing**

| File | |
|---|---|
| `CreateAeronauticsImpact.java` | Mod entry point. Config registration and six listeners. |
| `ImpactConfig.java` | Every setting, plus the per-tick `Tuning` snapshot everything else reads. |
| `ImpactStats.java` | What the mod costs the tick, printed on demand (`logPerformance`). |
| `mixin/BlockMixin.java` | Claims every block in the game for `ImpactCallback`. |
| `mixin/BlockStateProfileMixin.java` | Adds the profile field to `BlockState`. |
| `mixin/VoxelNeighborhoodStateMixin.java` | Routes collider classification through `VoxelClassifier`. |

`src/test/java/…` — `ImpactResolverTest` (the bulk of it), `SweepDetailTest`, `BackingTest`.

## Invariants

These are the things that will break in ways that are hard to diagnose if they are violated.

### `ImpactResolver` and `SweepDetail` must not reference Minecraft, NeoForge or Sable

Not a style preference. The JVM verifies a class by loading every type it mentions, so a single import of a
game type pulls the whole of Minecraft into the unit tests, and there are then no unit tests. This is why
every config value those two need is passed in as a parameter (`ImpactResolver`) or pushed in from
`ImpactConfig.Tuning.read()` (`SweepDetail.configure`) rather than read from `ImpactConfig` directly. The
call sites are wordier for it; that is the price.

### Nothing writes to the world from inside a physics step

`ImpactCallback` and `Backing` run inside Sable's step. They may read the level — through `getChunkNow`, never
through anything that could generate or load — and they may write into `PendingBreaks`. They may not set,
remove or update a block. The `WeakHashMap` and the per-tick counters in `PendingBreaks` are safe on the basis
that the step runs on the server thread.

`VoxelClassifier` and `ImpactCallback.needsOwnVoxel` are stricter still: they can run on Sable's mesh thread.
That is why `BlockProfile`'s cache is a plain non-volatile field (a stale read costs one rebuild, and a
profile is a pure function of the state) and why `ImpactStats` uses `LongAdder` for the two phases those
paths report.

### `BlockProfile` is cached per `BlockState` behind a generation counter

Block states are singletons baked at registry time, so a profile derived from one is derivable exactly once.
`BlockProfile.clearCache()` does not walk anything — it increments a counter, and a profile from an older
generation fails its own check on the next read and is rebuilt in place. That matters because the cache lives
on the states themselves, which nothing here can enumerate.

The consequence: **anything that changes what a profile would be must call `ImpactConfig.invalidate()`.** The
four listeners in `CreateAeronauticsImpact` are that — three config events and `TagsUpdatedEvent`, the last
because a material rule written against a tag means nothing until tags are populated, and a datapack reload
repopulates them.

### The mixins are optional, deliberately

`VoxelNeighborhoodStateMixin`'s two injections are both `require = 0`. They are optimisations: a Sable
release that reshapes `getState` should cost the merge, not the game. `BlockMixin` and
`BlockStateProfileMixin` are not optional — without them the mod does nothing at all.

### Declining a contact never means declining to be stopped by it

Every early return in `ImpactCallback.collide` is `CollisionResult.NONE`, which hands the contact back to
Sable to resolve normally. Running out of tick budget, hitting the contact ceiling, or deciding a block is
too tough all take the same path out, and in all of them the hull is still stopped by the block. The only
time the contact itself is dropped is a genuine punch-through.

## The tick budget

`HullSweeper` runs under a wall-clock ceiling (`maxTickMillis`) and gives up cleanly when it runs out. Three
mechanisms, easy to confuse:

- **`Budget`** — per sweep. Bounds how much one hull's pass can read and clear. Carving has its own so a tick
  spent mowing grass can never be the reason something tunnels.
- **`scanBudget` / `sweptThisTick` / `carvedThisTick`** — per tick, shared by every hull.
- **`detail`** — per *many* ticks. When ticks keep overrunning, the sweeper coarsens itself a rung
  (`SweepDetail`) and only climbs back after a hundred easy ones. `adapt()` is where that lives.

The fleet is served round-robin, offset by game time, so a world with more hulls than one tick can handle
does not serve the same few forever. Individual passes are additionally offset by hull id so a fleet does not
all scan on the same tick.

`quietTicks()` is the reason none of this usually costs anything: a hull with nothing near it is found via
the heightmap and left alone for a while, which is the ordinary case for anything in flight.

## How to add a config option

1. Declare a `public static final ModConfigSpec.*Value` in `ImpactConfig`, next to the ones it relates to —
   declaration order is the order it appears in the generated toml. The `.comment(...)` is the whole of its
   documentation for a server owner, so say what the trade is, not just what the number does.
2. Add a component to `ImpactConfig.Tuning` and read it in `Tuning.read()`. **The record's component order
   and the constructor call must line up** — they are positional, and the compiler only catches a mismatch if
   the types differ.
3. Skip `Tuning` only if the value is not read per tick. `cullInteriorVoxels` is read straight off the spec
   because it is asked from a remesh with no tick around it; `sweepFinestDetail` and `materialOverrides` are
   pushed into their classes from `read()`.
4. Document it in the README's reference tables. Every key in the code is in there, and nothing else is.

Defaults must reproduce existing behaviour exactly, so that upgrading is a no-op on an existing world.

## How to add a sweep pass

Passes live in `HullSweeper.sweep`, in the per-hull loop. A new one needs:

- a gate that can turn it off entirely, checked before the loop;
- an interval, offset by hull id (`(now + id) % interval == 0L`);
- a `Budget`, and an `exhausted()` check inside whatever it iterates;
- an `ImpactStats.Phase` and a `mark()`/`since()` pair around it, or it will be invisible in the profile;
- a reason it is correct to skip. Everything except the un-wedging pass can be dropped and picked up next
  tick. `sweepOverlaps` cannot — a hull nobody digs out stays buried for good — which is why it is neither
  gated on the detail rung nor given up on, only slowed.

Blocks are removed through `BlockScatter`, never through `level` directly: the effect rations and the
`blockUpdates` setting live there.

## Building and testing

```
./gradlew build          # jar into build/libs/, plus the tests
./gradlew test           # tests only
./gradlew build --offline -q
```

Sable and Sable Companion resolve from `https://maven.ryanhcode.dev/releases` and are compile-only. The mod
compiles against Sable 2.0.3 and declares `[2.0.1,3.0.0)`.

**Build variants.** `build_variant` in `gradle.properties` suffixes both the jar file and the name shown in
the mod list:

```
./gradlew build -Pbuild_variant=fast   →  create_aeronautics_impact-1.0.0-fast.jar
                                          "Create Aeronautics Impact (Fast)"
```

Same mod id, same version, same code — a variant is a set of config defaults, nothing more. It exists so a
pack can ship one jar instead of a jar plus instructions for editing a toml. The `v1.0.0-fast` branch is
exactly the release branch with different defaults in `ImpactConfig` and `build_variant=fast`.

**Tests.** 110 of them, all over the decision maths. They run without Minecraft on the classpath, which is
the point — see the first invariant. Anything that needs a game type is not unit tested and has to be tried
in a world.

**Profiling.** Set `logPerformance = true` in the server config. Every 100 ticks it prints the mod's share of
the tick, the four sweep phases separately, the two phases that run inside Sable's own time, the heaviest
crush seen, and — if any tick ran long — the slowest tick against the mod's share of *that* tick, which is
the figure that says whether the mod was responsible.

## Gotchas

- **Plot space vs world space.** A contraption's blocks live tens of thousands of blocks away in the
  plotgrid, inside the same `ServerLevel`. `impactPosition` arrives in the plot space of whichever sub-level
  owns the hit block, and in world space when that block is plain terrain. `BlockScatter` has two entry
  points for this reason.
- **Crack overlays are keyed by breaker id.** Vanilla keys them by whoever is mining, so `CrackTracker`
  synthesises a negative id per block position; two blocks sharing one would erase each other's.
- **`getChunkNow`, never `getChunk`.** Several paths run off the server thread or inside a step, where
  loading a chunk is a stall and a place to deadlock. A miss has to stay a miss.
- **Sub-level runtime ids are reused.** A removed sub-level hands its plot to the next contraption, which is
  why `ImpactCallback`'s plot cache is emptied every tick and `HullSweeper.TRACKED` expires entries.
- **`sable:fragile` is resolved by name.** Sable exposes it as a Veil `RegistryObject`, which is not on the
  compile classpath. `BlockProfile.Fragile` looks it up on first use, not at class load — this class can be
  loaded from a config event, before Sable's registry exists.

## Branches

- **`v1.0.0`** — the release build.
- **`v1.0.0-fast`** — performance-first config defaults. See `FAST.md` on that branch.

Same mod id, so only one of the two jars can be installed at a time.
