# Stone and cobblestone recipe compatibility

## Policy

Stone/cobblestone variants are interchangeable **as functional construction ingredients**, not as the material of the resulting rock block.

`kubejs/server_scripts/compat/retained/check__75_stone_cobble_tag_compat.js` extends literal `minecraft:stone` and `minecraft:cobblestone` inputs only for the **66 exact recipe IDs** in `BC_FUNCTIONAL_ROCK_RECIPES`. The list covers mechanisms, machinery/controls, transport fixtures, functional occult equipment, and stone weapons. It is not a namespace-wide or output-name rule. New recipes default to their native inputs until explicitly reviewed and listed.

The separate eight-rock furnace recipe continues to accept `#kubejs:furnace_materials`. Native stone tools retain `#minecraft:stone_tool_materials` support through the shared variant tags in `check__30_stone_cobble_compat.js`.

Do not restore blanket `event.replaceInput({}, ...)` substitutions for either raw stone or cobblestone.

## Preserve rock identity

Leave the authored material inputs of these recipes alone:

- slabs, stairs, walls, pillars, windows, chimneys, and material-specific decorative blocks;
- bricks, polishing/chiseling, rock-family reassembly, and regolith;
- cobblestone smelting, smooth-stone smelting, mossing, crushing, and other material transformations;
- stone-specific buttons/pressure plates and unreviewed special recipes.

For example, native cobbled-deepslate → deepslate and basalt → smooth-basalt routes must not also be captured by blanket widening of the vanilla stone/smooth-stone recipes. Stone slabs must not accept every geology stone merely because the global stone tag contains it.

## Boundary

The broad shared item/block tags are retained. A mod recipe that **natively authors a tag** still owns that choice; this policy does not rewrite those tag ingredients to vanilla stone or indiscriminately change tag memberships. Material-specific inputs other than vanilla stone/cobblestone are untouched.

This fixes the pack's global substitutions, not every independent native-tag conflict. The pre-fix offline audit found 411 conflicting-output pairs involving `forge:stone`/`forge:cobblestone`; that is a scope indicator, **not a verified count of eliminated clashes**. A fresh live recipe dump after an explicitly authorized deployment is needed to establish the new runtime totals. Unrelated leaf, wood-storage, boat, and NBT conflicts are outside this change.

## Extending the allowlist

1. Inspect the actual recipe ID, output, and inputs.
2. Confirm rock serves a functional role and is not the output's material identity.
3. Check potential input-pattern overlaps before opting the exact ID in.
4. Add the ID once to the explicit list and run the contract tests and Dev tier.

Avoid inferring permission from a mod namespace or a keyword such as `stone`. A new machine or a new decorative recipe should not be widened automatically.

## Validation

`StoneCobbleCompatibilityContractTest` is a Minecraft-free fast test. Its Node fixture executes the actual KubeJS recipe handler against a bounded recipe-event model and checks:

- both raw input substitutions are filtered by an exact reviewed recipe ID;
- all 66 functional IDs retain generic stone/cobblestone substitution;
- 28 material/conversion/future recipe IDs remain untouched;
- native tags, specific geology inputs, and native stone-tool ingredients remain unchanged;
- the existing furnace pattern, material union, and recipe ID remain unchanged;
- any active script restoring a blanket raw-rock rewrite fails the static guard.

Run the focused contract with `./gradlew test --tests com.bettercontent.tests.StoneCobbleCompatibilityContractTest`, then `./test.main.kts dev`. Node is provided by the lane's existing toolchain; no Minecraft process is started. Dist/Debug, deployment, refresh, and packaging remain separate explicitly authorized actions.
