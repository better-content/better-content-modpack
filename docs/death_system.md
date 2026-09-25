# Death, Return, and Tension

This is the pack-wide living description of player death and return. The tracked pack
defines installed mods and configuration; the owning custom-mod repositories define
their mechanics. The tables below distinguish **implemented** behavior from
**conditional** third-party behavior and **proposed** work. A manifest establishes
that a mod is installed; it does not establish that every native feature activates
in this pack. See [the workspace inventory](custom-mod-workspace.md) for custom-mod
identities and [the docs policy](README.md#doc-policy) for evidence precedence.

## Intended experience

**Dying should be thrilling, even when it violently changes the player's task.**
Leaving a prepared base increases the value of reaching safety. Local hazards,
scarce or geographically placed resources, and warned encounter waves add stakes
to the outward journey. Death's Door makes the immediate risk legible while the
player can still act. A final death releases that pressure; the return location,
sound, and particles make the next life feel like a new decision rather than an
obligatory retrieval trip.

This is a design goal and a playtest judgment, not a scalar maintained by a mod.
The pack does not calculate tension from distance to base. Keeping ordinary gear
on death is an explicit policy because a forced corpse run would consume the new
task choice. Consequences can still matter through lost life allocations and XP,
injury history, altered encounter state, and a return to the chosen starting place.
The desired rhythm is **travel and pressure → danger at Death's Door → release on
final death → reinvigoration on respawn**. Its quality must be judged in play,
especially for solo and multiplayer travel, distant deaths, and repeated deaths.

## Implemented lifecycle

1. **Damage and danger.** Vanilla admission rules, armor, absorption, and the
   installed combat and survival mods affect whether damage reaches the player.
   [Downed Player Revival](https://github.com/better-content/downed-player-revival)
   owns bodily injuries. A hit that exhausts positive HP enters Death's Door and
   adds one maim. The player retains ordinary movement, combat, and inventory
   controls. There is no bleed-out timer or required helper. Once at semantic zero,
   another accepted HP-damaging hit either adds one maim or begins final death;
   existing injuries determine the death roll. Healing can restore positive HP
   after the zero-crossing healing lock, while injuries require treatment.
   Explicit special-kill sources, command kill, and the void bypass the Door.
   Revival's current rules say totems do not prevent player death. Its
   [source README](https://github.com/better-content/downed-player-revival)
   owns exact probabilities, treatment, and exceptions.
2. **Death attempt.** A further lethal hit can reach Forge's `LivingDeathEvent`.
   Another handler may cancel that attempt. Revival redirects the
   `ForgeHooks.onLivingDeath` call in `ServerPlayer.die`; an uncanceled return calls
   `RevivalManager.finalDeath`, which posts `InjuryEvent.FinalDeath`. A canceled
   return calls `canceledDeath` and leaves the player alive. `FinalDeath` means
   Forge accepted this death path. It fires before the rest of vanilla death and
   later respawn processing finish; it is not a receipt for every consequence.
   Source: Revival's [server-death redirect](https://github.com/better-content/downed-player-revival/blob/main/src/main/java/com/bettercontent/downedplayerrevival/mixin/ServerDeathMixin.java)
   and [final-death publisher](https://github.com/better-content/downed-player-revival/blob/main/src/main/java/com/bettercontent/downedplayerrevival/RevivalManager.java).
3. **Final death.** Revival clears active bodily state while retaining a death
   recap until respawn. [RPG Stats](https://github.com/better-content/rpg-stats)
   clears the current life's committed allocations and grants its XP-level-based
   Heart Fragment entitlement on a confirmed final death. Other owners react to
   death using their own Forge hooks; their effects are listed below.
4. **Respawn.** [Class Selector](https://github.com/better-content/class-selector)
   returns an onboarded player to the separately locked personal starting
   location, with scripted sound and particle effects. Its personal spawn takes
   priority over ordinary non-forced bed and anchor changes. The native death
   screen remains visible because `do_immediate_respawn=false` in
   [Global Game Rules](../config/globalgamerules-common.toml). The configured
   `spawn_radius=256` applies where no personal spawn governs the return.
   Arena Challenges has a separate match-return handler described below; its
   interaction with Class Selector needs runtime confirmation.

The event boundary is deliberately narrow. The feeling of death tension emerges
from independently owned systems. A death attempt, an accepted death, and a
completed respawn are different lifecycle moments.

## Current pack policy and persistence

| Surface | Current rule | Authority |
| --- | --- | --- |
| Ordinary inventory, armor, hands, and hotbar | Kept on death for every configured damage type; no item-durability loss; no damage-source overrides | [Configurable Death server defaults](../defaultconfigs/configurabledeath-server.toml) |
| Experience | Its module is enabled; `droppedXPPercent=1` and `recoverableXPPercent=0`. This is the configured XP-loss policy, separate from item retention | [Configurable Death server defaults](../defaultconfigs/configurabledeath-server.toml) |
| Vanilla gamerule | `keep_inventory=false`; Global Game Rules reapplies it on world load. The pack relies on Configurable Death for ordinary item retention rather than the vanilla rule | [Global Game Rules](../config/globalgamerules-common.toml) |
| Curios slots | Curios has `keepCurios="DEFAULT"`, which follows the false vanilla gamerule. KeepCuriosInventory's bundled mixin forces the Curios drop decision to keep unless an item is blacklisted; the pack blacklist is empty. The combined runtime outcome still needs an observed final death before promising every slot survives | [Curios](../defaultconfigs/curios-server.toml), [KeepCuriosInventory](../config/keepcuriosinventory-common.toml) |
| Food and hunger | Configurable Death does not retain food level or saturation. Diet's installed `2.1.1` JAR defaults to a 100-percentage-point loss of each nutrient group on death, floored at zero (`deathPenaltyMethod=AMOUNT`); the pack does not pin a Diet server override | [Configurable Death](../defaultconfigs/configurabledeath-server.toml), [Diet manifest](../mods/diet.pw.toml) |
| Health progression | Spice of Life: Carrot Edition starts at five hearts, adds one heart per configured milestone, and retains its eaten-food list through death (`resetOnDeath=false`) | [Sol Carrot server defaults](../defaultconfigs/solcarrot-server.toml) |
| Per-life development | RPG Stats allocations clear on death; its separately stored Auto plan persists so later earned points can follow the plan | [RPG Stats source](https://github.com/better-content/rpg-stats) |
| Arena match exception | Arena Challenges snapshots the player's ordinary inventory, XP, and health before issuing a loaned kit. It restores that snapshot when the match ends, including after a match death; its own respawn handler also attempts to return the player to the arena | [Arena Challenges source](https://github.com/better-content/arena-challenges) |
| Bodily injury | Current-life injuries and treatment history clear on confirmed final death; a recap remains while dead and clears on respawn | [Revival source](https://github.com/better-content/downed-player-revival) |
| Lineage and discoveries | Ordinary death does not reset Threads discovery generations or the World Lifecycle Manager lineage | [Threads](threads.md), [World Lifecycle Manager source](https://github.com/better-content/world-lifecycle-manager) |

`defaultconfigs/` supplies defaults for new worlds. Existing saves can retain
their own server configuration. These rows describe the tracked pack's target,
not a claim about every previously created world.

## Better Content ownership

The following custom mods have a direct or supporting role. Their repositories
remain separate; this document owns the cross-pack explanation, not their runtime
state. Each name links to its owning source repository.

| Owner | Death-system contribution |
| --- | --- |
| [Downed Player Revival](https://github.com/better-content/downed-player-revival) | Damage-to-Death's-Door transition, maim roll, treatment, pressure presentation, final-death acceptance signal, and recap |
| [Class Selector](https://github.com/better-content/class-selector) | Locked personal return point and the sound/particle reinvigoration after respawn |
| [RPG Stats](https://github.com/better-content/rpg-stats) | Per-life allocation loss, persistent Auto plan, and XP-level-derived final-death Heart Fragments |
| [Depth Director](https://github.com/better-content/depth-director) | Underground pressure, warnings, bounded surges, semantic low-health response, and injury-aware pacing; injuries slow buildup without retiring the encounter |
| [Pillager Campaigns](https://github.com/better-content/pillager-campaigns) | Surface scouts and warned multi-wave assaults; Death's Door players remain targets, and the death observer retires a target and starts death grace |
| [Arena Challenges](https://github.com/better-content/arena-challenges) | Bounded duels and trials with loaned kits; player death resolves a match, restores saved inventory and XP, and requests an arena return on respawn |
| [Dimension Drink](https://github.com/better-content/dimension-drink) | Font run ends on final death; actual transport back to the origin is a separate successful-return event |
| [Player Traces](https://github.com/better-content/player-traces) | Records recent pose as a final-death echo and marks respawn in the world; ongoing Door play still generates traces |
| [Better Content Threads](https://github.com/better-content/better-content-threads) | Injury discoveries, final-cause death tips, and a lineage memory that survives ordinary death |
| [Dynamic Survival HUD](https://github.com/better-content/dynamic-survival-hud) | Shows semantic-zero danger and changed injury risk without owning the health or injury calculation |
| [Systemic Salience](https://github.com/better-content/systemic-salience) | Diet, temperature, thirst, and stamina interactions that change survival margins; resets its temporary metabolic state on death clone |
| [Realistic Ores](https://github.com/better-content/realistic-ores) | Geographically distinct finite deposits and dangerous Hotstone; makes travel and return matter |
| [Water Survival](https://github.com/better-content/water-survival) | Water collection and portable drinking choices supporting survival away from base |
| [Better Content Fixes](https://github.com/better-content/better-content-fixes) | Survival and compatibility rules, including interrupting sleep when danger commits; it is not the owner of final death |
| [World Lifecycle Manager](https://github.com/better-content/world-lifecycle-manager) | Persists lineage across ordinary player lives; world succession is a distinct, larger lifecycle |

See [Systemic Salience](systemic_salience.md#pulse-pacing) for director and
campaign pacing, [Realistic Ore Processing](realistic_ore_processing.md) for the
home/echo deposit geography, and [Learning Surfaces](learning_surfaces.md) for
the teaching contract.

## Installed third-party surfaces

**Policy or persistent capability.** [Configurable Death](../mods/configurable-death.pw.toml)
owns ordinary inventory and XP policy; [Global Game Rules](../mods/global-gamerules.pw.toml)
loads the world rules; [Curios](../mods/curios.pw.toml) and
[KeepCuriosInventory](../mods/keepcuriosinventory.pw.toml) affect equipment
retention. [Spice of Life: Carrot Edition](../mods/spice-of-life-carrot-edition.pw.toml)
owns persistent food-diversity hearts. [Diet](../mods/diet.pw.toml) owns ordinary
nutrition, while [Thirst Was Taken](../mods/thirst-was-taken.pw.toml) and
[Cold Sweat](../mods/cold-sweat.pw.toml) add hydration and temperature risk.
[Epic Fight](../mods/epic-fight-mod.pw.toml) changes combat risk and has
`keepSkills=true` in [its pack config](../config/epicfight-common.toml).

**Potential death prevention or native death transactions.** The installed
[Ars Nouveau](../mods/ars-nouveau.pw.toml), [Occultism](../mods/occultism.pw.toml),
[Twilight Forest](../mods/the-twilight-forest.pw.toml),
[Born in Chaos](../mods/born-in-chaos.pw.toml),
[Artifacts](../mods/artifacts.pw.toml), [Goety](../mods/goety.pw.toml), and
[Cataclysm](../mods/cataclysm.pw.toml) expose native saves, totems, charms,
or death-related item behavior. Installation does not establish that these
effects can override Revival's current player-death rule. Treat each as a
conditional compatibility surface; especially do not promise that a totem
rescues a player at Death's Door. [Quark](../mods/quark.pw.toml) has Totem of
Holding enabled, [Twilight Forest](../config/twilightforest-common.toml) has
Keepsake Casket settings, and [Supplementaries](../config/supplementaries-common.toml)
can show a compass-gated death marker. Because ordinary inventory is retained,
none of these is the pack's intended gear-recovery loop.

**Danger, location, and other death-side compatibility.**
[Tinkers' Construct](../mods/tinkers-construct.pw.toml),
[Fallout Wastelands](../mods/fallout-wastelands.pw.toml),
[Protection Pixel](../mods/protection-pixel.pw.toml),
[Timeless and Classics Zero](../mods/timeless-and-classics-zero.pw.toml),
[Pollution of the Realms](../mods/pollution-of-the-realms.pw.toml),
[Ice and Fire](../mods/ice-and-fire-dragons.pw.toml), and
[The Bumblezone](../mods/the-bumblezone.pw.toml) have native hazards,
damage, or death-dependent side effects worth checking against the accepted-death
boundary. [Enhanced AI](../mods/enhanced-ai.pw.toml),
[Farsighted Mobs](../mods/farsighted-mobs-forge.pw.toml),
[In Control](../mods/in-control.pw.toml), [The Deep Void](../mods/the-deep-void.pw.toml),
[Lost Cities](../mods/the-lost-cities.pw.toml), and
[Cataclysm](../mods/cataclysm.pw.toml) affect where and how dangerous encounters
occur. [Biome Spawn Point](../mods/biome-spawn-point.pw.toml),
[Tectonic](../mods/tectonic.pw.toml), [Nature's Spirit](../mods/natures-spirit.pw.toml),
[Large Ore Deposits](../mods/large-ore-deposits.pw.toml), and
[Excavated Variants](../mods/excavated-variants.pw.toml) affect the spatial
context of travel or resources. These are relevant to tension without owning
the player's final-death decision. Exact native side effects and worldgen
interactions remain with their owning mods; the manifest links prove inclusion,
not that every optional feature is active.

## Integration seams and refactor candidates

These are review candidates, not approved behavioral changes or evidence of a
reproduced bug.

| Seam | Current fact | Candidate review |
| --- | --- | --- |
| Accepted death versus event observation | Threads consumes Revival's `InjuryEvent.FinalDeath`. RPG Stats, Player Traces, Dimension Drink, Pillager Campaigns, Depth Director, and Arena Challenges also subscribe to `LivingDeathEvent` at differing priorities; Class Selector refreshes its personal spawn at that hook. Pillager Campaigns, Depth Director, and Arena Challenges do not explicitly inspect `event.isCanceled` in their death subscribers | For each side effect, decide whether it needs the accepted-death signal, can safely observe a possibly canceled attempt, or belongs at respawn. Preserve early XP capture where later handlers can clear it |
| Retention authority | Configurable Death keeps normal gear while the vanilla `keep_inventory` rule is false; Curios and KeepCuriosInventory add their own policies | Observe normal, Curios, and modded inventory slots across representative final deaths before promising complete gear retention |
| Native saves and corpse artifacts | Revival says totems do not prevent player death; several installed mods provide save or death-artifact mechanisms | Record each actual activation path and whether it cancels a death, runs after acceptance, or remains irrelevant when no items drop |
| Teaching and configuration drift | Revival's source now describes active play at Death's Door with no bleed-out, while its tracked common config still contains bleed/revive-era keys. Death tips and other pack copy may describe older treatment or totem behavior | Reconcile player-facing language and identify which config keys are live before editing either mechanics or copy |
| Geography and task choice | Return location and resource geography are independently owned; there is no explicit distance-to-base rule | Judge travel tension and post-respawn choice in play before adding a mechanic or moving repository boundaries |
| Arena versus personal return | Arena Challenges teleports a match loser to the arena during `PlayerRespawnEvent`; Class Selector teleports to the saved personal point at `LOWEST` priority. Arena also restores its pre-match XP and gear after death | Verify which location and audiovisual cue the player actually gets, whether the loaned kit is fully removed, and whether XP restoration is the intended scoped exception to normal death loss |

Keep the domain owners separate while those seams are investigated. A shared
confirmation signal is useful only for consequences that truly require an
accepted death. A central consequence registry or numerical death-tension meter
is not part of the current design.

Creature deaths form a separate economy boundary: Better Content Economy and
Malum handle credited mob-kill spirit release, and the economy explicitly excludes
players as spirit victims. They do not own the player's death/return lifecycle.
