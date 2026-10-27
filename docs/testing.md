# Smoke, Soak, Tele Soak and fresh distributions

There are three distinct pack-level runtime tiers. They share one lifecycle and consume existing packaged inputs; they do not build mods, deploy JARs, refresh Packwiz, package a release, or run repository-local verification. The user's standing full-pack EULA/world approval is recorded in [AGENTS.md](../AGENTS.md); no separate per-run authorization is needed to create a disposable server fixture.

| Tier | Acceptance |
| --- | --- |
| **Smoke** | Start one fresh dedicated server; connect one real full-pack client; verify current membership and a fresh message received by that client; disconnect; save and stop the server; validate the save, crash artifacts, process cleanup and unchanged ZIP hashes. |
| **Soak** | The same lifecycle, keeping that one client connected and responsive for **N minutes after verified join**. Check connection/process continuity and fresh client receipt throughout. |
| **Tele Soak** | On one client connection, discover every live registered dimension; prove each server dimension and a fresh client arrival; recover official Forge overall TPS to ≥19.0 for 30 continuously sampled seconds within 120 seconds of every hop; stay 300 uninterrupted responsive seconds after stabilization in each dimension, then complete three additional varied-order full-roster circuits. Record raw samples and preserve the Smoke save/stop/hash/cleanup gates. |

No reconnect, second client, feature tour, dimension sweep, multiplayer campaign, integrated-server branch or restart campaign is part of **Smoke or ordinary Soak**. Tele Soak alone has the controlled dimension itinerary, and is never inferred from an ordinary successful 300-second Soak. A failed connection is a failure, not an invitation to reconnect until green. Preparation, arrival and TPS recovery do not count toward dwell.

## Run existing artifacts

Use Java 17 and the repository's Gradle wrapper. The command requires Kotlin, Python 3.11+, pipx, Xvfb and xauth. PortableMC is pinned to 4.4.1 and downloads launcher/runtime dependencies into a reusable user cache. Software OpenGL is used when running the headless client. No pointer movement or mouse clicks are synthesized.

```sh
./test.main.kts smoke
./test.main.kts soak --minutes 30

# Explicit paths avoid any ambiguity in candidate discovery:
./test.main.kts smoke --client /path/client.zip --server /path/server.zip
./test.main.kts soak --minutes 60 --client /path/client.zip --server /path/server.zip
./test.main.kts tele-soak --client /path/client.zip --server /path/server.zip
```

Without explicit paths, `dist/` must contain exactly one matching `client/better-content.zip` and `server/better-content.zip` pair in the same release directory. Supply both explicit paths or neither. Soak requires an integer duration from 1 to 10080 minutes; Smoke and fixed-five-minutes-per-dimension Tele Soak have no duration option. The standing approval covers these runs; neither `BC_PACK_EULA_ACCEPTED` nor `BC_PACK_EULA_AUTHORIZATION` is needed. A later user revocation supersedes the approval.

Old `dev`, `dist`, `debug`, targets, retry/handoff protocols and queue commands are removed, not supported aliases. `./gradlew test` remains an ordinary implementation-local check, not a third runtime tier and not a prerequisite of Smoke/Soak.

## Exact inputs and disposable profile

Each run hashes both ZIPs before starting and checks those hashes again after execution, including failure paths. The server is extracted into a new fixture, never into a player's installation. The client is assembled from its exported manifest, packaged overrides and the embedded `better-content-artifacts.json` lock. Downloads use the lock's exact URLs, filenames and digests. The runner does not consult current checkout configs/recipes/mod JARs, import another pack, or invoke `package.sh` as a fallback.

The packager creates the client artifact lock while packaging. Older exports lacking it are not silently supplemented from live source: explicitly prepare a new distribution.

Fixture-only settings are recorded in evidence: offline authentication on loopback, an ephemeral server port, reduced render/simulation distance, bounded JVM heaps, disabled scheduled backups and disabled background Distant Horizons rendering/generation for software OpenGL. The complete packaged mod set remains installed. This profile proves this local dedicated session, not authenticated public-server login or production graphics performance.

A heartbeat executes only for the named connected player and sends a fresh unpredictable marker. Acceptance requires that marker in the real client's current output, not merely the command echoed in the server console. After the connected interval, a server kick ends the network session; the client/display processes are closed before normal server `stop`, which performs Minecraft's complete save itself. A normal run must observe a fresh all-dimensions-saved acknowledgement and a zero server exit; forced teardown cannot substitute for successful shutdown. The runner does not redundantly force a full console flush while the tick watchdog is still active.

Startup, response and shutdown deadlines bound hangs. They are not TPS, FPS, memory or performance acceptance thresholds. WARN records and nonfatal ERROR messages are retained for diagnosis, not blanket-failed. Crashes, unexpected disconnects, lost responsiveness, incomplete saves, unsuccessful shutdown, surviving owned processes and candidate mutation are failures.

## Evidence and process ownership

Evidence is retained at `generated/test-evidence/<run-id>/<smoke|soak|tele-soak>/`:

- terminal `run.json`, exact candidate paths/hashes and status;
- ordered `events.jsonl`, heartbeat and diagnostic memory observations;
- full server, client, materialization and display logs;
- disposable extracted installations and saved world;
- process/thread diagnostics on failure and cleanup outcome.

Run IDs cannot overwrite an existing tier result. Inspect the first useful error and retained fixture before retrying. Reruns get fresh identities/worlds; there is no automatic retry campaign. Neither success nor failure prunes old evidence, player worlds or launcher state. Manual cleanup of known disposable runs is a separate operation.

Smoke, Soak, Tele Soak and fresh releases share a fail-fast kernel-backed runner lock. Contention exits 75 with owner metadata. The lock is a safety primitive, not a coordinator/queue service. Do not bypass the supported facade to run heavyweight Gradle tasks.

## Fresh distribution gate

```sh
./release.main.kts
./release.main.kts --jobs 2 --soak-minutes 30
# Package the current pack inputs without upgrading newer sibling source checkouts:
./release.main.kts --use-bundled-mods --soak-minutes 30
```

Fresh release preparation reuses source-identical bundled custom JARs and stages changed runtime sources in dependency order. Building uses `stageRuntimeJar`, not `clean`, `verifyFull` or a blanket repository test sweep. Native staging uses the shared `$HOME/.tmp/better-content-gradle.lock` and caps each Gradle invocation at two workers; the preparation job count does not bypass Forge/MCP cache serialization. Local verification belongs to the owning repository and is deliberately separate from release acceptance.

`--use-bundled-mods` explicitly packages the JARs already present in this pack, without inspecting or upgrading sibling source checkouts. Use it for runner/content-only work when rolling forward all newer source repositories is outside scope. It records `runtime_inputs=bundled` in provenance and still requires the same mandatory Smoke and requested Soak; it is not a test bypass.

Existing bundled provider JARs seed the staging directory; updated active providers replace them before consumers build. Optional compile-only fixtures may be supplied explicitly through `BC_RELEASE_COMPILE_FIXTURE_DIR` (for example the inactive Celestial Events API required by the Fonts build). They are recorded as build inputs and are **not** activated or copied into the pack. See [custom mod workspace](custom-mod-workspace.md).

The release flow refreshes Packwiz during preparation, snapshots existing release directories, invokes the shared packager **once**, selects exactly one newly created complete ZIP pair (never an older pair), records its paths/hashes, and requires a fresh successful **Smoke on that exact pair**. It checks both matching fresh evidence and unchanged hashes before writing `dist/<release>/ready.json` or reporting success. An optional Soak uses those same unchanged artifacts. No repackaging occurs between acceptance and publication. Packaging retains all previous accepted/failed release directories and partial outputs; it never prunes `dist/`. Standalone discovery remains deliberately ambiguous when multiple retained pairs exist: supply explicit paths.

There is no successful-release `--skip-tests` bypass. Failed preparation, failed Smoke, missing/stale/mismatched evidence, candidate mutation or a requested failed Soak results in failure and no ready marker. `dist.sh` remains only the package-once primitive: its output alone is not an accepted release.

### New full-source candidate (separate, mandatory Tele Soak gate)

`./release.main.kts --fresh-source-plan /absolute/path/qualified-plan.json` is the new release path, **not** an alias for the legacy optional-Soak flow. It requires the current 49-member manifest with Spice activated, fresh qualified source snapshots/raw staged JARs for 48 active custom mods, and the exact accepted Build 1 `better-world-management` JAR/supervisor unchanged. Plans and receipts bind source/test/build inputs, provider hashes, required native checks and the independently supplied current-request cohort bound (`BC_FRESH_SOURCE_REQUEST_NOT_BEFORE_UTC`). Missing/old source qualifications fail before staging; the standing EULA/world approval applies. `--use-bundled-mods` and `--soak-minutes` cannot weaken this path. Third-party binaries remain hash-pinned.

The standing user approval permits a new full-pack fixture without per-run environment tokens. The path packages once and runs mandatory Smoke **and** Tele Soak on that same unchanged ZIP pair; Tele Soak release validation checks the raw `events.jsonl` sequences, complete live roster/circuits, strict 30-second TPS windows, uninterrupted dwell, save/stop and process cleanup before an atomic new ready marker. The release provenance records the standing approval reference; source/JVM green is not native or visual acceptance. Never repackage after acceptance or mutate accepted Build 1.

## Local regressions

```sh
./gradlew test
python3 .github/scripts/test_prepare_provider_jars.py
```

The small pack-local checks protect retained process cleanup, lifecycle/duration behavior, artifact materialization, provider staging and release-result identity. They do not start Minecraft and are not runtime-tier dependencies.

Feature regressions live in their owning independent source repositories under `../mod_source/`, using their documented JVM/native GameTest checks. Existing local behavioral coverage replaces duplicate source-text assertions. Pack-authored recipe/configuration/teaching choices do not acquire a false mod owner; the old bulk source/prose/recipe assertions were retired rather than recreated as a legacy suite.

Headless native tests do not prove real-client rendering or the full integration matrices that were deliberately removed. In particular the World Management shell supervisor stays pack-owned; pure archive/state tests do not establish its entire successor-world transaction.
