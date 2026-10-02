ServerEvents.recipes(function (event) {
    // One native casing consumes four aluminum plates, which makes the four
    // casings needed for the first aerospace machines disproportionately
    // expensive before the Moon. Preserve the ingredients as a batch craft.
    event.remove({ output: 'creatingspace:rocket_casing' })
    event.shaped('4x creatingspace:rocket_casing', [
        'ACA',
        'CAC',
        'ACA'
    ], {
        A: '#forge:ingots/cobalt',
        C: '#forge:plates/aluminum'
    }).id('kubejs:aerospace/rocket_casing_batch')

    event.custom({
        type: 'pneumaticcraft:pressure_chamber', pressure: 4.5,
        inputs: [
            { type: 'pneumaticcraft:stacked_item', item: 'kubejs:electrical_machine_block', count: 1 },
            { type: 'pneumaticcraft:stacked_item', item: 'creatingspace:rocket_casing', count: 2 },
            { type: 'pneumaticcraft:stacked_item', item: 'creatingspace:inconel_sheet', count: 2 },
            { type: 'pneumaticcraft:stacked_item', item: 'creatingspace:hastelloy_ingot', count: 2 },
            { type: 'pneumaticcraft:stacked_item', item: 'kubejs:titanium_thermal_plate', count: 2 }
        ],
        results: [{ item: 'kubejs:space_machine_block' }]
    }).id('kubejs:aerospace/space_machine_block')

    ;[
        ['creatingspace:rocket_engineer_table', ['ABA', 'SMS', 'ACA'], { A: 'creatingspace:rocket_casing', B: 'create:mechanical_crafter', M: 'kubejs:space_machine_block', S: 'creatingspace:inconel_sheet', C: 'create:precision_mechanism' }],
        ['creatingspace:mechanical_electrolyzer', ['TST', 'AMA', 'TPT'], { T: 'kubejs:titanium_thermal_plate', S: 'creatingspace:hastelloy_ingot', M: 'kubejs:space_machine_block', A: 'creatingspace:rocket_casing', P: 'pneumaticcraft:pressure_tube' }],
        ['creatingspace:air_liquefier', ['ISI', 'AMA', 'IPI'], { I: 'creatingspace:inconel_sheet', S: 'creatingspace:hastelloy_ingot', M: 'kubejs:space_machine_block', A: 'creatingspace:rocket_casing', P: 'pneumaticcraft:pressure_tube' }]
    ].forEach(function (root) {
        event.remove({ id: root[0] })
        event.shaped(root[0], root[1], root[2]).id('kubejs:aerospace/direct_root/' + root[0].substring('creatingspace:'.length))
    })
})
