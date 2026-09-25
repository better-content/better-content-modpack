# TECH-01 — Meteor and early technology component map

This source map records the authored and pinned routes used by the early technology palette. It distinguishes the meteor branch from the later Energy Acceptor / ME-network gate. It does not claim candidate recipe registration or runtime reachability.

## Component and acquisition matrix

| Component | Authored/native route | Progression role |
| --- | --- | --- |
| AE2 Sky Stone | AE2 meteorites provide the authored meteor material. The pack removes the Occultism miner recipe type because the pinned miner recipe directly outputs `ae2:sky_stone_block`. Blood Magic's `bloodmagic:meteor/ae2` invocation (500,000 LP syphon) is removed by exact recipe ID because it creates repeatable Sky Stone meteor material. Certus Quartz still has to come from finding an AE2 meteor. Other Blood Magic meteor recipes remain available. | Sky Stone comes from AE2 meteor world generation; Certus remains meteor-dependent. No pack-authored NPC or loot-table producer was found in the reviewed fixture. |
| `ae2:certus_quartz_crystal` / `ae2:charged_certus_quartz_crystal` | AE2's natural Certus deposit and charging apparatus. The authored controller recipe consumes one `ae2:charged_certus_quartz_crystal`. | Early material from AE2's world resources, not an operating ME network. |
| Create Rose Quartz | Pinned Create 6.0.8 `data/create/recipes/crafting/materials/rose_quartz.json`: one `forge:gems/quartz` plus eight redstone dust. | The quartz tag permits provider quartz; this recipe does not force every quartz source to Certus. |
| Polished Rose Quartz | Pinned Create 6.0.8 `data/create/recipes/sandpaper_polishing/rose_quartz.json`: sandpaper polish one Rose Quartz. | Intermediate for the Electron Tube. |
| Electron Tube | Pinned Create 6.0.8 `data/create/recipes/crafting/materials/electron_tube.json`: one Polished Rose Quartz and one `forge:plates/iron`. | Used by the Brass Machine Block, Deployer, Mechanical Crafter, and the authored AE2 controller recipe. The vanilla Create recipe is retained; Certus-only tubes were an audit proposal, not adopted policy. |
| Power Grid Integrated Circuit | Pinned Power Grid 0.5.4 `data/powergrid/recipes/mechanical_crafting/integrated_circuit.json`: Rose Quartz, redstone, gold nuggets, and lapis. | Part of the authored controller and the separate electrical branch. This exact recipe does not use Certus or Sky Stone. |
| Electrical Machine Block / Generator Housing | Pack source builds Conductive Casing from Create Copper Casing plus More Red Alloy Wire; the Electrical Machine Block then requires the Airtight Machine Block, casing, copper plates, wire, and one Electron Tube. The first Generator Housing requires iron/copper plates, Conductive Casing, and the Electrical Machine Block. | Power Grid's stationary electrical root is available through the authored electrical machine chain, without an AE2 Energy Acceptor. |
| AE2 Controller | `80_tech_palette.js` replaces the output recipe with a PneumaticCraft Pressure Chamber recipe at pressure 4.0 using `ae2:sky_stone_block`, `ae2:charged_certus_quartz_crystal`, `ae2:engineering_processor`, `powergrid:integrated_circuit`, and two `create:electron_tube`. | A pressure-built AE2 control root using meteor material and non-ME prerequisites. |
| AE2 Energy Acceptor | `20_ratlantis_logistics.js` has the sole pack-script producer: Power Grid Generator Housing, Ratlantis Arcane Logistics Core, OC2R Computer, Sky Stone, and Impossible Support Matrix. | Deliberately remains the later conjunctive, Ratlantis-rooted gate. It is separate from the early meteor palette. |

## Source evidence and limits

The Create fixture jar SHA-1 is `b13d912b9247a38d66d11598c121442585a1c1e9`; the Power Grid fixture jar SHA-1 is `ebdbcd3e267c8a92e6685728f9d7fdd3e7b9838a`. Both match the active Packwiz pins. The exact recipe resources above were read from those jars. The pinned Occultism bypass removal is protected by `TechPaletteSourceContractTest`.

The reviewed retained server fixture's static recipe/loot scan found no direct pack target loot or villager-offer output. A classfile scan of the active pinned mod set likewise found no target strings in known NPC offer classes. Dynamic offer registration that references registry fields rather than serialized item IDs is not ruled out. A full recipe graph, live merchant census, recipe registration, candidate integration, and Nether/meteor progression playthrough are still open; this source audit does not substitute for them.
