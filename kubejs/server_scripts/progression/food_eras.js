// Foraged fruit and animal foods are the first complete meal. Rice and garden
// crops appear after Bumblezone seeds; the cooking pot then joins more groups.
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

    event.custom({
        type: 'farmersdelight:cooking', container: { item: 'minecraft:bowl' },
        cookingtime: 200, experience: 0.2,
        ingredients: [
            { item: 'fruitsdelight:pineapple' }, { tag: 'forge:grain/rice' },
            { item: 'farmersdelight:cabbage' }, { item: 'farmersdelight:tomato' },
            { item: 'minecraft:egg' }, { item: 'farmersdelight:beef_patty' }
        ],
        result: { item: 'fruitsdelight:pineapple_fried_rice' }
    }).id('kubejs:food_eras/market_fried_rice')
})
