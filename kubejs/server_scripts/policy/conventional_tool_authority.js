// Tinkers' Construct owns conventional pick/axe/shovel/hoe/sword capability.
// This explicit family policy closes native recipes without registry-wide
// heuristics, while retaining unique weapons, bows, armor, and creature gear.
var BC_CONVENTIONAL_KINDS = ['pickaxe', 'axe', 'shovel', 'hoe', 'sword']
var BC_CONVENTIONAL_FAMILIES = [
    ['minecraft', ['wooden', 'stone', 'iron', 'golden', 'diamond', 'netherite']],
    ['ae2', ['certus_quartz', 'fluix', 'nether_quartz']],
    ['aether', ['gravitite', 'holystone', 'skyroot', 'zanite']],
    ['everythingcopper', ['copper']],
    ['goety', ['dark']],
    ['malum', ['soul_stained_steel']],
    ['iceandfire', [
        'copper', 'silver', 'dragonbone', 'myrmex_desert', 'myrmex_jungle',
        'dragonsteel_fire', 'dragonsteel_ice', 'dragonsteel_lightning'
    ]]
]

var BC_CONVENTIONAL_EXACT = [
    'ars_nouveau:enchanters_sword', 'create:cardboard_sword',
    'farmersdelight:diamond_knife', 'farmersdelight:flint_knife',
    'farmersdelight:golden_knife', 'farmersdelight:iron_knife',
    'farmersdelight:netherite_knife', 'goety:diamond_ice_axe',
    'goety:eerie_pickaxe', 'goety:graverobber_shovel',
    'goety:iron_ice_axe', 'goety:rampaging_axe',
    'notreepunching:diamond_knife', 'notreepunching:diamond_mattock',
    'notreepunching:diamond_saw', 'notreepunching:flint_axe',
    'notreepunching:flint_hoe', 'notreepunching:flint_knife',
    'notreepunching:flint_pickaxe', 'notreepunching:flint_shovel',
    'notreepunching:gold_knife', 'notreepunching:gold_mattock',
    'notreepunching:gold_saw', 'notreepunching:iron_knife',
    'notreepunching:iron_mattock', 'notreepunching:iron_saw',
    'notreepunching:netherite_knife', 'notreepunching:netherite_mattock',
    'notreepunching:netherite_saw', 'occultism:iesnium_pickaxe',
    'occultism:infused_pickaxe', 'the_flesh_that_hates:flesh_axe',
    'the_flesh_that_hates:flesh_sword', 'twilightforest:fiery_pickaxe',
    'twilightforest:fiery_sword', 'twilightforest:giant_pickaxe',
    'twilightforest:giant_sword', 'twilightforest:ironwood_axe',
    'twilightforest:ironwood_hoe', 'twilightforest:ironwood_pickaxe',
    'twilightforest:ironwood_shovel', 'twilightforest:ironwood_sword',
    'twilightforest:knightmetal_axe', 'twilightforest:knightmetal_pickaxe',
    'twilightforest:knightmetal_sword', 'twilightforest:steeleaf_axe',
    'twilightforest:steeleaf_hoe', 'twilightforest:steeleaf_pickaxe',
    'twilightforest:steeleaf_shovel', 'twilightforest:steeleaf_sword'
]

function bcConventionalToolIds() {
    var ids = BC_CONVENTIONAL_EXACT.slice()
    BC_CONVENTIONAL_FAMILIES.forEach(function (family) {
        family[1].forEach(function (material) {
            BC_CONVENTIONAL_KINDS.forEach(function (kind) {
                ids.push(family[0] + ':' + material + '_' + kind)
            })
        })
    })
    return ids
}

ServerEvents.recipes(function (event) {
    bcConventionalToolIds().forEach(function (id) {
        if (Item.exists(id)) event.remove({ output: id })
    })
})
