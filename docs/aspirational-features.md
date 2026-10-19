# Aspirational features

## Purpose and authority

This is the living inventory of features wanted for Better Content but outside current work scope.
It owns deferred intent, not implemented behavior, a release roadmap or permission to start work.
The pack owner decides additions, priorities and activation; recording a feature here does not
approve its proposed design or authorize implementation, deployment or testing.

Keep entries focused on the player-facing goal, proposed boundaries, unresolved decisions and
conditions for revisiting. When a feature enters explicitly approved work, update its status and
link its owning subject or source repository. Once implemented or declined, remove the deferred
entry after moving any still-relevant guidance to the proper living subject; do not accumulate
historical completion reports here. See the [documentation authority map](README.md),
[workspace policy](policies/workspace.md) and [testing policy](testing.md).

## Plant drinks: normalized juices, teas and botanical brews

**Status:** Wanted; deferred and outside current implementation scope. The owner requested this
plan be recorded, including the full discussion matrices. Recording it authorizes documentation
only, not implementation, asset copying, recipe changes, builds or deployment. The coverage rules
below capture the requested direction; names, new effects, quantities, brewing conditions and
trade-offs are proposals rather than finalized balance.

**Goal:** One coherent plant-drink system covering fruit, berries, leaves, herbs, flowers,
vegetables, roots, grains, coffee, cocoa, sap and suitable otherworldly botanicals. Every fruit
should have a plain juice and a brewed specialty. Normalize production instead of inheriting
unrelated upstream recipe conventions, while preserving existing specialist drink identities
where appropriate. See [food progression](food_progression.md) and
[content systems](content_systems.md) for current implemented behavior; this section does not
replace the runtime drink catalogue.

### Role and coverage rules

- **Hydration:** untreated/dirty water, with its existing penalties.
- **Hydration+:** purified water and plain plant drinks whose extra value is nutrition, calories
  or sugar. Nutrition alone is not a reason to move a drink into Buff tier. Nectar's nutritional
  amplification likewise does not by itself promote a juice into Buff tier; its thirst/resource
  costs still apply.
- **Buff:** a distinct functional effect such as healing, cleansing, protection, an ability or a
  specialist effect. Milk and honey remain Buff drinks through cleansing, although animal-led
  drinks are outside this plant-led expansion.
- Classify contents, not vessels. Capacity, accessibility and preparation workstation are
  separate from role. A keg-made drink is not automatically a Buff drink or alcoholic.
- Plain safe juices have no specialist effects: do not inherit healing, cleansing, potion effects
  or chorus teleportation from their source fruit. Move existing effect-bearing juices into
  separately named brewed specialties, and create plain counterparts.
- Existing orange and lemon juices are currently manual-only. Their cleansing must get explicit
  completion-safe handling before promising passive Drinks-slot compatibility; a rename is not
  an adapter. Existing milk/honey cleansing is not grounds for a pre-emptive nerf.
- Every fruit gets both preparations; not every leaf or seed needs an artificial juice. However,
  unpleasant is not unjuiceable: **onion, garlic, potato and kelp all get juice**. Save an absent
  juice entry for genuinely different preparation methods, not taste. Brief Nausea for especially
  unpleasant juices is only a possible drawback, not settled tuning or a hidden brew benefit.
- Keep existing teas, milkshakes and cocktails distinct. An existing fruit tea can fulfil that
  fruit's brewed-specialty entry without inventing a redundant cordial. Milk or honey additions
  may remain in mixed drinks; plant-led does not require every preparation to be vegan.

### Production and brewing matrix — proposed

| Stage | Inputs/process | Output | Boundary |
|---|---|---|---|
| Hand juicing | Standardized edible fruit/plant portion plus empty bottle | One plain juice | No mandatory sugar, starter, cooking or dilution for ordinary juicy fruit |
| Create juicing | Same portion, mechanical press over a basin | 250 mB plain juice | Same yield as hand production; automation, not a multiplication bonus |
| Bottling/emptying | 250 mB fluid and the appropriate vessel | One bottle or its fluid contents | Conserve volume and return vessels once; no bottle or fluid loops |
| Fresh tea/infusion | Water plus leaves, herbs or flowers; heated steeping | Prepared tea or infusion | Use an appropriate kettle/cooking-pot route rather than forcing every tea through a keg |
| Primary fruit brewing | 1,000 mB juice plus one common starter flower in a B&C keg | 1,000 mB named fruit brew | Initial proposal: eight minutes; native temperature band 3 is a candidate, not settled for every drink |
| Developed tea | Prepared tea plus its specified brewing inputs in the keg | Cultured/developed tea, including the existing kombucha family | Preserve a distinct reason for the further brewing stage |
| Specialist brewing | Specified base plus a specialist agent | Explicit unusual/cursed drink | Separate recipe/result; never silently substitute into a common recipe |
| Secondary brewing | Existing specialty plus an additional agent and keg time | Stronger or altered preparation | Four additional minutes is a starting proposal, not a universal upgrade rule |

Normalize edible portions across whole fruit, small berries and slices rather than using the
same item count blindly. Ordinary plain juices should start with consistent hydration/quenching
and modest fruit-derived nutrition; exact servings, nutrition, Nectar loads and thirst values
need balancing. Do not invent a mandatory water input merely to make pressing possible; any
intentional dilution recipe needs separate accounting and balance.

“Brew” is the umbrella, not “ferment”: it includes steeped teas, infusions, cordials and developed
keg preparations. The keg is a brewing vessel, not proof of literal yeast fermentation or alcohol.
Draught/alcohol is an explicit profile decision. Do not silently add it when migrating an existing
healing or cleansing juice.

### Brewing agents — proposed

| Agent group | Intended use | Rule |
|---|---|---|
| Common starter flowers | Most fruit brews | One explicit broad tag of ordinary non-harmful flowers; any member gives the same fruit-specific result |
| Wither rose | Grimberry and selected death-themed preparations | Explicit specialist recipe; excluded from the common pool |
| Chorus flower | Chorus specialty | Explicit otherworldly agent, not a substitute for common flowers |
| Nether wart | Selected intensified/unstable secondary preparations | Not a blanket requirement for every advanced drink |
| Crimson/warped fungi | Selected Nether-altered preparations | Specialist exceptions; fungi are included as brewing botanicals, not botanically classified as plants |
| Particular magical flowers | A few justified magical/dimensional recipes | Inspect actual pack ingredients before selecting; no generic requirement that every exotic fruit needs an exotic starter |

Flowers have two independent roles: an interchangeable agent in another plant's brew, or the
principal ingredient of their own infusion. Their proposed infusion effect below applies only
in the latter role. Keep poisonous, magical and exceptional plants out of the common starter
pool. No mandatory new yeast item, mushroom or added sugar is required for ordinary fruit brewing.
The flower-agent convention supersedes the earlier brown-mushroom default discussed for this
expansion; existing upstream mushroom recipes are not changed by this plan.

### Full plant / juice / brew / effect matrices

**Legend:** **Existing** in the effect column means preserve a current drink's benefit; it does
not mean the proposed name, recipe, plain juice or migration already exists. Unmarked effects
are new proposals. A dash means use a different preparation, not that the ingredient is absent.
These are the full working discussion matrices, not a completed live-registry inventory. Resolve
additional ingredients and aliases before implementation; do not treat a missing row as a permanent
exclusion. New effects remain subject to owner approval and native compatibility inspection.

#### Fruit trees

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Apple | Apple Juice | Apple Cider | **Existing:** Absorption |
| Pear | Plain Pear Juice | Orchard Soother | **Existing:** pear juice's Lozenge |
| Orange | Plain Orange Juice | Suncrest Tonic | **Existing:** orange juice's recovery/cleansing package |
| Kiwi | Kiwi Juice | Greenbound Fizz | Jump Boost |
| Mango | Mango Juice | Mango Tea / Mango Milkshake | **Existing:** Strength and Rage Aura |
| Lychee | Lychee Juice | Lychee Cherry Tea | **Existing:** Strength |
| Peach | Peach Juice | Peach Tea / Bellini Cocktail | **Existing:** Heal Aura; Bellini also causes Nausea |
| Mangosteen | Mangosteen Juice | Mangosteen Tea | **Existing:** Sliding |
| Hawberry | Hawberry Juice | Hawberry Tea | **Existing:** Appetizing |
| Bayberry | Bayberry Juice | Duskberry Cordial | Night Vision |
| Fig | Fig Juice | Featherfall Nectar | Slow Falling |
| Persimmon | Persimmon Juice | Embergold Tonic | Fire Resistance |
| Durian | Durian Juice | Thornhide Brew | Resistance with brief Nausea |

#### Berries and other fruit

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Blueberry — Fruits Delight | Blueberry Juice | Bluehour Cordial | Night Vision |
| Cranberry | Cranberry Juice | Bogclear Tonic | Removes Poison on completion |
| Sweet berry | Sweet Berry Juice | Bramblefoot Fizz | Speed |
| Glow berry | Glow Berry Juice | Glittering Grenadine | **Existing:** Glowing, Night Vision and alcohol effects |
| Mulberry | Mulberry Juice | Blackbough Cordial | Strength |
| Sourceberry | Sourceberry Juice | Spellbloom Tonic | Mana recovery; needs explicit Ars integration |
| Blue berry — Aether | Skyberry Juice | Cloudbreak Cordial | Slow Falling |
| Grimberry | Plain Grimberry Juice | Grimberry Rush — wither-rose agent | **Existing:** Haste |
| Lemon | Plain Lemon Juice | Bittergold Tonic | **Existing:** lemon juice's protection/cleansing package |
| Pineapple | Pineapple Juice | Goldrush Fizz | Haste |
| Melon | Plain Melon Juice | Summerheart Cordial | **Existing:** 2 HP healing per full serving |
| Hamimelon | Hamimelon Juice | Dawnheart Cordial | Absorption |
| Chorus fruit | Chorus Juice | Voidstep Elixir — chorus-flower agent | Slow Falling plus a completion-gated random blink |

#### Tea, coffee and cocoa

Existing Long/Strong versions remain within their families rather than becoming separate plant
rows. Ingredient processing matters: coffee cherries and beans can support different preparations.
Pinned native Respite recipes reference `farmersrespite:rose_hips` for Rose Hip Tea and coffee
berries plus glow berries for Gambler's Tea; verify actual pack acquisition during the coverage
audit rather than inferring ingredients from drink names.

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Green tea leaves | — | Green Tea | **Existing:** Haste |
| Green tea leaves, developed further | — | Kombucha | **Existing:** Haste and alcohol effects |
| Yellow tea leaves | — | Yellow Tea | **Existing:** Resistance |
| Black tea leaves | — | Black Tea | **Existing:** Caffeinated |
| Tea leaves, with milk | — | Builder's Tea | **Existing:** Haste |
| Coffee bush — berries and beans | Coffee Cherry Juice | Coffee / Cascara Tonic | Coffee's Caffeinated is **existing**; Cascara using it is proposed |
| Cocoa | — | Hot Cocoa | **Existing:** one random harmful, curable effect removed on completion |
| Rose bush / rose hips | Rose Hip Juice | Rose Hip Tea | **Existing:** Regeneration |
| Dandelion | — | Dandelion Tea | **Existing:** Comfort |
| Lemongrass | — | Lemongrass Infusion | Comfort |
| Ginger | Ginger Pressing | Hearthfire Ginger Tea | Removes Nausea on completion |
| Ube | Ube Milk — not raw juice | Ube Milk Tea | **Existing:** Night Vision |

#### Vegetables, roots and grains

Onion, garlic, potato and kelp juice are deliberate inclusions, even if their presentation is
unpleasant or cursed. Their plain forms do not inherit the functional benefits of their brews.
Raw potato juice need not replace the processed base of the existing vodka recipe; normalization
must choose and inspect the appropriate starch-processing route rather than merely rename it.

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Carrot | Carrot Juice | Goldeneye Tonic | Night Vision |
| Beetroot | Beetroot Juice | Strongroot Ale / Steel Toe Stout | **Existing:** Resistance and alcohol effects |
| Tomato | Tomato Juice | Bloody Mary / Red Rum | **Existing:** Raging and alcohol effects |
| Cabbage | Cabbage Juice | Greenward Kvass | Comfort |
| Onion | Onion Juice | Hearthroot Infusion | Removes one harmful, curable effect on completion |
| Garlic | Garlic Juice | Whiteclove Tonic | Removes Poison on completion |
| Potato | Potato Juice | Vodka | **Existing:** Draught protection/impairment and native alcohol effects |
| Wheat | Wheat Milk | Beer | **Existing:** Draught protection/impairment and native alcohol effects |
| Rice | Rice Milk | Rice Wine / Pale Jane | **Existing:** alcohol effects; Pale Jane also grants Comfort |
| Pumpkin | Pumpkin Juice / Pumpkin Seed Milk | Harvestgold Brew | Absorption |
| Sugar cane | Cane Juice | Canefire Brew | Speed, with its sugar consequences |
| Kelp | Kelp Juice | Tideward Infusion | Water Breathing |

For strongly flavored vegetables, cooked or steeped preparations remain possible alongside raw
juices. Grains, seeds, nuts and legumes should use actual available ingredients: do not invent
almond, oat, soy or coconut stock solely to fill a plant-milk category. Confirm any additional
suitable stock during the coverage audit. Existing alcoholic mixed drinks such as Salty Folly,
Saccharine Rum and other multi-base recipes also need reconciliation rather than accidental
removal merely because they lack a single-plant row here.

#### Ordinary flowers

The table describes principal-ingredient infusions, not effects granted by using a flower as
an interchangeable starter. Dandelion and rose bush are covered above. Overlapping effects are
intentional; collecting every decorative flower should not become mandatory progression.

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Poppy | — | Redpetal Tea | Comfort |
| Blue orchid | — | Marshlight Infusion | Water Breathing |
| Allium | — | Violetguard Tea | Removes Poison on completion |
| Azure bluet | — | Clearbrook Infusion | Brief Night Vision |
| Red tulip | — | Emberpetal Tea | Brief Strength |
| Orange tulip | — | Copperpetal Tea | Brief Haste |
| White tulip | — | Ivorypetal Tea | Brief Resistance |
| Pink tulip | — | Blushpetal Tea | Brief Regeneration |
| Oxeye daisy | — | Meadowrest Tea | Regeneration |
| Cornflower | — | Bluewind Infusion | Speed |
| Sunflower | — | Sunward Tea | Brief Fire Resistance |
| Lilac | — | Evening Lilac Tea | Comfort |
| Peony | — | Peony Heartcordial | Absorption |
| Flowering lily pad | — | Stillwater Infusion | Water Breathing |

#### Witch herbs and unusual botanicals

These are fantasy preparations, not real-world safety claims. Dangerous ingredients never enter
the common starter pool. An unsafe juice/extract is not promised to be a safe Hydration+ drink;
resolve its explicit risk, preparation and labelling before implementation. In particular,
belladonna being dangerous is not a claim that its berries cannot be juiced.

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Yellow dock | — | Dockside Bitter | Harmful-effect cleansing on completion |
| Mugwort | — | Dreamwalker Tea | Night Vision with brief Nausea |
| Mandrake | — | Mandrake Fortifier | Resistance with brief Slowness |
| Belladonna | Belladonna Juice — unsafe | Nightshade Veil — specialist preparation | Invisibility with Poison risk |
| Lily of the valley | — | Widow's Whisper — specialist preparation | Invisibility with Weakness |
| Wither rose | — | Gravebloom Draught — specialist preparation | Strength with brief Wither |
| Wither roots | — | Wither-root preparation — name/effect unresolved | Not a native Purulent Tea ingredient; inspect before assigning a specialty |
| Nether wart, with fermented spider eye | — | Purulent Tea | **Existing:** extends a random existing milk-curable effect |
| Coffee berries plus glow berries | Respective plain juices | Gambler's Tea | **Existing:** beneficial/harmful gamble |
| Warped fungus | — | Warped specialty — name/effect unresolved | Specialist brewing botanical; not a native Gambler's Tea ingredient |
| Cursed sap | —; sap rather than juice | Cursed Sap | **Existing:** Strength and Resistance |

#### Stronger preparations

| Plant | Juice | Brew | Effect |
|---|---|---|---|
| Melon | Melon Juice | Summerheart Reserve — secondary brewing with nether wart | **Existing:** stronger healing, 4 HP per full serving |
| Apple | Apple Juice | Long / Strong Apple Cider | **Existing:** duration/potency variants |
| Grimberry | Grimberry Juice | Blackwake Reserve — secondary brewing with crimson fungus | Stronger Haste with brief Weakness |

### Asset direction

Reuse suitable **juice textures from other mods and retint them aggressively**, rather than
replacing everything with a newly drawn shared bottle template. Preserve the donor bottle's
shape, glass, outlines and pixel detail while separating the liquid colours strongly enough for
inventory readability. Check each source asset's license and preserve required attribution before
copying. Generated textures should remain reproducible from the selected donor and tint recipe.

| Juice family | Proposed colour direction |
|---|---|
| Apple / pear / kiwi | Bright amber / chartreuse / saturated green |
| Orange / mango / persimmon | Vivid orange / deep golden orange / burnt orange |
| Lychee / peach / mangosteen | Rosy ivory / coral / violet |
| Hawberry / bayberry / fig | Scarlet / dark wine red / dusty magenta |
| Durian / pineapple / lemon | Acid yellow-green / strong gold / electric yellow |
| Melon / hamimelon | Watermelon pink / apricot orange |
| Fruits Delight blueberry / Aether blue berry | Indigo / electric blue |
| Cranberry / sweet berry / mulberry | Bright crimson / raspberry red / near-black purple |
| Glow berry / sourceberry / grimberry / chorus | Luminous-looking amber / arcane purple / deep teal / hot lavender |

These colours are art proposals, not evidence of native emissive rendering or extra drink effects.
Choose donor assets and palettes for the remaining vegetable/herbal drinks during authoring.

### Decisions and conditions for revisiting

- Audit pinned registrations, native food/drink behavior, recipes, tags and pack removals for all
  suitable plant ingredients and existing mixed drinks. Tags and translation names alone do not
  prove complete coverage or edible/drinkable behavior. Explicitly resolve borderline produce,
  torchberries, additional flowers, magical botanicals and ingredient aliases.
- Select owning sources after inspection: the existing drinking-water catalogue/reserve engine,
  physiology nutrition/metabolism integrations, native workstations and pack recipe integration
  have different responsibilities. Do not assume one new general-purpose mod is necessary.
- Finalize names, starter membership, process routes, serving yields, nutrition/Nectar, exact
  hydration/quenching, effect doses, drawbacks, temperature/time bands and intentional alcohol.
  Repetition of effects is acceptable; every fruit needs a specialty, not an entirely new mechanic.
- Inspect Create and B&C native recipe/volume/container behavior before choosing serializers.
  Eight-minute first stages and four-minute second stages are proposals, not proven balance.
- Migrate existing effect juices without duplicate effect-bearing shortcuts. Decide registry-ID,
  display-name, recipe and existing-save handling explicitly; no migration strategy is authorized
  by this document. Preserve specialist benefits without claiming renamed profiles are implemented.
- Keep prepaid owner-side fractional servings, proportional nutrition/metabolic consequences,
  full-thirst purchase/release restrictions, vessel conservation and completion-gated cleansing.
  Short timed effects must remain useful under actual sip cadence; accounting tests alone do not
  establish gameplay balance. New mana recovery, blinking and cleansing require explicit adapters.
- Preserve manual-only status until a bespoke action is safely integrated; Purulent Tea, for
  example, does not become slot-compatible merely because it appears in this matrix. Derive the
  concise "Drinks slot compatible" tooltip from actual admission, not planned coverage.
- Require separate implementation authorization and the applicable source, recipe, multiplayer,
  native inventory and visual acceptance scope before shipping. This entry claims no new runtime
  compatibility, balance, asset-license clearance or deployment acceptance.

## Loot: table-owned quantity and reward balancing

**Status:** Wanted; deferred and outside current implementation scope. The former global chest
normalizer is removed; a table-by-table replacement balance pass has not been implemented.
See [structure chest loot](content_systems.md#structure-chest-loot) for current behavior.

**Goal:** Make exploration rewards worthwhile and appropriately sized without flattening distinct
locations into generic supply bundles, deleting special rewards or manufacturing compensation
for every sparse roll.

### Proposed approach — not yet approved

- Balance explicitly selected loot tables and pools at their definitions: adjust roll counts,
  weights and item quantities rather than scoring and reconstructing final generated stacks.
- Give intended containers authored roles such as expedition supplies, encounter rewards and
  treasure. Roles guide table authoring, not automatic classification by folder-name regexes.
- Reference reusable supply pools explicitly where appropriate. Extra supplies are an opt-in
  part of a container's table, never an automatic response to an empty or progression-filtered roll.
- Inspect all consumers before changing a shared table. Native `CHEST` context does not prove
  physical chest use; Quark Stonelings can roll carried rewards through that path. Keep mob-carried
  rewards, nested helpers, packages and intentional empty/failure rewards outside blanket balancing.
- Preserve special-reward pools, item data and native stack behavior unless a specific balance
  decision changes them. Existing progression cuts and geological replacements remain independent;
  balancing must not restore prohibited acquisition routes.
- Use a narrowly targeted final cap only for a demonstrated post-table injection problem, with
  explicit tables/commodities and no filler generation, rather than reviving a global normalizer.

### Decisions and conditions for revisiting

- Inventory the active pinned tables, pools, injections and their consumers; identify actual empty
  or excessive reward problems before selecting changes. Retained diagnostic inventories are not
  automatically current implementation inputs or evidence.
- Approve the initial table set, roles, desired quantities, special rewards and treatment of
  intentional scarcity. No universal bundle targets or treasure-value score are settled here.
- Decide shared-pool ownership and preserve Lootr personal-roll behavior. Existing rolled or
  prefilled inventories must not be silently rewritten; any save migration is separate scope.
- Verify effective pool composition, progression restrictions, representative distributions and
  preservation of item state, including negative cases for mob/helper consumers. Source checks
  alone do not establish gameplay balance or exact-candidate native runtime acceptance.
- Require explicit implementation approval and separately authorized runtime testing and packaging.
  Recording this aspiration authorizes documentation only, not a replacement loot system.

## Valkyrien Skies: physical motion and spacecraft

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** Valkyrien Skies vehicles and moving contraptions should have realistic sources of
motion and Newtonian responses, including equal-and-opposite reactions. Extend that physical
vehicle model to player-built spacecraft, replacing Creating Space's spacecraft system rather
than maintaining a second, disconnected flight model.

### Proposed behavior — not yet approved

- Give propulsion and actuation identifiable physical sources: engines supply power, while
  wheels, propellers, control surfaces, thrusters and mechanisms apply forces through contact,
  a surrounding medium or expelled reaction mass as appropriate. Power alone is not thrust;
  internal mechanisms must not accelerate an isolated craft's center of mass for free.
- Apply equal-and-opposite forces and torques to interacting bodies. Pushing, towing, docking,
  collisions and mounted mechanisms should produce corresponding reactions rather than
  one-sided motion. Account for momentum carried away by exhaust or transferred to air,
  water or terrain without requiring individual particle simulation.
- Make mass, inertia, force application points and torque matter: heavier loads change
  acceleration, off-center thrust can rotate a craft, and braking or attitude changes need
  physical means. Preserve linear and angular momentum unless an external interaction changes
  them; avoid unexplained drag, automatic stopping or reactionless steering.
- Use Valkyrien Skies-driven spacecraft for continuous physical maneuvering, with vacuum-capable
  propulsion, finite propellant/reaction mass where applicable, coasting and deliberate braking.
  Atmospheric propellers and aerodynamic control surfaces must not provide vacuum thrust or
  control without a separately justified mechanism.

### Decisions and conditions for revisiting

- Inspect the selected Valkyrien Skies version and integrations before choosing an owning source
  repository; existing physics support and gaps are not assessed by this entry.
- Define practical simulation limits, acceptable approximations, player controls and any explicitly
  supernatural exceptions without presenting exceptions as ordinary Newtonian mechanics.
- Decide spacecraft assembly, atmosphere/space transitions, gravity and travel-scale behavior,
  persistence, multiplayer synchronization and compatibility with the pack's progression.
  Orbital simulation is an unresolved scope decision, not an assumed requirement.
- Plan the replacement boundary and migration for Creating Space's spacecraft, recipes, controls
  and progression dependencies; decide separately whether any non-spacecraft content remains.
- Require explicit implementation approval and separately authorized physics, multiplayer,
  performance and native visual verification. Recording this goal does not remove Creating Space,
  install Valkyrien Skies or authorize a deployment or test run.

## Hostile mobs: retreat after a one-sided fight

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** Ordinary hostile mobs that lose half their health without successfully landing a hit
should try to run away rather than continue a hopeless attack.

### Proposed behavior — not yet approved

- Track damage and successful hits within an encounter. A successful hit means actually damaging
  the opponent, not merely swinging or firing a projectile.
- Trigger retreat after losing health equal to half the mob's maximum health without landing a
  hit. Prefer encounter damage tracking over a simple current-health threshold so an already
  injured mob or unrelated environmental damage does not automatically trigger flight.
- Give retreat priority over attacking and prevent immediate target reacquisition from cancelling
  it. Choose reachable escape destinations away from the opponent rather than just clearing the
  attack target.
- Start with ordinary ground-based hostiles. Bosses, stationary enemies, flying mobs, creepers
  preparing to explode and mobs with custom combat AI need explicit inclusion/exclusion decisions;
  generic coverage does not promise compatibility with every modded hostile.

### Decisions and conditions for revisiting

- Define encounter start/end, damage attribution and whether the opponent must be a player.
- Confirm whether one successful hit prevents retreat for the rest of that encounter, as the
  initial request suggests, or whether losing half its health since its last successful hit also
  qualifies. The latter is a broader morale mechanic, not an assumed requirement.
- Decide retreat duration, escape distance, out-of-combat reset and cornered behavior. A bounded
  retreat of roughly 8–15 seconds with fighting back when cornered is a proposal, not settled tuning.
- Choose the owning server-side integration repository after inspecting existing combat AI;
  verify representative vanilla and modded mobs, pathfinding and multiplayer behavior under
  separately authorized implementation and testing. This entry authorizes no gameplay changes.

## Aspirational contextual Threads

**Status:** Wanted; outside current implementation scope. These are future teaching cards,
not requests to implement new gameplay mechanics, and do not fill the four vacated active slots.

- **Water bottle:** Contextual preparation and carrying advice that complements, rather than
  repeats, the existing Drinking Safe Water lesson.
- **Grappling hook:** A contextual Thread about acquiring or using the pack's hook and its
  useful traversal role.
- **Climbing:** A contextual Thread explaining the implemented climbing opportunity at a
  personal point of need.

Mechanic and trigger research, ownership, copy, art, and acceptance belong to separately
approved future work. No triggers are selected here. Recording these aspirations authorizes
neither implementation, deployment nor testing.

## Abyssal City: gameplay completion and pack integration

**Status:** Private source checkpoint published; gameplay completion and pack integration are deferred
and outside current work scope. This is not a completed or accepted MVP.

**Owning source:** [better-content/better-abyssal-city](https://github.com/better-content/better-abyssal-city)
(private; organization access required). Its README owns implementation details and verification
boundaries. The prototype is not installed in the pack; publication does not authorize deployment.

**Goal:** An explorable layered underwater city with dangerous mystery fluid, player sealing and
pumping/reclamation, finite local Gatherer/Warden ecology, and persistent intercity travel.

The published prototype implements city planning/generation, structural fixtures and experimental
traversal tooling. Generation readiness remains incomplete; hydraulics, actors and integrated
multiplayer/performance/visual/distributable acceptance remain unfinished. No gameplay-ready ZIP
or human acceptance is claimed.

### Conditions for revisiting

- Explicitly authorize resuming the isolated MVP and, separately, any main-pack integration.
- Resolve native ordinary-loading/physics-cache diagnostics and clarify the headless audio boundary.
- Prefer compositional navigability verification: validated pieces and connection contracts,
  assembled-world obstruction/connectivity checks, and short representative native client journeys.
  Do not rely on an exhaustive player-steering marathon or assume correct composition from piece tests alone.
- Complete and verify the hydraulic reclamation and finite actor loops, persistence, two-client play,
  performance, visual readability and the exact extracted standalone payload before player review.

## Complicated Bees: Forestry-style bee swarms

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** Forestry-style visual bee swarms around Complicated Bees nests and working apiaries,
so inhabited nests and productive colonies feel alive rather than like static blocks.

### Proposed approach — not yet approved

- Prefer studying actual Forestry swarm/bee-rendering code as the behavioral and visual reference,
  and adapting or reusing suitable code where licensing and version compatibility permit, rather
  than approximating the effect from memory. The exact Forestry version and source paths remain
  to be selected and inspected.
- Keep swarms cosmetic and client-side; do not introduce simulated bee entities, alter genetics
  or production, or imply activity when an apiary is inactive. Decide separately how wild nests
  signal occupancy and how swarm density reflects colony activity.
- Bound particle/render cost and viewing distance for dense apiary installations; respect client
  particle settings and provide an appropriate reduction/disable option.

### Source baseline and conditions for revisiting

The pack pin is recorded in [the Complicated Bees manifest](../mods/complicated-bees.pw.toml).
Source inspection of upstream `v1.20.1-3.4.6` found no ambient bee-particle emission in nests or
apiaries; its existing bee particle is emitted by the Bee Staff projectile, not colony activity.
This is an implementation gap, not a verified rendering failure.

Before activation, inspect the chosen Forestry implementation and license, choose the owning
integration repository, and establish how client visuals receive colony activity without adding
unnecessary network traffic. Explicit implementation approval and separately authorized native
visual/performance verification are required; this entry authorizes neither.

## EMI: recipes with available ingredients first

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** When viewing recipes in EMI, put recipes for which the player has all required
ingredients and quantities before recipes with missing ingredients. Keep the latter visible.

### Proposed behavior — not yet approved

- Stable ordering within each recipe category: ingredients available first, existing EMI order
  preserved within both groups. Do not mutate global recipe-manager lists.
- Evaluate availability once when opening the view, before pagination, rather than continuously
  moving recipes while the player is browsing.
- Interpret availability as having the inputs for one recipe execution, not necessarily being
  able to craft in the current workstation. Native EMI handlers retain authority over fill and
  crafting operations.
- Use native exposed inventory sources. Do not introduce unopened/nested backpack searches,
  custom storage handlers or a new item-transfer engine.

### Feasibility and boundaries

The pack pin is recorded in [the EMI manifest](../mods/emi.pw.toml). Initial source inspection
of EMI `1.1.24+1.20.1` found usable ingredient/quantity checks in
[`EmiPlayerInventory.canCraft`](https://github.com/emilyploszaj/emi/blob/23d251ea8ea3a5fd7d760948f36014b185eac69f/xplat/src/main/java/dev/emi/emi/api/recipe/EmiPlayerInventory.java)
and per-category lists before pagination in
[`RecipeScreen`](https://github.com/emilyploszaj/emi/blob/23d251ea8ea3a5fd7d760948f36014b185eac69f/xplat/src/main/java/dev/emi/emi/screen/RecipeScreen.java).
A narrow client-side mixin appears feasible without an EMI fork or server-side crafting changes.
This is a source-based feasibility assessment, not runtime verification; recheck against the
actual pin when implementation is authorized.

EMI's existing matcher is greedy: overlapping ingredient alternatives can produce false negatives.
Reusing native checks does not promise a new exact ingredient-allocation solver. Ordinary input
availability also does not establish workstation, catalyst or other process requirements.

### Decisions before activation

- Confirm ingredients-available ordering versus stricter "craftable in this menu" ordering.
- Decide whether category tabs should also be promoted; within-category ordering is the proposed
  minimum scope.
- Resolve EMI's preferred-recipe focus behavior so opening the view does not silently land past
  the promoted recipes, while explicit requests to view a particular recipe remain meaningful.
- Choose the owning client integration repository and settle inventory-source semantics for
  personal inventory, crafting menus and separately opened native backpacks.

Any implementation must follow [native inventory parity and acceptance](inventory-parity.md),
including both personal-inventory paths and relevant separate-backpack behavior. Activation
requires explicit scope approval; runtime acceptance remains separately authorized under the
shared testing policy.
