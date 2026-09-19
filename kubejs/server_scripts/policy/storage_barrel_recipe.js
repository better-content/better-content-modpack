ServerEvents.recipes(event => {
    // The native barrel + lever conversion bypasses the authored plank ring.
    event.remove({ id: 'sophisticatedstorage:spruce_barrel_from_vanilla_barrel' })
})
