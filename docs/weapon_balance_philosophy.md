# Weapon Balance Philosophy

This is the living design authority for weapon balance in Better Content. It records the reasoning that survives individual tuning passes: what a weapon owes the player, which advantages may alter its damage budget, and which exceptions must be explicit.

The balance philosophy currently covers the Tinkers' Construct family. The style inventory also records upstream Epic Fight and Weapons of Miracles presets; it does not extend TCon balance values to those native weapons. Other weapon systems need their own named balance pass.

## Shared principles

1. **DPS is mandatory.** Utility, manufacturing difficulty, rarity, and mob-specific bonuses do not replace general combat output.
2. **Normalize baselines, not every build.** Materials, traits, upgrades, affixes, execution, and encounters may diverge after the clean chassis is sound.
3. **Reach selects the next melee tier.** The approved recalibration uses actual damage-weighted grounded-combo reach alone, within 80–120 and with Sword at 100. Slow attacks retain per-hit compensation through speed normalization, not a second tier adjustment. The current legacy tiers remain active until the measurement gate below is satisfied.
4. **Slow attacks hit harder.** Missed, interrupted, and overkilling slow swings are costly, so committed weapons must visibly pay out per connection.
5. **Ordinary AOE is not a second damage bar.** Common cleave and sweeps are texture, not a large DPS tax.
6. **Generic effects count; narrow effects do not.** Armor piercing, armor bypass, unconditional offhand attacks, and generic burst belong in E-DPS. Mob-family bonuses do not.
7. **Primary and alternate modes have separate budgets.** Throws, charge shots, charged strikes, block counters, and casts do not inflate the neutral melee baseline.
8. **Animation identity needs a viable carrier.** Each supported Epic Fight form has at least one credible TCon tool or weapon.
9. **Tools and weapons remain separate catalogues.** A tool may fight well without ceasing to be work-first.

## Tinkers' Construct family

### Active legacy baseline

The authoritative machine-readable catalogue is `kubejs/config/tcon_edps_catalogue.json`. Its neutral grounded-combo baseline excludes dash, air, mount, innate skills, throws, charges, casting, and dual variants. Generic armour effects count; normal AOE does not.

| Relative tier | Absolute old-sword DPS |
|---:|---:|
| 90 | 81.0% |
| 95 | 85.5% |
| 100 | 90.0% |
| 105 | 94.5% |
| 110 | 99.0% |

The reference is the former same-material TCon Sword baseline. Native attack speed and material scaling are retained, so slow weapons receive larger per-hit multipliers. Pierce and the Estoc's generic bypass are evaluated against the pack armour canon; material traits, upgrades, utility, manufacturing cost, ordinary AOE, and mob-specific effects are excluded.

### Approved reach recalibration — measurement-gated, not active

The catalogue's `reach_normalization` policy records the approved replacement, with
`status: awaiting_complete_measurements`. Its empty measurement list is deliberately not
synthetic evidence. Current tiers, the active ×0.9 reference scale and production damage
remain unchanged until the full recalibration can be solved. The legacy table above describes
those active values, not the new target. No numerical tier is inferred from a weapon name,
visual model, trail, collider dimensions alone, or an old tier description.

The frozen scope is every non-ranged catalogue entry in Epic Fight's primary neutral grounded
mainhand combo, including native two-handed style when required. Optional dual variants, dash,
air, mount, skills, charges, casts and throws are excluded. Native WoM weapons and vanilla-style
mining-mode balance are outside this pass. Butcher Knife's Axe form must be measured like the
rest; its current 105 tier does not predetermine its replacement.

Measure each strike's greatest connecting initial horizontal attacker-to-target **center**
distance at 0.01-block resolution, confirming boundaries with repeated trials. Use one flat scene,
fixed-size stationary target, starting orientation and identical unupgraded material/loadout
conditions. Reset positions for strike-distance trials and prevent knockback contamination;
include native animation displacement. Record target dimensions, capability/preset, style,
strike identities, innate reach modifiers and exact candidate/input identities. Retain raw trial
evidence in classified generated outputs; compact approved inputs/provenance belong in the
catalogue only after measurement. Runtime measurement remains separately authorized under
[testing.md](testing.md).

Freeze each strike's pre-retuning, pre-mitigation damage contribution, summing same-strike damage
events but excluding AOE damage to other targets. Effective reach is
`R = sum(strike_reach * frozen_damage_weight) / sum(frozen_damage_weight)`.
For measured Sword reach `S` and the frozen roster's endpoints `Rmin`/`Rmax`:

- Below Sword: `tier = 100 + 20 * (S - R) / (S - Rmin)`.
- Above Sword: `tier = 100 - 20 * (R - S) / (Rmax - S)`.
- Equal to Sword: tier 100. Equal reach always gives equal tiers.

Clamp to 80–120 and round half up to six decimals; do not introduce tier bands. An absent side
of Sword is unused; if all reaches equal Sword, every tier is 100. Future roster additions require
explicit endpoint recalibration. The digest format and frozen roster identity are in the catalogue.

On activation, **melee only** restores scale 1.0: tier 100 means 100% of the former same-material
Sword baseline. Ordinary positive-head multipliers become
`(tier / 100) * 1.6 / (head_scale * speed)`, rounded to six decimals. Pierce, Estoc bypass and
zero-head Chisel use the separate source-backed scalar models below; generic armor effects change
the required raw multiplier, not the reach-selected tier. Do not apply the ordinary formula to
those special models.

Preserve native speeds, head/material scaling, capabilities, colliders and animation mappings.
Ranged retains its ×0.9 scale and current targets. Explicit alternate-mode budgets also remain;
any alternate attack inheriting melee damage needs a separately verified compensating coefficient
before activation. Preserve the Minotaur Axe signature exception. Missing/inconsistent measurements,
unresolved special models or alternate-budget coupling block publishing the recalibration, not
just one item's row. Do not partially restore the melee baseline.

`TconReachNormalization` supplies offline weighted-reach, exact-roster/input validation,
Sword-anchored tier mapping and ordinary-model arithmetic. `TconReachNormalizationTest` covers
synthetic mathematics and authored legacy resource agreement; neither establishes measured reach,
activated retuning or candidate-matched native acceptance.

#### Specialized scalar solving and remaining native boundaries

`TconSpecialDamage` implements armor-weighted monotonic inversion using the catalogue's existing
trash/elite/boss weights, armor and toughness. It solves a selected target tier without needing
reach measurements; selecting the actual tier still requires them. `TconSpecialDamageTest` checks
synthetic round trips across 80–120, the retained armor profile and checksum-pinned TCon
3.11.2.166 bytecode/resources (prepared automatically by the existing Dev native-input scanner).
It does not load native game classes.

For pre-innate material/base damage `B`, chassis multiplier `m` and innate level `L`, under a
neutral full-cooldown noncritical single-hit model:

- **Pierce:** main damage `(B + 0.5*L)*m`; armor becomes `max(0, armor - 2*L)`.
  The pinned `pierce.json` adds 0.5 damage per level and applies effect level `2*L` before melee.
  `MobEffectModule.applyEffect` subtracts one for the amplifier; `TinkerEffects`' pierce supplier
  applies -1 armor per amplifier-plus-one. `FloatToolStat.build` multiplies the additive bonus
  along with base damage.
- **Estoc legacy Piercing:** main damage `(B - 0.5*L)*m`, plus armor-bypassing secondary
  damage `L*m`. The pinned `PiercingModifier.addToolStats/afterMeleeHit` and damage-type tag
  establish these operations. Cooldown/crit scaling is held neutral here. Secondary-hit delivery,
  invulnerability interaction and Epic Fight hook execution still require native observation.
- **Fixed Chisel:** main damage `B*m`, with `B=1` from its material-independent authored definition;
  solve directly against the same armor-weighted Sword target rather than dividing by zero head scale.

The old catalogue coefficients **do not reproduce these native Pierce/Estoc models**. Its Pierce
numbers reproduce a bonus outside `m` with reductions 3/5; the native resource/stat path puts the
bonus inside `m` with reductions 2/4. Estoc's old value similarly corresponds to subtracting 0.5
outside `m`. These are diagnosed model discrepancies, not runtime observations or permission to
silently change production coefficients. The existing Chisel coefficient does reproduce its
legacy ×0.9 scalar target.

The scalar inversion uses vanilla armor-only absorption. Material traits, upgrades, enchantments,
resistance and Epic Fight's per-strike damage/armor-negation routing are not asserted by it. Before
activation, bind the measured loadout and actual native integration path, verify those hook/strike
semantics and reconcile the corrected special-model results with that candidate. That boundary
is **not** blocked by missing reach arithmetic; it is blocked by missing integration evidence.

#### Alternate-mode preservation

For a proven isolated raw-damage path `D = c*m*B`, preserving it after `m_old → m_new` requires
`c_new = c_old*m_old/m_new`. `preservingAlternateCoefficient` implements this necessary relationship;
contracts verify it across material bases and charge factors, before armor absorption. It does not
claim every arbitrary modifier/attribute build follows this linear path.

Pinned native inspection distinguishes the actual alternate owners:

- Blockram, Buckler, Fuma Shuriken and Javelin use `ThrownTool`: its hit context reads
  `ATTACK_DAMAGE` and multiplies by the stored launch multiplier. `ThrowingModule` takes that
  multiplier from **velocity**, which also changes trajectory. Do not compensate by altering velocity.
- Battlesign's native `BonkingModifier.getMeleeDamage` multiplies by a hard-coded 1.5 and its
  charged hit uses tool/player attributes. It is coupled to melee damage, not an independent JSON
  damage budget.
- War Pick is a native `ModifiableCrossbowItem`: its projectile path reads `PROJECTILE_DAMAGE`,
  not `ATTACK_DAMAGE`; War Charge's conditional formula changes velocity. A pure melee multiplier
  change therefore does not call for an invented projectile-damage compensation. Native integration
  still needs candidate-matched verification.

There is currently **no installed item-scoped damage-only compensation hook** for the coupled
throw/Bonk paths. The relationship is verified, but installing a suitable pre-mitigation hook and
checking it with the actual candidate remains an explicit activation prerequisite, independent of
reach measurement. No alternate coefficients, velocities, target labels or current melee damage
have been changed. Contracts freeze all six alternate target labels and existing chassis values;
that preserves current inputs, not proof that the old alternate targets were achieved in-game.
The Minotaur Axe +7 sprint signature exception remains untouched.

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
form and any material alternate mode, notes gathering utility without implying weaker combat, and
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

Orbit, Nova and Blackstar are carried by native Saulomonks. Better Compat Fixes'
version-gated WoM 2.0.171 hook changes their spawn-distance admission to strictly
more than **2,000 horizontal blocks from the nearest living, nonspectating player's
permanent Better Spawns onboarding point**, not world origin. The nearest player
at the attempted mob position supplies the anchor; missing, malformed or
other-dimension points deny admission rather than borrowing another player's point.
Desert biome injection, sand, difficulty and native mob-spawn checks are unchanged,
as are spawn weight, equipment and drops. This does not remove existing mobs.
The owning mod's `verifyFull` tests the transformed native predicate; the pack's
`SaulomonkSpawnContractTest` checks the onboarding key and upstream-pin boundary.
Source validation does not imply deployment or full-pack natural-spawn acceptance.

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

Combat Roll is removed. Better Compat Fixes requests ParCool's native dodge when a physical arrow key is double-tapped within seven client ticks while holding physical Left Shift and not crouching. Releasing Left Shift clears pending taps; Right Shift does not enable this shortcut. WASD, movement-key rebinding and vanilla forward double-tap sprint are unchanged. ParCool retains its native stamina, cooldown and action restrictions; its direct dodge binding can be assigned in Controls but is unbound in pack defaults. Independent arrow requests bypass only Epic ParCool's direct-key collision guard (including the false collision between two unbound bindings with an equipped Epic Fight dodge skill); Epic Fight's stamina check remains intact. Arrow requests also supply Epic ParCool's native roll-animation selector with the same directional impulses as the ParCool dodge request, rather than unrelated WASD input; vanilla key state and native animation/stamina handling are unchanged. Real-client acceptance remains pending. Epic Fight's native Roll and Step progression remains separate and uses Epic Fight's dedicated input (unbound in pack defaults). The Epic Fight combat camera is active in third-person only; it never changes perspective automatically.

Returning Blockram, Buckler, and Fuma Shuriken throws use the separate 100-tier throw target. Javelin throws stay at 105 per hit. Battlesign's charged Bonk and War Pick's charge projectile target 100. The Minotaur Axe sprint charge remains the one explicit signature exception.

The repository's supported full-depth evaluation is the explicitly ordered `./test.main.kts debug` tier.
