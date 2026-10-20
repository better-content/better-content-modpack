#!/usr/bin/env node
// Source-level recipe-boundary regression only; this is not effective full-pack recipe acceptance.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const fixtures = [
  ['fruitsdelight:mixing/flowing_orange_juice', 'create:mixing', null, { results: [{ fluid: 'fruitsdelight:orange_juice', amount: 250 }] }, true],
  ['fruitsdelight:mixing/flowing_lemon_juice', 'create:mixing', null, { results: [{ fluid: 'fruitsdelight:lemon_juice', amount: 250 }] }, true],
  ['fruitsdelight:mixing/flowing_pear_juice', 'create:compacting', null, { results: [{ fluid: 'fruitsdelight:pear_juice', amount: 250 }] }, true],
  ['farmersrespite:brewing/apple_cider', 'farmersrespite:brewing', null, { result: { fluid: 'farmersrespite:apple_cider', amount: 1000 } }, true],
  ['brewinandchewin:fermenting/beer', 'brewinandchewin:fermenting', null, { result: { fluid: 'brewinandchewin:beer', amount: 1000 } }, true],
  ['farmersdelight:melon_juice', 'minecraft:crafting_shapeless', 'farmersdelight:melon_juice', {}, true],
  ['ubesdelight:milk_tea_ube', 'farmersdelight:cooking', 'ubesdelight:milk_tea_ube', {}, false],
  ['brewinandchewin:fermenting/vodka', 'brewinandchewin:fermenting', null, { result: { fluid: 'brewinandchewin:vodka', amount: 1000 } }, false],
  ['brewinandchewin:fermenting/kombucha', 'brewinandchewin:fermenting', null, { result: { fluid: 'brewinandchewin:kombucha', amount: 1000 } }, false],
  ['farmersrespite:brewing/long_apple_cider', 'farmersrespite:brewing', 'farmersrespite:long_apple_cider', {}, false],
  ['better_drinking_water:plant/apple_cider_brew', 'brewinandchewin:fermenting', 'farmersdelight:apple_cider', { result: { fluid: 'farmersdelight:apple_cider', amount: 1000 } }, false],
  ['better_drinking_water:plant/beer_brew', 'brewinandchewin:fermenting', null, { result: { fluid: 'brewinandchewin:beer', amount: 1000 } }, false],
  ['other:unrelated_multi_result', 'create:mixing', null, { results: [{ item: 'minecraft:sugar' }, { fluid: 'minecraft:water', amount: 250 }] }, false],
];
const removed = new Set();
const event = {
  forEachRecipe(filter, callback) {
    for (const [id, type, output, json] of fixtures) {
      if (filter.output !== undefined && filter.output !== output) continue;
      if (filter.type !== undefined && filter.type !== type) continue;
      callback({ getId: () => id, json: JSON.stringify(json) });
    }
  },
  remove({ id }) { removed.add(id); },
};
runInNewContext(readFileSync(new URL('../kubejs/server_scripts/progression/plant_drinks.js', import.meta.url), 'utf8'), {
  ServerEvents: { recipes: callback => callback(event) },
});
for (const [id, , , , expected] of fixtures) assert.equal(removed.has(id), expected, `Plant recipe boundary: ${id}`);
console.log(`Plant drink recipe source boundaries: ${fixtures.length} passed (not full-pack runtime acceptance)`);
