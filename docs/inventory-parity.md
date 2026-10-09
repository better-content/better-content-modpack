# Native inventory retirement — audit-based acceptance

The backpack-backed overhaul is removed. Minecraft owns nine hotbar/27 main slots regardless of backpack equipment; Curios, Aether and Sophisticated Backpacks retain native authority. The journal provides client presentation/character panels only. Custom Sort/Stack/Trash Cursor/Undo are retired.

The user's revised scope supersedes the27-row qualification requirement: **audit only for inventory; remove all custom storage fixtures from implementation; keep other independent tests**. Native/third-party storage semantics are not independently requalified. Historical player ingredient/output loss remains unexplained; affected worlds are untouched.

## Exact acceptance gates

1. **Production/artifact audit:** source, registrations, dependencies and built/distributed JARs contain no retired menus/screens, aliases/appended slots, capability replacement, click/transfer/crafting override, prediction/network or EMI inventory handlers. Presentation may not replace screens/menus, move slots, mutate contents/NBT/gameplay packets/GUI scale or synthesize input. Twelve one-slot Curios policy/Aether consolidation and existing character panels remain. Theme palette, alpha/dimensions/UV/coloured pixels,64MiB cache/release/exclusions/native fallback retain their source tests.
2. **Local source/pack Dev:** changed repositories' documented checks, production-retirement/removal guards, diagnostic mapping/non-cancellation/isolation, lifecycle protocol rejection tests and scoped diff checks.
3. **Storage-free world lifecycle:** fresh seeded dedicated world boots/stops gracefully; typed Blight claims read from disk; staged integrated world reaches EMI readiness, saves marker/time and closes normally; separate client reopens with lineage state; marker equality/nondecreasing time; narrowly bound genuine verify save/exit; post-exit typed Blight preservation; logs/hashes/process cleanup. No item seed, craft action, ledger, backpack NBT/root oracle or storage reconnect/restart.
4. **Baseline complete Dist:** fast/diff, exact candidate structural/content checks, full-pack dedicated boot, real client join/readiness/heartbeat, strict logs/observational audits, unchanged candidate hashes and process cleanup. No custom storage fixture is installed or invoked.

Full Debug dimensions/campaigns/extended restart and human pointer/visual acceptance are deferred, not passed. No weaker seed/time budget/dependency set/warning policy or lucky retry is allowed.

## Removed implementation and retained diagnostics

The27-case driver, storage commands/targets, schemas/manifests/properties, backpack checkpoint, packet-action observer, combat isolation and all journal storage test build/staging/harness plumbing are deleted, not toggled off. No compatibility aliases remain. Departing source/history/support JAR and harness snapshot are preserved outside active repositories at `/home/dev/workspace_artifacts/reviews/native-storage-fixtures-retired-20261009/`. Prior storage reports retain their original outcome.

Nonfinite-rotation/Untamed-species observers now belong to separate test-only runtime diagnostics under better-runtime-diagnostics. Only `bc.pack_test.diagnostics.*` metadata is used; probes cannot cancel or replace native behavior/warnings. Copied fixture policy classifies only this diagnostic namespace and keeps blocking unknown namespaces. All retired/backend/test-support artifacts, including renamed copies and the diagnostic supplement, are forbidden in distributions.

The genuine verify-exit protocol keeps `bc.pack_test.verify_exit.v1` and exact payload fields/limits, using `bc.pack_test.verify_exit.*` run/player properties and exact `fixture/world-lifecycle` request directory. Canonical/symlink-safe paths, UUID/run/world/nonce, native reference identity, EMI readiness, loaded observation, marker/time oracle and one-shot native exit remain mandatory. Storage checkpoint eligibility is removed; lifecycle eligibility is not weakened.

## Commands and immutable release

- Journal: `./gradlew verifyFull stageRuntimeJar`.
- Runtime diagnostics: `./gradlew build`.
- Pack Dev: `./test.main.kts dev`.
- One canonical fresh candidate/focused lifecycle: `./release.main.kts --target world-save`.
- Complete unchanged prepared Dist: `BC_RELEASE_PREPARED=1 ./test.main.kts dist`.

Seal candidate/provenance once and audit actual ZIP/JAR bytes. Source/content corrections require a new candidate. Preserve first useful failure/logs/fixtures/hashes/cleanup; never relabel old failed candidates under revised criteria. Handoff lists audit scope, revisions, ZIP hashes, run IDs, cleanup, Java17 fresh-instance instructions and remaining manual acceptance. A focused pass alone is not complete Dist.

## Manual checklist and world safety

Use a fresh disposable instance/world. Review native inventory/backpack/Curios controls, crafting/EMI/recipe book, tooltips/cursor, supported/unsupported GUI interiors, coloured indicators, Stats/Plan/stances/nutrition/aspects/body/Mend, compact/wide record toggles, presentation opt-out and resource reload. No automated pointer/rendering pass is implied.

The original overhaul archive remains `/home/dev/workspace_artifacts/reviews/inventory-overhaul-retired-20261008/`; failed398–419 evidence and sealed ZIPs remain unchanged. Original411ZIPs are unavailable and must not be reconstructed. Historical413 is superseded/unsafe for important worlds.

No affected user-world access/migration/repair, existing backpack-root initialization or recovered-item claims. Back up important worlds before upgrading; fresh Java17 client/server instances only for qualification. Do not downgrade after saving with the replacement.
