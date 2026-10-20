'use strict';

// Executes the authored compiler and handler. Tag matching below is a bounded
// membership model, not proof of native KubeJS resolution or rendered tooltips.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = process.argv[2];
assert.ok(root, 'repository root required');
const registrations = [];
let tooltipHandlers = 0;
const context = {
    JsonIO: {
        read(file) {
            if (file === 'kubejs/config/hover_annotations.json') {
                return JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
            }
            // Unrelated generated formal/combat catalogues are not fixture inputs.
            if (file === 'kubejs/config/formal_magic_domains.json') {
                return { schema: 'bc.formal_magic_domains.v3' };
            }
            if (file === 'kubejs/config/tcon_edps_catalogue.json') {
                return { schema: 'bc.tcon_edps_catalogue.v1' };
            }
            throw new Error('unexpected fixture input: ' + file);
        }
    },
    Platform: { isLoaded: () => true },
    Text: {
        gray: text => ({ text, color: 'gray' }),
        red: text => ({ text, color: 'red' })
    },
    console: { info() {}, warn(message) { throw new Error(message); } },
    ItemEvents: {
        tooltip(handler) {
            tooltipHandlers++;
            handler({ add(target, line) { registrations.push({ target, ...line }); } });
        }
    }
};
const script = path.join(root, 'kubejs/client_scripts/guidance/10_hover_annotations.js');
vm.runInNewContext(fs.readFileSync(script, 'utf8'), context, { filename: script, timeout: 1000 });
assert.equal(tooltipHandlers, 1, 'one shared ordinary tooltip handler');
const labels = registrations.filter(row => ['Counts as stone', 'Counts as cobblestone'].includes(row.text));
assert.deepEqual(labels, [
    { target: '#forge:stone', text: 'Counts as stone', color: 'gray' },
    { target: '#forge:cobblestone', text: 'Counts as cobblestone', color: 'gray' }
]);
const cases = [
    { item: 'fixture:unusually_named_material', tags: ['forge:stone'], expected: ['Counts as stone'] },
    { item: 'fixture:mossy_material', tags: ['forge:cobblestone'], expected: ['Counts as cobblestone'] },
    { item: 'fixture:dual_member', tags: ['forge:stone', 'forge:cobblestone'], expected: ['Counts as stone', 'Counts as cobblestone'] },
    { item: 'fixture:decorative_stone_cobblestone', tags: [], expected: [] }
];
for (const sample of cases) {
    const actual = labels.filter(row => row.target.startsWith('#')
        ? sample.tags.includes(row.target.slice(1)) : row.target === sample.item).map(row => row.text);
    assert.deepEqual(actual, sample.expected, sample.item);
    assert.equal(new Set(actual).size, actual.length, 'no duplicate category labels');
}
process.stdout.write(JSON.stringify({ category_registrations: labels.length, membership_cases: cases.length, gray_styling: true }) + '\n');
