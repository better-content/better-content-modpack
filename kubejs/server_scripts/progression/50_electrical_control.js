ServerEvents.recipes(function (event) {
    event.remove({ id: 'powergrid:item_application/conductive_casing' })
    // Preserve the pack's copper casing + red-wire electrical gate. Deploying has
    // only two inputs, so Manufacturing consumes a physical blank in a second step
    // rather than replacing either original cost or fabricating a material rating.
    var conductiveBlank = Platform.isLoaded('better_manufacturing')
    event.custom({
        type: 'create:deploying',
        ingredients: [{ item: 'create:copper_casing' }, { item: 'morered:red_alloy_wire' }],
        results: [{ item: conductiveBlank
            ? 'better_manufacturing:conductive_casing_blank' : 'powergrid:conductive_casing' }]
    }).id(conductiveBlank ? 'kubejs:electrical_control/conductive_casing_blank'
        : 'kubejs:electrical_control/conductive_casing')
    if (conductiveBlank) {
        event.custom({
            type: 'create:deploying',
            ingredients: [{ item: 'better_manufacturing:conductive_casing_blank' },
                { item: 'tconstruct:large_plate' }],
            results: [{ item: 'powergrid:conductive_casing' }]
        }).id('kubejs:electrical_control/conductive_casing')
    }

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

    // PowerGrid owns stationary electrical generation. The housing is an open
    // rotor frame: pressure-built wiring/fittings prove its era, not a full cube.
    // Remove both orientations first so conversion cannot bypass manufacturing.
    event.remove({ output: 'pneumaticcraft:pneumatic_dynamo' })
    event.remove({ output: 'powergrid:generator_housing' })
    event.remove({ output: 'powergrid:vertical_generator_housing' })
    var generatorHousingInputs = [
        { type: 'pneumaticcraft:stacked_item', tag: 'forge:plates/iron', count: 4 },
        { type: 'pneumaticcraft:stacked_item', tag: 'forge:plates/copper', count: 2 },
        { type: 'pneumaticcraft:stacked_item', item: 'powergrid:conductive_casing', count: 2 },
        { type: 'pneumaticcraft:stacked_item', item: 'morered:red_alloy_wire', count: 2 },
        { type: 'pneumaticcraft:stacked_item', item: 'create:electron_tube', count: 1 }
    ]
    // This is the only new housing; reversible orientation swaps reuse its built frame.
    // Keep the no-Manufacturing recipe playable without requiring an unavailable TCon part.
    if (conductiveBlank) generatorHousingInputs.push({
        type: 'pneumaticcraft:stacked_item', item: 'tconstruct:large_plate', count: 1
    })
    event.custom({
        type: 'pneumaticcraft:pressure_chamber', pressure: 2.0,
        inputs: generatorHousingInputs,
        results: [{ item: 'powergrid:generator_housing' }]
    }).id('kubejs:electrical_control/direct_root/first_stationary_generator')
    event.shapeless('powergrid:vertical_generator_housing', [
        'powergrid:generator_housing'
    ]).id('kubejs:electrical_control/generator_vertical_conversion')
    event.shapeless('powergrid:generator_housing', [
        'powergrid:vertical_generator_housing'
    ]).id('kubejs:electrical_control/generator_horizontal_conversion')
    event.remove({ id: 'morered:soldering_table' })
    var solderingParts = Platform.isLoaded('better_manufacturing')
    var solderingKey = { P: 'morered:stone_plate', W: 'morered:red_alloy_wire',
        M: 'pneumaticcraft:printed_circuit_board', C: '#forge:plates/copper' }
    if (solderingParts) { solderingKey.H = 'tconstruct:tool_handle'; solderingKey.L = 'tconstruct:large_plate' }
    event.shaped('morered:soldering_table', solderingParts ? ['PPP', 'WMW', 'HCL'] : ['PPP', 'WMW', ' C '], solderingKey)
        .id('kubejs:electrical_control/direct_root/circuit_design_station')
})
