// priority: -10000
// Add narrow counterparts AFTER ordinary progression scripts. Only the exact
// deep-dagger recipe's tier is changed (native first-input dispatch requires it).
// Supervisor materializes this projection
// only after validating the effective lineage snapshot, before server startup.
var BC_WORLD_AXIOM_IDS = ('iron_law eternal_engine alloy_testament captive_hearth ' +
    'ark seed_covenant patient_hive familiars_name cartographers_memory font_atlas prospectors_eye celestial_almanac ' +
    'communal_archive fortified_beginnings settled_arrival founders_exchange ' +
    'sacrificial_tradition spirit_awakening chalk_covenant patient_alchemist ' +
    'shaped_horizon turning_year deep_compact chosen_omen').split(' ')

function bcWorldAxiomSelection() {
    try {
        // read() returns a Java Map/List bridge in pinned KubeJS. Parse the
        // public string API so all strict predicates see native JS JSON types.
        var projection = JSON.parse(JsonIO.readString('kubejs/config/world_axioms_runtime.json'))
        if (projection == null) return []
        if (projection.schema !== 'world_axioms_runtime_v1') throw new Error('invalid projection schema')
        if (typeof projection.lineage !== 'string' || !projection.lineage.trim()) throw new Error('invalid projection lineage')
        if (typeof projection.transaction !== 'string') throw new Error('invalid projection transaction type')
        if (typeof projection.generation !== 'number' || !isFinite(projection.generation) ||
            Math.floor(projection.generation) !== projection.generation || projection.generation < 0)
            throw new Error('invalid projection generation')
        if (!Array.isArray(projection.selected)) throw new Error('invalid projection selected type')
        // Native/Python neutral projection is a closed seven-field shape. It
        // intentionally has no transaction/build and may never enable a perk.
        if (projection.generation === 0) {
            var neutralKeys = ['schema', 'lineage', 'generation', 'transaction', 'selected', 'configuration', 'build_sha256']
            if (Object.keys(projection).length !== neutralKeys.length ||
                !neutralKeys.every(function (key) { return Object.prototype.hasOwnProperty.call(projection, key) }))
                throw new Error('invalid neutral projection field set')
            if (projection.transaction !== '') throw new Error('invalid neutral projection transaction')
            if (projection.selected.length !== 0) throw new Error('invalid neutral projection selected')
            if (projection.build_sha256 !== '') throw new Error('invalid neutral projection build_sha256')
            if (projection.configuration == null || typeof projection.configuration !== 'object' ||
                Array.isArray(projection.configuration) || Object.keys(projection.configuration).length !== 0)
                throw new Error('invalid neutral projection configuration')
            return []
        }
        // A paid projection still requires its nonempty transaction identity.
        if (!projection.transaction.trim()) throw new Error('invalid active projection transaction')
        var selected = []
        projection.selected.forEach(function (id) {
            if (typeof id !== 'string' || BC_WORLD_AXIOM_IDS.indexOf(id) < 0 || selected.indexOf(id) >= 0)
                throw new Error('unknown or duplicate axiom')
            selected.push(id)
        })
        if (selected.length > Math.min(24, projection.generation)) throw new Error('projection over budget')
        return selected
    } catch (error) {
        console.error('[world_axioms] fail closed: ' + error)
        return []
    }
}

ServerEvents.recipes(function (event) {
    var selected = bcWorldAxiomSelection()
    function has(id) { return selected.indexOf(id) >= 0 }
    function custom(id, recipe) { event.custom(recipe).id('kubejs:world_axioms/' + id) }
    function cauldron(id, output, count, inputs, heated) {
        var recipe = {
            type: 'hexerei:mixingcauldron',
            liquid: { fluid: 'minecraft:water' },
            ingredients: inputs.map(function (item) { return { item: item } }),
            output: { item: output, count: count },
            liquidOutput: { fluid: 'minecraft:water' },
            fluidLevelsConsumed: 250
        }
        if (heated) recipe.heatRequirement = 'heated'
        custom(id, recipe)
    }
    if (has('iron_law')) {
        event.shapeless('4x kubejs:lineage_refractory_mix', [
            'better_ore_geology:hotstone', 'better_ore_geology:black_shale',
            '#kubejs:ordinary_sand', '#kubejs:ordinary_sand', 'minecraft:gravel', 'minecraft:gravel'
        ]).id('kubejs:world_axioms/iron_law/refractory_mix')
        event.smelting('tconstruct:seared_brick', 'kubejs:lineage_refractory_mix')
            .id('kubejs:world_axioms/iron_law/smelting')
        event.blasting('tconstruct:seared_brick', 'kubejs:lineage_refractory_mix')
            .id('kubejs:world_axioms/iron_law/blasting')
    }
    if (has('eternal_engine')) {
        event.shaped('create:water_wheel', ['PPP', 'PAP', 'PPP'], {
            P: '#minecraft:planks', A: 'create:andesite_alloy'
        }).id('kubejs:world_axioms/eternal_engine/small_water_wheel')
    }
    if (has('alloy_testament')) {
        // Pinned casting reads one output item, not a count. Approved physical
        // batch preserves native copper90 + zinc90 -> brass180 using ONE billet,
        // then ordinary unpacking to TWO ingots. The zinc cast is consumed.
        custom('alloy_testament/brass_billet', {
            type: 'tconstruct:casting_basin', cast: { item: 'create:zinc_ingot' }, cast_consumed: true,
            fluid: { tag: 'forge:molten_copper', amount: 90 },
            result: 'kubejs:brass_billet', cooling_time: 60
        })
        event.shapeless('2x create:brass_ingot', ['kubejs:brass_billet'])
            .id('kubejs:world_axioms/alloy_testament/unpack_billet')
    }
    if (has('captive_hearth')) {
        event.shaped('better_industrial_heat:thermal_firebox', ['BBB', 'BFB', 'BBB'], {
            B: 'minecraft:brick', F: 'minecraft:furnace'
        }).id('kubejs:world_axioms/captive_hearth/firebox')
    }
    if (has('sacrificial_tradition')) {
        // Native registrar selects FIRST matching sword before checking tier.
        // An additive duplicate is ambiguous. Approved correction changes ONLY
        // this effective recipe's zero-based tier field, retaining all costs.
        event.forEachRecipe({ id: 'kubejs:bloodmagic/deep_dagger_of_sacrifice' }, function (original) {
            original.serialize()
            original.merge({ upgradeLevel: 1 })
        })
    }
    if (has('spirit_awakening')) {
        event.forEachRecipe({ id: 'malum:spirit_altar' }, function (original) {
            // Flush prior KubeJS replaceInput changes before copying effective JSON.
            // Clone rather than weaken the ordinary recipe or hardcode its other costs.
            original.serialize()
            var recipe = JSON.parse(original.json.toString())
            var replaced = 0
            function replaceSlate(value) {
                if (value == null || typeof value !== 'object') return
                if (value.item === 'bloodmagic:blankslate') {
                    value.item = 'malum:wicked_spirit'
                    replaced++
                }
                Object.keys(value).forEach(function (key) { replaceSlate(value[key]) })
            }
            replaceSlate(recipe.key)
            replaceSlate(recipe.ingredients)
            if (replaced === 0) {
                console.error('[world_axioms] spirit_awakening: effective altar has no initial slate; counterpart omitted')
                return
            }
            // Pinned native altar has no stone key: the old pack's stone->shale
            // replacement was a no-op. Approved correction: add ONE shale in an
            // unused corner, keeping four planks, one soulstone and two spirits.
            var hasShale = Object.keys(recipe.key || {}).some(function (key) {
                return recipe.key[key].item === 'better_ore_geology:black_shale'
            })
            if (!hasShale) {
                if (!recipe.pattern || !recipe.key || recipe.key.B || recipe.pattern[0].charAt(0) !== ' ') {
                    console.error('[world_axioms] spirit_awakening: no safe shale corner; counterpart omitted')
                    return
                }
                recipe.key.B = { item: 'better_ore_geology:black_shale' }
                recipe.pattern[0] = 'B' + recipe.pattern[0].substring(1)
            }
            custom('spirit_awakening/altar', recipe)
        })
    }
    if (has('chalk_covenant')) {
        cauldron('chalk_covenant/white_impure', 'occultism:chalk_white_impure', 1, [
            'occultism:burnt_otherstone', 'occultism:otherworld_ashes',
            'better_ore_geology:black_shale', 'minecraft:amethyst_shard',
            'malum:sacred_spirit', 'malum:wicked_spirit'
        ], true)
    }
    if (has('patient_alchemist')) {
        event.shapeless('kubejs:amethyst_preparation', [
            'minecraft:redstone', 'minecraft:redstone',
            'minecraft:glowstone_dust', 'minecraft:glowstone_dust',
            'minecraft:amethyst_shard', 'minecraft:amethyst_shard'
        ]).id('kubejs:world_axioms/patient_alchemist/preparation')
        cauldron('patient_alchemist/moon_dust', 'hexerei:moon_dust', 4, [
            'kubejs:amethyst_preparation', 'aether:ambrosium_shard', 'aether:ambrosium_shard',
            'aether:zanite_gemstone', 'aether:zanite_gemstone'
        ], false)
    }
})
