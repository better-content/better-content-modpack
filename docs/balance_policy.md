# Balance policy register

This is the current human-readable register for pack departures from upstream behavior.
Acquisition channels include recipes, machines and rituals, loot, trades, worldgen and entity
drops, guides, and runtime hooks. Verification names the source or focused check to inspect,
not a claim that the generic runtime suite asserts that policy. The server suite verifies
readiness, snapshot completeness, one lifecycle/archive transition, logs, and candidate hashes;
it has no per-row recipe, registry-stat, consumer, or worldgen policy gate. Snapshot validation
checks schema, completeness, and coherence, not every gameplay claim. Observed gameplay and
visual acceptance require separately authorized, candidate-matched evidence.

| Domain | Upstream behavior | Pack behavior | Owner | Rationale | Channels | Verification |
|---|---|---|---|---|---|---|
| Technology eras | Parallel roots | Hand/TCon → Create Powered Works → Create precision/steam → PneumaticCraft + Heat Sync → PowerGrid + More Red → Creating Space | pack progression | Legible capability sequence | recipes, guides | recipe/config source inspection |
| Primitive workshop | Generic rustic utilities inherit crop and machine-mod defaults | Straw, rope, canvas, fired goods, and simple metal fittings supply pre-Font local handling, homesteading, clothing, lighting, construction, and transport; plates, redstone, alloys, casings, and scalable automation remain later | pack progression | Useful handmade play before dimensional industry | recipes, guides | recipe source inspection |
| Ore separation | Create Bulk Washing and source-specific output ladders | String-mesh hand Sifting → powered dry/waterlogged Sifting → Spout-prepared pressure assay; Blood, Hexerei, Ars, and Occultism converge on the same canonical resources with distinct costs | Realistic Ores / Create Sifting | One resource economy with early access and meaningful recovery depth | recipes, items, guides | generated processing-contract tests and recipe inspection |
| Create steam | Native low-cost engine | Brass Machine Block required | Create | Precision proof | recipe | recipe/config source inspection |
| Electrical generation | Dynamo, bee, weather, AE2, and Ars conversion peers | PowerGrid alone owns stationary electrical generation; OC2R remains a consumer/peer; Creating Space's rocket generator is SU and Clockwork's steam generator is gas-network machinery | PowerGrid | One electrical authority | recipes, loot, guides | recipe/config source inspection |
| Conventional tools | Many fixed tools | Ordinary fixed tools close; unique weapons and gear remain | Tinkers' Construct | Material-system coherence | recipes, loot, trades | crafting-policy selectors and source inspection |
| Mahogany | Hexerei and Nature's Spirit plus Dynamic Trees | Nature's Spirit owns the tree and canonical wood set; retained Hexerei variants consume Nature's Spirit mahogany; Dynamic Trees Hexerei is retired | Nature's Spirit | One material authority | recipes, worldgen, loot | crafting-policy selectors and source inspection |
| Hand fishing | Vanilla/TCon rods | Only Starcatcher rods remain | Starcatcher | One fishing system | recipes, loot | crafting-policy selectors and source inspection |
| Petroleum | Create Diesel extraction/distillation | PneumaticCraft splitting supplies retained fuel consumers; Create Diesel extraction, scanning, pumping, and crude-oil distillation close | PneumaticCraft | Finite process ownership | recipes, machines | recipe/config source inspection |
| Ratlantis | Token/portal alternatives | Dimension Font only; token loot and recipes removed | Dimension Font | One entry route | loot, recipes, world | recipe/config source inspection |
| Logistics | Ungated roots and hidden post-craft tax | Basic chutes, hoppers, pipes, and fluid handling remain local and pre-Font; visible Ratlantis components root the Rapid Hopper, Pretty Pipes, Sophisticated automation, Create requests, AE2, Little Logistics vehicles, and Rats; one Courier Lattice yields eight pipes and a four-lattice craft supports a 32-pipe starter network, Oratchalcum gates blank modules, and Arcane Logistics Cores gate all seven high modules | Ratlantis Logistics | Useful local handling before scalable networks, with visible mid/high upgrades | recipes, custom hook | recipe source + owning-mod local tests |
| AE2 | Independent start and generators | Energy Acceptor requires PowerGrid, Ratlantis, OC2R, meteor material, and Impossible Matter; AE generators close | AE2 with PowerGrid supply | Conjunctive late root | recipes | recipe/config source inspection |
| Occultism parenting | Independent/End-oriented | Hexerei cauldron preparations require Aether, Nether, Bumblezone, and Ratlantis trophies equally | Hexerei | Four-Font parent | cauldron, rituals, guides | recipe/config source inspection |
| Blood siblings | Mixed hierarchy | Blood Magic, Malum, and Goety are siblings under Blood; Ars is concurrent | Blood domain | Clear magic ownership | recipes, docs | config/recipe source inspection |
| Finite matter | Water/lava and familiar generators | Ars generation glyphs/rituals, Blood sigils and their sigil-only consumers, Drygmy, and Whirlisprig generation close; finite bucket transformations remain | matter policy | No inputless nonliving matter | recipes, rituals | recipe/config source inspection |
| Finite space | Occultism remote/infinite storage and mining | Storage controllers, remotes, stabilizers, satchel, wormholes, mineshaft, and miners close; dimensional matrix remains finite | space policy | Bounded storage | recipes, rituals, guides | recipe/config source inspection |
| Flight tiers | Creative hooks, top jet/omega, ritual flight | Red Hook creative flight is off; jet boots 4–5 and omega close; listed finite/local alternatives remain | mobility policy | Bounded mobility | config, recipes | config/recipe source inspection |
| Stored travel | Warp and recall families | Ars warp, Goety Recall/Call/End Walk, Recall Potion, and Spatial Sign close; local, combat, creature transport, and dragon horn remain | mobility policy | No stored-coordinate player travel | recipes, rituals, runtime | recipe/config source inspection |
| Fluids | PneumaticCraft may respect infinite sources | Exception off; hose-pulley safeguards remain; strict vanilla sources are deferred | PneumaticCraft | Avoid new fluid migration | config | config source inspection |
| Dragon ecology | Vanilla dragon boss products | Vanilla head, egg, and breath close; legitimate custom-serializer consumers use generic Ice and Fire tags | Ice and Fire | Closed-End replacement | worldgen, drops, recipes, loot | recipe/tag consumer inspection |
| Ice and Fire world | Common defaults | Complete Overworld ecology at least-frequent nonzero settings; wild griefing full, tamed griefing off | Ice and Fire | Rare but complete ecology | config, worldgen | config/spec and material-data inspection |
| Dragonsteel | Native fixed equipment | Native config sets base armor 8, toughness 3, base armor durability 2500, tool durability 2500, and sword base attack 9; actual armor durability is slot-derived; equal TCon materials use 1500, 8.0, 3.0, netherite tier | Tinkers' Construct | Controlled top material | config, recipes, TCon data | config/spec and material-data inspection |
| Closed End | Vanilla portal and city ecosystem | End is inaccessible; orbit biome is `minecraft:is_end`; compatible ecology and rituals move there; End City loot injections are disabled | Creating Space | Preserve content without End access | dimensions, tags, worldgen | dimension/tag/worldgen source inspection |
| End resources | End-only catches, bees, trims, and Elytra | Catches move to Ratlantis; bee lineage uses Overworld chorus; trims use rituals; Elytra uses mechanical Ice and Fire inputs | Ice and Fire / magic owners | Finite replacements | fishing, mutations, rituals, recipes | recipe/config source inspection |
| Guide onboarding | Complicated Bees and ParCool grant manuals on first tick | No automatic manuals; both existing survival recipes remain available through recipe discovery | pack content | Keep first-join inventory quiet without hiding optional documentation | advancements, guides, recipes | advancement and guide-recipe inspection |
| Wood ladders | Only named Quark plank families have matching ladder recipes | Dedicated families retain their own ladders; every other `#minecraft:planks` member crafts the oak ladder | Quark with pack compatibility | Make all supported planks useful without inventing new ladder blocks | tags, recipes | config/recipe source inspection |
| Source anchor | Chunk anchor not a SourceManager provider | One Source Anchor exposes 144,000 Source and can pay the 100,000 sink | Arcane Chunk Loaders | Make Impossible Matter payable | custom runtime | owning-mod Source Anchor GameTest |
| Known debt | Renewable vanilla fluid and stone loops | Explicit non-blocking debt; not claimed compliant | future finite-matter pass | Honest scope | policy | recorded |

Ice and Fire loads the authored settings from `config/iceandfire-common.toml` through
its registered Forge COMMON spec, not `defaultconfigs/iceandfire-server.toml`. Dragon
cave and roost chance denominators are both 10,000. These are configured target values;
activation and item stats need a new authorized runtime observation, not an older snapshot.

Elemental Dragonsteel modifiers are used only when an existing compatible modifier is present. The
tracked material definitions assign none rather than inventing modifiers. The most recent complete
candidate evidence also observed no compatible modifier, but it is historical evidence unless its
candidate hashes match the tracked pack.

Volatile recipe, loot, trade, and namespace totals do not belong in this living register. Consult
a complete `generated/runtime-dumps/snapshot.json` and its named files only after matching its
snapshot ID and server-run `candidate_selected` hashes to the target. Incomplete or unmatched
snapshots are not evidence of exact current counts.
