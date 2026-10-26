package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

@Tag("fast")
class DistantHorizonsTransformSourceTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test
    fun `pinned DH source geometry audit stays tied to the active artifact and compatibility policy`() {
        val pin = Files.readString(root.resolve("mods/distant-horizons.pw.toml"))
        assertTrue(pin.contains("DistantHorizons-2.4.5-b-1.20.1-fabric-forge.jar"))
        assertTrue(pin.contains("ce3814dd5971edda4d04c3a42ef0df5c6cf8e10d"))
        assertTrue(pin.contains("file-id = 7375280"))

        // Inspect the pinned implementation; visual geometry still requires runtime acceptance.
        val archive = pinnedModJar("DistantHorizons-2.4.5-b-1.20.1-fabric-forge.jar")
        ZipFile(archive.toFile()).use { jar ->
            mapOf(
                "render/renderer/LodRenderer" to listOf("setShaderProgramMvmOffset"),
                "render/renderer/RenderParams" to listOf("exactCameraPosition"),
                "util/RenderUtil" to listOf("createLodModelViewMatrix", "createLodProjectionMatrix"),
            ).forEach { (owner, symbols) ->
                val entry = checkNotNull(jar.getEntry("com/seibel/distanthorizons/core/$owner.class"))
                val constants = jar.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                symbols.forEach { assertTrue(constants.contains(it), "Pinned DH lost $owner#$it") }
            }
        }

        val config = Files.readString(root.resolve("config/DistantHorizons.toml"))
        assertTrue(Regex("transparency\\s*=\\s*\"DISABLED\"").containsMatchIn(config))
        assertTrue(Files.isRegularFile(root.resolve("mods/distant-horizons.pw.toml")),
            "DH must remain in the active pack during compatibility validation")
    }
}
