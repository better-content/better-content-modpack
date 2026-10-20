# Dimensional Font and underground ecology

The four enabled worldgen Fonts share a radial court, a ring of fully oxidized copper, a decorative field with candles and shallow pools, and a dimension-specific block palette. Bumblezone sites additionally form three dense wax and hive clusters. Tree biome sites use stripped logs from the local tree family for their four supports and roof ring. Treeless sites omit both. The structure is selected only in new worlds; existing sites are not migrated.

| Font | Salience aspects | Spirit map sellers | Field vocabulary | Renewable or dimension-linked materials | Pack systems that consume them |
| --- | --- | --- | --- | --- | --- |
| Nether | Impact, Endurance | Infernal, Wicked | Blackstone, basalt, brick, magma, red candles | Wart, blaze powder/rod, magma cream, basalt | Heat Sync Boiler Heater, PneumaticCraft thermopneumatic plant, TCon Scorched Brick, Nether Font Libation |
| Aether | Mobility, Renewal | Aerial, Sacred | Holystone, icestone, aerogel, pale sky stone | Blue berries, swet balls, skyroot leaves/logs, aerogel, blue aercloud | Rotational Compressor, Clockwork air compressor/propeller bearing/gas thruster, jet boots, Aether Font Libation |
| Bumblezone | Work, Tempo, Robustness, Renewal | Earthen, Tempo, Sacred | Beeswax, ancient wax bricks and eyes, brood honeycomb, yellow candles | Pollen puffs, honeycomb, honey, carvable wax | Create mechanical harvester/plough, Airtight Upgrade, Bumblezone Font Libation, existing nursery systems |

Map offers use the saved index of already discovered natural Fonts. Each seller's allowed destinations are the union of Fonts sharing an aspect with its spirit identity. Maps rotate within that set after sale. The index lookup neither loads nor generates chunks. Resident spirit villagers receive a map listing at level two; wandering spirit traders can add a map to their authored catalogues.

## Libation and Pourer

A Create basin mixes one bucket of water with three native ingredients into one bucket of the matching Font Libation. An overhead Font Pourer accepts fluid through Forge fluid capability or a bucket, but the Font itself does not accept pipes. Place the Pourer exactly two blocks above the Font with one air block between them. During an active run, a matching libation pours down visibly and restores 48 charge per millibucket at a bounded rate of one millibucket every 12 ticks. A bucket therefore holds 48,000 charge, about ten extra minutes at the current 80 charge per second drain. The nominal 15,000 charge before entry costs is about three minutes. The Pourer is idle when there is no active run, its fluid does not match the Font, or the air gap is obstructed. It never loads chunks.

## Flower relocation and cultivation

Decorative flowers may be picked up and replanted using their native substrate, water, orientation,
clearance and quantity rules. Relocation is not propagation. Outside
`the_bumblezone:the_bumblezone`, flower harvests yield no flower seeds, regardless of wild origin,
imported lineage or automation. Existing seed stacks remain usable as one-use planting resources
in any dimension; saves, flowers and lineage records are not migrated or deleted. Ordinary
flower-item relocation works from either hand. Better Compat Fixes exempts only the explicit
decorative-flower block classification from its otherwise unchanged offhand BlockItem restriction;
it does not force admission over another policy or bypass native support/quantity rules.

Inside Bumblezone, any transplanted flower qualifies for the existing instant farming mechanic:
one seed with a 50% chance of a second per recovered native flower unit. Rapid place/break farming
there is intentional, without a timer or lineage gate. Tall upper halves do not add another roll.
Natural generation and exploration acquisition are not removed.

The source owner is `better-bumblezone-crops`: its fixed cultivar catalogue, decorative-flower tag,
reasoned native-tag exclusions, native placement adapters and flower propagation/multiplication
mixins. Wild food crops, tree foliage and fallen tree litter are not decorative living flowers,
even when upstream uses a flower tag or base class. Better Compat Fixes owns Forge bonemeal
admission and the flower-only offhand exception against that same explicit decorative tag. Scoped hooks preserve nonflower grass/ritual
outputs and one-for-one flower conversions; they do not intercept arbitrary world placement.
Lotus stems cannot regenerate flower heads off-origin, and Sniffer digging cannot mint new flower
planting resources there. Their existing support/relocation behavior and unrelated loot remain.

The owning mod's `scripts/check-flower-contract.py` checks source resources and the active,
hash-pinned native archives; its README names the native GameTests and coverage boundaries.
These are source contracts, not evidence of deployed artifacts, applied optional-mod mixins or
complete runtime closure of every installed generator. Same-candidate native placement, aquatic,
quantity-bearing, multiblock and automation acceptance remains separately gated by
[testing authorization](testing.md). Changed hover copy must be accepted on both native personal
inventory paths and EMI under [inventory parity](inventory-parity.md); no source check substitutes
for those observations.

## Recipe audit

The new gates attach native, distinguishable resources to the first machine or tier that expresses each dimension's theme. Existing Font-material logistics roots, occult chalk, Aether gas thruster, and Bumblezone nursery routes remain in place. The industrial audit covered the hand workshop, precision factory, thermal/pressure, acid chemistry, electrical control, transport, aerospace, and transition component scripts; the magic and logistics audit covered occult cauldrons and Font-material logistics roots. The hand workshop remains available before dimensional expeditions. The three libations are optional fuel, while the machine ingredients are mandatory progression links.

The pack recipe source is `kubejs/server_scripts/progression/85_dimension_font_ecology.js` plus the focused recipe scripts referenced above. Runtime validation must check registry IDs against the installed Aether, Bumblezone, Rats, Create, PneumaticCraft, Heat Sync, and Clockwork JARs. The worldgen screenshot harness is a separate minimal-mod development runtime; it is not shipped in the pack.

## Underground geological populations

**Authored implementation; compilation, native qualification and full-pack acceptance are
pending separately authorized gates.** Source/configuration is not live gameplay evidence.
The owners are Better Cave Encounters (classification, ordinary tables, Director rosters and
finite lineages) and Better Compat Fixes (causal hooks and the narrow legacy-veto query).
No fauna, new event/quest domain, environmental audio or teaching work is included.

Ten definitions under `kubejs/data/better_cave_encounters/geological_ecologies/` use explicit
raw-rock tags under `tags/blocks/geology/`. The pinned Unearthed registry has one axis-valued
`unearthed:schist`, not separate `schist_x/y/z` block IDs: the mapping totals **16 raw blocks**.
The Director population is the ordinary column plus its additional column below.

| Habitat | Raw Unearthed rock | Ordinary population | Additional Director population |
| --- | --- | --- | --- |
| Ivory ossuary | White granite, quartzite | Baby Skeleton, Decrepit Skeleton, Bone Spider, Dread Thrall | Rattled, Forgotten, Skeleton Thrasher; cold-local Stray |
| Ochre tombs | Beige limestone | Rattled, Frayed, Koboleton, Draugr | Barrel Zombie, Door Knight, Haunted Armor, Aptrgangr |
| Karst nests | Limestone, grey limestone | Silverfish, Spider, Pinch Beetle, Slime Beetle | Cave Spider, Web Spider, water-local Drowned, Coralssus |
| Rubble burrows | Conglomerate | Silverfish, Web Spider, Corrodent, Kobold | Death Worm |
| Silt carrion | Mudstone, siltstone | Corpse Fly, Baby Spider, Crypt Slime | Bloody Gadfly, Zombie Bruiser, water-local Drowned |
| Red hunting grounds | Rhyolite, weathered rhyolite | Dread Hound, Baby Spider, Dread Beast | Zombie Lumberjack, Fallen Chaos Knight |
| Banded sentinels | Schist | Frayed, Decrepit Skeleton | Skeleton Thrasher, Zombie Clown, Door Knight, Skeleton Demoman, Elite Draugr, Royal Draugr, Cataclysm The Watcher, Prowler |
| Silver haunt | Phyllite | Goety Wraith, Restless Spirit, Border Wraith, Ice and Fire Ghost | Muck Wraith, Dark Vortex, Reaper, Seared Spirit, Ignited Revenant |
| Mineral rifts | Granodiorite | Cave Enderman, Endermite, Endermaptera | Vanilla Enderman, End Enderman, End Islands Enderman, Ender Slime, Ender Golem |
| Black stalking caves | Slate, gabbro, pillow basalt | Spider, Dread Hound, Corrodent, Dread Scuttler | Forgotten, Zombie Bruiser, Ice and Fire Troll |

This retains all forty non-Deep-Void species and adds twenty unique species. Cataclysm
`the_watcher` is not Alex's Caves `watcher`; Goety Wraith is not Quark Wraith. Skeleton
Demoman remains `born_in_chaos_v1:skeleton_demoman`. Deep Void's separate roster and
fixed-breach/dimension exceptions remain unchanged, including Kobolediator.

Current surrounding raw stone is authoritative, including player-placed stone. The sampler
reads 125 loaded-only positions at axis offsets `{-4,-2,0,2,4}` around each actual candidate.
Eight mapped blocks are required. Every represented habitat contributes proportionally;
there is no dominant-only/top-two reduction or encounter-wide roster lock. Crafted/cobbled/
brick/slab/stair/grassy/overgrown variants do not count. Weathered granite, marble, dolomite,
kimberlite and other unassigned rocks use fallback. Recognized geology with no eligible
entry yields no spawn. Cache lifetime is one game tick, not geological history.

The existing noise blend now means **timing and unmapped fallback**, not mapped inhabitants.
Cadence, warnings, pressure, injury relief, budgets and recovery remain under that existing
contract. Candidate-local depth gates, hidden/reachable approach checks, light, collision,
world border, Forge vetoes and denied actors remain. Species retries are limited to eight at
the already warned position; native finalization is followed by another physical admission.
Old profile IDs and `BlendPrimary`/`BlendSecondary` saves are retained; live mobs are not replaced.

Ordinary attempts use the native monster spawning loop and caps. Groups are one; existing
ordinary entries weigh eight, new Common entries three and new Line entries one. Minimum
depth matches Director admission. Silver Haunt has 25% final admission, Mineral Rifts 50%,
others 100%, interpolated by geology and drawn once per attempt. Cold means Minecraft's
local cold predicate; Drowned need water within Euclidean distance four. Rock color alone
means neither cold nor water. Imported habitat selection never waives independent vetoes,
difficulty/darkness, physical suitability, border, player distance or native population caps.
Substitution is scoped to vanilla selection/placement/position calls, not native finalization,
other categories or external spawners that also use `NATURAL`. Fresh reweighted entries may
satisfy habitat membership only if the type remains in the final post-event candidate list;
cancelled lists and removed-type vetoes remain effective. PositionCheck is never forced ALLOW.

Only the former Overworld NATURAL Y<=62 selected-mob In Control rule moved into
`better_compat_fixes:legacy_underground_ambient_denied`. Its exact twenty-eight optional IDs
remain denied without a positive local ordinary-eligibility answer from Cave Encounters.
Surface reserve/tagged-ground and vanilla zombie/skeleton denials remain independent;
Creeper/miniboss/animal/aquatic rules and the lava Magma Cube spawner are unchanged.

Existing threat policies remain canonical across habitats. New Common entries use depth .15,
weight 3, base cost 2, packet 2, encounter/player 6; Line uses .35/2/4/1/4; elite Line
(Haunted Armor, Elite Draugr, Royal Draugr) uses .55/1/6/1/2; Heavy (Fallen Chaos Knight,
Troll) uses .68/1/10/1/1. Heavies remain Director-only, one per packet and no simultaneous
live heavy within an encounter family, including split/merge/unload/reload. Entity-keyed
encounter counts are apportioned with budgets at locality splits and summed on rejoin, never
reset because players separate or surrounding rocks change.

Native rewards, equipment and terrain behavior remain under existing gamerules/protections
and global loot policy. Structure/dimension exports deliberately expand Ancient Metal,
Black Steel and other native reward access; no ecology-specific suppression compensates for
that choice. [Cataclysm placement/rewards](cataclysm_better_cave_encounters_integration.md)
retain all six heavy reward paths. Death Worm natural weight stays zero and its existing
Compat burrowable-terrain tag is unchanged. Thin Veil and Buried Encounters retain their
own triggers, rosters and domain-event credit; cave reuse is not another event reward.

Director roots prepay base cost plus a finite offspring allowance before native finalization.
Causal scopes identify the actual immediate producer; an unowned victim never inherits an
owned attacker's lineage. Slime size four reserves twenty descendants by the native four-way
recursive bound. Corpse policies preserve native Maggot chance/gamerules with finite audited
counts; Dread's commander-dependent conversion path has a four-admission authored allowance,
not a claim of four native lifetime kills. Descendants share prepaid credits and hard active/
per-tick/per-second caps; excess children are rejected, not queued indefinitely. Versioned
SavedData retains credits, members/corpses and family heavy identity across unload, retirement
and split/merge. Legacy marked parents are not killed; insufficient remaining budget is
reported as capacity-limited rather than supplying free credits. Natural/structure/summoned/
player-owned unmarked populations are not adopted by proximity.

The owner READMEs describe authored deterministic and retained 25-pin native fixtures.
Active-mod metadata binds the declared pack/pin inputs and requires the Director's supported
`verifyWorld` gate before future candidate staging. Those gates have not been run for this
change; the declaration is not execution or authorization. Same-artifact full-pack acceptance, actual protection/
loot-modifier combinations and restart behavior remain subject to [testing authorization](testing.md).
