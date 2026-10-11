'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const actions = [];
const event = {
  remove(selector) { actions.push({type: 'remove', selector}); },
  shaped(output, pattern, key) {
    const row = {type: 'shaped', output, pattern, key};
    actions.push(row);
    return {id(id) { row.id = id; }};
  },
  shapeless(output, ingredients) {
    const row = {type: 'shapeless', output, ingredients};
    actions.push(row);
    return {id(id) { row.id = id; }};
  }
};
vm.runInNewContext(fs.readFileSync(process.argv[2], 'utf8'), {
  ServerEvents: {recipes(handler) { handler(event); }}
}, {timeout: 2000, filename: process.argv[2]});
process.stdout.write(JSON.stringify(actions));
