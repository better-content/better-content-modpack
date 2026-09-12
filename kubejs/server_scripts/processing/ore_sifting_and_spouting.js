// One separation language for the pack: granular feeds are sifted, while the
// Spout performs direct fluid treatments. Encased Fan washing has no recipes.
;(function () {
    var SKIP_SPLASHING = {
        'createaddition:rolling/ice': true,
        'create_confectionery:sugar_cube': true
    }
    var GRANULAR_INPUTS = {
        'minecraft:gravel': true,
        'minecraft:sand': true,
        'minecraft:red_sand': true,
        'minecraft:soul_sand': true,
        'create:crushed_raw_copper': true,
        'create:crushed_raw_gold': true,
        'create:crushed_raw_iron': true,
        'create:crushed_raw_zinc': true,
        'iceandfire:crushed_silver_ore': true,
        'creatingspace:moon_regolith': true,
        'malum:copper_node': true,
        'malum:gold_node': true,
        'malum:iron_node': true,
        'malum:zinc_node': true,
        'malum:crushed_brilliance': true,
        'malum:crushed_soulstone': true
    }
    var CANONICAL_FEEDS = {
        'create:crushed_raw_copper': { nugget: 'create:copper_nugget', concentrate: 'realistic_ores:copper_concentrate', base: 2, chance: 0.25 },
        'create:crushed_raw_gold': { nugget: 'minecraft:gold_nugget', concentrate: 'realistic_ores:gold_concentrate', base: 2, chance: 0.25 },
        'create:crushed_raw_iron': { nugget: 'minecraft:iron_nugget', concentrate: 'realistic_ores:iron_concentrate', base: 2, chance: 0.25 },
        'create:crushed_raw_zinc': { nugget: 'create:zinc_nugget', concentrate: 'realistic_ores:zinc_concentrate', base: 2, chance: 0.25 },
        'iceandfire:crushed_silver_ore': { nugget: 'iceandfire:silver_nugget', concentrate: 'realistic_ores:silver_concentrate', base: 2, chance: 0.25 },
        'malum:copper_node': { nugget: 'create:copper_nugget', concentrate: 'realistic_ores:copper_concentrate', base: 1, chance: 0.5 },
        'malum:gold_node': { nugget: 'minecraft:gold_nugget', concentrate: 'realistic_ores:gold_concentrate', base: 1, chance: 0.5 },
        'malum:iron_node': { nugget: 'minecraft:iron_nugget', concentrate: 'realistic_ores:iron_concentrate', base: 1, chance: 0.5 },
        'malum:zinc_node': { nugget: 'create:zinc_nugget', concentrate: 'realistic_ores:zinc_concentrate', base: 1, chance: 0.5 }
    }
    var SHARED_METALS = ['iron', 'nickel', 'copper', 'gold', 'tin', 'zinc', 'lead',
        'cadmium', 'silver', 'aluminum', 'titanium', 'cobalt', 'osmium', 'uranium', 'thorium']

    function firstIngredientId(json) {
        try {
            var ingredient = json.getAsJsonArray('ingredients').get(0).getAsJsonObject()
            return ingredient.has('item') ? '' + ingredient.get('item').getAsString() : null
        } catch (ignored) { return null }
    }

    function copiedArray(json, key) {
        try { return JSON.parse('' + json.getAsJsonArray(key)) } catch (ignored) { return [] }
    }

    function waterAmount(input) {
        if (input === 'minecraft:magma_block') return 1000
        if (input && input.indexOf('_concrete_powder') >= 0) return 250
        return 100
    }

    function safePath(id) {
        return String(id).replace(/[^a-z0-9_./-]/g, '_').replace(/[:/]/g, '_')
    }

    function canonicalizeLegacyResults(input, results) {
        var spec = CANONICAL_FEEDS[input]
        if (!spec) return results
        var converted = [{ item: spec.concentrate, count: spec.base }, { item: spec.concentrate, chance: spec.chance }]
        results.forEach(function (result) {
            if (result.item !== spec.nugget) converted.push(result)
        })
        return converted
    }

    ServerEvents.recipes(function (event) {
        var migratedSifting = 0
        var migratedSpouting = 0
        var skipped = 0

        // Remove bundled and third-party Sifting generation before installing the
        // curated geological recipes shipped by Realistic Ores and these legacy adapters.
        event.remove({ type: 'createsifter:sifting', mod: 'createsifter' })
        ;['createsifter:advanced_brass_mesh', 'createsifter:custom_mesh',
            'createsifter:advanced_custom_mesh'].forEach(function (item) {
            event.remove({ output: item })
        })

        ;['bloodmagic:arc', 'ars_nouveau:crush', 'occultism:crushing'].forEach(function (type) {
            event.forEachRecipe({ type: type }, function (recipe) {
                var encoded = '' + recipe.json
                for (var i = 0; i < SHARED_METALS.length; i++) {
                    var material = SHARED_METALS[i]
                    if (encoded.indexOf('forge:raw_materials/' + material) >= 0
                            || encoded.indexOf('forge:ores/' + material) >= 0) {
                        event.remove({ id: '' + recipe.getId() })
                        break
                    }
                }
            })
        })

        event.forEachRecipe({ type: 'create:splashing' }, function (recipe) {
            var id = '' + recipe.getId()
            var json = recipe.json
            var input = firstIngredientId(json)
            var results = canonicalizeLegacyResults(input, copiedArray(json, 'results'))

            if (!input || SKIP_SPLASHING[id] || input === 'create_confectionery:sugar_cube'
                    || (input === 'minecraft:ice' && results.length === 1
                        && results[0].item === 'minecraft:packed_ice')) {
                skipped++
                return
            }

            if (GRANULAR_INPUTS[input]) {
                event.custom({
                    type: 'createsifter:sifting',
                    ingredients: [{ item: input }, { item: 'createsifter:brass_mesh' }],
                    processingTime: 500,
                    results: results,
                    waterlogged: true
                }).id('kubejs:sifting/migrated_' + safePath(id))
                migratedSifting++
                return
            }

            // Filling has one item output. Every retained non-granular washing
            // recipe is a one-result water treatment in the audited pack.
            if (results.length === 1 && results[0].item && !results[0].chance) {
                var result = { item: results[0].item }
                if (results[0].count) result.count = results[0].count
                event.custom({
                    type: 'create:filling',
                    ingredients: [
                        { item: input },
                        { fluid: 'minecraft:water', amount: waterAmount(input) }
                    ],
                    results: [result]
                }).id('kubejs:spouting/migrated_' + safePath(id))
                migratedSpouting++
            } else {
                console.warn('[ore-processing] did not migrate multi-output treatment ' + id)
                skipped++
            }
        })

        // Any conventional raw metal source enters the same canonical economy.
        // The recipes use raw-material tags, so native world sources can vary while
        // their processing result remains stable.
        SHARED_METALS.forEach(function (material) {
            var raw = { tag: 'forge:raw_materials/' + material }
            var concentrate = { item: 'realistic_ores:' + material + '_concentrate' }
            ;[false, true].forEach(function (wet) {
                var recipe = {
                    type: 'createsifter:sifting',
                    ingredients: [raw, { item: 'createsifter:string_mesh' }],
                    processingTime: 500,
                    results: [concentrate]
                }
                if (wet) recipe.waterlogged = true
                event.custom(recipe).id('kubejs:sifting/raw_' + material + '_string_' + (wet ? 'wet' : 'dry'))
            })

            event.custom({
                type: 'bloodmagic:arc', input: raw, inputsize: 1,
                tool: { tag: 'bloodmagic:arc/cuttingfluid' }, consumeingredient: false,
                mainoutputchance: 0.0, output: { item: concentrate.item, count: 2 },
                addedoutput: [{ type: { item: 'bloodmagic:corrupted_tinydust' }, chance: 0.25, mainchance: 0.0 }]
            }).id('kubejs:ore_magic/blood_raw_' + material)
            event.custom({
                type: 'hexerei:mixingcauldron', liquid: { fluid: 'minecraft:water' },
                ingredients: [raw, raw, raw, raw, { item: 'hexerei:selenite_shard' },
                    { item: 'hexerei:selenite_shard' }, { item: 'hexerei:selenite_shard' },
                    { item: 'hexerei:selenite_shard' }],
                output: { item: concentrate.item, count: 8 },
                liquidOutput: { fluid: 'minecraft:water' }, fluidLevelsConsumed: 250,
                heatRequirement: 'heated'
            }).id('kubejs:ore_magic/hexerei_raw_' + material)
            event.custom({
                type: 'ars_nouveau:crush', input: raw,
                output: [{ chance: 1.0, count: 2, item: concentrate.item, maxRange: 1 }],
                skip_block_place: false
            }).id('kubejs:ore_magic/ars_raw_' + material)
            event.custom({
                type: 'occultism:crushing', crushing_time: 200,
                ignore_crushing_multiplier: false, ingredient: raw,
                result: { item: concentrate.item, count: 2 }
            }).id('kubejs:ore_magic/occultism_raw_' + material)
        })

        event.remove({ type: 'create:splashing' })
        console.info('[ore-processing] Bulk Washing disabled; migrated ' + migratedSifting
            + ' granular recipes to waterlogged Sifting and ' + migratedSpouting
            + ' water treatments to Spouting; deleted/skipped ' + skipped)
    })
})()
