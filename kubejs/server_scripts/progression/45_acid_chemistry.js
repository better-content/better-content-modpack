// Reachable, bounded acid authoring. PneumaticCraft owns contained gas work;
// Create owns open solid/liquid preparation. One molecule packet is 250 mB.

function bcAcidFluid(id, amount) {
    return { type: 'pneumaticcraft:fluid', fluid: id, amount: amount || 250 }
}

function bcAcidFluidTag(id, amount) {
    return { type: 'pneumaticcraft:fluid', tag: id, amount: amount || 250 }
}

function bcAcidThermo(event, id, itemInput, fluidInput, itemOutput, fluidOutput, pressure, temperature) {
    var recipe = {
        type: 'pneumaticcraft:thermo_plant',
        exothermic: false,
        item_input: itemInput,
        fluid_input: fluidInput,
        pressure: pressure,
        speed: 0.4,
        temperature: { min_temp: temperature }
    }
    if (itemOutput) recipe.item_output = itemOutput
    if (fluidOutput) recipe.fluid_output = fluidOutput
    event.custom(recipe).id('kubejs:chemistry/acids/' + id)
}

function bcAcidMixer(event, id, input1, input2, itemOutput, fluidOutput, pressure, time) {
    var recipe = {
        type: 'pneumaticcraft:fluid_mixer',
        input1: input1,
        input2: input2,
        pressure: pressure,
        time: time
    }
    if (itemOutput) recipe.item_output = itemOutput
    if (fluidOutput) recipe.fluid_output = fluidOutput
    event.custom(recipe).id('kubejs:chemistry/acids/' + id)
}

ServerEvents.recipes(function (event) {
    // Diamonds come from Realistic Ores' Tin Quartz assay, never coal compression.
    event.remove({ id: 'pneumaticcraft:pressure_chamber/coal_to_diamond' })

    event.custom({
        type: 'create:mixing',
        ingredients: [
            { item: 'minecraft:sugar' },
            { item: 'minecraft:sugar' },
            { fluid: 'minecraft:water', amount: 250 }
        ],
        results: [
            { fluid: 'chemlib:ethanol_fluid', amount: 250 },
            { item: 'chemlib:carbon_dioxide', chance: 0.20 }
        ],
        processingTime: 180
    }).id('kubejs:chemistry/acids/ethanol_fermentation')

    event.custom({
        type: 'pneumaticcraft:pressure_chamber',
        inputs: [
            { type: 'pneumaticcraft:stacked_item', item: 'realistic_ores:crushed_ironstone', count: 4 },
            { type: 'pneumaticcraft:stacked_item', item: 'minecraft:quartz', count: 2 },
            { item: 'pneumaticcraft:pressure_chamber_glass' }
        ],
        pressure: 2.0,
        results: [{ item: 'kubejs:vanadium_contact_catalyst' }]
    }).id('kubejs:chemistry/acids/vanadium_contact_catalyst')

    // Two water packets are separated into oxygen plus two solid gas packets.
    bcAcidMixer(event, 'water_electrolysis',
        bcAcidFluid('minecraft:water', 250), bcAcidFluid('minecraft:water', 250),
        { item: 'chemlib:hydrogen', count: 2 }, bcAcidFluid('chemlib:oxygen_fluid', 250), 3.0, 240)

    bcAcidThermo(event, 'sulfur_dioxide',
        { item: 'chemlib:sulfur' }, bcAcidFluid('chemlib:oxygen_fluid', 250), null,
        bcAcidFluid('chemlib:sulfur_dioxide_fluid', 250), 2.0, 523)

    bcAcidThermo(event, 'contact_catalyst_oxygenation',
        { item: 'kubejs:vanadium_contact_catalyst' }, bcAcidFluid('chemlib:oxygen_fluid', 250),
        { item: 'kubejs:oxygenated_vanadium_contact_catalyst' }, null, 2.5, 673)

    bcAcidThermo(event, 'sulfur_trioxide_contact',
        { item: 'kubejs:oxygenated_vanadium_contact_catalyst' }, bcAcidFluid('chemlib:sulfur_dioxide_fluid', 500),
        { item: 'kubejs:vanadium_contact_catalyst' }, bcAcidFluid('chemlib:sulfur_trioxide_fluid', 500), 3.0, 723)

    bcAcidMixer(event, 'sulfuric_acid_hydration',
        bcAcidFluid('chemlib:sulfur_trioxide_fluid', 250), bcAcidFluid('minecraft:water', 250),
        null, bcAcidFluid('chemlib:sulfuric_acid_fluid', 250), 2.0, 180)

    bcAcidMixer(event, 'acetic_acid_oxidation',
        bcAcidFluidTag('forge:ethanol', 250), bcAcidFluid('chemlib:oxygen_fluid', 250),
        null, bcAcidFluid('chemlib:acetic_acid_fluid', 250), 2.5, 220)

    event.custom({
        type: 'create:mixing',
        ingredients: [
            { item: 'minecraft:dried_kelp' }, { item: 'minecraft:dried_kelp' },
            { item: 'minecraft:dried_kelp' }, { item: 'minecraft:dried_kelp' },
            { item: 'minecraft:dried_kelp' }, { item: 'minecraft:dried_kelp' },
            { item: 'minecraft:dried_kelp' }, { item: 'minecraft:dried_kelp' },
            { fluid: 'minecraft:water', amount: 500 }
        ],
        results: [
            { item: 'chemlib:sodium_chloride', count: 2 },
            { item: 'chemlib:potassium_chloride' },
            { item: 'chemlib:cellulose', count: 2 }
        ],
        processingTime: 200
    }).id('kubejs:chemistry/acids/kelp_salt_wash')

    bcAcidThermo(event, 'hydrochloric_acid_mannheim',
        { type: 'pneumaticcraft:stacked_item', item: 'chemlib:sodium_chloride', count: 2 },
        bcAcidFluid('chemlib:sulfuric_acid_fluid', 250), { item: 'chemlib:sodium_sulfate' },
        bcAcidFluid('chemlib:hydrochloric_acid_fluid', 500), 2.5, 673)

    bcAcidThermo(event, 'nitric_acid_saltpeter',
        { type: 'pneumaticcraft:stacked_item', tag: 'forge:dusts/saltpeter', count: 2 },
        bcAcidFluid('chemlib:sulfuric_acid_fluid', 250), { item: 'chemlib:potassium_sulfate' },
        bcAcidFluid('chemlib:nitric_acid_fluid', 500), 2.75, 623)

})
