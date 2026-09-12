// Global loot progression scrub. Loot is a crafting surface, so high-power materials
// and creative/flight/global-bypass items must not enter through random tables.

var BC_LOOT_REMOVE_ITEMS = [
    // Fishing acquisition is standardized on Starcatcher rods.
    'minecraft:fishing_rod',
    'tconstruct:fishing_rod',
    'rats:chunky_cheese_token',
    'minecraft:dragon_head',
    'minecraft:dragon_egg',
    'minecraft:dragon_breath',
    // Generated from registry: all creative and netherite-named items are removed from generic loot.
    'ae2:creative_energy_cell',
    'ae2:creative_fluid_cell',
    'ae2:creative_item_cell',
    'aether:netherite_gloves',
    'ars_nouveau:creative_source_jar',
    'ars_nouveau:creative_spell_book',
    'arseng:creative_source_cell',
    'art_update:netherite_multitool',
    'bloodmagic:activationcrystalcreative',
    'bloodmagic:fragment_netherite_scrap',
    'bloodmagic:gravel_netherite_scrap',
    'bloodmagic:sand_netherite',
    'create:creative_blaze_cake',
    'create:creative_crate',
    'create:creative_fluid_tank',
    'create:creative_motor',
    'create:netherite_backtank',
    'create:netherite_backtank_placeable',
    'create:netherite_diving_boots',
    'create:netherite_diving_helmet',
    'create_central_kitchen:creative_tab_icon',
    'create_connected:creative_fluid_vessel',
    'create_things_and_misc:netherite_portable_whistle',
    'createmoredrillheads:amethyst_dusts_tipped_netherite_drill',
    'createmoredrillheads:emerald_dusts_tipped_netherite_drill',
    'createmoredrillheads:netherite_drill',
    'createmoredrillheads:quartz_dusts_tipped_netherite_drill',
    'createmoredrillheads:redstone_dusts_tipped_netherite_drill',
    'creatingspace:netherite_oxygen_backtank',
    'creatingspace:netherite_oxygen_backtank_placeable',
    'farmersdelight:netherite_knife',
    'goety:netherite_ravager_armor',
    'goety:netherite_trampler_armor',
    'hexerei:broom_netherite_tip',
    'hexerei:creative_waxing_kit',
    'littlelogistics:creative_capacitor',
    'malum:creative_scythe',
    'minecraft:elytra',
    'minecraft:netherite_axe',
    'minecraft:netherite_block',
    'minecraft:netherite_boots',
    'minecraft:netherite_chestplate',
    'minecraft:netherite_helmet',
    'minecraft:netherite_hoe',
    'minecraft:netherite_ingot',
    'minecraft:netherite_leggings',
    'minecraft:netherite_pickaxe',
    'minecraft:netherite_scrap',
    'minecraft:netherite_shovel',
    'minecraft:netherite_sword',
    'minecraft:netherite_upgrade_smithing_template',
    'naturesaura:netherite_finder',
    'oc2r:creative_energy',
    'powergrid:creative_current_source',
    'powergrid:creative_resistor',
    'powergrid:creative_voltage_source',
    'protection_pixel:smallnetheritesheet',
    'sophisticatedbackpacks:netherite_backpack',
    'sophisticatedstorage:basic_to_netherite_tier_upgrade',
    'sophisticatedstorage:copper_to_netherite_tier_upgrade',
    'sophisticatedstorage:diamond_to_netherite_tier_upgrade',
    'sophisticatedstorage:gold_to_netherite_tier_upgrade',
    'sophisticatedstorage:iron_to_netherite_tier_upgrade',
    'sophisticatedstorage:limited_netherite_barrel_1',
    'sophisticatedstorage:limited_netherite_barrel_2',
    'sophisticatedstorage:limited_netherite_barrel_3',
    'sophisticatedstorage:limited_netherite_barrel_4',
    'sophisticatedstorage:netherite_barrel',
    'sophisticatedstorage:netherite_chest',
    'sophisticatedstorage:netherite_shulker_box',
    'tconstruct:creative_slot',
    'tconstruct:molten_netherite_bucket',
    'tconstruct:netherite_item_frame',
    'tconstruct:netherite_nugget',
    'theoneprobe:creativeprobe',
    'tinkers_advanced:blaze_netherite',
    'tinkers_advanced:molten_blaze_netherite_bucket',
    'ubesdelight:rolling_pin_netherite',
]

// Renewable crop seeds do not belong on scarce exploration chest rolls. This is
// deliberately chest-only: crop harvesting and Dynamic Trees' own block drops
// remain their authoritative renewable acquisition routes.
var BC_CHEST_LOOT_REMOVE_SEEDS = [
    'farmersdelight:cabbage_seeds',
    'farmersdelight:tomato_seeds',
    'hexerei:sage_seed',
    'minecraft:beetroot_seeds',
    'minecraft:melon_seeds',
    'minecraft:pumpkin_seeds',
    'minecraft:wheat_seeds',
    'ubesdelight:lemongrass_seeds'
]

// Family cuts apply to loot as well as recipes; do not maintain a second list.
var BC_LOOT_QUARANTINE = JsonIO.read('kubejs/config/quarantined_items.json') || { items: [] }
BC_LOOT_REMOVE_ITEMS = BC_LOOT_REMOVE_ITEMS.concat(BC_LOOT_QUARANTINE.items || [])

// Enforce the central family contract on the loot surface. Exact selectors and
// namespace/prefix selectors are both resolved from the live item registry, so
// adding a governed family cannot silently leave chest or entity-drop access.
var BC_LOOT_POLICY = JsonIO.read('kubejs/config/crafting_policy.json') || { families: [] }
var BC_LOOT_ITEM_REGISTRY = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries').ITEM
var BC_LOOT_POTION_UTILS = Java.loadClass('net.minecraft.world.item.alchemy.PotionUtils')
var BC_LOOT_POTIONS = Java.loadClass('net.minecraft.world.item.alchemy.Potions')
var BC_LOOT_ITEMS = Java.loadClass('net.minecraft.world.item.Items')

function bcIsDisabledPotionDelivery(stack) {
    if (stack.is(BC_LOOT_ITEMS.SPLASH_POTION)
        || stack.is(BC_LOOT_ITEMS.LINGERING_POTION)
        || stack.is(BC_LOOT_ITEMS.TIPPED_ARROW)) return true
    return stack.is(BC_LOOT_ITEMS.POTION)
        && (!BC_LOOT_POTION_UTILS.getPotion(stack).equals(BC_LOOT_POTIONS.WATER)
            || !BC_LOOT_POTION_UTILS.getCustomEffects(stack).isEmpty())
}

function bcLootSelectorMatches(id, selector) {
    if (selector.exact_id) return id === selector.exact_id
    var split = id.indexOf(':')
    var namespace = split < 0 ? '' : id.substring(0, split)
    var path = split < 0 ? id : id.substring(split + 1)
    if (selector.namespace && namespace !== selector.namespace) return false
    if (selector.id_prefix && path.indexOf(selector.id_prefix) !== 0) return false
    return !!(selector.namespace || selector.id_prefix)
}

var bcLootRegistryIds = []
var bcLootRegistryIterator = BC_LOOT_ITEM_REGISTRY.keySet().iterator()
while (bcLootRegistryIterator.hasNext()) bcLootRegistryIds.push('' + bcLootRegistryIterator.next())

;(BC_LOOT_POLICY.families || []).forEach(function (family) {
    if (family.disposition !== 'cut' && family.disposition !== 'authority' && family.disposition !== 'canonical_duplicate') return
    ;(family.selectors || []).forEach(function (selector) {
        if (selector.tag) return
        bcLootRegistryIds.forEach(function (id) {
            if (bcLootSelectorMatches(id, selector) && BC_LOOT_REMOVE_ITEMS.indexOf(id) < 0) BC_LOOT_REMOVE_ITEMS.push(id)
        })
    })
})

function bcLootItemExists(id) {
    try { return Item.exists(id) } catch (e) { return false }
}

LootJS.modifiers(function (event) {
    // minecraft:empty is a protected built-in table and cannot be redefined.
    var allLoot = event.addLootTableModifier(/^(?!minecraft:empty$).*$/)
    for (var i = 0; i < BC_LOOT_REMOVE_ITEMS.length; i++) {
        if (bcLootItemExists(BC_LOOT_REMOVE_ITEMS[i])) allLoot.removeLoot(BC_LOOT_REMOVE_ITEMS[i])
    }
    allLoot.apply(function (context) {
        context.getLoot().removeIf(function (stack) { return bcIsDisabledPotionDelivery(stack) })
    })

    var chestLoot = event.addLootTableModifier(/.*:chests\/.*/)
    for (var j = 0; j < BC_CHEST_LOOT_REMOVE_SEEDS.length; j++) {
        if (bcLootItemExists(BC_CHEST_LOOT_REMOVE_SEEDS[j])) chestLoot.removeLoot(BC_CHEST_LOOT_REMOVE_SEEDS[j])
    }
})
