function addAllToTargets(event, targets, entries) {
    for (var ti = 0; ti < targets.length; ti++) {
        for (var ei = 0; ei < entries.length; ei++) {
            event.add(targets[ti], entries[ei])
        }
    }
}

var stoneLikeEntries = [
    '#unearthed:igneous',
    '#unearthed:metamorphic',
    '#unearthed:sedimentary',
    '#natures_spirit:chalk',
    '#natures_spirit:kaolin',
    '#natures_spirit:kaolin_bricks',
    'natures_spirit:travertine',
    'natures_spirit:chert',
]

var cobbleLikeEntries = [
    'unearthed:cobbled_beige_limestone',
    'unearthed:cobbled_grey_limestone',
    'unearthed:cobbled_limestone',
    'unearthed:cobbled_phyllite',
    'unearthed:cobbled_slate',
    'unearthed:mossy_cobbled_phyllite',
    'unearthed:mossy_cobbled_slate',
    'natures_spirit:cobbled_travertine',
    'natures_spirit:mossy_cobbled_travertine',
]

ServerEvents.tags('item', function (event) {
    var stoneTargets = [
        'forge:stone',
        'c:stone'
    ]

    var cobbleTargets = [
        'forge:cobblestone',
        'c:cobblestone'
    ]

    var normalStoneTargets = [
        'forge:normal_stone',
        'minecraft:stone_crafting_materials',
        'minecraft:stone_tool_materials',
        'tconstruct:workstation_rock'
    ]

    var mossyCobbleTargets = [
        'forge:cobblestone/mossy'
    ]

    addAllToTargets(event, stoneTargets, stoneLikeEntries)
    addAllToTargets(event, cobbleTargets, cobbleLikeEntries)
    event.add('kubejs:furnace_materials', '#forge:stone')
    event.add('kubejs:furnace_materials', '#forge:cobblestone')
    addAllToTargets(event, normalStoneTargets, stoneLikeEntries)
    addAllToTargets(event, normalStoneTargets, cobbleLikeEntries)
    addAllToTargets(event, mossyCobbleTargets, [
        'unearthed:mossy_cobbled_phyllite',
        'unearthed:mossy_cobbled_slate',
        'natures_spirit:mossy_cobbled_travertine'
    ])
})

ServerEvents.tags('block', function (event) {
    var stoneTargets = [
        'forge:stone',
        'c:stone'
    ]

    var cobbleTargets = [
        'forge:cobblestone',
        'c:cobblestone'
    ]

    var mossyCobbleTargets = [
        'forge:cobblestone/mossy'
    ]

    addAllToTargets(event, stoneTargets, stoneLikeEntries)
    addAllToTargets(event, cobbleTargets, cobbleLikeEntries)
    addAllToTargets(event, ['forge:normal_stone'], stoneLikeEntries)
    addAllToTargets(event, ['forge:normal_stone'], cobbleLikeEntries)
    addAllToTargets(event, mossyCobbleTargets, [
        'unearthed:mossy_cobbled_phyllite',
        'unearthed:mossy_cobbled_slate',
        'natures_spirit:mossy_cobbled_travertine'
    ])
})
