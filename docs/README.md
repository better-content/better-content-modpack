# Better Content Docs

This directory contains the pack's living documentation. Add another focused living document when a durable subject no longer fits an existing one; the repository remains the source of truth and the documents summarize current intent and operating state.

## Current Docs

- `README.md`: docs index, pack thesis, and living-doc policy.
- `progression.md`: progression spine, gates, chokepoints, pinnacle powers, and deadlock checks.
- `content_systems.md`: recipes, materials, chemistry, casings, loot, trades, quests, and content surfaces.
- `crafting_policy.md`: enforceable crafting-graph definition, selector rules, invariants, and known debt.
- `balance_policy.md`: canonical register of upstream departures, owners, acquisition channels, rationale, and verification.
- `questbook_standards.md`: secondary-ledger purpose, milestone tasks, icons, copy, rewards, visibility, and review policy.
- `realistic_ore_processing.md`: theory, invariants, yield semantics, authoring rules, and maintenance contract for geological deposit processing.
- `refactor_manifests.md`: current executable-script, progression, and custom-system ownership manifests.
- `weapon_balance_philosophy.md`: cross-system weapon balance doctrine, measurement rules, exceptions, and the current TCon/Epic Fight implementation.
- `performance_and_mods.md`: current mod state, runtime policy, performance configuration, worldgen constraints, and compatibility ownership.
- `custom-mod-workspace.md`: canonical custom-mod repositories, identities, artifacts, validation, and deployment workflow.
- `systemic_salience.md`: canonical aspect identities and their parallel stat, bodily-state, geological, visual, and audio expression.
- `threads.md`: contextual Thread discovery, reader, lineage collection, and cosmetic facsimile contract.
- `thread_art_direction.md`: the illustrated deck's reproducible impossible-archive visual grammar and card briefs.
- `lineage_endgame.md`: durable entropy caching, Condemnation, strategic curse, and mechanics-first lore model.
- `testing.md`: granular pack-suite boundaries, evidence ownership, and the fresh-distribution workflow.

## Pack Thesis

Better Content is a Forge 1.20.1 expert-pack content layer built around systems natural to its world. The current working model calls those systems **world spines**. Matter, Place, and Life make expected reality mechanically present; Blood, Fonts, and Traces Into Lineage each propose a different supernatural “what if.” History is a cross-cutting persistence rule, while Society emerges where embodied actors, local resources, and remembered consequences meet.

“Tech,” “magic,” and “adventure” remain useful player-facing packages, but they are not assumed to be causal roots. Tech chiefly packages organized mastery of Matter under Place constraints. Formal magic records and reproduces powers whose authority comes from a magical root. Adventure packages travel, danger, discovery, and exchange across several roots. This distinction is a working model for evaluating progression, not a claim that the current implementation already conforms to it.

Geological deposits, Y-band locality, processing ladders, machine casing tiers, spirit/villager markets, obelisk and dimension routes, body systems, persistent traces, and the death/respawn life-length loop are all progression surfaces through which those roots meet.

The main source trees are `kubejs/`, `config/`, `defaultconfigs/`, `datapacks/`, `globalresources/`, `resourcepacks/`, `shaderpacks/`, active `mods/*.pw.toml`, and bundled custom jars in `mods/`. Root `dist.sh` is the package-once primitive, `test.main.kts` is the explicitly selected granular evaluation facade, and `release.main.kts` owns explicitly requested fresh tested distributions; `package.sh` remains the internal packager.

Runtime directories, raw logs, crash reports, screenshots, profiler dumps, generated quest/site/runtime dumps, and local launcher state are not documentation. Keep them under `~/.cache/bc`, `server-instance/`, `server-template/`, or `generated/` unless explicitly requested otherwise.

## Doc Policy

Do not add new one-off audits, pass reports, JSON summaries, raw logs, RAM dumps, or diagnostics under `docs/`. Fold durable conclusions into the closest living doc and leave raw evidence in the run root.

When progression behavior changes, update `progression.md` and/or `content_systems.md`. When crafting-graph scope, mod categorization, item-support policy, exception families, or durable integration findings change, update `crafting_policy.md` and the affected `balance_policy.md` row. When player-facing quest structure, presentation, or authoring policy changes, update `questbook_standards.md`. When the Realistic Ores processing model, yields, assays, media, solvents, or extension rules change, update `realistic_ore_processing.md`. When executable script inventory or cross-repository ownership changes, update `refactor_manifests.md` and the root `kjs-script-summaries.md`. When Systemic Salience mappings or presentation change, update `systemic_salience.md`. When weapon references, normalized bands, compensation rules, generic-effect valuation, animation coverage, alternate modes, or signature exceptions change, update `weapon_balance_philosophy.md`. When custom-mod ownership, identity, validation, or deployment changes, update `custom-mod-workspace.md`. When mod composition, performance, or runtime compatibility changes, update `performance_and_mods.md`. Pack-level suites are run only on explicit user instruction through `test.main.kts`, as defined in the root `AGENTS.md` and summarized in `testing.md`.

Claims in these docs must be checked against their current implementation or evidence authority.
The tracked pack, including its Packwiz manifests, bundled custom JARs, config, data, and scripts,
defines the current target; owning custom-mod source defines the mechanics represented by those
JARs. Complete generated snapshots and named test evidence establish observed runtime state only for
the exact candidate hashes recorded by the same run. Match a snapshot ID to its server-run events
and `candidate_selected` hashes before calling it current. An unmatched or older snapshot remains
historical candidate evidence even when it is the latest complete snapshot. Design intent, future
systems, candidate art, and manual-playtest surfaces must remain explicitly labeled. Documentation
and implementation have no blanket precedence when they conflict; stop and obtain an owner decision
for the specific behavior, then reconcile both sides. If an ID, mod, recipe, config, or runtime
result cannot be confirmed, write `UNKNOWN` or frame it as a future candidate.

Do not add historical notes, generated Markdown reports, old schema notes, or retired tool matrices to the tracked tree. Fold durable conclusions into these living files and keep raw evidence outside the repository; do not recreate `quarantine/docs/`. Do not classify `.txt` files by extension alone: many launcher, Forge, FancyMenu, KubeJS, shaderpack, and mod files are live config or runtime inputs.
