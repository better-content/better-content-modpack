# Playtest protocol v2 — implementation interface

Authoring only; this document is not execution permission. Build-71 is the immutable scope reference; explicit fresh artifact binding is required later. Test support is standalone under `test-support/forge/`, never a production mod.

## Transport

Coordinator creates an exclusive run directory with `requests/` and `observations.jsonl`. Forge is launched with `-Dbc.playtest.directory=ABSOLUTE_DIRECTORY`, `-Dbc.playtest.runId=RUN_ID`, and `-Dbc.playtest.token=RANDOM_HEX_64`. Without all properties the support mod refuses activation. Requests are UTF-8 JSON published atomically as `requests/000000000001.json`, increasing one-based sequence. Native processing occurs on the server thread; only one request is in flight. Files larger than 1 MiB or lines larger than 1 MiB are rejected. No command execution, arbitrary Java evaluation or unbounded path access is provided by this protocol.

Request fields (all required): `schema: bc.playtest_action.v2`, `run_id`, `case_id`, `token`, `sequence` (positive integer), `driver` (stable action identifier), `timeout_ticks` (1..72000), `parameters` (object).

Observation fields (all required): `schema: bc.playtest_observation.v2`, `run_id`, `case_id`, `token`, `sequence` (monotonic global output sequence), `request_sequence` (input sequence), `driver`, `source` (`server` or `client`), `kind` (`started`, `sample`, `completed`, `failed`), `tick` (nonnegative cumulative observed tick), `payload` (object). Every request produces one `started` and one terminal `completed`/`failed`, with optional samples. Terminal output is flushed; partial trailing lines are never considered evidence. Token/run/request/driver mismatch, duplicate terminal or reordered output is fatal.

Native actions report observations, not assertion verdicts. Kotlin drivers evaluate independent expected values. A generic action that reports success cannot prove a gameplay promise. `failed` includes `payload.reason`; unknown drivers fail closed. Actions declare fixture mutations in observations, and no fixture-supplied resource proves natural acquisition.

## Minimum action surface

- `inventory.discover`: read effective registries/recipes after startup reload; observations reference run-relative inventory files with SHA-256 and explicit unsupported source types. No gameplay cases or qualification.
- `server.snapshot`: dimension/game time, player membership and state; no mutation.
- `player.snapshot`: real player UUID, inventory/food/health/effects/position; no mutation.
- `player.interact`: bounded normal interaction with a declared player, hand and block/entity; not arbitrary console text.
- `fixture.cleanup`: release only protocol-owned fixtures; terminal payload proves owned residuals.

Additional native action IDs must be explicitly registered, enumerated and bound to support artifact/driver inventory. `supported_drivers` discovery never implies a case implementation exists.

## Orchestration boundaries

Discovery accepts structurally bound draft manifests but cannot mark them reviewed. Campaign preflight requires reviewed v2 coverage, supported case/action IDs, observed candidate-bound inventory, independent oracles and explicit resource budgets. Original lifecycle tiers remain separate and uninstrumented.

Authoring model, case drivers, support module and qualifier are independently compiled/reviewed. All tests and native discovery remain unrun until separately approved. Unsupported requested coverage stays blocked; never synthesize passing receipts.

## Evidence bindings and offline replay

- `protocol.support_capabilities` binds the **containing test-support JAR** (same file/hash as `test_support`); its `META-INF/bc-playtest-support.json` resource enumerates action IDs. A separate unverified capability JSON cannot expand that artifact's support.
- The mailbox retains `requests.jsonl` as well as `observations.jsonl`. Campaign receipt fields `native_requests` and `native_protocol` bind these exact files. Offline qualification replays the same state machine and exact ordered action roster: discovery, each planned case step, then that case's cleanup. Failed-then-completed, replayed or unplanned requests are rejected.
- Per-case `case-ID-native.jsonl` lines contain `{step, observation}` with the complete native terminal observation. Case assertion evidence binds this file as `native-interaction`. Its request-sequence list must equal the planned case action sequence, including before/after actions with identical parameters.
- `driver.assertionIds` must exactly own the reviewed case assertions. Fixture-provisioned IDs must be declared but cannot occur as acquisition proof for that same case. Separate acquisition cases must be explicit dependencies, target the resource, appear in its acquisition coverage, and have authentic unprovisioned proof.
- `fixture.cleanup.cleanup_complete` establishes owned cleanup, not full gameplay-state restoration. `baseline_restored` remains false after any native action that may mutate player/world state, including interactions outside `fixture.begin`. No next executable case may run unless the previous baseline is proved restored. The final case may leave a changed disposable world after owned cleanup and a complete save; this is not authorization to reuse player worlds.

## Required review corpus

V2 repository receipts contain `reports`, each with a raw `report` binding, `sidecar` binding and full task `execution_log` binding. Reviewed repository `test_reports` independently declares expected task, kind, run ID, membership, time and timezone. Forge GameTest sidecars additionally bind their `native_log`. Zero tests, skipped/failed tests and stale fingerprints are rejected; nonfatal ERROR severity alone does not replace explicit impact review.

Visual review or its bound capture session must declare exactly one `server_log` and `client_log` binding, plus its authentic `input_log`. V2 log review `sources` must cover these, every repository execution/native log and all server/client logs for the campaign and three independent lifecycle tiers. `signature_indexes` binds complete diagnostic indexes; the qualifier recomputes signatures from the bound raw logs, requires exact nonoverlapping coverage and reviewed counts/impacts/dispositions. Missing logs, index overflow or omitted signatures blocks qualification, not a silent pass.

These v2 authoring fields do not upgrade historical v1 qualification receipts. No native/action/campaign evidence has been generated or validated by execution during this implementation work.
