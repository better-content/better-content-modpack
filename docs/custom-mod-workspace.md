# Custom Mod Workspace

Custom mod sources are independent repositories under a dedicated source directory in the common workspace. Worklane uses `/home/dev`, so the modpack is checked out at `/home/dev/better-content-modpack` and each custom mod is checked out at `/home/dev/mod_source/<repository>`. Other environments may use a different workspace root, but must preserve the `mod_source/<repository>` layout beside the modpack. Never recreate custom-mod source trees inside the modpack or at the workspace top level.

Every canonical repository is hosted at `https://github.com/better-content/<repository>.git`. Runtime IDs are a clean break from earlier development identifiers; no legacy world or config migration is supported.

## Build And Deploy

Run the listed validation and staging command from the custom-mod repository. `stageRuntimeJar` writes the deployable reobfuscated JAR to the canonical `build/libs/` path shown below.

Copy that JAR into `../../better-content-modpack/mods/`, removing any superseded version of the same custom mod. Do not deploy development-mapped or sources JARs.

Repository-local validation does not authorize deployment or pack-level testing. When the user
explicitly requests deployment of one mod, copy only its staged runtime JAR and run:

```sh
./test.main.kts dev
```

Dev checks fast contracts and the diff without changing Packwiz hashes in a shared workspace.
Fresh-dist preparation refreshes the tracked pack index after the deployed set settles.
Do not run Dist or Debug unless the user separately requests pack-level testing.

When the user explicitly requests a fresh tested distribution, run `./release.main.kts` from the
modpack. It requires clean active source repositories, reuses valid source-identical bundled JARs,
runs documented verification for changed or unannotated sources, stages and validates their fresh
JARs, deploys the staged set as a group, refreshes the pack, invokes `dist.sh` once, and runs
the Dist tier against those exact candidates. The
machine-readable release inventory is `gradle/active-custom-mods.json`; this table documents the
same active set for humans. Inventory `dependsOn` edges are release-build order constraints. They
stage typed Better Content API providers before their consumers: Dimension Drink before Economy,
Dynamic Survival HUD before Better
Content Fixes and Revival, WLM before Class Selector, Revival before its three consumers, every domain-event
provider before Threads, Heat Sync before Latent Chemlib and Airtight Machinery, and Bumblezone Cultivars
before Ratlantis Logistics so rats use the typed propagule catalogue.
This directed build graph is intentionally acyclic; Threads is the downstream event listener and
no provider depends on it.

`--skip-tests` is reserved for an explicitly test-free fresh-dist request. It retains unchanged
artifact reuse, stages changed sources without verification, and skips pack suites. There is no
implicit forced-rebuild mode.

Direct full `./test.main.kts debug` explicitly selects forced rebuild mode: all 44 active
repositories must be clean, every manifest-listed verification runs, and every runtime JAR is
rebuilt after `clean` regardless of matching source metadata. It packages one new candidate
pair and runs complete Dist and Debug on identical ZIP hashes. Targeted and queued Debug
continue to use an existing candidate.

## Reproducible Build Bootstrap

Every repository owns its official Gradle wrapper and a distribution SHA-256. Use `./gradlew`;
neither a global Gradle installation nor another checkout supplies the launcher. Java 17 is selected
through toolchain discovery, not a machine-specific installation path.

Typed provider consumers accept `BC_CUSTOM_MOD_JAR_DIR`, an explicit directory containing canonical
runtime artifact filenames. A blank override or missing required JAR fails during configuration;
it never silently falls back to another directory. With the variable unset, ordinary local builds
retain the canonical sibling `build/libs/` convention. Release builds pass their own staging
directory, which contains both reused and rebuilt providers in dependency order.

Repository CI checks out the modpack at the immutable commit in its workflow and runs
`.github/scripts/prepare-provider-jars.py --pack-root PATH --repository NAME --output DIRECTORY`.
Revival instead stages the HUD provider from an immutable source checkout pinned in its CI
workflow because the bundled provider baseline predates the injury presentation API.
The standard-library Python 3.11+ helper validates the provider closure, Packwiz SHA-256 entries,
and bundled source identities before staging anything. It does not build providers, warm the pack
cache, deploy JARs, or package the modpack. Update the workflow's pinned baseline deliberately when
a consumer requires a newer provider API; never substitute a floating branch.

The pack's fast reflection-boundary check also inspects custom-mod source files. Its CI workflow
materializes source-only checkouts at the revisions in `.github/ci-source-revisions.json`, including
both validation-only repositories. These checkouts are not built or used as provider artifacts.
Refresh their pins deliberately after reviewed source changes; never skip the reflection check
merely because a fresh runner starts without `mod_source/`.

Plugin selectors are pinned to the versions resolved during hygiene remediation. RPG Stats and
Systemic Salience retain their previously resolved `1.2.0.7-dev-SNAPSHOT` Parchment plugin to avoid
an unrequested toolchain change. Snapshot content and full transitive dependency locking/verification
remain reproducibility limitations requiring a separate reviewed change.

CI Kotlin is SDKMAN-managed `2.2.21` under `$HOME/.sdkman/candidates/kotlin/2.2.21`, activated by
`source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use kotlin 2.2.21` and exposed through
`$HOME/.local/bin`. Pack workflows use Packwiz `v0.0.0-20260906154125-ef87d964f8cb`, matching the
workspace inventory. Heavyweight workflow steps invoke `test.main.kts` and acquire its normal lock;
they are manual-only and are not a routine consequence of a hygiene change.

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

The active set contains 44 custom mods.

| Repository | Mod ID | Runtime artifact | Local validation and staging |
|---|---|---|---|
| [better-airtight-machines](https://github.com/better-content/better-airtight-machines) | `better_airtight_machines` | `better-airtight-machines-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-magic-chunk-anchors](https://github.com/better-content/better-magic-chunk-anchors) | `better_magic_chunk_anchors` | `better-magic-chunk-anchors-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-arena-trials](https://github.com/better-content/better-arena-trials) | `better_arena_trials` | `better-arena-trials-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-spirit-commerce](https://github.com/better-content/better-spirit-commerce) | `better_spirit_commerce` | `better-spirit-commerce-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-compat-fixes](https://github.com/better-content/better-compat-fixes) | `better_compat_fixes` | `better-compat-fixes-0.1.9.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-gameplay-notices](https://github.com/better-content/better-gameplay-notices) | `better_gameplay_notices` | `better-gameplay-notices-1.0.0.jar` | `./gradlew verifyFast stageRuntimeJar` |
| [better-discovery-guides](https://github.com/better-content/better-discovery-guides) | `better_discovery_guides` | `better-discovery-guides-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-bumblezone-crops](https://github.com/better-content/better-bumblezone-crops) | `better_bumblezone_crops` | `better-bumblezone-crops-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-buried-encounters](https://github.com/better-content/better-buried-encounters) | `better_buried_encounters` | `better-buried-encounters-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| better-wildfire | `better_wildfire` | `better-wildfire-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-journal-inventory](https://github.com/better-content/better-journal-inventory) | `better_journal_inventory` | `better-journal-inventory-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-spawns](https://github.com/better-content/better-spawns) | `better_spawns` | `better-spawns-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-create-train-fuel](https://github.com/better-content/better-create-train-fuel) | `better_create_train_fuel` | `better-create-train-fuel-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-create-kinetic-loss](https://github.com/better-content/better-create-kinetic-loss) | `better_create_kinetic_loss` | `better-create-kinetic-loss-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-cave-encounters](https://github.com/better-content/better-cave-encounters) | `better_cave_encounters` | `better-cave-encounters-0.2.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-dimension-fonts](https://github.com/better-content/better-dimension-fonts) | `better_dimension_fonts` | `better-dimension-fonts-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-deaths-door](https://github.com/better-content/better-deaths-door) | `better_deaths_door` | `better-deaths-door-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-survival-hud](https://github.com/better-content/better-survival-hud) | `better_survival_hud` | `better-survival-hud-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-hexerei-dynamic-trees](https://github.com/better-content/better-hexerei-dynamic-trees) | `better_hexerei_dynamic_trees` | `better-hexerei-dynamic-trees-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-malum-dynamic-trees](https://github.com/better-content/better-malum-dynamic-trees) | `better_malum_dynamic_trees` | `better-malum-dynamic-trees-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-exploration-load-control](https://github.com/better-content/better-exploration-load-control) | `better_exploration_load_control` | `better-exploration-load-control-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-industrial-heat](https://github.com/better-content/better-industrial-heat) | `better_industrial_heat` | `better-industrial-heat-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-weathering-offline-progress](https://github.com/better-content/better-weathering-offline-progress) | `better_weathering_offline_progress` | `better-weathering-offline-progress-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-chemlib-hazards](https://github.com/better-content/better-chemlib-hazards) | `better_chemlib_hazards` | `better-chemlib-hazards-0.2.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-mining-lamp](https://github.com/better-content/better-mining-lamp) | `better_mining_lamp` | `better-mining-lamp-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-oc2r-create-controls](https://github.com/better-content/better-oc2r-create-controls) | `better_oc2r_create_controls` | `better-oc2r-create-controls-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-oc2r-wireless-messaging](https://github.com/better-content/better-oc2r-wireless-messaging) | `better_oc2r_wireless_messaging` | `better-oc2r-wireless-messaging-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-pillager-campaigns](https://github.com/better-content/better-pillager-campaigns) | `better_pillager_campaigns` | `better-pillager-campaigns-0.5.4.jar` | `./gradlew verifyFull verifyWorld stageRuntimeJar` |
| [better-player-traces](https://github.com/better-content/better-player-traces) | `better_player_traces` | `better-player-traces-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-craftable-bouquets](https://github.com/better-content/better-craftable-bouquets) | `better_craftable_bouquets` | `better-craftable-bouquets-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-rail-beetle](https://github.com/better-content/better-rail-beetle) | `better_rail_beetle` | `better-rail-beetle-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-ratlantis-logistics](https://github.com/better-content/better-ratlantis-logistics) | `better_ratlantis_logistics` | `better-ratlantis-logistics-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-ore-geology](https://github.com/better-content/better-ore-geology) | `better_ore_geology` | `better-ore-geology-0.2.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-block-placement-preview](https://github.com/better-content/better-block-placement-preview) | `better_block_placement_preview` | `better-block-placement-preview-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-regolith-farming](https://github.com/better-content/better-regolith-farming) | `better_regolith_farming` | `better-regolith-farming-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-rehooked-grappling](https://github.com/better-content/better-rehooked-grappling) | `better_rehooked_grappling` | `better-rehooked-grappling-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-rpg-progression](https://github.com/better-content/better-rpg-progression) | `better_rpg_progression` | `better-rpg-progression-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-runtime-diagnostics](https://github.com/better-content/better-runtime-diagnostics) | `better_runtime_diagnostics` | `better-runtime-diagnostics-0.1.0.jar` | `./gradlew build stageRuntimeJar` |
| [better-settlement-roads](https://github.com/better-content/better-settlement-roads) | `better_settlement_roads` | `better-settlement-roads-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-survival-physiology](https://github.com/better-content/better-survival-physiology) | `better_survival_physiology` | `better-survival-physiology-0.1.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-tinkers-loot-affixes](https://github.com/better-content/better-tinkers-loot-affixes) | `better_tinkers_loot_affixes` | `better-tinkers-loot-affixes-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-village-fortifications](https://github.com/better-content/better-village-fortifications) | `better_village_fortifications` | `better-village-fortifications-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-drinking-water](https://github.com/better-content/better-drinking-water) | `better_drinking_water` | `better-drinking-water-1.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-world-management](https://github.com/better-content/better-world-management) | `better_world_management` | `better-world-management-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |

## Validation-Only Repositories

These Better Content repositories remain available for source validation but are not active modpack
content. Do not copy their runtime JARs into `mods/` unless the corresponding retired features are
explicitly activated.

| Repository | Reason | Local validation and staging |
|---|---|---|
| [dynamic-trees-dimension-compat](https://github.com/better-content/dynamic-trees-dimension-compat) | The addon requires The Undergarden, which the pack retired. | `./gradlew runData verifyFull` |
| [scalable-tnt](https://github.com/better-content/scalable-tnt) | TNT-01 source prototype; validation-only until unit-only authoring is reviewed and runtime/integration/balance acceptance is authorized. | `./gradlew test` |
