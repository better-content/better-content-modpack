// FOOD-03: Hexerei's drying rack is the authored food preservation surface.
// Heat Sync registers the dried outputs and tags them as dried_foods; these
// outputs have no furnace/smoker/campfire recipes, so drying cannot be folded
// back into a generic cooking loop.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('hexerei') || !Platform.isLoaded('heat_sync')) return

    var catalogue = [
        ['minecraft:beef', 'heat_sync:dried_beef', 'beef'],
        ['minecraft:porkchop', 'heat_sync:dried_porkchop', 'porkchop'],
        ['minecraft:chicken', 'heat_sync:dried_chicken', 'chicken'],
        ['minecraft:mutton', 'heat_sync:dried_mutton', 'mutton'],
        ['minecraft:rabbit', 'heat_sync:dried_rabbit', 'rabbit'],
        ['minecraft:cod', 'heat_sync:dried_cod', 'cod'],
        ['minecraft:salmon', 'heat_sync:dried_salmon', 'salmon']
    ]

    catalogue.forEach(function (entry) {
        event.remove({ output: entry[1] })
        event.custom({
            type: 'hexerei:drying_rack',
            ingredients: [{ item: entry[0] }],
            output: { item: entry[1] },
            dryingTimeInTicks: 2000
        }).id('kubejs:food/drying_rack/' + entry[2])
    })
})
