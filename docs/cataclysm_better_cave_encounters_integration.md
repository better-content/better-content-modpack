# Cataclysm and Depth Director placement

This pack integrates L_Ender's Cataclysm 3.31 for Forge 1.20.1 with Lionfish API 3.0. The pack owns the placements through biome-tag data and In Control rules; it does not use a Cataclysm dimension add-on. Cataclysm's arena structures remain intact so each boss keeps its native arena mechanics, including its respawner and activation altar.

## Arena destinations

| Cataclysm boss | Arena | Pack destination |
| --- | --- | --- |
| Leviathan | Sunken City | Overworld deep oceans |
| Netherite Monstrosity | Soul Blacksmith | Nether Wastes |
| Ignis | Burning Arena | Crimson Forest, Nether |
| Ender Guardian | Ruined Citadel | Creating Space Earth orbit, elevated to Y=128 |
| Harbinger | Ancient Factory | Fallout Wastelands |
| Ancient Remnant | Cursed Pyramid | Fallout Wastelands |
| Scylla | Acropolis | Aether skyroot biomes |
| Maledictus | Frosted Prison | The Deep Void |

The Cursed Pyramid and Ancient Factory share the Fallout destination but use their own structure spacing and biome tags. The Ruined Citadel needs a fixed elevated start because Earth orbit is void terrain; its custom structure lays its pieces relative to the start point. Acropolis was previously globally disabled in Structurify; that suppression is removed so its Aether biome placement can take effect. All eight arenas are explicitly denied in Lost Cities. Existing generated chunks are unchanged; arenas relocate in newly generated terrain.

## Depth Director miniboss roster

Each selected miniboss is a one-at-a-time heavy encounter, gated to deep ecology pressure:

| Ecology | Cataclysm miniboss | Native encounter drop retained |
| --- | --- | --- |
| Undead | Aptrgangr | Strange Key, bones, rotten flesh |
| Carrion | Coralssus | Coral Chunk |
| Spirits | Ignited Revenant | Burning Ashes (Ignis activation item) |
| Sculk | The Prowler | Redstone and iron |
| End | Ender Golem | Void Core |
| Deep Void | Kobolediator | Kobolediator Skull, Koboleton Bone, Ancient Metal |

These rewards stay in Cataclysm's native loot tables, so boss preparation materials are found by engaging the corresponding Depth Director encounter. The pack does not add a second reward path or replace Cataclysm drops.

Natural spawning is denied for all nine Cataclysm minibosses: the six in the roster and Wadjet, Amethyst Crab, and Clawdian. This keeps the selected six in Depth Director encounters while retaining Cataclysm's ambient Deeplings, Koboletons, and other non-miniboss ecology. The DD profiles remain dimension-eligible under Depth Director's normal rules, including Lost Cities; Flesh That Hates remains exclusive to its Lost Cities planet and is not part of these profiles.

## Change boundary

The five established ecology files and the Deep Void ecology are overlaid from KubeJS data, not edited in the Depth Director source repository. Existing worlds and already-generated chunks are not retroactively relocated or migrated. Pack tests, deployment, and distribution were not part of this content change.
