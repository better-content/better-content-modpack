# Questbook Standards

## Purpose

FTB Quests is Better Content's secondary achievement ledger and optional content teaser.
It records a small number of meaningful things the player has accomplished and
rewards those accomplishments without issuing commerce spirits. It is not a progression
guide, recipe map, onboarding flow, checklist of intermediates, or second copy
of EMI.

The nine live SNBT chapters are the only quest source. A generated graph,
generated quest directory, crate/screen staging data, or compiler hook must not
participate in authoring, packaging, or runtime loading.

The live non-completionist book contains exactly three chapters:

- **World** records settlement, self-sufficiency, vessels, dimensions, and
  planetary travel.
- **Works** records functioning material, factory, power, rail, storage, and
  aerospace capabilities.
- **Powers** records encounters with Blood, Fonts, folk and spirit traditions,
  formal magic, and Lineage.

The six completionist chapters remain optional collection ledgers for armour,
consumables, effects, enchantments, plants, and TConstruct weapons.

## Milestone Contract

World, Works, and Powers each contain ten high-salience milestones. Every
milestone is visible immediately, stands independently, and has no quest
dependency or quest link. Layout groups related accomplishments without
implying a route or required order.

A milestone belongs in the book only when completing it says something durable
about the player's relationship to the world. Ordinary ingredients, machine
parts, recipe steps, upgrades, and intermediate acquisitions do not qualify.
When several components are needed to represent one capability, keep them as
tasks on one milestone instead of restoring a chain of smaller quests.

Use the strongest available proof:

1. a gameplay criterion for an operation, structure, or event;
2. a stack predicate when meaningful state is carried on the item;
3. a dimension or structure task for arrival and discovery;
4. an item task only when possession accurately states the accomplishment.

Do not use dependencies to approximate completion. Do not use manual
checkmarks. A component pile must not be described as a functioning machine.

## Copy and Information Ownership

Milestone descriptions use one terse sentence naming the accomplishment or
possibility. They tease breadth without explaining how to traverse it. Do not
include ingredient lists, crafting layouts, route instructions, era language,
or navigation boilerplate.

Hover annotations own short item-local corrections, lifecycle or hidden state,
direct capabilities, process authority, provenance, scope boundaries, and exact
operating requirements. EMI and native recipe or guide surfaces own recipes,
uses, multiblocks, and apparatus detail. Event, HUD, onboarding, and world
surfaces own controls, warnings, scouting, rescue, and other non-item actions.
The achievement ledger must not duplicate those facts merely because a task
uses the same item, and no primary surface may direct players to a quest as its
authoritative explanation.

Annotate every pack-owned transition family and every curriculum-important system
with a natural item anchor unless its native hover already explains the relevant
pack model. Keep the annotation to one or two concise lines and leave recipes,
dynamic state, and full procedures on their authoritative surfaces. Create a
milestone only when the completed act is worth remembering.

## Rewards

Player-visible quests award experience bottles rather than commerce spirits.
Rewards remain manually claimed and retain their stable reward identities.
Spirits prove a credited player kill and therefore must never be granted by a
quest, loot table, kit, recipe, ore, or other non-combat source. Hidden technical
records, if any are introduced in the future, are not player-visible quests and
receive no reward.

## Stable Identity and Migration

Preserve chapter, quest, task, and reward IDs whenever the accomplishment and
proof retain the same meaning. Moving a retained milestone between chapter
files does not justify changing its ID. Removing an obsolete guide node removes
its unclaimed reward and presentation; do not preserve dead nodes as invisible
history.

FTB Quests 2001.4.21 parses hexadecimal IDs as signed longs. New IDs must be
unique uppercase 16-digit values in `0000000000000002` through
`7FFFFFFFFFFFFFFF`. Preserve an existing high-bit identity with its negative
two's-complement hexadecimal spelling everywhere it is referenced. Never mix
the unsigned display form with the signed on-disk form.

## Completionist Chapters

Completionist chapters may be dense because exhaustive collection is their
explicit purpose. Entries remain independent, use automatic tasks, have unique
and visually legible icons, and grant exactly eight experience bottles. They do not
feed World, Works, or Powers and never gate ordinary play.

Update a completionist roster only when the active obtainable content changes.
Quarantined, hidden, unobtainable, or visually indistinguishable content does
not receive a collection entry.

## Authoring Review

For every milestone change:

1. Verify the accomplishment against its recipe, event, criterion, predicate,
   structure, dimension, or owning custom mod.
2. Confirm the node has no dependencies or quest links and is visible from the
   start.
3. Confirm the task proves exactly what the title and sentence claim.
4. Confirm any reward is manually claimable and does not issue a commerce spirit.
5. Render the affected chapter with the supported standalone layout harness and
   inspect icons, spacing, labels, and clipping.
6. When pack-level testing is explicitly requested, run `./test.main.kts all` and confirm FTB loads three milestone chapters plus the six
   completionist chapters.

Do not add a quest compiler, linter, audit framework, generated report, or
historical design appendix. Fold durable policy changes into this file and keep
raw evidence outside the repository.
