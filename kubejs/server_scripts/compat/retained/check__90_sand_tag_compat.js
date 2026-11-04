// Replace direct overworld sand inputs with one ordinary-sand catalogue. This
// deliberately leaves chemically distinct sand-like materials alone:
// minecraft:soul_sand, minecraft:soul_soil, and modded ore/slag sands never
// enter this replacement because they are not direct vanilla sand inputs.
// KubeJS replaceInput also matches tag ingredients. Protect these exact scoped
// material identities, including native tag-only casts and sandpaper. All other
// functional uses remain generic by default, as with the rock identity policy.
var BC_SAND_IDENTITY_RECIPES = [
    'minecraft:sandstone',
    'minecraft:red_sandstone',
    'natures_spirit:pink_sandstone',
    'immersive_weathering:sand_layer_block',
    'immersive_weathering:red_sand_layer_block',
    'create:crafting/materials/sand_paper',
    'create:crafting/materials/red_sand_paper',
    'tconstruct:smeltery/sand_cast',
    'tconstruct:smeltery/red_sand_cast',
    'kubejs:hand_workshop/tconstruct/blank_sand_cast',
    'kubejs:hand_workshop/tconstruct/blank_red_sand_cast'
]
ServerEvents.recipes(function (event) {
    var genericSandUses = { not: BC_SAND_IDENTITY_RECIPES }
    event.replaceInput(genericSandUses, 'minecraft:red_sand', '#kubejs:ordinary_sand')
    event.replaceInput(genericSandUses, 'minecraft:sand', '#kubejs:ordinary_sand')
})
