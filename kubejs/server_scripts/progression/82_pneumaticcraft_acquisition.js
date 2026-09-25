// Replace low-tier emerald-only service acquisition with authored pressure
// routes. The combined Drill+Laser program keeps its native combination recipe.
var BC_PNCR_SERVICE_OFFERS = [
    'pneumaticcraft:assembly_program_drill',
    'pneumaticcraft:assembly_program_laser',
    'pneumaticcraft:assembly_program_drill_laser',
    'pneumaticcraft:pcb_blueprint',
    'pneumaticcraft:printed_circuit_board',
    'pneumaticcraft:drone'
]

ServerEvents.recipes(function (event) {
    ;[
        'pneumaticcraft:amadron/assembly_program_drill',
        'pneumaticcraft:amadron/assembly_program_laser',
        'pneumaticcraft:amadron/assembly_program_drill_laser',
        'pneumaticcraft:amadron/pcb_blueprint'
    ].forEach(function (id) { event.remove({ id: id }) })

    // This is the bootstrap for laser assembly of unassembled PCBs. Its inputs
    // are available from the mechanical/Create and basic pressure roots; it
    // must not consume the printed PCB that the laser program helps produce.
    global.bcPncrPressure(event,
        'kubejs:tech/pneumatic/assembly_program_laser',
        'pneumaticcraft:assembly_program_laser', 1, 1.5, [
            'create:precision_mechanism',
            'create:electron_tube',
            'pneumaticcraft:pressure_tube',
            'create:brass_sheet'
        ])

    global.bcPncrPressure(event,
        'kubejs:tech/pneumatic/assembly_program_drill',
        'pneumaticcraft:assembly_program_drill', 1, 2.5, [
            'minecraft:diamond',
            'pneumaticcraft:pneumatic_cylinder',
            'pneumaticcraft:compressed_iron_gear',
            'create:precision_mechanism'
        ])

    // The blueprint creates the UV Light Box used for the first printed PCB.
    // Keep this bootstrap independent of PCBs, instrumentation modules, and
    // assembly machines so the initial board route has no recipe cycle.
    global.bcPncrPressure(event,
        'kubejs:tech/pneumatic/pcb_blueprint',
        'pneumaticcraft:pcb_blueprint', 1, 1.5, [
            'pneumaticcraft:pressure_tube',
            'create:electron_tube',
            'create:brass_sheet',
            'minecraft:paper'
        ])

    // PneumaticCraft's static-offer registry expects this recipe ID to remain
    // present. Keep the offer available only through pressure-era material,
    // instead of dropping the recipe and producing a missing-offer warning.
    event.custom({
        type: 'pneumaticcraft:amadron',
        id: 'pneumaticcraft:amadron/pcb_blueprint',
        input: { type: 'ITEM', amount: 4, id: 'pneumaticcraft:pressure_tube' },
        level: 0,
        output: { type: 'ITEM', amount: 1, id: 'pneumaticcraft:pcb_blueprint' },
        static: true
    }).id('pneumaticcraft:amadron/pcb_blueprint')
})

MoreJSEvents.villagerTrades(function (event) {
    BC_PNCR_SERVICE_OFFERS.forEach(function (item) {
        event.removeTrades(MoreJS.ofTradeFilter({
            firstItem: '*',
            secondItem: '*',
            outputItem: item
        }))
    })
})

// The pinned PneumaticCraft mechanic-house chest otherwise bypasses the
// pressure/electronics tablet recipe with a direct Amadron Tablet loot entry.
LootJS.modifiers(function (event) {
    event.addLootTableModifier('pneumaticcraft:chests/mechanic_house')
        .removeLoot('pneumaticcraft:amadron_tablet')
        // Advanced tubes are an assembly-laser output behind the authored
        // thermopneumatic plant gate, so mechanic-house loot cannot grant them.
        .removeLoot('pneumaticcraft:advanced_pressure_tube')
})
