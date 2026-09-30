// Found Beetles are an early rail-work exception to manufactured machine parts.
// The item marker is read once by RailBeetleItem and yields fixed, ordinary supplies.
LootJS.modifiers(event => {
    if (!Platform.isLoaded('better_rail_beetle')) return
    event.addLootTableModifier('minecraft:chests/abandoned_mineshaft')
        .addLoot(Item.of('better_rail_beetle:better_rail_beetle', '{RailBeetleStarterPackage:1b}'))
        .randomChance(0.08)
    event.addLootTableModifier('minecraft:chests/village/village_toolsmith')
        .addLoot(Item.of('better_rail_beetle:better_rail_beetle', '{RailBeetleStarterPackage:1b}'))
        .randomChance(0.04)
})
