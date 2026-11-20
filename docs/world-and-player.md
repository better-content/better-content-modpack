# World systems and player experience

## Content ownership

The modpack owns cross-mod progression policy, five active Machine Blocks, genuinely
cross-mod transition items, exact era-root recipes, optional transport gates,
learning surfaces, and player documentation. Owning mods keep lifecycle and
runtime logic.

| System | Owner | Pack boundary |
| --- | --- | --- |
| Deposits, chunks, samples, Sifting/Spouting/pressure processing, canonical outputs | Realistic Ores | Physical and magical routes converge on shared products; no duplicate ore registration |
| Radioactive profiles, disturbance persistence, emissions | Latent ChemLib | Provides progression recipes; the mod owns physical behavior and its gameplay events |
| Heat storage/transport and Create Boiler Heater | Heat Sync | Provides era placement; native UI and hover own operating facts |
| Dimension Font obelisks, travel sessions, charge, and arrival sites | Dimension Drink | Pack recipes and learning surfaces may point to Fonts; the mod owns neutral charge generation/drain, session lifecycle, chunk tickets, and destination placement |
| Blood Altar bootstrap and tier reference | Blood Magic | Ordinary materials open the altar; a Heart Block made with one final-death fragment supplies 1 LP/t per installed fragment beside it. Blood Magic's in-game guide remains the authoritative Tier 1-5 multiblock reference. |
| Occult physical components | Hexerei | The mixing cauldron uses iron and a vanilla cauldron; impure chalk requires the three live Font trophies plus finite amethyst. Occultism retains rituals, spirit fire, bindings, servants, and the finite dimensional matrix |
| Occult storage | None | Controllers, stabilizers, remotes, satchel, wormholes, mineshaft, and miners are closed; finite AE2 storage and meteor-certus-rooted logistics remain |
| Vanilla boat durability, vessel-drop suppression, reinforced recipes | Better Content Fixes | No boat mutation or hiding scripts |
| Player-kill spirit release, spirit commerce, village professions, and wandering-trader lifecycle | Better Spirit Commerce | The mod owns credited-kill release through Malum's animated spirit entities, authored spirit-priced villager and wandering offers, matching stations/outfits/behaviours and Local Market; coins, emerald commerce, and eldritch/umbral trade are closed. Resident needs, gathering, settlements, resident barter and Player Stalls were extracted to a private source-only repository and are not active pack content. |
| TCon alloy composition and casting | Tinkers' Construct | Removes bypasses and authors exact cross-mod alloy recipes |
| Kinetic assembly | Create | Uses Machine Blocks only at listed direct roots |
| Pressure, plastic, and PCB processing | PneumaticCraft | Native compressed-air, petroleum/plastic, and PCB etching routes remain; ChemLib acids and gas-containment upgrades are absent |
| Electrical components and stationary generation | PowerGrid and MoreRed | Electrical Block starts PowerGrid's first generator/design roots; competing stationary generators are cut |
| Dragon ecology and materials | Ice and Fire | Replaces vanilla dragon-boss products; rare Overworld ecology supplies tagged products and equal-stat Dragonsteel TCon materials |
| Spaceflight feasibility | Better Spaceflight | Experimental feasibility-only source; no survival flight, oxygen or destination route is claimed |

### Stable pack IDs

- `kubejs:andesite_machine_block`
- `kubejs:copper_machine_block`
- `kubejs:brass_machine_block`
- `kubejs:airtight_machine_block`
- `kubejs:electrical_machine_block`

`kubejs:space_machine_block` is retired with Creating Space; the five IDs above remain active.

These are clean-break identities. Old `*_machine_casing`, seared/scorched,
circuited, Raw Impossible, and Impossible casing IDs are not part of the public
five-era graph and receive no aliases.

### KubeJS layout

Startup scripts register only the stable Machine Blocks and still-required
pack transition items. Server scripts are grouped by progression era,
transport, compatibility, utility, and narrow removal policy. Mandatory recipes
use exact installed IDs; optional addon recipes use explicit mod-loaded guards.
Ordinary integration scripts do not scan arbitrary recipe JSON, classify namespaces,
or silently skip mandatory roots. The narrow crafting-policy contract is an explicit
exception: its startup classifier checks loaded namespaces, and its final recipe
report inspects recipe JSON for named cut-family leaks and consumers. This does not
authorize a general audit framework; see [Crafting graph policy](progression.md#crafting-graph-policy).

### Item-hover annotations

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

### Learning surface hierarchy

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

Authoring and review follow [Learning surfaces](world-and-player.md#learning-surfaces). There is no
active achievement ledger or replacement reward system.

### Spirit commerce

**Five-spirit source foundation authored and reviewed; not staged or release-qualified.** Ordinary
markets, professions and payments use **Power, Motion, Endurance, Renewal and
Control**. The five registered core shard items are
`better_spirit_commerce:{power,motion,endurance,renewal,control}_spirit`; they are
both physical currency and native Malum reagents. Eldritch and Umbral remain
specialist Malum items, not ordinary commerce currencies.

The foundation mechanically projects the existing eight authored stock templates
into five markets without redesigning their goods or prices. Merged markets keep
both source lists and one matching campfire; the 288 authored wandering rows
therefore supply 285 projected rows, **not 285 offers per visit**. Visits select
12 goods plus the matching fire, with native egg/Font offers handled separately.
The [five-market catalogue research](spirit-commerce-five-market-catalogue.md)
has not been adopted as runtime data. Final catalogue selection/pricing remains
separate work; existing Font destination relationships are unioned under the
merged themes, not expanded to new destinations. Existing stock uses are not reset.

Player-credited kills immediately release spirits at the dying mob through
Malum's floating, homing entities. Native mappings and eligibility remain
authoritative, including mapped passive animals. Only the unmapped fallback is
hostile-only. Spawner-origin mobs, already-soulless victims and economy actors
release none. Converging ordinary outputs are summed and split at stack limits;
there is no dual payout or new Tempo source. Tagged ritual identities and exotic
outputs remain distinct. Currency stacks to 64 without delivery receipts.

Malum's old internal type/property names remain where native blockstates, ritual
selection, particles and indexing need them. Their shard suppliers resolve to
**five core plus two specialist items**; they do not impose seven separate ordinary
reagent requirements. The native pouch retains 27 generic slots and the altar
retains nine generic indices—container capacity is not an extra spirit category.
Existing creative-tab and recipe-viewer policies hide raw native ordinary shards;
actual client behavior remains unverified. Obsolete economy-to-native refinement
adapters are retired rather than converted into self-to-self recipes.

**Release hold:** nine pairs of native recipes lose input distinctions after
merging. Their exact IDs and unresolved output-selection risks are recorded in the
[five-spirit recipe follow-up](design.md#five-spirit-recipe-follow-up-release-hold).
The foundation preserves their IDs/outputs/costs; it has **not** disabled them or
chosen substitute reagents. Native first-match order is not an acceptable
resolution. All authored tests are **UNRUN**; do not deploy this source candidate
before catalogue/recipe follow-up and authorized runtime qualification.

### New worlds and backups

The refactor supplies no old-world identity migration. Create a tested backup
before updating an existing world. New worlds are the supported baseline because
deposit identities, placed small chunks, disturbed radioactivity, and Machine
Block identities all participate in saved state.

Electrochemistry and Arena Trials are excluded from the active pack as of 2026-11-17.
Existing saves containing their fluids/items, arena sites or pending machine/match
state require a separate recovery review; no migration or world reset is supplied.
Seasonal Trees remains in the candidate for fresh worlds. Seasonal
Daylight replaces Simple Day Length Extender atomically as the sole sky-clock owner;
the old clock configuration is retained only for rollback. Its nominal full
sunrise-to-sunrise cycle is 50,000 server ticks, while Minecraft and Serene
Seasons still use a 24,000 logical day baseline. The daylight curve changes
the day/night share through the year without changing `gameTime`, TPS or machine
simulation. Seasonal Trees' new-world spring initialization does not reset old
saved calendars or reconstruct damaged old trees. None of these candidate
behaviors has exact-pack runtime acceptance or an established-world migration.

## Dimensional Font ecology and resource links

The three enabled expedition Fonts share a radial court, a decorative field with candles and shallow pools, and a dimension-specific block palette. The 2026-11-16 source candidate replaces copper structural placements with stone bricks and copper roof shapes with corresponding deepslate-tile shapes, including village and arrival-site paths. Geometry and existing layout versions are retained. Bumblezone sites still form three dense wax and hive clusters. Tree biome sites use stripped local logs for their supports and roof ring; treeless sites omit these. This is future-placement-only: existing sites are not scanned, converted or migrated. The source candidate is not yet packaged or gameplay-qualified.

| Font | Salience aspects | Spirit map sellers (five-market source candidate) | Field vocabulary | Renewable or dimension-linked materials | Pack systems that consume them |
| --- | --- | --- | --- | --- | --- |
| Nether | Power, Endurance | Power, Control | Blackstone, basalt, brick, magma, red candles | Wart, blaze powder/rod, magma cream, basalt | Heat Sync Boiler Heater, PneumaticCraft thermopneumatic plant, TCon Scorched Brick, Nether Font Libation |
| Aether | Motion, Renewal | Motion, Renewal | Holystone, icestone, aerogel, pale sky stone | Blue berries, swet balls, skyroot leaves/logs, aerogel, blue aercloud | Rotational Compressor, Clockwork air compressor/propeller bearing/gas thruster, jet boots, Aether Font Libation |
| Bumblezone | Power, Motion, Endurance, Renewal | Endurance, Motion, Renewal | Beeswax, ancient wax bricks and eyes, brood honeycomb, yellow candles | Pollen puffs, honeycomb, honey, carvable wax | Create mechanical harvester/plough, Airtight Upgrade, Bumblezone Font Libation, existing nursery systems |

Map offers use the saved index of already discovered natural Fonts. The five-market source preserves the union of each merged market's previous destination relationships; the salience-aspect column does not independently grant additional destinations. Existing dormant Ratlantis relationships are retained, not newly enabled by this consolidation. Maps rotate within that set after sale. The index lookup neither loads nor generates chunks. Resident spirit villagers receive a map listing at level two; wandering spirit traders can add a map to their authored catalogues.

### Libation and Pourer

A Create basin mixes one bucket of water with three native ingredients into one bucket of the matching Font Libation. An overhead Font Pourer accepts fluid through Forge fluid capability or a bucket, but the Font itself does not accept pipes. Place the Pourer exactly two blocks above the Font with one air block between them. During an active run, a matching libation pours down visibly and restores 48 charge per millibucket at a bounded rate of one millibucket every 12 ticks. A bucket therefore holds 48,000 charge, about ten extra minutes at the current 80 charge per second drain. The nominal 15,000 charge before entry costs is about three minutes. The Pourer is idle when there is no active run, its fluid does not match the Font, or the air gap is obstructed. It never loads chunks.

### Recipe audit

The three live Fonts attach native materials to themed machines: Nether thermal
processing, Aether mobility and Bumblezone farming. The retired Ratlantis Font
has no libation or mandatory machine ingredient. Meteor Certus Quartz replaces
Ratlantis components for electronics and logistics, while native Rats materials
are found in rare Overworld biomes. The hand workshop remains available before
dimensional expeditions; three live libations are optional fuel.

The pack recipe source is `kubejs/server_scripts/progression/85_dimension_font_ecology.js` plus the focused recipe scripts referenced above. Runtime validation must check registry IDs against the installed Aether, Bumblezone, Rats, Create, PneumaticCraft, Heat Sync, and Clockwork JARs. The worldgen screenshot harness is a separate minimal-mod development runtime; it is not shipped in the pack.

## Ordinary Rats and retired Ratlantis rehoming

Normal players cannot enter Ratlantis. The Rats 8.1.3 bundled datapack stays enabled for native item, recipe, structure and loot definitions, but the retired Font is not restored and both token and portal outputs remain closed. Better Ratlantis Logistics and its custom abyss, sunken vaults, bait rule and three custom components are removed. Native cheese and rat taming follow Rats' own behavior. Existing player worlds are preserved; this candidate supplies no automated Ratlantis migration.

The earlier Ratlantis rehoming into Overworld coastal and cave biomes is retired. Pack-authored Ratlantis ores, plants, structures, special spawns and fishing injections are removed; ordinary Rats remains installed with its native non-Ratlantis gameplay. Saved structures and entities are not deleted. The exact runtime recipe, loot and visibility surface still needs candidate-matched verification.

Starcatcher chorus crab, end glow and voidbiter have separately authored warm/lukewarm Overworld ocean routes. The retired Ratlantis fishing injection is not a route for ordinary Rats items. Aquatic Ambitions' mechanical conduit retains marine alloys and the native conduit, substituting one meteor Certus Quartz crystal for the removed custom mechanism. Retained Rat archaeology uses obtainable amethyst and ordinary Rats cheese in place of inaccessible Ratlantis inputs; the hat and upgrade remain available by their authored recipes. Conventional fixed Rats tools remain closed by the Tinkers policy; unique weapons, bows, armour and rat upgrades remain native. Exact recipe and fishing behavior still needs candidate-matched verification.

The former Ratlantis-depths design is retired. Historical distribution ZIPs and evidence preserve their own content and are not rewritten.

## Untamed Wilds wildlife integration

The pack pins the published Forge 1.20.1 `untamedwilds-4.0.4-b004.jar` (CurseForge project 1399279, file 7441092). Its SHA-1 is `b4007ecf2fed03b89205b6fdd3fba6980c9e39bd`.

### Natural wildlife

Untamed Wilds' six ungated fauna worldgen modifiers are replaced by copies whose `configOption` points to `mobcontrol.masterspawner`. The common config keeps that switch false. The native underground fauna modifier already uses the switch. Flora modifiers remain active. In Control then replenishes 38 entries from the published JAR's critter, herbivore, predator, underground, ocean, and river tables, using a chance of `weight / 2000` per second, 20 placement attempts, bounded groups, and per-type caps. Land rules require sturdy ground, water rules require water, and cave rules stay below Y=48. Each In Control rule uses an `and.biometags` filter matching the species habitats in the published JAR. The filters avoid the upstream bear crash when its natural-spawn finalizer finds no valid species. Obsolete biome IDs in that JAR are omitted. Its feeder-only butterfly and benthos giant clam have no replenishment rule.

The exact-overlap priority is Genetic Animals, then Untamed Wilds, then Thalassophobia, then vanilla. Genetic Animals' farm animals retain their existing spawn policy. Untamed's softshell turtle, tortoise, and newt are distinct from Genetic Animals' enhanced turtle and axolotl. Vanilla camel, panda, and polar bear are denied for natural spawns only. Thalassophobia's sunfish, catfish, big catfish, generic shark, bull shark, goblin shark, hammerhead shark, whale shark, and whale lose their biome add-spawns entries and have a natural-spawn denial backstop. Its basking, frilled, and cookie-cutter sharks, fantasy or prehistoric creatures, and unique systems retain their native behavior.

Thalassophobia's naturally spawning giant clam remains because its live pearl harvest is repeatable. Untamed's giant clam is still available through non-natural means. One-way recipes convert Thalassophobia pearl to Untamed pearl and black pearl to Untamed giant pearl, preserving Untamed's pearl consumers.

EMI Loot excludes only Untamed's baleen whale preview. Its client worker otherwise constructs that entity before Untamed's species data is ready and logs an exception. The whale and its actual loot table remain in the game.

### Checks and tuning

The Dev suite checks file contracts without launching the pack. The targeted join test verifies startup with the full client and server pack and rejects log errors. It does not measure wildlife population, underwater placement, biome selection, or the live clam interaction; those still need a dedicated runtime check. If replenishment needs tuning, edit `config/incontrol/spawner.json` and the matching `kubejs/data/kubejs/tags/worldgen/biome/untamed_spawn/` filter; keep the JAR's species biome data and the disabled native fauna features as the authority for habitat and duplicate control.

## Cataclysm and Depth Director placement

This pack integrates L_Ender's Cataclysm 3.31 for Forge 1.20.1 with Lionfish API 3.0. The pack owns the placements through biome-tag data and In Control rules; it does not use a Cataclysm dimension add-on. Cataclysm's arena structures remain intact so each boss keeps its native arena mechanics, including its respawner and activation altar.

The table separates retained placement intent from deferred destinations. It is
not proof that every arena is reachable or generated in the candidate.

### Arena destinations

| Cataclysm boss | Arena | Pack destination |
| --- | --- | --- |
| Leviathan | Sunken City | Overworld deep oceans |
| Netherite Monstrosity | Soul Blacksmith | Nether Wastes |
| Ignis | Burning Arena | Crimson Forest, Nether |
| Ender Guardian | Ruined Citadel | Deferred; no active destination or replacement travel route |
| Harbinger | Ancient Factory | Deferred with rocket-only Fallout Wastelands access |
| Ancient Remnant | Cursed Pyramid | Deferred with rocket-only Fallout Wastelands access |
| Scylla | Acropolis | Aether skyroot biomes |
| Maledictus | Frosted Prison | The Deep Void |

The Fallout and Ruined Citadel rows describe deferred encounters, not reachable candidate destinations. Retained arenas keep their native activation and placement rules where their target dimensions remain available. Existing generated chunks are unchanged; no world migration is claimed.

### Depth Director miniboss roster

Each selected miniboss is a one-at-a-time heavy encounter, gated to deep ecology pressure:

| Ecology | Cataclysm miniboss | Native encounter drop retained |
| --- | --- | --- |
| Undead | Aptrgangr | Strange Key, bones, rotten flesh |
| Carrion | Coralssus | Coral Chunk |
| Spirits | Ignited Revenant | Burning Ashes (Ignis activation item) |
| Sculk | The Prowler | Redstone and iron |
| End | Ender Golem | Void Core |
| Deep Void | Kobolediator | Kobolediator Skull, Koboleton Bone, Ancient Metal |

These rewards stay in Cataclysm's native loot tables, so boss preparation materials are found by engaging the corresponding Depth Director encounter. The pack does not add a second reward path or replace Cataclysm drops.

Natural spawning is denied for all nine Cataclysm minibosses: the six in the roster and Wadjet, Amethyst Crab, and Clawdian. This keeps the selected six in Depth Director encounters while retaining Cataclysm's ambient Deeplings, Koboletons, and other non-miniboss ecology. The DD profiles remain subject to Depth Director's retained dimension rules; Flesh That Hates is exclusive to the Deep Void and is not part of these profiles.

### Change boundary

The five established ecology files and the Deep Void ecology are overlaid from KubeJS data, not edited in the Depth Director source repository. Existing worlds and already-generated chunks are not retroactively relocated or migrated. Pack tests, deployment, and distribution were not part of this content change.

## Soulwood first-tree source

The rare Malum sites add a once-only, finite native blight event. The bootstrap does not place blocks. Malum's own native event can probabilistically add `malum:soulwood_growth` to its blight pattern, so the original natural route remains intact.

For a dependable separate route, the Blood Altar turns one `malum:blighted_gunk` into one `malum:soulwood_growth` for 2,000 LP at altar tier 0. Malum 1.6.7's `blighted_soil` loot table drops four Blighted Gunk without Silk Touch; Silk Touch preserves the soil block instead. This ties the recipe to collected blight material without adding a guaranteed sapling to each site or a generic chest/trader bypass.

The recipe is authored at `kubejs/data/kubejs/recipes/malum/soulwood_growth_from_blighted_gunk.json`. It is source-authored and unit-contract tested; Packwiz synchronization, active candidate recipe registration and in-world altar acceptance remain pending. No pack-level or runtime test was run.

## Systemic Salience

Systemic Salience connects separate authorities: RPG Stats owns per-life development, Diet owns temporary nourishment, Better Survival Physiology owns nourishment-driven effects, Realistic Ores owns deposits, and Spirit Commerce owns its currencies/reagents. Matching identities do not share balances. TConstruct remains outside this matrix; its materials keep their native behavior.

### Canonical identity matrix

**2026-11-17 approved five-aspect source contract — foundation authored and reviewed,
compile-only checked; not installed or gameplay-accepted.** This supersedes the eight-food/eight-build
teaching-only proposal. The bounded exception addresses the reported difficulty of
understanding eight categories, not a finding from the friends' playtest. Diet has
five actual native groups, Life has five lean choices, and commerce/crafting share
five ordinary spirit types. Food names lead in nutrition UI; aspects are secondary
teaching cues. No shared gameplay-state service is introduced.

| Aspect / Life / core spirit ID | Actual Diet group / ID | Glyph | Teaching palette (sRGB) | Unchanged ore representatives |
| --- | --- | --- | --- | --- |
| Power / `power` | Protein / `proteins` | ✦ | `#FF4055` | Hotstone, Tin Quartz |
| Motion / `motion` | Carbs / `carbs` | ➜ | `#E0B01F` | Copper Bloom, Brassroot |
| Endurance / `endurance` | Spice / `spice` | ∞ | `#52606A` | Coal Measures, Ironstone |
| Renewal / `renewal` | Vegetables / `vegetables` | ✚ | `#6CCAF0` | Evaporite Beds |
| Control / `control` | Alcohol / `alcohol` | ⊕ | `#8E5BB7` | Black Shale |

Sprite-sheet indices are owner-local: Life and Physiology use different existing
atlases, and nutrition retains a distinct paid-Spice food badge. Matching identities
and teaching colors do not mean that copying one mod's numeric atlas slot selects
the correct art in another.

All eight deposits, their morphology and processing outputs remain distinct.
Eldritch and Umbral remain specialist spirits outside the five ordinary types
(seven spirit types total). Stamina, mana, souls, Life allocations and nutrition
remain independent native pools. See the [exact lean Life packages](progression.md#life-attributes--staged-pack-candidate-not-gameplay-accepted).
Glyph, name, morphology and behavior supplement color.

Font aspect metadata remains Nether=Power/Endurance, Aether=Motion/Renewal and
Bumblezone=Power/Motion/Endurance/Renewal. Discovery uses the same five identities
without inventing acquisition events. Spirit mergers conserve existing quantities:
Infernal/Impact + Arcane/Work → Power; Aerial/Mobility + Tempo → Motion;
Aqueous/Endurance + Earthen/Robustness → Endurance; Sacred → Renewal; Wicked →
Control. No new Tempo supply or second payout is introduced. Reagents are included,
not merely renamed coins. Catalogue goods/prices and recipe-specific substitute
reagents remain separate follow-up; no new Font trade destinations are authorized.
This is a fresh-world target with no old-character, item or auto-plan migration or
refunds. Existing worlds remain untouched.

### Sensory grammar

Every identity has one recurring motion and sound phrase. These cues play only at meaningful events—allocation previews and commits, nutrient threshold changes, and discrete nutrition activations—not on every attack, step, or mined block.

| Aspect | Motion | Sound motif | Geological expression |
| --- | --- | --- | --- |
| Power | radial burst | retained impact-family thud/transient | radiating Hotstone; Tin Quartz ribbons |
| Motion | directional sweep | retained mobility-family whoosh | clustered Copper Bloom; branching Brassroot |
| Endurance | expanding ring | retained endurance-family low tone | Coal Measures seams; dense Ironstone bands |
| Renewal | rising bloom | ascending chimes | Evaporite crystal bloom |
| Control | converging points | ticks resolving to a ping | Black Shale convergence |

Physiology and geology select five slots from the existing 18×18 badge atlas; unselected legacy art/audio may remain inert resources. Separate old Work, Tempo and Robustness cue identities are retired from these consumers. Context-specific sound mixes need not share one file. RPG allocation art/audio remains independently owned. Geology remains morphology-first, not an aspect-color wash.

### Teaching surfaces

There is deliberately no explicit comparison screen. Discovery is reinforced through the RPG allocation screen, the Journal's Diet-derived nutrition panel, food and ore tooltips, nutrition activation feedback, ore blocks in the world, and EMI's ordinary tooltip/recipe surfaces. Natural names lead; the aspect badge, glyph, colour, and name appear when the player interacts closely enough to need the formal identity.

RPG point increments provide a quiet preview pulse and motif. A successful server-confirmed allocation produces a short recap. Nutrition threshold crossings and discrete abilities use the corresponding motion and motif, while the meal recap explains which bodily state changed and for approximately how long. Recap labels wrap within the viewport and use bright neutral text beside the aspect badge. Four to ten seconds of display time scale with the number of changed states; menus and a hidden HUD pause that timer. Ore tooltips formally name the identity and preserve Shift for the assay summary; ore discovery never interrupts play with a toast or action-bar message.

### Pulse pacing

For systems whose salience fades under constant trickle, use **accumulate → discharge → recover**. Accumulation should have a meaningful input and a visible limit; discharge should be a discrete event with enough warning and bounded output to read; recovery should provide a real interval before the same pressure can build again. Preserve owner-specific clocks and consequences instead of forcing one shared timer onto every system.

| Owner surface | Accumulation and discharge | Recovery or quiet interval |
| --- | --- | --- |
| Depth Director | Eligible underground time builds encounter pressure. Each ecology has a warning, bounded surge and packet budget. | Ecology profiles specify a recovery phase; pressure also decays while on the surface. New injury relief is a separate one-time signal, not an encounter reset. |
| Pillager Campaigns | Eligible play schedules scouts or three-wave assaults; assault arrival receives a two-minute surface warning and spawn work is capped per tick and second. | Resolving a campaign schedules the next eligible window. A stalled wave has a finite progress timeout. Do not add a second encounter in the same track while an invasion remains active. |
| Spirit economy | Credited kills release physical Better Content spirits at the dying mob immediately. | The spirits home to their credited player through Malum's ordinary collection behavior; there is no release cadence or reward ledger. |
| Injury-pressure relief | A newly observed maim creates a temporary director-relief signal. | The saved signal decays each second on the configured curve (180 seconds by default), whether the injury remains or is treated; encounter budget and target state remain intact. |

These owners already implement meaningful pauses at their own scopes. Keep ordinary hunger, hydration, temperature, machine throughput and other continuous simulations continuous; report discrete threshold changes or milestones without turning every tick into a pulse. Do not add periodic drops, automatic reset, or an all-systems wave scheduler merely to make the doctrine look uniform.

### RPG development

The approved source contract replaces the eight builds with **Power, Motion,
Endurance, Renewal and Control**, using `F(p)=p/(p+10)` and
`B(p)=min(p,20)/20`. These are lean packages, not concatenated old builds. One
point is earned per new Life level peak; final death resets allocations, while
non-death clones and reconnects preserve fresh five-choice characters. Obsolete
allocation requests must fail atomically, including mixed old/valid IDs. See the
[exact effects and independent resource pools](progression.md#life-attributes--staged-pack-candidate-not-gameplay-accepted).

Five-choice Life source and regression tests are authored and compile-only checked.
Historical Build 55 Smoke and singleplayer checks used the older candidate and
establish neither these five choices nor their combat/climate effects. No tests, Minecraft launches or new JAR
staging are authorized in this source pass; authored regressions remain **UNRUN**.

Slice of Life: Carrot remains the sole owner of maximum-health progression.

### Nutrition

Diet's native registry, `builtin` suite and tracker own exactly **Protein, Carbs,
Spice, Vegetables and Alcohol**. Retired group definitions are excluded at native
loading, including reload, rather than hidden in the UI. Physiology retains only
cooldown/event state, not independent Sugar or Alcohol balances. The Journal reads
the synced native suite/values; Physiology owns their effects and the Diet suite
has no competing effects.

- **Protein / Power:** melee support and Heavy Blow/cleave, plus continuous
  correct-tool mining. No Work Rhythm, grace period or mining-burst layer.
- **Carbs / Motion:** movement/swimming, safer falls and the sprint-jump leap;
  existing sugar amplification and accelerated expenditure now apply to all
  Carbs. No hidden Sugar meter or extra sprint-speed stack.
- **Spice / Endurance:** paid seasoning health cost, capped mining/movement
  benefits, resource conservation and Deep Reserve. Merely reading a Spice value
  never charges health or awards a dose.
- **Vegetables / Renewal:** harmful-effect expiry, meal recovery and cleansing;
  no new maximum-health progression.
- **Alcohol / Control:** optional protection with impairment, blackout and
  recovery, retaining Carb interactions. Drinking is not required for nutrition
  completeness or progression.

Native tags and recipe inheritance remain authoritative for unmanaged foods;
there is no manually rewritten whole-food catalogue. Old grains, fruit and sugar
source contributions become Carbs. Dairy defaults to a configurable **80% Protein
/ 20% Carbs**, conserving the contribution. Unambiguously dairy-only native recipe
trails can use that split; mixed native membership retains native set-based shares,
not invented ingredient weights. Paid preparation provenance resolves each ingredient
before aggregation. Canonical five-key vectors are explicitly typed and never
remapped; mirrors, quality and portions are resolved once.

Unmanaged consumption stays Diet-owned. Managed Cooking/Drinking Water completion
owns its committed paid serving; previews and cancellation award nothing. Cooking
uses actual completion identities to reject duplicate callbacks, not item/tick
heuristics that merge distinct portions. Fractional water intake advances its paid
reserve before releasing the serving. Native Carbs/Alcohol decay multipliers are
zero because their existing metabolic clocks update the native values directly;
Protein, Spice and Vegetables retain ordinary Diet decay and applicable upper-band
behavior. Native final-death reset and non-death clone semantics are retained.

Food tooltips and the Journal use the same canonical results, not an alternate
hidden roster. Source/fixture/test compilation is available for the Diet/intake
batch; authored native integration and regression tests are **UNRUN**. Mixin
application, remote-client synchronization, real completion and lifecycle behavior
still require later authorized runtime acceptance.

### Ores

The complete player-facing roster is eight deposits plus the unrelated Oil Seep surface feature. Each deposit uses its aspect palette as an accent, a unique geological morphology, an immediate promise, and deeper assay routes:

- Hotstone: dangerous heat and heavy power.
- Copper Bloom: responsive copper mechanisms.
- Tin Quartz: productive toolmaking, quartz, and later gem-bearing assays.
- Brassroot: zinc/brass for moving machinery.
- Coal Measures: stored fuel for sustained work.
- Ironstone: durable iron and nickel-bearing depth.
- Evaporite Beds: salt, preservation, and fertile chemistry.
- Black Shale: redstone control, soul contamination, and precious traces.

Complex constituent recovery remains processing depth. Removed worldgen identities have no compatibility aliases or migrations.

In the source candidate, small ore chunks collected from surface samples can be melted directly when their full-sized chunk already has a supported molten output. Direct melting rounds down: for a 40 mB full chunk made from nine small chunks, each small chunk gives **4 mB**. Nine direct melts therefore give **36 mB**, while bundling those nine first still gives **40 mB**. Direct melting is convenient, not equally efficient; existing full-chunk and processing yields are unchanged.

### Acceptance rule

For every representative, test whether a player can predict its broad use from name, morphology, tooltip, and immediate behavior before reading exact numbers. A cue fails if it needs another row's identity to explain it, or if it merely exposes a weighted profile. Each representative must retain an unambiguous shared-aspect assignment. The five real food groups map one-to-one to the five aspects, while multiple distinct deposits may share an aspect without merging their geology or outputs. Shared names never merge nutrition, Life, currency or native resource pools.

## Death, Return, and Tension

This is the pack-wide living description of player death and return. The tracked pack
defines installed mods and configuration; the owning custom-mod repositories define
their mechanics. The tables below distinguish **implemented** behavior from
**conditional** third-party behavior and **proposed** work. A manifest establishes
that a mod is installed; it does not establish that every native feature activates
in this pack. See [the workspace inventory](implementation.md#custom-mod-workspace) for custom-mod
identities and [the docs policy](../README.md#doc-policy) for evidence precedence.

### Intended experience

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

### Implemented lifecycle

1. **Damage and danger.** Vanilla admission rules, armor, absorption, and the
   installed combat and survival mods affect whether damage reaches the player.
   [Downed Player Revival](https://github.com/better-content/better-deaths-door)
   owns bodily injuries. A hit that exhausts positive HP enters Death's Door and
   adds one maim. The player retains ordinary movement, combat, and inventory
   controls. There is no bleed-out timer or required helper. Once at semantic zero,
   another accepted HP-damaging hit either adds one maim or begins final death;
   existing injuries determine the death roll. Healing can restore positive HP
   after the zero-crossing healing lock, while injuries require treatment.
   Explicit special-kill sources, command kill, and the void bypass the Door.
   Revival's current rules say totems do not prevent player death. Its
   [source README](https://github.com/better-content/better-deaths-door)
   owns exact probabilities, treatment, and exceptions.
2. **Death attempt.** A further lethal hit can reach Forge's `LivingDeathEvent`.
   Another handler may cancel that attempt. Revival redirects the
   `ForgeHooks.onLivingDeath` call in `ServerPlayer.die`; an uncanceled return calls
   `RevivalManager.finalDeath`, which posts `InjuryEvent.FinalDeath`. A canceled
   return calls `canceledDeath` and leaves the player alive. `FinalDeath` means
   Forge accepted this death path. It fires before the rest of vanilla death and
   later respawn processing finish; it is not a receipt for every consequence.
   Source: Revival's [server-death redirect](https://github.com/better-content/better-deaths-door/blob/main/src/main/java/com/bettercontent/betterdeathsdoor/mixin/ServerDeathMixin.java)
   and [final-death publisher](https://github.com/better-content/better-deaths-door/blob/main/src/main/java/com/bettercontent/betterdeathsdoor/RevivalManager.java).
3. **Final death.** Revival clears active bodily state while retaining a death
   recap until respawn. [RPG Stats](https://github.com/better-content/better-rpg-progression)
   clears the current life's committed allocations and grants its XP-level-based
   Heart Fragment entitlement on a confirmed final death. Other owners react to
   death using their own Forge hooks; their effects are listed below.
4. **Respawn.** [Class Selector](https://github.com/better-content/better-spawns)
   returns an onboarded player to the separately locked personal starting
   location, with scripted sound and particle effects. Its personal spawn takes
   priority over ordinary non-forced bed and anchor changes. The native death
   screen remains visible because `do_immediate_respawn=false` in
   [Global Game Rules](../config/globalgamerules-common.toml). The configured
   World Lifecycle Manager selects a fresh world's initial shared-spawn biome by
   Cold Sweat's loaded habitable base-temperature bounds and rejects all
   `#minecraft:is_ocean` biomes plus any extra initial-spawn biome IDs in
   [`config/better_world_management-initial-spawn-blacklist.txt`](../config/better_world_management-initial-spawn-blacklist.txt).
   The saved spawn and the two blocks above it must all qualify, because vanilla
   can lift a first-join spectator into a vertically different biome. The spawn's
   chunk and all eight neighboring chunks must each have one qualifying biome
   column; the eight neighbors are sampled at their chunk centers and predicted
   surface heights. This is not a guarantee of contiguous land or effective comfort.
   This blacklist does not affect successor or personal spawns. The shared spawn
   radius is zero; first-join players scout in spectator mode, so terrain safety
   is not a condition for publishing an uninhabited initial world. The player's permanent personal respawn can
   be selected in any biome; `spawn_radius=256` remains the configured fallback on established
   worlds where no personal spawn governs the return.

The event boundary is deliberately narrow. The feeling of death tension emerges
from independently owned systems. A death attempt, an accepted death, and a
completed respawn are different lifecycle moments.

### Current pack policy and persistence

| Surface | Current rule | Authority |
| --- | --- | --- |
| Ordinary inventory, armor, hands, and hotbar | Kept on death for every configured damage type; no item-durability loss; no damage-source overrides | [Configurable Death server defaults](../defaultconfigs/configurabledeath-server.toml) |
| Experience | Its module is enabled; `droppedXPPercent=1` and `recoverableXPPercent=0`. This is the configured XP-loss policy, separate from item retention | [Configurable Death server defaults](../defaultconfigs/configurabledeath-server.toml) |
| Vanilla gamerule | `keep_inventory=false`; Global Game Rules reapplies it on world load. The pack relies on Configurable Death for ordinary item retention rather than the vanilla rule | [Global Game Rules](../config/globalgamerules-common.toml) |
| Curios slots | Curios has `keepCurios="DEFAULT"`, which follows the false vanilla gamerule. KeepCuriosInventory's bundled mixin forces the Curios drop decision to keep unless an item is blacklisted; the pack blacklist is empty. The combined runtime outcome still needs an observed final death before promising every slot survives | [Curios](../defaultconfigs/curios-server.toml), [KeepCuriosInventory](../config/keepcuriosinventory-common.toml) |
| Food and hunger | Configurable Death does not retain food level or saturation. Diet's installed `2.1.1` JAR defaults to a 100-percentage-point loss of each nutrient group on death, floored at zero (`deathPenaltyMethod=AMOUNT`); the pack does not pin a Diet server override | [Configurable Death](../defaultconfigs/configurabledeath-server.toml), [Diet manifest](../mods/diet.pw.toml) |
| Health progression | Spice of Life: Carrot Edition starts at five hearts, adds one heart per configured milestone, and retains its eaten-food list through death (`resetOnDeath=false`) | [Sol Carrot server defaults](../defaultconfigs/solcarrot-server.toml) |
| Per-life development | RPG Stats allocations clear on death; its separately stored Auto plan persists so later earned points can follow the plan | [RPG Stats source](https://github.com/better-content/better-rpg-progression) |
| Bodily injury | Current-life injuries and treatment history clear on confirmed final death; a recap remains while dead and clears on respawn | [Revival source](https://github.com/better-content/better-deaths-door) |
| Lineage and discoveries | Ordinary death does not reset Threads discovery generations or the World Lifecycle Manager lineage | [Threads](world-and-player.md#threads), [World Lifecycle Manager source](https://github.com/better-content/better-world-management) |

`defaultconfigs/` supplies defaults for new worlds. Existing saves can retain
their own server configuration. These rows describe the tracked pack's target,
not a claim about every previously created world.

### Better Content ownership

The following custom mods have a direct or supporting role. Their repositories
remain separate; this document owns the cross-pack explanation, not their runtime
state. Each name links to its owning source repository.

| Owner | Death-system contribution |
| --- | --- |
| [Downed Player Revival](https://github.com/better-content/better-deaths-door) | Damage-to-Death's-Door transition, maim roll, treatment, pressure presentation, final-death acceptance signal, and recap |
| [Class Selector](https://github.com/better-content/better-spawns) | Locked personal return point and the sound/particle reinvigoration after respawn |
| [RPG Stats](https://github.com/better-content/better-rpg-progression) | Per-life allocation loss, persistent Auto plan, and XP-level-derived final-death Heart Fragments |
| [Depth Director](https://github.com/better-content/better-cave-encounters) | Underground pressure, warnings, bounded surges, semantic low-health response, and injury-aware pacing; injuries slow buildup without retiring the encounter |
| [Pillager Campaigns](https://github.com/better-content/better-pillager-campaigns) | Surface scouts and warned multi-wave assaults; Death's Door players remain targets, and the death observer retires a target and starts death grace |
| [Dimension Drink](https://github.com/better-content/better-dimension-fonts) | Font run ends on final death; actual transport back to the origin is a separate successful-return event |
| [Player Traces](https://github.com/better-content/better-player-traces) | Records recent pose as a final-death echo and marks respawn in the world; ongoing Door play still generates traces |
| [Learning Surfaces](https://github.com/better-content/better-discovery-guides) | Injury discoveries, final-cause death tips, and a lineage memory that survives ordinary death |
| [Dynamic Survival HUD](https://github.com/better-content/better-survival-hud) | Shows semantic-zero danger and changed injury risk without owning the health or injury calculation |
| [Systemic Salience](https://github.com/better-content/better-survival-physiology) | Diet, temperature, thirst, and stamina interactions that change survival margins; resets its temporary metabolic state on death clone |
| [Realistic Ores](https://github.com/better-content/better-ore-geology) | Geographically distinct finite deposits and dangerous Hotstone; makes travel and return matter |
| [Water Survival](https://github.com/better-content/better-drinking-water) | Water collection and portable drinking choices supporting survival away from base |
| [Better Content Fixes](https://github.com/better-content/better-compat-fixes) | Survival and compatibility rules, including interrupting sleep when danger commits; it is not the owner of final death |
| [World Lifecycle Manager](https://github.com/better-content/better-world-management) | Persists lineage across ordinary player lives; world succession is a distinct, larger lifecycle |

See [Systemic Salience](world-and-player.md#pulse-pacing) for director and
campaign pacing, [Realistic Ore Processing](progression.md#realistic-ore-processing) for the
home/echo deposit geography, and [Learning Surfaces](world-and-player.md#learning-surfaces) for
the teaching contract.

### Installed third-party surfaces

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
[Cataclysm](../mods/cataclysm.pw.toml) affect where and how dangerous encounters
occur. World Lifecycle Manager selects the initial shared spawn in fresh worlds.
[Tectonic](../mods/tectonic.pw.toml), [Nature's Spirit](../mods/natures-spirit.pw.toml),
[Large Ore Deposits](../mods/large-ore-deposits.pw.toml), and
[Excavated Variants](../mods/excavated-variants.pw.toml) affect the spatial
context of travel or resources. These are relevant to tension without owning
the player's final-death decision. Exact native side effects and worldgen
interactions remain with their owning mods; the manifest links prove inclusion,
not that every optional feature is active.

### Integration seams and refactor candidates

These are review candidates, not approved behavioral changes or evidence of a
reproduced bug.

| Seam | Current fact | Candidate review |
| --- | --- | --- |
| Accepted death versus event observation | Threads consumes Revival's `InjuryEvent.FinalDeath`. RPG Stats, Player Traces, Dimension Drink, Pillager Campaigns, and Depth Director also subscribe to `LivingDeathEvent` at differing priorities; Class Selector refreshes its personal spawn at that hook. Pillager Campaigns and Depth Director do not explicitly inspect `event.isCanceled` in their death subscribers | For each side effect, decide whether it needs the accepted-death signal, can safely observe a possibly canceled attempt, or belongs at respawn. Preserve early XP capture where later handlers can clear it |
| Retention authority | Configurable Death keeps normal gear while the vanilla `keep_inventory` rule is false; Curios and KeepCuriosInventory add their own policies | Observe normal, Curios, and modded inventory slots across representative final deaths before promising complete gear retention |
| Native saves and corpse artifacts | Revival says totems do not prevent player death; several installed mods provide save or death-artifact mechanisms | Record each actual activation path and whether it cancels a death, runs after acceptance, or remains irrelevant when no items drop |
| Teaching and configuration drift | Revival's source now describes active play at Death's Door with no bleed-out, while its tracked common config still contains bleed/revive-era keys. Death tips and other pack copy may describe older treatment or totem behavior | Reconcile player-facing language and identify which config keys are live before editing either mechanics or copy |
| Geography and task choice | Return location and resource geography are independently owned; there is no explicit distance-to-base rule | Judge travel tension and post-respawn choice in play before adding a mechanic or moving repository boundaries |

Keep the domain owners separate while those seams are investigated. A shared
confirmation signal is useful only for consequences that truly require an
accepted death. A central consequence registry or numerical death-tension meter
is not part of the current design.

Creature deaths form a separate economy boundary: Better Content Economy and
Malum handle credited mob-kill spirit release, and the economy explicitly excludes
players as spirit victims. They do not own the player's death/return lifecycle.

For the endgame aspiration, see [Optional late-game threats](design.md#optional-late-game-threats).

## Threads

Threads answers “what just happened to me?” through 53 discoveries. Each card gives the
experienced event, its cause, and a useful next step. Gameplay does not have separate
reveal and completion stages. Exact recipes and apparatus remain in EMI, Ponder, and
native guides; survival fundamentals remain in the 18 loading/Lessons entries.

The canonical definitions, trigger routes, and art scene specifications live in
[`Learning Surfaces authoring roster`](../../mod_source/better-discovery-guides/authoring/discoveries.json).
All 53 cards have reviewed Journal copperplate illustrations. Their intended scenes
remain in the authoring roster. The packaged card
catalogue is `bc.better_discovery_guides.cards.v1`; the mod network protocol is 1. The new mod
identity starts fresh without saved-history migration. Cards, lessons, and tips are fixed
packaged catalogues for each release.

### Evidence and attribution

Outcome cards follow committed native events. First-use cards
also use personal points of need: closing an EMI recipe view, approaching a Font,
completed food use, and the first positive cave-pressure increment. Accepted movement
triggers bounded approach checks; inventory changes trigger bounded item checks. Providers
publish domain events; Learning Surfaces translates them. Outcome adapters remain pinned to
actual output, movement, or work boundaries. Another player's activity cannot stand
in for personal evidence.

Owned operations earn credit at success even while their owner is away or offline.
Persisted job, device, crop, route, or native operator identity supplies attribution.
Offline discoveries and their notices persist across reloads and deliver on reconnect.
No nearest-player fallback exists. Each card counts once per generation; repeated
signals and repeated attempts cannot inflate the score.

There is no hourly quota, quiet-period requirement, or discovery backlog scheduler.
The catalogue shapes pacing by choosing meaningful outcomes. Legitimate bursts remain
possible. All cards can be earned without another human. The World Condenser successor
card remains; integrated single-player Condenser lifecycle support is a separate future
work item, and current dedicated-server support does not require a second player.

### Reader and lineage

The reader shows discovered cards only. All plus seven topic filters cover World, Body,
Materials, Industry, Magic, Travel, and Lineage. Unread cards appear first; opening
selects and automatically develops the oldest unread card. Continue or Space after
reading selects and develops the next unread card across filters. A press that finishes
the illustration animation cannot also skip the explanation. There is no timed advance.

World Lifecycle Manager preserves the archive across successors. A successor resets
this generation's distinct-discovery score while preserving lifetime unique discoveries,
per-card generation counts, and read artwork. Ordinary death does not reset generation.
Re-encountering a known card may remind the player once in a successor without turning
old artwork unread again. The fallback server SavedData supports offline credit without
relying on death-cloned player NBT.

Native doorways appear only for installed working targets. The active roster retains
one Death's Door card; Body's six-region overview and contextual tips explain injury
details. Card copies remain cosmetic and grant no discovery to
their recipients.

### Death's Door and other teaching

The Death's Door card opens when the player enters that state. Native Body and
contextual tips cover persistent injuries, trauma, and treatment. Injury type chooses
medicine; region chooses impairment. HP healing, trauma expiry, and injury treatment
remain different systems.

Main-menu tips are stable per application launch. Esc-menu tips are disabled.
Final-death advice uses the committed final cause and shares
measured space with the native injury/treatment recap. The previously cited source
teaching guide is absent from the selected Learning Surfaces tree; the
[learning-surfaces contract](#learning-surfaces) and its linked authoring roster record
the available teaching definitions. The missing guide supplies no qualification claim.

### Review

All 53 card illustrations and 18 loading illustrations follow the
[no-humans art direction](world-and-player.md#thread-art-direction). The Learning Surfaces repository's required
local gate is `./gradlew verifyFull stageRuntimeJar`; isolated real-client fixtures
review compact/wide journal and native death-screen rendering. Provider repositories
verify their own outcomes. These checks do not imply pack deployment or full-pack tests.

## Learning surfaces

Better Content teaches through the systems the player encounters, not an achievement
ledger or objective graph. FTB Quests and its custom integration are not active pack
content. Their tasks and rewards are not transferred into Learning Surfaces or another system. The `better_discovery_guides` mod owns cards, lessons, and contextual tips; Threads remains the card reader and card vocabulary.

### Information ownership

| Surface | Responsibility |
| --- | --- |
| Loading lessons and Threads Lessons | Spoiler-free fundamentals and changed mental models, available before the relevant event |
| Main-menu tips | One stable preparation or possibility tip per application launch |
| Esc-menu tips | Disabled; the pause menu remains free of tip overlays |
| Death tips and native recap | Final-cause advice plus the current life’s injury and treatment history |
| Contextual Thread cards | A broader rule discovered through actual play evidence and remembered across the lineage |
| Item-hover annotations | Concise item-local facts, identical in inventory and EMI hover |
| EMI, Ponder, native guides and system screens | Exact recipes, apparatus, multiblocks, operating instructions, and native state |
| HUD, events and world feedback | Immediate controls, warnings, scouting, rescue, and situation-dependent guidance |

Use a shared concept ID when explanations address the same concept, while preserving
each surface's depth. Hover stays one or two concise lines; a lesson does not reveal
an unknown Thread. Native tooltips that already explain the pack model need no duplicate
annotation. No primary surface sends players to an achievement ledger for instruction.

Ore processing uses three contextual hover anchors. Chunks introduce String-Mesh
hand Sifting, crushed feeds contrast dry and waterlogged Sifting with Spouting,
and rinsed feeds state the four-at-2-bar pressure batch. The Encased Fan explicitly
corrects the retired Bulk Washing model; EMI owns exact chances and recipe layouts.

### Authoring contract

Start with the player action or misconception and identify the implementing recipe,
event, configuration, tag, or owning custom mod. Read that authority before writing
copy. Possession must not be described as operating a machine, and intended future
behavior must not be presented as implemented.

Preserve stable concept, Thread, and lesson identities when their meaning is unchanged.
Learning Surfaces has 53 live cards across seven topics; the Lessons reference and loading
rotation share 18 lessons. The main-menu and death contexts draw from a 199-tip
catalogue (Esc tips are disabled). Follow [Threads](world-and-player.md#threads)
for the single-explanation schema, personal trigger evidence, native doorways,
and lineage persistence. Domain mods
own their gameplay events; Learning Surfaces adapters translate those events into card signals.

Annotate pack transition families and curriculum-important systems with a natural
item anchor unless native hover already teaches the relevant model. Leave ingredients,
layouts, and ordinary uses to EMI; leave dynamic stack state to the owning mod. Keep
non-item guidance on its event, HUD, loading, Thread, or world surface.

Teaching does not mint commerce spirits or restore retired rewards. Thread facsimiles
remain cosmetic; lessons and the reader are not progression gates. Commerce spirits
come from credited player kills under the native-mapping and fallback rules described
in [Content ownership](world-and-player.md#content-ownership).

### Writing tone

Use clear, direct language on every Better Content-owned learning surface. Name the
mechanic, explain the rule, then give a useful action. Titles describe their subject;
proper names such as Threads, Source, and Dimensional Fonts remain unchanged.

Do not add lore, metaphors, personification, rhetorical invitations, or filler to
instructions. Replace internal design terms such as "bootstrap", "authority", and
"progression branch" with what the player can do or needs. Keep concrete requirements,
limits, costs, and warnings. Short copy does not need a minimum word count.

Threads use a title, the event experienced, its cause, and a useful next action. Hover
annotations remain one or two lines totaling at most 24 words. Apply this tone to
pack-authored guide notices, not to copied third-party guide prose or narrative design
documents.

### Readability and timing

- State the mechanical rule and a useful next action directly. Explain what
  the player can do; do not turn event predicates such as death or a terminal campaign
  outcome into instructions to seek that outcome.
- Preserve complete sentences. Wrap headlines, rules, and actions; use visible scrolling
  when they cannot fit. At narrow GUI widths, catalogues use one column. Decorative art
  may yield space to text, but art keeps its original aspect ratio.
- Use readable neutral text on a dark backing. Aspect pigments belong in badges and
  accents; dark pigments must not become the only way to read a label. Ordinary pack
  hover text uses light gray, with explicit wording for warnings.
- Loading copy fits its actual wrapped height and never delays world generation.
  Initial joins carry the current lesson into a paused World Ready screen until
  the player selects Begin or uses Enter, Space, or Escape.
  A lesson needs eight seconds of rendered exposure to advance loading history; quick
  joins and loading stalls do not count as reading. All lessons remain directly available.
- Movement lessons display the current loaded bindings. Menus must not consume the
  lifetime of a meal recap, and larger recaps need more reading time than a single line.
- Show a native doorway only when its installed target can be opened. Do not substitute
  a raw resource ID, a guessed key binding, or a vague message for a working guide.
- Keep player-authored Trace notes distinct from curated instructions. Their usefulness
  depends on what players leave; the pack supplies readable, contextual presentation.

Cards credit owned remote work at its actual successful outcome, including while the owner is
offline. First-use cards use explicit acquisition, completed use, screen-close, and approach
cues at the useful moment. Item checks run after relevant inventory changes; approach checks
run after accepted movement. Timed onboarding uses a due-time queue. These cues do not infer
machine operation from possession or proximity. No card requires another human; the World
Condenser remains limited by its current dedicated-server lifecycle support. There is no
hourly discovery quota or minimum interval.

Server and provider events own gameplay outcomes, ownership, lineage history, and rewards.
A bounded client report may credit only personal, non-reward teaching that the server
cannot observe directly, such as closing an EMI recipe. The server checks the report's
allowed action and target; it never treats that report as proof of an owned machine result.
The mod's three catalogues are packaged and fixed for a release. Resource packs may
change artwork but cannot reload copy or trigger rules while running.

All card and loading illustrations exclude humans, humanoids, humanlike spirits, body parts,
silhouettes and mannequins. Use concrete objects, mechanisms, environments, and nonhumanoid
creatures to explain the event. All 53 card illustrations and 18 loading/Lessons
illustrations use reviewed Journal copperplate art; the card copy and triggers are active.

### Review and validation

- Trace every changed claim and trigger to its implementation; inspect related surfaces
  for contradictory copy or duplicated authority.
- Check stable concepts, current native doorways, bounded copy, and relevant existing
  repository-local tests. Follow [Thread Art Direction](world-and-player.md#thread-art-direction) for art.
- Review reader layout, loading presentation, and development crossfades manually when
  changed. Automated tests must not synthesize mouse movement or clicks.
- Report changed player-visible behavior and verification. Run pack suites only when
  explicitly ordered through `test.main.kts`; otherwise state that pack testing was
  intentionally omitted under the Dev/Dist/Debug testing policy.

Do not recreate a quest compiler, layout harness, atlas exporter, or parallel validation
framework. Durable guidance belongs here; raw evidence belongs outside living docs.

### Main-menu and death tips

The fixed 199-tip catalogue declares each tip's eligible surface, requirements, relevant
mods, concept, and mechanical source. The main menu chooses one stable preparation or
possibility tip per application launch. Esc-menu tips are disabled; existing pause
entries remain in the catalogue but are not shown. Death uses the committed final
cause, including the injury provider's final-death event when present, and stays
fixed until respawn or logout. Contextual relevance takes priority over unseen novelty.

Each active surface keeps its own client-local exposure history. A tip counts as seen only
when its copy is actually rendered. Hidden or clipped advice does not consume history.
The native injury recap owns death controls and space; Learning Surfaces uses its measured
remaining area and omits a tip if the full copy will not fit. Tips never delay respawn
or alter normal and hardcore death controls. The packaged catalogue is fixed for a
release; invalid packaged data fails validation rather than silently swapping copy.

## Thread art direction

Cards illustrate a concrete cause and its visible consequence. The image supports the
explanation of what happened; a generic achievement emblem or unexplained fantasy scene
is insufficient. Every card and design may change when its teaching improves.

### No humans

All card and loading/Lessons art excludes humans, players, villagers, humanoid mobs,
humanlike spirits, humanoid silhouettes, mannequins, body parts, and distant background
figures. Nonhumanoid animals and creatures are permitted when relevant. Injury concepts
use damaged materials, medicine, impact, or shelter; they do not require anatomy.

### Shared visual language

Use copperplate or etched archive illustrations with precise lines, deep ink shadows,
parchment highlights, restrained mineral pigments, and a strong physical focal point.
Keep mechanical relationships legible at runtime size. Avoid letters, words, numerals,
frames, UI, logos, and watermarks. Code owns framing and typography. Do not impose
unrelated occult symbols, forced aspect links, or recurring mystery motifs.

Card masters are 2:3 portraits. Ship 256×384 full-color images and grayscale thumbnails,
plus cosmetic item derivatives whose predicate follows the global card order 1–53.
Loading masters are 2:1 landscapes and ship at 512×256. Seven topics organize the reader;
they do not require a whole-image tint or the retired four-suit composition rules.

### Editable source and review

The 53 card scenes and teaching definitions are maintained in
[`authoring/discoveries.json`](../../mod_source/better-discovery-guides/authoring/discoveries.json).
The previously cited `authoring/art-grammar.txt` is absent from the selected source;
the [art direction above](#thread-art-direction) records the available visual rules.
Exact prompts, original image-generation paths, reviewed masters, contact sheets, and
hashes were cited in a historical review bundle recorded in the operation handoff;
that citation alone does not establish current visual qualification.

`authoring/prepare_art.py REVIEW_BUNDLE` creates deterministic runtime derivatives and
facsimile model overrides. It does not generate new artwork. Visually inspect every
master for forbidden figures, subject accuracy, text artifacts, and small-scale clarity,
then review the actual compact/wide reader and loading surfaces. Preserve earlier
review bundles as superseded evidence rather than mixing them into the shipped roster.
