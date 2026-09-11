# Realistic Ore Processing

Realistic Ores owns deposit registration, worldgen, block lifecycle, processing
recipes, outputs, tags, and assets. Pack KubeJS does not register duplicate
chunks, crushed feeds, concentrates, tailings, or molten exits.

## First-contact geology

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

Each family exposes authored stone and deepslate blocks, a surface sample whose
item identity is `small_ore_chunk_<family>`, a full `ore_chunk_<family>`, and a
`crushed_<family>` processing feed. Native and Excavated Variants hosts separate
and reassemble losslessly. Scattered family worldgen and ADLODS deposits remain
complementary exploration layers. ADLODS mirrors the same home and echo bands: the home
configuration carries about 85% of expected large-body supply (95% for Hotstone), while
the distant echo is 1.5 times larger per body (2 times for Hotstone). Aggregate expected
yield remains within 1% of the prior configuration for every family.

## Processing depth

1. Ordinary mining yields one host-independent chunk; Silk Touch preserves the
   exact ore block. Nine small chunks combine into one full chunk.
2. A Millstone produces two crushed feeds and Crushing Wheels produce three.
   A full chunk cooks to two primary units; one crushed feed cooks to one.
3. The main technical route fills each crushed feed with 250 mB of water, then
   pressure-separates four matching rinsed feeds at 2 bar. It returns four primary
   concentrates plus the family's small, curated coproduct set. PneumaticCraft's
   result format has no chance field, so listed technical coproducts are deliberate
   deterministic outputs rather than misleading pseudo-probabilities.
4. Three optional transformations give crushed geology a legible magical use
   without pretending to be higher-yield ore multiplication: Blood Magic spends
   LP for one thematic material, Hexerei combines four feeds with all four
   dimension catalysts, and Ars crushing offers one low-chance elemental essence.
5. Furnace exits resolve to canonical native or ChemLib forms. TConstruct molten
   exits are metal-only; quartz, gems, salts, carbon, and other nonmetals remain
   item-form outputs.

Tin Quartz folds gem-bearing pegmatite assays into its industrial depth, while
Hotstone uses route-specific assay variants, so legible deposits can reveal
several late technical profiles without multiplying blocks.
The retained catalogue is 24 audited outputs plus rock salt, sodium chloride,
and saltpeter.

Acids remain authored chemistry products for their own consumers, but are not a
second ore-separation matrix. There are no acid permutations, grinding media,
or hidden "best ball" ladder to memorize.

## Immediate utility

The processing backend is intentionally deep, but every family must communicate
something before that backend is required. Coal chunks burn directly. Evaporite
material yields Rock Salt for cooking and preservation. Black Shale supports soul
fire, yields soul sand, and supplies redstone. Tin Quartz later separates gems.
Hotstone emits light,
hurts on contact, and can be consolidated into magma.

## Four salient routes

| Route | Input | Outcome |
| --- | --- | --- |
| Technical | four rinsed feeds at 2 bar | primary concentrate plus a compact deterministic assay |
| Blood | four crushed feeds and LP | one family-themed material, as listed below; no Malum spirits |
| Hexerei | four crushed feeds and four dimension catalysts | one family-themed occult material |
| Ars | one crushed feed | a 25% elemental-essence chance, with Tin Quartz's small Source Gem bonus |

These are different uses, not eight media tiers multiplying the same output.
Recipe viewers show exact outputs and apparatus costs; the processing manifest
records the shared intent.

The Blood Magic alchemy-table route consumes four matching crushed feeds. Ordinary
families cost 2,000 LP at upgrade level 2 and take 200 ticks; Black Shale and Hotstone
cost 5,000 LP at upgrade level 3 and take 400 ticks. Its exact one-item outputs are:

| Deposit | Blood output |
| --- | --- |
| Coal Measures | `minecraft:blaze_powder` |
| Ironstone | `minecraft:clay_ball` |
| Copper Bloom | `minecraft:phantom_membrane` |
| Tin Quartz | `minecraft:lapis_lazuli` |
| Brassroot | `minecraft:phantom_membrane` |
| Evaporite Beds | `minecraft:prismarine_crystals` |
| Black Shale | `bloodmagic:corrupted_dust` |
| Hotstone | `bloodmagic:destructivecrystal` |

## Design contract

World appearance supplies the useful prior; the name confirms the immediate
fantasy; behaviour proves it; EMI supplies the exact assay and recipe details.
The art remains dirty, morphology-first geology with host rock visually dominant.
Systemic Salience does not turn ores into neon aspect tokens.

## Retained constituent utility audit

| Constituent | Why it survives |
| --- | --- |
| Coal, iron, copper, tin, zinc | immediate fuel, tools, bronze, brass, and progression roots |
| Gold, redstone, quartz, lapis | familiar technical and magical crafting economies |
| Diamond, emerald, amethyst | deeper Tin Quartz assays and ordinary gem uses; no diamond ore generates |
| Rock salt, sodium chloride, saltpeter | cooking/preservation first; chemical and nitrate depth later |
| Soul sand, sulfur | immediate Black Shale/soul utility; soul, reagent, and pollution chemistry |
| Aluminum, cobalt, osmium, silver | live TConstruct materials and pack metallurgy/electrum routes |
| Nickel | Creating Space alloy routes and molten material |
| Titanium | Space Machine Block, aerospace recipes, and molten material |
| Uranium | Hotstone fissile assay, Necronium, nuclear simulation, and Protection Pixel recipes |
| Thorium | Hotstone fissile assay, nuclear decay chain, and Protection Pixel recipes |
| Lead, cadmium | Hotstone/Brassroot assay depth and real placeable-block absorber behavior in Latent's nuclear environment scan |

The removed beryl/beryllium, calcium, carbon, chromium, gallium, iridium,
magnesium, phosphate, platinum, silicon, sodium, tantalum, and tungsten
concentrates had no distinct live consumer strong enough to earn assay output.
The obsolete Alchemistry audit input was removed and is not a live use. The obsolete
`latent_chemlib/material_coefficients` data file was removed because the runtime
nuclear simulation does not load it; absorber evidence comes from the actual
block-ID environment scan.
