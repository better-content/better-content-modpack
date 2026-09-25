// Replace raw vanilla metal loot with the authored geological processing feed.
// This applies to block, structure, archaeology, entity and trade loot tables,
// so dimension rewards cannot bypass the same raw-metal identity policy.
var BC_RAW_METAL_LOOT_ROUTES = [
    ['minecraft:raw_iron', 'realistic_ores:small_ore_chunk_ironstone'],
    ['minecraft:raw_copper', 'realistic_ores:small_ore_chunk_copper_bloom'],
    ['minecraft:raw_gold', 'realistic_ores:gold_concentrate'],
    // Raw metal blocks already represent refined block value. Preserve their
    // stack count in the equivalent block instead of multiplying loot value.
    ['minecraft:raw_iron_block', 'minecraft:iron_block'],
    ['minecraft:raw_copper_block', 'minecraft:copper_block'],
    ['minecraft:raw_gold_block', 'minecraft:gold_block']
]

LootJS.modifiers(function (event) {
    if (!Platform.isLoaded('realistic_ores')) return

    var rawMetalLoot = event.addLootTableModifier(/^(?!minecraft:empty$).*$/)
    for (var i = 0; i < BC_RAW_METAL_LOOT_ROUTES.length; i++) {
        var route = BC_RAW_METAL_LOOT_ROUTES[i]
        rawMetalLoot.replaceLoot(route[0], route[1], true)
    }
})
