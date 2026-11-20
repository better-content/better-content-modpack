// Loader-free pack source contract; requires separate batch-4 RUN approval.
// Not a loaded RecipeManager, Minecraft runtime or save-compatibility test.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const pack = path.resolve(__dirname, '..')
const file = relative => path.join(pack, relative)
const text = relative => fs.readFileSync(file(relative), 'utf8')
const json = relative => JSON.parse(text(relative))
const absent = relative => assert.equal(fs.existsSync(file(relative)), false, relative)
const banned = ['chemlib', 'better_chemlib_hazards', 'better_airtight_machines']
const active = json('gradle/active-custom-mods.json').mods.map(mod => mod.modId)
for (const id of banned) {
    assert.ok(!active.includes(id), `${id} must not be an active custom mod`)
    assert.ok(!Object.hasOwn(json('kubejs/config/crafting_policy.json').namespaces, id),
        `${id} must not have an active crafting namespace`)
}
absent('mods/chemlib.pw.toml')
absent('mods/better-chemlib-hazards-0.2.0.jar')
absent('mods/better-airtight-machines-0.1.0.jar')
absent('config/chemlib-common.toml')
absent('kubejs/config/chemlib_form_policy.json')
absent('kubejs/data/better_chemlib_hazards')
assert.ok(!json('kubejs/data/forge/loot_modifiers/global_loot_modifiers.json')
    .entries.includes('better_chemlib_hazards:placed_nuclear_state'))
assert.ok(!json('config/almostunified/unify.json').modPriorities.includes('chemlib'))

// The pressure seal and structural block remain distinct from the absent installable upgrade.
const script = text('kubejs/server_scripts/progression/40_thermal_pressure.js')
function emitted(withParts) {
    const recipes = new Map(), removed = []
    const entry = (kind, value, pattern, key) => ({ id(id) {
        assert.ok(!recipes.has(id), `duplicate authored recipe ${id}`)
        recipes.set(id, {kind, value, pattern, key})
        return this
    } })
    const event = {
        remove(selector) { removed.push(selector) },
        custom(value) { return entry('custom', value) },
        shaped(value, pattern, key) { return entry('shaped', value, pattern, key) },
        shapeless(value, ingredients) { return entry('shapeless', value, ingredients) }
    }
    vm.runInNewContext(script, {
        ServerEvents: { recipes(callback) { callback(event) } },
        Platform: { isLoaded(mod) { return withParts && mod === 'better_manufacturing' } },
        Item: { of(id, nbt) { return {id, nbt} } }
    }, {filename: '40_thermal_pressure.js', timeout: 1000})
    return {recipes, removed}
}
for (const withParts of [false, true]) {
    const {recipes, removed} = emitted(withParts)
    const seal = recipes.get('kubejs:thermal_pressure/pressure_seal')
    assert.equal(seal.value, '2x kubejs:pressure_seal')
    assert.equal(seal.key.K, 'minecraft:dried_kelp')
    const block = recipes.get('kubejs:thermal_pressure/airtight_machine_block').value
    assert.equal(block.result.item, 'kubejs:airtight_machine_block')
    assert.equal(block.key.S.item, 'kubejs:pressure_seal')
    const chamber = recipes.get('kubejs:thermal_pressure/direct_root/pressure_chamber_interface')
    assert.ok(chamber, 'pressure chamber interface remains craftable')
    const ingredients = chamber.kind === 'custom' ? chamber.value.key : chamber.key
    assert.equal(ingredients.H.item || ingredients.H, 'minecraft:hopper')
    assert.equal(ingredients.W.item || ingredients.W, 'pneumaticcraft:pressure_chamber_wall')
    assert.equal(ingredients.A.item || ingredients.A, 'kubejs:pressure_seal')
    assert.ok(removed.some(row => row.id === 'pneumaticcraft:pressure_chamber_interface'))
    for (const native of ['pressure_chamber_wall', 'pressure_chamber_glass', 'pressure_chamber_valve']) {
        assert.ok(!removed.some(row => row.output === `pneumaticcraft:${native}` ||
            row.id === `pneumaticcraft:${native}`), `native ${native} must remain`)
    }
    assert.ok(!JSON.stringify([...recipes.values()]).includes('better_airtight_machines:'))
}
const acid = text('kubejs/server_scripts/progression/45_acid_chemistry.js')
assert.match(acid, /pneumaticcraft:pressure_chamber\/coal_to_diamond/)
assert.doesNotMatch(acid, /event\.remove\([^\n]*etching_acid|event\.remove\([^\n]*etching_tank/)
for (const relative of [
    'kubejs/data/pneumaticcraft/tags/fluids/etching_acid.json',
    'kubejs/data/pneumaticcraft/advancements/etchacid_bucket.json',
    'kubejs/data/pneumaticcraft/advancements/recipes/misc/etching_tank.json',
    'kubejs/assets/pneumaticcraft/patchouli_books/book/en_us/entries/manufacturing/etching_acid.json'
]) absent(relative)

const components = text('kubejs/server_scripts/progression/70_transition_components.js')
assert.match(components, /item: 'better_ore_geology:gold_concentrate'[^]*?item: 'better_ore_geology:gold_concentrate'[^]*?fluid: 'minecraft:water', amount: 250[^]*?item: 'kubejs:platinum_group_residue', count: 2/)
assert.match(components, /item: 'tconstruct:steel_ingot'[^]*?item: 'tconstruct:scorched_brick'[^]*?item: 'kubejs:titanium_thermal_plate'/)
const terrain = json('kubejs/config/terrain_recovery.json')
assert.ok(!Object.hasOwn(terrain, 'chemistry_audit'))
assert.ok(!Object.hasOwn(terrain.survey_pins, 'mods/chemlib.pw.toml'))
assert.ok(!terrain.archives.includes('mods/chemlib.pw.toml'))
assert.ok(terrain.entries.length > 0, 'physical terrain recovery entries retained')
for (const [name, entry] of Object.entries(terrain.entries)) {
    assert.ok(!JSON.stringify(entry).includes('chemlib:'), `${name} still grants removed chemistry`)
}
console.log('PASS retirement contract: active membership, native etchant/chamber, structural roots, residue/plate and terrain')
