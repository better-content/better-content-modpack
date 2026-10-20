// Inert execution of the actual production KubeJS handlers, not Minecraft proof.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = process.argv[2];
const seed = JSON.parse(fs.readFileSync(0, 'utf8'));
const data = JSON.parse(fs.readFileSync(root + '/kubejs/config/fish_nutrition.json'));
const tags = structuredClone(seed);
const items = {};
function add(tag, values) {
  tags[tag] ||= [];
  for (const value of Array.isArray(values) ? values : [values]) tags[tag].push(String(value));
}
function resolve(tag, stack = []) {
  assert(!stack.includes(tag), 'tag cycle: ' + tag);
  return [...new Set((tags[tag] || []).flatMap(v => v.startsWith('#') ? resolve(v.slice(1), [...stack, tag]) : [v]))];
}
const serverSource = fs.readFileSync(root + '/kubejs/server_scripts/progression/22_fish_families.js', 'utf8');
const startupSource = fs.readFileSync(root + '/kubejs/startup_scripts/progression/22_fish_families.js', 'utf8');
const JsonIO = { read(path) { assert.equal(path, 'kubejs/config/fish_nutrition.json'); return structuredClone(data); } };
vm.runInNewContext(serverSource, { JsonIO, ServerEvents: { tags(type, handler) {
  assert.equal(type, 'item');
  handler({ add, get(tag) { return { getObjectIds() { return resolve(tag); } }; } });
} } });
vm.runInNewContext(startupSource, { JsonIO, StartupEvents: { registry(type, handler) {
  assert.equal(type, 'item');
  handler({ create(id) {
    assert(!items[id]);
    const item = items[id] = {};
    const builder = {
      displayName(value) { item.name = value; return builder; },
      texture(value) { item.texture = value; return builder; },
      food(callback) {
        const food = { hunger(value) { item.hunger = value; return food; }, saturation(value) { item.saturation = value; return food; } };
        callback(food); return builder;
      }
    };
    return builder;
  } });
} } });
for (const source of [serverSource, startupSource]) {
  assert.throws(() => vm.runInNewContext(source, { JsonIO: { read: () => null } }), /fish nutrition contract/);
  assert.throws(() => vm.runInNewContext(source, { JsonIO: { read: () => ({ schema: 'wrong' }) } }), /fish nutrition contract/);
}
assert.equal(Object.keys(items).length, 4);
for (const [name, family] of Object.entries(data.families)) {
  assert.deepEqual(resolve('kubejs:fish/' + name + '_raw').sort(), [...family.raw].sort());
  if (name !== 'plain') {
    assert.equal(items[family.cooked].hunger, 6);
    assert.equal(items[family.cooked].saturation, 2);
    assert.equal(items[family.cooked].name, family.display_name);
  }
}
assert.deepEqual(Object.values(data.families).map(f => f.cooked).sort(), [
  'starcatcher:cooked_starcaught_fish', 'kubejs:rich_cooked_fish',
  'kubejs:root_cooked_fish', 'kubejs:grain_cooked_fish', 'kubejs:uncanny_cooked_fish'
].sort());
for (const [item, groups] of Object.entries(data.generic_output_groups)) {
  for (const group of groups) assert(resolve('diet:' + group).includes(item));
}
process.stdout.write(JSON.stringify({ tags, items }));
