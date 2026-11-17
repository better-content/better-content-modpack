package com.bettercontent.playtestsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Pure opt-in validation only; does not launch Minecraft or claim a discovered GameTest. */
final class RepositoryNativeReporterTest {
    @TempDir Path root;
    private final String report = "bc.playtest.repositoryReport";
    private final String run = "bc.playtest.repositoryRunId";
    private final String repository = "bc.playtest.repository";

    @AfterEach void clear() {
        System.clearProperty(report); System.clearProperty(run); System.clearProperty(repository);
    }
    @Test void inactiveWithoutExplicitOptIn() {
        System.clearProperty(report);
        assertDoesNotThrow(RepositoryNativeReporter::install);
    }
    @Test void optInRejectsUnboundRunOrRepositoryBeforeRegistration() {
        System.setProperty(report, root.toString());
        assertThrows(IllegalArgumentException.class, RepositoryNativeReporter::install);
        System.setProperty(run, "run-1");
        assertThrows(IllegalArgumentException.class, RepositoryNativeReporter::install);
        System.setProperty(repository, "../escaped");
        assertThrows(IllegalArgumentException.class, RepositoryNativeReporter::install);
    }
    @Test void optInRejectsAbsentOrLinkedReportDirectory() throws Exception {
        System.setProperty(report, root.resolve("missing").toString());
        System.setProperty(run, "run-1"); System.setProperty(repository, "better-spawns");
        assertThrows(IllegalArgumentException.class, RepositoryNativeReporter::install);
        Path alias = root.resolve("alias"); Files.createSymbolicLink(alias, root);
        System.setProperty(report, alias.toString());
        assertThrows(IllegalArgumentException.class, RepositoryNativeReporter::install);
    }
}
