package com.bettercontent.tests.suite.drivers

import com.bettercontent.tests.suite.MeasurementVerdict
import com.bettercontent.tests.suite.client.INPUT_RUNTIME_MARKER
import com.bettercontent.tests.suite.client.validateAuthenticWalkInput
import com.bettercontent.tests.suite.jsonFingerprint
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

/** Source-only control. No reviewed route, human-origin or natural acquisition is supplied by this generic driver.
 * Stops at the first deviation; never searches for a replacement route, rotates, teleports, jumps or retries.
 */
internal object WalkRouteControlDriver : CaseDriver {
    override val caseId = "control.travel.walk-route"
    private data class Cell(val x: Int, val y: Int, val z: Int)
    private data class Leg(val target: Cell, val waitTicks: Int, val holdId: String)
    private data class Route(val actor: String, val dimension: String, val profile: String, val routeId: String,
        val routeSha: String, val start: Cell, val legs: List<Leg>, val cells: List<Cell>, val yaw: Double)
    private fun parse(input: JsonNode): Route {
        require(input.isObject && input.fieldNames().asSequence().toSet() == setOf("player_uuid", "dimension",
            "input_profile", "route_id", "route_sha256", "route")) { "exact controller-reviewed route fields required" }
        fun text(node: JsonNode, key: String): String = node.path(key).also { require(it.isTextual) }.asText()
        fun coord(node: JsonNode, key: String, range: IntRange): Int = node.path(key).also {
            require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in range) { "invalid route $key" }
        }.asInt()
        fun cell(node: JsonNode): Cell {
            require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("x", "y", "z"))
            return Cell(coord(node, "x", -29_999_990..29_999_990), coord(node, "y", -2047..2047),
                coord(node, "z", -29_999_990..29_999_990))
        }
        val actor = text(input, "player_uuid"); val dimension = text(input, "dimension")
        val profile = text(input, "input_profile"); val routeId = text(input, "route_id")
        val routeSha = text(input, "route_sha256"); val raw = input.path("route")
        require(runCatching { UUID.fromString(actor).toString() == actor }.getOrDefault(false) &&
            Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(dimension) &&
            profile in setOf("1280x720@2", "1920x1080@3") &&
            Regex("[A-Za-z0-9._-]{1,64}").matches(routeId) &&
            Regex("[0-9a-f]{64}").matches(routeSha) && jsonFingerprint(raw) == routeSha &&
            raw.isObject && raw.fieldNames().asSequence().toSet() == setOf("start", "legs")) {
            "route identity, hash, client profile or dimension unreviewed"
        }
        val start = cell(raw.path("start")); val entries = raw.path("legs")
        require(entries.isArray && entries.size() in 1..4) { "route requires one to four fixed waypoints" }
        val legs = entries.map { row ->
            require(row.isObject && row.fieldNames().asSequence().toSet() == setOf("target", "wait_ticks", "hold_id"))
            val ticks = coord(row, "wait_ticks", 4..30)
            Leg(cell(row.path("target")), ticks, text(row, "hold_id"))
        }
        require(legs.map { it.holdId }.distinct().size == legs.size && legs.all {
            Regex("[A-Za-z0-9._-]{1,64}").matches(it.holdId)
        } && legs.sumOf { it.waitTicks + 20 } <= 200) { "unbounded movement IDs or time budget" }
        var previous = start; var dx = 0; var dz = 0
        val cells = linkedSetOf(start)
        for (leg in legs) {
            val stepX = leg.target.x - previous.x; val stepZ = leg.target.z - previous.z
            require(leg.target.y == start.y && ((stepX == 0 && abs(stepZ) in 1..2) ||
                (stepZ == 0 && abs(stepX) in 1..2))) { "only same-height cardinal flat walking is supported" }
            val sx = stepX.compareTo(0); val sz = stepZ.compareTo(0)
            if (dx == 0 && dz == 0) { dx = sx; dz = sz }
            require(sx == dx && sz == dz) { "a route requiring rotation, backtracking or search is unsupported" }
            for (i in 1..abs(stepX + stepZ)) cells += Cell(previous.x + i * sx, start.y, previous.z + i * sz)
            previous = leg.target
        }
        require(cells.size <= 9)
        return Route(actor, dimension, profile, routeId, routeSha, start, legs, cells.toList(),
            Math.toDegrees(atan2(-dx.toDouble(), dz.toDouble())))
    }
    private fun actor(t: Route) = suiteMapper.createObjectNode().put("player_uuid", t.actor)
    private fun block(t: Route, c: Cell, y: Int) = suiteMapper.createObjectNode()
        .put("dimension", t.dimension).put("x", c.x).put("y", y).put("z", c.z)
    private fun cells(legStart: Cell, end: Cell): List<Cell> {
        val sx = (end.x - legStart.x).compareTo(0); val sz = (end.z - legStart.z).compareTo(0)
        return (0..abs(end.x - legStart.x + end.z - legStart.z)).map { i ->
            Cell(legStart.x + sx * i, legStart.y, legStart.z + sz * i)
        }
    }
    private fun terrain(prefix: String, t: Route, sites: List<Cell>) = sites.flatMap { cell ->
        listOf(-1, 0, 1).map { offset -> DriverStep("$prefix.${cell.x}.${cell.y}.${cell.z}.$offset",
            DriverAction("block.snapshot", block(t, cell, cell.y + offset), 80)) }
    }
    override fun plan(parameters: JsonNode): List<DriverStep> {
        val t = parse(parameters)
        val plan = terrain("pre", t, t.cells).toMutableList()
        var previous = t.start
        for ((index, leg) in t.legs.withIndex()) {
            val label = "leg$index"; val maxTicks = leg.waitTicks + 20
            val shared = suiteMapper.createObjectNode().put("player_uuid", "@observed-player-uuid")
                .put("dimension", t.dimension).put("hold_id", leg.holdId)
                .put("input_runtime_sha256", INPUT_RUNTIME_MARKER)
            plan += DriverStep("$label.before", DriverAction("player.snapshot", actor(t)))
            plan += DriverStep("$label.hold", DriverAction("client.input_hold", shared.deepCopy().apply {
                put("kind", "walk_forward"); put("profile", t.profile); put("max_ticks", maxTicks)
            }, maxTicks + 20, "client"))
            plan += DriverStep("$label.wait", DriverAction("wait.ticks",
                suiteMapper.createObjectNode().put("ticks", leg.waitTicks), leg.waitTicks + 10))
            plan += DriverStep("$label.release", DriverAction("client.input_release", shared.deepCopy(), 80, "client"))
            plan += DriverStep("$label.status", DriverAction("client.input_status", shared.deepCopy(), 80, "client"))
            plan += DriverStep("$label.after", DriverAction("player.snapshot", actor(t)))
            plan += terrain("$label.post", t, cells(previous, leg.target))
            previous = leg.target
        }
        return plan
    }
    private fun number(row: JsonNode, key: String): Double? = row.path(key).takeIf { it.isNumber }?.asDouble()?.takeIf { it.isFinite() }
    private fun safeActor(row: JsonNode, t: Route, site: Cell): Boolean {
        val x = number(row, "x") ?: return false; val y = number(row, "y") ?: return false
        val z = number(row, "z") ?: return false; val yaw = number(row, "yaw") ?: return false
        val pitch = number(row, "pitch") ?: return false; val fall = number(row, "fall_distance") ?: return false
        return row.path("uuid").asText() == t.actor && row.path("dimension").asText() == t.dimension &&
            row.path("mode").asText() == "survival" &&
            row.path("on_ground").isBoolean && row.path("on_ground").asBoolean() &&
            listOf("swimming", "passenger", "sprinting").all { row.path(it).isBoolean && !row.path(it).asBoolean() } &&
            abs(fall) <= 0.05 && pitch in -90.0..90.0 &&
            abs(Math.IEEEremainder(yaw - t.yaw, 360.0)) <= 5.0 &&
            hypot(x - site.x - 0.5, z - site.z - 0.5) <= 0.65 && abs(y - site.y) <= 0.25 &&
            row.path("server_tick").isIntegralNumber && row.path("server_tick").asLong(-1) >= 0 &&
            row.path("server_monotonic_nanos").isIntegralNumber && row.path("server_monotonic_nanos").asLong(-1) > 0
    }
    private fun support(state: JsonNode): Boolean {
        if (!state.isObject || state.fieldNames().asSequence().toSet() != setOf("id", "properties") ||
            !state.path("id").isTextual || !state.path("properties").isObject) return false
        val properties = state.path("properties")
        return when (state.path("id").asText()) {
            "minecraft:stone", "minecraft:dirt", "minecraft:bedrock" -> properties.size() == 0
            "minecraft:grass_block" -> properties.fieldNames().asSequence().toSet() == setOf("snowy") &&
                properties.path("snowy").asText() in setOf("true", "false")
            "minecraft:deepslate" -> properties.fieldNames().asSequence().toSet() == setOf("axis") &&
                properties.path("axis").asText() in setOf("x", "y", "z")
            else -> false // Unknown provider, stairs, slabs, fluids, hazards and modded blocks are unsupported.
        }
    }
    private fun terrainOk(rows: List<DriverObservation>, t: Route, sites: List<Cell>): Boolean {
        if (rows.size != sites.size * 3) return false
        return sites.flatMap { cell -> listOf(-1, 0, 1).map { offset -> cell to offset } }
            .zip(rows).all { (coordinate, observation) ->
                val (cell, offset) = coordinate; val raw = observation.payload
                raw.path("dimension").asText() == t.dimension &&
                    listOf("x", "y", "z").zip(listOf(cell.x, cell.y + offset, cell.z))
                        .all { (key, value) -> raw.path(key).isIntegralNumber && raw.path(key).asInt() == value } &&
                    (if (offset == -1) support(raw.path("block_state")) else
                        raw.path("block_state").path("id").asText() == "minecraft:air" &&
                            raw.path("block_state").path("properties").isObject &&
                            raw.path("block_state").path("properties").size() == 0)
            }
    }
    override fun evaluate(parameters: JsonNode, observations: List<DriverObservation>): MeasurementVerdict {
        val t = parse(parameters); val plan = plan(parameters)
        require(validateTerminalSequence(plan, observations) == null) { "complete ordered server/client route observations required" }
        var position = 0; val initial = observations.take(t.cells.size * 3)
        require(terrainOk(initial, t, t.cells)) { "route preflight terrain unsupported, unavailable or mismatched" }
        position += initial.size
        var previousSite = t.start; var previousAfter: JsonNode? = null
        for (leg in t.legs) {
            val before = observations[position++].payload
            val hold = observations[position++].payload
            val wait = observations[position++].payload
            val release = observations[position++].payload
            val status = observations[position++].payload
            val after = observations[position++].payload
            val sites = cells(previousSite, leg.target)
            val post = observations.subList(position, position + sites.size * 3)
            position += post.size
            require(terrainOk(post, t, sites) &&
                sites.flatMap { cell -> listOf(-1, 0, 1).map { cell to it } }.all { (cell, offset) ->
                    val key = "pre.${cell.x}.${cell.y}.${cell.z}.$offset"
                    initial.single { it.step == key }.payload.path("block_state") ==
                        post.single { it.payload.path("x").asInt() == cell.x &&
                            it.payload.path("y").asInt() == cell.y + offset &&
                            it.payload.path("z").asInt() == cell.z }.payload.path("block_state")
                }) { "walk corridor changed or exceeded reviewed flat vanilla geometry" }
            require(safeActor(before, t, previousSite) && safeActor(after, t, leg.target) &&
                wait.path("ticks_observed").isIntegralNumber && wait.path("ticks_observed").asInt() == leg.waitTicks &&
                after.path("server_tick").asLong() > before.path("server_tick").asLong() &&
                after.path("server_tick").asLong() - before.path("server_tick").asLong() <= leg.waitTicks + 40 &&
                after.path("server_monotonic_nanos").asLong() > before.path("server_monotonic_nanos").asLong() &&
                (previousAfter == null || (before.path("server_tick").asLong() >= previousAfter.path("server_tick").asLong() &&
                    hypot(before.path("x").asDouble() - previousAfter.path("x").asDouble(),
                        before.path("z").asDouble() - previousAfter.path("z").asDouble()) <= 0.35)) &&
                validateAuthenticWalkInput(hold, release, status, t.actor, t.dimension, t.profile,
                    leg.holdId, hold.path("input_runtime_sha256").asText(), leg.waitTicks + 20)) {
                "client physical W or authoritative server actor route evidence missing"
            }
            val sx = (leg.target.x - previousSite.x).compareTo(0)
            val sz = (leg.target.z - previousSite.z).compareTo(0)
            val samples = status.path("walk_samples")
            val startX = number(before, "x")!!; val startZ = number(before, "z")!!
            require(samples.all { sample ->
                val x = number(sample, "x") ?: return@all false; val z = number(sample, "z") ?: return@all false
                val along = (x - startX) * sx + (z - startZ) * sz
                val lateral = (x - startX) * sz - (z - startZ) * sx
                along in -0.35..(sites.size - 1 + 0.8) && abs(lateral) <= 0.55
            } && hypot(number(hold, "x")!! - startX, number(hold, "z")!! - startZ) <= 0.65 &&
                hypot(number(status, "x")!! - number(after, "x")!!,
                    number(status, "z")!! - number(after, "z")!!) <= 0.9 &&
                abs(Math.IEEEremainder(number(after, "yaw")!! - number(before, "yaw")!!, 360.0)) <= 5.0 &&
                abs(number(after, "pitch")!! - number(before, "pitch")!!) <= 5.0) {
                "walk diverged from fixed corridor or rotated between server observations"
            }
            previousAfter = after; previousSite = leg.target
        }
        require(position == observations.size)
        return MeasurementVerdict("passed", "one bounded flat vanilla forward-only physical W route; actual client callbacks/key-up, loaded corridor and authoritative server before/after; reviewed source, natural origin and provenance separately required")
    }
}
