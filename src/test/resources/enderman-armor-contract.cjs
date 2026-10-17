const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const armor = Symbol('armor');
const enderman = { equals: type => type === 'minecraft:enderman' };
let handler;
vm.runInNewContext(fs.readFileSync(process.argv[2], 'utf8'), {
    Java: { loadClass: name => {
        if (name === 'net.minecraft.world.entity.EntityType') return { ENDERMAN: enderman };
        assert.equal(name, 'net.minecraft.world.entity.ai.attributes.Attributes');
        return { ARMOR: armor };
    } },
    ForgeEvents: { onEvent: (name, callback) => {
        assert.equal(name, 'net.minecraftforge.event.entity.EntityJoinLevelEvent');
        handler = callback;
    } }
});
function mob(type, base = 0) {
    return {
        base, writes: 0, toughness: 0, health: 40,
        getType: () => type,
        getAttribute(attribute) {
            assert.equal(attribute, armor);
            return { setBaseValue: value => { this.base = value; this.writes++; } };
        }
    };
}
function join(entity, client = false) {
    handler({ getLevel: () => ({ isClientSide: () => client }), getEntity: () => entity });
}
const fresh = mob('minecraft:enderman');
join(fresh);
assert.equal(fresh.base, 4);
join(fresh);
assert.equal(fresh.base, 4, 'repeat joins must not stack');
const saved = mob('minecraft:enderman', 7);
join(saved);
assert.equal(saved.base, 4, 'saved entities receive the same base');
assert.equal(saved.health, 40);
assert.equal(saved.toughness, 0);
for (const type of ['minecraft:zombie', 'endermanoverhaul:cave_enderman', 'minecraft:item']) {
    const other = mob(type);
    join(other);
    assert.equal(other.writes, 0, type);
}
const client = mob('minecraft:enderman');
join(client, true);
assert.equal(client.writes, 0);
console.log('enderman armour contract passed');
