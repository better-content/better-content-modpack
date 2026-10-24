# Bounded playtest logging

## Scope and authority

This subject owns the shared producer/output contract for playtest logging in active local
custom mods. The executable inventory is `gradle/active-custom-mods.json`; each owning source
repository documents its actual feature events and local tests. Source implementation establishes
coverage, not this document or an activation banner.

Use loader-provided SLF4J loggers named `bc.playtest.<modId>.<category>`. Small Java 17 helpers
are repository-local copies, callable from Kotlin, with distinct owning packages. They are not a
new shared runtime provider or dependency. A mod uses one helper instance across its categories.
The canonical helper behavior is exercised by owner-local `PlaytestLogContractTest` tests;
Gameplay Notices supplies the reviewed source-copy reference. A helper change must be reviewed
and propagated consistently rather than leaving undocumented per-mod forks.

## Activation and output

The pack's `config/better-content-log4j2.xml` leaves root INFO and enables the scoped playtest
hierarchy at DEBUG. It keeps the human console at INFO while the rolling `logs/latest.log`
receives bounded playtest DEBUG. It does not enable general Forge/network DEBUG. Ordinary
standalone mod launches keep their existing logger defaults unless a logging profile explicitly
enables this hierarchy.

Packaged production dedicated servers select the XML through `user_jvm_args.txt`. A manually
imported client must select it through its launcher's JVM arguments, relative to that instance's
actual game directory:

```text
-Dlog4j.configurationFile=config/better-content-log4j2.xml
```

To disable verbose playtest events without disabling operational failures, additionally set:

```text
-Dbc.playtest.level=INFO
```

`DEBUG` is the profile default. The property is interpreted by the XML, not a second source-level
configuration system: setting it without selecting that profile does not configure a standalone
logger. For per-mod/category selection add an additive named logger within `bc.playtest`, with
no private appenders or filters. For example:

```xml
<Logger name="bc.playtest.better_drinking_water.transaction" level="TRACE" additivity="true"/>
```

The XML's hierarchy permits ordinary INFO/DEBUG/TRACE choices. Do not use OFF/nonadditive
routes to hide failures. Existing Train Fuel and Weathering debug booleans gate only their
legacy detailed samples, now bounded TRACE; they do not gate the new causal DEBUG events.
Check the owning README for its exact category and feature coverage.

Each mod emits one INFO status at initialization when that level is enabled: effective mode,
limits, version/source identity if actually available, and integration/configuration state.
Unknown identity is explicitly unknown. Source metadata is read only once at initialization from
the owning Forge ModFile's exact `META-INF/better-content-source.properties`, bounded to 16 KiB
and checked for schema, owning mod ID and commit syntax. A generic classloader resource lookup
must not accidentally report another mod's commit. A status proves only initialization/visibility,
not successful gameplay, executed hooks, complete native coverage or deployed source equivalence.

The rolling file additionally rolls at 32 MiB using `fileIndex="nomax"`. Archives are not deleted
by count or expiry. Size rollover is not a total-disk quota.

## Events and limits

Records begin `BC_PLAYTEST schema=bc.playtest.v1`, followed by bounded, escaped single-line
`key=value` fields. Required fields are `mod`, `event`, `side`, `process`, `result`, `reason`.
Relevant records also carry dimension, local native tick, existing domain/operation identity,
before/after scalar values and elapsed time. Process IDs are ephemeral and shared within that
JVM; client/server ticks are not synchronized clocks. Existing protocol IDs are reused where
available. A local attempt ID must not be described as a native server acknowledgement.

- INFO status and existing operational errors remain distinct from verbose events.
- DEBUG explains meaningful decisions, transitions, committed mutations, expected rejections and
  deferred work. An ordinary rejection is not WARN/ERROR noise.
- TRACE is additional focused detail, never an unrestricted frame/tick/voxel/packet stream.
- A per-mod token bucket admits 120 detailed DEBUG+TRACE records/minute with burst 20.
- At most 256 controlled event/reason aggregation keys are held per mod, cleared at the
  30-monotonic-second summary window; excess keys contribute overflow counts.
- One coalesced summary per 30 seconds reports attempted/emitted/suppressed/overflow counts,
  up to eight reason entries and the aggregate of remaining reasons. Final lifecycle teardown
  can emit a final summary. Native logging is used without owned IO flushes, joins or waits.
- Records are limited to 2,048 characters and explicitly mark truncation. This bounds diagnostic
  rendering, never gameplay quantities. Reset clears diagnostic keys but does not replenish the
  per-process burst or rearm focused TRACE.

These are producer bounds, not measured performance acceptance criteria. Record real log
volume and workload observations; a source/unit pass cannot certify acceptable gameplay overhead.
Actual failed operations retain normal failure severity/context and strict-log consequences,
independent of verbose budgets. Do not reduce an error to DEBUG or suppress it to obtain a pass.

## Focused TRACE

Selecting TRACE for a logger is necessary but not sufficient. Its helper requires these JVM
properties, using the actual mod ID:

```text
-Dbc.playtest.trace.<modId>.dimension=minecraft:overworld
```

And **exactly one** focus:

```text
-Dbc.playtest.trace.<modId>.subject=<canonical-lowercase-UUID>
-Dbc.playtest.trace.<modId>.pos=12,64,-8
```

Pass only one of those two lines. Coordinates are canonical signed Java integers without
spaces; IDs/dimensions must match the event's native context. Not every feature supports both
focus kinds: the owning source/README identifies the contexts it supplies. Invalid or absent
focus suppresses detail and emits one explanatory status when TRACE is attempted. A focus
arms on its first matching event, lasts 60 monotonic seconds, admits at most 1,000 records,
and remains subject to the ordinary shared detail budget. It cannot rearm within that helper's
process lifetime; restart the process to arm a fresh focus.

## Non-interference and privacy

Level/admission checks precede expensive fields. Use already computed outcomes, not extra
random draws, entity construction, chunk loads, pathfinding, inventory scans, NBT serialization
or registry/catalogue traversals. Diagnostics retain no native world/player/menu references
and do not move native reads to background threads. Existing tick boundaries can request a cheap
summary; they do not emit one record per tick.

Log the actual stage: request, admitted work, scheduled work, serialization, successful mutation,
durable publication and observed client effect are different facts. Insertion success cannot be
inferred from a spawn call; ticket ownership from a local boolean; audible output from a sound
submission; durable storage from an ACK or save callback. Preserve existing export schemas and
fixture witness markers rather than renaming them into playtest events.

Exclude player names, chat, addresses, arbitrary component text, complete inventories, NBT,
packet bodies, schematics and seeds by default. Native UUID/position is used only where causal
context requires it and documented by that owner. Wireless topics/consumer identities remain
private. Gameplay Notices does not expose arbitrary caller-provided notice identity: theme/count
observations do not claim per-notice cross-wire correlation.

## Fixture audit and acceptance

`FixtureAuditLogging` derives launch-distinct immutable configuration from an extracted candidate,
keeps presentation filters separate, empties only copied Log Begone suppression lists, and adds an
unfiltered native audit sink. It permits only the intended additive playtest subtree with safe
levels and no private routes, validates root INFO and configuration hashes, and fails closed on
unsupported structures. Console thresholds must never be applied to the raw audit. DEBUG-only
output does not establish native readiness.

Runtime finalization scans distinct ready-launch raw audit files, not their aggregate mirrors.
`PlaytestLoggingAudit` validates envelopes, required fields, process/logger identity, per-process
activation uniqueness, record bounds, known private-field exclusions and summary conservation/
cardinality. Canonically bound launches must show activation and report the exact candidate's
source commit, not an unknown or another artifact's identity. It records actual native-record UTF-8
bytes and coalesced per-mod attempt/emission/suppression/overflow totals. These observations do
not establish every gameplay branch, a relative overhead claim or a new performance exemption.
The limited private-key check supplements, not replaces, owning-source privacy review.

After the fresh TNT/buried family's existing native world checks and process cleanup succeed,
logging acceptance additionally requires representative DEBUG links: priming to explosion handoff
by process/dimension/native entity UUID, removal-watch admission to actual insertion by the same
native origin and created mob UUID, and admission to the controlled withdrawal/TTL expiry. Only
that family's raw files are eligible, with at most 256 selected samples; older families, INFO-only
output, unrelated loggers, mismatched processes/origins and reversed native ticks cannot substitute.
This logging acceptance requires scoped DEBUG, changes no gameplay/TPS/deadline/visual oracle,
and does not claim that a handoff alone proves an explosion or that every detail escaped suppression.

The pack's source-copy invariant checks every active helper and its whole deterministic contract
test against the tracked Gameplay Notices reference, allowing only the owning package to differ;
helper runtime class identities must remain distinct across all active mods.

Real isolated Log4j tests cover scoped/global levels, console/file/raw separation, errors/fatals
and exception traces, rejected private routes, config drift and non-deleting rollover beyond the
native default archive count. Owner-local tests additionally exercise lazy disabled paths,
budgets/overflow/reset/concurrency, TRACE focus/expiry and actual feature outcomes. These are not
native acceptance by themselves. Full Debug, exact-candidate audits and required native frame
review remain under the [testing procedure](testing.md), including both inventory paths where
affected. Do not claim human-pointer or physical-listening checks from instrumentation.
