package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class DistantHorizonsTransformSourceTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))

    @Test
    fun `pinned DH source geometry audit stays tied to the active artifact and compatibility policy`() {
        val pin = Files.readString(root.resolve("mods/distant-horizons.pw.toml"))
        assertTrue(pin.contains("DistantHorizons-2.4.5-b-1.20.1-fabric-forge.jar"))
        assertTrue(pin.contains("ce3814dd5971edda4d04c3a42ef0df5c6cf8e10d"))
        assertTrue(pin.contains("file-id = 7375280"))

        val audit = Files.readString(root.resolve("docs/distant_horizons_render_geometry.md"))
        listOf(
            "LodRenderer.setShaderProgramMvmOffset",
            "RenderParams.exactCameraPosition",
            "RenderUtil.createLodModelViewMatrix",
            "RenderUtil.createLodProjectionMatrix",
            "m22` and `m23",
            "m00` and `m11",
            "runtime visual acceptance",
            "incorrect translation, rotation, FOV, or depth transform"
        ).forEach { assertTrue(audit.contains(it), "audit is missing: $it") }

        val config = Files.readString(root.resolve("config/DistantHorizons.toml"))
        assertTrue(Regex("transparency\\s*=\\s*\"DISABLED\"").containsMatchIn(config))
        val performance = Files.readString(root.resolve("docs/performance_and_mods.md"))
        assertTrue(performance.contains("Distant Horizons remains enabled during compatibility validation."))
        assertTrue(performance.contains("LOD transparency is disabled to avoid incorrect Distant Horizons/Oculus/shader depth"))
    }
}
