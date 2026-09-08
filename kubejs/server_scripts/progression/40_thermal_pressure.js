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
    event.shaped('compressedcreativity:rotational_compressor', ['PSP', 'GAG', 'CBC'], {
        P: 'create:propeller', S: 'create:shaft', G: 'pneumaticcraft:compressed_iron_gear',
        A: 'kubejs:airtight_machine_block', C: 'create:brass_casing', B: 'pneumaticcraft:pressure_tube'
    }).id('kubejs:thermal_pressure/direct_root/rotational_compressor')

    event.remove({ id: 'pneumaticcraft:pressure_chamber_interface' })
    event.shaped('2x pneumaticcraft:pressure_chamber_interface', ['HW', 'AW'], {
        H: 'minecraft:hopper', A: 'kubejs:airtight_machine_block', W: 'pneumaticcraft:pressure_chamber_wall'
    }).id('kubejs:thermal_pressure/direct_root/pressure_chamber_interface')

    event.remove({ output: 'tconstruct:nether_grout' })
    event.remove({ input: 'tconstruct:nether_grout' })
    event.remove({ id: 'tconstruct:smeltery/casting/scorched/brick_composite' })
    event.custom({
        type: 'create:mixing', heatRequirement: 'heated',
        ingredients: [
            { item: 'realistic_ores:rinsed_hotstone' },
            { item: 'realistic_ores:rinsed_black_shale' },
            { item: 'minecraft:gravel' }
        ],
        results: [{ item: 'tconstruct:scorched_brick', count: 2 }]
    }).id('kubejs:thermal_pressure/foundry/scorched_brick')
})
