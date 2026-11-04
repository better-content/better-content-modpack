// Exact memberships are pack data; Diet's native amounts/inheritance stay intact.
var BC_FISH_FAMILIES = JsonIO.read('kubejs/config/fish_nutrition.json')
if (!BC_FISH_FAMILIES || BC_FISH_FAMILIES.schema !== 'bc.fish_nutrition.v1') {
    throw new Error('Missing or invalid fish nutrition contract')
}
ServerEvents.tags('item', function (event) {
    Object.keys(BC_FISH_FAMILIES.families).forEach(function (name) {
        var family = BC_FISH_FAMILIES.families[name]
        event.add('kubejs:fish/' + name + '_raw', family.raw)
        var cookable = family.raw.filter(function (id) {
            return BC_FISH_FAMILIES.native_cooking_exclusions.indexOf(String(id)) === -1
        })
        event.add('kubejs:fish/' + name + '_cookable', cookable)
        family.groups.forEach(function (group) {
            // Direct output groups take precedence over recipe-wide inheritance.
            event.add('diet:' + group, family.raw)
            event.add('diet:' + group, family.cooked)
        })
    })
    // Preserve existing native cooking for inedible fire-resistant catches;
    // they gain no raw food properties or nutrient assignments.
    event.add('kubejs:fish/plain_cookable', BC_FISH_FAMILIES.legacy_plain_cookable)

    // These shared outputs have no species identity. Pin their pre-specialty
    // native group sets, retaining every original ingredient (and its recipes).
    // Other meals continue to use Diet's ordinary inheritance.
    Object.keys(BC_FISH_FAMILIES.generic_output_groups).forEach(function (item) {
        BC_FISH_FAMILIES.generic_output_groups[item].forEach(function (group) {
            event.add('diet:' + group, item)
        })
    })
})
