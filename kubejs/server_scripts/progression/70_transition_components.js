// Pack-owned transition components must have explicit, reachable manufacturing
// routes. These recipes bridge owning mods without restoring the removed casing
// ladder or hidden ChemLib plate forms.

ServerEvents.recipes(function (event) {
    // Fallout's composite is craftable from ordinary iron and coal, but its
    // steel output is quarantined. Smelt it into the canonical TCon form so
    // Electrical Control and the later transition components stay reachable.
    event.smelting('tconstruct:steel_ingot', 'fallout_wastelands_:steel_composite')
        .xp(1.0)
        .cookingTime(600)
        .id('kubejs:transition/metallurgy/steel_ingot')

    // Precision Factory support used by TaCZ's two base manufacturing benches.
    event.shaped('2x kubejs:brass_utility_assembly', [
        'SES',
        'BPB',
        'SES'
    ], {
        S: '#forge:plates/brass',
        E: 'create:electron_tube',
        B: 'create:brass_casing',
        P: 'create:precision_mechanism'
    }).id('kubejs:transition/precision_factory/brass_utility_assembly')

    // Electrical support modules require pressure-era electronics, but do not
    // consume another one-time Machine Block proof.
    global.bcPncrPressure(event,
        'kubejs:transition/electrical_control/electrical_control_module',
        'kubejs:electrical_control_module', 1, 3.5, [
            'powergrid:integrated_circuit',
            'powergrid:battery',
            'powergrid:capacitor',
            'pneumaticcraft:printed_circuit_board'
        ])

    global.bcPncrPressure(event,
        'kubejs:transition/electrical_control/electrical_instrumentation_module',
        'kubejs:electrical_instrumentation_module', 2, 3.5, [
            'powergrid:integrated_circuit',
            'powergrid:redstone_relay',
            { id: 'powergrid:wire', count: 2 },
            'pneumaticcraft:printed_circuit_board',
            'rats:oratchalcum_ingot'
        ])

    // AE materials remain post-electrical and meteor-dependent. The pack sheet
    // deliberately replaces hidden ChemLib plate forms in downstream recipes.
    event.custom({
        type: 'create:mixing',
        heatRequirement: 'heated',
        ingredients: [
            { tag: 'forge:ingots/steel' },
            { item: 'ae2:sky_dust' },
            { item: 'ae2:fluix_crystal' }
        ],
        results: [{ item: 'kubejs:sky_steel_ingot', count: 2 }],
        processingTime: 200
    }).id('kubejs:transition/ae2/sky_steel_ingot')

    event.custom({
        type: 'create:pressing',
        ingredients: [{ item: 'kubejs:sky_steel_ingot' }],
        results: [{ item: 'kubejs:sky_steel_sheet' }]
    }).id('kubejs:transition/ae2/sky_steel_sheet')

    global.bcPncrPressure(event,
        'kubejs:transition/ae2/ae_logic_package',
        'kubejs:ae_logic_package', 1, 4.0, [
            { id: 'kubejs:sky_steel_sheet', count: 2 },
            'ae2:logic_processor',
            'oc2r:circuit_board',
            'kubejs:electrical_control_module',
            'rats:oratchalcum_ingot'
        ])

    // Mountain and deep-deposit components back the extreme-Y reward recipes.
    event.custom({
        type: 'create:milling',
        ingredients: [{ tag: 'quark:corundum' }],
        results: [{ item: 'kubejs:corundum_lapping_grit', count: 2 }],
        processingTime: 100
    }).id('kubejs:transition/geology/corundum_lapping_grit')

    event.shaped('kubejs:mountain_beryl_lens', [
        ' G ',
        'EAE',
        ' G '
    ], {
        G: 'kubejs:corundum_lapping_grit',
        E: 'minecraft:emerald',
        A: 'minecraft:amethyst_shard'
    }).id('kubejs:transition/geology/mountain_beryl_lens')

    global.bcCreateCompacting(event,
        'kubejs:transition/geology/kimberlite_diamond_seed',
        'kubejs:kimberlite_diamond_seed', 1, [
            'unearthed:kimberlite',
            'unearthed:kimberlite',
            'unearthed:kimberlite',
            'unearthed:kimberlite',
            'minecraft:diamond'
        ])

    global.bcCreateCompacting(event,
        'kubejs:transition/geology/tungsten_carbide_insert',
        'kubejs:tungsten_carbide_insert', 1, [
            '#forge:ingots/steel',
            'better_ore_geology:crushed_ironstone',
            'minecraft:coal',
            'minecraft:coal'
        ], 'heated')

    event.custom({
        type: 'create:mixing',
        ingredients: [
            { item: 'better_ore_geology:gold_concentrate' },
            { item: 'better_ore_geology:gold_concentrate' },
            { fluid: 'chemlib:hydrochloric_acid_fluid', amount: 250 },
            { fluid: 'chemlib:nitric_acid_fluid', amount: 250 }
        ],
        results: [{ item: 'kubejs:platinum_group_residue', count: 2 }],
        processingTime: 260
    }).id('kubejs:transition/geology/platinum_group_residue')

    event.custom({
        type: 'create:mixing',
        heatRequirement: 'heated',
        ingredients: [
            { item: 'minecraft:soul_sand' },
            { item: 'minecraft:coal' },
            { item: 'chemlib:sulfur' },
            { item: 'bloodmagic:demonslate' }
        ],
        results: [{ item: 'kubejs:soulstone_carbon_matrix' }],
        processingTime: 220
    }).id('kubejs:transition/geology/soulstone_carbon_matrix')

    // Titanium concentrate becomes available through the acid-authored Hotstone
    // route. Pressing its canonical ingot gives aerospace a visible plate source.
    event.custom({
        type: 'create:pressing',
        ingredients: [{ tag: 'forge:ingots/titanium' }],
        results: [{ item: 'kubejs:titanium_thermal_plate' }]
    }).id('kubejs:transition/aerospace/titanium_thermal_plate')

    // Impossible Matter is a direct 100,000-Source sink. Repeated finite
    // substrate makes the cost visible while one valid Source Chunk Anchor can
    // satisfy the request atomically.
    event.custom({
        type: 'ars_nouveau:enchanting_apparatus',
        keepNbtOfReagent: false,
        output: { item: 'kubejs:impossible_support_matrix' },
        pedestalItems: [
            { item: 'ars_nouveau:source_gem_block' },
            { item: 'ars_nouveau:source_gem_block' },
            { item: 'ars_nouveau:source_gem_block' },
            { item: 'ars_nouveau:source_gem_block' },
            { item: 'minecraft:netherite_ingot' },
            { item: 'minecraft:netherite_ingot' },
            { item: 'minecraft:netherite_ingot' },
            { item: 'minecraft:netherite_ingot' }
        ],
        reagent: [{ item: 'minecraft:nether_star' }],
        sourceCost: 100000
    }).id('kubejs:transition/ae2/impossible_matter')
})
