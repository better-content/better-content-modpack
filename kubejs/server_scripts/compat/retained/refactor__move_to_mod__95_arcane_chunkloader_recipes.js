// Each anchor accepts proof from any current magic discipline, then specializes by power infrastructure.
ServerEvents.recipes(function (event) {
    var variants = [
        ['flux_chunk_anchor', 'powergrid:battery'],
        ['kinetic_chunk_anchor', 'create:shaft'],
        ['source_chunk_anchor', 'ars_nouveau:source_jar'],
        ['lifeforce_chunk_anchor', 'bloodmagic:life_essence_bucket'],
        ['pressure_chunk_anchor', 'pneumaticcraft:pressure_tube'],
        ['soul_chunk_anchor', 'goety:totem_of_souls'],
        ['spirit_chunk_anchor', 'malum:spirit_jar']
    ]

    variants.forEach(function (entry) {
        event.shaped('better_magic_chunk_anchors:' + entry[0], [
            'OEO',
            'EPE',
            'OCO'
        ], {
            O: 'minecraft:crying_obsidian',
            E: 'minecraft:ender_pearl',
            P: entry[1],
            C: '#better_magic_chunk_anchors:magic_catalysts'
        }).id('better_magic_chunk_anchors:' + entry[0])
    })
})
