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
        ['bumblezone', 'the_bumblezone:pollen_puff', 'minecraft:honeycomb', 'minecraft:honey_bottle'],
        ['ratlantis', 'rats:pirat_leaves', 'rats:marbled_cheese_raw', 'minecraft:redstone']
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

    event.remove({ output: 'better_industrial_heat:boiler_heater' })
    event.shaped('better_industrial_heat:boiler_heater', ['SCS', 'FHF', 'MTB'], {
        S: '#forge:plates/steel', C: '#forge:plates/copper',
        F: 'create:fluid_pipe', H: 'better_industrial_heat:heat_pipe',
        M: 'minecraft:magma_cream', T: 'create:fluid_tank', B: 'minecraft:blaze_rod'
    }).id('kubejs:fonts/nether/boiler_heater')

    event.remove({ output: 'pneumaticcraft:thermopneumatic_processing_plant' })
    event.shaped('pneumaticcraft:thermopneumatic_processing_plant', ['SSS', 'TMT', 'SPS'], {
        S: 'pneumaticcraft:reinforced_stone_slab', T: 'pneumaticcraft:small_tank',
        M: 'minecraft:magma_cream', P: 'pneumaticcraft:pressure_tube'
    }).id('kubejs:fonts/nether/thermopneumatic_plant')

    event.replaceInput({ output: 'pneumaticcraft:jet_boots_upgrade_5' },
        'minecraft:end_rod', 'aether:swet_ball')

    event.remove({ output: 'create:mechanical_harvester' })
    event.shaped('create:mechanical_harvester', ['APA', 'AIA', ' C '], {
        A: 'create:andesite_alloy', P: 'the_bumblezone:pollen_puff',
        I: '#forge:plates/iron', C: 'create:andesite_casing'
    }).id('kubejs:fonts/bumblezone/mechanical_harvester')

    event.remove({ output: 'create:mechanical_plough' })
    event.shaped('create:mechanical_plough', ['IWI', 'AAA', ' C '], {
        I: '#forge:plates/iron', W: 'the_bumblezone:carvable_wax',
        A: 'create:andesite_alloy', C: 'create:andesite_casing'
    }).id('kubejs:fonts/bumblezone/mechanical_plough')
})
