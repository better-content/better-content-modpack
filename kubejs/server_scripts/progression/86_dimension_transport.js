// Each first Font trip opens a repeatable transport route from its own terrain.
// Vanilla boats, rails, and minecarts retain their ordinary recipes.
ServerEvents.recipes(function (event) {
    // Nether gold and brick are abundant enough to lay a useful line after one
    // trip. A rail car, cargo car, and steam engine can be built from that line.
    event.shaped('32x minecraft:rail', ['GQG', 'GBG', 'GQG'], {
        G: 'minecraft:gold_ingot', Q: 'minecraft:quartz', B: 'minecraft:nether_brick'
    }).id('kubejs:fonts/nether/rail_batch')

    event.remove({ output: 'littlelogistics:seater_car' })
    event.shaped('littlelogistics:seater_car', ['PPP', 'G G', 'R R'], {
        P: 'minecraft:crimson_planks', G: 'minecraft:gold_ingot', R: 'minecraft:rail'
    }).id('kubejs:fonts/nether/seater_car')
    event.remove({ output: 'littlelogistics:chest_car' })
    event.shaped('littlelogistics:chest_car', [' C ', 'PGP', 'R R'], {
        C: 'minecraft:chest', P: 'minecraft:crimson_planks',
        G: 'minecraft:gold_ingot', R: 'minecraft:rail'
    }).id('kubejs:fonts/nether/chest_car')
    event.remove({ output: 'littlelogistics:steam_locomotive' })
    event.shaped('littlelogistics:steam_locomotive', [' G ', 'BFB', 'RCR'], {
        G: 'minecraft:gold_ingot', B: 'minecraft:blaze_rod',
        F: 'minecraft:furnace', R: 'minecraft:rail', C: 'littlelogistics:seater_car'
    }).id('kubejs:fonts/nether/steam_locomotive')

    // Quicksoil itself is the Aether's native fast path. Swets and skyroot
    // turn a small found patch into enough blocks to expand the route.
    event.shapeless('8x aether:quicksoil', [
        'aether:quicksoil', 'aether:swet_ball', 'aether:swet_ball',
        'aether:aether_dirt', 'aether:aether_dirt', 'aether:aether_dirt',
        'aether:aether_dirt', 'aether:aether_dirt'
    ]).id('kubejs:fonts/aether/quicksoil_path')

    // Ratlantis planks and the four-at-a-time Courier Lattice make a canal
    // fleet repeatable. Advanced barges still use their ordinary recipes.
    event.shaped('4x better_ratlantis_logistics:courier_lattice', ['CPC', 'POP', 'CPC'], {
        C: 'rats:marbled_cheese_raw', P: 'rats:pirat_planks',
        O: 'rats:oratchalcum_ingot'
    }).id('kubejs:fonts/ratlantis/native_courier_lattice')
    event.remove({ output: 'littlelogistics:barge' })
    event.shaped('2x littlelogistics:barge', ['P P', 'PCP', 'LLL'], {
        P: 'rats:pirat_planks', C: 'minecraft:chest',
        L: 'better_ratlantis_logistics:courier_lattice'
    }).id('kubejs:fonts/ratlantis/barge_pair')
    event.remove({ output: 'littlelogistics:tug' })
    event.shaped('littlelogistics:tug', [' L ', 'PFP', 'LLL'], {
        L: 'better_ratlantis_logistics:courier_lattice', P: 'rats:pirat_planks',
        F: 'minecraft:furnace'
    }).id('kubejs:fonts/ratlantis/tug')
    event.remove({ output: 'littlelogistics:barge_dock' })
    event.shaped('4x littlelogistics:barge_dock', ['PPP', 'L L', 'PPP'], {
        P: 'rats:pirat_planks', L: 'better_ratlantis_logistics:courier_lattice'
    }).id('kubejs:fonts/ratlantis/barge_docks')
    event.remove({ output: 'littlelogistics:tug_dock' })
    event.shaped('4x littlelogistics:tug_dock', ['PPP', 'LFL', 'PPP'], {
        P: 'rats:pirat_planks', L: 'better_ratlantis_logistics:courier_lattice',
        F: 'minecraft:furnace'
    }).id('kubejs:fonts/ratlantis/tug_docks')

    // Honey crystals are renewable in the Bumblezone; native honey-bucket
    // recipes then provide Beehemoth lures. A wax saddle avoids loot RNG.
    event.shapeless('2x minecraft:honey_bottle', [
        'the_bumblezone:honey_crystal_shards',
        'the_bumblezone:honey_crystal_shards',
        'minecraft:glass_bottle', 'minecraft:glass_bottle'
    ]).id('kubejs:fonts/bumblezone/honey_bottles')
    event.shaped('minecraft:saddle', ['WWW', 'PHP', 'P P'], {
        W: 'the_bumblezone:carvable_wax',
        P: 'the_bumblezone:pollen_puff', H: 'minecraft:honeycomb'
    }).id('kubejs:fonts/bumblezone/wax_saddle')

    // One consumed collar per tier allows a player to mark three companions.
    event.shaped('better_dimension_fonts:canvas_collar', [' S ', 'W W', ' S '], {
        S: 'minecraft:string', W: 'minecraft:white_wool'
    }).id('kubejs:fonts/companions/canvas_collar')
    event.shaped('better_dimension_fonts:iron_collar', [' I ', 'ICI', ' I '], {
        I: 'minecraft:iron_ingot', C: 'better_dimension_fonts:canvas_collar'
    }).id('kubejs:fonts/companions/iron_collar')
    event.shaped('better_dimension_fonts:diamond_collar', [' D ', 'DID', ' D '], {
        D: 'minecraft:diamond', I: 'better_dimension_fonts:iron_collar'
    }).id('kubejs:fonts/companions/diamond_collar')
})
