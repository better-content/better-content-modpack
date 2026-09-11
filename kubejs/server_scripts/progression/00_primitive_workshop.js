// Before the first Dimension Font, local work follows a material ladder:
// handworked fibers, hearth-fired goods, then simple metal fittings. These
// recipes preserve crop-specific upstream routes while ensuring generic
// workshop utilities do not depend on origin-controlled cultivars.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'create:crafting/kinetics/chute' })
    event.shaped('4x create:chute', ['NCN', 'C C', 'NCN'], {
        N: '#forge:nuggets/iron', C: 'farmersdelight:canvas'
    }).id('kubejs:primitive_workshop/create_chute')

    // The conventional-tool policy removes the wooden shovel used upstream as
    // a spoon. A wooden rod keeps the utensil silhouette without reviving it.
    event.remove({ id: 'farmersdelight:cooking_pot' })
    event.shaped('farmersdelight:cooking_pot', ['bRb', 'iWi', 'iii'], {
        b: 'minecraft:brick', R: '#forge:rods/wooden',
        i: '#forge:ingots/iron', W: '#forge:buckets/water'
    }).id('kubejs:primitive_workshop/cooking_pot')

    event.shaped('supplementaries:sack', ['CSC', 'C C', 'CCC'], {
        C: 'farmersdelight:canvas', S: 'minecraft:string'
    }).id('kubejs:primitive_workshop/sack_from_canvas')
    event.shaped('2x supplementaries:awning', ['CCC', 'S S'], {
        C: 'farmersdelight:canvas', S: 'minecraft:stick'
    }).id('kubejs:primitive_workshop/awning_from_canvas')
    event.shaped('supplementaries:doormat', ['CC'], {
        C: 'farmersdelight:canvas'
    }).id('kubejs:primitive_workshop/doormat_from_canvas')
    event.shaped('supplementaries:fire_pit', ['CFC', 'CCC'], {
        C: '#forge:ingots/copper', F: '#minecraft:coals'
    }).id('kubejs:primitive_workshop/fire_pit_from_coal')

    event.shaped('quark:feeding_trough', ['PSP', 'PPP'], {
        P: '#minecraft:planks', S: 'farmersdelight:straw'
    }).id('kubejs:primitive_workshop/feeding_trough_from_straw')
    event.shaped('4x quark:thatch', ['SS', 'SS'], {
        S: 'farmersdelight:straw'
    }).id('kubejs:primitive_workshop/quark_thatch_from_straw')
    event.shaped('4x dawnoftimebuilder:thatch_wheat', ['SS', 'SS'], {
        S: 'farmersdelight:straw'
    }).id('kubejs:primitive_workshop/dawn_thatch_from_straw')
    event.shaped('8x dawnoftimebuilder:wattle_and_daub', ['SC', 'CS'], {
        S: 'farmersdelight:straw', C: 'minecraft:clay_ball'
    }).id('kubejs:primitive_workshop/dawn_wattle_and_daub_from_straw')
    event.shaped('8x dawnoftimebuilder:white_wattle_and_daub', ['SC', 'CD'], {
        S: 'farmersdelight:straw', C: 'minecraft:clay_ball', D: 'minecraft:white_dye'
    }).id('kubejs:primitive_workshop/dawn_white_wattle_and_daub_from_straw')
})
