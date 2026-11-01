# Smoke, Soak, Tele Soak and fresh distributions

There are three distinct pack-level runtime tiers. They share one lifecycle and consume existing packaged inputs; they do not build mods, deploy JARs, refresh Packwiz, package a release, or run repository-local verification. The user's standing full-pack EULA/world approval is recorded in [AGENTS.md](../AGENTS.md); no separate per-run authorization is needed to create a disposable server fixture.

| Tier | Acceptance |
| --- | --- |
| **Smoke** | Start one fresh dedicated server; connect one real full-pack client; verify current membership and a fresh message received by that client; disconnect; save and stop the server; validate the save, crash artifacts, process cleanup and unchanged ZIP hashes. |
| **Soak** | The same lifecycle, keeping that one client connected and responsive for **N minutes after verified join**. Check connection/process continuity and fresh client receipt throughout. |
| **Tele Soak** | On one client connection, discover every live registered dimension; use spectator-mode, non-pre-generated remote chunks and prove each server dimension, fresh client arrival and server player coordinates. In every dimension, make ten fresh horizontal relocations of at least 10,000 blocks during a minimum 300-second responsive dwell (about one relocation every 30 seconds, delaying the next until TPS recovers). Recover official Forge overall TPS to ≥19.0 for 30 continuously sampled seconds within 120 seconds of **every** dimension hop and remote relocation; never reuse a previously visited landing chunk. Complete three additional varied-order full-roster circuits, with another fresh 10,000-block relocation in each dimension on each circuit. Record raw samples and preserve the Smoke save/stop/hash/cleanup gates. |

Tele Soak allocates remote X coordinates across the entire connected itinerary, rather than restarting at the same X in each dimension. This prevents a later real Font entry into Bumblezone from mapping to a chunk already visited during its initial dwell; the Font guard and interaction remain intact. Actual native Font arrivals must still be fresh and at least 10,000 blocks from the previous Bumblezone landing. No remote target is pre-generated.

No reconnect, second client, feature tour, dimension sweep, multiplayer campaign, integrated-server branch or restart campaign is part of **Smoke or ordinary Soak**. Tele Soak alone has the controlled dimension itinerary, and is never inferred from an ordinary successful 300-second Soak. A failed connection is a failure, not an invitation to reconnect until green. Initial preparation, dimension arrival and initial TPS recovery do not count toward dwell; remote relocation recovery during the dwell can extend its duration but cannot omit any of the ten relocations.

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

### Non-acceptance diagnostic replay after a failure

A code or JAR change cannot be hot-patched into a running Java client/server. After a failed run, use a **fresh disposable session** to investigate only the remaining work, without replaying every completed Tele Soak dwell/circuit hop:

```sh
./test.main.kts tele-soak --client /path/new/client.zip --server /path/new/server.zip \
  --diagnose-from /absolute/path/generated/test-evidence/FAILED_RUN/tele-soak/run.json
./test.main.kts soak --minutes 60 --client /path/new/client.zip --server /path/new/server.zip \
  --diagnose-from /absolute/path/generated/test-evidence/FAILED_RUN/soak/run.json
./test.main.kts smoke --client /path/new/client.zip --server /path/new/server.zip \
  --diagnose-from /absolute/path/generated/test-evidence/FAILED_RUN/smoke/run.json
```

The source must be a terminal **failed** run of the same tier; the new candidate may differ after a fix. A Tele Soak replay discovers and prepares the entire fresh live roster, checks that it matches the failed roster, then starts at the last attempted dimension visit (or the beginning if no visit was reached), repeating that visit's relocations before the remaining itinerary and circuits. It does not yet resume at an individual relocation ordinal. Soak reruns the fresh startup/join and only the previously unobserved connected seconds (minimum one second to exercise later lifecycle phases); Smoke reruns its short fresh lifecycle because startup and connection cannot be skipped. None reconnects to or mutates the failed fixture.

Diagnostic output lives in a separate `<tier>-diagnostic/` evidence directory, records its predecessor and checkpoint, and ends with `status: diagnostic` rather than `passed`; partial circuits and skipped dwells never generate acceptance completion. Even when all diagnostic work succeeds, **it cannot satisfy Smoke, Soak, Tele Soak or a release gate**. To accept a candidate, rerun the ordinary tier from the beginning with no `--diagnose-from`. The fail-fast runner lock, unique evidence, unchanged-ZIP hashes, fresh world and process cleanup still apply.

Old `dev`, `dist`, `debug`, targets, retry/handoff protocols and queue commands are removed, not supported aliases. `./gradlew test` remains an ordinary implementation-local check, not a third runtime tier and not a prerequisite of Smoke/Soak.

## Exact inputs and disposable profile

Each run hashes both ZIPs before starting and checks those hashes again after execution, including failure paths. The server is extracted into a new fixture, never into a player's installation. The client is assembled from its exported manifest, packaged overrides and the embedded `better-content-artifacts.json` lock. Downloads use the lock's exact URLs, filenames and digests. The runner does not consult current checkout configs/recipes/mod JARs, import another pack, or invoke `package.sh` as a fallback.

The packager creates the client artifact lock while packaging. Older exports lacking it are not silently supplemented from live source: explicitly prepare a new distribution.

Fixture-only settings are recorded in evidence: offline authentication on loopback, an ephemeral server port, reduced render/simulation distance, bounded JVM heaps (the full client uses 2–8 GiB for every tier so its initial resource reload does not exhaust a 4 GiB Smoke heap), disabled scheduled backups and disabled background Distant Horizons rendering/generation for software OpenGL. Tele Soak alone uses a spectator client to land safely in fresh, ungenerated terrain without a prepared pad at the remote destination; the loaded-dimension probe and client/server position receipts are still mandatory. The complete packaged mod set remains installed. This profile proves this local dedicated session, not authenticated public-server login or production graphics performance.

A heartbeat executes only for the named connected player and sends a fresh unpredictable marker. Acceptance requires that marker in the real client's current output, not merely the command echoed in the server console. During an in-flight Tele Soak hop, an actual received marker may precede its dimension-conditioned arrival receipt; the independent release validator accepts that temporary unqualified marker only within the hop's existing 120-second budget. Between arrivals it requires live, dimension-qualified client evidence within 30 seconds. The separate ≥19 TPS for 30 continuously sampled seconds within 120 seconds of **every** hop and relocation remains unchanged. After the connected interval, a server kick ends the network session; the client/display processes are closed before normal server `stop`, which performs Minecraft's complete save itself. A normal run must observe a fresh all-dimensions-saved acknowledgement and a zero server exit; forced teardown cannot substitute for successful shutdown. The runner does not redundantly force a full console flush while the tick watchdog is still active.

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

## Fresh distributions: explicit, optional runtime tiers

```sh
./release.main.kts                                  # package-only; no runtime tier
./release.main.kts --runtime-tiers smoke            # first new-source candidate: Smoke only
./release.main.kts --runtime-tiers soak --soak-minutes 30
./release.main.kts --runtime-tiers smoke,soak,tele-soak --soak-minutes 30
# Package current bundled JARs rather than inspecting sibling sources:
./release.main.kts --use-bundled-mods --runtime-tiers smoke
```

Fresh release preparation reuses source-identical bundled custom JARs and stages changed runtime sources in dependency order. Building uses `stageRuntimeJar`, not `clean`, `verifyFull` or a blanket repository test sweep. Native staging uses the shared `$HOME/.tmp/better-content-gradle.lock` and caps each Gradle invocation at two workers; the preparation job count does not bypass Forge/MCP cache serialization. Local verification belongs to the owning repository and is deliberately separate from release acceptance.

`--use-bundled-mods` explicitly packages the JARs already present in this pack, without inspecting or upgrading sibling source checkouts. Use it for runner/content-only work when rolling forward all newer source repositories is outside scope. It records `runtime_inputs=bundled` in provenance and observes the same explicitly selected runtime tiers; it does not imply that unselected tiers passed.

Existing bundled provider JARs seed the staging directory; updated active providers replace them before consumers build. Optional compile-only fixtures may be supplied explicitly through `BC_RELEASE_COMPILE_FIXTURE_DIR` (for example the inactive Celestial Events API required by the Fonts build). They are recorded as build inputs and are **not** activated or copied into the pack. See [custom mod workspace](custom-mod-workspace.md).

The release flow refreshes Packwiz, snapshots existing releases, invokes the packager **once**, selects exactly one newly created complete ZIP pair, validates the archive inventory and unchanged hashes, then atomically writes `dist/<release>/package-ready.json`. This means packaged only, not client/server tested. The default `--runtime-tiers none` runs no runtime tier. Explicitly select ordered, nonduplicate `smoke`, `soak`, and/or `tele-soak` with `--runtime-tiers`; Soak requires `--soak-minutes 1..10080`, which is invalid without Soak. Every selected tier runs on the **same unchanged ZIP pair**, with a fresh world/real client, shared runner lock and its ordinary full validation. No repackaging occurs after packaging. Previous accepted and failed releases are retained.

`dist/<release>/ready.json` is issued only if **requested Smoke passes** and every other tier requested in that release also passes. It is Smoke-certified, not proof of unrun Soak, Tele Soak or visual review. The immutable package marker records selected runtime tiers as `pending` and unselected tiers as `not_run`; the separate release result records `passed`/`failed`/`not_run`. A Smoke-ready marker records `passed`/`not_run` with exact candidate hashes and receipt hashes for passed tiers. A requested-tier failure leaves the package marker and failure evidence but **no** ready marker. Later standalone tier runs record separate evidence; they do not rewrite existing package or Smoke claims. `--skip-tests` does not exist. `dist.sh` is only a package-once primitive, not a release marker. With multiple retained ZIP pairs, supply both paths explicitly for standalone tiers.

### Strict qualified fresh-source candidate

`./release.main.kts --fresh-source-plan /absolute/path/qualified-plan.json --runtime-tiers smoke` remains available only **after all active sources, including full Cooking gameplay and World Condenser production, have qualified**. The user's later permission to bundle **incomplete Cooking** in non-destructive pass-through mode is deliberately NOT a source qualification or authorization to use this strict path; for that narrower Smoke candidate use the already staged `mods/` inputs via `./release.main.kts --use-bundled-mods --runtime-tiers smoke`. This tests the exact packaged pair, not the dormant Cooking catalogue or unqualified gameplay. This path requires a fresh qualification for **every approved active custom source**, including a **new World Management JAR and supervisor script**: no fixed membership count and no substitution of Build 1's accepted World Management bytes. Plans and receipts bind source, test, build, provider hashes, native checks, supervisor snapshot and the independent current-request cohort bound (`BC_FRESH_SOURCE_REQUEST_NOT_BEFORE_UTC`). Qualified raw JAR hashes must agree with staged and packaged bytes. Build 1's historical archive/hash guard stays intact; it is not authorization for a new artifact. Unqualified or source-only projects cannot be activated to fill the cohort. Third-party binaries remain hash-pinned.

The standing full-pack world approval requires no per-run environment tokens. Strict source qualification remains mandatory even if no runtime tier is requested. Tele Soak, when selected, retains the complete raw-event/live-roster/remote-relocation/TPS/lifecycle checks; omitting it makes no Tele Soak claim. Packaging writes the separate `package-ready.json`; a selected Smoke must pass before its distinct `ready.json` exists. A Smoke-ready partial Cooking candidate attests startup, connection and shutdown only, **not** that Cooking's unresolved food profiles or recipe migration passed gameplay tests. Never mutate accepted Build 1 or repackage a candidate after a failed tier.

### Independent visual tier (optional, separate invocation)

After a candidate is packaged, a **different identified reviewer** may conduct a fresh owned, real-client, in-world session and submit its capture/review receipts using `./release.main.kts --visual-tier /absolute/path/package-ready.json --visual-review /absolute/path/review.json`. Validation binds the actual package marker, unchanged ZIP hashes, provenance/inventory, candidate/world identity, reviewer independence, in-world screenshots and authentic input log, save and cleanup. A failed or stale review cannot become acceptance; attempted world identities are retained to prevent reuse. Success creates separate `generated/test-evidence/<run-id>/visual/receipt.json` and **never rewrites** `package-ready.json` or `ready.json`. No synthetic mouse evidence or old-world screenshots. The first Smoke-only candidate does **not** run this tier. Historical `--finalize-visual PENDING_JSON --visual-review REVIEW_JSON` is retained solely for previously issued pending receipts; it does not approve a new candidate.

## Local regressions

```sh
./gradlew test
python3 .github/scripts/test_prepare_provider_jars.py
```

The small pack-local checks protect retained process cleanup, lifecycle/duration behavior, artifact materialization, provider staging and release-result identity. Local checks also require Node.js to execute the real KubeJS culinary policy consumer against its quarantined audit policy, proving that retained historical IDs install no recipes. The Python materializer checks retain their 30-second local test-process budget. The separate supervisor transaction check has its own three-minute local test-process budget; neither changes any pack runtime acceptance deadline. They do not start Minecraft and are not runtime-tier dependencies.

Feature regressions live in their owning independent source repositories under `../mod_source/`, using their documented JVM/native GameTest checks. Existing local behavioral coverage replaces duplicate source-text assertions. Pack-authored recipe/configuration/teaching choices do not acquire a false mod owner; the old bulk source/prose/recipe assertions were retired rather than recreated as a legacy suite.

Headless native tests do not prove real-client rendering or the full integration matrices that were deliberately removed. In particular the World Management shell supervisor stays pack-owned; pure archive/state tests do not establish its entire successor-world transaction.
