// Native Diet computes nutrition. These four ordinary items preserve cooked
// nutrient-family identity without per-stack provenance or a custom Diet engine.
var BC_FISH_STARTUP = JsonIO.read('kubejs/config/fish_nutrition.json')
if (!BC_FISH_STARTUP || BC_FISH_STARTUP.schema !== 'bc.fish_nutrition.v1') {
    throw new Error('Missing or invalid fish nutrition contract')
}
StartupEvents.registry('item', function (event) {
    Object.keys(BC_FISH_STARTUP.families).forEach(function (name) {
        var family = BC_FISH_STARTUP.families[name]
        if (name === 'plain') return // Preserve the native cooked item and old stacks.
        event.create(family.cooked)
            .displayName(family.display_name)
            .texture('kubejs:item/fish/cooked_' + name + '_fish')
            .food(function (food) {
                // Same food properties as native BASIC_COOKED_FISH, not a buff.
                food.hunger(6).saturation(2.0)
            })
    })
})
