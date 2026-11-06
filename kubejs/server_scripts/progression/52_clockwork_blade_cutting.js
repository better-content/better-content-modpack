// Keep Clockwork's native Create saw work and output counts. The two BM-only
// source crafts pay one real broad blade and tough handle for each resulting
// blade while retaining the input plank species and each pair's material rate.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('vs_clockwork') || !Platform.isLoaded('better_manufacturing')) return

    var ids = Ingredient.of('#minecraft:planks').itemIds
    if (!ids || !ids.length) throw new Error('Clockwork blade cutting requires the loaded plank tag')
    var planks = []
    for (var i = 0; i < ids.length; i++) planks.push('' + ids[i])
    planks.sort()

    ;[
        ['propeller_blade', 3],
        ['wide_propeller_blade', 2]
    ].forEach(function (entry) {
        var name = entry[0]
        var count = entry[1]
        var id = 'vs_clockwork:cutting/' + name
        event.remove({ id: id })
        event.custom({
            type: 'create:cutting',
            ingredients: [{ type: 'forge:partial_nbt', items: planks,
                nbt: { 'better_manufacturing:clockwork_blade_cutting': { output: 'vs_clockwork:' + name } } }],
            processingTime: 100,
            results: [{ item: 'vs_clockwork:' + name, count: count }]
        }).id(id)
    })
})
