// Executes the actual production handlers with a bounded recipe-event model.
// This is not a native Forge/Create/Sifter runtime or an EMI acceptance test.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert/strict');
const root = path.resolve(process.argv[2] || '.');
const catalogue = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/terrain_recovery.json')));
const source = fs.readFileSync(path.join(root, 'kubejs/server_scripts/processing/terrain_recovery.js'), 'utf8');
const washingSource = fs.readFileSync(path.join(root, 'kubejs/server_scripts/processing/ore_sifting_and_spouting.js'), 'utf8');
const clone = x => JSON.parse(JSON.stringify(x));
const items = new Set(['createsifter:brass_mesh']);
for (const e of catalogue.entries) items.add(e.input);
for (const p of Object.values(catalogue.profiles)) for (const o of [...p.physical, ...p.chemical]) items.add(o.item);
const physical = new Set(['minecraft:sand', 'minecraft:red_sand', 'natures_spirit:pink_sand', 'minecraft:gravel', 'minecraft:dirt', 'minecraft:clay_ball', 'minecraft:calcite', 'minecraft:quartz', 'create:powdered_obsidian']);
const entries = new Map(catalogue.entries.map(e => [e.input, e]));
function matches(r, f) {
  return (!f.type || r.json.type === f.type) && (!f.id || r.id === f.id) &&
    (!f.mod || r.id.split(':')[0] === f.mod) &&
    (!f.output || [...(r.json.results || []), r.json.result || {}].some(o => o.item === f.output));
}
function fixture(data = clone(catalogue), missing = null, mutate = null, washing = true) {
  const recipes = new Map();
  function seed(id, json) { recipes.set(id, { id, json: clone(json) }); }
  for (const r of catalogue.remove_recipes) {
    if (r.id.startsWith('kubejs:sifting/migrated_')) {
      const id = r.source.split('#')[1];
      seed(id, { type: 'create:splashing', ingredients: [{ item: r.input }], results: [{ item: 'minecraft:clay_ball', chance: 0.25 }] });
    } else seed(r.id, { type: 'create:crushing', ingredients: [{ item: r.input }], results: [{ item: 'minecraft:gravel' }] });
  }
  for (const r of catalogue.recycling_exclusions) seed(r.id, { type: 'create:crushing', ingredients: [{ tag: r.tag }], results: [{ item: 'minecraft:quartz', chance: 0.25 }], processingTime: 350 });
  seed('test:unrelated', { type: 'create:crushing', ingredients: [{ item: 'test:unrelated' }], results: [{ item: 'test:result' }] });
  seed('create:milling/cobblestone', { type: 'create:milling', ingredients: [{ tag: 'forge:cobblestone' }], results: [{ item: 'minecraft:gravel' }] });
  seed('test:unrelated_sifting', { type: 'createsifter:sifting', ingredients: [{ item: 'test:unrelated' }], results: [{ item: 'test:result' }] });
  const pristine = clone([...recipes]);
  let handler;
  const context = {
    JsonIO: { read: file => { assert.equal(file, 'kubejs/config/terrain_recovery.json'); return data; } },
    Item: { exists: id => items.has(id) && id !== missing },
    Ingredient: { of: () => ({ itemIds: [] }) },
    ServerEvents: { recipes: fn => { handler = fn; } },
    console: { info() {}, warn() {} }
  };
  let mutations = 0;
  const event = {
    remove(filter) { mutations++; for (const [id, r] of recipes) if (matches(r, filter)) recipes.delete(id); },
    forEachRecipe(filter, fn) {
      for (const r of [...recipes.values()]) if (matches(r, filter)) {
        // Production calls Java Gson's getAsJsonArray for washing migration.
        const json = { toString: () => JSON.stringify(r.json), getAsJsonArray(key) {
          const a = r.json[key];
          return { toString: () => JSON.stringify(a), get(i) { return { getAsJsonObject() { const value = a[i]; return { has: k => k in value, get: k => ({ getAsString: () => value[k] }) }; } }; } };
        } };
        fn({ json, getId: () => r.id });
      }
    },
    custom(json) { return { id(id) { mutations++; assert(!recipes.has(id), 'competing recipe ID ' + id); seed(id, json); } }; }
  };
  vm.createContext(context);
  if (washing) { vm.runInContext(washingSource, context); handler(event); mutations = 0; }
  vm.runInContext(source, context);
  if (mutate) mutate(recipes);
  return { run: () => handler(event), recipes, pristine, mutations: () => mutations };
}
const f = fixture();
f.run();
const byRoute = { crushing: 0, sifting: 0 };
for (const e of catalogue.entries) {
  const p = catalogue.profiles[e.profile];
  const id = 'kubejs:terrain_recovery/' + p.route + '/' + e.input.replace(':', '/');
  const r = f.recipes.get(id).json;
  assert.equal(r.processingTime, 500);
  assert.equal(r.ingredients[0].item, e.input);
  assert.equal(r.type, p.route === 'crushing' ? 'create:crushing' : 'createsifter:sifting');
  if (p.route === 'sifting') {
    assert.equal(r.waterlogged, true);
    assert.deepEqual(r.ingredients[1], { item: 'createsifter:brass_mesh' });
  } else { assert.equal(r.ingredients.length, 1); assert(!('waterlogged' in r)); }
  for (const o of r.results) assert(o.chance > 0 && o.chance < 1, 'no guaranteed or zero outputs');
  assert.deepEqual(r.results, [...p.physical, ...p.chemical].map(o => ({ item: o.item, count: o.count, chance: o.chance })));
  assert.equal([...f.recipes.values()].filter(v => v.json.type === r.type && v.json.ingredients?.[0]?.item === e.input).length, 1, 'competing exact input ' + e.input);
  byRoute[p.route]++;
}
for (const p of Object.values(catalogue.profiles)) {
  assert(p.physical.every(o => physical.has(o.item)), 'unexpected metallic/precious/new physical output');
  const budget = p.physical.reduce((sum, o) => sum + o.count * o.chance * catalogue.physical_units[o.item], 0);
  assert(budget <= 0.25 + 1e-12);
  assert(p.chemical.length <= 2);
  for (const o of p.chemical) assert(o.count === 1 && o.chance <= 1/64 && catalogue.chemistry_audit[o.item].status === 'eligible');
}
for (const r of catalogue.recycling_exclusions) {
  const json = f.recipes.get(r.id).json;
  assert.deepEqual(json.ingredients[0], { type: 'forge:difference', base: { tag: r.tag }, subtracted: { item: r.exclude[0] } });
  assert.deepEqual(json.results, [{ item: 'minecraft:quartz', chance: 0.25 }]);
  assert.equal(json.processingTime, 350);
}
assert(f.recipes.has('test:unrelated'));
assert(f.recipes.has('test:unrelated_sifting'));
assert(f.recipes.has('create:milling/cobblestone'));
assert(![...f.recipes.values()].some(r => r.json.type === 'create:splashing'));
const profile = i => entries.get(i).profile;
assert.notEqual(profile('natures_spirit:sandy_soil'), profile('immersive_weathering:earthen_clay'));
assert.notEqual(profile('unearthed:limestone'), profile('unearthed:quartzite'));
assert.notEqual(profile('unearthed:white_granite'), profile('unearthed:gabbro'));
assert(catalogue.entries.filter(e => e.equivalence_group === 'unearthed_regolith').length === 35);
assert.equal(new Set(catalogue.entries.filter(e => e.equivalence_group === 'unearthed_regolith').map(e => e.profile)).size, 1);
for (const ns of ['unearthed', 'natures_spirit', 'immersive_weathering', 'quark', 'create', 'alexscaves']) assert(catalogue.entries.some(e => e.input.startsWith(ns + ':')));
for (const i of ['unearthed:dolerite', 'unearthed:pumice', 'unearthed:schist', 'unearthed:marble']) assert(entries.has(i));
for (const i of ['minecraft:diamond', 'create:asurine', 'minecraft:end_stone', 'minecraft:netherrack', 'minecraft:stone_bricks', 'minecraft:stone_slab']) assert(!entries.has(i));
let negatives = 0;
function reject(change, missing) {
  const data = clone(catalogue); change(data);
  const broken = fixture(data, missing, null, false);
  assert.throws(() => broken.run(), /terrain-recovery/);
  assert.equal(broken.mutations(), 0, 'invalid catalogue mutated recipes');
  negatives++;
}
reject(d => d.schema = 'future');
reject(d => d.unknown = true);
reject(d => d.entries.push(d.entries[0]));
reject(d => d.entries[0].profile = 'unknown');
reject(d => d.profiles.mafic.physical[0].chance = 0.9);
reject(d => d.profiles.felsic.physical[2].chance = 0.125);
reject(d => d.profiles.rooted_soil.chemical[0].chance = 0.1);
reject(d => d.profiles.rooted_soil.chemical[0].consumer = 'missing');
reject(d => d.entries[0].counterpart_of = 'missing');
reject(() => {}, catalogue.entries[0].input);
reject(() => {}, 'createsifter:brass_mesh');
for (const drift of ['missing', 'changed']) {
  const bad = fixture(clone(catalogue), null, recipes => {
    const id = catalogue.recycling_exclusions[0].id;
    if (drift === 'missing') recipes.delete(id);
    else recipes.get(id).json.ingredients = [{ item: 'minecraft:diorite' }];
  });
  assert.throws(() => bad.run(), /terrain-recovery/);
  negatives++;
}
console.log(JSON.stringify({ inputs: catalogue.entries.length, routes: byRoute, profiles: Object.keys(catalogue.profiles).length, negative_controls: negatives, existing_washing_handler_executed: true }));
