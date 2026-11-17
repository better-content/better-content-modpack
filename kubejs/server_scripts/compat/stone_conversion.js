// One-way ordinary stone conversions. The explicit roster is shared with
// retained/refactor__compatability__45_stone_surface_tool_compat.js and
// retained/check__30_stone_cobble_compat.js. Broad stone tags also contain
// decorated blocks, regolith and ore variants; deliberately do not use them.
ServerEvents.recipes(function (event) {
    var unearthedStones = [
        'beige_limestone', 'conglomerate', 'dolomite', 'gabbro',
        'granodiorite', 'grey_limestone', 'kimberlite', 'limestone',
        'mudstone', 'phyllite', 'quartzite', 'rhyolite', 'sandstone',
        'siltstone', 'slate', 'stone', 'white_granite'
    ]
    var stoneInputs = unearthedStones.map(function (stone) { return 'unearthed:' + stone })
    stoneInputs.push('natures_spirit:chalk', 'natures_spirit:kaolin',
        'natures_spirit:travertine', 'natures_spirit:chert')
    var cobbleInputs = [
        'unearthed:cobbled_beige_limestone', 'unearthed:cobbled_grey_limestone',
        'unearthed:cobbled_limestone', 'unearthed:cobbled_phyllite',
        'unearthed:cobbled_slate', 'natures_spirit:cobbled_travertine'
    ]
    function convert(inputs, result, kind) {
        inputs.forEach(function (input) {
            if (!Item.exists(input) || input === result) return
            // One source block becomes one vanilla block; no reverse conversions.
            event.shapeless(result, [input]).id('kubejs:stone_conversion/' + kind + '/' + input.replace(':', '/'))
        })
    }
    convert(stoneInputs, 'minecraft:stone', 'stone')
    convert(cobbleInputs, 'minecraft:cobblestone', 'cobblestone')
})
