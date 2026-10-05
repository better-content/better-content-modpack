// Conduit channeling is the renewable marine workshop. It does not replace
// geological copper/lapis recovery or the pack's Spout-only water treatment.
ServerEvents.recipes(function (event) {
    [
        'create_aquatic_ambitions:crushing/prismarine_bricks_to_lapis_and_copper',
        'create_aquatic_ambitions:crushing/prismarine_to_lapis',
        'create_aquatic_ambitions:splashing/suspicious_rock',
        'create_aquatic_ambitions:channeling/heart_of_the_sea'
    ].forEach(function (id) { event.remove({ id: id }) })

    // The Conduit Cage is the marine workshop's machinery gate. Keep the
    // native Conduit, alloy, rods, and fluid pipe visible in the craft.
    event.remove({ id: 'create:crafting/materials/mechanical_conduit' })
    event.shaped('create_aquatic_ambitions:mechanical_conduit',
        ['IMI', 'IAI', 'PUP'], {
            I: 'create_aquatic_ambitions:prismarine_alloy_rod',
            M: 'minecraft:blaze_rod',
            A: 'minecraft:conduit',
            P: 'create_aquatic_ambitions:prismarine_alloy',
            U: 'create:fluid_pipe'
        }).id('kubejs:underwater_works/mechanical_conduit')
})
