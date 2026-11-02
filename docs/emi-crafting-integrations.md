# EMI crafting integrations

## Ownership

- `better-compat-fixes`: `compat/emi/CauldronEmiPlugin`, `AmendmentsCauldronDisplays`, `CauldronFluidStack`.
- `better-spicy-farmers`: `integration/emi/SeasoningEmiPlugin`.
- `better-ore-geology`: `compat/emi/GeologyEmiPlugin`.
- Pack: `kubejs/client_scripts/policy/hide_excavated_variants.js` keeps the legacy hide policy for JEI-only clients. With EMI, the geology plugin removes hosted variants from the index **without disabling recipe ingredients**.

All plugins use the pinned EMI 1.1.24 Forge API. EMI is compile-only and the Forge `@EmiEntrypoint` is the client discovery boundary. No gameplay recipe, output, dose, or crafting permission is changed.

## Cauldron crafting

The Amendments 2.2.5 integration enumerates candidates from loaded ordinary crafting recipes with fluid-container ingredients and loaded `amendments:cauldron_crafting` recipes. It passes copied inputs and copied fluid reserves through `CauldronRecipeUtils.craft`, and displays only successful evaluations. It includes native single-item, split-stack, and surround-eight paths, cold/boiling requirements, actual consumed quantities, and the remaining fluid state.

Moonlight soft-fluid counts are not Forge mB. A serializable custom EMI stack displays the native cauldron fluid state, retaining its NBT. Its filled-container icon is explicitly a preview, not a claim that the vessel itself is consumed or produced. Filled vessels and equivalent Forge fluids provide usage lookup catalysts. The output fluid stays in the cauldron.

- Registration respects the enhanced-cauldron and hand-crafting switches. In the pinned upstream, the thrown-item path also checks the hand-crafting switch; the integration follows that actual implementation rather than inventing different behavior.
- Candidate expansions are bounded at 16,384 combinations per seed. Truncation emits a warning identifying the source recipe; this is not a promise of exhaustive coverage of arbitrary addon combinations.
- These are one-batch state-transition examples. Right-clicking natively may execute multiple batches when enough ingredients/fluid remain.
- No transfer handler or recipe-tree support is advertised for this in-world operation.
- This integration covers **crafting**, not every cauldron behavior. Arbitrary RGB dye blending, potion-effect mixing, entity dyeing and unrelated vanilla cauldron interactions are not enumerated.

## Saucing

The crafting-grid category obtains the loaded `SeasoningRecipe`, calls its matcher/assembler, and displays its actual bottle remainder. The ingredient cycles through the five supported 50–250 mB bottle states; the last dose returns glass. Removing the recipe removes these crafting examples.

The separate Create-spout category calls `SpoutSeasoning.fill` with a copied food and 50 mB of the actual sauce fluid. It is registered only when Create and the spout exist. Each accepted default food/placed-food item gets examples for the registered named sauces.

These are **first-dose examples**, not an enumeration of every possible mixed-sauce or food-NBT combination. The panel explains the 50 mB increment and 250 mB per-food capacity. Existing food data and earlier seasoning remain owned by the native operation. No autofill or recipe-tree support is claimed for these stateful examples.

## Hosted geology

For each registered, supported Excavated Variants block, the plugin evaluates the loaded separation and reassembly recipes independently. It does not infer ore families from item names. Separation displays both the chunk and the actual host-rock remainder, including that remainder in output lookup. Both routes appear in EMI's ordinary crafting category and retain their backing recipes.

Removing either native recipe removes that direction's displays. The other direction remains independent. Circular automatic recipe trees are disabled. Hosted variants stay out of the item index but remain available to recipe slots and R/U lookup.

## Verification

Automated checks cover combination expansion/budget reporting, optional-mod boundaries, native-evaluator ownership, serializer/recipe registration, dose constants, remainder wiring, and index-only filtering. These source/JVM checks are not a rendered-client acceptance test.

Client acceptance still requires the actual pack:

1. On a fresh join, verify the three categories/routes register without EMI errors; repeat after recipe reload.
2. Compare displayed cauldron batches against native crafting, including insufficient reserve, cold versus boiling water, surround-eight inputs, and fluid-only output. Verify vessel icons are not treated as actual bottle outputs.
3. Look up a food and each sauce. Compare crafting-grid and spout results, 50 mB consumption, 50 mB bottle -> glass, partial-bottle remainders, existing mixed seasoning, and rejection at 250 mB.
4. Hold a hosted ore block and use R/U. Verify both recipes, host-rock remainder, 2x2 crafting, and absence of hundreds of hosted blocks from the index.
5. Remove each native dynamic recipe via a disposable datapack and reload; ensure no stale examples survive.
6. Exercise optional absence (EMI, Amendments, Create, Excavated Variants) and the cauldron crafting config switch. Confirm no optional client class is loaded on a dedicated server.
