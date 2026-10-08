# Journal inventory contract

The equipped backpack is the player's storage of record: hotbar 0–8, bag face at inventory
9–35, full native bag capacity through the journal's growing, paginated real-slot block (no storage scrolling). Interaction
uses real vanilla slots, but routing, ownership, synchronization, persistence and presentation
are separate contracts. “The result slot emptied” is not successful transfer acceptance.

## Independent gates

| Gate | Evidence | Does not establish |
| --- | --- | --- |
| JVM / Dev | Accounting oracle, routing/face rules, mixin registration, JSON/report/coverage validation and helper exclusion | Minecraft interaction or rendering |
| Server semantics | Actual menu code with known loaded recipes; exact input/output/remainder deltas, destination and lifecycle assertions | Client dispatch or visible pixels |
| Client synchronization | Normal inventory-key opening and game-mode click/recipe dispatch; correlated server/client snapshots after network synchronization | Pointer hover targeting or pixel correctness |
| Persistence | Dedicated disconnect/reconnect and graceful restart, integrated save/reopen; merged crafted output, saved bag UUID, counts and complete stack NBT | Any lifecycle or candidate not actually exercised |
| Human visual/pointer | Actual gestures on exact candidate, native screenshots plus before/after state | Any untested surface/fixture combination |

Never summarize server parity alone as complete inventory acceptance. Manual results remain
`pending` unless a human actually executes the acceptance matrix.

## Fixture-only machinery

The old production `/journalparity` destructive first-online-player command is removed.
`better-journal-test-support.jar` is built separately by the journal repository and injected
ONLY into isolated server/client fixtures. It contains no replacements for production menu,
UI or mixin classes; production and release archives exclude test machinery. Candidate hashes
and support hashes are recorded separately and asserted unchanged.

`journalcontract PLAYER RUN sentinel|full` requires exact fixture player/run JVM identities,
rejects duplicate runs, isolates scenarios, and emits `bc.journal.contract.v1` JSON. The harness
requires exact manifest IDs/layers, unique rows, valid counts, identity hashes, completion and
successful cleanup; missing/malformed/stale/duplicate/pending results cannot pass. Readiness
may be retried, destructive scenario commands may not. Reports and bridge evidence are retained.

Accounting counts each physical location once, distinguishes full NBT stack identity, excludes
computed result previews, and includes scenario drops. Ordinary moves conserve items; known
crafts assert recipe transformations and container remainders. An unchanged log→plank ledger
is not a valid crafting oracle. Output must reach storage, not merely exist as a dropped entity.

## Coverage

Dist runs a bounded sentinel matrix: no bag, basic/diamond bag, canvas/planks/honey crafting,
matching/empty/full and limited destinations, shared movements, and client-path assertions.
Debug expands deterministic crafting across all supported tiers and states and adds dedicated
persistence. The manifest inside the hashed support JAR is authoritative for automated rows.

Shared rows cover split/place/merge, NBT distinction, number swaps, throw accounting, left/right
quick-craft, double-click collect, overflow, live bag replacement and chest/crafting/furnace
host menus. Client rows cover inventory opening, canvas result shift, reopen, number swap,
actual registered EMI handler fill, recipe-book requests from beyond the 27-cell face, and
replacement synchronization. Client stability must be accompanied by authoritative comparison;
registration markers alone do not prove fill behavior. Journal EMI acceptance does not by itself
prove foreign crafting-screen access beyond the first 27 bag cells. The pinned native EMI
crafting handler's bounded source range cannot establish that additional coverage.

### Proven candidate and pending completion

Build405 focused run `20261008T090510Z-97343` passed the existing **202/202** full
manifest rows. Initial merged-output preservation, actual client disconnect/reconnect, and
fresh dedicated-server restart passed full-count/NBT/capability checks. The final strict audit
reported zero log findings, suite success, and complete cleanup with no surviving PIDs.
Retained evidence: `generated/test-evidence/20261008T090510Z-97343/target-journal-inventory/`.
Its candidate hashes were:

- client: `cf5da1fd7e74ee6a063d516868fb701bcc2c8130497f9d51b762a2c3e4bf2a9b`;
- server: `883e2d703b117c2fd3e0b6a72971bdb91d60fb93cbf526814fc2ca3507b6d423`.

This was targeted multiplayer evidence, **not** complete Dist/Debug or integrated-server
save/reopen acceptance. Full Debug's `SingleplayerRuntimeTest.debugFreshWorldBootSaveAndReopen`
launches an integrated world in save mode, waits for its journal save checkpoint, and launches
a fresh client using the saved world and same support identity in verify mode. Its strict
journal verify report is required before claiming integrated persistence. Human pixel/pointer
acceptance also remains pending and follows the automated gates.

The canonical source manifest now includes four additional full-only real-client foreign EMI
rows in its **206-row** full matrix, with sentinel coverage remaining **36**:
`client.foreign_emi_fill`, `client.foreign_emi_refill`, `client.foreign_emi_background`, and
`client.foreign_emi_failed_fill`. They must independently prove beyond-face inputs, refill,
background updates without page jumps, and failed-fill non-revelation/conservation. Their
combined source gate passed 120 JVM tests, runtime-mapping checks, fixture isolation and JAR
staging at source revision `7caeeee85885d5cb1cfc4435d359812e5584a141`; pack Dev passed.
Runtime acceptance remains pending; build405 does not prove these new rows.
Runtime report validation continues to derive exact expected IDs/layers/totals from the
hashed support manifest rather than accepting a hardcoded total.

### Actual viewport and page evidence

The build405 `client.pagination_chest` response records a real native `ContainerScreen` /
`ChestMenu`, viewport **1280×720**, GUI dimensions **427×240**, native capacity **120**, and
**three** storage pages. Traversal passed for all 120 cells with an empty layout-limit value.
`client.pagination_compact` passed all 120 journal cells across **two** pages at the same
viewport/GUI dimensions. The earlier `client.geometry_compact` row explicitly used a resize
action, so this evidence does not claim that the entire old matrix avoided resizing.
The next source matrix removes that automated OS resize action and replaces the two old
geometry rows with `client.geometry_native` and `client.geometry_rebind`: actual unchanged
viewport observation and normal server-authored backpack tier rebind, respectively. Wide/compact
layout math remains covered locally; multi-viewport pixel/pointer acceptance is human-only.

The new foreign crafting rows must use the actual current viewport and GUI scale, without
OS-window resize or GUI-scale changes, and must establish multiple pages naturally for the
120-cell bag. Chest coverage is not proof of crafting-screen geometry. The SAME-equipped
native `BackpackScreen` has its own native layout and is not a supplemental Chrome
`pagination_all` target.

Remaining human gates: actual EMI button/R/U hover targeting, cross-surface mouse drags,
Sophisticated upgrade/filter controls, Curios effects, exotic-menu warnings, creative behavior,
death drops, carried-stack layering, and compact/wide pixel alignment. Follow
`mod_source/better-journal-inventory/docs/inventory-acceptance.md` (workspace-relative).
Integrated-server journal-specific checkpoint results remain separate from the existing general
singleplayer world-marker gate and must not be advertised as passed without their strict
save/verify reports. A fixture-only namespace classification is added to copied KubeJS policy;
its original/supplement hashes are recorded and unknown-namespace rejection remains enabled.

## Running

- Journal local gate: `./gradlew verifyFull stageRuntimeJar` in the source repository.
- Pack Dev: `./test.main.kts dev`.
- Existing candidate diagnosis: `./test.main.kts dist --target journal-inventory`;
  `./test.main.kts debug --target journal-inventory --retry-of RUN_ID` for the full focused matrix.
- New candidate for proven fixes: authorized `./release.main.kts --target journal-inventory
  --retry-of RUN_ID`; final release: `./release.main.kts --debug`, complete Dist/Debug on unchanged ZIPs.

Keep failed fixtures/logs and first useful diagnosis. Record process cleanup and all hashes.
Fresh distribution and pack tests still require explicit user authorization.

## Approved foreign EMI adapter boundary

The contained foreign crafting adapter is separately authorized to use supported EMI handler
registration and a pinned-version-guarded **public method in an internal registry**:
`EmiRecipeFiller.getAllHandlers`. The exception permits moving **only the owned adapter** to
the front of the handler list while preserving all other handlers and their relative order.
It is not general authorization to mutate EMI internals. No EMI bytecode mixin, upstream
algorithm edit, dependency upgrade, or log filtering is permitted. The owned adapter and
its priority decision require actual foreign-screen fixture evidence; successful plugin
registration alone is insufficient. This narrow ordering exception does not change the
ordinary game-mode dispatch, readiness, count/NBT synchronization, or contents-authority
contracts.

## Compatibility boundaries

Mods directly reading `Inventory.items` main slots bypass the bag face and see empty stacks.
Native bag capacity, filters and stack limits belong to Sophisticated Backpacks; the journal neither caps nor duplicates them. Server binding descriptors and matching integer-count metadata supplement ordinary vanilla item/NBT synchronization; they are not a custom inventory action/prediction protocol. Stale menu bindings and retained Forge views fail closed. Supported menus preserve host IDs and append only non-aliased storage; off-page slots retain stable IDs but have no hit targets. Unsupported foreign menus stay native;
vanilla hosting is not proof that every mod-specific registry or callback contract is satisfied.
Routing stays independent of the current page. Positive authoritative changes after an explicit action reveal the first affected destination; background changes do not jump pages. Full-capacity access, native upgrades, actual API/capability paths, natural synchronization and merge-only lifecycle persistence require fixture evidence on the candidate. Unintegrated raw-field consumers remain explicit limitations, not universal compatibility claims. Native creative behavior is retained.
