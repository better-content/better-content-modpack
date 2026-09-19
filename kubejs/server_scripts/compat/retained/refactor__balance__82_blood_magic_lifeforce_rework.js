// Installed Heart Fragments are the early LP/t path. Other Blood Magic LP
// throughput tools are pushed deeper.

ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('bloodmagic')) return

    event.remove({ id: 'bloodmagic:blood_altar' })
    event.shaped('bloodmagic:altar', [
        'OGO',
        'BFB',
        'DCD'
    ], {
        O: 'minecraft:obsidian',
        G: 'minecraft:gold_block',
        B: 'minecraft:bone_block',
        F: 'minecraft:furnace',
        D: 'minecraft:deepslate',
        C: 'minecraft:cauldron'
    }).id('kubejs:bloodmagic/heart_bound_blood_altar')

    if (Platform.isLoaded('rpg_stats')) {
        // The one fragment in this craft remains installed in the block item.
        event.shaped(Item.of('rpg_stats:heart_block', '{BlockEntityTag:{id:"rpg_stats:heart_block",Fragments:1L}}'), [
            'BIB',
            'IHI',
            'BIB'
        ], {
            B: 'minecraft:bone_block',
            I: 'minecraft:iron_ingot',
            H: 'rpg_stats:heart_fragment'
        }).id('kubejs:bloodmagic/first_heart_block')
    }

    if (event.recipes.bloodmagic && event.recipes.bloodmagic.altar) {
        event.remove({ id: 'bloodmagic:altar/daggerofsacrifice' })
        event.recipes.bloodmagic
            .altar('bloodmagic:daggerofsacrifice', 'minecraft:diamond_sword')
            .upgradeLevel(4)
            .altarSyphon(120000)
            .consumptionRate(80)
            .drainRate(80)
            .id('kubejs:bloodmagic/deep_dagger_of_sacrifice')
    }

    event.replaceInput({ id: 'bloodmagic:blood_rune_sacrifice' }, 'bloodmagic:blankslate', 'bloodmagic:demonslate')
    event.replaceInput({ id: 'bloodmagic:blood_rune_sac_2' }, 'bloodmagic:reinforcedslate', 'bloodmagic:etherealslate')
    event.replaceInput({ id: 'bloodmagic:blood_rune_self_sacrifice' }, 'bloodmagic:blankslate', 'bloodmagic:infusedslate')
    event.replaceInput({ id: 'bloodmagic:blood_rune_self_sac_2' }, 'bloodmagic:reinforcedslate', 'bloodmagic:demonslate')
})
