# Untamed Wilds wildlife integration

The pack pins the published Forge 1.20.1 `untamedwilds-4.0.4-b004.jar` (CurseForge project 1399279, file 7441092). Its SHA-1 is `b4007ecf2fed03b89205b6fdd3fba6980c9e39bd`.

## Natural wildlife

Untamed Wilds' six ungated fauna worldgen modifiers are replaced by copies whose `configOption` points to `mobcontrol.masterspawner`. The common config keeps that switch false. The native underground fauna modifier already uses the switch. Flora modifiers remain active. In Control then replenishes 38 entries from the published JAR's critter, herbivore, predator, underground, ocean, and river tables, using a chance of `weight / 2000` per second, 20 placement attempts, bounded groups, and per-type caps. Land rules require sturdy ground, water rules require water, and cave rules stay below Y=48. Each In Control rule uses an `and.biometags` filter matching the species habitats in the published JAR. The filters avoid the upstream bear crash when its natural-spawn finalizer finds no valid species. Obsolete biome IDs in that JAR are omitted. Its feeder-only butterfly and benthos giant clam have no replenishment rule.

The exact-overlap priority is Genetic Animals, then Untamed Wilds, then Thalassophobia, then vanilla. Genetic Animals' farm animals retain their existing spawn policy. Untamed's softshell turtle, tortoise, and newt are distinct from Genetic Animals' enhanced turtle and axolotl. Vanilla camel, panda, and polar bear are denied for natural spawns only. Thalassophobia's sunfish, catfish, big catfish, generic shark, bull shark, goblin shark, hammerhead shark, whale shark, and whale lose their biome add-spawns entries and have a natural-spawn denial backstop. Its basking, frilled, and cookie-cutter sharks, fantasy or prehistoric creatures, and unique systems retain their native behavior.

Thalassophobia's naturally spawning giant clam remains because its live pearl harvest is repeatable. Untamed's giant clam is still available through non-natural means. One-way recipes convert Thalassophobia pearl to Untamed pearl and black pearl to Untamed giant pearl, preserving Untamed's pearl consumers.

EMI Loot excludes only Untamed's baleen whale preview. Its client worker otherwise constructs that entity before Untamed's species data is ready and logs an exception. The whale and its actual loot table remain in the game.

## Checks and tuning

The Dev suite checks file contracts without launching the pack. The targeted join test verifies startup with the full client and server pack and rejects log errors. It does not measure wildlife population, underwater placement, biome selection, or the live clam interaction; those still need a dedicated runtime check. If replenishment needs tuning, edit `config/incontrol/spawner.json` and the matching `kubejs/data/kubejs/tags/worldgen/biome/untamed_spawn/` filter; keep the JAR's species biome data and the disabled native fauna features as the authority for habitat and duplicate control.
