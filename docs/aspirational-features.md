# Aspirational features

These are historical ambitions, newly discussed aspirations and unresolved design questions, not a current implementation or acceptance report. Dated comparisons below distinguish inspected implementation from intended outcomes; they are not live status inventories. The linked work-in-progress directories have their own scope and status; do not treat them as completed features or active pack inputs. Established principles are collected in [Gameplay design choices](gameplay-design-choices.md).

## Social goals: different ways to contribute and belong

**State:** Design direction clarified in discussion on 2026-10-28, not an implemented social contract or evidence of player outcomes. The comparison below reflects that discussion's read-only inspection of the local workspace and selected Build 10 artifacts. Source, recipes and technical acceptance can establish capabilities, not enjoyment, sustained participation, actual division of labour or cultural adoption. The comparison is intentionally dated rather than a claim about every subsequent pack revision.

The central goal is a world where people have different things worth doing, becoming good at and sharing, and where participation does not depend on matching everyone else's progression or schedule. Friends developing a shared world, solo players sharing discoveries and builds, newcomers supplying straightforward work, experienced specialists, and independent hosts and organisers should all have legitimate places. A giant public server, compulsory multiplayer, permanent professions and comprehensive historical recording are not assumed.

Recognisable culture should grow from practice: player-created terminology, reputations, techniques, places and stories. Adding records, professions or a market does not by itself establish that culture.

### Shared practical literacy, independently deep practices

**Basic Create and Tinkers' Construct are a common practical foundation, neither inherently technology nor magic.** Toolmaking, basic metallurgy, mechanisms and workshop infrastructure can serve many disciplines. That foundation can be substantial and enjoyable in its own right, not merely a tutorial to rush through.

The intended structure is **shared material and mechanical literacy → independently deep practices → cross-disciplinary projects**. Independence means avoiding substantial compulsory progression through another specialist discipline before one's own practice becomes worthwhile. It does not mean namespace purity, isolated material economies or avoiding Create and Tinkers.

A discipline should have accessible beginnings, substantial continuing investment, useful or desirable results, and decisions that remain interesting after a guide exists. Others should be able to benefit without repeating its entire progression. Canonise and mechanically enforce meaningful choices where appropriate, without assuming permanent player professions. Distinguish independent access, viable alternative methods and consequential commitments: these are separate design questions.

**Inspected foundation:** early construction, local handling, toolmaking, metallurgy and mechanisms already support several activities. Rail Beetle module slots and family restrictions enforce configuration choices; cultivation has environmental and harvest-versus-propagation decisions; ore processing offers several routes to common products; programmable spells offer space for invention.

**Divergence/open work:** those local choices do not establish consistently independent specialist depth. Several magic branches inherit substantial outside prerequisites, while industrial applications share a factory progression. In the inspected scripts, Malum and Goety have Blood-tier dependencies and Occultism's impure chalk joins Hexerei preparation with four Font destinations. Ars' early metallurgy/Create ingredients are not, by themselves, evidence that industrial technology parents magic. Identify where common workshop competence ends and compulsory outside specialisation begins. Late convergence, such as AE2 combining developed capabilities, may be a desirable joint project rather than a defect.

Check continuing decisions and practical dominance, not only recipe counts or dependency edges. The inspected sauces contribute to a common Spice quantity rather than distinct Spice-effect profiles; apparent variety need not create enduring production niches. Ore routes share useful outputs, but their practical non-dominance was not established. Automation may free a practitioner for more interesting work or finish the practice for them; its presence alone settles neither interpretation.

### Useful contribution without progression catch-up

**A newcomer joining a late-game world should be able to spend about half an hour doing straightforward work and return with something established players genuinely want.** This is a different layer from specialist professions. The specialist supplies knowledge, equipment and processing capacity; the newcomer can supply time, materials or execution. Experienced players wanting an undemanding session should have the same opportunities.

Equivalent beginner-collected material should not be intrinsically inferior. Progression may improve collection speed, comfort and access while experts obtain more value from the same underlying input.

**Inspected foundation:** geological collection supplies raw inputs that advanced processing can use. It is a promising relationship between simple collection and specialist capacity, not proof that current beginner collection throughput is sufficient.

**Divergence/open work:** mature-world demand, stockpiles, collection rates and competing automated supply have not been established. Candidate activities include accessible prepared-site extraction, geological surveying, woodcutting and timber supply, selected consumed botanical ingredients, scouting, marked construction and deliveries. Treat these as proposals to evaluate, not guaranteed permanent occupations. Resident gathering and automated farms already compete with some generic collection work. Better recovery can reduce input demand for a fixed output; it does not automatically create more employment.

Recurring commodity supply and expansion-generated jobs have different lifetimes. A delivery route becoming obsolete when its railway is finished is acceptable if expanding activity creates other useful work. Do not preserve every individual job artificially. Botanical supply needs selected accessible sources, useful ordinary returns and repeated consumption; abundant plants or one-time seed acquisition do not establish enduring demand.

#### Woodcutting and forest surveying

**Promising contribution loop:** find mature timber → assess species and access → harvest and collect → deliver against real demand → replant and/or survey the next stand. Basic tools can make the work accessible; established players can supply better axes, safe access, transport and funded timber requests without requiring the cutter to understand the factory, ship or building that will consume the wood. This is an available activity, not a locked profession.

Dynamic Trees' slower growth makes mature standing woodland a potentially valuable stock accumulated over time, rather than treating logs as a generic rapidly renewable farm output. Replanting does not immediately replace a felled mature stand. Surveying old-growth woodland, identifying desired species and recording practical access can therefore complement logging: a short outing can return useful timber and information about future stands. Particular species, distance and access can distinguish a mature remote forest from a newly planted local grove. Logging camps, paths, boats and freight routes can make those places usable; harvesting depletes the immediate opportunity while regrowth takes time.

**Verification still needed:** measure effective growth and harvest rates under the pack's actual settings, species and conditions, alongside mature-world timber demand. Check resident gathering, Create harvesting and other integrations for bypasses. Automation can remove harvesting labour without eliminating growth time or land requirements; its existence alone does not establish that timber demand is already saturated. Conversely, slow growth alone does not guarantee useful employment if stockpiles or plantation throughput cover consumption. The aim is useful work grounded in ecology and geography, not a blanket ban on forestry automation or an artificially protected permanent occupation.

### A custom asynchronous player-trade system

**Wanted:** connect players with resources but little time to players with time but little progression, without requiring both to be online together. This is player exchange, distinct from NPC commerce and from specialist progression.

Centre the design on funded procurement, alongside stocked sell offers:

- A visible deliverable, accepted item identity, quantity, exchange rate and destination.
- Real deposited payment or stock, rather than an unfunded promise.
- Useful partial fulfilment, so a large request supports short independently rewarding sessions.
- Settlement without the requester being online, with proceeds available for later collection.
- Accessible directions and necessary basic equipment; no hidden novice-quality penalty.

Automatically verifiable item exchanges and human-reviewed construction or surveying commissions need different promises. Do not present subjective work as guaranteed automatic completion. Escrow, acceptance and staged payments for commissions remain design choices, not a solved trust system.

**Inspected foundation:** Player Stall machinery handles offers, stock and proceeds, but the inspected executor serves resident buyers. It is not an established asynchronous player-to-player procurement market.

**Divergence/open work:** the proposed bridge from mature-player demand to newcomer work is missing. A possible spatial policy is broad visibility of requests with physical exchange at chosen local markets, preserving freight and meaningful places. This is not settled: newcomer access must not depend on a dangerous cross-world delivery, and global instant delivery should not silently replace transport logistics. Currency versus barter, discovery, exchange locations and commission rules remain open.

The market exposes and coordinates demand; it cannot manufacture genuine demand. Server-generated buyback must not masquerade as evidence that established players value a commodity. A useful acceptance question is whether a mature settlement voluntarily exchanges something meaningful for what a lightly equipped player can obtain in a short session.

### Sophisticated food through a coherent cooking practice

**Direction:** normalise sophisticated finished dishes around Kaleidoscope Cookery's active cooking model, rather than letting every food mod offer a separate shortcut to equivalent premium meals. The aim is good food that takes meaningful preparation, human labour and time, and remains worth eating, sharing and buying. This complements simple gathering work; it is not a compulsory chef profession or heavy industrial progression.

**Source implementation IN PROGRESS — 2026-10-28 09:50 UTC:** Kaleidoscope Cookery `1.6.0-forge+mc1.20.1` has a client/server Packwiz input. The separately authorised, source-only [Better Cooking implementation](../../mod_source/better-cooking/IMPLEMENTATION.md) is now developing the integrated culinary policy, preparation/runtime interactions and provider-owned physiology/hydration/age interfaces. Source-local compilation and deterministic tests are technical evidence only: they do not establish complete native coverage, an accepted pack runtime, real-client visual acceptance or human enjoyment, demand and cultural adoption. No new cooking runtime is activated or deployed by this note, and previously accepted ZIPs remain unchanged. Extra Delight remains an investigated option, not an added pack input. New preservation/canning/drying design is separately [deferred](#deferred-preservation-selective-drying-and-industrial-provisions); existing drying recipes, food properties, aging clocks and storage rates stay unchanged.

The proposed kitchen structure is **growing/gathering → prepared ingredients and intermediates → an appropriate final culinary process → finished food for consumption or trade**. Basic Create and Tinkers remain common workshop literacy, not compulsory advanced industrial specialisation. Bulk ingredient handling, fermentation, baking, drying and fluid/container work can complement active cooking where their function is genuinely distinct. Use the existing apparatus inventory before considering another mod: Extra Delight is an investigated option, not a prerequisite or a commitment to add its workstations. Exact roles depend on the consolidation pass below; this is not a commitment to keep parallel cutting boards, equivalent pots or every overlapping food catalogue.

Normalise the experience, not every dish into one pot. Stir-fries, soups and steamed dishes can use suitable Kaleidoscope surfaces; bread should still be baked and a finished pie should not require an artificial extra pot step. The intended rule is a coherent preparation process with worthwhile human involvement, not universal use of one final machine.

#### Consolidate cooking surfaces, starting with Farmer's Delight / Kaleidoscope redundancies

**Required consolidation, source implementation in progress:** make one coherent Kaleidoscope-led culinary system, not merely a cleaner-looking food catalogue or an exclusively hands-on single-mod boundary. Farmer's Delight and Kaleidoscope are the immediate overlap, but they are not the full boundary: other food addons, ordinary crafting, Create, Tinkers, Rats, PneumaticCraft and specialist magic recipes can provide equivalent outputs. Audit cutting boards and tools, cooking vessels, ingredient identities, crops, intermediates, finished dishes and serving/container interactions across the pack. Choose a canonical surface or item for equivalent functions, with Kaleidoscope owning the intended sophisticated-cooking interactions; retain complementary processes where their work or purpose is genuinely distinct, not merely because they come from another mod.

##### Apparatus inventory to carry into the audit

**Inspection snapshot, 2026-10-28:** the following is a grouped inventory seed from current pack inputs, inspected release source/JARs and retained Build 10 recipe evidence, not an exhaustive live recipe or survival-acquisition report. Kaleidoscope was added after that retained runtime. Group variants and multiblock families rather than treating every fruit cauldron or wood variant as a new practice. Include ingredient preparation and drinks, not only surfaces that produce final meals.

**Kaleidoscope and other hands-on kitchen interfaces:** keep Kaleidoscope together here, but do not mistake membership in this table for a human-only boundary.

| Surface/interface | Current role | Consolidation concern |
| --- | --- | --- |
| Kaleidoscope chopping board | Knife cuts and ingredient preparation | Canonical cutting workflow versus FD/addon duplication; tool compatibility. |
| Kaleidoscope stir-fry pot | Oil, ingredients, required stirring, serving window and burning | Protect chosen preparation/handling rules without extending them artificially to every dish. |
| Kaleidoscope stockpot | Soup bases, ingredients, lids, heat and portion serving | Distinguish formulation quality from passive cooking and serving. |
| Kaleidoscope steamer | Slotted, lidded, stackable steaming | Retain genuinely different heat/loading decisions. |
| Kaleidoscope millstone | Entity-driven grinding | Not hand grinding; automation is supported. Compare with Create milling. |
| Kaleidoscope teapot | Brewing, pouring and portable vessel use | Overlap with existing brewing and drink-container paths. |
| Kaleidoscope bamboo tray | Recipe-based exposure drying and wetting | Passive and automatable; do not assume every dried output is preserved or shelf-stable. |
| Kaleidoscope shawarma spit | Multiple inputs cooked through campfire recipes | Direct-roasting overlap; not continuous active work. |
| Farmer's Delight cutting board, cooking pot, skillet and stove | Cutting, batch meals, direct cooking and cookware heating | Select canonical equivalent functions; retain dependency infrastructure and distinct contributions. Cutting-board dispenser use is enabled and pot automation exists. |
| Ube's Delight baking mat | Tool-driven rolling/staged preparation | Dispenser support and direct finished-food recipes matter: the inspected mat recipe can produce bread from wheat without a separate bake. |
| Fruits Delight fruit/jelly cauldrons | Fruit, jam and jelly preparation | Check cauldron stages against Create alternatives and common ingredients. |
| Amendments enhanced cauldron | Liquid/item in-world crafting | Hand and thrown-item crafting are enabled; establish actual culinary matches before assigning it a specialised role. |
| Vanilla campfires and crafting grids/tables | Roasting and ordinary assembly/preparation | Count complete alternative routes, including finished foods assembled without a dedicated kitchen surface. |

**Other processing, passive and specialist routes:** these are audit targets, not a decision to remove industrial or magical cooking.

| Apparatus/process family | Established or inspected culinary relevance | Consolidation concern |
| --- | --- | --- |
| Furnaces, smokers and compatible variants | Standard cooking/baking | Keep accessible staples; decide which finished-food recipes remain appropriate. |
| Farmer's Respite brewing barrel | Tea, coffee and other brewing | Distinct brewing and pouring versus duplicate teapot outcomes. |
| Brewin' and Chewin' keg; placed unripe cheese wheels | Fermentation, drinks and cheese maturation | Distinguish loaded batch processing and passive aging from active labour. |
| Better Spicy Farmers spice kettle | Timed ingredient/fluid sauce batches | Automation interfaces exist; coordinate sauces, proportions and physiology rather than inventing a parallel nutrition system. |
| Hexerei drying racks | Existing pack-authored food-drying routes, retained unchanged | Preserve current recipes/properties/aging and storage rates; reevaluate eligibility, losses and shelf stability only in the separately deferred preservation work. |
| Rats milk-cauldron route, Auto Curdler and Chef Rat setups | Cheese and delegated cooking | Check both passive conversion and agent-operated preparation, not only player recipes. |
| Create mixer/basin and press/basin | Dough, sauces, chocolate, juices and fruit preparations | Bulk ingredients are useful; equivalent premium outputs need deliberate treatment. Existing pressing is not planned can-sealing. |
| Create millstone, crushing wheels and mechanical saw | Ingredient grinding and compatible food cutting, including fruit slicing | Shared workshop preparation can remain legitimate; check yields and duplicate intermediates. |
| Create encased-fan processing | Bulk cooking and applicable washing/processing | Include processing inherited from ordinary cooking recipes, not only named food-addon recipes. |
| Create spout and item drain | Filling, emptying and fluid finishing | Check portion volumes, container returns and alternative consumption/unpacking routes. |
| Create: Diesel Generators fermentation and Casting Spout | Fermentation systems and loaded chocolate casting | Fuel ethanol is not automatically a drink; preserve actual culinary distinctions. |
| PneumaticCraft Thermopneumatic Processing Plant | Chips, salmon tempura and ingredient-fluid production | Industrial frying/processing exists outside the obvious food mods. |
| PneumaticCraft Pressure Chamber and explosion crafting | Wheat-flour preparation | Include non-countertop ingredient routes; do not infer that all industrial flour must be removed. |
| Tinkers melter/smeltery/foundry family and casting tables/basins | Food-fluid processing, dough, cheese, honey, soups and coated apples | Check each structure's actual recipe support, fluid quantities and serving outputs; common Tinkers literacy is not itself an unwanted bypass. |
| Blood Magic Alchemy Table and Aether Altar | Narrow loaded food transformations, including bread, mint tea and enchanted berries | Decide explicit specialist exceptions instead of leaving equivalent outputs unexamined. |
| Complicated Bees centrifuge | Honey-related ingredient separation | Upstream ingredient preparation rather than a final-meal station. |
| General autocrafting and interaction automation | Eligible crafting recipes, dispensers, fake-player tools and delegated operations | Not extra culinary surfaces, but potentially complete alternatives to the intended human work. Verify capabilities rather than assuming all interfaces are supported. |

Heat-source-only blocks, decorative counters, storage and serving furniture are not automatically independent processors. Ube's Kalan, for example, is not established as a separate recipe-processing machine merely by looking like a stove. Extra Delight's oven, bowl, mortar, chiller and vat are excluded from the installed inventory; the proposed canning apparatus is future work. Inventory evidence includes `kubejs/server_scripts/progression/82_food_drying.js`, the FD/Amendments configs, inspected release source/JARs and the retained Build 10 runtime recipe dump; none establishes that every route has been exercised or remains obtainable in every subsequent revision.

##### Decisions and migration required

For each food family, record **input acquisition → preparation/intermediates → final processing → portioning/containers → consumption**, including alternate recipes, loot/trade acquisition where relevant, and automation. Give each equivalent route an explicit disposition: canonical, redirected, genuinely complementary, deliberately different industrial output, justified specialist exception, or removed. A route is not redundant merely because it is automatable, and automation is not acceptable merely because it was supplied upstream.

An ordinary crafting, FD-pot, rat-operated or industrial alternative must not silently produce the same sophisticated result while bypassing its intended preparation, handling or quality rules. If equivalent outputs are deliberately retained, specify what is preserved and what differs; otherwise redirect or remove the recipe rather than merely hiding it in EMI. Deliberate industrial processed-food routes remain legitimate and useful, with explicit outputs and trade-offs rather than a blanket ban on mechanisation.

Redirect retained recipes, tags and addon consumers to canonical ingredients/surfaces. Check acquisition, recipe discovery, Ponder/guidance and container handling together. Review supported tool tags alongside Tinkers' tool ownership instead of assuming a second set of equivalent kitchen tools is necessary. Test actual dispenser/fake-player/rat interactions where the selected human boundary depends on them; neither a manual-looking block nor recipe removal alone proves that boundary.

The approved source plan selects Kaleidoscope-led equivalent food cutting/pot routes, Kaleidoscope flour/raw dough and FD-family knife compatibility through tags, while retaining required FD/addon infrastructure and existing registered items. Raw cultivated crops retain FD/Bumblezone acquisition authority; explicit native magic-enhanced basic-food exceptions remain legitimate. Deleting dependencies is not the objective. Baking and other intentionally non-pot processes still need an explicit relationship to the working preparation labels below rather than being silently reclassified. Complete recipe migrations and existing-item handling remain subject to the explicit policy and qualification audit; this note does not itself change any recipe, item, mod or automation path.

#### Internal food terminology and thirst coverage

**Working internal labels only:** these are for design discussion and the food audit. There is currently no intention to expose them as in-game categories, names, badges or a prescribed player glossary. Investigate their boundaries before treating them as a completed classification.

Use two independent axes:

| Axis | Internal label | Working meaning |
| --- | --- | --- |
| Hydration | Wet | Food that contains water and provides thirst replenishment when eaten. This includes water-bearing solid food, not only drinks or visibly liquid dishes. |
| Hydration | Dry | The non-hydrating side of this distinction; exact thresholds and edge cases remain to be investigated. Do not automatically impose thirst loss merely because a food is dry. |
| Preparation | Simple | Food that has not been crafted. This describes preparation, not usefulness, nutritional quality or the player's level. |
| Preparation | Sophisticated | Food made through the integrated Kaleidoscope-led preparation practice, including appropriate complementary baking/roasting surfaces. This is not a single-mod-exclusive label, a synonym for all crafted food or its highest quality tier. |
| Preparation | Processed | Mass-production food; prospective industrial canned/dried products belong to the separate deferred preservation aspiration. This is not a blanket label for every item that has undergone any transformation. |

A food's preparation category does not determine its hydration: simple fruit can be wet, industrial food can be wet or dry, and sophisticated dishes need hydration assessed individually. Canning does not imply drying. Kaleidoscope execution quality is a separate property, not another preparation class. Ordinary non-Kaleidoscope crafting, cooking and preparation intermediates, ambiguous recipes and identical outputs obtainable through different routes need explicit decisions in the audit rather than silent classification.

**Approved hydration defaults, source integration in progress:** inventory all edible items and actual consumption routes, including water-bearing solids, drinks, placed portions and nested vessels. Per 250 mB, water/unsweetened tea provides **4 thirst / 5 quenching**; juice/milk 3/4, fermented alcohol 3/3, spirits 1/0 and mixed alcohol 2/2. Food-serving defaults are juicy melon/citrus or liquid soup 4/5; fruit/salad/thick stew 2/2.5; moist vegetables/cooked grain 1/1.25; meat .5/.625; soft bread/pastry .25/.3125; explicitly dry food 0/0. Explicit overrides and composite ingredient moisture plus added water/retention/actual yield take precedence; do not add derived and direct credit together. These are approved defaults, not proof of complete coverage. Preserve water purity/safety, paid drink reserves, partial sips, effects cadence and container accounting. Food hydration has no quality multiplier or paid reserve, and fractional credit must survive reconnect. New drying/rehydration and baseline shelf-stability choices are outside this implementation and remain deferred.

Everyday survival food should remain accessible and adequate, with automation where appropriate. Prepared cuisine and excellent execution can offer desirable nutrition, variety or effects without making kitchen labour compulsory to avoid punishment. Better execution should come from supported ingredient proportions and handling, not an exclusive personal chef-level permission.

**Inspected foundation:** Kaleidoscope's published 1.20.1 source includes a stir-fry sequence with required stirring, a serving window and burning; flexible pot/stockpot recipes evaluate ingredient proportions into quality. Extra Delight's inspected backport offers broader intermediate preparation and elapsed-time processes, including staged fermentation. These are source capabilities, not observations that a cooking session is enjoyable or profitable. Flexible-recipe quality is not automatically a meaningful property of every imported food or surface, and its world-seeded proportions can be learned and shared rather than providing endless unexplored mastery.

**Integration still wanted:** choose which valuable transformations require an actual player, distinguish active labour from passive maturation, and audit alternative crafting/Create recipes and interaction automation so they do not silently bypass the intended work. Neither mod has been established as inherently non-automatable. Avoid making labour merely hundreds of identical clicks: a useful batch can combine purposeful preparation and finishing with simmering, resting or fermentation between interventions. Existing drying remains available unchanged; redesigning it is not a required current kitchen step.

Integrate overlapping ingredients, crops, utensils and acquisition routes without bypassing existing cultivation rules. Deliberately map nutrition, food variety, hydration and effects to the pack's physiology; Kaleidoscope quality must not become an accidental second or conflicting nutrition system. Future async trade should preserve and explicitly match any meaningful dish quality, not accept a lower-quality result merely because its item ID matches. Equivalent novice-collected raw materials remain equivalent; output quality should reflect the actual preparation, not a hidden penalty for a new player.

#### Approved preparation and quality defaults

There is no chef XP or work currency. For a reference batch of **eight actual edible portions**, approved active-work targets are straightforward **20–40 seconds (30 target)**, substantial **60–120 seconds (90 target)** and elaborate **180–300 seconds (240 target)**. Preserve original input/output ratios and native portion counts, prevent whole-stack noodle bypasses, and distinguish real connected-player work from passive cooking time. Shared/tradable preparation and persistent stage progress must not become owner-locked. Valid tool/reach/target and action cadence, reject-before-debit, interruption, full-output and break recovery all need actual lifecycle tests; these target values are not observed session results.

Approved quality factors are **Poor .75 / Standard 1 / Excellent 1.15 / Superb 1.30**. Preserve the existing seeded flexible-formulation evaluator; fixed formulations are Standard unless carrying real preparation quality. Never reroll, compound quality or launder poor preparation. Apply quality once to hunger, **total saturation** (not nutrition and saturation modifier twice) and ordinary lasting nutrient entitlements. Ingredient provenance is divided by actual yield; direct tags are fallback, not extra credit. Water, sugar, alcohol and actual Spice dose are not quality-multiplied. Ordinary work-class base defaults are 6/.5, 8/.7 and 10/.9 nutrition/saturation modifier, with staple/special exceptions retaining base properties. Newly applied compatible benefits are capped at level I / 60 seconds without weakening preexisting stronger effects; harmful probabilities remain intact. Disable shield and suppress Kaleidoscope vigor/vitality/preservation/projectile_dodge/hinder/instant_smelting/warmth benefits; tundra_strider/sulfur/mustard remain allowed under the caps. These are the approved source contract, not a claim that every native effect and serving path is qualified.

Industrial preservation remains a worthwhile complementary ambition, but its complete design, material loops and acceptance checks are now in the standalone [deferred preservation section](#deferred-preservation-selective-drying-and-industrial-provisions), not current cooking implementation requirements.

#### Validate complete kitchen sessions, not recipe counts

Before claiming the integrated kitchen is qualified, follow representative end-to-end workflows: **a stir-fried meal, a flexible soup, a baked or fermented product**, alongside an accessible automated staple as the comparison. Distinctive brewing/fermentation/aging apparatus remain legitimate. Bulk canning and redesigned drying are deferred and are not current cooking acceptance gates. These are required technical integration and separate play-observation checks, not tests already established as complete.

- Measure acquisition/setup burden, purposeful interventions, repeated inputs, passive elapsed time, useful batch yield and cleanup/container returns. More clicks or a longer timer do not by themselves establish satisfying work.
- Compare complete alternatives, including ordinary crafting, FD/addon recipes, Create, Tinkers, delegated cooking and interaction automation. Confirm the chosen canonical path and each deliberate exception; do not infer success from a clean recipe browser.
- Check consumption and physiology across portions, quality tiers and container forms: hunger, nutrients, hydration, age, saturation and special effects need distinct oracles. Verify actual completion once, preview/cancellation zero, no duplicate Diet/Spice/physiology intake and matching tooltip values. All culinary transforms, including nonedible prepared batches, must retain oldest settled input age without a freshness reset; existing drying recipes/properties/clocks/storage rates remain unchanged.
- Check whether a learned formulation and its apparatus can be explained, copied and adapted by someone else without specialist catch-up or hidden personal chef permissions. Recipe sheets can transmit proportions; their preview is not evidence that the author successfully cooked the dish.
- Observe repeat demand and competing supply, including whether another player voluntarily values a kitchen batch. Do not substitute NPC buyback, recipe complexity or runtime acceptance for that evidence. Industrial-provision demand checks belong to the deferred preservation work.

A successful short kitchen session should produce useful food and room for judgment, not a compulsory daily chore or hundreds of identical clicks. Both the solo player using adequate staples and the group valuing a cook's work should remain viable; industrial provisions should have real uses without erasing cuisine.

References: [Kaleidoscope Cookery](https://modrinth.com/mod/kaleidoscope-cookery), [selected Forge 1.20.1 release](https://modrinth.com/mod/kaleidoscope-cookery/version/Ghp0qCKY), [Extra Delight unofficial 1.20.1 backport](https://modrinth.com/mod/extra-delight-unofficial-port). The approved source contract fixes work targets, quality scaling and hydration defaults; complete route classification, migration coverage, native lifecycle/real-client acceptance and human outcomes remain to be established.

### Expertise should expand others' agency

A communal workshop is desirable. Someone constructing it first is not automatically monopolising play. The desired result is that its tools let others begin distinct projects with their own decisions, not that everyone waits for one expert's next unlock and then follows predetermined assembly steps.

**Inspected foundation:** shared apparatus and transferable resources can lower participation costs.

**Divergence/open work:** many capabilities reside in items and apparatus; one expert may establish several branches while others merely operate them. Source complexity is not evidence of satisfying specialisation. Examine acquisition, construction, judgment, operation and adaptation separately. No observed division of labour yet establishes that infrastructure distributes rather than concentrates agency.

### Emergent vocabulary and transmissible knowledge

Do not prescribe a strategy glossary. Explain rules clearly, then let players invent or repurpose language for recurring distinctions they find useful. The desired loop is **discover a distinction → develop a method → communicate it → another person acts on or adapts it**. Independent groups need not invent identical words, and vocabulary volume is not a success metric.

**Inspected foundation:** vehicle configurations, cultivation arrangements, spell compositions, expedition preparation, geology and processing supply potential distinctions. Specific authored Ponder demonstrations, contextual learning and item information explain mechanisms; player constructions can demonstrate solutions; Traces can carry situated instructions, warnings, claims and memories. These surfaces are complementary, not interchangeable.

**Divergence/open work:** no candidate-linked human observation establishes that these surfaces routinely transmit methods, develop shared language or alter recipients' decisions. Recording fidelity alone is insufficient; understanding and adaptation matter. The inspected custom Ponder integration demonstrates particular mechanisms, not a general player-method publishing system.

### Multiple legitimate modes, including optional lineage

Solo play, continuing shared worlds and lineage succession should all be legitimate. **Lineage is a chosen mode, not the intended eventual fate of every world.** It can offer consequential loss, inheritance and renewal without universal appeal or identical rewards across modes.

**Inspected foundation:** succession perks chiefly affect successor-world setup; the inspection did not establish resets as a requirement for ordinary industrial or magical advancement.

**Divergence/open work:** the inspected succession support is dedicated-server dependent and schematic publication is operator controlled, with publisher attribution not necessarily identifying the designer or contributors. Evaluate continuity, preservation and renewal among people choosing lineage play rather than treating succession as every community's required rhythm. The separate entropy/Condemnation ambition below is not already implemented by these foundations.

### Social-design priorities and validation

Technical reliability, accurate prose and completed distribution remain necessary work. Known work-in-progress issues are not the main unresolved social-design thesis. The substantive priorities are:

1. Define and protect independently deep practices above the shared Create/Tinkers foundation.
2. Preserve accessible contributions in mature worlds without specialist catch-up.
3. Provide asynchronous player exchange that makes actual demand visible and dependable.
4. Check that common infrastructure opens agency rather than making one expert indispensable.
5. Observe whether people communicate, reuse and value one another's work.

Audit actual capability routes before writing branch contracts. Trace AND/OR acquisition channels, alternative recipes, apparatus, world access, loot and bypasses; then assess effort, continuing judgment, transferability, dominant alternatives, solo viability and late-game usefulness. A dependency graph is necessary but insufficient, and a recipe prerequisite is not automatically an all-channel bottleneck.

Small independent observations of a solo newcomer, friends, a late joiner and a returning player can test these questions. Neither technical Smoke acceptance nor source breadth demonstrates enjoyment, retention, successful specialisation or wider adoption. The broader social discussion does not authorise implementation, recruitment or playtest launches; the narrow cooking mod-addition authorisation is recorded separately above.

## Deferred preservation: selective drying and industrial provisions

**State — DEFERRED, 2026-10-28 09:50 UTC:** a standalone aspiration, not part of the currently authorised coherent-cooking implementation or its acceptance gates. No new canning, drying, preservation recipe, property or aging mechanic is implemented or authorised by this section. **Existing drying recipes, dried-food properties, spoilage/aging clocks and storage rates remain unchanged.** The criteria below describe prospective redesign, not current runtime guarantees.

### Selective drying and baseline shelf stability

The future design would make an eligible wet food shelf-stable through drying, but only selected wet foods should have drying routes. Do not provide a universal drying transformation for every wet food. Audit all foods to identify which should be shelf-stable to begin with, independently of their wet/dry or preparation labels; dry does not automatically mean shelf-stable, and wet does not automatically mean perishable. The intended new drying trade-off reduces **both nutrient contribution and hunger restoration** compared with the source food; shelf stability would be bought at that loss, not obtained as a free equivalent copy. Record baseline stability, drying eligibility and the intended preserved output per food. Exact loss rates, portion yields, shelf-life behaviour, eligible foods and recipes remain to be designed. Reevaluate existing Hexerei/Kaleidoscope drying routes only under that separate scope, not by silently modifying them during cooking integration.

### Industrial-scale canned and dried foods

Automated preservation and bulk production should be a worthwhile complementary practice, not an accidental bypass or necessarily inferior substitute for actively prepared cuisine. Potential uses include expedition provisions, remote worksites, settlement reserves and asynchronous trade. Consider coherent chains such as ingredient preparation → filling containers → heat treatment and sealing, or preparation → controlled drying → packing; these are proposals, not existing recipe integrations. Packaging materials, equipment, energy, throughput and distribution can give the industrial route its own costs and decisions while using the same agricultural inputs.

**Prospective canning contract:** canning would preserve the food's **nutrient contribution and hunger restoration**, unlike the intended drying losses. It requires **food, cans and lids**, with a **Create mechanical press used for sealing**. A sealed can must be opened using **either a can opener or a Create mechanical saw before consumption**; no alternate consumption or unpacking route may silently bypass that requirement. This supplies manual and mechanised opening alternatives without making every recipient build a processing line. Existing Create pressing is not evidence that this sealing process already exists.

### Unresolved quantities, material loops and effects

Exact drying losses, food quantities per can, portion yields, shelf-life behaviour, storage/transport convenience, hydration handling and any rehydration stages, can/lid materials and reuse or recycling, opener durability, processing times, equipment, energy and throughput remain unsettled. Hunger restoration and nutrient contribution are distinct; **saturation and special-effect handling need separate decisions**, not assumptions imported from those terms. The current cooking work/quality/hydration defaults do not silently settle preservation-specific numbers or effects.

Do not claim that a new spoilage model or canning process is already implemented, duplicate can contents for free, or silently copy every premium fresh-dish effect into an unattended recipe. Equally, preserved food need not always be poor food: both industrial provisions and actively prepared meals should have genuine uses without universally dominating one another. This direction is not a blanket industrial-cooking ban, a promise to install another mod or authorisation to change existing food stability now.

### Eventual preservation-specific checks

When separately authorised, include a complete bulk canned-food workflow and a selected dried-food workflow in integration and demand checks. Verify food/portion/container accounting, intended nutrient/hunger conservation for cans, both intended drying losses, quality/saturation/effect decisions, and that sealed cans cannot be eaten or unpacked around the opener-or-saw requirement. Test material return/reuse/recycling and opener wear without duplication, actual storage/transport behaviour and equivalent alternate routes. Observe whether industrial provisions have useful demand alongside cuisine; technical recipe/runtime acceptance, NPC buyback or unattended throughput alone does not establish human value. These are deferred requirements, not tests run or passed.

## Player and horse-powered Create stress

**State:** Early-game aspiration, not a claim that the new generators or their balance are implemented. Create already includes hand cranks. Create Horse Power has a 1.20.1 Forge Packwiz entry, but its inclusion in an accepted runtime and its place in progression still need verification.

Make manual and animal-powered rotation useful before self-running power without turning repetitive input into the best permanent factory strategy. All outputs should be usable **Create stress capacity (SU) at a defined RPM**, with clear input conditions and behaviour when the player or animal stops.

- **Hand crank:** Keep Create's built-in hand crank as the straightforward baseline; do not duplicate it with a new block. Use it as the reference for effort and early recipes.
- **Walking/running treadmill:** A conveyor-belt-style reskin or mechanical variant that converts the player's *forward movement relative to the belt* into rotation. Forward speed should affect output within a bounded range. Standing still, facing forward while motionless, or being carried by the belt must not generate free power; choose how sprinting, speed effects and exhaustion count.
- **Striking target:** A block the player can hit to drive a shaft. Reward actual valid strikes per second up to a cap; define whether tool type, attack cooldown and damage matter. Holding attack against a macro or abusing invulnerable targets should not create unbounded or costless output.
- **Jump-driven platform:** Convert repeated player jumps into rotation. Output should scale with actual jump height (and a completed landing or other physical work), within limits; simply holding jump or exploiting bouncing/flight should not produce infinite SU. Decide how jump boosts, fall risk and hunger expenditure enter the trade-off.
- **Horse crank:** Give Create Horse Power the same early-game treatment: accessible recipes, visible SU/RPM and realistic upkeep for a working animal. Compare sustainable output, space and care against hand cranking and player-operated machines. Do not assume its default config or recipes already fit the pack.

### Ecology tokens for the recipes

Every source should use **Andesite Alloy** plus its own legible Overworld-ecology ingredient:

| Power source | Ecology theme | Ingredient and purpose |
| --- | --- | --- |
| Hand crank | Woodland | Planks for its wooden handle/frame; the existing pack recipe already uses three planks, one Andesite Alloy and one Create shaft. |
| Treadmill belt | Ocean/coast | Kelp for a belt-like flexible element. |
| Striking target | Desert | Sand to fill a strikeable sandbag target. |
| Jump platform | Jungle | Bamboo for a flexible springboard structure. |
| Horse crank | Grassland | A hay bale (wheat block) to represent feed for a working horse. |

These are the selected **recipe directions**, not a claim that the new devices or the horse-crank recipe already use them. The ingredient origins are thematic rather than strictly biome-exclusive: planks, sand and farmed wheat can all be obtained outside their named ecologies, and harvested kelp or bamboo can be grown elsewhere. If visiting distinct biomes must be mandatory, that requires a separate acquisition design; do not describe this table as enforcing it. Avoid rare drops or rare-biome components as early-game gates.

Progression should provide a reason to try each method: hand crank for a first workshop task, treadmill/striking/jumping as distinct active player contributions, then animal power for longer-running pre-engine tasks. Match recipes, throughput, food/stamina or care costs, and teaching/advancement cues to that arc. Active player power should remain useful when short on fuel or infrastructure, while later automatic generation earns its advantage without making the early methods mandatory chores. Multiplayer should count each participant's real contribution without duplicating power from one action.

Open choices: concrete SU/RPM curves, machine designs and animation, what movement counts as work, anti-exploit bounds, compatibility with Create kinetic networks, how horse-crank configuration and animal welfare interact with existing pack systems, and where each source unlocks. No numbers or implementation route are settled here.

## Tinkers' parts as widely usable manufacturing components

**State:** Design aspiration, not a claim that all machines, blocks or products already accept Tinkers' Construct parts or preserve their material identity.

Tinkers' tool and component parts should be usable across a broad range of the pack's manufacturing, not confined to tool assembly. Machines, fixtures, vehicles, infrastructure and specialty products could be built from appropriately shaped parts wherever a real component relationship makes sense. The parts system should support a **wide variety of viable materials**, with recipes, casting/forming routes and interfaces that make those choices accessible and understandable; don't make one universal material the effective answer for every application.

A finished product **may** derive selected properties from the materials and parts used to make it, but it does **not have to**. Use inheritance when it creates an intelligible manufacturing choice. A product whose function is unchanged by its constituent material can simply consume the part without carrying dynamic stats. Do not automatically apply every property of every ingredient to every product.

**Replace generic machine tiers with material-property-based behaviour where it matters.** The intention is to treat Tinkers' existing material stats as the shared engineering vocabulary, or aliases for it, rather than inventing an unrelated second stat ladder. A machine's working capability, rate, precision, wear or response to loads can emerge from the relevant parts and construction instead of an arbitrary bronze → steel → advanced upgrade. A stronger frame need not improve a cutter's speed; a faster-moving part need not increase vessel capacity. Material traits may provide qualitative behaviours. Where a material-to-machine interpretation is not useful, no derived behaviour is required.

### Material stats as machine-facing properties

These **proposed aliases** are short names for interpreting Tinkers' material data, not claims that machines already use the values:

| Tinkers' material stat | Machine interpretation |
| --- | --- |
| `durability` | Service life |
| `mining_speed` | Material removal rate |
| `mining_tier` | Workpiece hardness limit |
| `melee_attack` | Impact force |
| `melee_damage` | Impact force |
| `melee_speed` | Cycle rate |
| `accuracy` | Manufacturing precision |
| `draw_speed` | Tensioning rate |
| `velocity` | Launch speed |
| `armor` | Surface protection |
| `toughness` | Shock tolerance |
| `knockback_resistance` | Recoil stability |

Tinkers' material traits are separate from these twelve stat fields and could provide **special behaviours**. The same stat can be a base value on one part and a modifier on another: do not compare head durability directly with a handle durability adjustment, or confuse Tinkers' mining tier with a generic machine tier. A recipe should choose parts whose **actual supported stat roles** match the machine's job: heads for a drill's hardness limit and removal rate, limbs for a tensioner or launcher, plating for protective housing. Bindings and other parts with no relevant numeric stats can still be construction ingredients without pretending to determine precision or throughput. The machine-specific mapping and balance remain to be designed, but the stats themselves are intended to carry the material choices.

### Making parts and showing their effects

- **Part Builder:** Select a part pattern and provide an eligible solid material to make a material-bearing part directly. This is the existing non-pouring path, not a basin that presses any solid into any shape.
- **Casting Table:** Place the matching sand or reusable *cast* (not a finished part), then supply an eligible molten material; the cast supplies shape and the fluid supplies material. Create 6.0.8 already registers explicit spout support for Tinkers' Casting Tables and Basins, and this pack's `defaultconfigs/create-server.toml` enables `allowCastingBySpout`. The spout checks the block's Forge fluid handler and fluid validity; it does not fill arbitrary tanks by default. The Basin generally serves larger cast outputs rather than being the ordinary tool-part press.
- **Other forming:** Pressing solid stock into eligible shapes could be an additional authored manufacturing route, not an existing universal Tinkers' recipe. Define which materials and part types genuinely support it.
- **EMI:** Show both how each eligible material becomes a particular part and how that *specific material–part combination* affects a particular machine. Display relevant input stat roles, resulting performance and trade-offs at recipe/part/machine lookup; casting recipes alone do not explain the machine outcome. Dynamic combinations may require generated displays or dedicated integration, not a claim that current EMI already shows them.

The interesting outcome is a network of trade-offs: raw-material availability, preparation and processing effort, efficiency, durability, weight, thermal or chemical limits, maintainability and recycling can favor different builds in different circumstances. Material alternatives should remain useful at different scales, from one-off hand-built equipment to repeatable factory production, without introducing free duplication or making high-end inputs categorically mandatory. If a product *does* inherit properties, represent its material choices clearly in recipes, tooltips and saved item/block state so those differences persist through placement, transport, repair and multiplayer use.

Open choices include which part forms and machines to support first, which properties merit inheritance, how material stats translate to non-tool products, how casts and assembly integrate with other mods, how equivalent parts are tagged, and how quality, costs and throughput are balanced. Broad support is the direction; an indiscriminate recipe for literally every item is not required. The [machine/material candidate index](machine-material-candidates.md) groups inspected pack apparatus and existing upgrade systems for these decisions; it is a design inventory, not a verified acquisition graph or implementation commitment.

## Valkyrien Skies: physical motion and spacecraft

**State:** Wanted and deferred; this is a replacement ambition, not a completed physics model. The isolated [VS spaceflight work-in-progress directory](../../feature-labs/vs-spaceflight/) contains the current prototype, evidence and status; its scope does not by itself fulfill the broader physics ambition below.

Vehicles and moving contraptions should have understandable sources of motion and Newtonian responses, including equal-and-opposite reactions. Player-built spacecraft would use that same physical model, replacing Creating Space's spacecraft rather than maintaining a disconnected second flight system.

- Engines provide power; wheels, propellers, control surfaces, thrusters and mechanisms apply forces through contact, a medium or expelled reaction mass. Power alone is not thrust. Internal mechanisms cannot accelerate an isolated craft's center of mass for free.
- Pushing, towing, docking, collisions and mounted mechanisms produce corresponding reactions. Momentum transferred to terrain, air, water or exhaust can use practical approximations without simulating every particle.
- Mass, inertia, force locations and torque matter. Loads change acceleration; off-center thrust rotates a craft; braking and attitude changes need physical means.
- Craft coast without unexplained stopping, drag or reactionless steering. Vacuum propulsion uses appropriate finite propellant/reaction mass; atmospheric propellers and aerodynamic control surfaces do not work in vacuum without a separately justified mechanism.

Open choices include simulation limits, controls, explicit supernatural exceptions, construction, atmosphere/space transitions, gravity, travel scale, persistence and multiplayer behavior. **Orbital simulation is unresolved**, not assumed. Replacing Creating Space spacecraft also leaves a separate decision about its non-spacecraft content and existing progression dependencies.

## Contextual Threads still wanted

**State:** Future teaching cards, not proposals for new movement or drinking mechanics. They were not intended to fill the four previously vacated active slots.

- **Water bottle:** preparation and carrying advice that complements the existing safe-water lesson.
- **Grappling hook:** acquiring/using the pack's hook and its traversal role.
- **Climbing:** explanation at a personal point of need.

Exact triggers, copy and scenes remain unchosen. These cards should explain an actual opportunity, not imply a future mechanic already exists.

## Abyssal City: hydraulic reclamation and finite ecology

**State:** An isolated source prototype, not installed in the pack or a completed gameplay-ready experience. See the [Better Abyssal City work-in-progress repository](../../mod_source/better-abyssal-city/) for its current scope, generation work and status; the hydraulic and ecological loop below is not yet implemented there.

An explorable layered underwater city would combine dangerous mystery fluid, sealing and pumping, player reclamation, finite local Gatherer/Warden ecology and persistent travel between cities.

The prototype had planning/generation, structural fixtures and experimental traversal tooling. Generation readiness, hydraulics, actors and a complete playable loop remained unfinished.

The desired navigability model is compositional: meaningful pieces and connections, assembled-world routes without accidental obstructions, and representative journeys. Completion means reclaiming and inhabiting/traversing a working city under its hydraulic and ecological pressures—not merely generating attractive pieces or steering through an exhaustive tour.

Open choices include how reclamation changes access and danger, finite actor/resource behavior, multiplayer persistence, and whether/how the standalone experience eventually joins the main pack.

## Hydrology: complete and integrate finite water

**State (2026-10-28):** The isolated [Better Finite Water source repository](../../mod_source/better-finite-water/) resumed its previously stopped qualification work under a separate explicit user request. It remains disabled by default and is not an active pack input. Its [completion audit](../../mod_source/better-finite-water/docs/COMPLETENESS.md) records bounded functional qualification, **not a complete or release-ready hydrology system**; that dated audit may lag resumed work. The earlier [water-simulation research](../../water-simulation-research/) is a screening experiment, not a deployed mod. The new world-generation and erosion ideas below are **additional aspirations beyond the source project's frozen first-support scope**, not new requirements imposed on its current qualification run.

The intended water system should make finite bodies behave as accounted-for volumes: downhill flow, connected-level and pressure equalisation, real damming and drainage, and meaningful interaction with excavations and future pumps. The ordinary vanilla water view should follow a server-owned volume ledger; one block equals 65,536 integer quanta internally, while rendered/block fluid levels remain coarser. Only **proven natural ocean connections** may act as unquantified sources or sinks at sea-level head. Unknown terrain must remain unresolved rather than silently supplying or swallowing water, and simulation must not generate distant chunks merely to discover an answer.

### Rivers made by water, not carved by worldgen

The longer-term world design would **remove predetermined worldgen rivers**: new terrain should not begin with vanilla-style pre-carved, pre-filled river channels simply because a river biome was selected. Rivers should instead emerge where sustained water input and the terrain's gradients drive flow, cut channels and carry sediment toward lower ground and oceans. That implies explicit, accountable sources such as rainfall, runoff, springs or watershed inflow; a finite-water ledger alone cannot create a persistent river from nothing. Ocean boundary rules must still distinguish genuinely proven natural sea connections from inland finite water.

**Water-flow-driven erosion** should make a meaningful difference to terrain: moving water can wear away susceptible material, transport sediment and deposit it where flow slows, potentially shifting banks, building deltas or changing a channel's course. Resistance, transport capacity and deposition should depend on flow and substrate rather than random block deletion or instantaneous global reshaping. Excavation, dams, culverts and sediment management could then become real engineering decisions. Erosion and terrain edits must update hydraulic geometry and conserve/account for moved material and water; they must not silently bypass the existing water ledger.

This is a large worldgen-and-simulation change, not a switch to flip on existing saves. Decide how rivers begin, how climate/biomes and springs supply water, what happens at existing-world/new-chunk seams, whether old rivers are preserved or explicitly migrated, and how player-built terrain and settlements are protected. Bound erosion work and chunk access so rivers cannot cause unbounded terrain edits, runaway server cost or generation of unexplored chunks. Visual and gameplay acceptance must show actual sustained channels and sensible landscape change—not just a world with its old rivers removed.

The current V7 source passed a named `verifyFull` run (105 fast checks, 23 native GameTests and four dedicated server lifetimes) and 20 additional native regression cases for its **narrow first-support scope**: ordinary water in full-block solid/air geometry, native buckets and tested edits, with normal save/restart and generated/unloaded geometry. Those passes do not establish scalable real-world performance, arbitrary terrain equilibrium, pack compatibility or release acceptance.

Completion requires, at minimum:

- **Native scale and ordinary terrain:** demonstrate the 4,096-cell sheet and measured full-server latency against the stated performance gate, plus real generated-terrain behavior. The corrected native large-sheet case was not run to acceptance; a nine-chunk natural-terrain scout found no qualifying enclosed candidate. Neither is a pass.
- **Safety and continuity:** preserve exact conservation, ocean exchange accounts, pending water and hydraulic provenance across real chunk unload/reload, edits and normal restarts without force-loading/generating terrain. Corrupt or incompatible existing saves must refuse safely; any player-world migration needs an explicit design, not guessed water origins or a reset.
- **Supported interactions:** deliberately qualify or safely reject waterlogging, sponges, freezing/melting, pistons, block displacement, entities and modded pumps, pipes, tanks or machines before calling the system pack-compatible. The current narrow guards for selected unsupported actions are not general integrations. Future water-related mechanics should participate in the ledger instead of duplicating or deleting water.
- **Pack adoption:** only after the isolated project meets its own completion/release requirements, decide activation, compatibility, world-transition policy and acceptance on an exact modpack distribution. Its GitHub publication and passing source-local functional tests do not themselves install it or satisfy Smoke/Soak.

Open design choices include the desired scope beyond first-support water, visual/physical fidelity, water-machine interfaces, acceptable simulation cost under large bodies and edit storms, and whether/how the Abyssal City hydraulic loop should use the same foundation. This aspirational worldgen/erosion expansion does **not** authorize changing the isolated project's existing qualification contract, deploying to the pack or promoting old evidence.

## Lineage endgame: entropy caching and Condemnation

**State:** Durable endgame concept. The old documentation identified no complete entropy economy, Condemnation system, authored curse roster or curse-specific assets. World succession and the Condenser were foundations, not this finished loop. See the [recovered lineage endgame notes](../../recovered-world-condenser/lineage_endgame.md) for the longer concept and the isolated [World Condenser work-in-progress directory](../../feature-labs/world-condenser/) for current prototype status. That lab explicitly excludes entropy and Condemnation from its scope.

### Worlds as production environments

**Entropy caching** is a persistent economy belonging to a lineage above individual worlds. Its structural inspiration is an idler economy, but its in-world substance is entropy, not antimatter. A world can become an expendable production environment.

**Condemnation** lets players irreversibly curse the current world to increase danger and lineage value. This wagers a world, rather than choosing a difficulty preset. Extreme escalation can make the world unsustainable and force succession.

The original shorthand was survival yield `Y = ∫ S(t) dt`: a curse is active and the world is still functioning, so meaningful survived time earns value. The accompanying intent was **convex reward versus severity**, so brief extreme risk can outvalue long safe farming; the actual severity-to-yield function remains unchosen. Fighting, retreat, repairs, evacuation and redundant infrastructure can all make survival meaningful. Banking on voluntary versus forced succession remains open.

### Curses have agendas, not just stat multipliers

The model is **agenda → objectives → available actions → victory condition**.

A curse can be an apocalypse-scale actor, process or altered world rule. Strategic choices may involve territory, settlements, routes, energy, production, resources, defenses, other factions, entropy infrastructure and asset survival.

Ordinary pre-endgame combat remains readable and deliberately imperfect. Condemned actors can instead optimize for their own purposes: avoid losing fights, preserve assets, blockade, attack logistics, exploit absence, abandon obsolete objectives, cooperate temporarily or pursue world nonviability. Their unfairness comes from strategic agency, not arbitrary cheating or unreadable unavoidable damage.

Multiple curses coexist. They need not prioritize the player or regard each other as allies. Threats can destroy, feed on, flee from or temporarily cooperate against one another. Directional invasion remains important: geographic origin, travel, routes and perceptible approach—not enemies materializing next to the player because a menu changed.

### Persistence as narrative

The player's lineage need not be the only one using caches or analogous cross-world mechanisms. Other actors have independent progress, interests, failures and incomplete knowledge.

A useful encounter is mutual recognition: the player enters an installation for another reason, recognizes an unexpected cache, and its owner reacts because that recognition reveals the player's capabilities. Both learn something surprising. A functioning cache conveys more than a book explaining one.

Repeated names, equipment, behavior, recovered technology and consequences can carry story across generations. Defeat advances knowledge without guaranteeing victory. Destroying a world need not erase its narrative significance. Explanations can remain uncertain between convergence, inheritance, common origin, independent discovery and cross-world contact; other actors use their own concepts rather than quoting player-facing rates and tiers.

### The intended complete loop

Discover/build the Condenser → establish lineage caching → choose irreversible Condemnation → survive interacting world-scale threats → evacuate or lose the world → settle value and discoveries → enter a successor with meaningful new capabilities and consequences → encounter further mechanically grounded narrative.

A reset button, growing number, single boss or textual ending does not complete that loop. Existing schematic knowledge, discoveries and generation-funded perks give continuity but do not already constitute entropy-funded rewards.

### Open design choices

| Question | Preserved direction or uncertainty |
| --- | --- |
| Shared-world consent | Who may condemn a world? Distinguish shared danger from personal lineage entitlement; account for griefing, late joiners and offline players. |
| Entry gate | Early successor access and dangerous entropy production need not unlock together. The meteor-material Condenser recipe did not establish late-game mastery. |
| Meaningful survival | Decide clocks, pauses, offline time, infrastructure requirements and anti-idle behavior; safe wall-clock waiting is not automatically production. |
| Severity | Choose a bounded convex curve and composition rule reflecting actual enacted danger. No numeric balance or roster was settled. |
| Cache ownership | Physical object versus abstract balance; capacity, destruction, theft, evacuation, spending and respec remain open. |
| Banking | Voluntary evacuation, intended forced world loss and failed succession are different outcomes. No double banking or spending through retries. |
| Rewards | Meaningful successor capabilities and consequences, not only larger rates; keep the existing generation-funded perk graph distinct unless deliberately redesigned. |
| Terminal conditions | Define each curse's victory and world nonviability, with warnings and possible recovery; a crash is not an intended apocalypse. |
| Platforms | Integrated single-player succession was disabled in the recorded foundation. Merely removing that guard is not a complete single-player design. |
| Breadth | Choose the first curse and launch roster; a second distinct curse is important to establish actual interaction, not only independent parallel waves. |

A useful development shape in the old design was one complete curse/caching/succession loop, then a second strategically different curse, then broader content and lineage rewards. The gameplay questions behind that sequence remain relevant: competing objectives, resource/territory contention, destruction of another actor's objective, retreat, cache loss, player absence and consequences that survive reloads and later generations. Permanent remote chunk loading was not intended as the hidden price of strategic simulation.

## Better plastics and connected chemical industries

**State:** New aspiration, not an implemented production chain or a claim that the current machines, fluids or Tinkers' casts already interoperate.

Plastics should be materials made through understandable chemical processes, with different feedstocks and polymer properties—not a single generic oil-to-plastic recipe. A player might refine or ferment feedstocks, make monomers or resin precursors, polymerise or cure them, then choose how to shape the result. Recovered feedstocks and recycled scrap should have deliberate costs and limitations rather than becoming free loops.

- **Casting:** Where a polymer can be processed as a melt, route molten plastic through a spout into compatible Tinkers' Construct casts to form parts. Define which materials and casts qualify, melt/processing temperatures, cooling or curing, required volumes, and whether casts survive reuse. Not every resin is remeltable: thermosets and light-cured materials need distinct processes rather than pretending they behave like thermoplastics.
- **3D printing:** Printers consume suitable filament, pellets or printable resin, plus power, time and any necessary supports or finishing inputs, to produce shaped parts. Printing should offer different geometries, small-batch flexibility or precision—not a universally cheaper substitute for casting. Layered deposition and light-cured resin printing need not share the same feedstock or machine behavior.
- **Material identity:** Different polymers can trade toughness, flexibility, chemical resistance, heat tolerance and insulation. Fibres, rubbers, coatings and adhesives can share upstream chemistry without all becoming the same item. Outputs and byproducts must conserve material across fluid, cast, printed and recycled forms.

Chemistry spans the classical branches—organic, inorganic, physical, analytical and biochemistry—but these overlap rather than forming five isolated tech trees. Organic chemistry is broadly carbon-compound chemistry; polymer chemistry crosses organic synthesis, physical/materials chemistry and sometimes biochemistry. PLA, for example, can connect biological feedstocks, organic intermediates, polymer processing and material properties.

Organise gameplay primarily around **interconnected production families**, not academic labels: petroleum refining and cracking; organic synthesis; polymers, rubber and fibres; industrial biotechnology; inorganic acids, alkalis, salts and fertilisers; electrochemistry; metallurgy; ceramics and glass; industrial gases and syngas; and specialty chemicals such as silicones, fluorochemicals and catalysts. Analytical chemistry can make identification, purity and quality control meaningful across these families, while environmental chemistry can give waste treatment and recovery a purpose. The families overlap in feedstocks, apparatus and outputs.

A mature chemical complex should share gases, acids, solvents, catalysts, intermediate chemicals and recycled byproducts instead of being ten disconnected recipe lines. Industrial inorganic and gas chemistry are promising backbones: sulfuric acid could serve fertiliser production, metal processing, petroleum refining and cellulose chemistry; hydrogen and syngas could link fuels, ammonia, methanol and polymer feedstocks. Cross-industry dependencies should reward integration without forcing every product through the entire complex.

Open choices include the first useful polymer roster; real versus simplified reaction steps; how purity and yields affect play; safety and waste handling; compatibility with existing mod fluids, Tinkers' casting and machine recipes; and whether printed parts gain any unique function. The [machine/material candidate index](machine-material-candidates.md) identifies possible consumers of cast or printed parts, including existing tier and upgrade systems; none has a validated polymer-part integration. No specific recipe, machine integration, property balance or progression gate is settled here.

## Provenance

The Valkyrien Skies, Threads, Abyssal City and lineage aspirations were recovered from modpack history, including commit `6004642691c0`. The plastics and chemical-industry direction above was added later. The social-goals section records the 2026-10-28 discussion, its clarified shared Create/Tinkers foundation, the proposed asynchronous player market and a dated implementation comparison; it is not a report of observed social outcomes. Links above point to separate work-in-progress directories and notes, not to accepted pack features.
