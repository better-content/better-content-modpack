// TaCZ Turrets is a late electrical defense system. Replace its early vanilla
// iron recipe with the pack's electrical instrumentation and AE control parts.
ServerEvents.recipes(function (event) {
    if (!Platform.isLoaded('tacz_turrets')) return

    event.remove({ id: 'tacz_turrets:turret' })
    global.bcFactoryCrafting(event, 'kubejs:tacz_turrets/electrical_turret', 'tacz_turrets:turret', 1, [
        'ICI',
        'RMR',
        'CSC'
    ], {
        I: 'kubejs:electrical_instrumentation_module',
        C: 'powergrid:conductive_casing',
        R: 'powergrid:redstone_relay',
        M: 'powergrid:electric_motor',
        S: 'kubejs:ae_logic_package'
    })
})
