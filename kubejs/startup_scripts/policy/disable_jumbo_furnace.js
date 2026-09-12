// Keep More Red's embedded dependency loaded, but do not advertise its
// unsupported Jumbo Furnace item in the creative inventory.
StartupEvents.modifyCreativeTab('minecraft:building_blocks', function (event) {
    event.remove('jumbofurnace:jumbo_furnace')
})
