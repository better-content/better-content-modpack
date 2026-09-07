# Crafting graph policy

The crafting graph is every survival acquisition or transformation edge, not
only crafting-table recipes. It includes recipes, machine processes, rituals,
loot, trades, world generation, entity drops, fishing, quest rewards, guide
recipes, and custom runtime hooks.

`kubejs/config/crafting_policy.json` is the machine-readable
`bc.crafting_policy.v1` contract. It classifies every loaded mod namespace,
names authority domains and family selectors, records capability proofs, and
lists acknowledged debt. `docs/balance_policy.md` is the human-readable change
register. These two files are authoritative; `cross_mod_boundaries.json` and
`magic_parenting.json` contain references only. Ars glyph grammar remains in
`formal_magic_domains.json` because those entries describe real spell grammar,
not duplicated ownership policy.

## Enforcement

An unknown loaded namespace blocks startup. Family/authority leaks and live
consumers of cuts are reported with exact recipe and item IDs by the final
KubeJS recipe pass. That report intentionally describes the upstream graph at
the start of recipe application; it is a migration/backlog inventory, not a
claim that the reported edges survive the pack's later removals. The effective
post-application runtime snapshot is authoritative for shipped recipe edges.
A reported category becomes blocking only after its known backlog is zero.
Creative/debug items remain classified as technical rather than survival graph
roots.

Recipe cuts and canonical-duplicate selectors are also applied to the global
loot modifier from this same contract. Exact selectors and namespace/prefix
selectors resolve against the live item registry, avoiding a second hand-kept
loot denylist. Static config, guide, quest, trade, worldgen, and custom-hook
checks cover the other acquisition surfaces.

Pretty Pipes uses a visible three-tier Ratlantis ladder. The Courier Lattice recipe yields four
lattices and each lattice-rooted pipe recipe yields eight pipes, so the first expedition supports
a 32-pipe starter network. A blank module visibly consumes an Oratchalcum Mechanism. The seven
high crafting, extraction, filter, high-priority, low-priority, retrieval, and speed modules each
visibly consume one Arcane Logistics Core; their low and medium recipes retain upstream costs.

Selectors are family-level wherever possible: namespace, tag, ID prefix, or an
exact ID when no stable family exists. Capability roots prove the first
meaningful entry into a system; they do not replace every downstream recipe.

Wood compatibility preserves dedicated outputs instead of merging wood
identities. Quark's named ladder families accept their canonical and vertical
planks, while every other loaded item in `#minecraft:planks` is accepted by
the oak-ladder recipe. The fallback changes recipe compatibility only; it does
not classify those planks as oak or replace their normal wood-family tags.

## Invariants

- Nonliving matter consumes finite substrate. Biological growth and breeding
  may remain renewable.
- Space may be costly and finite, including AE2 spatial IO, but not infinite or
  remotely universal.
- PowerGrid owns stationary electrical generation. Electrical consumers,
  storage, pressure, gas, SU, and OC2R remain supported.
- Tinkers' Construct owns conventional pickaxe/axe/shovel/hoe/sword capability.
  Unique weapons, bows, armor, creature gear, and material integrations remain.
- The vanilla End is inaccessible. End ecology needed by the pack is routed to
  Creating Space orbit, Ratlantis, Overworld cultivation, or Ice and Fire.
- Quest SNBT is hand-authored. No generative quest graph is a production input.

Vanilla water/lava source renewal and renewable stone loops are acknowledged
noncompliance for this release. PneumaticCraft's infinite-source exception is
off, and existing Create hose-pulley/bottomless-fluid safeguards remain. A
strict finite-fluid migration is deferred; Flowing Fluids and a custom fluid
implementation are intentionally not introduced here.
