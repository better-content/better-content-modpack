# Deferred features

## Purpose and authority

This is the living inventory of features wanted for Better Content but outside current work scope.
It owns deferred intent, not implemented behavior, a release roadmap or permission to start work.
The pack owner decides additions, priorities and activation; recording a feature here does not
approve its proposed design or authorize implementation, deployment or testing.

Keep entries focused on the player-facing goal, proposed boundaries, unresolved decisions and
conditions for revisiting. When a feature enters explicitly approved work, update its status and
link its owning subject or source repository. Once implemented or declined, remove the deferred
entry after moving any still-relevant guidance to the proper living subject; do not accumulate
historical completion reports here. See the [documentation authority map](README.md),
[workspace policy](policies/workspace.md) and [testing policy](testing.md).

## EMI: recipes with available ingredients first

**Status:** Wanted; deferred and outside current implementation scope.

**Goal:** When viewing recipes in EMI, put recipes for which the player has all required
ingredients and quantities before recipes with missing ingredients. Keep the latter visible.

### Proposed behavior — not yet approved

- Stable ordering within each recipe category: ingredients available first, existing EMI order
  preserved within both groups. Do not mutate global recipe-manager lists.
- Evaluate availability once when opening the view, before pagination, rather than continuously
  moving recipes while the player is browsing.
- Interpret availability as having the inputs for one recipe execution, not necessarily being
  able to craft in the current workstation. Native EMI handlers retain authority over fill and
  crafting operations.
- Use native exposed inventory sources. Do not introduce unopened/nested backpack searches,
  custom storage handlers or a new item-transfer engine.

### Feasibility and boundaries

The pack pin is recorded in [the EMI manifest](../mods/emi.pw.toml). Initial source inspection
of EMI `1.1.24+1.20.1` found usable ingredient/quantity checks in
[`EmiPlayerInventory.canCraft`](https://github.com/emilyploszaj/emi/blob/23d251ea8ea3a5fd7d760948f36014b185eac69f/xplat/src/main/java/dev/emi/emi/api/recipe/EmiPlayerInventory.java)
and per-category lists before pagination in
[`RecipeScreen`](https://github.com/emilyploszaj/emi/blob/23d251ea8ea3a5fd7d760948f36014b185eac69f/xplat/src/main/java/dev/emi/emi/screen/RecipeScreen.java).
A narrow client-side mixin appears feasible without an EMI fork or server-side crafting changes.
This is a source-based feasibility assessment, not runtime verification; recheck against the
actual pin when implementation is authorized.

EMI's existing matcher is greedy: overlapping ingredient alternatives can produce false negatives.
Reusing native checks does not promise a new exact ingredient-allocation solver. Ordinary input
availability also does not establish workstation, catalyst or other process requirements.

### Decisions before activation

- Confirm ingredients-available ordering versus stricter "craftable in this menu" ordering.
- Decide whether category tabs should also be promoted; within-category ordering is the proposed
  minimum scope.
- Resolve EMI's preferred-recipe focus behavior so opening the view does not silently land past
  the promoted recipes, while explicit requests to view a particular recipe remain meaningful.
- Choose the owning client integration repository and settle inventory-source semantics for
  personal inventory, crafting menus and separately opened native backpacks.

Any implementation must follow [native inventory parity and acceptance](inventory-parity.md),
including both personal-inventory paths and relevant separate-backpack behavior. Activation
requires explicit scope approval; runtime acceptance remains separately authorized under the
shared testing policy.
