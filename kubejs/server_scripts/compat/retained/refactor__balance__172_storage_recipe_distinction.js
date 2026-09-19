// STORE-02: keep wood storage at the verified copper-tier capacity while
// giving barrels and chests distinct, visible construction costs. Copper
// storage remains disabled by sophisticatedcore-common.toml.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('sophisticatedstorage')) return

    var woods = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped']
    woods.forEach(function (wood) {
        var barrelId = 'sophisticatedstorage:' + wood + '_barrel'
        var chestId = 'sophisticatedstorage:' + wood + '_chest'
        event.remove({ id: barrelId })
        event.remove({ id: chestId })

        event.shaped(Item.of('sophisticatedstorage:barrel', '{woodType:"' + wood + '"}'), ['PPP', 'PLP', 'PPP'], {
            P: 'minecraft:' + wood + '_planks', L: 'minecraft:lever'
        }).id('kubejs:storage/' + wood + '_barrel_plank_ring')

        event.shaped(Item.of('sophisticatedstorage:chest', '{woodType:"' + wood + '"}'), [' C ', ' L ', '   '], {
            C: 'minecraft:chest', L: 'minecraft:lever'
        }).id('kubejs:storage/' + wood + '_chest_container')
    })
})
