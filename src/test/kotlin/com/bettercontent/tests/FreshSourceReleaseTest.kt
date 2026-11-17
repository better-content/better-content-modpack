package com.bettercontent.tests

import com.bettercontent.tests.release.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Tag("fast")
class FreshSourceReleaseTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()

    private fun population(): Pair<ActiveModManifest, FreshSourcePlan> {
        val mods = (0..43).map { ActiveMod("fixture-$it", "fixture_$it", "fixture-$it.jar", listOf("verifyFull", "stageRuntimeJar")) } + listOf(
            ActiveMod("better-world-management", "better_world_management", "better-world-management-0.1.0.jar", listOf("verifyFull", "stageRuntimeJar")),
            ActiveMod("better-spicy-farmers", "better_spicy_farmers", "spice.jar", listOf("verifyFull", "stageRuntimeJar")),
            ActiveMod("better-drinking-water", "better_drinking_water", "water.jar", listOf("verifyFull", "stageRuntimeJar"),
                listOf("better-spicy-farmers")),
            ActiveMod("better-industrial-heat", "better_industrial_heat", "heat.jar", listOf("verifyFull", "stageRuntimeJar"),
                listOf("better-drinking-water", "better-spicy-farmers")),
            ActiveMod("better-survival-physiology", "better_survival_physiology", "phys.jar", listOf("verifyFull", "stageRuntimeJar"),
                listOf("better-drinking-water", "better-spicy-farmers")))
        val sources = mods.map {
            QualifiedFreshSource(it.repository, "head", "a".repeat(64), "d".repeat(64), "b".repeat(64), FreshInputSnapshot("qualification.json", "c".repeat(64)))
        }
        return ActiveModManifest("bc.active_custom_mods.v1", mods) to FreshSourcePlan("bc.fresh_custom_sources.v1", sources, emptyMap(), "2026-01-01T00:00:00Z", "2026-01-01T00:02:00Z",
            FreshInputSnapshot("/qualified/better-world-management-server.sh", "a".repeat(64)))
    }

    @Test fun dependencyOrderRejectsMissingOrCyclicProvidersAndOrdersWaterSpiceBeforePhys() {
        val (manifest, _) = population()
        val ordered = freshDependencyOrder(manifest.mods).map { it.repository }
        assertFalse("better-cooking" in ordered)
        assertDoesNotThrow { validateFreshSourcePopulation(manifest, population().second) }
        assertThrows(IllegalArgumentException::class.java) {
            val old = ActiveMod("better-cooking", "better_cooking", "better-cooking-0.1.0.jar", emptyList())
            val (_, plan) = population()
            validateFreshSourcePopulation(manifest.copy(mods = manifest.mods + old), plan.copy(sources = plan.sources +
                QualifiedFreshSource(old.repository, "head", "a".repeat(64), "d".repeat(64), "b".repeat(64),
                    FreshInputSnapshot("qualification.json", "c".repeat(64)))))
        }
        assertTrue(ordered.indexOf("better-spicy-farmers") < ordered.indexOf("better-drinking-water"))
        assertTrue(ordered.indexOf("better-drinking-water") < ordered.indexOf("better-industrial-heat"))
        assertTrue(ordered.indexOf("better-spicy-farmers") < ordered.indexOf("better-industrial-heat"))
        assertTrue(ordered.indexOf("better-drinking-water") < ordered.indexOf("better-survival-physiology"))
        assertTrue(ordered.indexOf("better-spicy-farmers") < ordered.indexOf("better-survival-physiology"))
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourcePopulation(manifest.copy(mods = manifest.mods.map {
            if (it.repository == "better-drinking-water") it.copy(dependsOn = emptyList()) else it
        }), population().second) }
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourcePopulation(manifest.copy(mods = manifest.mods.map {
            if (it.repository == "better-industrial-heat") it.copy(dependsOn = listOf("better-drinking-water")) else it
        }), population().second) }
        assertThrows(IllegalArgumentException::class.java) { freshDependencyOrder(listOf(ActiveMod("a", "a", "a.jar", emptyList(), listOf("missing")))) }
        assertThrows(IllegalArgumentException::class.java) { freshDependencyOrder(listOf(
            ActiveMod("a", "a", "a.jar", emptyList(), listOf("b")), ActiveMod("b", "b", "b.jar", emptyList(), listOf("a")))) }
        val (_, plan) = population()
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourcePopulation(manifest.copy(mods = manifest.mods.map {
            if (it.repository == "better-survival-physiology") it.copy(dependsOn = emptyList()) else it
        }), plan) }
    }

    @Test fun freshTypedPropsOnlyAdmitQualifiedExternalPathsAndNeverOldCookingJar() {
        val (_, plan) = population()
        val source = plan.sources.single { it.repository == "better-drinking-water" }
        val kc = Files.writeString(root.resolve("kc.jar"), "source-only fixture")
        val classes = Files.createDirectories(root.resolve("spice-classes"))
        val bound = source.copy(
            externalInputs = listOf(FreshInputSnapshot(kc.toString(), Hashes.sha256(kc)),
                FreshInputSnapshot(classes.toString(), inputSnapshotHash(classes, "tree"), "tree")),
            gradleProperties = mapOf("kaleidoscopeJar" to kc.toString(), "typedSpiceCompileClasses" to classes.toString()))
        assertEquals(listOf("-PkaleidoscopeJar=$kc", "-PtypedSpiceCompileClasses=$classes"),
            freshGradleProperties(bound, root.resolve("staging")))
        assertThrows(IllegalArgumentException::class.java) {
            freshGradleProperties(bound.copy(gradleProperties = bound.gradleProperties +
                ("typedSpiceCompileClasses" to root.resolve("unqualified-old-classes").toString())), root.resolve("staging"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            freshGradleProperties(bound.copy(gradleProperties = bound.gradleProperties +
                ("cookingJar" to root.resolve("mods/better-cooking-0.1.0.jar").toString())), root.resolve("staging"))
        }
    }

    @Test fun freshSourceBindingsRejectMissingPinnedSpiceAndWaterInputs() {
        val (manifest, plan) = population()
        val mods = manifest.mods.associateBy { it.repository }
        val sources = plan.sources.map { source ->
            val dependencies = mods.getValue(source.repository).dependsOn.associate { dependency ->
                mods.getValue(dependency).artifact to plan.sources.single { it.repository == dependency }.rawSha256
            }
            source.copy(providerSha256 = dependencies,
                gradleProperties = if (source.repository == "better-survival-physiology")
                    mapOf("spicyFarmersProviderJar" to "@staging/spice.jar") else emptyMap())
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateFreshSourceBindings(manifest, plan.copy(sources = sources), root)
        }
        assertTrue(error.message!!.contains("kaleidoscopeJar"), "missing qualified KC/typed providers must fail before staging")
    }

    @Test fun retiredCookingCannotReenterEitherFutureCandidateArchive() {
        val client = root.resolve("client.zip")
        val server = root.resolve("server.zip")
        fun archive(path: Path, cooking: Boolean) {
            java.util.zip.ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry(if (cooking) "pack/mods/better-cooking-0.1.0.jar" else "pack/pack.toml"))
                zip.write("fixture".toByteArray())
                zip.closeEntry()
            }
        }
        archive(client, false); archive(server, false)
        fun pair() = CandidatePair(root, client, server, Hashes.sha256(client), Hashes.sha256(server))
        assertDoesNotThrow { requireRetiredCookingAbsent(pair()) }
        archive(server, true)
        assertThrows(IllegalArgumentException::class.java) { requireRetiredCookingAbsent(pair()) }
        archive(server, false); archive(client, true)
        assertThrows(IllegalArgumentException::class.java) { requireRetiredCookingAbsent(pair()) }
    }

    @Test fun thirdPartyPinsIncludeRuntimeVersionsButPermitDistributionVersionAndIndexRefresh() {
        val pack = root.resolve("pack.toml")
        Files.writeString(pack, "version=\"Build 1\"\n[index]\nhash=\"old\"\n[versions]\nforge=\"47.4.22\"\nminecraft=\"1.20.1\"\n")
        val mod = Files.writeString(Files.createDirectories(root.resolve("mods")).resolve("external.pw.toml"), "file-id=123")
        val pins = thirdPartyPins(root)
        assertTrue("pack.toml#versions" in pins && "mods/external.pw.toml" in pins)
        Files.writeString(pack, Files.readString(pack).replace("Build 1", "Build 2").replace("old", "new"))
        assertEquals(pins, thirdPartyPins(root))
        Files.writeString(pack, Files.readString(pack).replace("47.4.22", "47.4.23"))
        assertNotEquals(pins, thirdPartyPins(root))
        Files.writeString(mod, "file-id=124")
        assertNotEquals(pins["mods/external.pw.toml"], thirdPartyPins(root)["mods/external.pw.toml"])
    }

    @Test fun typedClassTreesIncludeEveryFileAndRejectSymlinkSubstitutions() {
        val classes = Files.createDirectories(root.resolve("classes"))
        val file = Files.writeString(classes.resolve("API.class"), "current ABI")
        val before = inputSnapshotHash(classes, "tree")
        Files.writeString(file, "other ABI")
        assertNotEquals(before, inputSnapshotHash(classes, "tree"))
        Files.createSymbolicLink(classes.resolve("hidden-directory"), root)
        assertThrows(IllegalArgumentException::class.java) { inputSnapshotHash(classes, "tree") }
    }

    private fun teleFixture(): Triple<Path, CandidatePair, MutableList<MutableMap<String, Any>>> {
        val client = Files.writeString(root.resolve("client.zip"), "client")
        val server = Files.writeString(root.resolve("server.zip"), "server")
        val pair = CandidatePair(root, client, server, Hashes.sha256(client), Hashes.sha256(server))
        val dimensions = listOf("minecraft:overworld", "minecraft:the_nether")
        val events = mutableListOf<MutableMap<String, Any>>()
        var now = Instant.parse("2026-01-01T00:00:00Z")
        var lastHeartbeat = now
        var dimensionTarget = "initial"
        var marker = 0
        val landingCount = mutableMapOf<String, Int>()
        fun event(type: String, fields: Map<String, Any> = emptyMap()) {
            events += linkedMapOf<String, Any>("time" to now.toString(), "type" to type).apply { putAll(fields) }
        }
        fun heartbeat() {
            event("heartbeat", mapOf("marker" to "hb-${marker++}", "server_alive" to true, "client_received" to true, "verified_dimension" to dimensionTarget))
            lastHeartbeat = now
        }
        fun advance(seconds: Int) { repeat(seconds) {
            now = now.plusSeconds(1)
            if (java.time.Duration.between(lastHeartbeat, now).seconds >= 12) heartbeat()
        } }
        fun landing(dimension: String) {
            val ordinal = landingCount.getOrDefault(dimension, 0)
            landingCount[dimension] = ordinal + 1
            val x = ordinal * 10016.0 + 0.5
            event("landing_verified", mapOf("dimension" to dimension, "x" to x, "z" to 8.0,
                "chunk_x" to ordinal * 626, "chunk_z" to 0, "novel_chunk" to true,
                "route" to "remote_ungenerated_chunk", "distance_from_previous" to if (ordinal == 0) 0.0 else 10016.0))
        }
        fun receipt(identity: Map<String, Any>) {
            event("client_dimension_receipt", identity + mapOf("marker" to "arrival-${marker++}",
                "server_conditional" to true, "server_position_conditional" to true, "fresh_client_log_offset" to 123))
        }
        fun samples(type: String, identity: Map<String, Any>) {
            (0..30).forEach { second ->
                event(type, identity + mapOf("elapsed_seconds" to second.toDouble(), "tps" to 20.0))
                if (second < 30) advance(1)
            }
        }
        fun hop(dimension: String, circuit: Int) {
            dimensionTarget = dimension
            val identity = mapOf<String, Any>("dimension" to dimension, "circuit" to circuit)
            event("hop_started", identity); advance(1); landing(dimension); receipt(identity)
            event("arrival_verified", identity + ("hop_seconds" to 1.0))
            samples("tps_sample", identity)
            event("tps_stable", identity + ("recovery_seconds" to 30.0))
        }
        fun relocate(dimension: String, circuit: Int, ordinal: Int) {
            val identity = mapOf<String, Any>("dimension" to dimension, "circuit" to circuit, "relocation" to ordinal)
            event("relocation_started", identity); advance(1); landing(dimension); receipt(identity)
            event("relocation_arrival_verified", identity + ("hop_seconds" to 1.0))
            samples("relocation_tps_sample", identity)
            event("relocation_tps_stable", identity + ("recovery_seconds" to 30.0))
        }
        event("client_joined"); heartbeat()
        event("dimension_roster", mapOf("dimensions" to dimensions,
            "registered_dimensions" to dimensions + "minecraft:the_end", "excluded_dimensions" to listOf("minecraft:the_end")))
        dimensions.forEach { dimension ->
            hop(dimension, 0)
            val dwellStart = now
            event("dwell_started", mapOf("dimension" to dimension, "required_fresh_relocations" to 10))
            (1..10).forEach { ordinal -> advance(30); relocate(dimension, 0, ordinal) }
            event("dwell_completed", mapOf("dimension" to dimension, "actual_seconds" to java.time.Duration.between(dwellStart, now).seconds.toDouble(),
                "fresh_relocations" to 10))
        }
        (1..3).forEach { circuit ->
            event("circuit_started", mapOf("circuit" to circuit, "route" to dimensions))
            dimensions.forEach { dimension -> hop(dimension, circuit); relocate(dimension, circuit, 1) }
            event("circuit_completed", mapOf("circuit" to circuit))
        }
        event("tele_soak_complete", mapOf("dimension_count" to dimensions.size, "circuits" to 3))
        event("client_disconnected"); event("server_saved_and_stopped"); event("validated")
        event("process_cleanup", mapOf("complete" to true, "surviving_pids" to emptyList<Long>()))
        event("session_finished", mapOf("success" to true))
        val path = root.resolve("run.json")
        writeTele(path, pair, events)
        return Triple(path, pair, events)
    }
    private fun writeTele(path: Path, pair: CandidatePair, events: List<Map<String, Any>>) {
        val raw = path.resolveSibling("events.jsonl")
        Files.writeString(raw, events.joinToString("\n", postfix = "\n") { mapper.writeValueAsString(it) })
        val dims = listOf("minecraft:overworld", "minecraft:the_nether")
        mapper.writeValue(path.toFile(), mapOf("run_id" to "fresh", "tier" to "tele-soak", "status" to "passed", "candidates" to pair.document(),
            "scenario_completed" to true, "dwell_seconds_per_dimension" to 300, "circuits" to 3, "registered_dimensions" to dims,
            "dimension_results" to dims.map { mapOf("dimension" to it, "dwell_seconds" to 300.0, "fresh_relocations_verified" to true,
                "completed_circuits" to listOf(1, 2, 3), "tps_verified" to true) },
            "events_sha256" to Hashes.sha256(raw)))
    }

    @Test fun completeTeleReceiptRequiresMatchingPairAndActualLinkedSamples() {
        val (path, pair, _) = teleFixture()
        assertDoesNotThrow { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "old", pair, Instant.EPOCH) }
        Files.writeString(pair.client, "changed")
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun rawRegisteredRosterAndExactBlacklistAreMandatory() {
        val (path, pair, events) = teleFixture()
        assertDoesNotThrow { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        val roster = events.single { it["type"] == "dimension_roster" }
        val registered = roster.getValue("registered_dimensions")
        val excluded = roster.getValue("excluded_dimensions")
        roster.remove("registered_dimensions")
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        roster["registered_dimensions"] = registered
        roster["excluded_dimensions"] = emptyList<String>()
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        roster["excluded_dimensions"] = excluded
        roster["registered_dimensions"] = (registered as List<*>) + "test:omitted_live_dimension"
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        roster["registered_dimensions"] = registered
        writeTele(path, pair, events)
        assertDoesNotThrow { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun booleanTpsSuccessCannotHideShortOrSlowRawRecovery() {
        val (path, pair, events) = teleFixture()
        events.first { it["type"] == "tps_sample" }["tps"] = 18.0
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun rawSampleGapCannotCountAsContinuousThirtySeconds() {
        val (path, pair, events) = teleFixture()
        events.filter { it["dimension"] == "minecraft:overworld" && it["circuit"] == 0 }.forEach {
            if (it["type"] == "tps_sample" && it["elapsed_seconds"] as Double >= 5.0) it["elapsed_seconds"] = it["elapsed_seconds"] as Double + 3.0
            if (it["type"] == "tps_stable") it["recovery_seconds"] = 33.0
        }
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun missingWrongOrLateActualClientDimensionReceiptCannotPass() {
        val (path, pair, events) = teleFixture()
        val receipt = events.first { it["type"] == "client_dimension_receipt" }
        receipt["dimension"] = "minecraft:wrong"
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        receipt["dimension"] = "minecraft:overworld"
        events.remove(receipt)
        events.add(events.indexOfFirst { it["type"] == "arrival_verified" } + 1, receipt)
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun lateOrWrongDimensionHeartbeatsCannotPass() {
        val (path, pair, events) = teleFixture()
        val heartbeat = events.first { it["type"] == "heartbeat" && it["verified_dimension"] == "minecraft:overworld" }
        heartbeat["verified_dimension"] = "minecraft:the_nether"
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        heartbeat["verified_dimension"] = "minecraft:overworld"
        events.remove(heartbeat) // A 24s gap is within the documented 30s stable-proof budget.
        writeTele(path, pair, events)
        assertDoesNotThrow { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        val nextHeartbeat = events.first { it["type"] == "heartbeat" && it["verified_dimension"] == "minecraft:overworld" }
        events.remove(nextHeartbeat) // Two missing 12s heartbeats leave a >30s gap.
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun completedFlagsCannotHideAnOutOfOrderCircuit() {
        val (path, pair, events) = teleFixture()
        val completed = events.first { it["type"] == "circuit_completed" }
        events.remove(completed); events.add(0, completed)
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }

    @Test fun missingDimensionCircuitCleanupOrChangedRawEvidenceCannotPass() {
        val (path, pair, events) = teleFixture()
        val incomplete = events.filterNot { it["type"] == "arrival_verified" && it["dimension"] == "minecraft:the_nether" && it["circuit"] == 3 }
        writeTele(path, pair, incomplete)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        events.first { it["type"] == "process_cleanup" }["complete"] = false
        writeTele(path, pair, events)
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        writeTele(path, pair, events)
        Files.writeString(path.resolveSibling("events.jsonl"), "unlinked replacement")
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
    }
}
