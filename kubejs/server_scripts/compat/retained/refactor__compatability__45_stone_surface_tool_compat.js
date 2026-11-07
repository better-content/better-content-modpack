var BC_STONE_SURFACE_PICK_ONLY_BLOCKS = [
    'unearthed:overgrown_andesite',
    'unearthed:overgrown_diorite',
    'unearthed:overgrown_granite',
]

var BC_SHOVEL_REGOLITH_STONES = [
    'beige_limestone', 'conglomerate', 'dolomite', 'gabbro',
    'granodiorite', 'grey_limestone', 'kimberlite', 'limestone',
    'mudstone', 'phyllite', 'quartzite', 'rhyolite', 'sandstone',
    'siltstone', 'slate', 'stone', 'white_granite'
]

ServerEvents.tags('block', function (event) {
    for (var i = 0; i < BC_STONE_SURFACE_PICK_ONLY_BLOCKS.length; i++) {
        var id = BC_STONE_SURFACE_PICK_ONLY_BLOCKS[i]
        event.add('minecraft:mineable/pickaxe', id)
        event.remove('minecraft:mineable/axe', id)
        event.remove('minecraft:mineable/shovel', id)
        event.remove('minecraft:mineable/hoe', id)
        event.remove('minecraft:sword_efficient', id)
    }

    for (var ri = 0; ri < BC_SHOVEL_REGOLITH_STONES.length; ri++) {
        var stone = BC_SHOVEL_REGOLITH_STONES[ri]
        var regolith = 'unearthed:' + stone + '_regolith'
        var grassyRegolith = 'unearthed:' + stone + '_grassy_regolith'

        event.add('minecraft:mineable/shovel', regolith)
        event.add('minecraft:mineable/shovel', grassyRegolith)
        event.remove('minecraft:mineable/pickaxe', regolith)
        event.remove('minecraft:mineable/pickaxe', grassyRegolith)
        event.remove('minecraft:mineable/hoe', regolith)
        event.remove('minecraft:mineable/hoe', grassyRegolith)
    }
})
