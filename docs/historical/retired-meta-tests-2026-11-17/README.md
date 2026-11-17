# Retired meta-tests — 2026-11-17

## Current active suite: 309 methods, down from 481

The user requested pruning the Kotlin tests by maintenance value, while preserving the actual new in-game scenarios. Two passes removed **172 test methods from active compilation/execution**, leaving **309 methods in 73 test files**. These are Kotlin `@Test fun` declarations, not executed/passing results. Custom-mod, native Java, Python and Node tests are outside this count and unchanged.

Original test bodies are preserved here rather than rewritten to pass. This archive is **not a maintained alternative suite**: there is no legacy runner, Gradle source set, automatic restoration task or release requirement. `docs/` is already excluded from packaged exports. No broad tag/package filter hides tests that remain under `src/test/kotlin`.

## Retention rule

An active check must protect a concrete behaviour, catch a consequential regression, or prevent unsafe execution. Merely being adjacent to gameplay code is not sufficient. Keep:

- Pack recipes/configuration and correct source/provider/artifact staging.
- Actual action ordering, consumption/damage/drop/inventory outcomes and numerical expectations used by the gameplay scenarios.
- Scale/TPS/duration/arrival measurements and representative sampling/non-overlap rules.
- Process timeout/cleanup, bounded input release, save/copy/restore safety and real saved-state parsing.
- Small checks preventing corrupted archives, overwritten user files, wrong packaged inputs or false runtime success.

Retire synthetic certification/approval receipts, fingerprint/identity proof matrices, duplicated evidence adapters and diagnostic bookkeeping that does not add corresponding gameplay or safety coverage. Mixed classes are split rather than keeping all their certificates just because some methods are valuable. This does not claim that all retained methods run Minecraft; many are inexpensive unit regressions for those responsibilities.

## First pass: 103 methods

The top-level `inventory.json` records 27 archived test files and one test-only direct-Gradle-launch helper, with original paths and byte hashes:

| Retired responsibility | Methods |
| --- | ---: |
| Repository authoring, run-plan/report attestation, loaded-code provenance and both worker bridges | 51 |
| Suite evidence/v2 qualification receipts | 14 |
| Checkpoint proof/witness wire formats, acquisition/branch certification, diagnostic-purpose gates and v3 qualification aggregation | 38 |

The failing Gradle Test provenance bridge remains a historical failure, not a pass.

## Maintenance-value pass: another 69 methods

`maintenance-pass/inventory.json` preserves 16 whole-file retirements (31 methods) plus four original mixed classes. The whole-file retirements cover hand-authored authentic-input evidence, framebuffer approval templates, mutation-diagnostic bookkeeping, redundant evidence adapters, a proof-only physical-harvest plan, the narrow scale-conformance adapter and checkpoint execution bindings.

Mixed-class selection removed another 38 methods:

| Original class | Active replacement | Before → retained | Kept responsibility |
| --- | --- | ---: | --- |
| `FreshSourceReleaseTest` | Same class | 21 → 11 | Provider ordering, runtime dependency pins/class-tree safety, actual Tele Soak sample/arrival/circuit validation. |
| `VisualReleaseGateTest` | `WorkspaceAndArchiveSafetyTest` | 25 → 8 | Protect dirty user files/backups; reject unsafe ZIPs and changed packaged bytes. No fabricated reviewer/session certificates remain. |
| `SuiteContractTest` | Same class | 12 → 5 | Crafting-root/dependency cycles, reachability, named resources and progression-stage constraints. |
| `ClientInputRuntimeTest` | `ClientActionSequenceTest` | 7 → 3 | Arm-before-input and bounded release ordering for onboarding, walking and inventory opening. No host-PID or SHA-certificate fixture remains. |

All **27 retained mixed-class test method bodies are byte-identical** to their preserved originals. Their assertions were not relaxed. Unused synthetic reviewer/qualification/runtime-identity fixture builders were removed from active code with the retired methods.

The earlier first-pass statement that all 147 driver/client/oracle methods remained active is superseded: this pass reviewed their value too. **117 remain**, with real action/outcome/calculation checks retained; the 30 retired methods in those areas were proof, duplicated evidence-adapter or diagnostic bookkeeping tests. All actual scenario implementations and registry entries are unchanged.

## Validation and boundaries

- First-pass `compileTestKotlin` succeeded at 04:53 UTC; log `/home/dev/.tmp/prune481-20261117/compile.log`.
- Maintenance-pass `compileTestKotlin` succeeded at 05:08 UTC; log `/home/dev/.tmp/prune481-20261117/compile-maintenance.log`.
- Both used the existing compile and pack-runner locks, `--no-daemon --max-workers=2 --no-build-cache --rerun-tasks`. Main/test Kotlin compiled; the ordinary harness JAR was an incidental dependency. **No tests, GameTests, client/server worlds or distribution packaging ran.**
- Preserved originals retain their recorded bytes. Counts distinguish retired methods from retained methods whose complete original class is archived; do not sum annotations throughout this archive as the number removed.
- Application/scenario/native/mod implementation and release gates are unchanged. This prunes active test maintenance; it does not remove the remaining optional certification implementation or declare it qualified. Historical failures and unfinished gameplay coverage remain unresolved, not converted to passes.
