// Keep the exact diamond-source policy without replacing PneumaticCraft's
// native etching-acid route or introducing an unavailable ChemLib acid chain.
ServerEvents.recipes(function (event) {
    // Diamonds come from Realistic Ores' Tin Quartz assay, never coal compression.
    event.remove({ id: 'pneumaticcraft:pressure_chamber/coal_to_diamond' })
})
