package com.bettercontent.tests

import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class BlightLocusPersistenceTest {
    private fun fixture(test: (Path) -> Unit) {
        val directory = Path.of("build", "blight-persistence-tests", UUID.randomUUID().toString())
        Files.createDirectories(directory)
        try { test(directory) } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }

    private fun gzip(path: Path, body: DataOutputStream.() -> Unit) {
        Files.createDirectories(path.parent)
        DataOutputStream(GZIPOutputStream(Files.newOutputStream(path))).use { output ->
            output.writeByte(10); output.writeUTF(""); output.body(); output.writeByte(0)
        }
    }

    private fun writeWorld(world: Path, seed: Long = BlightLocusPersistence.SEED, seedType: Int = 4,
        claims: List<Long> = listOf(BlightLocusPersistence.CLAIM), listKind: Int = 4, claimsType: Int = 9) {
        gzip(world.resolve("level.dat")) {
            writeByte(10); writeUTF("Data")
            writeByte(10); writeUTF("WorldGenSettings")
            writeByte(seedType); writeUTF("seed")
            if (seedType == 4) writeLong(seed) else writeInt(seed.toInt())
            writeByte(0); writeByte(0)
        }
        gzip(world.resolve(BlightLocusPersistence.CLAIM_FILE)) {
            writeByte(10); writeUTF("data")
            writeByte(claimsType); writeUTF("attempted_cells")
            if (claimsType == 9) writeByte(listKind)
            writeInt(claims.size)
            claims.forEach { if (listKind == 4) writeLong(it) else writeInt(it.toInt()) }
            writeByte(0)
        }
    }

    @Test fun acceptsActualTypedSeedAndClaimAndReportsDerivedProvenanceHonestly() = fixture { world ->
        writeWorld(world)
        val observed = BlightLocusPersistence.read(world)
        assertEquals(BlightLocusPersistence.SEED, observed.seed)
        assertEquals(setOf(BlightLocusPersistence.CLAIM), observed.claims)
        assertEquals(false, observed.evidence("closed")["native_event_cardinality_observed"])
        assertEquals("derived_from_unchanged_policy_not_captured_stack_locals", observed.evidence("closed")["site_basis"])
    }

    @Test fun additionalNaturalClaimsAreAllowedButEarlierKeysCannotBeLost() = fixture { world ->
        writeWorld(world, claims = listOf(BlightLocusPersistence.CLAIM, 7L))
        val first = BlightLocusPersistence.read(world)
        writeWorld(world, claims = listOf(BlightLocusPersistence.CLAIM, 7L, 8L))
        assertEquals(setOf(BlightLocusPersistence.CLAIM, 7L, 8L), BlightLocusPersistence.read(world, first.claims).claims)
        writeWorld(world, claims = listOf(BlightLocusPersistence.CLAIM, 8L))
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world, first.claims) }
    }

    @Test fun wrongNativeSeedOrNumericTypeCannotBeCoerced() = fixture { world ->
        writeWorld(world, seed = 12L)
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
        writeWorld(world, seedType = 3)
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
    }

    @Test fun missingLocusFileOrClaimAndDuplicateKeysFailRatherThanCreatingOrRepairing() = fixture { world ->
        writeWorld(world, claims = listOf(8L))
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
        writeWorld(world, claims = listOf(BlightLocusPersistence.CLAIM, BlightLocusPersistence.CLAIM))
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
        Files.delete(world.resolve(BlightLocusPersistence.CLAIM_FILE))
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
        assertFalse(Files.exists(world.resolve(BlightLocusPersistence.CLAIM_FILE)))
    }

    @Test fun listElementTypeAndNativeListTagMustBeExact() = fixture { world ->
        writeWorld(world, listKind = 3)
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
        writeWorld(world, claimsType = 12)
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
    }

    @Test fun malformedGzipOrDuplicateCompoundKeysAreRejected() = fixture { world ->
        writeWorld(world)
        Files.write(world.resolve("level.dat"), byteArrayOf(1, 2, 3))
        assertThrows(java.io.IOException::class.java) { BlightLocusPersistence.read(world) }
        gzip(world.resolve("level.dat")) {
            writeByte(10); writeUTF("Data"); writeByte(0)
            writeByte(10); writeUTF("Data"); writeByte(0)
        }
        assertThrows(IllegalArgumentException::class.java) { BlightLocusPersistence.read(world) }
    }

    @Test fun observationsAreReadOnlyAndPostCloseHookRetainsOriginalJournalAndAuditGates() = fixture { world ->
        writeWorld(world)
        val level = Files.readAllBytes(world.resolve("level.dat"))
        val claims = Files.readAllBytes(world.resolve(BlightLocusPersistence.CLAIM_FILE))
        BlightLocusPersistence.read(world)
        assertArrayEquals(level, Files.readAllBytes(world.resolve("level.dat")))
        assertArrayEquals(claims, Files.readAllBytes(world.resolve(BlightLocusPersistence.CLAIM_FILE)))
        val source = Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/SingleplayerRuntimeTest.kt"))
        assertTrue(source.contains("DedicatedServerFixture(evidence.run, seed = BlightLocusPersistence.SEED)"))
        assertTrue(source.indexOf("seed.stopGracefully()") < source.indexOf("BlightLocusPersistence.read(sourceWorld)"))
        assertTrue(source.indexOf("first.close()", source.indexOf("val savedTime")) < source.indexOf("BlightLocusPersistence.read(firstSave, seedLoci.claims)"))
        assertTrue(source.contains("reopened.waitForWorldProbe(\"BC_DEBUG_WORLD_EXITED\")"))
        assertTrue(source.indexOf("reopened.waitForWorldProbe(\"BC_DEBUG_WORLD_EXITED\")") < source.indexOf("reopened.close()"))
        assertTrue(source.indexOf("reopened.close()") < source.indexOf("val reopenedLoci = BlightLocusPersistence.read("))
        for (required in listOf("first.waitJournalCheckpoint(\"save\")", "reopened.waitJournalCheckpoint(\"verify\")",
            "check(reopenedMarker == savedMarker)", "check(reopenedTime >= savedTime)", "LogPolicy.requireClean", "reopened.assertHashes()"))
            assertTrue(source.contains(required), required)
        val helper = Files.readString(Path.of("src/test/kotlin/com/bettercontent/tests/BlightLocusPersistence.kt"))
        for (forbidden in listOf("Files.write", "Files.copy", "Files.create", "setBlock(", "setChunkForced(", "newOutputStream"))
            assertFalse(helper.contains(forbidden), forbidden)
    }
}
