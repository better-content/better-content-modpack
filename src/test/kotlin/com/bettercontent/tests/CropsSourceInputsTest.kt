package com.bettercontent.tests

import com.bettercontent.tests.release.cropsSourceInputCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class CropsSourceInputsTest {
    @Test
    fun `only Crops gets explicit canonical input binding without changing owning arguments`() {
        val root = Path.of("/fixture/pack")
        val crops = Path.of("/fixture/mod_source/better-bumblezone-crops")
        val command = listOf(crops.resolve("gradlew").toString(), "--no-daemon", "clean", "verifyFull", "stageRuntimeJar")
        assertEquals(listOf("python3", "-B", "/fixture/pack/scripts/crops_source_inputs.py", "--root",
            "/fixture/pack", "--repository", crops.toString(), "--") + command,
            cropsSourceInputCommand(root, crops, command))
        assertEquals(command, cropsSourceInputCommand(root, Path.of("/fixture/mod_source/other"), command))
    }

    @Test
    fun `Crops adapter preserves registration provenance cold inputs and failed activities`() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        val work = Files.createDirectories(root.resolve("build/test-work/crops-source-inputs"))
        val log = Files.createTempDirectory(work, "controls-").resolve("controls.log")
        assertEquals(0, BoundedCommand.run(root,
            listOf("python3", "-B", root.resolve("scripts/test_crops_source_inputs.py").toString()),
            log, Duration.ofSeconds(30)))
        val output = Files.readString(log)
        assertTrue(output.contains("Ran 7 tests"), output)
        assertTrue(output.contains("OK"), output)
    }
}
