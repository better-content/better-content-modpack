# Testing and fresh distributions

## Scope and authorization

This is the sole shared test/release procedure for Better Content. Standing user authorization
covers local and cross-repository builds, source/artifact checks, native Minecraft/runtime tests,
deployment, Packwiz refresh, packaging, source qualification, release preparation and Dist/Debug.
There is no separate human approval gate for these operations, their setup, or bounded
fix/diagnosis/retry cycles. This is the canonical shared rule across the modpack and custom-mod
repositories; older repository-local approval wording does not reinstate a shared approval gate. Select useful work to complete the user's goal; permission is not a
reason to run unnecessary work. This standing policy replaces earlier workspace per-operation
approval requirements and approval-only wait-for-go-ahead holds. A new explicit stop or scope
exclusion still controls the task. It does not waive correctness, provenance, process ownership,
resource locks, retention/deletion protections or honest reporting.

**Cost and coordination warnings (not human approval gates):**
- Running Dist or Debug in parallel is wasteful: duplicate preparation and competing Minecraft
  processes consume substantial resources. Aggregate related changes into one coherent candidate
  and one coordinated run rather than launching a run per helper or edit. Keep the runner mutex.
- Before launching a heavy run, check live agents and their handoffs: confirm relevant source,
  configuration and artifact producers have finished and integrate their changes first. An idle
  status alone is not a completed handoff; unrelated agents need not stop working.
- **Debug is extremely slow and resource-heavy, a last-resort tool.** Prefer focused source/unit
  checks, targeted native scenarios and Dist where sufficient. Explain why full Debug is needed,
  record its expected cost/budget and announce it before launch; do not ask for permission.

Use the smallest relevant checks and record scope, resource limits, results and retained evidence.
State when pack testing is intentionally omitted. `dist.sh` packages once; it is not validation.
Only release preparation refreshes tracked Packwiz hashes; authoring/deployment steps do not.

Docs-only changes use `python3 -B scripts/check_documentation.py` and `git diff --check` rather
than full custom-mod builds. Source changes retain the owning repository's documented gate.
Worlds/saves/candidates/evidence/caches/receipts are retained by default across tasks and
conversations under [generated-data.md](policies/generated-data.md). Retention is not acceptance;
finishing a task closes activity and deletes nothing.

## Test input boundary

Historical reports, audit prose, catalogue dumps, screenshots, worlds, or a latest-pass directory
must not become undeclared fixtures or golden baselines. Required test inputs are authored/versioned
fixtures, source/configuration, or reproducibly fetched upstream artifacts verified against
committed pins. Declare and prepare those inputs explicitly so an empty output/cache workspace
can run the documented gate.

Tests may inspect outputs created by the current invocation to verify the system under test.
Provider/support JARs and verification checkpoints may be retained and reused only after the
canonical preparation path revalidates their scoped source/task/input/dependency identities and
exact bytes. Existence or historical success alone never establishes eligibility. Documentation checks establish authored
contract consistency, not proof of native behavior or runtime acceptance; native-input tests
must inspect the authoritative implementation or data directly.

## Correctness, performance and diagnostic verdicts

Correctness gates block on failed assertions, crashes, registry/script failures, actual failed
operations, unclassified ERROR/FATAL records, startup/join timeouts, candidate-hash changes and
incomplete process cleanup. Warning presence or count alone never fails correctness. A WARN
that reports an actual failure still fails on that operation, not its severity or occurrence
quota. Familiar-warning text is not an exemption for an ERROR/FATAL. Existing narrow
compatibility classifications remain independently tested; none waives a failed launch.

Runtime fixtures retain a separate unfiltered native audit log for each JVM launch. They derive
fixture-only logging XML from the candidate's production XML, preserve its presentation filters,
and empty only the extracted Log Begone suppression lists while keeping that mod installed.
Production files and ZIP bytes are not changed. Original/derived configuration hashes and audit
paths are recorded; configuration drift or a missing/non-native audit sink after readiness fails.
Restart launches use distinct retained audit files. Final strict-log, configuration and registered
artifact-integrity audits run after teardown for every runtime group and targeted retry, including
when a scenario or cleanup already failed. Each finalizer is attempted independently; additional
findings remain attached to the original failure and fail structured evidence and JUnit.
Previously suppressed ERROR/FATAL records are not grandfathered into compatibility exemptions.

OC2R 2.2.12 has two separately proved compatibility diagnostics, not a general networking/error
exemption. Its native ICMP-unavailable startup branch disables only the native echo path; the
pinned JVM fallback had an exhausted-buffer ownership defect repaired by Better Compat Fixes.
Controls preserves its original tests and additionally exercises delayed original echo tasks with
caller-storage mutation, actual TCP/UDP loopbacks, and exact loaded rootfs/scripts contents.
Its pinned reader accepts only JSON filesystem descriptors; the uppercase README is documentation,
not a failed descriptor. Only the exact null-native-return and README diagnostics can be classified,
after all four required native tests, original proof/log/provider/input hash bindings, startup and
shutdown succeed. Corrupt/data-mismatched libraries, failed fallback operations, malformed/missing
real descriptors, repeated diagnostics, missing/changed evidence and every other ERROR/FATAL stay
blocking. Raw logs remain unchanged. The gate-produced `build/compatibility/oc2r-compatibility.json`
support bundle is declared in the provider graph and bound by canonical checkpoints/provenance.
Runtime eligibility explicitly revalidates that exact support hash and candidate repair-provider
bytes and the retained original proof/receipt/latest+debug log hashes; the source controls never
stand in for current pack runtime acceptance. Intermediate live audits inspect a read snapshot,
allowing normal INFO appends without pretending the active log is immutable. Duplicate classified
records in that snapshot remain failures; teardown audits the complete final logs again. Source
proof logs remain immutable throughout. Without valid context the log policy remains strict.

Exact native Forge `DeferredWorkQueue` WARN messages reporting elapsed deferred startup work on
`main` or `Render thread` are **performance diagnostics**, not failed operations. The parser requires
the complete timestamp/logger/message, a valid mod namespace, and a positive finite duration in
`ms`, `s`, `min` or `h`. Malformed timing WARNs or other logger/thread WARNs are not automatically
correctness failures; actual failure payloads and unclassified ERROR/FATAL records remain
correctness findings. This category replaces the old per-mod duration and
occurrence allowances; it is not an `adpother` exception or a raised threshold for one candidate.

The isolated Spirit Commerce source GameTest fixture retains a causal timing classifier for
startup diagnostics, separate from correctness. An exact native `MinecraftServer`/`Server thread`
lag notice can be attributed to initial setup within a nonce-bound first-tick-end to second-tick-start
window. Attribution of a delayed notice between second-tick-end and third-tick-start requires
six ordered nonshipping clock samples and pinned native scheduling arithmetic: matching counters,
monotonic-clock brackets, schedule/catch-up increments and a conservative residual below the
native 2000 ms overload threshold. Only a lower bound on the post-first-tick setup interval is
subtracted; actual second-tick work is not. That interval may include queued work/scheduling and
is not a proven arena-only duration. Raw notices, proof eligibility and residual measurements
remain diagnostic evidence. Missing/invalid proof, repeats or later WARNs do not alone fail
correctness; actual failed operations and ERROR/FATAL records do. No standalone performance
budget is invented by this classifier. Existing assertions, workload/deadline gates and gameplay
performance acceptance remain unchanged; diagnostic attribution is not a repaired-performance claim.

Every runtime suite records `startup_performance_diagnostics`: source paths/lines, normalized seconds,
observation counts and per-mod maxima, including on failed runs. Aggregate/native logs can duplicate
observations, so timings are not summed. These records are diagnostic-only: a correctness pass is
not a claim of acceptable startup performance or representative gameplay performance.

Explicit performance gates remain separate and blocking: existing end-to-end startup/join deadlines,
heartbeats, and Debug's measured TPS requirements are unchanged. Do not turn a timing notice into
a gameplay verdict or declare a performance regression repaired from a successful retry. New
performance budgets require a documented workload/resource profile and measured acceptance criterion,
not a threshold tuned to a retained historical run. No Debug scope is implied by this separation.

## Timing observations and timeout policy

Record individual tests, suites/tasks and enclosing processes separately; a Gradle task duration
is not an individual native GameTest duration. Monotonic wallclock durations and native tick
counts are distinct measurements and must retain explicit units. Persist start and terminal
observations incrementally in durable evidence, including on failure/cancellation; a missing
terminal record is interrupted/UNKNOWN, never a successful sample. Retention and timing records
confer neither verification reuse nor runtime acceptance.

A measured hard timeout is **twice the empirical expected runtime of comparable successful
executions**. Record the sample count, selection/aggregation method, source and input identity,
environment/resource profile, setup scope and cache state supporting that expectation. Keep
setup, test, suite and process scopes distinguishable. Failed, cancelled, timed-out or otherwise
censored observations are diagnostic evidence, not successful baseline samples. Missing or
incomparable measurements mean **UNKNOWN**: do not infer an expectation by halving an existing
cap, doubling a tick count, borrowing another workload's duration or choosing an arbitrary value.
Do not silently inflate a deadline or mutate it during a running invocation. Existing fixed
readiness/cleanup/controller safety caps remain independently enforced; their presence is not
proof that an empirical twice-expected timeout has been established. New measurements require
an explicitly recorded comparable baseline before that policy can be claimed implemented.

Bounded Python supervisor and JVM `BoundedCommand` execution automatically append
`bc.timing.v1` JSONL to `<log>.timing.jsonl`; supervisor calls without a log use
`generated/timing/<uuid>.jsonl`. `BC_TIMING_FILE` can select an absolute retained output sink.
Records have a UUID `operation_id`, `start`/`terminal` events, monotonic `duration_ns`, explicit
scope/status and the scope's own `configured_timeout_ns` (`timeout_scope=scope`). Optional
`BC_TIMING_{RUN_ID,REVISION,INPUT_ID,PROFILE,ENVIRONMENT_ID,CACHE_ID}` metadata is `UNKNOWN` when
missing; a path label alone is not a validated cache/input identity. Process terminal states
include success, failure, timeout and cancellation. Missing terminals remain unknown.

The generic `gradle/test-timing.init.gradle` hook is activated with `--init-script` by integrated
source invocations. Its default sink is `<settings-root>/generated/timing/<uuid>.jsonl`, overridden
by `BC_TIMING_FILE`. It separates `gradle_task`, `junit_suite` and `junit_test` scopes; task skip,
up-to-date and no-source outcomes are not fresh test executions. JUnit outcomes preserve actual
Gradle result enums. An unfinished callback observed at build completion is `INTERRUPTED`.
`configured_timeout_ns` is the process's actual budget, a Gradle task's actual configured
`Task.timeout`, or null/UNKNOWN for JUnit; it never borrows the enclosing budget. Gradle records
separately carry `enclosing_process_timeout_ns` from `BC_TIMING_TIMEOUT_SECONDS`; process records
leave that enclosing field null. At task-graph readiness, before Gradle snapshots execution
budgets, the hook selects exact comparable task baselines and sets `Task.timeout`; a changed
selected cap at execution is rejected. `owned-task-deadlines.gradle` additionally contains an
interruption-ignoring task in its disposable Gradle JVM; `Task.timeout` alone is only an interrupt.
Supported supervisors automatically set `BC_TIMING_OWNED_GRADLE=true` for their `--no-daemon`
commands (including the trusted Crops wrapper). A known task budget without disposable ownership
fails before the task body instead of killing a shared daemon. Expiry writes a separate durable
health intent, allows two seconds for the original task callback to finish, then requests own-JVM
exit 124; an independent two-second shutdown grace permits own-JVM halt if necessary. Both graces
are separate from the exact selected task cap. Original task status remains unchanged;
`deadline_exceeded` prevents a late original success from becoming a baseline. Abrupt termination
leaves its original start unmatched, not a fabricated terminal. The generic Gradle Test listener remains observational:
its own case/suite caps are null, with `UNSUPPORTED_BY_GRADLE_TEST_LISTENER` enforcement. It does
not borrow a task deadline or claim individual-case enforcement.

For an offline report from explicit retained observations, use:

```sh
python3 -B scripts/timing_report.py --output /absolute/output/report.json /absolute/input/timing.jsonl
```

The `bc.timing_report.v1` reducer groups comparable scope/units/clock/duration boundaries,
revision/input/profile/environment/cache identities and scope-specific operation identities.
Run/operation/invocation UUIDs remain provenance, not grouping keys. It retains sample count
and provenance and uses the empirical median of **all** paired comparable successful durations;
the recommendation is exactly twice that median, with no minimum sample count, padding or
outlier removal. Missing identity/boundary/measurement yields UNKNOWN. Failures, cancellations,
timeouts, skips and nonexecution are excluded from successful baselines. Missing start/terminal
pairs make observations `INCOMPLETE`; malformed, duplicate or truncated records make them
`INVALID` and suppress all recommendations. Native ticks, native milliseconds and monotonic
nanoseconds remain separate, never converted through assumed 20 TPS.

The reducer writes serialized, atomic durable reports from bounded, locked input snapshots;
it does not discover history, change caps or authenticate producer identity claims. Report
`COMPLETE` means observation completeness only, with acceptance `NOT_EVALUATED`, not a test
pass. Own-scope cap comparisons report `UNKNOWN`, `MATCHES_2X_EMPIRICAL_MEDIAN` or
`DOES_NOT_MATCH_2X_EMPIRICAL_MEDIAN`; an enclosing cap is not an individual-test cap.
A report recommendation does not retroactively establish that an actual deadline was selected
or enforced. The separate pre-invocation selector `scripts/timing_budget.py` consumes
`BC_TIMING_BASELINES`, a JSON array of 1–64 explicit absolute **raw observation** paths. It
revalidates bounded snapshots with the reducer; persisted reports/catalogs, history discovery
and enclosing-scope caps are not baseline authority. Complete comparable successful samples
select `EXACT_2X_EMPIRICAL_MEDIAN`, preserving source/sample provenance, sample count and exact
rational expected runtime (including half-nanosecond medians). Zero/unrepresentable selections,
invalid/incomplete observations or unknown identities do not acquire an empirical cap.

Python supervision and JVM `BoundedCommand` freeze the selected positive nanosecond budget before
launch and enforce it; the effective process budget is propagated to child timing metadata.
Gradle takes one validated raw catalog snapshot per invocation, shared with the opt-in case
adapters, whose distinct boundaries require their own matching samples. Missing comparable
baselines retain existing process/task safety caps with
`UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED`; unknown case caps remain null. No deadline changes
mid-invocation, tick-to-wall conversion, padding or automatic historical baseline search occur.
Diagnostic/report/selector safety caps and teardown grace are separate from measured execution
budgets. Process observations retain their declared supervision-including-cleanup boundary;
cleanup does not silently extend an enforced execution budget.

The supervisor and Gradle build-finished hooks automatically refresh reports; explicit
`BoundedCommand` profile sinks also refresh after their durable process terminal. Incidental
captures inheriting a shared sink do not spawn a reducer per Git query. These reports do not
mean all public commands have report coverage or all configured caps comply.

Root harness tests and integrated source profiles opt into `scripts/timing-python/sitecustomize.py`.
It records actual standard unittest callbacks as `python_test` and real suite invocations as
`python_suite`, without result proxies or changed original arguments/returns. Expected failures,
unexpected successes, skips and unknown outcomes remain distinct, nonbaseline case outcomes.
One lazy canonical catalog snapshot supplies exact comparable case/suite 2× budgets in memory;
UNKNOWN own caps stay null. Scalar deadlines durably record timeout/unhealthy intent and signal
only their own Python process through its unchanged SIGTERM handler. After a separately recorded
two-second cleanup grace, `_exit(124)` contains a still-unreturned boundary; an already-returned
boundary cancels escalation. Neither sys.path/argv nor preexisting reporter modules are replaced.
The successful `TextTestRunner` path fails closed on observer errors; manually consumed custom
results outside that runner do not have equivalent acceptance authority. Process selectors
`BC_TIMING_PROCESS_NAME`/`process_name` are invocation-local: the reducer uses one only with a
known SHA-256 input binding, retaining real cwd/argv hashes as raw provenance. Selectors are not
inherited by arbitrary nested commands.

The source-only JUnit Platform adapter (`gradle/junit-test-timing.gradle`, `src/junitTiming/`)
uses the engine service SPI, not the generic Gradle listener, for individual `isTest` start/terminal
callbacks, including parameterized/dynamic cases. `BC_TIMING_JUNIT=true` opts in; integrated source
profiles set it explicitly. Root enables it automatically with `bcTimingJunitEnabled`, including
idempotent init-script plus Project application. Runtime attachment occurs after the owning Test
configuration so a later classpath assignment cannot discard the service adapter. It retains original engine invocation/thread/results and distinguishes
its `junit_platform_executionStarted_to_executionFinished` boundary from Gradle callback timing.
Skipped/aborted/nonexecuted cases are not successful samples. Exact own-case baselines enable a
worker-local wall watchdog; UNKNOWN-only cases schedule none. Engine-authored timeouts remain
independent. Fresh task-nonce health receipts must be balanced and healthy for every plan; only
Gradle-owned plans are compared with actual Gradle case counts. Tests launching an enclosed
Platform plan still emit every case, with the distinct
`junit_platform_enclosed_executionStarted_to_executionFinished` boundary, so neither count nor
baseline authority is borrowed between plans. Scalar admission retains no plan/test objects, and
enclosed observer failure marks the worker unhealthy. Missing, disabled or broken SPI cannot silently pass. Observer findings are suppressed onto an existing
test/task failure rather than replacing it.

The source-only native GameTest adapter (`gradle/native-test-timing.gradle`, `src/nativeTiming/`)
is attached with `BC_TIMING_NATIVE=true`, also set by integrated source profiles. The init hook
applies it before Forge RunConfig generation. It attaches a nonshipping sourceSet to exactly one
existing main-owning mod for each selected GameTest run, never an overlay mod or production
source dependency. Ordinary helpers stay outside the reserved `.mixin` package. Exact reviewed
SDK class hashes/lifecycle selectors, archive exclusions and actual ZIP-entry checks, plus a fresh
balanced healthy post-task receipt, are mandatory. Original reporters, arguments, results and
Throwable identity remain intact. Observations cover individual attempts, not final flaky-test
acceptance; native ticks/stopwatch milliseconds/monotonic nanoseconds stay separate. Comparable
own-attempt wall baselines enable scalar-only deadlines without changing native tick caps;
UNKNOWN leaves native timeout behavior intact. Timing helpers retain no World, block-entity or
RNG references and perform no gameplay calls.

Both case watchdogs arbitrate deadline/terminal races: original failures delivered before expiry
remain failures, and a late terminal cannot become a successful baseline sample. Expiry durably
records TIMED_OUT, pending interruptions, unhealthy receipt and shutdown intent, then exits only
the owned JVM with status 124. A separately recorded fixed two-second shutdown-hook cleanup grace
permits owned-JVM halt escalation; it is not added to the selected case budget. Scheduler/write
latency is not a hard-real-time guarantee. Observer classes/resources/service entries are excluded
from production/source archives; native and JUnit adapter archive checks remain required.

Focused raw-selector/process/Python controls, 93 focused Root tests, full Dev's 574 original JUnit
cases plus four enclosed engine probe observations, and nine actual Gradle task controls passed.
The four probe outcomes remain separate from the 574 passing Gradle cases. Actual Gradle engine
controls verified original success/failure/skip, disabled-SPI rejection, archive helper-byte
exclusion, and measured 95,866,141 ns case execution selecting/enforcing exactly 191,732,282 ns,
including hanging-shutdown containment. Controlled
Notices/TNT source compilations overlapped in private workers; the unchanged TNT native test
executed/passed with a fresh balanced healthy timing receipt, clean native-log audit and helper-free
JAR/reobf outputs. Evidence is retained in
`generated/test-evidence/fresh-fixes-20261023/retool-finish/` (`unit-6`, `root-dev-3`,
`gradle-controls-1`, `junit-gradle-3/result.json`, `real-2.source/proof.json`). Earlier failures
remain retained: isolated discovery needed real JSON runtime dependencies, fake argv expectations
needed the timing init hook, sealed adversarial fixtures needed explicit owner-level corruption
to reach the unchanged hash/provenance rejection assertions, and enclosed engine probes needed
distinct count accounting. Archive empty directories are not helper bytes, Gradle captures
shutdown stdout in binary results, and TNT explicitly disables the native class-name prefix. These controls do
not qualify all 46 providers or establish distribution/gameplay acceptance or natural throughput. Packaged runtime/Dist native-case coverage is not
implemented by these source-only adapters. Terminal-only XML is not durable start/censor evidence.
Legacy caps without comparable explicit raw baselines remain UNKNOWN, not universally empirical
2× compliant; no production expected-runtime baseline or release acceptance is inferred.

## Failure diagnosis and automated visual review

A failing experimental scenario is evidence to diagnose, not an instruction to change production
behavior until green. Classify each finding as **product defect**, **harness defect**,
**requirement mismatch** or **environment limitation**, with its first failing run/candidate,
expected contract, actual observation and causal evidence. A timeout alone does not identify the
cause. Preserve the initial evidence and record the reason for each assertion or fixture change.
Do not relax an assertion, log classification or performance threshold merely to fit a run.
Controlled negative/fault cases should demonstrate that the repaired oracle still detects the
violation; a synthetic harness pass is not native gameplay acceptance.

Automated visual acceptance requires an LLM to inspect the actual retained Minecraft framebuffer
images, with explicit feature criteria and candidate/support/image hashes. Inspect readable labels,
item/fluid distinctions, layout/clipping/overlap, native menu boundaries and the expected visible
state. EMI review includes promoted category/recipe ordering and explicit-focus behavior; bee review
includes occupied/active versus empty/inactive and reduced/disabled states; drink review includes
plain/specialty identity and legibility. The retained bee species pair is native forest/plains,
including same-body nest/queen change and invalidation. Independent server species/body-color
receipts bind typed client100-tick windows and their actual frames; plains uses its body color,
not its distinct nest color. Color criteria name native RGB authority, not byte-identical raster
pixels under lighting. Each pair retains one shared20-minute natural eligibility bound; no clock,
weather or queen-condition bypass, and the original controls/dense/unload/reconnect remain required. Shader/campfire review covers the real palette/phase matrix,
not just API state. Both personal-inventory paths and relevant separate native backpacks remain
mandatory under [inventory parity](inventory-parity.md). Missing, ambiguous or defective captures
block visual acceptance. Capture files, counters, mocked previews and placeholder browser images
cannot stand in for image inspection. Record human-pointer and physical-listening checks as
`NOT_PERFORMED` unless actually exercised; they are separate nonblocking verdicts, not inferred passes.

## Public tiers

The public test interface has three cumulative tiers. Dev needs no packaged candidate and can
run while other developers share the workspace:

```sh
./test.main.kts dev
```

Dev runs the fast Minecraft-free contracts and `git diff --check`; it does not refresh Packwiz
hashes. Cold-cache source inspection rehydrates only the native JAR/ZIP archives named by its tests through
`scripts/prepare_dev_inputs.py`, verifies each active pin's hash and exercises that rehydration's
synthetic tests. These downloads remain generated dependencies retained by default; no Minecraft launch, deployment
or distribution is performed. Fresh-dist preparation refreshes them once after source and pack changes settle.
The structure-loot Dev contract retains its exact ownership/pin/protected-pool checks and all
20,000 seeded samples per changed pool with unchanged tolerances. Its independent upstream table
baseline re-verifies every pinned archive without repeating the full template/class/worldgen scan;
it never borrows effective KubeJS table overrides. Synthetic reader controls cover override priority,
missing/mutated inputs and malformed upstream data. These source checks do not establish native
loot composition, placement, inventory delivery or performance acceptance. Report JUnit cases
separately from the Python/Node guards they invoke; guard counts are not additional JUnit cases.
Pack-level tiers are available without separate approval; apply the aggregation, live-agent and
Debug last-resort warnings above before choosing a heavy run:

```sh
./test.main.kts dist
./test.main.kts debug
```

Agents can use `test-control.main.kts` for JSON lines with stable run IDs and evidence paths.
For `run source --repo NAME` (or `--all`), active providers and their dependency closure use the
same internal Root owning-gate backend as fresh releases: forced fresh builds, every mandatory
verification profile in manifest order with ordinary/default staging last, strict native-log audit,
support hashes and the common `bc.custom_mod_source.v1` source stamp. Only that requested closure
must have clean, unchanged source HEADs; unrelated providers do not gate a selected source run.
Fresh run-local `source-jars` and `source-verification/result.json` bind successful provider stages,
source identities and consumed provider hashes; failed dependencies block consumers, with no
historical/bundled-stage fallback. Existing stages, unsafe/symlink paths, missing outputs and changed
sources are refused. These are independent source task qualifications, not canonical verification
session/epoch checkpoints or pack/runtime acceptance. They do not deploy, refresh or package anything.
The two supported non-active legacy trees remain validation-only and receive no qualified runtime JAR.
Root host orchestration holds no outer Forge cache lock; the shared build backend owns each actual
source Gradle invocation's cache lock. The legacy validation branch retains its existing lock.
`run` prints a running record immediately, then a final record; `status` and `wait` work from
another terminal while it runs. `wait` returns 4 when its bounded timeout expires or a completed
full Debug awaits visual review, 1 for a failed run, and 0 for a passed run. `awaiting_review` is
not a runtime failure or an accepted distribution. `evidence` lists recorded files. For example:

```sh
./test-control.main.kts list
./test-control.main.kts plan debug
./test-control.main.kts run source --repo better-regolith-farming
./test-control.main.kts run debug
./test-control.main.kts status RUN_ID
./test-control.main.kts wait RUN_ID --timeout-ms 30000
./test-control.main.kts evidence RUN_ID
./test-control.main.kts retry debug --target font:aether --retry-of RUN_ID --candidate-binding /absolute/path/to/release/provenance.json
```

`run source --all` verifies the active manifest repositories plus the two supported validation-only
repositories in dependency order and stages provider JARs for dependent builds. Retired Ratlantis
is not offered by source control; checkout count is not a build requirement. Full Dist and Debug use the same fresh release path as the
direct tier commands below. `submit --handoff /absolute/path/to/handoff.json` sends a prepared
immutable-candidate request to the shared queue.

Direct full Dist and Debug require clean active custom-mod repositories. Authorized preparation
revalidates retained provider checkpoints and verifies affected providers and their transitive
consumers, clean-rebuilding whenever exact build-only reuse is ineligible. With no eligible
checkpoints, or with `--rebuild-all`, it runs every active mod's verification tasks; matching embedded source metadata
alone cannot skip verification. Before an existing clean build, the canonical preparer atomically
relocates complete prior SDK build outputs (including nested Gradle modules) and explicit legacy
`run-gametest` / `run-gametest-validation` instances into that release's `prior-source-outputs/`
evidence. Ordinary developer `run`, visual and datagen instances are neither moved nor traversed. Reverification that reuses compiled
outputs retains those build trees in place but isolates old native instances. A TSV records completed
relocations; none confer acceptance. Tracked inputs, symlink output/destination roots, existing
destinations and unsupported cross-device moves fail closed before Gradle; there is no delete fallback.
Where declared, ordered `verificationProfiles` run the owning repository's public Gradle tasks and
project properties under the selected whole-invocation cache/workspace locks, with separate logs/retention roots
under `source-profiles/<repository>/<profile>/`. They must retain every ordinary/default task and
stage the final artifact with default properties. Profile tasks, properties, ordering and clean policy
participate in both Kotlin and Python checkpoint fingerprints; a default-only receipt cannot stand
in for a required optional-present/absent matrix. Multi-profile recipes rebuild rather than treating
restored compiled bytes as profile verification. Client visual profiles remain separate acceptance.

WFT's adult-size/manual-growth-pulse-versus-fixed-reference calibration category is retired,
including the legacy/v2 fixtures, optional-tree reference grow-outs and their diagnostic/matrix
qualification prerequisites. It must not be restored through an optional profile or an indirect
source task. Its retirement changes test scope, not production growth rates or reference values.
Both ordinary optional-present/absent `verifyFull` profiles remain required, including functional
pulse cadence, constant normalization, persistence and the other native integration cases; final
staging still uses default properties. The owning Source inventory defines the remaining native
identities. Historical calibration failures/worlds remain retained, not reclassified as passes.
Source/verification-input changes invalidate affected checkpoints through the normal fingerprints;
new qualification must execute the remaining gates before fresh packaging and same-artifact Dist.

Cave's two long native suites are **Debug-only**: `DepthDirectorWorldGameTests.realCataloguePlayersAndNaturalDepthCadence`
(80,000 ticks) and `GeologicalWorldGameTests.pinnedGeologyCatalogueLineagesAndNaturalPopulations`
(240,000 ticks). Source/Dist preparation requests only Cave `verifyFull` and `stageRuntimeJar`,
retaining its short unit/parser/ABI/JAR checks, native parser startup and strict native error gate.
Neither long suite may return through an indirect source prerequisite. Explicitly ordered Cave
`./gradlew verifyDebug` reaches the existing `verifyWorld` task and both unchanged suites; this
routing change is not evidence of execution. Their current source/Dist status is **DEFERRED / NOT RUN**,
not PASS; the earlier World timeout and all worlds/logs remain evidence. Debug's boundary inventory
records them as uncovered until explicitly executed and admitted, not covered by ordinary source
qualification. This narrow tier decision waives neither other providers nor final same-artifact Dist.

After each source invocation, the preparer applies the existing strict `LogPolicy` to actual SDK
native log files (including retained gzip archives and discovered nested modules), before artifact
staging/annotation, source checkpoint creation or deployment. This catches failed operations at
WARN as well as ERROR/FATAL, including native `LootModifierManager` decode failures and `LevelChunk`
failed/DUMMY block-entity loads (including reverse-worded “tried … but failed” records), and the
exact `Species` unsupported-rooty-soil diagnostic. That native method attempts a dirt fallback and
returns false; detecting it does not assert that the entire tree aborted. A Gradle zero exit and
passing test counts cannot conceal that operation or missing serializers. Per-module/profile reports under
`native-log-audits/` preserve original paths/hashes and identify any expanded audit copies.
An empty or clean log report supplies no native readiness, profile coverage, gameplay or runtime
acceptance claim; owning source gates and final pack acceptance remain mandatory.
For Crops, the source invocation additionally uses `scripts/crops_source_inputs.py` inside the
same whole-invocation lock. It registers the owning snapshot/raw-cache retention scopes before
Gradle, rehydrates only the reviewed recipe's named raw parents from actual Root pins, and passes
explicit `BC_CANONICAL_PACK_ROOT`, `BC_CANONICAL_ARCHIVES`, `BC_CANONICAL_ACTIVITY` and
`BC_OWNER_SESSION` bindings. The owning ordinary native task automatically prepares its fresh
immutable canonical snapshot; no conversation-created snapshot or sibling-checkout inference is
required. Existing anomalous cache bytes fail closed and remain retained. Failed/interrupted
invocations retain active activity for quiet/recovery; successful finish deletes nothing and is
not an acceptance verdict. These bindings do not enable an optional weaker fixture or alter
ordinary tasks, source-only/production archive exclusions, required codec/data assertions or logs.
The canonical preparer stages and deploys the coherent set, refreshes Packwiz and packages once. Runtime suites still use fresh
fixtures; the receipt never replaces pack acceptance. Release evidence binds tier IDs and hashes.
Unbound candidate discovery binds the exact `pack.toml` `Build N`/lettered version to its client
and server ZIPs under `dist/build-N.../`; older builds may coexist. Missing/partial current builds,
invalid/stale version metadata, unexpectedly newer build directories and symlink paths fail
instead of falling back to an older complete or previously passing pair. Explicit bindings and
queued handoffs take precedence over current-version discovery and select only their exact
paths/hashes; binding, candidate and support paths reject symlink ancestors.

### Persistent source verification and conversation controls

Use the coordinator's stable conversation ID (`PI_SESSION_ID` in Pi; explicitly set
`BC_OWNER_SESSION` for a launcher without that identity), never a pane or PID:

```sh
./test-control.main.kts session begin --owner-session "$PI_SESSION_ID"
./test-control.main.kts session status SESSION_ID
./test-control.main.kts session admit SESSION_ID --helper-session HELPER_CONVERSATION_ID
./test-control.main.kts session revoke SESSION_ID --helper-session HELPER_CONVERSATION_ID
./test-control.main.kts session begin --owner-session "$PI_SESSION_ID" --replace IDLE_OWNED_SESSION_ID
./release.main.kts --debug --verification-session SESSION_ID
./test-control.main.kts run source --all
./release.main.kts --debug --verification-session SESSION_ID --rebuild-all
./test-control.main.kts session end SESSION_ID
```

Opening a receipt or ordinary source/Dev checks does not establish a verified preparation
baseline; standing authorization permits useful preparation without another approval. Independent `run source` checks are explicitly noncanonical and do not consume or
advance release checkpoints; their runtime acceptance is `NOT_EVALUATED`. They reject
`--verification-session` rather than accept an ignored receipt flag. Use canonical release
preparation with the owned session for checkpoint creation and revalidation.
Canonical preparation checkpoints each provider only after its complete verification tasks,
artifact checks and input rechecks succeed. A later provider failure does not discard those
checkpoints, but only successful complete preparation establishes a deployable coherent set.
Without an explicit session, tested preparation creates an invocation control receipt, publishes
its finalized attempt and retains it on completion/failure. `--skip-tests` cannot consume or
create verified checkpoints.

The bounded `bc.testing_session.v2` control receipt in `.worklane/testing-sessions/` records owner,
generation, shared input fingerprint, per-repository source identity, verified JAR/provider hashes,
tasks and verification IDs. Finalized immutable attempts live under `completed/`; verified
production/support byte snapshots live separately under `artifacts/`. Neither receipt nor
checkpoint metadata embeds worlds or logs. New conversations may import eligible checkpoints
from finalized v2 attempts, including individually successful checkpoints from partial attempts,
but never adopt another conversation's active mutable control receipt. Original ownership and
verification IDs are preserved. v1 history remains untouched and is ineligible for import.
A failed or incomplete baseline never authorizes deployment or packaging; its individually successful checkpoints may be reused only after exact current
source/task/shared-input, production/support-byte and consumed-dependency checks. Missing or
invalid checkpoints require reverifying that provider and its transitive consumers, with
clean rebuilding unless their separate exact build identities permit build-only reuse. Legacy failed
runs without checkpoints cannot be imported from old JARs or successful-looking logs. Identity
includes HEAD, tracked/nonignored files and status; a later HEAD cannot be attached to a built
JAR. Artifact changes after a reuse decision fail rather than acquiring the previous verification
ID. Reused checkpoints retain their original verification IDs. Declared `supportArtifacts`
(e.g. the isolated native diagnostic supplement) are gate-produced, hash-bound inputs too;
missing/corrupt supplements invalidate reuse until canonical preparation restores or rebuilds
and rechecks their exact bytes. Changed providers invalidate
consumers. Per-provider manifest `verificationInputs` declare scoped external pack
contracts/upstream pins; changes invalidate that provider and its consumer closure. Common gate
implementation and shared build inputs invalidate all providers. Identities include Java release
and executable bytes, the actual Kotlin runtime JAR, and the resolved Gradle distribution's
library bytes (`bc.gradle.home`), as well as provider tasks and dependency declarations.
Embedded source metadata alone is insufficient. Exact verified-checkpoint reuse retains its
verification ID. Build-only reuse is narrower and does not reuse verification: when the exact
source/build/toolchain/dependency/support identity still matches, preparation may restore a
build snapshot and run every current verification task without an unconditional `clean`.
Only byte-identical final production/support artifacts retain the original `build_id`; successful
reverification always gets a fresh verification ID. Regenerated different bytes get a new build
ID. Source, unknown-input or build-identity changes require a clean rebuild.

Per-provider `buildInputs` default to `verificationInputs`. Explicit narrower scopes are allowed
only for proven verification-only contracts: Compat pins and Journal policy use empty build-input
lists; Commerce resources remain build inputs while its JEI/JEED verification pins do not. Shared
inputs cover the actual release verifier/controller and Gradle build/toolchain controls, not
unrelated Harness, packaging, test or queue facades. Explicit untested mode may independently
reuse source-identical bundled bytes without a verification claim. Session status adds an observational live rescan of shared inputs,
source/task identities, production/support bytes and the provider/consumer closure. It does not
advance verification or authorize reuse; preparation rechecks under the receipt lock.

After an interrupted or failed preparation, inspect the failure and confirm process cleanup, then
retry the same owned session **without `--rebuild-all`** to retain eligible checkpoints:

```sh
./release.main.kts --verification-session SESSION_ID
```

`--rebuild-all` explicitly starts a fresh verification epoch and disables archive import for
the lifetime of that active receipt: old complete-baseline records and partial checkpoints cannot
substitute for new verification, even if this fresh attempt later fails. Unchanged successful
checkpoints from that new epoch can then be resumed without the flag. Shared-contract changes
invalidate the entire set; scoped/source-only changes invalidate their providers and consumers.
Missing or modified checkpoint bytes make them ineligible rather than weakening verification;
task handoff itself does not delete them. All normal Dev/Dist acceptance,
immutable candidate hashes, strict diagnostics and process-cleanup requirements remain in force.

Same conversation identity survives compaction/reconnection. Only explicitly owner-admitted
helper conversation identities can share the active control; its ownership never changes.
Different owners are rejected from that mutable control. Cross-conversation checkpoint import
uses finalized immutable attempts instead. Explicit end refuses during preparation.
After stopping and confirming complete cleanup of an interrupted owned tree, its owner may use
`session recover SESSION_ID --confirm-terminated-run RUN_ID` to clear the abandoned active-run
marker while preserving checkpoints for canonical revalidation. Alternatively,
`session end SESSION_ID --confirm-terminated-run RUN_ID` marks its control ended without
deleting it. Both refuse if the coordinator PID still exists or the run ID does not match. That confirmation is an explicit
cleanup assertion, not inference from PID absence; helpers cannot perform owner recovery.
Recovery does not verify any artifact or authorize deployment. End unused active controls
explicitly. Admission/revocation and replacement refuse an active coordinator. End/replacement
is owner-only and marks controls ended while retaining their files, completed attempts and artifact
snapshots; a helper cannot transfer ownership or revive a receipt. Uncertain owner liveness
requires explicit owner reconciliation, never mutable-control adoption. Receipts are retained
across handoffs by default; task completion does not remove them.
Synthetic receipt tests do not establish completed cold source-build or Debug acceptance.
The two Librarian snapshot users pin the exact timestamped implementation via plugin resolution,
not merely a dated marker that still depends on a floating implementation.

After inspecting a failed run, use a targeted retry to check a fix without starting every Debug
scenario. Each retry makes a fresh fixture and records separate `target-*` evidence; it cannot be
mistaken for a full-tier pass. Supported targets are `server-ready` (Debug), `join` (Dist or Debug),
`font:bumblezone`, `font:aether`, `font:nether`, `fonts`, `dimension:<id>` for a directly
teleportable dimension, `dimensions`, `campaign-start` (three live encounters and platform repair),
`campaign` (three live client encounters), `restart-compat` (saved Flesh spread
restart and Untamed whale client spawn), `lineage-transition` (Debug lifecycle successor commit
and archive), `cursed-pyramid` (pinned seed, saved structure record,
and strict log audit in its surrounding chunks), `world-save` (seeded persistence),
`world-startup` (unseeded native integrated creation), `deep-void` (networked native entry), and
`gameplay` (observed familiarity/restart and six-fix native client acceptance; Debug only),
`plant-drinks`, `structure-loot`, `structure-loot-personal`, `bees-cosmetics`, `emi-native-order`,
`lifecycle-interactions`, and `interactions` (the native interaction families and
world-save/world-startup/deep-void targets are also Debug).
The `interactions` target runs all short native families, separate from the genuine gameplay soak.
Targets retain their scoped limitations: ordinary chest rolls do not by themselves establish
Lootr or generated-structure coverage, and manual drinking does not prove fractional reserves.
`structure-loot-personal` exercises personal rolls with two real connected players;
`lifecycle-interactions` exercises native replacement-player respawn and late-cancellation
observations, not same-object direct handler calls. The `campaign-start` fixture
uses the packaged spawn rules and removes a strip of campaign floor to verify that the
campaign-phase repair restores pathable geometry. For example:

```sh
./test.main.kts debug --target font:aether --retry-of RUN_ID --candidate-binding /absolute/path/to/release/provenance.json
./test.main.kts debug --target fonts --candidate-binding /absolute/path/to/release/provenance.json
./test.main.kts debug --target gameplay --candidate-binding /absolute/path/to/release/provenance.json
```

Targeted runs still perform fast checks and candidate validation, then start only the selected
runtime fixture and its required setup. They check candidate hashes, process cleanup, and strict
logs. Findings are recorded in the target evidence and fail that targeted run. If a source fix
needs a new JAR, use `./release.main.kts --target font:aether --retry-of RUN_ID` to package once
and test that target. A later unbound full Debug uses affected-source verification and runs complete Dist and Debug
on its new, unchanged ZIP pair.

Full retries also accept `--retry-of RUN_ID --candidate-binding PATH` without a target. The
binding is canonical tested release `release/provenance.json` with exact paths, ZIP SHA-256,
source commits and production/runtime input fingerprint. Mismatches fail rather than silently
selecting another candidate. Bound full Dist and Debug do not invoke release preparation, source rebuilding or packaging.
Bound full Debug runs a fresh pinned Dist prerequisite and complete Debug, checking the
prerequisite's exact selected candidate paths and hashes. `BC_TEST_RETRY_OF` is propagated through
that prerequisite for diagnostic lineage only, never as a pass waiver.
Harness-only edits can retain hashes; production/source changes require canonical preparation
and a new binding. Legacy provenance without runtime/support fingerprints cannot authorize reuse.
Targeted fixtures also require a binding or a coordinator handoff whose `artifacts` explicitly
pin the prepared runtime-support path and SHA-256. A leftover support JAR is not an input merely
because it exists. Prior run IDs establish diagnostic lineage, not a prior-pass waiver.

Candidate contracts parse effective server properties (including escapes), reject duplicate keys,
and compare them with authored production defaults. Server worlds, player/identity files and
lifecycle state, and client saves/state under override roots, are forbidden in candidates. Initial
extracts must be fresh before fixture mutation; contaminated candidates fail without repair or
sanitization. Intentional restart and explicitly staged persistence saves do not repeat first-launch
freshness checks. Client and server loader coordinates must agree with the supported Minecraft
1.20.1 / Forge 47.4.22 runtime; both normal and quick-play clients launch from validated manifest
pins, with no independent hard-coded quick-play fallback.

Dist includes Dev, candidate contracts, a real full-pack client joining a fresh dedicated server,
an online player heartbeat, strict logs, and candidate hashes. It does not run dimension travel.
Unbound full Debug uses unchanged candidate hashes from its newly packaged Dist run; bound full
Debug uses its exact retained candidate pair after a fresh Dist prerequisite. Both add
dedicated-server startup/runtime snapshot, three
fresh locations per directly teleportable dimension with strict three-sample TPS and Font-only
direct-travel guards, one lineage transition and archive, three live client campaigns,
server restart/client reconnect, three native Font round trips, native networked extended-bottom
Deep Void entry with negative controls, and singleplayer title screen, unseeded native creation,
and separately seeded save/reopen.

Dist, Debug, and `release.main.kts` share one kernel-backed runner mutex. The
supported entry points acquire it automatically and publish current ownership at
`$HOME/.local/share/worklane/pack-tests/owner.json`; contention fails immediately with exit code
75 and that ownership record. Dev and custom-mod repository-local checks do not use this mutex.
Do not invoke the heavyweight Gradle tasks directly: inherited authorization requires both
the actual full POSIX kernel lock and a live ancestor owner, not a token string alone. Direct
preparation also holds the runner mutex for its action lifetime.

Queue admission uses registered task producer authority, not a magic agent name. The authorized
producer supplies an immutable-candidate handoff and registers it with:

```sh
./pack-test-queue.main.kts request /absolute/path/to/handoff.json
./pack-test-queue.main.kts status
./pack-test-queue.main.kts run-next
```

Helpers do not wait inside a runner process when the mutex is owned. They register their
dependency with their task's accountable lead, become idle, and resume when that lead or the
assigned runner notifies the handoff callback. No permanent workspace-wide coordinator role is required. The `bc.pack_test_handoff.v1` document must include explicit user
authorization (`authorization.explicit`), nonblank `task_id`, positive `task_generation`,
`task_owner` naming the registered accountable lead, request ID and selector; producer and callback agent/pane identity; modpack HEAD and status; absolute
client/server paths and SHA-256 hashes; repository revisions/statuses; producer validations;
artifacts; ordered dependencies; requested scenarios; and prior evidence. Optional
`candidate_binding` carries the exact provenance path; artifacts must still bind support bytes.
Admission copies the document into the queue and rejects duplicate request IDs. Exact candidate
paths/hashes and symlink ancestry are checked before admission, at dispatch and before harness use.
Task owner, generation and selector-authorized operation are rechecked through
[workspace coordination](workspace-coordination.md) at all three boundaries. The operation is
`pack-test:dist` or `pack-test:debug` from the original selector; Debug's Dist prerequisite remains
under its Debug operation. Task registration grants these with repeatable `--operation`; an empty
grant set admits no pack tests. Producer agent identity alone confers no authority. The controller
uses `WORKSPACE_COORDINATION_ROOT` or `~/.worklane/coordination`, not the modpack root. These grants
are operator metadata, not cryptographic proof of a user conversation. The standing authorization
in this document satisfies the user-authorization condition; retain `authorization.explicit=true`
and normal owner/operation registration without asking for another approval.
Queued full Debug runs against that pinned pair and does not rebuild sources or package another
pair.

Short-lived harness commands use file-backed output, bounded execution and bounded diagnostic/
cleanup phases. Owned Linux process groups close the fast-parent-exit race for inherited-group
children, in addition to retained observed descendants. Deliberately detached, never-observed
escapees are not covered by that JVM tracker alone. Public release/test/control/lock/queue
invocations additionally use a Linux subreaper supervisor that retains PID/start identities,
reaps adopted descendants during execution and cleans them on parent loss, signal or deadline.
Its outer cap is eight hours; runtime Gradle groups are capped at four hours and Dev at twenty
minutes. Captured control/Git queries have thirty-second budgets and four-MiB output limits.
Diagnostic/TERM/KILL/reap phases are separately bounded; failed cleanup remains a failure with
retained ownership metadata. These mechanism contracts do not prove arbitrary external daemon
cleanup or Minecraft acceptance. The Node recipe-handler contract retains its 15-second budget;
passing repeated/concurrent observations cannot establish the cause of an earlier timeout.

The tiers run internal Gradle tasks `test`, `candidateTest`, `serverTest`, `multiplayerTest`,
`singleplayerTest`, `gameplayTest` and `interactionTest`. Dist sequences fast checks, the candidate
gate and the multiplayer runtime group. Full Debug is the explicitly invoked exhaustive release
gate: it includes all five runtime groups, including the observed gameplay soak/shader workload
and independently targetable interaction families. Each runtime group uses a fresh fixture.
Missing, skipped or aborted required scenarios and uncovered high-risk boundaries prevent a full
pass. [`gradle/debug-interactions.json`](../gradle/debug-interactions.json) owns the executable
boundary/scenario/visual-criteria inventory; it must account for every active custom provider.
Concrete source-native tests can satisfy precisely scoped boundaries only through canonical
verified-source provenance for the exact candidate, not arbitrary task names or old reports.

Dev writes ordinary Gradle XML and HTML reports only. Dist and Debug use a run ID and write
structured evidence beneath `generated/test-evidence/<run-id>/`: incremental JSON events, a
`bc.modpack_test_run.v1` summary, and candidate hashes. Runtime groups add logs and timeout
diagnostics; Debug also collects runtime data. The single-player group records the customized
title screen without injecting input. Failed fixtures remain retained for diagnosis across handoffs by default.
Automated tests must not synthesize mouse movement, clicks, scrolling or resizing. Threads reader
development, the Quark chat emote picker, the World Condenser configuration screen and the Create
World menu require actual native capture review and native action/behavior verification; they do
not require a blocking human-pointer verdict. If the harness cannot exercise the native action
without synthetic pointer input, the behavior remains unverified rather than being waived by
a capture. Human interaction, when not performed, is explicitly `NOT_PERFORMED`. The seeded persistence probe's
fallback marker isolates persistence; it is not production spawn-search acceptance. The separate
unseeded probe creates an absent normal world with a native random seed, observes the production
resolved marker, uses the typed production onboarding action, and requires Survival and forty
correlated native ticks before native disconnect/save. It does not author a spawn marker or test
the Create World GUI. Its profile uses twelve-GiB heap/eight processors/software rendering while
preserving production Distant Horizons generation, renderer, six workers and radius 32.

The native Deep Void probe arms one real Survival network player at the actual extended-bottom
boundary. Highest/lowest native END-tick observations correlate run/nonce/player/tick, exact
config and capability state, source threshold and resulting destination. Above-boundary and
disabled-switch negatives precede entry; entry additionally requires the real client arrival.
The supplement does not invoke the upstream procedure or teleport into the destination. Native
fixtures restore temporary switches and bind supplement/candidate hashes.

These authored scenarios still require a recorded full unchanged-candidate native run; local
contract/source passes are not runtime acceptance. Before rerunning, inspect the existing run and report its
ID, hashes, failed or aborted cases, evidence path, retained fixture, and process cleanup state.
Cleanup retains observed process descendants after their parent exits and checks termination after
graceful and forced shutdown. Every fixture is closed even when an earlier close fails. The
`process_cleanup` event reports `complete=false`, surviving PIDs, and an error when cleanup fails;
the suite then fails and retains its fixture and original failure evidence across task handoff. `complete=true` means
all tracked processes have exited; it does not waive an unexpected exit. Separate lifecycle
observations retain PID, expected-exit reason and exit status. An unsolicited exit (even status zero)
is a correctness failure; old readiness/settling markers cannot authorize a dead process. Expected
natural exit is declared before its triggering request and requires zero status plus the existing
native completion oracle. Harness-requested TERM/KILL teardown is recorded separately and remains
idempotent. Client liveness brackets player heartbeats and is checked before teardown. Singleplayer
`clearLevel` completion stops the integrated world, not its client process. Cleanup still attempts
every resource and reports actual termination completeness independently of lifecycle correctness.
Dev tests ordinary, forced, unexpected, and orphaned-child shutdown without launching Minecraft.

A runtime snapshot is evidence for a target only when its snapshot ID appears in that run's server
events and the run's `candidate_selected` hashes match the target under discussion. Completeness
makes a snapshot usable evidence; recency alone does not make it current. Do not use unmatched
snapshots or their volatile totals as claims about the tracked pack. Retained historical evidence
must be labeled with its original candidate/run, never presented as fresh acceptance.

The better-runtime-diagnostics completion schema is `bc.runtime_dump_completion.v3` and includes
`dimensions.json` (`bc.dimensions.v1`). Debug multiplayer traversal discovers targets at run time
from the loaded Creating Space rocket-accessible-dimension registry and enabled Dimension Drink Font
configuration; target counts are evidence, not hard-coded assumptions. Every discovered target
must be loaded. A single full-pack client travels as a spectator and requires both a server-side
position check and a client-side dimension/chunk report within one shared 90-second deadline,
including command time and client polling. Font-only destinations
require a direct-travel denial guard. Debug
visits three fresh, pairwise-distant locations per directly teleportable target and always requires three
consecutive samples at at least 18 mean TPS. The other two clients start only for Debug's
three-client Survival campaign check.
For terrain dimensions, Debug counts block IDs in 3×3 already loaded chunks at each visit. A
dimension passes when the combined samples contain at least 1,024 non-air, non-harness blocks and 256 blocks from
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
protection control makes each player invulnerable without changing game mode. An immediate scout is
started for each player through native loaded-approach validation, without an injected route.
After confirmed grounded Survival placement, the candidate-bound nonshipping support helper
records the existing familiarity state, then authors exactly 36,000 presence ticks through the
public production API for that player's current location. This is explicitly labeled
`history_origin=authored_fixture`, not acceptance of thirty observed online minutes. Fresh
SmokeClient2/3 must first reject an unfamiliar campaign and retain no encounter; the lead's
legitimately earned history is preserved. Requests bind run, nonce, UUID and exact fixture
position, are limited to three players, and remain retained after consumption. The helper does
not bypass eligibility or change progression clocks. A rejected terrain path triggers a retry at
another nearby player position within the established core on the prepared platform; each
rejected and accepted point is recorded.
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

**Observed gameplay scenario.** Full Debug runs this scenario in its own `gameplay` runtime
group; the explicit `gameplay` target invokes the same workload independently for partial retries.
Dist does not run the soak. It requires the same candidate/support byte binding and strict
log/process-cleanup gates. Its fresh Survival player first rejects an unfamiliar campaign, then
spends at least 18,000 actual eligible native ticks and 900 measured online seconds in the core.
A normal save/server-process restart and reconnect separate the second equally bounded visit.
Each visit must remain below 36,000 ticks, preventing the second visit alone from explaining the
final familiarity. Public familiarity must be false before/after the first visit and before
reconnect, then true after at least 36,000 observed ticks and 1,800 eligible online seconds total.
The observer never seeds history, changes a game clock, or counts offline time. Its retained
same-world checkpoint binds actual counts, monotonic/native tick witnesses and candidate hashes;
this is separate from the authored-history fixture above.

Before the client/soak, the target validates a fresh, strict complete native runtime snapshot
without global promotion. All 288 wandering-pool result IDs read from the selected candidate's
Commerce JAR must resolve in that invocation's live item registry; sibling sources and retained
proposal CSVs are not the runtime oracle. Snapshot ID and candidate/JAR hashes remain recorded.

The same gameplay scenario exercises actual registered spoiled items and native compost/rod behavior,
configured bush fields with explicitly authored native harvest/growth scheduling, production
readiness edges, and ordinary/Dynamic Trees cuts. Its nonshipping held-cut driver calls public
native mining APIs once per actual client tick and narrowly suppresses only an unrelated no-input
release while an exact run/nonce/player/target/candidate-bound action is active. It clears before
new targets, explicit stop, disconnect or expiry. This is authored fixture control, not human mouse
input or a read-only observer. Separate hooks only observe progress/particles. Ordinary trader
arrivals and repeat visits use the real spawner and finite expiry, with actual process-restart
UUID/expiry/post/fire persistence witnesses; the fixture never inserts traders or camp records.

Native framebuffer captures record renderer/state rather than a preview. The gameplay-only native
OpenAL Soft WAVE backend records actual mixer output, finalizes through normal client exit, and
retains per-launch PCM/header/binding evidence. A Forge source-start observer additionally checks
the actual native `Channel.playing()` state after play; it does not sample volume/pitch getters
that advance sound RNG. Dispatch, native source playing and aggregate nonzero mixer output are
not cue-specific waveform identification or physical listening approval. Likewise,
native rod-menu/backpack frames require paired native behavior and LLM inspection under
[inventory parity](inventory-parity.md), not just successful frame creation. Human-pointer and
physical-listening checks are separately `NOT_PERFORMED` when not exercised and do not block
otherwise complete automated acceptance. Never infer them from source checks or counters. All gameplay
probe/driver code remains in the isolated supplement, excluded from production/candidate archives.

After the full unshaded soak/restart/familiarity/scout proof, the target normally quits the client,
changes only the extracted fixture's Oculus `enableShaders=false` flag to true, and restarts the
same candidate client on the unchanged server/world. Typed pinned Oculus public APIs must report
actual enabled/in-use Complementary Reimagined r5.8.1, the unchanged ZIP hash, no fallback and no
stored shader error. Its 32-frame matrix covers eight palettes × normal/signal × native day/night.
Phase keys come from actual native level state, not visit labels or a clock offset. The visual
window is bounded by one 20-minute/24,000-tick daylight cycle after independently bounded native
startup; no `/time` or synthetic input is used. This is a shader-on visual workload, not a
thirty-minute shader performance verdict. Production Oculus config remains unchanged, and actual
framebuffer inspection is separate from API-state/counter success.

Debug's lifecycle smoke verifies one lineage transition, its committed archive, and final clean state.
It intentionally avoids a second generation; longer persistence matrices add substantial cost and
should be used only when needed, under the same last-resort warning, not a separate approval gate.

## Runtime scenario contracts

All runtime groups consume the exact bound production ZIP pair and gate-produced diagnostic
support bytes, extracted into fresh owned fixtures. Candidate/support hashes and strict logs
are checked at completion; hash drift, missing observations, process survivors and a budget
expiry fail rather than skip or retry. Each group has a four-hour outer controller cap including
setup and diagnostics. Individual readiness, command, client-arrival and polling budgets remain
tighter; no nested operation may reset the TPS stabilization deadline. Resource arguments and
fixture-only changes are recorded in `fixture_resource_budget`/profile events. Software rendering
is disclosed and is not a hardware-accelerated performance or human-interaction acceptance claim.
LLM visual acceptance requires review of the actual retained native frames, not software-renderer
state or capture counters.

| Scenario family | Inputs and observable oracle | Inner budget/resource contract |
| --- | --- | --- |
| Title/server readiness and Dist join | Fresh extracts; native title/server-ready markers and real online player heartbeat | Existing bounded startup/join waits; one client, recorded heap/CPU/software-renderer profile |
| Unseeded integrated startup | Absent save, native random normal world, production spawn selection/onboarding; resolved marker, Survival, forty correlated ticks, native stop and `level.dat` | Fifteen-minute absolute startup budget plus two-minute native exit; 12 GiB/eight processors, production DH settings |
| Seeded persistence | Authored seed/probe state, dedicated-created save and explicit fallback marker; native save/reopen observations | Bounded world-open/save/exit waits; isolated seeded profile, never labeled production fresh startup |
| Native Deep Void | Extended-bottom native world, exact upstream artifact, real Survival player; above/disabled negatives, same-tick native entry and client destination | Ten-minute absolute scenario budget with at-most-two-minute tick waits and at-most-ninety-second client/return waits |
| Dimension/Font traversal | Runtime-discovered registries/config, three fresh positions, server/client chunk reports, direct-denial guard and block-family/geometry oracles | Ninety-second arrivals; at least 18 TPS in three consecutive samples within one 180-second stabilization deadline; bounded nested calls |
| Campaign/repair | Three real Survival clients, fixture corridors, native routed encounters and repair geometry | Recorded three-client resource profile, bounded routed-stage/heartbeat waits; rejected routes recorded, not waived |
| Observed gameplay | One real Survival client, actual native ticks/monotonic time, saved campaign and ordinary-trader witnesses, native six-fix actions and framebuffer/audio evidence | Fixed 35-minute deadline per visit, each ≥18,000 ticks/900 eligible seconds and <36,000 ticks; existing software-rendered fixture profile, real restart; post-soak shader matrix ≤20 minutes/24,000 ticks after bounded startup |
| Restart/Flesh/whale | Current fixture saved state, native restart/reconnect; `restart-compat` binds the exact summoned whale UUID to native client join and two subsequent END ticks, not rendered visibility | Bounded stop/restart/client waits; whale receipt and heartbeat share 30 seconds after summon; fresh evidence and unchanged candidate hashes |
| Lifecycle/snapshot/pyramid | Native completion schema and snapshot ID, one committed lineage archive, pinned structure seed and saved structure record | Bounded native command/completion waits; one generation only; saved evidence belongs to the current run |
| Final audits/cleanup | Current logs, ZIP/support digests and all owned identities | Bounded diagnostic/TERM/KILL/reap phases; retain evidence across handoffs; process cleanup is not data purge |

## Full Debug visual finalization

Run full Debug through the structured facade when post-run visual admission is needed. If every
runtime, source-boundary and final-audit gate passes but reviewed frames are not yet admitted,
the run returns exit 4 with `awaiting_review`, never a success. Read the actual retained images
and native observations before writing a `bc.native_visual_review.v1` review. Every required
case has explicit criterion findings, image path/SHA-256, native screen/menu and GUI-scale
metadata, and exact candidate/support binding. A proposed review is not a generated pass.

```sh
python3 -B scripts/debug_coverage.py inventory
python3 -B scripts/debug_coverage.py admit-review --run-id NATIVE_DEBUG_RUN_ID --review /absolute/path/to/review.json
./test-control.main.kts finalize-debug CONTROL_RUN_ID
```

Use the control record's `child_runs.debug_run` when the release wrapper created a separate native
Debug run; never assume its ID equals the outer control ID. Finalization rechecks the unchanged
review contract, runtime evidence, strict audited logs, complete cleanup and actual candidate/support
bytes. Only a full Debug already awaiting review may be finalized. Failed runtime assertions,
uncovered high-risk boundaries, changed bytes or defective frames cannot be waived. No Minecraft
relaunch is needed solely to admit a valid review; production changes still require a fresh
candidate and complete full Debug. Historical failures and the initial pending-review state remain
retained. Human-pointer and physical-listening verdicts remain independently `NOT_PERFORMED`.

## Task registration and handoff

Activity bookkeeping is optional for source verification, Dev, native checks and releases.
`maintenance.main.kts begin --task TASK_ID --path PATH` may document consumed/output boundaries,
but stale activity rows, historical UNKNOWN states and task-specific guardian receipts are not
execution prerequisites. The retired lifecycle guardian/reconciliation tooling must not be run
or repaired to admit ordinary work. Preserve the candidate/support bytes through diagnosis and
delivery regardless of registration state. Queue handoffs remain active consumers until their
callbacks/delivery complete; source cache/workspace locks still prevent conflicting mutable
writes, and substantive source/gameplay tests and release provenance remain mandatory.

Before final success/failure/cancellation/blocked handoff, report results and stop owned producers.
Finish closes activity and releases its lease; it deletes nothing. Preserve outputs, caches,
provider/build JARs, evidence, worlds and receipts across handoffs and conversations by default.
Task assignment, turn-scoped file claims, operation-lifetime leases and retention records are
separate: releasing a turn claim does not end a task or cancel its helpers/jobs.

Purge is a separate operation, never a finish side effect. It requires quiet (no working agents),
stable (current usable modpack proof), and messy gates, and must protect the last known good build
and latest candidate with their support bytes and evidence. Follow the canonical
[retention/purge policy](policies/generated-data.md) for the implemented commands and safety gates;
do not use an old `finish --apply` cleanup recipe. Process termination remains mandatory even
though data is retained.

## Untested lettered builds

An explicit package-only request may produce an **untested** lettered revision of the current
numeric build: `443a`, then `443b`, through `443z`. The suffix is a packaging revision, not a
passing test tier. Never transfer the numeric build's Dev/Dist/Debug or visual verdict to changed
archives. State clearly in delivery that the lettered pair is untested and name the changed inputs.

For a config/content-only revision using the existing bundled JARs, use the existing package-once
entry point, under the shared runner mutex:

```sh
./pack-test-lock.main.kts run release release-skip-tests -- ./dist.sh --untested-suffix a
```

Refresh Packwiz metadata with `packwiz refresh` immediately before packaging so the changed inputs
have coherent hashes. This narrow package-only preparation does not invoke `release.main.kts`,
Gradle, source builds, deployment or test suites. Explicit user prohibitions on additional checks
also exclude Dev, documentation checks and runtime/visual checks; disclose these omissions rather
than silently running them. The normal tested fresh-release procedure is unchanged. No verified
receipt or tested provenance is created or reused for this path.

`pack.toml` and the client manifest carry `Build 443a`; canonical outputs are
`dist/build-443a/{client,server}/better-content.zip`. Suffixes advance without overwriting an existing
revision; the next ordinary numeric package after any `443` lettered revision is `444`.
Register/finish the packaging task under the retention policy. Lettered output retention follows
that same policy and is independent of acceptance.

## Fresh distributions

For a useful fresh distribution, use the supported release facade without another approval;
aggregate changes and check producer handoffs before launching:

```sh
./release.main.kts
./release.main.kts --jobs 4
./release.main.kts --jobs 4 --skip-tests
```

The release command consumes `gradle/active-custom-mods.json`, requires clean active repositories,
and records each local source `HEAD` in release evidence. It never fetches or modifies remotes.
Tested preparation revalidates persistent checkpoints, then verifies the required
provider/consumer closure using a clean rebuild or eligible exact build-only snapshot, with a
full verification baseline when no eligible verified checkpoints remain
or `--rebuild-all` is requested. It annotates and deploys the coherent staged set, refreshes Packwiz
hashes and checks the diff, runs `dist.sh`
exactly once, and then runs the complete Dist tier. Targeted and queued Debug can test that
unchanged ZIP pair when useful under the last-resort warning above. Direct full Debug uses persistent source verification
under a conversation-owned active control,
then packages once and runs complete Dist and Debug. Release evidence records
each mod's build mode and JAR hash.
Legacy JARs without source metadata are replaced during this bootstrap run.

Dependency resolution classifies manifests only from the production content roots. Retained
`workspace_artifacts/` task trees are excluded from discovery, like build/generated evidence;
unknown manifests elsewhere still fail classification rather than being silently packaged.

### Dependency-aware source workers and cache isolation

Provider preparation supports `--jobs 1` through `--jobs 4`, default two. The temporary serial-only
guard was removed after integrated immutable-view/publication/checkpoint controls, actual Gradle
attachment/deadline/archive controls, and controlled two-provider private-cache compilation/native
proof passed. This enables the implementation, not a claim that a fresh 46-provider set or a
measured natural-throughput target has already passed. Qualification still requires every owning
gate and the complete-set deployment/package barrier.

`ProviderInputRecipes.kt` derives each consumer's transitive provider inventory from the existing
active manifest and completed owning-gate records, including declared support artifacts.
`ProviderInputs.kt` copies exact hash/provenance-bound bytes into a fresh run-local
`provider-inputs/<repository>/` view, writes `provider-inputs.tsv` and its digest, and seals copied
files/directories read-only. It rejects aliases, unsafe/special paths, collisions, missing inputs
and unqualified records in qualified mode; failed partial views remain retained. Explicit
untested/diagnostic mode remains separately identified and never upgrades qualification.
Builds receive this view through `BC_CUSTOM_MOD_JAR_DIR`; native-log audits use the same view,
not a mutable global stage or sibling fallback. Existing Water/Physiology provider-JAR property
aliases are forced to that same view with highest-precedence CLI properties; profiles cannot
replace them with ambient overrides. Crops' policy-pinned bundled witnesses, including its prior
bundled self JAR, remain separately verified fixed bytes, not SOURCE/self edges. Exact manifest, inventory and bytes are rechecked
around use, including detection of extra files/directories and mutations. View digests participate
in build/checkpoint/provenance bindings. Missing or changed stored view digests invalidate the
provider's authoritative descendant closure before build admission, preserving epoch/ownership.
Build-only checkpoint reuse never publishes a qualified final before fresh gates. Support restore
uses the repository/session locks, retains differing old output bytes, and makes only independent
mutable build copies writable; immutable checkpoint bytes remain sealed.

Candidate publication copies to a private non-JAR partial, forces and hashes bytes, applies the
strict owning gate, rechecks the view/source/artifact, then uses Linux atomic no-replace rename
and forces the parent directory. Existing/raced targets and unsupported atomic publication fail
closed; there is no overwrite, hardlink or non-atomic fallback. Gate/cancellation failures retain
partials and original failures. Focused direct controls passed for view integrity, provenance,
mutation/cancellation, strict-gate failures and publication races; this is not real-provider or
release acceptance. Required optional/test/audit inputs still depend on correct authoritative
manifest declarations, not directory discovery or speculative all-mod dependencies.

The internal scheduler supports one to four workers independently of the workspace
controller's helper limit. The scheduler admits only providers whose declared dependencies have
completed successfully and binds consumers to those staged provider hashes. A provider failure
stops new admissions; already-running workers retain their evidence and must finish cleanup.
Incomplete worker cleanup prevents the deploy/package barrier. Parallelism changes neither the
manifest's mandatory tasks/profile order nor strict native-log, source/support-hash, coherent-set
and package-once gates. Standing authorization does not waive these execution-safety requirements.

The isolated-worker path uses retained run-local `build-isolation/` homes, Gradle user homes,
project caches, XDG directories, `BC_PACKAGE_ARTIFACT_CACHE` and temporary directories. It also binds Java `user.home` and
`java.io.tmpdir`; changing shell HOME alone is insufficient. Workers receive independent file
copies, not mutable symlinks or hardlinks into shared caches. Only dependency-download bytes
(`caches/modules-2/files-2.1`) and wrapper distributions seed these homes. Generated Forge
MCP/deobfuscation caches, transforms, compiled scripts, daemon state and project caches are not
seeded. Seed copying is bounded, checks filesystem headroom and rejects symlink/special-file
inputs; all partial/complete seed and worker outputs remain retained on failure or cancellation.

When private builds are needed, seed preparation precedes legacy writer admission. Shared seed
capture and legacy shared-cache fallback use the canonical POSIX `fcntl.lockf` mutex `/home/dev/better-content-modpack/.worklane/forge-cache.lock`. A similarly named file under
`/home/dev/.worklane/` is a different lock; BSD `flock` is not interchangeable. Private worker
cache locks and repository `.gradle/bc-source-workspace.lock` serialize each worker/repository's
mutable invocation, including build/world archival or reset. Legacy scripts with detected absolute
shared-cache/output references or relevant symlinks fall back to the whole-invocation global lock
rather than silently sharing generated Forge state. Independent `run source` and legacy validation
keep their existing shared-cache locking; they do not imply isolated parallel execution.

Run evidence records per-provider isolation/fallback decisions in `build-isolation-policy.tsv`
and seed identity in `build-isolation/seed-identity.tsv`. Inspect the actual decisions and inputs:
static fallback detection is conservative, not proof that arbitrary build scripts cannot escape
isolation. Controlled real-provider compile overlap and one transformed native run are proven;
full-provider cold/warm/fallback coverage and natural throughput still require their own evidence.
No separate per-run approval is needed within task scope, and no measured speedup follows from
synthetic scheduler tests or the controlled handshake.
Do not launch uncoordinated manual Gradle builds against shared caches or the same source outputs.
Consumers must use immutable validated provider snapshots, not replaceable sibling build outputs.

### Task-scoped Controls-deferred diagnostic candidate

For the explicitly authorized `logging-implementation-20261020` generation1 task only,
`./release.main.kts --diagnostic-controls /absolute/deferral.json` can prepare a **diagnostic,
unqualified** candidate. This is not `--skip-tests`, a verified baseline or permission to bypass
another provider. The input schema `bc.diagnostic_controls_deferral.v1` binds that exact task,
generation1, repository `better-oc2r-create-controls`, status `DEFERRED_UNQUALIFIED`, and absolute
`artifact`, `rejected_evidence`, `source_context` paths with their corresponding `_sha256`
fields. `artifact_source_commit` must match actual embedded identity, or explicitly `UNKNOWN`;
historical gate HEAD/dirt is not proof of that artifact's producer context. Preserve that distinction.

Only the bound retained Controls JAR is copied unchanged, without rebuilding, annotating or
inventing qualification. All other repositories must be clean and execute every ordinary/profile
source gate, including strict native-log audits, against the coherent staged provider set.
The diagnostic path does not consume/create normal verified-session checkpoints. It cannot combine
with `--skip-tests`, `--verification-session` or `--rebuild-all`. Failed other gates still prevent
deployment/refresh/package. `dist.sh` remains the unchanged package-once entry point.

Provenance uses distinct `bc.diagnostic_dist_provenance.v1`, `DIAGNOSTIC_UNQUALIFIED` qualification
and the exact retained Controls/rejection/context hashes. Bound runtime tests accept it only with
explicit matching `BC_RELEASE_DIAGNOSTIC_CONTROLS=/absolute/deferral.json` (propagated by the release
facade). An ambient diagnostic environment alone is rejected by the release facade: preparation
requires its explicit CLI option, so an inherited runtime opt-in cannot produce a normal-success
release status for an unqualified candidate. Other provider/source/support and candidate hashes remain mandatory. This is never accepted
as ordinary verified provenance. Strict runtime findings remain failures; normal Dist prerequisite
failure still prevents full Debug. Even otherwise successful runtime execution returns release
exit6 and cannot establish complete-source/Dist/Debug acceptance. Existing visual/inventory/TPS
requirements remain unchanged. Old and failed artifacts/evidence remain retained.

`--skip-tests` is the explicit untested-release path. It still reuses valid source-identical
bundled JARs. Changed or unannotated sources build through `stageRuntimeJar` without the custom-mod
verification tasks; the complete staged set is deployed, release preparation refreshes Packwiz, and packaging runs
exactly once. It skips the full pack suite. Release evidence and provenance record that tests
were skipped; choose this only for an intentionally untested build and disclose the omission,
never as a substitute for a tested acceptance claim.
