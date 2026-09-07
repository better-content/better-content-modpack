# AGENTS.md

## Scope
This repository is the Better Content Forge 1.20.1 modpack content layer.

## Active scripts
- `./dist.sh` creates versioned CurseForge/client and server-content ZIPs under the
  canonical ignored `dist/` directory. It accepts no output-directory override.
- `./test.main.kts` is the supported granular evaluation facade. It requires an explicit
  `fast`, `candidate`, `server`, `multiplayer`, `singleplayer`, or `all` selector.
- `./release.main.kts` is the only fresh-dist workflow. By default it reuses unchanged bundled
  runtime JARs whose source revision matches and validates/rebuilds changed repositories, then
  packages exactly once and runs `all`.
  `--skip-tests` is allowed only when the explicit fresh-dist request prohibits tests; it still
  rebuilds and stages every active runtime JAR and packages exactly once.
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

## Frugal testing policy

Use the smallest relevant focused validation for ordinary work: format parsing, targeted inspection,
`packwiz refresh` when metadata changed, hashes, and `git diff --check`. Harness changes run
`./test.main.kts fast`. Do not build a distribution or run `candidate`, `server`, `multiplayer`,
`singleplayer`, or `all` because a change appears runtime-sensitive. Pack-level testing runs only
when the user explicitly orders it or names a pack suite. Fresh distributions run only when the
user explicitly orders a fresh dist.

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

All heavyweight pack selectors and releases share the same kernel-backed mutex. Invoke them only
through `test.main.kts`, `release.main.kts`, or `pack-test-queue.main.kts run-next`; direct
heavyweight Gradle tasks reject calls without the inherited lock token. Contention is fail-fast
with exit code 75 and published owner metadata. A product lane must register its dependency with
`workspace_coord` and become idle for callback, never block a shell or poll the mutex. Queue
admission requires `bc.pack_test_handoff.v1`, explicit authorization, an allowed selector,
producer/callback identities, modpack and repository state, exact candidate paths and hashes,
producer validations and artifacts, ordered dependencies, scenarios, and prior evidence.

Superseded evidence may be pruned only through the guarded maintenance command. It retains evidence
matching the current candidate, the newest passed evidence for any suite missing from that run, and
failures with no later passing result.

The granular suite preserves the package, dedicated-server/runtime-data/lifecycle, multiplayer
connection, single-player startup, log-policy, and candidate-hash boundaries. It does not authorize
unrelated audits, performance budgets, persistence matrices, or gameplay scenario expansion.

Automated tests must not synthesize mouse movement or mouse clicks. UI flows that require pointer
interaction are manual visual gates; keep them documented and out of the automated harness.

The narrowly scoped `bc.crafting_policy.v1` contract is an authorized content policy, not a
general audit utility: its KubeJS startup check must reject unknown loaded namespaces and its
runtime recipe reporting may name exact cut-family leaks and live consumers. Keep it in
`kubejs/config/` and KubeJS scripts; it does not authorize a `tools/` tree or unrelated checks.
Questbook visual authoring is the sole exception: use the Minecraft-free sibling harness at
`/home/dev/ftb-quests-layout-harness/standalone` to render and inspect live FTB Quests chapter
layouts and to run its icon audit against an available reference-client atlas. These are focused
authoring checks, not permission to run pack-level suites.

## Tools and quarantine
The entire former `tools/` tree is quarantined. Do not recreate an active `tools/` directory.
`quarantine/` is unsupported and removable. Active runtime content and supported scripts
must not depend on or include it. Do not restore or invoke quarantined code unless the user
explicitly reverses this decision. This quarantine applies to the pack-local former `tools/`
tree, not to `/home/dev/ftb-quests-layout-harness`, which is the supported questbook layout
renderer. Custom mod sources live in independent repositories under
`/home/dev/mod_source/` and must not be recreated here or at the workspace top level.

## Runtime safety
Treat pre-existing changes as user-owned. Do not delete player worlds, saves, logs, crash
reports, screenshots, profiler data, or launcher state unless explicitly asked. The tracked
root `options.txt` remains the client-default source.

## FTB Quests authoring protocol

Treat `config/ftbquests/quests/` as hand-authored runtime content. Generative quest graphs,
generated quest directories, and quest-storage compilers are prohibited and must not replace
live SNBT. Read `docs/questbook_standards.md` before changing the questbook.

Before editing, state the intended player action, the task that proves it, and every literal
prerequisite. Inspect the entire affected chapter plus any linked source quests, reveal rules,
or KubeJS/custom-mod criteria. Do not infer mechanics from quest prose alone; trace the recipe,
event, tag, criterion, or integration that implements them.

Preserve chapter, quest, task, and reward IDs when their meanings remain the same. For new IDs,
use unique uppercase 16-digit hexadecimal values and search all live and stored quest content
before assigning them. A native quest link points to its authoritative quest; never duplicate
its task, reward, or completion state. Do not reorder unrelated SNBT or rewrite whole chapters
for a local change.

Coordinate overlapping quest edits at chapter granularity through the lane claim files. A claim
must name every chapter and cross-cutting integration file in scope. Before handoff, report:

- the player-visible behavior changed;
- IDs added, removed, or repurposed;
- dependencies, links, visibility, criteria, and rewards affected;
- supporting runtime files changed outside `config/ftbquests/`;
- the explicitly ordered pack-suite result and evidence path, or that pack testing was intentionally
  not run under the frugal-testing policy.

Do not invent a second quest compiler, schema, linter, audit, or validation command. Review SNBT
and presentation according to `docs/questbook_standards.md`; use only the sibling standalone
layout harness's documented render and icon-audit commands for static visual authoring.
`./test.main.kts` remains the supported pack-test facade, subject to the explicit-order policy.
