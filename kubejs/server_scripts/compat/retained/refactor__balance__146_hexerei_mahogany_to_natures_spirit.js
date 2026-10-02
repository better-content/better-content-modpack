function bcReplaceHexereiMahoganyInput(event, oldInput, newInput) {
    event.replaceInput({ mod: 'hexerei' }, oldInput, newInput)
    event.replaceInput({ output: /hexerei:.*/ }, oldInput, newInput)
}

ServerEvents.recipes(function (event) {
    // Nature's Spirit owns the canonical tree and ordinary wood set. Hexerei's
    // unique functional/decorative variants remain, but must consume the
    // Nature's Spirit material below.
    ;[
        'hexerei:mahogany_sapling',
        'hexerei:mahogany_leaves',
        'hexerei:mahogany_log',
        'hexerei:mahogany_wood',
        'hexerei:stripped_mahogany_log',
        'hexerei:stripped_mahogany_wood',
        'hexerei:mahogany_planks',
        'hexerei:mahogany_slab',
        'hexerei:mahogany_stairs',
        'hexerei:mahogany_button',
        'hexerei:mahogany_door',
        'hexerei:mahogany_fence',
        'hexerei:mahogany_fence_gate',
        'hexerei:mahogany_pressure_plate',
        'hexerei:mahogany_sign',
        'hexerei:mahogany_hanging_sign',
        'hexerei:mahogany_trapdoor',
        'hexerei:mahogany_boat',
        'hexerei:mahogany_chest_boat'
    ].forEach(function (item) { event.remove({ output: item }) })

    var mappings = [
        ['hexerei:mahogany_log', 'natures_spirit:mahogany_log'],
        ['hexerei:mahogany_wood', 'natures_spirit:mahogany_wood'],
        ['hexerei:stripped_mahogany_log', 'natures_spirit:stripped_mahogany_log'],
        ['hexerei:stripped_mahogany_wood', 'natures_spirit:stripped_mahogany_wood'],
        ['hexerei:mahogany_planks', 'natures_spirit:mahogany_planks'],
        ['#hexerei:mahogany_planks', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_slab', 'natures_spirit:mahogany_slab'],
        ['hexerei:mahogany_stairs', 'natures_spirit:mahogany_stairs'],
        ['hexerei:mahogany_button', 'natures_spirit:mahogany_button'],
        ['hexerei:mahogany_door', 'natures_spirit:mahogany_door'],
        ['hexerei:mahogany_fence', 'natures_spirit:mahogany_fence'],
        ['hexerei:mahogany_fence_gate', 'natures_spirit:mahogany_fence_gate'],
        ['hexerei:mahogany_pressure_plate', 'natures_spirit:mahogany_pressure_plate'],
        ['hexerei:mahogany_sign', 'natures_spirit:mahogany_sign'],
        ['hexerei:mahogany_hanging_sign', 'natures_spirit:mahogany_hanging_sign'],
        ['hexerei:mahogany_wall_sign', 'natures_spirit:mahogany_wall_sign'],
        ['hexerei:mahogany_wall_hanging_sign', 'natures_spirit:mahogany_wall_hanging_sign'],
        ['hexerei:mahogany_trapdoor', 'natures_spirit:mahogany_trapdoor'],
        ['hexerei:mahogany_boat', 'natures_spirit:mahogany_boat'],
        ['hexerei:mahogany_chest_boat', 'natures_spirit:mahogany_chest_boat'],

        // Hexerei-only decorative wood variants collapse to the closest pack-owned mahogany material.
        ['hexerei:mahogany_connected', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_window', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_window_pane', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_broom_stand', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_broom_stand_wall', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_chest', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_courier_depot', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_courier_depot_wall', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_drying_rack', 'natures_spirit:mahogany_planks'],
        ['hexerei:mahogany_woodcutter', 'natures_spirit:mahogany_planks'],
        ['hexerei:polished_mahogany_planks', 'natures_spirit:mahogany_planks'],
        ['hexerei:polished_mahogany_connected', 'natures_spirit:mahogany_planks'],
        ['hexerei:polished_mahogany_layered', 'natures_spirit:mahogany_planks'],
        ['hexerei:polished_mahogany_pillar', 'natures_spirit:mahogany_planks'],
        ['hexerei:polished_mahogany_slab', 'natures_spirit:mahogany_slab'],
        ['hexerei:polished_mahogany_stairs', 'natures_spirit:mahogany_stairs'],
        ['hexerei:polished_mahogany_button', 'natures_spirit:mahogany_button'],
        ['hexerei:polished_mahogany_door', 'natures_spirit:mahogany_door'],
        ['hexerei:polished_mahogany_fence', 'natures_spirit:mahogany_fence'],
        ['hexerei:polished_mahogany_fence_gate', 'natures_spirit:mahogany_fence_gate'],
        ['hexerei:polished_mahogany_pressure_plate', 'natures_spirit:mahogany_pressure_plate'],
        ['hexerei:polished_mahogany_sign', 'natures_spirit:mahogany_sign'],
        ['hexerei:polished_mahogany_trapdoor', 'natures_spirit:mahogany_trapdoor'],
        ['hexerei:waxed_mahogany_connected', 'natures_spirit:mahogany_planks'],
        ['hexerei:waxed_mahogany_window', 'natures_spirit:mahogany_planks'],
        ['hexerei:waxed_mahogany_window_pane', 'natures_spirit:mahogany_planks'],
        ['hexerei:waxed_polished_mahogany_connected', 'natures_spirit:mahogany_planks'],
        ['hexerei:waxed_polished_mahogany_layered', 'natures_spirit:mahogany_planks'],
        ['hexerei:waxed_polished_mahogany_pillar', 'natures_spirit:mahogany_planks']
    ]

    for (var i = 0; i < mappings.length; i++) {
         bcReplaceHexereiMahoganyInput(event, mappings[i][0], mappings[i][1])
    }
})
