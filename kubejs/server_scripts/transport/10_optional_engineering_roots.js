// Optional engineering branches consume one era proof at their first capability
// root. Native components carry every downstream recipe.
ServerEvents.recipes(function (event) {
    var manufacturing = Platform.isLoaded('better_manufacturing')
    if (Platform.isLoaded('vs_eureka')) {
        ;['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'crimson', 'warped'].forEach(function (wood) {
            var helm = 'vs_eureka:' + wood + '_ship_helm'
            event.remove({ id: helm })
        })

        ;['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'].forEach(function (wood) {
            var helm = 'vs_eureka:' + wood + '_ship_helm'
            var helmKey = { H: 'tconstruct:tool_handle', B: 'minecraft:barrel',
                F: 'minecraft:' + wood + '_fence', P: 'minecraft:' + wood + '_planks' }
            if (manufacturing) helmKey.L = 'tconstruct:large_plate'
            event.shaped(helm, manufacturing ? ['LH ', 'FBF', 'P P'] : [' H ', 'FBF', 'P P'], helmKey)
                .id('kubejs:transport/hand_workshop/eureka/' + wood + '_ship_helm')
        })
    }

    if (Platform.isLoaded('vs_clockwork')) {
        if (manufacturing) {
            // Native raw-plank cutting remains an unconfigured blade alternative.
            // It consumes no Tinkers part, so the two blade IDs remain partial;
            // never erase the native acquisition route merely for coverage.
            // This flywheel is a consumed intermediate for the native two-input
            // reactionwheel item-application, not a new machine or a retune loop.
            // The family baker refuses any flywheel with existing settings NBT.
            event.custom({ type: 'better_manufacturing:part_variant', pattern: ['F', 'B', 'H'],
                key: { F: { item: 'create:flywheel' }, B: { item: 'tconstruct:broad_blade' },
                    H: { item: 'tconstruct:tough_handle' } },
                result: { item: 'create:flywheel' }, function: 'better_manufacturing:clockwork/reactionwheel_prep',
                parts: [{ role: 'blank', slot: 0 }, { role: 'blade', slot: 1 }, { role: 'handle', slot: 2 }]
            }).id('kubejs:transport/aerospace/clockwork/reactionwheel_flywheel_prep')
        }
        // Four part-bearing native-ID recipes keep these same era gates with Manufacturing.
        // Without it, preserve the original pack roots and original native removals.
        if (!manufacturing) {
        event.remove({ id: 'vs_clockwork:crafting/kinetics/andesite_flap_bearing' })
        event.shaped('vs_clockwork:andesite_flap_bearing', [' A ', 'CMC', ' S '], {
            A: 'create:andesite_alloy', C: 'create:cogwheel',
            M: 'kubejs:copper_machine_block', S: 'create:shaft'
        }).id('kubejs:transport/powered_works/clockwork/andesite_flap_bearing')

        event.remove({ id: 'vs_clockwork:crafting/kinetics/brass_propeller_bearing' })
        event.shaped('vs_clockwork:brass_propeller_bearing', ['LPL', 'BMB', ' S '], {
            P: 'create:precision_mechanism', B: '#forge:plates/brass',
            M: 'kubejs:brass_machine_block', S: 'create:shaft', L: 'aether:stripped_skyroot_log'
        }).id('kubejs:transport/precision_factory/clockwork/brass_propeller_bearing')

        event.remove({ id: 'vs_clockwork:crafting/pneumatics/air_compressor' })
        event.shaped('vs_clockwork:air_compressor', ['PBP', 'GAG', ' C '], {
            P: 'pneumaticcraft:pressure_tube',
            G: 'pneumaticcraft:compressed_iron_gear', A: 'kubejs:airtight_machine_block',
            C: 'create:brass_casing', B: 'aether:blue_aercloud'
        }).id('kubejs:transport/thermal_pressure/clockwork/air_compressor')

        event.remove({ id: 'vs_clockwork:crafting/physics/gyro' })
        event.shaped('vs_clockwork:gyro', [' S ', 'CMC', ' P '], {
            S: 'vs_clockwork:gyroscopic_sensor', C: 'powergrid:integrated_circuit',
            M: 'kubejs:electrical_machine_block', P: 'create:precision_mechanism'
        }).id('kubejs:transport/electrical_control/clockwork/gyro')
        }

        // With Manufacturing, the native-ID mechanical recipe keeps these nine
        // aerospace gates and bakes plate/handle settings. Without it, keep the
        // prior shaped progression root and its native recipe removal unchanged.
        if (!manufacturing) {
            event.remove({ id: 'vs_clockwork:mechanical_crafting/gas_thruster' })
            event.shaped('vs_clockwork:gas_thruster', ['ABA', 'CMC', 'ATA'], {
                A: 'aether:aerogel', B: 'aether:blue_aercloud', C: 'powergrid:integrated_circuit',
                M: 'vs_clockwork:wanderlite_matrix', T: 'pneumaticcraft:pressure_tube'
            }).id('kubejs:transport/aerospace/clockwork/gas_thruster')
        }
    }

    if (Platform.isLoaded('trackwork')) {
        event.remove({ id: 'trackwork:simple_wheel' })
        if (manufacturing) {
            event.custom({ type: 'better_manufacturing:part_variant', pattern: ['HK ', 'KCK', ' K '],
                key: { H: [{ item: 'tconstruct:tough_handle' }, { item: 'tconstruct:bow_limb' }],
                    K: { item: 'minecraft:dried_kelp' }, C: { item: 'kubejs:copper_machine_block' } },
                result: { item: 'trackwork:simple_wheel' }, function: 'better_manufacturing:trackwork/running_gear',
                parts: [{ role: 'hub_suspension', slot: 0 }]
            }).id('kubejs:transport/powered_works/trackwork/simple_wheel')
        } else {
            event.shaped('trackwork:simple_wheel', [' K ', 'KCK', ' K '], {
                K: 'minecraft:dried_kelp', C: 'kubejs:copper_machine_block'
            }).id('kubejs:transport/powered_works/trackwork/simple_wheel')
        }
        event.remove({ id: 'trackwork:track_level_controller' })
        event.shaped('trackwork:track_level_controller', [' E ', 'MBM', ' R '], {
            E: 'create:electron_tube', M: 'create:precision_mechanism',
            B: 'kubejs:brass_machine_block', R: 'create:railway_casing'
        }).id('kubejs:transport/precision_factory/trackwork/level_controller')
    }

    if (Platform.isLoaded('better_rail_beetle')) {
        // The 22 material-dependent chassis/drives/modules use exact-ID datapack
        // variants when Manufacturing is present. Keep the original pack roots otherwise.
        if (!manufacturing) {
            event.remove({ id: 'better_rail_beetle:better_rail_beetle' })
            event.shaped('better_rail_beetle:better_rail_beetle', ['ECE', ' B ', ' M '], {
                E: 'create:electron_tube', C: 'create:railway_casing',
                B: 'minecraft:barrel', M: 'minecraft:minecart'
            }).id('kubejs:transport/precision_factory/better_rail_beetle')
        }

        var cradleKey = { B: '#forge:plates/brass', I: '#forge:plates/iron',
            C: 'create:andesite_casing', M: 'create:precision_mechanism' }
        if (manufacturing) { cradleKey.H = 'tconstruct:tool_handle'; cradleKey.L = 'tconstruct:large_plate' }
        event.shaped('better_rail_beetle:engine_cradle', manufacturing ? ['HIL', 'CMC', 'BIB'] : ['BIB', 'CMC', 'BIB'], cradleKey)
            .id('kubejs:transport/precision_factory/better_rail_beetle/engine_cradle')
        var frameKey = { B: '#forge:plates/brass', C: '#forge:plates/copper',
            I: '#forge:plates/iron', P: 'create:precision_mechanism' }
        if (manufacturing) { frameKey.H = 'tconstruct:tool_handle'; frameKey.L = 'tconstruct:large_plate' }
        event.shaped('2x better_rail_beetle:compact_module_frame', manufacturing ? ['HCB', 'IPI', 'BCL'] : ['BCB', 'IPI', 'BCB'], frameKey)
            .id('kubejs:transport/precision_factory/better_rail_beetle/module_frame')
        var remoteKey = { E: 'create:electron_tube', C: '#forge:plates/copper',
            M: 'better_rail_beetle:compact_module_frame', B: 'minecraft:stone_button' }
        if (manufacturing) { remoteKey.H = 'tconstruct:tool_handle'; remoteKey.J = 'tconstruct:large_plate' }
        event.shaped('better_rail_beetle:dispatch_remote', manufacturing ? ['HEJ', 'CMC', ' B '] : [' E ', 'CMC', ' B '], remoteKey)
            .id('kubejs:transport/precision_factory/better_rail_beetle/dispatch_remote')

        var materialTierOne = ['high_speed_governor_1', 'exhaust_recuperator_1', 'brake_manifold_1',
            'adhesion_sanders_1', 'compound_torque_clutch_1', 'reinforced_drawgear_1', 'trestle_erector_1']
        var railBeetleTierOne = function (output, center, side) {
            if (manufacturing && materialTierOne.indexOf(output) >= 0) return
            var key = { S: side, C: center, M: 'better_rail_beetle:compact_module_frame' }
            if (manufacturing) { key.H = 'tconstruct:tool_handle'; key.L = 'tconstruct:large_plate' }
            event.shaped('better_rail_beetle:' + output, manufacturing ? ['HSL', 'CMC', ' S '] : [' S ', 'CMC', ' S '], key)
                .id('kubejs:transport/precision_factory/better_rail_beetle/' + output)
        }
        var railBeetleTierTwo = function (output, prior, side) {
            if (manufacturing) return // All six tier-two thermal module roots bake in exact-ID datapack recipes.
            event.shaped('better_rail_beetle:' + output, [' S ', 'AMA', ' S '], {
                S: side, A: 'kubejs:airtight_machine_block', M: 'better_rail_beetle:' + prior
            }).id('kubejs:transport/thermal_pressure/better_rail_beetle/' + output)
        }
        var railBeetleElectricalTierTwo = function (output, prior, side) {
            if (manufacturing && output === 'exhaust_recuperator_2') return
            var key = { S: side, E: 'kubejs:electrical_machine_block', M: 'better_rail_beetle:' + prior }
            if (manufacturing) { key.H = 'tconstruct:tool_handle'; key.L = 'tconstruct:large_plate' }
            event.shaped('better_rail_beetle:' + output, manufacturing ? ['HSL', 'EME', ' S '] : [' S ', 'EME', ' S '], key)
                .id('kubejs:transport/electrical_control/better_rail_beetle/' + output)
        }

        railBeetleTierOne('high_speed_governor_1', 'create:speedometer', 'create:cogwheel')
        railBeetleTierOne('exhaust_recuperator_1', 'create:fluid_tank', '#forge:plates/copper')
        railBeetleTierOne('brake_manifold_1', 'create:fluid_pipe', '#forge:plates/iron')
        railBeetleTierOne('adhesion_sanders_1', '#kubejs:ordinary_sand', 'create:chute')
        railBeetleTierOne('compound_torque_clutch_1', 'create:clutch', 'create:large_cogwheel')
        railBeetleTierOne('reinforced_drawgear_1', 'create:minecart_coupling', '#forge:plates/iron')
        railBeetleTierOne('telescopic_survey_array_1', 'minecraft:spyglass', 'create:electron_tube')
        railBeetleTierOne('dispatch_receiver_1', 'better_rail_beetle:dispatch_remote', 'minecraft:redstone_torch')
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

        if (!manufacturing && Platform.isLoaded('create')) {
            event.shaped('better_rail_beetle:steam_drive', [' W ', 'SCS', ' F '], {
                W: 'create:fluid_tank', S: 'create:steam_engine',
                C: 'better_rail_beetle:engine_cradle', F: 'minecraft:blast_furnace'
            }).id('kubejs:transport/precision_factory/better_rail_beetle/steam_drive')
        }
        if (!manufacturing && Platform.isLoaded('powergrid')) {
            event.shaped('better_rail_beetle:flux_traction_motor', [' C ', 'MFM', ' E '], {
                C: 'powergrid:integrated_circuit', M: 'powergrid:electric_motor',
                F: 'better_rail_beetle:engine_cradle', E: 'kubejs:electrical_machine_block'
            }).id('kubejs:transport/electrical_control/better_rail_beetle/flux_traction_motor')
        }
        if (!manufacturing && Platform.isLoaded('ars_nouveau')) {
            event.shaped('better_rail_beetle:source_impeller', [' G ', 'GCG', ' A '], {
                G: 'ars_nouveau:source_gem', C: 'better_rail_beetle:engine_cradle', A: 'ars_nouveau:arcane_core'
            }).id('kubejs:transport/precision_factory/better_rail_beetle/source_impeller')
        }
        if (!manufacturing && Platform.isLoaded('bloodmagic')) {
            event.shaped('better_rail_beetle:lifeforce_ram', [' B ', 'BCB', ' T '], {
                B: 'bloodmagic:bloodstonebrick', C: 'better_rail_beetle:engine_cradle', T: 'bloodmagic:altar'
            }).id('kubejs:transport/precision_factory/better_rail_beetle/lifeforce_ram')
        }
        if (!manufacturing && Platform.isLoaded('pneumaticcraft')) {
            event.shaped('better_rail_beetle:pneumatic_expansion_motor', [' T ', 'GCG', ' A '], {
                T: 'pneumaticcraft:pressure_tube', G: 'pneumaticcraft:compressed_iron_gear',
                C: 'better_rail_beetle:engine_cradle', A: 'kubejs:airtight_machine_block'
            }).id('kubejs:transport/thermal_pressure/better_rail_beetle/pneumatic_expansion_motor')
        }
        if (!manufacturing && Platform.isLoaded('goety')) {
            event.shaped('better_rail_beetle:soul_combustor', [' O ', 'OCO', ' B '], {
                O: 'goety:ominous_stone', C: 'better_rail_beetle:engine_cradle', B: 'minecraft:soul_campfire'
            }).id('kubejs:transport/precision_factory/better_rail_beetle/soul_combustor')
        }
        if (!manufacturing && Platform.isLoaded('malum')) {
            event.shaped('better_rail_beetle:spirit_warping_engine', [' S ', 'SCS', ' B '], {
                S: 'malum:block_of_soulstone', C: 'better_rail_beetle:engine_cradle', B: 'malum:spirit_altar'
            }).id('kubejs:transport/precision_factory/better_rail_beetle/spirit_warping_engine')
        }
    }
})
