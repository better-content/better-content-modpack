const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { isDeepStrictEqual } = require('node:util')

const root = path.resolve(__dirname, '../../..')
const installer = fs.readFileSync(path.join(root, 'kubejs/server_scripts/progression/84_cooking_recipes.js'), 'utf8')
const baseline = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/cooking_recipes.json'), 'utf8'))
const changed = Object.keys(baseline.routes).filter(id => baseline.routes[id].action !== 'retain')
const market = 'kubejs:food_eras/market_fried_rice'
const creamy = 'kubejs:food_eras/creamy_fig_chicken'

function run(policy = baseline, overrides = {}, queued = {}) {
  const calls = []
  const sources = Object.fromEntries(changed.filter(id => id !== market && id !== creamy)
    .map(id => [id, policy.expected_sources[id]]))
  Object.assign(sources, overrides)
  const pending = Object.entries({ [creamy]: policy.expected_sources[creamy], ...queued })
    .map(([id, json]) => ({ json, removed: false, getId: () => id, serialize() {} }))
  const wrap = x => ({
    toJSON: () => x,
    getAsJsonObject: k => wrap(k === undefined ? x : x[k]),
    getAsJsonArray: k => wrap(x[k]),
    get: k => wrap(x[k]),
    has: k => Object.hasOwn(x, k)
  })
  const mock = {
    Java: { loadClass: () => ({ equals: (a, b) => isDeepStrictEqual(JSON.parse(JSON.stringify(a)), JSON.parse(JSON.stringify(b))) }) },
    JsonIO: { read: () => policy, readJson: () => wrap(policy), of: x => JSON.parse(JSON.stringify(x)) },
    console: { info() {} },
    ServerEvents: { recipes: f => f({
      addedRecipes: pending,
      forEachRecipe: (filter, callback) => {
        if (Object.hasOwn(sources, filter.id)) callback({ json: sources[filter.id] })
      },
      custom: json => ({ id: id => calls.push(['define', id, JSON.parse(JSON.stringify(json))]) }),
      remove: filter => calls.push(['remove', filter.id])
    }) }
  }
  let error = null
  try { vm.runInNewContext(installer, mock) } catch (caught) { error = String(caught) }
  return { calls, error, removed: pending.filter(row => row.removed).map(row => row.getId()) }
}

test('only 84 installs changed definitions, exactly once, after changed-source validation', () => {
  assert.doesNotMatch(fs.readFileSync(path.join(root, 'kubejs/server_scripts/progression/83_culinary_policy.js'), 'utf8'), /ServerEvents\.recipes/)
  const result = run()
  assert.equal(result.error, null)
  assert.deepEqual(result.calls.map(row => row[1]), baseline.definitions.map(row => row.id))
  assert.deepEqual(result.removed, [creamy])
  assert.equal(result.calls.filter(row => row[1] === market).length, 1)
})

test('source drift for a changed native route fails before any removal/definition', () => {
  const id = 'fruitsdelight:cooking/pineapple_fried_rice'
  const result = run(baseline, { [id]: { type: 'minecraft:smelting' } })
  assert.match(result.error, /Changed source recipe/)
  assert.deepEqual(result.calls, [])
  assert.deepEqual(result.removed, [])
})

test('unrelated retained ore source drift is not a new global gate', () => {
  const result = run(baseline, { 'kaleidoscope_cookery:millstone/coal_from_coal_ore': { type: 'minecraft:smelting' } })
  assert.equal(result.error, null)
})

test('legacy queued market source or altered paid carrier fails before any mutation', () => {
  const old = run(baseline, {}, { [market]: baseline.expected_sources[market] })
  assert.match(old.error, /Old market FD source still active/)
  assert.deepEqual(old.calls, [])
  assert.deepEqual(old.removed, [])
  const policy = structuredClone(baseline)
  const row = policy.routes[market].definitions[0].json
  delete row.carrier
  const changedPort = run(policy)
  assert.match(changedPort.error, /Fried-rice cost\/output drift/)
  assert.deepEqual(changedPort.calls, [])
  assert.deepEqual(changedPort.removed, [])
})
