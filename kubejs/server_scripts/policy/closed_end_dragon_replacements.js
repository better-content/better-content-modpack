// Repeated vanilla dragon damage drops are disabled; one Quark scale per
// Ender Dragon defeat supplies TConstruct material progression. Elytra duplication
// spends Ice and Fire dragon scales, and cut-feature consumers stay removed.
ServerEvents.recipes(function (event) {
    event.replaceInput({}, 'minecraft:dragon_breath', '#forge:bloods/dragon')
    event.replaceInput({}, 'minecraft:dragon_head', '#forge:skulls/dragon')
    event.replaceInput({}, 'minecraft:dragon_egg', '#forge:eggs/dragon')

    // KubeJS's generic replacement does not traverse several custom recipe
    // serializers. Rebuild only the audited consumers from their own JSON and
    // preserve every field other than the closed vanilla-dragon ingredient.
    var customDragonConsumers = [
        'bloodmagic:flask/flask_lingering',
        'complicated_bees:temp_unit/dragon_breath',
        'goety:blade_of_ender',
        'goety:death_scythe',
        'goety:focus/blasting_focus',
        'goety:focus/flying_focus',
        'goety:focus/rupture_focus',
        'goety:philosophers_stone',
        'goety:ring_of_the_dragon',
        'goety:thrall/summon_blastling_thrall',
        'goety:void_robe',
        'goety:void_staff',
        'occultism:ritual/familiar_fairy',
        'occultism:ritual/possess_shulker',
        'tconstruct:tools/modifiers/worktable/enchantment_converting/unenchant_book',
        'tconstruct:tools/modifiers/worktable/enchantment_converting/unenchant_tool'
    ]

    function replaceClosedDragonIngredient(value) {
        if (value === null || value === undefined || typeof value !== 'object') return 0
        if (Array.isArray(value)) {
            var arrayChanges = 0
            value.forEach(function (entry) { arrayChanges += replaceClosedDragonIngredient(entry) })
            return arrayChanges
        }

        var changes = 0
        if (value.item === 'minecraft:dragon_breath') {
            delete value.item
            value.tag = 'forge:bloods/dragon'
            changes++
        } else if (value.item === 'minecraft:dragon_head') {
            delete value.item
            value.tag = 'forge:skulls/dragon'
            changes++
        } else if (value.item === 'minecraft:dragon_egg') {
            delete value.item
            value.tag = 'forge:eggs/dragon'
            changes++
        }
        Object.keys(value).forEach(function (key) { changes += replaceClosedDragonIngredient(value[key]) })
        return changes
    }

    customDragonConsumers.forEach(function (id) {
        event.forEachRecipe({ id: id }, function (recipe) {
            var data = JSON.parse('' + recipe.json)
            var changes = replaceClosedDragonIngredient(data)
            if (changes < 1) throw new Error('[closed-end] expected vanilla dragon ingredient in ' + id)
            event.remove({ id: id })
            event.custom(data).id(id)
        })
    })

    event.remove({ output: 'moreartifacts:hero_shield' })
    event.shaped('moreartifacts:hero_shield', ['SES', 'DHD', ' S '], {
        S: '#forge:ingots/steel', E: '#forge:eggs/dragon',
        D: '#forge:gems/diamond', H: 'minecraft:shield'
    }).id('kubejs:dragon_ecology/moreartifacts_hero_shield')

    event.remove({ output: 'minecraft:elytra' })
    // Quark drops one scale when the Ender Dragon's death animation completes.
    // Keep that finite boss reward as TConstruct's scale source while its
    // repeatable explosion-hit drop is disabled in the pack config.
    event.shapeless('tconstruct:dragon_scale', ['quark:dragon_scale'])
        .id('kubejs:dragon_ecology/tconstruct_scale_from_quark')
    event.custom({
        type: 'create:mechanical_crafting',
        pattern: ['ASA', 'PCP', 'ASA'],
        key: {
            A: { item: 'iceandfire:amphithere_feather' },
            S: { item: 'iceandfire:stymphalian_bird_feather' },
            P: { item: 'iceandfire:pixie_wings' },
            C: { item: 'minecraft:phantom_membrane' }
        },
        result: { item: 'minecraft:elytra' },
        acceptMirrored: false
    }).id('kubejs:dragon_ecology/mechanical_elytra')
    event.shaped('2x minecraft:elytra', ['S S', ' E ', 'S S'], {
        S: '#forge:scales/dragon', E: 'minecraft:elytra'
    }).id('kubejs:dragon_ecology/quark_elytra_duplication')

    event.remove({ output: 'createdeco:netherite_sheet' })
    event.custom({
        type: 'create:pressing',
        ingredients: [{ item: 'minecraft:netherite_ingot' }],
        results: [{ item: 'createdeco:netherite_sheet' }]
    }).id('kubejs:create_deco/netherite_sheet_restore')
})
