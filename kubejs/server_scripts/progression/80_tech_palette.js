// TECH-01: the first AE2 palette is a native-component bridge from the
// Nether and the meteor field. It gives Certus and the controller a visible
// material gate without creating an ME network or a player progression flag.
ServerEvents.recipes(function (event) {
    event.remove({ output: 'ae2:quartz_glass' })
    event.shaped('ae2:quartz_glass', ['QGQ', 'GCG', 'QGQ'], {
        Q: 'ae2:certus_quartz_crystal', G: '#forge:glass', C: 'create:electron_tube'
    }).id('kubejs:tech/ae2/quartz_glass_native_palette')

    event.remove({ output: 'ae2:energy_acceptor' })
    event.shaped('ae2:energy_acceptor', ['CPC', 'PEP', 'CPC'], {
        C: 'ae2:certus_quartz_crystal', P: 'powergrid:conductive_casing', E: 'powergrid:integrated_circuit'
    }).id('kubejs:tech/ae2/energy_acceptor_powergrid')

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

    event.remove({ output: 'ae2:cell_workbench' })
    event.shaped('ae2:cell_workbench', ['QGQ', 'CPC', 'QGQ'], {
        Q: 'ae2:quartz_glass', G: '#forge:glass', C: 'ae2:calculation_processor',
        P: 'powergrid:integrated_circuit'
    }).id('kubejs:tech/ae2/cell_workbench_native_components')
})
