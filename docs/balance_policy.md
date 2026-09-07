# Balance policy register

This is the current human-readable register for pack departures from upstream behavior.
Acquisition channels include recipes, machines and rituals, loot, trades, worldgen and entity
drops, quests and guides, and runtime hooks. Verification names the present source contract or the
relevant runtime gate; it is not a historical audit ledger.

| Domain | Upstream behavior | Pack behavior | Owner | Rationale | Channels | Verification |
|---|---|---|---|---|---|---|
| Technology eras | Parallel roots | Hand/TCon → Create Powered Works → Create precision/steam → PneumaticCraft + Heat Sync → PowerGrid + More Red → Creating Space | pack progression | Legible capability sequence | recipes, quests, guides | server runtime gate |
| Create steam | Native low-cost engine | Brass Machine Block required | Create | Precision proof | recipe | server runtime gate |
| Electrical generation | Dynamo, bee, weather, AE2, and Ars conversion peers | PowerGrid alone owns stationary electrical generation; OC2R remains a consumer/peer; Creating Space's rocket generator is SU and Clockwork's steam generator is gas-network machinery | PowerGrid | One electrical authority | recipes, loot, quests, guides | server runtime gate |
| Conventional tools | Many fixed tools | Ordinary fixed tools close; unique weapons and gear remain | Tinkers' Construct | Material-system coherence | recipes, loot, trades | crafting-policy contract |
| Mahogany | Hexerei and Nature's Spirit plus Dynamic Trees | Nature's Spirit owns the tree and canonical wood set; retained Hexerei variants consume Nature's Spirit mahogany; Dynamic Trees Hexerei is retired | Nature's Spirit | One material authority | recipes, worldgen, loot | crafting-policy contract |
| Hand fishing | Vanilla/TCon rods | Only Starcatcher rods remain | Starcatcher | One fishing system | recipes, loot | crafting-policy contract |
| Petroleum | Create Diesel extraction/distillation | PneumaticCraft splitting supplies retained fuel consumers; Create Diesel extraction, scanning, pumping, and crude-oil distillation close | PneumaticCraft | Finite process ownership | recipes, machines | server runtime gate |
| Ratlantis | Token/portal alternatives | Dimension Font only; token loot and recipes removed | Dimension Font | One entry route | loot, recipes, world | server runtime gate |
| Logistics | Ungated roots and hidden post-craft tax | Visible Ratlantis components root Pretty Pipes, Sophisticated automation, Create requests, AE2, Little Logistics, and Rats | Ratlantis Logistics | Player-visible costs | recipes, custom hook | custom test + server runtime gate |
| AE2 | Independent start and generators | Energy Acceptor requires PowerGrid, Ratlantis, OC2R, meteor material, and Impossible Matter; AE generators close | AE2 with PowerGrid supply | Conjunctive late root | recipes | server runtime gate |
| Occultism parenting | Independent/End-oriented | Hexerei cauldron preparations require Aether, Nether, Bumblezone, and Ratlantis trophies equally | Hexerei | Four-Font parent | cauldron, rituals, guides | server runtime gate |
| Blood siblings | Mixed hierarchy | Blood Magic, Malum, and Goety are siblings under Blood; Ars is concurrent | Blood domain | Clear magic ownership | recipes, docs | static + server runtime gate |
| Finite matter | Water/lava and familiar generators | Ars generation glyphs/rituals, Blood sigils and their sigil-only consumers, Drygmy, and Whirlisprig generation close; finite bucket transformations remain | matter policy | No inputless nonliving matter | recipes, rituals | server runtime gate |
| Finite space | Occultism remote/infinite storage and mining | Storage controllers, remotes, stabilizers, satchel, wormholes, mineshaft, and miners close; dimensional matrix remains finite | space policy | Bounded storage | recipes, rituals, guides | server runtime gate |
| Flight tiers | Creative hooks, top jet/omega, ritual flight | Red Hook creative flight is off; jet boots 4–5 and omega close; listed finite/local alternatives remain | mobility policy | Bounded mobility | config, recipes | static + server runtime gate |
| Stored travel | Warp and recall families | Ars warp, Goety Recall/Call/End Walk, Recall Potion, and Spatial Sign close; local, combat, creature transport, and dragon horn remain | mobility policy | No stored-coordinate player travel | recipes, rituals, runtime | server runtime gate |
| Fluids | PneumaticCraft may respect infinite sources | Exception off; hose-pulley safeguards remain; strict vanilla sources are deferred | PneumaticCraft | Avoid new fluid migration | config | static |
| Dragon ecology | Vanilla dragon boss products | Vanilla head, egg, and breath close; legitimate custom-serializer consumers use generic Ice and Fire tags | Ice and Fire | Closed-End replacement | worldgen, drops, recipes, loot | exact-consumer runtime gate |
| Ice and Fire world | Common defaults | Complete Overworld ecology at least-frequent nonzero settings; wild griefing full, tamed griefing off | Ice and Fire | Rare but complete ecology | config, worldgen | static + registry runtime gate |
| Dragonsteel | Native fixed equipment | Armor 8/3/2500; leaked native tools 2500/9; equal TCon materials at 1500, 8.0, 3.0, netherite tier | Tinkers' Construct | Controlled top material | config, recipes, TCon data | static + registry runtime gate |
| Closed End | Vanilla portal and city ecosystem | End is inaccessible; orbit biome is `minecraft:is_end`; compatible ecology and rituals move there; End City loot injections are disabled | Creating Space | Preserve content without End access | dimensions, tags, worldgen | static + worldgen runtime gate |
| End resources | End-only catches, bees, trims, and Elytra | Catches move to Ratlantis; bee lineage uses Overworld chorus; trims use rituals; Elytra uses mechanical Ice and Fire inputs | Ice and Fire / magic owners | Finite replacements | fishing, mutations, rituals, recipes | server runtime gate |
| Quests | Generated graph plus live SNBT | Secondary ledger with three independent ten-node milestone chapters and six optional completionist chapters | FTB Quests | Achievement record, not guidance | quests, criteria | layout review + runtime gate |
| Quest code | Broad retired criteria surface | Exact 12 live criteria and 31 live predicates; retired detector branches removed | Better Content Quests | No dead quest work | custom runtime | custom test + runtime gate |
| Guide onboarding | Complicated Bees and ParCool grant manuals on first tick | No automatic manuals; both existing survival recipes remain available through recipe discovery | pack content | Keep first-join inventory quiet without hiding optional documentation | advancements, guides, recipes | static + multiplayer join gate |
| Wood ladders | Only named Quark plank families have matching ladder recipes | Dedicated families retain their own ladders; every other `#minecraft:planks` member crafts the oak ladder | Quark with pack compatibility | Make all supported planks useful without inventing new ladder blocks | tags, recipes | static + server runtime gate |
| Source anchor | Chunk anchor not a SourceManager provider | One Source Anchor exposes 144,000 Source and can pay the 100,000 sink | Arcane Chunk Loaders | Make Impossible Matter payable | custom runtime | custom GameTest + server runtime gate |
| Known debt | Renewable vanilla fluid and stone loops | Explicit non-blocking debt; not claimed compliant | future finite-matter pass | Honest scope | policy | recorded |

Elemental Dragonsteel modifiers are used only when an existing compatible modifier is present. The
tracked material definitions assign none rather than inventing modifiers. The most recent complete
candidate evidence also observed no compatible modifier, but it is historical evidence unless its
candidate hashes match the tracked pack.

Volatile recipe, loot, trade, and namespace totals do not belong in this living register. Consult
a complete `generated/runtime-dumps/snapshot.json` and its named files only after matching its
snapshot ID and server-run `candidate_selected` hashes to the target. Incomplete or unmatched
snapshots are not evidence of exact current counts.
