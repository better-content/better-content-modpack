// Small magical synthesis crossings that do not duplicate Realistic Ores processing.
// Ore separation now lives in four salient routes owned by Realistic Ores; the
// former acid-specific cutting fluids and dormant deposit matrix are gone.

function bcSynExists(id) {
    try { return Item.exists(id) } catch (e) { return false }
}

function bcSynArsImbuement(event, id, input, output, pedestalItems, sourceCost) {
    if (!bcSynExists(input) || !bcSynExists(output)) return
    event.custom({
        type: 'ars_nouveau:imbuement',
        count: 1,
        input: { item: input },
        output: output,
        pedestalItems: pedestalItems.map(function (item) { return { item: { item: item } } }),
        source: sourceCost
    }).id('kubejs:synthesis/magic/ars_imbuement/' + id)
}

var BC_SYN_MAGIC_CRYSTALS = [
    { input: 'minecraft:quartz', output: 'chemlib:silicon_dioxide', source: 400, pedestal: ['ars_nouveau:source_gem'] },
    { input: 'ae2:certus_quartz_crystal', output: 'ae2:fluix_dust', source: 1800, pedestal: ['ars_nouveau:source_gem', 'minecraft:redstone', 'bloodmagic:infusedslate'] }
]

ServerEvents.recipes(function (event) {
    BC_SYN_MAGIC_CRYSTALS.forEach(function (crystal) {
        bcSynArsImbuement(event, crystal.output.replace(':', '_'), crystal.input, crystal.output, crystal.pedestal, crystal.source)
    })
})
