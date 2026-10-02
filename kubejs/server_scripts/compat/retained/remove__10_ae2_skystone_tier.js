// kubejs/server_scripts/ae2_skystone_tier.js
ServerEvents.tags('block', event => {
    // Workaround: removing this problematic tag entry prevents a tag-resolution
    // issue that can stop the rest of this script from applying.
    event.remove('minecraft:mineable/pickaxe', 'theabyss:infused_magma')

    const blocks = [
        'ae2:sky_stone_block',
        'ae2:smooth_sky_stone_block',
        'ae2:sky_stone_brick',
        'ae2:sky_stone_small_brick',
        'ae2:sky_stone_chest',
        'ae2:smooth_sky_stone_chest',
        'ae2:sky_stone_slab',
        'ae2:sky_stone_stairs',
        'ae2:sky_stone_wall',
        'ae2:sky_stone_brick_slab',
        'ae2:sky_stone_brick_stairs',
        'ae2:sky_stone_brick_wall',
        'ae2:sky_stone_small_brick_slab',
        'ae2:sky_stone_small_brick_stairs',
        'ae2:sky_stone_small_brick_wall',
        'ae2:smooth_sky_stone_slab',
        'ae2:smooth_sky_stone_stairs',
        'ae2:smooth_sky_stone_wall'
    ]

    blocks.forEach(id => {
        event.add('minecraft:mineable/pickaxe', id)
        event.remove('minecraft:needs_iron_tool', id)
        event.remove('minecraft:needs_diamond_tool', id)
        event.add('minecraft:needs_stone_tool', id)
    })
})
