# Create Aeronautics Impact

**Airships that hit the world, and a world that loses.**

In Sable, physics bodies collide with terrain, but the terrain never answers back: a two-hundred-tonne
airship meets a birch tree and stops dead. This mod makes the terrain give way. A hull that hits something
decides, block by block, which of the two breaks. Then it ploughs, bores, crushes or shears its way through,
and pays speed for every block it takes.

## What it does

- **Impact.** Every contact is a contest between the block that was hit and the block that hit it, decided
  on material strength, closing speed and mass. The loser breaks and the winner takes wear.
- **Backing.** A block is only as strong as what is behind it. A one-block stone wall breaks like a pane of
  stone; the face of a mountain holds like a mountain.
- **Crushing.** Heavy builds press down on what they rest on. Trees and dirt give way under them, and stone
  holds.
- **Shock and fractures.** A hard enough crash is felt past the blocks it touched. Ships crack along their
  weakest joints and split into pieces, and windows go out along the whole hull.
- **Collapse and load bearing.** A build that lands hard folds under its own weight. Structures whose
  supports are destroyed come down.
- **Debris.** Broken blocks are thrown clear and settle where they land.
- **Cracking.** Damage that does not destroy a block is shown as break progress, and heals if it is left
  alone.

## Presets

Two settings pick a whole set of defaults at once:

- **Performance:** `QUALITY`, `BALANCED` (the default) or `FAST`.
- **Destruction strength:** `GENTLE`, `LIGHT`, `NORMAL`, `HEAVY` or `DEVASTATING`.

A preset only changes settings you have not edited yourself. Every value can still be tuned by hand, either
in `<world>/serverconfig/create_aeronautics_impact-server.toml` or from the in-game config screen (the icon
next to *Mods* in the pause menu).

## Performance

All the work happens on the server, under a per-tick time budget. When the budget is tight the mod lowers its
detail rather than stalling the tick. Set `logPerformance = true` to see how much of each tick it uses.

## Requirements

- Minecraft 1.21.1, NeoForge
- [Sable](https://modrinth.com/mod/sable) 2.0.1 or newer
- [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) is optional, 1.3.x

The mod must be installed on both the server and the client.

## License

MIT. Include it in any modpack, no need to ask.
