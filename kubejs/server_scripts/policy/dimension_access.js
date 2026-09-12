// Direct dimension portals are not pack routes. Player dimension travel should
// come from Dimensional Font routes or Creating Space rocket graph entries only.

var BC_DIRECT_DIMENSION_ROUTE_ITEMS = [
    'fallout_wastelands_:portal_frame',
    'fallout_wastelands_:wastelands',
    'rats:chunky_cheese_token',
    'bloodmagic:simplekey',
    'bloodmagic:minekey',
    'bloodmagic:mineentrancekey',
    'bloodmagic:teleposer',
    'bloodmagic:telepositionsigil',
    'bloodmagic:reagentteleposition',
    'bloodmagic:teleposerfocus',
    'bloodmagic:reinforcedteleposerfocus',
    'bloodmagic:enhancedteleposerfocus',
    'aether:aether_portal_frame',
    'rats:ratlantis_portal'
]

ServerEvents.recipes(function (event) {
    for (var i = 0; i < BC_DIRECT_DIMENSION_ROUTE_ITEMS.length; i++) {
        var item = BC_DIRECT_DIMENSION_ROUTE_ITEMS[i]
        if (Item.exists(item)) event.remove({ output: item })
    }

    event.remove({ id: 'fallout_wastelands_:portalframecraft' })
    event.remove({ id: 'fallout_wastelands_:portalignitercraft' })
    event.remove({ id: 'rats:chunky_cheese_token' })
    event.remove({ output: 'rats:chunky_cheese_token' })
    event.remove({ id: 'bloodmagic:soulforge/simple_key' })
    event.remove({ id: 'bloodmagic:soulforge/mine_key' })

    console.info('[space-dimension-access] disabled direct portal/key recipe outputs; Bumblezone and Ratlantis are Font-only')
})

// Eyes remain a finite ritual ingredient, but cannot open the vanilla End.
BlockEvents.rightClicked('minecraft:end_portal_frame', function (event) {
    if (event.item && event.item.id === 'minecraft:ender_eye') {
        event.player.tell('The End portal is disabled. Use Dimensional Fonts to visit other dimensions.')
        event.cancel()
    }
})
