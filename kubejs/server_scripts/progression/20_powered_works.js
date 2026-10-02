// Machine Blocks prove an era only at its direct roots; downstream Create parts
// return immediately to native components.
ServerEvents.recipes(function (event) {
    // Direct roots consume Machine Blocks. Batch them so proving the era does not
    // recursively multiply every lower-tier casing for the rest of the Works spine.
    event.shaped('4x kubejs:andesite_machine_block', ['PAP', 'ASA', 'PAP'], {
        P: '#forge:plates/iron', A: 'create:andesite_alloy', S: 'tconstruct:seared_bricks'
    }).id('kubejs:powered_works/andesite_machine_block')

    event.remove({ id: 'create:crafting/kinetics/millstone' })
    event.shaped('create:millstone', [' C ', 'AMA', ' S '], {
        C: 'create:cogwheel', A: 'minecraft:andesite', M: 'kubejs:andesite_machine_block', S: 'minecraft:stone'
    }).id('kubejs:powered_works/direct_root/millstone')
    event.remove({ id: 'create:crafting/kinetics/mechanical_press' })
    event.shaped('create:mechanical_press', [' S ', ' M ', ' I '], {
        S: 'create:shaft', M: 'kubejs:andesite_machine_block', I: 'minecraft:iron_block'
    }).id('kubejs:powered_works/direct_root/mechanical_press')

    event.remove({ id: 'create:crafting/kinetics/mechanical_mixer' })
    event.shaped('create:mechanical_mixer', [' S ', ' A ', ' W '], {
        S: 'create:shaft', A: '#forge:plates/iron', W: 'create:whisk'
    }).id('kubejs:powered_works/mechanical_mixer')

    event.remove({ output: 'create:copper_casing' })
    event.custom({
        type: 'create:compacting',
        ingredients: [{ tag: 'forge:stripped_logs' }, { tag: 'forge:plates/copper' }],
        results: [{ item: 'create:copper_casing' }]
    }).id('kubejs:powered_works/copper_casing')
    event.custom({
        type: 'create:compacting',
        ingredients: [
            { item: 'kubejs:andesite_machine_block' }, { item: 'create:copper_casing' },
            { tag: 'forge:plates/copper' }, { tag: 'forge:plates/copper' },
            { tag: 'forge:plates/copper' }, { tag: 'forge:plates/copper' },
            { item: 'minecraft:nether_brick' }, { item: 'create:andesite_alloy' }, { item: 'create:andesite_alloy' }
        ],
        results: [{ item: 'kubejs:copper_machine_block', count: 4 }]
    }).id('kubejs:powered_works/copper_machine_block')

    ;[
        ['create:crafting/kinetics/water_wheel', 'create:water_wheel'],
        ['create:crafting/kinetics/large_water_wheel', 'create:large_water_wheel'],
        ['create:crafting/kinetics/windmill_bearing', 'create:windmill_bearing'],
        ['create:crafting/kinetics/mechanical_pump', 'create:mechanical_pump']
    ].forEach(function (root) {
        event.remove({ id: root[0] })
        event.shaped(root[1], [' A ', 'AMA', ' A '], {
            A: 'create:andesite_alloy', M: 'kubejs:copper_machine_block'
        }).id('kubejs:powered_works/direct_root/' + root[1].substring('create:'.length))
    })
})
