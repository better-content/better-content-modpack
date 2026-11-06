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
    if (Platform.isLoaded('better_manufacturing')) {
        // Restore the second native gear and brass casing without erasing the
        // aerogel, shaft, pressure tube, seal, propeller or materialized parts.
        event.shapeless(Item.of('aether:aerogel',
            '{better_manufacturing:{rotational_compressor_bill:1}}'),
            ['aether:aerogel', 'pneumaticcraft:compressed_iron_gear', 'create:brass_casing'])
            .id('kubejs:thermal_pressure/rotational_compressor_bill')
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['XSP', 'GAL', 'CHB'],
            key: { X: { type: 'forge:partial_nbt', item: 'aether:aerogel',
                nbt: '{better_manufacturing:{rotational_compressor_bill:1}}' }, S: { item: 'create:shaft' },
                P: { item: 'create:propeller' }, G: { item: 'pneumaticcraft:compressed_iron_gear' },
                A: { item: 'kubejs:pressure_seal' }, C: { item: 'create:brass_casing' },
                B: { item: 'pneumaticcraft:pressure_tube' }, L: { item: 'tconstruct:large_plate' },
                H: { item: 'tconstruct:tough_handle' } },
            result: { item: 'compressedcreativity:rotational_compressor' },
            function: 'better_manufacturing:compressedcreativity/conversion',
            parts: [{ role: 'plate', slot: 5 }, { role: 'handle', slot: 7 }]
        }).id('kubejs:thermal_pressure/direct_root/rotational_compressor')
    } else {
        event.shaped('compressedcreativity:rotational_compressor', ['XSP', 'GAG', 'CBC'], {
            P: 'create:propeller', S: 'create:shaft', G: 'pneumaticcraft:compressed_iron_gear',
            A: 'kubejs:pressure_seal', C: 'create:brass_casing', B: 'pneumaticcraft:pressure_tube',
            X: 'aether:aerogel'
        }).id('kubejs:thermal_pressure/direct_root/rotational_compressor')
    }

    event.remove({ id: 'pneumaticcraft:pressure_chamber_interface' })
    if (Platform.isLoaded('better_manufacturing')) {
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['HWX', 'AW '],
            key: { H: { item: 'minecraft:hopper' }, W: { item: 'pneumaticcraft:pressure_chamber_wall' },
                A: { item: 'kubejs:pressure_seal' }, X: { item: 'tconstruct:large_plate' } },
            result: { item: 'pneumaticcraft:pressure_chamber_interface', count: 2 },
            function: 'better_manufacturing:pneupressure/pressure', parts: [{ role: 'body', slot: 2 }]
        }).id('kubejs:thermal_pressure/direct_root/pressure_chamber_interface')
    } else {
        event.shaped('2x pneumaticcraft:pressure_chamber_interface', ['HW', 'AW'], {
            H: 'minecraft:hopper', A: 'kubejs:pressure_seal', W: 'pneumaticcraft:pressure_chamber_wall'
        }).id('kubejs:thermal_pressure/direct_root/pressure_chamber_interface')
    }

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
