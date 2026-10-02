var BC_VANILLA_TOOL_TIERS = ['wooden', 'stone', 'iron', 'golden', 'diamond', 'netherite']
var BC_VANILLA_TOOL_KINDS = ['pickaxe', 'axe', 'shovel', 'sword', 'hoe']

var BC_VANILLA_STYLE_TOOL_FAMILIES = [
    ['ae2', ['certus_quartz', 'fluix', 'nether_quartz']],
    ['aether', ['gravitite', 'holystone', 'skyroot', 'zanite']],
    ['everythingcopper', ['copper']],
    ['goety', ['dark']],
    ['malum', ['soul_stained_steel']],
    ['iceandfire', ['copper', 'silver', 'dragonbone', 'myrmex_desert', 'myrmex_jungle',
        'dragonsteel_fire', 'dragonsteel_ice', 'dragonsteel_lightning']]
]

var BC_VANILLA_STYLE_TOOL_EXTRAS = [
    'ars_nouveau:enchanters_sword',
    'create:cardboard_sword',
    'farmersdelight:diamond_knife',
    'farmersdelight:flint_knife',
    'farmersdelight:golden_knife',
    'farmersdelight:iron_knife',
    'farmersdelight:netherite_knife',
    'goety:diamond_ice_axe',
    'goety:eerie_pickaxe',
    'goety:graverobber_shovel',
    'goety:iron_ice_axe',
    'goety:rampaging_axe',
    'notreepunching:diamond_knife',
    'notreepunching:diamond_mattock',
    'notreepunching:diamond_saw',
    'notreepunching:flint_axe',
    'notreepunching:flint_hoe',
    'notreepunching:flint_knife',
    'notreepunching:flint_pickaxe',
    'notreepunching:flint_shovel',
    'notreepunching:gold_knife',
    'notreepunching:gold_mattock',
    'notreepunching:gold_saw',
    'notreepunching:iron_knife',
    'notreepunching:iron_mattock',
    'notreepunching:iron_saw',
    'notreepunching:netherite_knife',
    'notreepunching:netherite_mattock',
    'notreepunching:netherite_saw',
    'occultism:iesnium_pickaxe',
    'occultism:infused_pickaxe',
    'the_flesh_that_hates:flesh_axe',
    'the_flesh_that_hates:flesh_sword',
    'twilightforest:fiery_pickaxe',
    'twilightforest:fiery_sword',
    'twilightforest:giant_pickaxe',
    'twilightforest:giant_sword',
    'twilightforest:ironwood_axe',
    'twilightforest:ironwood_hoe',
    'twilightforest:ironwood_pickaxe',
    'twilightforest:ironwood_shovel',
    'twilightforest:ironwood_sword',
    'twilightforest:knightmetal_axe',
    'twilightforest:knightmetal_pickaxe',
    'twilightforest:knightmetal_sword',
    'twilightforest:steeleaf_axe',
    'twilightforest:steeleaf_hoe',
    'twilightforest:steeleaf_pickaxe',
    'twilightforest:steeleaf_shovel',
    'twilightforest:steeleaf_sword'
]

function vanillaToolIds() {
    var tools = []
    for (var ti = 0; ti < BC_VANILLA_TOOL_TIERS.length; ti++) {
        for (var ki = 0; ki < BC_VANILLA_TOOL_KINDS.length; ki++) {
            tools.push('minecraft:' + BC_VANILLA_TOOL_TIERS[ti] + '_' + BC_VANILLA_TOOL_KINDS[ki])
        }
    }

    for (var fi = 0; fi < BC_VANILLA_STYLE_TOOL_FAMILIES.length; fi++) {
        var family = BC_VANILLA_STYLE_TOOL_FAMILIES[fi]
        for (var mi = 0; mi < family[1].length; mi++) {
            for (var fki = 0; fki < BC_VANILLA_TOOL_KINDS.length; fki++) {
                tools.push(family[0] + ':' + family[1][mi] + '_' + BC_VANILLA_TOOL_KINDS[fki])
            }
        }
    }

    for (var ei = 0; ei < BC_VANILLA_STYLE_TOOL_EXTRAS.length; ei++) tools.push(BC_VANILLA_STYLE_TOOL_EXTRAS[ei])

    return tools
}

JEIEvents.hideItems(function (event) {
    vanillaToolIds().forEach(function (tool) {
        event.hide(tool)
    })
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        vanillaToolIds().forEach(function (tool) {
            event.hide(tool)
        })
    })
}
