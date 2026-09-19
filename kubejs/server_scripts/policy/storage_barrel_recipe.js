ServerEvents.recipes(event => {
    // The native barrel + lever conversion bypasses the authored plank ring.
    event.remove({ id: 'sophisticatedstorage:spruce_barrel_from_vanilla_barrel' })

    // Copper storage remains readable in old worlds, but cannot be newly acquired.
    // Keep copper-to-higher upgrades so existing containers can move forward.
    const retiredCopperRecipes = [
        'sophisticatedstorage:basic_to_copper_tier_upgrade',
        'sophisticatedstorage:copper_barrel',
        'sophisticatedstorage:copper_chest',
        'sophisticatedstorage:copper_shulker_box',
        'sophisticatedstorage:copper_shulker_from_copper_chest',
        'sophisticatedstorage:double_copper_chest',
        'sophisticatedstorage:limited_copper_barrel_1',
        'sophisticatedstorage:limited_copper_barrel_2',
        'sophisticatedstorage:limited_copper_barrel_3',
        'sophisticatedstorage:limited_copper_barrel_4',
        'sophisticatedbackpacks:copper_backpack'
    ]
    retiredCopperRecipes.forEach(id => event.remove({ id: id }))
})
