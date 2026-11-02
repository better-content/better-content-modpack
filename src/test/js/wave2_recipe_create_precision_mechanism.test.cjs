// Focused source/archive unit only; no Forge conditions, Create recipe manager or loaded processing.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const { execFileSync } = require('node:child_process')

const id = 'sequenced_assembly/precision_mechanism'
const root = path.resolve(__dirname, '../../../kubejs/data/create/recipes')
const cache = path.join(os.homedir(), '.gradle/caches/forge_gradle/deobf_dependencies/com/simibubi/create/create-1.20.1')
const jars = process.env.CREATE_RECIPE_JAR ? [process.env.CREATE_RECIPE_JAR] :
  fs.readdirSync(cache).filter(dir => dir.startsWith('6.0.8-')).flatMap(dir =>
    fs.readdirSync(path.join(cache, dir)).filter(name => name.endsWith('-slim.jar'))
      .map(name => path.join(cache, dir, name)))
const original = jar => JSON.parse(execFileSync('unzip', ['-p', jar, `data/create/recipes/${id}.json`],
  { encoding: 'utf8' }))
const loadedCondition = [{ type: 'forge:mod_loaded', modid: 'better_manufacturing' }]
const absentCondition = [{ type: 'forge:not', value: loadedCondition[0] }]
const part = { item: 'tconstruct:tool_handle' }

// Lock the original chance pool as well as the primary result: cogwheel/shaft
// byproducts must not be silently removed to bypass the user's part cost.
const weightedResults = [
  { chance: 120.0, item: 'create:precision_mechanism' },
  { chance: 8.0, item: 'create:golden_sheet' },
  { chance: 8.0, item: 'create:andesite_alloy' },
  { chance: 5.0, item: 'create:cogwheel' },
  { chance: 3.0, item: 'minecraft:gold_nugget' },
  { chance: 2.0, item: 'create:shaft' },
  { chance: 2.0, item: 'create:crushed_raw_gold' },
  { item: 'minecraft:iron_ingot' },
  { item: 'minecraft:clock' }
]

test('pinned Create precision assembly has five paid loops and intact weighted byproducts', () => {
  assert.ok(jars.length > 0, 'Pinned Create 6.0.8 jar required, or set CREATE_RECIPE_JAR')
  for (const jar of jars) {
    const native = original(jar)
    assert.equal(native.type, 'create:sequenced_assembly')
    assert.deepEqual(native.ingredient, { tag: 'forge:plates/gold' })
    assert.deepEqual(native.transitionalItem, { item: 'create:incomplete_precision_mechanism' })
    assert.equal(native.loops, 5)
    assert.deepEqual(native.results, weightedResults)
    assert.deepEqual(native.sequence.map(step => step.ingredients[1]), [
      { item: 'create:cogwheel' }, { item: 'create:large_cogwheel' }, { tag: 'forge:nuggets/iron' }
    ])
    assert.ok(native.sequence.every(step => step.type === 'create:deploying'))
  }
})

test('loaded exact ID keeps native five-loop costs/results and pays five real tool handles', () => {
  assert.ok(jars.length > 0, 'Pinned Create 6.0.8 jar required, or set CREATE_RECIPE_JAR')
  const override = JSON.parse(fs.readFileSync(path.join(root, `${id}.json`), 'utf8'))
  assert.equal(override.type, 'forge:conditional')
  assert.equal(override.recipes.length, 2)
  assert.deepEqual(override.recipes[0].conditions, loadedCondition)
  assert.deepEqual(override.recipes[1].conditions, absentCondition)
  const configured = override.recipes[0].recipe
  for (const jar of jars) {
    const native = original(jar)
    assert.deepEqual(override.recipes[1].recipe, native, 'No-Mod branch is exactly pinned native recipe')
    assert.equal(configured.type, native.type)
    assert.deepEqual(configured.ingredient, native.ingredient)
    assert.deepEqual(configured.transitionalItem, native.transitionalItem)
    assert.equal(configured.loops, native.loops)
    assert.deepEqual(configured.results, native.results, 'all nine weighted outputs preserved')
    assert.equal(configured.sequence.length, native.sequence.length + 1)
    assert.deepEqual(configured.sequence.slice(0, native.sequence.length), native.sequence,
      'all three existing operations and paid costs in every loop stay unchanged')
    const added = configured.sequence.at(-1)
    assert.deepEqual(added, {
      type: 'create:deploying',
      ingredients: [native.transitionalItem, part],
      results: [native.transitionalItem]
    })
    assert.equal(configured.loops * configured.sequence.filter(step =>
      step.type === 'create:deploying' && step.ingredients[1]?.item === part.item).length, 5,
    'one part deployment per loop means FIVE consumed handles per completed batch')
  }
})
