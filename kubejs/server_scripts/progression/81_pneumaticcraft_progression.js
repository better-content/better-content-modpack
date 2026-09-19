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


    // Assembly tooling is a pressure-era capability, with electrical
    // instrumentation replacing the native raw PCB-only shortcut.
    event.remove({ output: 'pneumaticcraft:assembly_drill' })
    event.shaped('pneumaticcraft:assembly_drill', ['DPP', '  P', 'IEI'], {
        D: '#forge:gems/diamond', P: 'pneumaticcraft:pneumatic_cylinder',
        I: '#forge:ingots/compressed_iron', E: 'kubejs:electrical_instrumentation_module'
    }).id('kubejs:tech/pneumatic/assembly_drill_instrumented')

    // Universal sensing remains pneumatic, but enters through the authored
    // instrumentation family rather than a free vanilla repeater recipe.
    event.remove({ output: 'pneumaticcraft:universal_sensor' })
    event.shaped('pneumaticcraft:universal_sensor', [' S ', 'PIP', 'PTP'], {
        S: 'pneumaticcraft:seismic_sensor', P: 'pneumaticcraft:plastic',
        I: 'kubejs:electrical_instrumentation_module', T: 'pneumaticcraft:pressure_tube'
    }).id('kubejs:tech/pneumatic/universal_sensor_instrumentation')

    // TECH-07 service surfaces: programming and Amadron become available only
    // after pressure electronics exist, while the native programmable behavior
    // and Amadron trade data remain untouched.
    event.remove({ output: 'pneumaticcraft:programmer' })
    event.shaped('pneumaticcraft:programmer', ['RGR', 'TBT', 'P P'], {
        R: '#forge:dyes/red', G: '#forge:glass_panes/black',
        T: 'pneumaticcraft:turbine_rotor', B: 'pneumaticcraft:printed_circuit_board',
        P: 'kubejs:electrical_instrumentation_module'
    }).id('kubejs:tech/pneumatic/programmer_electrical_control')

    event.remove({ output: 'pneumaticcraft:programmable_controller' })
    event.shaped('pneumaticcraft:programmable_controller', ['IRI', 'CDP', 'INI'], {
        I: '#forge:ingots/compressed_iron', R: 'pneumaticcraft:remote',
        C: 'pneumaticcraft:printed_circuit_board', D: 'pneumaticcraft:drone',
        P: 'pneumaticcraft:advanced_pressure_tube', N: 'kubejs:ae_logic_package'
    }).id('kubejs:tech/pneumatic/programmable_controller_ae_logic')

    event.remove({ output: 'pneumaticcraft:amadron_tablet' })
    event.shaped('pneumaticcraft:amadron_tablet', ['PPP', 'PGP', 'PCP'], {
        P: 'pneumaticcraft:plastic', G: 'pneumaticcraft:gps_tool', C: 'pneumaticcraft:air_canister'
    }).id('kubejs:tech/pneumatic/amadron_tablet_pressure_service')
})
