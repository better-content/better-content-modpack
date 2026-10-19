# Aspirational features

## Purpose and authority

This is the living inventory of features wanted for Better Content but outside current work scope.
It owns deferred intent, not implemented behavior, a release roadmap or permission to start work.
The pack owner decides additions, priorities and activation; recording a feature here does not
approve its proposed design or authorize implementation, deployment or testing.

Keep entries focused on the player-facing goal, proposed boundaries, unresolved decisions and
conditions for revisiting. When a feature enters explicitly approved work, update its status and
link its owning subject or source repository. Once implemented or declined, remove the deferred
entry after moving any still-relevant guidance to the proper living subject; do not accumulate
historical completion reports here. See the [documentation authority map](README.md),
[workspace policy](policies/workspace.md) and [testing policy](testing.md).

## Valkyrien Skies: physical motion and spacecraft

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** Valkyrien Skies vehicles and moving contraptions should have realistic sources of
motion and Newtonian responses, including equal-and-opposite reactions. Extend that physical
vehicle model to player-built spacecraft, replacing Creating Space's spacecraft system rather
than maintaining a second, disconnected flight model.

### Proposed behavior — not yet approved

- Give propulsion and actuation identifiable physical sources: engines supply power, while
  wheels, propellers, control surfaces, thrusters and mechanisms apply forces through contact,
  a surrounding medium or expelled reaction mass as appropriate. Power alone is not thrust;
  internal mechanisms must not accelerate an isolated craft's center of mass for free.
- Apply equal-and-opposite forces and torques to interacting bodies. Pushing, towing, docking,
  collisions and mounted mechanisms should produce corresponding reactions rather than
  one-sided motion. Account for momentum carried away by exhaust or transferred to air,
  water or terrain without requiring individual particle simulation.
- Make mass, inertia, force application points and torque matter: heavier loads change
  acceleration, off-center thrust can rotate a craft, and braking or attitude changes need
  physical means. Preserve linear and angular momentum unless an external interaction changes
  them; avoid unexplained drag, automatic stopping or reactionless steering.
- Use Valkyrien Skies-driven spacecraft for continuous physical maneuvering, with vacuum-capable
  propulsion, finite propellant/reaction mass where applicable, coasting and deliberate braking.
  Atmospheric propellers and aerodynamic control surfaces must not provide vacuum thrust or
  control without a separately justified mechanism.

### Decisions and conditions for revisiting

- Inspect the selected Valkyrien Skies version and integrations before choosing an owning source
  repository; existing physics support and gaps are not assessed by this entry.
- Define practical simulation limits, acceptable approximations, player controls and any explicitly
  supernatural exceptions without presenting exceptions as ordinary Newtonian mechanics.
- Decide spacecraft assembly, atmosphere/space transitions, gravity and travel-scale behavior,
  persistence, multiplayer synchronization and compatibility with the pack's progression.
  Orbital simulation is an unresolved scope decision, not an assumed requirement.
- Plan the replacement boundary and migration for Creating Space's spacecraft, recipes, controls
  and progression dependencies; decide separately whether any non-spacecraft content remains.
- Require explicit implementation approval and separately authorized physics, multiplayer,
  performance and native visual verification. Recording this goal does not remove Creating Space,
  install Valkyrien Skies or authorize a deployment or test run.

## Hostile mobs: retreat after a one-sided fight

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** Ordinary hostile mobs that lose half their health without successfully landing a hit
should try to run away rather than continue a hopeless attack.

### Proposed behavior — not yet approved

- Track damage and successful hits within an encounter. A successful hit means actually damaging
  the opponent, not merely swinging or firing a projectile.
- Trigger retreat after losing health equal to half the mob's maximum health without landing a
  hit. Prefer encounter damage tracking over a simple current-health threshold so an already
  injured mob or unrelated environmental damage does not automatically trigger flight.
- Give retreat priority over attacking and prevent immediate target reacquisition from cancelling
  it. Choose reachable escape destinations away from the opponent rather than just clearing the
  attack target.
- Start with ordinary ground-based hostiles. Bosses, stationary enemies, flying mobs, creepers
  preparing to explode and mobs with custom combat AI need explicit inclusion/exclusion decisions;
  generic coverage does not promise compatibility with every modded hostile.

### Decisions and conditions for revisiting

- Define encounter start/end, damage attribution and whether the opponent must be a player.
- Confirm whether one successful hit prevents retreat for the rest of that encounter, as the
  initial request suggests, or whether losing half its health since its last successful hit also
  qualifies. The latter is a broader morale mechanic, not an assumed requirement.
- Decide retreat duration, escape distance, out-of-combat reset and cornered behavior. A bounded
  retreat of roughly 8–15 seconds with fighting back when cornered is a proposal, not settled tuning.
- Choose the owning server-side integration repository after inspecting existing combat AI;
  verify representative vanilla and modded mobs, pathfinding and multiplayer behavior under
  separately authorized implementation and testing. This entry authorizes no gameplay changes.

## Aspirational contextual Threads

**Status:** Wanted; outside current implementation scope. These are future teaching cards,
not requests to implement new gameplay mechanics, and do not fill the four vacated active slots.

- **Water bottle:** Contextual preparation and carrying advice that complements, rather than
  repeats, the existing Drinking Safe Water lesson.
- **Grappling hook:** A contextual Thread about acquiring or using the pack's hook and its
  useful traversal role.
- **Climbing:** A contextual Thread explaining the implemented climbing opportunity at a
  personal point of need.

Mechanic and trigger research, ownership, copy, art, and acceptance belong to separately
approved future work. No triggers are selected here. Recording these aspirations authorizes
neither implementation, deployment nor testing.

## Abyssal City: gameplay completion and pack integration

**Status:** Private source checkpoint published; gameplay completion and pack integration are deferred
and outside current work scope. This is not a completed or accepted MVP.

**Owning source:** [better-content/better-abyssal-city](https://github.com/better-content/better-abyssal-city)
(private; organization access required). Its README owns implementation details and verification
boundaries. The prototype is not installed in the pack; publication does not authorize deployment.

**Goal:** An explorable layered underwater city with dangerous mystery fluid, player sealing and
pumping/reclamation, finite local Gatherer/Warden ecology, and persistent intercity travel.

The published prototype implements city planning/generation, structural fixtures and experimental
traversal tooling. Generation readiness remains incomplete; hydraulics, actors and integrated
multiplayer/performance/visual/distributable acceptance remain unfinished. No gameplay-ready ZIP
or human acceptance is claimed.

### Conditions for revisiting

- Explicitly authorize resuming the isolated MVP and, separately, any main-pack integration.
- Resolve native ordinary-loading/physics-cache diagnostics and clarify the headless audio boundary.
- Prefer compositional navigability verification: validated pieces and connection contracts,
  assembled-world obstruction/connectivity checks, and short representative native client journeys.
  Do not rely on an exhaustive player-steering marathon or assume correct composition from piece tests alone.
- Complete and verify the hydraulic reclamation and finite actor loops, persistence, two-client play,
  performance, visual readability and the exact extracted standalone payload before player review.

## Complicated Bees: Forestry-style bee swarms

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** Forestry-style visual bee swarms around Complicated Bees nests and working apiaries,
so inhabited nests and productive colonies feel alive rather than like static blocks.

### Proposed approach — not yet approved

- Prefer studying actual Forestry swarm/bee-rendering code as the behavioral and visual reference,
  and adapting or reusing suitable code where licensing and version compatibility permit, rather
  than approximating the effect from memory. The exact Forestry version and source paths remain
  to be selected and inspected.
- Keep swarms cosmetic and client-side; do not introduce simulated bee entities, alter genetics
  or production, or imply activity when an apiary is inactive. Decide separately how wild nests
  signal occupancy and how swarm density reflects colony activity.
- Bound particle/render cost and viewing distance for dense apiary installations; respect client
  particle settings and provide an appropriate reduction/disable option.

### Source baseline and conditions for revisiting

The pack pin is recorded in [the Complicated Bees manifest](../mods/complicated-bees.pw.toml).
Source inspection of upstream `v1.20.1-3.4.6` found no ambient bee-particle emission in nests or
apiaries; its existing bee particle is emitted by the Bee Staff projectile, not colony activity.
This is an implementation gap, not a verified rendering failure.

Before activation, inspect the chosen Forestry implementation and license, choose the owning
integration repository, and establish how client visuals receive colony activity without adding
unnecessary network traffic. Explicit implementation approval and separately authorized native
visual/performance verification are required; this entry authorizes neither.

## EMI: recipes with available ingredients first

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** When viewing recipes in EMI, put recipes for which the player has all required
ingredients and quantities before recipes with missing ingredients. Keep the latter visible.

### Proposed behavior — not yet approved

- Stable ordering within each recipe category: ingredients available first, existing EMI order
  preserved within both groups. Do not mutate global recipe-manager lists.
- Evaluate availability once when opening the view, before pagination, rather than continuously
  moving recipes while the player is browsing.
- Interpret availability as having the inputs for one recipe execution, not necessarily being
  able to craft in the current workstation. Native EMI handlers retain authority over fill and
  crafting operations.
- Use native exposed inventory sources. Do not introduce unopened/nested backpack searches,
  custom storage handlers or a new item-transfer engine.

### Feasibility and boundaries

The pack pin is recorded in [the EMI manifest](../mods/emi.pw.toml). Initial source inspection
of EMI `1.1.24+1.20.1` found usable ingredient/quantity checks in
[`EmiPlayerInventory.canCraft`](https://github.com/emilyploszaj/emi/blob/23d251ea8ea3a5fd7d760948f36014b185eac69f/xplat/src/main/java/dev/emi/emi/api/recipe/EmiPlayerInventory.java)
and per-category lists before pagination in
[`RecipeScreen`](https://github.com/emilyploszaj/emi/blob/23d251ea8ea3a5fd7d760948f36014b185eac69f/xplat/src/main/java/dev/emi/emi/screen/RecipeScreen.java).
A narrow client-side mixin appears feasible without an EMI fork or server-side crafting changes.
This is a source-based feasibility assessment, not runtime verification; recheck against the
actual pin when implementation is authorized.

EMI's existing matcher is greedy: overlapping ingredient alternatives can produce false negatives.
Reusing native checks does not promise a new exact ingredient-allocation solver. Ordinary input
availability also does not establish workstation, catalyst or other process requirements.

### Decisions before activation

- Confirm ingredients-available ordering versus stricter "craftable in this menu" ordering.
- Decide whether category tabs should also be promoted; within-category ordering is the proposed
  minimum scope.
- Resolve EMI's preferred-recipe focus behavior so opening the view does not silently land past
  the promoted recipes, while explicit requests to view a particular recipe remain meaningful.
- Choose the owning client integration repository and settle inventory-source semantics for
  personal inventory, crafting menus and separately opened native backpacks.

Any implementation must follow [native inventory parity and acceptance](inventory-parity.md),
including both personal-inventory paths and relevant separate-backpack behavior. Activation
requires explicit scope approval; runtime acceptance remains separately authorized under the
shared testing policy.
