// Minecraft-free behavioral contract for the actual authored KubeJS handler.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const script = fs.readFileSync(process.argv[2], 'utf8')
let handler
const context = { ServerEvents: { recipes(callback) { assert.equal(handler, undefined); handler = callback } } }
vm.createContext(context)
vm.runInContext(script, context, { timeout: 1000 })
const identities = Array.from(context.BC_ROCK_IDENTITY_RECIPES)
assert.ok(identities.length > 0)
assert.equal(new Set(identities).size, identities.length)
assert.ok(identities.every(id => /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id)))
assert.equal(context.BC_FUNCTIONAL_ROCK_RECIPES, undefined, 'generic uses must not require an allowlist')

const criticalIdentities = [
    'minecraft:stone', 'minecraft:smooth_stone', 'minecraft:stone_bricks',
    'minecraft:stone_slab', 'minecraft:stone_stairs', 'minecraft:cobblestone_slab',
    'minecraft:cobblestone_stairs', 'minecraft:cobblestone_wall',
    'minecraft:stone_slab_from_stone_stonecutting',
    'minecraft:cobblestone_wall_from_cobblestone_stonecutting',
    'minecraft:andesite', 'minecraft:diorite',
    'minecraft:mossy_cobblestone_from_vine',
    'immersive_weathering:mossy_stone_from_vine',
    'bloodmagic:altar/slate', 'bloodmagic:arc/mossify_cobblestone',
    'create:haunting/blackstone',
    'better_ore_geology:crafting/ore_reassembly/ironstone',
    'unearthed:stone_regolith', 'quark:building/crafting/cobblestone_bricks',
    'adchimneys:stone_chimney', 'adchimneys:cobblestone_chimney',
    'minecraft:stone_button', 'minecraft:stone_pressure_plate'
]
assert.ok(criticalIdentities.every(id => identities.includes(id)))
const genericIds = [
    'minecraft:piston', 'minecraft:lever', 'minecraft:comparator',
    'minecraft:dispenser', 'minecraft:dropper', 'minecraft:observer', 'minecraft:repeater',
    'minecraft:stonecutter', 'epicfight:stone_greatsword',
    'create:crafting/kinetics/sticker', 'create:crafting/logistics/redstone_contact',
    'createsifter:sifter', 'sophisticatedstorage:controller', 'vs_eureka:engine',
    'littlelogistics:tug_dock', 'bloodmagic:blood_rune_blank', 'ars_nouveau:deny_scroll',
    // Real omissions from the former functional allowlist.
    'minecraft:coast_armor_trim_smithing_template',
    'minecraft:sentry_armor_trim_smithing_template',
    'minecraft:vex_armor_trim_smithing_template',
    'moreartifacts:artifacts_smithing_upgrade_template',
    'bloodmagic:soulforge/demon_crucible', 'bloodmagic:soulforge/resonator',
    'bloodmagic:alchemytable/reagent_lava', 'goety:enchant/blast_protection',
    'occultism:ritual/familiar_blacksmith', 'quark:tools/crafting/rune_duplication',
    'quark:building/crafting/stone_lamp', 'supplementaries:stone_lamp',
    'dawnoftimebuilder:stone_oven',
    // Generic downcycling is not a rock-family identity product.
    'create:milling/cobblestone', 'ars_nouveau:crush_stone', 'create:mixing/lava_from_cobble',
    // Neither mod namespaces nor names may become implicit functional allowlists.
    'minecraft:new_machine', 'create:new_machine', 'futuremod:new_machine',
    'futuremod:new_recipe', 'minecraft:stone_slab_copy'
]
assert.ok(genericIds.every(id => !identities.includes(id)))
const recipes = new Map()
for (const id of [...identities, ...genericIds]) recipes.set(id, { inputs: ['minecraft:stone', 'minecraft:cobblestone'] })
recipes.set('native:authored_tag', { inputs: ['#forge:stone', '#forge:cobblestone'] })
recipes.set('native:variant', { inputs: ['unearthed:limestone', 'unearthed:cobbled_limestone'] })
recipes.set('minecraft:stone_pickaxe', { inputs: ['#minecraft:stone_tool_materials'] })
recipes.set('tconstruct:tools/materials/rock/stone', { inputs: ['#forge:normal_stone'] })
recipes.set('tconstruct:workstation', { inputs: ['#tconstruct:workstation_rock'] })
let calls = 0
let furnace
const event = {
    replaceInput(filter, from, to) {
        assert.deepEqual(Object.keys(filter), ['not'])
        assert.deepEqual(Array.from(filter.not), identities)
        assert.ok(['minecraft:stone', 'minecraft:cobblestone'].includes(from))
        assert.equal(to, from === 'minecraft:stone' ? '#forge:stone' : '#forge:cobblestone')
        // Pinned KubeJS RecipeFilter semantics: array = OR of exact ID strings;
        // not negates the entire OR. No namespace or output-name classification.
        for (const [id, recipe] of recipes) {
            if (!filter.not.includes(id)) recipe.inputs = recipe.inputs.map(input => input === from ? to : input)
        }
        calls++
    },
    shaped(output, pattern, key) {
        assert.equal(furnace, undefined)
        furnace = { output, pattern: Array.from(pattern), key: { ...key } }
        return { id(id) { furnace.id = id } }
    }
}
handler(event)
assert.equal(calls, 2)
for (const id of identities) assert.deepEqual(recipes.get(id).inputs, ['minecraft:stone', 'minecraft:cobblestone'])
for (const id of genericIds) assert.deepEqual(recipes.get(id).inputs, ['#forge:stone', '#forge:cobblestone'])
assert.deepEqual(recipes.get('native:authored_tag').inputs, ['#forge:stone', '#forge:cobblestone'])
assert.deepEqual(recipes.get('native:variant').inputs, ['unearthed:limestone', 'unearthed:cobbled_limestone'])
assert.deepEqual(recipes.get('minecraft:stone_pickaxe').inputs, ['#minecraft:stone_tool_materials'])
assert.deepEqual(recipes.get('tconstruct:tools/materials/rock/stone').inputs, ['#forge:normal_stone'])
assert.deepEqual(recipes.get('tconstruct:workstation').inputs, ['#tconstruct:workstation_rock'])
assert.deepEqual(furnace, {
    output: 'minecraft:furnace', pattern: ['SSS', 'S S', 'SSS'],
    key: { S: '#kubejs:furnace_materials' }, id: 'kubejs:crafting/furnace_from_stone_or_cobblestone'
})
const accepts = (ingredient, item) => ingredient === item ||
    ingredient === '#forge:stone' && ['minecraft:stone', 'minecraft:basalt'].includes(item) ||
    ingredient === '#forge:cobblestone' && ['minecraft:cobblestone', 'minecraft:cobbled_deepslate'].includes(item)
assert.equal(accepts(recipes.get('minecraft:stone').inputs[1], 'minecraft:cobbled_deepslate'), false)
assert.equal(accepts(recipes.get('minecraft:smooth_stone').inputs[0], 'minecraft:basalt'), false)
assert.equal(accepts(recipes.get('minecraft:stone_slab').inputs[0], 'minecraft:basalt'), false)
assert.equal(accepts(recipes.get('minecraft:piston').inputs[1], 'minecraft:cobbled_deepslate'), true)
assert.equal(accepts(recipes.get('minecraft:sentry_armor_trim_smithing_template').inputs[1], 'minecraft:cobbled_deepslate'), true)
console.log(JSON.stringify({ identity_recipes: identities.length, generic_examples: genericIds.length, replacements: calls, furnace_preserved: true, tcon_preserved: true }))
