// Minecraft-free behavioral contract for the actual authored KubeJS handler.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const script = fs.readFileSync(process.argv[2], 'utf8')
let handler
const context = { ServerEvents: { recipes(callback) { assert.equal(handler, undefined); handler = callback } } }
vm.createContext(context)
vm.runInContext(script, context, { timeout: 1000 })
const ids = Array.from(context.BC_FUNCTIONAL_ROCK_RECIPES)
assert.ok(ids.length > 0)
assert.equal(new Set(ids).size, ids.length)
assert.ok(ids.every(id => /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id)))

const protectedIds = [
    'minecraft:stone', 'minecraft:smooth_stone', 'minecraft:stone_bricks',
    'minecraft:stone_slab', 'minecraft:stone_stairs', 'minecraft:cobblestone_slab',
    'minecraft:cobblestone_stairs', 'minecraft:cobblestone_wall',
    'minecraft:stone_slab_from_stone_stonecutting',
    'minecraft:cobblestone_wall_from_cobblestone_stonecutting',
    'minecraft:andesite', 'minecraft:diorite',
    'minecraft:mossy_cobblestone_from_vine',
    'immersive_weathering:mossy_stone_from_vine',
    'bloodmagic:altar/slate', 'bloodmagic:arc/mossify_cobblestone',
    'create:milling/cobblestone', 'create:haunting/blackstone',
    'better_ore_geology:crafting/ore_reassembly/ironstone',
    'unearthed:stone_regolith', 'quark:building/crafting/cobblestone_bricks',
    'adchimneys:stone_chimney', 'adchimneys:cobblestone_chimney',
    'minecraft:stone_button', 'minecraft:stone_pressure_plate',
    // Future recipes must default closed, even in an approved namespace.
    'minecraft:new_stone_variant', 'create:new_machine', 'futuremod:new_machine'
]
assert.ok(protectedIds.every(id => !ids.includes(id)))
const recipes = new Map()
for (const id of [...ids, ...protectedIds]) recipes.set(id, { inputs: ['minecraft:stone', 'minecraft:cobblestone'] })
// Native tags and variant-specific ingredients must not be altered or narrowed.
recipes.set('native:authored_tag', { inputs: ['#forge:stone', '#forge:cobblestone'] })
recipes.set('native:variant', { inputs: ['unearthed:limestone', 'unearthed:cobbled_limestone'] })
recipes.set('minecraft:stone_pickaxe', { inputs: ['#minecraft:stone_tool_materials'] })
let calls = 0
let furnace
const event = {
    replaceInput(filter, from, to) {
        assert.deepEqual(Object.keys(filter), ['id'])
        assert.ok(ids.includes(filter.id))
        assert.ok(['minecraft:stone', 'minecraft:cobblestone'].includes(from))
        assert.equal(to, from === 'minecraft:stone' ? '#forge:stone' : '#forge:cobblestone')
        const recipe = recipes.get(filter.id)
        recipe.inputs = recipe.inputs.map(input => input === from ? to : input)
        calls++
    },
    shaped(output, pattern, key) {
        assert.equal(furnace, undefined)
        furnace = { output, pattern: Array.from(pattern), key: { ...key } }
        return { id(id) { furnace.id = id } }
    }
}
handler(event)
assert.equal(calls, 2 * ids.length)
for (const id of ids) assert.deepEqual(recipes.get(id).inputs, ['#forge:stone', '#forge:cobblestone'])
for (const id of protectedIds) assert.deepEqual(recipes.get(id).inputs, ['minecraft:stone', 'minecraft:cobblestone'])
assert.deepEqual(recipes.get('native:authored_tag').inputs, ['#forge:stone', '#forge:cobblestone'])
assert.deepEqual(recipes.get('native:variant').inputs, ['unearthed:limestone', 'unearthed:cobbled_limestone'])
assert.deepEqual(recipes.get('minecraft:stone_pickaxe').inputs, ['#minecraft:stone_tool_materials'])
assert.deepEqual(furnace, {
    output: 'minecraft:furnace', pattern: ['SSS', 'S S', 'SSS'],
    key: { S: '#kubejs:furnace_materials' }, id: 'kubejs:crafting/furnace_from_stone_or_cobblestone'
})
// The old substitutions made these native material-specific recipes intersect.
const accepts = (ingredient, item) => ingredient === item ||
    ingredient === '#forge:stone' && ['minecraft:stone', 'minecraft:basalt'].includes(item) ||
    ingredient === '#forge:cobblestone' && ['minecraft:cobblestone', 'minecraft:cobbled_deepslate'].includes(item)
assert.equal(accepts(recipes.get('minecraft:stone').inputs[1], 'minecraft:cobbled_deepslate'), false)
assert.equal(accepts(recipes.get('minecraft:smooth_stone').inputs[0], 'minecraft:basalt'), false)
assert.equal(accepts(recipes.get('minecraft:stone_slab').inputs[0], 'minecraft:basalt'), false)
assert.equal(accepts(recipes.get('minecraft:piston').inputs[1], 'minecraft:cobbled_deepslate'), true)
console.log(JSON.stringify({ functional_recipes: ids.length, protected_recipes: protectedIds.length, replacements: calls, furnace_preserved: true }))
