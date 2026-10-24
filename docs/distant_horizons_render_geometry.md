# Distant Horizons 2.4.5 render geometry audit

This living integration document records the source geometry boundary for the pinned Forge
1.20.1 artifact, not a claim of current runtime visual acceptance:

- File: `DistantHorizons-2.4.5-b-1.20.1-fabric-forge.jar`
- CurseForge project/file: `508933/7375280`
- Packwiz SHA-1: `ce3814dd5971edda4d04c3a42ef0df5c6cf8e10d`
- Authority: current `mods/distant-horizons.pw.toml` pin and upstream artifact source.

## Transform path

`LodRenderer.setShaderProgramMvmOffset` computes the LOD origin relative to
`RenderParams.exactCameraPosition` on all three axes and supplies that camera-relative
position through `IDhApiShaderProgram.setModelOffsetPos` (and the corresponding event).
The standard vertex shader adds `uModelOffset` to the local vertex position, then applies
`uCombinedMatrix`. `DhTerrainShaderProgram.fillUniformData` forms that combined matrix as
projection multiplied by model-view. `RenderUtil.createLodModelViewMatrix` returns its
matrix copy without changing camera rotation or translation.

`RenderUtil.createLodProjectionMatrix` copies the active projection and changes only the
depth clip-plane terms through `Mat4f.setClipPlanes` (`m22` and `m23`). The horizontal and
vertical projection scale terms (`m00` and `m11`), which carry aspect/FOV, are retained.
The DH fade path inverts projection-times-model-view to reconstruct view-space positions
from depth; it uses the same matrix composition as terrain rendering.

## Source conclusion and boundary

The inspected source shows consistent camera-relative translation, preserved model-view
rotation, preserved FOV/aspect scale, and matching terrain/fade matrix composition. No
incorrect translation, rotation, FOV, or depth transform is evidenced in this exact artifact,
so this source audit records the existing geometry rather than introducing a speculative
transform patch. This is not a runtime visual acceptance: camera movement, FOV changes,
Oculus/shader combinations, terrain seams, and depth/fade appearance still require the
separate candidate/runtime visual review.

## Oculus / Complementary opaque-depth boundary

`config/DistantHorizons.toml` keeps LOD transparency `DISABLED`; DH remains enabled. This
setting is independent of the geometry transforms above, but requires an opaque-depth correction
for Oculus `1.8.0` with Complementary Reimagined `r5.8.1`:

- DH skips the transparent LOD pass when transparency is disabled.
- Oculus refreshes its separate opaque-depth copy only at the transparent-pass event. Its
  `dhDepthTex1` sampler otherwise exposes an unpopulated or stale copy in this mode.
- Complementary uses `dhDepthTex1` to reconstruct DH positions for TAA history reprojection;
  incorrect depth can therefore misalign history during camera translation/view bobbing.

The [Better Compat Fixes source](../../mod_source/better-compat-fixes/README.md) supplies a
client-only, exact-version-gated correction: disabled transparency uses the connected live DH
depth, which is already opaque-only. Enabled transparency and unavailable/cleared framebuffer
states preserve Oculus' native opaque-copy result. No camera transform, TAA weighting or
transparency setting is changed. Source changes do not update the pack's bundled JAR; deployment
and visual acceptance remain separate from this implemented source contract.

Pinned upstream authorities:
[DH core `eb231793`, LodRenderer](https://gitlab.com/jeseibel/distant-horizons-core/-/blob/eb2317934f313494caf8e11f7b2b8dff2e054e82/core/src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java),
[Oculus `b3b27813`, LodRendererEvents](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/compat/dh/LodRendererEvents.java), and
[Complementary `08e1c2ad`, TAA](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/08e1c2ada5eaf2fc36f08516c316b3d3c3677d8e/shaders/lib/antialiasing/taa.glsl).
These sources establish the depth contract, not a verified runtime cause for every blur/bobbing
symptom. Same-artifact in-game verification remains pending.
