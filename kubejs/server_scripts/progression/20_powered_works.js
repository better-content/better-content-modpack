// Machine Blocks prove an era only at its direct roots; downstream Create parts
// return immediately to native components.
ServerEvents.recipes(function (event) {
    // Direct roots consume Machine Blocks. Batch them so proving the era does not
    // recursively multiply every lower-tier casing for the rest of the Works spine.
    event.shaped('4x kubejs:andesite_machine_block', ['PAP', 'ASA', 'PAP'], {
        P: '#forge:plates/iron', A: 'create:andesite_alloy', S: 'tconstruct:seared_bricks'
    }).id('kubejs:powered_works/andesite_machine_block')

    var partVariants = Platform.isLoaded('better_manufacturing')
    function variant(output, pattern, key, role, slot, operation, id) {
        if (!partVariants) { event.shaped(output, pattern, key).id(id); return }
        var ingredients = {}
        Object.keys(key).forEach(function (symbol) {
            var value = key[symbol]
            ingredients[symbol] = value.charAt(0) === '#' ? { tag: value.substring(1) } : { item: value }
        })
        event.custom({ type: 'better_manufacturing:part_variant', pattern: pattern, key: ingredients,
            result: { item: output }, function: 'better_manufacturing:create/' + operation,
            parts: [{ role: role, slot: slot }] }).id(id)
    }
    event.remove({ id: 'create:crafting/kinetics/millstone' })
    // Preserve the stone and iron block gates. The native whisk already carries
    // its configured material properties: consume and bind it directly.
    var millstoneKey = { C: 'create:cogwheel', A: 'minecraft:andesite',
        M: 'kubejs:andesite_machine_block', S: 'minecraft:stone' }
    if (partVariants) millstoneKey.H = 'tconstruct:large_plate'
    variant('create:millstone', partVariants ? [' C ', 'AMA', 'HS '] : [' C ', 'AMA', ' S '],
        millstoneKey, 'working_surface', 6, 'milling', 'kubejs:powered_works/direct_root/millstone')
    event.remove({ id: 'create:crafting/kinetics/mechanical_press' })
    var pressKey = { S: 'create:shaft', M: 'kubejs:andesite_machine_block', I: 'minecraft:iron_block' }
    if (partVariants) pressKey.H = 'tconstruct:large_plate'
    variant('create:mechanical_press', partVariants ? ['SH', 'M ', 'I '] : ['S', 'M', 'I'],
        pressKey, 'working_surface', 1, 'pressing', 'kubejs:powered_works/direct_root/mechanical_press')

    event.remove({ id: 'create:crafting/kinetics/mechanical_mixer' })
    variant('create:mechanical_mixer', ['S', 'A', 'W'], {
        S: 'create:shaft', A: '#forge:plates/iron', W: 'create:whisk'
    }, 'whisk', 2, 'mixing', 'kubejs:powered_works/mechanical_mixer')

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
        ['create:crafting/kinetics/windmill_bearing', 'create:windmill_bearing']
    ].forEach(function (root) {
        event.remove({ id: root[0] })
        var key = { A: 'create:andesite_alloy', M: 'kubejs:copper_machine_block' }
        if (partVariants) key.H = root[1] === 'create:windmill_bearing' ? 'tconstruct:tool_binding' : 'tconstruct:tool_handle'
        event.shaped(root[1], partVariants ? ['HA ', 'AMA', ' A '] : [' A ', 'AMA', ' A '], key)
            .id('kubejs:powered_works/direct_root/' + root[1].substring('create:'.length))
    })
    event.remove({ id: 'create:crafting/kinetics/mechanical_pump' })
    // Without Manufacturing, preserve the original four-alloy copper Machine Block root.
    if (!partVariants) {
        event.shaped('create:mechanical_pump', [' A ', 'AMA', ' A '], {
            A: 'create:andesite_alloy', M: 'kubejs:copper_machine_block'
        }).id('kubejs:powered_works/direct_root/mechanical_pump')
    } else {
        // Add the impeller and native fluid pipe in free corners; keep all
        // four original andesite alloys and the copper Machine Block.
        variant('create:mechanical_pump', ['HA ', 'AMA', ' AP'], {
            A: 'create:andesite_alloy', H: 'tconstruct:tool_handle',
            M: 'kubejs:copper_machine_block', P: 'create:fluid_pipe'
        }, 'impeller', 0, 'pumping', 'kubejs:powered_works/direct_root/mechanical_pump')
    }
})
