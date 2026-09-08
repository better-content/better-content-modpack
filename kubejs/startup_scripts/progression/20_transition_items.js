// Pack-owned intermediates that still bridge otherwise unrelated owning mods.
// Realistic Ores forms, washed concentrates, tailings, and old casing tiers are
// intentionally absent.
StartupEvents.registry('fluid', function (event) {
    event.create('molten_andesite_alloy')
        .displayName('Molten Andesite Alloy')
        .thickTexture(0x7f8578)
        .bucketColor(0x7f8578)
})

StartupEvents.registry('item', function (event) {
    ;[
        ['nether_font_grout', 'Nether Font Grout'],
        ['aether_font_grout', 'Aether Font Grout'],
        ['bumblezone_font_grout', 'Bumblezone Font Grout'],
        ['ratlantis_font_grout', 'Ratlantis Font Grout']
    ].forEach(function (definition) {
        event.create(definition[0])
            .displayName(definition[1])
            .texture('tconstruct:item/grout')
    })

    ;[
        ['sky_steel_ingot', 'Sky Steel Ingot'],
        ['sky_steel_sheet', 'Sky Steel Sheet'],
        ['pressure_seal', 'Pressure Seal'],
        ['brass_utility_assembly', 'Brass Utility Assembly'],
        ['electrical_control_module', 'Electrical Control Module'],
        ['electrical_instrumentation_module', 'Electrical Instrumentation Module'],
        ['ae_logic_package', 'AE Logic Package'],
        ['impossible_support_matrix', 'Impossible Matter'],
        ['purified_blood_catalyst', 'Purified Blood Catalyst'],
        ['purified_source_core', 'Purified Source Core'],
        ['living_binding', 'Living Binding'],
        ['mountain_beryl_lens', 'Mountain Beryl Lens'],
        ['corundum_lapping_grit', 'Corundum Lapping Grit'],
        ['kimberlite_diamond_seed', 'Kimberlite Diamond Seed'],
        ['tungsten_carbide_insert', 'Tungsten Carbide Insert'],
        ['titanium_thermal_plate', 'Titanium Thermal Plate'],
        ['soulstone_carbon_matrix', 'Soulstone Carbon Matrix'],
        ['platinum_group_residue', 'Platinum Group Residue'],
        ['vanadium_contact_catalyst', 'Vanadium Contact Catalyst'],
        ['oxygenated_vanadium_contact_catalyst', 'Oxygenated Vanadium Contact Catalyst'],
        ['mashed_salmonberries', 'Mashed Salmonberries'],
        ['charred_blazing_chili', 'Charred Blazing Chili'],
        ['green_tea_extract', 'Green Tea Extract'],
        ['caffeine_extract', 'Caffeine Extract'],
        ['vision_extract', 'Vision Extract'],
        ['brine_extract', 'Brine Extract'],
        ['rose_hip_extract', 'Rose Hip Extract'],
        ['heatproof_extract', 'Heatproof Extract'],
        ['fermented_pomegranate_extract', 'Fermented Pomegranate Extract'],
        ['toxic_extract', 'Toxic Extract'],
        ['leaping_extract', 'Leaping Extract'],
        ['featherlight_extract', 'Featherlight Extract'],
        ['melon_life_extract', 'Melon Life Extract'],
        ['turtle_guard_extract', 'Turtle Guard Extract'],
        ['weakening_extract', 'Weakening Extract'],
        ['shadow_extract', 'Shadow Extract'],
        ['harm_extract', 'Harm Extract'],
        ['slowness_extract', 'Slowness Extract'],
        ['stabilized_reagent', 'Trip Fuel']
    ].forEach(function (definition) {
        event.create(definition[0]).displayName(definition[1])
    })
})
