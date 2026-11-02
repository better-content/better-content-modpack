// Focused, loader-free contract tests for the authored KubeJS polymer recipes.
// Run: node scripts/test_polymer_manufacturing.js
// This checks the script's emitted JSON model, NOT effective/native RecipeManager behavior.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const script = fs.readFileSync(path.join(__dirname, '..', 'kubejs', 'server_scripts',
    'progression', '46_polymer_manufacturing.js'), 'utf8')
const dependencies = ['better_manufacturing', 'pneumaticcraft', 'createdieselgenerators',
    'create', 'chemlib', 'brewinandchewin']

function emittedRecipes(missing = '') {
    const recipes = new Map()
    const event = {
        custom(json) {
            return { id(id) {
                assert.ok(!recipes.has(id), `duplicate polymer recipe ID ${id}`)
                recipes.set(id, json)
            } }
        },
        remove() { assert.fail('polymer chain must not remove existing native plastic routes') }
    }
    vm.runInNewContext(script, {
        Platform: { isLoaded(id) { return id !== missing && dependencies.includes(id) } },
        ServerEvents: { recipes(callback) { callback(event) } }
    }, {filename: '46_polymer_manufacturing.js', timeout: 1000})
    return recipes
}

for (const absent of dependencies) {
    assert.equal(emittedRecipes(absent).size, 0, `partial polymer route when ${absent} is missing`)
}

const recipes = emittedRecipes()
const get = id => {
    const key = `kubejs:polymers/${id}`
    assert.ok(recipes.has(key), `missing ${key}`)
    return recipes.get(key)
}
assert.equal(recipes.size, 20, 'both authored chains must be present and uniquely named')

const culture = get('lactic_culture')
assert.ok(culture.ingredients.some(input => input.item === 'minecraft:milk_bucket'),
    'milk must be paid for each culture batch')
assert.equal(culture.results.filter(output => output.item === 'minecraft:bucket').length, 1,
    'Create BasinRecipe consumes the milk bucket; return exactly one empty container')
assert.equal(culture.results.find(output => output.item === 'minecraft:bucket').count ?? 1, 1)
assert.equal(culture.results.find(output => output.item === 'better_manufacturing:lactic_culture').count, 2)

assert.equal(get('lactic_fermentation').type, 'createdieselgenerators:bulk_fermenting')
assert.equal(get('lactic_broth_concentration').results.length, 2, 'concentration must recover water')
assert.equal(get('lactide_under_vacuum').pressure, -0.5, 'lactide must require native vacuum')
assert.equal(get('pla_ring_opening').fluid_output.fluid, 'better_manufacturing:molten_pla')
assert.equal(get('propylene_coordination_polymerisation').item_output.item,
    'better_manufacturing:pp_stock')
assert.equal(get('pp_metered_melt').fluid_output.fluid, 'better_manufacturing:molten_pp')
assert.equal(get('pla_stock_cooling').item_output.item, 'better_manufacturing:pla_stock')
assert.equal(get('pp_stock_cooling').item_output.item, 'better_manufacturing:pp_stock')
assert.equal(get('butane_fuel').type, 'pneumaticcraft:fuel_quality')
assert.ok(!Array.from(recipes.values()).some(json => JSON.stringify(json).includes('pneumaticcraft:plastic')),
    'do not replace or silently conflate the two polymers with native generic plastic')

function polymerData(relative) {
    return JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'kubejs', 'data',
        'better_manufacturing', relative), 'utf8'))
}
for (const [stock, material, melt, headDurability, headMiningSpeed, handleDurability, handleMiningSpeed] of [
    ['pla', 'pla', 'molten_pla', 85, 2.5, -0.15, -0.10],
    ['pp', 'polypropylene', 'molten_pp', 140, 3.0, 0.05, -0.05]
]) {
    const stockId = `better_manufacturing:${stock}_stock`
    const materialId = `better_manufacturing:${material}`
    const fluidId = `better_manufacturing:${melt}`
    const stockRecipe = polymerData(`recipes/polymer_${stock}_stock_material.json`)
    const fluidRecipe = polymerData(`recipes/polymer_${stock}_melt_material.json`)
    assert.equal(stockRecipe.recipes[0].recipe.ingredient.item, stockId)
    assert.equal(stockRecipe.recipes[0].recipe.material, materialId)
    assert.equal(fluidRecipe.recipes[0].recipe.fluid.fluid, fluidId)
    assert.equal(fluidRecipe.recipes[0].recipe.fluid.amount, 90)
    assert.equal(fluidRecipe.recipes[0].recipe.output, materialId)
    assert.equal(polymerData(`tinkering/materials/definition/${material}.json`).tier, 1)
    // Machine rates now derive from native TCon part stats, not a second
    // material opt-in/screen-rate table. Do not restore the obsolete policy.
    assert.equal(fs.existsSync(path.join(__dirname, '..', 'kubejs', 'data',
        'better_manufacturing', 'engineering', 'materials', `${material}.json`)), false,
        `${material} must not restore a separate machine-rating table`)
    const stats = polymerData(`tinkering/materials/stats/${material}.json`).stats
    assert.equal(stats['tconstruct:head'].durability, headDurability,
        `${material} must retain its native TCon cutter durability`)
    assert.equal(stats['tconstruct:head'].mining_speed, headMiningSpeed,
        `${material} must retain its native TCon head mining speed`)
    assert.equal(stats['tconstruct:handle'].durability, handleDurability,
        `${material} must retain its ordinary TCon handle durability modifier`)
    assert.equal(stats['tconstruct:handle'].mining_speed, handleMiningSpeed,
        `${material} must retain its ordinary TCon handle mining-speed modifier`)
}
console.log(`PASS polymer authored-contract: ${dependencies.length} missing-mod guards; ${recipes.size} distinct recipes; reusable culture bucket, distinct PLA/PP native TCon head/handle stats, no legacy machine-rating tables and four 90-mB material links`)
