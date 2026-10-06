# Inventory parity contract

The journal inventory is a presentation layer over vanilla storage semantics: the
equipped backpack is the player's inventory (hotbar 0-8, bag face at main indices
9-35, full bag through the journal menu's bag block), and all interaction runs
through vanilla `AbstractContainerMenu` behavior on real slots. This document is
the executable spec for "behaves like a normal inventory".

Coverage keys: **probe** = asserted by `/journalparity` in-game (runs in the
multiplayer suite on every release); **jvm** = asserted by mod unit tests;
**visual** = pointer/pixel rows for the acceptance run (XTest rig or human).

| # | Behavior | Expected | Coverage |
| --- | --- | --- | --- |
| 1 | Journal opens as a normal menu | `E` opens a networked `JournalMenu`; sync/prediction vanilla | probe |
| 2 | Main face mapping | inventory 9-35 shows bag 0-26 on both peers | probe |
| 3 | Pick up (place anywhere) | left/right pickup, place into any bag cell | probe |
| 4 | Split | right-click splits a stack onto the cursor / into a cell | probe |
| 5 | Merge | same-item placement merges up to stack and slot limits | probe |
| 5b | Conservation | no interaction creates or destroys items; movement rows assert per-item totals before/after | probe |
| 5c | Nearest-cell placement | quick-move fills the nearest free cell (visible-first), never a far corner | probe |
| 6 | Left drag (even split) | drag across bag cells distributes evenly | probe |
| 7 | Right drag (one each) | one item per cell | jvm + probe (left variant) |
| 8 | Shift-click bag → hotbar | vanilla quick-move semantics | probe |
| 9 | Shift-click hotbar → bag | vanilla quick-move semantics | probe |
| 10 | Shift-click container → bag window | native host quick-move (fills player face = bag window) | jvm (rules) + visual |
| 11 | Number-key swap | swap hotbar N with any bag cell | probe |
| 12 | Throw | drop-key drops from bag cells | probe |
| 13 | Double-click collect | gathers matching stacks to the cursor | probe |
| 14 | Craft result computes | grid change produces the vanilla result in the result slot | probe (flagship) |
| 15 | Craft take consumes grid | taking the result consumes one per input; remainder items returned | probe |
| 16 | Craft result shift-click | output moves to storage; grid consumed | probe |
| 17 | Recipe book gather | gather sees the full bag (beyond the 27-slot face) | probe |
| 18 | Recipe book consume | `findSlotMatchingUnusedItem` + remove consume bag slots (incl. >27) | probe |
| 19 | Recipe book fill / craft-all | UI click-to-fill and craft-all draw from the bag | visual |
| 20 | Pickup routing | pickup fills hotbar, then bag; overflow returns to the world | probe |
| 21 | Close-return | closing returns craft-grid contents to storage | probe |
| 22 | Bag absent | storage inert; pickup fills hotbar then world | jvm (routing) |
| 23 | Bag swap mid-open | live rebinding; vanilla sync reconciles | visual |
| 24 | Reconnect / resize while holding | vanilla menu state handling | visual |
| 25 | Foreign surfaces | bag window on the player grid + surplus block (27-71), no scrolling; exotic menus stay native with a warning | jvm (classification) + visual |
| 26 | Surplus quick-move | surplus cells shift to hotbar then bag window | jvm (rules) + probe (journal equivalent) |
| 27 | EMI quick fill | fill button gathers from the bag via the registered MenuType handler and writes the grid | probe (registration) + visual |
| 27b | EMI R/U and fill cells | works on bag, craft, armor, curio cells (real slots) | visual |
| 28 | Curios equip effects | `CurioSlot` callbacks fire on equip/unequip | visual |
| 29 | Creative inventory | native creative UI; bag window is the main grid | visual |
| 30 | Death drops | bag item drops with contents (storage lives in the item) | visual |

## Integration shape (the layer beyond behavior)

Parity is three contracts, not one: semantics (hosted vanilla), shape and
affordances (fill order, visible placement, index conventions), and extension
registries (per-mod contracts keyed on identity). The journal must satisfy all
three or tooling silently degrades:

- **EMI recipe fill** dispatches on `MenuType`-registered `EmiRecipeHandler`s
  and only coerces for vanilla `InventoryScreen`. The journal registers a
  `StandardRecipeHandler` for its menu type (`JournalEmiPlugin`, asserted by
  the `journal-emi-handler registered` client-log marker in the harness).
- **Fill order**: quick-move fills the nearest free cell (forward). Vanilla's
  reverse-fill quirk is harmless in a 27-cell grid and hostile in a 72-slot
  bag; this is a deliberate, tested deviation.
- **Viewer/tool checks per surface** (EMI fill, recipe book, curios) remain
  visual rows; any row marked visual is re-verified at the pointer-action
  acceptance run.

## Documented deviations

- Mods reading `player.getInventory().items` field slots 9-35 directly see empty
  stacks; the bag is the storage of record. The ecosystem overwhelmingly uses
  `getItem`/`setItem`/`add`, which are fully face-aware.
- Bag storage beyond 72 slots is capped; beyond-capacity upgrades are ignored.
- The recipe book's own grid-fill UI is vanilla; its gather view covers the full
  bag, while the visible inventory face remains 27 slots (vanilla semantics).

## Running the parity suite

- Mod JVM tests: `./gradlew test` in `mod_source/better-journal-inventory`.
- In-game probe: `journalparity` (permission 2) on a running server; the
  multiplayer pack suite runs it automatically (`inventoryParityMatrixPasses`)
  and asserts `journal-parity-summary {"status":"passed",...}`.
- Visual rows: the pointer-action acceptance run (XTest rig or human) per the
  original acceptance-matrix procedure, screenshots retained with the run.
