# Progression

This pack uses six technology eras. Machine Blocks are one-time capability
proofs: only the explicitly named root machines consume them. Downstream
recipes return to each mod's native manufactured parts. Their proof recipes
produce batches of four Andesite, four Copper, four Brass, two Airtight, two
Electrical, and one Space Machine Block.

Progression is communicated by executable recipes, apparatus, native guides,
item-hover annotations, and event or world feedback. FTB Quests records a small
set of completed milestones but does not prescribe routes, expose recipe chains,
or gate discovery through quest dependencies.

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

## Era 1 — Hand Workshop and Tinkers' Construct

Build native Tinkers' Construct tools and reach the Nether, Aether, Bumblezone,
or Ratlantis Dimension Font. Each origin material makes its own named Font grout,
and all four grouts smelt to the same Seared Brick. Seared metallurgy owns alloy
composition: the automated path alloys molten stone with molten iron or zinc,
then casts molten Andesite Alloy. A Melter can instead consume one andesite with
10 mB of either molten metal to cast one native Create Andesite Alloy. The Hand
Crank is the only positive SU source before the Nether.

Font obelisks are powered only by internal charge. An unmodified Font stores
15,000 charge, costs 600 charge to start, regenerates 0.25 each inactive tick,
and has no join fee. An active run drains 80 charge plus 40 per participant each
second, giving a solo player 120 active seconds from a full unmodified Font.
Generated capacity and efficiency modifiers may extend or shorten that baseline.
Exhausting charge ends the run and returns its participants.

Bumblezone and Ratlantis are expedition-only: upstream portals, tokens, and hive
teleportation are disabled. Ordinary vegetable patches generate naturally in the
Overworld, but Bumblezone nurseries remain the origin of non-space food seed stock
and yield two to four propagules from mature plants. Outside an authored origin,
uprooting preserves one propagule with a ten-percent chance for a second; edible
produce is never itself the planting item. The Field Cook and Embark selections
therefore offer cabbage and tomatoes as food, not their seeds. The cultivar loot
modifier runs after the pack's other global loot modifiers so grass cannot leak
wheat or sage seed into the Overworld.

## Era 2 — Create Powered Works

Ratlantis is also the origin of scalable logistics. Courier lattices root bulk
rat, Pretty Pipes, and portable-storage infrastructure; oratchalcum mechanisms
root ordinary modules and Create's request network; arcane logistics cores root
high modules and advanced automation. AE2's Energy Acceptor requires PowerGrid
generation, Ratlantis logistics, OC2R computation, meteor material, and
Impossible Matter together. Vanilla chests and hoppers, early Create movement,
Eureka, Little Logistics docks, and Wares remain independent; usable Little
Logistics vehicles and its high-throughput Rapid Hopper require a visible
Ratlantis component.

Craft `kubejs:andesite_machine_block` from one Seared Brick block, four
Andesite Alloy, and four iron plates. It directly unlocks only the Millstone and
Mechanical Press. Press-made plates and a whisk unlock the Mixer without a
second Machine Block.

After reaching the Nether, compact the Andesite block, Copper Casing, four
copper plates, one Nether brick, and two Andesite Alloy into
`kubejs:copper_machine_block`. It directly roots water wheels, the Windmill
Bearing, the Mechanical Pump, and the first optional primitive transport roots.

## Era 3 — Create Precision and Steam

TCon alloying is the only brass composition route. Compact the Copper block,
Brass Casing, four brass plates, two electron tubes, and polished rose quartz
into `kubejs:brass_machine_block`. It directly unlocks the Deployer, Mechanical
Crafter, the first steam engine, and precision transport controls. Precision Mechanisms and native
components carry all later recipes. Quark's automatic Crafter is downstream of
the Create Mechanical Crafter rather than an independent iron-and-redstone bypass.

## Era 4 — PneumaticCraft and Heat Sync

Native Nether grout is disabled. Foundry access instead uses a heated Create
Mixer to combine rinsed Hotstone, rinsed Black Shale, and gravel into Scorched
Bricks, making the geological, kinetic, and TConstruct systems meet at one
visible boundary. Primitive compressed iron and dried-kelp/iron-plate pressure
seals keep the pressure entry reachable. Mechanically craft
`kubejs:airtight_machine_block` from the Brass block, four compressed iron
ingots, two pressure tubes, and two seals. It directly roots the Rotational
Compressor and the first Pressure Chamber interface.

Contained PneumaticCraft processes establish sulfuric and hydrochloric acid.
Heat Sync stores and moves process heat; its Boiler Heater consumes exactly
1/2/3 heat for an active Create boiler's 1/2/3 heat level.

## Era 5 — PowerGrid and More Red

Mechanically apply insulation/red-alloy wiring to Copper Casing for the first
PowerGrid conductive casing. Pressure-craft `kubejs:electrical_machine_block`
from Airtight proof, that casing, four copper plates, two primitive MoreRed
wires, and an electron tube. It directly roots PowerGrid's first stationary
generator housing and the circuit-design station. PowerGrid is the sole
stationary electrical-generation authority; PneumaticCraft remains the pressure
and petroleum authority. Iron-and-coal steel composite smelts into canonical
TCon steel for that machinery and later transition parts. Nitric acid and
gold/PGM-only mixed-acid recovery begin here. PowerGrid parts, sensors, motors,
and circuits carry later recipes.

## Era 6 — Creating Space

Pressure-assemble `kubejs:space_machine_block` from Electrical proof, two
rocket casings, two Inconel sheets, two Hastelloy ingots, and two titanium
thermal plates. Rocket casings retain their aluminum-and-cobalt composition but
craft in batches of four for the initial machine set. The Space Machine Block
directly roots the Rocket Engineer Table, Mechanical Electrolyzer, and Air
Liquefier. Native frames, tanks, engines, guidance, propellants, and life-support
parts carry the rest of the rocket graph.

## Optional transport

Eureka/VS, Clockwork, Trackwork, and Create trains are engineering branches,
not prerequisites for the ordinary factory spine. Primitive wooden ships begin
in the Hand Workshop; rough mechanical experiments begin in Powered Works;
precision trains and controls in Precision Factory; pressure-driven flight in
Thermal & Pressure; sensors and gyros in Electrical Control. Aether materials
gate stable high-performance flight, never primitive ships, basic Trackwork, or
low-performance aircraft. The Rail Beetle joins the Precision Factory branch by
combining a Railway Casing, a Barrel, a Minecart, and two Electron Tubes. Its
baseline coal firebox, 4-block/second drive, 64-rail survey, two-cart drawgear,
and one-wide/four-deep bridge work remain complete and practical. One engine
cradle accepts conditional Create steam, PowerGrid flux, Ars Source, Blood
lifeforce, Pneumatic air, Goety soul, or Malum spirit drives; an empty alternate
drive falls back to cargo fuel. Six module bays accept one module per family.
Tier-I machinery begins in Precision Factory, mechanical and civil Tier-II
modules mature in Thermal & Pressure, and efficiency, survey, and dispatch
Tier-II modules require Electrical Control. Every Tier-II recipe consumes its
Tier-I assembly.

## World compatibility

This is a clean break for new worlds. Old Machine Casing IDs have no aliases or
data migration. Back up an existing world before updating; remove old pack-owned
casings and finish in-flight recipes before attempting a manual conversion.
