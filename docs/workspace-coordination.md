# Workspace coordination

## Authority and scope

This subject owns the controller/operator interface for
[`scripts/workspace_coordination.py`](../scripts/workspace_coordination.py) and the separate,
user-owned Pi extension source
[`scripts/pi-workspace-coordination.ts`](../scripts/pi-workspace-coordination.ts). The installed
`/home/dev/.pi/agent/extensions/workspace-coordination.ts` is a symlink to that reviewed source,
not an independently maintained copy.
The frozen lane guide owns collaboration rules; [workspace policy](policies/workspace.md),
[generated-data policy](policies/generated-data.md) and [testing](testing.md) own authorization,
retention and test/release boundaries. A registered task, assignment or claim is **not user
authorization**, artifact acceptance, or permission to dispose of anything.

The controller uses only the Python standard library. Shared state defaults to
`/home/dev/.worklane/coordination`; `--root` selects an explicit absolute fixture/state root.
The live view annotates turns as `live`, `stale` or `unknown`. The Pi hook passes its actual
`--actor-pid` and stable `--session-id`; captured PID/start/boot identity is compared exactly.
Dead/reused identities are stale; absent or inaccessible identity is unknown. Manual CLI calls
without an explicit actor identity never guess the parent shell PID. Classification does not
auto-delete claims, release leases, or end tasks/assignments: uncertain ownership still needs
owner reconciliation. The lane guide separately permits removing legacy Markdown claim files
when fresh Herdr/ownership checks confirm their owners are inactive. That exception does not
permit releasing another owner's controller turn or lease, or clearing tasks, assignments, jobs
or retention records; use their documented owner/reconciliation interfaces.

State changes use one `flock`, atomic replacement and fsync. Symlink roots/ancestors, state and
lock files are rejected; request markers use `O_NOFOLLOW`. `live` and `authority` are lock-free,
read-only atomic snapshots, not history scans or state-writing transactions. Invalid state fails
closed. This is coordination among one user's agents, not a security boundary against that user.

## Independent lifetimes

- **Task:** one accountable lead, monotonic generation and active/cancelled/complete state.
  The generation survives final state as a tombstone; re-registration cannot revive old results.
- **Assignment:** a bounded helper subtask with exact workspace/tab/pane and generation.
  Helpers do not recursively delegate. Default helper limit is four, explicitly configurable
  at initial registration to at most sixteen. The accountable lead may explicitly update this
  bound through `task-settings` without replacing the task. Current explicit task scope may use
  a smaller bound.
- **Turn claim:** current writer intent, exact shared area tokens and injected Herdr topology.
  Only the owner releases it. Paths normalize relative to declared cwd and reject symlink
  ancestry; parent/child paths conflict. `token:`, `resource:` and `identity:` areas use exact
  semantic matching. Match areas with live Herdr agent state, not historical claims.
- **Resource lease:** operation-lifetime exclusive resource token, independent of turn end.
  Only its owner releases it; cancellation does not silently discard leases or owned claims.
- **Retention:** governed separately. This controller never deletes edits, outputs or evidence.

Normal turn completion releases only its turn claim, not tasks, helper assignments, jobs or leases.
Assignment `result` closes a helper slot without cancelling the task, but accepts only its current
active generation. Cancellation invalidates the task generation and its helper assignments first,
then requests owned-job termination and cooperative helper shutdown. Already-running unregistered
work and helpers without active hooks remain reported survivors, not guessed process-kill targets.

## Operator CLI

Run from the modpack; keep user authorization separate from these registrations:

```sh
python3 -B scripts/workspace_coordination.py live
python3 -B scripts/workspace_coordination.py task-register --task TASK --owner LEAD --helper-limit 4 --operation synthetic --operation docs
python3 -B scripts/workspace_coordination.py task-settings --task TASK --generation 1 --owner LEAD --helper-limit 12
python3 -B scripts/workspace_coordination.py authority --task TASK --generation 1 --owner LEAD --operation synthetic
python3 -B scripts/workspace_coordination.py turn-acquire --owner AGENT --area shared-area
python3 -B scripts/workspace_coordination.py turn-release --owner AGENT
python3 -B scripts/workspace_coordination.py lease-acquire --task TASK --generation 1 --owner AGENT --resource RESOURCE
python3 -B scripts/workspace_coordination.py lease-release --lease LEASE_ID --owner AGENT
python3 -B scripts/workspace_coordination.py result --assignment ASSIGNMENT_ID --generation 1
python3 -B scripts/workspace_coordination.py task-cancel --task TASK --owner LEAD
```

`authority` returns stable `valid`, `task`, `state`, `generation`, `owner` and `operation` fields.
Exit zero means matching active authority only; exit three means missing, stale, cancelled,
malformed or unreadable state. With `--operation`, `--owner` is mandatory and both the registered
lead and exact task operation must match. No-operation queries establish task liveness only.
Initial registration takes repeatable `--operation` tokens and defaults to **no allowed
operations**. Existing active registration is idempotent and never silently widens permissions.
A changed operation authorization scope needs a new task generation/registration.

`Coordinator(root).task_settings(task, generation, owner, helper_limit)` / `task-settings` changes
**only** the helper limit on an active, exact-generation task owned by the named accountable lead.
Run it from that lead's exact injected Herdr session/workspace/tab/pane; missing or different
injected topology, a foreign/helper principal, missing/inactive task or stale generation fails
closed. The limit must be an integer in `0..16` and cannot be below the task's occupied assignments,
including `launching` and `launch-failed` slots (conservatively across generations). Completed or
cancelled assignments do not occupy slots. No assignments are cancelled or reconciled by this
command. User authorization for a larger helper scope remains independently required.

The existing nonblocking admission transaction rejects maintenance reservation/contention and
atomically fsyncs the helper-limit change with a `settings_changes` audit (task, owner, generation,
exact topology, previous/new limit, occupied count and time). Repeating the current valid setting
is a no-op. Task generation, operations, topology, helper workspace/pending launches, assignments,
active jobs, claims, leases, execution epoch metadata and foreign state remain unchanged; no
producer is signalled or restarted. `task-register` remains initial-only/idempotent, not a settings
update path. Do not hand-edit controller state or cancel/re-register to change a live helper bound.

Queue/Harness consumers
carry ID, generation, accountable owner and requested operation (`pack-test:dist` or
`pack-test:debug`) and revalidate before admission/execution, not merely at handoff creation.
A query is a snapshot, not a reservation: cancellation may occur afterward, so operation leases
and execution-time checks remain necessary.

`assign --task TASK --owner LEAD --helper HELPER --workspace WORKSPACE --tab TAB --pane PANE
--area AREA` registers an existing validated helper topology. The lead topology is captured from
its injected environment at task registration. Register only topologies checked against Herdr.

`helper-launch --task TASK --owner LEAD --helper HELPER --area AREA --cwd ABSOLUTE_PATH
--prompt FLAGGED_AGENT_MESSAGE` launches a bounded Pi helper through the structured Herdr CLI,
without shell interpolation, focus changes or pane splits. It requires the registered lead's
exact injected session/pane. The task reuses one dedicated helper workspace distinct from the
lead, with one helper in each newly created tab. Task/generation/area environment reaches the
helper hook. Failure retains its assignment and discovered topology for reconciliation; it never
closes panes/workspaces to hide a partial launch. Inspect failure records before retrying.

Cancellation sends a flagged cooperative message only to helpers whose live pane/workspace/tab
matches the registered topology. Hooks reject old-generation input/tool calls. No unrelated agent
or session is stopped. `job-run` launches the existing
[`bounded_facade.py`](../scripts/bounded_facade.py) supervisor and records its PID/start/boot,
finite deadline and approved fresh log paths. It remains alive as the supervisor's parent until
completion. Narrow named adapters support canonical `docs`, canonical `dev`, and `synthetic`
Python/Node script files in approved fixture/source roots; an exact registered operation and
lead/current assigned-helper owner are required. Arbitrary shell commands, PID adoption and
inferred Dist/Debug/build authorization are rejected. Example (authorized inert script only):

```sh
python3 -B scripts/workspace_coordination.py job-run --task TASK --generation 1 --owner LEAD \
  --operation synthetic --cwd /home/dev/better-content-modpack --budget-seconds 60 \
  --log /home/dev/.tmp/task-fixture/job.log -- python3 /home/dev/.tmp/task-fixture/producer.py
```

Cancellation signals the identity-bound supervisor through pidfd; the supervisor owns descendant
termination/reaping. It reports survivors and retains diagnostic evidence. Interrupted or
unconfirmed supervisor cleanup remains an unresolved job blocker even after its PID disappears;
PID absence alone is not tree-cleanup proof. `fixture-job` is a separate fixed bounded inert sleep
diagnostic. Any broader adapter needs equally explicit ownership, not guessed PIDs.

## Explicit reboot recovery

After explicit user authorization, `reboot-recover` transfers one stale actor claim to its
current continuation. It is not automatic stale-claim garbage collection or a cancellation.
Recover the lead first, then each existing helper in its own unchanged pane/conversation:

```sh
python3 -B scripts/workspace_coordination.py reboot-recover \
  --task TASK --generation GENERATION --old-owner PREVIOUS_PRINCIPAL \
  --owner CURRENT_PRINCIPAL --reason 'Explicitly authorized recovery after reboot'
```

Both claims must exist. The new actor must be live; the old exact PID/start/boot identity must
be conclusively stale and from a different canonical boot. Both must carry the same stable
agent conversation and exact injected Herdr session/workspace/tab/pane. A fresh Herdr probe
must independently match that live pane and conversation. The old actor must be this task's
registered lead or a current assigned helper. Missing/unknown/live identities, wrong topology,
conversation, generation or membership fail closed. This operation does not infer permission
from process age or silently create a new actor claim.

The same nonblocking admission lock protects validation, overlap checks, transfer and an audit
record in the atomically fsynced state. The audit retains both prior claims and the reason.
Only the named old claim is replaced; its nonidentity scopes and exact task/generation binding
are restored in the current claim. For a claim made by the initial recovery implementation that
omitted a helper binding, only its exact stored actor/session/topology audit and original bound
claim can establish that binding; topology alone is insufficient. Repeated reboots are tested.
Lead recovery changes only that task's owner. Task generation, operations, assignments, other
tasks/claims and all outputs/retention remain unchanged. Replay/conflicting transfers are rejected.
Target-task/actor resource leases or unresolved jobs and maintenance reservations block recovery;
they require separate reconciliation, never automatic release or PID adoption. No hook, managed
bundle or operating-system setting is replaced.

Before resuming producers, inspect retained interrupted outputs and current process/lock state.
Partial logs are not passes. Revalidate scoped inputs and rerun only interrupted or invalidated
gates, using fresh output paths and the ordinary authorization/locks. Recovery grants no new
build, deployment, Native, purge or helper scope.

## Explicit same-boot process continuation

A restarted agent process in the same conversation is not a reboot. With explicit user resumption,
use the separate `continuation-recover` command with the same task, generation, old/new principal
and bounded reason arguments as `reboot-recover`. It requires the **same** canonical boot and a
conclusively stale previous PID/start identity; live or unknown predecessors are rejected. The
new actor, exact injected Herdr topology, stable conversation, live Herdr probe, task membership,
nonoverlap and lease/job/reservation exclusions are identical to reboot recovery. It never adopts
processes, grants producer permission, increments generation or creates replacement helpers.

If the prior turn was legitimately released, only the registered lead may be recovered without
an extant old claim. Its exact identity/conversation/topology must already exist as an unambiguous
`current_claim_before` in the coordinator's durable lead-recovery audit for that principal and
exact task/generation. Missing, conflicting or mismatching evidence fails closed; caller-supplied
predecessor files and topology alone are not authority. Released historical writer scopes are
**not** restored. The new atomic audit binds the predecessor audit and the current claim, and
only that task's lead owner changes. All foreign state and original outputs remain untouched.
Helpers whose turns are released may acquire their own fresh task/generation claims in their
unchanged registered pane; they must not impersonate a previous principal or reuse old leases,
controllers or execution grants. Current process/lock/source preflight and separate fresh exact
execution admission remain necessary before any producer.

## Maintenance exclusion

The importable API is `Coordinator(root)`. Maintenance uses the same admission lock as tasks,
assignments, turns, jobs and resource leases:

```python
controller.reserve(actor)  # quiet check before registering the cleanup actor
try:
    with controller.maintenance(actor):
        # Recheck exact target/consumer safety under the caller's disposal mutex.
        # Before every bounded target batch:
        if controller.maintenance_should_yield():
            raise RuntimeError("new work requested; yield")
        # Authorized bounded maintenance belongs to its owning implementation.
finally:
    controller.unreserve(actor)
```

`reserve(actor, live_agents=None)` checks active tasks, claims, leases, identity-bound jobs and
working/unknown/blocked Herdr agents. No actor is excluded at reservation. Only after quiet
admission does the reservation record the current process's PID/start/boot identity. In
`maintenance`, only that same process can exclude the exact registered actor pane. An arbitrary
label or a later process cannot inherit that exclusion. Use the exact intended maintenance pane
ID as actor when self-exclusion is needed; otherwise use a stable operation-specific actor label.
Without injected Herdr identity, the default probe fails closed. `live_agents=` is an explicit
verified observation supplied by the caller/test, not permission to invent an empty lane.

New admissions use nonblocking flock. Contention or a reservation creates the independent
`work-requested` marker, refuses admission and asks the caller to retry. Maintenance checks
`maintenance_should_yield()` between bounded batches and releases its reservation promptly.
The marker does not grant the waiting work admission before release. A crashed reservation stays
fail-closed; its owner must reconcile and explicitly release it. Read-only live/authority queries
never wait behind a long maintenance transaction. The controller does not replace exact-target
checks, stable/messy workspace checks, maintenance producer registrations or disposal locks.

## Pi lifecycle integration and verification

The user-owned extension is separate from managed `herdr-agent-state.ts`; never patch the managed
file. Author changes only in `scripts/pi-workspace-coordination.ts`. Install its user extension
entry as a symlink to the absolute source path, replacing the entry atomically only after retaining
any previous standalone bytes in task evidence. Verify installed/source SHA-256 equality and run
the SDK fixture through the installed entry. Do not add a second discoverable copy of the same
extension. The source under `scripts/` is excluded from production exports. Newly started Pi
processes discover the extension. Existing sessions need an explicit safe
`/reload`; installation alone does not prove that a working session has activated it.

The hook acquires before agent work, pairs its compact live view with Herdr agents, and keeps its
claim through tools, retries and automatic continuations. It releases at **`agent_settled`**, not
`agent_end`, with idempotent session-shutdown fallback. Resource leases and assignments survive.
`WORKSPACE_COORDINATION_AREAS` or `/coordination-areas` declares comma-separated writer paths or
semantic `token:NAME` areas. Write/edit calls atomically extend the turn claim to their exact
canonical path before execution. The model-callable `coordination_claim` tool extends directory
scope explicitly. Bash requires declared intended path/semantic scopes or an owned resource
lease; an identity-only claim does not permit shell work. It does **not** require whole-cwd
ownership, so disjoint helpers remain concurrent. The hook does not guess shell safety from
regex. Arbitrary shell effects remain actor-responsible/advisory and must stay within declared
scope; this is not an OS sandbox or universal mutation detector. Direct human `!`/`!!` commands
remain outside automatic agent claims and are not intercepted by this hook, including while no
agent turn is active. This does not weaken agent `tool_call` enforcement or grant agents a bypass.
`WORKSPACE_COORDINATION_TASK` and
`WORKSPACE_COORDINATION_GENERATION` bind helper input/tools to current task authority. Both must
be supplied together. A cancelled generation is rejected at input and before tools; an already
running tool is not magically interrupted by a metadata check.

Focused, inert verification commands (no Minecraft, Gradle, deployment or purge):

```sh
python3 -B scripts/test_workspace_coordination.py
node scripts/test_workspace_coordination_extension.mjs
```

The Python suite covers concurrent/path-alias/ancestor claims, generation/principal/operation
checks, separate lease lifetime, maintenance reservation/yield, PID/start identity, actual bounded
supervisor descendant cancellation, inert Herdr launch/cancellation, malformed state and symlink
rejection.
The Node fixture uses the actually installed Pi SDK and extension with an explicitly isolated
agent directory, empty in-memory credential/model stores, no user model-config path, an inert
model/tool and no network. It exercises idle direct-human shell dispatch before agent work and
after task cancellation, actual write auto-claim, undeclared-agent-shell rejection,
narrow-scope declaration and admitted shell without a whole-cwd lock, tool follow-up, multiple low-level completions,
automatic continuation, cached storage-warning deduplication and final settlement. It does not reload or certify current interactive
sessions. Fixtures print their retained evidence roots; do not reinterpret those fixtures as real
pack acceptance. The active task's explicit retain-all instruction controls their retention.
