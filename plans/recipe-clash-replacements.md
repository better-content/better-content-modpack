# Recipe-clash replacement plan

Status: proposed content decisions; no gameplay changes, deployment, packaging or runtime tests authorized by this document.

## Scope and truth boundary

Identify accidental same-context matching overlaps and give every reviewed family a deterministic acquisition route. Polymorph remains a compatibility fallback, not a resolution. Stonecutter/woodcutter selectable outputs are not crafting clashes. Same-output duplicates require separate checks for yield, NBT, remainders, durability, cooking time and experience.

The recovered post-rock review contains 405 historical conflicting-output pairs in 100 connected groups. Those are a checklist, NOT an exact current-build count or a universally complete audit. Group numbers below refer to that review and are only navigational. The original model omitted custom matching semantics and only screened machine recipes. A removed/renamed recipe must be retired from the checklist, not resurrected to satisfy an old count.

Current source directly confirms the canvas/two-thatch square overlap, identical four Create root recipes, eleven chest-and-lever recipes with different wood NBT, and global literal-sand substitution. The historical `kubejs:primitive/straw_string` ID is absent from current authored scripts; current `kubejs:primitive_workshop/string_from_straw` is three horizontal straw. The oak ladder source already attempts disjoint family/fallback tags. Both cases require effective current recipes rather than assuming historical witnesses still apply.

Universal/current-runtime completeness remains pending exact-candidate loaded recipe/tag/registry capture and inspection of native custom matchers. Planning does not authorize those pack runtime actions.

## Design rules

1. Preserve material identity, native storage authority, contents and progression gates.
2. Prefer disjoint ingredients over ornamental rearrangement. Shaped recipes mirror and translate; shapeless recipes match every arrangement of their multiset.
3. Reserve conventional silhouettes: plank ring for chest, five-plank U for boat, 2x2 straw for canvas, three horizontal blocks for slabs, six planks for trapdoors, 3x3 material for compression.
4. Generic functional rock/sand uses remain compatible. Identity products use explicit material families; never globally narrow compatibility to fix a few outputs.
5. Decorative alternatives move to stonecutting/woodcutting or distinct shaped assembly. Do not introduce electric/industrial gates into handmade products merely to distinguish them.
6. No material duplication or cheaper gate bypass: conversion input/output counts conserve the existing substrate; uncommon catalysts must be consumed or have explicit remainder handling.
7. Do not silently merge unique mod materials. Canonicalization proposals below need a consumer/tag/loot/runtime review before changing ownership.
8. No direct replacement of custom storage recipes with static NBT outputs. Use native copying/predicate behavior or a narrowly reviewed native adapter.
9. Proposed patterns are not certified collision-free until compared against the full effective graph, including mirrors, translations and shaped/shapeless intersections.

## Concrete high-priority patterns

Spaces below are empty slots. Symbols are local to each example. Existing outputs/yields remain unless a reviewed conservation correction is explicitly required.

### Straw and canvas

- Canvas: preserve native `SS / SS`, S = Farmer's Delight straw.
- String: preserve current `SSS`, S = straw, producing two string; remove obsolete conflicting pack recipes only if actually loaded.
- Rope: preserve native vertical straw route if the effective matcher confirms it is disjoint from current string.
- Quark thatch: propose `SS / SS / SS`, six straw, with yield chosen from the existing straw-to-thatch material ratio.
- Dawn wheat thatch: propose `SSS / SSS`, six straw, same reviewed material ratio. This is not a rotation of the Quark pattern under native matching.
- Replace/remove upstream wheat-based thatch overlaps as well; adding straw patterns alone leaves those conflicts.
- Dawn wattle-and-daub: propose `SCS / CSC`, S = straw, C = clay, retaining a reviewed bulk yield.
- Supplementaries daub: propose `CC / SS`, C = clay, S = straw. Check both against all shaped and shapeless daub routes.
- Coaster: preserve its small native route. Doormat: propose `CCC`, C = canvas, instead of the current two-canvas row; review resulting yield/cost.

### Storage

- Ordinary chests keep the plank ring.
- Remove native `sophisticatedstorage:generic_barrel` overlap and make generic barrels use native barrel + lever conversion. Do not require a chest-shaped generic barrel fallback.
- Named Sophisticated barrels retain the pack's planks-with-central-lever design after full graph comparison.
- Named Sophisticated chests: propose ` P / C / L` (one vertical column), P = the desired exact wood-family plank, C = native ordinary chest, L = lever. Add P to make eleven wood outcomes disjoint; remove the independent oak conversion overlap. This is a native storage conversion design, NOT permission to lose chest contents.
- Reject packed/content-bearing upgrade inputs through native predicates. Tier upgrades and shulker conversions keep their native item-copying semantics.

### Handmade utilities

- Rain collector: propose `PCP / P P / PPP`, P = any supported plank, C = canvas. Distinguishes collection lining from a boat without requiring metal.
- Cold Sweat boiler: propose a furnace plus copper fittings, ` C / CFC / C`, F = furnace, C = existing supported copper form. Confirm native tier/remainders before assigning the actual item.
- Eureka ballast: propose stone with an iron fitting rather than an empty rock ring; preserve a finite bulk-rock cost and native vehicle semantics.
- Bee frame: keep native frame if possible. String mesh: propose `SFS / FFF / SFS`, S = stick, F = string. Compare with all nine-slot string/wood recipes before settling the yield.
- Sand casts: move blanks away from 2x2 sandstone compression. Propose `SSS / S S` for ordinary blanks and the same shape with exact red-sand family for red blanks. Preserve original sand consumption per cast by recalibrating output, not arbitrary increased cost.

### Create Powered Works

Do not retain the loop that assigns all four roots ` A / AMA / A`. Preserve copper Machine Block M as the entry gate and distinguish each mechanical purpose:

- Water wheel: ` P / AMA / P`, P = supported plank, A = andesite alloy.
- Large wheel: upgrade an existing small wheel W: `PPP / PWP / PMP`. Confirm the small-wheel prerequisite and output count do not compound an unintended gate tax.
- Mechanical pump: ` P / AMA / S`, P = native fluid pipe, S = shaft.
- Windmill bearing: ` S / AMA / C`, S = shaft, C = native cogwheel.

These are proposed candidates, not verified available recipes; all referenced native IDs and acquisition gates must be checked before implementation.

### Identity-safe generic ingredients

- Sand: replace the unconditional literal-sand rewrite with generic-by-default substitution excluding reviewed identity recipes, following the rock policy's native exact-ID filter semantics. Add sandstone, colored sand layers, colored sandpaper and colored casts to the identity review.
- Slime blocks: vanilla block uses vanilla green slime balls; TCon sky/ichor/ender blocks use their own exact balls. Keep generic slime substitution for adhesives and functional components.
- Skyroot sticks: dedicated Skyroot route remains; generic ordinary-stick ingredient excludes Skyroot planks, or provides a separate explicitly intentional conversion that cannot overlap the dedicated route.
- Skyroot bucket: retain its native material outcome and move away from the bowl silhouette (propose tall `P P / P P / PPP`); ordinary bowl remains three-plank V.
- Ladders: preserve current canonical/vertical family tags and oak fallback, prove their live disjointness, and fix later broadening rather than removing compatibility.
- Material-specific altar/lamp variants: partition input families so a specialized blackstone/deepslate input never also makes the generic variant. Generic functional acceptance can be retained through a disjoint fallback tag or an explicit finishing route.

## Exhaustive disposition matrix for the 100 recovered groups

Each row is a proposed resolution; current activation, exact IDs and matcher equivalence remain implementation prerequisites. Rows sharing a policy should be fixed together, then checked globally. A decision to keep a deliberate choice must be explicit and station-specific; do not mark it as an eliminated overlap.

| Group | Conflicting recipe family | Proposed disposition |
|---|---|---|
| 1 | Immersive Weathering leaf piles / Ube leaf feast / duplicate Hexerei integration piles | Keep native leaf-pile row. Feast adds a food-binding ingredient (existing rice/food input chosen after food-era review), so it cannot consume only leaves. Resolve duplicate leaf-pile outputs by canonical tree/material owner, not random recipe order. |
| 2 | Eleven `kubejs:storage/*_chest_container` / native oak chest conversion | Exact-family plank + chest + lever; remove redundant oak conversion; preserve native storage copying. |
| 3 | Rain collector / wood boats / Dawn roof supports | Keep boat U; canvas-lined collector; roof supports use their own beams/slabs or finishing route instead of the same plank U. |
| 4 | Skyroot bed / sixteen vanilla colored beds | Vanilla bed inputs exclude Skyroot where it has a dedicated native bed. Offer color conversion of the Skyroot bed only if native colored variants exist. |
| 5 | Farmer's Delight, Quark, Ube produce crates / Rats assorted vegetables | Pick a canonical compression output per crop after tag/consumer review; alternatives convert from canonical crate via deliberate woodworking. Assorted vegetables requires genuinely distinct ingredient categories, not repeated members of one broad vegetable tag. Preserve compression/decompression conservation. |
| 6 | Native mod wood chests / Sophisticated generic barrel | Keep wood chests; barrel + lever native conversion replaces chest-shaped generic barrel fallback. |
| 7 | Fourteen Quark wood ladder outputs | Verify current family tags and oak fallback are disjoint in the effective graph; remove any later input broadening. No new arbitrary ladder shapes. |
| 8 | Generic/Quark furnaces / Cold Sweat boiler / Eureka ballast | Keep generic furnace; consolidate same-output furnace redundancies only after semantics review; boiler and ballast get functional identifying components. |
| 9 | Handmade normal/red blank casts / vanilla and pink sandstone | Identity-safe sand inputs plus non-square blank-cast patterns. |
| 10 | Four `kubejs:powered_works/direct_root/*` roots | Separate wheel/pump/bearing assembly patterns while preserving Machine Block gate; large wheel becomes a reviewed small-wheel upgrade. |
| 11 | Vanilla shulker box / Sophisticated tier chest-to-shulker conversions | Vanilla accepts only ordinary chest family; Sophisticated accepts exact native storage tiers with contents-copy predicates. Do not solve with static results. |
| 12 | Five Goety dark-altar material variants | Keep common Blood/pedestal gate; partition material identity inputs and generic fallback. |
| 13 | Vanilla charcoal / Dawn charred stripped spruce / Malum arcane charcoal | Vanilla charcoal excludes specialty Runewood/Soulwood inputs that have arcane routes. Dawn charred spruce moves to a deliberate finishing/charring process; do not rely on furnace order. Preserve relevant costs and fuel yields. |
| 14 | Canvas / Dawn thatch / Quark thatch | Reserve canvas 2x2; distinct six-straw portrait/landscape thatch rectangles with reviewed yields. |
| 15 | Hexerei mahogany panes/trapdoor / Nature's Spirit trapdoor | Nature's Spirit retains material authority; panes gain glass plus canonical mahogany frame; unique polished Hexerei decoration uses a finishing route if retained. |
| 16 | Three Ice and Fire dragon banners / Supplementaries dragon banner | Preserve typed skull + paper patterns. Supplementaries banner requires paper plus an existing typed pattern, or distinct dragon-scale ingredient; ensure no closed-End dependency. |
| 17 | Weathering sandy dirt / Nature's Spirit sandy soil / Unearthed siltstone | Do not broad-tag distinct geology outputs. Sandy soils get distinct substrate/ratio after native soil review; siltstone uses geological material or a consolidation process, not the same dirt/sand multiset. |
| 18 | Vanilla slime block / TCon three slime blocks | Exact slime identity per block; generic slime remains allowed for functional uses. |
| 19 | Dawn support slabs / Supplementaries shelf | Support slabs retain native material finishing. Shelf adds sticks/support hardware instead of three generic slabs alone. |
| 20 | Curved/straight raked gravel / TCon flint | Keep flint acquisition; raked gravel uses gravel plus a wooden rake-equivalent ingredient or stonecutting finishing, with explicit remainder handling if a tool is used. |
| 21 | Two Dawn daub routes / Supplementaries daub | Distinct six-slot weaving versus clay-over-straw layer patterns; remove duplicate Dawn routes where equivalent. |
| 22 | Waxed acacia/oak logs / Supplementaries fine wood | Keep exact-family waxed logs; fine wood uses a dedicated finishing recipe with distinct binder/input form, not the same eight logs + honeycomb multiset. |
| 23 | Blackstone/deepslate/generic stone lamps | Partition material identities; no generic recipe captures specialized material inputs. |
| 24 | Cobblestone chimney / Cold Sweat smokestack | Keep masonry chimney; smokestack includes metal fitting and/or boiler prerequisite, reviewed for early heat access. |
| 25 | Aether iron ring / Create Deco iron support | Keep wearable ring silhouette; support gets iron rods/bars plus joining component or larger support shape. |
| 26 | Skyroot bucket / vanilla bowl | Tall Skyroot bucket silhouette; keep bowl V. |
| 27 | Skyroot sticks / ordinary sticks | Disjoint dedicated-versus-fallback plank sets; preserve wood compatibility. |
| 28 | Alex's Caves metal barrel / Sophisticated iron barrel | Alex's Caves accepts ordinary barrel only; Sophisticated accepts native storage barrel and preserves contents. Narrow source predicates, not output-based blanket removal. |
| 29 | Alex's Caves uranium block decomposition / ChemLib uranium | Exact source-block identity gives its matching material; cross-system conversions, if justified, become explicit rather than implicit overlapping decompression. |
| 30 | Alex's Caves uranium shards / ChemLib uranium nuggets | Exact shard/nugget identity; no broad uranium tag in material-specific reassembly. |
| 31 | Bamboo hat / TCon punji | Hat adds string tie to bamboo; punji retains trap silhouette/cost. |
| 32 | Ars Energistique 64k / 256k portable cells | Larger tier upgrades exact 64k cell with native larger storage components; preserve Blood/AE gate and native stored Source/state. |
| 33 | Brewin' and Chewin' coaster / canvas doormat | Small coaster unchanged; three-canvas doormat candidate. |
| 34 | Cataclysm / Goety chiseled End stone | Deliberate stonecutter choices from exact input; remove competing table route. |
| 35 | Cataclysm chiseled purpur / vanilla purpur pillar | Keep vanilla pillar; Cataclysm finishing through stonecutting. |
| 36 | Cataclysm / Goety End-stone pillar | Distinct stonecutter outputs rather than competing table patterns. |
| 37 | Cataclysm / Weathering prismarine brick wall | Preserve both decorations via stonecutting choices; leave at most one table wall default. |
| 38 | Cataclysm / Supplementaries stone tile | Stonecutting choices from exact stone; preserve generic functional stone substitution elsewhere. |
| 39 | Cold Sweat Purity 1 waterskin cooking outputs | Inspect native NBT matcher and actual station types. One deterministic purification result per starting purity per station; separate campfire/furnace progression intentionally. Preserve water/temperature/capacity state. |
| 40 | Cold Sweat Purity -1 waterskin cooking outputs | Same purity-state policy as 39; no name-based assumption that `_campfire` means campfire type. |
| 41 | Bee frame / Create Sifting string mesh | Preserve frame; multi-string lattice mesh candidate; retain pre-powered sifting access. |
| 42 | Create normal/red sandpaper | Identity-safe exact color sand families; preserve generic sand for unrelated functional recipes. |
| 43 | Create Deco / Everything Copper bars | Preserve distinct decor via explicit finishing/stonecutter only if supported for these inputs; otherwise distinct rod-versus-ingot assembly. No automatic material merge. |
| 44 | Create Deco / Everything Copper door | One direct door assembly plus explicit decor conversion, preserving count/conservation. |
| 45 | Create Deco / Everything Copper trapdoor | One direct trapdoor assembly plus explicit decor conversion. |
| 46 | Create Deco / Weathering cracked red bricks | Choose one furnace default after material-owner review; other decoration via stonecutting or explicit conversion, never furnace ordering. |
| 47 | Create Deco / Weathering mossy bricks with moss | One mossing default plus deliberate alternate finish. Check recipe paths using either moss or vines together. |
| 48 | Create Deco / Weathering mossy bricks with vine | Same policy as 47; do not fix only one binder route. |
| 49 | Create Deco / TCon netherite nuggets | Canonical nugget per material after full consumer/tag review; exact nine-to-one round trip. |
| 50 | Dawn candlestick / Supplementaries candle holder | Candlestick gains its candle assembly; holder remains empty metal support. Respect existing candle/metal availability. |
| 51 | Dawn cobbled limestone / Quark polished dripstone | Exact geological input or stonecutter choice; no competing table polish. |
| 52 | Dawn lattice glass / Upgrade Aquatic glass trapdoor | Lattice glass requires wooden lattice frame; trapdoor retains glass assembly. |
| 53 | Dawn marble / Quark polished calcite | Exact material or stonecutting choices; do not implicitly redefine geological calcite as marble. |
| 54 | Dawn rammed dirt / vanilla mud bricks | Keep mud bricks; rammed dirt uses dirt/gravel consolidation with no overlap or substrate duplication. |
| 55 | Dawn sake cup / Thirst clay bowl | Keep bowl; cup uses narrow vertical clay profile or native pottery process. Do not turn drinking equipment into late machinery. |
| 56 | Dawn / Quark sandstone bricks | Stonecutting choices from exact sandstone; one table default if needed. |
| 57 | Dawn spruce low table / Twilight spruce banister | Low table uses tabletop slab + legs; banister retains railing pattern. |
| 58 | Upstream Dawn wheat thatch / Quark wheat thatch | Remove/replace both conflicting wheat-only routes consistently with group 14; retaining them would undo straw disambiguation. |
| 59 | Dawn waxed shutters / Farmer's Delight cutting board | Keep cutting board; shutters consume their wooden panel plus explicit wax/hinge component. |
| 60 | Everything Copper / Ice and Fire copper boots | Resolve armor ownership/balance first. If both retained, alternate requires deliberate trim/lining or upgrade of canonical armor; inspect stat/repair differences. |
| 61 | Competing copper chestplates | Same armor-family decision as 60; no four independent arbitrary silhouette changes. |
| 62 | Competing copper helmets | Same armor-family decision as 60. |
| 63 | Competing copper leggings | Same armor-family decision as 60. |
| 64 | Fallout blue industrial walkway / riveted slab | Preserve slab shape; walkway adds rail/grating/support input. |
| 65 | Fallout / TCon steel blocks | Canonical compression after material/consumer review; reversible nine-ingot conservation. |
| 66 | Fallout heavy-caliber casing yields 8 versus 9 | One reviewed yield/crafting path; audit wrench consumption/damage/remainder before retiring duplicate. Not a decorative pattern issue. |
| 67 | Fallout / TCon steel nuggets | Canonical material nugget and nine-to-one conversion; synchronize group 65. |
| 68 | Farmer's Delight rope / obsolete pack straw-string ID | Reconcile against current loaded graph first. Current source string is horizontal three straw; do not reintroduce absent two-straw recipe. Preserve native rope if now disjoint. |
| 69 | Fruits Delight / Quark apple crates | Apply canonical produce compression plus deliberate decorative conversion from group 5. |
| 70 | Goety burrowing / sensing focus | Preserve shared Blood gate; use distinct thematic cores (existing excavation reagent versus sensory reagent) inside the same gated slate frame. Verify native thematic ingredient IDs and acquisition tier before choosing them. |
| 71 | Goety shade stone / siltstone | Restore exact original material distinctions or distinct process, not two broadly tagged stone multisets. |
| 72 | Two Goety stash urn recipes with different output state | Inspect native NBT payloads; default empty urn, funded variant explicitly consumes its contained valuables and copies correct native state. Reject free embedded contents. |
| 73 | Hexerei / Nature's Spirit mahogany buttons | Nature's Spirit canonical wood products; unique polished Hexerei decorations only through explicit finishing if policy allows retention. |
| 74 | Mahogany doors | Same canonical-wood policy as 73. |
| 75 | Mahogany fences | Same canonical-wood policy as 73. |
| 76 | Mahogany fence gates | Same canonical-wood policy as 73. |
| 77 | Mahogany pressure plates | Same canonical-wood policy as 73. |
| 78 | Mahogany signs | Same canonical-wood policy as 73. |
| 79 | Mahogany slabs | Same canonical-wood policy as 73; preserve six-slab material ratio. |
| 80 | Mahogany stairs | Same canonical-wood policy as 73; review all source and cutting routes. |
| 81 | Ice and Fire / Occultism raw-silver decomposition | Exact source identity or approved canonical raw-silver material; preserve nine-to-one conservation and ore-processing ownership. |
| 82 | Ice and Fire / Occultism raw-silver compression | Same reversible material decision as 81; no asymmetric round trips. |
| 83 | Weathering normal/red sand layers | Exact sand identity; exempt from broadening. |
| 84 | Malum charcoal fragments / Quark charcoal black dye | Keep fragment decomposition; dye becomes fragment-based with material-equivalent yield or a distinct native grinding route. Do not require an unavailable pre-Malum reagent for basic black dye. |
| 85 | Malum Runewood wall / solid trapdoor | Preserve trapdoor; wall through supported woodcutting, or shaped posts + boards with explicit yield conservation. |
| 86 | Malum runic/plain small tainted-brick walls | Plain wall assembly; runic wall via native rune-finishing conversion from plain wall, preserving native rune costs. |
| 87 | Runic/plain small twisted-brick walls | Same rune-finishing policy as 86. |
| 88 | Runic/plain tainted-brick walls | Same rune-finishing policy as 86. |
| 89 | Runic/plain tainted-tile walls | Same rune-finishing policy as 86. |
| 90 | Runic/plain twisted-brick walls | Same rune-finishing policy as 86. |
| 91 | Runic/plain twisted-tile walls | Same rune-finishing policy as 86. |
| 92 | Malum Soulwood trapdoor / board wall | Same woodworking disposition as 85. |
| 93 | Vanilla coarse dirt / Unearthed conglomerate | Keep vanilla dirt/gravel mixing; conglomerate requires geological cement/consolidation or exact native geological input after geology-owner review. |
| 94 | Occultism candle / Untamed Wilds fat candle | Partition wax versus fat materials; if both use fat, one receives distinct wick/base assembly. Keep candle bootstrap accessible and preserve native uses. |
| 95 | Quark / TCon gold bars | Preserve distinct decorations via explicit finish or disjoint rod/ingot assembly; review output counts. |
| 96 | TCon / Twilight Knightmetal compression | Canonical block after material/consumer review; do not lose Twilight-native runtime identity if required. |
| 97 | TCon / Twilight Knightmetal decomposition | Same reversible canonicalization as 96, never a cross-identity duplication loop. |
| 98 | Native normal/red sand casts | Exact colored input families; cover native routes in addition to handmade blanks in group 9. |
| 99 | Deep Void flesh slab / tendrils | Slabs keep horizontal three-block pattern; tendrils use vertical `F / F / F` if native matching supports it, with conserved yield. Check flesh processing and drop semantics. |
| 100 | Deep Void peeping roots yields 4 versus 8 | One reviewed decomposition yield; compare log/wood input meanings, state and remainders before removing duplicate. Do not invent two outputs for identical input. |

## Identification and verification work before implementation

### 1. Reconcile exact current inputs

Record pack source revision/status and exact candidate inputs. Enumerate effective scripts, KubeJS data and all enabled datapacks plus native mod resources. Inspect final recipe ordering: checking the original JSON alone misses later `replaceInput` and generated recipes. Retire inactive/renamed historical entries. Recheck all 398 rock-related predicted removals against effective recipes, not just the 405 retained historical pairs. Inspect new recipes introduced since the audit, including Pretty Pipes and generated content.

### 2. Cover every matching context

- Crafting: native dimensions, holes, translation, reflection; full matching intersections for shaped/shaped, shaped/shapeless and shapeless/shapeless; strict NBT and custom ingredient predicates.
- Cooking: actual recipe type and matcher, input NBT/purity, result state, cooking time and experience. Different stations are distinct contexts.
- Native dynamic crafting: copying, repair, dyeing, decoration, container state, tier upgrades, TCon material modification. Inspect native `matches`, `assemble` and remainder behavior; model them or label unknown rather than assuming empty ingredients mean no overlap.
- Machines/rituals: input counts, fluids/amounts, catalysts, heat/pressure/tier/location and native recipe-selection UI. Identical normalized inputs are only candidates. Explicit selectable multi-output processes are not necessarily clashes.
- Retain same-output redundancies separately until semantics prove equivalence.

A fresh authorized runtime dump is necessary to certify the current loaded graph. Do not rerun the historical analyzer and call the result current. Do not introduce a retired general `tools/` audit framework.

### 3. Settle proposed choices

Approve the canonicalization/ownership rows (crates, metal forms, wood products, copper armor), yield rows (casings/roots), and NBT rows (storage/urns/waterskins) before editing. Most decoration rows should preserve both items through deliberate finishing rather than cutting content. All added ingredients must have real item IDs and reachable acquisition at the existing tier.

### 4. Implement coherently

Edit the owning scripts instead of stacking competing last-pass rewrites. Remove exact old recipe IDs and replace all native/pack alternate routes in the same family. Keep native custom serializers where state matters. Update relevant policy and script inventory, and add current authored test fixtures; no historical dump/report becomes a required test prerequisite.

### 5. Acceptance

- Every reviewed accidental overlap has a unique default matching result, a reviewed canonical conversion, or an explicit intentional-choice classification.
- Every proposal is globally compared against unchanged recipes, not merely its former opponent; tests include mirrored and translated grids and tag intersections.
- Old witnesses must no longer match competing outputs where still active; new witnesses prove intended output and counts/NBT/remainders.
- No identity substitution leaks, progression bypass, nonliving matter generation or compression round-trip profit.
- Run relevant authored contracts and the documented Dev tier once implementation is authorized. Deployment, Packwiz refresh, packaging, Dist/Debug and exact-candidate native verification remain separately authorized.
- A pack-wide all-clear requires native coverage of the omitted/custom/machine cases. Until then report confirmed resolved families, explicit intentional choices and UNKNOWN cases separately, with no misleading total.

## Recommended order

1. Straw/canvas/daub/coaster and four Create roots: immediate pack-authored ambiguity.
2. Chest conversions/generic barrels/ladders: high fan-out, native state-sensitive.
3. Sand identity whitelist and casts: shared normalization root cause.
4. Rain collector/furnaces/handmade utilities: preserve early access.
5. Material compression, crops and geology: owner/conservation review.
6. Decorations and wood identity: deliberate finishing routes.
7. Magic tiers/NBT/cooking state and special/custom recipes.
8. Exact-candidate global matcher verification and remaining machine/ritual review.
