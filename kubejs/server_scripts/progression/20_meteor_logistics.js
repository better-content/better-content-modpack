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
    BC_DISABLED_INPUTLESS_RAT_OUTPUTS.forEach(function (item) {
        event.remove({ output: item })
    })

    event.remove({ output: 'prettypipes:pipe' })
    event.shaped('8x prettypipes:pipe', ['RGR', 'IQI', 'RGR'], {
        R: 'minecraft:redstone', G: '#forge:glass', I: 'minecraft:iron_bars',
        Q: 'ae2:certus_quartz_crystal'
    }).id('kubejs:meteor_logistics/pipe_batch')

    event.remove({ output: 'prettypipes:blank_module' })
    event.shaped('prettypipes:blank_module', ['QMQ', 'SPS', 'QRQ'], {
        Q: 'ae2:certus_quartz_crystal', M: 'ae2:certus_quartz_crystal',
        S: 'minecraft:stone_slab', P: 'prettypipes:pipe', R: 'minecraft:redstone'
    }).id('kubejs:meteor_logistics/blank_module')

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
        var recipe = highModules[module]
        recipe.key.C = 'ae2:certus_quartz_crystal'
        event.remove({ output: 'prettypipes:' + module })
        event.shaped('prettypipes:' + module, recipe.pattern, recipe.key)
            .id('kubejs:meteor_logistics/high_' + module)
    })

    event.remove({ output: 'sophisticatedstorage:hopper_upgrade' })
    event.shaped('sophisticatedstorage:hopper_upgrade', ['IRI', 'HQH', ' U '], {
        I: '#forge:ingots/iron', R: '#forge:dusts/redstone', H: 'minecraft:hopper',
        Q: 'ae2:certus_quartz_crystal', U: 'sophisticatedstorage:upgrade_base'
    }).id('kubejs:meteor_logistics/storage_automation')

    event.remove({ output: 'create:redstone_requester' })
    event.shapeless('create:redstone_requester', [
        'create:stock_link', '#forge:dusts/redstone', '#forge:ingots/iron',
        'ae2:certus_quartz_crystal'
    ]).id('kubejs:meteor_logistics/create_requester')

    // Basic rat work and ordinary boats use their native recipes again.
    event.remove({ id: 'littlelogistics:rapid_hopper' })
    event.shaped('littlelogistics:rapid_hopper', ['GHG', ' Q ', ' R '], {
        G: '#forge:ingots/gold', H: 'minecraft:hopper',
        Q: 'ae2:certus_quartz_crystal', R: 'minecraft:redstone_block'
    }).id('littlelogistics:rapid_hopper')

    event.remove({ output: 'ae2:energy_acceptor' })
    event.shapeless('ae2:energy_acceptor', [
        'powergrid:generator_housing',
        'ae2:certus_quartz_crystal',
        'oc2r:computer',
        'ae2:sky_stone_block',
        'kubejs:impossible_support_matrix'
    ]).id('kubejs:meteor_logistics/ae2_energy_acceptor')
})
