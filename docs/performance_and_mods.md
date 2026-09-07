# Performance And Mods

This document records current pack state and operational constraints. Active downloaded mods are
the current `mods/*.pw.toml` files; active Better Content JARs are the machine-readable
`gradle/active-custom-mods.json` inventory documented in [Custom Mod Workspace](custom-mod-workspace.md).
Generated runtimes and old profiling directories are not authorities for active state.

## Active integration state

The Bumblezone 7.13.4 and Rats 8.1.3 are the active Font-only expedition realms replacing the
retired Undergarden and Deeper Darker routes. `bumblezone_cultivars` owns Bumblezone food flora and
propagation. `ratlantis_logistics` owns the three visible logistics components, bait-only trust,
finite Rats work behaviors, and tube traversal repair. Nature's Spirit owns mahogany ecology;
`dynamic-trees-hexerei` is validation-only, while the CurseForge Dynamic Trees addons for Aether
and Twilight Forest remain active. `dynamic_trees_malum` is the one active Better Content tree
extension.

TaCZ 1.1.8 is active with playerAnimator and client-only Accelerated Rendering. Create Armorer,
Applied Armorer, and Immersive Armorer remain external gun-pack ZIPs under `tacz/`. Their benches
progress from brass factory manufacture through post-AE2 Impossible and Electrical milestones.

The pinned Valkyrien Skies transport family is active on both sides:

- Valkyrien Skies `valkyrienskies-120-2.4.11.jar`;
- Eureka `eureka-1201-1.6.3.jar`;
- VS: Clockwork `clockwork-0.5.6.jar`;
- Trackwork `trackwork-1.20.1-1.2.4.jar`.

Eureka owns primitive post-Part-Builder wooden watercraft, Trackwork is a peer of Create trains,
and powered Eureka/Clockwork flight progresses through Aether, Airtight lift, PneumaticCraft gas
hardware, and PCB-controlled stabilization. Automated pack tests own startup and broad
compatibility only; helm control, camera behavior, rendering, specialized add-on mechanics,
observer synchronization, and ship persistence remain manual-playtest surfaces. Epic Fight's
optional third-person combat camera remains player-controlled.

Complicated Bees, EMI Ores, Forgotten Ruins, Quickstack, Ice and Fire, Hexerei, Malum, Occultism,
Goety, Ars Elemental, Ars Creo, Ars Energistique, Polymorph, Supplementaries, Amendments, Genetic
Animals, Fowl Play, Advanced Chimneys, and Realistic Block Physics are active. Farmer's Delight,
Ube's Delight, Farmer's Respite, and Brewin' and Chewin' are the active Delight food-and-drink
set. Quickstack's global `C` and `X` shortcuts remain unbound; its inventory buttons are the
deliberate transfer surface. The maintained `config/adchimneys/` definitions cover active furnace,
burner, smeltery, and generator emitters.

Claustrophobic Dungeons, Dungeon Crawl, Create Big Cannons, Reliquary, Undergarden, Deeper Darker,
the standalone coolant mod, the redundant fission-reactor mod, and Create New Age remain retired.
Forbidden and Arcanus, Roots Classic, Mahoutsukai, Eidolon, Theurgy, Psi, and Hex Casting have no
active Packwiz manifests and are inactive unless deliberately reintroduced.

## Runtime and memory policy

`release.main.kts` is the only fresh tested distribution path. It stages clean custom-mod builds,
packages once, and tests the unchanged candidate pair. Runtime fixtures extract those candidates
and change only disposable EULA, authentication, and port settings. The production server profile
uses the root 4 GiB initial and 16 GiB maximum heap baseline.

Full-pack memory pressure is primarily a content/profile question. Do not remove active content or
promise a low-memory target without a new measured comparison against current manifests. Model,
texture, atlas, native/render, worldgen, and decorative surfaces must be measured together; JVM
flag changes alone do not establish a smaller supported profile.

Runtime recipe-audit and block-hardness diagnostics ship disabled. An explicitly requested
`server` or `all` suite invokes the packaged runtime-data dumper, requires a complete snapshot, and
atomically refreshes ignored `generated/runtime-dumps/`. Incomplete snapshots never replace the
last complete one.

## Shader block motion classification

The active Complementary Reimagined archive limits `block.10005` grass motion to flexible plants.
Full cubes and rigid assemblies—including Burnt/Ice and Fire grass blocks, Tinkers' Construct slime
grass blocks, tree roots and branches, hedges, planters, flower boxes, pots, doors, and cactus
structures—must remain outside that class so their vertices do not deform like grass. Additions need
a model-shape audit: plant stems, crops, vines, flowers, ferns, and saplings may use grass motion;
host blocks, wood, containers, and multipart rigid supports may not. The focused static archive
contract freezes both the 81 audited exclusions and representative flexible inclusions.

## C2ME, Distant Horizons, and world generation

C2ME remains active with threaded world generation and no-tick view distance. In `config/c2me.toml`,
`ioSystem.replaceImpl = "false"` and `ioSystem.async = "false"` are a paired compatibility boundary
for Dynamic Trees and World Lifecycle Manager recovery. Do not enable either path without a
thread-safe compatibility change and an explicitly authorized server lifecycle gate.

Distant Horizons remains enabled during compatibility validation. Server generation is bounded by
`maxGenerationRequestDistance = 16`, synchronous load requests by
`maxSyncOnLoadRequestDistance = 32`, and per-player transfer by 256 KiB/s with adaptive transfer
enabled. LOD transparency is disabled to avoid incorrect Distant Horizons/Oculus/shader depth
composition; nearby vanilla and shader water remain unchanged.

`better_content_fixes` owns the foreground tick-budget governor. After a 100-tick sample warmup it
temporarily overrides only Distant Horizons' optional distant generation when the sliding p95
exceeds 50 ms or a tick exceeds 100 ms. It clears its in-memory override only after 600 consecutive
ticks with p95 at or below 40 ms and no tick above 100 ms, and never enables a user-disabled DH
setting. `/better_content_fixes performance_status` reports p50/p95/p99/max and override state.

The controlled exploration acceptance budget is measured after 60 seconds of warmup over a
ten-minute fresh-terrain route: median at most 40 ms, p95 at most 50 ms, p99 at most 100 ms, and no
tick above 250 ms. Pauses, saves, maintenance commands, and dimension transitions are separate
scenarios. C2ME already prioritizes chunk tasks by ticket level and distance; a strict global
near-player-first guarantee is not claimed because dependency chunks, synchronous player-critical
loads, forced tickets, and cross-dimension work must remain starvation-free. Keep the paired C2ME
IO compatibility settings above unchanged unless separately validated.

Performance evidence must use fixed candidate hashes, seed, route, view/simulation distances, DH
and C2ME settings, forced-ticket inventory, and host-load notes. Capture the same loaded-terrain
control and fresh-terrain route with `spark profiler start --timeout 600 --thread *
--ignore-sleeping`, plus a server-thread spike capture with `--only-ticks-over 50`. Store the
profiles, measurements, and sorted SHA-256 manifest under
`workspace_artifacts/evidence/performance-fonts/<UTC>/`; do not treat paused time, maintenance
commands, or the pre-event-driven Heat Sync profile as a gameplay baseline.

Lost Cities, Twilight Forest, and Fallout Wastelands are Creating Space destinations declared under
`kubejs/data/*/creatingspace/rocket_accessible_dimension/`. The Flesh That Hates is active, but its
six Mushroom Fields structures are disabled by `datapacks/worldgen_compat_fixes`, and
`better_content_fixes` disables its unconditional client proximity-music scan. Its entities,
combat, evolution, and ordinary Records-channel sounds remain active.

`datapacks/hyle_deep` owns the exhaustive Hyle/Unearthed deep-stone pass beginning at Y -64.
`better_content_fixes` moves Hyle and SGI terrain conforming to decoration tail, completes only
leftover replaceable bottom-section host blocks during generation, and registers the Unearthed
soil aliases Dynamic Trees needs. Do not add a chunk-load replacement sweep: it could rewrite
player-placed stone. Do not restore Unearthed Dynamic Trees soil-property files that reference
unregistered `rooty_unearthed_*` blocks.

Realistic Block Physics gives narrow Create transmission blocks an anchored-network profile.
Framed item frames and Hexerei drying racks use support-only wall attachment. Hexerei fluids,
functional furniture, and internal multiblock/model-state blocks are excluded from structural
physics; its windows, fabric blocks, and shaped fabric carpets use glass, wool, and carpet profiles.
Pollution of the Realms ash layers are excluded from solid physics. Every custom Realistic Ores deposit covered by
stone configured features must retain a matching Excavated Variants gravel definition.
Generated RBP assignments are registry-exact and mutually exclusive. Loose sand physics are reserved
for granular blocks rather than pickaxe-mined masonry; full blocks, slabs, stairs, and walls use
material-appropriate construction or wood profiles with shape-scaled mass and support.

## Custom compatibility ownership

`world_lifecycle_manager` owns dedicated-server succession and the persistent schematic library.
`player_traces` owns world-local, bounded movement and annotation data under the active save; it
does not composite the viewport or import server-root legacy data across generations.

`better_content_fixes` owns the active compatibility repairs for Dynamic Trees/Aether, Hyle,
Unearthed soil and farmland, Burnt grass, C2ME safe-random noise, Weather2 fog under Oculus,
PVJ Nether groundcover, TFTH proximity audio, and Dimension Font bounded placement. Dynamic Trees
falling trees remain item-drop only. Dimension Font layout version 2 is new-world data and does not
deserialize old pieces.

`pillager_campaigns` bounds its loaded-column sampling, route search, and spawn production; it must
never load terrain to seek a route. `settlement_roads` and `village_walls` likewise keep tick work
bounded and clean transient state. `class-selector` always owns first-join spectator scouting and
permanent spawn locking. Its active `progression` embark mode asks World Lifecycle Manager for the
current lineage policy: new lineages receive site-only onboarding, class perks expose only their
unlocked classes, and unlocking all six classes exposes the bounded Embark point-buy. World
Lifecycle Manager separately owns ordered successor spawn-biome preferences.

## Heat and pollution authority

`heat_sync` owns industrial heat storage, transfer, pipes, ambient bridging, hot water, and coolant
exchange. Create: Power Grid retains its electrical and device-overheat simulation; its optional
`ThermalBehaviour` adapter exposes that temperature to HeatSync without creating a second heat API.

`latent_chemlib` owns contained high-energy chemistry, decay heat, criticality, radiation, and
mass-debit semantics. It has no reactor power ladder or atmospheric scheduler. Released gases
become native AdPother pollutant blocks at the configured contained-mass boundary; AdPother then
owns movement, wind, impacts, detection, protection, emitters, chimneys, filters, and cleanup.
PneumaticCraft retains native pressure and thermo-plant behavior, with explicit normal-air and
chemical-service ledgers at the Latent boundary.

Rebuild and deploy custom JARs through [Custom Mod Workspace](custom-mod-workspace.md). Run pack
suites only when explicitly ordered, following [Testing and fresh distributions](testing.md).
