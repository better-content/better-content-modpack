# Five-spirit commerce: candidate offer catalogue

**Status: research proposal, 2026-11-17; not installed, tested, priced against a live economy, or approved for release.** This is the literal item-by-item catalogue requested for discussion. It does not alter any mod, pack, registry, world, or existing offer. The friends' Lineage world has not started. Treat `†` rows as **conditional candidates, not ready-to-ship offers**; the final recommendation is to replace a conditional row, not silently sell a gated item, if the stated check fails.

## Decision and size

The five proposed payment identities are **Power** (Impact + Work), **Motion** (Mobility + Tempo), **Endurance** (Endurance + Robustness), **Renewal**, and **Control**. Five villager-specialist pools contain **40 rows each, eight possible offers at each of five profession levels**: 200 village candidates total. Each specialist accepts exactly its *one* matching commerce spirit. Five wandering pools contain **35 goods plus one matching campfire each**: 180 rows total. The present eight-theme wandering data has 36 rows *including* its campfire, not 36 goods plus a campfire. Retain the present twelve-goods-plus-campfire visit: four guaranteed cheap staples, four draws from sixteen supplies, three from ten equipment choices, one from five standouts, and the matching campfire. A visited seller should not reroll on menu reopening or restock; villagers acquire new offers at level-up without replacing sold offers. This is a recommendation to preserve choice and stock, not an assertion that the current villager path implements it.

This is a **pool**, not a 40-item menu on one villager. Normal villager level-up selection can show a small subset from the eight candidates at that level. If every villager must have a fixed introductory offer, that requires a deliberate selection rule rather than merely registering eight Forge listings. The Local Market should show each seller's actually selected, available offers and payment spirit, not all pool candidates.

### How to read the lists

`item ID × output count — spirit cost` defines one suggested offer. A villager's proposed `maxUses` is **16** for cost 1–2, **8** for cost 3–4, **4** for cost 5–6, and **2** for cost 7–8, except where a finite global stock cap is stricter. Suggested XP is `2 × spirit cost`; these are *proposal defaults*, not measurements of acquisition rate. Wandering offers propose **16 uses** for cost 1–3, **8** for 4–8, **4** for 9–16, **2** for 17–32, and **1** for 33–64; standouts are capped at **one use** even when cheaper. The campfire is one for one spirit, 16 uses, as today. One-time spawn-egg and surveyed-Font-map offers are **not** counted in the 36-row goods pool. Existing per-output import ceilings must also be reconciled separately; reducing `maxUses` on one merchant is not a global scarcity policy.

The levels/buckets below are design classifications, not a promise that the listed item's loaded recipe or registry is correct. **75 of 200 village rows and 60 of 180 wandering rows are marked `†`**: those are research candidates, not a ready-to-enable majority. `†` means an effective-recipe, origin, stock, optional-mod, or NBT review is mandatory before the row can enter a shipping pool. A **hold** means do not enable it without a proven non-bypass route (and replace it if that route is unavailable); a conditional row is not a substitute for evidence. Even unmarked rows need the final loaded-pack trade/recipe census. For an ordinary early trade, *obtainable basic materials and utility* are acceptable; an otherwise gated dimension resource, meteor material, machine root, or renewable crop propagule is not an acceptable shortcut simply because the item exists. Commodity prices should be rebalanced after measuring the five new currency acquisition rates—especially Control and Renewal—not copied from the old eight-way pools.

## Village specialist pools — forty possibilities each

### Power — workshop supplier

**Promise:** turn hand materials into useful work, heat and bounded mechanical handling. The first tiers supply worksite materials; later tiers help an established workshop, but do not sell its capability-gating Press, Mixer, powered Sifter, Crushing Wheels or later power source. A small iron-nugget offer is conditional because even a limited metal import can replace the first ore trip.

**Novice (8):**

1. `minecraft:flint` ×8 — 1; hand-tool cutting edge.
2. `farmersdelight:straw` ×16 — 1; fibre gathered by primitive cutting.
3. `farmersdelight:canvas` ×4 — 2; first sacks and backpacks.
4. `farmersdelight:tree_bark` ×12 — 1; low-tech process material.
5. `minecraft:brick` ×8 — 2; fired fittings.
6. `minecraft:glass` ×8 — 2; bottles and workshop windows.
7. `minecraft:torch` ×16 — 2; lighting an active worksite.
8. `minecraft:iron_nugget` ×4 — 3 †; *at most* a few fittings, not a renewable ore replacement.

**Apprentice (8):**

9. `create:sand_paper` ×4 — 3; finishing work.
10. `minecraft:shears` ×1 — 3; work-tool convenience.
11. `minecraft:stonecutter` ×1 — 4; stone shaping.
12. `farmersdelight:cutting_board` ×1 — 4; hand processing.
13. `supplementaries:bellows` ×1 — 4; manual heat aid.
14. `minecraft:barrel` ×2 — 3; local storage.
15. `supplementaries:sack` ×1 — 4; portable handwork storage.
16. `minecraft:lantern` ×2 — 4; durable workshop light.

**Journeyman (8):**

17. `create:belt_connector` ×4 — 4; local transfer material.
18. `create:chute` ×4 — 4; bounded gravity handling.
19. `minecraft:scaffolding` ×16 — 3; worksite access.
20. `create:cardboard` ×16 — 4; packing material.
21. `create:depot` ×2 — 5; ordinary workshop staging.
22. `create:crafting_blueprint` ×1 — 5; work instructions, not automation.
23. `minecraft:smithing_table` ×1 — 5; fitting station, no upgrade template.
24. `minecraft:grindstone` ×1 — 4; tool upkeep.

**Expert (8):**

25. `create:super_glue` ×1 — 6; small assembly aid with finite import stock.
26. `create:fluid_pipe` ×8 — 5; local fluids, not free pumping.
27. `create:fluid_tank` ×1 — 6; finite storage.
28. `create:andesite_funnel` ×2 — 6 †; verify that selling alloy-fitted handling does not pre-empt the Seared alloy route.
29. `create:andesite_tunnel` ×2 — 6 †; same alloy-route check.
30. `create:portable_storage_interface` ×1 — 7 †; check contraption/part prerequisites and assembled reach.
31. `tconstruct:repair_kit` ×1 — 5 †; require a valid usable default stack/material; do not sell a blank kit.
32. `minecraft:cauldron` ×1 — 5; process vessel.

**Master (8):**

33. `minecraft:blast_furnace` ×1 — 8; iron-intensive faster heating, not ore creation.
34. `minecraft:anvil` ×1 — 8 †; finite iron-import ceiling required.
35. `create:brown_toolbox` ×1 — 8 †; check the gold-plate route before import.
36. `create:extendo_grip` ×1 — 8 †; check reach and original material/capability gates.
37. `minecraft:hopper` ×1 — 7; limited bulk iron convenience.
38. `minecraft:piston` ×2 — 7; mechanical force, not autonomous crafting.
39. `minecraft:dispenser` ×1 — 7; bounded world action, not a Deployer substitute.
40. `minecraft:tnt` ×2 — 8 †; force/heat item; mining and combat economics need a strict cap.

### Motion — wayfinder and carrier

**Promise:** know where to go and carry people or goods there. Rails and small vehicles are ordinary transport; finished Create train infrastructure and later locomotives are conditional because their pack roots may be an intentional precision/Nether gate. No jet-boot upgrades or travel to an undiscovered dimension is granted by a catalogue entry.

**Novice (8):**

1. `farmersdelight:rope` ×16 — 1; primitive route-making.
2. `minecraft:lead` ×2 — 2; moving livestock.
3. `minecraft:oak_boat` ×1 — 1; immediately usable water travel.
4. `minecraft:map` ×2 — 2; map-making material, not a prefilled world survey.
5. `minecraft:compass` ×1 — 3; finding home.
6. `minecraft:ladder` ×16 — 2; vertical route.
7. `minecraft:minecart` ×1 — 3; first rail ride.
8. `minecraft:rail` ×16 — 3; a small real route, not a complete railway.

**Apprentice (8):**

9. `minecraft:chest_minecart` ×1 — 4; freight.
10. `minecraft:powered_rail` ×8 — 5; powered route section.
11. `minecraft:detector_rail` ×4 — 5; train detection.
12. `minecraft:activator_rail` ×4 — 5; route actuation.
13. `minecraft:clock` ×1 — 4; trip timing.
14. `minecraft:spyglass` ×1 — 4; scouting.
15. `supplementaries:rope_arrow` ×8 — 5; traversing terrain.
16. `minecraft:saddle` ×1 — 8 †; finite alternative to finding/earning a saddle, not a new mount tier.

**Journeyman (8):**

17. `hangglider:glider_wing` ×1 — 5; repair or build component.
18. `hangglider:glider_framework` ×1 — 6; another build component.
19. `hangglider:hang_glider` ×1 — 8; attainable early gliding opportunity.
20. `minecraft:firework_rocket` ×8 — 5 †; must author valid Flight NBT, not a default invalid rocket.
21. `create:minecart_coupling` ×2 — 6; short vehicle train.
22. `minecraft:recovery_compass` ×1 — 8 †; review echo-shard discovery value and NBT/default behavior.
23. `naturescompass:naturescompass` ×1 — 8 †; discoverability benefit, not guaranteed safe arrival.
24. `minecraft:phantom_membrane` ×4 — 6; repair/travel supply.

**Expert (8):**

25. `create:track` ×24 — 7 †; verify Deployer/Press origin and cap track imports.
26. `create:controller_rail` ×8 — 7 †; precision-train gate review.
27. `create:track_signal` ×2 — 7 †; same train-system review.
28. `create:track_observer` ×2 — 7 †; same train-system review.
29. `littlelogistics:seater_car` ×1 — 7 †; check its Nether-material recipe and usable rail route.
30. `littlelogistics:chest_car` ×1 — 7 †; same route review.
31. `littlelogistics:seater_barge` ×1 — 7; native small-vessel route.
32. `littlelogistics:fishing_barge` ×1 — 8 †; check the pack's Starcatcher rod replacement.

**Master (8):**

33. `hangglider:reinforced_hang_glider` ×1 — 8 †; upgrade cost and actual lift/safety.
34. `littlelogistics:tug` ×1 — 8 †; actual fuel/route utility.
35. `littlelogistics:barge` ×1 — 7; primitive freight hull.
36. `littlelogistics:barrel_car` ×1 — 7 †; verify installed registry and recipe.
37. `littlelogistics:fluid_barge` ×1 — 8 †; inspect fluid transport and fuel source.
38. `littlelogistics:fluid_car` ×1 — 8 †; inspect fluid transport and rail gate.
39. `create:track_station` ×1 — 8 †; **hold unless** selling it cannot bypass the Precision Mechanism/Railway Casing train root.
40. `littlelogistics:steam_locomotive` ×1 — 8 †; **hold unless** its Blaze/Nether transport root is protected.

### Endurance — outfitter

**Promise:** withstand climate, combat, wear and long expeditions. These are protective or sustaining resources, not healing, food cultivation, or a cheap way to buy the complete diamond/geology ladder. Specialized late gear is conditional until its ingredients and pack routes are known.

**Novice (8):**

1. `minecraft:white_wool` ×8 — 1; beds and insulation.
2. `minecraft:leather` ×4 — 2; protective crafting.
3. `minecraft:coal` ×8 — 2; fuel reserve, not limitless generation.
4. `minecraft:shield` ×1 — 3; first defensive option.
5. `minecraft:white_bed` ×1 — 3; safe rest.
6. `minecraft:chain` ×8 — 3; rugged construction.
7. `minecraft:iron_bars` ×16 — 4; fortification.
8. `cold_sweat:goat_fur` ×4 — 4; early climate preparation.

**Apprentice (8):**

9. `cold_sweat:waterskin` ×1 — 5; carried water, not automatically safe water.
10. `cold_sweat:sewing_table` ×1 — 5; fitting protective clothing.
11. `cold_sweat:goat_fur_helmet` ×1 — 6; cold protection.
12. `cold_sweat:goat_fur_boots` ×1 — 6; cold protection.
13. `minecraft:chainmail_helmet` ×1 — 5; distinct armour slot.
14. `minecraft:chainmail_boots` ×1 — 5; distinct armour slot.
15. `minecraft:bucket` ×1 — 4; carry essential expedition fluids.
16. `minecraft:campfire` ×1 — 3; portable heat and cooking.

**Journeyman (8):**

17. `minecraft:chainmail_chestplate` ×1 — 7; torso protection.
18. `minecraft:chainmail_leggings` ×1 — 6; leg protection.
19. `cold_sweat:goat_fur_chestplate` ×1 — 7; cold protection.
20. `cold_sweat:goat_fur_leggings` ×1 — 7; cold protection.
21. `cold_sweat:hearth` ×1 — 8 †; climate utility; global finite stock and recipe review.
22. `tconstruct:iron_reinforcement` ×2 — 7 †; check the TCon material route.
23. `tconstruct:seared_reinforcement` ×2 — 7 †; check the Seared route; not a pre-Seared gift.
24. `cold_sweat:minecart_insulation` ×1 — 6; durable travelling shelter.

**Expert (8):**

25. `minecraft:iron_helmet` ×1 — 6; solid defence, not mining output.
26. `minecraft:iron_chestplate` ×1 — 8 †; substantial iron import.
27. `minecraft:iron_leggings` ×1 — 7 †; substantial iron import.
28. `minecraft:iron_boots` ×1 — 6; durable footwear.
29. `sophisticatedbackpacks:backpack` ×1 — 6; carry enough for longer trips; also craftable from canvas.
30. `sophisticatedbackpacks:iron_backpack` ×1 — 8 †; check tiered-upgrade and iron costs.
31. `tconstruct:repair_kit` ×1 — 5 †; typed repair material required; do not mint an empty kit.
32. `cold_sweat:hoglin_hide` ×2 — 8 †; Nether-origin material, **hold** if it skips the expedition.

**Master (8):**

33. `cold_sweat:chameleon_molt` ×2 — 7 †; source/biome review.
34. `pneumaticcraft:armor_upgrade` ×1 — 8 †; **hold** if pressure technology is bypassed.
35. `sophisticatedbackpacks:diamond_backpack` ×1 — 8 †; **hold** if it skips the diamond-upgrade chain.
36. `tconstruct:slimesteel_reinforcement` ×1 — 8 †; **hold** if it skips TCon alloying.
37. `minecraft:iron_horse_armor` ×1 — 6; protect a companion mount.
38. `minecraft:diamond_horse_armor` ×1 — 8 †; finite loot-equivalent protection only.
39. `minecraft:turtle_helmet` ×1 — 8 †; check turtle breeding/resource origin.
40. `minecraft:totem_of_undying` ×1 — 8 †; **hold by default**: raid reward/cheat-death import can invalidate danger.

### Renewal — provisioner and grower

**Promise:** replenish food, health, familiar plants and useful home ecology. Ordinary meals do not confer a seed. Cultivatable grain and vegetable *propagules* are intentionally Bumblezone-origin; do not stock them or casually sell high-tier dishes that grant their intended early nutrition rewards. The specialist is broader than a repetitive cooked-meat menu: dairy, flowers, teas, soil and recovery are distinct reasons to visit.

**Novice (8):**

1. `minecraft:apple` ×4 — 1; forage fruit.
2. `minecraft:honey_bottle` ×2 — 2; nectar/sugar.
3. `minecraft:cooked_beef` ×3 — 2; protein meal.
4. `minecraft:cooked_porkchop` ×3 — 2; protein alternative.
5. `minecraft:cooked_chicken` ×4 — 2; lighter protein.
6. `minecraft:cooked_salmon` ×4 — 2; fish protein.
7. `minecraft:milk_bucket` ×1 — 4 †; count the returned bucket as an iron import.
8. `minecraft:bone_meal` ×16 — 2; cultivation, never the missing seed.

**Apprentice (8):**

9. `rats:cheese` ×4 — 3; dairy preparation.
10. `farmersdelight:beef_patty` ×4 — 3; prepared meat ingredient.
11. `farmersdelight:smoked_ham` ×2 — 4; longer-use prepared meat.
12. `farmersdelight:fruit_salad` ×2 — 4; the pack's forage-fruit recipe.
13. `supplementaries:soap` ×4 — 3; domestic restoration.
14. `farmersdelight:skillet` ×1 — 5; simple cooking tool.
15. `farmersdelight:cooking_pot` ×1 — 6; iron-and-water kitchen, not a late machine.
16. `minecraft:composter` ×1 — 3; soil loop without seeds.

**Journeyman (8):**

17. `farmersrespite:dandelion_tea` ×2 — 4 †; confirm ingredient/effect route.
18. `farmersrespite:rose_hip_tea` ×2 — 4 †; same drink audit.
19. `farmersrespite:green_tea` ×2 — 5 †; same drink audit.
20. `farmersrespite:yellow_tea` ×2 — 5 †; same drink audit.
21. `rats:herb_bundle` ×4 — 4 †; inspect real medicinal utility.
22. `minecraft:beehive` ×1 — 6; **empty** domestic hive, not preloaded bees.
23. `procedural_bouquets:bouquet_grid` ×1 — 5; garden display.
24. `procedural_bouquets:potted_bouquet` ×1 — 5; domestic flora.

**Expert (8):**

25. `minecraft:oak_sapling` ×2 — 4; common tree restoration.
26. `minecraft:birch_sapling` ×2 — 4; common tree choice.
27. `minecraft:spruce_sapling` ×2 — 4; common temperate tree choice.
28. `minecraft:red_mushroom` ×4 — 4; fungi and meals.
29. `minecraft:brown_mushroom` ×4 — 4; complementary fungi.
30. `minecraft:azalea` ×2 — 4; local cave greenery.
31. `minecraft:flowering_azalea` ×2 — 5; flowering cave greenery.
32. `fruitsdelight:fig_chicken_stew` ×2 — 7 †; use the pack's fig/chicken/milk recipe; audit Diet credit.

**Master (8):**

33. `fruitsdelight:mango_milkshake` ×2 — 6 †; fruit+dairy benefit, not a new cultivar.
34. `fruitsdelight:blueberry_custard` ×2 — 6 †; fruit+dairy benefit.
35. `minecraft:golden_apple` ×1 — 8 †; one-use-per-villager and global rarity budget.
36. `pneumaticcraft:bandage` ×4 — 7 †; inspect medicine recipe and actual healing.
37. `rats:rat_upgrade_health` ×1 — 8 †; companion recovery, not free rat technology.
38. `malum:rune_of_idle_restoration` ×1 — 8 †; **hold** if ritual/material access is gated.
39. `aether:healing_stone` ×2 — 8 †; **hold** if it moves Aether-only loot into pre-Font villages.
40. `aether:skyroot_remedy_bucket` ×1 — 8 †; **hold** if Aether material origin is bypassed.

### Control — signalwright and binder

**Promise:** distinguish, select, measure, signal and direct. Basic physical switches are cheap; filters and remote/logical control should be valuable later. Do not turn this into a generic 'magic' shop or sell a blank enchanted book. Quartz/Brass/Netherite or higher-tier circuit outputs are conditional until their effective acquisition routes are established.

**Novice (8):**

1. `minecraft:lever` ×16 — 1; hand switch.
2. `minecraft:tripwire_hook` ×8 — 2; physical sensor.
3. `minecraft:target` ×4 — 3; aim/trigger feedback, not a seed.
4. `minecraft:writable_book` ×2 — 2; record plans.
5. `minecraft:book` ×4 — 2; basic inscription.
6. `minecraft:item_frame` ×8 — 3; identify storage.
7. `minecraft:redstone_torch` ×8 — 4 †; small redstone import before Black Shale.
8. `minecraft:note_block` ×2 — 3; audible signal.

**Apprentice (8):**

9. `minecraft:bow` ×1 — 3; deliberate aim.
10. `minecraft:crossbow` ×1 — 5; deliberate held shot.
11. `minecraft:name_tag` ×1 — 5; bind identity.
12. `minecraft:bookshelf` ×2 — 4; inscription support.
13. `minecraft:lectern` ×1 — 4; readable control instructions.
14. `minecraft:bell` ×1 — 5; social alarm.
15. `create:clipboard` ×1 — 5; portable task list.
16. `minecraft:lapis_lazuli` ×8 — 5 †; enchanting input; finite mineral import.

**Journeyman (8):**

17. `minecraft:redstone` ×8 — 5 †; bounded Black Shale alternative, not a new ore farm.
18. `minecraft:repeater` ×4 — 5; timing signal.
19. `minecraft:comparator` ×2 — 6 †; Nether-quartz gate review.
20. `minecraft:observer` ×1 — 6 †; same quartz gate review.
21. `minecraft:daylight_detector` ×1 — 6 †; same quartz gate review.
22. `minecraft:amethyst_shard` ×4 — 5; precision material.
23. `minecraft:spectral_arrow` ×8 — 6; mark a target.
24. `occultism:purified_ink` ×4 — 6 †; check ink's ritual origin.

**Expert (8):**

25. `sophisticatedbackpacks:filter_upgrade` ×1 — 6; selective carry.
26. `sophisticatedbackpacks:pickup_upgrade` ×1 — 6; directed pickup.
27. `sophisticatedbackpacks:magnet_upgrade` ×1 — 7; selective collection.
28. `sophisticatedbackpacks:refill_upgrade` ×1 — 7; controlled refill.
29. `sophisticatedbackpacks:restock_upgrade` ×1 — 7; controlled restock.
30. `create:redstone_link` ×2 — 7 †; check transmitter and andesite-casing access.
31. `create:content_observer` ×1 — 7 †; check monitored-inventory prerequisites.
32. `create:speedometer` ×1 — 7 †; actual measurement utility, not free motor power.

**Master (8):**

33. `sophisticatedbackpacks:advanced_pickup_upgrade` ×1 — 8 †; advanced-upgrade route.
34. `sophisticatedbackpacks:advanced_magnet_upgrade` ×1 — 8 †; same route.
35. `morered:multiplexer` ×2 — 8 †; circuit/material route.
36. `morered:pulse_gate` ×2 — 8 †; circuit/material route.
37. `create:display_link` ×1 — 8 †; **hold** if Brass Casing access is skipped.
38. `create:display_board` ×2 — 8 †; same brass/display route.
39. `minecraft:lodestone` ×1 — 8 †; **hold by default**: Netherite gate and compass binding.
40. `ars_nouveau:blank_parchment` ×4 — 8 †; check spell inscription and source route.

## Wandering pools — thirty-five goods plus campfire per spirit

For every theme below the **four staple rows are always selected**, four of sixteen supply rows, three of ten equipment rows, and one of five standout rows are selected using a trader-stable draw. All five candidate pools must have enough *registered, permitted* items in every bucket; if a `†` row is rejected, replace it before launch. The consumer must not quietly fill a missing equipment/standout slot with an unrelated staple. Currency must match the trader's one spirit. Prices and uses follow the defaults above.

### Power wanderer — portable workshop and force

**Staple (4, always present):**

1. `minecraft:flint` ×8 — 1; cut/build.
2. `farmersdelight:canvas` ×4 — 2; portable workspace.
3. `minecraft:torch` ×32 — 2; workable light.
4. `minecraft:brick` ×8 — 2; fired fitting.

**Supply (16, draw 4):**

5. `minecraft:string` ×16 — 2; binding.
6. `minecraft:paper` ×16 — 2; plans and patterns.
7. `farmersdelight:tree_bark` ×16 — 2; hand-processing stock.
8. `minecraft:charcoal` ×12 — 3; process heat.
9. `create:sand_paper` ×4 — 4; finishing.
10. `create:belt_connector` ×4 — 6; short transmission.
11. `create:chute` ×4 — 6; local handling.
12. `minecraft:scaffolding` ×16 — 4; mobile worksite.
13. `create:cardboard` ×16 — 4; packing.
14. `hexerei:wax_blend` ×4 — 5 †; actual ingredient route.
15. `malum:arcane_charcoal` ×8 — 6 †; magic/work material, not a progression bypass.
16. `malum:hex_ash` ×4 — 6 †; same origin check.
17. `occultism:otherworld_ashes` ×4 — 6 †; same origin check.
18. `minecraft:gunpowder` ×8 — 5; force reagent, global cap.
19. `minecraft:fire_charge` ×8 — 5; bounded ignition.
20. `minecraft:bricks` ×16 — 4; bulk fired construction.

**Equipment (10, draw 3):**

21. `minecraft:shears` ×1 — 6; cutting tool.
22. `minecraft:stonecutter` ×1 — 8; small stoneworks.
23. `farmersdelight:cutting_board` ×1 — 6; handwork station.
24. `minecraft:lantern` ×2 — 5; reusable light.
25. `supplementaries:bellows` ×1 — 8; heat control.
26. `create:crafting_blueprint` ×1 — 8; work planning.
27. `create:super_glue` ×1 — 12; assembly, limited import.
28. `minecraft:blast_furnace` ×1 — 12; hotter work.
29. `minecraft:smoker` ×1 — 8; direct food heating, not crop supply.
30. `supplementaries:sack` ×1 — 8; worksite carry.

**Standout (5, draw 1; one use):**

31. `create:brown_toolbox` ×1 — 18 †; inspect gold-plate cost.
32. `create:extendo_grip` ×1 — 24 †; check reach and capability gate.
33. `minecraft:anvil` ×1 — 24 †; finite iron-import stock.
34. `minecraft:tnt` ×4 — 16 †; explosion/mining balance.
35. `supplementaries:cannon` ×1 — 32 †; **hold** if it bypasses a weapons/progression root.

36. **Campfire:** `better_spirit_commerce:power_campfire` ×1 — 1 †; proposed *new* registry ID, not present in current checkout.

### Motion wanderer — journeys and transport

**Staple (4):**

1. `farmersdelight:rope` ×16 — 2; route-making.
2. `minecraft:lead` ×2 — 3; companion transport.
3. `minecraft:oak_boat` ×1 — 2; immediate river crossing.
4. `minecraft:map` ×2 — 2; record the journey.

**Supply (16):**

5. `fruitsdelight:blueberry` ×16 — 1; portable forage.
6. `fruitsdelight:pear` ×8 — 2; travelling food variety.
7. `fruitsdelight:mango` ×8 — 2; travelling food variety.
8. `fruitsdelight:kiwi` ×8 — 2; travelling food variety.
9. `minecraft:rail` ×32 — 6; a real short route.
10. `minecraft:powered_rail` ×8 — 8; powered stretch.
11. `minecraft:phantom_membrane` ×4 — 6; travel-equipment repair.
12. `create:track` ×24 — 12 †; track assembly/precision gate.
13. `create:controller_rail` ×8 — 12 †; same rail-system gate.
14. `supplementaries:rope_arrow` ×8 — 6; physical traversal.
15. `minecraft:clock` ×1 — 4; departure timing.
16. `minecraft:compass` ×1 — 4; orient home.
17. `minecraft:minecart` ×1 — 8; start rolling.
18. `minecraft:chest_minecart` ×1 — 10; move cargo.
19. `minecraft:ladder` ×32 — 3; climb route.
20. `minecraft:firework_rocket` ×8 — 8 †; valid Flight=1 output required.

**Equipment (10):**

21. `minecraft:saddle` ×1 — 14 †; scarce mount option.
22. `hangglider:hang_glider` ×1 — 14; pack-supported primitive flight.
23. `hangglider:glider_wing` ×1 — 8; field repair/build.
24. `hangglider:glider_framework` ×1 — 10; field build.
25. `minecraft:spyglass` ×1 — 8; distant scouting.
26. `create:minecart_coupling` ×2 — 10; small train.
27. `littlelogistics:seater_barge` ×1 — 12 †; inspect powered/connection needs.
28. `littlelogistics:chest_car` ×1 — 12 †; inspect Nether-plank gate.
29. `littlelogistics:tug` ×1 — 16 †; inspect fuel and route.
30. `littlelogistics:barge` ×1 — 12; native cargo hull.

**Standout (5; one use):**

31. `hangglider:reinforced_hang_glider` ×1 — 24 †; check upgrade/safety.
32. `naturescompass:naturescompass` ×1 — 24 †; locate biome, no teleport.
33. `minecraft:recovery_compass` ×1 — 24 †; echo-shard/Death's Door role.
34. `littlelogistics:fishing_barge` ×1 — 24 †; Starcatcher rod route.
35. `littlelogistics:fluid_barge` ×1 — 24 †; finite logistics, no fluid generation.

36. **Campfire:** `better_spirit_commerce:motion_campfire` ×1 — 1 †; proposed new registry ID.

### Endurance wanderer — expedition outfitter

**Staple (4):**

1. `minecraft:white_wool` ×16 — 1; warm shelter.
2. `minecraft:coal` ×12 — 2; fuel reserve.
3. `minecraft:leather` ×8 — 3; protective material.
4. `minecraft:shield` ×1 — 4; active defence.

**Supply (16):**

5. `cold_sweat:goat_fur` ×8 — 4; insulation.
6. `cold_sweat:hoglin_hide` ×4 — 8 †; Nether origin; **hold** if imported before first Nether trip.
7. `cold_sweat:chameleon_molt` ×4 — 8 †; creature source and scarcity.
8. `minecraft:chain` ×16 — 6; rugged supports.
9. `minecraft:iron_bars` ×16 — 6; fortification.
10. `minecraft:terracotta` ×32 — 4; durable fire-resistant construction.
11. `minecraft:stone_bricks` ×32 — 4; durable construction.
12. `minecraft:mossy_stone_bricks` ×32 — 6; weathered construction.
13. `create:cut_limestone` ×32 — 6; stone building stock.
14. `minecraft:cobblestone_wall` ×32 — 4; sturdy route and camp boundaries.
15. `minecraft:white_bed` ×1 — 5; rest.
16. `cold_sweat:waterskin` ×1 — 8; carry water, purity still matters.
17. `cold_sweat:minecart_insulation` ×1 — 12; protected transport.
18. `minecraft:bucket` ×1 — 5; fluid vessel.
19. `minecraft:campfire` ×1 — 4; real camp.
20. `minecraft:leather_boots` ×1 — 6; basic foot protection.

**Equipment (10):**

21. `cold_sweat:goat_fur_helmet` ×1 — 12; head insulation.
22. `cold_sweat:goat_fur_chestplate` ×1 — 16; torso insulation.
23. `cold_sweat:goat_fur_leggings` ×1 — 16; leg insulation.
24. `cold_sweat:goat_fur_boots` ×1 — 12; foot insulation.
25. `minecraft:chainmail_helmet` ×1 — 12; protective slot.
26. `minecraft:chainmail_chestplate` ×1 — 16; protective slot.
27. `minecraft:chainmail_leggings` ×1 — 16; protective slot.
28. `minecraft:chainmail_boots` ×1 — 12; protective slot.
29. `cold_sweat:sewing_table` ×1 — 12; fitted insulation.
30. `cold_sweat:hearth` ×1 — 24 †; finite regional climate anchor.

**Standout (5; one use):**

31. `sophisticatedbackpacks:backpack` ×1 — 12; carry an expedition; basic canvas recipe remains.
32. `sophisticatedbackpacks:iron_backpack` ×1 — 24 †; check tiered upgrade.
33. `sophisticatedbackpacks:diamond_backpack` ×1 — 48 †; **hold** if diamond progression is skipped.
34. `tconstruct:iron_reinforcement` ×4 — 16 †; check reinforcement material route.
35. `minecraft:turtle_helmet` ×1 — 24 †; turtle source and water survival.

36. **Campfire:** `better_spirit_commerce:endurance_campfire` ×1 — 1 †; proposed new registry ID.

### Renewal wanderer — provisioning and restoration

**Staple (4):**

1. `minecraft:apple` ×8 — 1; dependable forage food.
2. `minecraft:honey_bottle` ×4 — 2; nectar.
3. `minecraft:cooked_beef` ×4 — 2; protein.
4. `minecraft:milk_bucket` ×1 — 4 †; include the empty bucket in import accounting.

**Supply (16):**

5. `minecraft:cooked_porkchop` ×4 — 2; meat variety.
6. `minecraft:cooked_chicken` ×4 — 2; lighter meat.
7. `minecraft:cooked_mutton` ×4 — 2; meat alternative.
8. `minecraft:cooked_salmon` ×8 — 3; fish.
9. `rats:cheese` ×8 — 3; dairy.
10. `farmersdelight:smoked_ham` ×2 — 4; prepared protein.
11. `farmersdelight:beef_patty` ×4 — 3; cooking ingredient.
12. `farmersdelight:fruit_salad` ×2 — 4; pack's forager dish.
13. `fruitsdelight:fig_chicken_stew` ×2 — 5 †; exact pack recipe/food credit.
14. `farmersrespite:green_tea` ×2 — 4 †; ingredient and effect.
15. `farmersrespite:dandelion_tea` ×2 — 4 †; ingredient and effect.
16. `farmersrespite:rose_hip_tea` ×2 — 4 †; ingredient and effect.
17. `farmersrespite:yellow_tea` ×2 — 4 †; ingredient and effect.
18. `supplementaries:soap` ×8 — 3; renewal/domestic care.
19. `minecraft:bone_meal` ×16 — 3; growth aid, not a seed.
20. `fruitsdelight:mango_milkshake` ×2 — 5 †; fruit/dairy effect audit.

**Equipment (10):**

21. `farmersdelight:skillet` ×1 — 8; accessible cooking.
22. `farmersdelight:cooking_pot` ×1 — 12; broader meals, no seed.
23. `minecraft:composter` ×1 — 6; replenish soil.
24. `minecraft:beehive` ×1 — 12; empty bee home.
25. `procedural_bouquets:bouquet_grid` ×1 — 8; home florals.
26. `procedural_bouquets:potted_bouquet` ×1 — 8; home florals.
27. `rats:herb_bundle` ×4 — 8 †; inspect actual utility.
28. `pneumaticcraft:bandage` ×4 — 10 †; medicine rather than a tech shortcut.
29. `minecraft:oak_sapling` ×4 — 5; common woodland renewal.
30. `minecraft:birch_sapling` ×4 — 5; another common woodland choice.

**Standout (5; one use):**

31. `minecraft:golden_apple` ×1 — 20 †; global recovery stock.
32. `rats:rat_upgrade_health` ×1 — 20 †; companion care, rat upgrade route.
33. `malum:rune_of_idle_restoration` ×1 — 40 †; **hold** if ritual gate bypassed.
34. `aether:healing_stone` ×2 — 24 †; **hold** if Aether-only acquisition is intended.
35. `aether:skyroot_remedy_bucket` ×1 — 24 †; **hold** if Aether-only acquisition is intended.

36. **Campfire:** `better_spirit_commerce:renewal_campfire` ×1 — 1 †; proposed new registry ID.

### Control wanderer — signals and selective action

**Staple (4):**

1. `minecraft:arrow` ×32 — 3; controlled reach.
2. `minecraft:lever` ×16 — 1; intentional switching.
3. `minecraft:tripwire_hook` ×8 — 2; event sensing.
4. `minecraft:writable_book` ×2 — 2; records/instructions.

**Supply (16):**

5. `minecraft:spectral_arrow` ×16 — 8; mark a target.
6. `minecraft:target` ×4 — 6; aim signal.
7. `minecraft:book` ×8 — 4; record work.
8. `minecraft:name_tag` ×1 — 8; persistent identity.
9. `minecraft:item_frame` ×8 — 4; labelled storage.
10. `minecraft:redstone_torch` ×8 — 4 †; limited Black Shale alternative.
11. `minecraft:repeater` ×4 — 6; delayed signal.
12. `minecraft:comparator` ×2 — 8 †; quartz-origin check.
13. `minecraft:note_block` ×2 — 4; auditory signal.
14. `minecraft:bookshelf` ×2 — 6; readable/enchanting foundation.
15. `minecraft:lapis_lazuli` ×8 — 6 †; finite enchanting stock.
16. `occultism:purified_ink` ×4 — 8 †; check ritual ingredient source.
17. `ars_nouveau:blank_parchment` ×4 — 8 †; check inscription route.
18. `minecraft:amethyst_shard` ×8 — 6; precision crystal.
19. `minecraft:bell` ×1 — 8; public signal.
20. `minecraft:daylight_detector` ×1 — 8 †; quartz-origin check.

**Equipment (10):**

21. `minecraft:bow` ×1 — 6; precision weapon.
22. `minecraft:crossbow` ×1 — 12; retained aimed shot.
23. `create:clipboard` ×1 — 8; directed tasks.
24. `create:redstone_link` ×2 — 16 †; transmitter and casing route.
25. `create:speedometer` ×1 — 12 †; measure, not generate power.
26. `sophisticatedbackpacks:pickup_upgrade` ×1 — 12; selective pickup.
27. `sophisticatedbackpacks:filter_upgrade` ×1 — 12; selective storage.
28. `sophisticatedbackpacks:magnet_upgrade` ×1 — 16; controlled collection.
29. `sophisticatedbackpacks:refill_upgrade` ×1 — 16; controlled refill.
30. `sophisticatedbackpacks:restock_upgrade` ×1 — 16; controlled restock.

**Standout (5; one use):**

31. `sophisticatedbackpacks:advanced_magnet_upgrade` ×1 — 32 †; tiered upgrade route.
32. `sophisticatedbackpacks:advanced_pickup_upgrade` ×1 — 32 †; tiered upgrade route.
33. `morered:multiplexer` ×4 — 20 †; check circuit route.
34. `morered:pulse_gate` ×4 — 20 †; check circuit route.
35. `create:display_board` ×1 — 24 †; **hold** if Brass Casing production is bypassed.

36. **Campfire:** `better_spirit_commerce:control_campfire` ×1 — 1 †; proposed new registry ID.

## Excluded from the launch suggestion, despite old catalogue presence

- `ae2:sky_stone_block`, `ae2:certus_quartz_crystal`, charged Certus, AE2 processors, controller and related TECH-01 outputs: the pack explicitly removes these merchant outputs. The present villager JSON nevertheless includes Sky Stone and Certus. Do not reintroduce them under a different spirit.
- `minecraft:totem_of_undying` (Endurance village row 40 above) remains a **conditional, default-rejected** master candidate, not an ordinary village reward. The same applies to unbounded diamond armour and automatic survival gear: rarity and death stakes matter more than filling a pool quota.
- Cultivatable grain and vegetable seeds/propagules, Bumblezone-only stock, and conveniently prepared dishes that award those later nutrition groups before their intended origin. The Renewal rows retain only candidate meals whose ingredients and effective Diet credit still need review; an item being edible does not make it progression-safe.
- Aether/Nether/Bumblezone materials, finished Create train systems, Skyroot wheels, Nether-brick Millstones/Sifters/Wheels, Brass precision and pressure/electric roots as unconditional early purchases. Merchant level is **not** a proof of having visited a dimension or built a machine.
- Default `minecraft:enchanted_book` or `minecraft:filled_map` without authored enchant/map NBT, purportedly filled waterskins without validated purity/container data, splash/lingering potions and tipped arrows removed by the pack, and outputs not registered in the selected mod profile. A trade must give the advertised object rather than an empty or inert icon.
- Duplicate same-result rows at different costs/levels used only to reach a count. Genuine local alternatives (different armor pieces, vehicle roles, foods with different dietary behavior) are acceptable after audit; repeated paper/carrot/bone-meal rows under three price points are not.

## Feasibility findings and required decisions before implementation

1. **Current offers are not a working baseline.** In the inspected source, `resident/ResidentRules.isResident` returns true for *all* villagers; `resident/ResidentInteractions` cancels every such interaction; `mixin/AbstractVillagerMixin` clears their MerchantOffers. `trader/VillagerCatalogue.register(VillagerTradesEvent)` has no observed registration in `BetterSpiritCommerce`. The special profession mixin intentionally names existing spirit villagers and does not convert new vanilla villagers. Authoring 200 rows alone cannot create usable specialists. Choose how five ordinary job sites/professions actually enter villages and retain a non-destructive way to meet one of each.
2. **The Local Market is not village-wide today.** `LocalMarketClient` adds its button only to `MerchantScreen`; `LocalMarket.snapshot` scans eight blocks and caps at 256 visible offers, and `validSelection` checks the same distance and stock. Preserve an entry point on every villager and show only actually rolled offers; decide whether the market is a short-range browsing aid or a genuine village-level index. Expanding the query without matching secure selection/merchant-screen handling would expose offers the player cannot buy. The current row action opens the merchant screen and selects an offer; it is not a completed trade. Explain the two-step action in the UI.
3. **The five-currency conversion is not a JSON rename.** Current `CurrencyIdentity`, `SpiritKind`, `WanderingTraderTheme`, profession/campfire registries, trader affordability and collection history, stock policy, maps, colors/models and data validators assume eight identities. Malum defines no Tempo mob mapping; merging Tempo into Motion removes a present acquisition dead end but does not automatically fix low supply of any other spirit. Preserve Malum's distinct native reagents; one-way native-drop → commerce-currency conversion must not duplicate or reverse into reagents. The user states this is for a new world with no old-world backward-compatibility requirement; still do not update existing local saved worlds or discard backups by implication.
4. **Economy and finite stock must remain honest.** Current `WanderingPool` requires exactly 288 rows (36 × 8), 36 rows per kind, four Staples and one Campfire; `EconomyPolicy` validates 245 JSON villager rows, generates 35 Tempo rows from Arcane, and claims 280 total. Change these contracts deliberately for 5 × 40 villagers and 5 × 36 wandering records. `WanderingOfferSelector` uses a trader UUID for stable sampling; `WanderingTraderCatalogue` carries spent units across rebuilt offers. `AuthoredStockData` keeps world-level commodity ceilings distinct from per-seller uses; the proposed wandering pools use unique output IDs across all five themes to satisfy the current global uniqueness rule. A large pool does not justify restocking rare imported goods for every new villager.
5. **Origin and cost audit.** Run a read-only static effective-recipe/registry/loot comparison first for every `†`, then review a *fresh disposable* full-pack world only after test/run authorization. For each candidate, record ID, intended pack stage, true inputs/alternate recipes, natural source and stock. A conditional route that fails should be replaced with another genuinely useful row, not counted as approved. In particular: Motion rows 25–30 and 39–40; Endurance rows 21–23, 30–36, 38–40; Renewal rows 32–40; Control rows 17–21 and 30–40; Power rows 8, 28–31 and 34–40 need focused attention. Reconcile existing `RenewableAcquisitionPolicy` and the pack's MoreJS trade filters before believing an offer will appear.
6. **No release claim from this file.** The source checkout is dirty and may not match the packaged JAR. No tests, builds, GameTests, client screens, saved worlds, live registry census, friends' playtest or player-balance observation were performed here. This is a decision-ready *suggestion pool with explicit holds*, not a promise that 380 safe offers are ready for launch. A release candidate needs exact mod/JAR/pack pairing, source and load-time offer validation, payment/stock transaction checks, two-player Local Market interaction and reconnect, merchant restock/level-up persistence, first-currency accessibility, legitimate alternative progression and fresh-world rollback/backup review. Running tests still requires explicit human approval unless an existing standing permission clearly covers the run.

## Source anchors inspected

- Current source catalogues and validators: [`EconomyPolicy.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/config/EconomyPolicy.java), [`WanderingPool.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/config/WanderingPool.java), [`VillagerCatalogue.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/trader/VillagerCatalogue.java), [`WanderingOfferSelector.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/trader/WanderingOfferSelector.java), [`wandering_pool.json`](../../mod_source/better-spirit-commerce/src/main/resources/data/better_spirit_commerce/spiritcommerce/wandering_pool.json), [`spirit_economy_policy.json`](../../mod_source/better-spirit-commerce/src/main/resources/data/better_spirit_commerce/spiritcommerce/spirit_economy_policy.json).
- Merchant paths: [`BetterSpiritCommerce.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/BetterSpiritCommerce.java), [`AbstractVillagerMixin.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/mixin/AbstractVillagerMixin.java), [`LocalMarket.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/trader/LocalMarket.java), [`LocalMarketClient.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/trader/LocalMarketClient.java), [`AuthoredStockData.java`](../../mod_source/better-spirit-commerce/src/main/java/com/bettercontent/spiritcommerce/trader/AuthoredStockData.java), [`spirit-acquisition.md`](../../mod_source/better-spirit-commerce/docs/spirit-acquisition.md).
- Pack gates and policy: [`design.md` (five-category proposal)](design.md#five-gameplay-categories-for-diet-life-development-and-commerce), [`progression.md`](progression.md), [`world-and-player.md`](world-and-player.md), [`food_eras.js`](../kubejs/server_scripts/progression/food_eras.js), [`00_primitive_workshop.js`](../kubejs/server_scripts/progression/00_primitive_workshop.js), [`20_powered_works.js`](../kubejs/server_scripts/progression/20_powered_works.js), [`30_precision_factory.js`](../kubejs/server_scripts/progression/30_precision_factory.js), [`86_dimension_transport.js`](../kubejs/server_scripts/progression/86_dimension_transport.js), [`removed_items.js`](../kubejs/server_scripts/policy/removed_items.js).
