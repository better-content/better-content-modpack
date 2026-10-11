# Better Content documentation

## Start here: authority map

This is the single navigation entry point for maintained Better Content documentation. There is
one canonical owner per standing shared rule; repository entry points link here rather than
copying policies. All shared Better Content policy is versioned in this modpack repository.

| Subject | Canonical owner |
|---|---|
| Lane environment, tools and Herdr | `/home/dev/.config/agent-guidance/lane.md` (same frozen guide for Pi/Codex) |
| Polyrepo layout, ownership, commits and source/input boundary | [Workspace policy](policies/workspace.md) |
| Worlds/saves, outputs, caches and task-handoff disposal | [Generated data](policies/generated-data.md) |
| Shared tests, release procedure and authorization | [Testing](testing.md) |
| Native inventory implementation/acceptance | [Inventory parity](inventory-parity.md) |
| Local build commands and unique mod constraints | Owning repository `AGENTS.md` / `README.md` |
| Repository identities, provider graph and staging | [Custom-mod workspace](custom-mod-workspace.md); executable `gradle/active-custom-mods.json` |
| Installed local SDKs/tools | [Toolchains](toolchains.md) |

Current explicit user instructions determine task scope/authorization. Source/configuration
establish implemented behavior. Exact-candidate observations establish runtime results only for
that candidate during the active task. Design intent is not implementation, and a historical
pass is not fresh verification. If source and documented intent conflict, resolve the specific
owner decision rather than declaring either universally authoritative. Mark undiscoverable facts
`UNKNOWN`; do not invent acceptance. Task plans/claims and cached fixture instructions are not
standing policies or human authorization.

## Living subjects

### Progression, matter and gameplay

- [Progression](progression.md): spine, gates, acquisition and deadlock checks.
- [Content systems](content_systems.md): recipes, materials, machines, loot, trades and surfaces.
- [Crafting policy](crafting_policy.md): graph, selectors, invariants and known debt.
- [Balance policy](balance_policy.md): upstream departures, owners and verification.
- [Realistic ore processing](realistic_ore_processing.md): geological processing and yields.
- [Stone/cobble compatibility](stone-cobble-compatibility.md).
- [Food progression](food_progression.md) and [body/aspect identities](better_survival_physiology.md).
- [Death system](death_system.md), [lineage/endgame](lineage_endgame.md).
- [Weapon balance](weapon_balance_philosophy.md).
- [Dimension ecology](dimension_ecology.md).

### Teaching and visual authoring

- [Discovery guides](better_discovery_guides.md): sole shared teaching/authoring policy.
- [Threads](threads.md): contextual discovery, lineage and facsimiles.
- [Thread art direction](thread_art_direction.md): visual grammar and canonical authored masters.
- [Render geometry](distant_horizons_render_geometry.md): pinned DH source boundary, not pixel proof.

### Integration and acquisition

- [Performance/mod integration](performance_and_mods.md).
- [Cataclysm/cave encounters](cataclysm_better_cave_encounters_integration.md).
- [Untamed Wilds](untamed_wilds_integration.md).
- [Native EMI transport](native-emi-server-transport.md).
- [Native material-cost startup ordering](native-material-cost-prewarm.md).
- [Malum Soulwood](malum_soulwood_acquisition.md).
- [PneumaticCraft capability](pneumaticcraft_capability_acquisition.md).
- [Tech component acquisition](tech01_component_acquisition.md).
- [Script/ownership manifests](refactor_manifests.md), [script summaries](../kjs-script-summaries.md).

## Pack thesis

Better Content is a Forge 1.20.1 expert-pack content layer built around systems natural to its
world: Matter, Place and Life make expected reality mechanically present; Blood, Fonts and
Traces Into Lineage introduce distinct supernatural premises. History is a cross-cutting gameplay
persistence rule; Society emerges from actors, resources and consequences. This is a design
model, not a claim that every implementation already conforms.

Tech, magic and adventure are player-facing packages rather than assumed causal roots.
Geological deposits, local processing, machine tiers, spirit markets, Fonts, bodily systems,
traces and the death/respawn loop are progression surfaces connecting those roots.

## Documentation contract

Keep current durable guidance in the closest living subject. Each document states purpose/scope,
its owner/source authority and relevant operating/verification boundaries, with related links
where useful. Mod-specific design stays in that source repository; shared policy links to it.
READMEs describe purpose and local use; AGENTS files describe unique agent constraints and exact
local commands. Do not duplicate shared testing, retention or inventory-acceptance prose.

Update the subject and ownership/script inventories when implementation changes. Use current
manifests and source to reconcile names, counts, commands and API claims. Do not hard-code an
active-mod count in multiple documents. Machine-readable catalogs stay authoritative for their
own data rather than becoming competing narrative policy.

Do not add one-off audits, pass reports, raw logs, dumps, snapshots, historical plans or diagnostic
JSON under docs, or recreate `quarantine/docs/`. During active tasks, outputs live in classified
disposable roots; at handoff they are removed under [generated-data.md](policies/generated-data.md).
Do not copy old acceptance claims or advertise deleted evidence paths as still available.
Changelogs and license notices are repository history/legal metadata, not retention authorities.
Runtime `.txt` files may be live configuration; do not classify by extension alone.

The lightweight link/ownership contract is `python3 -B scripts/check_documentation.py` from the
modpack. It examines maintained Git/source docs, not generated fixtures or installed vendor docs.
