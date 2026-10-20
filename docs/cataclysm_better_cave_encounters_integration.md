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

The Cursed Pyramid and Ancient Factory share the Fallout destination but use their own structure spacing and biome tags. The Ruined Citadel needs a fixed elevated start because Earth orbit is void terrain; its custom structure lays its pieces relative to the start point. Acropolis was previously globally disabled in Structurify; that suppression is removed so its Aether biome placement can take effect. Lost Cities is disabled; its eight arena exclusions are retained under `disabled/lost-cities/config/incontrol/structure.json`, outside active configuration. Existing generated chunks are unchanged; arenas relocate in newly generated terrain.

## Depth Director miniboss roster

Each selected miniboss remains a deep, Director-only heavy. Mapped Overworld spawns now
use the local raw-rock habitat, while the historical profiles remain timing/unmapped
fallback inputs. There is at most one heavy per packet and no simultaneous live heavy
within an encounter family, including split/merge and unload/reload:

| Historical fallback | Mapped habitat | Cataclysm miniboss | Native encounter drop retained |
| --- | --- | --- | --- |
| Undead | Ochre tombs | Aptrgangr | Strange Key, bones, rotten flesh |
| Carrion | Karst nests | Coralssus | Coral Chunk |
| Spirits | Silver haunt | Ignited Revenant | Burning Ashes (Ignis activation item) |
| Sculk | Banded sentinels | The Prowler | Redstone and iron |
| End | Mineral rifts | Ender Golem | Void Core |
| Deep Void | Separate dimension roster, unchanged | Kobolediator | Kobolediator Skull, Koboleton Bone, Ancient Metal |

These six native reward paths remain intact, under the existing global loot policy.
There is no ecology-specific replacement table, drop suppression or equipment removal.
The new ordinary Koboleton/Draugr/Endermaptera and Director-only Elite Draugr/Royal Draugr/
The Watcher placements deliberately export suitable non-boss structure/dimension actors.
Koboleton and Draugr are ordinary in Ochre tombs; Elite Draugr, Royal Draugr and
`cataclysm:the_watcher` join Banded sentinels only through the Director; Endermaptera is
ordinary in Mineral rifts. This intentionally broadens native Ancient Metal and Black Steel
access. The Watcher here is not `alexscaves:watcher`. Native arena mechanics, equipment,
loot chances and terrain behavior remain unchanged; occurrence is the new access path.

Natural spawning is denied for all nine Cataclysm minibosses: the six in the roster and Wadjet, Amethyst Crab, and Clawdian. This keeps the selected six in Depth Director encounters while retaining Cataclysm's ambient Deeplings, Koboletons, and other non-miniboss ecology. The DD profiles remain dimension-eligible under Depth Director's normal rules. Lost Cities is disabled and is no longer an active destination; Flesh That Hates is not part of these profiles, and its existing behavior is unchanged by that cut.

## Change boundary

The five established fallback ecology files and Deep Void overlay remain intact in KubeJS
data. Ten new `geological_ecologies/` definitions and raw-rock tags provide the mapped
Overworld roster separately; the source owners implement local selection, ordinary admission
and finite causal lineage accounting. See [dimension ecology](dimension_ecology.md) for the
complete populations, tuning, legacy-denial migration and save contract. Existing arenas and
already-generated chunks are not relocated retroactively; current raw stone, including placed
raw stone, can change the next local population without replacing any live mob.

This is authored source/data, **not a compiled or runtime-accepted release**. New deterministic
and retained native fixtures, including live global-loot-policy and six-reward-path assertions,
remain unrun until separately authorized. A native source fixture cannot establish full-pack
same-artifact acceptance. No deployment, Packwiz refresh, distribution or release preparation
is implied by this source change.
