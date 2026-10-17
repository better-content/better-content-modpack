const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

// Representative native tag memberships, including multipart/single's
// inclusion in multipart. Addon multipart gear follows the same selector.
const assembled = ['tconstruct:pickaxe', 'tconstruct:cleaver', 'tconstruct:arrow', 'addon:multipart_tool'];
const single = ['tconstruct:travelers_shield', 'tconstruct:plate_shield', 'tconstruct:slime_helmet'];
const worn = ['tconstruct:plate_helmet', 'tconstruct:travelers_boots', 'tconstruct:slime_helmet'];
const direct = ['tconstruct:flint_and_brick', 'tconstruct:sky_staff', 'tconstruct:earth_staff', 'tconstruct:ender_staff', 'tconstruct:ichor_staff'];
const tags = {
    '#tconstruct:modifiable': [...assembled, ...single, ...worn, ...direct],
    '#tconstruct:modifiable/multipart': [...assembled, ...single, 'tconstruct:plate_helmet'],
    '#tconstruct:modifiable/multipart/single': single,
    '#tconstruct:modifiable/armor/worn': worn
};
function ingredient(values) {
    return {
        values: new Set(values),
        subtract(other) { return ingredient([...this.values].filter(id => !other.values.has(id))); }
    };
}
const source = fs.readFileSync(process.argv[2], 'utf8');
for (const emiLoaded of [false, true]) {
    const hidden = { jei: [], emi: [] };
    const handler = viewer => callback => callback({ hide(value) { hidden[viewer].push(...value.values); } });
    const context = {
        Ingredient: { of(tag) { assert.ok(tag in tags, `unexpected selector ${tag}`); return ingredient(tags[tag]); } },
        JEIEvents: { hideItems: handler('jei') },
        Platform: { isLoaded: mod => emiLoaded && mod === 'emi' }
    };
    if (emiLoaded) context.EMIEvents = { hideItems: handler('emi') };
    vm.runInNewContext(source, context);
    assert.deepEqual(hidden.jei.sort(), [...assembled].sort());
    assert.deepEqual(hidden.emi.sort(), emiLoaded ? [...assembled].sort() : []);
    for (const id of [...single, ...worn, ...direct, 'tconstruct:pick_head', 'tconstruct:seared_brick']) {
        assert.ok(!hidden.jei.includes(id), `${id} must stay visible`);
        assert.ok(!hidden.emi.includes(id), `${id} must stay visible`);
    }
}
console.log('station/anvil multipart tools hidden; direct crafts, single-part gear, armor and parts visible in both viewers');
