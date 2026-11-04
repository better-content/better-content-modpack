# KubeJS Script Summaries

This inventory covers every `.js` file under the three executable KubeJS roots. A listed file is loaded from its root, but a guard, placeholder or inert file may add no live recipe. `compat/retained` and `compat/reviewed` are review-history names, not disabled directories. `kubejs/inactive_review` is outside these roots and is excluded.

Current inventory: **114 scripts** — 11 client, 91 server, and 12 startup.

## Client scripts

- `kubejs/client_scripts/compat/retained/check__46_hide_hexerei_mahogany_tree_products.js` — Hides Hexerei’s superseded mahogany tree products from recipe viewers.
- `kubejs/client_scripts/compat/retained/check__48_hide_replaced_fishing_rods.js` — Hides native fishing rods superseded by the pack’s Starcatcher fishing route.
- `kubejs/client_scripts/compat/retained/remove__40_hide_quarantined_systems.js` — Hides exact quarantined and unsupported items from recipe viewers.
- `kubejs/client_scripts/guidance/10_hover_annotations.js` — Adds the pack's item-local hover annotations through the normal tooltip pipeline.
- `kubejs/client_scripts/policy/hide_completed_tcon_tools.js` — Hides completed TConstruct and extension tools from recipe viewers while retaining wearable armor and construction inputs.
- `kubejs/client_scripts/policy/hide_disabled_potions.js` — Hides disabled vanilla potion-delivery stacks and surfaces from recipe viewers while preserving plain water bottles.
- `kubejs/client_scripts/policy/hide_excavated_variants.js` — Hides Excavated Variants’ host-stone permutations from recipe viewers without removing their recipes or world content.
- `kubejs/client_scripts/policy/hide_fire_content.js` — Hides generated Better Wildfire smoldering and burnt blocks from recipe viewers while leaving world effects intact.
- `kubejs/client_scripts/policy/hide_native_ordinary_spirits.js` — Hides native ordinary Malum spirit shards while retaining their registered IDs; Better Content spirits remain visible.
- `kubejs/client_scripts/policy/hide_retired_ratlantis.js` — Hides exact Ratlantis-only items while keeping ordinary Rats items visible.
- `kubejs/client_scripts/policy/hide_vanilla_tools.js` — Hides vanilla-style tools whose reachable native-system replacements own progression.

## Server scripts

- `kubejs/server_scripts/99_world_axioms.js` — Adds only the selected-world Iron Law and Eternal Engine lineage recipe exceptions after ordinary progression; requires the supervisor projection.
- `kubejs/server_scripts/compat/curios_backpack_slot.js` — Tags Sophisticated Backpacks for the Curios back slot.
- `kubejs/server_scripts/compat/retained/check__10_overworld_block_drops.js` — Modifies selected Overworld block drops, including randomized gravel resources.
- `kubejs/server_scripts/compat/retained/check__12_unearthed_regolith_identity.js` — Gives Unearthed regolith variants drops matching their underlying stone identity.
- `kubejs/server_scripts/compat/retained/check__30_stone_cobble_compat.js` — Unifies compatible stone and cobblestone items under shared tags and the furnace-material union.
- `kubejs/server_scripts/compat/retained/check__35_wood_family_alias_compat.js` — Preserves dedicated wood-family recipes and routes otherwise unsupported planks through the oak ladder recipe.
- `kubejs/server_scripts/compat/retained/check__40_dirt_grass_compat.js` — Unifies compatible dirt and grass blocks under shared tags.
- `kubejs/server_scripts/compat/retained/check__50_sand_compat.js` — Unifies compatible sand blocks and items under shared tags.
- `kubejs/server_scripts/compat/retained/check__56_standardize_starcatcher_fishing.js` — Removes competing fishing-rod routes so Starcatcher owns meaningful fishing progression.
- `kubejs/server_scripts/compat/retained/check__75_stone_cobble_tag_compat.js` — Rewrites recipes to accept the pack’s unified tags and adds the basic furnace recipe for any tagged stone or cobblestone.
- `kubejs/server_scripts/compat/retained/check__90_sand_tag_compat.js` — Rewrites recipes to accept the unified sand tag.
- `kubejs/server_scripts/compat/retained/check__95_remove_native_chunkloaders.js` — Removes native chunk-loader recipes in favor of the pack’s magic-gated anchors.
- `kubejs/server_scripts/compat/retained/refactor__balance__110_extreme_y_band_reward_gates.js` — Uses high-altitude and deep-underground materials to gate powerful terrain-reward utilities.
- `kubejs/server_scripts/compat/retained/refactor__balance__125_magic_power_spike_gates.js` — Adds Blood Magic slate requirements to especially powerful rituals, focuses, generators, and magic networks.
- `kubejs/server_scripts/compat/retained/refactor__balance__127_ars_manuscript_progression.js` — Reauthors Ars glyph inscription around magical depth proofs and origin-specific catalysts.
- `kubejs/server_scripts/compat/retained/refactor__balance__146_hexerei_mahogany_to_natures_spirit.js` — Replaces Hexerei mahogany outputs with the pack’s Nature’s Spirit mahogany equivalents.
- `kubejs/server_scripts/compat/retained/refactor__balance__165_protection_pixel_post_ae2_gates.js` — Makes Protection Pixel armor a post-AE2 branch requiring advanced manufacturing and chemistry.
- `kubejs/server_scripts/compat/retained/refactor__balance__166_tome_of_blood_post_ae2_gates.js` — Makes Tome of Blood a post-AE2 hybrid combat-magic progression branch.
- `kubejs/server_scripts/compat/retained/refactor__balance__169_backpack_post_ae2_utility_gates.js` — Gates backpack automation and body-logistics upgrades behind AE2-era components.
- `kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js` — Gates TaCZ workbenches and weapon packs behind appropriate manufacturing milestones.
- `kubejs/server_scripts/compat/retained/refactor__balance__172_storage_recipe_distinction.js` — Gives wood barrels and chests distinct construction recipes while retaining their verified storage capacity.
- `kubejs/server_scripts/compat/retained/refactor__balance__174_tacz_turrets_electrical_gate.js` — Moves TaCZ Turrets behind electrical instrumentation and AE control components.
- `kubejs/server_scripts/compat/retained/refactor__balance__63_fonts_hexerei_occultism_chalk.js` — Connects Hexerei chalk preparation to dimension materials while preserving Occultism’s ritual hierarchy.
- `kubejs/server_scripts/compat/retained/refactor__balance__80_magic_progression_blood_slate_gates.js` — Gates native magic systems and Ars depth crossings behind Blood Magic slates and other magical proofs.
- `kubejs/server_scripts/compat/retained/refactor__balance__82_blood_magic_lifeforce_rework.js` — Makes Still-Beating Hearts the early LP route and pushes stronger Blood Magic throughput deeper.
- `kubejs/server_scripts/compat/retained/refactor__compatability__45_stone_surface_tool_compat.js` — Assigns selected stone-surface blocks to pickaxe mining tags.
- `kubejs/server_scripts/compat/retained/refactor__compatability__85_dirt_grass_tag_compat.js` — Rewrites recipes to accept unified dirt and grass tags.
- `kubejs/server_scripts/compat/retained/refactor__cross_mod_progression__59_formulaic_synthesis_magic_routes.js` — Retains two small Ars crystal crossings without duplicating Realistic Ores separation or acid-specific cutting fluids.
- `kubejs/server_scripts/compat/retained/refactor__move_to_mod__75_arcane_chunkloader_tags.js` — Defines interchangeable early magical proofs accepted by Arcane Chunk Loaders.
- `kubejs/server_scripts/compat/retained/refactor__move_to_mod__95_arcane_chunkloader_recipes.js` — Adds power-specialized Arcane Chunk Loader recipes accepting proof from any supported magical discipline.
- `kubejs/server_scripts/compat/retained/refactor__questionable__70_formal_magic_domains.js` — Generates formal-magic tier, domain, origin, and glyph-provenance tags.
- `kubejs/server_scripts/compat/retained/remove__10_ae2_skystone_tier.js` — Adjusts block tags so AE2 skystone occupies the intended mining tier.
- `kubejs/server_scripts/compat/retained/remove__10_campfire_recipe.js` — Replaces the native campfire recipe with the pack’s explicit crafting route.
- `kubejs/server_scripts/compat/reviewed/chemistry_fluid_compat.js` — Unifies the exact shared chemistry fluids used by installed processing mods.
- `kubejs/server_scripts/compat/reviewed/furnace_policy.js` — Applies the reviewed furnace recipe and fuel policy.
- `kubejs/server_scripts/compat/reviewed/ingot_rewrites.js` — Rewrites exact ingot recipes to the pack's canonical material forms.
- `kubejs/server_scripts/compat/reviewed/kettle_rune_swap.js` — Replaces kettle bucket requirements with Blood Magic altar-capacity runes.
- `kubejs/server_scripts/compat/reviewed/occultism_crushing_outputs.js` — Corrects twelve exact Occultism crushing outputs whose tag fallbacks resolve to unusable placeholders, retaining recipe IDs.
- `kubejs/server_scripts/compat/reviewed/replaceable_deepslate.js` — Maintains the exact replaceable-deepslate compatibility tags used by worldgen.
- `kubejs/server_scripts/compat/reviewed/untamed_thalassophobia_pearls.js` — Adds Thalassophobia clam pearls as a natural input for Untamed pearl recipes.
- `kubejs/server_scripts/policy/better_ore_geology_tag_policy.js` — Removes generated Excavated Variants item forms from generic ore tags so geological feedstocks keep their identity.
- `kubejs/server_scripts/policy/closed_end_dragon_replacements.js` — Replaces vanilla dragon-boss inputs with finite Ice and Fire families and authors the closed-End Elytra and netherite-sheet routes.
- `kubejs/server_scripts/policy/conventional_tool_authority.js` — Removes conventional native tool-family recipes so Tinkers' Construct owns pickaxe, axe, shovel, hoe, and sword progression.
- `kubejs/server_scripts/policy/dimension_access.js` — Removes direct portal and key recipes for retired or Font-only destinations and blocks vanilla End portal activation.
- `kubejs/server_scripts/policy/disable_jumbo_furnace.js` — Removes unsupported Jumbo Furnace processing and restores More Red red-alloy acquisition.
- `kubejs/server_scripts/policy/disable_sophisticatedbackpack_jukebox.js` — Removes recipes for disabled Sophisticated Backpacks jukebox upgrades.
- `kubejs/server_scripts/policy/global_loot_progression.js` — Removes progression-breaking materials from broad loot injection surfaces.
- `kubejs/server_scripts/policy/native_material_cost_prewarm.js` — Prewarms native TConstruct material-cost recipes before parallel KubeJS recipe conversion.
- `kubejs/server_scripts/policy/ore_raw_material_loot.js` — Redirects vanilla raw-metal loot across loot tables to authored geological feedstocks.
- `kubejs/server_scripts/policy/ore_raw_material_recipes.js` — Redirects legacy raw-metal recipe outputs to geological feedstocks while retaining ingredient costs.
- `kubejs/server_scripts/policy/removed_items.js` — Applies the exact deny list for intentionally unavailable outputs.
- `kubejs/server_scripts/policy/storage_barrel_recipe.js` — Closes a native barrel conversion that bypasses the authored plank recipe.
- `kubejs/server_scripts/policy/tnt_recipe.js` — Restricts TNT to the two vanilla sand inputs instead of broad sand tags.
- `kubejs/server_scripts/processing/ore_sifting_and_spouting.js` — Disables Bulk Washing, migrates granular separations to waterlogged Sifting, and assigns direct water treatments to Spouting.
- `kubejs/server_scripts/processing/terrain_recovery.js` — Registers exact capped Crushing and Sifting terrain-recovery recipes from the pack-owned profile catalogue.
- `kubejs/server_scripts/progression/00_primitive_workshop.js` — Establishes the pre-Font fiber, fired-goods, and simple-fittings recipe layer for local handling and homestead utilities.
- `kubejs/server_scripts/progression/10_hand_workshop.js` — Adds distinct underground-rock Refractory Compound, Seared Brick, Andesite Alloy casting and iron hand-crank entry; old Font grout routes are inert.
- `kubejs/server_scripts/progression/20_logistics_roots.js` — Inert historical placeholder; active Certus logistics recipes live in 20_meteor_logistics.js.
- `kubejs/server_scripts/progression/20_meteor_logistics.js` — Gates physical logistics and the AE2 Energy Acceptor with Certus, Sky Stone and later cross-system materials while retaining native Manufacturing parts.
- `kubejs/server_scripts/progression/20_powered_works.js` — Authors the Powered Works Machine Block roots and early kinetic machinery.
- `kubejs/server_scripts/progression/21_better_bumblezone_crops.js` — Removes recipes that would create origin-controlled cultivar propagules outside nursery and harvest acquisition.
- `kubejs/server_scripts/progression/21_rat_archeology.js` — Adds an obtainable archaeologist hat and a replacement Rats archaeology upgrade without Ratlantis travel tokens.
- `kubejs/server_scripts/progression/22_fish_families.js` — Applies exact fish-family and Diet nutrient tags from the fish nutrition catalogue.
- `kubejs/server_scripts/progression/22_underwater_works.js` — Routes marine workshop recipes through Conduit and underwater materials without replacing geological recovery.
- `kubejs/server_scripts/progression/30_precision_factory.js` — Authors the Precision Factory brass, deployment, and mechanical-crafting roots.
- `kubejs/server_scripts/progression/40_thermal_pressure.js` — Authors processed-geology Scorched Brick production, compressed-air entry, and airtight machinery while disabling native Nether grout.
- `kubejs/server_scripts/progression/45_acid_chemistry.js` — Authors canonical acid acquisition, renewable acetic uses, airtight gas containment, and removes native etching acid and coal-compression diamonds.
- `kubejs/server_scripts/progression/46_polymer_manufacturing.js` — Authors separate PLA and polypropylene process chains with explicit fluid and packet accounting.
- `kubejs/server_scripts/progression/50_electrical_control.js` — Authors Electrical Control generation, circuits, sensors, and advanced chemistry roots.
- `kubejs/server_scripts/progression/50_malum_soulstone_bootstrap.js` — Makes first raw Soulstone from Black Shale in Hexerei before the Spirit Altar, preserving native later processing.
- `kubejs/server_scripts/progression/60_aerospace.js` — Inert placeholder: Creating Space rocket recipes are retired and non-flight processing is owned elsewhere.
- `kubejs/server_scripts/progression/70_transition_components.js` — Authors reachable cross-mod components used by factory, electrical, aerospace, AE2, geology, and post-AE2 transitions.
- `kubejs/server_scripts/progression/80_tech_palette.js` — Bridges Nether and meteor components into early AE2 machinery without requiring an existing ME network.
- `kubejs/server_scripts/progression/81_pneumaticcraft_progression.js` — Places PneumaticCraft air machines, assembly and upgrades in the pressure and electrical progression.
- `kubejs/server_scripts/progression/82_food_drying.js` — Moves seven raw meats and fish to Hexerei drying racks for Heat Sync dried-food outputs.
- `kubejs/server_scripts/progression/82_pneumaticcraft_acquisition.js` — Replaces low-tier emerald-only PneumaticCraft service offers with authored pressure acquisition routes.
- `kubejs/server_scripts/progression/83_culinary_policy.js` — Guarded culinary migration planner; unqualified discovery policy leaves original recipes intact.
- `kubejs/server_scripts/progression/83_station_crafting.js` — Provides one-for-one crafting-table and TConstruct station exchanges while closing alternate station crafts.
- `kubejs/server_scripts/progression/84_cooking_recipes.js` — Guarded recipe-only Cooking migration that validates exact source recipes before applying approved replacements.
- `kubejs/server_scripts/progression/85_certus_quartz_components.js` — Substitutes meteor Certus in selected existing quartz component recipes while preserving provider shapes and costs.
- `kubejs/server_scripts/progression/85_dimension_font_ecology.js` — Adds renewable Font libations and dimension-native materials for later machine families.
- `kubejs/server_scripts/progression/86_dimension_transport.js` — Adds repeatable transport crafted from each first Font destination while retaining ordinary boats and rails.
- `kubejs/server_scripts/progression/food_eras.js` — Stages foraged and animal foods before renewable garden crops and expands cooking-pot combinations.
- `kubejs/server_scripts/progression/plant_drinks.js` — Removes superseded juice and drink recipes while native Better Drinking Water owns replacement recipes.
- `kubejs/server_scripts/transport/10_optional_engineering_roots.js` — Places Eureka, Clockwork, Trackwork, and Create train roots at their intended optional eras.
- `kubejs/server_scripts/transport/20_found_better_rail_beetles.js` — Adds early found Rail Beetle loot markers that yield fixed ordinary rail supplies.
- `kubejs/server_scripts/utility/10_recipe_surface_helpers.js` — Defines reusable Rhino-safe helpers for constructing and rewriting recipes.
- `kubejs/server_scripts/utility/21_starter_hook_loot.js` — Places ReHooked introductory grappling prototypes in loot; stronger tiers keep their later routes.
- `kubejs/server_scripts/utility/hooks_drones_gates.js` — Gates grappling hooks and autonomous drones as powerful route-editing utilities.
- `kubejs/server_scripts/zz_crafting_policy_report.js` — Reports exact surviving recipe outputs and live recipe consumers for centrally cut families.
- `kubejs/server_scripts/zz_recipe_clash_remediation.js` — Applies narrow exact-ID recipe corrections while preserving custom serializers and material costs.

## Startup scripts

- `kubejs/startup_scripts/99_world_axioms.js` — Registers physical lineage intermediate items; holding one does not activate an Axiom.
- `kubejs/startup_scripts/compat/retained/check__12_unearthed_regolith_hand_mining.js` — Adjusts Unearthed regolith blocks for the intended manual mining behavior.
- `kubejs/startup_scripts/compat/retained/remove__10_ae2_skystone_hardness.js` — Adjusts AE2 skystone block hardness and mining behavior.
- `kubejs/startup_scripts/policy/crafting_policy_contract.js` — Loads `bc.crafting_policy.v1` and blocks startup when a loaded namespace has no policy classification.
- `kubejs/startup_scripts/policy/disable_jumbo_furnace.js` — Removes the unsupported Jumbo Furnace item from the creative tab while its dependency remains loaded.
- `kubejs/startup_scripts/policy/disable_potion_brewing.js` — Clears vanilla and modded Brewing Stand registrations so potion delivery has no brewing path.
- `kubejs/startup_scripts/policy/disable_trickster_weight.js` — Removes the incompatible Trickster Weight effect from the startup registry surface.
- `kubejs/startup_scripts/policy/enderman_armor.js` — Sets Enderman base armor on joins without stacking repeated modifiers.
- `kubejs/startup_scripts/policy/hide_vanilla_tools_from_creative.js` — Removes replaced vanilla tools from creative tabs while leaving registry identities intact.
- `kubejs/startup_scripts/progression/10_machine_blocks.js` — Registers six structural Machine Block identities, including a retired but save-compatible Space Machine Block; recipes are elsewhere.
- `kubejs/startup_scripts/progression/20_transition_items.js` — Registers pack intermediates and molten Andesite Alloy; retained Font grout identities have no live metallurgy routes.
- `kubejs/startup_scripts/progression/22_fish_families.js` — Registers cooked fish-family item identities; native Diet computes nutrition.
