package com.bettercontent.tests.suite.client

import com.fasterxml.jackson.databind.JsonNode
import kotlin.math.abs

/** Strict independent read-only subset; never calls Better Spawns' resolver or predicate. */
internal object OnboardingTerrainOracle {
    private val support = setOf("minecraft:stone", "minecraft:dirt", "minecraft:grass_block",
        "minecraft:deepslate", "minecraft:bedrock")
    private val noSupport = setOf("minecraft:air", "minecraft:cave_air", "minecraft:void_air")
    private val resource = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private fun integer(row: JsonNode, key: String): Int? = row.path(key).takeIf {
        it.isIntegralNumber && it.canConvertToInt()
    }?.asInt()
    private fun flag(row: JsonNode, key: String): Boolean = row.path(key).isBoolean && row.path(key).asBoolean()
    private fun propertiesKnown(block: JsonNode, id: String): Boolean {
        val properties = block.path("properties")
        if (!properties.isObject) return false
        val fields = properties.fieldNames().asSequence().toSet()
        return when (id) {
            "minecraft:grass_block" -> fields == setOf("snowy") && properties.path("snowy").asText() in setOf("true", "false")
            "minecraft:deepslate" -> fields == setOf("axis") && properties.path("axis").asText() in setOf("x", "y", "z")
            else -> fields.isEmpty()
        }
    }
    /** Null means unsupported/malformed, not "no safe base"; inspect every preceding row before first support. */
    fun firstSupportingFeet(request: JsonNode, terrain: JsonNode): Int? {
        val min = integer(terrain, "min_build_height") ?: return null
        val max = integer(terrain, "max_build_height") ?: return null
        val requestedY = integer(request, "y") ?: return null
        if (max.toLong() - min.toLong() !in 3..384 ||
            requestedY !in -2048..2048 || !terrain.isObject ||
            !terrain.path("dimension").isTextual || terrain.path("dimension") != request.path("dimension") ||
            integer(terrain, "x") != integer(request, "x") || integer(terrain, "z") != integer(request, "z") ||
            !flag(terrain, "loaded_only_no_chunk_forcing") || !flag(terrain, "column_complete") ||
            terrain.path("material_classification").asText() != "none_native_raw_only") return null
        val top = requestedY.coerceIn(min + 1, max - 2).coerceAtMost(max - 3)
        if (integer(terrain, "scan_top_y") != top || top < min || top.toLong() - min.toLong() + 1 > 384) return null
        val rows = terrain.path("columns")
        if (!rows.isArray || rows.size().toLong() != max.toLong() - min.toLong() || rows.size() !in 1..384) return null
        // B captures every build Y, not only the scan prefix: reject missing upper rows too.
        for (index in 0 until rows.size()) {
            val row = rows[index]
            if (integer(row, "y") != min + index || !row.path("block_id").isTextual ||
                !resource.matches(row.path("block_id").asText()) || !row.path("properties").isObject) return null
        }
        for (baseY in top downTo min) {
            val row = rows[baseY - min]
            val id = row.path("block_id").asText()
            if (!propertiesKnown(row, id)) return null
            if (id in support) return baseY + 1
            if (id !in noSupport) return null // Unknown shape/provider cannot be assumed non-supporting.
        }
        return null
    }
    /** B's narrow native chain plus independent bounded movement/terrain/marker and actor checks. */
    fun matchesReviewedSite(bounds: JsonNode, arm: JsonNode, receipt: JsonNode, clientKey: JsonNode): Boolean {
        if (!bounds.isObject || bounds.fieldNames().asSequence().toSet() !=
            setOf("dimension", "x_min", "x_max", "y_min", "y_max", "z_min", "z_max") ||
            !bounds.path("dimension").isTextual || !resource.matches(bounds.path("dimension").asText()) ||
            !clientKey.isObject || !flag(clientKey, "spectator") || !flag(clientKey, "modifier_down") ||
            clientKey.path("dimension") != bounds.path("dimension") ||
            !receipt.isObject || !flag(receipt, "narrow_event_chain_complete") ||
            receipt.path("request_bounds") != bounds || arm.path("request_bounds") != bounds ||
            receipt.path("schema").asText() != "bc.playtest_passive_onboarding.v1") return false
        val lo = listOf("x_min", "y_min", "z_min").map { integer(bounds, it) ?: return false }
        val hi = listOf("x_max", "y_max", "z_max").map { integer(bounds, it) ?: return false }
        if (lo.zip(hi).any { (a, b) -> a > b } || hi[0].toLong() - lo[0] !in 0..2 ||
            hi[1].toLong() - lo[1] !in 0..3 || hi[2].toLong() - lo[2] !in 0..2) return false
        fun tick(key: String): Long? = receipt.path(key).takeIf { it.isIntegralNumber && it.canConvertToLong() && it.asLong() >= 0 }?.asLong()
        val armTick = tick("arm_tick") ?: return false
        val packetTick = tick("packet_handling_tick") ?: return false
        val eventTick = tick("finalization_event_tick") ?: return false
        val handlerTick = tick("packet_handler_return_tick") ?: return false
        val finalTick = tick("final_applied_state_tick") ?: return false
        if (armTick > packetTick || packetTick > eventTick || eventTick > handlerTick || handlerTick > finalTick ||
            listOf("packet_count", "finalization_event_count", "packet_handler_return_count")
                .any { integer(receipt, it) != 1 } ||
            receipt.path("observer_id") != arm.path("observer_id") ||
            receipt.path("actor_uuid") != arm.path("actor_uuid") ||
            !flag(receipt, "final_onboarding_complete") ||
            receipt.path("actual_kit").asText() != "spawn_only") return false
        val dims = listOf("x", "y", "z")
        val request = receipt.path("requested_site")
        val actorAtArm = receipt.path("actor_at_arm")
        val actorAtRequest = receipt.path("actor_at_request")
        val actual = receipt.path("actual_committed_site")
        val finalActor = receipt.path("actual_final_actor")
        val terrain = receipt.path("terrain_pre_commit")
        if (arm.path("actor_at_arm") != actorAtArm ||
            listOf(request, actorAtArm, actorAtRequest, actual, finalActor, clientKey, terrain).any {
                !it.isObject || it.path("dimension") != bounds.path("dimension")
            }) return false
        val requested = dims.map { integer(request, it) ?: return false }
        val actor = dims.map { integer(actorAtRequest, "block_$it") ?: return false }
        val client = dims.map { integer(clientKey, "block_$it") ?: return false }
        val armActor = dims.map { integer(actorAtArm, "block_$it") ?: return false }
        if (listOf(requested, actor, client, armActor).any { row -> row.indices.any { i -> row[i] !in lo[i]..hi[i] } } ||
            requested.indices.any { i -> abs(requested[i].toLong() - actor[i].toLong()) > 1 ||
                abs(requested[i].toLong() - client[i].toLong()) > (if (i == 1) 2L else 1L) } ||
            !clientKey.path("native_tick").isIntegralNumber || !clientKey.path("native_tick").canConvertToLong() ||
            clientKey.path("native_tick").asLong() < 0) return false
        val expectedFeet = firstSupportingFeet(request, terrain) ?: return false
        if (integer(actual, "x") != requested[0] || integer(actual, "y") != expectedFeet ||
            integer(actual, "z") != requested[2]) return false
        for (key in listOf("persisted_site_at_event", "persisted_site", "vanilla_respawn_site"))
            if (receipt.path(key) != actual) return false
        if (integer(finalActor, "block_x") != requested[0] || integer(finalActor, "block_y") != expectedFeet ||
            integer(finalActor, "block_z") != requested[2] || finalActor.path("mode").asText() != "survival" ||
            dims.any { axis -> !finalActor.path(axis).isNumber || !finalActor.path(axis).asDouble().isFinite() }) return false
        val after = receipt.path("site_after")
        if (!flag(after, "loaded_only_no_chunk_forcing") || !flag(after, "marker_base_observed") ||
            !flag(after, "feet_head_air_observed")) return false
        val base = after.path("base"); val feet = after.path("feet"); val head = after.path("head")
        if (integer(base, "y") != expectedFeet - 1 || integer(feet, "y") != expectedFeet ||
            integer(head, "y") != expectedFeet + 1 ||
            base.path("block_id").asText() != "better_spawns:respawn_marker" ||
            feet.path("block_id").asText() != "minecraft:air" || head.path("block_id").asText() != "minecraft:air" ||
            listOf(base, feet, head).any { it.path("properties").isObject.not() || it.path("properties").size() != 0 }) return false
        return true // Disk/reopen persistence and provider artifact pins are A-owned, not inferred here.
    }
}
