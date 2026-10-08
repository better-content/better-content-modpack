// priority: 1000
// Register before ordinary recipe callbacks. Native TConstruct casting constructors
// populate an unsynchronized shared material-cost lookup; KubeJS's final conversion
// is parallel. Construct/cache the original native recipes sequentially first.
// Concurrency is the build403 diagnosis hypothesis, not a runtime-proven remedy yet.
// No recipe JSON, item costs, outputs, or native cache values are rewritten here.
ServerEvents.recipes(function (event) {
    function prewarm(recipe) {
        if (recipe.getOriginalRecipe() == null) {
            throw new Error('Native casting prewarm returned no recipe: ' + recipe.getId())
        }
    }
    event.forEachRecipe({ type: 'tconstruct:table_casting_material' }, prewarm)
    event.forEachRecipe({ type: 'tconstruct:basin_casting_material' }, prewarm)
    event.forEachRecipe({ type: 'tconstruct:table_casting_composite' }, prewarm)
    event.forEachRecipe({ type: 'tconstruct:basin_casting_composite' }, prewarm)
})
