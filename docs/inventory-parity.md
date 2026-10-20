# Native inventory parity and acceptance

## Scope and authority

This is the sole shared implementation/acceptance contract for inventory GUI and compatibility
changes in any Better Content repository. The Journal decorates native personal inventory;
Sophisticated Backpacks opens separately and is not combined with it. Detailed Journal mechanics
and local commands belong in [its repository](../../mod_source/better-journal-inventory/README.md).

## Mandatory native paths

Every affected change covers both:

1. **No backpack:** native CuriosScreen/CuriosScreenV2 and CuriosContainer variants, including
   vanilla InventoryScreen/InventoryMenu fallback.
2. **Equipped backpack:** the same vanilla/Curios personal inventory still opens for ordinary
   inventory access, with native equipment and personal crafting. The backpack opens separately
   through Sophisticated's native controls, without Journal side panels, appended equipment/model
   or an injected personal crafting component.

Separate native screens retain independent crafting menus, callbacks, output slots and packets.
Share integration where practical without replacing native authority. Minecraft owns the 27 main
and nine hotbar slots. Curios, Aether and Sophisticated Backpacks own their native storage and
protocols. Do not reintroduce retired custom storage backends, transfer/prediction engines,
inventory aliases, Sort/Stack/Trash Cursor/Undo, fake installed upgrades, personal NBT/root
initialization or custom EMI storage handlers. Keep fixture commands/worlds out of production JARs.

Polymorph uses its native selector/widget and server selection authority on both paths, not a
decorative icon. Personal EMI uses vanilla/Curios' native personal 2×2 matrix;
separate backpack installed crafting upgrades retain their independent native 3×3 matrix and EMI
integration. No carried/nested backpack search or silent root repair. Keep native player/equipment
areas and original Record/EMI panels on personal inventory. Do not redirect ordinary inventory to
a backpack, append/lock/move its equipment slots, reserve combined-screen space, inject crafting
descriptors or intercept native backpack opening/transfer/tab-selection protocols.

## Native controls and presentation defaults

Tracked client defaults bind Quark's **Sort Player Inventory** / **Sort Container** and
Sophisticated Core's **Sort** to middle mouse. Better Compat Fixes selects one existing Quark
callback using the hovered native slot, and leaves separate Sophisticated screens to their native
sort handler; it does not implement sorting/storage itself. The obsolete Inventory Sorter binding
is removed. Existing installations retain their saved options: set those three controls to middle
mouse manually if needed; do not overwrite personal options automatically.

Ordinary EMI defaults reserve three native Favorites rows alongside Index, without a duplicate
Favorites page (pinned EMI removes duplicate subpanel types). Journal retains its fitted concurrent
layout. A presentation-only native-page fallback preserves Favorites access when native capacities
cannot coexist, and restores the ordinary subpanel on screen/geometry changes. Existing EMI
preferences can be configured through EMI's sidebar settings; no custom grid or backpack Journal
decoration is introduced. Mend/Cancel and progress/status use the shared server-authoritative
Death's Door treatment view on both personal-inventory paths. Journal lists active maims beside
Mend (stacked in narrow Record), with a separate server-derived combined-injury penalty summary
and shared Record scrolling. Essential Journal active-effect rows above the native center replace
the suppressed native inventory effects panel, with compact icons/overflow hover when height is
limited and an explicit below-fit notice when icons cannot fit. Native effect visibility, duration,
HUD state and separate backpack/foreign presentation remain native-owned. Record STANCES is the
sole style entry with Journal; Compat's central style button remains available without Journal.
These are source contracts, not evidence of deployed artifacts or paired visual acceptance.

## Current XP and exclusive backpack equipment (source contract)

Journal's Record reads the local player's native current XP level and next-level progress.
It does not introduce XP state or packets. Unspent attribute points and **Life peak** are
separate labels; peak is not current level. Wide/narrow offsets and scrolling share the
same measured XP header budget. Both personal-inventory paths retain this presentation;
separate native backpack screens remain undecorated.

Player-worn Sophisticated backpacks belong only in functional Curios `better_backpack[0]`.
All six installed tiers remain valid there. Better Compat Fixes enforces final native
Curios/armor admission, including competing validation listeners and normal quick/dispenser
paths; it does not replace storage, transfer, opening or item initialization. The dedicated
slot is reserved for backpacks; other back accessories retain their own native slots.
Carried/held/placed bags and backpack-wearing non-player mobs are unaffected. Existing
misplaced bags remain removable without automatic migration, content repair or deletion,
with a concise login warning naming the Backpack slot. Direct operator equipment-authoring
commands remain outside ordinary placement admission.

These describe owning source changes, not proof that the bundled pack JARs were deployed.
Optional-mod/version admission, actual menu identities, paired same-artifact captures and
automated native-behavior, LLM visual-review and human-interaction verdicts must still be reported independently.

## Acceptance before claiming completion

- Audit source and actual packaged artifacts for native authority and absence of retired engines.
- Run the changed repositories' documented local checks and authorized pack checks.
- Inspect real in-game captures of **both no-backpack and equipped states using the same changed
  artifact**. Exercise the affected native behavior on both. For Polymorph this includes an actual
  conflicting recipe, visible selector/choices, and correct server output after each selection.
- Identify artifact hashes and native screen/menu types; cover Curios variants/vanilla fallback,
  or explicitly mark them unverified. Review ordinary inventory with an equipped bag and separate
  native backpacks of representative sizes, narrow views, original personal-inventory panels and
  reload/disabled/foreign fallbacks when affected by the change.
- Automated production acceptance requires native behavior verification **and LLM inspection of
  the actual retained native framebuffer images** for both paths on the same candidate. Review
  readable labels, item counts, ordering, clipping, overlaps, native controls, unchanged personal
  panels and separate-backpack boundaries against the scenario's explicit visual criteria.
  Bind each finding to the image path/hash, native screen/menu identity, dimensions/GUI scale
  and production/support hashes. Missing, ambiguous or defective images fail visual acceptance;
  image existence, filenames, counters and model-generated previews are not review.
- Native API/action verification, LLM visual review and actual human pointer verification are
  distinct verdicts. Human pointer verification is not a blocking production gate; report it as
  `NOT_PERFORMED` unless actually exercised. Physical listening likewise remains `NOT_PERFORMED`
  unless performed, without turning mixer activity into a listening claim. Automated tests must
  not synthesize pointer/mouse movement/clicks/scroll/resize. A mocked GUI, static check,
  successful client join or equipped-only frame cannot replace paired visual acceptance.

Skipping either native path or its real visual/behavioral verification is an implementation and
acceptance failure for the owning assistant, not completion or inventory-wide partial success.
The automated acceptance change does not weaken native protocol, behavior, parity or image-review
requirements, and does not claim equivalence to human interaction. Without explicit deployment/runtime
authorization, perform local checks and state **paired visual acceptance pending**. This contract
does not authorize unsolicited deployment or a distribution.

## Independent runtime gates and reporting

Inventory source/artifact audit does not independently requalify third-party storage semantics.
Do not recreate the removed 27-case custom storage qualification driver, commands, fixtures or
ledger. Keep independent diagnostic mapping/isolation, storage-free world-lifecycle/Blight and
baseline Dist checks in their owning harness; full Debug remains separately authorized.
No source/audit/lifecycle/Dist result substitutes for the required native behavior and LLM visual
proof. Human-pointer evidence remains separately labeled and is not a required automated verdict.

During an active task, retain enough captures/logs/hashes for actual review and first-failure
diagnosis; do not relabel a failed candidate as passed or weaken safety assertions for a retry.
Retain worlds, captures, ZIPs and evidence across handoffs by default under
[generated-data.md](policies/generated-data.md), regardless of result. Finish closes activity
and deletes nothing; purge is separately gated and protects the last known good build and latest
candidate with their support/evidence. Retained history is not fresh acceptance. Do not claim
recovery of historical item loss or fresh runtime verification from source-only checks.

## Related policies

- [Workspace authority](policies/workspace.md)
- [Testing and release authorization](testing.md)
- [Journal-specific acceptance](../../mod_source/better-journal-inventory/docs/inventory-acceptance.md)
