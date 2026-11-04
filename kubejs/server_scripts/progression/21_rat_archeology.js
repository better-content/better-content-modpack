// The Rats archaeologist upgrade must not require a forbidden travel token or
// craft-only vanilla fixed tools. Native archeologist exchanges stay unchanged.
// Upstream registers the hat but supplies no recipe, loot table or exchange
// for obtaining it; make the first upgrade possible without itself.
ServerEvents.recipes(function (event) {
    event.shaped('rats:archeologist_hat', ['L L', 'LBG', 'LLL'], {
        L: 'minecraft:leather',
        B: 'minecraft:brush',
        G: 'minecraft:amethyst_shard'
    }).id('kubejs:rat_archeology/archeologist_hat')

    event.remove({ id: 'rats:upgrades/archeologist_upgrade' })
    event.shaped('rats:rat_upgrade_archeologist', ['MHM', 'BUB', 'PGS'], {
        M: 'rats:cheese',
        H: 'rats:archeologist_hat',
        B: '#forge:bones',
        U: 'rats:rat_upgrade_basic',
        P: 'minecraft:iron_ingot',
        G: 'minecraft:amethyst_shard',
        S: '#forge:plates/iron'
    }).id('kubejs:rat_archeology/archeologist_upgrade')
})
