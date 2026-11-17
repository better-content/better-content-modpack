# Selected backlog — source implementation handoff, 2026-11-16

## Scope and qualification

The user approved rows **1–6, 8–24, 26–30** of the existing difficulty triage, including its bounded feature-freeze exceptions. Rows **7 and 25 are excluded**. This document is a source/change-contract handoff, not a second feature backlog or a test suite.

**Not all 28 rows are complete.** Row 3 remains blocked by exact native melting units; row 11 has diagnostics but no verified repair of the reported failure. The other rows have source candidates with the limitations below. Compilation establishes type/build compatibility, not gameplay acceptance. The friends' Lineage world has not started.

Three supervised Pi workers used `openai/gpt-6-sol`. Work was isolated against captured dirty baselines, reviewed, then integrated using baseline-relative patches that reject concurrent edits. Existing dirty/untracked work and the independent testing group's ownership were preserved. No commits, resets, test execution, Minecraft/world launches, screenshots, fixture generators, mod JAR generation, deployment or publication were performed by this group. Release indexes/hashes were not regenerated; these sources are **not an installable release**.

## Per-row disposition

| Row | Source disposition | Important boundary |
| --- | --- | --- |
| 1 | Removed Sleeping Overhaul manifest and obsolete pack configs; removed celestial development runtime dependency. | Celestial restrictions remain. A compile-only dependency remains solely for untouched legacy GameTest sources; their assumptions need later adaptation. |
| 2 | Explicit ordinary-stone roster, one-way 1:1 vanilla stone/cobblestone conversion in `kubejs/server_scripts/compat/stone_conversion.js`. | No broad stone tags, reverse recipes, ore/decorative conversion or recovery loop. Unavailable IDs are skipped. KubeJS runtime unrun. |
| 3 | **Blocked; no direct sample-melting recipe added.** | Existing nine-small-to-full conversion and 40 mB full-chunk yield imply **40/9 mB per small item**. Pinned TCon melts integer mB per consumed item and offers no supported counted melting ingredient. The existing exact bundling route remains; no invented counted JSON or rounding. |
| 4 | Seasonal day fraction clamped to at least `5/9`, with integer ceiling. | Configured cycle length/TPS and seasonal variation retained; night/day ≤0.8. |
| 5 | Campaign warning/arrival messages use GameplayNotices. | Existing timing, recipients and deduplication retained; matched dependency now declared. |
| 6 | Native world-entry contrast mixin uses pale backgrounds and readable dark text with selection/hover/disabled distinctions. | Client rendering and actual injection unverified. No global recoloring. |
| 8 | Bronze and brass reusable casts for the existing 28 supported shapes. | Gold-equivalent costs/pattern rules; material art, tags and recipes. No new shapes/durability. Resource loading unrun. |
| 9 | Central future-placement copper→stone-brick/deepslate-tile mapping covers expedition, village and arrival paths. | Existing sites/geometry/layout versions retained; no world scan or conversion. |
| 10 | Guarded Cold Sweat ambient-only rate attribution candidate. | See calculation below; unknown/local-source conditions retain native rate. Runtime calibration remains unverified. |
| 11 | **Partial:** bounded actionable `PartVariantRecipe` settings-rejection diagnostic. | Ingredient mismatch vs settings vs EMI-transfer cause is not established. Paid ingredients, including prepared sifter Nether brick, are unchanged. Need exact failing grid/item/material/NBT evidence. |
| 12 | RPG available/drafted budget, active/proposed/next effect columns, irreversible-per-life copy and narration. | All eight builds, effect math and draft/commit protocol unchanged; narrow UI unrun. |
| 13 | Four wool armor-slot garments, zero armor/toughness/knockback bonus, leather durability, wool repair and conventional recipes. | Cold Sweat native armor/sewing path; each piece supplies one native wool unit (1.5 cold, 0 heat in pinned config), full set four units. Native sewing interaction unrun. |
| 14 | Six exact Advanced Chimneys IDs added to masonry/glass/refined-ore RBP profiles. | Static RBP block-entity capture/restore inspected; actual falling/landing unrun. Pumps, ducts, vents and unknown functional blocks remain excluded. |
| 15 | Added ordinary Quark cactus block/berry sack and Aquatic Ambitions prismarine-alloy block. | Pinned registration/config/resource inspection, not a loaded-registry measurement. Kept unknown BEs, fake blocks, quarantined coinstacks and deliberate thin/plant exemptions. Used maintained profiles, not a generated-file rewrite. |
| 16 | Deterministic tier-zero basic raiders, native crossbows and stone-grade melee. | No vindicators/advanced armor/random advanced TCon weapons at first tier. Later progression, wave counts, warnings and retreat unchanged. |
| 17 | Read-only preview legality helper checks known native placement/permission/survival restrictions. | **Not universal mod-event prediction.** RotaVision previews main hand; Compat's nonflower offhand prohibition cannot affect that path. Unknown restrictions remain server-owned; no side-effectful event probes. |
| 18 | Per-block-registry-ID orientation memory restored through native synchronization. | Client session only, cleared across world/session changes; no restart persistence. |
| 19 | Pinned EpicParCool fast-run builder suppresses only two equipment-stow callback registrations. | Eligibility, action state, other animations and revert-to-hands remain native. Exact version gate; injection/runtime unverified. |
| 20 | Dedicated knife handler charges three native durability per successfully cut grass/fern block. | Actual swept successes counted; no duplicate charge from generic use path. |
| 21–22 | EMI inventory/menu change refresh and stable available-first partition; explicit informational-display exclusions. | Native relative ordering/category ties retained. Menu transitions, widget/focus behavior and transfer unrun. |
| 23 | Forge spectator pickup veto plus narrow guards for actual Malum natural-spirit and worn Sophisticated magnet bypasses. | Preserve natural spirit from collect-then-discard; keep placed magnets/other carriers native. Exact pinned gates; not a universal ban on arbitrary third-party inventory writes. |
| 24 | Authoritative same-dimension player targets, ≤1 Hz updates, names/direction/distance, edge markers and loaded-entity glow. | Spectator eligibility/dimension changes clear data; 3-second expiry. No teleport, cross-dimensional tracking or chunk loads. |
| 26 | Excavated Variants exposure/model-data parent attachment corrected. | No deposit/morphology/output loss; rendering still needs client validation. |
| 27 | Five shared aspect mappings and removal of grain sequence/grace/burst plus extra fruit sprint speed. | Eight Diet values/deposits/RPG builds remain independent. Canonical specification is [Systemic Salience](world-and-player.md#canonical-identity-matrix). |
| 28 | Existing Death's Door layout owns Scout beside Details; Spawns owns episode validation, public retained-item custody and native respawn-to-scout. | High-risk lifecycle candidate, not runtime-qualified; details below. |
| 29 | Server-authoritative sneaking main-hand native-tool/offhand-input cutting, stable recipe-ID selection and hint. | Native chance/tool/remainder semantics; ordinary overflow drops. External Forge toss cancellation is respected and may prevent overflow delivery after debit; no crash or duplicate fallback. |
| 30 | Canonical rational mixture composition and captured potency in fluid/bottle/food NBT; all supplied valid ingredients used. | Six slots, 1000 mB, 1200 heated ticks; bottles250/doses50/cap250 mB per food unit. NBT transport and serving behavior unrun; see below. |

## Temperature reference and safety boundaries

Pinned Cold Sweat ordinary naked harmful drift is `sign × max(D*R/7, abs(R)/50)`, where `D` is distance outside native habitable bounds and configured `R=1`. Damage begins at body ±100. The minimum harmful native drift is 0.02 per tick: a WORLD-only envelope cannot obtain a 72,000-tick target while remaining outside those bounds. Holding WORLD safe for most of an hour would instead be an unwanted immunity/delay timer.

The candidate adds identity WORLD probes around a classified contiguous ambient prefix and each known local modifier. Only a complete same-tick, verified live layout with **every** local delta zero within `1e-9` may scale RATE. Unknown/interleaved/stale/missing probes, local contributions (even mutually canceling ones), nonzero/unknown BASE, recovery direction or incompatible RATE ordering retain native behavior. Diagnostics are bounded. No WORLD/CORE/BODY setter, chunk loading or exposure clock is added.

For neutral, unprotected, BASE-zero, steady harmful ambient exposure at 20 TPS and no acclimation change:

`k = min(1, (100/72000) / max(D*R/7, abs(R)/50))`.

The candidate multiplies the already native-insulated RATE by `k`; it does not cancel insulation/resistance. Strong detected local sources immediately retain full native RATE. Native body state owns persistence, so disconnect is not an exposure-timer reset. Bounds/BASE read through the API are from the previous completed native tick: transient changes have a one-tick uncertainty. The reference targets about 60 minutes plus native damage-check timing, **not** an unconditional promise for every biome, acclimation, equipment set or unknown modifier. Native modifier list/NBT serialization was statically inspected; no runtime calibration was executed.

## Death/custody contract

- Predeath interception/veto is unchanged from baseline. FinalDeath records episode/location metadata only; native drops finish before Scout can capture retained inventory, armor, offhand and supported Curios/cosmetic slots.
- The existing Death's Door overlay/layout owns native Respawn/Title Screen and measured secondary actions. Narrow widths stack secondary actions. Hints remain on the same measured viewport. Native readiness, hardcore policy, downed exclusion, pending state and stale episode checks remain mandatory.
- Optional common-side bridge version marker prevents old Door installations from loading the new integration. Spawns channel protocol is 6; new packet directions are explicit. Matched client/server builds are required.
- SavedData is the only extractable recovery inventory; cache block entities hold UUID/page pointers. Placement checks the nearest safe height in the death column, then adjacent rings, only in already loaded chunks and without replacing blocks. All pages must be placed before debit. Repeated valid requests are bounded to one attempt per 20 server ticks.
- Retained-slot debit is read back and reconciled against all inventory/Curios slots, including initially empty slots, to prevent callback movement from becoming duplication. A fully verified transfer can proceed through native `handleClientCommand(PERFORM_RESPAWN)` so the connection receives the canonical replacement player. Personal respawn selection remains the new-spawn authority.
- Placement failure consumes no retained items and leaves normal Respawn usable. An unverifiable/partial inventory mutation fails Scout closed for that episode; only proven public credits remain. An opaque, **non-extractable** audit snapshot and failure location/record ID are retained for explicit manual reconciliation rather than erased or automatically restored. No automatic recovery command/UI or second extractable inventory is introduced.
- Normal extraction updates SavedData. Empty resolved records retire; unresolved audit records do not. Cache destruction does not make the block entity the sole inventory owner. Ordinary disconnect/restart state is authored, not exercised. **This is not crash-atomic across Minecraft's independent world/player saves.**

## Sauce transport contract

Composition v2 stores canonical root-equivalent-per-mB fractions and production potency. Splitting fluid changes amount, not concentration. Generic tanks compare tagged stacks; distinct compositions are not silently merged. Named legacy sauces remain readable. Bottles require valid source fluid, not flowing fluid. The generic carrier has no bucket and rejects native Forge world placement because world fluid blocks cannot retain its composition. Its registry identities remain stable; privileged/bypassing block placement is unsupported.

Known in-tree bottle/flask/kettle/spout/food consumers retain or validate NBT. Stripped/malformed generic composition fails closed. This is **not** proof that all Create pipes, hose operations or third-party containers preserve the payload. Those paths must be qualified before deployment. No new mixture buffs or unrelated ingredients were introduced.

## Compile and source checks

Production compile tasks succeeded in isolated manufacturing, geology, discovery, RPG, placement preview, Death's Door, Spawns, seasonal daylight, campaigns (including core compilation without a core JAR), physiology, cooking, spicy farmers, Fonts and celestial events. Compatibility fixes also compiled successfully after both final spectator-bypass mixins and exact pinned version gates were wired.

Only explicit graph-inspected production compile tasks were used, with one compile job at a time in this group. Existing fixture source may be part of a production source set, but no fixture was generated or test executed. Mapping setup/compiler tasks are not gameplay evidence. A first celestial graph request used nonexistent `compileKotlin`; corrected `compileJava` graph/compilation succeeded. Resource checks are syntax parsing only; Gradle-template placeholders are substituted for TOML parsing. Existing test-owned files were left untouched.

Detailed baseline-relative patches, original hashes, application receipts, worker handoffs and compiler logs are under `/home/dev/.tmp/backlog-20261116/`. No candidate binary was installed. Before any later packaging, regenerate the required release metadata through the established process and satisfy the separately authorized release gates.

## Consolidated later qualification — all unrun here

- Exact recipe yields/cast shapes, invalid settings vs ingredient/EMI failures, paid Nether brick and conversion loops; direct sample melting remains blocked.
- Ambient reference versus strong sources, canceling sources, unknown modifiers, recovery/insulation and serialization; four wool pieces/sewing; seasonal bound and deterministic first campaign equipment/notices.
- Actual world-entry theme, all eight RPG cards/draft/commit, EMI inventory/menu transitions and informational exclusions; sprint stowing and orientation session reset.
- Ordinary/narrow death layout, resize/reinit, one Scout button, keyboard/narration, details/summary, reveal/reduced-motion and Discovery hint clipping. No Scout while alive/downed/hardcore/forbidden/absent or old integration.
- Request rejection/double-click/stale packets and native Respawn/Title Screen; post-drop inventory/Curios retention; public extraction/pages, safe placement failure, partial mutation/audit, disconnect/restart, no duplication or retained-item teleportation. Do not infer crash-atomicity.
- Distant same-dimension spectator markers, loaded glow, eligibility removal/expiry; ordinary item pickups, Malum spirits/pouches/commerce, worn item/XP magnets, normal and placed magnets.
- Native cutting recipe predicates, probabilities, chance exhaustion, durability/remainders/overflow/canceled toss; sauce quantities, output atomicity, potency snapshot, exact composition distinctions and NBT through transport, split/stack/servings/consumption.
- RBP chimneys/ordinary additions with state/BE data and failed landing; deliberate exclusions remain unchanged. Ore exposure rendering, eight nutrition/deposit identities, five cues, retired triggers and independent RPG builds.
- Adapt obsolete Sleeping Overhaul, Work Rhythm, old eight-aspect identifiers/palette and death-layout assertions/fixtures **later under the testing owner's process**; do not weaken assertions merely to match this source candidate.

New registry content includes casts, garments, generic sauce and recovery caches. Do not downgrade a world after using these registrations. Deployment, world backups and rollback qualification remain separate work.
