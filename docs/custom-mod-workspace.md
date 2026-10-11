# Custom-mod workspace and build inputs

## Scope and authority

Better Content custom mods are independent Git repositories under `/home/dev/mod_source/`,
beside `/home/dev/better-content-modpack`. Preserve this relative layout in other environments.
Do not recreate source inside the pack, at former top-level paths, or behind compatibility links.
Canonical remotes use `https://github.com/better-content/<repository>.git`.

The authoritative active inventory is
[`gradle/active-custom-mods.json`](../gradle/active-custom-mods.json): repository, mod ID,
canonical runtime artifact, verification/staging tasks and `dependsOn` build edges. Do not
maintain a competing table/count here. Read each owning `AGENTS.md` for unique local constraints.
All current runtime identities are a clean break; no legacy config/world identity migration.

| Non-active repository | Status and local verification |
|---|---|
| `burnt-grass-compat` | Validation-only; `./gradlew verifyFull`; active wildfire owns pack behavior |
| `dynamic-trees-dimension-compat` | Validation-only; `./gradlew runData verifyFull`; Undergarden retired |
| `better-ratlantis-logistics` | Retired/history-only after Ratlantis cut; no active build/staging/bundling |

No non-active JAR enters `mods/` without explicitly reactivating the feature. There are 47 source
checkouts currently; derive future counts from actual checkouts/manifest, not this historical number.

## Local build and provider bootstrap

Run validation and staging from the owning checkout using its Gradle wrapper. Java 17 is selected
through toolchain discovery, never a machine-specific source path. Wrappers own their official
distribution SHA-256; neither global Gradle nor another checkout supplies the launcher.
`stageRuntimeJar` writes the deployable reobfuscated artifact under `build/libs/`. Development-mapped
and sources JARs are not runtime inputs.

Typed consumers accept `BC_CUSTOM_MOD_JAR_DIR`, a directory containing canonical runtime filenames.
Blank overrides or missing required JARs fail configuration rather than silently falling back.
Without the override, local builds use sibling `build/libs/` provider artifacts. When task-handoff
cleanup has removed them, stage providers in manifest topological dependency order before building
consumers. Do not make Threads/event listeners upstream providers or introduce dependency cycles.
Release preparation stages providers and passes one explicit staging directory to consumers.

[testing.md](testing.md) is the sole deployment/release/testing procedure and authorization policy.
An explicitly ordered single-mod deployment copies only its staged runtime artifact; no unsolicited
pack refresh, distribution or runtime suite follows a source build. `release.main.kts` owns group
staging/deployment and source identity checks for an explicitly requested distribution.

## CI and reproducibility

Consumer CI pins an immutable modpack commit and uses
`.github/scripts/prepare-provider-jars.py --pack-root PATH --repository NAME --output DIRECTORY`.
The standard-library Python 3.11+ helper verifies closure, Packwiz SHA-256 and bundled identities;
it does not build, deploy, warm caches or package. Some API consumers stage a provider from an
immutable source revision when the pinned bundled baseline predates the required API. Inspect
the current owning workflow, not historical product names, to determine that exception.

The modpack reflection check materializes source-only revisions from
`.github/ci-source-revisions.json`, including validation-only repositories. Update pins deliberately
after reviewed source changes; do not skip the check because a runner lacks `mod_source/`.
Provider/CI pins must not float. SDK inventory is [toolchains.md](toolchains.md).
Snapshot plugin artifacts and transitive dependency verification remain reproducibility limits;
do not introduce an unrequested toolchain upgrade while normalizing documentation.

## Reflection and provenance boundary

Use typed APIs, Forge events or narrow mapped Mixin accessors/invokers. Runtime reflection is
allowed only for the diagnostic adapters/direct tests listed by exact path in
`gradle/reflection-allowlist.txt`. Fresh-release preflight scans all canonical source repositories;
the staged-JAR gate scans all active artifacts. An unlisted reflective call aborts release.
Optional integrations require explicit loaded-mod/supported-version guards; missing mods are inert,
but API drift in an expected pinned integration is a visible validation failure.

Bundled/staged JARs record repository, mod ID and source HEAD in
`META-INF/better-content-source.properties`. Source identity checks are local-only—no fetch/pull/
merge or remote contact. Full tested releases force verified clean rebuilds; targeted and explicit
test-free flows can reuse source-identical annotated JARs according to the release implementation.
[Testing](testing.md) defines those modes, without a second conflicting procedure here.

## Disposal and related guidance

Build/provider JARs, wrapper/dependency caches, isolated test worlds and reports are disposable at
handoff under [generated-data.md](policies/generated-data.md). Installed toolchains, tracked
production JARs and authored fixture templates/assets are inputs and survive. A fixture world is
not an authored GameTest template merely because both have similar names.

- [Workspace policy](policies/workspace.md)
- [Documentation authority](README.md)
- [Native inventory parity](inventory-parity.md)
