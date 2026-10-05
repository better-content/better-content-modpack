// Hexerei manufactures physical Occultism components; Occultism owns ritual
// activation, spirit fire, bindings, and summoned-servant gameplay.
//
// Hexerei is the preparation practice downstream of the Aether, Nether,
// and Bumblezone Fonts. Occultism still owns spirit fire,
// pentacles, named bindings, and the Foliot -> Djinni -> Afrit -> Marid ladder;
// Hexerei owns only the physical preparation of each impure chalk.

function bcFontIngredient(id) {
    if (id.charAt(0) === '#') return { tag: id.substring(1) }
    return { item: id }
}

function bcFontResult(id, count) {
    var result = { item: id }
    if (count && count > 1) result.count = count
    return result
}

function bcFontCanCraft(output, inputs) {
    try {
        if (!Item.exists(output)) return false
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].charAt(0) !== '#' && !Item.exists(inputs[i])) return false
        }
        return true
    } catch (e) {
        return false
    }
}

function bcFontCauldron(event, id, output, count, fluid, inputs, options) {
    if (!bcFontCanCraft(output, inputs)) return
    event.remove({ output: output })
    var recipe = {
        type: 'hexerei:mixingcauldron',
        liquid: { fluid: fluid },
        ingredients: inputs.map(bcFontIngredient),
        output: bcFontResult(output, count),
        liquidOutput: { fluid: fluid },
        fluidLevelsConsumed: options.fluidLevelsConsumed || 250
    }
    if (options.heatRequirement) recipe.heatRequirement = options.heatRequirement
    if (options.moonRequirement) recipe.moonRequirement = options.moonRequirement
    event.custom(recipe).id('kubejs:fonts_spine/hexerei_cauldron/' + id)
}

ServerEvents.recipes(function (event) {
    // Hexerei's crafting station is an overworld folk-crafting entry point.
    event.remove({ output: 'hexerei:mixing_cauldron' })
    event.shaped('hexerei:mixing_cauldron', [
        'III',
        'ICI',
        ' I '
    ], {
        I: 'minecraft:iron_ingot',
        C: 'minecraft:cauldron'
    }).id('kubejs:hexerei/overworld_mixing_cauldron')

    // Peer preparations retain distinct Font provenance instead of inheriting
    // Blood Magic's slate ladder.
    bcFontCauldron(event, 'moon_dust_aether', 'hexerei:moon_dust', 4, 'minecraft:water', [
        'minecraft:redstone',
        'minecraft:glowstone_dust',
        'aether:ambrosium_shard',
        'aether:zanite_gemstone',
        'minecraft:redstone',
        'minecraft:glowstone_dust',
        'aether:ambrosium_shard',
        'aether:zanite_gemstone'
    ], { moonRequirement: 'full_moon' })

    bcFontCauldron(event, 'infused_fabric_overworld', 'hexerei:infused_fabric', 2, 'minecraft:water', [
        'minecraft:black_dye',
        'minecraft:leather',
        'minecraft:string',
        'complicated_bees:beeswax',
        'minecraft:leather',
        'minecraft:string',
        'complicated_bees:beeswax',
        'minecraft:black_dye'
    ], {})

    bcFontCauldron(event, 'blood_sigil_nether', 'hexerei:blood_sigil', 1, 'minecraft:lava', [
        'minecraft:redstone',
        'minecraft:polished_blackstone',
        'minecraft:magma_cream',
        'minecraft:soul_soil',
        'minecraft:redstone',
        'minecraft:polished_blackstone',
        'minecraft:blaze_powder',
        'minecraft:nether_brick'
    ], { fluidLevelsConsumed: 333, heatRequirement: 'heated' })

    bcFontCauldron(event, 'crystal_ball_overworld', 'hexerei:crystal_ball', 1, 'minecraft:lava', [
        'minecraft:glass',
        'better_ore_geology:black_shale',
        'minecraft:glass',
        'minecraft:amethyst_shard',
        'hexerei:moon_dust',
        'minecraft:sculk_catalyst',
        'minecraft:glass',
        'better_ore_geology:black_shale'
    ], { heatRequirement: 'heated' })

    // Every impure chalk preparation proves all three expedition Fonts
    // equally: Aether ambrosium, Nether blaze powder, and Bumblezone honey
    // crystal. There is no Overworld or End substitute.
    bcFontCauldron(event, 'occultism_chalk_white_impure', 'occultism:chalk_white_impure', 1, 'minecraft:water', [
        'occultism:burnt_otherstone',
        'occultism:otherworld_ashes',
        'aether:ambrosium_shard',
        'minecraft:blaze_powder',
        'the_bumblezone:honey_crystal_shards'
    ], { heatRequirement: 'heated' })

    bcFontCauldron(event, 'occultism_chalk_gold_impure', 'occultism:chalk_gold_impure', 1, 'minecraft:water', [
        'occultism:chalk_white_impure',
        '#forge:dusts/gold',
        'aether:ambrosium_shard',
        'minecraft:blaze_powder',
        'the_bumblezone:honey_crystal_shards'
    ], { heatRequirement: 'heated' })

    bcFontCauldron(event, 'occultism_chalk_purple_impure', 'occultism:chalk_purple_impure', 1, 'minecraft:water', [
        'occultism:chalk_white_impure',
        '#forge:dusts/obsidian',
        'aether:ambrosium_shard',
        'minecraft:blaze_powder',
        'the_bumblezone:honey_crystal_shards'
    ], { heatRequirement: 'heated' })

    bcFontCauldron(event, 'occultism_chalk_red_impure', 'occultism:chalk_red_impure', 1, 'minecraft:lava', [
        'occultism:chalk_white_impure',
        'occultism:afrit_essence',
        'aether:ambrosium_shard',
        'minecraft:blaze_powder',
        'the_bumblezone:honey_crystal_shards'
    ], { fluidLevelsConsumed: 333, heatRequirement: 'heated' })

    // These are physical components/tools. Their ritual-derived ingredients
    // preserve Occultism's summoning and spirit-fire progression.
    ;[
        'occultism:spirit_attuned_crystal',
        'occultism:spirit_attuned_pickaxe_head',
        'occultism:book_of_binding_empty'
    ].forEach(function (output) { event.remove({ output: output }) })
    bcFontCauldron(event, 'occultism_spirit_attuned_crystal', 'occultism:spirit_attuned_crystal', 1, 'minecraft:water', [
        'occultism:spirit_attuned_gem', 'occultism:spirit_attuned_gem',
        'occultism:spirit_attuned_gem', 'occultism:spirit_attuned_gem'
    ], { heatRequirement: 'heated' })
    bcFontCauldron(event, 'occultism_spirit_attuned_pickaxe_head', 'occultism:spirit_attuned_pickaxe_head', 1, 'minecraft:water', [
        'occultism:spirit_attuned_gem', 'occultism:spirit_attuned_gem', 'occultism:spirit_attuned_gem'
    ], { heatRequirement: 'heated' })
    bcFontCauldron(event, 'occultism_empty_binding_book', 'occultism:book_of_binding_empty', 1, 'minecraft:water', [
        'occultism:awakened_feather', 'occultism:purified_ink', 'occultism:taboo_book'
    ], { heatRequirement: 'heated' })

    // Infinite-dimensional storage, remote access, stable wormholes, portable
    // storage, and miners are outside the finite-space/matter contract. The
    // dimensional matrix remains available as a finite ritual component.
    ;[
        'occultism:satchel', 'occultism:stable_wormhole',
        'occultism:storage_controller', 'occultism:storage_controller_base',
        'occultism:storage_remote', 'occultism:storage_remote_inert',
        'occultism:storage_stabilizer_tier1', 'occultism:storage_stabilizer_tier2',
        'occultism:storage_stabilizer_tier3', 'occultism:storage_stabilizer_tier4',
        'occultism:wormhole_frame', 'occultism:dimensional_mineshaft'
    ].forEach(function (output) { event.remove({ output: output }) })
})
