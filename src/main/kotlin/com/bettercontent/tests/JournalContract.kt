package com.bettercontent.tests

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** Expected identities are fixture-derived, never taken from the report being validated. */
data class JournalContractIdentity(
    val runId: String, val player: String, val mode: String,
    val clientSha256: String, val serverSha256: String, val journalSha256: String, val supportSha256: String,
)
data class JournalScenario(val id: String, val layer: String)

object JournalContractValidator {
    private val mapper = jacksonObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    const val MARKER = "journal-contract-report "
    const val CHECKPOINT_MARKER = "journal-checkpoint-report "

    fun validateCheckpoint(json: String, identity: JournalContractIdentity, operation: String): JsonNode {
        require(operation in setOf("save", "verify")) { "invalid journal checkpoint operation" }
        val report = mapper.readTree(json)
        require(report.isObject) { "journal checkpoint must be an object" }
        mapOf("schema" to "bc.journal.checkpoint.v1", "run_id" to identity.runId,
            "player" to identity.player, "operation" to operation, "status" to "passed").forEach { (field, value) ->
            val actual = report.path(field)
            require(actual.isTextual && actual.textValue() == value && value.isNotBlank()) {
                "journal checkpoint mismatch: $field"
            }
        }
        require(report.path("detail").isTextual && report.path("detail").textValue().isNotBlank()) {
            "journal checkpoint lacks detail"
        }
        return report
    }

    fun manifest(json: String, mode: String): Set<JournalScenario> {
        require(mode in setOf("sentinel", "full")) { "invalid manifest mode" }
        val document = mapper.readTree(json)
        require(document.isObject && document.fieldNames().asSequence().toSet() == setOf("schema", "sentinel", "full") &&
            document.path("schema").isTextual && document.path("schema").textValue() == "bc.journal.manifest.v1") {
            "journal manifest must have bc.journal.manifest.v1 schema, sentinel and full arrays"
        }
        val manifests = listOf("sentinel", "full").associateWith { name ->
            val rows = document.path(name)
            require(rows.isArray && rows.size() > 0) { "empty journal manifest: $name" }
            val scenarios = rows.map { row ->
                require(row.isObject && row.path("id").isTextual && row.path("layer").isTextual) { "invalid manifest row" }
                val scenario = JournalScenario(row.path("id").textValue(), row.path("layer").textValue())
                require(Regex("[a-z0-9_.-]+").matches(scenario.id) && scenario.layer in setOf("server", "client", "persistence")) {
                    "invalid manifest scenario: $scenario"
                }
                scenario
            }
            require(scenarios.map { it.id }.toSet().size == scenarios.size) { "duplicate manifest ID" }
            scenarios.toSet()
        }
        require(manifests.getValue("full").containsAll(manifests.getValue("sentinel"))) { "full must include sentinel scenarios" }
        return manifests.getValue(mode)
    }

    fun validate(json: String, identity: JournalContractIdentity, expected: Set<JournalScenario>): JsonNode {
        require(expected.isNotEmpty()) { "empty journal scenario manifest" }
        val report = mapper.readTree(json)
        require(report.isObject) { "journal report must be an object" }
        fun text(node: JsonNode, field: String): String {
            require(node.path(field).isTextual) { "journal field $field must be a string" }
            return node.path(field).textValue()
        }
        val identities = mapOf(
            "schema" to "bc.journal.contract.v1", "run_id" to identity.runId,
            "player" to identity.player, "mode" to identity.mode,
            "candidate_client_sha256" to identity.clientSha256, "candidate_server_sha256" to identity.serverSha256,
            "journal_sha256" to identity.journalSha256, "support_sha256" to identity.supportSha256,
        )
        require(identity.mode in setOf("sentinel", "full")) { "invalid journal mode" }
        identities.forEach { (field, value) ->
            require(value.isNotBlank() && text(report, field) == value) { "journal identity mismatch: $field" }
        }
        listOf(identity.clientSha256, identity.serverSha256, identity.journalSha256, identity.supportSha256).forEach {
            require(Regex("[a-f0-9]{64}").matches(it)) { "invalid expected journal hash" }
        }
        require(text(report, "status") == "passed") { "journal contract did not pass" }
        require(text(report, "cleanup") == "passed") { "journal cleanup did not pass" }
        val rows = report.path("rows")
        require(rows.isArray && rows.size() == expected.size) { "journal report row count mismatch" }
        val seen = mutableSetOf<JournalScenario>()
        rows.forEach { row ->
            require(row.isObject) { "journal row must be an object" }
            val scenario = JournalScenario(text(row, "id"), text(row, "layer"))
            require(seen.add(scenario)) { "duplicate journal row: $scenario" }
            require(scenario in expected) { "unexpected journal row: $scenario" }
            require(text(row, "status") == "passed") { "journal row did not pass: $scenario" }
            require(text(row, "detail").isNotBlank()) { "journal row has no detail: $scenario" }
        }
        require(seen == expected) { "missing journal scenarios" }
        listOf("expected_total", "passed", "total").forEach { field ->
            val count = report.path(field)
            require(count.isIntegralNumber && count.canConvertToInt() && count.intValue() == expected.size) {
                "journal count mismatch: $field"
            }
        }
        return report
    }
}
