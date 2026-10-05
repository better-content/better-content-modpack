// Scalable logistics roots in the three Font materials. Bumblezone honey
// crystal shards root bulk transport, Nether blaze rods root ordinary pipe
// modules, and Aether enchanted gravitite roots high-tier automation. Every
// component is consumed in the recipe the player can inspect.
var BC_DISABLED_INPUTLESS_RAT_OUTPUTS = [
    'rats:rat_upgrade_ore_doubling',
    'rats:rat_upgrade_fisherman',
    'rats:rat_upgrade_milker',
    'rats:rat_upgrade_aristocrat',
    'rats:rat_upgrade_christmas',
    'rats:rat_upgrade_support'
]

ServerEvents.recipes(function (event) {
    // This contextual recipe outputs an NBT-cleared copy of any module. It has
    // no static graph edges and would also charge a second tier component for
    // re-crafting an existing module, so it is not part of the restored ladder.
    event.remove({ id: 'prettypipes:module_clearing' })
    BC_DISABLED_INPUTLESS_RAT_OUTPUTS.forEach(function (item) {
        event.remove({ output: item })
    })
    event.remove({ output: 'rats:chunky_cheese_token' })
    event.remove({ output: 'rats:ratlantis_portal' })

    // The first Bumblezone trip supplies the shards for a useful 32-pipe
    // network. Modules then expose two additional visible tiers instead of
    // charging hidden inventory costs after crafting.
    event.remove({ output: 'prettypipes:pipe' })
    event.shaped('8x prettypipes:pipe', ['RGR', 'ILI', 'RGR'], {
        R: 'minecraft:redstone', G: '#forge:glass', I: 'minecraft:iron_bars',
        L: 'the_bumblezone:honey_crystal_shards'
    }).id('kubejs:logistics_roots/root/pretty_pipes')

    // Ordinary modules are Nether-rooted.
    event.remove({ output: 'prettypipes:blank_module' })
    event.shaped('prettypipes:blank_module', ['QMQ', 'SPS', 'QRQ'], {
        Q: 'ae2:certus_quartz_crystal', M: 'minecraft:blaze_rod',
        S: 'minecraft:stone_slab', P: 'prettypipes:pipe', R: 'minecraft:redstone'
    }).id('kubejs:logistics_roots/tier/nether_blank_module')

    // High modules are Aether-rooted.
    var BC_PRETTY_PIPES_HIGH_MODULES = {
        high_crafting_module: {
            pattern: ['GCG', 'GMG', 'GIG'],
            key: { G: 'minecraft:gold_ingot', C: 'aether:enchanted_gravitite',
                M: 'prettypipes:medium_crafting_module', I: 'minecraft:iron_ingot' }
        },
        high_extraction_module: {
            pattern: ['GCG', 'GMG', 'GGG'],
            key: { G: 'minecraft:gold_ingot', C: 'aether:enchanted_gravitite',
                M: 'prettypipes:medium_extraction_module' }
        },
        high_filter_module: {
            pattern: ['GCG', 'BMB', 'GBG'],
            key: { G: 'minecraft:gold_ingot', C: 'aether:enchanted_gravitite',
                B: 'minecraft:iron_bars', M: 'prettypipes:medium_filter_module' }
        },
        high_high_priority_module: {
            pattern: ['PCP', 'PMP', 'PPP'],
            key: { C: 'aether:enchanted_gravitite', P: 'minecraft:paper',
                M: 'prettypipes:medium_high_priority_module' }
        },
        high_low_priority_module: {
            pattern: ['PCP', 'PMP', 'PPP'],
            key: { C: 'aether:enchanted_gravitite', P: '#forge:cobblestone',
                M: 'prettypipes:medium_low_priority_module' }
        },
        high_retrieval_module: {
            pattern: ['RCR', 'GMG', 'RGR'],
            key: { R: 'minecraft:redstone_block', C: 'aether:enchanted_gravitite',
                G: 'minecraft:gold_ingot', M: 'prettypipes:medium_retrieval_module' }
        },
        high_speed_module: {
            pattern: ['GCG', 'BMB', 'GBG'],
            key: { G: 'minecraft:gold_ingot', C: 'aether:enchanted_gravitite',
                B: 'minecraft:sugar', M: 'prettypipes:medium_speed_module' }
        }
    }
    Object.keys(BC_PRETTY_PIPES_HIGH_MODULES).forEach(function (module) {
        var recipe = BC_PRETTY_PIPES_HIGH_MODULES[module]
        event.remove({ output: 'prettypipes:' + module })
        event.shaped('prettypipes:' + module, recipe.pattern, recipe.key)
            .id('kubejs:logistics_roots/tier/aether_' + module)
    })

    event.remove({ output: 'sophisticatedstorage:hopper_upgrade' })
    event.shaped('sophisticatedstorage:hopper_upgrade', ['IRI', 'HLH', ' U '], {
        I: '#forge:ingots/iron', R: '#forge:dusts/redstone', H: 'minecraft:hopper',
        L: 'the_bumblezone:honey_crystal_shards', U: 'sophisticatedstorage:upgrade_base'
    }).id('kubejs:logistics_roots/root/sophisticated_automation')

    event.remove({ output: 'create:redstone_requester' })
    event.shapeless('create:redstone_requester', [
        'create:stock_link', '#forge:dusts/redstone', '#forge:ingots/iron',
        'minecraft:blaze_rod'
    ]).id('kubejs:logistics_roots/root/create_request_logistics')

    event.remove({ output: 'rats:rat_upgrade_basic' })
    event.shaped('rats:rat_upgrade_basic', ['CCC', 'CLC', 'CCC'], {
        C: '#forge:cheese', L: 'the_bumblezone:honey_crystal_shards'
    }).id('kubejs:logistics_roots/root/rat_work_orders')

    // Ordinary hoppers remain early local transport. The high-throughput
    // variant is a scalable logistics upgrade rooted in the Bumblezone.
    event.remove({ id: 'littlelogistics:rapid_hopper' })
    event.shaped('littlelogistics:rapid_hopper', ['GHG', ' L ', ' R '], {
        G: '#forge:ingots/gold', H: 'minecraft:hopper',
        L: 'the_bumblezone:honey_crystal_shards', R: 'minecraft:redstone_block'
    }).id('littlelogistics:rapid_hopper')

    // Little Logistics watercraft keep their upstream recipes; the
    // Starcatcher rod swap lives in 86_dimension_transport.js.

    // AE2's first usable electrical supply conjunctively proves the PowerGrid,
    // Aether, OC2R, meteor, and Impossible Matter roots.
    event.remove({ output: 'ae2:energy_acceptor' })
    event.shapeless('ae2:energy_acceptor', [
        'powergrid:generator_housing',
        'aether:enchanted_gravitite',
        'oc2r:computer',
        'ae2:sky_stone_block',
        'kubejs:impossible_support_matrix'
    ]).id('kubejs:logistics_roots/root/ae2_powergrid_energy_acceptor')
})
