ServerEvents.recipes(function (event) {
    event.remove({ output: 'kubejs:pressure_seal' })
    event.shaped('2x kubejs:pressure_seal', ['KKK', 'KIK', 'KKK'], {
        K: 'minecraft:dried_kelp', I: '#forge:plates/iron'
    }).id('kubejs:thermal_pressure/pressure_seal')

    event.custom({
        type: 'create:mechanical_crafting',
        pattern: ['ITI', 'SBS', 'ITI'],
        key: {
            I: { item: 'pneumaticcraft:ingot_iron_compressed' },
            T: { item: 'pneumaticcraft:pressure_tube' },
            S: { item: 'kubejs:pressure_seal' },
            B: { item: 'kubejs:brass_machine_block' }
        },
        result: { item: 'kubejs:airtight_machine_block', count: 2 },
        acceptMirrored: false
    }).id('kubejs:thermal_pressure/airtight_machine_block')

    event.remove({ output: 'compressedcreativity:rotational_compressor' })
    // The compressor's core is 12x12x9 inside an open frame. Its era is
    // demonstrated by mechanical assembly of sealed parts, not a full cube.
    event.custom({
        type: 'create:mechanical_crafting', acceptMirrored: false,
        pattern: ['XSP', 'GAG', 'CBC'],
        key: {
            P: { item: 'create:propeller' }, S: { item: 'create:shaft' },
            G: { item: 'pneumaticcraft:compressed_iron_gear' }, A: { item: 'kubejs:pressure_seal' },
            C: { item: 'create:brass_casing' }, B: { item: 'pneumaticcraft:pressure_tube' },
            X: { item: 'aether:aerogel' }
        },
        result: { item: 'compressedcreativity:rotational_compressor' }
    }).id('kubejs:thermal_pressure/direct_root/rotational_compressor')

    // An interface is an open airlock frame, not a solid structural block.
    event.remove({ output: 'pneumaticcraft:pressure_chamber_interface' })
    event.custom({
        type: 'create:mechanical_crafting', acceptMirrored: false,
        pattern: ['HW', 'SW'],
        key: {
            H: { item: 'minecraft:hopper' }, S: { item: 'kubejs:pressure_seal' },
            W: { item: 'pneumaticcraft:pressure_chamber_wall' }
        },
        result: { item: 'pneumaticcraft:pressure_chamber_interface' }
    }).id('kubejs:thermal_pressure/direct_root/pressure_chamber_interface')

    event.remove({ output: 'tconstruct:nether_grout' })
    event.remove({ input: 'tconstruct:nether_grout' })
    event.remove({ id: 'tconstruct:smeltery/casting/scorched/brick_composite' })
    event.custom({
        type: 'create:mixing', heatRequirement: 'heated',
        ingredients: [
            { item: 'better_ore_geology:rinsed_hotstone' },
            { item: 'better_ore_geology:rinsed_black_shale' },
            { item: 'minecraft:basalt' }
        ],
        results: [{ item: 'tconstruct:scorched_brick', count: 2 }]
    }).id('kubejs:thermal_pressure/foundry/scorched_brick')
})
