# Testing and fresh distributions

Pack-level tests are intentionally expensive and run only when explicitly requested. Ordinary
changes use focused inspection or the owning format/tool. Changes to the test harness itself use:

```sh
./test.main.kts fast
```

An explicit pack-test request selects one existing candidate group:

```sh
./test.main.kts candidate
./test.main.kts server
./test.main.kts multiplayer
./test.main.kts singleplayer
./test.main.kts all
```

All heavyweight selectors and `release.main.kts` share one kernel-backed runner mutex. The
supported entry points acquire it automatically and publish current ownership at
`$HOME/.local/share/worklane/pack-tests/owner.json`; contention fails immediately with exit code
75 and that ownership record. `fast` and custom-mod repository-local checks do not use this mutex.
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

The selectors map exactly to `test`, `candidateTest`, `serverTest`, `multiplayerTest`, and
`singleplayerTest`. Gradle also exposes the aggregate `modpackTest`, but `test.main.kts all`
deliberately sequences `test`, the candidate gate, and the three independent runtime groups so a
candidate failure prevents Minecraft startup while a later runtime-group failure does not hide the
other groups' evidence. Each runtime group uses a fresh fixture.

`fast` writes ordinary Gradle XML and HTML reports only. Candidate, runtime, and `all` runs share a
run ID and write structured evidence beneath `generated/test-evidence/<run-id>/`: incremental JSON
events, a `bc.modpack_test_run.v1` summary, candidate hashes, logs, runtime data, and timeout
diagnostics. The single-player group also records the customized title screen without injecting
input. Failed fixtures are retained. Automated tests must not synthesize mouse movement or mouse
clicks. Threads reader development, the World Condenser configuration screen, and single-player
world creation are manual visual gates. Before rerunning, inspect the existing run and report its
ID, hashes, failed or aborted cases, evidence path, retained fixture, and process cleanup state.
Cleanup retains observed process descendants after their parent exits and checks termination after
graceful and forced shutdown. Every fixture is closed even when an earlier close fails. The
`process_cleanup` event reports `complete=false`, surviving PIDs, and an error when cleanup fails;
the suite then fails and retains its fixture and original failure evidence. `complete=true` means
all tracked processes have exited. The fast suite tests ordinary, forced, and orphaned-child
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
must be loaded. The test traverses three fresh, pairwise-distant locations per target as a
spectator, requires a post-teleport heartbeat within 90 seconds, then requires three consecutive
10-second samples at at least 18 mean TPS within 180 seconds. Timeouts retain the command, server
tail, process state, and fixture for diagnosis. Candidate hashes are checked again after traversal.

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
staged runtime JARs together, refreshes Packwiz, runs `dist.sh` exactly once, and finally invokes the
full pack suite. It records each mod's `reused` or `rebuilt` mode and JAR hash before testing the
unchanged ZIP pair.
Legacy JARs without source metadata are replaced during this bootstrap run.

`--skip-tests` is the explicit untested-release path. It rebuilds each active mod through
`stageRuntimeJar`, deploys the complete set, refreshes Packwiz, and packages exactly once, but skips
the custom-mod verification tasks and the full pack suite. Release evidence and provenance record
that tests were skipped; use this only when the fresh-dist request explicitly prohibits tests.
