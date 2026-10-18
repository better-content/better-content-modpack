'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync(process.argv[2], 'utf8');
function run(loaded) {
  const recipes = [], removals = [];
  vm.runInNewContext(script, {
    Platform: {isLoaded: id => loaded.includes(id)},
    ServerEvents: {recipes: handler => handler({
      remove: selector => removals.push(selector),
      custom: recipe => ({id: id => recipes.push({id, ...recipe})})
    })}
  });
  return {recipes, removals};
}
for (const loaded of [[], ['hexerei'], ['better_industrial_heat']]) {
  const result = run(loaded);
  assert.equal(result.recipes.length, 0);
  assert.equal(result.removals.length, 0);
}
const result = run(['hexerei', 'better_industrial_heat']);
assert.ok(result.recipes.length > 0);
assert.equal(new Set(result.recipes.map(recipe => recipe.id)).size, result.recipes.length);
for (const recipe of result.recipes) {
  assert.equal(recipe.type, 'hexerei:drying_rack');
  assert.equal(recipe.ingredients.length, 1);
  assert.equal(recipe.dryingTimeInTicks, 2000);
  assert.ok(result.removals.some(selector => selector.output === recipe.output.item));
}
assert.equal(result.removals.length, result.recipes.length);
console.log(JSON.stringify(result.recipes));
