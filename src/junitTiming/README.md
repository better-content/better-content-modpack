# Nonshipping JUnit Platform timing adapter

`gradle/junit-test-timing.gradle` is an opt-in **Project** attachment. The generic timing hook
applies it early with `BC_TIMING_JUNIT=true`; Root also opts in automatically through its
`bcTimingJunitEnabled` project extra property, including init-plus-project application.
It compiles the isolated `java/` source against
the owning test runtime SDK (Gradle's bundled platform API is a compile-only fallback), and adds
classes and `resources/` only to actual Gradle `Test` runtime classpaths. There is no production
main dependency, replacement launcher, invocation wrapper, test selector or changed test thread.
All archive tasks exclude the observer package, sources and service entry; JAR/ZIP tasks also
check their completed entries. Real archive exclusion acceptance still requires the owning build.

The service SPI observes `isTest` executionStarted/executionFinished, including engines,
parameterized invocations and dynamic tests. `junit_test` records carry the distinct
`junit_platform_executionStarted_to_executionFinished` duration boundary; they cannot borrow
Gradle callback baselines. Name/class/uniqueId, original execution thread ID, context, task and
build root are scalar metadata. `SUCCESS`, `FAILURE`, `SKIPPED` preserve the existing timing
schema; `engine_status` separately retains `SUCCESSFUL`, `FAILED`, `ABORTED` or `SKIPPED`.
Skipped containers emit nonexecuted descendant skip observations with null durations. They never
become successful samples. No test instance, descriptor, callback, engine result or Throwable
is retained. Engine-authored timeouts remain independent and are not replaced or inferred here.

The shared selector prepares a fresh catalog from **explicit raw** `BC_TIMING_BASELINES` paths.
The adapter snapshots it before Test execution and the worker loads that bootstrap once. Exact
full identities and the separate boundary must match; positive representable 2x rational medians
are checked again in Java. UNKNOWN identities/baselines leave the own-case cap null, and an
UNKNOWN-only plan never schedules a watchdog. Runtime deadlines never change during a case.

A worker-local watchdog and engine-terminal callback arbitrate scalar active entries under one
monitor. Original failures delivered before expiry win. A terminal arriving after the own cap
cannot become a successful baseline sample merely because scheduling delayed the watchdog.
Expiry writes TIMED_OUT, shutdown/escalation intent, pending INTERRUPTED records and an unhealthy
receipt before initiating System.exit(124) on an owned shutdown thread. An independent watchdog
halts **only its own JVM** after a fixed two-second shutdown-hook cleanup grace if necessary.
That grace is separately recorded and is not added to the test budget. No external PIDs,
signals, world calls, file deletion or test-thread interruption are used. Files/worlds remain
retained. Scheduler and durable-write latency are not zero-time hard-real-time guarantees.

Each plan produces a CREATE_NEW health receipt in a fresh task-nonce directory. The Gradle
finalizer checks nonce/task/sink and healthy balanced counts for EVERY plan. Only Gradle-owned
plans are compared with Gradle's original case count. Real enclosed Launcher executions retain
all their cases and separate `junit_platform_enclosed_executionStarted_to_executionFinished`
baselines, never extra invented Gradle callbacks. Scalar admission holds no plan/test objects;
enclosed observer errors mark worker health failed. Full Dev verified 574 original passing
Gradle cases plus four enclosed probe observations without summing those boundaries. Missing/disabled/broken SPI cannot quietly pass successful Test execution. Existing
test/task failure stays primary, with recorder findings suppressed onto it. Listener errors do
not replace an original engine exception. Process death before health leaves missing evidence,
never a successful recorder receipt.

## Focused inert controls

The following uses installed JDK/API files only, never Gradle or Minecraft. Outputs must be fresh
absolute directories and are retained. It exercises actual service auto-registration, preserved
custom-engine invocation thread and exception identity, success/failure/abort/container-skip,
dynamic cases, UNKNOWN, ordinary worker exit, hanging shutdown-hook escalation, deterministic
clock/failure races, odd medians, ambiguity, and concurrent durable writes.

```sh
python3 -B src/junitTiming/contracts/run_controls.py \
  --api-dir /absolute/gradle-8.8/lib/plugins \
  --output /absolute/fresh/retained/direct-controls
```

## Lead-only actual Gradle controls

Use a fresh retained copy of `contracts/gradle/`, not this authored directory, and a private
Gradle user/project cache. Set `BC_JUNIT_ADAPTER_ROOT` to this repository root and set
`BC_JUNIT_CONTROL_CLASSPATH` to an explicit path-separated list of the already installed pinned
JUnit Jupiter 5.11.4 API/engine/params, platform 1.11.4 launcher/engine/commons, opentest4j and
apiguardian JARs. The fixture has no repositories or download dependencies. Example invocation
with those explicitly prepared paths:

```sh
BC_TIMING_JUNIT=true BC_JUNIT_ADAPTER_ROOT=/home/dev/better-content-modpack \
BC_JUNIT_CONTROL_CLASSPATH="$PINNED_JUNIT_JARS" \
BC_TIMING_FILE="$EVIDENCE/events.jsonl" \
BC_TIMING_REVISION=authored-junit-control BC_TIMING_INPUT_ID=authored-junit-control \
BC_TIMING_PROFILE=inert-gradle-control BC_TIMING_ENVIRONMENT_ID=jdk17-gradle8.8 \
BC_TIMING_CACHE_ID=private-control \
python3 -B scripts/bounded_facade.py --budget-seconds 120 -- \
/absolute/gradle-8.8/bin/gradle --offline --no-daemon --no-watch-fs \
  --gradle-user-home "$PRIVATE_USER_HOME" --project-cache-dir "$PRIVATE_PROJECT_CACHE" \
  --init-script /home/dev/better-content-modpack/gradle/test-timing.init.gradle \
  -p "$FRESH_FIXTURE_COPY" test observerLeakProbe
```

Expected: original callbacks and XML remain; case/suite/task boundaries stay distinct; health
counts include the parameterized/dynamic/disabled cases; leak-probe archive contains neither
observer classes nor service entry. `-PcontrolMode=failure` must retain the original exception
and fail. `-PdisableSpi=true` must fail the missing-receipt health guard. With an explicit baseline
JSON array naming the successful control's **raw** event file, rerun the same fixture under
`-PcontrolMode=timeout` and `-PcontrolMode=hang-hook` as intentional fault-injection controls;
only exact comparable case identities select caps, worker exits 124, health is false, pending
cases are censored, and Gradle fails. This authored fault matrix is not a production benchmark
or an assertion that production expected runtimes are known. Use distinct event sinks per run,
retain every failed run, and inspect all descendant/worker termination before finishing.

Actual Gradle attachment, fresh health, original failure/skip callbacks, disabled-SPI rejection,
archive helper-byte exclusion and ordinary/hanging worker timeout controls were also exercised in
`generated/test-evidence/fresh-fixes-20261023/retool-finish/junit-gradle-3/result.json`.
The prior `junit-gradle-2/postvalidated-result.json` preserves the initial harness failure and
its correction: Gradle's shutdown stdout is in retained binary results, not necessarily the
console. Fresh corrected matrix3 passed directly. Its measured 95,866,141 ns case
selected/enforced exactly 191,732,282 ns; fault modes are intentional inert
controls, not production benchmarks. Empty directory entries contain no observer bytecode or
service resources. Separate `real-2.source/proof.json` records controlled real-source compilation
and one transformed native test with helper-free JAR/reobf outputs. Neither proof qualifies all
providers or a packaged distribution; source/pack and same-artifact acceptance remain separate.
