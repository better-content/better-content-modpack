// Final recipe-graph reporting pass for bc.crafting_policy.v1. The existing
// pre-application backlog remains non-blocking; every finding names the recipe,
// family, and item so runtime-snapshot review can distinguish removals from leaks.
function bcPolicyCollectStrings(value, result) {
    if (value === null || value === undefined) return
    if (typeof value === 'string') {
        var match = value.match(/[a-z0-9_.-]+:[a-z0-9_./-]+/g)
        if (match) match.forEach(function (id) { result.push(id) })
        return
    }
    if (Array.isArray(value)) {
        value.forEach(function (entry) { bcPolicyCollectStrings(entry, result) })
        return
    }
    if (typeof value === 'object') {
        Object.keys(value).forEach(function (key) { bcPolicyCollectStrings(value[key], result) })
    }
}

function bcPolicyMatches(id, selector) {
    if (selector.exact_id) return id === selector.exact_id
    var split = id.indexOf(':')
    var namespace = split < 0 ? '' : id.substring(0, split)
    var path = split < 0 ? id : id.substring(split + 1)
    if (selector.namespace && namespace !== selector.namespace) return false
    if (selector.id_prefix && path.indexOf(selector.id_prefix) !== 0) return false
    return !!(selector.namespace || selector.id_prefix)
}

ServerEvents.recipes(function (event) {
    var contract = JsonIO.read('kubejs/config/crafting_policy.json') || { families: [] }
    var quarantine = JsonIO.read('kubejs/config/quarantined_items.json') || { items: [] }
    var exactCuts = (quarantine.items || []).concat([
        'minecraft:dragon_head', 'minecraft:dragon_egg', 'minecraft:dragon_breath'
    ])
    var governed = []
    ;(contract.families || []).forEach(function (family) {
        if (family.disposition === 'creative_or_technical' || family.disposition === 'supported') return
        ;(family.selectors || []).forEach(function (selector) {
            if (!selector.tag) governed.push({ family: family.id, selector: selector })
        })
    })

    var findings = {}
    event.forEachRecipe({}, function (recipe) {
        var id = '' + recipe.getId()
        var data
        try {
            data = JSON.parse('' + recipe.json)
        } catch (error) {
            console.warn('[bc.crafting_policy.v1] unreadable recipe JSON: ' + id + ' (' + error + ')')
            return
        }
        var allIds = []
        var outputIds = []
        bcPolicyCollectStrings(data, allIds)
        bcPolicyCollectStrings(data.result, outputIds)
        bcPolicyCollectStrings(data.results, outputIds)
        bcPolicyCollectStrings(data.output, outputIds)

        exactCuts.forEach(function (item) {
            if (allIds.indexOf(item) >= 0) findings['consumer|' + id + '|' + item] = true
            if (outputIds.indexOf(item) >= 0) findings['leak|' + id + '|quarantine|' + item] = true
        })
        governed.forEach(function (rule) {
            allIds.forEach(function (item) {
                if (bcPolicyMatches(item, rule.selector)) {
                    findings['family-consumer|' + id + '|' + rule.family + '|' + item] = true
                }
            })
            outputIds.forEach(function (item) {
                if (bcPolicyMatches(item, rule.selector)) {
                    findings['leak|' + id + '|' + rule.family + '|' + item] = true
                }
            })
        })
    })

    var keys = Object.keys(findings).sort()
    keys.forEach(function (finding) {
        console.info('[bc.crafting_policy.v1] pre-application: ' + finding.replace(/\|/g, ': '))
    })
    console.info('[bc.crafting_policy.v1] pre-application recipe report: ' + keys.length + ' exact findings')
})
