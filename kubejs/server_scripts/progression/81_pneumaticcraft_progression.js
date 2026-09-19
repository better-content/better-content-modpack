// TECH-03: PneumaticCraft owns air, pressure, heat chemistry and its machine
// upgrades. PowerGrid keeps stationary electrical generation and the acid
// chemistry script keeps nitric-acid PCB etching intact.
ServerEvents.recipes(function (event) {
    event.remove({ output: 'pneumaticcraft:air_compressor' })
    event.shaped('pneumaticcraft:air_compressor', ['IPI', 'CMC', 'IRI'], {
        I: '#forge:plates/iron', P: 'pneumaticcraft:pressure_tube', C: 'pneumaticcraft:ingot_iron_compressed',
        // Basic air starts the pressure era. Airtight Machine Blocks belong
        // to the later thermopneumatic and electrical roots.
        M: 'kubejs:pressure_seal', R: 'create:cogwheel'
    }).id('kubejs:tech/pneumatic/air_compressor_pressure_root')

    event.remove({ output: 'pneumaticcraft:thermopneumatic_processing_plant' })
    event.shaped('pneumaticcraft:thermopneumatic_processing_plant', ['TAT', 'CMC', 'TAT'], {
        T: 'pneumaticcraft:pressure_tube', A: 'pneumaticcraft:advanced_pressure_tube',
        C: 'pneumaticcraft:compressed_iron_gear', M: 'kubejs:airtight_machine_block'
    }).id('kubejs:tech/pneumatic/thermopneumatic_processing')

    event.remove({ output: 'pneumaticcraft:assembly_controller' })
    global.bcPncrPressure(event, 'kubejs:tech/pneumatic/assembly_controller',
        'pneumaticcraft:assembly_controller', 1, 5.0, [
            'pneumaticcraft:advanced_pressure_tube', 'pneumaticcraft:printed_circuit_board',
            'powergrid:integrated_circuit', 'kubejs:electrical_machine_block'
        ])

    // Plastic remains PneumaticCraft's native petroleum and heat product. The
    // assembly and PCB gates below consume it once that processing route exists.

    event.remove({ output: 'pneumaticcraft:drone' })
    global.bcPncrPressure(event, 'kubejs:tech/pneumatic/drone',
        'pneumaticcraft:drone', 1, 5.0, [
            'pneumaticcraft:printed_circuit_board', 'pneumaticcraft:plastic',
            'powergrid:electric_motor', 'create:precision_mechanism'
        ])

    event.remove({ output: 'pneumaticcraft:armor_upgrade' })
    event.shaped('pneumaticcraft:armor_upgrade', ['PSP', 'ICI', 'PSP'], {
        P: 'pneumaticcraft:plastic', S: 'pneumaticcraft:printed_circuit_board',
        I: 'powergrid:integrated_circuit', C: 'pneumaticcraft:compressed_iron_gear'
    }).id('kubejs:tech/pneumatic/armor_upgrade_sensors')
})
