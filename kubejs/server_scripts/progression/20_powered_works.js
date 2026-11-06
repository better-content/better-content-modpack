// Category roots are material-specific. Structural blocks never carry a
// dimension requirement shared by unrelated categories. Small native machines
// use fitted parts, not intact full-block structural assemblies.
ServerEvents.recipes(function (event) {
    event.shaped('4x kubejs:andesite_machine_block', ['ISI', 'SLS', 'ISI'], {
        I: '#forge:ingots/iron', S: '#forge:stone', L: '#forge:stripped_logs'
    }).id('kubejs:powered_works/andesite_machine_block')

    var partVariants = Platform.isLoaded('better_manufacturing')
    function variant(output, pattern, key, role, slot, operation, id) {
        if (!partVariants) { event.shaped(output, pattern, key).id(id); return }
        var ingredients = {}
        Object.keys(key).forEach(function (symbol) {
            var value = key[symbol]
            ingredients[symbol] = value.charAt(0) === '#' ? { tag: value.substring(1) } : { item: value }
        })
        event.custom({ type: 'better_manufacturing:part_variant', pattern: pattern, key: ingredients,
            result: { item: output }, function: 'better_manufacturing:create/' + operation,
            parts: [{ role: role, slot: slot }] }).id(id)
    }
    // Iron workshop transmission is available before Seared metallurgy. Keep
    // the existing alloy and Manufacturing part-consuming native IDs as later
    // alternatives; removing every recipe by output would erase those routes.
    var ironShaftKey = { I: '#forge:ingots/iron' }
    if (partVariants) ironShaftKey.H = 'tconstruct:tool_handle'
    event.shaped('8x create:shaft', partVariants ? ['I', 'I', 'H'] : ['I', 'I'], ironShaftKey)
        .id('kubejs:powered_works/workshop/iron_shaft')
    var workshopCogInputs = ['create:shaft', '#minecraft:planks']
    var workshopLargeCogInputs = ['create:shaft', '#minecraft:planks', '#minecraft:planks']
    if (partVariants) {
        workshopCogInputs.push('tconstruct:tool_handle')
        workshopLargeCogInputs.push('tconstruct:tool_handle')
    }
    event.shapeless('create:cogwheel', workshopCogInputs)
        .id('kubejs:powered_works/workshop/iron_cogwheel')
    event.shapeless('create:large_cogwheel', workshopLargeCogInputs)
        .id('kubejs:powered_works/workshop/iron_large_cogwheel')
    event.remove({ id: 'create:crafting/kinetics/andesite_casing' })
    event.shaped('2x create:andesite_casing', ['N N', 'LAL', 'N N'], {
        N: '#forge:nuggets/iron', L: '#forge:stripped_logs', A: 'minecraft:andesite'
    }).id('kubejs:powered_works/workshop/andesite_casing')
    event.remove({ id: 'create:crafting/kinetics/mechanical_bearing' })
    var bearingKey = { P: '#minecraft:wooden_slabs', C: 'create:andesite_casing', S: 'create:shaft' }
    if (partVariants) bearingKey.H = 'tconstruct:tool_handle'
    event.shaped('create:mechanical_bearing', partVariants ? [' P', 'HC', ' S'] : ['P', 'C', 'S'], bearingKey)
        .id('kubejs:powered_works/workshop/mechanical_bearing')
    event.remove({ id: 'create:crafting/kinetics/depot' })
    event.shapeless('create:depot', ['create:andesite_casing', '#forge:ingots/iron'])
        .id('kubejs:powered_works/workshop/depot')
    event.remove({ id: 'create:crafting/kinetics/basin' })
    // Keep all five workshop iron ingots; the Manufacturing branch fills the
    // existing empty top-center slot with a real TCon vessel liner.
    variant('create:basin', partVariants ? ['ILI', 'III'] : ['I I', 'III'],
        partVariants ? { I: '#forge:ingots/iron', L: 'tconstruct:large_plate' }
            : { I: '#forge:ingots/iron' },
        'liner', 1, 'fluid_envelope', 'kubejs:powered_works/workshop/basin')
    event.remove({ id: 'create:crafting/kinetics/whisk' })
    if (partVariants) {
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['CH ', 'SCS', 'SSS'],
            key: { H: { item: 'tconstruct:tool_handle' }, C: { tag: 'forge:ingots/iron' },
                S: { tag: 'forge:plates/iron' } }, result: { item: 'create:whisk' },
            function: 'better_manufacturing:create/mixing', parts: [{ role: 'shaft', slot: 1 }]
        }).id('kubejs:powered_works/workshop/whisk')
    } else {
        event.shaped('create:whisk', [' C ', 'SCS', 'SSS'], {
            C: '#forge:ingots/iron', S: '#forge:plates/iron'
        }).id('kubejs:powered_works/workshop/whisk')
    }
    event.remove({ id: 'create:crafting/kinetics/millstone' })
    // Preserve the stone and iron block gates. The native whisk already carries
    // its configured material properties: consume and bind it directly.
    var millstoneKey = { C: 'create:cogwheel', A: 'minecraft:andesite',
        M: 'kubejs:andesite_machine_block', S: 'minecraft:stone', N: 'minecraft:nether_brick' }
    if (partVariants) millstoneKey.H = 'tconstruct:large_plate'
    variant('create:millstone', partVariants ? ['NC ', 'AMA', 'HS '] : ['NC ', 'AMA', ' S '],
        millstoneKey, 'working_surface', 6, 'milling', 'kubejs:powered_works/direct_root/millstone')
    event.remove({ id: 'create:crafting/kinetics/mechanical_press' })
    var pressKey = { S: 'create:shaft', M: 'kubejs:andesite_machine_block', I: 'minecraft:iron_block' }
    if (partVariants) pressKey.H = 'tconstruct:large_plate'
    variant('create:mechanical_press', partVariants ? ['SH', 'M ', 'I '] : ['S', 'M', 'I'],
        pressKey, 'working_surface', 1, 'pressing', 'kubejs:powered_works/direct_root/mechanical_press')

    event.remove({ id: 'create:crafting/kinetics/mechanical_mixer' })
    variant('create:mechanical_mixer', ['S', 'A', 'W'], {
        S: 'create:shaft', A: '#forge:plates/iron', W: 'create:whisk'
    }, 'whisk', 2, 'mixing', 'kubejs:powered_works/mechanical_mixer')

    event.remove({ output: 'create:copper_casing' })
    event.custom({
        type: 'create:compacting',
        ingredients: [{ tag: 'forge:stripped_logs' }, { tag: 'forge:plates/copper' }],
        results: [{ item: 'create:copper_casing' }]
    }).id('kubejs:powered_works/copper_casing')
    event.custom({
        type: 'create:compacting',
        ingredients: [
            { item: 'kubejs:andesite_machine_block' }, { item: 'create:copper_casing' },
            { tag: 'forge:plates/copper' }, { tag: 'forge:plates/copper' },
            { tag: 'forge:plates/copper' }, { tag: 'forge:plates/copper' },
            { tag: 'forge:plates/iron' }, { tag: 'forge:nuggets/iron' }, { tag: 'forge:nuggets/iron' }
        ],
        results: [{ item: 'kubejs:copper_machine_block', count: 4 }]
    }).id('kubejs:powered_works/copper_machine_block')

    // Ordinary fabricated propellers and sail fittings must not restore a
    // hidden alloy prerequisite for fans or the Aether wind-power branch.
    event.remove({ id: 'create:crafting/kinetics/propeller' })
    if (partVariants) {
        event.custom({ type: 'better_manufacturing:part_variant', pattern: ['PBP', 'BSB', 'PBP'],
            key: { B: { item: 'tconstruct:small_blade' }, P: { tag: 'forge:plates/iron' },
                S: { item: 'create:shaft' } },
            result: { item: 'create:propeller' }, function: 'better_manufacturing:create/airflow',
            parts: [{ role: 'north', slot: 1 }, { role: 'west', slot: 3 },
                { role: 'east', slot: 5 }, { role: 'south', slot: 7 }]
        }).id('kubejs:powered_works/workshop/propeller')
    } else {
        event.shaped('create:propeller', [' P ', 'PSP', ' P '], {
            P: '#forge:plates/iron', S: 'create:shaft'
        }).id('kubejs:powered_works/workshop/propeller')
    }
    event.remove({ id: 'create:crafting/kinetics/white_sail' })
    event.shaped('2x create:white_sail', ['WS', 'SI'], {
        W: '#minecraft:wool', S: '#forge:rods/wooden', I: '#forge:nuggets/iron'
    }).id('kubejs:powered_works/workshop/white_sail')

    // Skyroot proves renewable water/wind generation. The consumed native
    // handle/binding remains part of each built machine with Manufacturing.
    ;[
        ['create:water_wheel', ['LIL', 'ISI', 'LPL'], ['LIL', 'ISI', 'LIL'], 'tconstruct:tool_handle'],
        ['create:large_water_wheel', ['LIL', 'IWI', 'LPL'], ['LIL', 'IWI', 'LIL'], 'tconstruct:tool_handle'],
        ['create:windmill_bearing', [' L ', 'LCS', 'H  '], [' L ', 'LCS', '   '], 'tconstruct:tool_binding']
    ].forEach(function (root) {
        var output = root[0]
        var key = output === 'create:large_water_wheel'
            ? { L: 'aether:skyroot_log', I: '#forge:ingots/iron', W: 'create:water_wheel' }
            : output === 'create:windmill_bearing'
                ? { L: 'aether:skyroot_log', C: 'create:andesite_casing', S: 'create:shaft' }
                : { L: 'aether:skyroot_log', I: '#forge:ingots/iron', S: 'create:shaft' }
        if (partVariants && output === 'create:windmill_bearing') key.H = root[3]
        event.remove({ output: output })
        var id = 'kubejs:powered_works/direct_root/' + output.substring('create:'.length)
        if (partVariants && output !== 'create:windmill_bearing') {
            // Water wheels are needed before mechanical crafters. Mark a real
            // Skyroot log with one consumed handle; keep all five native logs.
            var paidKey = { L: { item: 'aether:skyroot_log' }, I: { tag: 'forge:ingots/iron' },
                P: { type: 'forge:partial_nbt', item: 'aether:skyroot_log',
                    nbt: '{better_manufacturing:{skyroot_wheel_log:1}}' } }
            paidKey[output === 'create:large_water_wheel' ? 'W' : 'S'] =
                { item: output === 'create:large_water_wheel' ? 'create:water_wheel' : 'create:shaft' }
            event.custom({ type: 'minecraft:crafting_shaped', pattern: root[1], key: paidKey,
                result: { item: output } }).id(id)
        } else {
            event.shaped(output, partVariants ? root[1] : root[2], key).id(id)
        }
    })

    event.remove({ output: 'create:andesite_funnel' })
    event.shaped('2x create:andesite_funnel', [' K ', 'ICI'], {
        K: 'minecraft:dried_kelp', I: '#forge:nuggets/iron', C: 'create:andesite_casing'
    }).id('kubejs:powered_works/workshop/andesite_funnel')
    event.remove({ output: 'create:andesite_tunnel' })
    event.shaped('2x create:andesite_tunnel', ['KKK', 'ICI'], {
        K: 'minecraft:dried_kelp', I: '#forge:nuggets/iron', C: 'create:andesite_casing'
    }).id('kubejs:powered_works/workshop/andesite_tunnel')

    // The native pump datapack already provides a local, part-consuming route.

    // Nether brick is paid by each separator, including non-ore uses.
    if (Platform.isLoaded('createsifter')) {
        event.remove({ id: 'createsifter:sifter' })
        if (partVariants) {
            // Pay all eight native sticks and the tier-specific center before
            // the four physical handle parts become a configured mesh.
            ;[
                ['string', 'minecraft:string'], ['andesite', 'create:andesite_alloy'],
                ['zinc', 'create:zinc_ingot'], ['brass', 'create:brass_ingot']
            ].forEach(function (row) {
                event.shaped(Item.of('minecraft:stick',
                    '{better_manufacturing:{' + row[0] + '_mesh_paid:1}}'),
                    row[0] === 'string' ? ['SMS', 'SSS', 'SSS'] : ['SSS', 'SMS', 'SSS'],
                    { S: 'minecraft:stick', M: row[1] })
                    .id('kubejs:powered_works/refinement/prepare_' + row[0] + '_mesh')
            })
            // Preserve both native sticks and the Nether-brick gate without
            // moving this early machine behind the mechanical crafter.
            event.shaped(Item.of('minecraft:nether_brick', '{better_manufacturing:{sifter_paid_sticks:1}}'),
                [' S ', ' N ', ' S '], { S: 'minecraft:stick', N: 'minecraft:nether_brick' })
                .id('kubejs:powered_works/refinement/paid_sifter_brick')
            // Bundle the complete native brass-upgrade bill, leaving the
            // predecessor Sifter intact so its saved rating can be inherited.
            event.shaped(Item.of('minecraft:redstone', '{better_manufacturing:{brass_sifter_paid_materials:1}}'),
                ['PPP', 'B B', 'BRB'], { P: '#forge:plates/brass', B: '#forge:ingots/brass',
                    R: '#forge:dusts/redstone' })
                .id('kubejs:powered_works/refinement/brass_sifter_paid_materials')
            event.custom({ type: 'better_manufacturing:part_variant',
                pattern: ['WAW', 'HCH', ' N '],
                key: { W: { tag: 'minecraft:planks' }, A: { item: 'create:andesite_casing' },
                    H: { item: 'tconstruct:tool_handle' }, C: { item: 'create:cogwheel' },
                    N: { type: 'forge:partial_nbt', item: 'minecraft:nether_brick',
                        nbt: '{better_manufacturing:{sifter_paid_sticks:1}}' } },
                result: { item: 'createsifter:sifter' }, function: 'better_manufacturing:sifter/machine',
                parts: [{ role: 'left', slot: 3 }, { role: 'right', slot: 5 }]
            }).id('kubejs:powered_works/refinement/sifter')
        } else {
            event.shaped('createsifter:sifter', ['WAW', 'SCS', ' N '], {
                W: '#minecraft:planks', A: 'create:andesite_casing', S: 'minecraft:stick',
                C: 'create:cogwheel', N: 'minecraft:nether_brick'
            }).id('kubejs:powered_works/refinement/sifter')
        }
        // The brass upgrade consumes the gated Sifter and keeps its native
        // three-part configuration, brass/redstone cost, and exact recipe ID.
    }
    event.remove({ id: 'create:mechanical_crafting/crushing_wheel' })
    if (partVariants) {
        event.custom({ type: 'better_manufacturing:bulk_wheel_variant',
            function: 'better_manufacturing:createbulk/crushing_wheel', acceptMirrored: false,
            pattern: ['   H   ', '  AAA  ', ' AAPAA ', ' APNPA ', ' AAPAA ', '  AAA  ', '   L   '],
            key: { A: { item: 'create:andesite_alloy' }, P: { tag: 'minecraft:planks' },
                N: { item: 'minecraft:nether_brick' }, H: { item: 'tconstruct:hammer_head' },
                L: { item: 'tconstruct:large_plate' } },
            parts: [{ role: 'hammer', slot: 3 }, { role: 'plate', slot: 45 }],
            result: { item: 'create:crushing_wheel', count: 2 }
        }).id('kubejs:powered_works/refinement/crushing_wheels')
    } else {
        event.custom({ type: 'create:mechanical_crafting', acceptMirrored: false,
            pattern: [' AAA ', 'AAPAA', 'APNPA', 'AAPAA', ' AAA '],
            key: { A: { item: 'create:andesite_alloy' }, P: { tag: 'minecraft:planks' },
                N: { item: 'minecraft:nether_brick' } },
            result: { item: 'create:crushing_wheel', count: 2 }
        }).id('kubejs:powered_works/refinement/crushing_wheels')
    }
})
