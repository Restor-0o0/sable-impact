# Towards a real crash

A checklist, for review before anything here is built.

The request behind it: one module that computes the kinetic energy of a build from its speed and its mass,
and then carries that energy through the hull on contact, spending it down as it goes — so that hitting the
ground with an airship blows the glass out along its length, folds the soft structures, and leaves the real
destruction at the point that touched.

That is a good description of what actually happens, and most of the machinery for it already exists in this
mod under other names. What follows is an honest accounting of what is here, what is missing, and what has to
be given up to get the rest — grouped into phases so that each one can be judged on its own before the next
is started.

**Status.** Phase 3 is done, in full, and shipped in 1.7.0. Phase 1 is done in the part that mattered
most (1.3 and 1.6, for the build's own energy). Phases 2 and 4 are not built, and 5 is a list of things that
will stay true whatever else happens. Each item below carries its own mark.

---

## Where it already stands

| The request | What exists | What is wrong with it |
|---|---|---|
| Energy from speed and mass | `shock.kineticScale` prices a wave off the striking body's ½mv² | ~~Recomputed per contact~~ — fixed in 1.7 by `shock.oneCrash`: one reservoir per build, drawn down until the build has been quiet as long as its damage budget needs |
| Carried through the hull | `ShockWave` walks block to block and stops at gaps | It spends by *distance travelled*, not by *what it travelled through*. Sixteen blocks of wool cost what sixteen blocks of steel cost |
| Decreasing as it goes | `shock.falloff`, one number | Isotropic and material-blind. A shock through a hull is neither |
| Heavy damage at the impact | `shock.cost` prices a block, near blocks are reached first | Real enough |
| Glass knocked out, soft structures folded | `fragile` in the material table, and since 1.7 the whole of `[stress]` | Done. Three failure modes, a threshold rather than a price, and a fragile pass of its own that outruns the wave |
| The rest falls down | `Collapse` | Not a support model at all — a fixed-speed front with a taper, tuned to look right. It does not ask what was holding anything up |

So this is not a rewrite from nothing. It is four specific replacements and one new pass.

---

## Phase 1 — One crash, one energy

The single largest correctness problem is that a crash is currently *many* events that each derive their own
energy. Everything else is downstream of fixing that.

- [ ] **1.1** Compute the crash energy once, at the moment of first contact, from Sable's `MassData` and the
  closing velocity between the two bodies: `E = ½·m·v²` using the *relative* velocity, so a build landing on
  another build is priced the same either way round.
- [ ] **1.2** Add the rotational term, `½·I·ω²`. A ship that comes down nose-first and slews carries real
  energy in the yaw, and ignoring it makes every glancing crash too gentle.
- [x] **1.3** Hold the result in one ledger per (build, crash), keyed the way `BuildDamage` already keys a
  build, and let *every* consumer draw on it: crushing, waves, cracks, collapse, and debris.
  *Partly. The reservoir now persists across the ticks of one crash and is cleared on the same rest timer
  `BuildDamage` uses, so waves and cracks share one energy per crash. Crushing, collapse and debris still
  have budgets of their own.*
- [ ] **1.4** Charge the energy actually spent. A block thrown at `v` costs `½·m_block·v²`; a block broken
  costs its own resistance; a block merely cracked costs a fraction. Today debris velocity is free.
- [ ] **1.5** Subtract what the crash does not spend on damage: rebound (`rebound`, `reboundSpin`), drag
  (`breakDragMass`), and a flat share for deformation that Minecraft cannot show.
- [x] **1.6** End the crash when the ledger is empty rather than when a per-system budget runs out. This is
  what makes a small bump small and a real crash large, without either being tuned separately.
  *Done for the build's own side. Terrain is still refilled per tick, deliberately: a hull ploughing a
  hillside is meant to keep ploughing it.*
- [ ] **1.7** Keep a scale factor over the whole thing. A 200-tonne ship at 30 m/s carries about 90 MJ; at any
  honest price per block that is the entire ship, every time. The model has to be deliberately un-real
  somewhere, and one explicit dial is better than the current five implicit ones.

**Cost:** moderate. Mostly a refactor of `ShockWave`'s budget into something owned by the impact rather than
by the wave. **Risk:** every existing shock setting changes meaning, so the whole config needs retuning.

---

## Phase 2 — Energy through the structure, not through space

- [ ] **2.1** Attenuate per block passed through, by that block's material, not by distance travelled. Steel
  carries a shock a long way; wool eats it. `BlockProfile` already has the material in hand.
- [ ] **2.2** Divide, don't just decay. A shock arriving at a broad deck spreads over its whole width and each
  block gets less; the same shock arriving at a strut concentrates and the strut fails. This is the single
  thing that would make hulls behave structurally rather than spherically, and it means tracking how wide the
  front is, not only how far it has gone.
- [ ] **2.3** Make it directional. Strongest along the impact axis, weakest across it — a cone rather than a
  sphere. A ship that lands on its keel should have the shock run fore and aft, not out through the sides.
- [ ] **2.4** Reflect off free surfaces. A shock that reaches the far side of a hull and comes back is what
  blows out the panel opposite the hit; this is spalling and it is one of the most recognisable things about
  a real impact. `crackSpall` is a local neighbour effect, not this.
- [ ] **2.5** Stop paying twice. A block already destroyed this crash must not be re-priced by a second wave
  crossing the same place.

**Cost:** high. 2.2 in particular means the wave has to know its own cross-section, which the current
frontier-set does not track. **Risk:** this is where the tick time will go.

---

## Phase 3 — Failure by stress, not by price

Today a wave *buys* blocks: it has a budget and a block has a price. A wave with a large budget breaks
obsidian as readily as glass, just for more money. That is why a big crash eats everything indiscriminately.

- [x] **3.1** Break a block when the energy density arriving at it exceeds its strength, and not otherwise.
  Energy that arrives below the threshold is *not spent* — it passes on. This one change is what produces
  the requested behaviour on its own: the glass goes, the structure holds, and the energy keeps travelling.
  *`[stress]`, on by default. `stress = false` restores the budgeted wave.*
- [x] **3.2** Give the material table three failure modes rather than one strength:
  - **brittle** (glass, ice, lamps) — a very low threshold, shatters, and passes almost nothing on;
  - **ductile** (metal, wool, wood) — a high threshold, absorbs a great deal without breaking;
  - **structural** (stone, blocks bearing load) — fails at the threshold and stops carrying.
- [x] **3.3** Give fragile blocks their own cheap pass with a much lower threshold and a much longer reach
  than the structural wave, so "the shock ran the length of the ship and took the windows out" is one flood
  fill over glass, not a full wave.
  *`stress.glass`. It fills through solid material rather than through space, so it follows the decks.*
- [x] **3.4** Weight the threshold by how well the block is held — `backingWeight` and `hullBackingWeight`
  already do this for contacts and should feed the same number here.
  *`stress.backing`, on a neighbour count rather than on a raycast: a wave reads thousands of blocks and the
  contact-side backing is far too expensive to run per block.*

**Cost:** low to moderate. The material table exists; this is mostly new fields and a changed comparison.
**Risk:** low. This is the phase most likely to be worth doing on its own, even if nothing else here is.

**Built in 1.7.0**, and the estimate held. The one thing it did not predict: with nothing being bought, the
break ceilings stopped bounding the walk — a shock passing through material it cannot break is free — so the
mode needed a scan ceiling and an intensity floor of its own to stay finite.

---

## Phase 4 — Gravity finishes it

`Collapse` is currently a fixed-speed front with a taper, tuned until it looked right. The real rule is
simpler: whatever is no longer held up, falls.

- [ ] **4.1** After the shock, flood-fill the build from whatever is still supported and mark everything that
  is not. Bound the fill — this is the expensive operation of the whole document.
- [ ] **4.2** Prefer breaking *joints* over deleting blocks, and let Sable's own sub-level split do the
  falling. A wing that shears off and falls as a wing is worth more than a wing that dissolves into falling
  sand, and Sable already has the machinery.
- [ ] **4.3** Recompute the energy of each landing. A section that falls forty blocks lands with real energy
  of its own; today a re-armed collapse reuses the original impact velocity.
- [ ] **4.4** Keep the current front as the fallback for when the fill is too expensive or the build is too
  large. It is crude, but it is cheap and it is bounded, and a server that cannot afford the real answer
  should still get a wreck.

**Cost:** high, and this is where a large hull will stop the server if it is done carelessly.
**Risk:** high. It also depends on Sable behaving well under repeated splits, which is exactly where the
`Sub-level assembly attempted inside plot of already removed sub-level` crash comes from.

---

## Phase 5 — The parts that stay unreal

Worth agreeing on before the rest, because they bound how good this can get.

- [ ] **5.1** **Blocks cannot deform.** A real airship hull in a crash mostly crumples; it does not shatter.
  Everything above converts deformation energy into breakage because there is nothing else to convert it to,
  so a Minecraft crash will always look more brittle than the real thing.
- [ ] **5.2** **Blocks have no joints.** Real structures fail at their connections and come apart in large
  panels. A block grid fails block by block, which is why wreckage looks granular. 4.2 is the closest
  available approximation.
- [ ] **5.3** **Real energies are absurd.** See 1.7. Some deliberate scaling is not a shortcut here, it is a
  requirement.
- [ ] **5.4** **Block entities cannot fly.** A falling block carries no block entity data, so a chest thrown
  as debris would quietly empty itself. Machinery either drops as items or stays where it is.
- [x] **5.5** **Everything stays switchable.** Every phase above ships behind its own flag with the current
  behaviour as the `false` case, as the rest of this mod does.
  *Held so far: `stress`, `stress.glass`, `shock.oneCrash` and both `[optimize]` switches each restore the
  previous behaviour exactly.*

---

## Suggested order

**3 → 1 → 2 → 4.**

Phase 3 is cheap, low-risk, and produces the behaviour actually described in the request — glass out along
the length, structure holding, destruction concentrated where it touched — without any of the rest.
Phase 1 is the correctness fix that everything else needs. Phase 2 is what makes it read as a structure.
Phase 4 is the largest and the riskiest, and the current collapse is a serviceable stand-in until it exists.

**What is left.** 3 is done and 1 is done where it was cheap to do (1.3, 1.6); 1.1, 1.2, 1.4, 1.5 and 1.7
remain, and all of them want Sable's `MassData` and the contact manifold rather than the numbers this mod
already has. Phase 2 is next and is now the obvious gap: a shock still attenuates by `falloff` per block
travelled as well as by what it travelled through, so distance is still doing work that material should be
doing alone. Phase 4 is unchanged and still the riskiest thing in this document.
