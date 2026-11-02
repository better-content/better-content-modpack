// Two deliberately separate polymer chains. mB is process capacity, NOT mole count or mass.
// PLA packet accounting: sugar item = one sucrose packet (342 mass units); 250 mB water =
// one water packet (18). 750 mB water + sugar -> solution; concentration returns
// 500 mB unreacted water. 1000 mB broth represents 4 lactic-acid packets (4*90);
// 500 mB concentrated acid retains all four, hence 250 mB acid -> one lactide
// item (144) + 500 mB water (2*18). One lactide -> 180 mB PLA -> two 90 mB stocks.
// PP packets: 1000 mB LPG fractionation -> 500 mB propane + 500 mB usable butane;
// 250 mB propane = one C3H8 (44), 250 mB propylene = one C3H6 (42),
// plus two hydrogen atom items. 250 mB propylene -> one PP stock (42) -> 90 mB melt.
// Those conversions are authored material equivalents, not equal-mB molecular masses.
// Temperatures below are native K, with approximate Celsius notes (K - 273.15).
// Native pressure is gauge bar. Thermo -0.5 requires a RUNNING, pneumatically
// connected vacuum source: the native plant checks pressure <= -0.5, not proximity.
// PNC Vacuum Pump needs >2 bar on its + side and exposes vacuum ONLY on its
// directional - side (down to -1 bar); connect that side by pressure tube to
// the plant air network. A nearby but disconnected pump cannot satisfy this.
// Fermenter has no recipe temperature serializer: 30-40 C is conceptual, NOT enforced.
// Omitting heatRequirement maps to native Create HeatCondition.NONE; that means
// NO burner requirement (NONE.testBlazeBurner returns true even if heated), NOT
// a measured ambient temperature and NOT a prohibition on external heating.
// No catalyst retention subsystem: prepared formulations are consumed per batch.
// No added water in ring-opening, no hydrogen in propylene polymerisation.

function polymerFluid(id, amount) { return { type: 'pneumaticcraft:fluid', fluid: id, amount: amount } }
function polymerThermo(event, id, inputFluid, inputItem, outputFluid, outputItem, minK, maxK, pressure, speed) {
    var recipe = {
        type: 'pneumaticcraft:thermo_plant',
        exothermic: false,
        speed: speed,
        temperature: { min_temp: minK, max_temp: maxK }
    }
    if (inputFluid) recipe.fluid_input = inputFluid
    if (inputItem) recipe.item_input = inputItem
    if (outputFluid) recipe.fluid_output = outputFluid
    if (outputItem) recipe.item_output = outputItem
    if (pressure !== null) recipe.pressure = pressure
    event.custom(recipe).id('kubejs:polymers/' + id)
}

ServerEvents.recipes(function (event) {
    // Keep the two chains contiguous: a missing native serializer or culture/
    // chemical source must not leave partially loadable polymer recipes behind.
    // Existing native plastic routes remain untouched when any dependency is absent.
    if (['better_manufacturing', 'pneumaticcraft', 'createdieselgenerators',
        'create', 'chemlib', 'brewinandchewin'].some(id => !Platform.isLoaded(id))) return
    // Open, aqueous preparation. Fresh lactic culture is a separate starter, not ethanol yeast.
    event.custom({
        type: 'create:mixing',
        ingredients: [{item: 'minecraft:sugar'}, {fluid: 'minecraft:water', amount: 750}],
        results: [{fluid: 'better_manufacturing:sugar_solution', amount: 1000}],
        processingTime: 160
    }).id('kubejs:polymers/sugar_solution')

    // The Bulk Fermenter actually executes its own recipe; this is not an ethanol rename.
    // Native default HeatCondition.NONE permits all burner levels; no 30-40 C gate.
    event.custom({
        type: 'createdieselgenerators:bulk_fermenting',
        ingredients: [
            {fluid: 'better_manufacturing:sugar_solution', amount: 1000},
            {item: 'better_manufacturing:lactic_culture'}
        ],
        processingTime: 1200,
        results: [{fluid: 'better_manufacturing:lactic_acid_broth', amount: 1000}]
    }).id('kubejs:polymers/lactic_fermentation')

    // Heated contained refinery: water recovery and concentration are TWO outputs.
    event.custom({
        type: 'pneumaticcraft:refinery',
        input: polymerFluid('better_manufacturing:lactic_acid_broth', 1000),
        results: [
            {fluid: 'minecraft:water', amount: 500},
            {fluid: 'better_manufacturing:concentrated_lactic_acid', amount: 500}
        ],
        temperature: {min_temp: 363, max_temp: 383}
    }).id('kubejs:polymers/lactic_broth_concentration')

    // Oligomer formation + depolymerisation abstracted into this ONE real plant step.
    // 180-220 C => 453-493 K; -0.5 gauge bar is a genuine vacuum requirement.
    polymerThermo(event, 'lactide_under_vacuum',
        polymerFluid('better_manufacturing:concentrated_lactic_acid', 250),
        {item: 'better_manufacturing:tin_carboxylate_catalyst'},
        {fluid: 'minecraft:water', amount: 500},
        {item: 'better_manufacturing:lactide'}, 453, 493, -0.5, 0.2)

    // Dry, contained feed: first meter lactide into an independently identified fluid;
    // the second stage uses native single-fluid/single-item thermo input for catalyst.
    polymerThermo(event, 'dry_lactide_feed', null,
        {item: 'better_manufacturing:lactide'},
        {fluid: 'better_manufacturing:dry_lactide', amount: 180}, null,
        423, 453, 0.5, 0.3)
    polymerThermo(event, 'pla_ring_opening',
        polymerFluid('better_manufacturing:dry_lactide', 180),
        {item: 'better_manufacturing:tin_carboxylate_catalyst'},
        {fluid: 'better_manufacturing:molten_pla', amount: 180}, null,
        423, 453, 1.0, 0.2)

    // Refinery remains the native crude-oil -> multiple fuel fractions machine.
    // LPG is a MIXTURE. Both separated C3 and C4 fractions retain fuel usefulness.
    event.custom({
        type: 'pneumaticcraft:refinery',
        input: polymerFluid('pneumaticcraft:lpg', 1000),
        results: [
            {fluid: 'chemlib:butane_fluid', amount: 500},
            {fluid: 'chemlib:propane_fluid', amount: 500}
        ],
        temperature: {min_temp: 323, max_temp: 373}
    }).id('kubejs:polymers/lpg_fractionation')

    // Supported nickel/tin-on-alumina formulation is consumed in normal item slot.
    // 550-650 C => 823-923 K; full per-packet conversion, no unconverted propane represented.
    polymerThermo(event, 'propane_dehydrogenation',
        polymerFluid('chemlib:propane_fluid', 250),
        {item: 'better_manufacturing:supported_dehydrogenation_catalyst'},
        {fluid: 'better_manufacturing:hot_propylene', amount: 250},
        {item: 'chemlib:hydrogen', count: 2}, 823, 923, 2.0, 0.2)

    // Native heat exchange to <= 323 K (~50 C), explicit authored separation
    // abstraction. Do not claim ordinary condensation alone grants industrial purity.
    polymerThermo(event, 'propylene_cooling_separation',
        polymerFluid('better_manufacturing:hot_propylene', 250), null,
        {fluid: 'better_manufacturing:propylene_stream', amount: 250}, null,
        273, 323, 1.0, 0.3)

    // Supported pressure-controlled coordination reaction, 423-453 K (150-180 C).
    // Gameplay 3 gauge bar intentionally NOT a claim of realistic industrial pressure.
    polymerThermo(event, 'propylene_coordination_polymerisation',
        polymerFluid('better_manufacturing:propylene_stream', 250),
        {item: 'better_manufacturing:coordination_polymerisation_catalyst'},
        null, {item: 'better_manufacturing:pp_stock'},
        423, 453, 3.0, 0.2)

    // PP melt shaping is a distinct native heating operation, 453-503 K (180-230 C).
    // One stock -> precisely 90 mB: the same cost that the Tinkers part cast uses.
    polymerThermo(event, 'pp_metered_melt', null,
        {item: 'better_manufacturing:pp_stock'},
        {fluid: 'better_manufacturing:molten_pp', amount: 90}, null,
        453, 503, 0.5, 0.3)

    // Native Heat Frame Cooling accepts ITEM/container input, not a pipe or tank;
    // our polymers intentionally have no bucket. Use the Thermopneumatic Plant's
    // real fluid tank and cooled heat exchanger instead. No plastic output bonus.
    polymerThermo(event, 'pla_stock_cooling',
        polymerFluid('better_manufacturing:molten_pla', 90), null,
        null, {item: 'better_manufacturing:pla_stock'},
        273, 383, 0.5, 0.3)
    polymerThermo(event, 'pp_stock_cooling',
        polymerFluid('better_manufacturing:molten_pp', 90), null,
        null, {item: 'better_manufacturing:pp_stock'},
        273, 383, 0.5, 0.3)

    // Two narrowly required oxide precursors, made from obtainable ingots and
    // existing electrolysis oxygen; not a general chemical precursor tree.
    // 2 Sn + O2 -> 2 SnO (tin carboxylate preparation feed).
    polymerThermo(event, 'tin_oxide_prep',
        polymerFluid('chemlib:oxygen_fluid', 250),
        {type: 'pneumaticcraft:stacked_item', tag: 'forge:ingots/tin', count: 2},
        null, {item: 'chemlib:tin_oxide', count: 2}, 523, 673, 1.0, 0.3)
    // 4 Al + 3 O2 -> 2 Al2O3 (catalyst support).
    polymerThermo(event, 'aluminum_oxide_support',
        polymerFluid('chemlib:oxygen_fluid', 750),
        {type: 'pneumaticcraft:stacked_item', tag: 'forge:ingots/aluminum', count: 4},
        null, {item: 'chemlib:aluminum_oxide', count: 2}, 623, 773, 1.0, 0.3)
    // Catalysts are deliberately consumable prepared formulations, not raw metal magic.
    // Tin oxide + acid + cellulose is an ABSTRACT tin-carboxylate prep, NOT tin octoate.
    event.custom({
        type: 'create:mixing', heatRequirement: 'heated',
        ingredients: [
            {item: 'chemlib:tin_oxide'},
            {item: 'chemlib:cellulose'},
            {fluid: 'chemlib:acetic_acid_fluid', amount: 250}
        ],
        results: [{item: 'better_manufacturing:tin_carboxylate_catalyst', count: 2}],
        processingTime: 240
    }).id('kubejs:polymers/formulated_tin_carboxylate')
    // Nickel/tin promoted alumina: formulated supported catalyst, not a literal
    // balanced synthesis or a catalyst maintenance/recovery system.
    event.custom({
        type: 'create:mixing', heatRequirement: 'heated',
        ingredients: [
            {tag: 'forge:ingots/nickel'}, {item: 'chemlib:tin_oxide'},
            {item: 'chemlib:aluminum_oxide'}
        ],
        results: [{item: 'better_manufacturing:supported_dehydrogenation_catalyst', count: 4}],
        processingTime: 300
    }).id('kubejs:polymers/formulated_dehydrogenation_catalyst')
    // Stereoselective coordination catalyst is a prepared system. Titanium,
    // alumina, salt and acid represent formulated/activated sites, NOT a balanced
    // organometallic precursor synthesis or raw titanium directly polymerising gas.
    event.custom({
        type: 'create:mixing', heatRequirement: 'heated',
        ingredients: [
            {tag: 'forge:ingots/titanium'}, {item: 'chemlib:sodium_chloride'},
            {item: 'chemlib:aluminum_oxide'},
            {fluid: 'chemlib:acetic_acid_fluid', amount: 250}
        ],
        results: [{item: 'better_manufacturing:coordination_polymerisation_catalyst', count: 4}],
        processingTime: 300
    }).id('kubejs:polymers/formulated_coordination_catalyst')

    // Cultivation abstraction: existing lactic fermented food inoculates milk;
    // ingredients are consumed. Not ethanol yeast or a balanced culture synthesis.
    event.custom({
        type: 'create:mixing', ingredients: [
            {item: 'minecraft:milk_bucket'},
            {item: 'brewinandchewin:kimchi'},
            {item: 'minecraft:sugar'}
        ],
        // Create's BasinRecipe.apply extracts the bucket item without a crafting
        // remainder; explicitly return the one reusable empty bucket, not its milk.
        results: [{item: 'better_manufacturing:lactic_culture', count: 2},
            {item: 'minecraft:bucket'}],
        processingTime: 400
    }).id('kubejs:polymers/lactic_culture')

    event.custom({
        type: 'pneumaticcraft:fuel_quality', air_per_bucket: 1300000, burn_rate: 1.0,
        fluid: polymerFluid('chemlib:butane_fluid', 1000)
    }).id('kubejs:polymers/butane_fuel')
})
