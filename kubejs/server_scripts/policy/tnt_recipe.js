// TNT uses the exact ordinary-sand catalogue shared by generic recipes.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'minecraft:tnt' })
    event.shaped('minecraft:tnt', ['GSG', 'SGS', 'GSG'], {
        G: 'minecraft:gunpowder',
        S: '#kubejs:ordinary_sand'
    }).id('minecraft:tnt')
})
