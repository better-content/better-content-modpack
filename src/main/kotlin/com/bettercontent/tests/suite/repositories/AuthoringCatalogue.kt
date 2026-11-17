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
    need(rows.isArray && rows.size() == 206 && catalogue.size == rows.size() && catalogue.keys.none(String::isBlank),
        "incomplete/duplicate catalogue cases (203 historical + two client + onboarding)")
    val onboardingActions = listOf("world.entry", "player.observe_onboarding.arm", "client.input_hold",
        "client.input_release", "client.input_status", "player.observe_onboarding.await", "player.snapshot")
    val onboardingProfiles = listOf("1280x720@2", "1920x1080@3")
    val onboarding = catalogue["world.onboarding"]
    need(onboarding != null && onboarding.path("required_assertions").map { it.asText() } == listOf(
        "world.onboarding.first-join-spectator", "world.onboarding.genuine-spawn-only-input",
        "world.onboarding.safe-final-survival-site") &&
        onboarding.path("required_capabilities").map { it.asText() } == onboardingActions &&
        onboarding.path("supported_input_profiles").map { it.asText() } == onboardingProfiles &&
        onboarding.path("source_path").asText().endsWith("/drivers/WorldOnboardingDriver.kt") &&
        onboarding.path("test_path").asText().endsWith("/drivers/OnboardingControlPlanTest.kt"),
        "onboarding requires genuine seven-action source and supported (not selected) client profiles")
    val controls = document.path("control_metadata")
    val controlRows = controls.takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    need(controls.isArray && controls.size() == 2 && controlRows.keys == setOf(
        "control.travel.walk-route", "control.client.inventory-screen") && controlRows.size == 2,
        "two registered control metadata rows required, never additional executable full cases")
    val profiles = listOf("1280x720@2", "1920x1080@3")
    val walk = controlRows["control.travel.walk-route"]
    val walkActions = listOf("block.snapshot", "player.snapshot", "client.input_hold", "wait.ticks",
        "client.input_release", "client.input_status")
    need(walk != null && walk.path("source_path").asText().endsWith("/drivers/WalkRouteControlDriver.kt") &&
        walk.path("test_path").asText().endsWith("/drivers/WalkRouteControlDriverTest.kt") &&
        walk.path("plan_cardinality").asText() == "fixed-reviewed-1-to-4-legs" &&
        walk.path("required_capabilities").map { it.asText() } == walkActions &&
        walk.path("supported_input_profiles").map { it.asText() } == profiles &&
        walk.path("state").asText() == "authored-control-only-unrun" &&
        walk.path("reviewed_route_sha256").isNull && walk.path("reviewed_profile").isNull,
        "walk control metadata must retain bounded route/profile with no fabricated review")
    val screen = controlRows["control.client.inventory-screen"]
    val screenActions = listOf("player.snapshot", "client.snapshot", "client.input_hold", "client.input_release",
        "client.input_status", "client.snapshot", "client.framebuffer", "player.snapshot")
    need(screen != null && screen.path("source_path").asText().endsWith("/client/InventoryScreenControlDriver.kt") &&
        screen.path("test_path").asText().endsWith("/client/InventoryScreenControlDriverTest.kt") &&
        screen.path("required_capabilities").map { it.asText() } == screenActions.distinct() &&
        screen.path("exact_action_sequence").map { it.asText() } == screenActions &&
        screen.path("supported_input_profiles").map { it.asText() } == profiles &&
        screen.path("state").asText() == "authored-control-only-unrun" &&
        screen.path("reviewed_framebuffer_sha256").isNull && screen.path("reviewed_profile").isNull,
        "inventory screen control metadata must retain real input/frame chain without fabricated review")
    val diagnosticRows = document.path("diagnostic_obligations")
    val diagnostic = diagnosticRows.takeIf { it.isArray }?.associateBy { it.path("id").asText() }.orEmpty()
    val diagnosticId = "control.mutation.external-getter-negative"
    val diagnosticDriver = "src/main/kotlin/com/bettercontent/tests/suite/drivers/Stage1ExternalGetterNegativeControlDriver.kt"
    val diagnosticTest = "src/test/kotlin/com/bettercontent/tests/suite/drivers/Stage1ExternalGetterNegativeControlDriverTest.kt"
    val dormant = diagnosticRows.isMissingNode || diagnosticRows.isArray && diagnosticRows.isEmpty
    val separateNegative = diagnosticRows.isArray && diagnosticRows.size() == 1 &&
        diagnostic.keys == setOf(diagnosticId) && diagnosticId !in catalogue
    need(if (qualification) dormant || separateNegative else separateNegative,
        "getter negative must remain a separate diagnostic-only obligation")
    val negative = diagnostic[diagnosticId]
    if (negative != null) need(negative.path("kind").asText() == "diagnostic-negative-only-not-qualification" &&
        negative.path("required_assertions").isArray && negative.path("required_assertions").isEmpty &&
        negative.path("required_inventory_domains").isArray && negative.path("required_inventory_domains").isEmpty &&
        negative.path("required_capabilities").map { it.asText() } == listOf("block_mutation.control_expose_states_v1") &&
        negative.path("implementation_path").asText() == diagnosticDriver &&
        negative.path("source_path").asText() == diagnosticDriver && negative.path("test_path").asText() == diagnosticTest &&
        negative.path("state").asText() in setOf("authored-uncompiled-unrun-not-executable",
            "authored-compiled-unrun-not-executable", "reviewed-diagnostic-only") &&
        negative.path("remaining_defects").isArray && !negative.path("remaining_defects").isEmpty,
        "diagnostic negative must bind only C real-getter source, not a full-case assertion or inventory promise")
    need(manifestRows.isArray && active.size == manifestRows.size(), "duplicate or malformed executable cases")
    need(selectedCaseIds.isNotEmpty() && selectedCaseIds.all { it in active &&
        (it in catalogue || !qualification && it == diagnosticId && it in diagnostic) },
        "selected obligation missing from catalogue/executable manifest")
    if (qualification) need(selectedCaseIds == catalogue.keys && selectedCaseIds == active.keys &&
        diagnosticId !in active, "qualification requires complete catalogue of exactly the 206 full cases and no diagnostic getter")
    val reviewedAt = runCatching { Instant.parse(document.path("reviewed_at").asText()) }.getOrNull()
    need(document.path("execution_eligible").asBoolean(false) &&
        (!qualification || document.path("qualification_eligible").asBoolean(false)) &&
        document.path("status").asText() == "reviewed" &&
        document.path("reviewed_by").asText().isNotBlank() && reviewedAt != null && !reviewedAt.isAfter(Instant.now()),
        "draft/unbound catalogue is never executable or qualified")
    for (id in selectedCaseIds) {
        if (id == diagnosticId && !qualification) {
            val row = negative ?: continue
            val selected = active[id] ?: continue
            val binding = bindings[id]
            val candidate = row.path("candidate_binding")
            val scope = row.path("scope_review")
            val reviewedTime = runCatching { Instant.parse(scope.path("reviewed_at").asText()) }.getOrNull()
            need(row.path("state").asText() == "reviewed-diagnostic-only" &&
                row.path("review_status").asText() == "reviewed" &&
                candidate.isObject && candidate.path("client_sha256").asText() ==
                    manifest.path("candidate").path("client").path("sha256").asText() &&
                candidate.path("server_sha256").asText() ==
                    manifest.path("candidate").path("server").path("sha256").asText() &&
                candidate.path("test_support_sha256").asText() == manifest.path("test_support").path("sha256").asText() &&
                scope.isObject && scope.path("status").asText() == "reviewed" &&
                scope.path("reviewer").asText().isNotBlank() && reviewedTime != null &&
                !reviewedTime.isAfter(Instant.now()) &&
                selected.path("layer").asText() == "campaign" && selected.path("assertions").isArray &&
                selected.path("assertions").isEmpty && selected.path("required_inventory_domains").isArray &&
                selected.path("required_inventory_domains").isEmpty &&
                selected.path("implementation").path("state").asText() == "authored" &&
                selected.path("implementation").path("path").asText() == diagnosticDriver &&
                binding?.path("driver_id")?.asText() == diagnosticId &&
                binding?.path("required_actions")?.map { it.asText() } ==
                    listOf("block_mutation.control_expose_states_v1"),
                "unreviewed/incorrect diagnostic getter source, candidate, scope or single native action: $id")
            continue
        }
        val row = catalogue[id] ?: continue
        val selected = active[id] ?: continue
        fun match(field: String, other: JsonNode, description: String) {
            val left = ids(row.path(field)); val right = ids(other)
            need(left != null && left.isNotEmpty() && left == right, "$description diverges or is empty: $id")
        }
        match("required_assertions", selected.path("assertions"), "assertion contract")
        match("required_capabilities", bindings[id]?.path("required_actions") ?: selected.path("__missing__"), "native action contract")
        match("required_capabilities", selected.path("required_capabilities"), "selected case capability contract")
        if (id == "world.onboarding") {
            val binding = bindings[id]
            val selectedProfile = binding?.path("parameters")?.path("onboarding")?.path("input_profile")?.asText()
            need(binding?.path("required_actions")?.map { it.asText() } == onboardingActions &&
                selected.path("required_capabilities").map { it.asText() } == onboardingActions &&
                selectedProfile != null && selectedProfile in onboardingProfiles &&
                manifest.path("profile").path("visual_profiles").map { it.asText() }.contains(selectedProfile),
                "onboarding needs exact seven ordered actions and one reviewed client input profile: $id")
        }
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
