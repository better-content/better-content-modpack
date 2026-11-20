// Focused, loader-free retirement contract. Run only after the batch-4 test gate.
// Native runtime availability and save migration require separate acceptance.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const root = path.join(__dirname, '..')
const pack = (...parts) => path.join(root, ...parts)
const materialData = relative => JSON.parse(fs.readFileSync(pack('kubejs', 'data',
    'better_manufacturing', relative), 'utf8'))

assert.equal(fs.existsSync(pack('kubejs', 'server_scripts', 'progression',
    '46_polymer_manufacturing.js')), false, 'retired PLA/PP acquisition script must not execute')
for (const stock of ['pla', 'pp']) {
    for (const mode of ['stock', 'melt']) {
        assert.equal(fs.existsSync(pack('kubejs', 'data', 'better_manufacturing', 'recipes',
            `polymer_${stock}_${mode}_material.json`)), false,
        `retired ${stock} ${mode} casting link must not load`)
    }
}
for (const [stock, material, headDurability, headMiningSpeed, handleDurability, handleMiningSpeed] of [
    ['pla', 'pla', 85, 2.5, -0.15, -0.10],
    ['pp', 'polypropylene', 140, 3.0, 0.05, -0.05]
]) {
    assert.equal(materialData(`tinkering/materials/definition/${material}.json`).tier, 1,
        `${material} saved material definition must remain registered`)
    assert.equal(fs.existsSync(pack('kubejs', 'data', 'better_manufacturing',
        'engineering', 'materials', `${material}.json`)), false,
        `${material} must not restore a second machine-rating table`)
    const stats = materialData(`tinkering/materials/stats/${material}.json`).stats
    assert.equal(stats['tconstruct:head'].durability, headDurability)
    assert.equal(stats['tconstruct:head'].mining_speed, headMiningSpeed)
    assert.equal(stats['tconstruct:handle'].durability, handleDurability)
    assert.equal(stats['tconstruct:handle'].mining_speed, handleMiningSpeed)
}
const native = fs.readFileSync(pack('kubejs', 'server_scripts', 'progression',
    '81_pneumaticcraft_progression.js'), 'utf8')
assert.match(native, /pneumaticcraft:plastic/, 'native plastic consumers remain')
assert.doesNotMatch(native, /event\.remove\(\{\s*output:\s*'pneumaticcraft:plastic'/,
    'do not remove the native plastic output')
console.log('PASS polymer retirement: four acquisition links absent; PLA/PP saved material stats retained; native plastic untouched')
