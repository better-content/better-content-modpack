// Loader-free guard for the retired ChemLib-derived exact-gas AdPother layer.
// Does not establish save compatibility or exercise a Minecraft runtime.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const pack = path.resolve(__dirname, '..')
const adpother = path.join(pack, 'config/adpother')
const pollution = path.join(adpother, 'Pollutants')
const native = ['carbon', 'dust', 'sulfur']
assert.deepEqual(fs.readdirSync(pollution).sort(), native.map(id => `${id}.cfg`).sort())
assert.equal(fs.existsSync(path.join(pack, 'kubejs/config/adpother_latent_gas_policy.json')), false)
const blockstates = path.join(pack, 'kubejs/assets/adpother/blockstates')
assert.ok(!fs.existsSync(blockstates) || fs.readdirSync(blockstates).length === 0)
for (const category of ['Emitters', 'Fuels', 'Sources', 'Torches']) {
    for (const name of fs.readdirSync(path.join(adpother, category))) {
        if (!name.endsWith('.cfg')) continue
        const text = fs.readFileSync(path.join(adpother, category, name), 'utf8')
        const emission = text.match(/\bEmission \{([^}]*)\}/)
        if (!emission) continue
        const keys = [...emission[1].matchAll(/^\s*S:([a-z_]+)=/gm)].map(match => match[1]).sort()
        assert.deepEqual(keys, native, `${category}/${name} must emit only native pollution`)
        assert.doesNotMatch(text, /latent-base-|sync-adpother-latent-gases|Expanded pollutants/)
    }
}
for (const name of ['hostile_mobs.cfg', 'livings.cfg', 'environment.cfg']) {
    const text = fs.readFileSync(path.join(adpother, 'Impacts', name), 'utf8')
    assert.doesNotMatch(text, /LATENT EXACT GAS IMPACTS/)
    for (const id of native) assert.match(text, new RegExp(`\\b${id} \\{`))
}
// Avoid silently disabling native pollution while retiring the custom exact-gas profiles.
for (const [name, carbon, sulfur] of [
    ['Sources/player_dying.cfg', '1.0', '0.0'],
    ['Torches/minecraft$torch.cfg', '0.1', '0.01']
]) {
    const text = fs.readFileSync(path.join(adpother, name), 'utf8')
    assert.ok(text.includes(`S:carbon=${carbon}`), name)
    assert.ok(text.includes(`S:sulfur=${sulfur}`), name)
}
console.log('PASS AdPother exact-gas retirement: native pollution retained, custom gas layer absent')
