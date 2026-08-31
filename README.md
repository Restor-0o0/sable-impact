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

## Configuration

Server-side, written to `<world>/serverconfig/create_aeronautics_impact-server.toml` on first load. Every
knob is documented in place. The ones worth knowing first:

- `impactStrength`, `minImpactSpeed` — how hard a hull hits and how slow it can be and still hit at all.
- `hardnessWeight`, `resistanceExponent` — how block strength is read and how flat the scale is.
- `backingWeight` — how much of a block's strength is on loan from its neighbours. `0` restores plain
  material strength.
- `crushBlocks`, `crushInterval`, `crushPressureScale` — the weight pass and how often it runs.
- `crackBlocks`, `crackResilience` — partial damage. Turning cracking off makes hits all-or-nothing.
- `maxTickMillis`, `maxBlocksPerTick`, `sweepScanBudget` — the performance ceilings. Lower them first if the
  server is struggling.

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

## Branches

- **`v1.0.0`** — the release build.
- **`v1.0.0-fast`** — the same mod with six deliberate quality-for-tick-time trades. See
  [`FAST.md`](FAST.md) on that branch for what each one costs. Same mod id, so only one of the two jars can
  be installed at a time.

`AERO_IMPACT_DESIGN.md` documents the model the code implements.
