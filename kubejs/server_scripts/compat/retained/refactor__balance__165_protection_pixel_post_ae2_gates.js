// Protection Pixel is the post-AE2 body armor branch. Upstream recipes are mostly
// brass/iron/Create-era; this pass makes the branch consume AE2 local intelligence,
// quantum manufacturing, fission heat, and extreme-depth chemistry.

function bcPpExists(id) {
    try { return Item.exists(id) } catch (e) { return false }
}

function bcPpRemove(event, outputs) {
    for (var i = 0; i < outputs.length; i++) event.remove({ output: outputs[i] })
}

function bcPpMechanical(event, output, pattern, key, id) {
    if (!bcPpExists(output)) return
    event.remove({ output: output })
    event.custom({
        type: 'create:mechanical_crafting',
        pattern: pattern,
        key: key,
        result: { item: output },
        acceptMirrored: false
    }).id(id)
}

function bcPpSequence(event, input, transitional, output, sequence, loops, id) {
    if (!bcPpExists(output) || !bcPpExists(transitional)) return
    event.remove({ output: output })
    event.custom({
        type: 'create:sequenced_assembly',
        ingredient: { item: input },
        transitionalItem: { item: transitional },
        sequence: sequence,
        results: [{ item: output }],
        loops: loops
    }).id(id)
}

function bcPpDeploy(transitional, item) {
    return {
        type: 'create:deploying',
        ingredients: [{ item: transitional }, { item: item }],
        results: [{ item: transitional }]
    }
}

function bcPpPress(transitional) {
    return {
        type: 'create:pressing',
        ingredients: [{ item: transitional }],
        results: [{ item: transitional }]
    }
}

function bcPpFillLava(transitional, amount) {
    return {
        type: 'create:filling',
        ingredients: [{ item: transitional }, { fluid: 'minecraft:lava', amount: amount }],
        results: [{ item: transitional }]
    }
}

ServerEvents.recipes(function (event) {
    if (!bcPpExists('protection_pixel:armorloadplatform')) return
    if (!bcPpExists('kubejs:sky_steel_sheet') || !bcPpExists('kubejs:ae_logic_package')) return bcPpRemove(event, [
        'protection_pixel:smallnetheritesheet',
        'protection_pixel:ironarmorplate',
        'protection_pixel:brassarmorplate',
        'protection_pixel:alloyarmorplate',
        'protection_pixel:reinforcedfiber',
        'protection_pixel:heatresistantceramicsheet',
        'protection_pixel:chestplatelining',
        'protection_pixel:leggingslining',
        'protection_pixel:socks_boots',
        'protection_pixel:equipmentkit',
        'protection_pixel:armorplatekit',
        'protection_pixel:powerengine',
        'protection_pixel:heatoverlockingmechanism',
        'protection_pixel:armorloadplatform',
        'protection_pixel:steamectoskeleton',
        'protection_pixel:linkplate_helmet',
        'protection_pixel:linkplate_chestplate',
        'protection_pixel:linkplate_leggings',
        'protection_pixel:linkplate_boots',
        'protection_pixel:maneuveringwing',
        'protection_pixel:suspjetpack',
        'protection_pixel:wingsofprismas_chestplate'
    ])

    event.remove({ output: 'protection_pixel:smallnetheritesheet' })
    event.custom({
        type: 'create:mixing',
        heatRequirement: 'superheated',
        ingredients: [
            { item: 'minecraft:netherite_ingot' },
            { item: 'kubejs:sky_steel_sheet' },
            { item: 'kubejs:sky_steel_sheet' },
            { item: 'kubejs:soulstone_carbon_matrix' },
            { item: 'kubejs:titanium_thermal_plate' }
        ],
        results: [{ item: 'protection_pixel:smallnetheritesheet', count: 2 }]
    }).id('kubejs:protection_pixel/small_netherite_sheet')

    event.remove({ output: 'protection_pixel:reinforcedfiber' })
    event.custom({
        type: 'create:mixing',
        heatRequirement: 'heated',
        ingredients: [
            { item: 'minecraft:netherite_scrap' },
            { item: 'create:belt_connector' },
            { item: 'kubejs:tungsten_carbide_insert' },
            { item: 'kubejs:platinum_group_residue' },
            { item: 'bloodmagic:etherealslate' }
        ],
        results: [{ item: 'protection_pixel:reinforcedfiber', count: 2 }]
    }).id('kubejs:protection_pixel/reinforced_fiber')

    event.remove({ output: 'protection_pixel:heatresistantceramicsheet' })
    event.custom({
        type: 'create:mixing',
        heatRequirement: 'superheated',
        ingredients: [
            { item: 'minecraft:white_terracotta' },
            { item: 'minecraft:netherite_scrap' },
            { item: 'better_industrial_heat:heat_pipe' },
            { item: 'better_ore_geology:thorium_concentrate' },
            { item: 'better_ore_geology:uranium_concentrate' }
        ],
        results: [{ item: 'protection_pixel:heatresistantceramicsheet', count: 2 }]
    }).id('kubejs:protection_pixel/heat_resistant_ceramic_sheet')

     bcPpSequence(event, 'protection_pixel:smallnetheritesheet', 'protection_pixel:incompletealloyarmorplate', 'protection_pixel:alloyarmorplate', [bcPpPress('protection_pixel:incompletealloyarmorplate'), bcPpDeploy('protection_pixel:incompletealloyarmorplate', 'kubejs:sky_steel_sheet'), bcPpDeploy('protection_pixel:incompletealloyarmorplate', 'kubejs:sky_steel_sheet'), bcPpDeploy('protection_pixel:incompletealloyarmorplate', 'bloodmagic:etherealslate'), bcPpDeploy('protection_pixel:incompletealloyarmorplate', 'kubejs:titanium_thermal_plate')
    ], 2, 'kubejs:protection_pixel/alloy_armor_plate')

     bcPpMechanical(event, 'protection_pixel:armorloadplatform', [
        'SQSQS',
        'QLALQ',
        'SFCFS',
        'QLALQ',
        'SQSQS'
    ], {
        S: { item: 'kubejs:sky_steel_sheet' },
        Q: { item: 'kubejs:sky_steel_sheet' },
        L: { item: 'better_industrial_heat:heat_pipe' },
        A: { item: 'protection_pixel:alloyarmorplate' },
        F: { item: 'better_chemlib_hazards:gas_reaction_chamber' },
        C: { item: 'ae2:engineering_processor' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/armor_load_platform')

     bcPpMechanical(event, 'protection_pixel:powerengine', [
        'HFHFH',
        'FQCQF',
        'HFAFH',
        'FQCQF',
        'HFHFH'
    ], {
        H: { item: 'better_industrial_heat:heat_pipe' },
        F: { item: 'better_chemlib_hazards:gas_reaction_chamber' },
        Q: { item: 'kubejs:ae_logic_package' },
        C: { item: 'ae2:engineering_processor' },
        A: { item: 'protection_pixel:alloyarmorplate' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/power_engine')

     bcPpMechanical(event, 'protection_pixel:heatoverlockingmechanism', [
        'RHR',
        'QEQ',
        'RHR'
    ], {
        R: { item: 'kubejs:platinum_group_residue' },
        H: { item: 'better_industrial_heat:coolant_exchanger' },
        Q: { item: 'kubejs:ae_logic_package' },
        E: { item: 'protection_pixel:powerengine' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/heat_overlock_mechanism')

     bcPpMechanical(event, 'protection_pixel:equipmentkit', [
        'FHFHF',
        'HAPAH',
        'FPEPF',
        'HAPAH',
        'FHFHF'
    ], {
        F: { item: 'protection_pixel:reinforcedfiber' },
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:sky_steel_sheet' },
        E: { item: 'protection_pixel:powerengine' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/equipment_kit')

     bcPpMechanical(event, 'protection_pixel:armorplatekit', [
        'AFAFA',
        'FHPHF',
        'AKEKA',
        'FHPHF',
        'AFAFA'
    ], {
        A: { item: 'protection_pixel:alloyarmorplate' },
        F: { item: 'protection_pixel:reinforcedfiber' },
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        P: { item: 'kubejs:ae_logic_package' },
        K: { item: 'protection_pixel:equipmentkit' },
        E: { item: 'bloodmagic:etherealslate' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/armor_plate_kit')

     bcPpMechanical(event, 'protection_pixel:chestplatelining', [
        'FHF',
        'APA',
        'FHF'
    ], {
        F: { item: 'protection_pixel:reinforcedfiber' },
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:ae_logic_package' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/chestplate_lining')

     bcPpMechanical(event, 'protection_pixel:leggingslining', [
        'FHF',
        'APA',
        'F F'
    ], {
        F: { item: 'protection_pixel:reinforcedfiber' },
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:ae_logic_package' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/leggings_lining')

     bcPpMechanical(event, 'protection_pixel:socks_boots', [
        'F F',
        'H H',
        'A A'
    ], {
        F: { item: 'protection_pixel:reinforcedfiber' },
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        A: { item: 'protection_pixel:alloyarmorplate' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/socks_boots')

     bcPpMechanical(event, 'protection_pixel:linkplate_helmet', [
        'APA',
        'HNH',
        'F F'
    ], {
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:ae_logic_package' },
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        N: { item: 'minecraft:netherite_helmet' },
        F: { item: 'protection_pixel:reinforcedfiber' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/linkplate_helmet')

     bcPpMechanical(event, 'protection_pixel:linkplate_chestplate', [
        'A A',
        'HNH',
        'APA'
    ], {
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:ae_logic_package' },
        H: { item: 'protection_pixel:chestplatelining' },
        N: { item: 'minecraft:netherite_chestplate' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/linkplate_chestplate')

     bcPpMechanical(event, 'protection_pixel:linkplate_leggings', [
        'ANA',
        'H H',
        'P P'
    ], {
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:ae_logic_package' },
        H: { item: 'protection_pixel:leggingslining' },
        N: { item: 'minecraft:netherite_leggings' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/linkplate_leggings')

     bcPpMechanical(event, 'protection_pixel:linkplate_boots', [
        'P P',
        'H H',
        'ANA'
    ], {
        A: { item: 'protection_pixel:alloyarmorplate' },
        P: { item: 'kubejs:ae_logic_package' },
        H: { item: 'protection_pixel:socks_boots' },
        N: { item: 'minecraft:netherite_boots' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/linkplate_boots')

     bcPpMechanical(event, 'protection_pixel:steamectoskeleton', [
        'SPEPS',
        'PFCFP',
        'EFAFE',
        'PFCFP',
        'SPEPS'
    ], {
        S: { item: 'protection_pixel:smallnetheritesheet' },
        P: { item: 'kubejs:sky_steel_sheet' },
        E: { item: 'protection_pixel:powerengine' },
        F: { item: 'protection_pixel:reinforcedfiber' },
        C: { item: 'ae2:engineering_processor' },
        A: { item: 'protection_pixel:armorloadplatform' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/steam_exoskeleton')

     bcPpMechanical(event, 'protection_pixel:suspjetpack', [
        'TPT',
        'FEF',
        'TPT'
    ], {
        T: { item: 'create:copper_backtank' },
        P: { item: 'kubejs:ae_logic_package' },
        F: { item: 'protection_pixel:reinforcedfiber' },
        E: { item: 'protection_pixel:powerengine' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/susp_jetpack')

     bcPpMechanical(event, 'protection_pixel:maneuveringwing', [
        'PFEFP',
        'FWBWF',
        'PFEFP'
    ], {
        P: { item: 'kubejs:sky_steel_sheet' },
        F: { item: 'protection_pixel:reinforcedfiber' },
        E: { item: 'protection_pixel:powerengine' },
        W: { item: 'minecraft:elytra' },
        B: { item: 'ae2:engineering_processor' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/maneuvering_wing')

     bcPpMechanical(event, 'protection_pixel:tosaki_helmet', [
        'HOH',
        'QLQ',
        'STS'
    ], {
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        O: { item: 'better_ore_geology:crushed_hotstone' },
        Q: { item: 'kubejs:ae_logic_package' },
        L: { item: 'protection_pixel:linkplate_helmet' },
        S: { item: 'protection_pixel:smallnetheritesheet' },
        T: { item: 'better_ore_geology:thorium_concentrate' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/tosaki_helmet_lava_diving')

     bcPpMechanical(event, 'protection_pixel:tosaki_chestplate', [
        'HOH',
        'QLQ',
        'AEA',
        'SOS'
    ], {
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        O: { item: 'better_ore_geology:crushed_hotstone' },
        Q: { item: 'kubejs:ae_logic_package' },
        L: { item: 'protection_pixel:linkplate_chestplate' },
        A: { item: 'protection_pixel:heatoverlockingmechanism' },
        E: { item: 'protection_pixel:equipmentkit' },
        S: { item: 'protection_pixel:smallnetheritesheet' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/tosaki_chestplate_lava_diving')

     bcPpMechanical(event, 'protection_pixel:tosaki_leggings', [
        'HQH',
        'OLO',
        'SIS',
        ' H '
    ], {
        H: { item: 'protection_pixel:heatresistantceramicsheet' },
        Q: { item: 'kubejs:ae_logic_package' },
        O: { item: 'better_ore_geology:crushed_hotstone' },
        L: { item: 'protection_pixel:linkplate_leggings' },
        S: { item: 'protection_pixel:smallnetheritesheet' },
        I: { item: 'kubejs:titanium_thermal_plate' }
    }, 'kubejs:create/mechanical_crafting/protection_pixel/tosaki_leggings_lava_diving')

     bcPpSequence(event, 'protection_pixel:wingsofprism_chestplate', 'protection_pixel:incompletewingsofprism', 'protection_pixel:wingsofprismas_chestplate', [bcPpFillLava('protection_pixel:incompletewingsofprism', 500), bcPpPress('protection_pixel:incompletewingsofprism'), bcPpDeploy('protection_pixel:incompletewingsofprism', 'protection_pixel:heatoverlockingmechanism'), bcPpDeploy('protection_pixel:incompletewingsofprism', 'kubejs:ae_logic_package'), bcPpDeploy('protection_pixel:incompletewingsofprism', 'bloodmagic:etherealslate'), bcPpDeploy('protection_pixel:incompletewingsofprism', 'kubejs:titanium_thermal_plate')
    ], 2, 'kubejs:protection_pixel/wingsofprism_as')

    // Broad safety net: all named armor/equipment should consume post-AE2 components if an
    // upstream recipe survives because of alternate IDs or future mod updates.
    var lateOutputs = [
        'protection_pixel:tosaki_helmet', 'protection_pixel:tosaki_chestplate', 'protection_pixel:tosaki_leggings',
        'protection_pixel:workerhornet_chestplate', 'protection_pixel:wingsofprism_chestplate', 'protection_pixel:hellsnake_chestplate',
        'protection_pixel:breaker_chestplate', 'protection_pixel:typhoon_chestplate', 'protection_pixel:falconnest_chestplate',
        'protection_pixel:magneticstorm_chestplate', 'protection_pixel:closed_helmet', 'protection_pixel:plague_helmet',
        'protection_pixel:nightdemon_helmet', 'protection_pixel:bloodprisoner_helmet', 'protection_pixel:anchorpoint_leggings',
        'protection_pixel:buoyancy_leggings', 'protection_pixel:slingshot_leggings', 'protection_pixel:cannonarm',
        'protection_pixel:pneumaticgrenadelauncharm', 'protection_pixel:hookcannon', 'protection_pixel:heatpulsethruster',
        'protection_pixel:tacticaloxygensupplydevice', 'protection_pixel:blooddialysisdevice', 'protection_pixel:evasionwing'
    ]
    for (var i = 0; i < lateOutputs.length; i++) {
        if (!bcPpExists(lateOutputs[i])) continue
        event.replaceInput({ output: lateOutputs[i] }, 'minecraft:iron_ingot', 'protection_pixel:alloyarmorplate')
        event.replaceInput({ output: lateOutputs[i] }, '#forge:ingots/iron', 'protection_pixel:alloyarmorplate')
        event.replaceInput({ output: lateOutputs[i] }, 'create:andesite_alloy', 'ae2:engineering_processor')
        event.replaceInput({ output: lateOutputs[i] }, 'create:precision_mechanism', 'kubejs:ae_logic_package')
        event.replaceInput({ output: lateOutputs[i] }, 'create:brass_sheet', 'protection_pixel:smallnetheritesheet')
        event.replaceInput({ output: lateOutputs[i] }, '#forge:ingots/brass', 'protection_pixel:smallnetheritesheet')
    }
})
