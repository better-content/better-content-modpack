package com.bettercontent.tests.suite.repositories

import com.bettercontent.tests.suite.boundInput
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path
import java.time.Instant

/** A separately SHA-bound authoring obligation catalogue, never an execution result. */
internal fun validateAuthoringCatalogue(root: Path, manifest: JsonNode, selectedCaseIds: Set<String>,
    qualification: Boolean): List<String> {
    val errors = mutableListOf<String>()
    val file = runCatching { boundInput(root, manifest.path("authoring_catalogue")) }.getOrElse {
        return listOf("v3 authoring catalogue unbound: ${it.message}")
    }
    val document = runCatching { suiteMapper.readTree(file.toFile()) }.getOrElse {
        return listOf("v3 authoring catalogue unreadable: ${it.message}")
    }
    fun need(ok: Boolean, detail: String) { if (!ok) errors += detail }
    fun ids(node: JsonNode): Set<String>? {
        if (!node.isArray || node.any { !it.isTextual || it.asText().isBlank() }) return null
        val values = node.map { it.asText() }
        return values.toSet().takeIf { it.size == values.size }
    }
    need(document.path("schema").asText() == "bc.playtest_authoring_catalogue.v3", "unsupported authoring catalogue schema")
    need(document.path("purpose").asText() == manifest.path("purpose").asText() &&
        document.path("purpose").asText() in setOf("qualification", "diagnostic"), "catalogue/manifest purpose mismatch")
    val sourceDocuments = document.path("source_documents")
    for (key in listOf("historical_catalogue", "source_oracles", "open_assessments", "candidate_reference")) {
        val source = runCatching { boundInput(root, sourceDocuments.path(key)) }.getOrNull()
        need(source != null, "authoring catalogue historical input changed or missing: $key")
    }
    val owners = document.path("owners")
    val ownerRows = owners.takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    val manifestRepositories = manifest.path("repositories").takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    val manifestOwners = manifestRepositories.keys
    need(owners.isArray && owners.size() == 54 && ownerRows.size == 54 &&
        document.path("required_owner_count").asInt(-1) == 54 &&
        document.path("required_source_repository_count").asInt(-1) == 53 &&
        manifestOwners == ownerRows.keys && "better-content-modpack" in ownerRows,
        "catalogue does not account for exact 53 repositories plus pack")
    ownerRows.forEach { (id, owner) ->
        need(id.isNotBlank() && ids(owner.path("case_ids")) == setOf("repo.$id.promise", "repo.$id.scale", "repo.$id.ci") &&
            owner.path("source_path").asText().isNotBlank() && owner.path("test_path").asText().isNotBlank() &&
            owner.path("provider_paths").isArray && owner.path("toolchain_paths").isArray &&
            owner.path("configuration_paths").isArray, "source/test/provider/toolchain/config obligation missing for $id")
        if (qualification) need(owner.path("source_to_artifact").asText() == "verified" &&
            owner.path("production_source_paths").isArray && !owner.path("production_source_paths").isEmpty &&
            owner.path("toolchain_paths").isArray && !owner.path("toolchain_paths").isEmpty &&
            owner.path("configuration_paths").isArray && !owner.path("configuration_paths").isEmpty &&
            ids(owner.path("expected_runtime_test_ids"))?.isNotEmpty() == true &&
            owner.path("artifact_sha256").asText().matches(Regex("[0-9a-f]{64}")) &&
            owner.path("artifact_sha256").asText() == manifestRepositories[id]?.path("artifact_sha256")?.asText() &&
            owner.path("status").asText() == "reviewed", "unbound artifact or zero exact expected test membership: $id")
    }
    val rows = document.path("cases")
    val catalogue = rows.takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    val manifestRows = manifest.path("cases")
    val active = manifestRows.takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    val bindings = manifest.path("driver_bindings").takeIf { it.isArray }?.associateBy { it.path("case_id").asText() }.orEmpty()
    need(rows.isArray && catalogue.size == rows.size() && catalogue.keys.none(String::isBlank), "duplicate or malformed catalogue cases")
    need(manifestRows.isArray && active.size == manifestRows.size(), "duplicate or malformed executable cases")
    need(selectedCaseIds.isNotEmpty() && selectedCaseIds.all { it in active && it in catalogue }, "selected obligation missing from catalogue/executable manifest")
    if (qualification) need(selectedCaseIds == catalogue.keys && selectedCaseIds == active.keys,
        "qualification requires complete catalogue and executable case membership")
    val reviewedAt = runCatching { Instant.parse(document.path("reviewed_at").asText()) }.getOrNull()
    need(document.path("execution_eligible").asBoolean(false) &&
        (!qualification || document.path("qualification_eligible").asBoolean(false)) &&
        document.path("status").asText() == "reviewed" &&
        document.path("reviewed_by").asText().isNotBlank() && reviewedAt != null && !reviewedAt.isAfter(Instant.now()),
        "draft/unbound catalogue is never executable or qualified")
    for (id in selectedCaseIds) {
        val row = catalogue[id] ?: continue
        val selected = active[id] ?: continue
        fun match(field: String, other: JsonNode, description: String) {
            val left = ids(row.path(field)); val right = ids(other)
            need(left != null && left.isNotEmpty() && left == right, "$description diverges or is empty: $id")
        }
        match("required_assertions", selected.path("assertions"), "assertion contract")
        match("required_capabilities", bindings[id]?.path("required_actions") ?: selected.path("__missing__"), "native action contract")
        match("required_capabilities", selected.path("required_capabilities"), "selected case capability contract")
        match("required_inventory_domains", selected.path("required_inventory_domains"), "reviewed inventory scope")
        need(row.path("domain").asText().isNotBlank() && row.path("domain").asText() == selected.path("domain").asText(),
            "domain not exact: $id")
        need(row.path("implementation_path").asText().isNotBlank() &&
            row.path("implementation_path").asText() == selected.path("implementation").path("path").asText() &&
            row.path("state").asText() == "reviewed" && selected.path("implementation").path("state").asText() == "authored",
            "implementation path/review missing: $id")
        need(row.path("source_path").asText().isNotBlank() && row.path("test_path").asText().isNotBlank() &&
            row.path("provider_paths").isArray && row.path("producer_contract").path("status").asText() == "reviewed",
            "source/test/provider contract not reviewed: $id")
        need(row.path("target_bindings").isArray && row.path("target_bindings").size() > 0 &&
            row.path("target_bindings") == selected.path("target_bindings") &&
            row.path("inventory_domain_review").asText() == "reviewed" &&
            row.path("acquisition").path("status").asText() in setOf("reviewed", "reviewed-not-applicable") &&
            row.path("persistence").path("status").asText() in setOf("reviewed", "reviewed-not-applicable") &&
            row.path("reviews").path("status").asText() == "reviewed" &&
            listOf("source", "target", "artifact", "human").all { row.path("reviews").path(it).asText().isNotBlank() },
            "target/acquisition/persistence/review obligation unbound: $id")
    }
    return errors
}
