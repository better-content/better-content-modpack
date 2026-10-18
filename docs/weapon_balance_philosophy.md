# Weapon Balance Philosophy

This is the living design authority for weapon balance in Better Content. It records the reasoning that survives individual tuning passes: what a weapon owes the player, which advantages may alter its damage budget, and which exceptions must be explicit.

The balance philosophy currently covers the Tinkers' Construct family. The style inventory also records upstream Epic Fight and Weapons of Miracles presets; it does not extend TCon balance values to those native weapons. Other weapon systems need their own named balance pass.

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
| Tachi | Fuma Shuriken, Katana |
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

## Epic Fight and Weapons of Miracles style inventory

Weapons of Miracles is pinned on both sides by
`mods/weapons-of-miracles.pw.toml` to **2.0.171 for Forge 1.20.1** (Modrinth
version `nf0AyjKh`). Its upstream release notes name our pinned Epic Fight
**20.14.17** as the tested version; its native dependency range is `[20.14.3,)`.
This establishes declared compatibility, not full-pack runtime acceptance.
The `wom` crafting-policy namespace retains upstream content; installation does
not constitute a progression/balance pass or guarantee every survival acquisition
route in this closed-End pack. No TCon mappings are changed by this addition.

The inventory counts named capability presets, not individual animations or
learnable skills. A preset can reuse another form's animations, and styles such
as dual wield or sheath can be branches within one preset.

### Base Epic Fight: 16 presets

The pinned `WeaponCapabilityPresets` defines Axe, Hoe, Pickaxe, Shovel, Sword,
Spear, Greatsword, Uchigatana, Tachi, Longsword, Dagger, Fist, Bow, Crossbow,
Trident and Shield. The TCon tables above cover all except Uchigatana and Shield.
Ordinary shields supply the defensive Shield capability; Buckler is deliberately
Fist instead. Our Katana is Tachi, not Uchigatana, so Battojutsu's sheath/draw
playstyle has no explicitly mapped TCon carrier. The native EF Uchigatana's
survival acquisition route is not established here.

### Weapons of Miracles: 17 named presets

These are the actual types in the pinned JAR's
`data/wom/capabilities/weapons/` resources and registered by
`WOMWeaponCapabilityPresets`. Carriers are native item mappings, not claims that
all of those items are currently obtainable in survival.

| Preset | Native carrier(s) |
|---|---|
| `wom:staff` | Wooden, Stone, Golden, Iron, Diamond and Netherite Staff |
| `wom:agony` | Agony |
| `wom:torment` | Torment (`wom:tormented_mind`) |
| `wom:ruine` | Ruine |
| `wom:satsujin` | Satsujin |
| `wom:ender_blaster` | Ender Blaster |
| `wom:clawed_gauntle` | Clawed Gauntle (`wom:jabberwocky`; upstream spelling) |
| `wom:antitheus` | Antitheus |
| `wom:herrscher` | Herrscher |
| `wom:gesetz` | Gesetz |
| `wom:moonless` | Moonless |
| `wom:solar` | Solar |
| `wom:napoleon` | Napoleon |
| `wom:evil_tachi` | Evil Odachi (`wom:evil_tachi`) |
| `wom:orbit` | Orbit |
| `wom:nova` | Nova |
| `wom:blackstar` | Blackstar |

WoM's Golden, Iron, Diamond and Netherite Greataxes map to base
`epicfight:greatsword`, and Hollow Longsword maps to `epicfight:longsword`;
these do not add distinct presets. The existing TCon integration contributes
three more named presets: `epicfighttinkercompat:longbow`,
`epicfighttinkercompat:crossbow` and `epicfighttinkercompat:swasher`.
That is **36 named presets** across these three providers: 16 base + 17 WoM + 3
TCon integration. It is not a count of 36 wholly independent animation sets.
`WeaponsOfMiraclesContractTest` inspects the hash-pinned native resources to keep
the WoM inventory and fallback distinctions reproducible without Minecraft.
Packwiz index/hash refresh and runtime acceptance remain release-preparation work
under the [shared testing policy](testing.md).

## TCon dodge, camera, and alternate modes

Combat Roll and Better Content Fixes' directional double-tap integration are removed. Epic Fight's native Roll and Step progression is the only dodge system and uses Epic Fight's dedicated input. The Epic Fight combat camera is active in third-person only; it never changes perspective automatically.

Returning Blockram, Buckler, and Fuma Shuriken throws use the separate 100-tier throw target. Javelin throws stay at 105 per hit. Battlesign's charged Bonk and War Pick's charge projectile target 100. The Minotaur Axe sprint charge remains the one explicit signature exception.

The repository's supported full-depth evaluation is the explicitly ordered `./test.main.kts debug` tier.
