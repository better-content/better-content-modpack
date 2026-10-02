ServerEvents.recipes(function (event) {
    event.replaceInput({}, 'minecraft:stone', '#forge:stone')
    event.replaceInput({}, 'minecraft:cobblestone', '#forge:cobblestone')
    event.shaped('minecraft:furnace', [
        'SSS',
        'S S',
        'SSS'
    ], {
        S: '#kubejs:furnace_materials'
    }).id('kubejs:crafting/furnace_from_stone_or_cobblestone')
})
