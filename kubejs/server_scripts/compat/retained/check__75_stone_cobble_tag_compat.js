// Rock is interchangeable only when it is a functional construction ingredient.
// Never broaden every recipe: slabs, stairs, walls, bricks, polishing, mossing,
// geology reassembly, and smelting must retain their native material identity.
// New recipes are opt-in by exact ID, not by namespace, output name, or recipe type.
var BC_FUNCTIONAL_ROCK_RECIPES = [
    // Vanilla mechanisms; native stone tools already use stone_tool_materials.
    'minecraft:comparator',
    'minecraft:dispenser',
    'minecraft:dropper',
    'minecraft:lever',
    'minecraft:observer',
    'minecraft:piston',
    'minecraft:repeater',
    'minecraft:stonecutter',
    'aether:skyroot_piston',
    // Workshop machinery and controls.
    'cold_sweat:boiler',
    'cold_sweat:smokestack',
    'create:crafting/kinetics/sticker',
    'create:crafting/logistics/powered_latch',
    'create:crafting/logistics/powered_toggle_latch',
    'create:crafting/logistics/pulse_extender',
    'create:crafting/logistics/pulse_repeater',
    'create:crafting/logistics/pulse_timer',
    'create:crafting/logistics/redstone_contact',
    'create:mechanical_crafting/crushing_wheel',
    'createsifter:sifter',
    'pneumaticcraft:elevator_caller',
    'prettypipes:low_low_priority_module',
    'prettypipes:medium_low_priority_module',
    'quark:automation/crafting/redstone_randomizer',
    'quark:tweaks/crafting/utility/misc/repeater',
    'rats:upgrades/disenchanter_upgrade',
    'sophisticatedstorage:controller',
    'sophisticatedstorage:storage_input',
    'sophisticatedstorage:storage_io',
    'sophisticatedstorage:storage_link',
    'sophisticatedstorage:storage_output',
    'supplementaries:crank',
    'supplementaries:relayer',
    'supplementaries:turn_table',
    'vs_eureka:ballast',
    'vs_eureka:engine',
    // Transport fixtures.
    'littlelogistics:barge_dock',
    'littlelogistics:guide_rail_corner',
    'littlelogistics:guide_rail_tug',
    'littlelogistics:tug_dock',
    'littlelogistics:vessel_detector',
    // Functional occult equipment, not slate/stone transmutation recipes.
    'ars_nouveau:deny_scroll',
    'bloodmagic:alchemy_table',
    'bloodmagic:arc',
    'bloodmagic:blood_rune_blank',
    'bloodmagic:blood_rune_capacity',
    'bloodmagic:blood_rune_displacement',
    'bloodmagic:blood_rune_orb',
    'bloodmagic:blood_rune_sacrifice',
    'bloodmagic:blood_rune_self_sacrifice',
    'bloodmagic:blood_rune_speed',
    'bloodmagic:incense_altar',
    'bloodmagic:primitive_furnace_cell',
    'bloodmagic:primitive_hydration_cell',
    'bloodmagic:soul_forge',
    'goety:creeper_totem',
    'goety:focus/barricade_focus',
    'goety:focus/earth_punch_focus',
    'goety:focus/eruption_focus',
    'goety:focus/ministrous_focus',
    'goety:focus/quaking_focus',
    'goety:frost_staff',
    // Stone weapons are functional tools, not geological output variants.
    'epicfight:stone_dagger',
    'epicfight:stone_greatsword',
    'epicfight:stone_longsword',
    'epicfight:stone_tachi'
]

ServerEvents.recipes(function (event) {
    BC_FUNCTIONAL_ROCK_RECIPES.forEach(function (id) {
        event.replaceInput({ id: id }, 'minecraft:stone', '#forge:stone')
        event.replaceInput({ id: id }, 'minecraft:cobblestone', '#forge:cobblestone')
    })
    event.shaped('minecraft:furnace', [
        'SSS',
        'S S',
        'SSS'
    ], {
        S: '#kubejs:furnace_materials'
    }).id('kubejs:crafting/furnace_from_stone_or_cobblestone')
})
