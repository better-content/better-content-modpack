# KubeJS Script Summaries

This index is generated from the three executable KubeJS roots. Every listed JavaScript file is active; directory names such as `retained` and `reviewed` describe review history, not load state. `kubejs/inactive_review` is deliberately excluded because it is outside the executable roots.

Current inventory: **75 active scripts** — 6 client, 60 server, and 9 startup.

## Client scripts

- `kubejs/client_scripts/compat/retained/check__46_hide_hexerei_mahogany_tree_products.js` — Hides Hexerei’s superseded mahogany tree products from recipe viewers.
- `kubejs/client_scripts/compat/retained/check__48_hide_replaced_fishing_rods.js` — Hides native fishing rods superseded by the pack’s Starcatcher fishing route.
- `kubejs/client_scripts/compat/retained/remove__40_hide_quarantined_systems.js` — Hides quarantined chemistry, transport, test, legacy, and unsupported items from recipe viewers.
- `kubejs/client_scripts/guidance/10_hover_annotations.js` — Adds the pack's item-local hover annotations through the normal tooltip pipeline.
- `kubejs/client_scripts/policy/hide_excavated_variants.js` — Hides Excavated Variants’ host-stone permutations from recipe viewers without removing their recipes or world content.
- `kubejs/client_scripts/policy/hide_vanilla_tools.js` — Hides vanilla-style tools whose reachable native-system replacements own progression.

## Server scripts

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
- `kubejs/server_scripts/compat/retained/refactor__balance__171_k_turrets_electrical_gates.js` — Reauthors K-Turrets as advanced electrical-era autonomous defenses.
- `kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js` — Gates TaCZ workbenches and weapon packs behind appropriate manufacturing milestones.
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
- `kubejs/server_scripts/compat/retained/remove__40_blood_orbs_from_still_beating_hearts.js` — Uses a Still-Beating Heart to gate the Weak Blood Orb while preserving native later-orb recipes.
- `kubejs/server_scripts/compat/retained/remove__70_food_potion_reagents.js` — Turns foods into processed potion reagents before final brewing.
- `kubejs/server_scripts/compat/reviewed/chemistry_fluid_compat.js` — Unifies the exact shared chemistry fluids used by installed processing mods.
- `kubejs/server_scripts/compat/reviewed/furnace_policy.js` — Applies the reviewed furnace recipe and fuel policy.
- `kubejs/server_scripts/compat/reviewed/ingot_rewrites.js` — Rewrites exact ingot recipes to the pack's canonical material forms.
- `kubejs/server_scripts/compat/reviewed/kettle_rune_swap.js` — Replaces kettle bucket requirements with Blood Magic altar-capacity runes.
- `kubejs/server_scripts/compat/reviewed/replaceable_deepslate.js` — Maintains the exact replaceable-deepslate compatibility tags used by worldgen.
- `kubejs/server_scripts/policy/dimension_access.js` — Removes direct portal routes so rocket and Font graphs own intended dimension access.
- `kubejs/server_scripts/policy/closed_end_dragon_replacements.js` — Replaces vanilla dragon-boss inputs with finite Ice and Fire families and authors the closed-End Elytra and netherite-sheet routes.
- `kubejs/server_scripts/policy/conventional_tool_authority.js` — Removes conventional native tool-family recipes so Tinkers' Construct owns pickaxe, axe, shovel, hoe, and sword progression.
- `kubejs/server_scripts/policy/global_loot_progression.js` — Removes progression-breaking materials from broad loot injection surfaces.
- `kubejs/server_scripts/policy/removed_items.js` — Applies the exact deny list for intentionally unavailable outputs.
- `kubejs/server_scripts/policy/tnt_recipe.js` — Restricts TNT to the two vanilla sand inputs instead of broad sand tags.
- `kubejs/server_scripts/processing/ore_sifting_and_spouting.js` — Disables Bulk Washing, migrates granular separations to waterlogged Sifting, and assigns direct water treatments to Spouting.
- `kubejs/server_scripts/progression/00_primitive_workshop.js` — Establishes the pre-Font fiber, fired-goods, and simple-fittings recipe layer for local handling and homestead utilities.
- `kubejs/server_scripts/progression/10_hand_workshop.js` — Authors four named Font grouts, automated and Melter-scale Andesite Alloy casting, and hand-cranked Create entry.
- `kubejs/server_scripts/progression/20_ratlantis_logistics.js` — Removes direct Ratlantis access and gives Pretty Pipes, Sophisticated automation, Create requests, Rats, AE2, and Little Logistics visible Ratlantis-rooted recipes.
- `kubejs/server_scripts/progression/21_bumblezone_cultivars.js` — Removes recipes that would create origin-controlled cultivar propagules outside nursery and harvest acquisition.
- `kubejs/server_scripts/progression/20_powered_works.js` — Authors the Powered Works Machine Block roots and early kinetic machinery.
- `kubejs/server_scripts/progression/30_precision_factory.js` — Authors the Precision Factory brass, deployment, and mechanical-crafting roots.
- `kubejs/server_scripts/progression/40_thermal_pressure.js` — Authors processed-geology Scorched Brick production, compressed-air entry, and airtight machinery while disabling native Nether grout.
- `kubejs/server_scripts/progression/45_acid_chemistry.js` — Authors bounded PneumaticCraft acid chemistry while removing the coal-compression diamond bypass.
- `kubejs/server_scripts/progression/50_electrical_control.js` — Authors Electrical Control generation, circuits, sensors, and advanced chemistry roots.
- `kubejs/server_scripts/progression/60_aerospace.js` — Authors Aerospace engineering, atmosphere-processing, and rocket roots.
- `kubejs/server_scripts/progression/70_transition_components.js` — Authors reachable cross-mod components used by factory, electrical, aerospace, AE2, geology, and post-AE2 transitions.
- `kubejs/server_scripts/transport/10_optional_engineering_roots.js` — Places Eureka, Clockwork, Trackwork, and Create train roots at their intended optional eras.
- `kubejs/server_scripts/utility/10_recipe_surface_helpers.js` — Defines reusable Rhino-safe helpers for constructing and rewriting recipes.
- `kubejs/server_scripts/utility/hooks_drones_gates.js` — Gates grappling hooks and autonomous drones as powerful route-editing utilities.
- `kubejs/server_scripts/zz_crafting_policy_report.js` — Reports exact surviving recipe outputs and live recipe consumers for centrally cut families.

## Startup scripts

- `kubejs/startup_scripts/compat/retained/check__12_unearthed_regolith_hand_mining.js` — Adjusts Unearthed regolith blocks for the intended manual mining behavior.
- `kubejs/startup_scripts/compat/retained/remove__10_ae2_skystone_hardness.js` — Adjusts AE2 skystone block hardness and mining behavior.
- `kubejs/startup_scripts/compat/retained/remove__40_potion_brewing_registry.js` — Replaces vanilla reagent discovery with brewing recipes based on food-derived extracts.
- `kubejs/startup_scripts/policy/disable_trickster_weight.js` — Removes the incompatible Trickster Weight effect from the startup registry surface.
- `kubejs/startup_scripts/policy/hide_vanilla_tools_from_creative.js` — Removes replaced vanilla tools from creative tabs while leaving registry identities intact.
- `kubejs/startup_scripts/policy/crafting_policy_contract.js` — Loads `bc.crafting_policy.v1` and blocks startup when a loaded namespace has no policy classification.
- `kubejs/startup_scripts/progression/10_machine_blocks.js` — Registers the six clean-break Machine Blocks used as one-time era proofs.
- `kubejs/startup_scripts/progression/20_transition_items.js` — Registers the four Font grouts and the remaining pack-owned cross-system intermediates.
