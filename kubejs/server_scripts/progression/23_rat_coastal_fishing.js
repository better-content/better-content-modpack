// The Rats Ratlantis fishing table is dimension-scoped upstream. Put its
// native Rats rewards in the selected accessible Overworld coastal waters.
// Vanilla fishing staples stay in their own tables; keep additions rare.
LootJS.modifiers(function (event) {
    var nativeRatsCatches = [
        ['rats:ratfish', 0.018],
        ['rats:rat_toga', 0.002],
        ['rats:rat_pelt', 0.002],
        ['rats:raw_rat', 0.002],
        ['rats:plastic_waste', 0.002],
        ['rats:feral_rat_claw', 0.001],
        ['rats:filth', 0.001],
        ['rats:treacle', 0.001],
        ['rats:oratchalcum_nugget', 0.0008],
        ['rats:gem_of_ratlantis', 0.0008],
        ['rats:ghost_pirat_ectoplasm', 0.001],
        ['rats:tangled_rat_tails', 0.001],
        ['rats:plague_essence', 0.0008],
        ['rats:plague_tome', 0.0003]
    ]
    nativeRatsCatches.forEach(function (entry) {
        event.addLootTableModifier('minecraft:gameplay/fishing')
            .anyDimension('minecraft:overworld')
            .anyBiome('minecraft:warm_ocean', 'minecraft:lukewarm_ocean')
            .randomChance(entry[1])
            .addLoot(entry[0])
    })
})
