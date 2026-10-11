# Native learning contracts

## Scope and authority

This maintained subject owns the native-behavior boundaries referenced by the pack's hover
annotation metadata. It is authored contract documentation, not retained task evidence or a
runtime acceptance report. Exact upstream inputs are selected by committed Packwiz metadata;
`HoverAnnotationLearningSurfaceTest.kt` and `GunManufacturingContractTest.kt` inspect those
hash-verified inputs directly. See [teaching policy](better_discovery_guides.md),
[testing](testing.md), and [generated-data boundaries](policies/generated-data.md).

Player-facing lines and selectors remain in `kubejs/config/hover_annotations.json`. Update the
owning pin, direct native-input assertions, and this contract together when upstream behavior
changes. Downloaded archives and inspection outputs remain disposable and are rehydrated by
`scripts/prepare_dev_inputs.py`; historical audit files are never required test inputs.

## Dynamic Trees planting

The `mods/dynamictrees.pw.toml` input supplies `Seed#doPlanting`, which invokes
`Species.plantSapling`; the seed planting chance also uses `biomeSuitability`. Soil and species
suitability govern planting. No stump lookup or stump-targeted planting occurs in this Seed
planting path. The pack's seed hover must explain suitable soil/biome without promising stump
replacement. Pack overrides are owned by `config/dynamictrees-common.toml`.

## Seasonal observation

The `mods/serene-seasons.pw.toml` input supplies `CalendarItem.class` and
`SeasonSensorBlock.class`. The calendar observes seasonal state; the sensor's `updatePower`
uses `getSeasonCycleTicks` and season duration to produce a 0–15 redstone signal tracking
progress through the selected season. Neither item replaces crop suitability or seasonal
weather behavior. The native sensor recipe consumes the calendar, quartz, and colorless glass.

## Pollution observation and protection

The `mods/pollution-of-the-realms.pw.toml` input supplies the Aerometer's local
`EntityPollution` readings and the respirator registry's wearer-oriented `RespiratorEffect`
handling. Measurement is not removal. Iron, gold, and diamond respirators protect the wearer;
they do not clean contamination from the world. Direct input checks cover local influence,
respirator lookup, effect duration, and all three registered respirator identities.

## Weather alerts and probability

The `mods/weather-storms-tornadoes.pw.toml` input registers the tornado sensor, tornado siren,
and weather forecast. Sensor/siren behavior observes forming storms within configured range;
the siren additionally observes intense particle storms. Forecast interaction reports local
storm probability rather than a live warning. The artifact may contain resources mentioning
`tornado_siren_manual`, but there is no corresponding registered object; do not expose a hover
claim for it. Tests inspect the registered block/item fields and native recipes directly.

## AE2 encoded intent

The `mods/applied-energistics-2.pw.toml` input defines three distinct item roles:

- `BLANK_PATTERN` is `ItemDefinition<MaterialItem>`: material awaiting encoding.
- `CRAFTING_PATTERN` is `ItemDefinition<CraftingPatternItem>`: encoded crafting intent.
- `PROCESSING_PATTERN` is `ItemDefinition<ProcessingPatternItem>`: encoded external processing.

Encoding does not itself execute a craft. Crafting uses the powered network; processing sends
inputs to an external machine. Native generic field signatures are checked without loading
Minecraft classes.

## First Brass alloy

The `mods/tinkers-construct.pw.toml` input contains
`data/tconstruct/recipes/smeltery/alloys/molten_brass.json`: 90 mB molten copper plus
90 mB molten zinc at 605 K produce 180 mB molten brass. The route requires the full Smeltery;
pack removal of Create mixing is owned by `kubejs/server_scripts/progression/30_precision_factory.js`.
Tests compare the native recipe's fluid tags, amounts, and temperature directly.

## Armorer manufacturing

Committed `tacz/*.pw.toml` pins select Applied, Create, and Immersive Armorer archives. Their
39 native gun index identities must remain covered by pack acquisition: 35 same-ID data
recipe overrides plus four authored script routes. Pack recipes retain native result identities
and consume the namespace's manufactured component exactly once. The double-gun recipe keeps
both correctly identified source guns.

The native Applied/ Create muzzle and melee-ammunition indices have display assets but no
native workbench recipes; the pack supplies those routes in
`kubejs/server_scripts/compat/retained/refactor__balance__171_tacz_manufacturing_gates.js`.
Coverage is derived from the current pinned ZIP indices, not a generated catalogue TSV.
