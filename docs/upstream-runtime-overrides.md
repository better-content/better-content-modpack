# Local upstream runtime corrections

This is the authority for the five **local-only** resource-corrected archive identities in
`config/better-content-runtime-overrides.json` and the separate two-artifact compatibility
contract in `config/better-content-interface-runtime-overrides.json`. It does not replace [testing](testing.md),
[source qualification](custom-mod-workspace.md), or [retention](policies/generated-data.md).

## Scope and original identity

The independently identified `bc-runtime.1` recipe lives in the owning Compat source repository,
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

## Qualification and assembly

Compat's `qualifyUpstreamRuntimeOverrides`, part of `verifyFast`, checks the current production
replacement/plugin bytes and runs all 17 pure/pinned-original controls, including two identical
builds. It retains originals and every attempt under `build/upstream-runtime-evidence/`; successful
support snapshots are under `build/upstream-runtime-overrides/`. Root declares the six support
artifacts in `gradle/active-custom-mods.json` and binds them to the owning source qualification.

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
applies old five then new two, with one normal Packwiz export and seven exact CF tuple exclusions.
The new receipt has no variable provider field or normalization. Both validators bind both exact
ZIPs. All startup/join/heartbeat/teardown and raw-log requirements remain; source/class controls
alone are not runtime acceptance. Fixture audit derivation additionally admits additive Mixin
DEBUG application records while retaining the INFO root, all failure severities and unchanged
packaged production logging. No suppression or audit exemption is added.

## Remaining native blockers and frozen-contract limits

The local Build 466 dedicated-server attempt reached readiness and recorded all three
replacement-Mixin applications. It did **not** establish clean Dist acceptance, client join,
fuel consumption or no-double-emission behavior. The retained native audit exposes another
obsolete AdPother selector: the kept Boiler transformer searches `tick` for a `setFuel(int)`
call no longer present in Cold Sweat 2.4. Its script has no config gate. The frozen v1 policy
binds the exact final AdPother archive, so removing a fourth registration is incompatible with
that policy even if all 3,144 class bytes remain invariant. Do not bypass the validator, add
shadow classes or weaken logging to make this candidate pass. A successor correction contract
requires explicit review; v1 receipts and failed archives remain retained.

Additional native findings include Epic Fight's automatic KubeJS binding reflecting a public
client-only method descriptor on a dedicated server, and an AllTheLeaks Citadel repair assuming
a removed static `dataMap`. Neither has been repaired or accepted by these recipes. Moonlight's
Fabric-API-ID diagnostic also fires for intentionally installed Forgified Fabric API; that log
alone does not establish a broken API, and this observation is not a log-policy exemption or
permission to remove the API. Actual consumption and multiplicity checks are still required
for the shared Hearth fuel path; application witnesses alone do not establish their results.

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
