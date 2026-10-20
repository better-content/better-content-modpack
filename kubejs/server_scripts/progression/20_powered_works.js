// Category roots are material-specific. Structural blocks never carry a
// dimension requirement shared by unrelated categories. Small native machines
// use fitted parts, not intact full-block structural assemblies.
ServerEvents.recipes(function (event) {
    event.shaped('4x kubejs:andesite_machine_block', ['ISI', 'SLS', 'ISI'], {
        I: '#forge:ingots/iron', S: '#forge:stone', L: '#forge:stripped_logs'
    }).id('kubejs:powered_works/andesite_machine_block')

    // Complete pre-metallurgy transmission closure. TCon still owns alloying.
    event.remove({ output: 'create:shaft' })
    event.shaped('8x create:shaft', ['I', 'I'], {
        I: '#forge:ingots/iron'
    }).id('kubejs:powered_works/workshop/shaft')
    event.remove({ output: 'create:cogwheel' })
    event.shapeless('create:cogwheel', ['create:shaft', '#minecraft:planks'])
        .id('kubejs:powered_works/workshop/cogwheel')
    event.remove({ output: 'create:large_cogwheel' })
    event.shapeless('create:large_cogwheel', ['create:shaft', '#minecraft:planks', '#minecraft:planks'])
        .id('kubejs:powered_works/workshop/large_cogwheel')
    event.remove({ output: 'create:andesite_casing' })
    event.shaped('2x create:andesite_casing', ['N N', 'LAL', 'N N'], {
        N: '#forge:nuggets/iron', L: '#forge:stripped_logs', A: 'minecraft:andesite'
    }).id('kubejs:powered_works/workshop/andesite_casing')
    event.remove({ output: 'create:mechanical_bearing' })
    event.shaped('create:mechanical_bearing', ['P', 'C', 'S'], {
        P: '#minecraft:wooden_slabs', C: 'create:andesite_casing', S: 'create:shaft'
    }).id('kubejs:powered_works/workshop/mechanical_bearing')

    // Native Depot/Basin recipes also consume Andesite Alloy; override these
    // supports or the apparent plate/mixing bootstrap still depends on TCon.
    event.remove({ output: 'create:depot' })
    event.shapeless('create:depot', ['create:andesite_casing', '#forge:ingots/iron'])
        .id('kubejs:powered_works/workshop/depot')
    event.remove({ output: 'create:basin' })
    event.shaped('create:basin', ['I I', 'III'], { I: '#forge:ingots/iron' })
        .id('kubejs:powered_works/workshop/basin')

    // The first Press has no plate prerequisite. Its placed housing is shorter
    // than an intact Machine Block, so the native fitted casing remains here.
    event.remove({ output: 'create:mechanical_press' })
    event.shaped('create:mechanical_press', [' S ', ' C ', ' I '], {
        S: 'create:shaft', C: 'create:andesite_casing', I: 'minecraft:iron_block'
    }).id('kubejs:powered_works/direct_root/mechanical_press')
    event.remove({ output: 'create:whisk' })
    event.shaped('create:whisk', [' S ', 'PSP', 'PPP'], {
        S: 'create:shaft', P: '#forge:plates/iron'
    }).id('kubejs:powered_works/workshop/whisk')
    event.remove({ output: 'create:mechanical_mixer' })
    event.shaped('create:mechanical_mixer', [' S ', ' A ', ' W '], {
        S: 'create:shaft', A: '#forge:plates/iron', W: 'create:whisk'
    }).id('kubejs:powered_works/mechanical_mixer')

    event.remove({ output: 'create:copper_casing' })
    event.custom({
        type: 'create:compacting',
        ingredients: [{ tag: 'forge:stripped_logs' }, { tag: 'forge:plates/copper' }],
        results: [{ item: 'create:copper_casing' }]
    }).id('kubejs:powered_works/copper_casing')
    event.custom({
        type: 'create:compacting',
        ingredients: [
            { item: 'kubejs:andesite_machine_block' }, { item: 'create:copper_casing' },
            { tag: 'forge:plates/copper' }, { tag: 'forge:plates/copper' },
            { tag: 'forge:plates/copper' }, { tag: 'forge:plates/copper' },
            { tag: 'forge:plates/iron' }, { tag: 'forge:nuggets/iron' }, { tag: 'forge:nuggets/iron' }
        ],
        results: [{ item: 'kubejs:copper_machine_block', count: 4 }]
    }).id('kubejs:powered_works/copper_machine_block')

    // Ordinary fabricated propellers and sail fittings must not restore a
    // hidden alloy prerequisite for fans or the Aether wind-power branch.
    event.remove({ output: 'create:propeller' })
    event.shaped('create:propeller', [' P ', 'PSP', ' P '], {
        P: '#forge:plates/iron', S: 'create:shaft'
    }).id('kubejs:powered_works/workshop/propeller')
    event.remove({ id: 'create:crafting/kinetics/white_sail' })
    event.shaped('2x create:white_sail', ['WS', 'SI'], {
        W: '#minecraft:wool', S: '#forge:rods/wooden', I: '#forge:nuggets/iron'
    }).id('kubejs:powered_works/workshop/white_sail')

    // Aether roots only sustainable water/wind generation, not the shared block.
    ;[
        ['create:water_wheel', ['LIL', 'ISI', 'LIL']],
        ['create:large_water_wheel', ['LIL', 'IWI', 'LIL']],
        ['create:windmill_bearing', [' L ', ' C ', ' S ']]
    ].forEach(function (root) {
        var output = root[0]
        var key = output === 'create:large_water_wheel'
            ? { L: 'aether:skyroot_log', I: '#forge:ingots/iron', W: 'create:water_wheel' }
            : output === 'create:windmill_bearing'
                ? { L: 'aether:skyroot_log', C: 'create:andesite_casing', S: 'create:shaft' }
                : { L: 'aether:skyroot_log', I: '#forge:ingots/iron', S: 'create:shaft' }
        event.remove({ output: output })
        event.shaped(output, root[1], key).id('kubejs:powered_works/direct_root/' + output.substring('create:'.length))
    })

    // Bounded local insertion does not need meteor routing or alloy composition.
    event.remove({ output: 'create:andesite_funnel' })
    event.shaped('2x create:andesite_funnel', [' K ', 'ICI'], {
        K: 'minecraft:dried_kelp', I: '#forge:nuggets/iron', C: 'create:andesite_casing'
    }).id('kubejs:powered_works/workshop/andesite_funnel')
    event.remove({ output: 'create:andesite_tunnel' })
    event.shaped('2x create:andesite_tunnel', ['KKK', 'ICI'], {
        K: 'minecraft:dried_kelp', I: '#forge:nuggets/iron', C: 'create:andesite_casing'
    }).id('kubejs:powered_works/workshop/andesite_tunnel')

    // Pumping is ordinary fluid handling, not power generation or ore enrichment.
    event.remove({ output: 'create:mechanical_pump' })
    event.shapeless('create:mechanical_pump', ['create:cogwheel', 'create:fluid_pipe'])
        .id('kubejs:powered_works/direct_root/mechanical_pump')

    // Nether capability is in each enrichment apparatus, including its non-ore
    // uses, never in the workshop's shared fittings or Machine Blocks.
    event.remove({ output: 'create:millstone' })
    event.shaped('create:millstone', [' C ', 'SNS', ' P '], {
        C: 'create:cogwheel', S: '#forge:stone', N: 'minecraft:nether_brick', P: '#forge:plates/copper'
    }).id('kubejs:powered_works/direct_root/millstone')
    if (Platform.isLoaded('createsifter')) {
        event.remove({ output: 'createsifter:sifter' })
        event.shaped('createsifter:sifter', ['WAW', 'SCS', ' N '], {
            W: '#minecraft:planks', A: 'create:andesite_casing', S: 'minecraft:stick',
            C: 'create:cogwheel', N: 'minecraft:nether_brick'
        }).id('kubejs:powered_works/refinement/sifter')
        // Keep the native brass throughput upgrade, with an explicit Nether root.
        event.remove({ output: 'createsifter:brass_sifter' })
        event.shaped('createsifter:brass_sifter', ['PPP', 'BSB', 'BNB'], {
            P: '#forge:plates/brass', B: '#forge:ingots/brass', S: 'createsifter:sifter',
            N: 'minecraft:nether_brick'
        }).id('kubejs:powered_works/refinement/brass_sifter')
    }
    event.remove({ output: 'create:crushing_wheel' })
    event.custom({
        type: 'create:mechanical_crafting', acceptMirrored: false,
        pattern: [' AAA ', 'AAPAA', 'APNPA', 'AAPAA', ' AAA '],
        key: { A: { item: 'create:andesite_alloy' }, P: { tag: 'minecraft:planks' }, N: { item: 'minecraft:nether_brick' } },
        result: { item: 'create:crushing_wheel', count: 2 }
    }).id('kubejs:powered_works/refinement/crushing_wheels')
})
