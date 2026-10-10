# Stone and cobblestone recipe compatibility

## Policy: whitelist rock identity, not generic uses

Stone/cobblestone substitution is **generic by default**. Maintain an explicit whitelist only for products or transformations whose identity is tied to the authored rock. Do not require an inventory of functional recipes to preserve compatibility.

`kubejs/server_scripts/compat/retained/check__75_stone_cobble_tag_compat.js` contains **125 exact protected recipe IDs** in `BC_ROCK_IDENTITY_RECIPES`. It replaces literal `minecraft:stone` and `minecraft:cobblestone` inputs with their shared tags for every recipe **except** those identities:

```js
var genericRockUses = { not: BC_ROCK_IDENTITY_RECIPES }
event.replaceInput(genericRockUses, 'minecraft:stone', '#forge:stone')
event.replaceInput(genericRockUses, 'minecraft:cobblestone', '#forge:cobblestone')
```

The pinned KubeJS 2001.6.5-build.16 `RecipeFilter.of` parses an array of strings as an OR of exact recipe IDs and `not` negates that whole filter. An ID list inside `id` is not used. There are two filtered substitutions, not one full recipe scan per whitelist entry.

## Protected identity products

The whitelist covers the reviewed loaded stone/cobblestone consumers that produce material-specific decoration or transformations:

- vanilla stone/cobblestone slabs, stairs, walls, bricks, and stone-specific buttons/plates;
- stone/cobble smelting, polishing, chiseling, mossing, and rock-family conversions;
- geological reassembly, regolith, and named derived rock blocks;
- material-specific chimneys, windows, pillars, statues, tiles, masonry, and terrain blocks;
- specific material transmutations, finishing, and casting.

For example, cobbled deepslate retains its native deepslate route rather than being captured by vanilla stone smelting; basalt retains its smooth-basalt route rather than being captured by smooth-stone smelting. A geology stone is not automatically an ingredient of vanilla stone slabs.

The whitelist is by **recipe identity**, not a namespace, output-name substring, or broad recipe-type exclusion.

## Generic uses remain supported

Unlisted recipes retain generic raw-rock substitution, including machines, mechanisms, tools, transport fixtures, ritual equipment/reagents, template duplication, generic downcycling, and future functional recipes. Functional products are not protected merely because their names contain `stone`: stone lamps and stone ovens remain generic uses.

In particular, Coast/Sentry/Vex armor-trim template duplication and More Artifacts' upgrade template continue accepting rock variants without special allowlist entries. The same default covers Blood Magic soulforge devices, spell/ritual uses, and generic crushing of rock into gravel. The earlier 66-ID functional allowlist is removed.

The eight-rock furnace recipe still accepts `#kubejs:furnace_materials`. Native stone tools retain `#minecraft:stone_tool_materials`. Tinkers' Construct's `tconstruct:tools/materials/rock/stone` continues accepting `#forge:normal_stone`, and its workstations retain `#tconstruct:workstation_rock` support.

## Boundary and maintenance

Shared item/block tags are unchanged. Native tag ingredients remain native tag ingredients, including in protected recipes: this policy stops pack-authored broadening of literal vanilla stone/cobblestone, not indiscriminate narrowing of mod-authored tags. Other specific geology inputs are untouched.

New generic recipes need **no list entry**. New rock-identity products must be reviewed and added to the protection whitelist where their literal stone/cobblestone inputs would otherwise be broadened. Names are not used as a hidden fallback classifier.

To extend the identity whitelist:

1. Inspect the actual recipe ID, output, and material relationship.
2. Confirm the output identity depends on the particular input rock, rather than using it as generic construction material.
3. Add the exact recipe ID once; preserve the authored native ingredients.
4. Run the contract tests and Dev tier.

Do not restore unconditional `event.replaceInput({}, ...)` calls that bypass the whitelist. Equally, do not restore a functional-recipe allowlist that silently narrows generic uses outside its catalogue.

The pre-fix offline audit found 411 conflicting-output pairs involving `forge:stone`/`forge:cobblestone`. That is a scope indicator, **not a verified count of eliminated clashes**. Independently authored native-tag conflicts may remain. A fresh live dump after an explicitly authorized deployment is needed for new runtime totals. Unrelated leaf, wood-storage, boat, and NBT conflicts are outside this change.

## Validation

`StoneCobbleCompatibilityContractTest` is a Minecraft-free fast test. Its Node fixture executes the actual KubeJS handler against a bounded recipe-event model with the pinned `not`/OR-of-exact-IDs filter semantics. It checks:

- all 125 identity IDs preserve their inputs;
- 38 generic examples accept both shared tags, including former omissions and future recipes;
- similarly named or adjacent IDs are not accidentally protected by a prefix/namespace rule;
- native tags, specific geology ingredients, stone-tool/TCon material/workstation inputs remain unchanged;
- the furnace pattern, material union, and recipe ID remain unchanged;
- exactly two filtered replacements execute;
- no active script adds an unconditional substitution bypass.

Run `./gradlew test --tests com.bettercontent.tests.StoneCobbleCompatibilityContractTest`, then `./test.main.kts dev`. Node is already provided by the lane toolchain; no Minecraft process starts. Deployment, refresh, packaging, Dist, and Debug remain separate explicitly authorized actions.
