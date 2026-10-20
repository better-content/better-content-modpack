// Seared metallurgy needs two independently acquired underground rock families.
// Raw/cobbled forms belong to one family; Schist axes are not separate families.
// The ordinary Create workshop does not depend on these metallurgical recipes.
var BC_REFRACTORY_FAMILIES = [
    ['gabbro', ['unearthed:gabbro']],
    ['slate', ['unearthed:slate', 'unearthed:cobbled_slate']],
    ['phyllite', ['unearthed:phyllite', 'unearthed:cobbled_phyllite']],
    ['quartzite', ['unearthed:quartzite']],
    ['schist', ['unearthed:schist']]
]

ServerEvents.tags('item', function (event) {
    BC_REFRACTORY_FAMILIES.forEach(function (family) {
        event.add('kubejs:refractory_stone/' + family[0], family[1])
    })
})

ServerEvents.recipes(function (event) {
    // TCon's native cast recipes consume these blanks in the Part Builder
    // material slot. Keep the blank inputs visible in EMI so cast making does
    // not depend on discovering an implicit table action.
    // Four-sand T, not the sandstone square; retain original handmade cost/yield.
    event.shaped('4x tconstruct:blank_sand_cast', ['SSS', ' S '], {
        S: 'minecraft:sand'
    }).id('kubejs:hand_workshop/tconstruct/blank_sand_cast')
    event.shaped('4x tconstruct:blank_red_sand_cast', ['SSS', ' S '], {
        S: 'minecraft:red_sand'
    }).id('kubejs:hand_workshop/tconstruct/blank_red_sand_cast')

    event.remove({ output: 'tconstruct:grout' })
    event.remove({ input: 'tconstruct:grout' })
    ;['nether', 'aether', 'bumblezone'].forEach(function (origin) {
        // Keep the item registrations for saved stacks, but no metallurgy routes.
        event.remove({ output: 'kubejs:' + origin + '_font_grout' })
        event.remove({ input: 'kubejs:' + origin + '_font_grout' })
    })
    // Unordered distinct-family pairs: two union-tag slots would accept duplicates.
    BC_REFRACTORY_FAMILIES.forEach(function (left, index) {
        BC_REFRACTORY_FAMILIES.slice(index + 1).forEach(function (right) {
            event.shapeless('2x kubejs:refractory_compound', [
                '#kubejs:refractory_stone/' + left[0],
                '#kubejs:refractory_stone/' + right[0]
            ]).id('kubejs:hand_workshop/refractory/' + left[0] + '_' + right[0])
        })
    })
    event.smelting('tconstruct:seared_brick', 'kubejs:refractory_compound')
        .id('kubejs:hand_workshop/refractory/seared_brick')
    event.blasting('tconstruct:seared_brick', 'kubejs:refractory_compound')
        .id('kubejs:hand_workshop/refractory/seared_brick_blasting')

    // Close surface synthesis and cross-family conversions, not cobble restoration.
    ;[
        'unearthed:slate_from_smooth_stone',
        'unearthed:quartzite_from_smooth_sandstone',
        'unearthed:quartzite_from_smooth_red_sandstone',
        'unearthed:phyllite_from_slate', 'unearthed:schist_from_phyllite',
        'unearthed:gabbro'
    ].forEach(function (id) { event.remove({ id: id }) })

    event.remove({ type: 'minecraft:crafting_shaped', output: 'create:andesite_alloy' })
    event.remove({ type: 'minecraft:crafting_shapeless', output: 'create:andesite_alloy' })
    event.remove({ type: 'create:mixing', output: 'create:andesite_alloy' })
    event.remove({ id: 'tconstruct:compat/create/andesite_alloy_iron' })
    event.remove({ id: 'tconstruct:compat/create/andesite_alloy_zinc' })

    ;[['iron', 'forge:molten_iron'], ['zinc', 'forge:molten_zinc']].forEach(function (route) {
        event.custom({
            type: 'tconstruct:alloy',
            inputs: [
                { tag: 'tconstruct:seared_stone', amount: 90 },
                { tag: route[1], amount: 90 }
            ],
            result: { fluid: 'kubejs:molten_andesite_alloy', amount: 180 },
            temperature: 800
        }).id('kubejs:hand_workshop/alloying/andesite_alloy_' + route[0])
    })

    // Preserve upstream's efficient Melter route alongside the automated alloy path.
    ;[['iron', 'forge:molten_iron', 20], ['zinc', 'forge:molten_zinc', 16]].forEach(function (route) {
        event.custom({
            type: 'tconstruct:casting_basin',
            cast: { item: 'minecraft:andesite' },
            cast_consumed: true,
            fluid: { tag: route[1], amount: 10 },
            result: 'create:andesite_alloy',
            cooling_time: route[2]
        }).id('kubejs:hand_workshop/casting/andesite_alloy_' + route[0] + '_melter')
    })

    event.custom({
        type: 'tconstruct:casting_table',
        cast: { tag: 'tconstruct:casts/multi_use/ingot' },
        fluid: { fluid: 'kubejs:molten_andesite_alloy', amount: 90 },
        result: 'create:andesite_alloy',
        cooling_time: 50
    }).id('kubejs:hand_workshop/casting/andesite_alloy_ingot')

    event.remove({ id: 'create:crafting/kinetics/hand_crank' })
    event.shaped('create:hand_crank', ['PPP', ' A ', ' S '], {
        P: '#minecraft:planks', A: '#forge:ingots/iron', S: 'create:shaft'
    }).id('kubejs:hand_workshop/hand_crank')
})
