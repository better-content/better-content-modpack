# Native inventory qualification

The backpack-backed inventory overhaul is retired, not disabled behind a toggle. Minecraft owns nine hotbar and27 main slots with or without an equipped backpack. Sophisticated Backpacks owns its separate contents, UUID, settings, upgrades, filters, native controls and persistence. Curios/Aether use the existing twelve-slot policy and native Curios menu/protocol. The journal only supplies shared client presentation and character records; custom Sort, Stack, Trash Cursor and Undo are removed.

**Implementation/qualification is in progress. No replacement candidate is certified yet.** Historical build413 is superseded/unsafe for important worlds. Its Dist pass missed the actual no-backpack client crafting path reported by the user; the loss mechanism was not established. Never describe the historical36/208-row reports as proof of the replacement.

## Independent gates

| Gate | Evidence | Does not establish |
| --- | --- | --- |
| Source/Dev | Palette/boundary tests; isolated support build; strict schemas, candidate exclusion and lifecycle guards | Runtime interactions or visible rendering |
| Actual native client | Normal inventory key, game-mode clicks/recipe requests and default EMI fill; observed native inbound packets | Human pointer/hit-test or pixels |
| Accounting/synchronization | Separate server location ledger, recipe input/output/remainder deltas, stored-output checks, exact stable client/server snapshots, close/reopen | Other worlds/candidates or recovery of lost items |
| Persistence | Genuine disconnect/reconnect and graceful dedicated restart; integrated save/reopen and genuine opt-in exit with strict disk checks | A lifecycle not exercised or save migration |
| Human visual/pointer | Real gestures and rendering on the exact candidate | Untested surfaces, resolutions or controls |

Automated success never substitutes for independent human visual/pointer acceptance. Full Debug is explicitly deferred for this user-approved retirement delivery; focused native qualification and complete Dist on the exact unchanged ZIP pair are required.

## Isolated native support

`better-native-inventory-test-support.jar` is built separately in the journal source repository. It is injected only into disposable client/server fixtures and classified only in copied KubeJS policy, retaining blocking unknown-namespace enforcement. Both this supplement and retired support are forbidden in release archives, including renamed JARs/classes. Production contains neither fixture commands nor the old menu, slots, capabilities, packets, prediction or EMI handlers.

Commands:

- `nativeinventorycontract PLAYER RUN sentinel|full`
- `nativeinventorycheckpoint PLAYER RUN save|verify`

Exact player/run JVM bindings and absolute evidence roots are required. Destructive runs/checkpoint saves cannot be retried for luck. Expected IDs/layers, candidate hashes and support hash are fixture-derived, not accepted from reports. Strict duplicate-key/trailing-data handling, identity/count/completion/restoration checks remain mandatory. The active schemas are `bc.native_inventory.manifest.v1`, `bc.native_inventory.contract.v1` and `bc.native_inventory.checkpoint.v1`; old schemas cannot pass. Some generic Kotlin lifecycle helper/type names retain their historical Journal spelling, but contain only the new native schemas and supplement paths.

## Ordered client coverage

The27-row manifest begins with no-backpack Survival2×2 repeated plank QUICK_MOVE. Before any equipped-backpack test it covers empty and merging destinations, full hotbar with main space, full main with hotbar space, exact one-output capacity, completely full destinations, and a honey-bottle remainder recipe. Every row requires normal close/reopen/close conservation.

The same native states run on an ordinary3×3 crafting table. Further rows cover default EMI and recipe-book fill on native player/table menus; native chest/furnace and a modded Tinkers crafting station; pickup/split/merge/number-swap/drag/collect through game-mode dispatch; independent player/backpack storage and normal native backpack opening; native Curios policy/equipment with an Aether gloves effect; and presentation opt-out/resource reload without native slot/data changes.

All27 client rows are also Dist obligations; Dist cannot omit the basic no-backpack gate. Rendering/pointer coverage and native mouse-only upgrade/filter/control interaction remain manual. Native transport observers are fixture-only, informational and noncancelling; no dependency bytecode, native decisions or warning policy is modified.

Accounting counts physical player inventory, crafting/container storage, cursor, Curios and newly created fixture drops once, excluding computed result previews. Native crafts require exact input/output/remainder transformations and output in storage rather than merely dropped. Conservation alone cannot certify crafting. Client snapshots must match the independently observed server state after ordinary synchronization; no forced resend, state repair, manual cursor correction, screen replacement, synthetic pointer/scroll/resize or GUI-scale change is permitted.

Natural crafting support/reach requirements remain strict: reachable empty space over four existing solid support cells, no artificial floor or relocation. A failed precondition stops the driver and explicitly aborts dependent rows. Fixture-owned blocks, player inventory/Curios and game mode are restored; restoration failure independently fails the report. Campaign terrain preparation remains isolated after native fixtures, Fonts and dimension sweep.

## Native persistence and unrelated safeguards

Save-only setup uses a fresh native netherite120 fixture bag, legal Omega, counts300 and Integer.MAX_VALUE, an empty NBT-sensitive memory filter, native settings/upgrade metadata and a native60+4 existing-stack merge. A separate vanilla checkpoint craft is server-semantic only, not the client-path gate. Verification requires the existing typed UUID/root BEFORE handler getters and compares complete serialized stack NBT (including ForgeCaps), all physical player cells, worn/cosmetic Curios, native bag cells/settings/upgrades and identity. It never reseeds, initializes, repairs, synchronizes or saves.

Fresh Forge UP/main getters must expose the ordinary36 player cells with ordinary limits, independently of the native120-cell backpack. Native Omega/filter observations stay on the native bag handler, not an invented Forge backpack facade. Full snapshots/native owner roots must remain unchanged by getters.

Blight's nonblocking native-load safeguard, strict typed seed/claim preservation, actual opt-in verify exit, post-exit disk oracle, diagnostics, Font-only/direct-travel restrictions, scoped Aether first-arrival eligibility, campaign/TPS/resource budgets, candidate hashes, log audits and process cleanup remain independent mandatory gates. No forced save/tick, migration, NBT repair, reseeding, warning exemption or OOM attribution without temporal evidence is authorized.

## Running and delivery

- Journal source: `./gradlew verifyFull stageRuntimeJar`.
- Pack source: `./test.main.kts dev`.
- Authorized fresh candidate: `./release.main.kts --target native-inventory` (packages exactly once).
- Existing immutable candidate: `./test.main.kts debug --target native-inventory`; integrated safeguard: `./test.main.kts debug --target world-save`.
- Complete Dist on the unchanged prepared pair: `BC_RELEASE_PREPARED=1 ./test.main.kts dist`.

A focused run is not complete Dist. Never rebuild/repackage between qualification and delivery. Preserve first useful failures and retained fixtures before considering another candidate. Hand off exact ZIP/provenance hashes, run IDs, results, cleanup and manual acceptance scope. Full Debug remains deferred, not passed.

## Historical reference and world safety

The complete pre-retirement source/history/JAR/support/harness documentation is sealed at `/home/dev/workspace_artifacts/reviews/inventory-overhaul-retired-20261008/`, including `source/pack-harness/docs/inventory-parity.md`. Historical398–413 evidence and failed worlds remain retained. The archive is never a build input. Original411 ZIP retention was missed; do not reconstruct originals or upgrade its verdict.

Do not access the affected user world for qualification, migrate/repair native saves, initialize existing UUID/content roots or claim recovered items. Back up important worlds before upgrading; use a fresh Java17 instance for the delivered candidate. Move existing items only through normal native gameplay. Do not downgrade after saving with the replacement.
