package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Keep the standard-library disposal contract in the Minecraft-free Dev gate. */
@Tag("fast")
class WorkspaceMaintenanceTest {
    @Test
    fun disposableWorkspaceContracts() {
        val root = Path.of(System.getProperty("bc.repo.root")).toFile()
        val process = ProcessBuilder("python3", "-B", "scripts/test_workspace_maintenance.py")
            .directory(root).inheritIO().start()
        assertEquals(0, process.waitFor(), "workspace disposal contracts failed")
    }
}
