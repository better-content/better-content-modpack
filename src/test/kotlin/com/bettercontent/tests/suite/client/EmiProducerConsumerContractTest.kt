package com.bettercontent.tests.suite.client

import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.DriverObservation
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.DeserializationFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Runs only through the explicit test-phase native-producer -> Kotlin-consumer task, never compile. */
@Tag("emi-producer-consumer")
class EmiProducerConsumerContractTest {
    @Test fun productionProjectionBytesReachTheActualSharedConsumer() {
        val name = requireNotNull(System.getProperty("bc.emi.contract.output")).takeIf(String::isNotBlank)
            ?: error("explicit native production EMI fixture path missing")
        val file = Path.of(name).toAbsolutePath().normalize()
        require(file.fileName.toString() == "emi-public-000000000001.json" &&
            file.parent.startsWith(Path.of(System.getProperty("bc.repo.root")).resolve("test-support/forge/build/emi-contract")) &&
            Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) { "foreign/linked EMI fixture" }
        val envelopeFile = file.resolveSibling(file.fileName.toString() + ".envelope.json")
        require(Files.isRegularFile(envelopeFile, NOFOLLOW_LINKS) && !Files.isSymbolicLink(envelopeFile) &&
            Files.size(envelopeFile) in 1..4096) { "producer envelope missing/oversized" }
        val envelope: JsonNode = suiteMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .readTree(Files.readAllBytes(envelopeFile))
        assertEquals("bc.playtest_emi_contract_envelope.v1", envelope.path("schema").asText())
        assertEquals("native_test_production_public_projection_seam_not_live_client", envelope.path("source").asText())
        assertEquals(file.toString(), envelope.path("file").asText())
        assertEquals("a".repeat(64), envelope.path("emi_jar_sha256").asText())
        val payload = suiteMapper.createObjectNode().apply {
            put("file", envelope.path("file").asText()); put("file_sha256", envelope.path("sha256").asText())
            put("file_bytes", envelope.path("bytes").asLong())
            put("index_count", envelope.path("index_count").asInt())
            put("recipe_count", envelope.path("recipe_count").asInt())
        }
        val doc = verifyCapturedEmiPublicIndex(file.parent, 1, payload, envelope.path("emi_jar_sha256").asText())
        assertEquals(listOf("minecraft:stone", "minecraft:stone"), doc.path("index_ids").map { it.asText() })
        assertEquals(4, doc.path("recipes").size())
        val recipes = doc.path("recipes")
        val fuel = recipes.filter { it.path("provider_class").asText() == "dev.emi.emi.recipe.EmiFuelRecipe" }
        assertEquals(2, fuel.size)
        assertTrue(fuel.all { it.path("recipe_id").asText() == "emi:fuel_coal" && it.path("output_ids").isEmpty })
        val anonymous = recipes.single { it.path("identity_kind").asText() == "anonymous_public_projection" }
        assertTrue(anonymous.path("recipe_id").isNull && anonymous.path("projection_sha256").asText().matches(Regex("[0-9a-f]{64}")))
        assertEquals(listOf("minecraft:granite", "minecraft:stone"), anonymous.path("input_alternative_ids").map { it.asText() })
        assertEquals(1, recipes.count { it.path("provider_class").asText() == "dev.emi.emi.recipe.EmiShapedRecipe" })
        // The independently authored expected roster (not a copy of the producer document) must
        // also traverse the actual scenario driver and compare the same captured bytes.
        fun recipe(id: String?, provider: String, category: String, inputs: List<String>, outputs: List<String>): JsonNode {
            val projection = if (id == null) java.security.MessageDigest.getInstance("SHA-256")
                .digest(suiteMapper.writeValueAsBytes(listOf("bc.playtest_emi_public_projection.v1", provider, category,
                    inputs, outputs))).joinToString("") { "%02x".format(it) } else null
            return suiteMapper.valueToTree(mapOf("identity_kind" to if (id == null) "anonymous_public_projection" else "public_recipe_id",
                "recipe_id" to id, "provider_class" to provider, "category_id" to category,
                "output_ids" to outputs, "input_alternative_ids" to inputs, "projection_sha256" to projection))
        }
        val fuelRecord = recipe("emi:fuel_coal", "dev.emi.emi.recipe.EmiFuelRecipe", "emi:fuel", emptyList(), emptyList())
        val expectedRecords = listOf(fuelRecord, fuelRecord,
            recipe("minecraft:stone", "dev.emi.emi.recipe.EmiShapedRecipe", "minecraft:crafting", emptyList(), listOf("minecraft:stone")),
            recipe(null, "dev.emi.emi.recipe.EmiSyntheticIngredientRecipe", "emi:ingredient",
                listOf("minecraft:granite", "minecraft:stone"), emptyList())).sortedBy { row ->
            listOf(row.path("identity_kind").asText(), row.path("recipe_id").toString(),
                row.path("provider_class").asText(), row.path("category_id").asText(),
                row.path("input_alternative_ids").toString(), row.path("output_ids").toString()).joinToString("|")
        }
        val reviewed = suiteMapper.createObjectNode().apply {
            put("player_uuid", "00000000-0000-0000-0000-000000000001")
            put("dimension", "minecraft:overworld"); put("emi_jar_sha256", envelope.path("emi_jar_sha256").asText())
            putArray("expected_index_ids").add("minecraft:stone").add("minecraft:stone")
            putArray("expected_recipe_records").apply { expectedRecords.forEach { record -> add(record) } }
        }
        fun snapshot() = suiteMapper.createObjectNode().apply {
            put("player_uuid", reviewed.path("player_uuid").asText()); put("dimension", "minecraft:overworld")
            put("selected_slot", 0); putArray("inventory")
        }
        val export = snapshot().apply {
            set<JsonNode>("file", payload.path("file")); set<JsonNode>("file_sha256", payload.path("file_sha256"))
            set<JsonNode>("file_bytes", payload.path("file_bytes")); set<JsonNode>("index_count", payload.path("index_count"))
            set<JsonNode>("recipe_count", payload.path("recipe_count"))
            put("complete", true); put("emi_jar_sha256", envelope.path("emi_jar_sha256").asText())
            put("source", "pinned dev.emi.emi.api.EmiApi:getIndexStacks/getRecipeManager")
        }
        val observations = EmiIndexDriver.plan(reviewed).zip(listOf(snapshot(), export, snapshot())).map { (step, state) ->
            DriverObservation(step.name, step.action.id, "client", "completed", state)
        }
        assertEquals("passed", EmiIndexDriver.evaluate(reviewed, observations).state)
        val mismatched = reviewed.deepCopy().apply {
            (path("expected_index_ids") as com.fasterxml.jackson.databind.node.ArrayNode).remove(1)
        }
        assertThrows(IllegalArgumentException::class.java) { EmiIndexDriver.evaluate(mismatched, observations) }
    }
}
