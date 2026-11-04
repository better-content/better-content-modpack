// Meteor Certus replaces the retired Ratlantis component ladder. The first
// pipes need no operating ME network; later modules keep their native tiers.
var BC_DISABLED_INPUTLESS_RAT_OUTPUTS = [
    'rats:rat_upgrade_ore_doubling',
    'rats:rat_upgrade_fisherman',
    'rats:rat_upgrade_milker',
    'rats:rat_upgrade_aristocrat',
    'rats:rat_upgrade_christmas',
    'rats:rat_upgrade_support'
]

ServerEvents.recipes(function (event) {
    event.remove({ id: 'prettypipes:module_clearing' })
    event.remove({ output: 'rats:chunky_cheese_token' })
    event.remove({ output: 'rats:ratlantis_portal' })
    BC_DISABLED_INPUTLESS_RAT_OUTPUTS.forEach(function (item) {
        event.remove({ output: item })
    })

    var partConstruction = Platform.isLoaded('better_manufacturing')
    event.remove({ output: 'prettypipes:pipe' })
    var pipeKey = { R: 'minecraft:redstone', G: '#forge:glass', I: 'minecraft:iron_bars',
        Q: 'ae2:certus_quartz_crystal' }
    if (partConstruction) pipeKey.H = 'tconstruct:tool_handle'
    event.shaped('8x prettypipes:pipe', partConstruction ? ['HGR', 'IQI', 'RGR'] : ['RGR', 'IQI', 'RGR'], pipeKey)
        .id('kubejs:meteor_logistics/pipe_batch')

    event.remove({ output: 'prettypipes:blank_module' })
    var blankKey = { Q: 'ae2:certus_quartz_crystal', M: 'ae2:certus_quartz_crystal',
        S: 'minecraft:stone_slab', P: 'prettypipes:pipe', R: 'minecraft:redstone' }
    if (partConstruction) blankKey.H = 'tconstruct:tool_handle'
    event.shaped('prettypipes:blank_module', partConstruction ? ['QMQ', 'HPS', 'QRQ'] : ['QMQ', 'SPS', 'QRQ'], blankKey)
        .id('kubejs:meteor_logistics/blank_module')

    var highModules = {
        high_crafting_module: { pattern: ['GCG', 'GMG', 'GIG'],
            key: { G: 'minecraft:gold_ingot', M: 'prettypipes:medium_crafting_module', I: 'minecraft:iron_ingot' } },
        high_extraction_module: { pattern: ['GCG', 'GMG', 'GGG'],
            key: { G: 'minecraft:gold_ingot', M: 'prettypipes:medium_extraction_module' } },
        high_filter_module: { pattern: ['GCG', 'BMB', 'GBG'],
            key: { G: 'minecraft:gold_ingot', B: 'minecraft:iron_bars', M: 'prettypipes:medium_filter_module' } },
        high_high_priority_module: { pattern: ['PCP', 'PMP', 'PPP'],
            key: { P: 'minecraft:paper', M: 'prettypipes:medium_high_priority_module' } },
        high_low_priority_module: { pattern: ['PCP', 'PMP', 'PPP'],
            key: { P: '#forge:cobblestone', M: 'prettypipes:medium_low_priority_module' } },
        high_retrieval_module: { pattern: ['RCR', 'GMG', 'RGR'],
            key: { R: 'minecraft:redstone_block', G: 'minecraft:gold_ingot', M: 'prettypipes:medium_retrieval_module' } },
        high_speed_module: { pattern: ['GCG', 'BMB', 'GBG'],
            key: { G: 'minecraft:gold_ingot', B: 'minecraft:sugar', M: 'prettypipes:medium_speed_module' } }
    }
    Object.keys(highModules).forEach(function (module) {
        // These two material-dependent native-ID recipes bake installed module settings.
        if (partConstruction && (module === 'high_extraction_module' || module === 'high_speed_module')) return
        var recipe = highModules[module]
        recipe.key.C = 'ae2:certus_quartz_crystal'
        event.remove({ output: 'prettypipes:' + module })
        var outputPattern = recipe.pattern
        if (partConstruction) {
            recipe.key.H = 'tconstruct:large_plate'
            // Replace one repeated structural ingredient, never Certus C or prior module M.
            outputPattern = module === 'high_high_priority_module' || module === 'high_low_priority_module'
                ? ['HCP', 'PMP', 'PPP']
                : module === 'high_retrieval_module' ? ['HCR', 'GMG', 'RGR']
                : ['HCG', 'GMG', 'GIG']
            if (module === 'high_filter_module') outputPattern = ['HCG', 'BMB', 'GBG']
        }
        event.shaped('prettypipes:' + module, outputPattern, recipe.key)
            .id('kubejs:meteor_logistics/high_' + module)
    })

    event.remove({ output: 'sophisticatedstorage:hopper_upgrade' })
    if (Platform.isLoaded('better_manufacturing')) {
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['IRI', 'HQH', 'AU '],
            key: {
                I: { tag: 'forge:ingots/iron' }, R: { tag: 'forge:dusts/redstone' },
                H: { item: 'minecraft:hopper' }, Q: { item: 'ae2:certus_quartz_crystal' },
                A: { item: 'tconstruct:tool_handle' }, U: { item: 'sophisticatedstorage:upgrade_base' }
            }, result: { item: 'sophisticatedstorage:hopper_upgrade' },
            function: 'better_manufacturing:sophisticatedstorage/hopper',
            parts: [{ role: 'actuator', slot: 6 }]
        }).id('kubejs:meteor_logistics/storage_automation')
    } else {
        event.shaped('sophisticatedstorage:hopper_upgrade', ['IRI', 'HQH', ' U '], {
            I: '#forge:ingots/iron', R: '#forge:dusts/redstone', H: 'minecraft:hopper',
            Q: 'ae2:certus_quartz_crystal', U: 'sophisticatedstorage:upgrade_base'
        }).id('kubejs:meteor_logistics/storage_automation')
    }

    event.remove({ output: 'create:redstone_requester' })
    var requesterInputs = [
        'create:stock_link', '#forge:dusts/redstone', '#forge:ingots/iron',
        'ae2:certus_quartz_crystal'
    ]
    if (partConstruction) requesterInputs.push('tconstruct:tool_handle')
    event.shapeless('create:redstone_requester', requesterInputs).id('kubejs:meteor_logistics/create_requester')

    // Basic rat work and ordinary boats use their native recipes again.
    // With Manufacturing the native-ID variant preserves this Certus gate; without it
    // keep the prior pack root, not the upstream no-Certus shortcut.
    if (!Platform.isLoaded('better_manufacturing')) {
        event.remove({ id: 'littlelogistics:rapid_hopper' })
        event.shaped('littlelogistics:rapid_hopper', ['GHG', ' Q ', ' R '], {
            G: '#forge:ingots/gold', H: 'minecraft:hopper',
            Q: 'ae2:certus_quartz_crystal', R: 'minecraft:redstone_block'
        }).id('littlelogistics:rapid_hopper')
    }

    // Durable network devices each consume one finite Sky Stone block. The
    // item terminal retains its plate and other named components.
    event.remove({ id: 'prettypipes:item_terminal' })
    event.shaped('prettypipes:item_terminal', ['HPS', 'RCE', 'IPD'], {
        H: 'tconstruct:large_plate', P: 'minecraft:ender_pearl', S: 'ae2:sky_stone_block',
        R: 'prettypipes:high_retrieval_module', C: '#forge:chests',
        E: 'prettypipes:high_extraction_module', I: 'minecraft:iron_block',
        D: 'minecraft:diamond'
    }).id('prettypipes:item_terminal')
    event.remove({ id: 'prettypipes:pressurizer' })
    if (partConstruction) {
        event.custom({ type: 'better_manufacturing:part_variant',
            pattern: ['TST', 'SBS', 'TSA'],
            key: { T: { item: 'minecraft:iron_ingot' }, S: { item: 'prettypipes:high_speed_module' },
                B: { item: 'ae2:sky_stone_block' }, A: { item: 'tconstruct:tough_handle' } },
            result: { item: 'prettypipes:pressurizer' },
            function: 'better_manufacturing:prettypipes/pressurizer',
            parts: [{ role: 'working_assembly', slot: 8 }]
        }).id('prettypipes:pressurizer')
    } else {
        event.shaped('prettypipes:pressurizer', ['TST', 'SBS', 'TSA'], {
            T: 'minecraft:iron_ingot', S: 'prettypipes:high_speed_module',
            B: 'ae2:sky_stone_block', A: 'minecraft:iron_ingot'
        }).id('prettypipes:pressurizer')
    }

    event.remove({ output: 'rats:rat_upgrade_basic' })
    event.shaped('rats:rat_upgrade_basic', ['CCC', 'QLQ', 'CCC'], {
        C: '#forge:cheese', L: 'the_bumblezone:honey_crystal_shards',
        Q: 'ae2:certus_quartz_crystal'
    }).id('kubejs:meteor_logistics/rat_work_orders')

    event.remove({ output: 'ae2:energy_acceptor' })
    var acceptorInputs = [
        'powergrid:generator_housing',
        'ae2:certus_quartz_crystal',
        'aether:enchanted_gravitite',
        'oc2r:computer',
        'ae2:sky_stone_block',
        'kubejs:impossible_support_matrix'
    ]
    if (partConstruction) acceptorInputs.push('tconstruct:large_plate')
    event.shapeless('ae2:energy_acceptor', acceptorInputs).id('kubejs:meteor_logistics/ae2_energy_acceptor')
})
