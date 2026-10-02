// Occultism's tag-output fallback becomes an unusable placeholder when the preferred
// item is absent. Own only the twelve confirmed broken recipes and retain their
// original IDs so downstream recipe references remain stable.
;(function () {
    var RECIPES = [
        ['blaze_powder_from_rod', 'forge:rods/blaze', 'minecraft:blaze_powder', 1, false],
        ['certus_quartz_dust_from_gem', 'forge:gems/certus_quartz', 'ae2:certus_quartz_dust', 1, false],
        ['coal_dust', 'forge:ores/coal', 'bloodmagic:coalsand', 4, false],
        ['datura', 'forge:crops/datura', 'occultism:datura_seeds', 2, false],
        ['end_stone_dust', 'forge:end_stones', 'occultism:crushed_end_stone', 1, false],
        ['iesnium_dust', 'forge:ores/iesnium', 'occultism:iesnium_dust', 2, false],
        ['iesnium_dust_from_ingot', 'forge:ingots/iesnium', 'occultism:iesnium_dust', 1, true],
        ['iesnium_dust_from_raw', 'forge:raw_materials/iesnium', 'occultism:iesnium_dust', 2, false],
        ['iesnium_dust_from_raw_block', 'forge:storage_blocks/raw_iesnium', 'occultism:iesnium_dust', 18, false],
        ['iridium_dust_from_ingot', 'forge:ingots/iridium', 'chemlib:iridium_dust', 1, true],
        ['redstone_dust', 'forge:ores/redstone', 'minecraft:redstone', 4, false],
        ['tungsten_dust_from_ingot', 'forge:ingots/tungsten', 'chemlib:tungsten_dust', 1, true]
    ]

    ServerEvents.recipes(function (event) {
        RECIPES.forEach(function (spec) {
            var id = 'occultism:crushing/' + spec[0]
            event.remove({ id: id })
            event.custom({
                type: 'occultism:crushing',
                crushing_time: 200,
                ignore_crushing_multiplier: spec[4],
                ingredient: { tag: spec[1] },
                result: { item: spec[2], count: spec[3] }
            }).id(id)
        })
    })
})()
