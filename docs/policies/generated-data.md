# Generated data is disposable

## Scope and authority

This is the sole Better Content retention/disposal policy. It covers all development worlds and
saves, runtime instances, candidates/distributions, test evidence, failed fixtures, captures,
dumps/logs, agent sessions/history/images, dependency caches, and repository build/provider JAR
outputs. This environment is **not a save backup service or historical archive**. No world is
sentimentally protected; dead-build worlds are not a reason to keep a build alive. Current
candidates, latest passes, unresolved failures and sealed distributions receive no automatic
retention exemption. Authored/operating inputs remain inputs as defined in
[workspace.md](workspace.md); never classify `.codex`, `.cache`, or an art directory wholesale.

## Task lifetime

Keep outputs while their task/consumer is active, including diagnosis, retries and sequential
tests of one unchanged candidate. Before final success, failure, cancellation or blocked handoff,
report results and dispose of the task's outputs. Report deleted paths as deleted, not available
for later inspection. An old passing result is not fresh runtime verification.

Stop owned producers gracefully before finishing; do not kill unrelated agents or remove files
under a live consumer. Queue work is an active consumer, not historical retention. An explicitly
requested delivery/pin must identify the exact path and an expiration time/condition; never
invent a permanent pin. An active safety deferral means cleanup is incomplete and must be
reported and retried once the owner releases/exits. Release/deployment authorization is separate.

Rebuildable dependency caches, wrapper downloads and local provider/build JARs are deleted at
handoff once idle. Installed SDKs/tools, credentials, configuration, tracked wrapper/bootstrap
inputs and bundled production JARs survive. The next source build downloads dependencies and
rebuilds providers in the manifest's dependency order. No cache warming/rebuild is part of cleanup.

## Commands and producer contract

Run from the modpack (installed Kotlin SDK; no Gradle dependency):

```sh
./maintenance.main.kts audit
./maintenance.main.kts begin --task TASK_ID --path /home/dev/workspace_artifacts/reviews/TASK_ID
./maintenance.main.kts finish --task TASK_ID --apply
./maintenance.main.kts prune --apply
./maintenance.main.kts prune --resume TRANSACTION_ID
```

For Python-only cleanup, including after caches have been deleted:

```sh
python3 -B scripts/workspace-maintenance.py audit
```

Register a task **before** producing or consuming disposable files. Repeat `--path` for each
output/input boundary it consumes, including exact candidate paths; tasks also hold the shared
build-cache lease. `finish` closes the lease and disposes eligible idle outputs. The command never
stops processes itself: the caller first shuts down its producers and coordinates with other
owners. Read-only consumers use `begin --no-build-cache` to avoid a needless provider/cache lease.
Exit 3 means active or permission-blocked disposable targets remain; exit 1 means unsafe or
interrupted cleanup. Foreign-owned outputs are still disposable, not promoted to authored inputs:
report the blocker and ask their owner/host administrator to release permissions, then resume.
Never escalate privileges or silently claim the purge is complete. `audit` does not mutate anything. Failed fixture/report contents and missing
candidates do not prevent deletion.

Temporary delivery pins use `begin --task DELIVERY_ID --path PATH --pin-until UNIX_SECONDS` with
a concrete user-requested deadline. Finish the delivery task after transfer; the pin protects
only until that deadline. Unregister completed unpinned tasks at finish. Stale active registrations
require owner reconciliation, not guessed expiry or automatic killing.

Supported producer entry points are release/test controls and documented local verification
commands; agents wrap their full activity in `begin`/`finish`. Do not attach an unconditional
per-command trap that deletes a candidate between tests or while a queue still consumes it.

## Safety and storage

The v2 cleanup interface emits `delete`, `defer_active`, and `protect_input` decisions, allocated
bytes and consumer identities. It uses an explicit output-root allowlist, tracked/nonignored
input detection, live cwd/FD/mapping/session checks and task leases. Disposable symlinks are
unlinked without traversing their targets; ancestor symlinks, path escapes, target substitution
and unexpected mount boundaries are rejected. Unknown paths outside classified output roots
are inputs until inspected, not blanket deletion targets.

Cleanup stores only bounded live-task metadata and incomplete transaction records under `.worklane/`.
Completed transactions are removed, not accumulated as history. The final handoff reports
measured space reclaimed and exact active deferrals in text; it does not create another archive,
Git bundle, evidence backup or compatibility link. `workspace_artifacts/README.md` is merely a
pointer here; its other contents are disposable task output.
