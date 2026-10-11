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

## Public tiers

The public test interface has three cumulative tiers. Dev needs no packaged candidate and can
run while other developers share the workspace:

```sh
./test.main.kts dev
```

Dev runs the fast Minecraft-free contracts and `git diff --check`; it does not refresh Packwiz
hashes. Cold-cache source inspection rehydrates only the native JARs named by its tests through
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
./test-control.main.kts retry debug --target font:aether --retry-of RUN_ID
```

`run source --all` verifies the active manifest repositories plus the two supported validation-only
repositories in dependency order and stages provider JARs for dependent builds. Retired Ratlantis
is not offered by source control; checkout count is not a build requirement. Full Dist and Debug use the same fresh release path as the
direct tier commands below. `submit --handoff /absolute/path/to/handoff.json` sends a prepared
immutable-candidate request to the shared queue.

Direct full Dist and Debug require clean active custom-mod repositories. They run every active
mod's manifest-listed verification tasks and rebuild every runtime JAR after `clean`, even when
the bundled JAR records the same source revision. It stages the JARs in dependency order,
deploys the validated set, refreshes Packwiz, packages once, then runs complete Dist and Debug
on the new ZIP pair. Release evidence links the Dist and Debug run IDs and candidate hashes.

After inspecting a failed run, use a targeted retry to check a fix without starting every Debug
scenario. Each retry makes a fresh fixture and records separate `target-*` evidence; it cannot be
mistaken for a full-tier pass. Supported targets are `server-ready` (Debug), `join` (Dist or Debug),
`font:bumblezone`, `font:aether`, `font:nether`, `fonts`, `dimension:<id>` for a directly
teleportable dimension, `dimensions`, `campaign-start` (three live encounters and platform repair),
`campaign` (three live client encounters), `restart-compat` (saved Flesh spread
restart and Untamed whale client spawn), `lineage-transition` (Debug lifecycle successor commit
and archive), `cursed-pyramid` (pinned seed, saved structure record,
and strict log audit in its surrounding chunks), and `world-save` (Debug). The `campaign-start` fixture
uses the packaged spawn rules and removes a strip of campaign floor to verify that the
campaign-phase repair restores pathable geometry. For example:

```sh
./test.main.kts debug --target font:aether --retry-of 20260926T070341Z-1044833
./test.main.kts debug --target fonts
```

Targeted runs still perform fast checks and candidate validation, then start only the selected
runtime fixture and its required setup. They check candidate hashes, process cleanup, and strict
logs. Findings are recorded in the target evidence and fail that targeted run. If a source fix
needs a new JAR, use `./release.main.kts --target font:aether --retry-of RUN_ID` to package once
and test that target. Direct full Debug later rebuilds all active mods and runs complete Dist and
Debug on its new, unchanged ZIP pair.

Dist includes Dev, candidate contracts, a real full-pack client joining a fresh dedicated server,
an online player heartbeat, strict logs, and candidate hashes. It does not run dimension travel.
Direct full Debug uses unchanged candidate hashes from its newly packaged Dist run and adds
dedicated-server startup/runtime snapshot, three
fresh locations per directly teleportable dimension with strict three-sample TPS and Font-only
direct-travel guards, one lineage transition and archive, three live client campaigns,
server restart/client reconnect, four native Font round trips, and singleplayer startup/world
boot/save/reopen.

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
screen, and the Create World menu are manual visual gates. Debug's non-pointer world probe tests
fresh save boot and reopen, not the menu. Before rerunning, inspect the existing run and report its
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

## Task registration and handoff

Before source verification, a release, or a multi-command test/retry sequence, register its task
and consumed/output boundaries with `maintenance.main.kts begin --task TASK_ID --path PATH`.
All tests/retries in that task keep the same candidate alive until diagnosis/testing/delivery
ends. Queue handoffs remain active consumers until their callbacks/delivery complete.

Before final success/failure/cancellation/blocked handoff, report results, stop owned producers,
then run `maintenance.main.kts finish --task TASK_ID --apply`. Caches/provider/build JARs are
removed once idle, along with evidence, ZIPs and worlds regardless of verdict. Active consumers
are reported as incomplete cleanup, not historical retention. See the canonical
[disposal policy](policies/generated-data.md) for audit, pins, resume and safety behavior.
Successful packaging can remove expanded staging sooner; failed staging lasts only for the
active task's diagnosis. No current-candidate/latest-pass/unresolved-failure exception survives.

## Fresh distributions

Only an explicit fresh-dist request authorizes:

```sh
./release.main.kts
./release.main.kts --jobs 4
./release.main.kts --jobs 4 --skip-tests
```

The release command consumes `gradle/active-custom-mods.json`, requires clean active repositories,
and records each local source `HEAD` in release evidence. It never fetches or modifies remotes.
Full Dist and Debug verify and rebuild every active source after `clean`. They annotate and deploy all
staged runtime JARs together, refreshes Packwiz hashes and checks the diff, runs `dist.sh`
exactly once, and then runs the complete Dist tier. Targeted and queued Debug can test that
unchanged ZIP pair when explicitly requested. Direct full Debug forces verification and rebuilding
of every active mod, then packages once and runs complete Dist and Debug. Release evidence records
each mod's build mode and JAR hash.
Legacy JARs without source metadata are replaced during this bootstrap run.

`--skip-tests` is the explicit untested-release path. It still reuses valid source-identical
bundled JARs. Changed or unannotated sources build through `stageRuntimeJar` without the custom-mod
verification tasks; the complete staged set is deployed, release preparation refreshes Packwiz, and packaging runs
exactly once. It skips the full pack suite. Release evidence and provenance record that tests
were skipped; use this only when the fresh-dist request explicitly prohibits tests.
