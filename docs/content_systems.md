# Content ownership

The modpack owns cross-mod progression policy, six Machine Blocks, genuinely
cross-mod transition items, exact era-root recipes, optional transport gates,
learning surfaces, and player documentation. Owning mods keep lifecycle and
runtime logic.

| System | Owner | Pack boundary |
| --- | --- | --- |
| Deposits, chunks, samples, Sifting/Spouting/pressure processing, canonical outputs | Realistic Ores | Physical and magical routes converge on shared products; no duplicate ore registration |
| Radioactive profiles, disturbance persistence, emissions | Latent ChemLib | Provides progression recipes; the mod owns physical behavior and its gameplay events |
| Heat storage/transport and Create Boiler Heater | Heat Sync | Provides era placement; native UI and hover own operating facts |
| Dimension Font obelisks, travel sessions, charge, and arrival sites | Dimension Drink | Pack recipes and learning surfaces may point to Fonts; the mod owns neutral charge generation/drain, session lifecycle, chunk tickets, and destination placement |
| Blood Altar bootstrap and tier reference | Blood Magic | Still-Beating Heart plus overworld materials opens the altar; Blood Magic's in-game guide remains the authoritative Tier 1-5 multiblock reference |
| Occult physical components | Hexerei | The mixing cauldron requires Aether, Nether, Bumblezone, and Ratlantis trophies equally; Occultism retains rituals, spirit fire, bindings, servants, and the finite dimensional matrix |
| Occult storage | None | Controllers, stabilizers, remotes, satchel, wormholes, mineshaft, and miners are closed; finite AE2 storage and visible Ratlantis-rooted logistics remain |
| Vanilla boat durability, vessel-drop suppression, reinforced recipes | Better Content Fixes | No boat mutation or hiding scripts |
| Player-kill spirit release, spirit commerce, seven village professions, and wandering-trader lifecycle | Better Content Economy | The standalone mod owns credited-kill release through Malum's animated spirit entities, the exact 245-offer villager catalogue, seven matching stations/outfits/behaviours, seven 13-good wandering stocks, and matching villager-egg offers; coins, emerald commerce, and eldritch/umbral trade are closed |
| TCon alloy composition and casting | Tinkers' Construct | Removes bypasses and authors exact cross-mod alloy recipes |
| Kinetic assembly | Create | Uses Machine Blocks only at listed direct roots |
| Pressure chemistry | PneumaticCraft | Pack authors bounded cross-mod acid/root recipes |
| Electrical components and stationary generation | PowerGrid and MoreRed | Electrical Block starts PowerGrid's first generator/design roots; competing stationary generators are cut |
| Dragon ecology and materials | Ice and Fire | Replaces vanilla dragon-boss products; rare Overworld ecology supplies tagged products and equal-stat Dragonsteel TCon materials |
| Aerospace components | Creating Space | Space Block starts three aerospace roots |

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

Authoring and review follow [Learning surfaces](learning_surfaces.md). There is no
active achievement ledger or replacement reward system.

## Spirit commerce

The seven ordinary village professions correspond one-to-one with sacred,
wicked, arcane, aerial, aqueous, earthen, and infernal spirits. Each has a
matching coloured workstation, outfit, local utility behaviour, and 35-offer
catalogue priced only in its own spirit at 1–8. Other employed villager
professions are normalized into these seven; raw vanilla and third-party offers
are removed. Eldritch and umbral spirits never appear in commerce.

Wandering traders use the same seven identities, colours, and matching payment
spirits. Every identity carries 13 themed goods plus one one-use offer of two
matching spirits for two villager eggs already assigned to the corresponding
profession. Player-credited kills release spirits through Malum's floating, homing
item entities. Malum-native mappings remain authoritative, including mapped passive
animals such as cows, pigs, sheep, and chickens. Only the unmapped fallback is
hostile-only: it deterministically releases two ordinary spirits. Spawner-origin
mobs, already-soulless victims, and economy actors release none. The native Spirit Pouch is
the sole supported specialist storage surface. Create Deco coins, coin stacks,
wallets, coin recipes, emerald-priced offers, Wares/Font adapters, and specialist
harvesting tools are inert or hidden.

## New worlds and backups

The refactor supplies no old-world identity migration. Create a tested backup
before updating an existing world. New worlds are the supported baseline because
deposit identities, placed small chunks, disturbed radioactivity, and Machine
Block identities all participate in saved state.
