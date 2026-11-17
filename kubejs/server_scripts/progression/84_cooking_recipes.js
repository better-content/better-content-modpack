// priority: -100001
// Sole cooking recipe installer. Validate changed sources and the complete
// replacement plan before mutating recipes; retain unrelated native routes.
ServerEvents.recipes(function (event) {
    var policy = JsonIO.read('kubejs/config/cooking_recipes.json')
    if (!policy || String(policy.schema) !== 'better_cooking:recipe_policy.v1' || policy.enabled !== true ||
        !policy.routes || !policy.definitions || !policy.remove_ids || !policy.expected_sources || !policy.source_recipe_hashes) {
        throw new Error('[cooking-recipes] Missing or incomplete recipe plan')
    }
    var Objects = Java.loadClass('java.util.Objects')
    // Raw Gson preserves numeric-looking shaped pattern keys (e.g. "0").
    var rawPolicy = JsonIO.readJson('kubejs/config/cooking_recipes.json')
    var rawSources = rawPolicy.getAsJsonObject('expected_sources')
    var rawDefinitions = rawPolicy.getAsJsonArray('definitions')
    function sameJson(left, right) { return Objects.equals(JsonIO.of(left), JsonIO.of(right)) }
    var seen = {}, owned = {}, remove = {}, pendingIds = {}, pendingSources = {}, pendingCounts = {}
    var id, i, route, definition, target, action
    var requestedPending = policy.pending_source_ids || []
    for (i = 0; i < requestedPending.length; i++) pendingIds[String(requestedPending[i])] = true
    if (event.addedRecipes) event.addedRecipes.forEach(function (recipe) {
        if (recipe.removed) return
        recipe.serialize()
        var key = String(recipe.getId())
        if (!policy.routes[key] || String(policy.routes[key].action) === 'retain') return
        pendingSources[key] = recipe
        pendingCounts[key] = (pendingCounts[key] || 0) + 1
    })

    // Only sources that this installer replaces/removes need a live source
    // comparison. The historical 2,160 retained rows are not a global food
    // qualification gate (nor a reason to reject unrelated ore/tray changes).
    var problems = []
    for (id in policy.routes) {
        route = policy.routes[id]
        action = String(route.action)
        if (action === 'retain') continue
        if (['redirect', 'replace', 'remove'].indexOf(action) < 0 || !route.reason ||
            !policy.source_recipe_hashes[id] || !policy.expected_sources[id] || !rawSources.has(id)) {
            throw new Error('[cooking-recipes] Invalid changed route ' + id)
        }
        // The former market recipe was pack-authored in food_eras.js; it no
        // longer queues an FD recipe, so require its stable ID to be vacant.
        if (id === 'kubejs:food_eras/market_fried_rice') {
            var oldMarket = 0
            event.forEachRecipe({ id: String(id) }, function () { oldMarket++ })
            if (oldMarket || pendingCounts[id]) problems.push('Old market FD source still active ' + id)
            continue
        }
        var count = 0
        var checkSource = function (recipe) {
            count++
            var actual = recipe.json
            var expected = rawSources.get(String(id))
            if (!Objects.equals(actual, expected)) {
                problems.push('Changed source recipe ' + id + ' expected=' + expected + ' actual=' + actual)
            }
        }
        if (pendingIds[id]) {
            if (pendingCounts[id] === 1) checkSource(pendingSources[id])
        } else {
            event.forEachRecipe({ id: String(id) }, checkSource)
        }
        if (count !== 1) problems.push('Missing/duplicate changed source recipe ' + id)
    }
    if (problems.length) throw new Error('[cooking-recipes] Changed source validation failed (' + problems.length + '):\n' + problems.join('\n'))

    // Assert the two separately approved fixed wok ports preserve each source
    // formulation, bowl payment and feast-block output. No quality on the feast.
    var friedRice = ['fruitsdelight:cooking/pineapple_fried_rice', 'kubejs:food_eras/market_fried_rice']
    for (i = 0; i < friedRice.length; i++) {
        id = friedRice[i]
        route = policy.routes[id]
        var source = policy.expected_sources[id]
        if (!route || String(route.action) !== 'redirect' || !source ||
            !source.ingredients || source.ingredients.length !== 6 || !source.container || !source.result ||
            !sameJson(source.container, { item: 'minecraft:bowl' }) ||
            !sameJson(source.result, { item: 'fruitsdelight:pineapple_fried_rice' })) {
            throw new Error('[cooking-recipes] Invalid fried-rice source ' + id)
        }
        var port = route.definitions && route.definitions[0] && route.definitions[0].json
        if (!port || String(port.type) !== 'kaleidoscope_cookery:pot' ||
            !port.ingredients || !port.result || !port.carrier ||
            !sameJson(port.ingredients, source.ingredients) || !sameJson(port.result, source.result) ||
            !sameJson(port.carrier, source.container) || Number(port.time) !== Number(source.cookingtime) ||
            Number(port.stir_fry_count) !== 3 || port.soup_base || port.quality) {
            throw new Error('[cooking-recipes] Fried-rice cost/output drift ' + id)
        }
    }

    // Validate the full set of *changes* before any event.custom/event.remove.
    for (i = 0; i < policy.definitions.length; i++) {
        definition = policy.definitions[i]
        id = String(definition.id)
        if (!definition.json || !definition.json.type || String(definition.json.type).indexOf('better_cooking:') === 0 || seen[id]) {
            throw new Error('[cooking-recipes] Invalid or duplicate definition ' + id)
        }
        seen[id] = definition.json
    }
    for (id in policy.routes) {
        route = policy.routes[id]
        action = String(route.action)
        if (action === 'retain' || action === 'remove') continue
        if (!route.definitions || !route.definitions.length) throw new Error('[cooking-recipes] Missing replacement ' + id)
        for (i = 0; i < route.definitions.length; i++) {
            target = String(route.definitions[i].id)
            if (!seen[target] || owned[target] || !sameJson(seen[target], route.definitions[i].json)) {
                throw new Error('[cooking-recipes] Missing, duplicate-owned or changed definition ' + target)
            }
            if (action === 'redirect' && (route.definitions.length !== 1 || target !== id)) {
                throw new Error('[cooking-recipes] Redirect must retain source ID ' + id)
            }
            owned[target] = id
        }
    }
    for (id in seen) {
        if (!owned[id]) throw new Error('[cooking-recipes] Orphan replacement ' + id)
        if (owned[id] !== id) {
            var collisions = 0
            event.forEachRecipe({ id: String(id) }, function () { collisions++ })
            if (collisions || pendingCounts[id]) throw new Error('[cooking-recipes] Replacement ID already exists ' + id)
        }
    }
    for (i = 0; i < policy.remove_ids.length; i++) {
        id = String(policy.remove_ids[i]); route = policy.routes[id]
        if (remove[id] || !route || (String(route.action) !== 'replace' && String(route.action) !== 'remove') || seen[id]) {
            throw new Error('[cooking-recipes] Invalid exact removal ' + id)
        }
        if (String(route.action) === 'remove') {
            var canonical = policy.routes[String(route.canonical_recipe)]
            if (!canonical || String(canonical.action) !== 'retain' || String(route.canonical_recipe) === id) {
                throw new Error('[cooking-recipes] Removal lacks retained canonical route ' + id)
            }
        }
        remove[id] = true
    }
    for (id in policy.routes) {
        route = policy.routes[id]
        if ((String(route.action) === 'replace' || String(route.action) === 'remove') && !remove[id]) {
            throw new Error('[cooking-recipes] Missing removal ' + id)
        }
    }
    // Exact queued-object removal keeps the same-ID replacement that follows.
    for (id in pendingIds) {
        if (policy.routes[id] && String(policy.routes[id].action) !== 'retain') pendingSources[id].removed = true
    }
    for (i = 0; i < policy.definitions.length; i++) {
        definition = policy.definitions[i]
        event.custom(rawDefinitions.get(i).getAsJsonObject().getAsJsonObject('json')).id(String(definition.id))
    }
    for (i = 0; i < policy.remove_ids.length; i++) event.remove({ id: String(policy.remove_ids[i]) })
    console.info('[cooking-recipes] Installed ' + policy.definitions.length + ' definitions; removed ' + policy.remove_ids.length + ' exact IDs')
})
