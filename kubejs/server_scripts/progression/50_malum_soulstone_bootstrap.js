// The first Soulstone comes from ordinary Black Shale prepared in Hexerei.
// Native Malum smelting then turns raw Soulstone into processed Soulstone.
// This route is available before the first Spirit Altar and does not replace
// Malum's other Soulstone processing recipes.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('malum') || !Platform.isLoaded('hexerei') || !Platform.isLoaded('realistic_ores')) return

    event.custom({
        type: 'hexerei:mixingcauldron',
        liquid: { fluid: 'minecraft:water' },
        ingredients: [
            { item: 'realistic_ores:black_shale' },
            { item: 'realistic_ores:black_shale' },
            { item: 'realistic_ores:black_shale' },
            { item: 'realistic_ores:black_shale' },
            { item: 'minecraft:soul_sand' },
            { item: 'minecraft:bone_meal' },
            { item: 'minecraft:amethyst_shard' },
            { item: 'minecraft:charcoal' }
        ],
        output: { item: 'malum:raw_soulstone' },
        liquidOutput: { fluid: 'minecraft:water' },
        fluidLevelsConsumed: 250,
        heatRequirement: 'heated'
    }).id('kubejs:malum/black_shale_to_raw_soulstone')
})

// MALUM-07: a process substrate route for Brilliance. Compared with the native
// Brilliant Stone route, crushed Soulstone plus Malum Hex Ash keeps Brilliance
// inside Malum's material line while heated Create mixing supplies the pulse.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('malum') || !Platform.isLoaded('create')) return

    event.custom({
        type: 'create:mixing',
        heatRequirement: 'heated',
        ingredients: [
            { item: 'malum:crushed_soulstone' },
            { item: 'malum:crushed_soulstone' },
            { item: 'malum:hex_ash' }
        ],
        results: [{ item: 'malum:crushed_brilliance', count: 1 }],
        processingTime: 240
    }).id('kubejs:malum/brilliance/from_crushed_soulstone')
})
