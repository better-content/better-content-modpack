ServerEvents.recipes(function (event) {
    event.remove({ type: 'create:mixing', output: 'create:brass_ingot' })
    event.remove({ output: 'create:brass_casing' })
    event.custom({
        type: 'create:compacting',
        ingredients: [{ tag: 'forge:stripped_logs' }, { tag: 'forge:plates/brass' }],
        results: [{ item: 'create:brass_casing' }]
    }).id('kubejs:precision_factory/brass_casing')
    event.custom({
        type: 'create:compacting',
        ingredients: [
            { item: 'kubejs:copper_machine_block' }, { item: 'create:brass_casing' },
            { tag: 'forge:plates/brass' }, { tag: 'forge:plates/brass' },
            { tag: 'forge:plates/brass' }, { tag: 'forge:plates/brass' },
            { item: 'create:electron_tube' }, { item: 'create:electron_tube' },
            { item: 'create:polished_rose_quartz' }
        ],
        results: [{ item: 'kubejs:brass_machine_block', count: 4 }]
    }).id('kubejs:precision_factory/brass_machine_block')

    event.remove({ id: 'create:crafting/kinetics/deployer' })
    // Construction-only neighbors: retain the era roots and native behavior, with no material bonuses.
    var partConstruction = Platform.isLoaded('better_manufacturing')
    var deployerParts = { E: 'create:electron_tube', B: 'kubejs:brass_machine_block', H: 'create:brass_hand' }
    if (partConstruction) deployerParts.P = 'tconstruct:tool_handle'
    event.shaped('create:deployer', partConstruction ? [' E ', ' BP', ' H '] : [' E ', ' B ', ' H '], deployerParts)
        .id('kubejs:precision_factory/direct_root/deployer')
    event.remove({ id: 'create:crafting/kinetics/mechanical_crafter' })
    var crafterParts = { E: 'create:electron_tube', B: 'kubejs:brass_machine_block', A: 'create:andesite_alloy' }
    if (partConstruction) crafterParts.P = 'tconstruct:tool_handle'
    event.shaped('3x create:mechanical_crafter', partConstruction ? ['PE ', 'EBE', ' A '] : [' E ', 'EBE', ' A '], crafterParts)
        .id('kubejs:precision_factory/direct_root/mechanical_crafter')
    event.remove({ id: 'quark:automation/crafting/crafter' })
    var quarkCrafterKey = { I: '#forge:ingots/iron', C: 'minecraft:crafting_table',
        R: '#forge:dusts/redstone', M: 'create:mechanical_crafter' }
    if (partConstruction) { quarkCrafterKey.H = 'tconstruct:tool_handle'; quarkCrafterKey.L = 'tconstruct:large_plate' }
    event.shaped('quark:crafter', partConstruction ? ['HLI', 'ICI', 'RMR'] : ['III', 'ICI', 'RMR'], quarkCrafterKey)
        .id('kubejs:precision_factory/quark_crafter')
    event.remove({ id: 'create:crafting/kinetics/track_station' })
    var stationKey = { R: 'create:railway_casing', B: 'kubejs:brass_machine_block', S: 'create:sturdy_sheet' }
    if (partConstruction) stationKey.H = 'tconstruct:tool_handle'
    event.shaped('create:track_station', partConstruction ? [' R ', 'HB ', ' S '] : [' R ', ' B ', ' S '], stationKey)
        .id('kubejs:precision_factory/transport_root/track_station')

    event.remove({ output: 'create:steam_engine' })
    if (partConstruction) {
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['TG ', 'ABA', ' C '],
            key: { T: { item: 'tconstruct:tough_handle' }, G: { tag: 'forge:plates/gold' },
                A: { item: 'create:andesite_alloy' }, B: { item: 'kubejs:brass_machine_block' },
                C: { tag: 'forge:storage_blocks/copper' } },
            result: { item: 'create:steam_engine' }, function: 'better_manufacturing:createbulk/steam_engine',
            parts: [{ role: 'piston_rod', slot: 0 }]
        }).id('kubejs:precision_factory/direct_root/steam_engine')
    } else {
        event.shaped('create:steam_engine', [' G ', 'ABA', ' C '], {
            G: '#forge:plates/gold', A: 'create:andesite_alloy',
            B: 'kubejs:brass_machine_block', C: '#forge:storage_blocks/copper'
        }).id('kubejs:precision_factory/direct_root/steam_engine')
    }
})
