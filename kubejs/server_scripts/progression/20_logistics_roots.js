// Pretty Pipes is certus-based physical logistics before AE2 processors and
// matter-to-energy storage. Sky Stone anchors durable network devices only.
// Other logistics branches retain their visible Font-material roots.
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

    // Growable certus supplies routing logic without taxing every pipe or
    // module with finite meteor stone. Four crafts make a 32-pipe network.
    event.remove({ output: 'prettypipes:pipe' })
    event.shaped('8x prettypipes:pipe', ['RGR', 'ILI', 'RGR'], {
        R: 'minecraft:redstone', G: '#forge:glass', I: 'minecraft:iron_bars',
        L: 'ae2:certus_quartz_crystal'
    }).id('kubejs:logistics_roots/root/pretty_pipes')

    // Certus and redstone provide logic without AE2 processors.
    event.remove({ output: 'prettypipes:blank_module' })
    event.shaped('prettypipes:blank_module', ['QRQ', 'SPS', 'QRQ'], {
        Q: 'ae2:certus_quartz_crystal',
        S: 'minecraft:stone_slab', P: 'prettypipes:pipe', R: 'minecraft:redstone'
    }).id('kubejs:logistics_roots/proto_ae2/blank_module')

    // High modules refine the same certus logic.
    var BC_PRETTY_PIPES_HIGH_MODULES = {
        high_crafting_module: {
            pattern: ['GCG', 'GMG', 'GIG'],
            key: { G: 'minecraft:gold_ingot', C: 'ae2:certus_quartz_crystal',
                M: 'prettypipes:medium_crafting_module', I: 'minecraft:iron_ingot' }
        },
        high_extraction_module: {
            pattern: ['GCG', 'GMG', 'GGG'],
            key: { G: 'minecraft:gold_ingot', C: 'ae2:certus_quartz_crystal',
                M: 'prettypipes:medium_extraction_module' }
        },
        high_filter_module: {
            pattern: ['GCG', 'BMB', 'GBG'],
            key: { G: 'minecraft:gold_ingot', C: 'ae2:certus_quartz_crystal',
                B: 'minecraft:iron_bars', M: 'prettypipes:medium_filter_module' }
        },
        high_high_priority_module: {
            pattern: ['PCP', 'PMP', 'PPP'],
            key: { C: 'ae2:certus_quartz_crystal', P: 'minecraft:paper',
                M: 'prettypipes:medium_high_priority_module' }
        },
        high_low_priority_module: {
            pattern: ['PCP', 'PMP', 'PPP'],
            key: { C: 'ae2:certus_quartz_crystal', P: '#forge:cobblestone',
                M: 'prettypipes:medium_low_priority_module' }
        },
        high_retrieval_module: {
            pattern: ['RCR', 'GMG', 'RGR'],
            key: { R: 'minecraft:redstone_block', C: 'ae2:certus_quartz_crystal',
                G: 'minecraft:gold_ingot', M: 'prettypipes:medium_retrieval_module' }
        },
        high_speed_module: {
            pattern: ['GCG', 'BMB', 'GBG'],
            key: { G: 'minecraft:gold_ingot', C: 'ae2:certus_quartz_crystal',
                B: 'minecraft:sugar', M: 'prettypipes:medium_speed_module' }
        }
    }
    Object.keys(BC_PRETTY_PIPES_HIGH_MODULES).forEach(function (module) {
        var recipe = BC_PRETTY_PIPES_HIGH_MODULES[module]
        event.remove({ output: 'prettypipes:' + module })
        event.shaped('prettypipes:' + module, recipe.pattern, recipe.key)
            .id('kubejs:logistics_roots/proto_ae2/' + module)
    })

    // One finite meteor stone per network device, not per pipe or module.
    event.remove({ output: 'prettypipes:item_terminal' })
    event.shaped('prettypipes:item_terminal', ['SPI', 'RCE', 'IPD'], {
        S: 'ae2:sky_stone_block', P: 'minecraft:ender_pearl', I: 'minecraft:iron_block',
        R: 'prettypipes:high_retrieval_module', C: '#forge:chests',
        E: 'prettypipes:high_extraction_module', D: 'minecraft:diamond'
    }).id('kubejs:logistics_roots/proto_ae2/item_terminal')

    // The native crafting-terminal upgrade inherits its terminal's Sky Stone.
    event.remove({ output: 'prettypipes:pressurizer' })
    event.shaped('prettypipes:pressurizer', ['TST', 'SBS', 'TST'], {
        T: 'minecraft:iron_ingot', S: 'prettypipes:high_speed_module',
        B: 'ae2:sky_stone_block'
    }).id('kubejs:logistics_roots/proto_ae2/pressurizer')

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
