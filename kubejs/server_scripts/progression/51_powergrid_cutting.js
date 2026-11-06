// Preserve PowerGrid's native saw work, one wooden slab and two-circuit yield.
// Create cutting has one item input, so pay a real plate when preparing the
// SAME wooden-slab species, then require that marked slab on the native saw.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('powergrid') || !Platform.isLoaded('better_manufacturing')) return

    var ids = Ingredient.of('#minecraft:wooden_slabs').itemIds
    if (!ids || !ids.length) throw new Error('PowerGrid cutting requires the loaded wooden-slab tag')
    var slabs = []
    for (var i = 0; i < ids.length; i++) slabs.push('' + ids[i])
    slabs.sort()
    var seen = {}
    slabs.forEach(function (slab) {
        // Sanitize namespace and path for a deterministic unique recipe ID;
        // a tag expansion collision must fail instead of losing a native slab.
        var key = slab.replace(/[^a-z0-9_./:-]/g, '_').replace(/[:/]/g, '_')
        if (seen[key]) throw new Error('PowerGrid slab recipe ID collision: ' + seen[key] + ' vs ' + slab)
        seen[key] = slab
        event.shapeless(Item.of(slab, '{better_manufacturing:{empty_circuit_slab:1}}'),
            [slab, 'tconstruct:large_plate'])
            .id('kubejs:powergrid_cutting/prepare/' + key)
    })
    // Forge's partial-NBT Ingredient accepts a list of concrete loaded items.
    // Keep PowerGrid's exact native cutting ID for every tagged slab species.
    event.remove({ id: 'powergrid:cutting/empty_circuit_slabs' })
    event.custom({
        type: 'create:cutting',
        ingredients: [{ type: 'forge:partial_nbt', items: slabs,
            nbt: { better_manufacturing: { empty_circuit_slab: 1 } } }],
        processingTime: 50,
        results: [{ item: 'powergrid:empty_circuit', count: 2 }]
    }).id('powergrid:cutting/empty_circuit_slabs')
})
