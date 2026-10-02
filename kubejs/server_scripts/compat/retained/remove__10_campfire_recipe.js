ServerEvents.recipes(function (event) {
    event.remove({ id: 'minecraft:campfire' })
    event.shaped('minecraft:campfire', [
        ' S ',
        'SCS',
        'LLL'
    ], {
        S: 'minecraft:stick',
        C: '#minecraft:coals',
        L: '#minecraft:logs'
    }).id('kubejs:crafting/campfire')
})
