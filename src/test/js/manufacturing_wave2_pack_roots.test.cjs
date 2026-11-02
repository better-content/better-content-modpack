// Isolated Node unit checks for authored shared KubeJS roots and native TCon material assets.
// Native recipe-manager acceptance requires separate authorization and is not inferred here.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const scripts = path.resolve(__dirname, '../../../kubejs/server_scripts/progression')
const nativeStats = path.resolve(__dirname, '../../../kubejs/data/better_manufacturing/tinkering/materials/stats')
function recipes(file, manufacturing) {
  const added = []
  const event = {
    remove() {}, replaceInput() {},
    shaped(output, pattern, key) {
      const recipe = { type: 'minecraft:crafting_shaped', output, pattern, key }
      return { id(id) { recipe.id = id; added.push(recipe) } }
    },
    custom(recipe) { return { id(id) { added.push({ ...recipe, id }) } } },
    shapeless(output, inputs) { return { id(id) { added.push({ type: 'minecraft:crafting_shapeless', output, inputs, id }) } } }
  }
  const context = {
    Platform: { isLoaded(mod) { return mod === 'better_manufacturing' && manufacturing } },
    ServerEvents: { recipes(callback) { callback(event) } }
  }
  vm.runInNewContext(fs.readFileSync(path.join(scripts, file), 'utf8'), context, { filename: file })
  return added
}
function get(added, id) {
  const found = added.filter(recipe => recipe.id === id)
  assert.equal(found.length, 1, `${id} must have one effective shared root`)
  return found[0]
}
function ingredients(recipe) {
  const result = []
  const grid = recipe.pattern
  const keys = Object.keys(recipe.key)
  assert.equal(new Set(grid.map(row => row.length)).size, 1, 'rectangular crafting grid')
  for (const row of grid) for (const symbol of row) {
    if (symbol === ' ') continue
    assert.ok(keys.includes(symbol), `missing ${symbol} key in ${recipe.id}`)
    const value = recipe.key[symbol]
    result.push(typeof value === 'string' ? value : value.item || '#' + value.tag)
  }
  for (const symbol of keys) assert.ok(grid.some(row => row.includes(symbol)), `unused ${symbol} key in ${recipe.id}`)
  return result
}
const powered = [
  ['millstone', ['minecraft:stone', 'create:cogwheel', 'minecraft:andesite'], 'tconstruct:large_plate', 6],
  ['mechanical_press', ['minecraft:iron_block', 'create:shaft', 'kubejs:andesite_machine_block'], 'tconstruct:large_plate', 1],
  ['mechanical_mixer', ['create:whisk', 'create:shaft', '#forge:plates/iron'], 'create:whisk', 2],
  ['mechanical_pump', ['kubejs:copper_machine_block', 'create:andesite_alloy'], 'tconstruct:tool_handle', 0]
]
test('powered-work roots retain native gates and no-Manufacturing recipes', () => {
  for (const loaded of [false, true]) {
    const all = recipes('20_powered_works.js', loaded)
    for (const [name, gates, part, slot] of powered) {
      const root = get(all, `kubejs:powered_works/${name === 'mechanical_mixer' ? '' : 'direct_root/'}${name}`)
      const items = ingredients(root)
      for (const gate of gates) assert.ok(items.includes(gate), `${name} lost ${gate}`)
      assert.equal(items.filter(i => i === 'create:andesite_alloy').length,
        name === 'mechanical_pump' ? 4 : items.filter(i => i === 'create:andesite_alloy').length)
      if (loaded) {
        assert.equal(root.type, 'better_manufacturing:part_variant')
        assert.equal(items.filter(i => i === part).length, 1)
        assert.equal(root.parts.length, 1)
        assert.equal(root.parts[0].slot, slot)
        const symbol = root.pattern.join('')[slot]
        assert.equal(root.key[symbol].item || root.key[symbol], part, `bound input index ${slot} in ${name}`)
      } else {
        assert.equal(root.type, 'minecraft:crafting_shaped')
        assert.ok(!items.some(i => i.startsWith('tconstruct:tool_') || i === 'tconstruct:large_plate'))
      }
    }
  }
})
test('harvester construction consumes a blade without fabricated baked settings', () => {
  for (const loaded of [false, true]) {
    const root = get(recipes('85_dimension_font_ecology.js', loaded), 'kubejs:fonts/bumblezone/mechanical_harvester')
    const items = ingredients(root)
    assert.equal(root.type, 'minecraft:crafting_shaped')
    for (const gate of ['the_bumblezone:pollen_puff', '#forge:plates/iron', 'create:andesite_casing']) {
      assert.ok(items.includes(gate))
    }
    assert.equal(items.filter(i => i === 'create:andesite_alloy').length, 4)
    assert.equal(items.filter(i => i === 'tconstruct:small_blade').length, loaded ? 1 : 0)
    assert.equal(root.function, undefined)
  }
})

test('obsolete pack machine-rating JSON is disconnected without deleting native TCon materials', () => {
  const legacy = path.resolve(__dirname, '../../../kubejs/data/better_manufacturing/engineering/materials')
  for (const id of ['iron', 'wood', 'rock', 'copper', 'pla', 'polypropylene']) {
    assert.equal(fs.existsSync(path.join(legacy, id + '.json')), false, `${id} must not restore a second rating table`)
  }
  for (const id of ['pla', 'polypropylene']) {
    assert.ok(fs.existsSync(path.join(nativeStats, id + '.json')), `${id} native TCon stats must remain`)
    assert.ok(fs.existsSync(path.resolve(__dirname,
      `../../../kubejs/data/better_manufacturing/recipes/polymer_${id === 'pla' ? 'pla' : 'pp'}_stock_material.json`)))
  }
})

test('PLA and PP preserve distinct real TCon native head and handle stats', () => {
  const read = id => JSON.parse(fs.readFileSync(path.join(nativeStats, id + '.json'), 'utf8')).stats
  const pla = read('pla'), pp = read('polypropylene')
  for (const stats of [pla, pp]) {
    assert.ok(Number.isFinite(stats['tconstruct:head'].mining_speed))
    assert.ok(stats['tconstruct:head'].mining_speed > 0)
    assert.ok(Number.isFinite(stats['tconstruct:head'].durability))
    assert.ok(Number.isFinite(stats['tconstruct:handle'].durability))
    assert.ok(Number.isFinite(stats['tconstruct:handle'].mining_speed))
  }
  assert.notEqual(pla['tconstruct:head'].mining_speed, pp['tconstruct:head'].mining_speed)
  assert.notEqual(pla['tconstruct:handle'].durability, pp['tconstruct:handle'].durability)
  // Source assets only: recipe readers must use these TCon stats directly after migration.
})

test('conductive casing keeps the copper/red-wire gate and a no-Manufacturing direct route', () => {
  for (const loaded of [false, true]) {
    const all = recipes('50_electrical_control.js', loaded)
    const stage = get(all, loaded ? 'kubejs:electrical_control/conductive_casing_blank'
      : 'kubejs:electrical_control/conductive_casing')
    assert.equal(stage.type, 'create:deploying')
    assert.deepEqual(Array.from(stage.ingredients, ingredient => ingredient.item),
      ['create:copper_casing', 'morered:red_alloy_wire'])
    assert.equal(stage.results[0].item, loaded
      ? 'better_manufacturing:conductive_casing_blank' : 'powergrid:conductive_casing')
    assert.equal(stage.results.length, 1)
    if (loaded) {
      const finish = get(all, 'kubejs:electrical_control/conductive_casing')
      assert.equal(finish.type, 'create:deploying')
      assert.deepEqual(Array.from(finish.ingredients, ingredient => ingredient.item),
        ['better_manufacturing:conductive_casing_blank', 'tconstruct:large_plate'])
      assert.equal(finish.results[0].item, 'powergrid:conductive_casing')
      assert.equal(finish.results.length, 1)
    } else {
      assert.ok(!all.some(recipe => recipe.results?.some(result =>
        result.item === 'better_manufacturing:conductive_casing_blank')))
    }
    const block = get(all, 'kubejs:electrical_control/electrical_machine_block')
    assert.ok(block.inputs.some(input => input.item === 'powergrid:conductive_casing'))
    assert.ok(ingredients(get(all, 'kubejs:electrical_control/direct_root/first_stationary_generator'))
      .includes('kubejs:electrical_machine_block'))
  }
})

test('later Nether-magma PNCR root masks earlier source candidate pending native evidence', () => {
  for (const manufacturing of [false, true]) {
    const added = []
    const outputOf = recipe => recipe.result?.item || recipe.results?.[0]?.item || recipe.output
    const event = {
      remove(filter) {
        if (filter.output) for (let i = added.length - 1; i >= 0; i--)
          if (outputOf(added[i]) === filter.output) added.splice(i, 1)
        else if (filter.id) for (let i = added.length - 1; i >= 0; i--)
          if (added[i].id === filter.id) added.splice(i, 1)
      },
      replaceInput() {},
      shaped(output, pattern, key) {
        const recipe = { type: 'minecraft:crafting_shaped', output, pattern, key }
        return { id(id) { recipe.id = id; added.push(recipe) } }
      },
      custom(recipe) { return { id(id) { added.push({ ...recipe, id }) } } }
    }
    const context = {
      global: {},
      Platform: { isLoaded(mod) { return mod === 'better_manufacturing' && manufacturing } },
      ServerEvents: { recipes(callback) { callback(event) } }
    }
    vm.createContext(context)
    for (const file of ['utility/10_recipe_surface_helpers.js',
        'progression/81_pneumaticcraft_progression.js', 'progression/85_dimension_font_ecology.js']) {
      vm.runInContext(fs.readFileSync(path.join(scripts, '..', file), 'utf8'), context, { filename: file })
    }
    assert.ok(!added.some(recipe => recipe.id === 'kubejs:tech/pneumatic/thermopneumatic_processing'),
      'earlier authored route must not be counted as effective after later output removal')
    const dimension = get(added, 'kubejs:fonts/nether/thermopneumatic_plant')
    assert.equal(dimension.result?.item || dimension.output, 'pneumaticcraft:thermopneumatic_processing_plant')
    assert.equal(dimension.type, manufacturing ? 'better_manufacturing:part_variant' : 'minecraft:crafting_shaped')
    assert.ok(ingredients(dimension).includes('minecraft:magma_cream'))
  }
})
