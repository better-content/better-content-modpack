# Better Content Modpack

Better Content is an expert Forge 1.20.1 modpack and its supporting content layer. Its enforceable acquisition rules are described by the [crafting graph policy](docs/progression.md#crafting-graph-policy) and [balance register](docs/progression.md#balance-policy-register). Custom mod source, ownership, build, and deployment metadata is maintained in the [custom mod workspace guide](docs/implementation.md#custom-mod-workspace). Every custom mod lives in an independent repository under the workspace's `mod_source/` directory (`/home/dev/mod_source` in Worklane), never inside this repository.

Pack-level testing and fresh distributions are explicit operations. See [Testing and fresh distributions](docs/testing.md#smoke-overworld-tele-soak-tele-soak-and-fresh-distributions) for Smoke and both Tele Soak tiers, durable session evidence, and the mandatory exact-artifact Smoke release gate.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Documentation map

These are living documents: they state current intent and operating boundaries, not blanket proof of gameplay or runtime acceptance. Keep detailed contracts in their owning section and retain raw evidence outside the tracked docs.

- [Design and future directions](docs/design.md): established principles and clearly labeled future proposals, including recovered aspirations.
- [Progression, acquisition and balance](docs/progression.md): era gates, canonical crafting and balance rules, food, geology, combat and component acquisition.
- [World systems and player experience](docs/world-and-player.md): dimensions, ecology, physiological and death systems, discovery and learning surfaces.
- [Implementation, ownership and performance](docs/implementation.md): mod ownership, script manifests, validation boundaries and runtime policy.
- [Testing and fresh distributions](docs/testing.md): exact-artifact Smoke, Overworld Tele Soak and full Tele Soak evidence.

`LICENSE-NOTICE.md` remains a packaged legal notice, `config/explosionoverhaul/README.md` stays with its configuration, and `kjs-script-summaries.md` remains the generated script inventory. Historical fixtures in `generated/` and the separate `feature-labs/` checkout are not living documentation.

## Pack Thesis

Better Content is a Forge 1.20.1 expert-pack content layer built around systems natural to its world. The current working model calls those systems **world spines**. Matter, Place, and Life make expected reality mechanically present; Blood, Fonts, and Traces Into Lineage each propose a different supernatural “what if.” History is a cross-cutting persistence rule, while Society emerges where embodied actors, local resources, and remembered consequences meet.

“Tech,” “magic,” and “adventure” remain useful player-facing packages, but they are not assumed to be causal roots. Tech chiefly packages organized mastery of Matter under Place constraints. Formal magic records and reproduces powers whose authority comes from a magical root. Adventure packages travel, danger, discovery, and exchange across several roots. This distinction is a working model for evaluating progression, not a claim that the current implementation already conforms to it.

Geological deposits, Y-band locality, processing ladders, machine casing tiers, spirit/villager markets, obelisk and dimension routes, body systems, persistent traces, and the death/respawn life-length loop are all progression surfaces through which those roots meet.

The main source trees are `kubejs/`, `config/`, `defaultconfigs/`, `datapacks/`, `globalresources/`, `resourcepacks/`, `shaderpacks/`, and active `mods/*.pw.toml`. Git tracks **no JARs**: the 54 custom-mod JARs in `mods/` are local cache inputs whose paths and SHA-256 hashes remain in Packwiz `index.toml`. A fresh clone must restore exact cached inputs or build the selected source repositories in `gradle/active-custom-mods.json` before packaging; the index alone cannot supply custom binaries. Gradle launchers restore their ignored wrapper JAR from the pinned, checksum-verified Gradle distribution and require Python 3. Root `dist.sh` is the package-once primitive, `test.main.kts` exposes Smoke and both Tele Soak tiers on existing artifacts, and `release.main.kts` requires Smoke on an explicitly prepared fresh ZIP pair before success; `package.sh` remains the internal packager.

Runtime directories, raw logs, crash reports, screenshots, profiler dumps, generated runtime dumps, and local launcher state are not documentation. Keep them under `~/.cache/bc`, `server-instance/`, `server-template/`, or `generated/` unless explicitly requested otherwise.

## Doc Policy

Do not add new one-off audits, pass reports, JSON summaries, raw logs, RAM dumps, or diagnostics under `docs/`. Fold durable conclusions into the closest living doc and leave raw evidence in the run root.

When progression behavior changes, update `progression.md#progression` and/or `world-and-player.md#content-ownership`. When crafting-graph scope, mod categorization, item-support policy, exception families, or durable integration findings change, update `progression.md#crafting-graph-policy` and the affected `progression.md#balance-policy-register` row. When player-facing teaching or authoring policy changes, update `world-and-player.md#learning-surfaces`. When the Realistic Ores processing model, yields, assays, media, solvents, or extension rules change, update `progression.md#realistic-ore-processing`. When executable script inventory or cross-repository ownership changes, update `implementation.md#current-implementation-manifests` and the root `kjs-script-summaries.md`. When Systemic Salience mappings or presentation change, update `world-and-player.md#systemic-salience`. When weapon references, normalized bands, compensation rules, generic-effect valuation, animation coverage, alternate modes, or signature exceptions change, update `progression.md#weapon-balance-philosophy`. When custom-mod ownership, identity, validation, or deployment changes, update `implementation.md#custom-mod-workspace`. When mod composition, performance, or runtime compatibility changes, update `implementation.md#performance-and-mods`. Pack-level suites use `test.main.kts` only when authorized for the operation, as summarized in `testing.md#smoke-overworld-tele-soak-tele-soak-and-fresh-distributions`. This checkout has no root `AGENTS.md`; do not rely on one for approval or commands.

The current consolidation target uses fresh source-owned JARs, existing source/unit checks and one exact-artifact Smoke on the non-strict experimental route. This is an intended gate, not a passed result or a claim of world migration. Claims in these docs must be checked against their current implementation or evidence authority.
The tracked pack metadata, config, data and scripts plus the checksum-matched local custom-JAR cache
define the current source target; owning custom-mod source defines the mechanics represented by those
JARs. Git does not track the JAR bytes. Complete generated snapshots and named test evidence establish observed runtime state only for
the exact candidate hashes recorded by the same run. Match a snapshot ID to its server-run events
and `candidate_selected` hashes before calling it current. An unmatched or older snapshot remains
historical candidate evidence even when it is the latest complete snapshot. Design intent, future
systems, candidate art, and manual-playtest surfaces must remain explicitly labeled. Documentation
and implementation have no blanket precedence when they conflict; stop and obtain an owner decision
for the specific behavior, then reconcile both sides. If an ID, mod, recipe, config, or runtime
result cannot be confirmed, write `UNKNOWN` or frame it as a future candidate.

Do not add historical notes, generated Markdown reports, old schema notes, or retired tool matrices to the tracked tree. Fold durable conclusions into these living files and keep raw evidence outside the repository; do not recreate `quarantine/docs/`. Do not classify `.txt` files by extension alone: many launcher, Forge, FancyMenu, KubeJS, shaderpack, and mod files are live config or runtime inputs.
