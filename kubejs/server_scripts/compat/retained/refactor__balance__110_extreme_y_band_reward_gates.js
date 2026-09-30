// Extreme Y-band reward gates.
// These recipes make terrain commitment pay off with powerful but bounded utility.
// Mountain materials come from high-altitude deposits; deepslate and lava-depth
// materials come from ADLODS bands tightened in config/adlods/Deposits.

var BC_EXTREME = {
    mountain: {
        emerald: 'minecraft:emerald',
        ruby: 'kubejs:corundum_lapping_grit',
        sapphire: 'kubejs:mountain_beryl_lens'
    },
    deepslate: {
        platinum: 'kubejs:platinum_group_residue',
        palladium: 'kubejs:tungsten_carbide_insert',
        rhodium: 'kubejs:titanium_thermal_plate',
        ruthenium: 'kubejs:kimberlite_diamond_seed'
    },
    lava: {
        osmium: 'kubejs:soulstone_carbon_matrix',
        iridium: 'kubejs:titanium_thermal_plate',
        hotstone: 'better_ore_geology:crushed_hotstone',
        debris: 'minecraft:netherite_scrap'
    },
    gate: {
        brass: 'create:brass_casing',
        power: 'powergrid:conductive_casing',
        oc2r: 'powergrid:conductive_casing',
        space: 'creatingspace:rocket_casing',
        ae2: 'ae2:engineering_processor',
        etherealSlate: 'bloodmagic:etherealslate'
    }
}

function bcExtremeItemExists(id) {
    try { return Item.exists(id) } catch (e) { return false }
}

function bcExtremeRecipe(event, output, pattern, keys, id) {
    if (!bcExtremeItemExists(output)) {
        console.info('[extreme-y-rewards] Skipping recipe for missing optional output: ' + output)
        return
    }
    for (var key in keys) {
        var ingredient = keys[key]
        if (ingredient && ingredient.charAt && ingredient.charAt(0) !== '#' && ingredient.indexOf(':') >= 0 && !bcExtremeItemExists(ingredient)) {
            console.info('[extreme-y-rewards] Skipping recipe for missing ingredient: ' + ingredient)
            return
        }
    }

    event.remove({ output: output })
    global.bcFactoryCrafting(event, id, output, 1, pattern, keys, true)
}

ServerEvents.recipes(function (event) {
    // Mountain-height rewards: strong exploration and movement utility without teleportation or flight.
     bcExtremeRecipe(event, 'artifacts:cloud_in_a_bottle', [
        'SES',
        'RPR',
        'SBS'
    ], {
        S: BC_EXTREME.mountain.sapphire,
        E: BC_EXTREME.mountain.emerald,
        R: BC_EXTREME.mountain.ruby,
        P: 'create:precision_mechanism',
        B: BC_EXTREME.gate.power
    }, 'kubejs:extreme_y_rewards/mountain/cloud_in_a_bottle')

     bcExtremeRecipe(event, 'artifacts:digging_claws', [
        'RER',
        'SBS',
        'RER'
    ], {
        R: BC_EXTREME.mountain.ruby,
        E: BC_EXTREME.mountain.emerald,
        S: BC_EXTREME.mountain.sapphire,
        B: BC_EXTREME.gate.brass
    }, 'kubejs:extreme_y_rewards/mountain/digging_claws')

     bcExtremeRecipe(event, 'artifacts:pocket_piston', [
        'RPR',
        'EBE',
        'SAS'
    ], {
        R: BC_EXTREME.mountain.ruby,
        P: 'create:piston_extension_pole',
        E: BC_EXTREME.mountain.emerald,
        B: BC_EXTREME.gate.power,
        S: BC_EXTREME.mountain.sapphire,
        A: 'create:andesite_alloy'
    }, 'kubejs:extreme_y_rewards/mountain/pocket_piston')

    // Deepslate-depth rewards: high-value workshop/local-site utility.
     bcExtremeRecipe(event, 'sophisticatedbackpacks:advanced_magnet_upgrade', [
        'PMP',
        'RBR',
        'PMP'
    ], {
        P: BC_EXTREME.deepslate.palladium,
        M: 'sophisticatedbackpacks:magnet_upgrade',
        R: BC_EXTREME.deepslate.rhodium,
        B: BC_EXTREME.gate.oc2r
    }, 'kubejs:extreme_y_rewards/deepslate/advanced_magnet_upgrade')

     bcExtremeRecipe(event, 'buildinggadgets2:gadget_destruction', [
        'RPR',
        'SBS',
        'UTU'
    ], {
        R: BC_EXTREME.deepslate.rhodium,
        P: BC_EXTREME.deepslate.platinum,
        S: BC_EXTREME.gate.space,
        B: 'buildinggadgets2:gadget_building',
        U: BC_EXTREME.deepslate.ruthenium,
        T: BC_EXTREME.deepslate.palladium
    }, 'kubejs:extreme_y_rewards/deepslate/gadget_destruction')

     bcExtremeRecipe(event, 'ae2:spatial_anchor', [
        'RPR',
        'SBS',
        'RPR'
    ], {
        R: BC_EXTREME.deepslate.ruthenium,
        P: BC_EXTREME.deepslate.platinum,
        S: 'ae2:singularity',
        B: BC_EXTREME.gate.ae2
    }, 'kubejs:extreme_y_rewards/deepslate/spatial_anchor')

    // Lava-depth rewards: survival/combat upgrades for the most dangerous extraction band.
     bcExtremeRecipe(event, 'artifacts:obsidian_skull', [
        'DIO',
        'SBS',
        'OID'
    ], {
        D: BC_EXTREME.lava.debris,
        I: BC_EXTREME.lava.iridium,
        O: BC_EXTREME.lava.osmium,
        S: 'minecraft:obsidian',
        B: BC_EXTREME.gate.space
    }, 'kubejs:extreme_y_rewards/lava_depths/obsidian_skull')

     bcExtremeRecipe(event, 'artifacts:fire_gauntlet', [
        'OIO',
        'IBI',
        'OSO'
    ], {
        O: BC_EXTREME.lava.osmium,
        I: BC_EXTREME.lava.iridium,
        B: BC_EXTREME.gate.space,
        S: 'bloodmagic:demonslate'
    }, 'kubejs:extreme_y_rewards/lava_depths/fire_gauntlet')

     bcExtremeRecipe(event, 'artifacts:universal_attractor', [
        'OIO',
        'PBP',
        'OIO'
    ], {
        O: BC_EXTREME.lava.hotstone,
        I: BC_EXTREME.lava.iridium,
        P: BC_EXTREME.deepslate.palladium,
        B: BC_EXTREME.gate.ae2
    }, 'kubejs:extreme_y_rewards/lava_depths/universal_attractor')

     bcExtremeRecipe(event, 'sophisticatedstorage:netherite_chest', [
        'IDI',
        'OCO',
        'IDI'
    ], {
        I: BC_EXTREME.lava.iridium,
        D: BC_EXTREME.lava.debris,
        O: BC_EXTREME.lava.osmium,
        C: 'sophisticatedstorage:diamond_chest'
    }, 'kubejs:extreme_y_rewards/lava_depths/netherite_chest')

     bcExtremeRecipe(event, 'sophisticatedstorage:netherite_barrel', [
        'IDI',
        'OCO',
        'IDI'
    ], {
        I: BC_EXTREME.lava.iridium,
        D: BC_EXTREME.lava.debris,
        O: BC_EXTREME.lava.osmium,
        C: 'sophisticatedstorage:diamond_barrel'
    }, 'kubejs:extreme_y_rewards/lava_depths/netherite_barrel')
})
