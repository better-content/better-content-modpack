var BC_QUARANTINE_POLICY = JsonIO.read('kubejs/config/quarantined_items.json') || { schema: '', items: [] }
if (BC_QUARANTINE_POLICY.schema !== 'bc.quarantined_items.v1') {
    console.warn('[KubeJS] Ignoring unsupported quarantine manifest schema: ' + BC_QUARANTINE_POLICY.schema)
    BC_QUARANTINE_POLICY = { schema: 'bc.quarantined_items.v1', items: [] }
}

// The transport stack exposes several implementation, test, creative, legacy,
// and incomplete surfaces. Keep those out of recipe viewers so every visible
// VS-family entry represents a supported survival path. The incomplete
// Wanderwand pieces remain available internally to the sequenced assembly that
// consumes them; hiding them only removes misleading standalone viewer entries.
var BC_HIDDEN_VS_TECHNICAL_ITEMS = [
    'valkyrienskies:area_assembler',
    'valkyrienskies:connection_checker',
    'valkyrienskies:physics_entity_creator',
    'valkyrienskies:ship_assembler',
    'valkyrienskies:ship_creator',
    'valkyrienskies:ship_creator_smaller',
    'valkyrienskies:ship_remover',
    'valkyrienskies:test_antigrav',
    'valkyrienskies:test_chair',
    'valkyrienskies:test_flap',
    'valkyrienskies:test_hinge',
    'valkyrienskies:test_thruster',
    'valkyrienskies:test_wing',
    'vs_clockwork:asteroid_block',
    'vs_clockwork:creative_gas_generator',
    'vs_clockwork:creative_gravitron',
    'vs_clockwork:debug_lightning_arcer',
    'vs_clockwork:handheld_drill',
    'vs_clockwork:handheld_saw',
    'vs_clockwork:incomplete_hose_spool',
    'vs_clockwork:incomplete_telescoping_mechanism',
    'vs_clockwork:incomplete_wanderwand',
    'vs_clockwork:triode',
    'vs_clockwork:wanderlite_deepslate_ore',
    'vs_clockwork:wanderlite_end_ore',
    'vs_clockwork:wanderlite_nyx_ore'
]

var BC_HIDDEN_ITEMS = (BC_QUARANTINE_POLICY.items || [])
    .concat(BC_HIDDEN_VS_TECHNICAL_ITEMS)
    .concat([
        'pneumaticcraft:chunkloader_upgrade',
        'ae2:debug_chunk_loader',
        'createsifter:advanced_brass_mesh',
        'createsifter:custom_mesh',
        'createsifter:advanced_custom_mesh',
        // Registered Manufacturing components remain for save continuity, but
        // the removed PLA/PP chain no longer grants these acquisitions.
        'better_manufacturing:pla_stock',
        'better_manufacturing:pp_stock',
        'better_manufacturing:lactide',
        'better_manufacturing:propylene',
        'better_manufacturing:tin_carboxylate_catalyst',
        'better_manufacturing:supported_dehydrogenation_catalyst',
        'better_manufacturing:coordination_polymerisation_catalyst',
        'better_manufacturing:lactic_culture'
    ])

function bcHideRegisteredItems(event) {
    BC_HIDDEN_ITEMS.forEach(function (item) {
        try {
            if (Item.exists(item)) event.hide(item)
        } catch (ignored) {}
    })
}

var BC_HIDDEN_FLUIDS = [
    'better_manufacturing:sugar_solution',
    'better_manufacturing:lactic_acid_broth',
    'better_manufacturing:concentrated_lactic_acid',
    'better_manufacturing:dry_lactide',
    'better_manufacturing:molten_pla',
    'better_manufacturing:hot_propylene',
    'better_manufacturing:propylene_stream',
    'better_manufacturing:molten_pp'
]

JEIEvents.hideItems(function (event) {
    bcHideRegisteredItems(event)
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        bcHideRegisteredItems(event)
    })
}

JEIEvents.hideFluids(function (event) {
    BC_HIDDEN_FLUIDS.forEach(function (fluid) { event.hide(fluid) })
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideFluids(function (event) {
        BC_HIDDEN_FLUIDS.forEach(function (fluid) { event.hide(fluid) })
    })
}
