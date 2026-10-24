# Inert Gradle timing controls

Purpose: exercise the generic task/JUnit observer in an offline, Minecraft-free project.
This is focused implementation evidence, not provider/native qualification or acceptance.
The accountable lead alone runs Gradle/cache writers under the current task authorization.
The helper must not execute this driver.

```sh
python3 -B gradle/timing-controls/run_controls.py \
  --gradle /absolute/gradle-8.5/bin/gradle \
  --output /home/dev/workspace_artifacts/fresh-tested-dist-20261023/gradle-timing-controls-UNIQUE
```

The output directory must not exist. All fixture copies, Gradle build outputs, logs,
observations, catalog snapshots and automatic reports remain retained. The driver performs
8 serial minimal Gradle invocations, offline and without native/Forge plugins. JUnit 4.13.2
and Hamcrest 1.3 are the selected Gradle 8.5 distribution's own libraries; no downloads occur.
It respects the lead's selected Gradle user home rather than silently introducing a cache domain.
Each invocation has an independent 180-second controller safety cap, with process-group cleanup
on expiry. That fixed control cap is not an empirical test budget.

Controls:

- Two real successful `probe` executions supply explicit raw baseline files.
- The third invocation uses their exact twice-median task budget selected at graph readiness.
  An 8-second interruptible action must actually be interrupted, not merely record a chosen cap.
  The original 20-second cap, chosen/actual cap and enclosing 180-second cap remain distinct.
- Changed input identity leaves the existing task cap unchanged and expectation UNKNOWN.
- Original action failure and skipped task retain outcomes and durable terminal/report evidence.
- Original JUnit success/failure/skip callbacks, exception classes and XML reports survive unchanged.
  All individual JUnit own caps stay UNKNOWN, not the aggregate task or process cap.

## Supported per-case API limitation

Gradle's supported `TestListener` observes before/after descriptors/results but exposes no
per-case cancellation/deadline actuator. `Task.timeout` bounds the enclosing task, not a case.
An observer cannot safely manufacture per-case hard enforcement without changing original
invocation/thread/reporters. JUnit Jupiter engine configuration (`junit.jupiter.execution.timeout.*`)
can establish engine-owned deadlines, but annotations/config precedence, parameterized/dynamic
case identity and invocation lifecycle scopes must be handled by the owning engine integration.
Its same-thread mode is cooperative interruption, not hard containment; separate-thread mode
changes thread semantics. JUnit 4 timeout annotations/rules likewise cannot be inferred reliably
from Gradle descriptors alone. This adapter therefore explicitly records
`UNSUPPORTED_BY_GRADLE_TEST_LISTENER`, leaving `configured_timeout_ns` null for JUnit suites/cases.
It does not mutate original tests or claim aggregate timeout as individual enforcement.

A passing control result covers the exact retained inert executions only. Real provider/native,
transformed adapters, cancellation from external controllers and packaged runtime scope are
separate verification work; do not infer them here.
