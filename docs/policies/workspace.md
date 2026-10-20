# Better Content workspace policy

## Authority and scope

This is the canonical shared policy for `/home/dev/better-content-modpack` and all
`/home/dev/mod_source/<repository>` checkouts. Start at [the documentation index](../README.md).
Global environment/tooling/Herdr behavior belongs to `/home/dev/.config/agent-guidance/lane.md`.
Explicit current user instructions determine task authorization; a claim, old plan, available
command, or installed tool does not grant it. Repository `AGENTS.md` owns unique local commands
and constraints, not a second copy of shared policy.

## Layout and ownership

`/home/dev` is not a Git repository. Custom-mod sources live only in `mod_source/`, beside the
modpack, never inside the pack or at former top-level paths. The canonical inventory is
[custom-mod-workspace.md](../custom-mod-workspace.md); the executable release graph is
`gradle/active-custom-mods.json`. Count active entries there rather than hard-coding a count.
Mod-specific design belongs in its owning repository; pack integration belongs in this docs tree.

Before editing, inspect each affected repository's `git status --short`. Preserve unrelated dirty,
untracked and ignored authored inputs. Never use blanket `git clean`, reset, or history rewriting.
Inspect the compact [coordination view](../workspace-coordination.md) and live Herdr agents at
turn start, then relevant ownership details before editing overlapping areas. Historical narrative
claims are snapshots, not proof of current work; do not reread every old claim on every turn.
Acquire atomic turn-scoped claims before editing and release at the actual end of the turn,
including failure/cancellation. Tools and automatic continuations belong to the same turn.
Task assignments, work claims, operation-lifetime leases and retention records are separate.
Controller claims remain owner-released; unknown/inconsistent ownership needs reconciliation.
Legacy Markdown claim files may also be removed after confirming their owners are inactive under
the lane guide's fresh Herdr/ownership checks. This exception does not release controller state,
resource leases, task registrations or retention records.
Peer messages coordinate work but never grant user authorization.

Each task has one accountable lead, not a permanent workspace-wide hierarchy. Leads may delegate
bounded independent subtasks within user authorization, specifying objective, files, starting
identity, allowed checks/resources, deliverable and return route. Use a dedicated helper Herdr
workspace with one helper per separate tab, never pane splitting; remain solo if this topology
is unavailable. Helpers do not recursively delegate by default. Register task/generation identity,
reject late superseded results, review integration and account for surviving jobs. Serialize
shared Git-index integration and overlapping writers, not all readers/disjoint work.

Explicit cancellation cascades to that task's helpers and owned jobs while preserving edits and
evidence. Ordinary turn completion releases work claims but does not cancel assigned work.
Full-lifetime Forge/pack resource locks remain separate. Communicate meaningful events with
bounded waits and no acknowledgement loops. Exact messaging conventions belong to the lane guide.

Authored source, Git history, tracked pack content and bundled production JARs, installed SDKs,
credentials, agent configuration/hooks/packages/skills, Pi conversation history, and deliberately
authored art/audio are inputs. They must not be removed by disposal. Pi history under
`/home/dev/.pi/agent/sessions/` remains protected even when its conversation is inactive or ended;
only an explicit user request to delete the specified history authorizes removal. Generated copies,
fixtures, candidates, saves, captures, build outputs, other generated agent data and caches are
retained by default without becoming authored inputs. Completion, cancellation, failure, claim
release and handoff do not delete them. See [generated-data.md](generated-data.md) for deliberate
classification and quiet/stable/messy reclamation, with independent protection of latest and
last-known-good candidates and necessary evidence/support artifacts.

## Validation, commits and publication

Use the owning repository's smallest relevant documented local validation. Documentation-only
changes use the shared document/link contract and `git diff --check`; unrelated full mod builds
are not required. Source/resource changes still use the repository's full documented gate.
Commit each coherent validated repository-local change, stage only owned files, and push the
current branch when a canonical remote exists. Do not push while a required check fails.

Source validation does not authorize deployment, another repository's builds, Packwiz refresh,
packaging or runtime suites. [testing.md](../testing.md) is the sole shared test/release procedure
and authorization policy. Explicitly ordered package-only revisions use untested letter suffixes
(e.g. `443a`); they never inherit the parent build's acceptance. Follow its
[untested lettered-build procedure](../testing.md#untested-lettered-builds), respecting any explicit
prohibition on builds or checks. Do not deploy development-mapped or sources JARs. After disposal,
provider artifacts must be regenerated in dependency order before consumer source builds.

Inventory GUI/compatibility work anywhere in the workspace must follow
[inventory-parity.md](../inventory-parity.md), covering both native paths and the same changed
artifact. Source checks and client join do not constitute visual acceptance. Lack of explicit
runtime authorization means visual acceptance is pending, not silently waived.

## Documentation and toolchains

[The living-document policy](../README.md#documentation-contract) governs updates, authority,
links and normalization. Do not recreate one-off historical handoffs or evidence catalogs.
[toolchains.md](../toolchains.md) is the one local SDK/tool inventory. Update it when a toolchain
is installed, replaced or removed. Installed tools are protected inputs. Rebuildable caches remain retained/reusable until deliberate
classification and authorized quiet/stable/messy maintenance; they are not deleted at handoff.
