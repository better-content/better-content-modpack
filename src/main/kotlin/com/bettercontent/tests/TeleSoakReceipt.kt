package com.bettercontent.tests

/** A projection of the append-only Tele Soak log; release must independently verify the raw events. */
internal class TeleSoakReceipt {
    private var dimensions: List<String> = emptyList()
    private val dwell = linkedMapOf<String, Double>()
    private val recovered = linkedMapOf<String, MutableSet<Int>>()
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
            "dwell_completed" -> {
                val dimension = fields["dimension"] as? String ?: error("dwell lacks dimension")
                val seconds = (fields["actual_seconds"] as? Number)?.toDouble() ?: error("dwell lacks seconds")
                check(dwell.putIfAbsent(dimension, seconds) == null) { "duplicate dwell for $dimension" }
            }
            "circuit_completed" -> {
                val circuit = (fields["circuit"] as? Number)?.toInt() ?: error("circuit lacks index")
                check(completedCircuits.add(circuit)) { "duplicate circuit $circuit" }
            }
            "tele_soak_complete" -> { check(!complete) { "duplicate Tele Soak completion" }; complete = true }
        }
    }

    fun result(success: Boolean): Map<String, Any> {
        val scenarioCompleted = success && complete && dimensions.isNotEmpty() &&
            dimensions.size == dimensions.toSet().size && dimensions.toSet() == dwell.keys &&
            dwell.values.all { it.isFinite() && it >= 300.0 } && completedCircuits == setOf(1, 2, 3) &&
            recovered.keys == dimensions.toSet() && recovered.values.all { it == setOf(0, 1, 2, 3) }
        val results = dimensions.map { dimension ->
            mapOf("dimension" to dimension, "dwell_seconds" to (dwell[dimension] ?: 0.0),
                "completed_circuits" to (1..3).filter { it in (recovered[dimension] ?: emptySet()) && it in completedCircuits },
                "tps_verified" to (recovered[dimension] == setOf(0, 1, 2, 3)))
        }
        return mapOf("scenario_completed" to scenarioCompleted, "dwell_seconds_per_dimension" to 300,
            "circuits" to 3, "registered_dimensions" to dimensions, "dimension_results" to results)
    }
}
