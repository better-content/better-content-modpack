// Execute the production scripts; stand-ins model only the Java collection/stack
// methods they use. This is a source behavior check, not Minecraft acceptance.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = process.argv[2]
const config = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/chest_loot_normalization.json')))
const families = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/crafting_policy.json'))).families
const quarantine = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/quarantined_items.json'))).items
const ids = new Set([...Object.keys(config.bundles), ...quarantine,
    'minecraft:iron_sword', 'minecraft:wheat_seeds', 'minecraft:pumpkin_seeds', 'minecraft:melon_seeds',
    'minecraft:elytra', 'minecraft:netherite_ingot', 'minecraft:potion', 'minecraft:splash_potion',
    'minecraft:lingering_potion', 'minecraft:tipped_arrow', 'minecraft:diamond',
    'better_compat_fixes:soap_on_a_rope', 'better_rail_beetle:better_rail_beetle',
    'minecraft:raw_iron', 'minecraft:raw_copper', 'minecraft:raw_gold',
    'minecraft:raw_iron_block', 'minecraft:raw_copper_block', 'minecraft:raw_gold_block'])
const EMPTY = { equals(other) { return other === EMPTY } }
class Stack {
    constructor(id, count = 1, tag = null) { Object.assign(this, { id, count, tag }) }
    getCount() { return this.count }
    isEmpty() { return this.count <= 0 }
    hasTag() { return this.tag !== null }
    is(item) { return this.id === item }
}
class List extends Array {
    size() { return this.length }
    get(i) { return this[i] }
    add(stack) { this.push(stack) }
    clear() { this.length = 0 }
    removeIf(predicate) { for (let i = this.length - 1; i >= 0; i--) if (predicate(this[i])) this.splice(i, 1) }
}
const handlers = []
const event = {
    addLootTableModifier(pattern) {
        const actions = []
        handlers.push({ pattern, actions })
        return {
            removeLoot(id) { actions.push(c => c.loot.removeIf(s => s.id === id)); return this },
            replaceLoot(from, to) {
                actions.push(c => c.loot.forEach(s => { if (s.id === from) s.id = to })); return this
            },
            apply(fn) { actions.push(fn); return this }
        }
    }
}
const sandbox = vm.createContext({
    JsonIO: { read: p => JSON.parse(fs.readFileSync(path.join(root, p))) },
    Item: { of: (id, n) => new Stack(id, n), exists: id => ids.has(id) },
    Platform: { isLoaded: () => true },
    LootJS: { modifiers: fn => fn(event) },
    Java: { loadClass(name) {
        if (name.endsWith('LootTable')) return { EMPTY }
        if (name.endsWith('BuiltInRegistries')) return { ITEM: { keySet() {
            const iterator = ids.values(); let next = iterator.next()
            return { iterator: () => ({ hasNext: () => !next.done, next() {
                const value = next.value; next = iterator.next(); return value
            } }) }
        } } }
        if (name.endsWith('PotionUtils')) return {
            getPotion: s => ({ equals: p => (s.tag?.Potion || 'minecraft:empty') === p }),
            getCustomEffects: s => ({ isEmpty: () => !s.tag?.CustomPotionEffects?.length })
        }
        if (name.endsWith('Potions')) return { WATER: 'minecraft:water' }
        if (name.endsWith('Items')) return {
            POTION: 'minecraft:potion', SPLASH_POTION: 'minecraft:splash_potion',
            LINGERING_POTION: 'minecraft:lingering_potion', TIPPED_ARROW: 'minecraft:tipped_arrow'
        }
        throw new Error(name)
    } }
})
const scripts = ['global_loot_progression.js', 'ore_raw_material_loot.js', 'chest_loot_normalization.js']
const priority = file => Number(fs.readFileSync(path.join(root, 'kubejs/server_scripts/policy', file), 'utf8')
    .match(/^\/\/ priority: (-?\d+)/)?.[1] || 0)
assert(priority('chest_loot_normalization.js') < Math.min(priority(scripts[0]), priority(scripts[1])))
for (const file of scripts.sort((a, b) => priority(b) - priority(a))) {
    vm.runInContext(fs.readFileSync(path.join(root, 'kubejs/server_scripts/policy', file), 'utf8'), sandbox, { filename: file })
}
function run(table, stacks, { seed = 1, type = 'CHEST', missing = false } = {}) {
    const loot = List.from(stacks)
    let state = seed >>> 0
    const context = {
        loot, getLoot: () => loot, getLootTableId: () => table, getType: () => type,
        getServer: () => ({ getLootData: () => ({ getLootTable: () => missing ? EMPTY : { equals: () => false } }) }),
        getRandom: () => ({ nextInt(n) {
            assert(n > 0)
            state = (Math.imul(state, 1664525) + 1013904223) >>> 0
            return state % n
        } })
    }
    for (const { pattern, actions } of handlers) if (pattern.test(table)) for (const action of actions) action(context)
    return loot
}
function score(loot) {
    return loot.reduce((n, s) => n + (config.bundles[s.id] && !s.hasTag()
        ? Math.ceil(s.count / config.bundles[s.id]) : 1), 0)
}
function summary(loot) { return loot.map(s => [s.id, s.count, s.tag]) }
function cut(id) {
    return quarantine.includes(id) || families.some(f => ['cut', 'authority', 'canonical_duplicate'].includes(f.disposition)
        && (f.selectors || []).some(s => s.exact_id === id || ((s.namespace || s.id_prefix)
            && (!s.namespace || id.split(':')[0] === s.namespace)
            && (!s.id_prefix || id.split(':')[1].startsWith(s.id_prefix)))))
}
assert.equal(config.schema, 'bc.chest_loot_normalization.v1')
assert.equal(new Set(config.supplements).size, config.supplements.length)
for (const [id, count] of Object.entries(config.bundles)) {
    assert(Number.isInteger(count) && count >= 1 && count <= 32, id)
    assert(!cut(id), `normalization must not resurrect policy cuts: ${id}`)
}
for (const id of config.supplements) {
    assert(config.bundles[id], `missing bundle for ${id}`)
    assert(!/seed|potion|coin|processor|raw_/.test(id), `unsafe supplement ${id}`)
}
for (const profile of Object.values(config.profiles)) {
    assert(Number.isInteger(profile.min) && Number.isInteger(profile.max))
    assert(profile.min > 0 && profile.min <= profile.max && profile.max <= 2 * config.supplements.length)
}
for (const [table, profile] of [
    ['minecraft:chests/village/village_plains_house', config.profiles.supplies],
    ['forgottenruins:chest/interior', config.profiles.supplies],
    ['iceandfire:chest/fire_dragon_roost', config.profiles.encounter],
    ['goety:chests/crypt_loot', config.profiles.encounter],
    ['cataclysm:chests/desert_treasure', config.profiles.treasure],
    ['forgottenruins:chest/burial_chamber', config.profiles.treasure]
]) {
    for (let seed = 1; seed <= 200; seed++) {
        for (const input of [[], [new Stack('minecraft:bread')],
            Array.from({ length: 30 }, () => new Stack('minecraft:bread', 64)),
            Object.keys(config.bundles).map(id => new Stack(id, 64)),
            [new Stack('minecraft:iron_sword'), new Stack('minecraft:wheat_seeds', 16),
                new Stack('minecraft:potion', 1, { Potion: 'minecraft:healing' })]
        ]) {
            const loot = run(table, input, { seed })
            assert(score(loot) >= profile.min && score(loot) <= profile.max, `${table}: ${score(loot)}`)
            for (const s of loot) if (config.bundles[s.id] && !s.hasTag()) assert(s.count <= 2 * config.bundles[s.id])
            assert(loot.every(s => s.count > 0))
            assert(!loot.some(s => cut(s.id)))
        }
    }
}
// Same seed, same outcome; no Math.random or player/global state.
assert.deepEqual(summary(run('goety:chests/crypt_loot', [], { seed: 67 })),
    summary(run('goety:chests/crypt_loot', [], { seed: 67 })))
// Both naming conventions lose seeds; compensation occurs afterward.
for (const table of ['forgottenruins:chest/farming', 'minecraft:chests/village/village_plains_house']) {
    const loot = run(table, [new Stack('minecraft:wheat_seeds', 32)])
    assert(!loot.some(s => s.id === 'minecraft:wheat_seeds'))
    assert(score(loot) >= 6)
}
// Preserve the exact object and state of special/native injection rewards.
const special = [new Stack('minecraft:diamond', 9), new Stack('minecraft:paper', 40, { map: 12 }),
    new Stack('better_compat_fixes:soap_on_a_rope', 1, { owner: 'native' }),
    new Stack('better_rail_beetle:better_rail_beetle', 1, { RailBeetleStarterPackage: 1 }),
    new Stack('minecraft:potion', 1, { Potion: 'minecraft:water', Purity: 3 })]
const protectedResult = run('minecraft:chests/abandoned_mineshaft', special)
for (const stack of special) assert(protectedResult.includes(stack))
assert.equal(special[1].count, 40)
// More special rewards than the ceiling: retain all rather than deleting rare gear.
const overflow = Array.from({ length: 20 }, (_, n) => new Stack('special:relic', 1, { n }))
assert.deepEqual(Array.from(run('minecraft:chests/simple_dungeon', overflow)), overflow)
// No normalization outside root chest tables or for an unresolved table.
for (const table of ['minecraft:blocks/stone', 'minecraft:entities/zombie', 'minecraft:gameplay/fishing',
    'minecraft:archaeology/desert_pyramid', 'artifacts:inject/chests/desert_pyramid',
    'goety:inject/chests/simple_dungeon', 'thirst:chests/simple_dungeon', 'lootr:chests/elytra', 'minecraft:empty']) {
    assert.equal(run(table, []).length, 0, table)
}
assert.equal(run('example:chests/missing', [], { missing: true }).length, 0)
assert.equal(run('minecraft:chests/simple_dungeon', [], { type: 'ENTITY' }).length, 0)
// Raw geological routes still happen first, and the replacement is never reduced.
const geological = run('minecraft:chests/simple_dungeon', [new Stack('minecraft:raw_iron', 7)])
assert.equal(geological.find(s => s.id === 'better_ore_geology:small_ore_chunk_ironstone').count, 7)
// Repeated upstream/injected stacks have the same commodity budget as one stack.
const split = run('minecraft:chests/simple_dungeon', [new Stack('minecraft:bread', 4), new Stack('minecraft:bread', 4)])
const merged = run('minecraft:chests/simple_dungeon', [new Stack('minecraft:bread', 8)])
assert.deepEqual(summary(split), summary(merged))
console.log('Chest loot normalization: production behavior, tier bounds, policy cuts, exclusions, protected state and deterministic RNG passed')
