var BC_GENERIC_LOG_FAMILIES = [
    '#aether:skyroot_logs',
    '#aether:golden_oak_logs',
    '#quark:ancient_logs',
    '#quark:azalea_logs',
    '#quark:blossom_logs',
    '#natures_spirit:mahogany_logs',
    '#natures_spirit:saxaul_logs',
    '#natures_spirit:redwood_logs',
    '#natures_spirit:aspen_logs',
    '#natures_spirit:fir_logs',
    '#natures_spirit:cypress_logs',
    '#natures_spirit:coconut_logs',
    '#natures_spirit:wisteria_logs',
    '#natures_spirit:willow_logs',
    '#natures_spirit:cedar_logs',
    '#natures_spirit:sugi_logs',
    '#natures_spirit:palo_verde_logs',
    '#natures_spirit:olive_logs',
    '#natures_spirit:joshua_logs',
    '#natures_spirit:ghaf_logs',
    '#natures_spirit:maple_logs',
    '#natures_spirit:larch_logs',
    'fallout_wastelands_:molder_wood_log',
    'fallout_wastelands_:molder_wood_wood',
    'fallout_wastelands_:joshua_log',
    'fallout_wastelands_:joshua_log_log'
]

var BC_HOLLOW_LOG_FAMILY_ALIASES = [
    ['minecraft:oak_logs', 'quark:hollow_oak_log'],
    ['minecraft:spruce_logs', 'quark:hollow_spruce_log'],
    ['minecraft:birch_logs', 'quark:hollow_birch_log'],
    ['minecraft:jungle_logs', 'quark:hollow_jungle_log'],
    ['minecraft:acacia_logs', 'quark:hollow_acacia_log'],
    ['minecraft:cherry_logs', 'quark:hollow_cherry_log'],
    ['minecraft:dark_oak_logs', 'quark:hollow_dark_oak_log'],
    ['minecraft:mangrove_logs', 'quark:hollow_mangrove_log'],
    ['minecraft:crimson_stems', 'quark:hollow_crimson_stem'],
    ['minecraft:warped_stems', 'quark:hollow_warped_stem'],
    ['quark:ancient_logs', 'quark:hollow_ancient_log'],
    ['quark:azalea_logs', 'quark:hollow_azalea_log'],
    ['quark:blossom_logs', 'quark:hollow_blossom_log']
]

var BC_LADDER_PLANK_ALIASES = [
    ['oak', 'minecraft:oak_planks', 'quark:vertical_oak_planks', 'quark:building/crafting/oak_ladder'],
    ['spruce', 'minecraft:spruce_planks', 'quark:vertical_spruce_planks', 'quark:building/crafting/spruce_ladder'],
    ['birch', 'minecraft:birch_planks', 'quark:vertical_birch_planks', 'quark:building/crafting/birch_ladder'],
    ['jungle', 'minecraft:jungle_planks', 'quark:vertical_jungle_planks', 'quark:building/crafting/jungle_ladder'],
    ['acacia', 'minecraft:acacia_planks', 'quark:vertical_acacia_planks', 'quark:building/crafting/acacia_ladder'],
    ['cherry', 'minecraft:cherry_planks', 'quark:vertical_cherry_planks', 'quark:building/crafting/cherry_ladder'],
    ['dark_oak', 'minecraft:dark_oak_planks', 'quark:vertical_dark_oak_planks', 'quark:building/crafting/dark_oak_ladder'],
    ['mangrove', 'minecraft:mangrove_planks', 'quark:vertical_mangrove_planks', 'quark:building/crafting/mangrove_ladder'],
    ['bamboo', 'minecraft:bamboo_planks', 'quark:vertical_bamboo_planks', 'quark:building/crafting/bamboo_ladder'],
    ['crimson', 'minecraft:crimson_planks', 'quark:vertical_crimson_planks', 'quark:building/crafting/crimson_ladder'],
    ['warped', 'minecraft:warped_planks', 'quark:vertical_warped_planks', 'quark:building/crafting/warped_ladder'],
    ['ancient', 'quark:ancient_planks', 'quark:vertical_ancient_planks', 'quark:world/crafting/woodsets/ancient/ladder'],
    ['azalea', 'quark:azalea_planks', 'quark:vertical_azalea_planks', 'quark:world/crafting/woodsets/azalea/ladder'],
    ['blossom', 'quark:blossom_planks', 'quark:vertical_blossom_planks', 'quark:world/crafting/woodsets/blossom/ladder']
]

var BC_OAK_LADDER_INPUT_TAG = 'kubejs:planks/oak_ladder_inputs'

function bcAddWoodFamilyAliases(event) {
    for (var gi = 0; gi < BC_GENERIC_LOG_FAMILIES.length; gi++) {
        event.add('minecraft:logs', BC_GENERIC_LOG_FAMILIES[gi])
    }

    for (var hi = 0; hi < BC_HOLLOW_LOG_FAMILY_ALIASES.length; hi++) {
        event.add(BC_HOLLOW_LOG_FAMILY_ALIASES[hi][0], BC_HOLLOW_LOG_FAMILY_ALIASES[hi][1])
    }

    for (var pi = 0; pi < BC_LADDER_PLANK_ALIASES.length; pi++) {
        var alias = BC_LADDER_PLANK_ALIASES[pi]
        var tag = 'kubejs:planks/' + alias[0]
        event.add(tag, alias[1])
        event.add(tag, alias[2])
    }

    event.add('minecraft:planks', 'fallout_wastelands_:joshua_tree_woodplanks')
}

ServerEvents.tags('item', function (event) {
    bcAddWoodFamilyAliases(event)

    // Quark supplies dedicated outputs for the named families above. Every
    // other loaded plank remains useful by falling back to the oak ladder.
    event.add(BC_OAK_LADDER_INPUT_TAG, '#minecraft:planks')
    for (var i = 0; i < BC_LADDER_PLANK_ALIASES.length; i++) {
        var alias = BC_LADDER_PLANK_ALIASES[i]
        if (alias[0] === 'oak') continue
        event.remove(BC_OAK_LADDER_INPUT_TAG, alias[1])
        event.remove(BC_OAK_LADDER_INPUT_TAG, alias[2])
    }
})
ServerEvents.tags('block', bcAddWoodFamilyAliases)

ServerEvents.recipes(function (event) {
    for (var i = 0; i < BC_LADDER_PLANK_ALIASES.length; i++) {
        var alias = BC_LADDER_PLANK_ALIASES[i]
        var inputTag = alias[0] === 'oak'
            ? '#' + BC_OAK_LADDER_INPUT_TAG
            : '#kubejs:planks/' + alias[0]
        event.replaceInput({ id: alias[3] }, alias[1], inputTag)
    }
})
