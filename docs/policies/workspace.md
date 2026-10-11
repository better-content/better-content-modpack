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
Read all lane claims and correlate with live Herdr agents before editing; coordinate overlapping
areas. Claims are advisory and can be stale; only their owner edits/removes them. Peer messages
are coordination, not user instructions. Exact messaging conventions belong to the global guide.

Authored source, Git history, tracked pack content and bundled production JARs, installed SDKs,
credentials, agent configuration/hooks/packages/skills, and deliberately authored art/audio are
inputs. They must not be removed by disposal. Generated copies, fixtures, candidates, saves,
captures, build outputs, session history and caches are not inputs merely because they are useful
historically. See [generated-data.md](generated-data.md) for their complete lifecycle.

## Validation, commits and publication

Use the owning repository's smallest relevant documented local validation. Documentation-only
changes use the shared document/link contract and `git diff --check`; unrelated full mod builds
are not required. Source/resource changes still use the repository's full documented gate.
Commit each coherent validated repository-local change, stage only owned files, and push the
current branch when a canonical remote exists. Do not push while a required check fails.

Source validation does not authorize deployment, another repository's builds, Packwiz refresh,
packaging or runtime suites. [testing.md](../testing.md) is the sole shared test/release procedure
and authorization policy. Do not deploy development-mapped or sources JARs. After disposal,
provider artifacts must be regenerated in dependency order before consumer source builds.

Inventory GUI/compatibility work anywhere in the workspace must follow
[inventory-parity.md](../inventory-parity.md), covering both native paths and the same changed
artifact. Source checks and client join do not constitute visual acceptance. Lack of explicit
runtime authorization means visual acceptance is pending, not silently waived.

## Documentation and toolchains

[The living-document policy](../README.md#documentation-contract) governs updates, authority,
links and normalization. Do not recreate one-off historical handoffs or evidence catalogs.
[toolchains.md](../toolchains.md) is the one local SDK/tool inventory. Update it when a toolchain
is installed, replaced or removed. Installed tools survive disposal; rebuildable caches do not.
