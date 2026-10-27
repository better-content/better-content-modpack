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
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Tag("fast")
class FreshSourceReleaseTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()

    @Test fun standingApprovalNeedsNoPerRunEnvironmentAndCannotBeOverriddenByPlan() {
        assertEquals("standing-full-pack-approval-2026-10-27", requireFreshRuntimeAuthorization(emptyMap()))
        assertEquals("standing-full-pack-approval-2026-10-27", requireFreshRuntimeAuthorization(mapOf(
            "BC_PACK_EULA_ACCEPTED" to "false", "BC_PACK_EULA_AUTHORIZATION" to "unrelated reference",
            "plan_eula_accepted" to "false")))
    }

    private fun population(): Pair<ActiveModManifest, FreshSourcePlan> {
        val mods = (0..44).map { ActiveMod("fixture-$it", "fixture_$it", "fixture-$it.jar", listOf("verifyFull", "stageRuntimeJar")) } + listOf(
            ActiveMod("better-world-management", "better_world_management", "better-world-management-0.1.0.jar", listOf("verifyFull", "stageRuntimeJar")),
            ActiveMod("better-spicy-farmers", "better_spicy_farmers", "spice.jar", listOf("verifyFull", "stageRuntimeJar")),
            ActiveMod("better-drinking-water", "better_drinking_water", "water.jar", listOf("verifyFull", "stageRuntimeJar")),
            ActiveMod("better-survival-physiology", "better_survival_physiology", "phys.jar", listOf("verifyFull", "stageRuntimeJar"),
                listOf("better-drinking-water", "better-spicy-farmers")))
        val sources = mods.filterNot { it.repository == "better-world-management" }.map {
            QualifiedFreshSource(it.repository, "head", "a".repeat(64), "d".repeat(64), "b".repeat(64), FreshInputSnapshot("qualification.json", "c".repeat(64)))
        }
        return ActiveModManifest("bc.active_custom_mods.v1", mods) to FreshSourcePlan("bc.fresh_custom_sources.v1", sources, emptyMap(), "2026-01-01T00:00:00Z", "2026-01-01T00:02:00Z")
    }

    @Test fun exactly48FreshPlusTheSingleFrozenWlmExceptionAreRequired() {
        val (manifest, plan) = population()
        assertDoesNotThrow { validateFreshSourcePopulation(manifest, plan) }
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourcePopulation(manifest, plan.copy(sources = plan.sources.drop(1))) }
        assertThrows(IllegalArgumentException::class.java) {
            validateFreshSourcePopulation(manifest, plan.copy(sources = plan.sources.drop(1) + plan.sources.last()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateFreshSourcePopulation(manifest.copy(mods = manifest.mods.map {
                if (it.repository == "better-world-management") it.copy(artifact = "new-wlm.jar") else it
            }), plan)
        }
    }

    @Test fun dependencyOrderRejectsMissingOrCyclicProvidersAndOrdersWaterSpiceBeforePhys() {
        val (manifest, _) = population()
        val ordered = freshDependencyOrder(manifest.mods).map { it.repository }
        assertTrue(ordered.indexOf("better-drinking-water") < ordered.indexOf("better-survival-physiology"))
        assertTrue(ordered.indexOf("better-spicy-farmers") < ordered.indexOf("better-survival-physiology"))
        assertThrows(IllegalArgumentException::class.java) { freshDependencyOrder(listOf(ActiveMod("a", "a", "a.jar", emptyList(), listOf("missing")))) }
        assertThrows(IllegalArgumentException::class.java) { freshDependencyOrder(listOf(
            ActiveMod("a", "a", "a.jar", emptyList(), listOf("b")), ActiveMod("b", "b", "b.jar", emptyList(), listOf("a")))) }
        val (_, plan) = population()
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourcePopulation(manifest.copy(mods = manifest.mods.map {
            if (it.repository == "better-survival-physiology") it.copy(dependsOn = emptyList()) else it
        }), plan) }
    }

    @Test fun typedProviderAndClassTreeBindingsAreMandatoryBeforeAnyStaging() {
        val (manifest, plan) = population()
        val classes = root.resolve("typed-water-classes").toString()
        val corrected = plan.copy(sources = plan.sources.map { source ->
            if (source.repository != "better-survival-physiology") source else source.copy(
                providerSha256 = mapOf("water.jar" to "b".repeat(64), "spice.jar" to "b".repeat(64)),
                externalInputs = listOf(FreshInputSnapshot(classes, "a".repeat(64), "tree")),
                gradleProperties = mapOf("spicyFarmersProviderJar" to "@staging/spice.jar", "typedWaterCompileClasses" to classes))
        })
        assertDoesNotThrow { validateFreshSourceBindings(manifest, corrected) }
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourceBindings(manifest, plan) }
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourceBindings(manifest, corrected.copy(sources = corrected.sources.map {
            if (it.repository == "better-survival-physiology") it.copy(externalInputs = emptyList()) else it
        })) }
        assertThrows(IllegalArgumentException::class.java) { validateFreshSourceBindings(manifest, corrected.copy(sources = corrected.sources.map {
            if (it.repository == "better-survival-physiology") it.copy(providerSha256 = it.providerSha256 + ("water.jar" to "old-runtime-hash")) else it
        })) }
    }

    private fun git(repository: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args).directory(repository.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
    }

    private data class Qualification(val repository: Path, val mod: ActiveMod, val source: QualifiedFreshSource, val document: MutableMap<String, Any>)
    private fun qualification(): Qualification {
        val repo = Files.createDirectories(root.resolve("source"))
        git(repo, "init", "-q"); git(repo, "config", "user.email", "fixture@example.invalid"); git(repo, "config", "user.name", "Fixture")
        Files.writeString(repo.resolve("runtime.java"), "class Runtime {}")
        git(repo, "add", "."); git(repo, "commit", "-qm", "fixture")
        val head = ProcessBuilder("git", "rev-parse", "HEAD").directory(repo.toFile()).start().let { p ->
            val text = p.inputStream.bufferedReader().readText().trim(); check(p.waitFor() == 0); text
        }
        val mod = ActiveMod("fixture", "fixture", "fixture.jar", listOf("verifyFull", "stageRuntimeJar"))
        val jar = repo.resolve("build/libs/fixture.jar"); Files.createDirectories(jar.parent)
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            out.putNextEntry(JarEntry("META-INF/mods.toml")); out.write("[[mods]]\nmodId=\"fixture\"\n".toByteArray()); out.closeEntry()
        }
        val log = Files.writeString(root.resolve("verification.log"), "fixture verification passed")
        val fingerprint = productionFingerprint(repo)
        val verificationFingerprint = sourceVerificationFingerprint(repo)
        val document = linkedMapOf<String, Any>("schema" to "bc.source_qualification.v1", "repository" to "fixture", "head" to head,
            "status" to "passed", "pre_fingerprint" to fingerprint, "post_fingerprint" to fingerprint,
            "pre_source_verification_fingerprint" to verificationFingerprint, "post_source_verification_fingerprint" to verificationFingerprint,
            "pre_captured_utc" to "2026-01-01T00:00:00Z", "post_captured_utc" to "2026-01-01T00:01:00Z",
            "raw_sha256" to Hashes.sha256(jar), "native_required" to true, "native_status" to "passed",
            "native_not_required_reason" to "Unused in this synthetic receipt; full verification may not waive native checks",
            "checks" to listOf(mapOf("task" to "verifyFull", "status" to "passed", "evidence" to FreshInputSnapshot(log.toString(), Hashes.sha256(log)))),
            "provider_sha256" to emptyMap<String, String>(), "gradle_properties" to emptyMap<String, String>(), "external_inputs" to emptyList<Any>())
        val file = root.resolve("qualification.json"); mapper.writeValue(file.toFile(), document)
        val source = QualifiedFreshSource("fixture", head, fingerprint, verificationFingerprint, Hashes.sha256(jar), FreshInputSnapshot(file.toString(), Hashes.sha256(file)))
        return Qualification(repo, mod, source, document)
    }
    private fun rewritten(q: Qualification): QualifiedFreshSource {
        val path = Path.of(q.source.qualification.path); mapper.writeValue(path.toFile(), q.document)
        return q.source.copy(qualification = q.source.qualification.copy(sha256 = Hashes.sha256(path)))
    }

    @Test fun qualificationBindsActualPrePostSourceRuntimeAndChecksRatherThanHeadAlone() {
        val q = qualification()
        assertDoesNotThrow { validateQualifiedFreshSource(q.repository, q.mod, q.source) }
        Files.writeString(q.repository.resolve("runtime.java"), "class Runtime { int dirty; }")
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, q.source) }
    }

    @Test fun testOnlyChangesInvalidateQualificationWithoutChangingProductionIdentity() {
        val q = qualification()
        val before = productionFingerprint(q.repository)
        val tests = Files.createDirectories(q.repository.resolve("src/test/java"))
        Files.writeString(tests.resolve("NewRequiredTest.java"), "class NewRequiredTest {}")
        assertEquals(before, productionFingerprint(q.repository))
        assertNotEquals(q.source.sourceVerificationFingerprint, sourceVerificationFingerprint(q.repository))
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, q.source) }
    }

    @Test fun ignoredTestSourcesAndOwnerInstructionsAreStillVerificationInputsNotGeneratedOutputs() {
        val q = qualification()
        Files.writeString(q.repository.resolve(".gitignore"), "src/test/\nbuild/\n")
        val baseline = sourceVerificationFingerprint(q.repository)
        Files.writeString(Files.createDirectories(q.repository.resolve("src/test/java")).resolve("HiddenTest.java"), "class HiddenTest {}")
        assertNotEquals(baseline, sourceVerificationFingerprint(q.repository))
        val beforeInstructions = sourceVerificationFingerprint(q.repository)
        Files.writeString(q.repository.resolve("AGENTS.md"), "Required native gate")
        assertNotEquals(beforeInstructions, sourceVerificationFingerprint(q.repository))
        val beforeGenerated = sourceVerificationFingerprint(q.repository)
        Files.writeString(Files.createDirectories(q.repository.resolve("build/logs")).resolve("generated.log"), "non-input")
        assertEquals(beforeGenerated, sourceVerificationFingerprint(q.repository))
    }

    @Test fun anIndependentCurrentRequestBoundaryRejectsBackdatedPlansAndHistoricalQualification() {
        val (_, plan) = population()
        assertThrows(FreshReleaseBlocked::class.java) { validateFreshQualificationCohort(plan, emptyMap()) }
        val external = mapOf("BC_FRESH_SOURCE_REQUEST_NOT_BEFORE_UTC" to "2026-01-02T00:00:00Z")
        assertThrows(IllegalArgumentException::class.java) { validateFreshQualificationCohort(plan, external) }
        val current = plan.copy(qualificationNotBeforeUtc = "2026-01-02T00:00:00Z", createdUtc = "2026-01-02T01:00:00Z")
        val (start, created) = validateFreshQualificationCohort(current, external)
        val q = qualification()
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, q.source, start, created) }
        q.document["pre_captured_utc"] = "2026-01-02T00:10:00Z"; q.document["post_captured_utc"] = "2026-01-02T00:20:00Z"
        assertDoesNotThrow { validateQualifiedFreshSource(q.repository, q.mod, rewritten(q), start, created) }
        q.document["post_captured_utc"] = "2026-01-02T01:01:00Z"
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, rewritten(q), start, created) }
    }

    @Test fun blockedNativeAndMissingRequiredChecksCannotBeWaived() {
        val q = qualification()
        q.document["native_required"] = true; q.document["native_status"] = "blocked"
        assertThrows(FreshReleaseBlocked::class.java) { validateQualifiedFreshSource(q.repository, q.mod, rewritten(q)) }
        q.document["native_status"] = "skipped"
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, rewritten(q)) }
        q.document["native_required"] = false; q.document["native_status"] = "not-required"
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, rewritten(q)) }
        q.document["native_required"] = true; q.document["native_status"] = "passed"; q.document["checks"] = emptyList<Any>()
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, rewritten(q)) }
    }

    @Test fun changedRawJarOrEvidenceInvalidatesQualification() {
        val q = qualification()
        Files.writeString(q.repository.resolve("build/libs/fixture.jar"), "different runtime output")
        assertThrows(IllegalArgumentException::class.java) { validateQualifiedFreshSource(q.repository, q.mod, q.source) }
    }

    @Test fun providerPropertiesCannotBypassQualifiedStagingOrExecuteArbitraryGradleProperties() {
        val q = qualification().source.copy(providerSha256 = mapOf("spice.jar" to "a".repeat(64)),
            gradleProperties = mapOf("spicyFarmersProviderJar" to "@staging/spice.jar"))
        assertEquals(listOf("-PspicyFarmersProviderJar=${root.resolve("staged/spice.jar")}"), freshGradleProperties(q, root.resolve("staged")))
        assertThrows(IllegalArgumentException::class.java) { freshGradleProperties(q.copy(gradleProperties = mapOf("init-script" to "untrusted")), root) }
        assertThrows(IllegalArgumentException::class.java) { freshGradleProperties(q.copy(gradleProperties = mapOf("spicyFarmersProviderJar" to "@staging/../old.jar")), root) }
        assertThrows(IllegalArgumentException::class.java) { freshGradleProperties(q.copy(gradleProperties = mapOf("typedWaterCompileClasses" to "/unqualified/classes")), root) }
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
        fun hop(dimension: String, circuit: Int) {
            dimensionTarget = dimension
            val identity = mapOf("dimension" to dimension, "circuit" to circuit)
            event("hop_started", identity); advance(1)
            event("client_dimension_receipt", identity + mapOf("marker" to "arrival-${marker++}", "server_conditional" to true, "fresh_client_log_offset" to 123))
            event("arrival_verified", identity + ("hop_seconds" to 1.0))
            (0..30).forEach { second ->
                event("tps_sample", identity + mapOf("elapsed_seconds" to second.toDouble(), "tps" to 20.0))
                if (second < 30) advance(1)
            }
            event("tps_stable", identity + ("recovery_seconds" to 30.0))
        }
        event("client_joined"); heartbeat()
        event("dimension_roster", mapOf("dimensions" to dimensions))
        dimensions.forEach { dimension ->
            hop(dimension, 0); event("dwell_started", mapOf("dimension" to dimension)); advance(300)
            event("dwell_completed", mapOf("dimension" to dimension, "actual_seconds" to 300.0))
        }
        (1..3).forEach { circuit ->
            event("circuit_started", mapOf("circuit" to circuit, "route" to dimensions))
            dimensions.forEach { hop(it, circuit) }; event("circuit_completed", mapOf("circuit" to circuit))
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
            "dimension_results" to dims.map { mapOf("dimension" to it, "dwell_seconds" to 300.0, "completed_circuits" to listOf(1, 2, 3), "tps_verified" to true) },
            "events_sha256" to Hashes.sha256(raw)))
    }

    @Test fun completeTeleReceiptRequiresMatchingPairAndActualLinkedSamples() {
        val (path, pair, _) = teleFixture()
        assertDoesNotThrow { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "old", pair, Instant.EPOCH) }
        Files.writeString(pair.client, "changed")
        assertThrows(IllegalArgumentException::class.java) { validateTeleSoakReceipt(path, "fresh", pair, Instant.EPOCH) }
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
        events.remove(heartbeat) // Two adjacent 12s intervals become a forbidden 24s gap.
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
