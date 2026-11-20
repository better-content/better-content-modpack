#!/usr/bin/env node
// Regression: the compile-only World Management item must not be emitted in a shipping recipe.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const script = fs.readFileSync(path.join(__dirname, '..', 'kubejs/server_scripts/progression/80_tech_palette.js'), 'utf8')

function execute(worldManagementInstalled) {
  const recipes = []
  const removed = []
  const event = {
    remove(query) { removed.push(query.output) },
    shaped(result, pattern, key) {
      if (result === 'better_world_management:world_condenser_interface' && !worldManagementInstalled) {
        throw Error('KubeJS would reject absent World Management result')
      }
      assert.ok(Array.isArray(pattern) && pattern.length > 0 && key)
      return { id(id) { recipes.push({ type: 'shaped', result, id }) } }
    },
    custom(definition) { return { id(id) { recipes.push({ type: 'custom', result: definition.results[0].item, id }) } } }
  }
  vm.runInNewContext(script, {
    ServerEvents: { recipes(callback) { callback(event) } },
    Platform: { isLoaded(mod) {
      assert.ok(['better_manufacturing', 'better_world_management'].includes(mod))
      return mod === 'better_manufacturing' || worldManagementInstalled
    } },
    global: { bcPncrStack(item, count) { return { item, count } } }
  }, { filename: '80_tech_palette.js', timeout: 1000 })
  return { recipes, removed }
}
const missing = execute(false)
const installed = execute(true)
const wlm = 'better_world_management:world_condenser_interface'
assert.ok(!missing.removed.includes(wlm))
assert.ok(!missing.recipes.some(row => row.result === wlm))
assert.ok(installed.removed.includes(wlm))
assert.equal(installed.recipes.filter(row => row.result === wlm).length, 1)
assert.equal(installed.recipes.find(row => row.result === wlm).id,
  'kubejs:tech/world_condenser_interface_meteor_gate')
assert.deepEqual(missing.recipes.filter(row => row.result !== wlm),
  installed.recipes.filter(row => row.result !== wlm))
console.log('tech palette optional World Management recipe guards passed')
