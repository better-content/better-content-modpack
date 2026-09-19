// TECH-05: keep TConstruct's hand pattern as the station tool, but give the
// first station one direct, readable workshop route. The native table exchange
// is removed so a later table cannot silently bypass the hand-workshop step.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'tconstruct:tables/crafting_station' })
    event.remove({ id: 'tconstruct:tables/crafting_station_from_tables' })
    event.shaped('tconstruct:crafting_station', ['P', 'W'], {
        P: 'tconstruct:pattern', W: 'minecraft:crafting_table'
    }).id('kubejs:tech/stations/tconstruct_crafting_station_hand_route')
})
