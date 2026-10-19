// priority: -10000
// Run after the progression scrub, geological replacements and prototype additions.
// LootJS 2.13.1 invokes these actions at ForgeHooks.modifyLoot RETURN: native
// global-modifier injections have already contributed to the same loot list.
var BC_CHEST_NORMALIZATION = JsonIO.read('kubejs/config/chest_loot_normalization.json')
var BC_CHEST_EMPTY_TABLE = Java.loadClass('net.minecraft.world.level.storage.loot.LootTable').EMPTY

if (!BC_CHEST_NORMALIZATION || BC_CHEST_NORMALIZATION.schema !== 'bc.chest_loot_normalization.v1') {
    throw new Error('Missing or invalid chest loot normalization policy')
}

var BC_CHEST_TREASURE = new RegExp(BC_CHEST_NORMALIZATION.treasure_pattern)
var BC_CHEST_ENCOUNTER = new RegExp(BC_CHEST_NORMALIZATION.encounter_pattern)

function bcChestProfile(table) {
    // Only root structure tables, never inject/chests, block/entity drops,
    // archaeology, fishing, gifts, or Lootr's synthetic Elytra table.
    if (!/^[a-z0-9_.-]+:chests?\//.test(table)) return null
    var namespace = table.substring(0, table.indexOf(':'))
    if (BC_CHEST_NORMALIZATION.excluded_namespaces.indexOf(namespace) >= 0) return null
    var path = table.substring(table.indexOf(':') + 1)
    if (BC_CHEST_TREASURE.test(path)) return BC_CHEST_NORMALIZATION.profiles.treasure
    if (BC_CHEST_ENCOUNTER.test(path)) return BC_CHEST_NORMALIZATION.profiles.encounter
    return BC_CHEST_NORMALIZATION.profiles.supplies
}

function bcNormalizeChestLoot(context) {
    if (('' + context.getType()) !== 'CHEST') return
    var profile = bcChestProfile('' + context.getLootTableId())
    if (!profile) return
    // Do not conceal unresolved tables by manufacturing a successful-looking chest.
    if (context.getServer().getLootData().getLootTable(context.getLootTableId()).equals(BC_CHEST_EMPTY_TABLE)) return

    var loot = context.getLoot()
    var counts = {}
    var protectedLoot = []
    var bundles = BC_CHEST_NORMALIZATION.bundles
    for (var i = 0; i < loot.size(); i++) {
        var stack = loot.get(i)
        if (stack.isEmpty()) continue
        var id = '' + stack.id
        // Only an explicit ordinary-supply allowlist may be reduced. Equipment,
        // relics, maps, unique progression rewards and every tagged stack survive
        // byte-for-byte, including inventories, enchantments and water purity.
        if (bundles[id] && !stack.hasTag()) {
            counts[id] = (counts[id] || 0) + stack.getCount()
        } else {
            protectedLoot.push(stack)
        }
    }

    var ids = Object.keys(counts)
    var score = protectedLoot.length
    ids.forEach(function (id) {
        // At most two bundles of any ordinary commodity, even if many upstream
        // pools or injections selected it. Splitting a stack cannot evade this.
        counts[id] = Math.min(counts[id], 2 * bundles[id])
        score += Math.ceil(counts[id] / bundles[id])
    })

    // Tighten the upper tail by removing ordinary bundles, never special rewards.
    // Random selection avoids consistently sacrificing the last table entry.
    var random = context.getRandom()
    while (score > profile.max && ids.length > 0) {
        var index = random.nextInt(ids.length)
        var id = ids[index]
        var before = Math.ceil(counts[id] / bundles[id])
        counts[id] = Math.max(0, (before - 1) * bundles[id])
        score--
        if (counts[id] === 0) ids.splice(index, 1)
    }

    // Restore sparse/filtered rolls with finite, mundane expedition supplies,
    // not replacement tools, seeds, potions, currencies or gated components.
    var target = profile.min + random.nextInt(profile.max - profile.min + 1)
    var choices = BC_CHEST_NORMALIZATION.supplements.filter(function (id) {
        return (counts[id] || 0) < 2 * bundles[id]
    })
    while (score < target && choices.length > 0) {
        var index = random.nextInt(choices.length)
        var id = choices[index]
        var before = Math.ceil((counts[id] || 0) / bundles[id])
        counts[id] = (before + 1) * bundles[id]
        score++
        if (counts[id] === 2 * bundles[id]) choices.splice(index, 1)
    }

    loot.clear()
    protectedLoot.forEach(function (stack) { loot.add(stack) })
    Object.keys(counts).forEach(function (id) {
        if (counts[id] > 0) loot.add(Item.of(id, counts[id]))
    })
}

LootJS.modifiers(function (event) {
    event.addLootTableModifier(/^[a-z0-9_.-]+:chests?\//).apply(bcNormalizeChestLoot)
})
