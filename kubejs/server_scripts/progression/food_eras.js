// Foraged fruit, finite wild vegetables/rice and animal foods feed exploration.
// Renewable garden crops require Bumblezone seeds; the cooking pot joins more groups.
ServerEvents.recipes(function (event) {
    event.shapeless('farmersdelight:fruit_salad', [
        'minecraft:bowl', 'fruitsdelight:blueberry', 'minecraft:apple', 'minecraft:honey_bottle'
    ]).id('kubejs:food_eras/foragers_fruit_salad')

    event.custom({
        type: 'farmersdelight:cooking', container: { item: 'minecraft:bowl' },
        cookingtime: 200, experience: 0.1,
        ingredients: [
            { item: 'minecraft:chicken' }, { item: 'fruitsdelight:fig' },
            { item: 'fruitsdelight:fig' }, { item: 'minecraft:milk_bucket' }
        ],
        result: { item: 'fruitsdelight:fig_chicken_stew' }
    }).id('kubejs:food_eras/creamy_fig_chicken')

    // The distinct six-ingredient market feast is installed exactly once by
    // 84_cooking_recipes.js as a fixed KC wok recipe with a paid bowl.
})
