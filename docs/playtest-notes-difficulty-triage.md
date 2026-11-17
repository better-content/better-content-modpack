# Playtest notes: preliminary implementation difficulty

Discussion snapshot, 2026-11-14. Not implementation approval or a second feature backlog: deferred proposals remain owned by [Aspirational features](design.md#aspirational-features). These estimates concern the user's local playtest notes, not results from the friends' Lineage playtest.

**Discussion source:** [this chat's local transcript](/home/dev/.codex/sessions/2026/11/14/rollout-2026-11-14T12-27-29-01a1d7aa-aea9-7fb1-b4d1-9c23f10d4ca3.jsonl). Codex session/thread ID: `01a1d7aa-aea9-7fb1-b4d1-9c23f10d4ca3`; resume locally with `codex resume 01a1d7aa-aea9-7fb1-b4d1-9c23f10d4ca3`. This is a local transcript link, not a public share URL. Earlier notes were also loaded from the [original Pi session](/home/dev/.pi/agent/sessions/--home-dev-better-content-modpack--/2026-11-09T07-32-36-661Z_01a1bf39-28b5-754d-a010-5c37d7eb1b84.jsonl).

## Tentative selection — 2026-11-14

User tentatively approved **1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 27, 28, 29, 30**. Item **21** (intermittent EMI availability sorting repair) was added by the subsequent instruction “approve 21”.

Not selected in that message (not treated as rejected):

- **7:** mod-independent pre-space progression reference.
- **25:** handheld cutting animation. Row 29's cutting functionality is tentatively selected independently.

This records tentative scope agreement during discussion, not an instruction to begin implementation or execute tests. Existing feature-freeze requirements remain applicable; tentative selection does not constitute a justified, bounded feature exception. The impact classifications below are unchanged by selection.

## Difficulty table

**Easy:** localized configuration, recipes, or an existing handler. **Medium:** bounded integration across several paths, or meaningful diagnosis/calibration. **Hard:** coordinated system changes or new persistent lifecycle with recovery/migration requirements. Unknown bug causes do not automatically make implementation hard. Source inspection establishes implementation paths, not runtime correctness.

Impact abbreviations: **E!** = provisional critical enjoyment blocker; **T!** = provisional critical technical blocker; **D** = deferred. Deferred items can wait because no current blocker was reported; reconsider when their stated player problem blocks meaningful play or an exception is justified. Difficulty and priority are independent.

| # | Action and reported need | Difficulty | Impact | Concrete implementation basis / next investigation |
|---|---|---|---|---|
| 1 | Remove Sleeping Overhaul; restore vanilla sleep timing | Easy | T! | Remove pack entry and obsolete configuration; existing compatibility hooks are mod-gated. Check other mods' sleep dependencies. User reports unsustainable accelerated-tick overhead; configuration targets 800 TPS. |
| 2 | Convert stone/cobble variants easily into vanilla stone/cobblestone | Easy | D | Existing stone compatibility scripts already collect variants. Add one-way conversion recipes with coverage and no resource-recovery loops. Tags alone do not satisfy this. |
| 3 | Make surface samples directly meltable in TCon | Easy | D | Ore Geology has full-chunk melting and small-chunk collection/crafting. Add small/sample melting recipes with consistent yields; resolve integer fluid rounding. |
| 4 | Keep every night at most 80% of daytime | Easy | D | Better Seasonal Daylight owns the clock. Its equinox fraction and annual amplitude can enforce a minimum day fraction of 5/9 without changing the fixed cycle or TPS. |
| 5 | Route campaign notifications through the pretty notification bus | Easy | D | Replace warning/arrival actionbar sends in InvasionRuntime with existing GameplayNotices API. Preserve recipients and timings. |
| 6 | Fix grey-on-grey world selection text | Easy | E! | FancyMenu world selection layout uses a pale background; inspect the actual text owner before changing foreground/theme. Reported readability failure; exact widget unverified. |
| 7 | Write mod-independent pre-space progression reference | Medium | D | Reconcile existing progression gates and alternative routes into one document. TCon/Create are the only required shared industrial backbone; research/design work, no new engine. |
| 8 | Add reusable cast materials besides gold | Medium | D | TCon supports reusable casts via shape tags. Add material-specific cast items/models, casting recipes and membership across shapes; audit addon recipes with hardcoded casts. Existing shapes, not new tool parts. |
| 9 | Remove harvestable copper from Dimensional Fonts | Medium | D | Copper occurs in hardcoded site geometry, roof helpers and village template palettes. Replace across active generation paths while preserving layout. New generation needs no world migration; existing sites require a separate decision. |
| 10 | About one hour before ambient temperature damage; strong sources stay strong | Medium | E! | Cold Sweat ambient/source configuration interacts with physiology drift damping and RPG resistances. Establish an unprotected baseline and tune ambient response separately from local sources. One rate multiplier cannot safely express both. |
| 11 | Repair sifter / machine recipes accepting TCon parts | Medium | E! | Shared PartVariantRecipe assembles role-based parts through PartSettings and NativePartRates; invalid assembly silently returns empty. Inspect that shared failure path and EMI transfer separately. User suspects all such recipes; breadth and root cause unverified. |
| 12 | Make RPG level-up screen understandable | Medium | E! | Existing StatsScreen has cards, detail views and a draft/commit flow. Improve hierarchy, explanations and information density within that UI; stat/protocol redesign is not required by the note. |
| 13 | Wool clothing: zero damage reduction, insulation, sewing | Medium | D | Conventional wearable items/material, recipes and textures plus Cold Sweat insulation/sewing configuration. Existing wool insulation support helps; the wearable set still needs authoring. |
| 14 | Make chimneys obey gravity appropriately | Easy, conditional | D | RBP leaves unmatched blocks without physics. If chimney definitions are missing, add profiles; first establish coverage and block-entity behavior. A falling-block incompatibility would raise this to Medium. |
| 15 | Audit other unexpected gravity exemptions | Medium | D | Compare registered blocks/tags with resolved RBP definitions and intentional exemptions. This estimates the audit; resulting fixes need individual ratings. |
| 16 | Reduce early campaign difficulty | Medium | E! | Roster permits vindicators at low intensity and TCon loadouts use random materials. Code changes are small; calibrating roster/equipment against stone weapons and leather armor is the larger task. Death retreat already worked in the reported session. |
| 17 | Hide placement ghosts for prohibited blocks | Medium | D | Rotavision compatibility currently admits all BlockItems; support preview checks RBP support, not every placement restriction. Share a side-effect-free legality check with actual restriction owners. |
| 18 | Remember orientation by block type | Easy | D | Upstream ClientEvents has a single rotationIndex and resets on held-item changes. Replace reset behavior with a per-type session map and reuse rotation synchronization. Persistence across restarts is not assumed. |
| 19 | Stop ParCool sprint from stowing the held weapon/tool | Medium | E! | Epic x ParCool bridges fast-running animation and action start/stop. Locate the stow transition and narrowly override it; exact responsible hook remains unverified. |
| 20 | Increase butcher-knife grass-cutting durability cost | Easy | D | ButcherKnifeDurability already handles grass/fern breaks and charges one durability. Adjust the dedicated handler; separately check whether sweeping or tool-specific damage bypasses it. |
| 21 | Fix intermittent EMI availability sorting | Medium | D | RecipeScreenMixin snapshots inventory at construction and partitions recipes/categories by canCraft. Diagnose stale inventory, transfer and screen refresh behavior before choosing the fix. |
| 22 | Exclude always-available informational EMI categories from promotion | Easy | D | Add an explicit category/recipe eligibility predicate to the same existing availability partition. No replacement sorting system required. |
| 23 | Prevent spectator item pickup | Easy, conditional | D | A server-side cancellable pickup guard is small. First establish the actual pickup path: vanilla spectator behavior may already prevent ordinary pickup; a bypassing mod needs its own adapter. |
| 24 | Give spectators clear player-location cues | Medium | D | Better Spawns already sends onboarding-player UUIDs and glows loaded players. Widening recipients/targets is Easy; cues beyond entity tracking require positions, synchronization and HUD rendering. Scope determines final rating. |
| 25 | Animate handheld cutting | Medium | D | Depends on row 29. Add visible action timing and first/third-person presentation, accounting for Epic Fight/ParCool animation ownership. Do not consume ingredients on a client-only animation event. |
| 26 | Fix ore deposits displaying only one texture | Medium | D | Existing ExposureBakedModel and Excavated Variants resource remapping already implement variation. Trace model data, selected model and texture slots. Suspected excavated-variant interaction is unverified; no evidence yet that a renderer rewrite is needed. |
| 27 | Systemic Salience v2: five aspects | Hard | D | Reconcile physiology, geology, Font affinities, trade mappings, teaching and audiovisual assets; choose merges and compatibility for old identities. Five identities and surviving properties remain design work. Do not collapse RPG builds or Diet groups merely to match the count. |
| 28 | Re-enter spectator scouting after death to change personal spawn | Hard | D | PersonalRespawnSystem already supports reselection and a persisted flag. Death-screen entry alone is bounded; safe retained-inventory custody, disconnect/restart recovery and preventing item teleportation make the full proposal Hard. |
| 29 | Handheld cutting for all cutting recipes | Medium | D | Adapt Farmer's Delight and Kaleidoscope/Better Cooking recipes to a server-authoritative hand interaction: tool/input matching, consumption, durability, chances, outputs and remainders. Includes nonfood/axe recipes. Whether a knife overrides their native tools is unresolved. Animation is row 25. |
| 30 | Arbitrary-composition hot sauce with tracked spice applied to food | Hard | D | Current SpiceCatalog/KettleBatch use named fixed profiles; SauceContent registers separate fluids and SeasoningDose stores named-sauce amounts. Arbitrary mixtures need conserved volume/spice data through kettle, containers, Create transport and food, plus compatibility for existing saved sauces. |

## Explicitly dropped

- Shipping the user's EMI favourites: dropped at their request because pack updates could overwrite players' personal favourites.

## Evidence locations

- Pack recipes: `kubejs/server_scripts/compat/retained/check__30_stone_cobble_compat.js`, `check__75_stone_cobble_tag_compat.js`; `kubejs/server_scripts/progression/20_powered_works.js`.
- Supporting source repositories under `/home/dev/mod_source`: `better-compat-fixes`, `better-manufacturing`, `better-seasonal-daylight`, `better-ore-geology`, `better-dimension-fonts`, `better-rpg-progression`, `better-survival-physiology`, `better-pillager-campaigns`, `better-gameplay-notices`, `better-block-placement-preview`, `better-spawns`, `better-cooking`, `better-spicy-farmers`, `better-spirit-commerce`.
- Pinned upstream JAR inspection supplied the TCon cast-tag, Rotavision reset and Epic x ParCool bridge findings. Source presence is not proof the reported session exercised that code.

No gameplay files changed, builds performed or tests executed for this triage. Further findings should be appended below so the preliminary table remains distinguishable from later evidence.

## Further exploration

### 2026-11-14: sleep removal dependencies

- `better-celestial-events` has a required mixin configuration, but its `SleepingOverhaulMixin` is explicitly annotated `@Pseudo`. The required configuration alone is therefore not evidence that removing Sleeping Overhaul breaks loading. Its `mods.toml` has no mandatory Sleeping Overhaul dependency.
- The Celestial Events development build still declares Sleeping Overhaul as compile/runtime dependencies, and `CelestialGameTests` directly asserts that its server state exists. Those assumptions need updating if that development/test setup is meant to represent the pack after removal. No tests were run.
- Independent `PlayerSleepInBedEvent` and `SleepFinishedTimeEvent` handlers block sleep on active celestial-event nights. Removing Sleeping Overhaul restores vanilla timing on ordinary nights; it does not remove those separately owned restrictions. This distinction should be recorded in the eventual fix, rather than silently broadening removal scope.
- **Estimate remains Easy**, with dependency/test-assumption cleanup included. Absent-mod loading remains unverified.

### 2026-11-14: sifter failure boundaries

- The actual sifter recipe requires a specially prepared Nether brick carrying `better_manufacturing.sifter_paid_sticks`, not an ordinary Nether brick. The preparatory recipe exists in `20_powered_works.js`; this is a concrete additional matching condition to check when reproducing the failure, not proof of player error.
- Read the pinned Forge 47.4.13 `PartialNBTIngredient.Serializer`: it accepts the recipe's `item` plus `nbt` shape. This rules out that particular JSON-schema suspicion by inspection; it does not prove the recipe loads or transfers correctly.
- The two part bindings (slots 3 and 5) agree with the shaped recipe and `SifterFrameSettings`' required `left`/`right` tool handles. No obvious role/slot mismatch was found.
- Assembly can still fail through native material registry availability, material/stat validity or missing iron reference stats. `PartVariantRecipe` converts `IllegalArgumentException` from settings calculation into an empty output without exposing the reason. Distinguish ingredient mismatch, settings rejection and EMI transfer before changing recipes broadly.
- **Estimate remains Medium; root cause unverified.** The useful next diagnostic is to identify which of these boundaries fails with the actual input stacks.

### 2026-11-14: EMI and spectator cues

- `RecipeScreenMixin` populates recipe/category availability only in its constructor, using `EmiPlayerInventory.of(player)`. Its `init` redirect sorts using that cached category map. There is no cache-refresh path in this mixin. Stale availability is a concrete hypothesis for the intermittent report, not a demonstrated cause; the caller's screen lifecycle and native inventory semantics still matter. **Sorting repair remains Medium.**
- Informational-category exclusion can be added to that existing partition/promotion decision. **Exclusion remains Easy.**
- `OnboardingVisibilitySync` sends only players who have not completed onboarding, and only to that same group. `OnboardingPlayerVisibility` then applies glow only to entities the client can resolve by UUID. Thus established friends are excluded by server policy, and distant untracked players cannot gain a marker through this glow code alone. **Changing target policy is Easy; comprehensive distant-location cues remain Medium.**

### 2026-11-14: Font generation continuity

- `DimensionalFontStructurePiece.postProcess` calls `DimensionalFontSiteGenerator.place`, establishing a concrete generation path for the hardcoded copper geometry.
- The structure piece saves its layout version and rejects versions different from the generator's current version when loading. A palette-only copper replacement should not casually increment that version: doing so without compatibility would reject previously saved pieces.
- Already placed blocks would remain unchanged without a separate conversion operation. Partially generated structures may receive the new palette in later-generated chunks; account for that boundary when evaluating a replacement. **Medium remains appropriate** because of coverage and generation continuity, not because a world-wide migration is inherently required.
