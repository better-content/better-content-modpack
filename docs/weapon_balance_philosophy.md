# Weapon Balance Philosophy

This is the living design authority for weapon balance in Better Content. It records the reasoning that survives individual tuning passes: what a weapon owes the player, which advantages may alter its damage budget, and which exceptions must be explicit.

The document currently contains only the Tinkers' Construct family philosophy. Other weapon systems need their own named pass; they do not inherit TCon values automatically.

## Shared principles

1. **DPS is mandatory.** Utility, manufacturing difficulty, rarity, and mob-specific bonuses do not replace general combat output.
2. **Normalize baselines, not every build.** Materials, traits, upgrades, affixes, execution, and encounters may diverge after the clean chassis is sound.
3. **Costs and advantages are asymmetric.** Short reach and slow commitment are strongly punishing and deserve compensation. Long reach and high speed have diminishing rewards.
4. **Slow attacks hit harder.** Missed, interrupted, and overkilling slow swings are costly, so committed weapons must visibly pay out per connection.
5. **Ordinary AOE is not a second damage bar.** Common cleave and sweeps are texture, not a large DPS tax.
6. **Generic effects count; narrow effects do not.** Armor piercing, armor bypass, unconditional offhand attacks, and generic burst belong in E-DPS. Mob-family bonuses do not.
7. **Primary and alternate modes have separate budgets.** Throws, charge shots, charged strikes, block counters, and casts do not inflate the neutral melee baseline.
8. **Animation identity needs a viable carrier.** Each supported Epic Fight form has at least one credible TCon tool or weapon.
9. **Tools and weapons remain separate catalogues.** A tool may fight well without ceasing to be work-first.

## Tinkers' Construct family

### Baseline

The authoritative machine-readable catalogue is `kubejs/config/tcon_edps_catalogue.json`. Its neutral grounded-combo baseline excludes dash, air, mount, innate skills, throws, charges, casting, and dual variants. Generic armour effects count; normal AOE does not.

| Relative tier | Absolute old-sword DPS |
|---:|---:|
| 90 | 81.0% |
| 95 | 85.5% |
| 100 | 90.0% |
| 105 | 94.5% |
| 110 | 99.0% |

The reference is the former same-material TCon Sword baseline. Native attack speed and material scaling are retained, so slow weapons receive larger per-hit multipliers. Pierce and the Estoc's generic bypass are evaluated against the pack armour canon; material traits, upgrades, utility, manufacturing cost, ordinary AOE, and mob-specific effects are excluded.

### Epic Fight form map

The pack replaces Better Combat with Epic Fight. Every chassis in the 45-entry balance catalogue has an explicit item capability that selects an existing Epic Fight form; the pack does not author bespoke movesets for each TCon item. Longbow, Crossbow, and Swasher retain the integration's dedicated ranged capabilities. Tools are mining-preferred and weapons combat-preferred.

| Tool form | Tools |
|---|---|
| Axe | Hand Axe, Broad Axe, Wrench |
| Fist | Sniffer Claws |
| Greatsword | Sledge Hammer, Vein Hammer, Scythe, Blockram |
| Pickaxe | Mattock, Pickaxe, Pickadze, War Pick, Chisel |
| Shovel | Excavator, Shovel |
| Sword | Battle Spade |
| Hoe | Kama |

| Weapon form | Weapons |
|---|---|
| Axe | Butcher Knife, Minotaur Axe |
| Bow | Shortbow |
| Crossbow | Blowpipe |
| Dagger | Dagger, Rapier |
| Fist | Buckler |
| Greatsword | Cleaver, Greatsword, Helix Blade |
| Longsword | Estoc |
| Spear | Pitchfork, Halberd, Amethyst Staff, Quarterstaff, Pike, Lance |
| Sword | Cutlass, Battlesign, Scepter, Sword |
| Tachi | Fuma Shuriken, Khopesh, Katana |
| Trident | Javelin |
| Integration ranged | Longbow, Crossbow, Swasher |

The machine-readable mappings live under each item's
`kubejs/data/<namespace>/capabilities/weapons/` resource path, where they override the
maintained `epic_tinkers_construct_v9` datapack for the catalogue roster. Existing v9
numeric attributes and colliders remain intact apart from schema/style corrections;
items outside the catalogue continue through v9 or the compatibility mod's fallback.

The normal item-hover surface derives one concise player-facing handling line
from this catalogue and those capability mappings. It humanizes the Epic Fight
form and any material alternate mode, marks work-first tools where useful, and
never exposes EDPS tiers, multipliers, head-scale math, or compatibility keys.

Shovel and Battle Spade remain separate: the Shovel is a conventional work tool with shovel motion, while the two-head Battle Spade is a martial combat chassis with sword combos. Javelin keeps its throw through Trident; Amethyst Staff keeps its caster identity while using a melee Spear form.

### Dodge, camera, and alternate modes

Combat Roll and Better Content Fixes' directional double-tap integration are removed. Epic Fight's native Roll and Step progression is the only dodge system and uses Epic Fight's dedicated input. The Epic Fight combat camera is active in third-person only; it never changes perspective automatically.

Returning Blockram, Buckler, and Fuma Shuriken throws use the separate 100-tier throw target. Javelin throws stay at 105 per hit. Battlesign's charged Bonk and War Pick's charge projectile target 100. The Minotaur Axe sprint charge remains the one explicit signature exception.

The repository's supported full-depth evaluation is the explicitly ordered `./test.main.kts debug` tier.
