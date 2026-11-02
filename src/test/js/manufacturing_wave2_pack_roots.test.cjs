// Isolated Node unit checks for authored shared KubeJS roots and material policy.
// Native recipe-manager acceptance requires separate authorization and is not inferred here.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const scripts = path.resolve(__dirname, '../../../kubejs/server_scripts/progression')
const materials = path.resolve(__dirname, '../../../kubejs/data/better_manufacturing/engineering/materials')
function recipes(file, manufacturing) {
  const added = []
  const event = {
    remove() {}, replaceInput() {},
    shaped(output, pattern, key) {
      const recipe = { type: 'minecraft:crafting_shaped', output, pattern, key }
      return { id(id) { recipe.id = id; added.push(recipe) } }
    },
    custom(recipe) { return { id(id) { added.push({ ...recipe, id }) } } }
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

test('actual pack cheap materials support the working part shapes and traits', () => {
  const read = id => JSON.parse(fs.readFileSync(path.join(materials, id + '.json'), 'utf8'))
  const wood = read('wood'), rock = read('rock'), copper = read('copper'), iron = read('iron')
  assert.equal(wood.material, 'tconstruct:wood')
  assert.equal(rock.material, 'tconstruct:rock')
  assert.equal(copper.material, 'tconstruct:copper')
  assert.equal(iron.material, 'tconstruct:iron')
  for (const trait of ['mixing_rate', 'pump_rate']) {
    assert.ok(wood.supported_parts.includes('tconstruct:tool_handle'))
    assert.ok(wood.traits[trait] > 0 && wood.traits[trait] < iron.traits[trait])
  }
  for (const trait of ['milling_rate', 'press_rate']) {
    assert.ok(rock.supported_parts.includes('tconstruct:large_plate'))
    assert.ok(rock.traits[trait] > 0)
    assert.ok(iron.traits[trait] > 0)
  }
  // DieselSettings.bake requires all three rated traits on every eligible plate;
  // policy presence does not prove a loaded recipe or physical engine response.
  for (const policy of [copper, rock]) {
    assert.ok(policy.supported_parts.includes('tconstruct:large_plate'))
    for (const trait of ['diesel_conversion', 'diesel_load', 'diesel_thermal']) {
      assert.ok(policy.traits[trait] >= 0.75 && policy.traits[trait] <= 1.5)
      assert.ok(iron.traits[trait] > 0)
    }
  }
  assert.equal(copper.traits.diesel_conversion, 0.9)
  assert.equal(rock.traits.diesel_conversion, 0.75)
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
