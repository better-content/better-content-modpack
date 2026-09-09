# Content ownership

The modpack owns cross-mod progression policy, six Machine Blocks, genuinely
cross-mod transition items, exact era-root recipes, optional transport gates,
the milestone ledger, and player documentation. Owning mods keep lifecycle and
runtime logic.

| System | Owner | Pack boundary |
| --- | --- | --- |
| Deposits, chunks, samples, processing, grinding balls, canonical outputs | Realistic Ores | Supplies acid/era inputs only; no duplicate ore registration |
| Radioactive profiles, disturbance persistence, emissions | Latent ChemLib | Provides progression recipes and milestone recognition only where the accomplishment remains salient |
| Heat storage/transport and Create Boiler Heater | Heat Sync | Provides era placement; native UI and hover own operating facts |
| Dimension Font obelisks, travel sessions, charge, and arrival sites | Dimension Drink | Pack recipes and quests may point to Fonts; the mod owns neutral charge generation/drain, session lifecycle, chunk tickets, and destination placement |
| Blood Altar bootstrap and tier reference | Blood Magic | Still-Beating Heart plus overworld materials opens the altar; Blood Magic's in-game guide remains the authoritative Tier 1-5 multiblock reference |
| Occult physical components | Hexerei | The mixing cauldron requires Aether, Nether, Bumblezone, and Ratlantis trophies equally; Occultism retains rituals, spirit fire, bindings, servants, and the finite dimensional matrix |
| Occult storage | None | Controllers, stabilizers, remotes, satchel, wormholes, mineshaft, and miners are closed; finite AE2 storage and visible Ratlantis-rooted logistics remain |
| Vanilla boat durability, vessel-drop suppression, reinforced recipes | Better Content Fixes | No boat mutation or hiding scripts |
| Coin acquisition, purse storage and pickup routing, merchant currency, village commerce, loot replacement, and wandering-trader lifecycle | Better Content Economy | The standalone mod owns combat and chest income, seven direct-coin purse slots and their survival-inventory panel, the exact 312-row villager catalogue, exact emerald-to-copper conversion for external merchants, emerald-to-coin loot policy, scheduled themed offers, the village-starter offer, Wares agreements, and the optional Font map |
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
Scripts do not scan arbitrary recipe JSON, inspect namespaces, or silently skip
mandatory roots.

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

Annotations correct a materially wrong or incomplete mental model. They do not
repeat ingredients, layouts, attributes, or ordinary uses that EMI and native
tooltips already communicate. Dynamic stack state remains owned by the source
mod. World events, controls, onboarding, scouting, and other guidance without a
natural item anchor remain on their event, HUD, or world surfaces.

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
| FTB Quests | A secondary achievement ledger and optional teaser, never a tutorial, dependency graph, or recipe guide |

Shared concepts align equivalent explanations, but each surface keeps its own
depth. A loading lesson may lead to a known Thread and its native doorway; it
does not reveal an unknown card. Hover annotations stay concise and never grow
into lesson prose merely to reproduce another surface.

## Achievement ledger

FTB Quests is not a progression guide. Its live authored surface is three
always-visible, independent ten-node chapters—World, Works, and Powers—plus six
optional completionist chapters. Milestones record durable accomplishments and
tease major possibilities; they contain no dependency graph, quest links,
recipe chains, onboarding instructions, or item-local explanatory prose.

Gameplay criteria, stack predicates, dimension or structure tasks, and only
then exact item tasks prove completion. Every player-visible completable quest
awards an authored Create Deco coin that remains manually claimed. Deleted
guide-node rewards are not redistributed. FTB visibility has no pack-authored
unlock policy or book-burning bypass.

The wandering trader is one shared temporary world visitor. Its first scheduled
arrival is after two active-server days, successful visits repeat every five
days, and themes rotate through Naturalist, Surveyor, Quartermaster, and
Antiquarian. Every themed visitor also carries one guaranteed, one-use village
starter offer: eight copper coins buy two ordinary unassigned villager spawn
eggs. Raw vanilla and third-party wandering offers are removed; only the curated
themed stock, starter offer, Wares agreement, and dimensional Font-map adapter
are allowed.

The coin purse is the existing seven-slot `better_content_economy:coin_purse`
Curios store, restricted to the seven direct Create Deco coin items. The same
storage is visible as an attached 2x4 panel in the survival inventory; its eighth
cell is intentionally inactive. Ground-picked direct coins fill matching purse
stacks and then empty purse slots before ordinary inventory, while coin stacks
and all non-coin items retain normal pickup behavior. Offers exposed by
non-vanilla `AbstractVillager` merchants convert exact emerald stacks in either
cost position or the result to the same count of copper coins without changing
uses, demand, XP, price multipliers, or other offer metadata.

## New worlds and backups

The refactor supplies no old-world identity migration. Create a tested backup
before updating an existing world. New worlds are the supported baseline because
deposit identities, placed small chunks, disturbed radioactivity, and Machine
Block identities all participate in saved state.
