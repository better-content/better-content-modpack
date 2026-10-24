# Content ownership

The modpack owns cross-mod progression policy, six Machine Blocks, genuinely
cross-mod transition items, exact era-root recipes, optional transport gates,
learning surfaces, and player documentation. Owning mods keep lifecycle and
runtime logic.

| System | Owner | Pack boundary |
| --- | --- | --- |
| Deposits, chunks, samples, Sifting/Spouting/pressure processing, canonical outputs | Realistic Ores | Physical and magical routes converge on shared products; no duplicate ore registration |
| Ordinary Overworld terrain constituent recovery | Pack KubeJS | Explicit `kubejs/config/terrain_recovery.json` profiles drive exact-input Crushing Wheel and wet Brass Sifting recipes; existing products only, nominal 25% physical recovery, no metals or precious gems; quartz is allowed as a bulk mineral. See [terrain recovery](realistic_ore_processing.md#ordinary-overworld-terrain-recovery). |
| Radioactive profiles, disturbance persistence, emissions | Latent ChemLib | Provides progression recipes; the mod owns physical behavior and its gameplay events |
| Heat storage/transport and Create Boiler Heater | Heat Sync | Provides era placement; native UI and hover own operating facts |
| Dimension Font obelisks, travel sessions, charge, and arrival sites | Dimension Drink | Pack recipes and learning surfaces may point to Fonts; the mod owns neutral charge generation/drain, session lifecycle, chunk tickets, and destination placement |
| Blood Altar bootstrap and tier reference | Blood Magic | Ordinary materials open the altar; a Heart Block made with one final-death fragment supplies 1 LP/t per installed fragment beside it. Blood Magic's in-game guide remains the authoritative Tier 1-5 multiblock reference. |
| Occult physical components | Hexerei | The mixing cauldron uses iron and a vanilla cauldron; impure chalk requires Aether, Nether, and Bumblezone trophies equally. Occultism retains rituals, spirit fire, bindings, servants, and the finite dimensional matrix |
| Occult storage | None | Controllers, stabilizers, remotes, satchel, wormholes, mineshaft, and miners are closed; finite AE2 storage and visible Font-rooted logistics remain |
| Vanilla boat durability, vessel-drop suppression, reinforced recipes | Better Content Fixes | No boat mutation or hiding scripts |
| Player-kill spirit release, spirit commerce, eight profession identities, resident barter, and wandering-trader lifecycle | Better Spirit Commerce | The standalone mod owns credited-kill release through Malum's animated spirit entities, eight matching stations/outfits/behaviours, a 280-row profession catalogue, eight 36-row wandering pools (288 authored rows), thirteen core offers selected per visit, 48 Plague Doctor oddities, and matching villager-egg offers. Village residents use native barter rather than the fixed profession catalogue; coins, emerald commerce, and eldritch/umbral trade are closed |
| Drinking-water quality | Better Drinking Water / Thirst | Dirty (0) or purified (3); legacy intermediate tiers count as dirty. Supported vessels share prepaid fractional sipping; sneak-empty-hand drinking draws from approved cauldrons/tanks. Equipping does not purify. Boiling and configured kettle/sand-filter treatment purify finite supplies; native bucket/fluid transfers preserve quality. Exact support and exclusions live in the [owning source](../../mod_source/better-drinking-water/README.md). |
| Fractional beverage sipping | Better Drinking Water / Better Survival Physiology | The native Drinks slot accepts the owning mod's explicit beverage catalogue. Paid fractions release hydration, nutrition, Nectar/Booze and timed effects without replaying whole-drink consumption. Accepted items say “Drinks slot compatible”; special-action/manual-only exclusions live in the [owning source](../../mod_source/better-drinking-water/README.md#beverage-catalogue-and-fractional-effects). |
| Wild fruit trees and berry fields | Better Wild Fruit Trees | Fixed-location forage, mature harvest/regrowth, propagation restrictions and diamond-tier direct player removal; non-bush fruit plants and ordinary trees remain native. See the [owning source](../../mod_source/better-wild-fruit-trees/README.md). |
| Rotten food reuse and chopping feedback | Better Content Fixes | Rotten Produce enters native composting; Rotten Meat is basic Starcatcher bait. Cut-face wood particle rate increases linearly with completion. Source/native checks are not audiovisual acceptance. |
| Regional pillager familiarity | Better Pillager Campaigns | New campaign work requires the target player's accumulated local Survival presence, not world age; physical enemies retain native pursuit. See the [owning source](../../mod_source/better-pillager-campaigns/README.md). |
| TCon alloy composition and casting | Tinkers' Construct | Removes bypasses and authors exact cross-mod alloy recipes |
| Celestial events | Better Celestial Events | Pack authors habitat tags; Font and Commerce owners implement free travel and fair visits. See [Celestial events](celestial-events.md). |
| Kinetic assembly | Create | Independent iron workshop; category-specific Skyroot power, Nether refinement and Certus routing. Full Machine Blocks only in demonstrated structural housings, not compact devices |
| Seared entry | Pack progression and Unearthed | Two distinct underground rock families make Refractory Compound; raw/ordinary cobbled whitelist, no Font grout or geological synthesis shortcuts |
| Structural assemblies | Pack progression | Six stable Machine Block IDs; first three display as Machine/Toughened/Precision. One Space block per Air Liquefier; other current apparatus roots use fitted era parts. No saved-machine migration |
| Acid and gas chemistry | ChemLib, PneumaticCraft, Create, Better Content Fixes | Pack authors canonical acid routes and renewable acetic uses; airtight machine upgrades retain gas-state ChemLib contents |
| Electrical components and stationary generation | PowerGrid and MoreRed | Generator Housing uses plates, conductive casings, wire and an Electron Tube at 2 bar; the Soldering Table uses a printed PCB. Neither consumes an Electrical Machine Block; competing stationary generators are cut |
| Dragon ecology and materials | Ice and Fire | Replaces vanilla dragon-boss products; rare Overworld ecology supplies tagged products and equal-stat Dragonsteel TCon materials |
| Aerospace components | Creating Space | One Space Machine Block fits each Air Liquefier; Rocket Engineer Table and Mechanical Electrolyzer instead use fitted components plus an integrated circuit at 4.5 bar |

## Stable pack IDs

- `kubejs:andesite_machine_block`
- `kubejs:copper_machine_block`
- `kubejs:brass_machine_block`
- `kubejs:airtight_machine_block`
- `kubejs:electrical_machine_block`
- `kubejs:space_machine_block`

These are clean-break identities. Old `*_machine_casing`, seared/scorched,
circuited, Raw Impossible, and Impossible casing IDs are not part of the public
six-era graph and receive no aliases.

## KubeJS layout

Startup scripts register only the stable Machine Blocks and still-required
pack transition items. Server scripts are grouped by progression era,
transport, compatibility, utility, and narrow removal policy. Mandatory recipes
use exact installed IDs; optional addon recipes use explicit mod-loaded guards.
Ordinary integration scripts do not scan arbitrary recipe JSON, classify namespaces,
or silently skip mandatory roots. The narrow crafting-policy contract is an explicit
exception: its startup classifier checks loaded namespaces, and its final recipe
report inspects recipe JSON for named cut-family leaks and consumers. This does not
authorize a general audit framework; see [Crafting graph policy](crafting_policy.md).

## Structure chest loot

Owning loot tables and their explicit pools/injections define reward quantities
and themes. There is no global chest-quantity normalizer, bundle budget, inferred
supply/encounter/treasure role, or compensation for sparse or empty rolls.
Surviving stacks retain their quantities, grouping and item data rather than
being aggregated and reconstructed. This removes the former normalization
callback's invalid zero-argument KubeJS `hasTag()` call as well as its broad scope.

Native LootJS `CHEST` context is not proof of a physical container: a mob such as
Quark's Stoneling can use that context for its carried reward. Quantity balancing
selects explicit owning tables/pools, accounts for their consumers, and adjusts
rolls, weights or counts there. Shared supply pools may be referenced explicitly
by intended containers; helper tables, mob rewards and future table names must
not acquire generic supplies by default.

The table-owned balance inputs are
[`structure-loot-review.json`](../src/test/resources/structure-loot-review.json).
They distinguish physical consumer witnesses and authored supply/encounter/treasure
roles from helpers, packages, mob rewards, intentional empty/cut tables and unresolved
consumers. Roles are review decisions, not a production folder-name classifier.
Selected `kubejs/data/*/loot_tables/` overrides tune commodity counts/rolls in Alex's
Caves cabins, vanilla supply/dungeon tables, Rats pet shops and Wares warehouses.
Special pools, conditions, item identities and item state remain unchanged.
Reviewed-but-unchanged tables are intentional outcomes; missing/uncertain consumers
must be resolved against active native configuration before claiming full coverage.

Existing acquisition policy remains: `global_loot_progression.js` removes closed
families and potion delivery, and removes listed renewable seeds from native
`CHEST` contexts regardless of table-folder convention. That seed policy still
applies to non-container callers using CHEST parameters; retiring quantity
normalization does not change it. `ore_raw_material_loot.js` retains geological
replacements, and existing targeted reward additions remain unchanged. Filtering
may leave an empty result; it does not trigger a top-up or restore cut items.

Prefilled structure inventories and already-rolled Lootr personal inventories
are not rewritten. This change affects future rolls only; it does not repair or
rebalance saved rewards or change Lootr's texture preference.

`ChestLootNormalizationTest` guards the retirement of the blanket normalizer and
executes the remaining production filters/replacements with minimal collection
and stack stand-ins. It checks empty and fully filtered rolls, CHEST-context mob
and helper consumers, unchanged large/duplicate stacks, preserved item state,
chest-only seed removal and geological replacement quantities. The stack stand-in
rejects zero-argument `hasTag()` rather than inventing that API. These hermetic
Minecraft-free checks are not full-pack runtime acceptance.

`StructureLootBalanceTest` additionally rehydrates pinned source inputs through
[`structure_loot_inputs.py`](../scripts/structure_loot_inputs.py), checks review/input
identity and effective authored Forge modifiers, compares protected table structure,
and exercises negative mutations plus seeded distributions for changed pools.
The closed unused-definition scan's v2 identity hashes every sorted class/data entry name and byte
from all current archives, not ZIP timestamps/order or unsearched `META-INF` source provenance.
Pinned upstream archive integrity and exact owning class/template witnesses remain independently
required. All current class/data bytes are still searched; changed/added/removed consumer bytes and
duplicate scanned entries fail closed. This avoids a self-invalidating archive-header fingerprint
when fresh custom JARs are stamped, without ignoring a new consumer or accepting historical scan
output. The owning controls cover metadata-only rebuilds and changed byte/name/scope negatives.
The sample profile and tolerances live in the executable review inputs; the Python
sampler does not implement native GLMs, LootJS, conditional pools or container
splitting. Full-pack native rolls, actual structure consumers and Lootr personal-roll
behavior remain separate required acceptance checks, not conclusions from a source
review or seeded sampler.

## Tinkers' wood material

Wood cannot supply tool heads. The override at
`kubejs/data/tconstruct/tinkering/materials/stats/wood.json` explicitly sets
`tconstruct:head` to `null`: TConstruct merges material stats across data packs,
so merely omitting that entry preserves the upstream head stats. Wood remains
available for handles, bindings, grips, arrow shafts, bow limbs and shield cores.

## Tinkers' tool visibility

`kubejs/client_scripts/policy/hide_completed_tcon_tools.js` hides completed multipart
station/anvil tools from both EMI and JEI. It selects `tconstruct:modifiable/multipart`
minus `multipart/single` and wearable armor, not the broad `modifiable` tag. Direct
crafts such as Flint and Brick and slime staffs, single-part shields, wearable armor,
parts, materials, modifiers and recipes remain visible. This is viewer filtering,
not recipe removal.

## Item-hover annotations

Better Content owns one pack-authored item-hover annotation surface. Concise,
item-local pack facts appear through the normal tooltip pipeline, so EMI hover
and ordinary inventory hover show the same annotation. Static records live in
`kubejs/config/hover_annotations.json`; large stable families may instead be
generated from an existing authoritative config, such as formal glyph origins
or the TConstruct/Epic Fight handling catalogue.

The registry uses `bc.hover_annotations.v2`. Every static record has a stable
`concept_id` shared by equivalent item-local explanations, plus the owning
domain and authoritative implementation path. Hover copy stays the concise
local correction while Threads and loading briefs teach the broader model.

Annotations cover every pack-owned transition family and every curriculum-important
system with a natural item anchor when the native hover does not explain the pack's
model. An existing native tooltip counts as coverage when it already teaches that
model; the pack does not add a duplicate merely to claim ownership.

Copy may identify a capability root, lifecycle, process authority, requirement,
or another useful role beyond correcting an outright misconception. It still does
not repeat ingredients, layouts, attributes, or ordinary uses that EMI and native
tooltips already communicate. Dynamic stack state remains owned by the source mod.
World events, controls, onboarding, scouting, and other guidance without a natural
item anchor remain on their event, HUD, loading-lesson, Thread, or world surfaces.

## Learning surface hierarchy

Better Content uses several cooperating learning surfaces rather than a single
progression guide:

| Surface | Authority |
| --- | --- |
| Loading lessons and the Threads Lessons reference | Spoiler-free fundamentals and changed mental models that should be available before the relevant event occurs |
| Contextual Thread cards | Discovering a broader rule through authoritative play evidence, preserving it across the lineage, and handing off to a precise deeper surface |
| Item-hover annotations | One- or two-line corrections tied naturally to a specific stack, identical in inventory and EMI hover |
| EMI, Ponder, and native GuideME/Patchouli or system screens | Recipes, apparatus, multiblocks, exact operating instructions, and dynamic state owned by the implementing mod |
| HUD, event, and world feedback | Immediate controls, warnings, scouting, rescue, and other guidance whose meaning depends on the live situation |

Shared concepts align equivalent explanations, but each surface keeps its own
depth. A loading lesson may lead to a known Thread and its native doorway; it
does not reveal an unknown card. Hover annotations stay concise and never grow
into lesson prose merely to reproduce another surface.

Authoring and review follow [Learning surfaces](better_discovery_guides.md). There is no
active achievement ledger or replacement reward system.

## Spirit commerce

Wandering spirit traders and economy-driven villages are **separate systems**, even though Better
Spirit Commerce implements both. Wandering traders sell authored goods for matching spirits at
authored prices and stock limits; their offers do not depend on resident supplies, needs,
workstations, customer-supplied crafting ingredients or commissions. Village residents instead
barter real goods through their own local economy. See the
[owning gameplay boundary](../../mod_source/better-spirit-commerce/docs/commerce-systems.md).

The eight village professions correspond to sacred, wicked, arcane, aerial,
aqueous, earthen, infernal, and Tempo spirits. Each has a
matching coloured workstation, outfit, local utility behaviour, and 35-offer
catalogue priced only in its own spirit at 1–8. These are catalogue and identity
definitions, not the current village-resident trading interface: fixed profession
catalogues and profession conversion are disabled for village villagers, who use
personal supplies and nearby recipes through the native barter journal. Eldritch and umbral spirits never appear in commerce.

Wandering traders use the same eight identities, colours, and matching payment
spirits. Each identity has a 36-row authored pool. A visit deterministically selects four
fixed staples, four supplies, three equipment offers, one standout, and its matching campfire:
thirteen core offers. The campfire costs one corresponding commerce spirit. The existing
one-use offer of two matching spirits for two villager eggs and optional Font-map offer are
additional. Selection is bound to the trader UUID and theme, not rerolled by reopening,
saving/reloading, or foreign-offer repair. The standalone `bc.wandering_pool.v1` resource owns
these wandering rows; the legacy profession catalogue and Plague Doctor oddities remain separate.

Finite stock remains world-global where already specified: the original 22 commodity ceilings
(total 64) are unchanged. New imports have explicit finite ceilings consumed only by spirit-paid
merchant sales, including any matching Plague Doctor sale; they do not debit resident barter.
Stock schema 3 preserves existing balances and zeroes during v1/v2 migration. Missing known
keys on a v3 reload fail closed rather than replenishing stock.

Player-credited kills immediately release Better Content spirits at the
dying mob through Malum's floating, homing item entities. Malum-native mappings remain authoritative, including mapped passive
animals such as cows, pigs, sheep, and chickens. Only the unmapped fallback is
hostile-only: it deterministically releases two ordinary spirits. Spawner-origin
mobs, already-soulless victims, and economy actors release none. The seven ordinary
Better Content spirits replace Malum's ordinary spirit items in recipes and other
pack uses; Eldritch and Umbral remain Malum items. Currency stacks to 64 without
delivery receipts. The native Spirit Pouch is
the sole supported specialist storage surface. Create Deco coins, coin stacks,
wallets, coin recipes, emerald-priced offers, Wares/Font adapters, and specialist
harvesting tools are inert or hidden.

### Ordinary trader arrival

A non-spectator Overworld player qualifies when both `doTraderSpawning` and `doMobSpawning`
gamerules and Commerce's recurring visits are enabled. No spirit-funds or nearby-camp gate remains;
native safe placement in loaded terrain still applies. A world-wide saved schedule rolls 2.5 to
4.5 elapsed game-time days (60,000–108,000 ticks), initially from the first eligible check and
then from each successful arrival. Visits can overlap. Each trader stays until its offers run out
or seven elapsed game-time days (168,000 ticks) after arrival, with no deadline renewal on reload.
Departure does not change the next arrival. Failed placements retry every 1,200 ticks without
rerolling the multi-day wait. Already-saved arrival deadlines remain unchanged; shorter waits apply to new rolls.
Schedule and retry deadlines survive saves/restarts; sleeping and
`/time` do not accelerate these clocks. Legacy inactive schedules start a fresh initial wait;
existing camps keep their saved expiry. Expired unloaded records and later-loaded scenery retire
without forcing chunks; a failed campsite rolls back only its newly created trader/untamed owned
companions and restores the native saved trader identity.

Selection uses the anchor player's recent actual pickup history 85% of the time
(square-root-dampened totals over the current and previous nine game-time days). The 15% surprise
branch chooses uniformly among **all eight identities**, not only currently affordable markets;
no history also means uniform selection. Spending and storage do not erase pickup history, and
admission does not require matching currency for the selected market. Other-dimension pickups
share the Overworld game-time clock, unaffected by sleeping/daytime commands. Spirit Fair retains
its independent celestial occurrence contract.
See the [owning contract](../../mod_source/better-spirit-commerce/README.md#ordinary-trader-arrival).

### Spirit campfires

Each wandering trader's campfire matches its spirit-market palette. The campfire is sold only
by the matching trader, for one corresponding spirit; there is no crafting recipe. Purchased
fires are player-placeable and recoverable. Trader-owned scenery has persisted ownership,
produces no item drops and is retired on camp relocation/departure, without touching purchased
fires or player replacement blocks. Cleanup never force-loads old terrain.

Commerce owns the blocks, flames, standalone smoke fallback and lifecycle. Pack emitter definitions
under `config/adchimneys/Emitters/better_spirit_commerce$*_campfire*.cfg` integrate the new variants
with Advanced Chimneys' colored replacement smoke. Ordinary normal/soul campfires are unchanged.
The source integration does not add another wind force on top of ForgeEndertech and Weather2;
combined wind/shader appearance remains a full-stack client acceptance concern. Details and
source checks belong to [Spirit Commerce](../../mod_source/better-spirit-commerce/README.md).

## Forage, chopping and rotten food

Finite wild vegetable and rice patches use pack-owned data under
`kubejs/data/kubejs/worldgen/{configured_feature,placed_feature}/forage/` and the matching
`forge/biome_modifier/forage/` owners. The seven native Farmer's Delight placements remain
suppressed. Better Bumblezone Crops owns food-only harvest filtering, non-propagation and
actual-produce discovery events; [food progression](food_progression.md) owns their placement
parameters and the distinction between wild food and imported planting stock. These patches
are generated only in new Overworld chunks, not renewed or retrofitted.

Fixed-location berry fields include sweet berry, Fruits Delight blueberry and cranberry, and
optional Ars Nouveau sourceberry bushes. New decoration targets 30–42 sparsely spaced plants
within a 47×47 footprint, limited by terrain; existing chunks are not backfilled. Fields are
centered on their decoration chunk to keep generation writes within its 3×3 chunk neighborhood. Bush foliage
stays mature across native picking and regrowth, with each plant's native food yield. Direct
planting and berry-to-pot propagation are closed. Direct normal-player removal of these bushes
and protected fruit trees needs a registered diamond-or-better axe tier. Tinkers' axes use their
native per-stack harvest tier, including upgrades, rather than their dummy superclass tier;
broken or uninitialized tools are denied. Machine/FakePlayer mining is exempt. Indirect soil/hoe/fluid removal remains native. Commands, world editing and writes that
bypass normal placement events are not a propagation guarantee. Protected tree classification is
loaded-only and bounded, failing open when a fruit root is unavailable; ordinary trees stay native.

Natural regrowth is paced independently of this size: a mean 36-bush field targets roughly
40 berries per 50,000 active game ticks (about 33–47 for 30–42 plants), with species-specific
regrowth chances preserving native picking yields. Productive protected trees target roughly
160 fruit over the same interval: eight-item harvests remain, but successful natural fruit
replacements spend a root-owned allowance of one fruit block per 2,500 ticks, banked to at most
two replacements. Failed geometry does not spend that allowance. Native root data preserves
credit, while chunk reloads do not accrue unloaded time. These are renewable-production targets,
not guarantees under seasonal suppression, inactive chunks or poor geometry; initial ripe stock,
bonemeal and ordinary crops retain their separate behavior. See the owning mod for rate assumptions.

Fruit trees retain native generated size and fertility. Wood growth uses constant per-species
rates calibrated to roughly 30 extended days from fresh native geometry (1,500,000 active ticks,
about 20h50m at 20 TPS), using the native full-trunk radius as the adult-size reference. Full-size
energy and growth-only tapering apply from the first pulse: there is no age ledger, changing
size ceiling, day-thirty event or automatic fertility supply. Canopy filling can continue naturally;
soil, light, seasons, clearance and explicit boosts affect actual development. The reference assumes
randomTickSpeed 3/global DT growthMultiplier 0.5, not a universal physical maximum/deadline.
Ordinary-tree rates, fruit renewal and rarity are unchanged; see the owning mod's calibration.

New discoveries are deliberately very rare: field attempts pass an additional 1-in-128 roll
following native biome/frequency filters (a usual 1-in-32 rule becomes 1-in-4,096 eligible chunks
per berry type). Protected weighted fruit-tree choices pass a 1-in-256 roll; a rejected fruit choice
returns native unhandled so ordinary biome trees can still be selected. Native preferences,
global tree density/chance and JoCodes remain; existing resources are not culled. These rolls
permit clusters and do not guarantee minimum spacing or measured landscape density.

**Fruit extension status — source only, not ready:** the
[owning source draft](https://github.com/better-content/better-wild-fruit-trees/blob/4bb19c8f0b665fe7299f47c1edbd83cc556f87bd/README.md#optional-food-tree-extension--source-only-not-ready)
has uncompiled/unexecuted full-policy work for `dynamictreesplus:saguaro_cactus`,
`dtnatures_spirit:coconut` and `dtquark:ancient`, including exact-origin saguaro→mega
provenance rather than treating all mega cacti as fruit trees. Food/product routes are preserved
separately from propagation, including placeable coconut processing without germination. Four
independent native growth references are still missing; the cactus structural adult-reference
criterion is unresolved, and native/client/fixture gates remain unrun. No matching runtime JAR
was built or deployed. The existing thirteen-species descriptions above do not establish that
these three additions are active or accepted; ordinary cacti, cocoa, archwood pods and Aether
trees are not new policy targets.

Chopping feedback is a time-integrated rate: `2 + 16 × completion` material chips per second,
sampled on native mining ticks with fractional carry, not a fixed chip budget per block or a
render-frame budget. Chips originate at the cut; stopping, changing targets/material or rolling
progress back resets the accumulator. Ordinary logs and Dynamic Trees are covered; native
completion debris and unrelated blocks are unchanged.

Rotten Produce uses vanilla composting at a 65% layer chance toward bone meal. Rotten Meat is
additive basic Starcatcher bait, retaining its native rod menu, lure-time benefit and bobber-owned
consumption. These display names retain the `better_industrial_heat:spoiled_produce` and
`better_industrial_heat:spoiled_meat` registry IDs. Eating them applies only Poison X for five
seconds. Ordinary stale food applies Thirst IV for 60 seconds; spoiled food applies Thirst VIII
and Malnourishment I for 60 seconds. The owning [Industrial Heat README](../../mod_source/better-industrial-heat/README.md#eating-penalties)
defines the drain scales. [Drinking Water](../../mod_source/better-drinking-water/README.md#quality-and-purification)
owns the two-tier water penalties: dirty water applies Hunger VIII for 60 seconds and
Malnourishment X for 25 seconds, while purified water has no purity penalty. Specialist bait pools and the existing disabled Aquaculture recipe are unchanged.
The Compat source fixture proves native mechanics with explicit stand-ins when Industrial Heat
is absent; actual rotten-item loading and client chopping appearance remain full-stack acceptance
work. Its mechanical gate still reports inherited Farmer's Respite recipe/loot diagnostics and a
Dispenser mixin warning; this is not a clean strict-log pack verdict. See the
[owning checks and limitations](../../mod_source/better-compat-fixes/README.md).

## Regional campaign familiarity

New pillager campaign targeting/materialization requires 36,000 actually observed living Survival
ticks (30 minutes at 20 TPS) accumulated per player and dimension across visits. Nearby presence
has full influence through roughly 128 blocks and smoothly falls to zero at 256; offline time,
world age and another player's history do not count. The bounded occupied-cell ledger preserves
established regions over transient travel but can evict the weakest history at its 1,024-cell cap.
Already physical enemies can still pursue into unfamiliar terrain. Existing progression clocks
and campaign lifetimes are unchanged. Source-native fixtures seed explicit authored familiarity;
such a seed is not evidence of a real elapsed 30-minute session or restart acceptance.

## New-world baseline

The refactor supplies no old-world identity migration. New worlds are the supported baseline
because deposit identities, placed small chunks, disturbed radioactivity and Machine Block
identities participate in saved state. This development environment is not a save-backup service:
worlds/saves are generated data retained by default under
[generated-data.md](policies/generated-data.md); ending a task does not delete them.
