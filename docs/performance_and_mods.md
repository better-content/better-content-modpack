# Performance And Mods

This document records current pack state and operational constraints. Active downloaded mods are
the current `mods/*.pw.toml` files; active Better Content JARs are the machine-readable
`gradle/active-custom-mods.json` inventory documented in [Custom Mod Workspace](custom-mod-workspace.md).
Generated runtimes and old profiling directories are not authorities for active state.

## Active integration state

The Deep Void `1.98.1` native fall entry is adapted by Better Compat Fixes to the source dimension's
actual minimum build height minus six blocks. Its vanilla absolute `TeleportHeight` is superseded;
players must fall below the real world, not merely cross vanilla Y=-70 inside extended terrain.
Normal/any-dimension entry retains native `<` and End entry retains native `<=`; native enable
switches, dimension eligibility and the teleport operation remain owned by The Deep Void.
The owning [compatibility source](../../mod_source/better-compat-fixes/README.md) documents the
pinned procedure, native branch GameTest and production-artifact verification.

Deep Void entrances are registered as `better_compat_fixes:deep_void_entrance`, searchable with
`/locate structure better_compat_fixes:deep_void_entrance`. One random-spread structure set uses
128-chunk spacing and 64-chunk separation (one candidate per 2,048-block region before biome
rejection). Its 18 inspected upstream templates have equal selection weight, including upstream
revisions. Every variant has a separate lined 3×3 bore through the actual world bottom, a surrounding
floor/chamber and a passage into the decoration. Explicit template air remains active; authoring
structure blocks and saved entities are excluded. Existing template loot/spawners remain native.
Structure placement is Overworld-only and clipped per chunk, without shared mutable placement
settings. New starts occur in newly generated chunks; existing origin temples/chunks are not migrated.
`PlaceCultTemple = false` disables the competing one-time origin entrance. Abductors are unchanged.

Below Y=0 in the Overworld, carried ordinary compasses point toward the nearest confirmed entrance
start within a bounded eight-region search. Lodestone/recovery compasses, Y≥0 and other dimensions
retain native behavior. Searches are server-side, asynchronous and cached: at most one global
in-flight chunk request and one new request per ten ticks, requesting only `STRUCTURE_STARTS`,
never terrain or full chunks. They may create structure metadata in unexplored chunks, but do not
populate terrain. Old chunks without entrance starts are skipped. Movement by 32 blocks, a five-minute
refresh, reconnect and data reload invalidate relevant state; no result retains the spawn target.
Client needle/packet behavior and full-pack exploration acceptance require a matching deployed
artifact and are not established by isolated source/native fixture checks.

The Bumblezone 7.13.4 is the active Font-only expedition realm replacing the
retired Undergarden and Deeper Darker routes. `better_bumblezone_crops` owns Bumblezone food flora and
propagation. The three visible logistics roots are pack recipes in
`kubejs/server_scripts/progression/20_logistics_roots.js`. Nature's Spirit remains the canonical mahogany
wood and progression authority; `better_hexerei_dynamic_trees` restores dynamic Hexerei mahogany, willow,
and witch hazel trees. The CurseForge Dynamic Trees addons for Aether and Twilight Forest remain
active, along with `better_malum_dynamic_trees`.

TaCZ 1.1.8 is active with playerAnimator and client-only Accelerated Rendering. Create Armorer,
Applied Armorer, and Immersive Armorer remain external gun-pack ZIPs under `tacz/`. Their benches
progress from brass factory manufacture through post-AE2 Impossible and Electrical milestones.
Their content namespaces are TaCZ pack IDs, not Forge mod IDs; recipe guards use the loaded TaCZ
mod, while candidate validation checks that the three packaged ZIPs match their manifests.

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

Tinkers' Construct and the supported TCon addon melee mappings have client-side Epic Fight
trail skins in `kubejs/assets/<namespace>/item_skins/`. These supply blade/head-local endpoints,
lifetime and the native swing-trail texture; capability mappings alone do not define a trail.
Short blades, swords, long blades, heavy blades, katanas, tool heads and polearms use distinct
spans. The skins do not replace item renderers or change movesets/stats. Tinkers Integration's
native material-colour hook tints modifiable tools; neutral silver is the authored fallback.
Ranged and fist mappings intentionally receive no blade ribbon. The Minecraft-free
`TinkerWeaponTrailsTest` checks coverage and complete resource fields, not native visibility,
material tint appearance or exact model alignment; those remain in-game visual checks.

Complicated Bees, EMI Ores, Forgotten Ruins, Ice and Fire, Hexerei, Malum, Occultism,
Goety, Ars Elemental, Ars Creo, Ars Energistique, Polymorph, Supplementaries, Amendments, Genetic
Animals, Fowl Play, Advanced Chimneys, and Realistic Block Physics are active. Farmer's Delight,
Ube's Delight, Farmer's Respite, and Brewin' and Chewin' are the active Delight food-and-drink
set. Supported ordinary inventory/container paths use Quark's native sorting; separate
Sophisticated screens retain their own native sorting. Journal's custom Sort/Quick Stack/Trash/Undo
utilities remain retired; see [native inventory parity](inventory-parity.md).
The maintained `config/adchimneys/` definitions cover active furnace,
burner, smeltery, and generator emitters.

Create Sifting 1.8.6 is active with Create 6.0.8. String-mesh hand sifting is the
pre-power ore separation path; standard and Brass Sifters automate the same recipe
language. Advanced and custom meshes are hidden and unobtainable. Create fan-based
Bulk Washing has no recipes; water treatment belongs to Spouting.

Claustrophobic Dungeons, Dungeon Crawl, Create Big Cannons, Reliquary, Undergarden, Deeper Darker,
Temperature Bands, the standalone coolant mod, the redundant fission-reactor mod, and Create New Age remain retired.
Forbidden and Arcanus, Roots Classic, Mahoutsukai, Eidolon, Theurgy, Psi, and Hex Casting have no
active Packwiz manifests and are inactive unless deliberately reintroduced.

FTB Quests, its Better Content integration, FTB Teams, FTB XMod Compat, and FTB Filter
System are retired. FTB Library remains required by Satako, and independent FTB Backups
remains active. Learning is owned by Threads, loading lessons, hover, and native surfaces.

## Runtime and memory policy

`release.main.kts` is the only fresh tested distribution path. It stages clean custom-mod builds,
packages once, and tests the unchanged candidate pair. Runtime fixtures extract those candidates
and change disposable EULA, authentication, and port settings, plus disable FTB Backups'
scheduled backups inside the fixture. The production backup configuration is unchanged.
The production server profile
uses the root 4 GiB initial and 16 GiB maximum heap baseline.

Full-pack memory pressure is primarily a content/profile question. Do not remove active content or
promise a low-memory target without a new measured comparison against current manifests. Model,
texture, atlas, native/render, worldgen, and decorative surfaces must be measured together; JVM
flag changes alone do not establish a smaller supported profile.

Runtime recipe-audit and block-hardness diagnostics ship disabled. Explicitly authorized full
Debug through `./test.main.kts debug` includes the packaged runtime-data dumper, requires a complete
snapshot, and atomically refreshes ignored `generated/runtime-dumps/`. Incomplete snapshots never
replace the last complete one. The public tiers are Dev, Dist and Debug; `server` and `all` are not
public selectors. The focused `--target server-ready` checks readiness only, not snapshot promotion.
See [testing](testing.md) for authorization, candidate binding and the full Debug procedure.

## Shader block motion classification

The active Complementary Reimagined archive limits `block.10005` grass motion to flexible plants.
Full cubes and rigid assemblies—including Better Content Fire/Ice and Fire grass blocks, Tinkers' Construct slime
grass blocks, tree roots and branches, hedges, planters, flower boxes, pots, doors, and cactus
structures—must remain outside that class so their vertices do not deform like grass. Additions need
a model-shape audit: plant stems, crops, vines, flowers, ferns, and saplings may use grass motion;
host blocks, wood, containers, and multipart rigid supports may not. The focused static archive
contract freezes both the 81 audited exclusions and representative flexible inclusions.

Realistic Ores uses Complementary's `block.10024` modded-ore material class rather than the
full-block emissive class. The active shader options set `GLOWING_ORE_MASTER=2`, so the shader
detects and emits only contrasting mineral pixels. The focused archive contract freezes all 16
canonical stone/deepslate deposits and all 312 generated Excavated Variants deposits across the
39 active host stones; an ore family or host-stone change must update that classification together.

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

`better_exploration_load_control` owns the foreground tick-budget governor. After a 100-tick sample warmup it
temporarily overrides only Distant Horizons' optional distant generation when the sliding p95
exceeds 50 ms or a tick exceeds 100 ms. It clears its in-memory override only after 600 consecutive
ticks with p95 at or below 40 ms and no tick above 100 ms, and never enables a user-disabled DH
setting. `/better_exploration_load_control performance_status` reports p50/p95/p99/max and override state.

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

Twilight Forest and Fallout Wastelands are Creating Space destinations declared under
`kubejs/data/*/creatingspace/rocket_accessible_dimension/`. Lost Cities is disabled: its manifest,
profiles, rocket destination and structure exclusions are retained outside active content under
[`disabled/lost-cities/`](../disabled/lost-cities/README.md). Its Earth-orbit adjacency and crafting
namespace are inactive; compatibility implementations remain in `better_compat_fixes`, guarded by
mod presence/version, with the pack's Lost Cities serialization setting explicitly off.

The Flesh That Hates remains installed and unchanged; the former Lost Cities ownership is no longer
an active integration. This cut does not add or retarget TFTH ecology to another dimension or to
Depth Director encounters. Its six Mushroom Fields structures remain disabled by
`datapacks/worldgen_compat_fixes`, and `better_compat_fixes` still disables its unconditional client
proximity-music scan. Existing entity, combat, evolution and ordinary Records-channel sound behavior
is unchanged.

`datapacks/hyle_deep` owns the exhaustive Hyle/Unearthed deep-stone pass beginning at Y -64.
`better_compat_fixes` moves Hyle and SGI terrain conforming to decoration tail, completes only
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

RotaVision 1.0.2 shows a translucent context-oriented ghost for every held BlockItem, even before a
manual rotation is selected. Better Content Fixes asks the server for a rate-limited, read-only RBP
evaluation of the proposed states: teal is supported in the current server snapshot, pulsing amber
predicts a fall or crushing failure, red remains vanilla-invalid placement, and white means the result
is unmanaged, unavailable, pending, or stale. This is an advisory snapshot rather than a future
stability guarantee; it never blocks placement, schedules physics, or mutates RBP stability data.
Beds, doors, and double plants include their companion state. Building Gadgets and Create schematic
systems are intentionally outside the v1 preview scope.

## Custom compatibility ownership

`better_world_management` owns dedicated-server succession and the persistent schematic library.
`better_player_traces` owns world-local, bounded movement and annotation data under the active save; it
does not composite the viewport or import server-root legacy data across generations.

`better_compat_fixes` owns the active compatibility repairs for Dynamic Trees/Aether, Hyle,
Unearthed soil and farmland, Better Content Fire ground, C2ME safe-random noise, Weather2 fog under Oculus,
PVJ Nether groundcover, TFTH proximity audio, and Dimension Font bounded placement. Dynamic Trees
falling trees remain item-drop only. Dimension Font layout version 3 is new-world data and does not
deserialize old pieces.

`better_pillager_campaigns` bounds its loaded-column sampling, route search, and spawn production; it must
never load terrain to seek a route. `better_settlement_roads` and `better_village_fortifications` likewise keep tick work
bounded and clean transient state. `better-spawns` always owns first-join spectator scouting and
permanent spawn locking. Its active `progression` embark mode asks World Lifecycle Manager for the
current lineage policy: new lineages receive site-only onboarding, class perks expose only their
unlocked classes, and unlocking all six classes exposes the bounded Embark point-buy. World
Lifecycle Manager separately owns ordered successor spawn-biome preferences.
The pack's default preference list contains the same six safe temperate biomes as first-join
onboarding. A successor attempt checks the final shared-spawn feet biome before publication.
Once a player joins a candidate, Lifecycle Manager writes an inhabited marker in that world;
the supervisor preserves the candidate and transaction if the marker or saved player data is
present, including after a failed health check or interrupted retry.

## Heat and pollution authority

`better_industrial_heat` owns industrial heat storage, transfer, pipes, ambient bridging, hot water, and coolant
exchange. Create: Power Grid retains its electrical and device-overheat simulation; its optional
`ThermalBehaviour` adapter exposes that temperature to HeatSync without creating a second heat API.

`better_chemlib_hazards` owns contained high-energy chemistry, decay heat, criticality, radiation, and
mass-debit semantics. It has no reactor power ladder or atmospheric scheduler. Released gases
become native AdPother pollutant blocks at the configured contained-mass boundary; AdPother then
owns movement, wind, impacts, detection, protection, emitters, chimneys, filters, and cleanup.
PneumaticCraft retains native pressure and thermo-plant behavior, with explicit normal-air and
chemical-service ledgers at the Latent boundary.

Rebuild and deploy custom JARs through [Custom Mod Workspace](custom-mod-workspace.md). Run pack
suites only when explicitly ordered, following [Testing and fresh distributions](testing.md).
