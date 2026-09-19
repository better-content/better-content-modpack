// Found Beetles are an early rail-work exception to manufactured machine parts.
// The item marker is read once by RailBeetleItem and yields fixed, ordinary supplies.
LootJS.modifiers(event => {
    if (!Platform.isLoaded('rail_beetle')) return
    event.addLootTableModifier('minecraft:chests/abandoned_mineshaft')
        .addLoot(Item.of('rail_beetle:rail_beetle', '{RailBeetleStarterPackage:1b}'))
        .randomChance(0.08)
    event.addLootTableModifier('minecraft:chests/village/village_toolsmith')
        .addLoot(Item.of('rail_beetle:rail_beetle', '{RailBeetleStarterPackage:1b}'))
        .randomChance(0.04)
})
