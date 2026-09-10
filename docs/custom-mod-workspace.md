# Custom Mod Workspace

Custom mod sources are independent repositories under a dedicated source directory in the common workspace. Worklane uses `/home/dev`, so the modpack is checked out at `/home/dev/better-content-modpack` and each custom mod is checked out at `/home/dev/mod_source/<repository>`. Other environments may use a different workspace root, but must preserve the `mod_source/<repository>` layout beside the modpack. Never recreate custom-mod source trees inside the modpack or at the workspace top level.

Every canonical repository is hosted at `https://github.com/better-content/<repository>.git`. Runtime IDs are a clean break from earlier development identifiers; no legacy world or config migration is supported.

## Build And Deploy

Run the listed validation and staging command from the custom-mod repository. `stageRuntimeJar` writes the deployable reobfuscated JAR to the canonical `build/libs/` path shown below.

Copy that JAR into `../../better-content-modpack/mods/`, removing any superseded version of the same custom mod. Do not deploy development-mapped or sources JARs.

Repository-local validation does not authorize deployment or pack-level testing. When the user
explicitly requests deployment of one mod, copy only its staged runtime JAR and run:

```sh
packwiz refresh
```

`packwiz refresh` updates the tracked pack index after a JAR replacement. Do not run a pack suite
unless the user separately requests pack-level testing.

When the user explicitly requests a fresh tested distribution, run `./release.main.kts` from the
modpack. It requires clean active source repositories, runs every active repository's documented
full verification, stages and validates fresh JARs, deploys them as a group, refreshes the pack,
invokes `dist.sh` once, and runs all granular pack suites against those exact candidates. The
machine-readable release inventory is `gradle/active-custom-mods.json`; this table documents the
same active set for humans. Inventory `dependsOn` edges are release-build order constraints. They
stage typed Better Content API providers before their consumers: Dimension Drink before Economy,
Class Selector and Dimension Drink before Better Content Quests, Dynamic Survival HUD before Better
Content Fixes, WLM before Class Selector, Revival before its three consumers, every domain-event
provider before Threads, and Heat Sync before Latent Chemlib.
This directed build graph is intentionally acyclic; Threads is the downstream event listener and
no provider depends on it.

## Reflection Boundary

Custom Java and Kotlin source must use typed APIs, Forge events, or narrow mapped Mixin
accessors/invokers. Runtime reflection is permitted only for Runtime Data Dumper's diagnostic
adapters and direct adapter tests listed by exact repository-relative path in
`gradle/reflection-allowlist.txt`. The fresh release preflight scans every Git repository in the
canonical `mod_source/` workspace, and the staged-JAR gate inspects every active custom mod before
deployment; an unlisted reflective call aborts the release.
Optional integrations use explicit loaded-mod and supported-version guards. Missing optional mods
remain inert, while API drift in an expected pinned integration is a visible validation failure.

The fresh-dist flow also performs a local-only source revision check before building. Each bundled
custom JAR records its repository, mod ID, and source `HEAD` in
`META-INF/better-content-source.properties`. The release evidence reports whether each local
checkout is unchanged, changed, or based on a legacy JAR without an identity. This check never
fetches, pulls, merges, or contacts a remote. Source-identical annotated JARs are reused; changed or
legacy JARs are rebuilt, and every staged release artifact retains the source metadata.

## Canonical Active Inventory

| Repository | Mod ID | Runtime artifact | Local validation and staging |
|---|---|---|---|
| [arcane-chunk-loaders](https://github.com/better-content/arcane-chunk-loaders) | `arcane_chunk_loaders` | `arcane-chunk-loaders-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-content-fixes](https://github.com/better-content/better-content-fixes) | `better_content_fixes` | `better-content-fixes-0.1.7.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-content-economy](https://github.com/better-content/better-content-economy) | `better_content_economy` | `better-content-economy-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-content-quests](https://github.com/better-content/better-content-quests) | `better_content_quests` | `better-content-quests-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-content-threads](https://github.com/better-content/better-content-threads) | `better_content_threads` | `better-content-threads-1.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [bumblezone-cultivars](https://github.com/better-content/bumblezone-cultivars) | `bumblezone_cultivars` | `bumblezone-cultivars-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [class-selector](https://github.com/better-content/class-selector) | `class_selector` | `class-selector-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [create-train-fuel-scaling](https://github.com/better-content/create-train-fuel-scaling) | `create_train_fuel_scaling` | `create-train-fuel-scaling-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [create-transmission-loss](https://github.com/better-content/create-transmission-loss) | `create_transmission_loss` | `create-transmission-loss-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [depth-director](https://github.com/better-content/depth-director) | `depth_director` | `depth-director-0.2.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [dimension-drink](https://github.com/better-content/dimension-drink) | `dimension_drink` | `dimension-drink-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [dynamic-survival-hud](https://github.com/better-content/dynamic-survival-hud) | `dynamic_survival_hud` | `dynamic-survival-hud-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [dynamic-trees-malum](https://github.com/better-content/dynamic-trees-malum) | `dynamic_trees_malum` | `dynamic-trees-malum-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [heat-sync](https://github.com/better-content/heat-sync) | `heat_sync` | `heat-sync-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [immersive-weathering-sampler](https://github.com/better-content/immersive-weathering-sampler) | `immersive_weathering_sampler` | `immersive-weathering-sampler-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [latent-chemlib](https://github.com/better-content/latent-chemlib) | `latent_chemlib` | `latent-chemlib-0.2.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [mining-helmet](https://github.com/better-content/mining-helmet) | `mining_helmet` | `mining-helmet-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [oc2r-create-bridge](https://github.com/better-content/oc2r-create-bridge) | `oc2r_create_bridge` | `oc2r-create-bridge-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [oc2r-wireless-pubsub](https://github.com/better-content/oc2r-wireless-pubsub) | `oc2r_wireless_pubsub` | `oc2r-wireless-pubsub-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [pillager-campaigns](https://github.com/better-content/pillager-campaigns) | `pillager_campaigns` | `pillager-campaigns-0.5.4.jar` | `./gradlew verifyFull verifyWorld stageRuntimeJar` |
| [world-lifecycle-manager](https://github.com/better-content/world-lifecycle-manager) | `world_lifecycle_manager` | `world-lifecycle-manager-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [procedural-bouquets](https://github.com/better-content/procedural-bouquets) | `procedural_bouquets` | `procedural-bouquets-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [rail-beetle](https://github.com/better-content/rail-beetle) | `rail_beetle` | `rail-beetle-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [realistic-ores](https://github.com/better-content/realistic-ores) | `realistic_ores` | `realistic-ores-0.2.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [ratlantis-logistics](https://github.com/better-content/ratlantis-logistics) | `ratlantis_logistics` | `ratlantis-logistics-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [runtime-data-dumper](https://github.com/better-content/runtime-data-dumper) | `runtime_data_dumper` | `runtime-data-dumper-0.1.0.jar` | `./gradlew build stageRuntimeJar` |
| [downed-player-revival](https://github.com/better-content/downed-player-revival) | `downed_player_revival` | `downed-player-revival-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [rpg-stats](https://github.com/better-content/rpg-stats) | `rpg_stats` | `rpg-stats-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [settlement-roads](https://github.com/better-content/settlement-roads) | `settlement_roads` | `settlement-roads-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [systemic-salience](https://github.com/better-content/systemic-salience) | `systemic_salience` | `systemic-salience-0.1.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [tinkers-construct-affixes](https://github.com/better-content/tinkers-construct-affixes) | `tinkers_construct_affixes` | `tinkers-construct-affixes-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [player-traces](https://github.com/better-content/player-traces) | `player_traces` | `player-traces-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [village-walls](https://github.com/better-content/village-walls) | `village_walls` | `village-walls-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [water-survival](https://github.com/better-content/water-survival) | `water_survival` | `water-survival-1.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |

## Validation-Only Repositories

These Better Content repositories remain available for source validation but are not active modpack
content. Do not copy their runtime JARs into `mods/` unless the corresponding retired features are
explicitly activated.

| Repository | Reason | Local validation and staging |
|---|---|---|
| [dynamic-trees-dimension-compat](https://github.com/better-content/dynamic-trees-dimension-compat) | The addon requires The Undergarden, which the pack retired. | `./gradlew runData verifyFull` |
| [dynamic-trees-hexerei](https://github.com/better-content/dynamic-trees-hexerei) | Nature's Spirit owns mahogany ecology; the redundant Hexerei tree addon is retired. | `./gradlew runData verifyFull` |
