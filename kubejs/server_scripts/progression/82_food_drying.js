// FOOD-03: Hexerei's drying rack is the authored food preservation surface.
// Heat Sync registers the dried outputs and tags them as dried_foods; these
// outputs have no furnace/smoker/campfire recipes, so drying cannot be folded
// back into a generic cooking loop.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('hexerei') || !Platform.isLoaded('better_industrial_heat')) return

    var catalogue = [
        ['minecraft:beef', 'better_industrial_heat:dried_beef', 'beef'],
        ['minecraft:porkchop', 'better_industrial_heat:dried_porkchop', 'porkchop'],
        ['minecraft:chicken', 'better_industrial_heat:dried_chicken', 'chicken'],
        ['minecraft:mutton', 'better_industrial_heat:dried_mutton', 'mutton'],
        ['minecraft:rabbit', 'better_industrial_heat:dried_rabbit', 'rabbit'],
        ['minecraft:cod', 'better_industrial_heat:dried_cod', 'cod'],
        ['minecraft:salmon', 'better_industrial_heat:dried_salmon', 'salmon']
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
