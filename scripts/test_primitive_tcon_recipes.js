// Primitive tools supply only their part materials. TConstruct's ToolStack.verifyTag
// (when loaded) and ModifiableItem.inventoryTick -> ensureInitialized rebuild native
// stats, modifiers, slots, and multipliers; serialized tic_stats would bypass that
// initialization and freeze numbers from a different tool definition/material set.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

const root = path.join(__dirname, '..')
const directory = path.join(root, 'kubejs/data/kubejs/recipes/primitive')
const index = fs.readFileSync(path.join(root, 'index.toml'), 'utf8')
const pack = fs.readFileSync(path.join(root, 'pack.toml'), 'utf8')
const indexHash = crypto.createHash('sha256').update(index).digest('hex')
assert.ok(pack.includes(`hash = "${indexHash}"`), 'pack.toml must point to the current index')
const expected = {
    bone: 'tconstruct:bone',
    flint: 'tconstruct:flint',
    rock: 'tconstruct:rock#stone'
}
const tool = {
    hand_axe: ['tconstruct:hand_axe', 'tconstruct:string'],
    butcher_knife: ['additionalweaponry:butcher_knife', null]
}
const ids = fs.readdirSync(directory).filter(name => /^(bone|flint|rock)_(hand_axe|butcher_knife)\.json$/.test(name))
assert.equal(ids.length, 6, 'all six primitive material/tool combinations must be covered')
for (const [material, part] of Object.entries(expected)) {
    for (const [shape, [item, binding]] of Object.entries(tool)) {
        const name = `${material}_${shape}.json`
        assert.ok(ids.includes(name), `missing ${name}`)
        const bytes = fs.readFileSync(path.join(directory, name))
        const recipe = JSON.parse(bytes)
        assert.equal(recipe.type, 'minecraft:crafting_shapeless')
        assert.equal(recipe.result.item, item)
        assert.equal(recipe.result.count, 1)
        const materials = [part, 'tconstruct:wood', binding ?? part]
        assert.equal(recipe.result.nbt, `{tic_materials:${JSON.stringify(materials)}}`,
            `${name} must let TCon compute every derived stat and trait from its parts`)
        const relative = `kubejs/data/kubejs/recipes/primitive/${name}`
        const hash = crypto.createHash('sha256').update(bytes).digest('hex')
        assert.ok(index.includes(`file = "${relative}"\nhash = "${hash}"`),
            `${name} must be packaged with its current hash`)
    }
}
console.log('PASS primitive TCon: six material-only recipes and Packwiz hashes')
