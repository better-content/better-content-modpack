# Progression, acquisition and balance

## Progression

The five technology eras describe an enduring workshop with increasingly capable
power infrastructure, not five sets of replacement processing machines. Create
remains the processing apparatus for its established domains: the same Press or
Mixer can keep working as its supply changes from player effort to generated
rotation, steam, compressed air, or electricity. Specialist capabilities such as
Tinkers' metallurgy, pressure processing, and electronics retain their own apparatus.

There are three interacting progression axes:

- **Power infrastructure:** how energy is generated, buffered, distributed, and
  converted into rotation at the machine.
- **Fitted components:** native Tinkers' Construct parts give selected machines
  and conversion hardware their Better Manufacturing properties.
- **Capability access:** recipes and apparatus still determine which processes
  are available. A stronger power source or better material does not bypass them.

These are cumulative options. Water and wind remain useful after steam, pressure,
and electricity become available. This framing does **not** move recipes earlier
or mean that most machines already unlock in Hand Workshop. Machine Blocks prove
capability at explicitly named roots, rather than serving as mandatory ingredients
in every machine. The retained Andesite, Copper, Brass, Airtight, and Electrical
block recipes produce batches of four, four, four, two, and two. Creating Space
and its Space Machine Block are retired from this candidate.

Progression is communicated by executable recipes, apparatus, native guides,
item-hover annotations, loading lessons, contextual Threads, and event or world
feedback. Learning surfaces do not impose an objective graph or grant quest rewards.

### Power progression at a glance

| Era | Supply to the kinetic workshop | What changes |
| --- | --- | --- |
| 1 — Hand Workshop | Player effort → Hand Crank → rotation | Hands-on operation of the early workshop; usable load and RPM still matter. |
| 2 — Powered Workshop | Water/wind → rotation | Continuous generation without continuous player operation; transmission layout becomes important. |
| 3 — Steam Power | Heat and water → boiler/Steam Engine → rotation | Fuel, water supply, boiler capacity, and heat management support a larger workshop. |
| 4 — Compressed-Air Power | Compressor → pressure storage/tubes → Compressed Air Engine → rotation | Store and distribute compressed air, then drive machinery near the point of use. |
| 5 — Electrical Power | Generation/storage → electrical circuit → motor → rotation | Batteries, circuit supply, wiring, and motor load shape distributed machine operation. |

Air engines and electric motors are **conversion bridges**, not replacements for
Create processing machines. Their behavior comes from the upstream energy system:
PneumaticCraft/Compressed Creativity pressure, volume, operating thresholds and air
consumption; PowerGrid battery behavior, voltage/current, circuit load and thermal
limits. Storage decouples generation from use only within those native limits.
Neither chain is a lossless or universal "SU battery". Likewise, boiler heat and
Heat Sync heat storage are not a tank of stored SU or a new steam-fluid network.

### Fitted parts: what the workshop is made from

Better Manufacturing's candidate recipes consume native TCon parts and calculate
machine-specific settings at crafting time. A configured Whisk carries its rating
into a Mixer; a configured Propeller carries its rating into a Fan. The resulting
settings persist with supported machines, independently of the chosen power source.
Material reloads affect future crafts, not existing machines. This is not a universal
in-place part-swapping, wear, salvage, or retained-part-inventory system.

| Machine or component | Consumed part | Authored rating role |
| --- | --- | --- |
| Mechanical Press / Millstone | Large Plate | Working surface / work rate |
| Mechanical Drill / Saw | Pick Head / Small Blade | Cutting work and supported harvest tier |
| Mechanical Mixer | Whisk made with a Tool Handle | Inherited mixing work rate |
| Encased Fan | Propeller made with four Small Blades | Inherited airflow |
| Mechanical Pump | Tool Handle | Pump pressure |
| Crushing Wheels | Hammer Head + Large Plate | Work rate and kinetic load |
| Mechanical Plough / Roller | Small Axe Head / Large Plate | Debris breaking / excavation work, not invented crop-growth bonuses |
| Basin / Fluid Tank | Large Plate liner | Fluid capacity |
| Fluid Pipe / Valve / Item Drain / Spout | Large Plate liner | Fluid rate |
| Hose Pulley / Portable Fluid Interface | Large Plate fitting | Fluid transfer rate |
| Weighted Ejector | Bow Limb | Recharge/launch rating used in charging and stress accounting; native trajectory remains |
| Steam Engine | Tough Handle piston rod | Conversion and mechanical capacity limit |
| Rotational Compressor / Compressed Air Engine | Large Plate + Tough Handle; engine also consumes a configured rotor | Conversion rating within native pressure/output rules |
| PowerGrid Electric Motor | Tough Handle rotor | Drive rating applied through native circuit load |

**A part ingredient does not always imply a performance rating.** Shafts,
cogwheels, the Deployer, Mechanical Crafter and other construction-only recipes
consume parts without acquiring material-dependent speed or efficiency. Their
native behavior remains. Higher-rated conversion hardware also does not imply free
energy: compressor output pays matching kinetic stress, and the air engine retains
its native RPM ceiling and running air accounting.

These are source-authored candidate recipes/hooks, not a full-pack gameplay
acceptance claim. Better Manufacturing remains in progress; see
[implementation status and source references](implementation.md#manufacturing-ownership-and-implementation-status).

### Delivering power costs capacity

Better Create Kinetic Loss adds component- and RPM-dependent transmission demand
to the native network's stress total. Shafts, gears, belts and chains consume part
of the available capacity; this is not gradual RPM decay along a shaft. Its rates
come from [pack configuration](../config/better_create_kinetic_loss-common.toml),
not the material of a handle used to craft a shaft.

A central shaft-driven workshop and local air/electric drives therefore present
different layout choices. Converting energy near a machine can shorten kinetic
transmission, but replaces that distance with pressure or electrical distribution
and its own costs and limits. It is not automatically more efficient. After
conversion, the same Create stress/RPM rules, transmission demand, fitted working
parts and processing recipes still govern useful work.

### Capability gates remain distinct

| Capability | Current authored access, independent of the power-era framing |
| --- | --- |
| Basic hand-powered Create workshop | Iron workshop transmission, first Press and Hand Crank; Mixer consumes its fitted Whisk. |
| Renewable water/wind power | Skyroot-log acquisition; this is not a shared gate on all workshop utilities. |
| Milling, Sifting and Crushing Wheels | Their Nether-brick roots remain; powered separation is not moved into the primitive workshop. |
| Precision automation and steam | Brass composition through TCon; Brass Casing for Deployer/Crafter, Precision Mechanism for Steam Engine. |
| Pressure processes | Compressed iron, seals and pressure apparatus; recipe-specific pressure and other inputs still apply. |
| Electrical generation/control | Conductive casing, pressure-made generator fittings and the authored electrical roots; PCB starts the circuit-design station. |

The detailed sections below retain acquisition and processing facts. Older script
and recipe labels map to these headings: `powered_works` = Powered Workshop,
`precision_factory` = Steam Power and precision, `thermal_pressure` = Compressed-Air
Power and process heat, `electrical_control` = Electrical Power. These labels and
item/recipe IDs are unchanged.

### Create and addon acquisition audit — source-only, not a retier

This family-level inventory compares the pinned Create 6.0.8 and addon recipe
artifacts with pack datapack overrides and KubeJS removals/replacements. It is
**not** a loaded full-pack recipe graph or proof of survival acquisition. In
particular, a file's `hand_workshop`, `powered_works`, or `precision_factory`
location does not impose an unlock: the actual ingredients and alternate outputs
do. `source-authored` below means a recipe or policy edge exists in the inspected
sources; `unverified` means its effective current-pack route or runtime behavior
has not been established by candidate-matched loaded evidence. No family here is
claimed `loaded-verified`. Earlier isolated Create/Sifter checks in
[implementation status](implementation.md#manufacturing-ownership-and-implementation-status)
do not establish the whole current roster. Both conditional Better Manufacturing
and no-Manufacturing recipe branches, native alternatives and later KubeJS removal
passes must be checked before a release claim.

The [Manufacturing selection ledger](../kubejs/data/better_manufacturing/coverage.json)
lists 93 Create IDs and 73 Sifting/Diesel/Compressed Creativity/Clockwork/Trackwork
IDs, including six explicitly excluded entries. It inventories part coverage,
**not** 166 progression gates, live machines or accepted routes. The separately
pinned Horse Power and Aquatic Ambitions need their own checks; Create Deco's
mostly decorative variants, color/material blocks, technical controller blocks
and creative-only entries do not each need an era. Functional Create items not
selected for Manufacturing (such as the Blaze Burner, Redstone Link, depot,
display controls and track) still belong in the audit. Counts reflect the
inspected source selection, not an active-world registry census.

For precedence, the KubeJS scripts remove exact native IDs for Hand Crank,
Millstone, Press, Mixer, Deployer, Crafter, Sifter and Crushing Wheels before
adding distinct `kubejs:` recipe IDs for those outputs; wheel/wind, Harvester/Plough, Steam Engine,
Requester and casings are removed **by output**. Many other Create roots use
same-ID [`forge:conditional` datapack recipes](../kubejs/data/create/recipes/)
with a part-consuming Manufacturing branch and an ordinary fallback; the
Sifter/Millstone/Crushing Wheels KubeJS branches also cover both modes.
[`removed_items.js`](../kubejs/server_scripts/policy/removed_items.js),
[`crafting_policy.json`](../kubejs/config/crafting_policy.json) and
[`ore_sifting_and_spouting.js`](../kubejs/server_scripts/processing/ore_sifting_and_spouting.js)
can close outputs or processing paths independently of an apparatus recipe.
Neither these source removals nor native recipe IDs alone prove that no other
recipe produces the same output in the loaded pack.

| Functional family | Current source route and alternative | Capability; provisional placement and impact | Evidence |
| --- | --- | --- | --- |
| Hand workshop, gearing and local transfer | [`10_hand_workshop.js`](../kubejs/server_scripts/progression/10_hand_workshop.js) authors a Hand Crank; [`20_powered_works.js`](../kubejs/server_scripts/progression/20_powered_works.js) also supplies iron-based shaft/cogwheel alternatives without an Andesite Alloy prerequisite; primitive chute and depot have separate roots. Parts in shafts/gears are construction costs, not material-dependent loss ratings. | Keep basic local work and transmission early. A source-script name does not lock the output to its named era. | source-authored; full-pack unverified |
| Ordinary Create transformation | The same `20_powered_works.js` authors the first iron Press, Whisk/Mixer and Basin; [conditional Create recipe overrides](../kubejs/data/create/recipes/) offer Saw, Drill, Fan, Pump, Spout and Item Drain with ordinary iron/copper/andesite and, with Manufacturing, native TCon parts. No Nether/Skyroot ingredient is intrinsic to the Mixer recipe. | Keep ordinary pressing, mixing, cutting, airflow and local fluids as a common workshop; machine rating and available SU/RPM govern performance, not invented new processing tiers. | source-authored; full-pack unverified |
| Early versus enhanced ore work | Hand String Mesh yields one primary unit. `20_powered_works.js` currently puts Nether brick on Millstone, powered Sifter and mechanical-crafted Crushing Wheels. The Millstone gives **two feeds per chunk**, Wheels three; wet zinc/brass meshes and later pressure assay expose different coproduct depth. Advanced Brass Mesh output is removed in [`ore_sifting_and_spouting.js`](../kubejs/server_scripts/processing/ore_sifting_and_spouting.js). | **Deferred proposal only:** let an early Millstone make two ore feeds without Nether brick. Retain Nether on Sifter and Wheels, brass mesh grades and pressure assay. This changes recovery, not merely the machine's name or food-grinding access. | source-authored; full-pack unverified |
| First continuous SU and animal work | `20_powered_works.js` removes native wheel/windmill outputs and rebuilds all three with Skyroot logs, including conditional Manufacturing costs. The pinned Create Horse Power JAR's native Horse Crank uses an oak fence, cogwheel and stone; no pack recipe replacement was found. | **Deferred proposal only:** local obtainable wood for the basic/large water wheels and windmill, preserving existing material/part bills and optional Skyroot compatibility. Horse Crank is an unverified possible earlier sustained-power alternative; audit output/upkeep rather than call it a bypass. | wheel/wind source-authored; horse behavior unverified |
| Steam, diesel and process heat | [`30_precision_factory.js`](../kubejs/server_scripts/progression/30_precision_factory.js) roots the Steam Engine in a Precision Mechanism and optional TCon piston rod. The native Blaze Burner still needs its distinct Blaze fuel/heat route; native Diesel bulk fermentation and brass/fuel engines are separate. [`crafting_policy.json`](../kubejs/config/crafting_policy.json) and [`removed_items.js`](../kubejs/server_scripts/policy/removed_items.js) close Diesel crude extraction/distillation and inputless lava fermentation. Heat Sync heats Create boilers. | Keep later fuel/boiler management and retained finite-fuel engines distinct; inspect actual alternative fuels before treating Diesel as a steam or pressure shortcut. Neither heat nor fuel is stored SU. | source-authored; full-pack unverified |
| Pressure and electrical conversion | [`40_thermal_pressure.js`](../kubejs/server_scripts/progression/40_thermal_pressure.js) roots the Rotational Compressor in pressure parts; Compressed Creativity's configured Air Engine converts pressure back to SU. [`50_electrical_control.js`](../kubejs/server_scripts/progression/50_electrical_control.js) roots PowerGrid generation in pressure-made wiring/casings; its motor converts circuit power to SU. | Keep air/electrical buffering and specialist chemistry/electronics later; local drives still pay kinetic transmission stress. Compressed Creativity's Heater remains unresolved for survival acquisition, not a proven new power tier. | source-authored; full-pack and Heater acquisition unverified |
| Precision crafting and moving-item automation | `30_precision_factory.js` replaces Deployer and Mechanical Crafter recipes with Brass Casing/electron-tube roots; conditional [Mechanical Arm](../kubejs/data/create/recipes/crafting/kinetics/mechanical_arm.json) and speed-control recipes use Brass/Precision Mechanism. | Retain later autonomous action, assembly and precision control despite their use in crafting. Early hand Press/Mixer must not imply an early Deployer/Crafter. | source-authored; full-pack unverified |
| Instruments, redstone and displays | Native Create Speedometer/Stressometer, Redstone Link, threshold switches and display parts use their own material recipes; some smart controls also consume Brass Casing or Precision Mechanism. No single Manufacturing selection covers all these controls. | Keep measurement and local logic distinct from autonomous routing, precision crafting and stored power; check control recipes and alternate signals before treating the whole family as a later machine gate. | source-authored; full-pack unverified |
| Local versus intelligent logistics | Primitive chute, early funnels/tunnels, belts and vaults are local transfer. [Stock Link](../kubejs/data/create/recipes/crafting/logistics/stock_link.json) uses vault plus native copper/redstone Transmitter; packaging and stock displays have separate ordinary recipes. [`20_meteor_logistics.js`](../kubejs/server_scripts/progression/20_meteor_logistics.js) gives the Redstone Requester finite meteor Certus rather than requiring an ME network. | Keep basic transport distinct from requests/routing. Audit early Stock Link/packaging behavior and alternative requester outputs; do not equate a cheap observation link with unrestricted inventory networking. | source-authored; full-pack unverified |
| Stationary versus moving world work | A stationary Drill/Saw can have early workshop ingredients. [Bearings, pistons and gantries](../kubejs/data/create/recipes/crafting/kinetics/) have comparatively ordinary casing/shaft recipes, whereas Roller inherits Crushing Wheels. [`85_dimension_font_ecology.js`](../kubejs/server_scripts/progression/85_dimension_font_ecology.js) ties Harvester/Plough to Bumblezone materials. | Keep basic heads useful; review assembled mobile quarry/farm reach before changing any bearing or motion recipe. Existing Bumblezone and wheel gates stay intact; source cheapness alone is not evidence of an exploit. | source-authored; full-pack/mobile impact unverified |
| Schematics and construction at scale | [Schematicannon](../kubejs/data/create/recipes/crafting/schematics/schematicannon.json) uses iron blocks, logs and dispenser (plus a plate with Manufacturing); Schematic Table has a simpler native/conditional route. | Potentially powerful bulk building but still consumes actual material. Audit in-world reach and legitimate construction benefit before choosing a deeper gate; do not reclassify stored schematics as free inventory. | source-authored; full-pack/impact unverified |
| Bulk fluids and item/contraption interfaces | [Hose Pulley](../kubejs/data/create/recipes/crafting/kinetics/hose_pulley.json) uses copper casing, dried kelp and copper (plus fitted plate conditionally); portable interfaces and tanks have separate recipes. [`create-server.toml`](../defaultconfigs/create-server.toml) sets `bottomlessFluidMode = "DENY_ALL"`. | Ordinary pumping/spouting stays useful early. Audit range, transport and world effects of Hose Pulley before retiering; the infinite-source safeguard does not by itself settle those impacts. | source-authored; full-pack/impact unverified |
| Rail and optional vehicles | `30_precision_factory.js` roots Train Station in Precision Mechanism/Railway Casing; native track assembly uses Deployer and Press. [`10_optional_engineering_roots.js`](../kubejs/server_scripts/transport/10_optional_engineering_roots.js) separates rough Clockwork/Trackwork from brass precision, pressure and electrical controls; the Rail Beetle has its own staged modules. | Preserve usable primitive ships/basic wheels and later precision trains, stable high-performance flight and powered vehicle modules. Clockwork Copter Bearing is registered but lacks confirmed survival acquisition. | source-authored; full-pack/Copter acquisition unverified |
| Marine processing and other addon recipes | [`22_underwater_works.js`](../kubejs/server_scripts/progression/22_underwater_works.js) removes selected Aquatic Ambitions geology/heart-of-sea recipes and makes the Mechanical Conduit from a native Conduit, prismarine parts, fluid pipe and Blaze Rod. Create Sifting's graded meshes remain distinct from the ordinary workshop. Create Deco contributes construction finishes rather than a machine era. | Keep marine channeling, controlled material sources and themed processing distinct. Do not infer Certus is consumed by the conduit from an outdated source comment; verify any other surviving output route before claiming closure. | source-authored; full-pack unverified |

This audit makes **no** Schematicannon, Hose Pulley, mobile-contraption, stock
logistics or Horse Crank gate decision. It does not turn source-only candidates
into friends' playtest observations. The bounded future retier and reasons for
its deferral live in [Aspirational features](design.md#create-power-and-capability-retier).

### Life attributes — staged pack candidate, not gameplay-accepted

The separately owned RPG mod now has a **staged, unreleased candidate** for eight per-life
builds. The exact RPG JAR is in the local pack inputs and Build 55's packaged
client/server pair. All 54 active custom mods were freshly built from local source
for Build 52; changed cave and discovery-guides sources were rebuilt for Builds 53
and 55, respectively, while unchanged staged JARs were reused. Build 55 passed
real-client Smoke, and a separate disposable singleplayer world generated, rendered
and saved. Neither check exercised pinned-mod combat, temperature or allocation effects. Do not mistake
this for friends' playtesting or gameplay acceptance. Each new experience-level peak
still awards one Life point; committed points are lost on final death. Old
body-part IDs and auto-plans are **not** migrated: keep existing worlds untouched,
and use a freshly created world for the eventual friends' playtest. The existing
allocation screen previews drafted, committed, next-point, and 20-point values.

Steady bonuses follow `F(p)=p/(p+10)` and milestones follow
`B(p)=min(p,20)/20`. At 20 points, `F=2/3` and `B=1`:

- **Vanguard:** Epic Fight armor negation `+6F` percentage points and attack
  knockback `+0.20F`; impact `+0.60B` modifies the hit, not a guaranteed stagger.
- **Sharpshooter:** bow spread and TaCZ gun dispersion each `−18F%`, gun recoil
  `−12F%`; bow launch velocity `+8B%`. Gun and bow effects are separate.
- **Artisan:** mining speed `+12F%`, Tinkers' item-use speed `+0.09F` native
  units; block reach `+B` blocks.
- **Trailblazer:** safe fall `+3F` blocks, swim speed `+15F%`, step height
  `+0.40B` blocks; a full one-block step still needs 0.60 baseline height.
- **Survivalist:** independently slow outward heat/cold drift `15F%`; Cold
  Sweat's resolved safe interval expands by `−0.5B` freezing and `+0.5B`
  burning native units. Neither benefit replaces temperature equipment or food.
- **Spellcaster:** Ars max mana `+60F` and regeneration `+1.2F` per second;
  Goety soul-cost reduction `6F%`, casting-time reduction `8F%`, and additive
  range `+B`. Goety's actual range is integer-valued; a fraction alone does
  not extend a zero-baseline spell. Mana and souls are not interchangeable.
- **Skirmisher:** independent ParCool and Epic Fight stamina capacity `+12F%`
  and recovery `+10F%` on **each** pool. Pools and action costs stay separate;
  no fixed extra-dodge count is promised.
- **Aerialist:** Tinkers' jump boost `+0.09F` native units, Epic Fight basic
  air-attack stamina cost `−12F%` (2 → 1.84 at 20 without other modifiers),
  and existing Air Slash damage modifier `1.5+0.30B` (1.8× at 20). No vanilla
  crit, grounded-hit, or fall-protection bonus.

These are bonuses on top of equipped/modded values, not replacements. The new
Cold Sweat and Epic Fight hooks still need disposable in-world verification before
friends-world deployment or a gameplay-accepted release; native GameTests and
Build 55 Smoke and singleplayer world creation do not establish the promised outcomes. The one-pool stamina
merger remains deferred.

### Prelude — Primitive Workshop

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

### Era 1 — Hand Workshop

Ore separation starts here rather than at the furnace. Use a String Mesh by hand
on a geological chunk to recover one canonical primary unit. Heat remains a
four-nugget emergency exit for metal chunks, but nonmetals emerge directly from
separation and never require ceremonial smelting.

Build native Tinkers' Construct tools and acquire two different underground
rock families among Gabbro, Slate, Phyllite, Quartzite, and Schist. One stone
from each family makes two ordinary TConstruct Grout; smelting or blasting one
grout makes a Seared Brick. The old named Font grouts and Refractory Compound
are no longer registered. Seared metallurgy owns alloy composition: the
automated path alloys molten stone with molten iron or zinc, then casts molten
Andesite Alloy. A Melter can
instead consume one andesite with 10 mB of either molten metal to cast one
native Create Andesite Alloy. Iron-fitted shaft, cogwheel, casing, press and
Hand Crank routes make the basic Create workshop available before Seared entry.
Native TCon Patterns and the Part Builder turn ordinary wood into handles,
bindings, small blades and large plates at one, one, two and four material units;
no metal casting is needed for those first consumed parts.

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

Bumblezone is expedition-only: upstream hive teleportation is disabled.
Ratlantis player access is retired. Ordinary Rats remain, while unavailable
Ratlantis-only inputs are replaced with obtainable materials. Token and portal
outputs stay closed. Ordinary vegetable patches generate naturally in the
Overworld, but Bumblezone nurseries remain the origin of non-space food seed stock
and yield two to four propagules from mature plants. Outside an authored origin,
uprooting preserves one propagule with a ten-percent chance for a second; edible
produce is never itself the planting item. The Field Cook and Embark selections
therefore offer cabbage and tomatoes as food, not their seeds. The cultivar loot
modifier runs after the pack's other global loot modifiers so grass cannot leak
wheat or sage seed into the Overworld.

### Era 2 — Powered Workshop

Exact terrain recovery profiles cover 205 inputs and 27 profiles. Their 500-tick
Crushing or waterlogged Brass Sifting routes cap physical return at 25 percent,
exclude ore and gem assays, and remove two recycling inputs that would amplify
returns. The catalogue names every input and replaces only nine exact recipes;
it does not classify arbitrary stone by tag. Exact-ID recipe-clash fixes preserve
material identity and leave stateful Manufacturing and electrochemistry routes
under their own serializers.

The standard Sifter automates the same recipe language used by hand sifting.
Milling a chunk into two feeds improves primary recovery; Crushing Wheels later
produce three. Dry Sifting returns the primary product, while a waterlogged
Sifter can expose coproducts appropriate to its installed mesh.

Meteor Certus Quartz roots scalable smart logistics without requiring an
operating ME network: one crystal makes eight Pretty Pipes pipes; blank and
high modules, Create requests, Sophisticated hopper upgrades and the Rapid
Hopper consume finite certus. Basic rat work and ordinary Little Logistics
watercraft retain native recipes. AE2's Energy Acceptor requires PowerGrid
generation, one certus crystal, OC2R computation, one sky stone block, and
Impossible Matter. Ratlantis remains inaccessible and does not gate
electronics; ordinary Rats retain obtainable local materials.

Craft `kubejs:andesite_machine_block` from common iron, stone and stripped
wood. The iron workshop supplies its own transmission and first Press; its
Whisk carries configured Manufacturing parts into the Mixer. The Millstone
requires Nether brick in its own recipe. Sifters and Crushing Wheels pay the
same Nether entry separately, including for non-ore processing.

Compact the Andesite block, Copper Casing, copper and iron plates into four
`kubejs:copper_machine_block`. Renewable water and wind generation instead
uses Skyroot logs. The Mechanical Pump, funnels and tunnels remain local
workshop utilities without a dimensional gate. Native part-bearing shaft,
cogwheel, propeller and pump alternatives remain available where applicable.

### Era 3 — Steam Power

Brass Sifters provide the high-throughput physical separation route and expose
all authored coproduct grades when waterlogged. A Spout is not a separator: it
uses water to prepare crushed geology for the later pressure route and performs
direct hydration, cleaning, stripping, and rusting operations formerly assigned
to Bulk Washing.

TCon alloying is the only brass composition route. Compact the Copper block,
Brass Casing, four brass plates, two electron tubes, and polished rose quartz
into `kubejs:brass_machine_block`. The Deployer and Mechanical Crafter use Brass Casing and retain consumed
native parts when Manufacturing is installed. Precision Mechanisms, native
parts, and the existing batch yield carry the Steam Engine and transport
controls without a second full-block casing. Quark's automatic Crafter is downstream of
the Create Mechanical Crafter rather than an independent iron-and-redstone bypass.

### Era 4 — Compressed-Air Power

Four matching rinsed feeds at 2 bar yield four primary units plus the complete
deterministic geological assay. This is the maximum-recovery route, especially
for trace and precious coproducts.

Native Nether grout is disabled; this does not affect ordinary Seared grout.
Foundry access instead uses a heated Create Mixer to combine rinsed Hotstone,
rinsed Black Shale, and basalt into Scorched Bricks, making the geological,
kinetic, and TConstruct systems meet at one
visible boundary. Primitive compressed iron and dried-kelp/iron-plate pressure
seals keep the pressure entry reachable. Mechanically craft
`kubejs:airtight_machine_block` from the Brass block, four compressed iron
ingots, two pressure tubes, and two seals. The open Rotational Compressor and
Pressure Chamber Interface consume pressure seals and configured native parts
rather than an intact block. The Thermopneumatic Plant has one Nether-magma
root with a configured liner part.

Sugar fermentation supplies renewable ethanol, and an open Create Basin oxidizes
it into canonical ChemLib acetic acid. Acetic acid preserves Pickled Pickles and,
with kelp-derived cellulose, makes reusable-capacity Acetate Membranes for hazardous
ChemLib pollutants. Two membranes, two pressure seals, and compressed iron make an
Airtight Upgrade. Use it on a Basin, Fluid Mixer, or Thermopneumatic Processing Plant
before processing gas-state ChemLib inputs or outputs; sneak-use empty-handed to
recover it.

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

### Era 5 — Electrical Power

Mechanically apply insulation/red-alloy wiring to Copper Casing for the first
PowerGrid conductive casing. Pressure-craft `kubejs:electrical_machine_block`
from Airtight proof, that casing, four copper plates, two primitive MoreRed
wires, and an electron tube. Pressure-made wiring and fittings root PowerGrid's first stationary generator
housing. A printed PCB starts the circuit-design station, which also consumes
configured native parts when Manufacturing is present. PowerGrid is the sole
stationary electrical-generation authority; PneumaticCraft remains the pressure
and petroleum authority. Iron-and-coal steel composite smelts into canonical
TCon steel for that machinery and later transition parts. PowerGrid parts, sensors, motors,
and circuits carry later recipes.

### Deferred destinations and spaceflight

Creating Space is retired from this candidate. Better Spaceflight is included from the available feasibility-only source at `e3591959d2c614b71ca96d478ada43f8ac597f81`; the unrecovered survival-preview flight, oxygen and destination implementation is explicitly retired. It adds no new rocket route, Font portal or space production graph. Rocket-only Fallout Wastelands and Twilight Forest access, Ruined Citadel and Ender Guardian are deferred.

### Optional transport

Eureka/VS, Clockwork, Trackwork, and Create trains are engineering branches,
not prerequisites for the ordinary factory spine. Primitive wooden ships begin
in the Hand Workshop; rough mechanical experiments begin in Powered Works;
precision trains and controls in Precision Factory; pressure-driven machinery in
Thermal & Pressure; sensors and gyros in Electrical Control. Aether materials
gate stable high-performance flight, never primitive ships, basic Trackwork, or
low-performance aircraft. The Rail Beetle joins the Precision Factory branch by
combining a Railway Casing, a Barrel, a Minecart, and two Electron Tubes. Its
found example can also appear in an abandoned mineshaft chest; placing that marked
item supplies 32 rails, 16 cobblestone supports, and 8 coal without advanced modules.
baseline coal firebox, 4-block/second drive, 64-rail survey, two-cart drawgear,
and one-wide/four-deep bridge work remain complete and practical. One engine
cradle accepts conditional Create steam, PowerGrid flux, Ars Source, Blood
lifeforce, Pneumatic air, Goety soul, or Malum spirit drives; an empty alternate
drive falls back to fuel in the Beetle's working inventory. Freight belongs in the
consist. Six module bays accept one module per family.
Tier-I machinery begins in Precision Factory, mechanical and civil Tier-II
modules mature in Thermal & Pressure, and efficiency, survey, and dispatch
Tier-II modules require Electrical Control. Every Tier-II recipe consumes its
Tier-I assembly. These nine module upgrades explicitly depart from root-only
Machine Block use: the six mechanical/civil upgrades each consume two Airtight
Machine Blocks; the three efficiency/survey/dispatch upgrades each consume two
Electrical Machine Blocks.

### World compatibility

This is a clean break for new worlds. Old Machine Casing IDs have no aliases or
data migration. Back up an existing world before updating; remove old pack-owned
casings and finish in-flight recipes before attempting a manual conversion.

## Crafting graph policy

The crafting graph is every survival acquisition or transformation edge, not
only crafting-table recipes. It includes recipes, machine processes, rituals,
loot, trades, world generation, entity drops, fishing, guide
recipes, and custom runtime hooks.

`kubejs/config/crafting_policy.json` is the machine-readable
`bc.crafting_policy.v1` contract. It classifies every loaded mod namespace,
names authority domains and family selectors, records capability proofs, and
lists acknowledged debt. `docs/progression.md#balance-policy-register` is the human-readable change
register. These two files are authoritative; `cross_mod_boundaries.json` and
`magic_parenting.json` contain references only. Ars glyph grammar remains in
`formal_magic_domains.json` because those entries describe real spell grammar,
not duplicated ownership policy.

### Enforcement

An unknown loaded namespace blocks startup. Family/authority leaks and live
consumers of cuts are reported with exact recipe and item IDs by the final
KubeJS recipe pass. That report intentionally describes the upstream graph at
the start of recipe application; it is a migration/backlog inventory, not a
claim that the reported edges survive the pack's later removals. The effective
post-application runtime snapshot is authoritative for shipped recipe edges.
A reported category becomes blocking only after its known backlog is zero.
Creative/debug items remain classified as technical rather than survival graph
roots.

Fruits Delight is classified as content and its Dynamic Trees add-on as world
generation; L2 Harvester is content, its 0.5x and 6.0x adapters are
integrations, and L2 Library is infrastructure.

Recipe cuts and canonical-duplicate selectors are also applied to the global
loot modifier from this same contract. Exact selectors and namespace/prefix
selectors resolve against the live item registry, avoiding a second hand-kept
loot denylist. Config, guides, trades, worldgen, and custom hooks need focused
inspection of their owning sources; the generic runtime suite does not assert every
acquisition edge.

The partial Cooking Smoke cohort classifies `better_cooking` as a dormant integration and the newly loaded `kaleidoscope_cookery` and `createhorsepower` namespaces as content, all with `not_applicable` support state. This only closes the loaded-namespace inventory; it does not certify their recipe graphs or promote Cooking's incomplete catalogue. Unknown future namespaces still block startup.

The live pack also classifies Better Content Notifications as presentation,
Create: Aquatic Ambitions as content, and Upgrade Aquatic as world content.
Their support state is not yet a claim that their acquisition graphs are fully integrated.

Vanilla potion delivery is an NBT-sensitive cut that cannot be expressed as a
whole-item selector because `minecraft:potion` also represents plain water.
Brewing registries and alternative brewing surfaces are disabled; recipes,
loot, trades, and newly placed structure inventories reject effect-bearing
drinkable potions and every splash, lingering, and tipped-arrow variant. Plain
water bottles, including Thirst purity metadata, remain supported. Food effects,
status effects, and mod-owned flask systems are outside this cut.

Pretty Pipes uses a visible meteor Certus ladder. One crystal produces eight basic pipes
without a working ME network. A blank module consumes five certus crystals in total,
including its original four; the seven high crafting, extraction, filter, high-priority,
low-priority, retrieval and speed modules each add one certus crystal to their native
medium-tier prerequisites. Ordinary rat work and watercraft keep their native recipes.

Selectors are family-level wherever possible: namespace, tag, ID prefix, or an
exact ID when no stable family exists. Capability roots prove the first
meaningful entry into a system; they do not replace every downstream recipe.

Wood compatibility preserves dedicated outputs instead of merging wood
identities. Quark's named ladder families accept their canonical and vertical
planks, while every other loaded item in `#minecraft:planks` is accepted by
the oak-ladder recipe. The fallback changes recipe compatibility only; it does
not classify those planks as oak or replace their normal wood-family tags.

### Invariants

- Nonliving matter consumes finite substrate. Biological growth and breeding
  may remain renewable.
- Space may be costly and finite, including AE2 spatial IO, but not infinite or
  remotely universal.
- PowerGrid owns stationary electrical generation. Electrical consumers,
  storage, pressure, gas, SU, and OC2R remain supported.
- Tinkers' Construct owns conventional pickaxe/axe/shovel/hoe/sword capability.
  Unique weapons, bows, armor, creature gear, and material integrations remain.
- The vanilla End is inaccessible. End-only encounters and rocket destinations
  remain deferred; retained Overworld cultivation, coastal fishing and Ice and Fire
  provide the currently available alternatives.
- Vanilla potion delivery is absent; plain water bottles and independently owned
  food, status-effect, and flask systems remain.

Vanilla water/lava source renewal and renewable stone loops are acknowledged
noncompliance for this release. PneumaticCraft's infinite-source exception is
off, and existing Create hose-pulley/bottomless-fluid safeguards remain. A
strict finite-fluid migration is deferred; Flowing Fluids and a custom fluid
implementation are intentionally not introduced here.

## Balance policy register

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
| Technology eras | Parallel roots | Hand Workshop → Powered Workshop → Steam Power → Compressed-Air Power → Electrical Power; cumulative supply options for enduring machinery, with fitted parts and distinct capability gates; spaceflight deferred | pack progression | Explain power infrastructure without implying replacement machines or changed acquisition | recipes, guides | recipe/config source inspection; Manufacturing acceptance remains separate |
| Primitive workshop | Generic rustic utilities inherit crop and machine-mod defaults | Straw, rope, canvas, fired goods, and simple metal fittings supply pre-Font local handling, homesteading, clothing, lighting, construction, and transport; plates, redstone, alloys, casings, and scalable automation remain later | pack progression | Useful handmade play before dimensional industry | recipes, guides | recipe source inspection |
| Ore separation | Create Bulk Washing and source-specific output ladders | String-mesh hand Sifting → powered dry/waterlogged Sifting → Spout-prepared pressure assay; Blood, Hexerei, Ars, and Occultism converge on the same canonical resources with distinct costs | Realistic Ores / Create Sifting | One resource economy with early access and meaningful recovery depth | recipes, items, guides | generated processing-contract tests and recipe inspection |
| Create steam | Native low-cost engine | Precision Mechanism root; Manufacturing recipe consumes a Tough Handle piston rod, not a Brass Machine Block | Create / Better Manufacturing | Precision access and fitted conversion hardware | recipe, native capacity hook | `30_precision_factory.js`; Manufacturing source inspection, not full-pack acceptance |
| Electrical generation | Dynamo, bee, weather, AE2, and Ars conversion peers | PowerGrid alone owns stationary electrical generation; OC2R remains a consumer/peer and Clockwork's steam generator is gas-network machinery | PowerGrid | One electrical authority | recipes, loot, guides | recipe/config source inspection |
| Conventional tools | Many fixed tools | Ordinary fixed tools close; unique weapons and gear remain | Tinkers' Construct | Material-system coherence | recipes, loot, trades | crafting-policy selectors and source inspection |
| Mahogany | Hexerei and Nature's Spirit plus Dynamic Trees | Nature's Spirit owns the canonical mahogany wood set and progression; Dynamic Trees Hexerei supplies dynamic Hexerei tree structures | Nature's Spirit | One material authority | recipes, worldgen, loot | crafting-policy selectors and source inspection |
| Hand fishing | Vanilla/TCon rods | Only Starcatcher rods remain | Starcatcher | One fishing system | recipes, loot | crafting-policy selectors and source inspection |
| Petroleum | Create Diesel extraction/distillation | PneumaticCraft splitting supplies retained fuel consumers; Create Diesel extraction, scanning, pumping, and crude-oil distillation close | PneumaticCraft | Finite process ownership | recipes, machines | recipe/config source inspection |
| Ratlantis | Token/portal/Font alternatives | Player travel retired; ordinary Rats remain, while unavailable recipe inputs are replaced with obtainable materials | Rats / pack worldgen | Preserve Rats gameplay without dimension access | loot, recipes, world | recipe/registry + native-worldgen tests |
| Logistics | Ratlantis component ladder | Chutes, hoppers and ordinary rat work stay local; one meteor certus crystal makes eight Pretty Pipes pipes, five craft a blank module, and one adds to each high module, request network, Sophisticated automation and Rapid Hopper; native Little Logistics watercraft return | meteor Certus / pack recipes | Visible finite smart inputs without an ME bootstrap loop | recipes, guides | recipe registry + meteor acquisition tests |
| AE2 | Independent start and generators | Energy Acceptor requires PowerGrid, OC2R, one meteor certus crystal, one sky stone block and Impossible Matter; AE generators close | AE2 with PowerGrid supply | Conjunctive late root without inaccessible materials | recipes | registry/meteor source inspection |
| Occultism parenting | Independent/End-oriented | Hexerei cauldron preparations require Aether, Nether and Bumblezone trophies plus finite amethyst; spirit-attuned crystal uses amethyst | Hexerei | Three live Font parent | cauldron, rituals, guides | recipe registry inspection |
| Blood siblings | Mixed hierarchy | Blood Magic, Malum, and Goety are siblings under Blood; Ars is concurrent | Blood domain | Clear magic ownership | recipes, docs | config/recipe source inspection |
| Vanilla potions | Brewing Stand, alternate brewers, loot, trades, and structures supply drinkable, splash, lingering, and tipped-arrow variants | Brewing and alternate delivery close; effect-bearing vanilla potion stacks are hidden and removed from normal acquisition while plain water bottles, food effects, status effects, and mod-owned flasks remain | pack potion policy / Better Content Fixes | Remove a redundant delivery family without breaking survival water or independent magic systems | recipes, brewing registry, loot, trades, structures, viewers | KubeJS policy inspection + Better Content Fixes unit and GameTests |
| Finite matter | Water/lava and familiar generators | Ars generation glyphs/rituals, Blood sigils and their sigil-only consumers, Drygmy, and Whirlisprig generation close; finite bucket transformations remain | matter policy | No inputless nonliving matter | recipes, rituals | recipe/config source inspection |
| Finite space | Occultism remote/infinite storage and mining | Storage controllers, remotes, stabilizers, satchel, wormholes, mineshaft, and miners close; dimensional matrix remains finite | space policy | Bounded storage | recipes, rituals, guides | recipe/config source inspection |
| Flight tiers | Creative hooks, top jet/omega, ritual flight | Red Hook creative flight is off; jet boots 4–5 and omega close; listed finite/local alternatives remain | mobility policy | Bounded mobility | config, recipes | config/recipe source inspection |
| Stored travel | Warp and recall families | Ars warp, Goety Recall/Call/End Walk, Recall Potion, and Spatial Sign close; local, combat, creature transport, and dragon horn remain | mobility policy | No stored-coordinate player travel | recipes, rituals, runtime | recipe/config source inspection |
| Fluids | PneumaticCraft may respect infinite sources | Exception off; hose-pulley safeguards remain; strict vanilla sources are deferred | PneumaticCraft | Avoid new fluid migration | config | config source inspection |
| Dragon ecology | Vanilla dragon boss products | Vanilla head, egg, and breath close; legitimate custom-serializer consumers use generic Ice and Fire tags | Ice and Fire | Closed-End replacement | worldgen, drops, recipes, loot | recipe/tag consumer inspection |
| Ice and Fire world | Common defaults | Complete Overworld ecology at least-frequent nonzero settings; wild griefing full, tamed griefing off | Ice and Fire | Rare but complete ecology | config, worldgen | config/spec and material-data inspection |
| Dragonsteel | Native fixed equipment | Native config sets base armor 8, toughness 3, base armor durability 2500, tool durability 2500, and sword base attack 9; actual armor durability is slot-derived; equal TCon materials use 1500, 8.0, 3.0, netherite tier | Tinkers' Construct | Controlled top material | config, recipes, TCon data | config/spec and material-data inspection |
| Closed End | Vanilla portal and city ecosystem | End remains inaccessible; rocket-only encounters and destinations are deferred | pack progression | Avoid inaccessible active routes | dimensions, recipes, worldgen | source and recipe inspection |
| The Flesh That Hates ecology | Mod entities available for general encounter design | Deep Void only; pack structures use flesh biomes and source gates independent incubator/servant paths with loaded-chunk, 4,096-probe and 32-read bounds | pack integration | Keep its ecology in one retained habitat | spawns, dimensions | source and exact-artifact runtime inspection |
| End resources | End-only catches, bees, trims, and Elytra | Catches move to warm/lukewarm Overworld ocean; bee lineage uses Overworld chorus; trims use rituals; Elytra uses mechanical Ice and Fire inputs | Ice and Fire / magic owners | Finite replacements | fishing, mutations, rituals, recipes | recipe/config source inspection |
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

## Food progression

The Journal displays Diet's eight active groups and stored values; Diet's separate screen is not exposed. Protein, Grains, Fruit, Vegetables, Spice, and Dairy provide lasting nutrition benefits. Sugar and Alcohol are temporary loads. Benefits are applied by Better Survival Physiology, not Diet's native effect suite. Prepared food credits supported Diet groups supplied by its paid ingredients; its direct tag is not extra credit.

| Group | First dependable source | Later food and role |
| --- | --- | --- |
| Protein | Meat, fish, eggs | Charged strikes, then stagger and cleave |
| Sugar | Fruit, honey | Amplifies benefits; hunger and nutrition drain rise with load |
| Grains | Bumblezone grain seed line | Correct-tool mining rhythm and a work burst |
| Fruit | Wild fruit and berries | Safer travel, stride, and a sprinting leap |
| Vegetables | Carrots, other vegetables | Hunger, thirst, and stamina savings; emergency reserve |
| Spice | Actual paid seasonings | Bounded work/movement benefits with a serving cost |
| Dairy | Cow milk and cheese | Meal recovery and cleansing |
| Alcohol | Fermented drinks | Damage reduction with rising impairment and blackout at full load |

The Overworld supplies wild fruit and animal foods. Farmer's Delight's seven wild crop placements are disabled at their native biome modifier IDs. Cultivatable grain and vegetable stock comes from the Bumblezone. Fruits Delight provides forage plants and cooking recipes, while its Dynamic Trees addon gives the fruit trees the pack's tree behavior. Farmer's Delight fruit salad is available from early fruit and honey; a cooking pot can make fig chicken stew with milk, and later pineapple fried rice combines cultivated grain, vegetables, eggs, meat, and fruit. See the actual recipes in EMI for exact counts and tools.

Milk drinking directly awards Dairy because a milk bucket is not an edible food item in Diet. Fruits Delight foods retain their distinct utility effects; newly applied vanilla beneficial effects are limited to level I and 60 seconds, while Health Boost, Cycling's experience orbs, and Digesting's overflow nutrition are suppressed. Existing stronger effects are preserved.

Sugar amplifies the six lasting groups' unlocked effects linearly with `1 + load`; the hunger cost and both native and upper-band nutrition decay scale with `8^load`. Alcohol reduces incoming damage according to its load and Sugar amplification. Its control and work penalties begin above 30% and rise to severe impairment at full load. At 100% the player blacks out until load drops to 85%.

## Realistic Ore Processing

Realistic Ores owns deposit registration, worldgen, block lifecycle, processing
recipes, outputs, tags, and assets. Pack KubeJS does not register duplicate
chunks, crushed feeds, concentrates, tailings, or molten exits.

### First-contact geology

The complete player-facing worldgen roster is deliberately limited to eight
behavioural fantasies:

| Deposit | Immediate promise | Visual identity |
| --- | --- | --- |
| Coal Measures | fuel | broken uneven carbon seams; home Y 40..144, echo Y 160..320 |
| Ironstone | iron and tools | rusty lenticular/oolitic beds; home Y -16..80, echo Y 112..288 |
| Copper Bloom | copper | branching stockwork and oxidation halos; home Y 16..112, echo Y -112..-56 |
| Tin Quartz | bronze and quartz | steep quartz lodes and cassiterite splays; home Y -72..16, echo Y 160..384 |
| Brassroot | zinc and brass | asymmetric dendritic fracture veins; home Y -48..40, echo Y 96..192 |
| Evaporite Beds | salt and preservation | stacked salt/gypsum beds; home Y 72..176, echo Y -32..24 |
| Hotstone | dangerous usable heat and heavy matter | lopsided breccia pipes; home Y -128..-72, echo Y 192..448 |
| Black Shale | redstone and supernatural material | dark laminations and violet stringers; home Y -104..-24, echo Y 0..64 |

Oil Seep remains a separate fluid surface feature. Technical materials survive
as assay depth where they have an audited pack use; they do not create additional
worldgen families merely to preserve chemical taxonomy.

The Aether has rare ironstone in Holystone. Twilight Forest has rare Coal Measures,
Ironstone, Copper Bloom, and Tin Quartz in ordinary stone. The pack removes
Twilight's legacy coal, iron, and copper placed features only in its own biomes;
the matching Realistic Ores deposits provide their ordinary material routes in
new chunks. Twilight gold remains until an authored gold-bearing replacement is
available. Create zinc and Occultism silver add modifiers are already scoped to
the deliberately empty `#kubejs:no_biomes` tag; their processing outputs remain.

Each family exposes authored stone and deepslate blocks, a surface sample whose
item identity is `small_ore_chunk_<family>`, a full `ore_chunk_<family>`, and a
`crushed_<family>` processing feed. Native and Excavated Variants hosts separate
and reassemble losslessly. Scattered family worldgen and ADLODS deposits remain
complementary exploration layers. ADLODS mirrors the same home and echo bands: the home
configuration carries about 85% of expected large-body supply (95% for Hotstone), while
the distant echo is 1.5 times larger per body (2 times for Hotstone). Aggregate expected
yield remains within 1% of the prior configuration for every family.

ADLODS deposit `Indicator` circles place matching Realistic Ores surface samples near
the corresponding underground body. A sample is a useful local clue to prospect
below, although a later extraction of the same ore family does not prove it came
from that particular body. The sample can also be collected for a small chunk.

### Processing depth

1. Ordinary mining yields one host-independent chunk; Silk Touch preserves the
   exact ore block. Nine small chunks combine into one full chunk.
2. A String Mesh can hand-sift a chunk for one primary recovery unit before
   rotational power. A Millstone produces two crushed feeds and Crushing Wheels
   produce three, which makes comminution the first recovery multiplier.
3. Dry Sifting recovers the primary product. Waterlogged Zinc Sifting adds major
   and minor coproducts at 12.5% and 6.25% per feed. Waterlogged Brass Sifting
   exposes major, minor, trace, and precious grades at 25%, 12.5%, 5%, and 1.25%.
4. The maximum-recovery technical route fills each crushed feed with 250 mB of water, then
   pressure-separates four matching rinsed feeds at 2 bar. It returns four primary
   recovery units plus the family's complete curated coproduct set. PneumaticCraft's
   result format has no chance field, so listed technical coproducts are deliberate
   deterministic outputs rather than misleading pseudo-probabilities.
5. Blood Magic, Hexerei, Ars Nouveau, and Occultism process unsplit chunks into
   the same canonical primary products. Their apparatus costs and side bonuses
   differ, but they cannot stack with Create's comminution multiplier.
6. Heat is a low-yield fallback and finishing operation for metals only. One metal
   chunk or concentrate yields four nuggets. Quartz, gems, salts, carbon, sulfur,
   redstone, lapis, and other nonmetals leave separation in their usable item form.

Tin Quartz folds gem-bearing pegmatite assays into its industrial depth, while
Hotstone uses route-specific assay variants, so legible deposits can reveal
several late technical profiles without multiplying blocks.
The retained catalogue is 24 audited outputs plus rock salt, sodium chloride,
and saltpeter.

Acids remain authored chemistry products for their own consumers, but are not a
second ore-separation matrix. There are no acid permutations, grinding media,
or hidden "best ball" ladder to memorize.

### Immediate utility

The processing backend is deep but begins without machinery: every chunk can be
hand-sifted with a String Mesh. Coal chunks also burn directly, Black Shale blocks
support soul fire, and Hotstone emits light, hurts on contact, and can be consolidated
into magma. Direct Rock Salt and Soul Sand crafting shortcuts are intentionally gone;
their recovery now teaches the shared separation system.

### Four salient routes

| Route | Input | Outcome |
| --- | --- | --- |
| Technical | four rinsed feeds at 2 bar | four primary units plus the complete deterministic assay |
| Blood | one chunk and ARC cutting fluid | two primary units plus a 25% corrupted tiny-dust chance |
| Hexerei | four chunks, four selenite shards, water, and heat | eight primary units |
| Ars | one chunk | two primary units plus a 25% family essence chance; Tin Quartz may also yield Source Gem |
| Occultism | one chunk | two primary units, modified by the summoned crusher's tier |

These are alternative approaches to shared resource results, not isolated resource
economies. Recipe viewers show exact outputs and apparatus costs; the version-3
processing manifest records the common units and route-specific bonuses.

Create Bulk Washing is disabled. Granular legacy washing recipes use waterlogged
Sifting, while direct hydration, cleaning, stripping, and rusting operations use a
Spout. Flour and cleaning operations consume 100 mB, concrete consumes 250 mB, and
magma-to-obsidian consumes 1,000 mB. The outputless sugar-cube recipe and direct
ice-to-packed-ice shortcut are removed.

### Design contract

World appearance supplies the useful prior; the name confirms the immediate
fantasy; behaviour proves it; EMI supplies the exact assay and recipe details.
The art remains dirty, morphology-first geology with host rock visually dominant.
Systemic Salience does not turn ores into neon aspect tokens.

### Retained constituent utility audit

| Constituent | Why it survives |
| --- | --- |
| Coal, iron, copper, tin, zinc | immediate fuel, tools, bronze, brass, and progression roots |
| Gold, redstone, quartz, lapis | familiar technical and magical crafting economies |
| Diamond, emerald, amethyst | deeper Tin Quartz assays and ordinary gem uses; no diamond ore generates |
| Rock salt, sodium chloride, saltpeter | cooking/preservation first; chemical and nitrate depth later |
| Soul sand, sulfur, vanadium | immediate Black Shale/soul utility; sulfur and vanadium supply the supported contact-process acid chain |
| Aluminum, cobalt, osmium, silver | live TConstruct materials and pack metallurgy/electrum routes |
| Nickel | Retained molten material and historical source; no active space gate |
| Titanium | Retained molten material and thermal plate; no active space gate |
| Uranium | Hotstone fissile assay, Necronium, nuclear simulation, and Protection Pixel recipes |
| Thorium | Hotstone fissile assay, nuclear decay chain, and Protection Pixel recipes |
| Lead, cadmium | Hotstone/Brassroot assay depth and real placeable-block absorber behavior in Latent's nuclear environment scan |

The removed beryl/beryllium, calcium, carbon, chromium, gallium, iridium,
magnesium, phosphate, platinum, silicon, sodium, tantalum, and tungsten
concentrates had no distinct live consumer strong enough to earn assay output.
The obsolete Alchemistry audit input was removed and is not a live use. The obsolete
`better_chemlib_hazards/material_coefficients` data file was removed because the runtime
nuclear simulation does not load it; absorber evidence comes from the actual
block-ID environment scan.

## Weapon Balance Philosophy

This is the living design authority for weapon balance in Better Content. It records the reasoning that survives individual tuning passes: what a weapon owes the player, which advantages may alter its damage budget, and which exceptions must be explicit.

The document currently contains only the Tinkers' Construct family philosophy. Other weapon systems need their own named pass; they do not inherit TCon values automatically.

Weapons of Miracles is a pinned candidate mod input. Its presence adds combat
content; it does not grant a blanket balance, animation, tooltip, compatibility
or in-game qualification. The proposed reach-based melee recalibration remains
future work in [Design](design.md#reach-based-melee-recalibration).

### Shared principles

1. **DPS is mandatory.** Utility, manufacturing difficulty, rarity, and mob-specific bonuses do not replace general combat output.
2. **Normalize baselines, not every build.** Materials, traits, upgrades, affixes, execution, and encounters may diverge after the clean chassis is sound.
3. **Costs and advantages are asymmetric.** Short reach and slow commitment are strongly punishing and deserve compensation. Long reach and high speed have diminishing rewards.
4. **Slow attacks hit harder.** Missed, interrupted, and overkilling slow swings are costly, so committed weapons must visibly pay out per connection.
5. **Ordinary AOE is not a second damage bar.** Common cleave and sweeps are texture, not a large DPS tax.
6. **Generic effects count; narrow effects do not.** Armor piercing, armor bypass, unconditional offhand attacks, and generic burst belong in E-DPS. Mob-family bonuses do not.
7. **Primary and alternate modes have separate budgets.** Throws, charge shots, charged strikes, block counters, and casts do not inflate the neutral melee baseline.
8. **Animation identity needs a viable carrier.** Each supported Epic Fight form has at least one credible TCon tool or weapon.
9. **Tools and weapons remain separate catalogues.** A tool may fight well without ceasing to be work-first.

### Tinkers' Construct family

#### Baseline

The authoritative machine-readable catalogue is `kubejs/config/tcon_edps_catalogue.json`. Its neutral grounded-combo baseline excludes dash, air, mount, innate skills, throws, charges, casting, and dual variants. Generic armour effects count; normal AOE does not.

| Relative tier | Absolute old-sword DPS |
|---:|---:|
| 90 | 81.0% |
| 95 | 85.5% |
| 100 | 90.0% |
| 105 | 94.5% |
| 110 | 99.0% |

The reference is the former same-material TCon Sword baseline. Native attack speed and material scaling are retained, so slow weapons receive larger per-hit multipliers. Pierce and the Estoc's generic bypass are evaluated against the pack armour canon; material traits, upgrades, utility, manufacturing cost, ordinary AOE, and mob-specific effects are excluded.

#### Epic Fight form map

The pack replaces Better Combat with Epic Fight. Every chassis in the 45-entry balance catalogue has an explicit item capability that selects an existing Epic Fight form; the pack does not author bespoke movesets for each TCon item. Longbow, Crossbow, and Swasher retain the integration's dedicated ranged capabilities. Tools are mining-preferred and weapons combat-preferred.

| Tool form | Tools |
|---|---|
| Axe | Hand Axe, Broad Axe, Wrench |
| Fist | Sniffer Claws |
| Greatsword | Sledge Hammer, Vein Hammer, Scythe, Blockram |
| Pickaxe | Mattock, Pickaxe, Pickadze, War Pick, Chisel |
| Shovel | Excavator, Shovel |
| Sword | Battle Spade |
| Hoe | Kama |

| Weapon form | Weapons |
|---|---|
| Axe | Butcher Knife, Minotaur Axe |
| Bow | Shortbow |
| Crossbow | Blowpipe |
| Dagger | Dagger, Rapier |
| Fist | Buckler |
| Greatsword | Cleaver, Greatsword, Helix Blade |
| Longsword | Estoc |
| Spear | Pitchfork, Halberd, Amethyst Staff, Quarterstaff, Pike, Lance |
| Sword | Cutlass, Battlesign, Scepter, Sword |
| Tachi | Fuma Shuriken, Katana |
| Trident | Javelin |
| Integration ranged | Longbow, Crossbow, Swasher |

The machine-readable mappings live under each item's
`kubejs/data/<namespace>/capabilities/weapons/` resource path, where they override the
maintained `epic_tinkers_construct_v9` datapack for the catalogue roster. Existing v9
numeric attributes and colliders remain intact apart from schema/style corrections;
items outside the catalogue continue through v9 or the compatibility mod's fallback.

The normal item-hover surface derives one concise player-facing handling line
from this catalogue and those capability mappings. It humanizes the Epic Fight
form and any material alternate mode, marks work-first tools where useful, and
never exposes EDPS tiers, multipliers, head-scale math, or compatibility keys.

Shovel and Battle Spade remain separate: the Shovel is a conventional work tool with shovel motion, while the two-head Battle Spade is a martial combat chassis with sword combos. Javelin keeps its throw through Trident; Amethyst Staff keeps its caster identity while using a melee Spear form.

#### Dodge, camera, and alternate modes

Combat Roll and Better Content Fixes' directional double-tap integration are removed. Epic Fight's native Roll and Step progression is the only dodge system and uses Epic Fight's dedicated input. The Epic Fight combat camera is active in third-person only; it never changes perspective automatically.

Returning Blockram, Buckler, and Fuma Shuriken throws use the separate 100-tier throw target. Javelin throws stay at 105 per hit. Battlesign's charged Bonk and War Pick's charge projectile target 100. The Minotaur Axe sprint charge remains the one explicit signature exception.

Weapon regressions belong to their owning source-local tests and explicit manual balance reviews. Pack-level Smoke/Soak exercise only session lifecycle, not weapon balance or a scripted combat tour.

## TECH-01 — Meteor and early technology component map

This source map records the authored and pinned routes used by the early technology palette. It distinguishes the meteor branch from the later Energy Acceptor / ME-network gate. It does not claim candidate recipe registration or runtime reachability.

### Component and acquisition matrix

| Component | Authored/native route | Progression role |
| --- | --- | --- |
| AE2 Sky Stone | AE2 meteorites provide the authored meteor material. Sky Stone is pickaxe mineable at stone tier and retains AE2's native block drops. The pack removes the Occultism miner recipe type because the pinned miner recipe directly outputs `ae2:sky_stone_block`. Blood Magic's `bloodmagic:meteor/ae2` invocation (500,000 LP syphon) is removed by exact recipe ID because it creates repeatable Sky Stone meteor material. Certus Quartz still has to come from finding an AE2 meteor. Other Blood Magic meteor recipes remain available. | Sky Stone comes from AE2 meteor world generation; Certus remains meteor-dependent. No pack-authored NPC or loot-table producer was found in the reviewed fixture. |
| `ae2:certus_quartz_crystal` / `ae2:charged_certus_quartz_crystal` | AE2's natural Certus deposit and charging apparatus. The authored controller recipe consumes one `ae2:charged_certus_quartz_crystal`. | Early material from AE2's world resources, not an operating ME network. |
| Create Rose Quartz | Pinned Create 6.0.8 `data/create/recipes/crafting/materials/rose_quartz.json` supplies one `forge:gems/quartz` plus eight redstone dust; the pack changes its quartz slot to `ae2:certus_quartz_crystal`. | The crafted Rose Quartz route requires meteor Certus. |
| Polished Rose Quartz | Pinned Create 6.0.8 `data/create/recipes/sandpaper_polishing/rose_quartz.json`: sandpaper polish one Rose Quartz. | Intermediate for the Electron Tube. |
| Electron Tube | Pinned Create 6.0.8 `data/create/recipes/crafting/materials/electron_tube.json`: one Polished Rose Quartz and one `forge:plates/iron`. | Used by the Brass Machine Block, Deployer, Mechanical Crafter, and the authored AE2 controller recipe. Its native recipe remains; the crafted Rose Quartz prerequisite now requires Certus. |
| Power Grid Integrated Circuit | Pinned Power Grid 0.5.4 `data/powergrid/recipes/mechanical_crafting/integrated_circuit.json`: Rose Quartz, redstone, gold nuggets, and lapis. | Part of the authored controller and the separate electrical branch. This exact recipe does not use Certus or Sky Stone. |
| Electrical Machine Block / Generator Housing | Pack source builds Conductive Casing from Create Copper Casing plus More Red Alloy Wire; the Electrical Machine Block then requires the Airtight Machine Block, casing, copper plates, wire, and one Electron Tube. The first Generator Housing uses 2-bar pressure, iron/copper plates, Conductive Casing, red wire, and an Electron Tube. The printed PCB opens the Soldering Table. | Power Grid's stationary electrical root is available through the authored electrical machine chain, without an AE2 Energy Acceptor. |
| AE2 Controller | `80_tech_palette.js` replaces the output recipe with a PneumaticCraft Pressure Chamber recipe at pressure 4.0 using `ae2:sky_stone_block`, `ae2:charged_certus_quartz_crystal`, `ae2:engineering_processor`, `powergrid:integrated_circuit`, and two `create:electron_tube`. | A pressure-built AE2 control root using meteor material and non-ME prerequisites. |
| AE2 Energy Acceptor | `20_meteor_logistics.js` requires Power Grid Generator Housing, one accessible meteor Certus crystal, OC2R Computer, one Sky Stone block and Impossible Support Matrix. | Later conjunctive gate without an inaccessible Ratlantis component or operating ME-network prerequisite. |

### Source evidence and limits

The Create fixture jar SHA-1 is `b13d912b9247a38d66d11598c121442585a1c1e9`; the Power Grid fixture jar SHA-1 is `ebdbcd3e267c8a92e6685728f9d7fdd3e7b9838a`. Both match the active Packwiz pins. The exact recipe resources above were read from those jars. The pinned Occultism bypass removal is protected by `TechPaletteSourceContractTest`.

The reviewed retained server fixture's static recipe/loot scan found no direct pack target loot or villager-offer output. A classfile scan of the active pinned mod set likewise found no target strings in known NPC offer classes. Dynamic offer registration that references registry fields rather than serialized item IDs is not ruled out. A full recipe graph, live merchant census, recipe registration, candidate integration, and Nether/meteor progression playthrough are still open; this source audit does not substitute for them.

## PneumaticCraft capability and acquisition matrix

Source snapshot: the active pack pin `pneumaticcraft-repressurized-6.0.22+mc1.20.1.jar`, CurseForge project/file `281849/7307654`, SHA-1 `cbe2d3c5df7c2770ac59eb6f02489d8917627c03`. The retained Build 292 server artifact has the same SHA-1. This is a source inventory; it does not prove that recipes load or that a route is reachable in a generated world.

The matrix separates PneumaticCraft's native mechanics from the pack's recipe overrides. It records known route edges from `kubejs/server_scripts/progression/81_pneumaticcraft_progression.js`, `45_acid_chemistry.js`, and `utility/10_recipe_surface_helpers.js`. The progression script is currently dirty shared work and was read without editing.

| Capability | Relevant PneumaticCraft identities | Current source route | Open source or acceptance edge |
|---|---|---|---|
| Basic compressed air | Air Compressor, pressure tubes, air canisters, pressure regulator/storage | Pack replaces the compressor recipe with iron plates, pressure tube, compressed iron, pressure seal and Create cogwheel. Tube, regulator and canister recipes remain native. | Exact earliest acquisition and pressure safety acceptance require candidate recipe loading and route verification. |
| Pressure chamber | Pressure Chamber, glass, valve, pressure tube | Native pressure chamber route; pack retains it as a basic pressure-era processing surface. The added acid catalyst route uses 2.0 bar. | Native recipe inputs and unlock root have not been closed as a complete acquisition graph. |
| Thermal and fluid processing | Thermopneumatic Processing Plant, Refinery, Fluid Mixer, Etching Tank, Thermal Compressor | Pack gates the thermopneumatic plant with advanced tube, compressed-iron gear and an authored airtight machine block. `45_acid_chemistry.js` authors acid reactions in the PneumaticCraft thermo plant/fluid mixer and preserves bounded pressure, temperature and batch inputs. | Native refinery/fuel routes and all output consumers need an exhaustive graph audit; no recipe-load/runtime result is claimed. |
| Plastic and heat chemistry | Oil, LPG, plastic, heat sources and plant fuels | Plastic is explicitly left on PneumaticCraft's native petroleum and heat route. Acid chemistry stays with the owned heat plant; nitric acid uses two saltpeter dust per bounded batch. | No separate plastic bypass is authored here. Complete source/acquisition closure for oil, fuel and heat remains open. |
| PCB manufacture | PCB Blueprint, Empty PCB, Unassembled PCB, Printed Circuit Board; laser assembly program | Native Empty PCB pressure-chamber recipe is retained. Native laser assembly produces Unassembled PCB; the authored acid script removes native etching-acid shortcut and provides nitric-acid chemistry. The pack's current script uses printed boards as electronic-control inputs. | `82_pneumaticcraft_acquisition.js` removes the four exact Amadron program/blueprint recipes and filters mechanic/villager sales of those four items plus printed PCBs. It authors a Laser Program (1.5 bar: Create precision mechanism/electron tube/brass plus pressure tube), Drill Program (2.5 bar: diamond/cylinder/compressed-iron gear/precision mechanism), and PCB Blueprint (1.5 bar: pressure tube/electron tube/brass/paper). The Blueprint recipe has no PCB or instrumentation prerequisite. PneumaticCraft’s native UV Light Box recipe consumes the Blueprint and pressure-era components; this is intended to support the first printed PCB. Native Empty PCB pressure-chamber recipe, UV exposure behavior and complete output reachability still need acceptance. |
| Assembly system and programs | Assembly Controller, Drill, Laser, Platform, Import/Export IO; Drill, Laser, Drill+Laser programs | Controller is authored through `bcPncrPressure` at 5 bar with advanced tube, PCB, PowerGrid integrated circuit and electrical machine block. Drill is authored with diamond, pneumatic cylinders, compressed iron and electrical instrumentation. Laser Program is authored at 1.5 bar; Drill Program at 2.5 bar. The native Drill+Laser combination recipe remains. Platform and IO recipes remain native. | Exact Amadron recipe rows for the three programs are removed and mechanic/villager sales are filtered. This closes the source-level purchase bypass; recipe loading, complete program output acquisition, and requirements for Platform/IO remain open. |
| Sensors and logistics | Universal Sensor, Inventory Upgrade, Coordinate Tracker Upgrade | Pack replaces Universal Sensor with a seismometer, plastic, electrical instrumentation and pressure tube. Inventory Upgrade requires PCB, plastic, instrumentation and a wooden chest; Coordinate Tracker requires GPS tool, PCB, AE logic and redstone. | Sensor/upgrade outputs, alternate trades and all prerequisite roots remain unverified as a complete graph. |
| Drones and armor | Drone, Drone Interface, Armor Upgrade and upgrade family | Drone recipe is pressure-gated at 5 bar and requires PCB, plastic, PowerGrid motor and Create precision mechanism. Armor Upgrade is authored from plastic, PCB, integrated circuit and compressed-iron gear. Script 82 filters the mechanic profession's direct base-Drone sale. | The exact mechanic trade bytecode includes a base Drone offer, now filtered. Specialized logistics/harvesting/collector/guard drones and the native Drone Interface/armor upgrade alternatives remain to be reconciled against their consumers; recipe loading and progression reachability remain open. |
| Advanced upgrades | Jet Boots tiers 3–5, Minigun, Entity Tracker, Charging, Magnet and Range upgrades | The pack removes each listed output recipe and adds `pneumaticcraft:pressure_chamber` routes at authored 3.0–5.0 bar, retaining the named prior tier, material and control inputs in `81_pneumaticcraft_progression.js`. | Inputs and native alternate sources need complete route audit; no broad `advanced` status is inferred from air access. |
| Exceptional services | `pneumaticcraft:programmer`, `pneumaticcraft:programmable_controller`, `pneumaticcraft:amadron_tablet` | Programmer is authored from turbine rotor, PCB and instrumentation; Programmable Controller from remote, PCB, drone, advanced tube and AE logic; Amadron Tablet from plastic, GPS tool and air canister. Native programmed behavior and unrelated Amadron services are preserved. The known direct Amadron Tablet entry in mechanic-house loot is removed by script 82. | The direct Amadron Tablet and advanced-pressure-tube entries in the PneumaticCraft mechanic-house chest are removed by script 82; the latter protects the authored thermopneumatic-plant gate. The mechanic trades for PCB Blueprint and printed PCB are filtered; the four static Amadron program/blueprint recipes are removed. Other loot/trade channels and candidate application remain unverified. |

### Exact native acquisition evidence

The exact-hash PNCR 6.0.22 artifact contains static Amadron recipes for `assembly_program_drill`, `assembly_program_laser`, `assembly_program_drill_laser`, and `pcb_blueprint`. The three program purchases and Blueprint purchase are removed; other fluid/emerald Amadron services remain. The native Drill+Laser combination recipe remains. Laser Program and the first PCB Blueprint pressure recipe have no printed-PCB or instrumentation input. The native unassembled-PCB recipe requires the Laser Program, while native Assembly Laser requires a printed PCB; the pressure Blueprint recipe and mechanic-house loot filter preserve a possible first-board route without a recipe cycle.

Inspection of the pinned `VillagerTradesRegistration` bytecode found mechanic offers for PCB Blueprint, printed PCB, and the base Drone. Script 82 filters those outputs. The native base Drone route in script 81 is gated at 5 bar. A scan of all 155 PneumaticCraft loot tables found two direct progression bypasses in `pneumaticcraft:chests/mechanic_house.json`: `pneumaticcraft:amadron_tablet` (weight 2), and `pneumaticcraft:advanced_pressure_tube` (weight 4). The latter bypasses the authored thermopneumatic plant gate; script 82 removes both exact outputs from that table. Other component loot is retained. A scan of every installed server JAR's loot tables found no other matching direct service or advancement acquisition. A direct recipe-output census across the same installed JAR set found no other-mod recipe creating a targeted PneumaticCraft capability/service item; the listed direct recipe outputs are in the pinned PneumaticCraft data and are either replaced by progression/81 or intentionally retained (native first-board and combined Drill+Laser paths). Compressed Creativity adds `data/compressedcreativity/loot_tables/blocks/bracketed_advanced_pressure_tube.json`, and PneumaticCraft has `data/pneumaticcraft/loot_tables/blocks/advanced_pressure_tube.json`; both are block-break recovery for already-placed advanced tubes, not a new origin for obtaining the first tube. These source changes are covered by the focused unit contract, but their KubeJS/MoreJS/LootJS registration has not been loaded in a candidate.

The pinned native Empty PCB pressure-chamber recipe does not consume the PCB Blueprint; that Blueprint crafts the native UV Light Box. Its authored recipe has no PCB or instrumentation input. Actual UV exposure producing the first printed PCB and complete acquisition reachability remain acceptance work.

### Source closure still required

This inventory is the source manifest required by TECH-03, but it does not close TECH-03 or TECH-07. Remaining source work is to reconcile cross-mod trader and world-generation acquisition sources, trace the complete prerequisite graph for native services and outputs, and close any bypasses of authored local-economy or meteor gates without removing required native behavior. The exact known Amadron program/blueprint and mechanic-house Tablet/advanced-tube bypasses are now covered in source; KubeJS registration and all remaining route closure still require evidence. The actual earliest route graph, thermal/pressure safety, UV first-board behavior and all item acquisition remain acceptance work; this phase has run unit tests only.
