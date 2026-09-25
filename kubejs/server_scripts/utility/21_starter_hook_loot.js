// ReHooked's six introductory prototypes are loot-only; stronger native tiers
// retain their authored mechanical-assembly routes.
LootJS.modifiers(event => {
    if (!Platform.isLoaded('rehooked') || !Platform.isLoaded('rehooked_intro_hooks')) return

    const earlyTables = {
        'minecraft:chests/abandoned_mineshaft': 0.07,
        'minecraft:chests/simple_dungeon': 0.06,
        'minecraft:chests/shipwreck_supply': 0.06,
        'minecraft:chests/ruined_portal': 0.05,
        'minecraft:chests/village/village_toolsmith': 0.06,
        'minecraft:chests/village/village_taiga_house': 0.05
    }
    const placement = {
        soap_on_a_rope: Object.keys(earlyTables),
        block_and_tackle: [
            'minecraft:chests/abandoned_mineshaft',
            'minecraft:chests/simple_dungeon',
            'minecraft:chests/village/village_toolsmith'
        ],
        grapnel_bundle: [
            'minecraft:chests/abandoned_mineshaft',
            'minecraft:chests/shipwreck_supply',
            'minecraft:chests/village/village_taiga_house'
        ],
        climbing_vine: [
            'minecraft:chests/shipwreck_supply',
            'minecraft:chests/ruined_portal',
            'minecraft:chests/village/village_taiga_house'
        ],
        ratchet_reel: [
            'minecraft:chests/simple_dungeon',
            'minecraft:chests/ruined_portal',
            'minecraft:chests/village/village_toolsmith'
        ],
        anglers_gaff: [
            'minecraft:chests/shipwreck_supply',
            'minecraft:chests/village/village_toolsmith',
            'minecraft:chests/village/village_taiga_house'
        ]
    }

    Object.entries(placement).forEach(([name, tables]) => {
        const item = `better_content_fixes:${name}`
        if (!Item.exists(item)) return
        tables.forEach(table => {
            const chance = earlyTables[table]
            event.addLootTableModifier(table)
                .addLoot(Item.of(item).withCount(1))
                .randomChance(chance)
        })
    })
})
