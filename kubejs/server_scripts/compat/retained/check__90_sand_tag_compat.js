// Replace both direct vanilla inputs with one ordinary-sand catalogue.
// The final replacement must use the same target because replaceInput also matches tags.
ServerEvents.recipes(function (event) {
    event.replaceInput({}, 'minecraft:red_sand', '#kubejs:ordinary_sand')
    event.replaceInput({}, 'minecraft:sand', '#kubejs:ordinary_sand')
})
