package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.suite.drivers.DriverObservation
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Authored negative and plan contracts only; no client, screenshot, world or test execution here. */
class NarrowClientDriversTest {
    @TempDir lateinit var root: Path
    private fun emiRecipe(id: String?, provider: String, category: String, outputs: List<String>,
        inputs: List<String> = emptyList()) = suiteMapper.createObjectNode().apply {
        put("identity_kind", if (id == null) "anonymous_public_projection" else "public_recipe_id")
        if (id == null) putNull("recipe_id") else put("recipe_id", id)
        put("provider_class", provider); put("category_id", category)
        putArray("output_ids").apply { outputs.forEach(::add) }
        putArray("input_alternative_ids").apply { inputs.forEach(::add) }
        if (id == null) put("projection_sha256", java.security.MessageDigest.getInstance("SHA-256")
            .digest(suiteMapper.writeValueAsBytes(listOf("bc.playtest_emi_public_projection.v1", provider,
                category, inputs, outputs))).joinToString("") { "%02x".format(it) })
        else putNull("projection_sha256")
    }
    private fun emi() = suiteMapper.createObjectNode().apply {
        put("player_uuid", "00000000-0000-0000-0000-000000000001")
        put("dimension", "minecraft:overworld")
        put("emi_jar_sha256", "a".repeat(64))
        putArray("expected_index_ids").add("minecraft:stone")
        putArray("expected_recipe_records").add(emiRecipe("minecraft:stone",
            "dev.emi.emi.recipe.EmiShapedRecipe", "minecraft:crafting", listOf("minecraft:stone")))
    }
    private fun sync() = suiteMapper.createObjectNode().apply {
        put("player_uuid", "00000000-0000-0000-0000-000000000001")
        put("dimension", "minecraft:overworld")
        put("fixture_id", "reviewed-sync")
        put("selected_slot", 2)
        put("item_id", "minecraft:stone")
        put("count", 1)
        put("expected_stack_sha256", "b".repeat(64))
        put("food_level", 20)
        put("saturation", 5.0)
        put("wait_ticks", 20)
    }
    @Test fun emiIndexPlanNeverClaimsBroadUsageNavigationOrAcquisition() {
        assertEquals(setOf("client.emi.index-roster"), EmiIndexDriver.assertionIds)
        val trial = EmiIndexDriver.expandTrials(emi(), "origin").single()
        assertEquals(emptySet<String>(), trial.provisionedTargets)
        assertEquals(listOf("client.snapshot", "client.emi_index", "client.snapshot"),
            EmiIndexDriver.planTrial(trial).map { it.action.id })
        assertEquals(listOf("client", "client", "client"), EmiIndexDriver.planTrial(trial).map { it.action.source })
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.evaluateTrial(trial, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            EmiIndexDriver.plan(emi().apply { putArray("expected_index_ids").add("minecraft:zinc").add("minecraft:air") })
        }
    }
    private fun syncObservations(): List<DriverObservation> {
        val p = sync()
        fun snapshot(client: Boolean, after: Boolean) = suiteMapper.createObjectNode().apply {
            put(if (client) "player_uuid" else "uuid", p.path("player_uuid").asText())
            put("dimension", "minecraft:overworld"); put("selected_slot", 2)
            put("mode", "survival"); put("synthetic_scale_participant", false)
            put("held_item", if (after) "minecraft:stone" else "minecraft:air")
            put("held_count", if (after) 1 else 0); put("held_stack_sha256", "b".repeat(64))
            val inventory = putArray("inventory")
            for (slot in 0..40) if (client || (after && slot == 2)) {
                inventory.addObject().apply {
                    put("slot", slot); put(if (client) "item_id" else "item", if (after && slot == 2) "minecraft:stone" else "minecraft:air")
                    put("count", if (after && slot == 2) 1 else 0)
                    put(if (client) "stack_snbt_sha256" else "stack_sha256", "b".repeat(64))
                }
            }
        }
        val grant = suiteMapper.createObjectNode().apply {
            put("fixture_id", "reviewed-sync"); put("player_uuid", p.path("player_uuid").asText())
            put("owner_case_id", InventorySyncDriver.caseId); put("hand", "main_hand")
            put("item_id", "minecraft:stone"); put("count", 1)
            put("source_origin", "authored_fixture_not_natural"); put("provisioned_item", "minecraft:stone")
            put("provisioned_count", 1); putArray("provisioned_targets").add("minecraft:stone")
            putArray("fixture_mutations").add("inventory").add("food")
        }
        val payloads = listOf(snapshot(false, false), snapshot(true, false), grant,
            suiteMapper.createObjectNode().put("ticks_observed", 20), snapshot(false, true), snapshot(true, true))
        return InventorySyncDriver.plan(p).zip(payloads).map { (step, payload) ->
            DriverObservation(step.name, step.action.id, step.action.source, "completed", payload)
        }
    }

    @Test fun syncOracleRequiresFullClientRosterAndExactFixtureOwner() {
        assertEquals("passed", InventorySyncDriver.evaluate(sync(), syncObservations()).state)
        fun changed(index: Int, mutate: (ObjectNode) -> Unit) = syncObservations().mapIndexed { i, observation ->
            if (i != index) observation else observation.copy(payload = observation.payload.deepCopy<ObjectNode>().also(mutate))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventorySyncDriver.evaluate(sync(), changed(5) { (it.path("inventory") as com.fasterxml.jackson.databind.node.ArrayNode).remove(40) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventorySyncDriver.evaluate(sync(), changed(2) { it.put("owner_case_id", "foreign.case") })
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventorySyncDriver.evaluate(sync(), changed(5) { (it.path("inventory")[2] as ObjectNode).put("count", 2) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventorySyncDriver.evaluate(sync(), changed(5) { (it.path("inventory")[2] as ObjectNode).put("count", 4_294_967_297L) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventorySyncDriver.evaluate(sync(), changed(2) { it.put("player_uuid", "00000000-0000-0000-0000-000000000002") })
        }
    }

    @Test fun emiIdProjectionPreservesDuplicateStackIdsWithoutClaimingVariantCoverage() {
        val p = emi().apply {
            putArray("expected_index_ids").add("minecraft:stone").add("minecraft:stone")
            putArray("expected_recipe_records").add(emiRecipe("emi:fuel_coal",
                "dev.emi.emi.recipe.EmiFuelRecipe", "emi:fuel", emptyList()))
                .add(emiRecipe("minecraft:stone", "dev.emi.emi.recipe.EmiShapedRecipe",
                    "minecraft:crafting", listOf("minecraft:stone")))
        }
        val file = root.resolve("emi-public.json")
        val document = suiteMapper.createObjectNode().apply {
            put("schema", "bc.playtest_emi_public_index.v2"); put("emi_jar_sha256", "a".repeat(64))
            set<com.fasterxml.jackson.databind.JsonNode>("index_ids", p.path("expected_index_ids"))
            set<com.fasterxml.jackson.databind.JsonNode>("recipes", p.path("expected_recipe_records"))
        }
        Files.writeString(file, suiteMapper.writeValueAsString(document))
        fun snapshot() = suiteMapper.createObjectNode().apply {
            put("player_uuid", p.path("player_uuid").asText()); put("dimension", "minecraft:overworld")
            put("selected_slot", 0); putArray("inventory")
        }
        val export = snapshot().apply {
            put("complete", true); put("emi_jar_sha256", "a".repeat(64))
            put("source", "pinned dev.emi.emi.api.EmiApi:getIndexStacks/getRecipeManager")
            put("file", file.toString()); put("file_bytes", Files.size(file)); put("file_sha256", Hashes.sha256(file))
            put("index_count", 2); put("recipe_count", 2)
        }
        val observations = EmiIndexDriver.plan(p).zip(listOf(snapshot(), export, snapshot())).map { (step, payload) ->
            DriverObservation(step.name, step.action.id, "client", "completed", payload)
        }
        assertEquals("passed", EmiIndexDriver.evaluate(p, observations).state)
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.evaluate(emi(), observations) }
        val malformed = emi().apply {
            putArray("expected_recipe_records").add(emiRecipe(null,
                "external.UnknownRecipe", "emi:ingredient", emptyList(), listOf("minecraft:stone")))
        }
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.plan(malformed) }
        val unsortedOutputs = emi().apply {
            putArray("expected_recipe_records").add(emiRecipe("emi:test",
                "dev.emi.emi.recipe.EmiFuelRecipe", "emi:fuel", listOf("minecraft:stone", "minecraft:coal")))
        }
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.plan(unsortedOutputs) }
        export.put("complete", "true")
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.evaluate(p, observations) }
        export.put("complete", true)
        Files.writeString(file, suiteMapper.writeValueAsString(document) + "\n{}")
        export.put("file_bytes", Files.size(file)); export.put("file_sha256", Hashes.sha256(file))
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException::class.java) { EmiIndexDriver.evaluate(p, observations) }
    }

    @Test fun emiAnonymousProjectionIsExplicitNullIdAndDomainBound() {
        val synthetic = emiRecipe(null, "dev.emi.emi.recipe.EmiSyntheticIngredientRecipe",
            "emi:ingredient", emptyList(), listOf("minecraft:granite", "minecraft:stone", "minecraft:stone"))
        val plan = emi().apply {
            putArray("expected_recipe_records").add(synthetic).add(emiRecipe("minecraft:stone",
                "dev.emi.emi.recipe.EmiShapedRecipe", "minecraft:crafting", listOf("minecraft:stone")))
        }
        assertEquals(1, EmiIndexDriver.expandTrials(plan, "origin").size)
        val tampered = plan.deepCopy()
        ((tampered.path("expected_recipe_records")[0]) as ObjectNode).put("projection_sha256", "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.plan(tampered) }
        val fabricated = plan.deepCopy()
        ((fabricated.path("expected_recipe_records")[0]) as ObjectNode).put("recipe_id", "emi:fake_recipe")
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.plan(fabricated) }
        val drift = plan.deepCopy()
        ((drift.path("expected_recipe_records")[0]) as ObjectNode).put("extra_provider_field", true)
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.plan(drift) }
        val reordered = plan.deepCopy()
        ((reordered.path("expected_recipe_records")[0].path("input_alternative_ids")) as
            com.fasterxml.jackson.databind.node.ArrayNode).apply { removeAll(); add("minecraft:stone"); add("minecraft:granite") }
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.plan(reordered) }
    }

    @Test fun inventorySyncUsesDeclaredBehaviorOnlyGrantAndRejectsIncompleteStream() {
        assertEquals(setOf("client.inventory.slot-sync"), InventorySyncDriver.assertionIds)
        val trial = InventorySyncDriver.expandTrials(sync(), "origin").single()
        assertEquals(setOf("minecraft:stone"), trial.provisionedTargets)
        assertEquals(listOf("server", "client", "server", "server", "server", "client"),
            InventorySyncDriver.planTrial(trial).map { it.action.source })
        assertEquals(listOf("player.snapshot", "client.snapshot", "fixture.begin", "wait.ticks",
            "player.snapshot", "client.snapshot"), InventorySyncDriver.planTrial(trial).map { it.action.id })
        assertThrows(IllegalArgumentException::class.java) { InventorySyncDriver.evaluateTrial(trial, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { InventorySyncDriver.plan(sync().apply { put("count", 0) }) }
    }
}
