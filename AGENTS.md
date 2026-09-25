# AGENTS.md

## Scope
This repository is the Better Content Forge 1.20.1 modpack content layer.

## Active scripts
- `./dist.sh` creates versioned CurseForge/client and server-content ZIPs under the
  canonical ignored `dist/` directory. It accepts no output-directory override.
- `./test.main.kts` is the supported three-tier evaluation facade. It requires an explicit
  `dev`, `dist`, or `debug` selector.
- `./release.main.kts` is the only fresh-dist workflow. By default it reuses unchanged bundled
  runtime JARs whose source revision matches and validates/rebuilds changed repositories, then
  refreshes Packwiz hashes, packages exactly once, and runs `dist`.
  `--skip-tests` is allowed only when the explicit fresh-dist request prohibits tests; it still
  reuses unchanged JARs, builds/stages changed sources without verification, and packages exactly once.
- `./maintenance.main.kts audit` reports evidence retention decisions without changing the
  workspace. `./maintenance.main.kts prune --apply` removes only superseded test evidence and
  redundant distribution staging after cleanliness, process, path, and candidate-hash guards pass.
- `./package.sh` is the shared internal packager; do not invoke alternate assemblers.
- `./pack-test-queue.main.kts` is the persistent coordinator queue, and
  `./pack-test-lock.main.kts` is the internal single-runner mutex wrapper. Only
  `workspace_coord` may admit an immutable-candidate handoff; `pack_tests` owns queue execution,
  harness state, and test documentation.

`dist.sh` performs packaging only. Command availability, input copying, packwiz export, and
ZIP creation may fail operationally; it must not add validation, integrity, provenance,
content, schema, archive-membership, cleanliness, or correctness verdicts.

`dist.sh` remains a package-once primitive. Do not treat it as validation or run it as the routine
conclusion of an edit. A fresh tested distribution is authorized only when the user explicitly asks
for one, and must use `release.main.kts`; never rebuild between testing and publication.

## Three-tier testing policy

Use the smallest relevant focused validation for ordinary work: format parsing, targeted inspection,
and `./test.main.kts dev`. Dev runs the fast Minecraft-free contracts and `git diff --check`;
it needs no candidate and does not update tracked Packwiz hashes while developers share the
workspace. Only fresh-dist preparation in `release.main.kts` runs `packwiz refresh`, followed by
`git diff --check`. Authoring, deployment, and packaging steps must not run it independently.
Do not build a distribution or run `dist` or `debug` because a change appears runtime-sensitive.
Pack-level testing and fresh distributions still require an explicit user order.

Custom-mod changes still run that repository's documented local verification, but do not authorize
cross-repository builds, JAR deployment, pack refresh, packaging, or pack tests. When pack testing
was not ordered, say in the handoff that it was intentionally omitted under this policy.

Before starting a pack test, inspect existing `generated/test-evidence/` runs. Reuse evidence that
already answers the diagnostic question. Each explicit pack run must keep its run ID, command,
candidate hashes, named failures and aborts, first useful diagnosis, complete logs, screenshots,
runtime snapshot, lifecycle/archive evidence, process diagnostics, and retained failed-fixture path.
Never report only that tests failed, delete a failed fixture, rebuild the candidate, or rerun an
expensive suite before inspecting its evidence. Confirm whether child processes were cleaned up so
another agent can safely continue.

Dist, Debug, and releases share the same kernel-backed mutex. Invoke them only
through `test.main.kts`, `release.main.kts`, or `pack-test-queue.main.kts run-next`; direct
heavyweight Gradle tasks reject calls without the inherited lock token. Contention is fail-fast
with exit code 75 and published owner metadata. A product lane must register its dependency with
`workspace_coord` and become idle for callback, never block a shell or poll the mutex. Queue
admission requires `bc.pack_test_handoff.v1`, explicit authorization, an allowed selector,
producer/callback identities, modpack and repository state, exact candidate paths and hashes,
producer validations and artifacts, ordered dependencies, scenarios, and prior evidence.
Queue selectors are `dist` and `debug`.

Superseded evidence may be pruned only through the guarded maintenance command. It retains evidence
matching the current candidate, the newest passed evidence for any suite missing from that run, and
failures with no later passing result.

Dist runs Dev checks, validates the exact packaged candidate pair, and uses a real full-pack
client to visit one fresh location in every directly teleportable loaded dimension. Each visit
requires a client heartbeat and one 10-second TPS sample; a low sample triggers three consecutive
passing samples. Font-only destinations require their direct-travel denial guard.
Dist also audits logs and candidate hashes. Debug uses the same candidate hashes and adds
dedicated-server runtime data and lifecycle, three fresh locations per directly teleportable
dimension with strict three-sample TPS, single-player startup and world save/reopen, the 30-minute three-client
campaign soak, restart/reconnect, and native Font round-trip scenarios.
Further scenario expansion still requires an explicit user order.

Automated tests must not synthesize mouse movement or mouse clicks. UI flows that require pointer
interaction are manual visual gates; keep them documented and out of the automated harness.

The narrowly scoped `bc.crafting_policy.v1` contract is an authorized content policy, not a
general audit utility: its KubeJS startup check must reject unknown loaded namespaces and its
runtime recipe reporting may name exact cut-family leaks and live consumers. Keep it in
`kubejs/config/` and KubeJS scripts; it does not authorize a `tools/` tree or unrelated checks.
The former standalone quest-layout harness is obsolete and is not required. Teaching now spans
Threads, loading screens, and other maintained surfaces; review their actual content and behavior
without recreating the obsolete harness or inferring permission for pack-level suites.

## Tools and quarantine
The entire former `tools/` tree is quarantined. Do not recreate an active `tools/` directory.
`quarantine/` is unsupported and removable. Active runtime content and supported scripts
must not depend on or include it. Do not restore or invoke quarantined code unless the user
explicitly reverses this decision. Custom mod sources live in independent repositories under
`/home/dev/mod_source/` and must not be recreated here or at the workspace top level.

## Runtime safety
Treat pre-existing changes as user-owned. Do not delete player worlds, saves, logs, crash
reports, screenshots, profiler data, or launcher state unless explicitly asked. The tracked
root `options.txt` remains the client-default source.

## Learning-surface authoring

Read `docs/learning_surfaces.md` before changing player teaching. Maintained surfaces are loading
lessons, Threads, tooltips, EMI/Ponder, native guides, and contextual HUD feedback. FTB Quests and
its custom integration are retired; do not recreate quest graphs, completion ledgers, or compilers.

State the intended player action and choose the surface that can teach it at the useful moment.
Trace every mechanical claim to the actual recipe, event, tag, or implementation. Preserve domain
events consumed by Threads and avoid duplicating rewards or progression state across surfaces.

Coordinate overlapping content and integration edits through the lane claim files. Before handoff,
report the player-visible change, affected surfaces and triggers, local verification, and the
explicitly ordered pack-suite result or that pack testing was intentionally omitted.
`./test.main.kts` remains the supported pack-test facade, subject to the explicit-order policy.
