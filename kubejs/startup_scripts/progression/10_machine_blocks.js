// Structural assemblies, not universal era proofs. Recipes live in era-local scripts; these are
// the only pack-owned progression blocks registered at startup.
global.BC_MACHINE_BLOCKS = [
    ['andesite_machine_block', 'Machine Block'],
    ['copper_machine_block', 'Toughened Machine Block'],
    ['brass_machine_block', 'Precision Machine Block'],
    ['airtight_machine_block', 'Airtight Machine Block'],
    ['electrical_machine_block', 'Electrical Machine Block'],
    ['space_machine_block', 'Space Machine Block']
]

StartupEvents.registry('block', function (event) {
    global.BC_MACHINE_BLOCKS.forEach(function (definition) {
        event.create(definition[0])
            .displayName(definition[1])
            .hardness(3.5)
            .resistance(6.0)
            .soundType('metal')
            .requiresTool(true)
    })
})
