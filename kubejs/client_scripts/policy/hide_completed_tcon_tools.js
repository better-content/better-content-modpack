// Hide multipart tools assembled at a Tinker Station/Anvil, not everything
// that accepts modifiers. Single-part tools/shields and wearable armor are
// crafted/cast elsewhere; keep those, direct crafts (e.g. Flint and Brick
// and slime staffs), parts, materials, modifiers, and recipes visible.
function bcCompletedTconTools() {
    return Ingredient.of('#tconstruct:modifiable/multipart')
        .subtract(Ingredient.of('#tconstruct:modifiable/multipart/single'))
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
