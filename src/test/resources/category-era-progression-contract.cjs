'use strict';
// Bounded, Minecraft-free execution of the real handlers, following the existing
// Pretty Pipes fixture pattern. This is a recipe contract, not a graph auditor.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = process.argv[2];
const actions = [], tags = new Map();
let owner;
function capture(row) {
  row.owner = owner;
  actions.push(row);
  return {id(id) { row.id = id; return this; }};
}
const event = {
  remove(selector) { capture({type: 'remove', selector}); },
  replaceInput(selector, before, after) { capture({type: 'replace', selector, before, after}); },
  shaped(output, pattern, key) { return capture({type: 'minecraft:crafting_shaped', output, pattern, key}); },
  shapeless(output, ingredients) { return capture({type: 'minecraft:crafting_shapeless', output, ingredients}); },
  custom(recipe) { return capture({...recipe}); },
  smelting(output, input) { return capture({type: 'minecraft:smelting', output, ingredients: [input]}); },
  blasting(output, input) { return capture({type: 'minecraft:blasting', output, ingredients: [input]}); }
};
const context = vm.createContext({
  global: {}, Platform: {isLoaded() { return true; }},
  ServerEvents: {
    recipes(handler) { handler(event); },
    tags(kind, handler) {
      assert.equal(kind, 'item');
      handler({add(tag, items) { assert(!tags.has(tag)); tags.set(tag, Array.from(items)); }});
    }
  }
});
const scripts = [
  'utility/10_recipe_surface_helpers.js',
  'progression/10_hand_workshop.js', 'progression/20_powered_works.js',
  'progression/20_logistics_roots.js', 'progression/30_precision_factory.js',
  'progression/40_thermal_pressure.js', 'progression/50_electrical_control.js',
  'progression/60_aerospace.js', 'progression/81_pneumaticcraft_progression.js',
  'progression/85_dimension_font_ecology.js', 'transport/10_optional_engineering_roots.js'
];
for (owner of scripts) {
  vm.runInContext(fs.readFileSync(path.join(root, 'kubejs/server_scripts', owner), 'utf8'),
    context, {filename: owner, timeout: 2000});
}
function stack(value) {
  if (typeof value === 'string') {
    const match = value.match(/^(\d+)x (.+)$/);
    return {item: match ? match[2] : value, count: match ? Number(match[1]) : 1};
  }
  return value && value.item ? {item: value.item, count: value.count || 1} : null;
}
function outputs(row) {
  return (row.output ? [stack(row.output)] : row.results || [row.result]).map(stack).filter(Boolean);
}
function ingredient(value) {
  if (typeof value === 'string') return value;
  return value.item || (value.tag ? '#' + value.tag : 'fluid:' + value.fluid);
}
function counts(row) {
  const result = new Map();
  const add = (input, count = 1) => result.set(ingredient(input), (result.get(ingredient(input)) || 0) + count);
  if (row.pattern) {
    for (const symbol of Array.from(row.pattern.join('')).filter(s => s !== ' ')) add(row.key[symbol]);
  } else {
    for (const input of row.ingredients || row.inputs || []) add(input, input.count || 1);
  }
  return result;
}
// Apply explicit removals in handler order, including the later Font/pressure owner.
const effective = [];
for (const row of actions) {
  if (row.type === 'remove') {
    for (let i = effective.length - 1; i >= 0; --i) {
      const selector = row.selector, candidate = effective[i];
      const matches = (!selector.output || outputs(candidate).some(o => o.item === selector.output)) &&
        (!selector.input || counts(candidate).has(selector.input)) &&
        (!selector.id || candidate.id === selector.id) && (!selector.type || candidate.type === selector.type);
      if (matches) effective.splice(i, 1);
    }
  } else if (row.type !== 'replace') effective.push(row);
}
function recipe(item) {
  const rows = effective.filter(row => outputs(row).some(o => o.item === item) &&
    !['kubejs:electrical_control/generator_vertical_conversion',
      'kubejs:electrical_control/generator_horizontal_conversion'].includes(row.id));
  assert.equal(rows.length, 1, `one authored entry for ${item}`);
  return rows[0];
}
function removed(selector) {
  return actions.some(row => row.type === 'remove' && Object.entries(selector).every(([k, v]) => row.selector[k] === v));
}
const expectedFamilies = {
  gabbro: ['unearthed:gabbro'], slate: ['unearthed:slate', 'unearthed:cobbled_slate'],
  phyllite: ['unearthed:phyllite', 'unearthed:cobbled_phyllite'],
  quartzite: ['unearthed:quartzite'], schist: ['unearthed:schist']
};
assert.equal(tags.size, 5);
for (const [family, items] of Object.entries(expectedFamilies))
  assert.deepEqual(tags.get('kubejs:refractory_stone/' + family), items);
const pairs = effective.filter(row => outputs(row).some(o => o.item === 'kubejs:refractory_compound'));
assert.equal(pairs.length, 10);
const accepts = (row, a, b) => {
  const acceptsSlot = (tag, item) => tags.get(tag.substring(1)).includes(item);
  return (acceptsSlot(row.ingredients[0], a) && acceptsSlot(row.ingredients[1], b)) ||
    (acceptsSlot(row.ingredients[0], b) && acceptsSlot(row.ingredients[1], a));
};
for (const [left, leftItems] of Object.entries(expectedFamilies)) {
  for (const [right, rightItems] of Object.entries(expectedFamilies)) {
    for (const a of leftItems) for (const b of rightItems) {
      assert.equal(pairs.filter(row => accepts(row, a, b)).length, left === right ? 0 : 1, `${a} + ${b}`);
      assert.equal(pairs.filter(row => accepts(row, b, a)).length, left === right ? 0 : 1, `reversed ${a} + ${b}`);
    }
  }
}
for (const row of pairs) {
  assert.equal(row.type, 'minecraft:crafting_shapeless');
  assert.equal(outputs(row)[0].count, 2);
  assert.notEqual(row.ingredients[0], row.ingredients[1]);
}
for (const excluded of ['minecraft:stone', 'unearthed:regolith', 'unearthed:polished_slate',
  'unearthed:mossy_cobbled_slate', 'unearthed:slate_bricks', 'better_ore_geology:ironstone',
  'unearthed:cobbled_gabbro', 'unearthed:cobbled_quartzite']) {
  assert(!Array.from(tags.values()).flat().includes(excluded), excluded);
}
// Schist orientations are block states of this single registered/loot item,
// never extra ingredient families or invented axis item IDs.
assert.equal(Array.from(tags.values()).flat().filter(id => id.includes('schist')).length, 1);
for (const type of ['minecraft:smelting', 'minecraft:blasting']) {
  const firing = effective.filter(row => row.type === type && counts(row).has('kubejs:refractory_compound'));
  assert.equal(firing.length, 1);
  assert.deepEqual(outputs(firing[0]), [{item: 'tconstruct:seared_brick', count: 1}]);
}
for (const item of ['tconstruct:grout', ...['nether', 'aether', 'bumblezone'].map(o => `kubejs:${o}_font_grout`)]) {
  assert(removed({output: item}) && removed({input: item}), item);
  assert(!effective.some(row => outputs(row).some(o => o.item === item) || counts(row).has(item)), item);
}
for (const id of ['slate_from_smooth_stone', 'quartzite_from_smooth_sandstone',
  'quartzite_from_smooth_red_sandstone', 'phyllite_from_slate', 'schist_from_phyllite', 'gabbro'])
  assert(removed({id: 'unearthed:' + id}), id);
assert(!actions.some(row => row.type === 'remove' && /cobbled/.test(row.selector.id || '')),
  'same-family restoration stays native');

// A fixed workshop bootstrap, not an arbitrary reachability framework. Native
// pressing/fluid-pipe recipes are resource-checked separately against the pin.
const available = new Set([
  '#forge:ingots/iron', '#forge:nuggets/iron', '#forge:ingots/copper', '#forge:stripped_logs',
  '#forge:stone', '#minecraft:planks', '#minecraft:wooden_slabs', 'minecraft:andesite',
  'minecraft:iron_block', 'minecraft:dried_kelp', 'minecraft:stick'
]);
const make = item => {
  const row = recipe(item);
  for (const input of counts(row).keys()) assert(available.has(input), `bootstrap ${item} lacks ${input}`);
  available.add(item);
};
for (const item of ['create:shaft', 'create:cogwheel', 'create:large_cogwheel',
  'create:andesite_casing', 'create:mechanical_bearing', 'create:depot', 'create:basin',
  'create:hand_crank', 'create:mechanical_press', 'kubejs:andesite_machine_block']) make(item);
assert(available.has('create:depot') && available.has('create:hand_crank') && available.has('create:mechanical_press'));
available.add('#forge:plates/iron'); available.add('#forge:plates/copper');
make('create:whisk'); make('create:mechanical_mixer'); make('create:copper_casing');
make('create:propeller');
available.add('#minecraft:wool'); available.add('#forge:rods/wooden');
make('create:white_sail');
assert.equal(recipe('create:copper_casing').type, 'create:compacting');
make('kubejs:copper_machine_block');
assert.equal(recipe('kubejs:copper_machine_block').type, 'create:compacting');
assert(!Array.from(counts(recipe('create:mechanical_press')).keys()).some(i => i.includes('plates/')));
assert(!Array.from(counts(recipe('kubejs:andesite_machine_block')).keys()).some(i => /plates|alloy|seared/.test(i)));
// Physical local handling remains earlier than meteor routing.
available.add('create:fluid_pipe');
for (const item of ['create:andesite_funnel', 'create:andesite_tunnel', 'create:mechanical_pump']) make(item);
assert(removed({type: 'create:mixing', output: 'create:andesite_alloy'}));
assert(!effective.some(row => outputs(row).some(o => o.item === 'create:andesite_alloy') && row.type.startsWith('minecraft:crafting')));

available.add('aether:skyroot_log');
for (const item of ['create:water_wheel', 'create:large_water_wheel', 'create:windmill_bearing']) {
  const row = recipe(item);
  assert(counts(row).has('aether:skyroot_log'), item);
  make(item); // Complete fitting closure above contains no Nether or TCon input.
  assert(removed({output: item}), `remove native alternatives to ${item}`);
}
for (const item of ['create:millstone', 'createsifter:sifter', 'createsifter:brass_sifter', 'create:crushing_wheel']) {
  assert(counts(recipe(item)).has('minecraft:nether_brick'), item);
  assert(removed({output: item}), `remove native alternatives to ${item}`);
}
// Native String Mesh hand recovery is owned by Better Ore Geology, untouched.
const sifting = fs.readFileSync(path.join(root, 'kubejs/server_scripts/processing/ore_sifting_and_spouting.js'), 'utf8');
assert(sifting.includes('createsifter:string_mesh'));

for (const item of ['create:redstone_requester', 'sophisticatedstorage:hopper_upgrade',
  'rats:rat_upgrade_basic', 'littlelogistics:rapid_hopper']) {
  const inputs = counts(recipe(item));
  assert(inputs.has('ae2:certus_quartz_crystal'), item);
  assert(!Array.from(inputs.keys()).some(i => /processor|energy_acceptor|sky_stone|powergrid:/.test(i)), item);
}
for (const row of effective.filter(row => outputs(row).some(o => o.item.startsWith('prettypipes:')))) {
  const output = outputs(row)[0].item;
  const stone = counts(row).get('ae2:sky_stone_block') || 0;
  assert.equal(stone, ['prettypipes:item_terminal', 'prettypipes:pressurizer'].includes(output) ? 1 : 0, output);
}
assert(!effective.some(row => outputs(row).some(o => o.item === 'prettypipes:crafting_terminal')));
for (const required of ['powergrid:generator_housing', 'aether:enchanted_gravitite',
  'oc2r:computer', 'ae2:sky_stone_block', 'kubejs:impossible_support_matrix'])
  assert(counts(recipe('ae2:energy_acceptor')).has(required), required);

// Exact structural apparatus allowlist, distinct from the six-block manufacturing chain.
const assemblies = new Set(['andesite', 'copper', 'brass', 'airtight', 'electrical', 'space'].map(n => `kubejs:${n}_machine_block`));
const consumers = [];
for (const row of effective) {
  const used = Array.from(counts(row)).filter(([item]) => assemblies.has(item));
  if (!used.length) continue;
  const out = outputs(row);
  assert.equal(out.length, 1);
  if (assemblies.has(out[0].item)) continue; // Assembly manufacture may batch.
  consumers.push(out[0].item);
  assert.equal(used.reduce((n, [, count]) => n + count, 0), out[0].count, out[0].item);
}
assert.deepEqual(consumers.sort(), ['creatingspace:air_liquefier']);
for (const item of ['create:mechanical_press', 'create:millstone', 'create:mechanical_pump',
  'create:deployer', 'create:mechanical_crafter', 'create:steam_engine', 'create:track_station',
  'trackwork:simple_wheel', 'pneumaticcraft:pressure_chamber_interface',
  'compressedcreativity:rotational_compressor', 'pneumaticcraft:assembly_controller',
  'pneumaticcraft:thermopneumatic_processing_plant', 'powergrid:generator_housing',
  'morered:soldering_table', 'creatingspace:rocket_engineer_table', 'creatingspace:mechanical_electrolyzer']) {
  assert(!Array.from(counts(recipe(item)).keys()).some(input => assemblies.has(input)), item);
}
assert.equal(outputs(recipe('pneumaticcraft:pressure_chamber_interface'))[0].count, 1);
for (const item of ['creatingspace:rocket_engineer_table', 'creatingspace:mechanical_electrolyzer']) {
  assert.equal(recipe(item).type, 'pneumaticcraft:pressure_chamber');
  assert.equal(recipe(item).pressure, 4.5);
  assert(counts(recipe(item)).has('powergrid:integrated_circuit'));
}
for (const item of ['pneumaticcraft:pressure_chamber_interface', 'compressedcreativity:rotational_compressor',
  'pneumaticcraft:thermopneumatic_processing_plant']) assert.equal(recipe(item).type, 'create:mechanical_crafting');
const beetle = effective.filter(row => outputs(row).some(o => o.item.startsWith('better_rail_beetle:') && o.item.endsWith('_2')));
assert.equal(beetle.length, 9);
for (const row of beetle) {
  const output = outputs(row)[0].item, input = counts(row);
  assert.equal(input.get(output.replace(/_2$/, '_1')), 1, output);
  assert(!Array.from(input.keys()).some(i => assemblies.has(i)), output);
  if (['high_speed_governor', 'brake_manifold', 'adhesion_sanders', 'compound_torque_clutch', 'reinforced_drawgear', 'trestle_erector'].some(n => output.endsWith(n + '_2'))) {
    assert.equal(row.type, 'create:mechanical_crafting');
    assert.equal(input.get('kubejs:pressure_seal'), 2);
  } else assert.equal(input.get('powergrid:integrated_circuit') >= 2, true);
}
for (const [output, input] of [['powergrid:generator_housing', 'powergrid:vertical_generator_housing'],
  ['powergrid:vertical_generator_housing', 'powergrid:generator_housing']]) {
  const conversion = effective.filter(row => row.id.endsWith('_conversion') && outputs(row).some(o => o.item === output));
  assert.equal(conversion.length, 1);
  assert.deepEqual(Array.from(counts(conversion[0])), [[input, 1]], 'orientation cannot bootstrap a generator');
}
// Snapshot the complete declared Machine Block use: no tag/dynamic consumer
// outside these reviewed owners can silently escape this bounded fixture.
const reviewed = new Set(scripts);
function scan(directory, prefix = '') {
  for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
    const relative = prefix + entry.name, file = path.join(directory, entry.name);
    if (entry.isDirectory()) scan(file, relative + '/');
    else if (entry.name.endsWith('.js') && fs.readFileSync(file, 'utf8').includes('_machine_block'))
      assert(reviewed.has(relative), `Unreviewed Machine Block owner ${relative}`);
  }
}
scan(path.join(root, 'kubejs/server_scripts'));
assert.equal(recipe('powergrid:generator_housing').pressure, 2.0);
assert.equal(recipe('pneumaticcraft:assembly_controller').pressure, 5.0);
process.stdout.write(JSON.stringify({status: 'category-era-contract-passed', recipes: effective.length,
  refractoryPairs: pairs.length, structuralConsumers: consumers}));
