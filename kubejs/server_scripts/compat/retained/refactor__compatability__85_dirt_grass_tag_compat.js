// Soil identities are not interchangeable feedstocks. Functional uses still
// accept the shared dirt domain, including future compatible variants.
var BC_DIRT_IDENTITY_RECIPES = [
    'minecraft:coarse_dirt',
    'immersive_weathering:sandy_dirt',
    'natures_spirit:sandy_soil',
    'unearthed:siltstone',
    'unearthed:conglomerate',
    'dawnoftimebuilder:rammed_dirt'
]
ServerEvents.recipes(function (event) {
    event.replaceInput({ not: BC_DIRT_IDENTITY_RECIPES }, 'minecraft:dirt', '#kubejs:dirt_like')
    event.replaceInput({}, 'minecraft:grass_block', '#kubejs:grass_like')
})
