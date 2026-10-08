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
rows, plus two direct native crafting-input return rows, in its **208-row** full matrix,
with sentinel coverage remaining **36**:
`client.foreign_emi_fill`, `client.foreign_emi_refill`, `client.foreign_emi_background`, and
`client.foreign_emi_failed_fill`. They must independently prove beyond-face inputs, refill,
background updates without page jumps, and failed-fill non-revelation/conservation. Their
combined source gate passed 147 JVM tests, runtime-mapping checks, fixture isolation and JAR
staging at source revision `0c3be2023066174a0739a4a692caf74b2e609f5d`.
`client.foreign_grid_return` and `client.foreign_grid_return_blocked` independently require
ordinary native QUICK_MOVE delivery into the hidden final cell or exact blocked conservation.
The bounded input-return extension preserves native IDs10..45 first, then uses only validated
appended cells for any residual. These direct rows do not pretend to execute EMI's client-only
fallback branch. The same pinned EMI server component enables its supported native fill route;
see `docs/native-emi-server-transport.md`.
Runtime acceptance remains pending. Build406 passed202/206, but all four EMI rows exited before
action because the old server package lacked EMI; strict findings were zero and cleanup complete.
Its failed fixture is retained; neither build405 nor406 proves the new208 rows.
Build407 reached205/208 with genuine native server EMI source/return/intent/reveal observations
and passing background/failed-fill/blocked controls. Fill/refill failed the native result oracle
because the fixture's null server level access skipped normal result calculation; the fixture
now uses an ordinary native crafting-table provider and restores its owned block normally.
Direct grid return delivered the exact item but did not reveal it: outbound count dedup now
compares detached authoritative contents plus state, since accepted native prediction need not
increment vanilla state. No state is forced, result oracle waived, or prediction suppressed.
Build407 had zero strict findings and complete cleanup; persistence was not reached. Its
failed fixture remains retained, and the next full208/persistence verdict remains pending.
Build408 ended181/208 before the first foreign action: the ordinary table disappeared and the
menu closed; remaining foreign/native failures cascaded from that missing fixture table.
Fixture restoration failed with two strict cleanup findings, while process cleanup completed.
Persistence was not reached. Retained physics configuration/native falling behavior and saved
terrain strongly support an unsupported-table diagnosis, but no per-placement/fall trace was
captured. Setup now searches reachable empty space atop four existing solid support cells,
rejects trees/liquids/entities/block entities, records selected/current player/support geometry,
and places once through the ordinary provider. No artificial support, relocation, exemption,
re-placement or forced reopening is used. Player restoration precedes the exact table invariant;
a missing/changed table still fails cleanup without overwriting unexpected blocks.
Build409 run `20261008T120014Z-1206958` proved208/208, exact fixture restoration and all six
actual foreign source/result/return/intent/reveal/negative-control oracles. Direct native return
revealed its destination while vanilla state remained1. The upgraded native120/Omega/MAX/filter
checkpoint survived genuine reconnect and a fresh dedicated restart with exact full persisted
UUID/count/NBT/settings/upgrades and two fresh pure Forge129-cell/filter/limit/bound observations.
Its overall suite nevertheless FAILED strict6 from native nonfinite-rotation errors and native
UntamedWilds no-species warnings. Process cleanup completed and both ZIP hashes remained
unchanged; behavior/persistence success does not waive audit failure or qualify full Debug/human
acceptance. Retain its entire failed fixture.
The next source adds only explicitly gated fixture diagnostics: bounded INFO actor/caller evidence
at rejected native rotation entries and loaded-but-both-species-caches-missing native lookup.
Native methods, errors, return values, species registration, contents and audit criteria remain
unchanged. Separate support refmap/config/manifest and exact runtime targets are audited, with
both directions of production/fixture exclusion. This is causal observation, NOT a fault repair;
a nonreproduction cannot establish that build409's native faults were fixed.

Build410 focused run `20261008T130947Z-1647920` passed208/208, restoration, upgraded reconnect
and fresh dedicated restart, unchanged hashes and complete cleanup under the existing log policy.
It did not reproduce the rotation entries/errors. It **did** reproduce species warnings: server
saved-shark loading before login and client creative enumeration during JEI recipe synchronization.
Audit0 is not an absence of those raw warnings or a species repair; no new exemption was added.

Build411 full Dist `20261008T132332Z-17336311` and Debug server checks passed. Its full Debug
multiplayer run `20261008T132332Z-17336312` independently proved208/208, restoration, upgraded
reconnect/dedicated restart and dimension traversal, then failed three-player startup and aborted
the dependent restart test. Its server was killed with exit137; historical OOM attribution remains
inconclusive. The interrupted metadata has no final audit or certified cleanup and must not be
rewritten as a completed pass. Integrated testing was not reached.

Same411 focused recovery `20261008T153906Z-2347363/target-world-save` failed seed-server readiness
before any integrated client/checkpoint. This separate run completed cleanup with no survivors,
audit0 and suite success=false. Repeated actual JVM thread dumps identified an owned
`BlightLocusBootstrap` same-chunk FULL lookup inside Forge's pre-FULL `ChunkEvent.Load` callback;
this is unrelated to inventory and consistent with Forge's documented loading-deadlock warning.
The contained owned fix defers admission to a native server END tick with nonblocking chunk
availability and transient unload-safe metadata. It preserves natural site selection, valid-ground
checks, persisted claim format and native Malum(2,4,4) event/generator, including native neighbor
access. Its30 unit/source tests and2 repository Forge GameTests passed; these are not the packed
Forge/C2ME runtime regression. This product change requires a new canonical candidate, not a
claim that411 was repaired.

Harness-only processor/Mesa budgets preserve all original heap profiles, mods, rendering,
viewports, timeouts, three simultaneous live players and TPS/oracles. A new fresh fixture may use
the failed world's captured native seed `2345578283189886729` through the existing seed parameter;
never modify/reseed the retained failed world. Native selected-locus claim and subsequent save/
reopen preservation are independent causal evidence; unrelated random-seed readiness is insufficient.
The strict observer reads native typed LONG seed and TAG_List(TAG_Long) claims without writes,
rejects missing/duplicate/lost claims and allows additional naturally claimed sites.

The reopened fixture's process closure is not itself a certified save. An opt-in, exact
nonce/run/player/world-root/request-directory-bound lifecycle signal requests normal native exit
only after actual journal VERIFY and the matching loaded marker/time oracles. The client waits
for actual EMI readiness, captures current native identities, sets its one-shot guard before
`disconnect()/clearLevel()` and reports correlated exit only after return and server-thread
termination. Absent/stale/mismatched/nonregular/symlink/oversized requests remain inert; default
VERIFY and SAVE behavior remain unchanged without the opt-in. This does not force ticks/saves,
change markers or write/repair NBT. Strict post-exit disk checks, fresh reopen and final audits
remain independent; the marker alone cannot waive a native save failure.

Final-candidate full Dist/Debug and audits/hashes/cleanup remain required for automated signoff.
Human pointer/visual playtesting is independent acceptance, not a prerequisite to delivering the
qualified distribution. Historical409 rotation faults remain unidentified.

### Build412 recovery and remaining qualification

Build412 focused captured-seed save/reopen `20261008T173623Z-2876123` passed natural claim
persistence, native120/Omega/MAX/filter/settings/upgrades/UUID/full-NBT, fresh Forge129, genuine
normal exit and post-exit native disk checks, with audit0/unchanged hashes/complete cleanup.
Full Dist `20261008T175946Z-29951231` passed. Full Debug `20261008T175946Z-2995123` failed:
server and complete single-player groups passed, but multiplayer failed journal181/208, Aether
native geometry1003/1024, campaign heartbeat and strict EMI teardown audit. Its normal cleanup
completed with no survivors; dependent campaign restart was aborted. No partial pass waives those
failures. Exact single-player snapshot and native before/after/Forge/filter comparisons passed.

Source-only phase correction `a24000c1` isolates campaign terrain preparation from native join,
journal, Font and traversal tests and gives the seven phases distinct orders. The initial full
fixture previously installed a high one-layer campaign floor before natural crafting checks;
login316 is not the unrecorded coordinate at the actual failing crafting search, so a precise
terrain rejection cause or runtime repair is not claimed. Four new fast guards and all272 Dev
checks passed. Natural support/reach/restoration and all campaign/TPS/resource oracles stay intact.

The Aether sampler was correct: all nine saved FULL chunks exactly matched the original census,
with963 Aether blocks plus40 Dynamic Trees Aether blocks;619 copper and one return seal remained
excluded. Authorized Fonts source `28faa27bdddcd0e98f4d4fff65033c5f49c27042` rejects sparse NEW
first-arrival candidates within the existing search radius. It requires1024 actual Aether-namespace
blocks in the same nine centered chunks after projecting the unchanged return court writes,
before placement. No pad counts, terrain repair, enlarged sampling, rescue seed or reduced
threshold is used. Search-local counts are freshly rechecked before acceptance; cached arrival
sites and other dimensions remain unchanged. A rejected new Aether entry spends no opening
entry charge or player binding and cannot clear natural mobs at an unarrived site; ordinary
preparation/activation/rollback lifecycle writes remain. Local84 JVM tests (including21 new
regressions),12 negative evidence guards, all50 executed Forge GameTests, real Mixin mapping
regeneration and runtime JAR staging passed. First staging failure from missing incremental
Java annotation-processor output remains retained; no mapping file was fabricated. These local
Forge47.4.13 results are not packed Forge47.4.22/C2ME qualification.

All three campaign clients timed out before cleanup during severe host memory pressure; this is
not proven OOM attribution or direct bee NBT mutation. JEI runtime disappearance during EMI
background work caused comparator/index diagnostics; vanilla recipe deletion is not established.
The human restarted the host;20:54 available RAM46GiB/free swap24GiB improves headroom but is
not a runtime pass. The new canonical candidate still needs focused Font and complete unchanged
Dist/Debug evidence. Build412's separately sealed ZIPs remain exact; original411 physical ZIP
retention was missed before packager cleanup, although its failed worlds/logs/fingerprints remain.

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
save/verify reports. The new shared checkpoint seeds a native netherite120 bag, one legal Omega
upgrade, tagged off-face counts300 and Integer.MAX_VALUE, and an empty NBT-sensitive memory
filter. It retains the real merge-only60+4=64 target, exact native settings/upgrade NBT comparison,
and a separate pure fresh UP/main Forge129-cell observation after reopen. Setup is save-only;
verify neither reseeds nor repairs missing UUID/root/settings data. This stronger checkpoint is
qualified by build412's actual upgraded integrated save/reopen reports, not by the historical
plain-bag checkpoints. A fixture-only namespace classification is added to copied KubeJS policy;
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
