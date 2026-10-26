# Aspirational features

Recovered player-facing ambitions and unresolved design questions for Better Content. These are not all unstarted: some were deferred, some had source prototypes, and some were partly implemented when the old documentation was removed. Status descriptions below reflect that historical record, not a new assessment of the current game. Established principles are collected in [Gameplay design choices](gameplay-design-choices.md).

## Chilli and a saturation-backed health economy

**State:** Early discussion; core mechanics and numerical balance remain unsettled.

Spicy food should have a distinct dietary identity. Filling meals should fuel heightened performance and the recovery it costs, rather than merely leaving the player unable to eat for a long time.

### Direction

- **Chilli is broader than peppers.** Culinary heat, pungency and tingling/numbing spice can include Sichuan pepper. Garlic, onion and clove contribute much less than chilli peppers. This is a food identity, not a capsaicin measurement; aroma alone need not qualify.
- **Seasoning applies broadly.** Adding spice to ordinary meals would add Chilli alongside their existing food groups. Application, item representation and division among servings remain open.
- **Trade actual HP for boosts.** Strong spice causes substantial direct damage, rather than reserving maximum HP. Mild pungency implies smaller exposure. The meal's stored food energy can fund recovery.
- **Saturation becomes the fuel for HP recovery.** Healing spends saturation, not merely checks that some is present. Time controls conversion speed, not unlimited free restoration. The zero-saturation, hunger-only natural-healing fallback would need reconsideration.
- **Other healing enters the same economy.** One discussed example converted Instant Health into roughly ten seconds of saturation delivery rather than direct HP. Ten seconds was illustrative: total yield and delivery rate both need limits. Regeneration, magic, lifesteal and other sources cannot silently remain free bypasses.
- **Nourishment and modifiers need distinct roles.** Conserving activity expenditure while still paying healing expenditure was one proposal. Faster recovery and more efficient recovery are different advantages. Stored saturation and the Saturation status effect are separate balance surfaces.

The desired loop is **seasoned meal → Chilli damage and boosts → spend food energy recovering → need food sooner**. A substantial meal can pay its own spice cost more effectively than a raw pepper. Separate restorative food can also support deliberate spice use. Healing counteracting the damage is intentional; maximum spice being free and always optimal is not.

### Open choices

- Which boosts justify mild versus extreme seasoning? What are damage timing, scaling, duration, lethality and the role of a lasting group versus temporary load?
- What are saturation capacity, hunger requirements, conversion rate/cost, overflow and waste? Can the player eat when hunger is full but saturation is depleted? Damage alone does not guarantee the ability to eat.
- How do renewable/non-food healing, absorption, damage mitigation and damage-triggered effects interact without free loops?
- How are arbitrary seasoning, food-group inheritance and bodily recovery divided among the existing systems?

## Plant drinks: plain juices, teas and botanical brews

**State:** The old record described an owner-approved expansion with source implementation in progress. Later food documentation already described an authored catalogue and production rules. The matrices below preserve the design conversation; they are not a claim that every proposed name, effect or preparation is available.

One coherent plant-drink system should cover fruit, berries, leaves, herbs, flowers, vegetables, roots, grains, coffee, cocoa, sap and suitable otherworldly botanicals. Every fruit gets a plain juice and a brewed specialty. Existing teas, milkshakes, cocktails and specialist benefits retain distinct identities rather than being erased by normalization.

### Drink roles

| Role | Meaning |
| --- | --- |
| Hydration | Untreated/dirty water, with its penalties |
| Hydration+ | Purified water and plain plant drinks whose extra value is nutrition, calories or sugar |
| Buff | A distinct functional effect: healing, cleansing, protection, an ability or a specialist action |

Contents determine role, not vessel, workstation or the fact that a drink came from a keg. Nectar amplification alone does not turn a juice into a Buff drink. Milk and honey remain Buff drinks through cleansing, even though they are outside this plant-led expansion.

Safe plain juices do not inherit healing, cleansing, potion effects or chorus teleportation. Those benefits belong in separately named brewed specialties. Not every leaf or seed needs an artificial “juice,” but unpleasant is not unjuiceable: onion, garlic, potato and kelp juice are deliberate inclusions. Milk and honey can remain ingredients; plant-led does not mean every mixed drink is vegan.

### Production model

| Preparation | Intended process and yield | Remaining distinction |
| --- | --- | --- |
| Hand juice | One standardized edible portion and a bottle → one 250 mB plain juice | Ordinary juicy fruit needs no mandatory water, sugar, starter or cooking |
| Create juice | Same portion, mechanical press over a basin → 250 mB | Automation rather than a yield multiplier |
| Bottling/emptying | One serving ↔ 250 mB and the appropriate vessel | Conserve volume and return vessels once |
| Fresh tea/infusion | Water and leaves/herbs/flowers, heated steeping | Kettle or cooking-pot identity, not everything in a keg |
| Primary fruit brew | 1,000 mB juice and a common safe starter flower → 1,000 mB named brew | Eight minutes at temperature band 3 was the selected direction in later source descriptions |
| Developed tea | Prepared tea and further brewing inputs | A reason for the extra stage; retain kombucha's identity |
| Specialist brew | Named base and exceptional agent | Explicit unusual/cursed preparation, not a silent common-recipe substitution |
| Secondary brew | Existing specialty, extra agent and keg time | Four additional minutes was the starting direction, not an automatic upgrade for every drink |

Whole fruits, small berries and slices need comparable edible portions, not blindly identical item counts. Grain/seed milks and starch-based alcohol retain appropriate processing rather than being renamed raw juices. “Brew” includes steeping, infusions, cordials and developed preparations; it is not proof of alcohol or literal yeast fermentation. Booze is explicit per drink.

### Agents

- Ordinary non-harmful flowers form an interchangeable common starter pool; the fruit determines the result.
- Wither rose belongs in explicit death-themed specialties such as Grimberry Rush, not the common pool.
- Chorus flower belongs to the chorus specialty.
- Nether wart and crimson/warped fungi can have selected secondary or unusual preparations, not universal advanced-brewing requirements.
- A few magical/dimensional recipes can justify specific magical flowers.
- A flower used as someone else's starter does **not** grant its own infusion's effect.
- No mandatory new yeast, mushroom or sugar input is needed for ordinary fruit brewing. The flower convention replaced an earlier brown-mushroom idea.

### Plant / preparation / effect matrices

**Retained benefit** means a benefit the old discussion intended to preserve from an existing drink, not proof that the proposed new recipe/name exists. Other effects and new names are proposals. Repeated effects are acceptable: every fruit needs a specialty, not a unique new mechanic.

#### Fruit trees

| Plant | Plain preparation | Brewed specialty | Effect direction |
| --- | --- | --- | --- |
| Apple | Apple Juice | Apple Cider | Retained benefit: Absorption |
| Pear | Plain Pear Juice | Orchard Soother | Retained benefit: pear juice's Lozenge |
| Orange | Plain Orange Juice | Suncrest Tonic | Retained benefit: orange juice's recovery/cleansing |
| Kiwi | Kiwi Juice | Greenbound Fizz | Jump Boost |
| Mango | Mango Juice | Mango Tea / Mango Milkshake | Retained benefits: Strength and Rage Aura |
| Lychee | Lychee Juice | Lychee Cherry Tea | Retained benefit: Strength |
| Peach | Peach Juice | Peach Tea / Bellini Cocktail | Retained benefit: Heal Aura; Bellini also causes Nausea |
| Mangosteen | Mangosteen Juice | Mangosteen Tea | Retained benefit: Sliding |
| Hawberry | Hawberry Juice | Hawberry Tea | Retained benefit: Appetizing |
| Bayberry | Bayberry Juice | Duskberry Cordial | Night Vision |
| Fig | Fig Juice | Featherfall Nectar | Slow Falling |
| Persimmon | Persimmon Juice | Embergold Tonic | Fire Resistance |
| Durian | Durian Juice | Thornhide Brew | Resistance with brief Nausea |

#### Berries and other fruit

| Plant | Plain preparation | Brewed specialty | Effect direction |
| --- | --- | --- | --- |
| Fruits Delight blueberry | Blueberry Juice | Bluehour Cordial | Night Vision |
| Cranberry | Cranberry Juice | Bogclear Tonic | Poison removal on completion |
| Sweet berry | Sweet Berry Juice | Bramblefoot Fizz | Speed |
| Glow berry | Glow Berry Juice | Glittering Grenadine | Retained benefits: Glowing, Night Vision and alcohol effects |
| Mulberry | Mulberry Juice | Blackbough Cordial | Strength |
| Sourceberry | Sourceberry Juice | Spellbloom Tonic | Mana recovery; a distinct Ars interaction |
| Aether blue berry | Skyberry Juice | Cloudbreak Cordial | Slow Falling |
| Grimberry | Plain Grimberry Juice | Grimberry Rush, with wither rose | Retained benefit: Haste |
| Lemon | Plain Lemon Juice | Bittergold Tonic | Retained benefit: lemon juice's protection/cleansing |
| Pineapple | Pineapple Juice | Goldrush Fizz | Haste |
| Melon | Plain Melon Juice | Summerheart Cordial | Retained benefit: 2 HP per full serving |
| Hamimelon | Hamimelon Juice | Dawnheart Cordial | Absorption |
| Chorus fruit | Chorus Juice | Voidstep Elixir, with chorus flower | Slow Falling and a completion-gated random blink |

#### Tea, coffee and cocoa

| Plant/base | Plain preparation where appropriate | Specialty | Effect direction |
| --- | --- | --- | --- |
| Green tea leaves | — | Green Tea | Retained benefit: Haste |
| Developed green tea | — | Kombucha | Retained benefits: Haste and alcohol effects |
| Yellow tea leaves | — | Yellow Tea | Retained benefit: Resistance |
| Black tea leaves | — | Black Tea | Retained benefit: Caffeinated |
| Tea with milk | — | Builder's Tea | Retained benefit: Haste |
| Coffee berries/beans | Coffee Cherry Juice | Coffee / Cascara Tonic | Coffee's Caffeinated retained; Cascara using it proposed |
| Cocoa | — | Hot Cocoa | Retained benefit: one random harmful, curable effect removed on completion |
| Rose bush/hips | Rose Hip Juice | Rose Hip Tea | Retained benefit: Regeneration |
| Dandelion | — | Dandelion Tea | Retained benefit: Comfort |
| Lemongrass | — | Lemongrass Infusion | Comfort |
| Ginger | Ginger Pressing | Hearthfire Ginger Tea | Nausea removal on completion |
| Ube | Ube Milk, not raw juice | Ube Milk Tea | Retained benefit: Night Vision |

Long/Strong variants remain within their families. Coffee cherries and processed beans can justify different preparations. The former source notes identified rose hips for Rose Hip Tea and coffee berries plus glow berries for Gambler's Tea; drink names alone do not establish ingredient availability.

#### Vegetables, roots and grains

| Plant | Plain preparation | Brewed specialty | Effect direction |
| --- | --- | --- | --- |
| Carrot | Carrot Juice | Goldeneye Tonic | Night Vision |
| Beetroot | Beetroot Juice | Strongroot Ale / Steel Toe Stout | Retained benefits: Resistance and alcohol effects |
| Tomato | Tomato Juice | Bloody Mary / Red Rum | Retained benefits: Raging and alcohol effects |
| Cabbage | Cabbage Juice | Greenward Kvass | Comfort |
| Onion | Onion Juice | Hearthroot Infusion | One harmful, curable effect removed on completion |
| Garlic | Garlic Juice | Whiteclove Tonic | Poison removal on completion |
| Potato | Potato Juice | Vodka | Retained Booze protection/impairment and alcohol effects |
| Wheat | Wheat Milk | Beer | Retained Booze protection/impairment and alcohol effects |
| Rice | Rice Milk | Rice Wine / Pale Jane | Retained alcohol effects; Pale Jane also gives Comfort |
| Pumpkin | Pumpkin Juice / Pumpkin Seed Milk | Harvestgold Brew | Absorption |
| Sugar cane | Cane Juice | Canefire Brew | Speed, with sugar consequences |
| Kelp | Kelp Juice | Tideward Infusion | Water Breathing |

Cooked or steeped vegetable preparations can coexist with raw juices. Plant milks should follow available ingredients, not invent almond, oat, soy or coconut stock just to fill a category. Multi-base alcoholic drinks such as Salty Folly and Saccharine Rum retain a place even without a single-plant row. Brief Nausea for unpleasant juice remained a possible drawback, not a settled effect.

#### Ordinary flower infusions

These effects belong to the flower as principal ingredient, not its interchangeable starter role. Dandelion and rose hips appear above. Overlap avoids making every decorative flower mandatory progression.

| Plant | Infusion | Effect direction |
| --- | --- | --- |
| Poppy | Redpetal Tea | Comfort |
| Blue orchid | Marshlight Infusion | Water Breathing |
| Allium | Violetguard Tea | Poison removal on completion |
| Azure bluet | Clearbrook Infusion | Brief Night Vision |
| Red tulip | Emberpetal Tea | Brief Strength |
| Orange tulip | Copperpetal Tea | Brief Haste |
| White tulip | Ivorypetal Tea | Brief Resistance |
| Pink tulip | Blushpetal Tea | Brief Regeneration |
| Oxeye daisy | Meadowrest Tea | Regeneration |
| Cornflower | Bluewind Infusion | Speed |
| Sunflower | Sunward Tea | Brief Fire Resistance |
| Lilac | Evening Lilac Tea | Comfort |
| Peony | Peony Heartcordial | Absorption |
| Flowering lily pad | Stillwater Infusion | Water Breathing |

#### Witch herbs and unusual botanicals

Fantasy preparations, not real-world safety advice. Unsafe extracts are not implicitly safe Hydration+ drinks; their risks and presentation remain explicit.

| Plant/base | Plain preparation where appropriate | Specialty | Effect direction |
| --- | --- | --- | --- |
| Yellow dock | — | Dockside Bitter | Harmful-effect cleansing on completion |
| Mugwort | — | Dreamwalker Tea | Night Vision with brief Nausea |
| Mandrake | — | Mandrake Fortifier | Resistance with brief Slowness |
| Belladonna | Belladonna Juice, unsafe | Nightshade Veil | Invisibility with Poison risk |
| Lily of the valley | — | Widow's Whisper | Invisibility with Weakness |
| Wither rose | — | Gravebloom Draught | Strength with brief Wither |
| Wither roots | — | Unresolved | Not a native Purulent Tea ingredient; later notes found no obtainable inventory stock |
| Nether wart and fermented spider eye | — | Purulent Tea | Retained benefit: extend a random existing milk-curable effect |
| Coffee berries and glow berries | Their respective juices | Gambler's Tea | Retained beneficial/harmful gamble |
| Warped fungus | — | Unresolved warped specialty | Not a native Gambler's Tea ingredient |
| Cursed sap | Sap, not juice | Cursed Sap | Retained benefits: Strength and Resistance |

#### Stronger preparations

| Base | Specialty | Effect direction |
| --- | --- | --- |
| Melon | Summerheart Reserve, secondary brew with nether wart | Retained stronger healing: 4 HP per full serving |
| Apple | Long / Strong Apple Cider | Retained duration/potency variants |
| Grimberry | Blackwake Reserve, secondary brew with crimson fungus | Stronger Haste with brief Weakness |

### Appearance

The preferred direction was to reuse suitable juice bottle artwork and retint liquids strongly while preserving bottle shape, glass, outlines and pixel detail—not replace every drink with a newly drawn universal bottle. Donor licensing/attribution still matters to that choice.

| Family | Color direction |
| --- | --- |
| Apple / pear / kiwi | Bright amber / chartreuse / saturated green |
| Orange / mango / persimmon | Vivid orange / deep golden orange / burnt orange |
| Lychee / peach / mangosteen | Rosy ivory / coral / violet |
| Hawberry / bayberry / fig | Scarlet / dark wine red / dusty magenta |
| Durian / pineapple / lemon | Acid yellow-green / strong gold / electric yellow |
| Melon / hamimelon | Watermelon pink / apricot orange |
| Fruits Delight blueberry / Aether blue berry | Indigo / electric blue |
| Cranberry / sweet berry / mulberry | Bright crimson / raspberry red / near-black purple |
| Glow berry / sourceberry / grimberry / chorus | Luminous-looking amber / arcane purple / deep teal / hot lavender |

“Luminous-looking” does not imply emissive rendering or an additional drink effect.

### Remaining design questions

- Complete ingredient coverage and aliases, including borderline produce, torchberries, magical plants and mixed drinks.
- Final names, serving sizes, nutrition/Nectar doses, hydration, quenching, drawbacks and intentional alcohol. A repeated specialist effect is preferable to a forced new mechanic.
- Cleansing, mana and blink behavior under fractional sipping: one completion action, not a full action per sip. Short effects need to remain useful at real drinking cadence.
- Safe passive handling of existing orange/lemon cleansing and other bespoke actions. Appearing in a matrix does not itself make a drink slot-compatible.
- How old specialist juices become distinct plain/brewed preparations without duplicate effect shortcuts. The later source direction chose a fresh-world catalogue rather than promising legacy identity compatibility; existing inventories and paid reserves were not to be bulk-rewritten.
- Reconcile direct-healing specialties with the proposed saturation economy if that separate idea is adopted. The matrix preserves the older healing intent; it does not decide that conflict.

## Loot: worthwhile, table-owned exploration rewards

**State:** The old notes described selected table-owned overrides as authored/in progress. Comprehensive physical-container and personal-loot behavior remained unresolved. The former global chest normalizer had already been removed.

Exploration rewards should be worthwhile and appropriately sized without flattening every location into the same supplies or manufacturing compensation for every empty roll.

- Tune explicit table/pool rolls, weights and quantities rather than score and reconstruct generated item stacks.
- Give chosen containers supply, encounter or treasure roles through deliberate authoring, not folder-name inference.
- Reusable supply pools are an opt-in, not an automatic response to a filtered reward.
- Preserve special pools, item data, stack behavior, intentional failure rewards and scarcity unless a particular balance decision changes them.
- Mob-carried rewards, packages and helper tables keep their distinct purposes. A chest-like loot context is not proof of a physical chest.
- Existing progression cuts and geological replacements remain independent. Balancing does not restore prohibited acquisition routes.
- A narrowly selected cap could address a demonstrated injection problem; it is not a reason to revive a blanket normalizer or filler generation.

Open choices are the initial problem tables, desired quantities, special rewards, treatment of intentional emptiness, shared-pool consumers and Lootr personal rolls. Existing rolled/prefilled inventories are not silently rewritten. No universal treasure-value score or bundle target was settled.

## Valkyrien Skies: physical motion and spacecraft

**State:** Wanted and deferred; this is a replacement ambition, not a completed physics model.

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

**State:** An isolated source prototype, not installed in the pack or a completed gameplay-ready experience. A private source checkpoint existed in `better-content/better-abyssal-city`.

An explorable layered underwater city would combine dangerous mystery fluid, sealing and pumping, player reclamation, finite local Gatherer/Warden ecology and persistent travel between cities.

The prototype had planning/generation, structural fixtures and experimental traversal tooling. Generation readiness, hydraulics, actors and a complete playable loop remained unfinished.

The desired navigability model is compositional: meaningful pieces and connections, assembled-world routes without accidental obstructions, and representative journeys. Completion means reclaiming and inhabiting/traversing a working city under its hydraulic and ecological pressures—not merely generating attractive pieces or steering through an exhaustive tour.

Open choices include how reclamation changes access and danger, finite actor/resource behavior, multiplayer persistence, and whether/how the standalone experience eventually joins the main pack.

## Reach-based melee recalibration

**State:** Previously chosen direction, not activated balance. The old catalogue described missing complete measurements and unresolved coupling to alternate attacks. This is a Tinkers' melee proposal, not a balance pass for every native Epic Fight or Weapons of Miracles weapon.

Use actual damage-weighted reach in ordinary grounded mainhand combos to select relative melee output, with Sword at 100 and a continuous 80–120 range. Shorter-reach chassis receive higher output; longer reach trades some output for safety. Slow swings keep their per-hit compensation through speed normalization, not a second speed-derived tier adjustment.

For strike reaches `r_i` and frozen pre-retuning damage weights `d_i`, effective reach is:

`R = sum(r_i × d_i) / sum(d_i)`

With Sword reach `S` and roster endpoints `Rmin`, `Rmax`:

- Below Sword: `100 + 20 × (S − R) / (S − Rmin)`.
- Above Sword: `100 − 20 × (R − S) / (Rmax − S)`.
- Equal reach gives equal tiers; all-equal reach gives 100 throughout.

The intended melee reference would return to the former same-material Sword scale, rather than the legacy 0.9 scale. Ranged budgets remain separate. Throws, charged Bonks and other coupled alternate attacks should not accidentally gain or lose their own established damage budgets; compensation must not alter projectile velocity merely to change damage. Native Pierce/Estoc armor interactions need their actual models rather than the older approximate coefficients. The Minotaur Axe sprint signature stays an explicit exception.

The unresolved design inputs are actual connecting reach across the roster, special-model integration and item-scoped alternate-damage separation. Weapon names, model size or an old tier are not measured reach. Dash, air, mount, skills, charges, casts, throws and optional dual variants are outside the proposed neutral-melee comparison.

## Fixed-location forage extensions

**State:** Source drafts existed for saguaro cactus, coconut and ancient trees; these were explicitly outside the established thirteen-species fruit-tree baseline.

Extend fixed-location food-resource treatment to `dynamictreesplus:saguaro_cactus`, `dtnatures_spirit:coconut` and `dtquark:ancient`, while preserving their native useful products. Placing a whole coconut for food processing is not the same as germinating a new tree. Saguaro-to-mega provenance should distinguish actual protected origins from unrelated mega cacti.

The intended growth references and the definition of an adult cactus were unresolved. This does not extend protection to ordinary cacti, cocoa, archwood pods or Aether trees by implication.

A separate unresolved yield choice was whether mature off-origin source-marked crops should occasionally return a second propagule. Older prose mentioned a ten-percent chance, while the recorded implementation returned exactly one. Neither historical sentence settles the desired rule here.

## Lineage endgame: entropy caching and Condemnation

**State:** Durable endgame concept. The old documentation identified no complete entropy economy, Condemnation system, authored curse roster or curse-specific assets. World succession and the Condenser were foundations, not this finished loop.

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

## Provenance

Recovered and consolidated from modpack commit `6004642691c0`, principally the former aspirational-features, lineage/endgame, weapon-balance, progression, food and content-system documents. The plant matrices preserve the old discussion names and benefit intentions. Development policy, approval workflows, test commands, build inventories, historical run reports and links to deleted prose are not restored. Only these two new design documents are introduced.
