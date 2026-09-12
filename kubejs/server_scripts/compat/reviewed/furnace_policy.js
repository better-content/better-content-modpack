// kubejs/server_scripts/badfurnace.js
;(function () {
    var DEBUG = true
    var NUGGETS_ORE_RAW = 4
    var NUGGETS_RAW_BLOCK = 36
    var TYPES = ['minecraft:smelting', 'minecraft:blasting']
    var NUGGET_TAG_PREFIXES = ['forge:nuggets/', 'c:nuggets/']

    function log(msg) { if (DEBUG) console.log('[ore_to_nuggets] ' + msg) }
    function warn(msg) { console.log('[ore_to_nuggets] ' + msg) }

    function itemExists(id) {
        try { return !Item.of(id).isEmpty() } catch (e) { return false }
    }

    function endsWith(s, suf) {
        s = '' + s
        return s.length >= suf.length && s.substring(s.length - suf.length) === suf
    }

    function splitId(id) {
        var p = ('' + id).split(':')
        return { ns: p[0] || 'minecraft', path: p[1] || '' }
    }

    function getResultItemId(json) {
        if (!json || !json.has('result')) return null
            var el = json.get('result')
            if (!el) return null

                if (el.isJsonPrimitive && el.isJsonPrimitive()) {
                    try { return '' + el.getAsString() } catch (e1) { return null }
                }

                if (el.isJsonObject && el.isJsonObject()) {
                    var obj = el.getAsJsonObject()
                    if (obj.has('item')) {
                        try { return '' + obj.get('item').getAsString() } catch (e2) { return null }
                    }
                }

                return null
    }

    function pickNuggetId(base, preferredNs) {
        var i, tag, ids

        for (i = 0; i < NUGGET_TAG_PREFIXES.length; i++) {
            tag = '#' + NUGGET_TAG_PREFIXES[i] + base
            try {
                ids = Ingredient.of(tag).itemIds
                if (ids && ids.length) return '' + ids[0]
            } catch (e1) {}
        }

        var same = preferredNs + ':' + base + '_nugget'
        if (itemExists(same)) return same

            var mc = 'minecraft:' + base + '_nugget'
            if (itemExists(mc)) return mc

                return null
    }

    function tagPath(tagId) {
        var t = '' + tagId
        var parts = t.split(':')
        return parts.length > 1 ? parts[1] : parts[0]
    }

    function tagMatchesOreOrRaw(tagId, base) {
        try {
            var path = tagPath(tagId)
            if (path === (base + '_ores') || path === ('ores_' + base)) return true
            var seg = path.split('/')
            if (seg.length < 2) return false
                var kind = seg[seg.length - 2]
                var name = seg[seg.length - 1]
                if (name !== base) return false
                    return (kind === 'ores' || kind === 'raw_materials')
        } catch (e) { return false }
    }

    function tagMatchesRawBlock(tagId, base) {
        try {
            var seg = tagPath(tagId).split('/')
            if (seg.length < 2) return false
                var kind = seg[seg.length - 2]
                var name = seg[seg.length - 1]
                if (kind !== 'storage_blocks') return false
                    return (name === ('raw_' + base) || name === (base + '_raw'))
        } catch (e) { return false }
    }

    function itemMatchesOreOrRaw(itemId, base) {
        try {
            var p = splitId(itemId).path
            if (p === (base + '_ore')) return true
                if (p === ('deepslate_' + base + '_ore')) return true
                    if (p === ('raw_' + base)) return true
                        if (p === (base + '_raw')) return true
        } catch (e) {}
        return false
    }

    function itemMatchesRawBlock(itemId, base) {
        try {
            var p = splitId(itemId).path
            if (p === ('raw_' + base + '_block')) return true
                if (p === (base + '_raw_block')) return true
                    if (p === ('raw_' + base + '_bricks')) return true
                        if (p === (base + '_raw_bricks')) return true
        } catch (e) {}
        return false
    }

    // Returns: 'raw_block' | 'ore_raw' | null
    function ingredientKind(ingEl, base) {
        if (!ingEl) return null

            if (ingEl.isJsonArray && ingEl.isJsonArray()) {
                var arr = ingEl.getAsJsonArray()
                var i, k, sawOreRaw = false
                for (i = 0; i < arr.size(); i++) {
                    k = ingredientKind(arr.get(i), base)
                    if (k === 'raw_block') return 'raw_block'
                        if (k === 'ore_raw') sawOreRaw = true
                }
                return sawOreRaw ? 'ore_raw' : null
            }

            if (ingEl.isJsonObject && ingEl.isJsonObject()) {
                var obj = ingEl.getAsJsonObject()

                if (obj.has('item')) {
                    try {
                        var it = '' + obj.get('item').getAsString()
                        if (itemMatchesRawBlock(it, base)) return 'raw_block'
                            if (itemMatchesOreOrRaw(it, base)) return 'ore_raw'
                                return null
                    } catch (e1) { return null }
                }

                if (obj.has('tag')) {
                    try {
                        var tg = '' + obj.get('tag').getAsString()
                        if (tagMatchesRawBlock(tg, base)) return 'raw_block'
                            if (tagMatchesOreOrRaw(tg, base)) return 'ore_raw'
                                return null
                    } catch (e2) { return null }
                }
            }

            return null
    }

    // FIXED: build OR ingredients properly instead of Ingredient.of(''+jsonArray)
    function ingredientToKjs(ingEl) {
        if (!ingEl) return null

            if (ingEl.isJsonArray && ingEl.isJsonArray()) {
                try {
                    var arr = ingEl.getAsJsonArray()
                    var parts = []
                    var i
                    for (i = 0; i < arr.size(); i++) {
                        var sub = arr.get(i)
                        if (sub && sub.isJsonObject && sub.isJsonObject()) {
                            var obj = sub.getAsJsonObject()
                            if (obj.has('item')) parts.push('' + obj.get('item').getAsString())
                                else if (obj.has('tag')) parts.push('#' + obj.get('tag').getAsString())
                        }
                    }
                    if (parts.length === 0) return null
                        return Ingredient.of(parts)
                } catch (e0) { return null }
            }

            if (ingEl.isJsonObject && ingEl.isJsonObject()) {
                var obj2 = ingEl.getAsJsonObject()
                if (obj2.has('item')) {
                    try { return Ingredient.of('' + obj2.get('item').getAsString()) } catch (e1) { return null }
                }
                if (obj2.has('tag')) {
                    try { return Ingredient.of('#' + obj2.get('tag').getAsString()) } catch (e2) { return null }
                }
            }

            return null
    }

    ServerEvents.recipes(function (event) {
        var changed = 0
        var skippedUnsupportedResult = 0
        var skippedNotTarget = 0
        var skippedNoNugget = 0
        var skippedCantConvertIngredient = 0
        var errored = 0

        TYPES.forEach(function (typeId) {
            event.forEachRecipe({ type: typeId }, function (r) {
                var rid = '' + r.getId()
                var json = r.json

                try {
                    var resultId = getResultItemId(json)
                    if (!resultId) return

                        var res = splitId(resultId)
                        var base = null
                        if (endsWith(res.path, '_ingot')) {
                            base = res.path.substring(0, res.path.length - '_ingot'.length)
                        } else if (endsWith(res.path, '_block')) {
                            base = res.path.substring(0, res.path.length - '_block'.length)
                        } else {
                            skippedUnsupportedResult++
                            return
                        }

                        if (!json || !json.has('ingredient')) return
                            var ingEl = json.get('ingredient')

                            var kind = ingredientKind(ingEl, base)
                            if (!kind) { skippedNotTarget++; return }
                            if (endsWith(res.path, '_block') && kind !== 'raw_block') {
                                skippedNotTarget++
                                return
                            }

                            var nuggetId = pickNuggetId(base, res.ns)
                            if (!nuggetId) { skippedNoNugget++; return }

                            var ing = ingredientToKjs(ingEl)
                            if (!ing) { skippedCantConvertIngredient++; return }

                            var outCount = (kind === 'raw_block') ? NUGGETS_RAW_BLOCK : NUGGETS_ORE_RAW
                            var out = Item.of(nuggetId, outCount)

                            // Preserve the native timing and reward metadata while
                            // changing only the material yield.
                            event.remove({ id: rid })
                            var rebuilt = typeId === 'minecraft:smelting'
                                ? event.smelting(out, ing)
                                : event.blasting(out, ing)
                            if (json.has('experience')) rebuilt.xp(json.get('experience').getAsFloat())
                            if (json.has('cookingtime')) rebuilt.cookingTime(json.get('cookingtime').getAsLong())
                            if (json.has('group')) rebuilt.group(json.get('group').getAsString())
                            if (json.has('category')) rebuilt.merge({ category: json.get('category').getAsString() })
                            rebuilt.id(rid)

                                    changed++
                                    if (DEBUG) log(typeId + ' ' + rid + ' : ' + resultId + ' (' + kind + ') -> ' + outCount + 'x ' + nuggetId)
                } catch (e) {
                    errored++
                    warn('ERROR at ' + rid + ': ' + e)
                }
            })
        })

        log('done: changed=' + changed +
        ' skippedUnsupportedResult=' + skippedUnsupportedResult +
        ' skippedNotTarget=' + skippedNotTarget +
        ' skippedNoNugget=' + skippedNoNugget +
        ' skippedCantConvertIngredient=' + skippedCantConvertIngredient +
        ' errored=' + errored)
    })
})()

// Keep the canonical sand-tag glass recipe after mod datapacks narrow it.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'minecraft:glass' })
    event.smelting('minecraft:glass', '#minecraft:smelts_to_glass')
        .xp(0.1)
        .cookingTime(200)
        .id('minecraft:glass')
})
