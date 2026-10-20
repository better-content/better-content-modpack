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
})
