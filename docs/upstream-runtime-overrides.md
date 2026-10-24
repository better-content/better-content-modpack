# Local upstream runtime corrections

This is the authority for the five **local-only** resource-corrected archive identities in
`config/better-content-runtime-overrides-v2.json`, the separate two-artifact compatibility
contract in `config/better-content-interface-runtime-overrides.json`, and the independent
ATL applicability correction in `config/better-content-startup-runtime-overrides.json`.
Legacy `config/better-content-runtime-overrides.json`, its builder/specification and historical
receipts remain unchanged; they are not the active successor qualification. This document does not replace [testing](testing.md),
[source qualification](custom-mod-workspace.md), or [retention](policies/generated-data.md).

## Scope and original identity

The retained original `bc-runtime.1` recipe lives in the owning Compat source repository,
`src/upstreamRuntime/overrides-v1.json`; its builder and source controls live under `scripts/` there.
The tracked Root policy binds exact source/builder hashes, original SHA1/SHA256 and CurseForge
identities, corrected filenames/SHA256s, resource witnesses and recursive class inventories.
Existing `.pw.toml` original pins and original download-cache files remain unchanged.

Only these runtime resources change:

- Forgified Fabric API: five missing Mixin `minVersion` declarations; move only `MouseMixin`
  and `ScreenAccessor` into the existing screen API client list inside the nested archive.
- EMI Loot and YACL: one missing `minVersion` declaration each.
- Relics: move only `ScreenMixin` from common to the existing client list.
- AdPother: remove only the three obsolete Cold Sweat hearth / Little Logistics locomotive / tug
  coremod registrations. Exact-gated production Compat replacements already own these emissions;
  repairing both paths would double-count. Shared scripts and their failure reporting remain intact.

All 3,144 recursive class bytes, licenses, untouched scripts and compressed records are invariant.
Wrong pins, unexpected resources/structure, duplicates, signatures, unsafe paths and unsupported
archive layouts reject rather than being normalized, ignored or stripped. Changed deflate output
is bound to the recorded compressor version; another environment must reproduce the authored
output identity or receive a separately reviewed recipe/policy revision.

## Reviewed resource successor and qualification

The separately versioned `bc-runtime.2` recipe is `src/upstreamRuntime/overrides-v2.json` in
Compat, built by `scripts/build_upstream_runtime_overrides_v2.py`. It additionally removes only
`coldsweat$boiler`, whose obsolete transformer cannot find its target in exact Cold Sweat 2.4.
All four removed registrations are checked as an exact distinct population. Shared scripts,
licenses and all 3,144 recursive upstream classes remain unchanged. The first four archive
filenames/bytes remain `bc-runtime.1`; only AdPother becomes `bc-runtime.2`.

The production Hearth injections now select exactly `drainFuel()V`. Actual Mixin 0.8.5 selection
controls show that the old unqualified selector chose the first declared matching overload,
which is the intended outer method in this pinned class. This is order-safety hardening, **not**
evidence of historic double injection. The successor binds the independently compiled replacement
class digest; the original v1 digest is not widened or rewritten.

Compat's active `qualifyUpstreamRuntimeOverridesV2`, part of `verifyFast`, checks exact production
replacement/plugin bytes and runs 19 pure/pinned-original controls, including deterministic
repeat builds and legacy recipe/builder invariance. Originals and every attempt are retained
under `build/upstream-runtime-v2-evidence/`; successful support snapshots are under
`build/upstream-runtime-overrides-v2/`. Root declares six support artifacts in
`gradle/active-custom-mods.json` and binds them to the owning source qualification.

## Assembly

After all required source gates, Root's release preparer independently checks declared support
hashes, exact recipe/resource/inventory witnesses, current original pins and actual staged Compat
replacement/config bytes. The pre-stamp raw provider SHA is freshly recorded, not inherited from
a historical receipt; the separately stamped qualified provider SHA is recorded too. Only that one
variable raw-provider receipt field is normalized in the authored template. It seals an immutable
copy plus `selection.json` under the release evidence and publishes the local `current.path` pointer.
No prior runtime verdict transfers.

`package.sh` remains the sole packager; `dist.sh` remains package-once with no qualification or
content verdict. Normal assembly resolves immutable originals, then substitutes the prepared
separately named copies in generated trees. Client export removes only the five exact original
CurseForge tuples before embedding corrected overrides, preventing a launcher from downloading
duplicate originals. Direct packaging uses the last prepared input pointer; missing inputs fail
rather than silently shipping originals. Explicit untested releases retain their untested status
and may consume those prepared inputs; that is not fresh source or runtime acceptance.

Exact-candidate contracts require authored policy bytes, corrected archive hashes, original/tuple
exclusion and actual Compat replacement/config registration on **both** archives. Full client/server
initialization, join, heartbeats and strict native audits are still required. Resource/class controls
alone are not proof of an actual native fuel-consumption operation or full-pack acceptance.

## Independent interface/side compatibility corrections

The separate `bc-interface.1` recipe, builder and 12 actual-original/pure controls are owned by
Compat's `src/upstreamRuntime/interface-overrides-v1.json` and `interface-README.md`. It does
**not** modify the preceding five-artifact recipe, its outputs, or its 3,144-class invariant.

- Move only Epic ParCool's client animation accessor from common to client.
- Move only VS's Create chain-conveyor interaction/rendering Mixin from common to client.
- For the exact VS vibration ticker interface, change exactly three PRIVATE|STATIC method
  access words to PUBLIC|STATIC. Preserve STATIC, every descriptor/annotation/Code attribute,
  instruction and all other class bytes. The third handler still delegates with INVOKESTATIC.
  This backports declaration visibility for active Mixin 0.8.5; it does not replace global libraries.

The additional two originals contain 8,062 recursive classes; exactly one changes three access
bytes. Root independently derives those offsets and compares the complete expected class, not
just a permissive `class_bytes_invariant=false` flag. Unexpected body, metadata, static-bit,
method, resource or archive changes reject. Relics' mixed client/server Item Mixin stays common;
its server cooldown and registration behavior must not be dropped to hide a client-type warning.

`qualifyUpstreamInterfaceOverrides` retains separate originals/attempts and publishes three
source-qualified support files under `build/upstream-interface-overrides/`. Root seals separate
`interface-overrides/` evidence and `.worklane/interface-overrides/current.path`; the sole packager
applies the five-resource successor, then these two, then the independent ATL correction, with
one normal Packwiz export and eight exact CF tuple exclusions.
The new receipt has no variable provider field or normalization. Both validators bind both exact
ZIPs. All startup/join/heartbeat/teardown and raw-log requirements remain; source/class controls
alone are not runtime acceptance. Fixture audit derivation additionally admits additive Mixin
DEBUG application records while retaining the INFO root, all failure severities and unchanged
packaged production logging. No suppression or audit exemption is added.

## Independent ATL startup applicability correction

`bc-startup.1` corrects only the common Citadel issue's `@Issue.versionRange` constant-pool UTF8
operand in exact AllTheLeaks 1.1.1: `[2.5.4,)` becomes `[2.5.4,2.6.3),(2.6.3,)`. Actual Maven range
semantics exclude the reviewed Citadel 2.6.3 ABI and equivalent version spellings, without
inventing the earliest map-removal version or changing decisions for other versions. Exact
Citadel 2.6.3 has no static `dataMap`; Forge reads this applicability annotation before ATL queues
or initializes the obsolete issue. An ordinary Mixin targeting `IssueManager` is not relied on:
ATL can load it during Mixin configuration preparation.

The source contract is `src/upstreamRuntime/startup-overrides-v1.json`; separate builder,
controls and `qualifyUpstreamStartupOverrides` retain originals/attempts and publish two support
files under `build/upstream-startup-overrides/`. Of 880 recursive classes, only this exact
annotation string's length/payload changes. Every method/field/Code attribute and all other
classes, resources, ZIP metadata and notices remain unchanged. Actual Forge annotation scanning
and Maven parser controls are source/metadata evidence, not native startup acceptance. Root
independently verifies the full receipt without a variable provider-field normalization, seals
`startup-overrides/` evidence and uses `.worklane/startup-overrides/current.path`.

## Epic Fight and native fuel integration: acceptance pending

The local Build 466 dedicated-server attempt reached readiness and recorded all three
replacement-Mixin applications. It did **not** establish clean Dist acceptance, client join,
fuel consumption or no-double-emission behavior. The retained failure logs and v1 receipts stay
intact. Internal checksum pins are reviewable identities, not an external approval barrier:
corrections use explicit successors rather than bypassing validators or changing history.

Compat now has a separate required, physical-server-only startup ABI config for Epic Fight.
Its supported Mixin `preApply` hook removes only the unique public-static client-only
`getLocalPlayerPatch(LocalPlayer):LocalPlayerPatch` member before automatic Rhino enumeration.
Exact raw/mapped resources and every member are checked before mutation; the other 14 methods,
four fields, erased Optional getter, original bindings and physical-client behavior are
preserved. Fail-closed checks run in application, not Mixin's potentially log-only preparation
exception path. Static controls do not establish actual cold server binding success.

Four additive nonshipping native tests cover real Hearth consumption, inherited Boiler
consumption, and Little Logistics locomotive/tug inventory consumption. They compare completed
original AdPother dispatch and its actual integer-plus-fraction emission state with one native
reference; a genuine doubled native reference must be rejected. Empty, cold-only, manual,
continuing-burn and invalid-fuel operations retain their original behavior without combustion
emissions. No original operation is cancelled/replaced. The fixture uses only a separately
named local corrected SDK coordinate; raw originals and cached mappings are not overwritten.
The integrated fixture compiles, and the default owning profile passed all 85 required native
tests, including all four consumption/emission cases and their repeated/zero/doubled controls.
The first native attempt exposed a fixture-precondition mistake: native `onFuelChanged` recalculates
usage flags, so every repeated isolated operation must establish its own active-usage precondition.
That setup correction retained production hooks and all assertions; the failed evidence remains.
The remaining optional populations and full same-candidate Dist still require execution. This
source-native proof is not pack startup, client join or overall acceptance.

## Bounded native Forge API compatibility scope

The pack deliberately retains native Forge Forgified Fabric API and establishes a pack-owned,
version-pinned compatibility scope with Moonlight. This is **not** an upstream endorsement,
permission to load arbitrary Fabric/Connector providers, or a claim that every API/module/rendering
path is tested. Moonlight's current ID-only detector cannot distinguish this real native Forge
implementation from an unsupported provider. The correction must classify the exact provider at
its one callsite, not make global `ModList` presence lie, rename/remove the API, change the logger,
or exempt a failure from the audit.

Admission requires unambiguous JavaFML ownership, actual native entrypoint instances, exact
qualified root/nested identities and ABIs, and the exact Moonlight pair. Unknown, incomplete,
unreadable or mismatched providers retain the original diagnostic branch. Raw production and
explicitly qualified nonshipping mapped SDK roles must not be confused. Existing resource-only
corrections and all 3,144 production class bytes remain unchanged; SDK mappings are separate,
retained development inputs, never production overrides.

A separate `native-forge-api` source profile adds exactly two required tests (87 with the ordinary
85-case graph): natural server interaction through Forge to the real Fabric callback and Moonlight
map persistence, plus native pack/reload coexistence and actual generated-resource visibility.
All prior profiles and identities remain required. Native tests must reject duplicate dispatch,
verify nonmatching/cancellation behavior and preserve original operations; API existence, direct
callback invocation and INFO logs are not integration proof. The first owning native invocation passed all 87
required cases, including both additions and strict native logs. That is not canonical 46-provider
qualification or full-pack/client acceptance. Full same-artifact client startup/assets/join, relevant
map/resource observations, heartbeats, teardown and unchanged strict raw audits remain required.

## C2ME and Registrate companions

The server-only `server-config/config-overrides/c2me.toml` uses C2ME's supported module gate to set
`clientSideConfig.modifyMaxVDConfig.enabled=false`. A semantic contract requires all other settings
to match the client source; the client remains `"default"`. No world-generation/I/O module is disabled
by this change.

Fruits Delight's embedded Registrate **1.3.11** wins JarJar selection in the real pack. Compat admits
only reviewed raw/official-mapped class pairs for 1.3.3 and 1.3.11. The explicit native profile selects
1.3.11 and checks its exact mapped resources from the active loader; ordinary profiles retain 1.3.3.
Unknown, partial and mixed pairs reject. This is separate from the resource archive corrections.

## Publication boundary

**Do not publish these corrected binaries or generated local ZIPs based on this recipe/receipt.**
Relics, AdPother and Epic ParCool declare All Rights Reserved without a located grant. Other artifacts carry
notice/source obligations, including YACL's LGPL metadata. Existing license/notices are preserved,
but preservation and passing tests are not redistribution permission or complete compliance.
Only patch source/specifications are committed here; corrected third-party archives remain local
retained outputs. Any public redistribution needs an independently established applicable grant
and completed notice/source obligations. No upload is authorized by these contracts.
