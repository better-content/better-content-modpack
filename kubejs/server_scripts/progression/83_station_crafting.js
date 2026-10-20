// Crafting tables and TConstruct stations exchange freely, one item each way.
// Keep native alternative station crafts closed; no iron or pattern is consumed.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'tconstruct:tables/crafting_station' })
    event.remove({ id: 'tconstruct:tables/crafting_station_from_tables' })
    event.remove({ id: 'tconstruct:tables/crafting_station_from_logs' })
    event.shapeless('tconstruct:crafting_station', ['minecraft:crafting_table'])
        .id('kubejs:tech/stations/crafting_station_from_crafting_table')
    event.shapeless('minecraft:crafting_table', ['tconstruct:crafting_station'])
        .id('kubejs:tech/stations/crafting_table_from_crafting_station')
})
