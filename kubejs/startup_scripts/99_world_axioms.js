// Physical intermediates only; owning a stack never activates an Axiom.
StartupEvents.registry('item', function (event) {
    event.create('lineage_refractory_mix').displayName('Lineage Refractory Mix')
        .texture('minecraft:item/clay_ball')
    event.create('brass_billet').displayName('Brass Billet (Two Ingots)')
        .texture('create:item/brass_ingot')
    event.create('amethyst_preparation').displayName('Amethyst Preparation')
        .texture('minecraft:item/amethyst_shard')
})
