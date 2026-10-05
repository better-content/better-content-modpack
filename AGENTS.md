# AGENTS.md

## Scope
This repository is the Better Content Forge 1.20.1 modpack content layer.

## Active scripts
- `./dist.sh` creates versioned CurseForge/client and server-content ZIPs under the
  canonical ignored `dist/` directory. It accepts no output-directory override.
- `./test.main.kts` is the supported three-tier evaluation facade. It requires an explicit
  `dev`, `dist`, or `debug` selector. Full direct `debug` routes through forced fresh-dist
  preparation, complete Dist, and Debug on the resulting unchanged ZIP pair. `dist` and
  `debug` also accept a focused `--target`; queued full Debug uses `--existing-candidate`
  to preserve its immutable-candidate handoff.
- `./release.main.kts` is the only fresh-dist workflow. By default it verifies and rebuilds
  every active custom mod from clean local source, then refreshes Packwiz hashes, packages
  exactly once, and runs complete Dev and Dist. Its `--debug` mode adds Debug on the same
  candidate hashes.
  For explicitly ordered bug fixing, `--target TARGET` packages once and runs focused validation;
  the candidate still needs full Dist and Debug on unchanged ZIP hashes before signoff.
  `--skip-tests` is allowed only when the explicit fresh-dist request prohibits tests; it still
  reuses unchanged JARs, builds/stages changed sources without verification, and packages exactly once.
- `./maintenance.main.kts audit` reports evidence retention decisions without changing the
  workspace. `./maintenance.main.kts prune --apply` removes only superseded test evidence and
  redundant distribution staging after repository-state, process, path, and candidate-hash guards pass.
  It preserves dirty repository files and records their fingerprints before and after pruning.
  Use `./maintenance.main.kts prune --resume TRANSACTION_ID` only if a validated deletion was
  interrupted; inspect its transaction manifest first.
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
Use `./test.main.kts debug --target TARGET --retry-of RUN_ID` for a focused retry after diagnosis.
Targets use fresh fixtures, candidate contracts, the relevant runtime assertion, hash checks, and
process cleanup, while retaining separate `target-*` evidence. They do not replace a full-tier
pass. A source change that needs a new candidate may use `./release.main.kts --target TARGET
--retry-of RUN_ID`; this packages once without a full Dist run during the bug-fix loop. Run the
full requested tiers once on the final unchanged candidate.

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

Dist runs Dev checks, validates the exact packaged candidate pair, and verifies that one real
full-pack client joins a fresh dedicated server and responds to a server-command heartbeat.
Dist also audits logs and candidate hashes. Full direct Debug first rebuilds and tests all 45
active custom mods from clean source checkouts and creates one new candidate; targeted and
queued Debug keep their existing candidate. Debug uses its Dist candidate hashes and adds
dedicated-server runtime data and lifecycle, three fresh locations per directly teleportable
dimension with strict three-sample TPS and Font-only direct-travel guards, single-player startup
and world save/reopen, three live client campaigns, restart/reconnect, and native
Font round trips for Bumblezone, Aether, and Nether. Debug also requires client-side
arrival evidence and bounded native block-family counts in every classified terrain dimension;
space and utility dimensions retain travel checks without a terrain threshold.
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

Read `docs/better_discovery_guides.md` before changing player teaching. Maintained surfaces are loading
lessons, Threads, tooltips, EMI/Ponder, native guides, and contextual HUD feedback. FTB Quests and
its custom integration are retired; do not recreate quest graphs, completion ledgers, or compilers.

State the intended player action and choose the surface that can teach it at the useful moment.
Trace every mechanical claim to the actual recipe, event, tag, or implementation. Preserve domain
events consumed by Threads and avoid duplicating rewards or progression state across surfaces.

Coordinate overlapping content and integration edits through the lane claim files. Before handoff,
report the player-visible change, affected surfaces and triggers, local verification, and the
explicitly ordered pack-suite result or that pack testing was intentionally omitted.
`./test.main.kts` remains the supported pack-test facade, subject to the explicit-order policy.
