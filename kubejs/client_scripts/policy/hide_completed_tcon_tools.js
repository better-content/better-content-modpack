// Completed TConstruct gear is assembled and inspected through the tool
// stations. Hide those generated stacks from recipe viewers while retaining
// wearable armor, tool parts, materials, modifiers, and recipes.
function bcCompletedTconTools() {
    return Ingredient.of('#tconstruct:modifiable')
        .subtract(Ingredient.of('#tconstruct:modifiable/armor/worn'))
}

JEIEvents.hideItems(function (event) {
    event.hide(bcCompletedTconTools())
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        event.hide(bcCompletedTconTools())
    })
}
