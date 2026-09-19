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
