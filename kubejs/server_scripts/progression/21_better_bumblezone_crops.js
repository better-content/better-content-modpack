// Cultivar propagules enter play through Bumblezone nurseries. Recipes that
// manufacture seeds from produce would bypass that expedition origin.
ServerEvents.recipes(function (event) {
    event.remove({ output: '#better_bumblezone_crops:seeds' })
})
