// TECH-05: the first TConstruct station is a direct workshop upgrade. The
// native table exchange is removed so a later table cannot silently bypass the
// ordinary material step, and no pattern item is consumed by the station.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'tconstruct:tables/crafting_station' })
    event.remove({ id: 'tconstruct:tables/crafting_station_from_tables' })
    event.shaped('tconstruct:crafting_station', [' I ', ' W '], {
        I: '#forge:plates/iron', W: 'minecraft:crafting_table'
    }).id('kubejs:tech/stations/tconstruct_crafting_station_hand_route')
})
