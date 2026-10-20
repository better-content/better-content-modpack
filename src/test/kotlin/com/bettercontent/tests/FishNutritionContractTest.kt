package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Pinned source/data and inert handlers only; no native gameplay acceptance. */
@Tag("fast")
class FishNutritionContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test
    fun `regional catch windows and nutrition families match pinned native content`() {
        // Literal declarations let prepare_dev_inputs.py rehydrate a cold cache.
        val inputs = listOf(
            pinnedModJar("starcatcher-2.2.1-FORGE-1.20.1.jar"),
            pinnedModJar("diet-forge-2.1.1+1.20.1.jar"),
            pinnedModJar("fruitsdelight-1.1.3.jar"),
            pinnedModJar("FarmersDelight-1.20.1-1.3.1.jar"),
            pinnedModJar("ubesdelight-forge-1.20.1-0.4.11.jar"),
            pinnedModJar("BrewinAndChewin-1.20.1-3.2.1.jar"),
            pinnedModJar("kubejs-forge-2001.6.5-build.16.jar"),
        )
        inputs.forEach { assertTrue(Files.isRegularFile(it), "Missing declared native input $it") }
        val process = ProcessBuilder("python3", "-B", "scripts/test_fish_nutrition.py")
            .directory(root.toFile()).redirectErrorStream(true).start()
        val completed = process.waitFor(120, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly().waitFor()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(completed, "Fish source contracts exceeded 120 seconds: $output")
        assertEquals(0, process.exitValue(), output)
        println(output)
    }
}
