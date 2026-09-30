// Native magic-spine gates plus Ars formal-depth crossings.
// Ars accepts proof from any developed magic discipline; Blood does not parent it.

var BM_SLATE_T1 = 'bloodmagic:blankslate'
var BM_SLATE_T2 = 'bloodmagic:reinforcedslate'
var BM_SLATE_T3 = 'bloodmagic:infusedslate'
var BM_SLATE_T4 = 'bloodmagic:demonslate'
var BM_SLATE_T5 = 'bloodmagic:etherealslate'
var BC_OVERWORLD_OCCULT_T1 = 'better_ore_geology:black_shale'
var BC_OVERWORLD_OCCULT_T4 = 'minecraft:sculk_catalyst'

function gate(event, filter, oldInput, newInput) {
    event.replaceInput(filter, oldInput, newInput)
}

ServerEvents.recipes(function (event) {
    // Altar I: first dirty-magic proofs.
    gate(event, { id: 'malum:spirit_altar' }, '#forge:ingots/gold', BM_SLATE_T1)
    gate(event, { id: 'malum:spirit_altar' }, '#forge:stone', BC_OVERWORLD_OCCULT_T1)

    // Altar III: dangerous bridge magic, spirits, and servants.
    // Tome of Blood moved to the post-AE2 hybrid branch in
    // 166_tome_of_blood_post_ae2_gates.js.

    // Formal depth I: Source begins after any magic discipline supplies real proof.
    gate(event, { output: 'ars_nouveau:source_gem' }, 'minecraft:amethyst_shard', '#kubejs:formal_magic/proof/common')
    gate(event, { output: 'ars_nouveau:source_gem_block' }, 'minecraft:amethyst_block', '#kubejs:formal_magic/proof/common')
    gate(event, { id: 'ars_nouveau:imbuement_chamber' }, '#forge:ingots/gold', '#kubejs:formal_magic/proof/uncommon')

    // Formal depth II: apparatus and Elemental foci use rare proof on native surfaces.
    gate(event, { id: 'ars_nouveau:enchanting_apparatus' }, '#forge:ingots/gold', '#kubejs:formal_magic/proof/rare')
    gate(event, { id: 'ars_elemental:imbuement_lesser_air_focus' }, 'minecraft:gold_ingot', '#kubejs:formal_magic/proof/rare')
    gate(event, { id: 'ars_elemental:imbuement_lesser_earth_focus' }, 'minecraft:gold_ingot', '#kubejs:formal_magic/proof/rare')
    gate(event, { id: 'ars_elemental:imbuement_lesser_fire_focus' }, 'minecraft:gold_ingot', '#kubejs:formal_magic/proof/rare')
    gate(event, { id: 'ars_elemental:imbuement_lesser_water_focus' }, 'minecraft:gold_ingot', '#kubejs:formal_magic/proof/rare')
    gate(event, { id: 'goety:cursed_cage' }, 'goety:cursed_bars', BM_SLATE_T4)
    gate(event, { id: 'goety:cursed_cage' }, 'minecraft:sculk', BC_OVERWORLD_OCCULT_T4)
    gate(event, { id: 'goety:dark_altar' }, 'goety:magic_emerald', BM_SLATE_T4)
    gate(event, { id: 'goety:dark_altar' }, 'minecraft:sculk_catalyst', BC_OVERWORLD_OCCULT_T4)

    // Formal depth III: Creo is the Create-era application of formal motion.
    gate(event, { id: 'ars_creo:starbuncle_wheel' }, 'create:water_wheel', '#kubejs:formal_magic/proof/epic')

    // Formal depth V: Énergistique is the post-AE2 Source-network capstone.
    gate(event, { id: 'arseng:me_source_jar' }, 'ars_nouveau:manipulation_essence', '#kubejs:formal_magic/proof/legendary')
    gate(event, { id: 'arseng:source_acceptor' }, 'ars_nouveau:source_gem_block', '#kubejs:formal_magic/proof/legendary')
    gate(event, { id: 'arseng:source_cell_housing' }, 'ars_nouveau:manipulation_essence', '#kubejs:formal_magic/proof/legendary')
})
