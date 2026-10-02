// Replace direct overworld sand inputs with one ordinary-sand catalogue. This
// deliberately leaves chemically distinct sand-like materials alone:
// minecraft:soul_sand, minecraft:soul_soil, and modded ore/slag sands never
// enter this replacement because they are not direct vanilla sand inputs.
// The final replacement must use the same target because replaceInput also
// matches tags. TNT is reauthored separately with this same catalogue.
ServerEvents.recipes(function (event) {
    event.replaceInput({}, 'minecraft:red_sand', '#kubejs:ordinary_sand')
    event.replaceInput({}, 'minecraft:sand', '#kubejs:ordinary_sand')
})
