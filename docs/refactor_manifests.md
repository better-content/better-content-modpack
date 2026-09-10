# Current Implementation Manifests

These manifests describe the checked-in implementation. They are maintenance
indexes, not migration instructions or substitutes for recipes, registries, or
custom-mod source. When a row disagrees with executable content, fix the row and
the closest living design document in the same change.

## Executable KubeJS surface

KubeJS executes JavaScript recursively under all three active roots. The current
tree contains 71 active scripts: 6 client, 57 server, and 8 startup. The exact
path and responsibility index is maintained in [`../kjs-script-summaries.md`](../kjs-script-summaries.md).

| Root | Active areas | Count |
| --- | --- | ---: |
| `kubejs/client_scripts` | compatibility 4, guidance 1, policy 1 | 6 |
| `kubejs/server_scripts` | compatibility 37, policy 6, progression 10, transport 1, utility 2, root policy report 1 | 57 |
| `kubejs/startup_scripts` | compatibility 3, policy 3, progression 2 | 8 |

The `compat/retained` and `compat/reviewed` names classify ownership history;
they do not disable execution. `kubejs/inactive_review` is outside every active
script root and remains non-executable. No action-named directory is treated as
a staging area inside an active root.

## Progression and ownership

| Surface | Runtime authority | Pack-owned boundary |
| --- | --- | --- |
| Six technology eras and Machine Blocks | Pack KubeJS | Exact cross-mod root recipes, transition items, optional transport gates |
| Geological deposits and processing | Realistic Ores | Era inputs only; see [`realistic_ore_processing.md`](realistic_ore_processing.md) |
| Radiation and contained chemistry | Latent ChemLib | Cross-mod gates; no duplicate physical simulation |
| Industrial heat | Heat Sync | Era placement and integrations; Heat Sync owns storage, transfer, ambient mapping, and Create boiler heat |
| Dimension Fonts | Dimension Drink | Recipes and milestone references only; Dimension Drink owns obelisks, charge, sessions, tickets, and arrival sites |
| Settlement paths | Settlement Roads | No pack placement script; Settlement Roads owns dry-route planning, persistence, road palette, and placement; bridge scaffolding is dormant and unsupported |
| Vanilla vessel durability and recipes | Better Content Fixes | No boat mutation or hiding script |
| Credited-kill spirit release, seven village professions/stations/behaviours, matching spirit trades, and seven wandering identities | Better Content Economy | No parallel KubeJS spirit source, trade catalogue, profession conversion, or kill-drop script; pack policy only retires old coin/equipment surfaces |
| TConstruct affixes and Epic Fight mapping | Tinkers' Construct Affixes | Pack supplies no parallel material-affix catalogue |
| World reset lifecycle | World Lifecycle Manager | Pack supplies runtime configuration and deploys its independent JAR |
| Persistent footprints and notes | Player Traces | Pack supplies runtime configuration and deploys its independent JAR |

The six stable pack IDs are:

- `kubejs:andesite_machine_block`
- `kubejs:copper_machine_block`
- `kubejs:brass_machine_block`
- `kubejs:airtight_machine_block`
- `kubejs:electrical_machine_block`
- `kubejs:space_machine_block`

Old Machine Casing identities, generic washed/tailings forms, duplicate ore
catalogues, and universal recipe-introspection scripts have no active owner.

## Dimension Font charge contract

An unmodified obelisk has 15,000 maximum charge, a 600-charge start cost, no join
cost, and 0.25 passive charge per inactive tick. An active run drains 80 charge plus 40
per participant once per second. Data-driven capacity and efficiency modifiers
may change run length; a full unmodified Font supplies 120 solo active seconds
after its start cost. Starting and joining charge is deducted only after the
backend accepts the transition; exhausted charge returns participants and closes
the run. The origin chunk is ticketed for the active run. Arrival sites have no
resource-fluid storage, input slot, compatibility item, or resource scatter.

## Settlement road contract

Settlement Roads is an active bundled custom mod. Ground routes place a complete
three-block-wide surface: dirt path in grassy biomes and gravel in non-grassy
biomes. A deterministic 20% of eligible road-edge cells uses coarse dirt.
Ground routes do not add cobblestone guide marks or roadside walls. Water bridges
are disabled and unsupported: the planner uses a dry detour when one is available
and otherwise omits the connection. Bridge code and isolated bridge tests remain
dormant scaffolding and do not establish supported pack behavior.

## Validation and deployment

Custom-mod repository, artifact, and command ownership is canonical in
[`custom-mod-workspace.md`](custom-mod-workspace.md). A source change is complete
only after that repository's documented validation passes. Deployment uses the
reobfuscated runtime JAR from `build/libs/`, followed by `packwiz refresh` and
the explicitly ordered granular pack suites exposed by `./test.main.kts`.
