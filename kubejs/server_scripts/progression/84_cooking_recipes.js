// priority: -100001
// Recipe-only migration runs after ordinary pack recipe edits.
// The legacy culinary policy remains non-destructive.
ServerEvents.recipes(function (event) {
    var policy = JsonIO.read('kubejs/config/cooking_recipes.json')
    if (!policy || policy.enabled === false) return
    if (String(policy.schema) !== 'better_cooking:recipe_policy.v1' || policy.enabled !== true) {
        throw new Error('[cooking-recipes] Invalid recipe-only policy')
    }
    var legacy = JsonIO.read('kubejs/config/culinary_policy.json')
    if (legacy && String(legacy.qualification) === 'qualified') {
        throw new Error('[cooking-recipes] Legacy gameplay policy must not install alongside recipe-only migration')
    }
    if (!policy.routes || !policy.catalogue || !policy.definitions || !policy.remove_ids || !policy.source_recipe_hashes || !policy.expected_sources) {
        throw new Error('[cooking-recipes] Incomplete recipe plan')
    }
    var seen = {}, owned = {}, remove = {}, route, id, i
    var Objects = Java.loadClass('java.util.Objects')
    // MapJS/JsonIO.of can coerce numeric-looking strings to numbers. Keep the
    // source baseline as raw Gson, especially shaped patterns such as ["0"].
    var rawPolicy = JsonIO.readJson('kubejs/config/cooking_recipes.json')
    var rawSources = rawPolicy.getAsJsonObject('expected_sources')
    var rawDefinitions = rawPolicy.getAsJsonArray('definitions')
    function sameJson(left, right) {
        // Gson equality ignores object key order, preserves array order and
        // compares numeric primitives by value. Avoid traversing Rhino wrappers.
        return Objects.equals(JsonIO.of(left), JsonIO.of(right))
    }
    var pendingIds = {}, pendingSources = {}, pendingCounts = {}
    var requestedPending = policy.pending_source_ids || []
    for (i = 0; i < requestedPending.length; i++) pendingIds[String(requestedPending[i])] = true
    if (event.addedRecipes) event.addedRecipes.forEach(function (recipe) {
        if (recipe.removed) return
        recipe.serialize()
        var key = String(recipe.getId())
        pendingSources[key] = recipe
        pendingCounts[key] = (pendingCounts[key] || 0) + 1
    })
    // Compare originals AND the separately queued post-script source recipes.
    for (id in policy.routes) {
        if (!policy.expected_sources[id]) throw new Error('[cooking-recipes] Missing source baseline ' + id)
        var count = 0
        var checkSource = function (recipe) {
            count++
            // Keep Gson objects intact: JS round-trips can reinterpret numeric
            // crafting key names (for example supplementaries' "0" key).
            var actual = recipe.json
            if (!Objects.equals(actual, rawSources.get(String(id)))) {
                throw new Error('[cooking-recipes] Changed source recipe ' + id + ' expected=' + rawSources.get(String(id)) + ' actual=' + actual)
            }
        };
        if (pendingIds[id]) {
            if (pendingCounts[id] === 1) checkSource(pendingSources[id])
        } else {
            event.forEachRecipe({ id: String(id) }, checkSource)
        }
        if (count !== 1) throw new Error('[cooking-recipes] Missing/duplicate source recipe ' + id)
    }
    // Validate the entire document before calling any mutating recipe API.
    for (i = 0; i < policy.definitions.length; i++) {
        var d = policy.definitions[i]
        id = String(d.id)
        if (!d.json || !d.json.type || String(d.json.type).indexOf('better_cooking:') === 0 || seen[id]) {
            throw new Error('[cooking-recipes] Invalid or duplicate definition ' + id)
        }
        seen[id] = d.json
    }
    for (id in policy.routes) {
        route = policy.routes[id]
        var action = String(route.action)
        if (!route.reason || !policy.source_recipe_hashes[id] ||
            ['retain', 'redirect', 'replace', 'remove'].indexOf(String(route.action)) < 0) {
            throw new Error('[cooking-recipes] Invalid decision ' + id)
        }
        if (action === 'replace' || action === 'redirect') {
            if (!route.definitions || !route.definitions.length) throw new Error('[cooking-recipes] Missing replacement ' + id)
            for (i = 0; i < route.definitions.length; i++) {
                var target = String(route.definitions[i].id)
                if (!seen[target] || owned[target] || !sameJson(seen[target], route.definitions[i].json)) {
                    throw new Error('[cooking-recipes] Missing, duplicate-owned or changed definition ' + target)
                }
                if (action === 'redirect' && (route.definitions.length !== 1 || target !== id)) {
                    throw new Error('[cooking-recipes] Redirect must retain its source ID ' + id)
                }
                owned[target] = id
            }
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
    // Exact object removal is needed for queued redirects: an ID-wide remove
    // could also remove the replacement queued under the same ID.
    for (id in pendingIds) {
        if (String(policy.routes[id].action) !== 'retain') pendingSources[id].removed = true
    }
    for (i = 0; i < policy.definitions.length; i++) {
        var definition = policy.definitions[i]
        event.custom(rawDefinitions.get(i).getAsJsonObject().getAsJsonObject('json')).id(String(definition.id))
    }
    for (i = 0; i < policy.remove_ids.length; i++) event.remove({ id: String(policy.remove_ids[i]) })
    console.info('[cooking-recipes] Installed ' + policy.definitions.length + ' definitions; removed ' + policy.remove_ids.length + ' exact IDs')
})
