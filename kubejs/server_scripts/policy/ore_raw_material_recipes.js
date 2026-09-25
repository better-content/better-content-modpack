// Keep legacy raw-metal conversion recipes on the authored geological feeds.
// The original ingredients and metal quantities remain the same; only results
// that exposed bypassing raw-ore identities are redirected.
var BC_RAW_METAL_RECIPE_ROUTES = [
    {
        source: 'quark:building/crafting/raw_iron_bricks_revert',
        id: 'kubejs:ore_policy/raw_iron_bricks_to_ironstone',
        output: '9x realistic_ores:small_ore_chunk_ironstone',
        ingredients: ['quark:raw_iron_bricks']
    },
    {
        source: 'quark:building/crafting/raw_copper_bricks_revert',
        id: 'kubejs:ore_policy/raw_copper_bricks_to_copper_bloom',
        output: '9x realistic_ores:small_ore_chunk_copper_bloom',
        ingredients: ['quark:raw_copper_bricks']
    },
    {
        source: 'quark:building/crafting/raw_gold_bricks_revert',
        id: 'kubejs:ore_policy/raw_gold_bricks_to_gold_concentrate',
        output: '9x realistic_ores:gold_concentrate',
        ingredients: ['quark:raw_gold_bricks']
    },
    {
        source: 'goety:psgold',
        id: 'kubejs:ore_policy/philosophers_stone_gold_concentrate',
        output: '2x realistic_ores:gold_concentrate',
        ingredients: ['#forge:raw_materials/gold', 'goety:philosophers_stone']
    }
]

ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('realistic_ores')) return

    for (var i = 0; i < BC_RAW_METAL_RECIPE_ROUTES.length; i++) {
        var route = BC_RAW_METAL_RECIPE_ROUTES[i]
        event.remove({ id: route.source })
        event.shapeless(route.output, route.ingredients).id(route.id)
    }
})
