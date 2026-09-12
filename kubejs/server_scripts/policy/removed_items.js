// Narrow deny policy. Each listed output is intentionally unavailable; unlike
// the former staging script this never scans arbitrary recipe JSON.
var BC_DISABLED_ITEMS = (JsonIO.read('kubejs/config/quarantined_items.json') || { items: [] }).items || []
var BC_CHEMLIB_HIDDEN = JsonIO.read('kubejs/config/chemlib_form_policy.json') || {}
BC_DISABLED_ITEMS = BC_DISABLED_ITEMS
    .concat(BC_CHEMLIB_HIDDEN.hidden_gas_buckets || [])
    .concat(BC_CHEMLIB_HIDDEN.hidden_forms || [])
    .concat(BC_CHEMLIB_HIDDEN.hidden_compounds || [])

ServerEvents.recipes(function (event) {
    ;[
        'burnt:gunpowder_recipe',
        'burnt:fire_barrel_recipe_2',
        'createdieselgenerators:bulk_fermenting/lava',
        'pneumaticcraft:amadron/emerald_to_oil',
        'ars_nouveau:water_essence_to_bucket',
        'ars_nouveau:water_essence_to_obsidian',
        'ars_nouveau:fire_essence_to_magma_block',
        'ars_nouveau:conjuration_essence_to_soul_sand',
        'ars_nouveau:conjuration_essence_to_end_stone',
        'bloodmagic:alchemytable/sigil_lava_bucket',
        'bloodmagic:alchemytable/sigil_water_bucket',
        'bloodmagic:alchemytable/sigil_water_bottle',
        'bloodmagic:alchemytable/reagent_frost_water_sigil',
        'bloodmagic:alchemytable/sulfur_from_sigil',
        'bloodmagic:alchemytable/advance_cutting_fluid_sigil',
        'bloodmagic:alchemytable/basic_cutting_fluid_sigil',
        'bloodmagic:alchemytable/clay_from_sand_sigil',
        'bloodmagic:alchemytable/intermediate_cutting_fluid_sigil',
        'bloodmagic:alchemytable/leather_from_flesh_sigil',
        'createdieselgenerators:distillation/crude_oil',
        'createdieselgenerators:distillation/superheated_crude_oil',
        // Exact final-graph closures for serializers whose outputs are not
        // matched by KubeJS's generic output filter.
        'ars_elemental:head_cut/behead_dragon',
        'ars_nouveau:drygmy_charm',
        'ars_nouveau:enchanters_sword',
        'ars_nouveau:relay_warp',
        'ars_nouveau:stable_warp_scroll',
        'ars_nouveau:thread_drygmy',
        'ars_nouveau:thread_whirlisprig',
        'ars_nouveau:warp_scroll_copy',
        'ars_nouveau:whirlisprig_charm',
        'arseng:source_acceptor',
        'arseng:source_cell_housing',
        'arseng:source_storage_cell_1k',
        'arseng:source_storage_cell_4k',
        'arseng:source_storage_cell_16k',
        'arseng:source_storage_cell_64k',
        'arseng:source_storage_cell_256k',
        'pneumaticcraft:pressure_chamber/pressure_chamber_disenchanting',
        'pneumaticcraft:pressure_chamber/pressure_chamber_enchanting',
        'tconstruct:tools/severing/ender_dragon_head',
        'tconstruct:smeltery/entity_melting/heads/ender_dragon'
    ].forEach(function (id) { event.remove({ id: id }) })

    // Potion delivery is a cut system. Plain water bottles remain available to
    // thirst and water-transfer recipes, so potion-item handling stays exact.
    ;[
        'ars_nouveau:potion_flask_amp_fill',
        'ars_nouveau:potion_flask_et_fill',
        'ars_nouveau:potion_flask_fill',
        'jeed:potion_accepors',
        'jeed:compat/tipped_spikes_potion_provider',
        'supplementaries:bamboo_spikes_tipped',
        'supplementaries:integration/tipped_spikes_washing',
        'supplementaries:jeed/tipped_spikes_potion_provider',
        'supplementaries:soap/tipped_arrow',
        'tconstruct:smeltery/casting/filling/lingering_bottle',
        'tconstruct:smeltery/casting/filling/splash_bottle',
        'tconstruct:smeltery/casting/filling/tipped_arrow',
        'tconstruct:smeltery/casting/filling/tipped_arrow_clean'
    ].forEach(function (id) { event.remove({ id: id }) })
    ;[
        'minecraft:crafting_special_tippedarrow',
        'pneumaticcraft:gun_ammo_potion_crafting',
        'tconstruct:casting_table_tipped_clearing',
        'tconstruct:casting_table_tipping',
        'tconstruct:tipped_tool_transform',
        'brewinandchewin:create_potion_pouring'
    ].forEach(function (type) { event.remove({ type: type }) })
    event.remove({ output: 'minecraft:splash_potion' })
    event.remove({ output: 'minecraft:lingering_potion' })
    event.remove({ output: 'minecraft:tipped_arrow' })

    // Preserve unrelated consumers by substituting the native reagent that
    // represented the removed potion effect. All other costs stay unchanged.
    ;[
        {
            id: 'goety:focus/soul_heal_focus',
            json: {
                type: 'goety:ritual', ritual_type: 'goety:craft',
                activation_item: { item: 'goety:empty_focus' }, craftType: 'magic', soulCost: 1, duration: 10,
                ingredients: [
                    { tag: 'forge:storage_blocks/lapis' }, { tag: 'forge:storage_blocks/lapis' },
                    { tag: 'forge:storage_blocks/lapis' }, { item: 'goety:cursed_ingot' },
                    { item: 'goety:cursed_ingot' }, { item: 'minecraft:glistering_melon_slice' }
                ],
                result: { item: 'goety:soul_heal_focus' }
            }
        },
        {
            id: 'goety:focus/weakening_focus',
            json: {
                type: 'goety:ritual', ritual_type: 'goety:craft',
                activation_item: { item: 'goety:empty_focus' }, craftType: 'magic', soulCost: 1, duration: 10,
                ingredients: [
                    { item: 'goety:mystic_core' }, { item: 'goety:wind_blast_focus' },
                    { item: 'goety:cursed_ingot' }, { item: 'goety:cursed_ingot' },
                    { item: 'minecraft:fermented_spider_eye' }
                ],
                result: { item: 'goety:weakening_focus' }
            }
        },
        {
            id: 'goety:wind_robe',
            json: {
                type: 'goety:ritual', ritual_type: 'goety:craft',
                activation_item: { item: 'goety:wind_core' }, craftType: 'sky', soulCost: 1, duration: 10,
                ingredients: [
                    { item: 'goety:gale_fabric' }, { item: 'goety:gale_fabric' },
                    { item: 'goety:gale_fabric' }, { item: 'goety:magic_fabric' },
                    { item: 'goety:magic_fabric' }, { item: 'minecraft:orange_dye' },
                    { item: 'minecraft:phantom_membrane' }
                ],
                result: { item: 'goety:wind_robe' }
            }
        },
        {
            id: 'pneumaticcraft:jumping_upgrade_3',
            json: {
                type: 'minecraft:crafting_shaped', category: 'misc',
                key: {
                    C: { item: 'pneumaticcraft:pneumatic_cylinder' },
                    J: { item: 'minecraft:rabbit_foot' }, P: { item: 'minecraft:piston' },
                    U: { item: 'pneumaticcraft:jumping_upgrade_2' }
                },
                pattern: ['PCP', 'JUJ', ' J '],
                result: { item: 'pneumaticcraft:jumping_upgrade_3' }, show_notification: true
            }
        },
        {
            id: 'pneumaticcraft:jumping_upgrade_4',
            json: {
                type: 'minecraft:crafting_shaped', category: 'misc',
                key: {
                    C: { item: 'pneumaticcraft:pneumatic_cylinder' },
                    J: { item: 'minecraft:glowstone_dust' }, P: { item: 'minecraft:piston' },
                    U: { item: 'pneumaticcraft:jumping_upgrade_3' }
                },
                pattern: ['PCP', 'JUJ', ' J '],
                result: { item: 'pneumaticcraft:jumping_upgrade_4' }, show_notification: true
            }
        },
        {
            id: 'pneumaticcraft:night_vision_upgrade',
            json: {
                type: 'minecraft:crafting_shaped', category: 'misc',
                key: {
                    G: { item: 'pneumaticcraft:pressure_chamber_glass' },
                    L: { tag: 'pneumaticcraft:upgrade_components' },
                    N: { item: 'minecraft:golden_carrot' }
                },
                pattern: ['LNL', 'GNG', 'LNL'],
                result: { item: 'pneumaticcraft:night_vision_upgrade' }, show_notification: true
            }
        },
        {
            id: 'twilightforest:equipment/zombie_scepter',
            json: {
                type: 'minecraft:crafting_shapeless', category: 'equipment',
                ingredients: [
                    { item: 'minecraft:blaze_powder' },
                    { type: 'forge:partial_nbt', item: 'twilightforest:zombie_scepter', nbt: '{Damage:9}' },
                    { item: 'minecraft:rotten_flesh' }
                ],
                result: { item: 'twilightforest:zombie_scepter' }
            }
        },
        {
            id: 'fallout_wastelands_:abraxocleanertoacid',
            json: {
                type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['a', 'b', 'c'],
                key: {
                    a: { item: 'fallout_wastelands_:abraxo_cleaner' },
                    b: { type: 'forge:nbt', item: 'minecraft:potion', count: 1, nbt: '{Potion:"minecraft:water"}' },
                    c: { item: 'fallout_wastelands_:cloth' }
                },
                result: { item: 'fallout_wastelands_:acid', count: 2 }
            }
        },
        {
            id: 'fallout_wastelands_:fertilizercraft',
            json: {
                type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['ab', 'c ', 'd '],
                key: {
                    a: { item: 'fallout_wastelands_:tato' }, b: { item: 'minecraft:bone_meal' },
                    c: { type: 'forge:nbt', item: 'minecraft:potion', count: 1, nbt: '{Potion:"minecraft:water"}' },
                    d: { item: 'fallout_wastelands_:dented_can' }
                },
                result: { item: 'fallout_wastelands_:fertilizer', count: 2 }
            }
        }
    ].forEach(function (replacement) {
        event.remove({ id: replacement.id })
        event.custom(replacement.json).id(replacement.id)
    })
    event.remove({ type: 'occultism:miner' })
    event.remove({ type: 'bloodmagic:dimension_drink' })
    event.replaceInput({ id: 'bloodmagic:alchemytable/reagent_suppression' },
        'bloodmagic:teleposer', 'minecraft:sponge')
    BC_DISABLED_ITEMS.forEach(function (item) { event.remove({ output: item }) })
})

// Remove every registered offer that produces a disabled potion-delivery item.
// Regular potion filters are NBT-specific so plain water offers remain legal.
var BC_TRADE_REGISTRIES = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
var BC_TRADE_POTIONS = Java.loadClass('net.minecraft.world.item.alchemy.Potions')

function bcRemovePotionTrades(event) {
    ;['minecraft:splash_potion', 'minecraft:lingering_potion', 'minecraft:tipped_arrow']
        .forEach(function (output) {
            event.removeTrades(MoreJS.ofTradeFilter({ firstItem: '*', secondItem: '*', outputItem: output }))
        })

    var iterator = BC_TRADE_REGISTRIES.POTION.iterator()
    while (iterator.hasNext()) {
        var potion = iterator.next()
        if (potion.equals(BC_TRADE_POTIONS.WATER)) continue
        var potionId = '' + BC_TRADE_REGISTRIES.POTION.getKey(potion)
        event.removeTrades(MoreJS.ofTradeFilter({
            firstItem: '*',
            secondItem: '*',
            outputItem: {
                type: 'forge:nbt', item: 'minecraft:potion', count: 1,
                nbt: '{Potion:"' + potionId + '"}'
            }
        }))
    }
}

MoreJSEvents.villagerTrades(function (event) { bcRemovePotionTrades(event) })
MoreJSEvents.wandererTrades(function (event) { bcRemovePotionTrades(event) })
