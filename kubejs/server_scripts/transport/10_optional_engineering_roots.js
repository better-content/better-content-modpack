// Optional engineering branches consume one era proof at their first capability
// root. Native components carry every downstream recipe.
ServerEvents.recipes(function (event) {
    if (Platform.isLoaded('vs_eureka')) {
        ;['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'crimson', 'warped'].forEach(function (wood) {
            var helm = 'vs_eureka:' + wood + '_ship_helm'
            event.remove({ id: helm })
        })

        ;['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'].forEach(function (wood) {
            var helm = 'vs_eureka:' + wood + '_ship_helm'
            event.shaped(helm, [' H ', 'FBF', 'P P'], {
                H: 'tconstruct:tool_handle', B: 'minecraft:barrel',
                F: 'minecraft:' + wood + '_fence', P: 'minecraft:' + wood + '_planks'
            }).id('kubejs:transport/hand_workshop/eureka/' + wood + '_ship_helm')
        })
    }

    if (Platform.isLoaded('vs_clockwork')) {
        event.remove({ id: 'vs_clockwork:crafting/kinetics/andesite_flap_bearing' })
        event.shaped('vs_clockwork:andesite_flap_bearing', [' A ', 'CMC', ' S '], {
            A: 'create:andesite_alloy', C: 'create:cogwheel',
            M: 'kubejs:copper_machine_block', S: 'create:shaft'
        }).id('kubejs:transport/powered_works/clockwork/andesite_flap_bearing')

        event.remove({ id: 'vs_clockwork:crafting/kinetics/brass_propeller_bearing' })
        event.shaped('vs_clockwork:brass_propeller_bearing', [' P ', 'BMB', ' S '], {
            P: 'create:precision_mechanism', B: '#forge:plates/brass',
            M: 'kubejs:brass_machine_block', S: 'create:shaft'
        }).id('kubejs:transport/precision_factory/clockwork/brass_propeller_bearing')

        event.remove({ id: 'vs_clockwork:crafting/pneumatics/air_compressor' })
        event.shaped('vs_clockwork:air_compressor', ['PTP', 'GAG', ' C '], {
            P: 'pneumaticcraft:pressure_tube', T: 'create:shaft',
            G: 'pneumaticcraft:compressed_iron_gear', A: 'kubejs:airtight_machine_block',
            C: 'create:brass_casing'
        }).id('kubejs:transport/thermal_pressure/clockwork/air_compressor')

        event.remove({ id: 'vs_clockwork:crafting/physics/gyro' })
        event.shaped('vs_clockwork:gyro', [' S ', 'CMC', ' P '], {
            S: 'vs_clockwork:gyroscopic_sensor', C: 'powergrid:integrated_circuit',
            M: 'kubejs:electrical_machine_block', P: 'create:precision_mechanism'
        }).id('kubejs:transport/electrical_control/clockwork/gyro')

        event.remove({ id: 'vs_clockwork:mechanical_crafting/gas_thruster' })
        event.shaped('vs_clockwork:gas_thruster', ['ABA', 'CMC', 'ATA'], {
            A: 'aether:aerogel', B: 'aether:blue_aercloud', C: 'powergrid:integrated_circuit',
            M: 'vs_clockwork:wanderlite_matrix', T: 'pneumaticcraft:pressure_tube'
        }).id('kubejs:transport/aerospace/clockwork/gas_thruster')
    }

    if (Platform.isLoaded('trackwork')) {
        event.remove({ id: 'trackwork:simple_wheel' })
        event.shaped('trackwork:simple_wheel', [' K ', 'KCK', ' K '], {
            K: 'minecraft:dried_kelp', C: 'kubejs:copper_machine_block'
        }).id('kubejs:transport/powered_works/trackwork/simple_wheel')
        event.remove({ id: 'trackwork:track_level_controller' })
        event.shaped('trackwork:track_level_controller', [' E ', 'MBM', ' R '], {
            E: 'create:electron_tube', M: 'create:precision_mechanism',
            B: 'kubejs:brass_machine_block', R: 'create:railway_casing'
        }).id('kubejs:transport/precision_factory/trackwork/level_controller')
    }

    if (Platform.isLoaded('rail_beetle')) {
        event.remove({ id: 'rail_beetle:rail_beetle' })
        event.shaped('rail_beetle:rail_beetle', ['ECE', ' B ', ' M '], {
            E: 'create:electron_tube', C: 'create:railway_casing',
            B: 'minecraft:barrel', M: 'minecraft:minecart'
        }).id('kubejs:transport/precision_factory/rail_beetle')

        event.shaped('rail_beetle:engine_cradle', ['BIB', 'CMC', 'BIB'], {
            B: '#forge:plates/brass', I: '#forge:plates/iron',
            C: 'create:andesite_casing', M: 'create:precision_mechanism'
        }).id('kubejs:transport/precision_factory/rail_beetle/engine_cradle')
        event.shaped('2x rail_beetle:compact_module_frame', ['BCB', 'IPI', 'BCB'], {
            B: '#forge:plates/brass', C: '#forge:plates/copper',
            I: '#forge:plates/iron', P: 'create:precision_mechanism'
        }).id('kubejs:transport/precision_factory/rail_beetle/module_frame')
        event.shaped('rail_beetle:dispatch_remote', [' E ', 'CMC', ' B '], {
            E: 'create:electron_tube', C: '#forge:plates/copper',
            M: 'rail_beetle:compact_module_frame', B: 'minecraft:stone_button'
        }).id('kubejs:transport/precision_factory/rail_beetle/dispatch_remote')

        var railBeetleTierOne = function (output, center, side) {
            event.shaped('rail_beetle:' + output, [' S ', 'CMC', ' S '], {
                S: side, C: center, M: 'rail_beetle:compact_module_frame'
            }).id('kubejs:transport/precision_factory/rail_beetle/' + output)
        }
        var railBeetleTierTwo = function (output, prior, side) {
            event.shaped('rail_beetle:' + output, [' S ', 'AMA', ' S '], {
                S: side, A: 'kubejs:airtight_machine_block', M: 'rail_beetle:' + prior
            }).id('kubejs:transport/thermal_pressure/rail_beetle/' + output)
        }
        var railBeetleElectricalTierTwo = function (output, prior, side) {
            event.shaped('rail_beetle:' + output, [' S ', 'EME', ' S '], {
                S: side, E: 'kubejs:electrical_machine_block', M: 'rail_beetle:' + prior
            }).id('kubejs:transport/electrical_control/rail_beetle/' + output)
        }

        railBeetleTierOne('high_speed_governor_1', 'create:speedometer', 'create:cogwheel')
        railBeetleTierOne('exhaust_recuperator_1', 'create:fluid_tank', '#forge:plates/copper')
        railBeetleTierOne('brake_manifold_1', 'create:fluid_pipe', '#forge:plates/iron')
        railBeetleTierOne('adhesion_sanders_1', 'minecraft:sand', 'create:chute')
        railBeetleTierOne('compound_torque_clutch_1', 'create:clutch', 'create:large_cogwheel')
        railBeetleTierOne('reinforced_drawgear_1', 'create:minecart_coupling', '#forge:plates/iron')
        railBeetleTierOne('telescopic_survey_array_1', 'minecraft:spyglass', 'create:electron_tube')
        railBeetleTierOne('dispatch_receiver_1', 'rail_beetle:dispatch_remote', 'minecraft:redstone_torch')
        railBeetleTierOne('trestle_erector_1', 'create:deployer', 'create:mechanical_piston')
        railBeetleTierOne('caged_searchlight', 'minecraft:lantern', '#forge:plates/copper')

        railBeetleTierTwo('high_speed_governor_2', 'high_speed_governor_1', 'create:precision_mechanism')
        railBeetleTierTwo('brake_manifold_2', 'brake_manifold_1', 'pneumaticcraft:pressure_tube')
        railBeetleTierTwo('adhesion_sanders_2', 'adhesion_sanders_1', 'pneumaticcraft:compressed_iron_gear')
        railBeetleTierTwo('compound_torque_clutch_2', 'compound_torque_clutch_1', 'pneumaticcraft:compressed_iron_gear')
        railBeetleTierTwo('reinforced_drawgear_2', 'reinforced_drawgear_1', 'create:sturdy_sheet')
        railBeetleTierTwo('trestle_erector_2', 'trestle_erector_1', 'create:mechanical_crafter')
        railBeetleElectricalTierTwo('exhaust_recuperator_2', 'exhaust_recuperator_1', 'powergrid:integrated_circuit')
        railBeetleElectricalTierTwo('telescopic_survey_array_2', 'telescopic_survey_array_1', 'powergrid:integrated_circuit')
        railBeetleElectricalTierTwo('dispatch_receiver_2', 'dispatch_receiver_1', 'morered:red_alloy_wire')

        if (Platform.isLoaded('create')) {
            event.shaped('rail_beetle:steam_drive', [' W ', 'SCS', ' F '], {
                W: 'create:fluid_tank', S: 'create:steam_engine',
                C: 'rail_beetle:engine_cradle', F: 'minecraft:blast_furnace'
            }).id('kubejs:transport/precision_factory/rail_beetle/steam_drive')
        }
        if (Platform.isLoaded('powergrid')) {
            event.shaped('rail_beetle:flux_traction_motor', [' C ', 'MFM', ' E '], {
                C: 'powergrid:integrated_circuit', M: 'powergrid:electric_motor',
                F: 'rail_beetle:engine_cradle', E: 'kubejs:electrical_machine_block'
            }).id('kubejs:transport/electrical_control/rail_beetle/flux_traction_motor')
        }
        if (Platform.isLoaded('ars_nouveau')) {
            event.shaped('rail_beetle:source_impeller', [' G ', 'GCG', ' A '], {
                G: 'ars_nouveau:source_gem', C: 'rail_beetle:engine_cradle', A: 'ars_nouveau:arcane_core'
            }).id('kubejs:transport/precision_factory/rail_beetle/source_impeller')
        }
        if (Platform.isLoaded('bloodmagic')) {
            event.shaped('rail_beetle:lifeforce_ram', [' B ', 'BCB', ' T '], {
                B: 'bloodmagic:bloodstonebrick', C: 'rail_beetle:engine_cradle', T: 'bloodmagic:altar'
            }).id('kubejs:transport/precision_factory/rail_beetle/lifeforce_ram')
        }
        if (Platform.isLoaded('pneumaticcraft')) {
            event.shaped('rail_beetle:pneumatic_expansion_motor', [' T ', 'GCG', ' A '], {
                T: 'pneumaticcraft:pressure_tube', G: 'pneumaticcraft:compressed_iron_gear',
                C: 'rail_beetle:engine_cradle', A: 'kubejs:airtight_machine_block'
            }).id('kubejs:transport/thermal_pressure/rail_beetle/pneumatic_expansion_motor')
        }
        if (Platform.isLoaded('goety')) {
            event.shaped('rail_beetle:soul_combustor', [' O ', 'OCO', ' B '], {
                O: 'goety:ominous_stone', C: 'rail_beetle:engine_cradle', B: 'minecraft:soul_campfire'
            }).id('kubejs:transport/precision_factory/rail_beetle/soul_combustor')
        }
        if (Platform.isLoaded('malum')) {
            event.shaped('rail_beetle:spirit_warping_engine', [' S ', 'SCS', ' B '], {
                S: 'malum:block_of_soulstone', C: 'rail_beetle:engine_cradle', B: 'malum:spirit_altar'
            }).id('kubejs:transport/precision_factory/rail_beetle/spirit_warping_engine')
        }
    }
})
