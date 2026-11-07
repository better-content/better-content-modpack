# Implementation, ownership and performance

## Custom Mod Workspace

Custom mod sources are independent repositories under a dedicated source directory in the common workspace. Worklane uses `/home/dev`, so the modpack is checked out at `/home/dev/better-content-modpack` and each custom mod is checked out at `/home/dev/mod_source/<repository>`. Other environments may use a different workspace root, but must preserve the `mod_source/<repository>` layout beside the modpack. Never recreate custom-mod source trees inside the modpack or at the workspace top level.

Every canonical repository is hosted at `https://github.com/better-content/<repository>.git`. Runtime IDs are a clean break from earlier development identifiers. No automatic legacy world or config migration is supplied; preserve existing worlds and backups.

### Build And Deploy

Run the listed validation and staging command from the custom-mod repository. `stageRuntimeJar` writes the deployable reobfuscated JAR to the canonical `build/libs/` path shown below.

Copy that JAR into `../../better-content-modpack/mods/` only when deployment is explicitly requested, removing a superseded version of that custom mod. Do not deploy development-mapped or sources JARs. Repository-local validation alone does not authorize deployment, packaging or pack-level testing.

For ordinary pack implementation changes, run `./gradlew test` and `git diff --check` as relevant. Local verification is separate from Smoke, Overworld Tele Soak, full Tele Soak and fresh-distribution acceptance; none automatically runs all source-repository suites. Packwiz refresh belongs only to explicit fresh-distribution preparation after deployment settles.

An explicitly requested fresh distribution uses `./release.main.kts`. The ordinary source-aware route stages changed active runtime sources in dependency order with `stageRuntimeJar` (not `clean` or `verifyFull`), reuses matching bundled artifacts, refreshes Packwiz and packages exactly once. By default it runs **no** runtime tier and writes `package-ready.json` for the unchanged ZIP pair. Select `--runtime-tiers smoke` for Smoke-certified `ready.json`; `--runtime-tiers overworld-tele-soak` and/or `tele-soak` opt into other complete tests on the same pair. A ready marker requires selected Smoke to pass, and never implies unrun tiers. The stricter `--fresh-source-plan` route requires a fresh qualified source and raw JAR for **every** approved active mod, including separately qualified World Management/supervisor bytes, even for a package-only candidate. The user has specifically permitted an **incomplete Cooking JAR** in a Smoke candidate: it loads in pass-through mode with unresolved catalogue data, preserving native food/recipe/crop behavior and making **no Cooking gameplay acceptance claim**. This exception does not qualify Cooking's full migration or World Condenser, and does not apply to the strict all-sources-qualified fresh-source-plan route. There is no `--skip-tests`, forced Debug rebuild or targeted-release bypass. `--use-bundled-mods` explicitly retains existing JARs and never inspects/builds sibling source checkouts; it is not a fresh-source qualification route. Independent visual review is a separate optional tier; see [testing](testing.md#smoke-overworld-tele-soak-tele-soak-and-fresh-distributions).

The machine-readable active set is `gradle/active-custom-mods.json`. Its `dependsOn` graph orders typed providers before consumers; existing bundled providers seed release staging, and newly staged active providers replace them before consumers build. Optional compile-only fixtures can be supplied through `BC_RELEASE_COMPILE_FIXTURE_DIR`; they are recorded build inputs, never implicitly activated or deployed. Celestial Events is now an active typed provider for Fonts and is ordered before that consumer.

Standalone `./test.main.kts smoke` and `./test.main.kts overworld-tele-soak` consume existing packaged artifacts and never build, deploy, refresh or package. The old Dev/Dist/Debug selectors, queue and scenario targets are removed. See [testing](testing.md#smoke-overworld-tele-soak-tele-soak-and-fresh-distributions) for the lifecycle and evidence contract.

### Reproducible Build Bootstrap

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

Plugin selectors are pinned to the versions resolved during hygiene remediation. RPG Stats and
Systemic Salience retain their previously resolved `1.2.0.7-dev-SNAPSHOT` Parchment plugin to avoid
an unrequested toolchain change. Snapshot content and full transitive dependency locking/verification
remain reproducibility limitations requiring a separate reviewed change.

CI Kotlin is SDKMAN-managed `2.2.21` under `$HOME/.sdkman/candidates/kotlin/2.2.21`, activated by
`source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use kotlin 2.2.21` and exposed through
`$HOME/.local/bin`. Pack workflows use Packwiz `v0.0.0-20260906154125-ef87d964f8cb`, matching the
workspace inventory. Heavyweight workflow steps invoke `test.main.kts` and acquire its normal lock;
they are manual-only and are not a routine consequence of a hygiene change.

### Reflection Boundary

Custom Java and Kotlin source must use typed APIs, Forge events, or narrow mapped Mixin
accessors/invokers. Runtime reflection remains a source-ownership concern for narrow diagnostic adapters and their direct tests. The pack no longer scans all sibling checkouts or bytecode as a release prerequisite; regression and API-boundary checks belong to the owning repositories.
Optional integrations use explicit loaded-mod and supported-version guards. Missing optional mods
remain inert, while API drift in an expected pinned integration is a visible validation failure.

The fresh-dist flow also performs a local-only source revision check before building. Each bundled
custom JAR records its repository, mod ID, and source `HEAD` in
`META-INF/better-content-source.properties`. The release evidence reports whether each local
checkout is unchanged, changed, or based on a legacy JAR without an identity. Production input fingerprints detect dirty runtime changes as well as committed revisions; test-only edits do not require a runtime rebuild. This check never fetches, pulls, merges, or contacts a remote. Exported source trees without `.git` use an explicit `source-snapshot-sha256:<production fingerprint>` identity instead of claiming a Git commit. Their production files are enumerated directly, excluding build/cache/test/runtime-evidence trees; missing or incomplete source trees fail closed. Snapshot identities do not satisfy the strict qualified fresh-source contract. Source-identical annotated JARs are reused; changed or legacy JARs are staged without an implicit verification sweep, and every staged release artifact retains source metadata.

### Canonical Active Inventory

The candidate manifest has **54 custom mods** in `gradle/active-custom-mods.json`; this is a target inventory, not a built or Smoke-accepted distribution. It removes Dynamic Trees Dimension Compat, adds Electrochemistry, Seasonal Daylight and Seasonal Trees, and retains experimental Abyssal City and Better Spaceflight. Spaceflight uses the available `e3591959d2c614b71ca96d478ada43f8ac597f81` feasibility source; its unrecovered survival-preview flight, oxygen and destination code is retired. Abyssal City uses `verifyGeneration` and a controlled reobfuscated output. All included source-owned JARs, including unchanged providers, require a fresh isolated rebuild with source/provider/artifact provenance for this consolidation. Finite Water remains excluded; Creating Space and Undergarden are removed. No gameplay qualification, world migration or source-test result follows from manifest membership.

| Repository | Mod ID | Runtime artifact | Local validation and staging |
|---|---|---|---|
| better-manufacturing | `better_manufacturing` | `better-manufacturing-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar`; implementation in progress, not deployed or gameplay-qualified |
| [better-airtight-machines](https://github.com/better-content/better-airtight-machines) | `better_airtight_machines` | `better-airtight-machines-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-magic-chunk-anchors](https://github.com/better-content/better-magic-chunk-anchors) | `better_magic_chunk_anchors` | `better-magic-chunk-anchors-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-arena-trials](https://github.com/better-content/better-arena-trials) | `better_arena_trials` | `better-arena-trials-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-spirit-commerce](https://github.com/better-content/better-spirit-commerce) | `better_spirit_commerce` | `better-spirit-commerce-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-compat-fixes](https://github.com/better-content/better-compat-fixes) | `better_compat_fixes` | `better-compat-fixes-0.1.9.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-gameplay-notices](https://github.com/better-content/better-gameplay-notices) | `better_gameplay_notices` | `better-gameplay-notices-1.0.1.jar` | `./gradlew verifyFast stageRuntimeJar` |
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
| [better-ore-geology](https://github.com/better-content/better-ore-geology) | `better_ore_geology` | `better-ore-geology-0.3.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-block-placement-preview](https://github.com/better-content/better-block-placement-preview) | `better_block_placement_preview` | `better-block-placement-preview-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-regolith-farming](https://github.com/better-content/better-regolith-farming) | `better_regolith_farming` | `better-regolith-farming-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-rehooked-grappling](https://github.com/better-content/better-rehooked-grappling) | `better_rehooked_grappling` | `better-rehooked-grappling-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-rpg-progression](https://github.com/better-content/better-rpg-progression) | `better_rpg_progression` | `better-rpg-progression-1.0.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-runtime-diagnostics](https://github.com/better-content/better-runtime-diagnostics) | `better_runtime_diagnostics` | `better-runtime-diagnostics-0.1.0.jar` | `./gradlew build stageRuntimeJar` |
| [better-settlement-roads](https://github.com/better-content/better-settlement-roads) | `better_settlement_roads` | `better-settlement-roads-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-spicy-farmers](https://github.com/better-content/better-spicy-farmers) | `better_spicy_farmers` | `better-spicy-farmers-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-survival-physiology](https://github.com/better-content/better-survival-physiology) | `better_survival_physiology` | `better-survival-physiology-0.1.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-tinkers-loot-affixes](https://github.com/better-content/better-tinkers-loot-affixes) | `better_tinkers_loot_affixes` | `better-tinkers-loot-affixes-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-village-fortifications](https://github.com/better-content/better-village-fortifications) | `better_village_fortifications` | `better-village-fortifications-1.0.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-drinking-water](https://github.com/better-content/better-drinking-water) | `better_drinking_water` | `better-drinking-water-1.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-world-management](https://github.com/better-content/better-world-management) | `better_world_management` | `better-world-management-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-celestial-events](https://github.com/better-content/better-celestial-events) | `better_celestial_events` | `better-celestial-events-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-wild-fruit-trees](https://github.com/better-content/better-wild-fruit-trees) | `better_wild_fruit_trees` | `better-wild-fruit-trees-0.1.1.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [scalable-tnt](https://github.com/better-content/scalable-tnt) | `scalable_tnt` | `scalable-tnt-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar` |
| [better-cooking](https://github.com/better-content/better-cooking) | `better_cooking` | `better-cooking-0.1.0.jar` | `./gradlew verifyFast stageRuntimeJar`; legacy gameplay policy remains quarantined; separate recipe-only pack policy |
| [better-abyssal-city](https://github.com/better-content/better-abyssal-city) | `better_abyssal_city` | `better-abyssal-city-0.1.0.jar` | isolated `scripts/verify_generation.sh` → reobfuscated output; gameplay unqualified |
| [better-spaceflight](https://github.com/better-content/better-spaceflight) | `better_spaceflight` | `better-spaceflight-0.1.0.jar` | `./gradlew test stageRuntimeJar`; feasibility source only; gameplay unqualified |
| [better-electrochemistry](https://github.com/better-content/better-electrochemistry) | `better_electrochemistry` | `better-electrochemistry-0.1.0.jar` | `./gradlew test stageRuntimeJar`; new-world activation pending gates |
| [better-seasonal-daylight](https://github.com/better-content/better-seasonal-daylight) | `better_seasonal_daylight` | `better-seasonal-daylight-0.1.0.jar` | `./gradlew test stageRuntimeJar`; sole clock owner in new worlds, pending gates |
| [better-seasonal-trees](https://github.com/better-content/better-seasonal-trees) | `better_seasonal_trees` | `better-seasonal-trees-0.1.0.jar` | `./gradlew verifyFull stageRuntimeJar`; new-world activation pending gates |

### Manufacturing ownership and implementation status

The active scope is part-based machine variants, replacing the broader chemistry/recovery program.
`mod_source/better-manufacturing` owns crafting-time calculation and native behavior hooks. The pack
owns recipes, progression and explicit role-specific ratings. Outputs store only versioned
`better_manufacturing:settings`: calculated properties, output identity and material labels. Parts are
consumed normally; no retained parts, wear, refunds or qualification-transfer history are used.
Material reload changes future crafts, not existing machines. Missing/invalid settings use native baseline.

The player-facing model is [power infrastructure plus fitted parts](progression.md#power-progression-at-a-glance):
one processing apparatus can be driven by different energy systems, while its crafted properties remain
independent of the source. This documentation framing changes no recipes, registry IDs, save data or
acceptance status. It does not authorize moving most machines into Hand Workshop or replacing specialist
pressure/chemical/electrical processes with Create recipes.

#### Power and fitted-part implementation cross-reference

Local Java paths below are relative to
[`better-manufacturing/src/main/java/com/bettercontent/manufacturing/`](../../mod_source/better-manufacturing/src/main/java/com/bettercontent/manufacturing/).
These are source-inspection references, not evidence that every optional family works in the assembled pack.

| Surface | Recipe/config evidence | Native behavior hook / boundary |
| --- | --- | --- |
| Rated Press/Mixer and inherited Whisk | `kubejs/server_scripts/progression/20_powered_works.js` | `recipe/PartVariantRecipe.java`, `api/NumericEvaluator.java`, `mixin/WorkshopRateMixins.java`, `integration/create/KineticVariantBehaviour.java`: capture numeric settings, adjust native work and persist the configured machine; power source is not part of the rating. |
| Construction-only parts | `20_powered_works.js`, `30_precision_factory.js`, conditional Create recipe JSONs | Ordinary shaped/shapeless recipes consume parts but do not confer ratings; do not infer performance from an ingredient alone. |
| Steam → SU | `30_precision_factory.js`: Precision Mechanism plus Tough Handle piston rod, not a Brass Machine Block | `integration/createbulk/BulkNative.java`, `mixin/CreatebulkSteamShaftMixin.java`: adjust available shaft capacity within the rated conversion/mechanical limit; retain native boiler efficiency, heat/water supply and speed steps. Heat Sync owns its separate heat buffer, not stored SU. |
| SU → compressed air | `40_thermal_pressure.js`; `defaultconfigs/compressedcreativity-server.toml` | `mixin/CompressedConversionMixin.java` and `mixin/CompressedKineticCostMixin.java`: rated air production pays corresponding kinetic stress. Native pressure/storage rules remain. |
| Compressed air → SU | `kubejs/data/compressedcreativity/recipes/crafting/compressed_air_engine.json`; same server config | `mixin/CompressedConversionMixin.java`: output respects the native 256-RPM ceiling; sub-baseline working output reduces running air cost correspondingly, while higher ratings do not discount air at unchanged capped output. Idle/overstressed accounting remains native. |
| Electricity → SU | `kubejs/data/powergrid/recipes/mechanical_crafting/electric_motor.json`; `defaultconfigs/powergrid-server.toml` | `mixin/PowergridMotorLeverageMixin.java`: the Tough Handle rotor's drive rating adjusts native coil resistance. PowerGrid circuit power determines output; batteries, wiring and thermal behavior remain PowerGrid-owned, not an independent free-SU multiplier. |
| Kinetic transmission | `config/better_create_kinetic_loss-common.toml` | Separate `better-create-kinetic-loss` mod: `network/NetworkScanner.kt` sums component/RPM loss; `mixin/StressCalculatorMixin.kt` adds it to network stress. No shaft-material loss rating or gradual RPM decay is implied. |

Pressure and electrical storage buffer their own medium before conversion. Their capacities, supply
thresholds, charging/discharging behavior and losses must be checked against the pinned implementations;
there is no universal efficiency, runtime or interchangeability promise. Short local shaft runs can avoid
some kinetic transmission demand, but that alone does not establish an air/electric layout as more efficient.

#### Functional acquisition audit boundary

The [Create/addon family ledger](progression.md#create-and-addon-acquisition-audit--source-only-not-a-retier)
reconciles native recipe IDs, pack JSON overrides, KubeJS removals/replacements,
and conditional Manufacturing/fallback branches at **source level**. Its
[93 Create + 73 addon coverage entries](../kubejs/data/better_manufacturing/coverage.json)
are selections for fitted parts (including six excluded entries), not verified
survival acquisitions or a count of recipe edits. Horse Power and Aquatic
Ambitions require separate scrutiny, while decorative Create Deco variants are
not individual manufacturing gates. Construction-only TCon ingredients do not
inherit ratings; the separately configured kinetic transmission cost is not
computed from those parts. The Mixer is not gated by the name of its
`20_powered_works.js` script. The current Millstone requires Nether brick,
whereas the proposal to make its two-feed ore route early and permit local-wood
water/wind roots is explicitly [deferred](design.md#create-power-and-capability-retier).
No candidate-matched loaded full-pack acceptance establishes the entire roster,
its alternate recipes or the Horse Crank's usable output. No gameplay edits or
runtime validation followed from this documentation audit.

#### Existing validation record

The first wave authored candidate settings-bearing recipes and native hooks for Create/Sifter,
PNCR/Compressed, fluids/heat, Power Grid, Clockwork, vehicles and other selected families, plus
construction-only exact-ID overrides. Candidate JSON counts do **not** establish effective
acquisition or native performance. Wave-two owners are validating those families against actual
optional mods and loaded recipes. The earlier isolated Create/Sifter core checkpoint passed
21/21 native GameTests, compilation and JUnit, but did not load optional families. Polymer
processes and casting were also authored; PLA/PP production, downstream consumers, bootstrap
and combined Hazards propylene identity remain unverified. Generic plastic alternatives remain.

Shared `20_powered_works.js` retains millstone stone, press iron block, and all four alloys
in the mechanical-pump root while adding consumed working parts where applicable. Mixer
consumes and binds its **configured native whisk** rather than discarding whisk settings or
adding another handle. The no-Manufacturing shapes remain pack progression but have **not**
been loaded in the bounded native profile. Six isolated Node unit checks (6/6 on
2026-11-02) cover authored gates, construction-only harvester, real PLA/PP TCon
head/handle stat assets, removed legacy rating data, source-order masking and
both conductive-casing branches;
they do **not** load recipes or the mod runtime. A separate actual Create/20
native profile passed 31/31 required GameTests with real KubeJS scripts, pack wood policy,
RecipeManager `byKey`/`matches` of selected active20 recipes and configured whisk inheritance;
31 tests do **not** mean 31 roster outputs or every mounted JSON route was accepted.
Instant crop harvesting has no fabricated work rate; plough debris breaking and roller
excavation retain native work candidates. The later Font ecology script still removes
thermopneumatic plant output before adding its Nether-magma root; the earlier
advanced-tube definition is authored but **not established as effective**. Do not
reopen it based on source presence alone: loaded recipe-manager evidence and progression
review are pending. The later Font script's dead `jet_boots_upgrade_5` end-rod substitution
was removed as a **no-op cleanup**: the earlier 5-bar pressure recipe already consumes
upgrade4, vortex cannon, elytra and dragon breath in both branches, with no end rod to
replace. This does **not** add an Aether gate or establish newly validated acquisition.
The temporary copper/rock `diesel_conversion` entries and all six bespoke
machine-rating JSON files were **removed** after family readers cut over and core
disconnected the engineering catalog reload/network path. The intended diesel and
other machine properties derive from consumed native TCon part stats using bounded
per-machine formulas; this source migration is not a loaded diesel result.
Advanced/custom Sifter mesh acquisition stays closed.

The coverage roster contains 462 selected IDs and five excluded IDs; original 205
material-dependent / 257 recipe-only labels are not final dispositions. The primary
Create shaft/cogwheel/large-cogwheel shaped roots consume handles with Manufacturing,
but pinned native cutting one alloy → six shafts in 200 ticks and deploying
shaft+planks → cogwheel / cogwheel+planks → large cogwheel remain obtainable in
both modes. The former absent-only suppressions were withdrawn. Recipes-create then saved three
exact-ID conditional alternatives: with Manufacturing, sequenced assembly keeps the
original cutting/deploying operation, inputs and six-shaft/200-tick yield where
applicable, then consumes a TCon handle; without Manufacturing it retains the pinned
native one-/two-input recipe. Create's own transitional NBT isolates each recipe ID,
but loaded serializer/RecipeManager behavior is unverified. Native
`create:sequenced_assembly/precision_mechanism` separately has weighted cogwheel
(chance 5) and shaft (chance 2) byproducts. Recipes-create saved an exact-ID
conditional adaptation adding one **consumed handle per native loop (five handles
per five-loop batch)** while retaining the gold plate, five each of cogwheel,
large cogwheel and iron nugget, all 15 original operations, all nine weighted
results and the unchanged no-Manufacturing recipe. That five-handle cost is an
explicit new gameplay tradeoff, not a one-time handle charge. Its isolated pinned
Create-archive source unit passed 2/2; it does **not** prove Forge condition
selection, loaded RecipeManager precedence, physical consumes or native yields.
The shaft/cogwheel rows are source-only construction candidates, not fully
accepted acquisitions. For example,
`create:mechanical_harvester` is reclassified construction-only (ordinary consumed blade),
`pneumaticcraft:pressure_chamber_valve` remains partial while its native assembly alternate
and consumed physical blank await runtime validation. `powergrid:conductive_casing`
is now a source-only construction route: the shared script still removes the cheap native
andesite/zinc item application; with Manufacturing its existing copper casing + red-wire
first deploying step makes an inert blank, then blank + consumed Tinkers large plate
makes the casing. Without Manufacturing the unchanged pack one-step copper/red-wire
route remains. This adds no saved performance setting and has no loaded acquisition
proof. The stationary
and vertical PowerGrid generator housings are construction-only plain Blocks, not
rated settings/BE machines; their current electrical-block/conductive-casing root
and two orientation conversions remain in shared script 50 until the future
recipes-tech exact-ID handoff. `powergrid:empty_circuit` is construction-only: its pinned native cutting
uses one wooden slab for two circuits in 50 ticks; the Manufacturing-only
same-species prepared-slab route pays a physical part while retaining that
cutting yield and work. No material-performance rate is invented. Loaded
acquisition remains unverified. No new PowerGrid acceptance is implied. A
missing direct recipe does not prove unavailability: `powergrid:transformer_small`
is assembled in-world from a paid plated core; `create:small_bogey`,
`pneumaticcraft:drone_interface`, OC2R gateway and the electric compressor/heater
still require acquisition evidence before any exclusion. No invented acquisition
is authorized. `trackwork:simple_wheel` has a
resolved source ownership boundary: the shared transport script currently
removes its family exact-ID recipe and substitutes a KubeJS route without the native
`simple_wheel_part` predecessor. That part itself requires five-wide mechanical crafting,
while Create mechanical crafters are gated at precision_factory; replacing the working
powered_works wheel route with that predecessor would move wheel acquisition a full era.
The working shared route remains unchanged. The vehicle owner aligned the shadowed
family JSON's Manufacturing branch with the same H/kelp/copper shape and part role as
a dormant fallback; `event.remove` still masks that exact ID, so this is **not a second
effective route**. Neither route is newly accepted. Clockwork's `gas_heater`, `steam_generator`
and `blade_controller` are now **construction-only** in source: their exact conditional
Manufacturing recipes consume the prior Tinkers parts as ordinary shaped crafting,
without saved performance settings; native heat/boiler/manual-pitch behavior remains
upstream and no-Manufacturing branches remain shaped. `vs_clockwork:duct` retains its
configured gas-engine component role; exhaust's new paid native-speed response hook is
a source candidate, not a loaded result. Loaded acquisition and runtime behavior have not
been revalidated. The Complicated Bees
`mellarium_temp_unit` and `mellarium_hydroregulator` are construction-only **source
candidates**, not material-rate conversions: native categorical tolerance and product
chance do not support an honest throughput rating. Recipes-other saved the exact
Manufacturing hydro shape with one consumed TCon handle and all original prismarine,
nautilus and base costs; the full nine-slot temperature shape now yields the registered
inert blank, consumed with one handle for the native unit. No-Manufacturing exact-ID
recipes retain pinned originals. Native fuel, chance, yields and settings are unchanged;
its two isolated pinned-JAR source units passed, but neither loaded acquisition nor
biology was revalidated. The adjacent `mellarium_base` has a new **source-only**
Manufacturing recipe using a 4×3 Create mechanical-crafting grid: its eight native
panels and log remain, with one consumed handle and one large plate. This avoids
adding a thirteenth component or silently reducing the native panel cost, but
changes the Manufacturing route from hand crafting to powered mechanical crafting;
review its progression position before acceptance. The conditional no-Manufacturing
branch retains the native eight-panel 3×3 craft. No loaded recipe selection,
mechanical-crafter operation, other output route or multiplayer acquisition has
been checked after this change; do not mark the base verified. The excluded Backpack
hoppers and Clockwork gas tank/vent/sealer stay excluded, not converted. Per-ID candidate/source-route
handoff is maintained with the wave-two pack owner; neither authored overrides nor an
absent route count as accepted conversion.

`mellarium_base` remains explicitly held from acceptance: its cost-preserving
mechanical route is authored, but progression/alternate acquisition and loaded
behavior have not been checked. Do not add a thirteenth
blank or claim the full Manufacturing roster complete from source authoring alone.

Pretty Pipes speed/extraction and high-module construction have source-only
cost-preserving revisions. All seven high modules now have Manufacturing branches
using a 3×4 Create mechanical craft: each keeps its full previous Certus-gated
3×3 ingredient grid and pays one physical part in the extra row. The five
construction-only modules consume a large plate; high speed/extraction consume
a handle and use a bounded native-ID serializer that bakes new settings while
retaining the medium predecessor's unrelated native NBT. Medium speed likewise
uses that serializer to keep its full-grid native iron/sugar cost and consume a
handle; the partly empty low speed/extraction and medium extraction grids retain
their native redstone/iron counts by moving the displaced ingredient into a free
slot. Medium extraction also copies the low predecessor's native NBT. The
without-Manufacturing hand crafts are unchanged. The mechanical-crafter
dependency, compiled serializer, metadata preservation and all alternate module
routes require progression and loaded-recipe review before any ID is promoted.

Source-only Create recipe cost repairs also keep the native iron/stone/copper inputs
alongside consumed parts for drill, saw, millstone, press, whisk, harvester,
fluid tank, both fluid-pipe orientations, hose pulley, fluid valve, plough and
propeller. The actual powered-works whisk override also retains its second
original iron ingot beside the handle. In the four-blade
propeller grid the native four iron plates occupy the cardinal positions and
four blades occupy the corners. All changed part-slot bindings and extra-cost
routes remain uncompiled and unloaded; do not infer effective acquisition,
network behavior or accepted progression from these JSON edits.

The source-only full-grid repair uses a bounded Create mechanical part-variant
serializer for 3×4 or 3×5 late-stage crafts: it preserves every original
3×3 ingredient and consumes physical working parts in an extra row while
baking the owner-specific settings. Candidates now include the Eureka engine,
Trackwork medium/large track upgrades, Industrial Heat heat pipe/exchanger and
boiler heater, diesel huge engine/turbocharger, Compressed Creativity empty
mesh/industrial blower/engine rotor, and the Clockwork air compressor. The
actual Font-of-the-Nether boiler heater and thermopneumatic plant script
overrides were repaired too: the Manufacturing plant retains six native slabs,
two strict-empty tanks, a pressure tube, magma and its liner part. The
pre-existing no-Manufacturing Font route deliberately stays at its pack cost;
it was not rewritten to match the native recipe. Clockwork's existing 5×5 gas
thruster mechanical branch now retains both the native D/B/S/C/P bill and its
previous Aether/circuit/pressure gates alongside the working parts. All new
mechanical-crafter dependencies are source-only progression decisions, not
loaded availability proofs.

Earlier powered-works constructs are not silently moved to precision_factory:
the actual propeller override now consumes its four native iron plates in
addition to four real blades and the shaft; water-wheel and large-water-wheel
roots consume all five Skyroot logs, four iron ingots and their shaft or wheel,
with one of those logs first marked by a physically consumed tool handle. The
windmill bearing retains both native Skyroot logs and puts its tool binding in
a vacant row. The no-Manufacturing shapes and yields remain unchanged. The
marked log is tied to the wheel route, not a new registry component; its
source-only crafting/ingredient behavior remains unverified. In the optional
eternal_engine world axiom, any plank species can pay the eighth original plank
and the binding before the marked plank enters the original full grid. The
optional captive_hearth route similarly prepares an eight-brick/furnace bill,
then attaches a consumed large plate to a rated thermal firebox at the early
crafting station. No-Manufacturing world-axiom branches are unchanged.

The early Sifter now pays both native sticks and its Nether-brick gate in a
marked existing-identity brick before its two real tool handles are installed.
Four tier-specific meshes pay the full eight-stick/center-material bill in a
marked existing-identity stick before a mandatory four-handle configuration;
the original BM stock-output alternatives have been removed. The brass Sifter
pays all original brass plates/ingots/redstone in a marked redstone precursor,
then consumes its predecessor and two real handles so the predecessor rating
can be retained. These marks do not add registry components or claim tested
KubeJS/Forge ingredient behavior; no-Manufacturing forms remain unchanged.

The three PNCR drill-bit thermo exact-ID branches now require their original
ingot/block tag paid together with a pick head, retaining original lubricant,
pressure, temperature, speed and air multiplier. The diamond→netherite path
keeps the native netherite ingot and assembly-drill program; PNCR-specific
source hooks carry bounded configuration across the native outputs rather than
paying a second head or allowing stock input to create a configured bit.
Twenty-seven further PNCR source recipes use route-specific, same-item cost
bundles for native ingredients displaced by parts, while the effective Font
thermopneumatic plant is covered by the six-slab mechanical recipe described
above. These are authored source routes, not executed native-accounting
receipts; any other loaded alternate acquisition remains unreviewed.

AE2 source-only exact-ID alternatives now cover both native energy-acceptor
roots and the cable-to-interface/pattern-provider conversions with consumed
plates while keeping their original input costs. Pretty Pipes' effective
Certus/Sky-Stone pressurizer override and its native-ID fallback now pay the
original fourth iron ingot along with the rated tough handle in a 3×4 craft;
the high-speed-module predecessor remains an input. Quark's precision_factory
crafter already requires a Create mechanical crafter: its Manufacturing branch
now uses that available station for all five original iron ingots plus two
construction parts, without adding a material-performance setting. The
iron-workshop shaft batch keeps two iron ingots and eight outputs while paying
one tool handle; workshop cogwheel and large-cogwheel alternatives pay their
native shaft/planks and an additional handle. The effective workshop
mechanical-bearing branch now keeps its slab, casing and shaft while consuming
an additional handle. No-Manufacturing workshop roots
remain unchanged. The extra handle requirement does not establish the loaded
TConstruct-part bootstrap and must be reviewed before pack use.

Clockwork source-only fixes retain pinned native inputs and previous pack gates
for the gas thruster, air compressor and several bearings/gyro. A narrow
pre-consumption Deployer recipe filter rejects only part-free gas-engine and
reactionwheel item applications while the configured duct/flywheel path keeps
native work and settings inheritance. A Clockwork-only marked-plank source
craft now consumes one same-species native-tag plank plus three/two real broad
blade and tough-handle pairs; the exact native Create cutting IDs retain their
100-tick work and three/two outputs. The saw hook rejects malformed or unmarked
inputs before consuming them and transfers each pair's bounded rating to one
blade, with the native initial BladeLength of one. The finishing route retains
native blade NBT and rejects malformed explicit lengths; legacy stock blades
retain their safe native fallback. Saw-hook composition, source tag expansion
and subsequent self-cutting remain **unverified**, not acceptance evidence.
Four WIP IDs (heater, copter bearing, OC2R gateway and monitor)
remain active and unresolved without an invented route or unsupported exclusion.
In the pinned OC2R 2.2.12 JAR, gateway and monitor are registered blocks
with implementation classes and assets, but neither occurs in the complete
native recipe or block-loot-table sets; the effective pack has no identified
recipe or removal for either. That static absence does not prove a negative
about every in-world/dynamic acquisition, so both remain selected pending
source/loaded resolution. The pinned Compressed Creativity heater is registered
and has a block-drop loot table but no established direct native recipe; a drop
from an already-placed block does not establish first survival acquisition. The
pinned Clockwork copter bearing has a registered block/item and models but no
found native or pack recipe/loot route; its owner bearing class shows no
item-producing interaction. Neither static observation proves exclusion.
PowerGrid source-only gauge conversion variants now pay a part while retaining
the native one-gauge input; source review found winding placement from a paid
copper coil, transformers assembled from paid plated cores, and CRT encasing
of a paid CRT as in-world predecessor-dependent outputs. Create encasing of
six shafts/cogwheels also starts from part-paid predecessor crafts; its
encased-fluid-pipe route carries the precursor's rating through the existing
`FluidPipeConversionsMixin` and `FluidVariantBehaviour`. Legacy unrated world
blocks may still be encased via native fallback, and none of these interactions
has been loaded or verified. Its `circuit_board` lacks a demonstrated initial direct source
route and is not silently made available. Rail Beetle's chassis now uses 3×4
mechanical crafting to retain native compass/rails/furnace/minecart, the pack's
electron tubes/casing/barrel and two rated working parts. The electrical and
pneumatic motor branches retain their existing pack progression predecessor
alongside their machine-block gates; no no-Manufacturing script was changed.
Little Logistics' conditional source routes keep native outputs and costs;
both docks pay displaced stone through a marked existing spring before their
original two-output crafts. Native-item NBT and full-pack route behavior are
not asserted from these source edits. OC2R's nine available recipe-only roots
now use BM-only 3×4 Create crafts containing their full pinned owner bills,
two construction parts, and the computer's original flash-memory NBT; the
no-Manufacturing recipe bodies remain unchanged. Four early Sophisticated
Storage/Backpacks roots use route-marked same-identity material bundles to pay
one missing native ingredient and both parts without changing the final
crafting-table station; the native controller-to-three-links alternative also
consumes parts alongside its original controller and ender pearl. Source-only
serializer, conditional loading, item-enabled settings and predecessor
configuration still require later validation.

Complicated Bees' source pass now supplies marked, native-cost ingredient
preparations for its early full-grid gyrofuge/frame/analysis routes, preserving
the original crafting-table station. Seven later Mellarium-dependent recipes
retain their full native bills and predecessor blocks in 3×4 Create crafts;
the gyrofuge panel's native smithing template/base/addition still apply, with a
part-paid marked redstone torch. A tiered gyrofuge consumes its physical
predecessor and keeps unrelated native NBT, while fresh working parts replace
the prior Manufacturing rating. These are source-authored, not loaded or
accepted; the 45 selected Bees no-Manufacturing branches were source-compared with
pinned native recipe bodies, including the formerly leaking Manufacturing-only
serializer in tiered units; runtime loading and acquisition remain unverified. Compressed Creativity and diesel source review
found the existing eight-step mesh filling still retains its native water work
and NBT-transfer hook; the additional basin lid and bulk fermenter recipes pay
parts without dropping their native inputs. The effective thermal-pressure
rotational-compressor override now uses a marked aerogel bill that pays its
previously displaced second compressed-iron gear and brass casing, retaining
the prior pack progression, pressure tube, seal and both physical parts; the
no-Manufacturing route is unchanged. Native bracketed pressure-tube world
applications and frozen mesh swaps are not asserted loaded or verified.

PLA and PP are **ordinary Tinkers materials**. Their actual TCon head/handle stats
are distinct and must flow through the same part-in-machine formulas as other
materials, with no extra material-to-machine rating/opt-in table, polymer-only
permission layer or synthetic role scores. The obsolete pack engineering material JSON files (`iron`, `wood`, `rock`, `copper`,
`pla`, `polypropylene`) and their 52 machine-trait keys/shape opt-in lists are now
removed following core's compiled disconnect of the active engineering catalog and
all direct recipe-reader migrations. Real TCon material definitions, native stats,
casting and acquisition remain. Dormant historical Java codec/evaluator sources are
not a live material rating authority; loaded behavior and EMI remain unverified.
No new PLA/PP or other roster acceptance is implied. Source-local wave-two checks
include six focused shared-script/native-stat-asset units. The
historical independent Create/actual20 native run has a scoped 31/31 passing Forge userdev receipt
(`better-manufacturing/build/gametest/856c112d-b73d-41ba-8897-654be127fbe8/execution.json`,
`full_roster_accepted=false`). This bounds demonstrated Create/Sifter craft, settings,
work/stress, Steam supplied-shaft and lifecycle cases; it does **not** prove complete
crushing-wheel processing-tick throughput, full boiler water/heat budgets, optional
PNCR/fluid/thermal families or every alternative acquisition route. Required Bumblezone/85
harvester startup remains blocked by the pinned Bumblezone refmap/mapping problem; the
independent profile explicitly excludes that branch. Full-roster acceptance, polymer chain
qualification, pack deployment, client/full-pack launch, Packwiz refresh and packaging
have **not** occurred. Current user direction permits production implementation and
**isolated unit tests only**. The updated pack script, retired legacy data and TCon
stat assets passed six Node source unit checks. Java native-stat migration remains in progress without
family-wide unit coverage; no new native behavior is verified. No integration/end-to-end/native tests, Forge GameTests, loader/client/server
launches, smoke runs or aggregate qualification are authorized by the historical receipt. Existing broader
historical coverage gates remain unmet; no incomplete-Manufacturing packaging exception
applies. See the source README for current native ownership and tested boundaries.

### Recipe-only Cooking policy

`kubejs/config/cooking_recipes.json` uses `better_cooking:recipe_policy.v1`, independently of the quarantined `culinary_policy.v1`. `84_cooking_recipes.js` checks both original and separately queued post-script source recipes before making exact-ID changes. Queued sources are serialized before the comparison; exact queued-object removal prevents an ID-wide removal from erasing a same-ID replacement. It uses existing installed serializers only. Never qualify the legacy gameplay policy to enable this migration.

The source-owned tools `recipe_catalogue.py`, `recipe_baseline.py`, `recipe_routes.py`, `recipe_station_migration.py`, `compile_station_plan.py` and `recipe_policy.py` bind all 1,060 live default-stack foods, authored roles, source JSON and 2,242 explicit route decisions. Native cutting and cooking choices are separately authored and source-fingerprinted; comparisons use full cutting output vectors, including by-products, rather than only the display result. `verify_recipe_installation.py` checks the fresh server's original source JSON, committed recipe serializer bytes, loaded outputs/serializers, unchanged food properties and canonical compatibility tags. The operator command `/runtimedata recipe-policy` independently parses each intended definition with its installed serializer and compares its network bytes with the committed recipe manager; pending KubeJS event JSON is not mistaken for installed state. Its receipt is not Smoke or Tele Soak acceptance.

Current choices install **82 exact-ID redirects**: 26 deterministic knife cuts onto Kaleidoscope chopping boards (with 104 model-stage resources), 21 stockpot routes, 20 stir-fry pot routes, and 15 flour/dough or dependency redirects. The queued pack-authored creamy fig-chicken route is included. Original ingredient alternatives, quantities, output NBT, deterministic by-products, portions and conditions are retained. Cooking choices check ingredient remainders and serving carriers against live FD/Kaleidoscope facts; native water/lid/heat or oil/stirring/heat handling and native XP behavior are explicit process changes, not claims of identical apparatus. FD-family knife compatibility remains tag-based.

Flour/dough producers use Kaleidoscope items with old-stock alternatives and additive wheat flour/dough tags. Slime-ball and Power Grid circuit-board consumers accept canonical dough without changing nonfood outputs, sequence steps or loops. Native retention is recipe-specific: probabilistic cuts, competing native cut formats, wild-crop acquisition, axe cuts, baking mats, distinct desserts/beverages/feasts, final plating, specialist processes, drying and nonfood infrastructure are preserved. Pineapple fried-rice routes retain their required bowl rather than silently losing it through Kaleidoscope's implicit stir-fry carrier lookup. Retained routes record source facts and same-output comparisons; unknown serializers fail review rather than receiving a blanket fallback. Registered items and FD/addon infrastructure stay installed. No new food properties, preservation/canning, intake profiles, custom preparation stages or physiology gates are introduced.

### Explicit Activation Dependencies And Boundaries

Dynamic Trees Dimension Compat and its required Undergarden dependency are excluded from the candidate. Independent Dynamic Trees integrations remain. Their old source and evidence are retained outside the active manifest.

Celestial Events' authored Thin Veil forest/swamp biome selections remain **pack-owned** under `kubejs/data/better_celestial_events/tags/worldgen/biome/`. They were restored byte-for-byte from pack revision `3900ac64` for this explicit activation. The source build already consumes and bundles those declared pack inputs; owner-local verification using them is not hermetic, and their ownership must not be moved into source merely to satisfy a test.

This consolidation requires fresh isolated outputs for every included source-owned JAR and the existing source/unit checks, followed by one exact-artifact Smoke on a non-strict experimental candidate. Historical bundled bytes cannot satisfy that fresh rebuild. Smoke proves its recorded startup, client join, save and shutdown lifecycle only; it does not qualify Abyssal City, Spaceflight gameplay, seasonal behavior, combat, visual quality or an existing-world upgrade.

`better-finite-water` remains source-only and must not be deployed implicitly. Future cloned sources likewise require explicit activation; presence under `mod_source/` is not pack membership.

## Current Implementation Manifests

These manifests describe the checked-in implementation. They are maintenance
indexes, not migration instructions or substitutes for recipes, registries, or
custom-mod source. When a row disagrees with executable content, fix the row and
the closest living design document in the same change.

### Executable KubeJS surface

KubeJS executes JavaScript recursively under `kubejs/client_scripts`,
`kubejs/server_scripts` and `kubejs/startup_scripts`. The current candidate has
**114 executable-root JavaScript files**: 11 client, 91 server and 12 startup.
[`../kjs-script-summaries.md`](../kjs-script-summaries.md) lists each exact path
once. This is a file inventory, not a claim that every loaded script installs a
live recipe: the retired aerospace file and duplicate logistics path are inert,
and other scripts may be conditional.

The `compat/retained` and `compat/reviewed` names classify ownership history;
they do not disable execution. `kubejs/inactive_review` is outside every active
script root and remains non-executable. No action-named directory is treated as
a staging area inside an active root.

### Progression and ownership

| Surface | Runtime authority | Pack-owned boundary |
| --- | --- | --- |
| Five power-centered technology eras and Machine Blocks | Pack KubeJS | Cumulative workshop supply options; exact cross-mod capability roots, transition items and optional transport gates remain unchanged; aerospace deferred |
| Fitted working parts and conversion hardware | Better Manufacturing + native TCon parts | Pack supplies recipes/roles; selected settings affect native machines, construction-only ingredients do not imply ratings; acceptance remains family-specific |
| Pressure/electrical supply to Create | PneumaticCraft + Compressed Creativity / PowerGrid | Pack gates access; native storage, networks and conversion supply existing kinetic machines rather than replacement processing blocks |
| Kinetic transmission demand | Better Create Kinetic Loss | Pack config sets component/RPM costs; no material-dependent shaft loss |
| Geological deposits and processing | Realistic Ores | Era inputs only; see [`progression.md#realistic-ore-processing`](progression.md#realistic-ore-processing) |
| Radiation and contained chemistry | Latent ChemLib | Cross-mod gates; no duplicate physical simulation |
| Industrial heat | Heat Sync | Era placement and integrations; Heat Sync owns storage, transfer, ambient mapping, and Create boiler heat |
| Dimension Fonts | Dimension Drink | Recipes and learning references only; Dimension Drink owns obelisks, charge, sessions, tickets, and arrival sites |
| Settlement paths | Settlement Roads | No pack placement script; Settlement Roads owns dry-route planning, persistence, road palette, and placement; bridge scaffolding is dormant and unsupported |
| Vanilla vessel durability and recipes | Better Content Fixes | No boat mutation or hiding script |
| Credited-kill spirit release, seven village professions/stations/behaviours, matching spirit trades, and seven wandering identities | Better Content Economy | No parallel KubeJS spirit source, trade catalogue, profession conversion, or kill-drop script; pack policy only retires old coin/equipment surfaces |
| TConstruct affixes and Epic Fight mapping | Tinkers' Construct Affixes | Pack supplies no parallel material-affix catalogue |
| World reset lifecycle | World Lifecycle Manager | Pack supplies runtime configuration and deploys its independent JAR |
| Persistent footprints and notes | Player Traces | Pack supplies runtime configuration and deploys its independent JAR |

The five active Machine Block IDs are:

- `kubejs:andesite_machine_block`
- `kubejs:copper_machine_block`
- `kubejs:brass_machine_block`
- `kubejs:airtight_machine_block`
- `kubejs:electrical_machine_block`

The former `kubejs:space_machine_block` is retired with Creating Space.

Old Machine Casing identities, generic washed/tailings forms, duplicate ore
catalogues, and universal recipe-introspection scripts have no active owner.

### Dimension Font charge contract

An unmodified obelisk has 15,000 maximum charge, a 600-charge start cost, no join
cost, and 1.25 passive charge per inactive tick, refilling the base capacity in ten
in-game minutes. An active run drains 80 charge plus 40
per participant once per second. Data-driven capacity and efficiency modifiers
may change run length; a full unmodified Font supplies 120 solo active seconds
after its start cost. Starting and joining charge is deducted only after the
backend accepts the transition; exhausted charge returns participants and closes
the run. The origin chunk is ticketed for the active run. Arrival sites have no
resource-fluid storage, input slot, compatibility item, or resource scatter.

### Settlement road contract

Settlement Roads is an active bundled custom mod. Ground routes place a complete
three-block-wide surface: dirt path in grassy biomes and gravel in non-grassy
biomes. A deterministic 20% of eligible road-edge cells uses coarse dirt.
Ground routes do not add cobblestone guide marks or roadside walls. Water bridges
are disabled and unsupported: the planner uses a dry detour when one is available
and otherwise omits the connection. Bridge code and isolated bridge tests remain
dormant scaffolding and do not establish supported pack behavior.

### Validation and deployment

Custom-mod repository, artifact, and command ownership is canonical in
[`implementation.md#custom-mod-workspace`](implementation.md#custom-mod-workspace). A source change is complete
only after that repository's documented validation passes. Deployment uses the
reobfuscated runtime JAR from `build/libs/` only when explicitly requested. Pack-local checks use `./gradlew test` separately from runtime acceptance.
An explicitly ordered fresh distribution refreshes Packwiz hashes, packages once and requires Smoke on that exact pair; optional Overworld Tele Soak uses the unchanged artifacts. Neither tier runs source-local verification automatically.

## Performance And Mods

This document records current pack state and operational constraints. Active downloaded mods are
the current `mods/*.pw.toml` files; active Better Content JARs are the machine-readable
`gradle/active-custom-mods.json` inventory documented in [Custom Mod Workspace](implementation.md#custom-mod-workspace).
Generated runtimes and old profiling directories are not authorities for active state.

### Active integration state

Bumblezone 7.13.4 remains an active Font expedition realm; Rats 8.1.3 remains installed but
normal Ratlantis player access is retired. `better_bumblezone_crops` owns Bumblezone food flora and
propagation. Ordinary Rats resources and creatures remain; Ratlantis rehoming into Overworld coastal or cave biomes is removed.
Better Ratlantis Logistics, its custom components, bait restriction, abyss and vaults are removed. Nature's Spirit remains the canonical mahogany
wood and progression authority; `better_hexerei_dynamic_trees` restores dynamic Hexerei mahogany, willow,
and witch hazel trees. The CurseForge Dynamic Trees addons for Aether and Twilight Forest remain
active, along with `better_malum_dynamic_trees`.

TaCZ 1.1.8 is active with playerAnimator and client-only Accelerated Rendering. Create Armorer,
Applied Armorer, and Immersive Armorer remain external gun-pack ZIPs under `tacz/`. Their benches
progress from brass factory manufacture through post-AE2 Impossible and Electrical milestones.
Their content namespaces are TaCZ pack IDs, not Forge mod IDs; recipe guards use the loaded TaCZ
mod, while candidate validation checks that the three packaged ZIPs match their manifests.

The pinned Valkyrien Skies transport family is active on both sides:

- Valkyrien Skies `valkyrienskies-120-2.4.11.jar` (original pinned upstream artifact);
- Eureka `eureka-1201-1.6.3.jar`;
- VS: Clockwork `clockwork-0.5.6.jar`;
- Trackwork `trackwork-1.20.1-1.2.4.jar`.

Epic ParCool and Valkyrien Skies now both resolve their original JARs on client and server. Better Compat Fixes supplies narrowly bound MixinSquared cancellations for the server-only client references and the unsupported VS interface injector, plus its source-owned vibration bridge. This is an **unqualified candidate**: cancellation occurs at Mixin target pre-apply, and neither earlier configuration errors nor Epic ParCool's server entrypoint have been cleared by a native run. Earlier locally modified JARs remain only in historical builds/commits and recovery, not the new Packwiz index.

Eureka owns primitive post-Part-Builder wooden watercraft, Trackwork is a peer of Create trains,
and powered Eureka/Clockwork flight progresses through Aether, Airtight lift, PneumaticCraft gas
hardware, and PCB-controlled stabilization. Automated pack tests own startup and broad
compatibility only; helm control, camera behavior, rendering, specialized add-on mechanics,
observer synchronization, and ship persistence remain manual-playtest surfaces. Epic Fight's
optional third-person combat camera remains player-controlled.

Complicated Bees, EMI Ores, Forgotten Ruins, Ice and Fire, Hexerei, Malum, Occultism,
Goety, Ars Elemental, Ars Creo, Ars Energistique, Polymorph, Supplementaries, Amendments, Genetic
Animals, Fowl Play, Advanced Chimneys, and Realistic Block Physics are active. Farmer's Delight,
Ube's Delight, Farmer's Respite, and Brewin' and Chewin' are the active Delight food-and-drink
set. The Journal inventory owns Sort, Quick Stack, and Trash with Undo; the previous separate
inventory utility mods are removed. The maintained `config/adchimneys/` definitions cover active furnace,
burner, smeltery, and generator emitters.

Weapons of Miracles is added as a pinned candidate input. Its Epic Fight and tooltip
ownership must be reconciled against the exact installed version; manifest
presence does not qualify broad combat behavior. Electrochemistry supplies a
fresh-world chemistry branch alongside retained PneumaticCraft/ChemLib/Manufacturing
ownership, not a restoration of Creating Space flight processing. Seasonal Daylight
and Seasonal Trees are fresh-world candidate additions; keep one sky-clock owner
and preserve existing saved calendar state.

Create Sifting 1.8.6 is active with Create 6.0.8. String-mesh hand sifting is the
pre-power ore separation path; standard and Brass Sifters automate the same recipe
language. Advanced and custom meshes are hidden and unobtainable. Create fan-based
Bulk Washing has no recipes; water treatment belongs to Spouting.

Claustrophobic Dungeons, Dungeon Crawl, Create Big Cannons, Reliquary, Undergarden, Lost Cities, Deeper Darker,
Temperature Bands, the standalone coolant mod, the redundant fission-reactor mod, and Create New Age remain retired.
Forbidden and Arcanus, Roots Classic, Mahoutsukai, Eidolon, Theurgy, Psi, and Hex Casting have no
active Packwiz manifests and are inactive unless deliberately reintroduced.

FTB Quests, its Better Content integration, FTB Teams, FTB XMod Compat, and FTB Filter
System are retired. FTB Library remains required by Satako, and independent FTB Backups
remains active. Learning is owned by Threads, loading lessons, hover, and native surfaces.

### Runtime and memory policy

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

Runtime recipe-audit and block-hardness diagnostics ship disabled. An explicitly requested
`server` or `all` suite invokes the packaged runtime-data dumper, requires a complete snapshot, and
atomically refreshes ignored `generated/runtime-dumps/`. Incomplete snapshots never replace the
last complete one.

### Shader block motion classification

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

### C2ME, Distant Horizons, and world generation

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

Creating Space destinations are retired. Twilight Forest and Fallout Wastelands rocket-only access and the Ruined Citadel encounter are deferred; no new portal or Font route is added. The Flesh That Hates is retained only in `the_deep_void:deep_void`. Six pack structure overrides target the Deep Void flesh-biome tag, and `better_exploration_load_control` gates the pinned native incubator and servant paths to that dimension. Its ordinary site search samples at most 4,096 positions in loaded chunks; its servant column walk is capped at 32 block-state reads per attempt. The native callers handle missing sites. Exact-artifact runtime verification still must prove mixin application, structure placement and exclusive habitat; no existing saved entities are removed.

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

### Custom compatibility ownership

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
Lifecycle Manager separately owns successor spawn-biome preferences in
`config/better_world_management-successor-biomes.txt`. Fresh initial worlds no longer
read a biome-ID preference list: they select a final shared-spawn biome whose registered
base temperature is within Cold Sweat's loaded habitable bounds (10–37.78°C in this
pack) and which is not in `#minecraft:is_ocean`. The same rule applies at the two
blocks above the saved position, because vanilla first-join placement can lift a
spectator into a vertically different ocean biome. Additional initial-only excluded
IDs may be added to `config/better_world_management-initial-spawn-blacklist.txt`;
the ocean tag exclusion applies even if that file is absent. No FULL-chunk terrain-safety check
is required for first-join spectators. This biome-baseline criterion does not guarantee
effective comfort at every time, season or elevation. Successor spawn checks retain
their existing terrain policy.
Once a player joins a candidate, Lifecycle Manager writes an inhabited marker in that world;
the supervisor preserves the candidate and transaction if the marker or saved player data is
present, including after a failed health check or interrupted retry.

### Heat and pollution authority

`better_industrial_heat` owns industrial heat storage, transfer, pipes, ambient bridging, hot water, and coolant
exchange. Create: Power Grid retains its electrical and device-overheat simulation; its optional
`ThermalBehaviour` adapter exposes that temperature to HeatSync without creating a second heat API.

`better_chemlib_hazards` owns contained high-energy chemistry, decay heat, criticality, radiation, and
mass-debit semantics. It has no reactor power ladder or atmospheric scheduler. Released gases
become native AdPother pollutant blocks at the configured contained-mass boundary; AdPother then
owns movement, wind, impacts, detection, protection, emitters, chimneys, filters, and cleanup.
PneumaticCraft retains native pressure and thermo-plant behavior, with explicit normal-air and
chemical-service ledgers at the Latent boundary.

Rebuild and deploy custom JARs through [Custom Mod Workspace](implementation.md#custom-mod-workspace). Run pack
suites only when explicitly ordered, following [Testing and fresh distributions](testing.md#smoke-overworld-tele-soak-tele-soak-and-fresh-distributions).

## Distant Horizons 2.4.5 render geometry audit

This audit records the source geometry for the exact pinned Forge 1.20.1 artifact:

- File: `DistantHorizons-2.4.5-b-1.20.1-fabric-forge.jar`
- CurseForge project/file: `508933/7375280`
- Packwiz SHA-1: `ce3814dd5971edda4d04c3a42ef0df5c6cf8e10d`
- Inspected copy: retained multiplayer fixture at `generated/test-evidence/20260913T220011Z-357174/multiplayer/fixture/client-1/mods/`

### Transform path

`LodRenderer.setShaderProgramMvmOffset` computes the LOD origin relative to
`RenderParams.exactCameraPosition` on all three axes and supplies that camera-relative
position through `IDhApiShaderProgram.setModelOffsetPos` (and the corresponding event).
The standard vertex shader adds `uModelOffset` to the local vertex position, then applies
`uCombinedMatrix`. `DhTerrainShaderProgram.fillUniformData` forms that combined matrix as
projection multiplied by model-view. `RenderUtil.createLodModelViewMatrix` returns its
matrix copy without changing camera rotation or translation.

`RenderUtil.createLodProjectionMatrix` copies the active projection and changes only the
depth clip-plane terms through `Mat4f.setClipPlanes` (`m22` and `m23`). The horizontal and
vertical projection scale terms (`m00` and `m11`), which carry aspect/FOV, are retained.
The DH fade path inverts projection-times-model-view to reconstruct view-space positions
from depth; it uses the same matrix composition as terrain rendering.

### Source conclusion and boundary

The inspected source shows consistent camera-relative translation, preserved model-view
rotation, preserved FOV/aspect scale, and matching terrain/fade matrix composition. No
incorrect translation, rotation, FOV, or depth transform is evidenced in this exact artifact,
so this source audit records the existing geometry rather than introducing a speculative
transform patch. This is not a runtime visual acceptance: camera movement, FOV changes,
Oculus/shader combinations, terrain seams, and depth/fade appearance still require the
separate candidate/runtime visual review.

`config/DistantHorizons.toml` keeps LOD transparency `DISABLED`; the pack performance guide
documents this as the compatibility setting for DH/Oculus/shader depth composition. DH
remains enabled. This setting is independent of the transform finding above.
