# Native inventory parity and acceptance

## Scope and authority

This is the sole shared implementation/acceptance contract for inventory GUI and compatibility
changes in any Better Content repository. The Journal is one player-facing inventory backed by
two native paths, not two separate features. Detailed Journal mechanics and local commands
belong in [its repository](../../mod_source/better-journal-inventory/README.md).

## Mandatory native paths

Every affected change covers both:

1. **No backpack:** native CuriosScreen/CuriosScreenV2 and CuriosContainer variants, including
   vanilla InventoryScreen/InventoryMenu fallback.
2. **Equipped backpack:** native Sophisticated Backpacks screen/menu, combined equipment and
   menu-local personal crafting component.

Matching artwork does not imply shared crafting menus, callbacks, output slots or packets.
Share integration where practical without replacing native authority. Minecraft owns the 27 main
and nine hotbar slots. Curios, Aether and Sophisticated Backpacks own their native storage and
protocols. Do not reintroduce retired custom storage backends, transfer/prediction engines,
inventory aliases, Sort/Stack/Trash Cursor/Undo, fake installed upgrades, personal NBT/root
initialization or custom EMI storage handlers. Keep fixture commands/worlds out of production JARs.

Polymorph uses its native selector/widget and server selection authority on both paths, not a
decorative icon. Personal EMI defaults to the native personal 2×2 matrix; explicit installed
native crafting-tab selection uses its independent 3×3 matrix. No carried/nested backpack search
or silent root repair. Keep native player/equipment areas and original Record/EMI panels.

## Acceptance before claiming completion

- Audit source and actual packaged artifacts for native authority and absence of retired engines.
- Run the changed repositories' documented local checks and authorized pack checks.
- Inspect real in-game captures of **both no-backpack and equipped states using the same changed
  artifact**. Exercise the affected native behavior on both. For Polymorph this includes an actual
  conflicting recipe, visible selector/choices, and correct server output after each selection.
- Identify artifact hashes and native screen/menu types; cover Curios variants/vanilla fallback,
  or explicitly mark them unverified. Review representative equipped sizes, compact views,
  original panels, reload/disabled/foreign fallbacks when affected by the change.
- Native API verification and actual human pointer verification are distinct verdicts. Automated
  tests must not synthesize pointer/mouse movement/clicks/scroll/resize. A mocked GUI, static check,
  successful client join or equipped-only frame cannot replace paired visual acceptance.

Skipping either native path or its real visual/behavioral verification is an implementation and
acceptance failure for the owning assistant, not completion or inventory-wide partial success.
Without explicit deployment/runtime authorization, perform local checks and state **paired visual
acceptance pending**. This contract does not authorize unsolicited deployment or a distribution.

## Independent runtime gates and reporting

Inventory source/artifact audit does not independently requalify third-party storage semantics.
Do not recreate the removed 27-case custom storage qualification driver, commands, fixtures or
ledger. Keep independent diagnostic mapping/isolation, storage-free world-lifecycle/Blight and
baseline Dist checks in their owning harness; full Debug remains separately authorized.
No source/audit/lifecycle/Dist result substitutes for visual or interaction proof.

During an active task, retain enough captures/logs/hashes for actual review and first-failure
diagnosis; do not relabel a failed candidate as passed or weaken safety assertions for a retry.
At final handoff dispose of worlds, captures, ZIPs and evidence under
[generated-data.md](policies/generated-data.md), regardless of result. This workspace is not a
save backup service. Do not claim recovery of historical item loss or fresh runtime verification
from source-only checks. Current source requirements survive; historical artifact paths do not.

## Related policies

- [Workspace authority](policies/workspace.md)
- [Testing and release authorization](testing.md)
- [Journal-specific acceptance](../../mod_source/better-journal-inventory/docs/inventory-acceptance.md)
