var BC_QUARANTINE_POLICY = JsonIO.read('kubejs/config/quarantined_items.json') || { schema: '', items: [] }
if (BC_QUARANTINE_POLICY.schema !== 'bc.quarantined_items.v1') {
    console.warn('[KubeJS] Ignoring unsupported quarantine manifest schema: ' + BC_QUARANTINE_POLICY.schema)
    BC_QUARANTINE_POLICY = { schema: 'bc.quarantined_items.v1', items: [] }
}

var BC_CHEMLIB_FORM_POLICY = JsonIO.read('kubejs/config/chemlib_form_policy.json') || { hidden_gas_buckets: [], hidden_forms: [], hidden_compounds: [] }

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
    .concat(BC_CHEMLIB_FORM_POLICY.hidden_gas_buckets || [])
    .concat(BC_CHEMLIB_FORM_POLICY.hidden_forms || [])
    .concat(BC_CHEMLIB_FORM_POLICY.hidden_compounds || [])
    .concat(BC_HIDDEN_VS_TECHNICAL_ITEMS)
    .concat([
        'pneumaticcraft:chunkloader_upgrade',
        'ae2:debug_chunk_loader',
        'createsifter:advanced_brass_mesh',
        'createsifter:custom_mesh',
        'createsifter:advanced_custom_mesh'
    ])

function bcHideRegisteredItems(event) {
    BC_HIDDEN_ITEMS.forEach(function (item) {
        try {
            if (Item.exists(item)) event.hide(item)
        } catch (ignored) {}
    })
}

JEIEvents.hideItems(function (event) {
    bcHideRegisteredItems(event)
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        bcHideRegisteredItems(event)
    })
}
