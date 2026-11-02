// Isolated recipe-JSON unit checks only. No KubeJS/Forge loader or native recipe-manager claim.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '../../../kubejs/data/create/recipes')
const cases = [
  ['cutting/andesite_alloy', {
    type: 'create:cutting', ingredients: [{ item: 'create:andesite_alloy' }],
    processingTime: 200, results: [{ count: 6, item: 'create:shaft' }]
  }, 'shaft'],
  ['deploying/cogwheel', {
    type: 'create:deploying',
    ingredients: [{ item: 'create:shaft' }, { tag: 'minecraft:planks' }],
    results: [{ item: 'create:cogwheel' }]
  }, 'cogwheel'],
  ['deploying/large_cogwheel', {
    type: 'create:deploying',
    ingredients: [{ item: 'create:cogwheel' }, { tag: 'minecraft:planks' }],
    results: [{ item: 'create:large_cogwheel' }]
  }, 'large_cogwheel']
]
const read = file => JSON.parse(fs.readFileSync(path.join(root, file + '.json'), 'utf8'))
const absent = { type: 'forge:not', value: { type: 'forge:mod_loaded', modid: 'better_manufacturing' } }

test('three exact native one/two-input bypasses retain original recipes only without Manufacturing', () => {
  for (const [id, native] of cases) {
    const wrapper = read(id)
    assert.equal(wrapper.type, 'forge:conditional', id)
    assert.equal(wrapper.recipes.length, 1, `${id}: no unparted Manufacturing branch`)
    assert.deepEqual(wrapper.recipes[0].conditions, [absent], `${id}: no-Mod-only condition`)
    assert.deepEqual(wrapper.recipes[0].recipe, native, `${id}: original type/inputs/time/count/result`)
  }
})

test('part-gated primary shaft/cogwheel routes remain distinct, conditional and obtainable', () => {
  for (const [id, , output] of cases) {
    const primary = read('crafting/kinetics/' + output)
    assert.equal(primary.type, 'forge:conditional', id)
    assert.equal(primary.recipes.length, 2, `${output}: loaded and no-Mod branches`)
    assert.deepEqual(primary.recipes[0].conditions, [{ type: 'forge:mod_loaded', modid: 'better_manufacturing' }])
    assert.deepEqual(primary.recipes[1].conditions, [absent])
    const loaded = primary.recipes[0].recipe
    const fallback = primary.recipes[1].recipe
    const inputs = recipe => recipe.ingredients || recipe.pattern.flatMap(row =>
      [...row].filter(symbol => symbol !== ' ').map(symbol => recipe.key[symbol]))
    assert.equal(loaded.result.item, 'create:' + output)
    assert.deepEqual(loaded.result, fallback.result, `${output}: unchanged native result/count`)
    assert.equal(inputs(loaded).filter(item => item.item === 'tconstruct:tool_handle').length, 1,
      `${output}: exactly one consumed part in primary branch`)
    assert.equal(inputs(fallback).filter(item => item.item === 'tconstruct:tool_handle').length, 0,
      `${output}: no Manufacturing dependency in fallback`)
  }
})
