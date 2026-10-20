# Progression

This pack uses six manufacturing eras and independent material-based capability
branches. A category does not inherit every other category's gate. Machine Blocks
are structural assemblies, not universal proof tokens: their chain produces four
Machine, four Toughened, four Precision, two Airtight, two Electrical and one Space
assembly. All six registry IDs and placed blocks remain stable; the first three
names change only in display. Materials may be shared or traded; there are no
player exploration flags.

## Category × era roots

| Category | Explicit installed roots and manufacturing/material boundary |
| --- | --- |
| Transport | `create:track_station`, `create:steam_engine`: precision mechanisms; `trackwork:simple_wheel`: pressed copper; `trackwork:track_level_controller`: brass casing; `vs_eureka:oak_ship_helm` and other wooden helms: ordinary workshop |
| Automation | `create:mechanical_press`, `create:mechanical_mixer`: iron workshop; `create:deployer`, `create:mechanical_crafter`: metallurgical brass casings; `quark:crafter`: consumes a Mechanical Crafter |
| Logistics | `prettypipes:pipe`, `prettypipes:blank_module`, `create:redstone_requester`, `sophisticatedstorage:hopper_upgrade`, `rats:rat_upgrade_basic`, `littlelogistics:rapid_hopper`: growable Certus; terminals/Pressurizer additionally use selected finite stone |
| Material refinement | `create:millstone`, `createsifter:sifter`, `createsifter:brass_sifter`, `create:crushing_wheel`: Nether Brick in each apparatus; hand String Mesh remains earlier |
| Power generation | `create:hand_crank`: iron; `create:water_wheel`, `create:large_water_wheel`, `create:windmill_bearing`: native Skyroot Logs; steam retains precision and heat; `powergrid:generator_housing`: 2-bar native electrical parts |
| Building blocks | `create:andesite_casing`: iron nuggets, logs and andesite; `create:copper_casing`: press plate and compacting; `createdeco:industrial_iron_block` and decorative products retain their material recipes, not shared dimensional proofs |
| Transmission/control | `create:shaft`, `create:cogwheel`, `create:large_cogwheel`, `create:mechanical_bearing`: simple iron/wood fittings; precision and electrical controls retain native manufactured parts |
| Fluid handling/containment | `create:mechanical_pump`: cogwheel/pipe; `pneumaticcraft:pressure_chamber_interface`, `compressedcreativity:rotational_compressor`, `pneumaticcraft:thermopneumatic_processing_plant`: mechanical assembly of sealed/native parts, not full blocks |
| Optional associated branches | `vs_clockwork:andesite_flap_bearing`: copper casing; `vs_clockwork:brass_propeller_bearing`: precision/Aether; `vs_clockwork:air_compressor`: pressure parts; `vs_clockwork:gyro`: PowerGrid motor. `better_rail_beetle:*` compact modules use native parts/seals/circuits and consume prior tiers, never full blocks |
| Aerospace | `creatingspace:rocket_engineer_table`, `creatingspace:mechanical_electrolyzer`: 4.5-bar assembly with native electrical parts; `creatingspace:air_liquefier`: one Space assembly per output |

Create Aquatic Ambitions, Deco and Diesel Generators retain exact upstream
material/fabrication recipes except existing explicit cuts. Aquatic processing
inherits its apparatus requirements, not a namespace-wide material replacement. Existing electron-tube/rose-quartz
Certus composition remains intact in precision control parts; this plan does not
rebalance their higher-era compositions.
Diesel stationary generation cuts remain in force: PowerGrid retains that authority.
Clockwork, Trackwork and Creating Space remain optional engineering branches.
No generic graph auditor, worldgen change, ore-yield change or magic rebalance is
part of this category contract.

Progression is communicated by executable recipes, apparatus, native guides,
item-hover annotations, loading lessons, contextual Threads, and event or world
feedback. Learning surfaces do not impose an objective graph or grant quest rewards.

## Prelude — Primitive Workshop

Before the first Dimension Font, crafting follows three material steps. Handworked
goods begin with wood and twigs, rock, flint, bone, hides, wool, straw, string,
Farmer's Delight rope, canvas, paper, and dyes. Hearth work adds charcoal or coal,
clay, brick, glass, and cooked stone. Simple copper and iron ingots, nuggets,
chains, and buckets then provide fittings. Redstone, plates, alloys, gears,
casings, and Machine Blocks are not primitive-workshop reagents.

Local handling is useful at this stage without becoming a logistics network.
Quark's wooden chute ejects items into the world, Farmer's Delight's basket
catches them, and Create's canvas-and-iron chute moves items vertically between
inventories without power. Vanilla and copper hoppers, the Little Logistics fluid
hopper, Quark pipes, and Supplementaries faucets and pulleys remain available as
bounded local transport. Rain collection, cooking, temperature preparation,
rustic storage and construction, boats, minecarts, ziplines, hang gliders, and
primitive Eureka ships use the same workshop palette.

Origin crops retain their native uses, but generic workshop goods do not depend
on them. Farmer's Delight straw and canvas therefore provide early alternatives
for sacks, awnings, doormats, feeding troughs, thatch, and wattle-and-daub, while
coal provides the ordinary fire-pit route. Explicitly bamboo-, flax-, food-, or
magic-themed content remains tied to its own material.

An Overworld cave start can gather loose rock or flint, twigs, and hanging roots
without an onboarding kit. Four hanging roots make one canvas, which binds a
primitive hand axe. Cave floors also hold occasional fallen oak wood and rare
azalea trees. These resources recur in newly generated caves; the chosen spawn
point does not receive a guaranteed cache.

## Era 1 — Hand Workshop and Tinkers' Construct

Ore separation starts here rather than at the furnace. Use a String Mesh by hand
on a geological chunk to recover one canonical primary unit. Heat remains a
four-nugget emergency exit for metal chunks, but nonmetals emerge directly from
separation and never require ceremonial smelting.

Native Tinkers' Construct toolmaking precedes Seared metallurgy. Combine two
**different** underground families from Gabbro, Slate, Phyllite, Quartzite and
Schist to make two Refractory Compound. Raw blocks and ordinary cobbled Slate/
Phyllite qualify; regolith, ores, mossy and decorative forms do not. Schist
orientations drop the same family item. Each compound smelts or blasts into one
Seared Brick. Ten unordered family-pair recipes accept either slot order and
reject same-family pairs. No broad stone tag or mined-location metadata is used.

Native surface-to-Slate/Quartzite, Slate-to-Phyllite, Phyllite-to-Schist and
Gabbro synthesis recipes are removed. Same-family cobble restoration remains.
Ordinary grout and the three Font grouts have no production/use routes; the
pack Font-grout IDs remain inert legacy registrations. Font travel no longer
unlocks starter metallurgy. The later Scorched/Foundry boundary is separate.

Seared metallurgy retains alloy composition authority: the automated path alloys
molten stone with molten iron or zinc and casts Andesite Alloy. A Melter can
instead consume andesite with 10 mB of either molten metal for one native alloy.
No primitive Andesite Alloy composition recipe is restored. The Hand Crank uses
iron fittings for the first Press/Mixer; water and wind power branch through Aether.

Font obelisks are powered only by internal charge. An unmodified Font stores
15,000 charge, costs 600 charge to start, regenerates 1.25 each inactive tick,
and refills from empty in ten in-game minutes. It has no join fee. An active run drains 80 charge plus 40 per participant each
second, giving a solo player 120 active seconds from a full unmodified Font.
Generated capacity and efficiency modifiers may extend or shorten that baseline.
Exhausting charge ends the run and returns its participants.

Harvesting a generated Font breaks its dimension binding. The replanted Font cannot
start an expedition, but it can support a World Condenser mounted two blocks above it.
Players configure the Condenser's successor choices there; an operator commits the
permanent reset on a supervised dedicated server.

Bumblezone is expedition-only: upstream portals, tokens, and hive
teleportation are disabled. Ordinary vegetable patches generate naturally in the
Overworld, but Bumblezone nurseries remain the origin of non-space food seed stock
and yield two to four propagules from mature plants. Outside an authored origin,
the current `CultivarLootModifier` returns exactly one propagule for a mature,
non-persistent-harvest, source-marked crop, and none for an unsourced crop; immature
or persistent-harvest crops return none off-origin. Decorative flowers relocate
freely but yield seeds only in Bumblezone, one or two per recovered flower unit.
Edible produce is never itself the planting
item. The previously documented ten-percent chance for a second
off-origin propagule conflicts with this implementation and remains an unresolved
design-intent decision, not an implemented yield. The Field Cook and Embark selections
therefore offer cabbage and tomatoes as food, not their seeds. The cultivar loot
modifier runs after the pack's other global loot modifiers so grass cannot leak
wheat or sage seed into the Overworld.

## Era 2 — Create Powered Works

The standard Sifter automates the same recipe language used by hand sifting.
Milling a chunk into two feeds improves primary recovery; Crushing Wheels later
produce three. Dry Sifting returns the primary product, while a waterlogged
Sifter can expose coproducts appropriate to its installed mesh.

Growable Certus roots scalable logistics: storage hopper automation, rat work
orders, the Rapid Hopper and Create's request network all consume it. Honey
crystal shards remain native themed parts, not sufficient routing gates. Pretty
Pipes develops the same certus-and-redstone routing intelligence before AE2 processors:
one certus crystal makes eight pipes, and certus supplies blank and high modules.
Items remain physical cargo in connected inventories, not matter-to-energy
storage. One finite Sky Stone block anchors an Item Terminal, with none added
for its Crafting Terminal upgrade; a Pressurizer costs one more. Its optional
acceleration runs on basic PneumaticCraft air, consuming 10 mL per item while
retaining 2 bar in a 1,000 mL chamber. Ordinary transport needs no air.
AE2's Energy Acceptor still requires PowerGrid generation, Aether
material, OC2R computation, meteor material, and Impossible Matter together. Vanilla chests and hoppers, early Create movement,
Eureka, Little Logistics docks, and Wares remain independent; Little
Logistics vehicles keep their upstream recipes, and the high-throughput Rapid
Hopper combines Certus routing with native honey material.

Shafts/cogs, casings, bearings, Hand Crank and the first Press close over wood,
stone and iron without plates, Seared Bricks or Andesite Alloy. Native Depot and
Basin alloy recipes are also replaced with iron fittings, closing both operating
support paths. The Press makes plates for the whisk/Mixer, propellers and copper
casing; sails use wool, wooden rods and iron nuggets. The first structural Machine Block
uses wood, stone and iron fittings. Compact it with Copper Casing, four copper
plates, one iron plate and two iron nuggets into Toughened assemblies: no Nether
or TCon alloy ingredient enters this shared structure.

Skyroot Logs directly build both sizes of Water Wheel and the Windmill Bearing;
their fittings need neither Nether nor TCon. Nether Brick directly builds the
Millstone, standard Sifter, Brass Sifter and mechanically crafted Crushing Wheels,
including their non-ore uses. The Pump uses a cogwheel and native fluid pipe.
Chutes, ordinary hoppers, belts and iron-fitted funnels/tunnels stay local handling,
not meteor-rooted requesting.

## Era 3 — Create Precision and Steam

Brass Sifters provide the high-throughput physical separation route and expose
all authored coproduct grades when waterlogged. A Spout is not a separator: it
uses water to prepare crushed geology for the later pressure route and performs
direct hydration, cleaning, stripping, and rusting operations formerly assigned
to Bulk Washing.

TCon alloying is the only brass composition route. Compact the Copper block,
Brass Casing, four brass plates, two electron tubes, and polished rose quartz
into `kubejs:brass_machine_block` (Precision Machine Block). This structural
assembly feeds Airtight manufacture. The Deployer and Mechanical Crafter use
Brass Casings; Steam Engine and Track Station use Precision Mechanisms. None
contains an intact Machine Block. Native components carry later recipes. Quark's automatic Crafter is downstream of
the Create Mechanical Crafter rather than an independent iron-and-redstone bypass.

## Era 4 — PneumaticCraft and Heat Sync

Four matching rinsed feeds at 2 bar yield four primary units plus the complete
deterministic geological assay. This is the maximum-recovery route, especially
for trace and precious coproducts.

Native Nether grout is disabled. Foundry access instead uses a heated Create
Mixer to combine rinsed Hotstone, rinsed Black Shale, and Basalt into Scorched
Bricks, making the geological, kinetic, and TConstruct systems meet at one
visible boundary. Primitive compressed iron and dried-kelp/iron-plate pressure
seals keep the pressure entry reachable. Mechanically craft
`kubejs:airtight_machine_block` from the Brass block, four compressed iron
ingots, two pressure tubes, and two seals. It supplies the structural chain into
Electrical manufacture. The open Rotational Compressor and Pressure Chamber
interface instead use mechanically assembled seals and native fittings. One
interface is produced per assembly. The native thermopneumatic plant likewise
uses Mechanical Crafting, with its existing Magma Cream requirement.

Sugar fermentation supplies renewable ethanol, and an open Create Basin oxidizes
it into canonical ChemLib acetic acid. Acetic acid preserves Pickled Pickles and,
with kelp-derived cellulose, makes reusable-capacity Acetate Membranes for hazardous
ChemLib pollutants. Two membranes, two pressure seals, two Bumblezone Carvable Wax,
and one compressed-iron ingot make an Airtight Upgrade. Use it on a Basin, Fluid Mixer,
or Thermopneumatic Processing Plant before processing gas-state ChemLib inputs or outputs;
sneak-use empty-handed to recover it.

The sealed pressure chain electrolyzes water, oxidizes Black Shale sulfur through
the vanadium contact process, and hydrates sulfur trioxide into sulfuric acid. The
full Black Shale pressure assay supplies the catalyst's vanadium. Sulfuric acid then
converts kelp-derived sodium chloride into hydrochloric acid and saltpeter into
nitric acid. Nitric acid is PneumaticCraft's sole PCB etchant; its native etching
acid is hidden and has no recipe. Registered phosphoric acid forms remain outside
the survival chemistry surface until they have a distinct supported purpose. Gold
concentrate with hydrochloric and nitric acid yields Platinum Group Residue in
Create mixing. These routes have no Electrical Machine Block or PowerGrid gate.
Heat Sync stores and moves process heat; its Boiler Heater consumes exactly
1/2/3 heat for an active Create boiler's 1/2/3 heat level.

## Era 5 — PowerGrid and More Red

Mechanically apply insulation/red-alloy wiring to Copper Casing for the first
PowerGrid conductive casing. Pressure-craft `kubejs:electrical_machine_block`
from Airtight proof, that casing, four copper plates, two primitive MoreRed
wires, and an electron tube. It supplies the structural chain into Space
manufacture. The open generator frame is independently pressure-assembled at
2 bar from plates, conductive casings, wiring and an electron tube; the small
Soldering Table consumes a pressure-made PCB rather than an intact block. PowerGrid is the sole
stationary electrical-generation authority; PneumaticCraft remains the pressure
and petroleum authority. Iron-and-coal steel composite smelts into canonical
TCon steel for that machinery and later transition parts. PowerGrid parts, sensors, motors,
and circuits carry later recipes.

## Era 6 — Creating Space

Pressure-assemble `kubejs:space_machine_block` from Electrical proof, two
rocket casings, two Inconel sheets, two Hastelloy ingots, and two titanium
thermal plates. Rocket casings retain their aluminum-and-cobalt composition but
craft in batches of four for the initial machine set. The Space Machine Block
directly fits the Air Liquefier: one intact block per output. The undersized
Rocket Engineer Table and exposed Mechanical Electrolyzer instead pressure-assemble
native frames, thermal/precision parts and an integrated circuit at 4.5 bar. Native frames, tanks, engines, guidance, propellants, and life-support
parts carry the rest of the rocket graph.

### Structural resource basis

The resource contracts bind native inspection to the committed Create 6.0.8
SHA1 `b13d912b9247a38d66d11598c121442585a1c1e9`, Unearthed SHA1
`5cf92a5a4fef9166501b43609b38d24f56c76dba`, and Creating Space 1.7.13 SHA1
`7b341b9c32fec9fcf020ae6d8429c59917415f7b`. Deployer housing is 12 deep;
Press housing is 14 high, Pump 12 wide, Steam Engine 15 high and Track Station
13 high. Moving hands, shafts and press heads do not supply a missing full body.
The Millstone's hollow body and 12-high moving insert are reviewed separately,
not justified by its aggregate 16-cube bounds. The rocket table is 13.5 high and
12 deep; the live Electrolyzer housing is 14 high despite its extended head.
The Liquefier has a complete cubic placed body with separate internal propeller;
it alone retains a full apparatus assembly. Open pressure frames and the
PowerGrid rotor housing use native sealed/electrical parts instead.

Local-archive resource tests verify SHA1 before reading resources and explicitly
skip when the exact pinned archive is unavailable; they do not download, build,
or claim visual/gameplay acceptance. The bounded real-handler fixture covers
154 authored recipes, all family-pair/form combinations, branch prerequisites
and exact structural consumer counts. Full Kotlin/Dev and fresh-world runtime
acceptance remain separate gates.

## Optional transport

Eureka/VS, Clockwork, Trackwork, and Create trains are engineering branches,
not prerequisites for the ordinary factory spine. Primitive wooden ships begin
in the Hand Workshop; rough mechanical experiments begin in Powered Works;
precision trains and controls in Precision Factory; pressure-driven flight in
Thermal & Pressure; sensors and gyros in Electrical Control. Aether materials
gate stable high-performance flight, never primitive ships, basic Trackwork, or
low-performance aircraft. The Rail Beetle joins the Precision Factory branch by
combining a Railway Casing, a Barrel, a Minecart, and two Electron Tubes. Its
found example can also appear in an abandoned mineshaft or village toolsmith chest;
placing that marked item supplies 32 rails, 16 cobblestone supports, and 8 coal without
advanced modules. The Rail Beetle source also owns cave-siding discoveries: a parked
Beetle coupled to a passenger cart and reserve chest cart, with 64 rails, 64 cobblestone
supports and 16 coal in each of the working and reserve inventories. These generate
only at suitable dry Overworld cave walls with Create available. The post-carving
feature searches selected chunks for a natural cave mouth, then excavates a ruined
rail-work camp into the adjoining rock. The Beetle's nose faces outward into the
open cave; the coupled carts sit deeper in the alcove. Broken timbers, a workbench,
an extinguished hearth and rubble mark the site, without an extra loot chest.
The site supplies its own parking space instead of requiring a naturally clear
13-block corridor; fluid, surface, ore and existing-structure conflicts veto
excavation. New discoveries are not `/locate structure` targets; existing saved
sites remain readable. Opening the
Beetle wakes its survey; unclaimed examples do not consume their supplies. Freight
reserves must be transferred manually, and taking the vehicles never restocks the site.
This source addition requires a newly deployed mod artifact and newly generated chunks;
it is not a claim about the currently bundled JAR. The
baseline coal firebox, 4-block/second drive, 64-rail survey, two-cart drawgear,
and one-wide/four-deep bridge work remain complete and practical. One engine
cradle accepts conditional Create steam, PowerGrid flux, Ars Source, Blood
lifeforce, Pneumatic air, Goety soul, or Malum spirit drives; an empty alternate
drive falls back to fuel in the Beetle's working inventory. Freight belongs in the
consist. Six module bays accept one module per family.
Tier-I machinery begins in Precision Factory, mechanical and civil Tier-II
modules mature in Thermal & Pressure, and efficiency, survey, and dispatch
Tier-II modules require Electrical Control. Every Tier-II recipe consumes its
Tier-I assembly. There is no compact-module exception to structural fit: the
six mechanical/civil upgrades mechanically assemble two pressure seals with the
prior module and native parts; the three electrical upgrades use integrated
circuits. PowerGrid flux and pneumatic engine modules use native casings/cylinders,
not Machine Blocks.

## World compatibility

New worlds are the acceptance baseline. This change preserves all six Machine
Block registry IDs and placed blocks; it changes display names and future
manufacturing recipes only. No migration, confiscation, retroactive conversion,
worldgen backfill or saved-machine removal is introduced. Source/model inspection
is not effective-loaded-recipe or gameplay acceptance; those require a separately
authorized exact-candidate runtime pass.
