# Generated data: retention and deliberate reclamation

## Scope and authority

This is the sole Better Content retention/reclamation policy. It covers development worlds and
saves, runtime instances, candidates/distributions, evidence, failed fixtures, captures, dumps,
logs, generated agent data, dependency caches and repository build/provider outputs.
[Workspace policy](workspace.md) defines authored/operating inputs. Task completion, failure,
cancellation, blocked handoff, claim release and expired delivery pins are **not deletion triggers**.
Retain generated material by default across conversations, including unresolved diagnoses.
Retention is not a claim of correctness or fresh runtime acceptance.

Authored source, Git history, production inputs/JARs, installed tools/SDKs, credentials,
configuration, authored art/audio and **the entire `/home/dev/.pi/agent/sessions/` tree** are protected.
Pi history, including inactive conversations and attachments, requires an explicit user request
naming the history to delete. Never classify `.codex`, `.cache`, or art directories wholesale.

## Candidate and verification preservation

Protect the complete latest packaged candidate, not merely its two ZIPs. Candidate ordering is
numeric build then optional lowercase revision (`443 < 443a < 444`); partial builds do not replace
complete pairs. Lettered or newly packaged builds do not inherit acceptance.

Independently preserve the last known-good distribution and its evidence/support artifacts. A
failed or untested newer candidate must not displace it. The stability registration records exact
artifact/evidence hashes and current production identity; it is an operator-recorded acceptance
basis, not a verifier that creates runtime proof. Include required retry/support artifacts when
recording that basis, and use retained classifications for supporting directories. Missing or
changed evidence makes current stability unknown but does not remove last-known-good protection.

Completed verification receipts and immutable supporting artifacts may survive conversations.
Reuse requires the relevant source, toolchain, contract, dependency and artifact checks described
in [testing.md](../testing.md). Never adopt another owner's active mutable run, import a verdict
from successful-looking logs, or describe historical acceptance as a fresh native run. Retention
is an optimization: documented tests must still work with empty generated/cache directories.

## Activity, retention and ownership are separate

Task assignments, turn-scoped work claims, operation-lifetime resource leases and retention
records are separate. See [workspace policy](workspace.md). Register producers/consumers before
using outputs, including exact candidate and support boundaries. Read-only consumers may omit
the shared build-cache lease. Shut down owned producers gracefully before closing their activity;
never kill unrelated processes. Queue requests remain consumers until completed or cancelled.

`finish` closes only the named owner's activity and deletes **nothing**. Its old `--apply` spelling
is accepted for compatibility but does not prune. Closed task records and paths remain retained.
`release-retention` is a separate reasoned owner decision, not deletion; explicit output
classification and all cleanup gates still apply afterward. A pin expiration ends a lease only.

Legacy registrations preserve their paths and data. Unknown ownership requires explicit
reconciliation with a reason; current actors must not impersonate an old owner. Do not clear
another task's registration merely because its agent disappeared. No installation-time purge,
old-plan replay, or wholesale migration cleanup is permitted.

## Inventory and commands

The standard-library controller is `scripts/workspace-maintenance.py`; `maintenance.main.kts`
is its Kotlin facade and does not start Gradle. Inventory distinguishes **protected, retained,
reusable, reclaimable and unknown**. Unknown legacy outputs remain retained until inspected and
deliberately classified. A `delete` action in a read-only plan means explicitly reclaimable,
not permission to execute that plan.

```sh
./maintenance.main.kts audit                         # read-only full inventory
./maintenance.main.kts status --cached               # bounded, no payload scan
./maintenance.main.kts measure                       # update cached sizes/growth
./maintenance.main.kts begin --task TASK --owner OWNER --path /absolute/output
./maintenance.main.kts update --task TASK --owner OWNER --path /another/output
./maintenance.main.kts finish --task TASK --owner OWNER
./maintenance.main.kts release-retention --task TASK --owner OWNER --reason 'need resolved'
./maintenance.main.kts classify --path /absolute/output --kind reclaimable --reason 'obsolete fixture'
./maintenance.main.kts prune --apply --owner MAINTENANCE_ID
./maintenance.main.kts prune --resume TRANSACTION_ID --owner MAINTENANCE_ID
```

`--owner` is a stable session/task principal, defaulting to `BC_OWNER_SESSION` or `PI_SESSION_ID`
when available. Legacy owner reconciliation additionally requires `--reconcile-legacy --reason`.
Repeat `--path` to add consumed boundaries; use `--no-build-cache` for read-only consumers.
Explicit delivery deadlines use `--pin-until UNIX_SECONDS`; expiry alone never deletes data.

A good-state record requires `stability --state good --basis TEXT --artifact PATH --evidence PATH`
with repeatable artifact/evidence arguments. Record the actual acceptance scope, not a unit-test
pass masquerading as modpack usability. `stability --state broken|unknown --basis TEXT` invalidates
current good-state eligibility without erasing the last known-good retention record. Production
and evidence identities are revalidated before pruning. Never fabricate an acceptance record to
make cleanup pass.

## Quiet + stable + messy

Real pruning requires all three:

1. **Quiet:** no agents working, and no conflicting registered work, claims, jobs or resource
   leases. Unknown liveness fails closed. Low CPU and absence of tests do not establish quiet.
2. **Stable:** the current modpack is known good and usable, with an acceptance basis and matching
   current production/artifact/evidence identities. Idle agents, unchanged files, warning absence,
   old builds and source-unit passes do not establish this.
3. **Messy:** enough explicitly reclaimable, unblocked material exists to justify deletion.

Observe quiet before taking an exclusive coordination reservation. Admissions request a yield
rather than overlapping maintenance. Recheck before targets and within large trees (bounded
64-entry deletion batches); new work causes a safe stop. Never bypass admission by invoking the
internal deletion function directly. Interrupted transactions remain for inspection and must
revalidate targets; v2 deletion transactions cannot resume under the new policy.

Initial configurable thresholds are warning at 80% filesystem use or under 50 GiB available,
critical at 90% or under 20 GiB, and messy at 20 GiB reclaimable. `configure` accepts
`--warning-used`, `--critical-used`, `--warning-free`, `--critical-free`, and `--messy-bytes`;
free/size arguments are bytes. Report filesystem capacity as such, not as a confirmed lane quota.
Storage warnings never override quiet/stable/consumer protections or fail correctness gates.
Cached status reports measurements' age, growth and largest targets; measurements older than an
hour are explicitly stale. Agents should surface actionable, deduplicated warnings rather than
scan the entire workspace on every tool call.

## Deletion safety

Explicit output roots, tracked/nonignored input detection, live cwd/FD/mapping/session/socket
checks, task retention and resource leases remain enforced. Preserve all active consumers and
unresolved diagnoses. Prefer obsolete fixtures/staging over useful dependency caches; useful
reusable material is not reclaimable merely because it can technically be rebuilt.

Reject escaping paths, symlink ancestors, substituted targets, unexpected mounts and unsafe state
files. Unlink disposable symlinks without following targets. Recheck protection and consumers
before deletion. Report permission blockers; never escalate privileges, chown foreign outputs,
or silently report completion. Exit 3 means permission-blocked eligible targets remain; errors,
unsafe paths and interrupted admission fail without claiming a completed purge.

Reports state actual removed paths/bytes, retained material and blockers. `workspace_artifacts/`
contains retained task outputs, not authored production content. Do not move diagnostics into
versioned docs or create shadow archives, Git bundles or compatibility links to evade inventory.
