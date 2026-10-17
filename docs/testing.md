# Testing and fresh distributions

## Scope and authorization

This is the sole shared test/release procedure for Better Content. Repository-local source
checks do not authorize cross-repository builds, deployment, Packwiz refresh, packaging or
pack suites. Ordinary changes use the smallest relevant local check. Runtime pack tests and
fresh distributions require explicit user orders, never an inferred risk assessment. State
when pack testing is intentionally omitted. `dist.sh` packages once; it is not validation.
Only release preparation refreshes tracked Packwiz hashes; authoring/deployment steps do not.

Docs-only changes use `python3 -B scripts/check_documentation.py` and `git diff --check` rather
than full custom-mod builds. Source changes retain the owning repository's documented gate.
All worlds/saves/candidates/evidence/caches are task-lifetime data under
[generated-data.md](policies/generated-data.md), not permanent acceptance archives.

## Test input boundary

Tests must never require generated results retained from an earlier run: historical reports,
audit prose, catalogue dumps, screenshots, worlds, or a latest-pass directory are not fixtures
or golden baselines. Required inputs are authored/versioned fixtures, source/configuration,
or reproducibly fetched upstream artifacts verified against committed pins. Declare and prepare
those inputs explicitly so an empty output/cache workspace can run the documented gate.

Tests may inspect outputs created by the current invocation to verify the system under test;
provider/support JARs must likewise be built or explicitly prepared for that invocation. This
is not permission to reuse unverified leftover output. Documentation checks establish authored
contract consistency, not proof of native behavior or runtime acceptance; native-input tests
must inspect the authoritative implementation or data directly.

## Correctness, performance and diagnostic verdicts

Correctness gates block on failed assertions, crashes, registry/script failures, unclassified
warning/error records, startup/join timeouts, candidate-hash changes and incomplete process cleanup.
Existing narrow compatibility classifications remain independently tested; there is no blanket
warning/error exemption and no mod-specific waiver for a failed launch.

Exact native Forge `DeferredWorkQueue` WARN messages reporting elapsed deferred startup work on
`main` or `Render thread` are **performance diagnostics**, not failed operations. The parser requires
the complete timestamp/logger/message, a valid mod namespace, and a positive finite duration in
`ms`, `s`, `min` or `h`. Malformed messages, other loggers/threads, ERROR severity and additional
failure payloads remain correctness findings. This category replaces the old per-mod duration and
occurrence allowances; it is not an `adpother` exception or a raised threshold for one candidate.

The isolated Spirit Commerce source GameTest fixture also distinguishes initial arena setup from
runtime correctness: at most one exact native `MinecraftServer`/`Server thread` startup-lag WARN
may be recorded as a performance diagnostic when its nonce-bound, nonshipping phase observer
proves it occurred in initial world/arena setup before the second server tick. Positive duration
and consistent tick-count fields are required and retained with the source line. Missing or
invalid phase proof, repeated notices, ERROR severity, malformed payloads and later runtime lag
remain failures. This source-fixture classification does not change the full-pack log policy,
assertions, startup/join deadlines or performance acceptance; it is not a claim that the delay
was repaired.

Every runtime suite records `startup_performance_diagnostics`: source paths/lines, normalized seconds,
observation counts and per-mod maxima, including on failed runs. Aggregate/native logs can duplicate
observations, so timings are not summed. These records are diagnostic-only: a correctness pass is
not a claim of acceptable startup performance or representative gameplay performance.

Explicit performance gates remain separate and blocking: existing end-to-end startup/join deadlines,
heartbeats, and Debug's measured TPS requirements are unchanged. Do not turn a timing notice into
a gameplay verdict or declare a performance regression repaired from a successful retry. New
performance budgets require a documented workload/resource profile and measured acceptance criterion,
not a threshold tuned to a retained historical run. No Debug scope is implied by this separation.

## Public tiers

The public test interface has three cumulative tiers. Dev needs no packaged candidate and can
run while other developers share the workspace:

```sh
./test.main.kts dev
```

Dev runs the fast Minecraft-free contracts and `git diff --check`; it does not refresh Packwiz
hashes. Cold-cache source inspection rehydrates only the native JAR/ZIP archives named by its tests through
`scripts/prepare_dev_inputs.py`, verifies each active pin's hash and exercises that rehydration's
synthetic tests. These downloads remain disposable dependencies; no Minecraft launch, deployment
or distribution is performed. Fresh-dist preparation refreshes them once after source and pack changes settle.
Pack-level tiers run only when explicitly requested:

```sh
./test.main.kts dist
./test.main.kts debug
```

Agents can use `test-control.main.kts` for JSON lines with stable run IDs and evidence paths.
`run` prints a running record immediately, then a final record; `status` and `wait` work from
another terminal while it runs. `wait` returns 4 when its bounded timeout expires, 1 for a failed
run, and 0 for a passed run. `evidence` lists recorded files. For example:

```sh
./test-control.main.kts list
./test-control.main.kts plan debug
./test-control.main.kts run source --repo better-regolith-farming
./test-control.main.kts run debug
./test-control.main.kts status RUN_ID
./test-control.main.kts wait RUN_ID --timeout-ms 30000
./test-control.main.kts evidence RUN_ID
./test-control.main.kts retry debug --target font:aether --retry-of RUN_ID --candidate-binding /absolute/path/to/release/provenance.json
```

`run source --all` verifies the active manifest repositories plus the two supported validation-only
repositories in dependency order and stages provider JARs for dependent builds. Retired Ratlantis
is not offered by source control; checkout count is not a build requirement. Full Dist and Debug use the same fresh release path as the
direct tier commands below. `submit --handoff /absolute/path/to/handoff.json` sends a prepared
immutable-candidate request to the shared queue.

Direct full Dist and Debug require clean active custom-mod repositories. The first authorized
preparation in a verification session runs every active mod's verification tasks and clean
runtime rebuild, even when the bundled JAR records the same source revision. Later preparation
rebuilds changed/missing/hash-mismatched providers and their transitive consumers. It stages and
deploys the coherent set, refreshes Packwiz and packages once. Runtime suites still use fresh
fixtures; the receipt never replaces pack acceptance. Release evidence binds tier IDs and hashes.

### Conversation-scoped source verification

Use the coordinator's stable conversation ID (`PI_SESSION_ID` in Pi; explicitly set
`BC_OWNER_SESSION` for a launcher without that identity), never a pane or PID:

```sh
./test-control.main.kts session begin --owner-session "$PI_SESSION_ID"
./test-control.main.kts session status SESSION_ID
./test-control.main.kts session admit SESSION_ID --helper-session HELPER_CONVERSATION_ID
./test-control.main.kts session revoke SESSION_ID --helper-session HELPER_CONVERSATION_ID
./test-control.main.kts session begin --owner-session "$PI_SESSION_ID" --replace IDLE_OWNED_SESSION_ID
./release.main.kts --debug --verification-session SESSION_ID
./test-control.main.kts run source --all --verification-session SESSION_ID
./release.main.kts --debug --verification-session SESSION_ID --rebuild-all
./test-control.main.kts session end SESSION_ID
```

Opening a receipt or ordinary source/Dev checks does not authorize preparation or establish a
baseline. Source-control checks validate ownership but do not advance the release receipt.
Only successful canonical preparation establishes verified deployable artifacts. Without a
session, tested preparation uses a full invocation-scoped baseline and removes its receipt on
completion/failure. `--skip-tests` cannot consume a verified receipt.

The atomic bounded receipt in `.worklane/testing-sessions/` records owner, generation, shared
input fingerprint, per-repository source identity, verified JAR/provider hashes, tasks and latest
successful verification ID. It contains no worlds, logs or historical result catalogue. A failed
or incomplete baseline cannot authorize reuse. Identity includes HEAD, tracked/nonignored files
and status; a later HEAD cannot be attached to a built JAR. Artifact changes after a reuse
decision fail rather than acquiring the previous verification ID. Declared `supportArtifacts`
(e.g. the isolated native diagnostic supplement) are gate-produced, hash-bound inputs too;
missing/corrupt supplements require rebuilding their provider. Changed providers invalidate
consumers. The manifest's `verificationInputs` declare external pack contracts/upstream pins and
shared build inputs; changes conservatively require a full baseline. Embedded source metadata
alone is insufficient. Session status adds an observational live rescan of shared inputs,
source/task identities, production/support bytes and the provider/consumer closure. It does not
advance verification or authorize reuse; preparation rechecks under the receipt lock.

Same conversation identity survives compaction/reconnection. Only explicitly owner-admitted
helper conversation identities can share it; receipt ownership never changes. Different owners are rejected;
uncertain liveness is not permission to adopt a receipt. Explicit end refuses during preparation.
After stopping and confirming complete cleanup of an interrupted owned tree, its owner may use
`session end SESSION_ID --confirm-terminated-run RUN_ID`; this refuses if the coordinator PID
still exists. That confirmation is an explicit cleanup assertion, not inference from PID absence.
End unused sessions explicitly. Admission/revocation and explicit replacement refuse an active
coordinator. End/replacement is owner-only; a helper cannot transfer ownership or revive a receipt.
Uncertain owner liveness remains fail-closed and requires explicit owner reconciliation, never
cross-conversation adoption. Receipts are task-lifetime data and are removed on handoff.
Synthetic receipt tests do not establish completed cold source-build or Debug acceptance.
The two Librarian snapshot users pin the exact timestamped implementation via plugin resolution,
not merely a dated marker that still depends on a floating implementation.

After inspecting a failed run, use a targeted retry to check a fix without starting every Debug
scenario. Each retry makes a fresh fixture and records separate `target-*` evidence; it cannot be
mistaken for a full-tier pass. Supported targets are `server-ready` (Debug), `join` (Dist or Debug),
`font:bumblezone`, `font:aether`, `font:nether`, `fonts`, `dimension:<id>` for a directly
teleportable dimension, `dimensions`, `campaign-start` (three live encounters and platform repair),
`campaign` (three live client encounters), `restart-compat` (saved Flesh spread
restart and Untamed whale client spawn), `lineage-transition` (Debug lifecycle successor commit
and archive), `cursed-pyramid` (pinned seed, saved structure record,
and strict log audit in its surrounding chunks), `world-save` (seeded persistence),
`world-startup` (unseeded native integrated creation), and `deep-void` (networked native entry)
(the last three are Debug). The `campaign-start` fixture
uses the packaged spawn rules and removes a strip of campaign floor to verify that the
campaign-phase repair restores pathable geometry. For example:

```sh
./test.main.kts debug --target font:aether --retry-of RUN_ID --candidate-binding /absolute/path/to/release/provenance.json
./test.main.kts debug --target fonts --candidate-binding /absolute/path/to/release/provenance.json
```

Targeted runs still perform fast checks and candidate validation, then start only the selected
runtime fixture and its required setup. They check candidate hashes, process cleanup, and strict
logs. Findings are recorded in the target evidence and fail that targeted run. If a source fix
needs a new JAR, use `./release.main.kts --target font:aether --retry-of RUN_ID` to package once
and test that target. Direct full Debug later uses the session's affected-source verification
and runs complete Dist and Debug on its new, unchanged ZIP pair.

Full retries also accept `--retry-of RUN_ID --candidate-binding PATH` without a target. The
binding is canonical tested release `release/provenance.json` with exact paths, ZIP SHA-256,
source commits and production/runtime input fingerprint. Mismatches fail rather than silently
selecting another candidate. Bound full Debug runs a fresh Dist prerequisite and complete Debug.
Harness-only edits can retain hashes; production/source changes require canonical preparation
and a new binding. Legacy provenance without runtime/support fingerprints cannot authorize reuse.
Targeted fixtures also require a binding or a coordinator handoff whose `artifacts` explicitly
pin the prepared runtime-support path and SHA-256. A leftover support JAR is not an input merely
because it exists. Prior run IDs establish diagnostic lineage, not a prior-pass waiver.

Dist includes Dev, candidate contracts, a real full-pack client joining a fresh dedicated server,
an online player heartbeat, strict logs, and candidate hashes. It does not run dimension travel.
Direct full Debug uses unchanged candidate hashes from its newly packaged Dist run and adds
dedicated-server startup/runtime snapshot, three
fresh locations per directly teleportable dimension with strict three-sample TPS and Font-only
direct-travel guards, one lineage transition and archive, three live client campaigns,
server restart/client reconnect, four native Font round trips, native networked extended-bottom
Deep Void entry with negative controls, and singleplayer title screen, unseeded native creation,
and separately seeded save/reopen.

Dist, Debug, and `release.main.kts` share one kernel-backed runner mutex. The
supported entry points acquire it automatically and publish current ownership at
`$HOME/.local/share/worklane/pack-tests/owner.json`; contention fails immediately with exit code
75 and that ownership record. Dev and custom-mod repository-local checks do not use this mutex.
Do not invoke the heavyweight Gradle tasks directly: they require the inherited lock token.

Only `workspace_coord` admits work to the persistent queue. It supplies an immutable-candidate
handoff and registers it with:

```sh
./pack-test-queue.main.kts request /absolute/path/to/handoff.json
./pack-test-queue.main.kts status
./pack-test-queue.main.kts run-next
```

Product lanes do not wait inside a runner process when the mutex is owned. They register their
dependency with `workspace_coord`, become idle, and resume when the coordinator or `pack_tests`
notifies the handoff callback. The `bc.pack_test_handoff.v1` document must include explicit user
authorization, request ID and selector; producer and callback agent/pane identity; modpack HEAD
and status; absolute client/server paths and SHA-256 hashes; repository revisions/statuses;
producer validations; artifacts; ordered dependencies; requested scenarios; and prior evidence.
Admission copies the document into the queue, rejects duplicate request IDs, and the harness
revalidates the exact candidate paths and hashes before use.
Queued full Debug runs against that pinned pair and does not rebuild sources or package another
pair.

Short-lived harness commands use file-backed output, bounded execution and bounded diagnostic/
cleanup phases. Owned Linux process groups close the fast-parent-exit race for inherited-group
children, in addition to retained observed descendants. Deliberately detached, never-observed
escapees are not covered by that JVM tracker alone. Public release/test/control/lock/queue
invocations additionally use a Linux subreaper supervisor that retains PID/start identities,
reaps adopted descendants during execution and cleans them on parent loss, signal or deadline.
Its outer cap is eight hours; runtime Gradle groups are capped at four hours and Dev at twenty
minutes. Captured control/Git queries have thirty-second budgets and four-MiB output limits.
Diagnostic/TERM/KILL/reap phases are separately bounded; failed cleanup remains a failure with
retained ownership metadata. These mechanism contracts do not prove arbitrary external daemon
cleanup or Minecraft acceptance. The Node recipe-handler contract retains its 15-second budget;
passing repeated/concurrent observations cannot establish the cause of an earlier timeout.

The tiers run internal Gradle tasks `test`, `candidateTest`, `serverTest`, `multiplayerTest`, and
`singleplayerTest`. Dist sequences fast checks, the candidate gate, and the multiplayer runtime
group. Debug runs the same gates and all three runtime groups to collect diagnostic evidence.
Each runtime group uses a fresh fixture.

Dev writes ordinary Gradle XML and HTML reports only. Dist and Debug use a run ID and write
structured evidence beneath `generated/test-evidence/<run-id>/`: incremental JSON events, a
`bc.modpack_test_run.v1` summary, and candidate hashes. Runtime groups add logs and timeout
diagnostics; Debug also collects runtime data. The single-player group records the customized
title screen without injecting input. Failed fixtures remain available during the active task only.
Automated tests must not synthesize mouse movement or mouse
clicks. Threads reader development, the Quark chat emote picker, the World Condenser configuration
screen, and the Create World menu are manual visual gates. The seeded persistence probe's
fallback marker isolates persistence; it is not production spawn-search acceptance. The separate
unseeded probe creates an absent normal world with a native random seed, observes the production
resolved marker, uses the typed production onboarding action, and requires Survival and forty
correlated native ticks before native disconnect/save. It does not author a spawn marker or test
the Create World GUI. Its profile uses twelve-GiB heap/eight processors/software rendering while
preserving production Distant Horizons generation, renderer, six workers and radius 32.

The native Deep Void probe arms one real Survival network player at the actual extended-bottom
boundary. Highest/lowest native END-tick observations correlate run/nonce/player/tick, exact
config and capability state, source threshold and resulting destination. Above-boundary and
disabled-switch negatives precede entry; entry additionally requires the real client arrival.
The supplement does not invoke the upstream procedure or teleport into the destination. Native
fixtures restore temporary switches and bind supplement/candidate hashes.

These authored scenarios still require a recorded full unchanged-candidate native run; local
contract/source passes are not runtime acceptance. Before rerunning, inspect the existing run and report its
ID, hashes, failed or aborted cases, evidence path, retained fixture, and process cleanup state.
Cleanup retains observed process descendants after their parent exits and checks termination after
graceful and forced shutdown. Every fixture is closed even when an earlier close fails. The
`process_cleanup` event reports `complete=false`, surviving PIDs, and an error when cleanup fails;
the suite then fails and keeps its fixture and original failure evidence until task handoff. `complete=true` means
all tracked processes have exited. Dev tests ordinary, forced, and orphaned-child
shutdown without launching Minecraft.

A runtime snapshot is evidence for a target only when its snapshot ID appears in that run's server
events and the run's `candidate_selected` hashes match the target under discussion. Completeness
makes a snapshot usable evidence; recency alone does not make it current. Do not use unmatched
snapshots or their volatile totals as claims about the tracked pack; dispose of them at handoff.

The better-runtime-diagnostics completion schema is `bc.runtime_dump_completion.v3` and includes
`dimensions.json` (`bc.dimensions.v1`). Debug multiplayer traversal discovers targets at run time
from the loaded Creating Space rocket-accessible-dimension registry and enabled Dimension Drink Font
configuration; target counts are evidence, not hard-coded assumptions. Every discovered target
must be loaded. A single full-pack client travels as a spectator and requires both a server-side
position check and a client-side dimension/chunk report within 90 seconds. Font-only destinations
require a direct-travel denial guard. Debug
visits three fresh, pairwise-distant locations per directly teleportable target and always requires three
consecutive samples at at least 18 mean TPS. The other two clients start only for Debug's
three-client Survival campaign check.
For terrain dimensions, Debug counts block IDs in 3×3 already loaded chunks at each visit. A
dimension passes when the combined samples contain at least 64 non-air blocks and 16 blocks from
its broad expected family. Space and utility dimensions have no terrain threshold. Each of the
Bumblezone, Aether, and Nether Fonts is also activated through the same server-side
block interaction used by right-click; the real client must arrive, pass a geometry probe, and
return through the in-world return Font interaction. The histograms and assessments are retained
with the run evidence. These checks show representative block content, not exact terrain shape or
structure placement.
When geometry fails, retain the fixture and compare its histogram with a separate fresh world
using the same pinned mod JARs and default mod worldgen, without pack-authored datapacks or
KubeJS content. The comparison is diagnostic; it does not waive the failing candidate result.
Timeouts retain the command, server tail, process state, and fixture
for diagnosis. Candidate hashes are checked again after the multiplayer scenario completes.

In Debug, the multiplayer fixture then keeps three real clients connected to that same full-pack dedicated
server in Survival at linear Overworld checkpoints 10,000 blocks apart. The harness-only
protection control makes each player invulnerable without changing game mode. A routed scout is
started for each player with no injected route. A rejected terrain path triggers a retry at another
nearby player position on the prepared platform; each rejected and accepted point is recorded.
The test confirms that all three clients remain connected and all three encounters are active
before continuing to restart and log audits.
This is separate from the isolated Pillager Campaigns development harness and is evidence for the
packaged modpack candidate.

Because the three clients share one headless software-rendered host, the fixture lowers client
render distance/FPS and bounds Distant Horizons workers. Every candidate mod is still installed,
loaded, and connected to the same full-pack server; these fixture-only limits prevent rendering
work from starving the network heartbeats of already-connected clients.
The dedicated fixture uses view distance 4 and simulation distance 6 while the three Survival
campaign players are active. This keeps the local 48–72 block campaign approach inside the loaded
terrain contract while limiting cold-chunk fan-out without changing the production candidate's
server.properties.

Before Debug's campaign phase, the fixture lays down three bounded grass corridors at
the 0/10,000/20,000 checkpoints and places the players above them. These pads exist only in the
fresh extracted test world, making the routed local-approach proof deterministic while retaining
the complete modpack, real server tick, and real campaign materialization path.

Debug's lifecycle smoke verifies one lineage transition, its committed archive, and final clean state.
It intentionally avoids a second generation; longer persistence matrices require separate explicit
authorization.

## Runtime scenario contracts

All runtime groups consume the exact bound production ZIP pair and gate-produced diagnostic
support bytes, extracted into fresh owned fixtures. Candidate/support hashes and strict logs
are checked at completion; hash drift, missing observations, process survivors and a budget
expiry fail rather than skip or retry. Each group has a four-hour outer controller cap including
setup and diagnostics. Individual readiness, command, client-arrival and polling budgets remain
tighter; no nested operation may reset the TPS stabilization deadline. Resource arguments and
fixture-only changes are recorded in `fixture_resource_budget`/profile events. Software rendering
is disclosed and is not a hardware-accelerated performance or manual visual acceptance claim.

| Scenario family | Inputs and observable oracle | Inner budget/resource contract |
| --- | --- | --- |
| Title/server readiness and Dist join | Fresh extracts; native title/server-ready markers and real online player heartbeat | Existing bounded startup/join waits; one client, recorded heap/CPU/software-renderer profile |
| Unseeded integrated startup | Absent save, native random normal world, production spawn selection/onboarding; resolved marker, Survival, forty correlated ticks, native stop and `level.dat` | Fifteen-minute absolute startup budget plus two-minute native exit; 12 GiB/eight processors, production DH settings |
| Seeded persistence | Authored seed/probe state, dedicated-created save and explicit fallback marker; native save/reopen observations | Bounded world-open/save/exit waits; isolated seeded profile, never labeled production fresh startup |
| Native Deep Void | Extended-bottom native world, exact upstream artifact, real Survival player; above/disabled negatives, same-tick native entry and client destination | Ten-minute absolute scenario budget with at-most-two-minute tick waits and at-most-ninety-second client/return waits |
| Dimension/Font traversal | Runtime-discovered registries/config, three fresh positions, server/client chunk reports, direct-denial guard and block-family/geometry oracles | Ninety-second arrivals; at least 18 TPS in three consecutive samples within one 180-second stabilization deadline; bounded nested calls |
| Campaign/repair | Three real Survival clients, fixture corridors, native routed encounters and repair geometry | Recorded three-client resource profile, bounded routed-stage/heartbeat waits; rejected routes recorded, not waived |
| Restart/Flesh/whale | Current fixture saved state, native restart/reconnect and client spawn observations | Bounded stop/restart/client waits; fresh evidence and unchanged candidate hashes |
| Lifecycle/snapshot/pyramid | Native completion schema and snapshot ID, one committed lineage archive, pinned structure seed and saved structure record | Bounded native command/completion waits; one generation only; saved evidence belongs to the current run |
| Final audits/cleanup | Current logs, ZIP/support digests and all owned identities | Bounded diagnostic/TERM/KILL/reap phases; retain failure during diagnosis, dispose at handoff |

## Task registration and handoff

Before source verification, a release, or a multi-command test/retry sequence, register its task
and consumed/output boundaries with `maintenance.main.kts begin --task TASK_ID --path PATH`.
All tests/retries in that task keep the same candidate alive until diagnosis/testing/delivery
ends. Queue handoffs remain active consumers until their callbacks/delivery complete.

Before final success/failure/cancellation/blocked handoff, report results, stop owned producers,
then run `maintenance.main.kts finish --task TASK_ID --apply`. Caches/provider/build JARs are
removed once idle, along with evidence, superseded ZIPs and worlds regardless of verdict.
The latest complete packaged ZIP pair remains retained under the disposal policy. Active consumers
are reported as incomplete cleanup, not historical retention. See the canonical
[disposal policy](policies/generated-data.md) for audit, pins, resume and safety behavior.
Successful packaging can remove expanded staging sooner; failed staging lasts only for the
active task's diagnosis. Apart from the latest complete packaged ZIP pair, no
current-candidate/latest-pass/unresolved-failure exception survives.

## Untested lettered builds

An explicit package-only request may produce an **untested** lettered revision of the current
numeric build: `443a`, then `443b`, through `443z`. The suffix is a packaging revision, not a
passing test tier. Never transfer the numeric build's Dev/Dist/Debug or visual verdict to changed
archives. State clearly in delivery that the lettered pair is untested and name the changed inputs.

For a config/content-only revision using the existing bundled JARs, use the existing package-once
entry point, under the shared runner mutex:

```sh
./pack-test-lock.main.kts run release release-skip-tests -- ./dist.sh --untested-suffix a
```

Refresh Packwiz metadata with `packwiz refresh` immediately before packaging so the changed inputs
have coherent hashes. This narrow package-only preparation does not invoke `release.main.kts`,
Gradle, source builds, deployment or test suites. Explicit user prohibitions on additional checks
also exclude Dev, documentation checks and runtime/visual checks; disclose these omissions rather
than silently running them. The normal tested fresh-release procedure is unchanged. No verified
receipt or tested provenance is created or reused for this path.

`pack.toml` and the client manifest carry `Build 443a`; canonical outputs are
`dist/build-443a/{client,server}/better-content.zip`. Suffixes advance without overwriting an existing
revision; the next ordinary numeric package after any `443` lettered revision is `444`.
Register/finish the packaging task under the disposal policy. Lettered output retention follows
that same policy and is independent of acceptance.

## Fresh distributions

Only an explicit fresh-dist request authorizes:

```sh
./release.main.kts
./release.main.kts --jobs 4
./release.main.kts --jobs 4 --skip-tests
```

The release command consumes `gradle/active-custom-mods.json`, requires clean active repositories,
and records each local source `HEAD` in release evidence. It never fetches or modifies remotes.
Tested preparation clean-rebuilds and verifies the session's required provider/consumer closure,
with a full initial baseline. It annotates and deploys the coherent staged set, refreshes Packwiz
hashes and checks the diff, runs `dist.sh`
exactly once, and then runs the complete Dist tier. Targeted and queued Debug can test that
unchanged ZIP pair when explicitly requested. Direct full Debug uses conversation-scoped source verification,
then packages once and runs complete Dist and Debug. Release evidence records
each mod's build mode and JAR hash.
Legacy JARs without source metadata are replaced during this bootstrap run.

Release workers may concurrently inspect/reuse/stage artifacts, but source Gradle invocations
share a workspace-level Forge cache lock. ForgeGradle's generated MCP/mapped JARs are not safe
for concurrent generation or reading during generation, especially after cache disposal. The
lock serializes complete source verification/staging invocations without skipping any gate or
changing Forge pins. Do not launch uncoordinated manual source Gradle builds against the same
cache during release preparation.

`--skip-tests` is the explicit untested-release path. It still reuses valid source-identical
bundled JARs. Changed or unannotated sources build through `stageRuntimeJar` without the custom-mod
verification tasks; the complete staged set is deployed, release preparation refreshes Packwiz, and packaging runs
exactly once. It skips the full pack suite. Release evidence and provenance record that tests
were skipped; use this only when the fresh-dist request explicitly prohibits tests.
