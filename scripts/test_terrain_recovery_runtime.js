// Loader-free regression for the retired chemistry audit. Runtime registry and
// actual RecipeManager behavior still require the exact-artifact Minecraft gate.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const source = fs.readFileSync(path.join(root, 'kubejs/server_scripts/processing/terrain_recovery.js'), 'utf8')
const catalogue = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/terrain_recovery.json'), 'utf8'))

function load(data) {
    const installed = [], removed = []
    const event = {
        remove(selector) { removed.push(selector) },
        forEachRecipe(selector, callback) {
            const spec = data.recycling_exclusions.find(entry => entry.id === selector.id)
            if (spec) callback({json: JSON.stringify({
                type: 'create:crushing', ingredients: [{tag: spec.tag}], results: [{item: 'minecraft:gravel'}]
            })})
        },
        custom(recipe) { return {id(id) { installed.push({id, recipe}) }} }
    }
    vm.runInNewContext(source, {
        ServerEvents: {recipes(callback) { callback(event) }},
        JsonIO: {readString() { return JSON.stringify(data) }},
        Item: {exists() { return true }}, // Item registry is checked in Minecraft, not simulated here.
        console: {info() {}}
    }, {filename: 'terrain_recovery.js', timeout: 3000})
    return {installed, removed}
}

assert.ok(!Object.hasOwn(catalogue, 'chemistry_audit'), 'retired Chemlib audit must remain absent')
const {installed} = load(catalogue)
assert.equal(installed.length, catalogue.entries.length + catalogue.recycling_exclusions.length)
assert.ok(installed.some(({recipe}) => recipe.results?.some(output => output.item === 'bloodmagic:saltpeter')),
    'guano must retain the real Blood Magic saltpeter route')
const unexpected = structuredClone(catalogue)
unexpected.profiles.guano.chemical[0].item = 'minecraft:diamond'
assert.throws(() => load(unexpected), /invalid chemical output guano/,
    'removed or unknown chemistry must not silently pass')
console.log('PASS terrain recovery: retired audit, retained saltpeter, rejected unapproved chemical output')
