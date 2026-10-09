package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/** This supplement belongs to the disposable fixture, never to the candidate or pack mods/. */
class JournalFixtureSupport(private val evidence: EvidenceRun, pair: CandidatePair, player: String) {
    private val source = evidence.config.root.parent.resolve("mod_source/better-journal-inventory/build/libs/better-native-inventory-test-support.jar")
    val mode = if (evidence.tier == "debug") "full" else "sentinel"
    val bridge = evidence.fixture.resolve("native-inventory-contract").toAbsolutePath().normalize()
    val identity: JournalContractIdentity
    val expected: Set<JournalScenario>
    val properties: String
    private val installed = mutableListOf<Path>()
    private val fixturePolicies = mutableMapOf<Path, String>()

    init {
        require(Files.isRegularFile(source)) { "build journal test support before pack tests: $source" }
        JournalCandidateExclusion.validate(pair.client)
        JournalCandidateExclusion.validate(pair.server)
        val manifest = ZipFile(source.toFile()).use { jar ->
            val entry = requireNotNull(jar.getEntry("native-inventory-manifest.json")) { "support JAR lacks scenario manifest" }
            jar.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        expected = JournalContractValidator.manifest(manifest, mode)
        val journalHash = ZipFile(pair.server.toFile()).use { archive ->
            val journals = archive.entries().asSequence().filter {
                it.name.substringAfterLast('/').startsWith("better-journal-inventory-") && it.name.endsWith(".jar")
            }.toList()
            require(journals.size == 1) { "server candidate must contain exactly one runtime journal JAR" }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            archive.getInputStream(journals.single()).use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        identity = JournalContractIdentity(evidence.config.runId, player, mode,
            pair.clientSha256, pair.serverSha256, journalHash, Hashes.sha256(source))
        bridge.createDirectories()
        val manifestPath = bridge.resolve("manifest.json")
        manifestPath.toFile().writeText(manifest)
        properties = mapOf(
            "run_id" to identity.runId, "player" to player, "mode" to mode,
            "client_sha256" to identity.clientSha256, "server_sha256" to identity.serverSha256,
            "presentation_sha256" to journalHash, "support_sha256" to identity.supportSha256,
            "dir" to bridge.toString(), "manifest" to manifestPath.toString(),
        ).entries.joinToString(" ") { (key, value) ->
            require(value.none { it.isWhitespace() || it == '"' || it == '\'' }) { "unsafe fixture JVM property" }
            "-Dbc.native.inventory.$key=$value"
        }
        evidence.directory.resolve("native-inventory-manifest.json").toFile().writeText(manifest)
        evidence.event("journal_fixture_support", mapOf("source" to source.toString(), "mode" to mode,
            "run_id" to identity.runId, "player" to player, "candidate_client_sha256" to identity.clientSha256,
            "candidate_server_sha256" to identity.serverSha256, "journal_sha256" to journalHash,
            "support_sha256" to identity.supportSha256, "expected_total" to expected.size, "bridge" to bridge.toString()))
    }

    fun install(root: Path) {
        require(root.toAbsolutePath().normalize().startsWith(evidence.fixture.toAbsolutePath().normalize())) {
            "journal support installation is fixture-only"
        }
        require(Hashes.sha256(source) == identity.supportSha256) { "support JAR changed after fixture preparation" }
        val mods = root.resolve("mods").also { it.createDirectories() }
        JournalCandidateExclusion.validateDirectory(mods)
        val journals = Files.list(mods).use { files -> files.filter {
            it.fileName.toString().startsWith("better-journal-inventory-") && it.toString().endsWith(".jar")
        }.toList() }
        require(journals.size == 1 && Hashes.sha256(journals.single()) == identity.journalSha256) {
            "fixture journal JAR differs from server candidate"
        }
        val policy = root.resolve("kubejs/config/crafting_policy.json")
        require(Files.isRegularFile(policy)) { "fixture lacks crafting policy" }
        val original = policy.readText()
        val originalHash = Hashes.sha256(policy)
        val patched = JournalFixturePolicy.classify(original)
        policy.toFile().writeText(patched)
        val fixtureHash = Hashes.sha256(policy)
        fixturePolicies[policy] = fixtureHash
        val evidencePrefix = "journal-fixture-policy-${installed.size}"
        evidence.directory.resolve("$evidencePrefix-original.json").toFile().writeText(original)
        evidence.directory.resolve("$evidencePrefix-supplemented.json").toFile().writeText(patched)
        evidence.event("journal_fixture_classification", mapOf("path" to policy.toString(),
            "original_policy_sha256" to originalHash, "fixture_policy_sha256" to fixtureHash,
            "namespace" to JournalFixturePolicy.NAMESPACE, "support_sha256" to identity.supportSha256,
            "candidate_client_sha256" to identity.clientSha256, "candidate_server_sha256" to identity.serverSha256))
        val destination = mods.resolve(source.fileName)
        Files.copy(source, destination)
        installed.add(destination)
    }

    fun preserveBridge() {
        val output = evidence.directory.resolve("native-inventory-contract-bridge").also { it.createDirectories() }
        Files.walk(bridge).use { files -> files.filter { Files.isRegularFile(it) }.forEach { source ->
            val target = output.resolve(bridge.relativize(source))
            target.parent.createDirectories()
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } }
    }

    fun assertStable() {
        require(Hashes.sha256(source) == identity.supportSha256) { "support JAR changed during test" }
        installed.forEach { require(Hashes.sha256(it) == identity.supportSha256) { "installed support JAR changed: $it" } }
        fixturePolicies.forEach { (path, hash) -> require(Hashes.sha256(path) == hash) { "fixture crafting policy changed: $path" } }
        require(JournalContractValidator.manifest(bridge.resolve("manifest.json").readText(), mode) == expected) {
            "fixture scenario manifest changed during test"
        }
    }
}
