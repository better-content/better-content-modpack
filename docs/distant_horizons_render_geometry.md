# Distant Horizons 2.4.5 render geometry audit

This living integration document records the source geometry boundary for the pinned Forge
1.20.1 artifact, not a claim of current runtime visual acceptance:

- File: `DistantHorizons-2.4.5-b-1.20.1-fabric-forge.jar`
- CurseForge project/file: `508933/7375280`
- Packwiz SHA-1: `ce3814dd5971edda4d04c3a42ef0df5c6cf8e10d`
- Authority: current `mods/distant-horizons.pw.toml` pin and upstream artifact source.
  Historical fixture copies are disposable under [generated-data.md](policies/generated-data.md).

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

`config/DistantHorizons.toml` keeps LOD transparency `DISABLED`; the pack performance guide
documents this as the compatibility setting for DH/Oculus/shader depth composition. DH
remains enabled. This setting is independent of the transform finding above.
