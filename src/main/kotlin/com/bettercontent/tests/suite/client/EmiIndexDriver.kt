package com.bettercontent.tests.suite.client

import java.security.MessageDigest
import com.fasterxml.jackson.databind.DeserializationFeature
import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.bettercontent.tests.suite.drivers.*
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

private val emiProviderPattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
private val emiEmptyOutputProviders = setOf("dev.emi.emi.recipe.EmiFuelRecipe",
    "dev.emi.emi.api.recipe.EmiInfoRecipe", "dev.emi.emi.recipe.EmiSyntheticIngredientRecipe")
private fun emiRecipeSortKey(row: JsonNode): String = listOf(
    row.path("identity_kind").asText(), row.path("recipe_id").toString(),
    row.path("provider_class").asText(), row.path("category_id").asText(),
    row.path("input_alternative_ids").toString(), row.path("output_ids").toString()
).joinToString("|")

/** Structural format only; caller must separately bind bytes, candidate JAR and independent expected membership. */
internal fun validateEmiPublicDocument(document: JsonNode, emiJarSha256: String,
    indexCount: Int, recipeCount: Int) {
    require(emiJarSha256.matches(shaPattern) && indexCount in 1..100_000 && recipeCount in 1..100_000 &&
        document.isObject && document.fieldNames().asSequence().toSet() ==
        setOf("schema", "emi_jar_sha256", "index_ids", "recipes") &&
        document.path("schema").isTextual && document.path("schema").asText() == "bc.playtest_emi_public_index.v2" &&
        document.path("emi_jar_sha256").isTextual && document.path("emi_jar_sha256").asText() == emiJarSha256)
    val index = document.path("index_ids")
    require(index.isArray && index.size() == indexCount && index.all { it.isTextual &&
        it.asText().length in 3..512 && resourcePattern.matches(it.asText()) } &&
        index.map { it.asText() }.let { it == it.sorted() }) { "EMI public index IDs malformed or not sorted" }
    validateEmiRecipeRecords(document.path("recipes"), recipeCount)
}

private fun validateEmiRecipeRecords(records: JsonNode, count: Int): List<JsonNode> {
    require(count in 1..100_000 && records.isArray && records.size() == count) { "EMI recipe projection absent/oversized" }
    val recipes = records.toList()
    fun ids(row: JsonNode, key: String): List<String> {
        val field = row.path(key)
        require(field.isArray && field.size() <= 100_000 && field.all { it.isTextual &&
            it.asText().length in 3..512 && resourcePattern.matches(it.asText()) }) { "EMI $key invalid" }
        return field.map { it.asText() }.also { require(it == it.sorted()) { "EMI $key not sorted" } }
    }
    require(recipes.all { row ->
        if (!row.isObject || row.fieldNames().asSequence().toSet() != setOf("identity_kind", "recipe_id",
                "provider_class", "category_id", "output_ids", "input_alternative_ids", "projection_sha256") ||
            row.toString().length > 32_768) return@all false
        val kind = row.path("identity_kind"); val id = row.path("recipe_id")
        val provider = row.path("provider_class"); val category = row.path("category_id")
        if (!kind.isTextual || !provider.isTextual || !emiProviderPattern.matches(provider.asText()) ||
            !category.isTextual || !resourcePattern.matches(category.asText())) return@all false
        val outputs = ids(row, "output_ids"); val inputs = ids(row, "input_alternative_ids")
        if (outputs.isEmpty() && provider.asText() !in emiEmptyOutputProviders) return@all false
        when (kind.asText()) {
            "public_recipe_id" -> id.isTextual && id.asText().length <= 512 && resourcePattern.matches(id.asText()) &&
                inputs.isEmpty() && row.path("projection_sha256").isNull
            "anonymous_public_projection" -> id.isNull && provider.asText() ==
                "dev.emi.emi.recipe.EmiSyntheticIngredientRecipe" && category.asText() == "emi:ingredient" &&
                inputs.isNotEmpty() && row.path("projection_sha256").isTextual &&
                row.path("projection_sha256").asText() == MessageDigest.getInstance("SHA-256")
                    .digest(suiteMapper.writeValueAsBytes(listOf("bc.playtest_emi_public_projection.v1",
                        provider.asText(), category.asText(), inputs, outputs)))
                    .joinToString("") { "%02x".format(it) }
            else -> false
        }
    } && recipes.map(::emiRecipeSortKey).let { it == it.sorted() }) {
        "EMI recipe records malformed, unsupported or unsorted"
    }
    return recipes
}

/** Narrow public-API index comparison, NOT usage, tags, GUI navigation or natural acquisition. */
internal object EmiIndexDriver : TrialCaseDriver {
    override val caseId = "client.emi.public-index"
    override val assertionIds = setOf("client.emi.index-roster")
    private data class Plan(val player: String, val dimension: String, val emiSha: String,
        val index: List<String>, val recipes: List<JsonNode>)

    private fun parse(node: JsonNode): Plan {
        require(node.isObject && node.fieldNames().asSequence().toSet() ==
            setOf("player_uuid", "dimension", "emi_jar_sha256", "expected_index_ids", "expected_recipe_records")) {
            "reviewed EMI roster parameters missing or expanded"
        }
        val player = node.path("player_uuid").asText(); val dimension = node.path("dimension").asText()
        require(runCatching { UUID.fromString(player).toString() == player }.getOrDefault(false) &&
            resourcePattern.matches(dimension) && node.path("emi_jar_sha256").asText().matches(shaPattern)) {
            "EMI expected player/dimension/selected candidate JAR hash invalid"
        }
        fun roster(key: String): List<String> {
            val list = node.path(key)
            require(list.isArray && list.size() in 1..100_000)
            return list.map { row -> require(row.isTextual && row.asText().length in 3..512); row.asText() }
                .also { require(it == it.sorted()) { "EMI $key must be an independently reviewed sorted multiset" } }
        }
        val index = roster("expected_index_ids")
        require(index.all { id -> resourcePattern.matches(id) }) { "EMI index resource IDs not canonical" }
        val records = node.path("expected_recipe_records")
        val recipes = validateEmiRecipeRecords(records, records.size())
        return Plan(player, dimension, node.path("emi_jar_sha256").asText(), index, recipes)
    }

    override fun expandTrials(parameters: JsonNode, inputCheckpointId: String): List<TrialRequest> {
        parse(parameters)
        require(inputCheckpointId.matches(Regex("[a-z0-9][a-z0-9_.-]*")))
        return listOf(TrialRequest(caseId, "index_roster", "public-index", inputCheckpointId,
            parameters.deepCopy(), assertionIds, emptySet()))
    }

    override fun plan(parameters: JsonNode): List<DriverStep> {
        parse(parameters)
        return listOf(
            DriverStep("before", DriverAction("client.snapshot", suiteMapper.createObjectNode(), 200, "client")),
            DriverStep("index", DriverAction("client.emi_index", suiteMapper.createObjectNode(), 1200, "client")),
            DriverStep("after", DriverAction("client.snapshot", suiteMapper.createObjectNode(), 200, "client"))
        )
    }
    override fun planTrial(request: TrialRequest): List<DriverStep> {
        require(request.caseId == caseId && request.trialId == "index_roster" && request.targetId == "public-index" &&
            request.assertionIds == assertionIds && request.provisionedTargets.isEmpty())
        return plan(request.parameters)
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val reviewed = parse(parameters)
        require(validateTerminalSequence(plan(parameters), observations) == null) { "EMI native client trial incomplete" }
        val before = observations[0].payload; val exported = observations[1].payload; val after = observations[2].payload
        require(listOf(before, exported, after).all { it.path("player_uuid").asText() == reviewed.player &&
            it.path("dimension").asText() == reviewed.dimension &&
            it.path("selected_slot").isIntegralNumber && it.path("selected_slot").canConvertToInt() &&
            it.path("selected_slot").asInt() in 0..8 && it.path("inventory").isArray } &&
            before.path("selected_slot") == after.path("selected_slot") &&
            before.path("inventory") == after.path("inventory") &&
            exported.path("complete").isBoolean && exported.path("complete").asBoolean() &&
            exported.path("emi_jar_sha256").asText() == reviewed.emiSha &&
            exported.path("source").asText() == "pinned dev.emi.emi.api.EmiApi:getIndexStacks/getRecipeManager") {
            "EMI public index is not bound to one stable joined client and pinned JAR"
        }
        require(exported.path("file").isTextual && exported.path("file_sha256").isTextual &&
            exported.path("file_bytes").isIntegralNumber && exported.path("file_bytes").canConvertToLong() &&
            listOf("index_count", "recipe_count").all { exported.path(it).isIntegralNumber && exported.path(it).canConvertToInt() })
        val path = Path.of(exported.path("file").asText())
        require(path.isAbsolute && Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
            Files.size(path) in 1..32_000_000 && exported.path("file_sha256").asText().matches(shaPattern)) {
            "EMI public export unbound or replaced"
        }
        // Hash and parse the same bounded capture, not a second live-process-controlled file read.
        val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(32_000_001) }
        require(bytes.size in 1..32_000_000 && exported.path("file_bytes").asLong() == bytes.size.toLong() &&
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } ==
                exported.path("file_sha256").asText()) { "EMI public export bytes differ from terminal binding" }
        val document: JsonNode = suiteMapper.copy()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes)
        validateEmiPublicDocument(document, reviewed.emiSha,
            exported.path("index_count").asInt(), exported.path("recipe_count").asInt())
        require(document.path("index_ids").map { it.asText() } == reviewed.index &&
            document.path("recipes").toList() == reviewed.recipes &&
            exported.path("index_count").asInt() == reviewed.index.size &&
            exported.path("recipe_count").asInt() == reviewed.recipes.size) {
            "actual EMI public index/recipe projections differ from independently reviewed multiset"
        }
        return MeasurementVerdict("passed", "pinned actual-client EMI public index and typed recipe projections (synthetic anonymous ID-only alternatives, not NBT or amounts); no tags, usage navigation, GUI approval or acquisition")
    }
    override fun evaluateTrial(request: TrialRequest, observations: List<DriverObservation>): MeasurementVerdict {
        planTrial(request)
        return evaluate(request.parameters, observations)
    }
    override fun aggregate(parameters: JsonNode, requests: List<TrialRequest>, receipts: List<TrialReceipt>): MeasurementVerdict {
        require(requests.size == 1 && receipts.size == 1 && requests == expandTrials(parameters, requests.single().inputCheckpointId))
        val receipt = receipts.single(); val request = requests.single()
        require(receipt.trialId == request.trialId && receipt.targetId == request.targetId &&
            receipt.inputCheckpointId == request.inputCheckpointId && receipt.state == "passed" &&
            receipt.epochId.isNotBlank() && receipt.inputCheckpointVerified && receipt.cleanupComplete)
        return evaluateTrial(request, receipt.observations)
    }
}
