# Lineage Endgame: Entropy Caching and Condemnation

This is the durable design contract and completion roadmap for the pack's intended
lineage-scale endgame. Pack integration belongs here; mod implementation and local operating
instructions belong in the owning source repository. Source establishes implemented behavior;
the roadmap below describes proposed work, not completed features or runtime acceptance.
Threads currently foreshadows these ideas; it does not implement entropy generation,
Condemnation, or curses.

Related subjects: [progression](progression.md), [Threads](threads.md),
[death](death_system.md), [teaching](better_discovery_guides.md), and
[testing](testing.md).

## Two systems above a world

**Entropy caching** is the long-horizon persistent economy. Its structural inspiration is an idler economy, but its in-world quantity is entropy rather than antimatter. Entropy belongs to a Prestige lineage above any individual world. Worlds are therefore capable of becoming expendable production environments. World Lifecycle Manager is the persistence boundary because it already separates world-local state, lineage state, and successor-world handling.

**Condemnation** lets a player irreversibly curse the current world. Each curse materially increases danger and lineage value: the player is wagering the world, not selecting a difficulty preset. Extreme Condemnation may make a world unsustainable and force Prestige.

The primary yield model is survival time under active severity:

$$Y=\int S(t)\,dt$$

Curse active plus world still functioning means value accumulates. Every meaningful interval survived matters, whether it is spent fighting, retreating, fortifying, repairing, evacuating, or preserving redundant infrastructure. Yield should increase convexly with severity so brief survival at extreme risk can outperform long safe farming. Escalation is effectively irreversible. Exact banking rules for voluntary versus forced Prestige remain **UNKNOWN**.

## Curses as strategic systems

A curse is generally an apocalypse-scale actor, process, or altered world rule, not a statistics multiplier. Its reusable model is:

> Agenda → objectives → available actions → victory condition

Strategic reasoning may use territory, settlements, routes, energy, production, resources, defensive positions, other factions, entropy infrastructure, and asset survival. Pillager Campaigns is the present architectural precedent: canonical strategic state is separate from Minecraft-world execution, with logistics, persistence, retreat, and objectives.

Pre-endgame combat remains readable, counterable, intentionally flawed videogame combat. Condemned actors instead optimize for their agendas. They may refuse unfavorable engagements, preserve assets, attack logistics, blockade territory, exploit absence, abandon obsolete objectives, cooperate temporarily, fight other curses, or pursue conditions that make the world nonviable. Their unfairness should arise from strategic agency, not arbitrary cheating or unreadable unavoidable attacks.

Multiple curses must coexist. None may assume it is the only hostile authority, that the player is always its priority, or that all enemies are allies. Desired spectacle comes from systems colliding: threats destroy or feed on one another, contest territory, flee across player infrastructure, and become temporary de facto allies when objectives coincide.

Directional invasion remains foundational. Threats have geographic origin, travel across the Overworld, interact with routes and settlements, and can be perceived before contact. Condemnation escalates that spatial grammar rather than replacing it with spawn-near-player events.

## In-universe persistence and narrative

The player's lineage is not necessarily the sole user of entropy caching. Advanced actors may maintain their own caches, persistent-value mechanisms, or analogous escalation systems under different terminology. This must be demonstrated through functioning mechanics rather than exposition.

A key encounter grammar is mutual recognition: the player penetrates an installation for another reason, recognizes an unexpected cache, and the owner reacts because recognition reveals the player's own capabilities. Both parties learn something neither expected. Advanced actors are not omniscient.

Condemnation rewards lineage power and major narrative developments. Story should arise through systems, encounters, repeated names, recovered technology, infrastructure, behavior, and consequences that persist across Prestige. Defeat can advance knowledge. A destroyed world is not narratively erased; it may leave discoveries, remembered encounters, unlocks, and consequences in later generations.

## Lore communication contract

- Imply before explaining. Use incidental terms, repeated proper-noun-like labels, equipment, behavior, and systemic parallels.
- Mechanics are stronger evidence than text. A working cache is better than a book explaining one.
- Do not immediately explain every name. Recognition across long intervals is part of the reward.
- The player's path must not feel uniquely authored for them. Other actors have independent progress, infrastructure, failures, and interests.
- Other actors describe analogous mechanics through their own concepts, never transparent player-facing rates or tiers.
- Preserve uncertainty between independent discovery, common origin, inheritance, convergence, cross-world interaction, and other explanations.
- Preserve mutual ignorance. Discoveries can surprise both sides.
- Prestige failure is canon-compatible because the lineage persists.

## Current resources and implementation boundary

The canonical implementation spelling is **World Condenser**, item/mod namespace
`better_world_management:world_condenser_interface`. “World Condensor” refers to the same
planned subject, not a second machine. The source repository is
[`better-world-management`](../../mod_source/better-world-management/README.md);
its documentation also calls the system World Lifecycle Manager. Java classes still use
`Prestige*` names. Those names are useful search terms, not separate endgame implementations.

The resource map covers authored pack/source inputs. Generated builds, retained worlds and
old receipts are not evidence that the intended endgame works today. No dedicated entropy
cache, Condemnation implementation, authored curse roster, or curse-specific art/audio was
identified in the current maintained pack and custom-mod sources.

### World Condenser: reusable implemented foundation

Paths in the following table are relative to `/home/dev/mod_source/better-world-management/`
unless another root is specified. Java implementation files are under
`src/main/java/com/bettercontent/betterworldmanagement/`.

| Resource | Current role and limitation |
|---|---|
| `README.md`, `AGENTS.md` | Lifecycle scope, local build/verification commands, dedicated-server restriction and operator workflow. |
| `PrestigeRegistry.java`, `PrestigeMod.java`, `PrestigeClient.java` | Interface block/item/block-entity/menu registration and client wiring. |
| `WorldCondenserAssembly.java`, `WorldCondenserInterfaceBlock.java`, `WorldCondenserBlockEntity.java` | Validates an unbound Dimensional Font two blocks below the interface with air between; opens the machine and maintains the staged magical-pour effect. No entropy/fluid production is implemented. |
| `WorldCondenserMenu.java`, `WorldCondenserScreen.java`, `WorldCondenserLayout.java`, `WorldCondenserAccess.java` | Configure, Schematics and Perks tabs; physical versus remote access and operator-only staged commit. No Condemnation/cache tab exists. |
| `PrestigeNetwork.java`, `PrestigeClientState.java`, `PrestigeLimits.java` | Server-authoritative actions, bounded state transfer and client display state. New irreversible actions must extend server validation, not just client buttons. |
| `PrestigeService.java`, `PrestigeCoordinator.java`, `PrestigeContracts.java` | Draft/stage/commit, world/lineage/transaction identity, successor landing and health contracts. Current lifecycle schemas are v5; this is not an entropy ledger. |
| `PrestigePerks.java` | Existing generation-funded graph: biome selection, six starting classes, four embark-budget upgrades and starter Schematicannon; one upcoming-generation point, capped at twelve. Entropy does not fund these perks today. |
| `api/LineageApi.java`, `api/LineagePlayerDataApi.java`, `LineagePlayerDataStore.java`, `SingleplayerLineageStore.java` | Shared lineage identity and namespaced per-player lineage NBT. Integrated saves have distinct durable bindings; copied saves retain theirs. These APIs do not provide a shared transactional entropy economy. |
| `SchematicLibrary.java`, `SchematicLocalStore.java`, `SchematicDownloadStore.java`, `SchematicSelector.java` | Sanitized operator publication and local download of persistent Create plans. Plans are not carried physical inventories or free construction resources. |
| `SchematicannonSubstitutions.java`, `SchematicannonSubstitutionNetwork.java`, associated mixins | Persistent per-cannon material fallbacks; useful successor rebuilding infrastructure, not an endgame reward economy. |
| `api/event/WorldCondenserAccessedEvent.java`, `api/event/SchematicPublishedEvent.java`, `api/event/SchematicSubstitutionEvent.java` | Existing factual integration seams; do not treat machine access as proof of succession, entropy banking or condemnation. |
| `InitialSpawnCreation.java`, `InitialSpawnSeedSearch.java`, `IncrementalLandingSearch.java`, `api/InitialSpawnService.java` | Initial/successor spawn support; keep successor safety independent of the hostile world's viability. |
| Pack `better-world-management-server.sh`; source `PrestigeArchiveVerifier.java` | Supervised stop, strict cold-archive verification, successor attempts, rollback and exactly-once lineage advancement. Entropy settlement and forced-Prestige semantics are not present. |
| Pack `config/better_world_management-biomes.txt` | Allowed successor biome preferences. |

The Condenser is already an accessible reset mechanism, **not yet a complete endgame
production machine**. On integrated single-player servers, reset, perks, lineage schematics,
Condenser operation and lifecycle commands are disabled. Durable lineage identity alone does
not close that platform gap. On dedicated servers players configure/stage; a nearby operator
commits the reset.

### Acquisition, art and player-facing resources

| Owner/path | Resource |
|---|---|
| Pack `kubejs/server_scripts/progression/80_tech_palette.js` | Removes other Condenser output recipes and installs `kubejs:tech/world_condenser_interface_meteor_gate`: four AE2 Sky Stone blocks, four Create brass sheets and one obsidian. This is a meteor-material gate, not proof of late-game mastery. |
| World-management `src/main/resources/data/better_world_management/recipes/world_condenser_interface.json` | Standalone fallback recipe uses reinforced slate, brass sheets and obsidian; overridden by the pack. |
| World-management `src/main/resources/assets/better_world_management/` | `blockstates/world_condenser_interface.json`, block/item models, `lang/en_us.json`, and two shipped block textures (`world_condenser_interface_side.png`, `world_condenser_interface_top.png`). |
| World-management `src/main/resources/data/` | Interface block loot table and `minecraft/tags/blocks/mineable/pickaxe.json`. |
| World-management [`art/README.md`](../../mod_source/better-world-management/art/README.md), `art/source-textures/`, `tools/DownsampleTextures.java` | Three 1254-pixel authored/generated master PNGs (hull, side, top) and the existing texture downsampler. This source-repository utility is not the retired pack `tools/` tree. |
| [`better-dimension-fonts`](../../mod_source/better-dimension-fonts/README.md) | Generated expedition Fonts; harvesting breaks the binding, producing the replanted unbound Condenser base. The separate Font Pourer handles expedition libations/charge; it is not a Condenser entropy input. |
| Pack `kubejs/config/hover_annotations.json` | Condenser assembly and operator-commit warnings; related lineage-plan and Schematicannon fallback annotations. |
| Pack [progression](progression.md), [Threads](threads.md), [teaching](better_discovery_guides.md) | Existing acquisition, persistence and dedicated-server caveats. |
| Discovery source `authoring/discoveries.json` and `src/main/resources/data/better_discovery_guides/threads/catalogue.json` | Authored and generated `lineage_successor` discovery: entering a verified successor, not simply using the machine. |
| Discovery source `compat/bettercontent/WorldLifecycleThreads.java` under its main Java package | Lineage visit history and the persistence bridge used by Threads; reused knowledge must remain player/lineage scoped. |
| Discovery source `authoring/masters/lineage_successor.png` and assets under `textures/gui/threads/`, `textures/item/thread_cards/`, `models/item/thread_cards/` | Existing successor master, full/thumbnail reader art, card texture and item model. No curse-specific assets were identified. |
| Discovery source `src/main/resources/assets/better_discovery_guides/death_hints/catalogue.json` | Existing lifecycle/Condenser guidance. New teaching must follow factual outcomes and preserve the lore communication contract above. |
| Pack `mods/better-world-management-0.1.0.jar`, `gradle/active-custom-mods.json` | Shipping artifact and executable custom-mod dependency/staging inventory. Artifact presence is not fresh verification. |

Discovery paths above are rooted at
[`/home/dev/mod_source/better-discovery-guides/`](../../mod_source/better-discovery-guides/README.md).

### Condemnation: design and adjacent systems

- **Direct design:** this document is the current entropy/Condemnation contract. Banking,
  concrete curse content, severity values, cache construction and lineage rewards remain
  unspecified. No executable curse definitions were found.
- **Strategic precedent:**
  [`better-pillager-campaigns`](../../mod_source/better-pillager-campaigns/README.md), especially
  `invasion-core/`, `runner/` and its Forge execution layer, provides deterministic strategic
  traversal, persistence, an effect outbox, loaded-terrain execution and bounded native routing.
  It is not already a general autonomous curse/faction framework. Its ordinary counterable
  encounters must not silently become condemned adversaries.
- **World and player consequences:** [death](death_system.md), Better Deaths Door and
  Better Player Traces supply related life/death/history surfaces. Ordinary death remains
  distinct from world loss; no current death rule establishes forced Prestige.
- **Rebuilding and continuity:** lineage schematics, existing perks and Threads archives are
  concrete reasons to care about another generation. None currently establishes entropy
  value or proves that an advanced NPC has its own persistent cache.

### Existing verification resources

- World-management `src/test/java/com/bettercontent/betterworldmanagement/` includes
  `WorldCondenserAccessTest`, `WorldCondenserLayoutTest`, `PrestigeServiceStateTest`,
  `PrestigeContractsTest`, `PrestigeArchiveVerifierTest`, `PrestigePerksTest`,
  `LineagePlayerDataStoreTest`, `SingleplayerLineageStoreTest` and schematic tests.
- `WorldCondenserGameTests.java` covers the unbound-Font assembly, successor spawn retention,
  packet bounds, safe schematic NBT and substitution behavior.
- The source `src/visualHarness/` supports real-client visual fixtures; it does not prove
  unimplemented entropy/curse UI.
- Pack `WorldCondenserMeteorGateContractTest.kt` and
  `HoverAnnotationLearningSurfaceTest.kt` cover acquisition/teaching contracts.
- Pack `ServerRuntimeTest.kt#oneLineageTransitionCommitsAndArchivesCleanly` and
  `gradle/debug-interactions.json` describe the existing lifecycle integration scenario.
  [Testing](testing.md) limits that smoke to one committed lineage transition and archive,
  not the future economy, forced-world-loss or multi-curse matrix.

These are available checks, not a claim that they were rerun for this documentation work.

## Endgame completion roadmap

### Completion target

A complete loop is: discover and build the Condenser → establish lineage entropy caching →
choose irreversible Condemnation → survive interacting world-scale threats → evacuate or lose
the world → settle value and persistent discoveries safely → enter a successor with meaningful
new capabilities and consequences → encounter further mechanically grounded narrative.
A reset button, a growing number, one boss, or a textual ending alone does not complete it.

The milestones below are proposed implementation order. Detailed mod design should be authored
in each owning repository once the open decisions are resolved; do not create a second competing
pack-wide endgame contract.

### 1. Resolve progression, ownership and irreversible-action rules

Decide and document the following before implementing the economy:

| Decision still UNKNOWN | Proposed planning direction; not an adopted rule |
|---|---|
| Platform completion target | Preserve the dedicated path; treat integrated single-player lifecycle as a separate explicit workstream required before claiming parity. Do not enable destructive single-player reset merely by removing its guard. |
| Who owns entropy and who may condemn a shared world | Distinguish shared world severity from player/lineage entitlement. Define authority, consent, late joiners, offline players and grief prevention; preserve current reset authorization until explicitly redesigned. |
| Acquisition and endgame entry | Keep early successor access separate from unlocking dangerous entropy production. Decide whether the current meteor gate stays, and what later capabilities unlock caching/Condemnation. |
| What counts as functioning survival | Specify clock, pause/offline behavior, loaded/unloaded execution, infrastructure requirements and anti-idle conditions. Elapsed wall time must not accidentally become unlimited safe production. |
| Severity and yield | Select a bounded, convex yield curve, curse-composition law, numeric precision and overflow policy. Severity must reflect enacted danger rather than an unfulfilled declaration. |
| Cache and settlement | Define physical cache versus abstract balance, storage limits, destruction/theft, evacuation, voluntary and forced Prestige awards, spending and respec. Do not silently replace generation-funded perks. |
| Terminal conditions and failure | Give each curse explicit victory conditions and define world nonviability/forced-Prestige admission, warning and recovery. Distinguish intended gameplay failure from a crash or failed successor. |
| Content breadth | Agree the launch curse roster, escalation depth, reward progression and narrative milestones. No approved roster or numeric balance exists yet. |

**Exit:** the player can be told exactly what is irreversible, what can be lost, what persists,
and who has authority. Each decision has an owner and an executable acceptance case.

### 2. Extend lineage transactions and build the entropy economy

Use world management as the persistence boundary. Define versioned world-local curse state,
world-earned/unsettled entropy, durable lineage balances/unlocks and per-player discoveries.
Add stable APIs rather than letting pack scripts mutate private transaction files. Keep shared
balances separate from the existing per-player NBT API unless ownership is explicitly personal.

Implement bounded server-authoritative accrual, cache behavior and spending. Bind settlement to
lineage, generation and reset transaction; journal/idempotently replay changes so stop, retry,
rollback and crash cannot double-bank, double-spend or consume rewards for an unaccepted successor.
Specify how intended world loss differs from infrastructure failure before connecting forced exits.

**Exit:** deterministic economy tests and process-boundary fixtures prove exact awards, reconnect
behavior, independent lineages, invalid-state rejection, numeric limits and crash recovery.
A safe baseline world does not acquire condemned yield through menus or clock manipulation.

### 3. Deliver one complete curse as a vertical slice

Choose the first curse only after milestone 1. Implement agenda → objectives → available actions →
victory condition, geographic origin, observable approach, irreversible activation, escalation,
world effects and terminal behavior. Separate canonical strategic state from Minecraft execution;
reuse the campaign architectural lessons without assuming its current cadence/AI solves this task.

Connect the Condenser's activation/status UI, entropy accrual, a real cache, voluntary evacuation,
forced failure, settlement and successor entry. Ship temporary authoring fixtures only as
non-shipping harness inputs, not command-only substitutes for survival acquisition.

**Exit:** a Survival player can complete the entire loop with real world consequences and one
persistent reward/discovery; cancellation/replayed packets cannot undo a committed curse or
manufacture rewards. Restart/unloaded terrain does not erase the threat or teleport it to a player.

### 4. Prove interacting curses, then expand the roster

Add a second strategically distinct curse before scaling content breadth. Define competing
objectives, diplomacy/hostility, territory/resource contention, attacks on infrastructure,
retreat, target arbitration and curse-specific victory/failure outcomes. A curse must be able to
prioritize another actor over the player. Define what happens when one curse destroys another's
objective or the cache that both contest.

Bound strategy work, effect queues, entity counts, pathfinding and terrain observations. Do not
make permanent remote chunk loading a hidden price of endgame simulation. Preserve readable
pre-endgame campaign behavior outside explicit Condemnation.

**Exit:** native scenarios show two curses materially interfering with each other and the player;
strategy-only simulation is not sufficient. Then implement the agreed full roster and severity
combinations with authored behavior, visuals, sound and progression placement.

### 5. Complete lineage rewards, narrative and teaching

Implement the agreed entropy-funded rewards with meaningful successor changes, not just larger
rates. Preserve ordinary death versus world succession boundaries and document interactions with
the current twelve-point generation graph. Add mechanically functioning non-player cache owners,
mutual-recognition encounters and persistent consequences/discoveries so world loss can advance
knowledge without guaranteeing success.

Teach first activation, enacted danger, cache loss/banking, evacuation, forced loss and successor
changes through native feedback and factual Threads events. Author curse/cache art and audio;
reuse existing Condenser/successor masters where appropriate rather than implying missing assets
already exist. Preserve uncertainty in lore while making irreversible gameplay consequences clear.

**Exit:** at least one later-generation encounter reacts to an actual earlier outcome, and a
player can understand risk, earned versus banked value and persistence without an external guide.
No quest graph or retired FTB completion ledger is reintroduced.

### 6. Qualify the complete supported-platform loop

Extend current source, native and pack scenarios rather than treating existing lifecycle smoke
as endgame acceptance. The matrix must include:

- Normal and extreme severity; first and later generations; voluntary evacuation and intended
  forced world loss; cache survival/destruction under the adopted rules.
- Two or more simultaneous curses, hostile interference, destroyed objectives, player absence,
  unloaded/reloaded terrain and finite performance/resource budgets.
- Ordinary death, logout/reconnect, all players offline, late joining, concurrent operators,
  rejected remote/nonoperator actions and replayed/out-of-order client requests.
- Process interruption during accrual, spend, staging, archive, settlement and successor health;
  exhausted successor attempts and rollback; exactly-once balance and lineage advancement.
- Existing saves with no endgame state, malformed/mismatched state and the explicitly chosen
  compatibility policy; independent saves cannot leak lineage rewards or discoveries.
- Real-client Condenser/cache/curse displays and warnings at relevant GUI scales, plus readable
  approach/escalation/world-loss feedback. Integrated single-player must pass its own lifecycle
  cases before parity is claimed; current disabled behavior is not acceptance of that future path.

UI/source checks, simulated agents and a successful server join are not interchangeable
with native behavior or visual acceptance.

**Exit:** the same qualified artifacts demonstrate the agreed complete loop, persistent rewards,
non-player recognition and multi-curse consequences on every platform claimed as supported.
Unimplemented platforms, absent roster entries and unobserved runtime behavior remain explicit gaps.

## Compact contract

> Entropy caching makes worlds economically expendable. Condemnation lets the player deliberately increase the value of every moment a threatened world survives. Curses are autonomous apocalypse-scale systems with agendas and victory conditions, capable of fighting the player and each other. Extreme failure may force Prestige but still advances the lineage. Narrative progression comes largely from mechanically grounded evidence that other actors have encountered, understood, or exploited analogous lineage-scale phenomena—often revealed accidentally, with neither side possessing complete information.
