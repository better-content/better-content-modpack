// Execute the remaining production loot policies, not an invented ItemStack API.
// This is a Minecraft-free source behavior check, not native runtime acceptance.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = process.argv[2]
for (const retired of ['kubejs/config/chest_loot_normalization.json',
    'kubejs/server_scripts/policy/chest_loot_normalization.js']) {
    assert(!fs.existsSync(path.join(root, retired)), `blanket normalization must stay retired: ${retired}`)
}
const quarantine = JSON.parse(fs.readFileSync(path.join(root, 'kubejs/config/quarantined_items.json'))).items
const ids = new Set([...quarantine, 'minecraft:bread', 'minecraft:paper', 'minecraft:diamond',
    'minecraft:iron_sword', 'minecraft:wheat_seeds', 'minecraft:pumpkin_seeds', 'minecraft:melon_seeds',
    'minecraft:elytra', 'minecraft:netherite_ingot', 'minecraft:potion', 'minecraft:splash_potion',
    'minecraft:lingering_potion', 'minecraft:tipped_arrow',
    'minecraft:raw_iron', 'minecraft:raw_copper', 'minecraft:raw_gold',
    'minecraft:raw_iron_block', 'minecraft:raw_copper_block', 'minecraft:raw_gold_block'])
class Stack {
    constructor(id, count = 1, nbt = null) { Object.assign(this, { id, count, nbt }) }
    // KubeJS hasTag is item-tag membership, NOT a zero-argument NBT predicate.
    hasTag(tag) { assert.equal(typeof tag, 'string', 'hasTag requires an item tag'); return false }
    is(item) { return this.id === item }
}
class List extends Array {
    removeIf(predicate) { for (let i = this.length - 1; i >= 0; i--) if (predicate(this[i])) this.splice(i, 1) }
}
const handlers = []
const event = {
    addLootTypeModifier(type) {
        const builder = this.addLootTableModifier(/.*/)
        handlers[handlers.length - 1].type = type
        return builder
    },
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
    Item: { exists: id => ids.has(id) },
    Platform: { isLoaded: () => true },
    LootJS: { modifiers: fn => fn(event) },
    Java: { loadClass(name) {
        if (name.endsWith('LootContextType')) return { CHEST: 'CHEST' }
        if (name.endsWith('BuiltInRegistries')) return { ITEM: { keySet() {
            const iterator = ids.values(); let next = iterator.next()
            return { iterator: () => ({ hasNext: () => !next.done, next() {
                const value = next.value; next = iterator.next(); return value
            } }) }
        } } }
        if (name.endsWith('PotionUtils')) return {
            getPotion: s => ({ equals: p => (s.nbt?.Potion || 'minecraft:empty') === p }),
            getCustomEffects: s => ({ isEmpty: () => !s.nbt?.CustomPotionEffects?.length })
        }
        if (name.endsWith('Potions')) return { WATER: 'minecraft:water' }
        if (name.endsWith('Items')) return {
            POTION: 'minecraft:potion', SPLASH_POTION: 'minecraft:splash_potion',
            LINGERING_POTION: 'minecraft:lingering_potion', TIPPED_ARROW: 'minecraft:tipped_arrow'
        }
        throw new Error(name)
    } }
})
for (const file of ['global_loot_progression.js', 'ore_raw_material_loot.js']) {
    vm.runInContext(fs.readFileSync(path.join(root, 'kubejs/server_scripts/policy', file), 'utf8'), sandbox, { filename: file })
}
function run(table, stacks, type = 'CHEST') {
    const loot = List.from(stacks)
    // Deliberately expose no RNG, list-clear/rebuild or supply-generation APIs.
    const context = { loot, getLoot: () => loot, getLootTableId: () => table, getType: () => type }
    for (const { pattern, actions, type: requiredType } of handlers) {
        if (pattern.test(table) && (!requiredType || requiredType === type)) {
            for (const action of actions) action(context)
        }
    }
    return Array.from(loot)
}
// Neither an empty native roll nor progression filtering manufactures compensation.
for (const table of ['minecraft:chests/simple_dungeon', 'quark:entities/stoneling',
    'example:mob_carried_reward', 'example:nested/helper', 'example:future_container',
    'the_bumblezone:structures/sempiternal_sanctum/sudoku_failed', 'minecraft:empty']) {
    assert.deepEqual(run(table, []), [], table)
}
for (const table of ['minecraft:chests/simple_dungeon', 'example:mob_carried_reward']) {
    assert.deepEqual(run(table, [new Stack('minecraft:wheat_seeds', 32),
        new Stack('minecraft:netherite_ingot'), new Stack('minecraft:potion', 1, { Potion: 'minecraft:healing' })]), [])
}
// Preserve native quantities, duplicate-stack grouping, object identity and data.
for (const table of ['minecraft:chests/simple_dungeon', 'example:mob_carried_reward',
    'example:nested/helper', 'example:future_container']) {
    const input = [new Stack('minecraft:bread', 64), new Stack('minecraft:bread', 64),
        new Stack('minecraft:paper', 40, { map: 12 }), new Stack('minecraft:diamond', 9),
        new Stack('minecraft:potion', 1, { Potion: 'minecraft:water', Purity: 3 })]
    const before = JSON.stringify(input)
    const result = run(table, input)
    assert.equal(JSON.stringify(input), before)
    assert.deepEqual(result, input)
    result.forEach((stack, i) => assert.equal(stack, input[i]))
}
// Seed removal remains chest-context-only; unrelated drops retain native quantities.
for (const type of ['BLOCK', 'ENTITY', 'FISHING', 'GIFT', 'UNKNOWN']) {
    const seed = new Stack('minecraft:wheat_seeds', 32)
    assert.equal(run('example:reward', [seed], type)[0], seed)
}
// Existing geological replacement is unchanged, with no subsequent quantity cap.
const geological = run('minecraft:chests/simple_dungeon', [new Stack('minecraft:raw_iron', 7)])
assert.equal(geological.length, 1)
assert.equal(geological[0].id, 'better_ore_geology:small_ore_chunk_ironstone')
assert.equal(geological[0].count, 7)
console.log('Chest loot ownership: native quantities, no filler, preserved state and progression filters passed')
