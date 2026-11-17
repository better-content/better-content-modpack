package com.bettercontent.tests.suite

import java.nio.file.Path
import java.util.ServiceLoader

/** A native adapter is deliberately not inferred from the existence of old probe classes. */
internal interface CampaignAdapter {
    val supportedCases: Set<String>
    /** Must own the existing disposable lifecycle, one world and native receipts; never package inputs. */
    fun execute(request: CampaignRequest)
    fun executeV2(request: V2CampaignRequest) { error("adapter does not implement v2") }
    fun executeV3(request: com.bettercontent.tests.suite.checkpoints.V3CampaignRequest) { error("adapter does not implement checkpoint epochs") }
}
internal data class CampaignRequest(val root: Path, val manifestPath: Path, val manifest: SuiteManifest,
    val client: Path, val server: Path, val cases: List<String>, val diagnostic: Boolean)

internal fun campaignRequest(root: Path, manifestPath: Path, client: Path, server: Path, selected: List<String>): CampaignRequest {
    val manifest = loadSuiteManifest(manifestPath)
    val problems = manifestProblems(manifest, root)
    require(problems.isEmpty()) { "Campaign blocked before world creation: authoring checkpoint incomplete:\n${problems.joinToString("\n")}" }
    require(client.toRealPath() == resolveSuitePath(root, manifest.candidate.client.path).toRealPath() &&
        server.toRealPath() == resolveSuitePath(root, manifest.candidate.server.path).toRealPath()) { "explicit artifacts differ from manifest" }
    validateCandidateInventory(manifest, root)
    readBoundFile(root, manifest.testSupport)
    val authored = manifest.cases.filter { it.layer == "campaign" }.map { it.id }
    require(authored.isNotEmpty()) { "no authored campaign cases" }
    require(selected.distinct().size == selected.size && selected.all { it in authored }) { "invalid diagnostic selector" }
    // A selector always makes the run diagnostic, even if it happens to list every case.
    return CampaignRequest(root, manifestPath, manifest, client, server, if (selected.isEmpty()) authored else selected, selected.isNotEmpty())
}

fun main(args: Array<String>) {
    require(args.size in 7..8) { "internal usage: SuiteMain ROOT campaign|qualify|discover|convert-suite|inspect-suite MANIFEST CLIENT SERVER CASES RECEIPTS [OUTPUT]; use test.main.kts" }
    require(!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) { "invoke test.main.kts under its shared lock" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val manifest = Path.of(args[2]).toAbsolutePath().normalize()
    when (args[1]) {
        "qualify" -> {
            require(args[3].isEmpty() && args[4].isEmpty() && args[5].isEmpty() && args[6].isNotBlank())
            val index = Path.of(args[6]).toAbsolutePath().normalize()
            when (suiteMapper.readTree(manifest.toFile()).path("schema").asText()) {
                "bc.playtest_manifest.v2" -> println(qualifyV2Suite(root, manifest, index))
                "bc.playtest_manifest.v3" -> println(com.bettercontent.tests.suite.checkpoints.qualifyV3Suite(root, manifest, index))
                else -> error("historical receipt versions cannot qualify; explicitly convert a draft and author new evidence")
            }
        }
        "campaign" -> {
            require(args[3].isNotBlank() && args[4].isNotBlank() && args[6].isEmpty())
            val selected = if (args[5].isEmpty()) emptyList() else args[5].split(',').also { require(it.all(String::isNotBlank)) }
            val adapters = ServiceLoader.load(CampaignAdapter::class.java).toList()
            require(adapters.size == 1) { "Campaign blocked: exactly one reviewed native adapter is required. No world was created." }
            val adapter = adapters.single()
            if (suiteMapper.readTree(manifest.toFile()).path("schema").asText() == "bc.playtest_manifest.v3") {
                val request = com.bettercontent.tests.suite.checkpoints.v3CampaignRequest(root, manifest, Path.of(args[3]), Path.of(args[4]), selected)
                val ordinary = adapter.supportedCases.containsAll(request.base.cases.map { it.id })
                val isolatedDiagnosticGetter = request.purpose ==
                    com.bettercontent.tests.suite.checkpoints.ExecutionPurpose.DIAGNOSTIC &&
                    request.base.cases.size == 1 && request.base.cases.single().id ==
                    com.bettercontent.tests.suite.checkpoints.DIAGNOSTIC_GETTER_CASE &&
                    com.bettercontent.tests.suite.drivers.DriverRegistry.diagnosticOnly(
                        request.base.cases.single().id) != null &&
                    request.base.cases.single().id !in adapter.supportedCases
                val boundedDiagnosticIds =
                    com.bettercontent.tests.suite.checkpoints.reviewedDiagnosticPositiveCases.keys +
                    com.bettercontent.tests.suite.checkpoints.reviewedStage1NegativeCases.keys
                val selectedControls = request.base.cases.map { it.id }.filter { it in boundedDiagnosticIds }
                val isolatedDiagnosticControls = request.purpose ==
                    com.bettercontent.tests.suite.checkpoints.ExecutionPurpose.DIAGNOSTIC &&
                    selectedControls.isNotEmpty() && "control.mutation.setup-delegated" in selectedControls &&
                    adapter.supportedCases.containsAll(request.base.cases.filterNot {
                        it.id in selectedControls }.map { it.id }) &&
                    selectedControls.all { id ->
                        com.bettercontent.tests.suite.drivers.DriverRegistry.diagnosticOnly(id) != null &&
                        id !in adapter.supportedCases }
                require(ordinary || isolatedDiagnosticGetter || isolatedDiagnosticControls) {
                    "native adapter lacks requested drivers" }
                adapter.executeV3(request)
            } else {
                val request = v2CampaignRequest(root, manifest, Path.of(args[3]), Path.of(args[4]), selected)
                require(adapter.supportedCases.containsAll(request.cases.map { it.id })) { "Campaign blocked: native adapter lacks requested case drivers" }
                adapter.executeV2(request)
            }
        }
        "discover" -> {
            require(args.size == 8 && args[3].isNotBlank() && args[4].isNotBlank() && args[5].isEmpty() && args[6].isEmpty() && args[7].isNotBlank())
            println(discoverSuite(root, manifest, Path.of(args[3]), Path.of(args[4]), Path.of(args[7]).toAbsolutePath().normalize()))
        }
        "convert-suite" -> {
            require(args.size == 8 && args.slice(3..6).all(String::isEmpty) && args[7].isNotBlank())
            val output = Path.of(args[7]).toAbsolutePath().normalize()
            val converted = com.bettercontent.tests.suite.checkpoints.convertCheckpointDraft(suiteMapper.readTree(manifest.toFile()))
            exclusiveJson(output, converted)
            println("Draft converted only; no review or evidence upgraded: $output")
        }
        "inspect-suite" -> {
            require(args.size == 8 && args[3].isNotBlank() && args[4].isNotBlank() && args[5].isEmpty() && args[6].isEmpty() && args[7].isNotBlank())
            val output = Path.of(args[7]).toAbsolutePath().normalize()
            val inventory = com.bettercontent.tests.suite.v2.contracts.inspectCandidate(Path.of(args[3]), Path.of(args[4]))
            exclusiveJson(output, inventory)
            println("Offline archive inventory only; no effective runtime claims: $output")
        }
        else -> error("unknown suite command")
    }
}
