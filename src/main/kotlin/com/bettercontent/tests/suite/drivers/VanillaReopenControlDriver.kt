package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Source-only first read-only action after A's separately sealed checkpoint and genuine fresh login.
 * UNREGISTERED until A's Store+fresh-process+saved-playerdata qualifier independently joins the raw terminal.
 * Native live mode is NOT independently saved playerGameType or persistence proof. */
internal object VanillaReopenControlDriver : CaseDriver {
    override val caseId = "control.persistence.vanilla-reopen"
    // clientRequired remains true: A checks actual fresh client/session, but none may precede this first read-only snapshot.
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val hash = Regex("[0-9a-f]{64}")
    private fun dimension(parameters: JsonNode): String {
        require(parameters.isObject && parameters.fieldNames().asSequence().toSet() == setOf("dimension")) {
            "only independently reviewed reopen dimension belongs in a driver target"
        }
        val value = parameters.path("dimension")
        require(value.isTextual && resource.matches(value.asText())) { "invalid reviewed reopen dimension" }
        return value.asText()
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        dimension(parameters)
        return listOf(DriverStep("first-readonly-reopened-player", DriverAction("player.snapshot",
            suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid"))))
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val expectedDimension = dimension(parameters)
        require(observations.size == 1 && observations.single().driver == "player.snapshot" &&
            observations.single().source == "server" && observations.single().kind == "completed") {
            "first action must be only actual read-only server snapshot"
        }
        val row = observations.single().payload
        val actor = row.path("uuid"); val mode = row.path("mode"); val liveDimension = row.path("dimension")
        require(actor.isTextual && runCatching { UUID.fromString(actor.asText()).toString() == actor.asText() }.getOrDefault(false) &&
            mode.isTextual && mode.asText() in setOf("survival", "creative", "adventure", "spectator") &&
            liveDimension.isTextual && liveDimension.asText() == expectedDimension &&
            row.path("server_tick").isIntegralNumber && row.path("server_tick").asLong() >= 0 &&
            row.path("selected_slot").isIntegralNumber && row.path("selected_slot").asInt() in 0..8 &&
            row.path("inventory").isArray && row.path("inventory").size() <= 41) {
            "actual actor/dimension/live mode/tick or occupied inventory missing"
        }
        val wrapper = row.path("inventory_slots_v1")
        require(wrapper.isObject && wrapper.fieldNames().asSequence().toSet() ==
            setOf("schema", "source", "slots", "external_capabilities_included", "sealed_playerdata_proven_by_native") &&
            wrapper.path("schema").asText() == "bc.playtest_vanilla_inventory_slots.v1" &&
            wrapper.path("source").asText() == "actual_server_player_vanilla_inventory_0_40" &&
            wrapper.path("external_capabilities_included").isBoolean &&
            !wrapper.path("external_capabilities_included").asBoolean() &&
            wrapper.path("sealed_playerdata_proven_by_native").isBoolean &&
            !wrapper.path("sealed_playerdata_proven_by_native").asBoolean() &&
            wrapper.path("slots").isArray && wrapper.path("slots").size() == 41) {
            "actual explicit live vanilla 41-slot source missing (occupied-only roster insufficient)"
        }
        val slots = wrapper.path("slots").toList()
        val parsed = slots.mapIndexed { index, entry ->
            require(entry.isObject && entry.fieldNames().asSequence().toSet() ==
                setOf("slot", "empty", "item", "count", "stack_canonical_algorithm", "stack_canonical_sha256") &&
                entry.path("slot").isIntegralNumber && entry.path("slot").asInt() == index &&
                entry.path("empty").isBoolean && entry.path("count").isIntegralNumber &&
                entry.path("count").canConvertToInt()) { "missing or unordered explicit vanilla slot" }
            if (entry.path("empty").asBoolean()) {
                require(entry.path("item").isNull && entry.path("count").asInt() == 0 &&
                    entry.path("stack_canonical_algorithm").isNull && entry.path("stack_canonical_sha256").isNull) {
                    "empty slot must be explicit null without fabricated air/hash"
                }
                null
            } else {
                val id = entry.path("item").asText(""); val count = entry.path("count").asInt(-1)
                val canonical = entry.path("stack_canonical_sha256").asText("")
                require(resource.matches(id) && id != "minecraft:air" && count in 1..64 &&
                    entry.path("stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
                    hash.matches(canonical)) { "untyped nonempty vanilla slot" }
                Triple(id, count, canonical)
            }
        }
        val occupied = row.path("inventory").map { entry ->
            val slot = entry.path("slot")
            require(slot.isIntegralNumber && slot.asInt() in 0..40 && entry.path("item").isTextual &&
                entry.path("count").isIntegralNumber && entry.path("stack_canonical_sha256").isTextual &&
                entry.path("stack_canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1") {
                "legacy occupied view cannot be checked against all vanilla slots"
            }
            slot.asInt() to Triple(entry.path("item").asText(), entry.path("count").asInt(),
                entry.path("stack_canonical_sha256").asText())
        }
        require(occupied.map { it.first }.distinct().size == occupied.size &&
            occupied.toMap() == parsed.mapIndexedNotNull { index, stack -> stack?.let { index to it } }.toMap()) {
            "full41 and occupied-only native views disagree"
        }
        val selected = parsed[row.path("selected_slot").asInt()]
        require(row.path("held_item").asText() == (selected?.first ?: "minecraft:air") &&
            row.path("held_count").asInt(-1) == (selected?.second ?: 0) &&
            row.path("held_stack_canonical_sha256").asText() == (selected?.third ?: "empty")) {
            "selected live stack contradicts same-player full41 snapshot"
        }
        return MeasurementVerdict("inconclusive",
            "one authentic live read-only 41-slot/mode view; A must compare prior Store-sealed saved full41/playerGameType and fresh-process ancestry",
            mapOf("actual_actor_uuid" to actor.asText(), "live_dimension" to liveDimension.asText(),
                "live_mode_not_saved_mode" to mode.asText(), "server_tick" to row.path("server_tick").asLong(),
                "live_vanilla_slots_v1" to slots.map { it.deepCopy<JsonNode>() }))
    }
}
