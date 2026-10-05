# Dimensional Font ecology and resource links

The four enabled worldgen Fonts share a radial court, a ring of fully oxidized copper, a decorative field with candles and shallow pools, and a dimension-specific block palette. Bumblezone sites additionally form three dense wax and hive clusters. Tree biome sites use stripped logs from the local tree family for their four supports and roof ring. Treeless sites omit both. The structure is selected only in new worlds; existing sites are not migrated.

| Font | Salience aspects | Spirit map sellers | Field vocabulary | Renewable or dimension-linked materials | Pack systems that consume them |
| --- | --- | --- | --- | --- | --- |
| Nether | Impact, Endurance | Infernal, Wicked | Blackstone, basalt, brick, magma, red candles | Wart, blaze powder/rod, magma cream, basalt | Heat Sync Boiler Heater, PneumaticCraft thermopneumatic plant, TCon Scorched Brick, Nether Font Libation |
| Aether | Mobility, Renewal | Aerial, Sacred | Holystone, icestone, aerogel, pale sky stone | Blue berries, swet balls, skyroot leaves/logs, aerogel, blue aercloud | Rotational Compressor, Clockwork air compressor/propeller bearing/gas thruster, jet boots, Aether Font Libation |
| Bumblezone | Work, Tempo, Robustness, Renewal | Earthen, Tempo, Sacred | Beeswax, ancient wax bricks and eyes, brood honeycomb, yellow candles | Pollen puffs, honeycomb, honey, carvable wax | Create mechanical harvester/plough, Airtight Upgrade, Bumblezone Font Libation, existing nursery systems |

Map offers use the saved index of already discovered natural Fonts. Each seller's allowed destinations are the union of Fonts sharing an aspect with its spirit identity. Maps rotate within that set after sale. The index lookup neither loads nor generates chunks. Resident spirit villagers receive a map listing at level two; wandering spirit traders can add a map to their authored catalogues.

## Libation and Pourer

A Create basin mixes one bucket of water with three native ingredients into one bucket of the matching Font Libation. An overhead Font Pourer accepts fluid through Forge fluid capability or a bucket, but the Font itself does not accept pipes. Place the Pourer exactly two blocks above the Font with one air block between them. During an active run, a matching libation pours down visibly and restores 48 charge per millibucket at a bounded rate of one millibucket every 12 ticks. A bucket therefore holds 48,000 charge, about ten extra minutes at the current 80 charge per second drain. The nominal 15,000 charge before entry costs is about three minutes. The Pourer is idle when there is no active run, its fluid does not match the Font, or the air gap is obstructed. It never loads chunks.

## Recipe audit

The new gates attach native, distinguishable resources to the first machine or tier that expresses each dimension's theme. Existing Font-material logistics roots, occult chalk, Aether gas thruster, and Bumblezone nursery routes remain in place. The industrial audit covered the hand workshop, precision factory, thermal/pressure, acid chemistry, electrical control, transport, aerospace, and transition component scripts; the magic and logistics audit covered occult cauldrons and Font-material logistics roots. The hand workshop remains available before dimensional expeditions. The three libations are optional fuel, while the machine ingredients are mandatory progression links.

The pack recipe source is `kubejs/server_scripts/progression/85_dimension_font_ecology.js` plus the focused recipe scripts referenced above. Runtime validation must check registry IDs against the installed Aether, Bumblezone, Rats, Create, PneumaticCraft, Heat Sync, and Clockwork JARs. The worldgen screenshot harness is a separate minimal-mod development runtime; it is not shipped in the pack.
