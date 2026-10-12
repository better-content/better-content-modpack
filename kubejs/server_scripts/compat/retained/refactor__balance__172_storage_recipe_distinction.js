// STORE-02: keep wood storage at the verified copper-tier capacity while
// giving barrels and chests distinct, visible construction costs. Copper
// storage remains disabled by sophisticatedcore-common.toml.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('sophisticatedstorage')) return

    // Native oak conversion otherwise remains a second chest + lever outcome.
    event.remove({ id: 'sophisticatedstorage:oak_chest_from_vanilla_chest' })

    var woods = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped']
    // The authored data override retains GenericWoodStorageRecipe's native
    // mixed/non-custom-wood predicate and now charges eight planks + a lever.
    // It consumes no stateful barrel; the vanilla-barrel bypass stays removed.

    woods.forEach(function (wood) {
        var barrelId = 'sophisticatedstorage:' + wood + '_barrel'
        var chestId = 'sophisticatedstorage:' + wood + '_chest'
        event.remove({ id: barrelId })
        event.remove({ id: chestId })

        event.shaped(Item.of('sophisticatedstorage:barrel', '{woodType:"' + wood + '"}'), ['PPP', 'PLP', 'PPP'], {
            P: 'minecraft:' + wood + '_planks', L: 'minecraft:lever'
        }).id('kubejs:storage/' + wood + '_barrel_plank_ring')

        // The ordinary-chest input and existing result semantics are unchanged.
        // One exact-family plank distinguishes every authored wood outcome.
        event.shaped(Item.of('sophisticatedstorage:chest', '{woodType:"' + wood + '"}'), ['P', 'C', 'L'], {
            P: 'minecraft:' + wood + '_planks', C: 'minecraft:chest', L: 'minecraft:lever'
        }).id('kubejs:storage/' + wood + '_chest_container')
    })
})
