// More Red embeds Jumbo Furnace as a mandatory dependency in 1.20.1, but the
// furnace itself is outside the supported pack graph. Remove its processing
// surface and restore More Red's pre-embedding red-alloy fallback.
ServerEvents.recipes(function (event) {
    event.remove({ type: 'jumbofurnace:jumbo_smelting' })

    event.shapeless('morered:red_alloy_ingot', [
        '#morered:red_alloyable_ingots',
        '#forge:dusts/redstone',
        '#forge:dusts/redstone',
        '#forge:dusts/redstone',
        '#forge:dusts/redstone'
    ]).id('kubejs:crafting/morered_red_alloy_ingot')
})
