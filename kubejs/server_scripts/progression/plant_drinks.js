// priority: -100003
// Apply native drink ownership after cooking validation and exact clash corrections.
// Fresh-world clean break. No save mutation, item remapping or legacy effect-bearing juice shortcut.
// The native Better Drinking Water JAR owns registrations, 250 mB recipes and entitlements.
ServerEvents.recipes(event => {
    const retiredJuices = [
        'fruitsdelight:pear_juice', 'fruitsdelight:orange_juice', 'fruitsdelight:lemon_juice',
        'fruitsdelight:kiwi_juice', 'fruitsdelight:hamimelon_juice',
        'farmersdelight:melon_juice', 'farmersrespite:strong_melon_juice',
        'the_deep_void:grimberry_juice'
    ]
    const normalized = [
        'farmersdelight:apple_cider', 'fruitsdelight:mango_tea', 'fruitsdelight:lychee_cherry_tea',
        'fruitsdelight:peach_tea', 'fruitsdelight:mangosteen_tea', 'fruitsdelight:hawberry_tea',
        'farmersrespite:rose_hip_tea',
        'brewinandchewin:glittering_grenadine', 'brewinandchewin:strongroot_ale',
        'brewinandchewin:bloody_mary', 'brewinandchewin:beer', 'brewinandchewin:rice_wine'
    ]
    retiredJuices.concat(normalized).forEach(output => {
        event.forEachRecipe({ output: output }, recipe => {
            if (!String(recipe.getId()).startsWith('better_drinking_water:plant/')) event.remove({ id: recipe.getId() })
        })
    })
    // Native fluid-producing workstation recipes are not matched by an item-output filter.
    // Keep mixtures/secondary variants, processed-starch vodka, kombucha and native specialist teas.
    const replacedFluids = retiredJuices.concat(normalized).concat([
        'farmersrespite:melon_juice', 'farmersrespite:apple_cider'
    ])
    ;['brewinandchewin:fermenting', 'farmersrespite:brewing', 'create:mixing', 'create:compacting'].forEach(type => {
        event.forEachRecipe({ type: type }, recipe => {
            if (String(recipe.getId()).startsWith('better_drinking_water:plant/')) return
            const json = JSON.parse(String(recipe.json))
            const results = json.results || (json.result ? [json.result] : [])
            if (results.some(result => replacedFluids.indexOf(result.fluid) >= 0)) event.remove({ id: recipe.getId() })
        })
    })
    // Cooking can queue replacement recipes that forEachRecipe does not expose.
    // Apply the same primary-drink ownership to those exact queued objects too;
    // never remove Better Drinking Water's own 250 mB routes or secondary drinks.
    const ownedItems = retiredJuices.concat(normalized)
    const fluidTypes = ['brewinandchewin:fermenting', 'farmersrespite:brewing', 'create:mixing', 'create:compacting']
    if (event.addedRecipes) event.addedRecipes.forEach(recipe => {
        if (recipe.removed) return
        recipe.serialize()
        if (String(recipe.getId()).startsWith('better_drinking_water:plant/')) return
        const json = JSON.parse(String(recipe.json))
        const results = json.results || (json.result ? [json.result] : [])
        const ownedItem = results.some(result => ownedItems.indexOf(typeof result === 'string' ? result : result.item) >= 0)
        const ownedFluid = fluidTypes.indexOf(String(json.type)) >= 0 &&
            results.some(result => replacedFluids.indexOf(result.fluid) >= 0)
        if (ownedItem || ownedFluid) recipe.removed = true
    })
})
