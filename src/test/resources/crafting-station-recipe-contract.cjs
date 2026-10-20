const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

// Exercise the authored callback, not native crafting or a runtime recipe manager.
const removed = [];
const added = [];
let callbacks = 0;
vm.runInNewContext(fs.readFileSync(process.argv[2], 'utf8'), {
    ServerEvents: { recipes(callback) {
        callbacks++;
        callback({
            remove(filter) { removed.push(JSON.parse(JSON.stringify(filter))); },
            shapeless(output, ingredients) {
                const recipe = JSON.parse(JSON.stringify({ type: 'minecraft:crafting_shapeless', output, ingredients }));
                added.push(recipe);
                return { id(id) { recipe.id = id; } };
            }
            // No shaped/custom/replacement methods: unrelated mutations must fail.
        });
    } }
});
assert.equal(callbacks, 1);
assert.deepEqual(removed, [
    { id: 'tconstruct:tables/crafting_station' },
    { id: 'tconstruct:tables/crafting_station_from_tables' },
    { id: 'tconstruct:tables/crafting_station_from_logs' }
]);
// Bare item outputs mean exactly one item in standard KubeJS recipes.
assert.deepEqual(added, [
    { type: 'minecraft:crafting_shapeless', output: 'tconstruct:crafting_station',
      ingredients: ['minecraft:crafting_table'], id: 'kubejs:tech/stations/crafting_station_from_crafting_table' },
    { type: 'minecraft:crafting_shapeless', output: 'minecraft:crafting_table',
      ingredients: ['tconstruct:crafting_station'], id: 'kubejs:tech/stations/crafting_table_from_crafting_station' }
]);
assert.equal(new Set(added.map(recipe => recipe.id)).size, 2);
console.log(JSON.stringify({ removed: removed.map(filter => filter.id), added }));
