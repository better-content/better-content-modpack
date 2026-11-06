// Each dimensional Font supplies a renewable libation and a visible native
// material for a later machine family. One bucket restores 48,000 charge.
ServerEvents.recipes(function (event) {
    event.shaped('better_dimension_fonts:font_pourer', ['OVO', 'GPG', 'OHO'], {
        O: 'minecraft:oxidized_cut_copper', V: 'create:fluid_valve',
        G: '#forge:glass', P: 'create:fluid_pipe', H: 'minecraft:pointed_dripstone'
    }).id('kubejs:fonts/font_pourer')

    ;[
        ['nether', 'minecraft:nether_wart', 'minecraft:blaze_powder', 'minecraft:magma_cream'],
        ['aether', 'aether:blue_berry', 'aether:swet_ball', 'aether:skyroot_leaves'],
        ['bumblezone', 'the_bumblezone:pollen_puff', 'minecraft:honeycomb', 'minecraft:honey_bottle']
    ].forEach(function (row) {
        event.custom({
            type: 'create:mixing',
            ingredients: [
                { item: row[1] }, { item: row[2] }, { item: row[3] },
                { fluid: 'minecraft:water', amount: 1000 }
            ],
            results: [{ fluid: 'better_dimension_fonts:' + row[0] + '_libation', amount: 1000 }],
            processingTime: 200
        }).id('kubejs:fonts/libation/' + row[0])
    })

    var partVariants = Platform.isLoaded('better_manufacturing')
    event.remove({ output: 'better_industrial_heat:boiler_heater' })
    if (partVariants) {
        event.custom({ type: 'better_manufacturing:mechanical_part_variant',
            pattern: ['SCS', 'FHF', 'MTB', 'P  '], acceptMirrored: false,
            key: { P: { item: 'tconstruct:large_plate' }, S: { tag: 'forge:plates/steel' },
                C: { tag: 'forge:plates/copper' }, F: { item: 'create:fluid_pipe' },
                H: { item: 'better_industrial_heat:heat_pipe' }, M: { item: 'minecraft:magma_cream' },
                T: { item: 'create:fluid_tank' }, B: { item: 'minecraft:blaze_rod' } },
            result: { item: 'better_industrial_heat:boiler_heater' },
            function: 'better_manufacturing:industrialheat/thermal', parts: [{ role: 'thermal_surface', slot: 9 }]
        }).id('kubejs:fonts/nether/boiler_heater')
    } else {
        event.shaped('better_industrial_heat:boiler_heater', ['SCS', 'FHF', 'MTB'], {
            S: '#forge:plates/steel', C: '#forge:plates/copper',
            F: 'create:fluid_pipe', H: 'better_industrial_heat:heat_pipe',
            M: 'minecraft:magma_cream', T: 'create:fluid_tank', B: 'minecraft:blaze_rod'
        }).id('kubejs:fonts/nether/boiler_heater')
    }

    // The sole thermopneumatic plant root pays Nether magma and consumes a
    // configured liner part; duplicate earlier roots would bypass this gate.
    event.remove({ output: 'pneumaticcraft:thermopneumatic_processing_plant' })
    if (partVariants) {
        event.custom({ type: 'better_manufacturing:mechanical_part_variant',
            pattern: ['SSS', 'TPT', 'SSS', 'ML '], acceptMirrored: false,
            key: { L: { item: 'tconstruct:large_plate' }, S: { item: 'pneumaticcraft:reinforced_stone_slab' },
                T: { type: 'better_manufacturing:pneu_empty_tank', item: 'pneumaticcraft:small_tank' },
                M: { item: 'minecraft:magma_cream' }, P: { item: 'pneumaticcraft:pressure_tube' } },
            result: { item: 'pneumaticcraft:thermopneumatic_processing_plant' },
            function: 'better_manufacturing:pneuprocess/chemistry', parts: [{ role: 'liner', slot: 10 }]
        }).id('kubejs:fonts/nether/thermopneumatic_plant')
    } else {
        event.shaped('pneumaticcraft:thermopneumatic_processing_plant', ['SSS', 'TMT', 'SPS'], {
            S: 'pneumaticcraft:reinforced_stone_slab', T: 'pneumaticcraft:small_tank',
            M: 'minecraft:magma_cream', P: 'pneumaticcraft:pressure_tube'
        }).id('kubejs:fonts/nether/thermopneumatic_plant')
    }

    event.remove({ output: 'create:mechanical_harvester' })
    // Crop visits are instantaneous: a consumed blade is construction, not
    // a fictional work-rate setting. Keep the original route without Manufacturing.
    var harvesterKey = { A: 'create:andesite_alloy', P: 'the_bumblezone:pollen_puff',
        I: '#forge:plates/iron', C: 'create:andesite_casing' }
    if (partVariants) harvesterKey.B = 'tconstruct:small_blade'
    event.shaped('create:mechanical_harvester', partVariants ? ['APA', 'AIA', 'BC '] : ['APA', 'AIA', ' C '], harvesterKey)
        .id('kubejs:fonts/bumblezone/mechanical_harvester')

    event.remove({ output: 'create:mechanical_plough' })
    if (partVariants) {
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['IWI', 'AAA', 'HC '],
            key: { I: { tag: 'forge:plates/iron' }, W: { item: 'the_bumblezone:carvable_wax' },
                A: { item: 'create:andesite_alloy' }, H: { item: 'tconstruct:small_axe_head' },
                C: { item: 'create:andesite_casing' } },
            result: { item: 'create:mechanical_plough' }, function: 'better_manufacturing:create/field_work',
            parts: [{ role: 'working_surface', slot: 6 }]
        }).id('kubejs:fonts/bumblezone/mechanical_plough')
    } else {
        event.shaped('create:mechanical_plough', ['IWI', 'AAA', ' C '], {
            I: '#forge:plates/iron', W: 'the_bumblezone:carvable_wax',
            A: 'create:andesite_alloy', C: 'create:andesite_casing'
        }).id('kubejs:fonts/bumblezone/mechanical_plough')
    }
})
