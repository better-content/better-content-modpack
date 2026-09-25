// TECH-01: the first AE2 palette is a native-component bridge from the
// Nether and the meteor field. It gives Certus and the controller a visible
// material gate without creating an ME network or a player progression flag.
ServerEvents.recipes(function (event) {
    event.remove({ output: 'ae2:quartz_glass' })
    event.shaped('ae2:quartz_glass', ['QGQ', 'GCG', 'QGQ'], {
        Q: 'ae2:certus_quartz_crystal', G: '#forge:glass', C: 'create:electron_tube'
    }).id('kubejs:tech/ae2/quartz_glass_native_palette')

    event.remove({ output: 'ae2:controller' })
    event.custom({
        type: 'pneumaticcraft:pressure_chamber', pressure: 4.0,
        inputs: [
            global.bcPncrStack('ae2:sky_stone_block', 1),
            global.bcPncrStack('ae2:charged_certus_quartz_crystal', 1),
            global.bcPncrStack('ae2:engineering_processor', 1),
            global.bcPncrStack('powergrid:integrated_circuit', 1),
            global.bcPncrStack('create:electron_tube', 2)
        ],
        results: [{ item: 'ae2:controller' }]
    }).id('kubejs:tech/ae2/controller_meteor_core')


    // Parallel smart-component roots: processors are pressure-built from the
    // meteor/certus palette and PowerGrid controls, with no ME network access.
    event.remove({ output: 'ae2:logic_processor' })
    event.custom({
        type: 'pneumaticcraft:pressure_chamber', pressure: 3.0,
        inputs: [
            global.bcPncrStack('ae2:printed_silicon', 1),
            global.bcPncrStack('minecraft:gold_ingot', 1),
            global.bcPncrStack('minecraft:redstone', 1),
            global.bcPncrStack('create:electron_tube', 1),
            global.bcPncrStack('powergrid:integrated_circuit', 1)
        ],
        results: [{ item: 'ae2:logic_processor' }]
    }).id('kubejs:tech/ae2/logic_processor_pressure_palette')

    event.remove({ output: 'ae2:engineering_processor' })
    event.custom({
        type: 'pneumaticcraft:pressure_chamber', pressure: 4.0,
        inputs: [
            global.bcPncrStack('ae2:printed_silicon', 1),
            global.bcPncrStack('minecraft:diamond', 1),
            global.bcPncrStack('minecraft:redstone', 1),
            global.bcPncrStack('create:electron_tube', 1),
            global.bcPncrStack('powergrid:integrated_circuit', 1)
        ],
        results: [{ item: 'ae2:engineering_processor' }]
    }).id('kubejs:tech/ae2/engineering_processor_meteor_palette')

    event.remove({ output: 'ae2:cell_workbench' })
    event.shaped('ae2:cell_workbench', ['QGQ', 'CPC', 'QGQ'], {
        Q: 'ae2:quartz_glass', G: '#forge:glass', C: 'ae2:calculation_processor',
        P: 'powergrid:integrated_circuit'
    }).id('kubejs:tech/ae2/cell_workbench_native_components')

    // World reset authority remains in WLM's operator-gated interface. Its
    // authored recipe is replaced so that the interface itself also requires
    // material recovered from AE2 meteorites.
    event.remove({ output: 'world_lifecycle_manager:world_condenser_interface' })
    event.shaped('world_lifecycle_manager:world_condenser_interface', ['SBS', 'BOB', 'SBS'], {
        S: 'ae2:sky_stone_block', B: 'create:brass_sheet', O: 'minecraft:obsidian'
    }).id('kubejs:tech/world_condenser_interface_meteor_gate')
})
