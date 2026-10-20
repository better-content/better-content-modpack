// Exact, pack-owned terrain profiles. No runtime stone/dirt classifier or ore assay.
;(function () {
    function fail(message) { throw new Error('[terrain-recovery] ' + message) }
    function keys(value, allowed, label) {
        Object.keys(value).forEach(function (key) {
            if (allowed.indexOf(key) < 0) fail('unknown ' + label + ' field ' + key)
        })
    }
    function number(value, min, max) {
        return typeof value === 'number' && isFinite(value) && value >= min && value <= max
    }
    function item(id) {
        if (typeof id !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id) || !Item.exists(id)) fail('missing/invalid item ' + id)
    }
    ServerEvents.recipes(function (event) {
        var data = JsonIO.read('kubejs/config/terrain_recovery.json')
        if (!data || data.schema !== 'bc.terrain_recovery.v1') fail('missing catalogue or unsupported schema')
        keys(data, ['schema', 'survey_pins', 'processing_time', 'recovery', 'archives', 'physical_units', 'profiles', 'entries', 'exclusions', 'remove_recipes', 'recycling_exclusions', 'chemistry_audit', 'conversion_sources'], 'catalogue')
        if (data.processing_time !== 500 || data.recovery !== 0.25 || !data.entries || !data.entries.length) fail('invalid catalogue constants')
        item('createsifter:brass_mesh')
        var seen = {}, used = {}, excluded = {}, groups = {}
        data.exclusions.forEach(function (entry) {
            keys(entry, ['input', 'reason'], 'exclusion')
            if (!entry.reason || excluded[entry.input]) fail('invalid exclusion ' + entry.input)
            excluded[entry.input] = true
        })
        Object.keys(data.profiles).forEach(function (name) {
            var profile = data.profiles[name]
            keys(profile, ['route', 'basis', 'physical', 'chemical'], 'profile')
            if (['crushing', 'sifting'].indexOf(profile.route) < 0 || !profile.basis || !profile.physical.length || profile.chemical.length > 2) fail('invalid profile ' + name)
            var fraction = 0, budget = 0, outputs = {}
            profile.physical.forEach(function (output) {
                keys(output, ['item', 'fraction', 'count', 'chance'], 'physical output')
                item(output.item)
                if (outputs[output.item]) fail('duplicate output ' + name + '/' + output.item)
                outputs[output.item] = true
                var unit = data.physical_units[output.item]
                if (!number(unit, 0.25, 1) || !number(output.fraction, 0, 1) || !number(output.chance, 0.000001, 0.25) || output.chance > output.fraction * data.recovery + 1e-10 || output.count !== (output.item === 'minecraft:clay_ball' ? 4 : 1)) fail('invalid physical output ' + name)
                if (output.item === 'minecraft:quartz' && output.chance > (name === 'quartzite' ? 0.25 : 1 / 16)) fail('quartz cap ' + name)
                fraction += output.fraction
                budget += output.chance * output.count * unit
            })
            if (Math.abs(fraction - 1) > 1e-10 || budget > data.recovery + 1e-10) fail('physical budget ' + name)
            profile.chemical.forEach(function (output) {
                keys(output, ['item', 'count', 'chance', 'consumer', 'basis'], 'chemical output')
                item(output.item)
                var audit = data.chemistry_audit[output.item]
                if (outputs[output.item] || output.count !== 1 || !number(output.chance, 0.000001, 1 / 64) || !output.basis || !audit || audit.status !== 'eligible' || audit.consumer !== output.consumer) fail('invalid chemical output ' + name)
                outputs[output.item] = true
            })
        })
        data.entries.forEach(function (entry) {
            keys(entry, ['input', 'profile', 'equivalence_group', 'source', 'counterpart_of'], 'entry')
            item(entry.input)
            if (seen[entry.input] || excluded[entry.input] || !data.profiles[entry.profile] || !entry.source || !entry.equivalence_group) fail('duplicate/unclassified input ' + entry.input)
            seen[entry.input] = entry
            used[entry.profile] = true
            // Equivalence is declared, never inferred from names or a broad tag.
            var signature = JSON.stringify(data.profiles[entry.profile].physical) + JSON.stringify(data.profiles[entry.profile].chemical)
            if (groups[entry.equivalence_group] && groups[entry.equivalence_group] !== signature) fail('unsafe equivalence group ' + entry.equivalence_group)
            groups[entry.equivalence_group] = signature
        })
        data.entries.forEach(function (entry) {
            if (entry.counterpart_of && (!seen[entry.counterpart_of] || seen[entry.counterpart_of].profile !== entry.profile)) fail('counterpart profile ' + entry.input)
        })
        Object.keys(data.profiles).forEach(function (name) { if (!used[name]) fail('unused profile ' + name) })

        // Validate everything before modifying the recipe event.
        data.remove_recipes.forEach(function (recipe) {
            keys(recipe, ['id', 'input', 'source'], 'replacement')
            if (!seen[recipe.input] || !recipe.source || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(recipe.id)) fail('invalid exact replacement')
        })
        data.recycling_exclusions.forEach(function (recipe) {
            keys(recipe, ['id', 'tag', 'exclude'], 'recycling exclusion')
            if (recipe.exclude.length !== 1 || !seen[recipe.exclude[0]]) fail('invalid recycling exclusion')
        })
        data.remove_recipes.forEach(function (recipe) { event.remove({ id: recipe.id }) })
        data.recycling_exclusions.forEach(function (spec) {
            // Two pinned Create recipes only; retain their native outputs and
            // manufactured-family recycling while excluding the raw terrain item.
            var replacement = null
            event.forEachRecipe({ id: spec.id }, function (recipe) {
                var json = JSON.parse('' + recipe.json)
                if (json.type !== 'create:crushing' || json.ingredients.length !== 1 || json.ingredients[0].tag !== spec.tag) fail('unexpected recycling recipe ' + spec.id)
                json.ingredients = [{ type: 'forge:difference', base: { tag: spec.tag }, subtracted: { item: spec.exclude[0] } }]
                replacement = json
            })
            if (!replacement) fail('missing required recycling recipe ' + spec.id)
            event.remove({ id: spec.id })
            event.custom(replacement).id(spec.id)
        })
        data.entries.forEach(function (entry) {
            var profile = data.profiles[entry.profile]
            var results = profile.physical.concat(profile.chemical).map(function (output) {
                return { item: output.item, count: output.count, chance: output.chance }
            })
            var json = {
                type: profile.route === 'crushing' ? 'create:crushing' : 'createsifter:sifting',
                ingredients: [{ item: entry.input }], processingTime: data.processing_time, results: results
            }
            if (profile.route === 'sifting') {
                json.ingredients.push({ item: 'createsifter:brass_mesh' })
                json.waterlogged = true
            }
            event.custom(json).id('kubejs:terrain_recovery/' + profile.route + '/' + entry.input.replace(':', '/'))
        })
        console.info('[terrain-recovery] installed ' + data.entries.length + ' exact terrain recipes; no ore assays or hand routes')
    })
})()
