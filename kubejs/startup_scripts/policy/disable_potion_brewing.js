// Vanilla potion delivery is intentionally absent. Keep the Brewing Stand
// registry empty even when another loaded mod contributes a custom recipe.
MoreJSEvents.registerPotionBrewing(function (event) {
    event.removeByPotion(null, null, null)
    event.removeContainer(Ingredient.of('*'))
    event.removeByCustom(null, null, null)
    event.removeByCustom(function () { return true })
})
