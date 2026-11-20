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

**2026-11-18 source transition — not shipped or fully native-qualified:** the approved bottle-sauce replacement removes sauce fluids and the Create-spout route. Filled bottles carry captured composition and 1–5 remaining splashes as item NBT, with no fluid capability. One splash is internally 50 mB-equivalent; food retains its 250 mB-equivalent capacity. Empty bottles return vanilla glass. The intended production routes are the heated manual kettle and Create-powered heated maker, with a Create-powered item-fed saucer for application.

The inspected source crafting-grid category obtains the loaded `SeasoningRecipe` and calls its matcher/assembler for first-dose examples using full bottles and named composition templates. These are not exhaustive mixture/food-NBT combinations. Removing the recipe removes these crafting examples. Existing food data and earlier seasoning remain owned by the native operation; no autofill or recipe-tree support is claimed.

**Open presentation gap at this checkpoint:** `sauce_making` is registered without process examples; partial/glass remainders, 1–5-dose bottle examples, supported placed-food coverage and automated-workstation guidance are not yet complete in the inspected EMI implementation. These are required follow-up checks, not working features inferred from registration. The previous fluid/spout description applies only to the earlier implementation, not this source candidate.

## Hosted geology

For each registered, supported Excavated Variants block, the plugin evaluates the loaded separation and reassembly recipes independently. It does not infer ore families from item names. Separation displays both the chunk and the actual host-rock remainder, including that remainder in output lookup. Both routes appear in EMI's ordinary crafting category and retain their backing recipes.

Removing either native recipe removes that direction's displays. The other direction remains independent. Circular automatic recipe trees are disabled. Hosted variants stay out of the item index but remain available to recipe slots and R/U lookup.

## Verification

Automated checks cover combination expansion/budget reporting, optional-mod boundaries, native-evaluator ownership, serializer/recipe registration, dose constants, remainder wiring, and index-only filtering. These source/JVM checks are not a rendered-client acceptance test.

Client acceptance still requires the actual pack:

1. On a fresh join, verify the three categories/routes register without EMI errors; repeat after recipe reload.
2. Compare displayed cauldron batches against native crafting, including insufficient reserve, cold versus boiling water, surround-eight inputs, and fluid-only output. Verify vessel icons are not treated as actual bottle outputs.
3. On an accepted bottle-system candidate, look up sauce production and ordinary/supported placed foods. Verify actual ingredient/water/glass inputs, four-bottle batch output, manual/automatic requirements, crafting-grid and saucer guidance, one-splash consumption, last-splash glass return, partial-bottle remainders, existing mixed seasoning and the five-splash capacity. Confirm no retired sauce-fluid/spout examples remain. The open presentation gaps above must be resolved before claiming this check passes.
4. Hold a hosted ore block and use R/U. Verify both recipes, host-rock remainder, 2x2 crafting, and absence of hundreds of hosted blocks from the index.
5. Remove each native dynamic recipe via a disposable datapack and reload; ensure no stale examples survive.
6. Exercise optional absence (EMI, Amendments, Create, Excavated Variants) and the cauldron crafting config switch. Confirm no optional client class is loaded on a dedicated server.
