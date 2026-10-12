// Narrow, exact-ID corrections for the recovered content families. This is not
// a runtime clash detector or an allocator. Pattern costs/results are preserved,
// and custom/stateful serializers are deliberately not rewritten by this pass.
var BC_CLASH_PATTERNS = {
    'better_drinking_water:rain_collector': ['###', ' # ', ' # '],
    'hexerei:polished_mahogany_door': ['# #', '# #', '# #'],
    'hexerei:polished_mahogany_fence': ['# #', ' ||', '# #'],
    'hexerei:polished_mahogany_fence_gate': ['||#', '||#'],
    'hexerei:polished_mahogany_pressure_plate': ['# ', ' #'],
    'hexerei:polished_mahogany_sign': ['###', '###', '|  '],
    'hexerei:polished_mahogany_slab': ['#', '#', '#'],
    'hexerei:polished_mahogany_stairs': ['###', '# #', ' # '],
    'hexerei:polished_mahogany_trapdoor': ['###', '#  ', '## '],
    'goety:end/chiseled_end_stone': ['##'],
    'cataclysm:chiseled_purpur_block': ['##'],
    'cataclysm:end_stone_pillar': ['##'],
    'immersive_weathering:prismarine_brick_wall': ['SSS', 'S S', ' S '],
    'supplementaries:stone_tile': ['000', ' 0 '],
    'untamedwilds:blocks/candle_from_fat': ['#S'],
    'dawnoftimebuilder:charred_spruce_roof_support': ['III', 'I I'],
    'dawnoftimebuilder:waxed_acacia_roof_support': ['III', 'I I'],
    'cold_sweat:boiler': ['CCC', 'DDD', 'C C'],
    'vs_eureka:ballast': ['#C#', 'C#C', 'C# '],
    'quark:building/crafting/furnaces/blackstone_furnace': ['# #', '###', '###'],
    'quark:building/crafting/furnaces/deepslate_furnace': ['###', ' ##', '###'],
    'cold_sweat:smokestack': ['CCC', 'C C', ' C '],
    'supplementaries:item_shelf': ['0 ', '00'],
    'createdeco:iron_support': ['b ', 'b ', 'bb'],
    'aether:skyroot_bucket': ['#', '#', '#'],
    'armoroftheages:bamboo_hat': ['I  ', 'III'],
    'createsifter:string_mesh': ['STS', 'SSS', 'SSS'],
    'everythingcopper:copper_bars': ['iii', 'i i', 'i  '],
    'everythingcopper:copper_door': ['iii', ' ii', '  i'],
    'everythingcopper:copper_trapdoor': ['iii', ' i '],
    'dawnoftimebuilder:candlestick': ['io'],
    'dawnoftimebuilder:cobbled_limestone': ['iii', ' i '],
    'dawnoftimebuilder:lattice_glass': ['III', ' I '],
    'dawnoftimebuilder:marble': ['III', ' I '],
    'dawnoftimebuilder:rammed_dirt': ['iii', ' i '],
    'dawnoftimebuilder:sake_cup': ['I', 'I', 'I'],
    'dawnoftimebuilder:sandstone_bricks': ['III', ' I '],
    'dawnoftimebuilder:spruce_low_table': ['III', ' ii'],
    'dawnoftimebuilder:waxed_oak_small_shutters': ['III', 'iIi'],
    'everythingcopper:copper_boots': ['i i', '   ', 'i i'],
    'everythingcopper:copper_chestplate': ['iii', 'iii', 'i i'],
    'everythingcopper:copper_helmet': ['iii', 'ii '],
    'everythingcopper:copper_leggings': ['iii', 'iii', ' i '],
    'fallout_wastelands_:blue_industrial_alloy_walkway_craft': ['a', 'a', 'a'],
    'malum:runewood_boards_wall': ['###', '# #', ' # '],
    'malum:soulwood_boards_wall': ['###', '# #', ' # '],
    'malum:runic_small_tainted_rock_bricks_wall': ['###', '# #', ' # '],
    'malum:runic_small_twisted_rock_bricks_wall': ['###', '# #', ' # '],
    'malum:runic_tainted_rock_bricks_wall': ['###', '# #', ' # '],
    'malum:runic_tainted_rock_tiles_wall': ['###', '# #', ' # '],
    'malum:runic_twisted_rock_bricks_wall': ['###', '# #', ' # '],
    'malum:runic_twisted_rock_tiles_wall': ['###', '# #', ' # '],
    'quark:building/crafting/gold_bars': ['###', '# #', ' # '],
    'the_deep_void:tendrils': ['aa', 'a ']
}

function bcClashCounts(pattern) {
    var counts = {}
    pattern.forEach(function (row) {
        for (var i = 0; i < row.length; i++) {
            var ch = row.charAt(i)
            if (ch !== ' ') counts[ch] = (counts[ch] || 0) + 1
        }
    })
    return JSON.stringify(Object.keys(counts).sort().map(function (ch) { return [ch, counts[ch]] }))
}

function bcClashNativeData(recipe, id) {
    // RecipeJS stores replacements in component values; serialize before reading
    // json or cloning it would silently discard preceding progression gates.
    recipe.serialize()
    var data = JSON.parse('' + recipe.json)
    if (data.type !== 'minecraft:crafting_shaped' && data.type !== 'minecraft:crafting_shapeless') {
        throw new Error('[recipe-clash] refusing nonstandard serializer for ' + id + ': ' + data.type)
    }
    return data
}

function bcClashRewrite(event, id, transform) {
    event.forEachRecipe({ id: id }, function (recipe) {
        var data = bcClashNativeData(recipe, id)
        transform(data)
        event.remove({ id: id })
        event.custom(data).id(id)
    })
}

function bcClashFinish(event, id, base, sticks, count) {
    bcClashRewrite(event, id, function (data) {
        // Only stateless decorative/material substrates. StrictNBTIngredient's
        // absent nbt field represents a pristine stack; named/state-bearing
        // inputs are not silently erased. No storage/equipment upgrade uses it.
        data.type = 'minecraft:crafting_shapeless'
        data.ingredients = [{ type: 'forge:nbt', item: base }]
        for (var i = 0; i < sticks; i++) data.ingredients.push({ item: 'minecraft:stick' })
        delete data.key
        delete data.pattern
        if (count !== undefined) data.result.count = count
    })
}

function bcClashCanonical(value) {
    if (Array.isArray(value)) return value.map(bcClashCanonical)
    if (value && typeof value === 'object') {
        var sorted = {}
        Object.keys(value).sort().forEach(function (key) { sorted[key] = bcClashCanonical(value[key]) })
        return sorted
    }
    return value
}

function bcClashCoalesce(event, ids) {
    var entries = []
    ids.forEach(function (id) {
        event.forEachRecipe({ id: id }, function (recipe) {
            var data = bcClashNativeData(recipe, id)
            if (!data.result || !data.result.item) throw new Error('[recipe-clash] unexpected duplicate result ' + id)
            var count = data.result.count || 1
            delete data.result.count
            entries.push({ id: id, count: count, semantics: JSON.stringify(bcClashCanonical(data)) })
        })
    })
    if (entries.length !== 2) return  // Never resurrect an absent/previously cut route.
    if (entries[0].semantics !== entries[1].semantics) {
        throw new Error('[recipe-clash] duplicate semantics differ: ' + ids.join(', '))
    }
    entries.sort(function (a, b) { return a.count - b.count || (a.id < b.id ? -1 : 1) })
    event.remove({ id: entries[1].id })
}

ServerEvents.recipes(function (event) {
    // Deliberately untouched: Cold Sweat purity recipes need native water/
    // temperature state copying, not static NBT results. Goety's two nine-slate
    // foci cannot gain a discriminator without reducing the shared Blood gate
    // or consuming a stateful focus. Neither is silently weakened here.
    // Explicit material-equivalent finishing keeps alternate forms reachable
    // even where worldgen/casting prefers the primary form. No global retagging,
    // nine-unit compression profit, or quarantined Fallout steel source.
    ;[
        ['chemlib_uranium_block', 'alexscaves:block_of_uranium', 'chemlib:uranium_metal_block'],
        ['iceandfire_raw_silver_block', 'occultism:raw_silver_block', 'iceandfire:raw_silver_block'],
        ['tconstruct_knightmetal_block', 'twilightforest:knightmetal_block', 'tconstruct:knightmetal_block']
    ].forEach(function (route) {
        if (!Item.exists(route[1]) || !Item.exists(route[2])) return
        event.custom({
            type: 'minecraft:crafting_shapeless',
            ingredients: [{ type: 'forge:nbt', item: route[1] }, { item: 'minecraft:stick' }],
            result: { item: route[2] }
        }).id('kubejs:recipe_clash_finish/' + route[0])
    })

    // Retain native direct compression; alternative cosmetic crates finish
    // the exact primary crate, retaining all nine produce units on round trips.
    ;['beetroot', 'carrot', 'potato'].forEach(function (crop) {
        bcClashFinish(event, 'quark:building/crafting/compressed/' + crop + '_crate', 'farmersdelight:' + crop + '_crate', 1)
    })
    bcClashFinish(event, 'quark:building/crafting/compressed/apple_crate', 'fruitsdelight:apple_crate', 1)
    bcClashRewrite(event, 'rats:assorted_vegetables', function (data) {
        if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 9) {
            throw new Error('[recipe-clash] unexpected assorted vegetables batch')
        }
        // Three independently named crop families, not nine interchangeable
        // slots in one vegetable union. Original nine-vegetable cost remains.
        data.ingredients = []
        ;['carrot', 'potato', 'beetroot'].forEach(function (crop) {
            for (var i = 0; i < 3; i++) data.ingredients.push({ tag: 'forge:crops/' + crop })
        })
    })
    bcClashFinish(event, 'hexerei:polished_mahogany_button', 'natures_spirit:mahogany_button', 1)
    bcClashFinish(event, 'dawnoftimebuilder:curved_raked_gravel', 'minecraft:gravel', 1, 1)
    bcClashFinish(event, 'dawnoftimebuilder:straight_raked_gravel', 'minecraft:gravel', 2, 1)
    bcClashFinish(event, 'tconstruct:common/materials/netherite_nugget_from_ingot', 'minecraft:netherite_ingot', 1)
    // One stick is already occupied by the ordinary torch recipe.
    bcClashFinish(event, 'quark:tweaks/crafting/utility/misc/charcoal_to_black_dye', 'minecraft:charcoal', 2)

    // Restore the window's native glass substrate rather than interpreting the
    // canonical mahogany input mapping as free glass. Six native windows were
    // three planks + three glass (two windows per plank/glass pair).
    bcClashRewrite(event, 'hexerei:mahogany_window_pane', function (data) {
        data.pattern = ['PGP', 'GPG']
        data.key = { P: { item: 'natures_spirit:mahogany_planks' }, G: { tag: 'forge:glass' } }
    })

    // Identical native serializers, predicates, actions and result state mean
    // the same item's remainder behavior applies to both recipes. Only the
    // count differs; no new wrench, damage or container behavior is invented.
    bcClashCoalesce(event, ['fallout_wastelands_:heavycalibercasingcraft', 'fallout_wastelands_:heavycalibercrafting'])
    bcClashCoalesce(event, ['the_deep_void:peeping_roots', 'the_deep_void:peeping_roots_wood'])

    Object.keys(BC_CLASH_PATTERNS).forEach(function (id) {
        bcClashRewrite(event, id, function (data) {
            var pattern = BC_CLASH_PATTERNS[id]
            if (data.type !== 'minecraft:crafting_shaped' || bcClashCounts(data.pattern) !== bcClashCounts(pattern)) {
                throw new Error('[recipe-clash] ingredient-count drift for ' + id)
            }
            data.pattern = pattern
        })
    })

    // The food discriminator is explicit and handmade, not a new machine gate.
    bcClashRewrite(event, 'ubesdelight:minecraft/crafting/leaf_feast', function (data) {
        if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 3) {
            throw new Error('[recipe-clash] unexpected leaf feast inputs')
        }
        data.ingredients.push({ item: 'farmersdelight:rice' })
    })

    function exclude(base, subtracted) {
        return { type: 'forge:difference', base: base, subtracted: subtracted }
    }
    event.forEachRecipe({ id: 'minecraft:charcoal' }, function (recipe) {
        recipe.serialize()
        var data = JSON.parse('' + recipe.json)
        if (data.type !== 'minecraft:smelting') throw new Error('[recipe-clash] unexpected charcoal station')
        data.ingredient = exclude(data.ingredient, [{ tag: 'malum:runewood_logs' }, { tag: 'malum:soulwood_logs' }])
        event.remove({ id: 'minecraft:charcoal' })
        event.custom(data).id('minecraft:charcoal')
    })
    // Charring retains its native one-log smoking/blasting choices and their
    // time/XP. A smoker is four logs around a furnace: no later metal gate.
    // Retire only the superseded competing furnace route. Campfire would lose
    // furnace XP, so it is not a safe substitute merely by retaining JSON fields.
    event.remove({ id: 'dawnoftimebuilder:charred_spruce_log_stripped_from_smelting' })

    event.forEachRecipe({ id: 'immersive_weathering:cracked_bricks' }, function (recipe) {
        recipe.serialize()
        var data = JSON.parse('' + recipe.json)
        if (data.type !== 'minecraft:smelting' || data.result !== 'immersive_weathering:cracked_bricks' ||
                data.cookingtime !== 200 || data.experience !== 0.1) {
            throw new Error('[recipe-clash] unexpected cracked-brick cooking semantics')
        }
        // The retained Create Deco furnace default already pays the same one
        // brick substrate, 200 ticks and 0.1 XP. Finishing does not award XP twice.
        data.type = 'minecraft:crafting_shapeless'
        data.ingredients = [{ type: 'forge:nbt', item: 'createdeco:cracked_red_bricks' }, { item: 'minecraft:stick' }]
        data.result = { item: 'immersive_weathering:cracked_bricks' }
        delete data.ingredient
        delete data.cookingtime
        delete data.experience
        event.remove({ id: 'immersive_weathering:cracked_bricks' })
        event.custom(data).id('immersive_weathering:cracked_bricks')
    })

    bcClashRewrite(event, 'minecraft:stick', function (data) {
        data.key['#'] = exclude({ tag: 'minecraft:planks' }, { tag: 'aether:skyroot_stick_crafting' })
    })
    ;['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
        'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'].forEach(function (color) {
        bcClashRewrite(event, 'minecraft:' + color + '_bed', function (data) {
            data.key.X = exclude({ tag: 'minecraft:planks' }, { tag: 'aether:planks_crafting' })
            data.key['#'] = { item: 'minecraft:' + color + '_wool' }
        })
    })
    var tiers = ['', 'copper_', 'iron_', 'gold_', 'diamond_', 'netherite_']
    bcClashRewrite(event, 'minecraft:shulker_box', function (data) {
        data.key['#'] = exclude({ tag: 'forge:chests' }, tiers.map(function (tier) {
            return { item: 'sophisticatedstorage:' + tier + 'chest' }
        }))
    })
    ;['alexscaves:metal_barrel', 'alexscaves:metal_barrel_from_iron'].forEach(function (id) {
        bcClashRewrite(event, id, function (data) {
            data.key.B = exclude({ tag: 'forge:barrels/wooden' }, tiers.map(function (tier) {
                return { item: 'sophisticatedstorage:' + tier + 'barrel' }
            }))
        })
    })
    bcClashRewrite(event, 'goety:dark_altar_stone', function (data) {
        data.key.s = exclude(data.key.s, ['blackstone', 'deepslate', 'highrock', 'ominous_stone'].map(function (family) {
            return { tag: 'goety:full_' + family + '_craft' }
        }))
    })
    bcClashRewrite(event, 'goety:shade/shade_stone_craft', function (data) {
        data.key.s = exclude({ tag: 'forge:stone' }, { tag: 'forge:sandstone' })
    })
    bcClashRewrite(event, 'supplementaries:stone_lamp', function (data) {
        data.key['0'] = exclude({ tag: 'forge:stone' }, [
            { item: 'minecraft:blackstone' }, { item: 'minecraft:deepslate' }
        ])
    })

    // Restore exact source identities after broad upstream/pack material aliases.
    // These are reversible material products, not functional construction uses.
    var identityKeys = {
        'minecraft:slime_block': { '#': { item: 'minecraft:slime_ball' } },
        'tconstruct:common/slime/ender/slimeblock': { '#': { item: 'tconstruct:ender_slime_ball' } },
        'tconstruct:common/slime/ichor/slimeblock': { '#': { item: 'tconstruct:ichor_slime_ball' } },
        'tconstruct:common/slime/sky/slimeblock': { '#': { item: 'tconstruct:sky_slime_ball' } },
        'alexscaves:uranium_from_shard': { N: { item: 'alexscaves:uranium_shard' } },
        'chemlib:uranium_nugget_to_ingot': { N: { item: 'chemlib:uranium_nugget' } },
        'occultism:crafting/raw_silver_block': { p: { item: 'occultism:raw_silver' } },
        'iceandfire:raw_silver_to_raw_silver_block': { '#': { item: 'iceandfire:raw_silver' } },
        // Fallout steel ingots are already quarantined. Do not reopen that
        // material or turn its residual recipes into unreachable new routes.
        'tconstruct:common/materials/steel_block_from_ingots': {
            '#': { item: 'tconstruct:steel_ingot' }, '*': { item: 'tconstruct:steel_ingot' }
        },
        'tconstruct:common/materials/knightmetal_block_from_ingots': {
            '#': { item: 'tconstruct:knightmetal_ingot' }, '*': { item: 'tconstruct:knightmetal_ingot' }
        },
        'twilightforest:compressed_blocks/knightmetal_block': { '#': { item: 'twilightforest:knightmetal_ingot' } }
    }
    Object.keys(identityKeys).forEach(function (id) {
        bcClashRewrite(event, id, function (data) {
            Object.keys(identityKeys[id]).forEach(function (key) {
                if (!data.key || !data.key[key]) throw new Error('[recipe-clash] missing identity key ' + id + '/' + key)
                data.key[key] = identityKeys[id][key]
            })
        })
    })
    var identitySingles = {
        'alexscaves:uranium_from_block': 'alexscaves:block_of_uranium',
        'chemlib:uranium_block_to_ingot': 'chemlib:uranium_metal_block',
        'occultism:crafting/raw_silver_ingot_from_block': 'occultism:raw_silver_block',
        'iceandfire:raw_silver_block_to_raw_silver': 'iceandfire:raw_silver_block',
        'tconstruct:common/materials/steel_nugget_from_ingot': 'tconstruct:steel_ingot',
        'tconstruct:common/materials/knightmetal_ingot_from_block': 'tconstruct:knightmetal_block',
        'twilightforest:compressed_blocks/reversed/knightmetal_block_ingot': 'twilightforest:knightmetal_block'
    }
    Object.keys(identitySingles).forEach(function (id) {
        bcClashRewrite(event, id, function (data) {
            if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 1) {
                throw new Error('[recipe-clash] unexpected identity decomposition ' + id)
            }
            data.ingredients = [{ item: identitySingles[id] }]
        })
    })

    // Keep the native fine-wood honeycomb ring. Both exact-family wax batches
    // preserve eight logs/one honeycomb/eight outputs, with the wax applicator
    // at a corner rather than the competing generic recipe's center.
    ;['dawnoftimebuilder:waxed_acacia_log_stripped', 'dawnoftimebuilder:waxed_oak_log_stripped'].forEach(function (id) {
        bcClashRewrite(event, id, function (data) {
            if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 9 ||
                    !data.ingredients.slice(1).every(function (v) { return JSON.stringify(v) === JSON.stringify(data.ingredients[1]) })) {
                throw new Error('[recipe-clash] unexpected wax batch ' + id)
            }
            data.type = 'minecraft:crafting_shaped'
            data.pattern = ['HWW', 'WWW', 'WWW']
            data.key = { H: data.ingredients[0], W: data.ingredients[1] }
            delete data.ingredients
        })
    })
    bcClashRewrite(event, 'natures_spirit:sandy_soil', function (data) {
        if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 4) {
            throw new Error('[recipe-clash] unexpected sandy soil inputs')
        }
        data.type = 'minecraft:crafting_shaped'
        data.pattern = ['DD', 'SS']
        data.key = { D: data.ingredients[0], S: data.ingredients[2] }
        delete data.ingredients
    })

    // The native cells are ordinary shapeless recipes, not state-copying upgrade
    // serializers. Preserve their four effective ingredients (including the
    // already-applied formal proof) and vanilla assembly/remainder semantics.
    ;[
        ['arseng:portable_source_cell_64k', ['AB', 'CD']],
        ['arseng:portable_source_cell_256k', ['AC', 'DB']]
    ].forEach(function (cell) {
        bcClashRewrite(event, cell[0], function (data) {
            if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 4) {
                throw new Error('[recipe-clash] unexpected portable cell inputs ' + cell[0])
            }
            data.type = 'minecraft:crafting_shaped'
            data.pattern = cell[1]
            data.key = { A: data.ingredients[0], B: data.ingredients[1], C: data.ingredients[2], D: data.ingredients[3] }
            delete data.ingredients
        })
    })

    ;['fire', 'ice', 'lightning'].forEach(function (kind) {
        bcClashRewrite(event, 'iceandfire:banners/banner_pattern_' + kind + '_head', function (data) {
            if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 2) {
                throw new Error('[recipe-clash] unexpected typed dragon banner inputs')
            }
            data.type = 'minecraft:crafting_shaped'
            data.pattern = ['S', 'P']
            data.key = { S: data.ingredients[0], P: data.ingredients[1] }
            delete data.ingredients
        })
    })
    bcClashRewrite(event, 'supplementaries:dragon_banne_pattern', function (data) {
        if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 2) {
            throw new Error('[recipe-clash] unexpected generic dragon banner inputs')
        }
        data.type = 'minecraft:crafting_shaped'
        data.pattern = ['PS']
        data.key = { P: data.ingredients[0], S: data.ingredients[1] }
        delete data.ingredients
    })

    // Both moss/vine routes are changed together. Keeping either shapeless
    // opponent would still capture every arrangement of the same two inputs.
    ;[
        ['createdeco:mossy_red_bricks_from_moss_block', ['AB']],
        ['createdeco:mossy_red_bricks_from_vine', ['AB']],
        ['immersive_weathering:mossy_bricks_from_moss_block', ['A', 'B']],
        ['immersive_weathering:mossy_bricks_from_vine', ['A', 'B']]
    ].forEach(function (mossing) {
        bcClashRewrite(event, mossing[0], function (data) {
            if (data.type !== 'minecraft:crafting_shapeless' || data.ingredients.length !== 2) {
                throw new Error('[recipe-clash] unexpected mossing inputs ' + mossing[0])
            }
            data.type = 'minecraft:crafting_shaped'
            data.pattern = mossing[1]
            data.key = { A: data.ingredients[0], B: data.ingredients[1] }
            delete data.ingredients
        })
    })
})
