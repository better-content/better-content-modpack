# Local upstream runtime resource corrections

This is the authority for the five **local-only** corrected runtime archive identities in
`config/better-content-runtime-overrides.json`. It does not replace [testing](testing.md),
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
Relics and AdPother declare All Rights Reserved without a located grant. Other artifacts carry
notice/source obligations, including YACL's LGPL metadata. Existing license/notices are preserved,
but preservation and passing tests are not redistribution permission or complete compliance.
Only patch source/specifications are committed here; corrected third-party archives remain local
retained outputs. Any public redistribution needs an independently established applicable grant
and completed notice/source obligations. No upload is authorized by these contracts.
