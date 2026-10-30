// Source-only V3 culinary migration. No edits to registered legacy items or finite loot.
// Discovery/unqualified policies are deliberately non-destructive. Installation is
// all-or-nothing at recipe event construction; exact serializer/native acceptance
// remains required before this source may be activated in a distribution.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('better_cooking')) return
    var policy = JsonIO.read('kubejs/config/culinary_policy.json')
    if (!policy || String(policy.schema) !== 'better_cooking:culinary_policy.v1') {
        throw new Error('[culinary] Missing or incompatible policy')
    }
    if (String(policy.qualification) !== 'qualified') {
        console.warn('[culinary] UNQUALIFIED discovery policy: retaining all original recipes; no culinary acceptance claim')
        return
    }
    if (!Platform.isLoaded('kaleidoscope_cookery')) throw new Error('[culinary] Missing native kitchen provider')
    if (!policy.native_qualification || !policy.migration) throw new Error('[culinary] Missing qualification receipt/migration')
    if (!policy.acquisition || String(policy.acquisition.status) !== 'closed' || policy.native_unresolved_recipe_ids.length) {
        throw new Error('[culinary] Unclosed acquisition or native recipe coverage; refusing migration')
    }
    for (var a = 0; a < policy.acquisition.rows.length; a++) {
        if (policy.acquisition.rows[a].unresolved.length) throw new Error('[culinary] Unresolved acquisition origin')
    }
    var migration = policy.migration
    if (String(migration.qualification) !== 'qualified' || policy.gaps.length || migration.errors.length || migration.unresolved_source_ids.length) {
        throw new Error('[culinary] Unresolved migration; refusing recipe removals')
    }
    var key
    for (key in policy.foods) {
        if (policy.foods[key].unresolved.length) throw new Error('[culinary] Unresolved food ' + key)
    }
    for (key in policy.routes) {
        if (policy.routes[key].unresolved.length || String(policy.routes[key].disposition) === 'unresolved') {
            throw new Error('[culinary] Unresolved alternate route ' + key)
        }
    }
    var definitions = migration.recipes
    var removals = migration.remove_ids
    var seen = {}
    var covered = {}
    var i
    // Validate the complete plan before constructing anything, then emit EVERY
    // replacement before removing ANY source. No output-wide/namespace removals.
    for (i = 0; i < definitions.length; i++) {
        var definition = definitions[i]
        var id = String(definition.id)
        if (seen[id] || !definition.json || !definition.json.type) throw new Error('[culinary] Invalid/duplicate replacement ' + id)
        if (definition.canonical_rewrites || definition.canonical_consumer_alternatives) {
            if (!policy.routes[id] || String(policy.routes[id].source_id) !== id || String(policy.routes[id].disposition) === 'replace') {
                throw new Error('[culinary] Canonical rewrite lacks identified retained source ' + id)
            }
        }
        seen[id] = true
    }
    for (i = 0; i < removals.length; i++) {
        var oldId = String(removals[i])
        var route = policy.routes[oldId]
        if (covered[oldId] || !route || String(route.disposition) !== 'replace' || !route.replacement || !seen[String(route.replacement.id)]) {
            throw new Error('[culinary] Removal lacks exact replacement ' + oldId)
        }
        covered[oldId] = true
    }
    for (i = 0; i < definitions.length; i++) event.custom(definitions[i].json).id(String(definitions[i].id))
    for (i = 0; i < removals.length; i++) event.remove({ id: String(removals[i]) })
})
