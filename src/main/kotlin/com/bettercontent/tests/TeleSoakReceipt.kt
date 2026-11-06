package com.bettercontent.tests

/** A projection of the append-only Tele Soak log; release must independently verify the raw events. */
internal class TeleSoakReceipt(private val overworldOnly: Boolean = false) {
    private var dimensions: List<String> = emptyList()
    private val dwell = linkedMapOf<String, Double>()
    private val recovered = linkedMapOf<String, MutableSet<Int>>()
    private val relocations = linkedMapOf<String, MutableSet<Pair<Int, Int>>>()
    private val landedChunks = linkedMapOf<String, MutableSet<Pair<Int, Int>>>()
    private val lastPosition = linkedMapOf<String, Pair<Double, Double>>()
    private val completedCircuits = mutableSetOf<Int>()
    private var complete = false

    fun record(type: String, fields: Map<String, Any?>) {
        when (type) {
            "dimension_roster" -> {
                check(dimensions.isEmpty()) { "duplicate live dimension roster" }
                dimensions = (fields["dimensions"] as? List<*>)?.map { it as? String ?: error("invalid dimension") }
                    ?: error("missing live dimension roster")
            }
            "tps_stable" -> {
                val dimension = fields["dimension"] as? String ?: error("stable TPS lacks dimension")
                val circuit = (fields["circuit"] as? Number)?.toInt() ?: error("stable TPS lacks circuit")
                check(recovered.getOrPut(dimension, ::mutableSetOf).add(circuit)) { "duplicate TPS recovery for $dimension/$circuit" }
            }
            "relocation_tps_stable" -> {
                val dimension = fields["dimension"] as? String ?: error("relocation lacks dimension")
                val circuit = (fields["circuit"] as? Number)?.toInt() ?: error("relocation lacks circuit")
                val ordinal = (fields["relocation"] as? Number)?.toInt() ?: error("relocation lacks ordinal")
                check(ordinal in 1..10 && relocations.getOrPut(dimension, ::mutableSetOf).add(circuit to ordinal)) {
                    "duplicate/invalid relocation in $dimension/$circuit/$ordinal"
                }
            }
            "landing_verified" -> {
                val dimension = fields["dimension"] as? String ?: error("landing lacks dimension")
                val x = (fields["x"] as? Number)?.toDouble() ?: error("landing lacks X")
                val z = (fields["z"] as? Number)?.toDouble() ?: error("landing lacks Z")
                val cx = (fields["chunk_x"] as? Number)?.toInt() ?: error("landing lacks chunk X")
                val cz = (fields["chunk_z"] as? Number)?.toInt() ?: error("landing lacks chunk Z")
                val route = fields["route"] as? String ?: error("landing lacks route")
                val previous = lastPosition[dimension]
                val distance = previous?.let { kotlin.math.hypot(x - it.first, z - it.second) }
                check(x.isFinite() && z.isFinite() && cx == kotlin.math.floor(x / 16).toInt() &&
                    cz == kotlin.math.floor(z / 16).toInt() && fields["novel_chunk"] == true &&
                    (route == "remote_ungenerated_chunk" ||
                        (route == "native_font" && dimension == "the_bumblezone:the_bumblezone")) &&
                    (route == "native_font" || distance == null || distance >= 9999.0) &&
                    landedChunks.getOrPut(dimension, ::mutableSetOf).add(cx to cz)) {
                    "reused or too-close Tele Soak landing in $dimension at $cx/$cz"
                }
                lastPosition[dimension] = x to z
            }
            "dwell_completed" -> {
                val dimension = fields["dimension"] as? String ?: error("dwell lacks dimension")
                val seconds = (fields["actual_seconds"] as? Number)?.toDouble() ?: error("dwell lacks seconds")
                check(dwell.putIfAbsent(dimension, seconds) == null) { "duplicate dwell for $dimension" }
            }
            "circuit_completed" -> {
                val circuit = (fields["circuit"] as? Number)?.toInt() ?: error("circuit lacks index")
                check(completedCircuits.add(circuit)) { "duplicate circuit $circuit" }
            }
            "tele_soak_complete", "overworld_tele_soak_complete" -> {
                check(!complete && (type == "overworld_tele_soak_complete") == overworldOnly) {
                    "duplicate or wrong-profile Tele Soak completion"
                }
                complete = true
            }
        }
    }

    fun result(success: Boolean): Map<String, Any> {
        val scenarioCompleted = if (overworldOnly) success && complete && dimensions == listOf("minecraft:overworld") &&
            dwell["minecraft:overworld"]?.let { it.isFinite() && it >= 3600.0 } == true &&
            recovered["minecraft:overworld"] == setOf(0) &&
            relocations["minecraft:overworld"] == (1..10).map { 0 to it }.toSet() &&
            landedChunks["minecraft:overworld"]?.size == 11 && completedCircuits.isEmpty()
        else success && complete && dimensions.isNotEmpty() &&
            dimensions.size == dimensions.toSet().size && dimensions.toSet() == dwell.keys &&
            dwell.values.all { it.isFinite() && it >= 300.0 } && completedCircuits == setOf(1, 2, 3) &&
            recovered.keys == dimensions.toSet() && recovered.values.all { it == setOf(0, 1, 2, 3) } &&
            dimensions.all { dimension -> relocations[dimension] ==
                ((1..10).map { 0 to it } + (1..3).map { it to 1 }).toSet() &&
                landedChunks[dimension]?.size == 17 }
        val results = dimensions.map { dimension ->
            mapOf("dimension" to dimension, "dwell_seconds" to (dwell[dimension] ?: 0.0),
                "completed_circuits" to (1..3).filter { it in (recovered[dimension] ?: emptySet()) && it in completedCircuits },
                "tps_verified" to (recovered[dimension] == if (overworldOnly) setOf(0) else setOf(0, 1, 2, 3)),
                "fresh_relocations_verified" to (relocations[dimension] ==
                    (if (overworldOnly) (1..10).map { 0 to it }.toSet()
                    else ((1..10).map { 0 to it } + (1..3).map { it to 1 }).toSet()) &&
                    landedChunks[dimension]?.size == if (overworldOnly) 11 else 17))
        }
        return mapOf("scenario_completed" to scenarioCompleted,
            "dwell_seconds_per_dimension" to if (overworldOnly) 3600 else 300,
            "circuits" to if (overworldOnly) 0 else 3, "registered_dimensions" to dimensions,
            "dimension_results" to results)
    }
}
