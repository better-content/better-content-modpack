ServerEvents.recipes(function (event) {
    event.remove({ id: 'powergrid:item_application/conductive_casing' })
    event.custom({
        type: 'create:deploying',
        ingredients: [{ item: 'create:copper_casing' }, { item: 'morered:red_alloy_wire' }],
        results: [{ item: 'powergrid:conductive_casing' }]
    }).id('kubejs:electrical_control/conductive_casing')

    event.custom({
        type: 'pneumaticcraft:pressure_chamber', pressure: 2.0,
        inputs: [
            { type: 'pneumaticcraft:stacked_item', item: 'kubejs:airtight_machine_block', count: 1 },
            { type: 'pneumaticcraft:stacked_item', item: 'powergrid:conductive_casing', count: 1 },
            { type: 'pneumaticcraft:stacked_item', tag: 'forge:plates/copper', count: 4 },
            { type: 'pneumaticcraft:stacked_item', item: 'morered:red_alloy_wire', count: 2 },
            { type: 'pneumaticcraft:stacked_item', item: 'create:electron_tube', count: 1 }
        ],
        results: [{ item: 'kubejs:electrical_machine_block', count: 2 }]
    }).id('kubejs:electrical_control/electrical_machine_block')

    // PowerGrid owns stationary electrical generation. Both orientations are
    // removed first so conversion cannot bypass the Electrical Machine Block.
    event.remove({ output: 'pneumaticcraft:pneumatic_dynamo' })
    event.remove({ output: 'powergrid:generator_housing' })
    event.remove({ output: 'powergrid:vertical_generator_housing' })
    event.shaped('powergrid:generator_housing', ['IPI', 'CMC', 'IPI'], {
        I: '#forge:plates/iron', P: '#forge:plates/copper',
        C: 'powergrid:conductive_casing', M: 'kubejs:electrical_machine_block'
    }).id('kubejs:electrical_control/direct_root/first_stationary_generator')
    event.shapeless('powergrid:vertical_generator_housing', [
        'powergrid:generator_housing'
    ]).id('kubejs:electrical_control/generator_vertical_conversion')
    event.shapeless('powergrid:generator_housing', [
        'powergrid:vertical_generator_housing'
    ]).id('kubejs:electrical_control/generator_horizontal_conversion')
    event.remove({ id: 'morered:soldering_table' })
    event.shaped('morered:soldering_table', ['PPP', 'WMW', ' C '], {
        P: 'morered:stone_plate', W: 'morered:red_alloy_wire',
        M: 'kubejs:electrical_machine_block', C: '#forge:plates/copper'
    }).id('kubejs:electrical_control/direct_root/circuit_design_station')
})
