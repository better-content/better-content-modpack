// Isolated recipe-JSON unit checks only. No KubeJS/Forge loader or native recipe-manager claim.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const { execFileSync } = require('node:child_process')

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

test('each exact alternative consumes a part in loaded native assembly; no-Mod preserves pinned recipe', () => {
  const cache = path.join(os.homedir(), '.gradle/caches/forge_gradle/deobf_dependencies/com/simibubi/create/create-1.20.1')
  const candidates = process.env.CREATE_RECIPE_JAR ? [process.env.CREATE_RECIPE_JAR] :
    fs.readdirSync(cache).filter(dir => dir.startsWith('6.0.8-')).flatMap(dir =>
      fs.readdirSync(path.join(cache, dir)).filter(name => name.endsWith('-slim.jar'))
        .map(name => path.join(cache, dir, name)))
  assert.ok(candidates.length > 0, 'Pinned Create 6.0.8 recipe archive required (or set CREATE_RECIPE_JAR)')
  for (const [id, native] of cases) {
    const override = read(id)
    assert.equal(override.type, 'forge:conditional', id)
    assert.equal(override.recipes.length, 2, `${id}: loaded and no-Mod branches`)
    assert.deepEqual(override.recipes[0].conditions, [{ type: 'forge:mod_loaded', modid: 'better_manufacturing' }])
    assert.deepEqual(override.recipes[1].conditions, [absent])
    const loaded = override.recipes[0].recipe
    const fallback = override.recipes[1].recipe
    assert.equal(loaded.type, 'create:sequenced_assembly', `${id}: original operation must stay Create-native`)
    assert.deepEqual(loaded.ingredient, native.ingredients[0], `${id}: original base item still paid`)
    assert.deepEqual(loaded.results, native.results, `${id}: original result and count retained`)
    assert.equal(loaded.loops, 1, `${id}: one complete original input per batch`)
    assert.deepEqual(loaded.transitionalItem, { item: 'create:incomplete_precision_mechanism' },
      `${id}: reuses existing Create-managed, recipe-ID-tagged assembly carrier`)
    assert.equal(loaded.sequence.length, 2, `${id}: native process and separate part-consuming deploy`)
    const [originalStep, partStep] = loaded.sequence
    assert.equal(originalStep.type, native.type, `${id}: retains original cutting/deploying operation`)
    assert.deepEqual(originalStep.ingredients, [loaded.transitionalItem, ...native.ingredients.slice(1)],
      `${id}: original secondary inputs still paid in first native operation`)
    assert.deepEqual(originalStep.results, [loaded.transitionalItem], `${id}: carrier passes to next operation`)
    if ('processingTime' in native)
      assert.equal(originalStep.processingTime, native.processingTime, `${id}: retains original cutting duration`)
    assert.equal(partStep.type, 'create:deploying', `${id}: part is actually consumed by Create deploying`)
    assert.deepEqual(partStep.ingredients, [loaded.transitionalItem, { item: 'tconstruct:tool_handle' }])
    assert.deepEqual(partStep.results, [loaded.transitionalItem])
    for (const jar of candidates) {
      const archived = JSON.parse(execFileSync('unzip', ['-p', jar, `data/create/recipes/${id}.json`],
        { encoding: 'utf8' }))
      assert.deepEqual(archived, native, `${id}: pinned native archive body did not change`)
      assert.deepEqual(fallback, archived, `${id}: absent-Manufacturing recipe exactly native`)
    }
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
