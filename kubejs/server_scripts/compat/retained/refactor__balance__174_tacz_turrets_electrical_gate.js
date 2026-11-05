// Gate the TaCZ Turrets workbench, not a nonexistent turret item. The upstream
// recipe produces an NBT-marked TaCZ workbench; retain that BlockId on our result.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('tacz_turrets')) return

    event.remove({ id: 'tacz_turrets:turret_workbench' })
    event.shaped(Item.of('tacz:workbench_b', '{BlockId:"tacz_turrets:turret_workbench"}'), [
        'ICI',
        'RMR',
        'CSC'
    ], {
        I: 'kubejs:electrical_instrumentation_module',
        C: 'powergrid:conductive_casing',
        R: 'powergrid:redstone_relay',
        M: 'powergrid:electric_motor',
        S: 'kubejs:ae_logic_package'
    }).id('kubejs:tacz_turrets/electrical_turret')
})
