# Testing and fresh distributions

The public test interface has three cumulative tiers. Dev needs no packaged candidate and can
run while other developers share the workspace:

```sh
./test.main.kts dev
```

Dev runs the fast Minecraft-free contracts and `git diff --check`; it does not refresh Packwiz
hashes. Fresh-dist preparation refreshes them once after source and pack changes settle.
Pack-level tiers are expensive and run only when explicitly requested:

```sh
./test.main.kts dist
./test.main.kts debug
```

Dist includes Dev, candidate contracts, server startup/snapshot/one lineage transition,
singleplayer title, multiplayer join, all-dimension travel with one TPS sample per location,
a two-minute three-client campaign, strict logs, and candidate hashes. A sample below 18 TPS
switches its location to three consecutive passing samples within 180 seconds. Debug uses
unchanged candidate hashes and adds strict three-sample travel, the 30-minute campaign soak,
server restart/client reconnect, native Font travel, and singleplayer world boot/save/reopen.

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

The tiers run internal Gradle tasks `test`, `candidateTest`, `serverTest`, `multiplayerTest`, and
`singleplayerTest`. The facade sequences fast checks, the candidate gate, and three runtime groups.
Dist stops after the first failed gate or runtime group to shorten failed candidate loops; Debug
continues through independent runtime groups to collect diagnostic evidence. Each runtime group
uses a fresh fixture.

Dev writes ordinary Gradle XML and HTML reports only. Dist and Debug runs share a
run ID and write structured evidence beneath `generated/test-evidence/<run-id>/`: incremental JSON
events, a `bc.modpack_test_run.v1` summary, candidate hashes, logs, runtime data, and timeout
diagnostics. The single-player group also records the customized title screen without injecting
input. Failed fixtures are retained. Automated tests must not synthesize mouse movement or mouse
clicks. Threads reader development, the Quark chat emote picker, the World Condenser configuration
screen, and the Create World menu are manual visual gates. Debug's non-pointer world probe tests
fresh save boot and reopen, not the menu. Before rerunning, inspect the existing run and report its
ID, hashes, failed or aborted cases, evidence path, retained fixture, and process cleanup state.
Cleanup retains observed process descendants after their parent exits and checks termination after
graceful and forced shutdown. Every fixture is closed even when an earlier close fails. The
`process_cleanup` event reports `complete=false`, surviving PIDs, and an error when cleanup fails;
the suite then fails and retains its fixture and original failure evidence. `complete=true` means
all tracked processes have exited. Dev tests ordinary, forced, and orphaned-child
shutdown without launching Minecraft.

A runtime snapshot is evidence for a target only when its snapshot ID appears in that run's server
events and the run's `candidate_selected` hashes match the target under discussion. Completeness
makes a snapshot usable evidence; recency alone does not make it current. Preserve unmatched
snapshots as historical candidate evidence and do not use their volatile totals as claims about the
tracked pack.

The runtime-data-dumper completion schema is `bc.runtime_dump_completion.v3` and includes
`dimensions.json` (`bc.dimensions.v1`). Multiplayer smoke discovers targets at run time from the
loaded Creating Space rocket-accessible-dimension registry and enabled Dimension Drink Font
configuration; target counts are evidence, not hard-coded assumptions. Every discovered target
must be loaded. A single full-pack client traverses three fresh, pairwise-distant locations per
target as a spectator and requires a post-teleport heartbeat within 90 seconds. Dist takes one
10-second sample per location and expands low-TPS results to three consecutive passing samples;
Debug always requires three consecutive samples at at least 18 mean TPS. The other two clients
are not started for this teleport/TPS test; they are started once afterward for the separate
three-client Survival soak. Timeouts retain the command, server tail, process state, and fixture
for diagnosis. Candidate hashes are checked again after traversal.

The multiplayer fixture then keeps three real clients connected to that same full-pack dedicated
server in Survival at linear Overworld checkpoints 10,000 blocks apart. The harness-only
protection control makes each player invulnerable without changing game mode. A routed scout is
started for each player with no injected route, then the complete pack runs for a two-minute Dist
or 30-minute Debug
wall-clock soak while server/client liveness, campaign status, game time, and logs are sampled.
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

Before the campaign phase, the fixture lays down three bounded 161×161-block grass pads at
the 0/10,000/20,000 checkpoints and places the players above them. These pads exist only in the
fresh extracted test world, making the routed local-approach proof deterministic while retaining
the complete modpack, real server tick, and real campaign materialization path.

The lifecycle smoke verifies one lineage transition, its committed archive, and final clean state.
It intentionally avoids a second generation; longer persistence matrices require separate explicit
authorization.

## Evidence maintenance

Inspect retention decisions without changing the workspace:

```sh
./maintenance.main.kts audit
```

After reviewing that output, explicitly apply the guarded prune with:

```sh
./maintenance.main.kts prune --apply
```

The command refuses dirty repositories, competing Gradle or Minecraft processes, unsafe paths, and
candidate-hash changes. It retains the evidence matching the current ZIP pair, the newest passed
report for a suite missing from that run, and any failure without a later passing result. Pruning
writes a `bc.workspace_maintenance.v1` transaction manifest beneath the Worklane state directory.
Successful packaging removes its expanded server staging tree after the server ZIP is complete;
failed packaging keeps staging for diagnosis.

## Fresh distributions

Only an explicit fresh-dist request authorizes:

```sh
./release.main.kts
./release.main.kts --jobs 4
./release.main.kts --jobs 4 --skip-tests
```

The release command consumes `gradle/active-custom-mods.json`, requires clean active repositories,
compares each local source `HEAD` with the revision embedded in its currently bundled JAR, and
records that local-only update check in release evidence. It never fetches or modifies remotes.
It reuses unchanged bundled runtime JARs whose embedded source revision matches the clean checkout,
and runs the documented verification only for changed repositories. It annotates and deploys all
staged runtime JARs together, refreshes Packwiz hashes and checks the diff, runs `dist.sh`
exactly once, and then runs the complete Dist tier. Debug is run separately against the unchanged
ZIP pair when explicitly requested. Release evidence records each mod's `reused` or `rebuilt`
mode and JAR hash.
Legacy JARs without source metadata are replaced during this bootstrap run.

`--skip-tests` is the explicit untested-release path. It still reuses valid source-identical
bundled JARs. Changed or unannotated sources build through `stageRuntimeJar` without the custom-mod
verification tasks; the complete staged set is deployed, release preparation refreshes Packwiz, and packaging runs
exactly once. It skips the full pack suite. Release evidence and provenance record that tests
were skipped; use this only when the fresh-dist request explicitly prohibits tests.
