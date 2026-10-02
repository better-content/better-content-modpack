// Tome of Blood is treated as a post-AE2 hybrid combat-magic branch. Its native
// recipes are small Blood/Ars conversions, but the outputs let Ars casting spend
// LP, scale with Demon Will, and merge Living Armor with mage gear.

function bcTobExists(id) {
    try { return Item.exists(id) } catch (e) { return false }
}

function bcTobRemoveIds(event, ids) {
    for (var i = 0; i < ids.length; i++) event.remove({ id: ids[i] })
}

function bcTobInputExists(input) {
    if (!input) return false
    if (typeof input === 'string') return bcTobExists(input)
    if (input.item) return bcTobExists(input.item)
    return true
}

function bcTobInputsExist(inputs) {
    for (var i = 0; i < inputs.length; i++) {
        if (!bcTobInputExists(inputs[i])) return false
    }
    return true
}

function bcTobAlchemy(event, output, input, syphon, ticks, upgradeLevel, id) {
    if (!bcTobExists(output) || !bcTobInputsExist(input)) return
    event.custom({
        type: 'bloodmagic:alchemytable',
        input: input,
        output: { item: output },
        syphon: syphon,
        ticks: ticks,
        upgradeLevel: upgradeLevel
    }).id(id)
}

function bcTobGlyph(event, output, exp, inputItems, id) {
    if (!bcTobExists(output) || !bcTobInputsExist(inputItems)) return
    event.custom({
        type: 'ars_nouveau:glyph',
        count: 1,
        exp: exp,
        inputItems: inputItems.map(function (item) { return { item: { item: item } } }),
        output: output
    }).id(id)
}

function bcTobArmor(event, output, reagent, id) {
    if (!bcTobExists(output) || !bcTobExists(reagent) || !bcTobInputsExist([
        'ars_nouveau:magebloom_fiber',
        'bloodmagic:etherealslate',
        'kubejs:ae_logic_package',
        'kubejs:sky_steel_sheet',
        'kubejs:purified_source_core',
        'kubejs:living_binding'
    ])) return
    event.custom({
        type: 'ars_nouveau:enchanting_apparatus',
        keepNbtOfReagent: true,
        output: { item: output },
        pedestalItems: [
            { item: 'ars_nouveau:magebloom_fiber' },
            { item: 'bloodmagic:etherealslate' },
            { item: 'kubejs:ae_logic_package' },
            { item: 'kubejs:sky_steel_sheet' },
            { item: 'kubejs:purified_source_core' },
            { item: 'kubejs:living_binding' }
        ],
        reagent: [{ item: reagent }],
        sourceCost: 12000
    }).id(id)
}

ServerEvents.recipes(function (event) {
    if (!bcTobExists('tomeofblood:novice_tome_of_blood')) return
    bcTobRemoveIds(event, [
        'tomeofblood:altar/novice_blood_tome',
        'tomeofblood:altar/apprentice_blood_tome',
        'tomeofblood:altar/archmage_blood_tome',
        'tomeofblood:alchemytable/apprentice_tome',
        'tomeofblood:alchemytable/archmage_tome',
        'tomeofblood:glyph_sentient_harm',
        'tomeofblood:glyph_sentient_wrath',
        'tomeofblood:living_mage_hood',
        'tomeofblood:living_mage_robes',
        'tomeofblood:living_mage_leggings',
        'tomeofblood:living_mage_boots'
    ])

     bcTobAlchemy(event, 'kubejs:purified_blood_catalyst', [
        { item: 'bloodmagic:reinforcedslate' },
        { item: 'ars_nouveau:source_gem' },
        { item: 'minecraft:amethyst_shard' }
    ], 8000, 120, 2, 'kubejs:tomeofblood/alchemytable/purified_blood_catalyst')

     bcTobAlchemy(event, 'kubejs:living_binding', [
        { item: 'bloodmagic:etherealslate' },
        { item: 'bloodmagic:archmagebloodorb' },
        { item: 'ars_nouveau:magebloom_fiber' },
        { item: 'kubejs:purified_blood_catalyst' },
        { item: 'kubejs:soulstone_carbon_matrix' }
    ], 90000, 300, 5, 'kubejs:tomeofblood/alchemytable/living_binding')

    if (bcTobInputsExist([
        'ars_nouveau:source_gem_block',
        'ars_nouveau:wilden_tribute',
        'ars_nouveau:manipulation_essence',
        'bloodmagic:etherealslate',
        'kubejs:mountain_beryl_lens',
        'kubejs:corundum_lapping_grit',
        'kubejs:purified_blood_catalyst',
        'kubejs:ae_logic_package',
        'ars_nouveau:archmage_spell_book',
        'kubejs:purified_source_core'
    ])) {
        event.custom({
            type: 'ars_nouveau:enchanting_apparatus',
            keepNbtOfReagent: false,
            output: { item: 'kubejs:purified_source_core' },
            pedestalItems: [
                { item: 'ars_nouveau:source_gem_block' },
                { item: 'ars_nouveau:wilden_tribute' },
                { item: 'ars_nouveau:manipulation_essence' },
                { item: 'bloodmagic:etherealslate' },
                { item: 'kubejs:mountain_beryl_lens' },
                { item: 'kubejs:corundum_lapping_grit' },
                { item: 'kubejs:purified_blood_catalyst' },
                { item: 'kubejs:ae_logic_package' }
            ],
            reagent: [{ item: 'ars_nouveau:archmage_spell_book' }],
            sourceCost: 12000
        }).id('kubejs:tomeofblood/enchanting_apparatus/purified_source_core')
    }

     bcTobAlchemy(event, 'tomeofblood:novice_tome_of_blood', [
        { item: 'ars_nouveau:novice_spell_book' },
        { item: 'bloodmagic:archmagebloodorb' },
        { item: 'ae2:controller' },
        { item: 'kubejs:ae_logic_package' },
        { item: 'kubejs:purified_source_core' },
        { item: 'kubejs:living_binding' }
    ], 150000, 360, 5, 'kubejs:tomeofblood/alchemytable/novice_tome_post_ae2')

     bcTobAlchemy(event, 'tomeofblood:apprentice_tome_of_blood', [
        { item: 'tomeofblood:novice_tome_of_blood' },
        { item: 'ars_nouveau:apprentice_spell_book' },
        { item: 'ars_nouveau:wilden_tribute' },
        { item: 'bloodmagic:etherealslate' },
        { item: 'ae2:engineering_processor' },
        { item: 'kubejs:sky_steel_sheet' }
    ], 50000, 240, 4, 'kubejs:tomeofblood/alchemytable/apprentice_tome_post_ae2')

     bcTobAlchemy(event, 'tomeofblood:archmage_tome_of_blood', [
        { item: 'tomeofblood:apprentice_tome_of_blood' },
        { item: 'ars_nouveau:archmage_spell_book' },
        { item: 'minecraft:nether_star' },
        { item: 'bloodmagic:etherealslate' },
        { item: 'kubejs:impossible_support_matrix' },
        { item: 'better_chemlib_hazards:gas_reaction_chamber' }
    ], 100000, 320, 5, 'kubejs:tomeofblood/alchemytable/archmage_tome_post_ae2')

     bcTobGlyph(event, 'tomeofblood:glyph_sentient_harm', 80, [
        'bloodmagic:soulsword',
        'bloodmagic:etherealslate',
        'kubejs:ae_logic_package',
        'kubejs:purified_source_core'
    ], 'kubejs:tomeofblood/glyph_sentient_harm_post_ae2')

     bcTobGlyph(event, 'tomeofblood:glyph_sentient_wrath', 120, [
        'bloodmagic:soulscythe',
        'bloodmagic:throwing_dagger',
        'ars_nouveau:conjuration_essence',
        'bloodmagic:etherealslate',
        'kubejs:impossible_support_matrix',
        'better_chemlib_hazards:gas_reaction_chamber'
    ], 'kubejs:tomeofblood/glyph_sentient_wrath_post_ae2')

     bcTobArmor(event, 'tomeofblood:living_mage_hood', 'bloodmagic:livinghelmet', 'kubejs:tomeofblood/living_mage_hood_post_ae2')
     bcTobArmor(event, 'tomeofblood:living_mage_robes', 'bloodmagic:livingplate', 'kubejs:tomeofblood/living_mage_robes_post_ae2')
     bcTobArmor(event, 'tomeofblood:living_mage_leggings', 'bloodmagic:livingleggings', 'kubejs:tomeofblood/living_mage_leggings_post_ae2')
     bcTobArmor(event, 'tomeofblood:living_mage_boots', 'bloodmagic:livingboots', 'kubejs:tomeofblood/living_mage_boots_post_ae2')
})
