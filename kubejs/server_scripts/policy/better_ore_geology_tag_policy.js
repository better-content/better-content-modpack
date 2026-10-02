// Realistic Ores host families are geological feedstocks, not generic ores.
// Excavated Variants mirrors its generated block attachments into item tags,
// so remove those generated item forms after the data-pack tags are assembled.
ServerEvents.tags('item', function (event) {
    var hostedFamily = /^excavated_variants:.*_(black_shale|brassroot|coal_measures|copper_bloom|evaporite_beds|hotstone|ironstone|tin_quartz)$/
    event.remove('forge:ores', hostedFamily)
    event.remove('c:ores', hostedFamily)
})
