package com.bettercontent.tests

import java.util.UUID
import kotlin.math.abs

/** Correlated native observations, not a model that substitutes for the player tick. */
object NativeDeepVoidContract {
    data class Before(val run: String, val nonce: UUID, val kind: String, val player: UUID, val tick: Long,
        val dimension: String, val bottom: Int, val threshold: Double, val y: Double, val natural: Boolean,
        val voidEnabled: Boolean, val otherEnabled: Boolean, val endEnabled: Boolean)
    data class After(val run: String, val nonce: UUID, val kind: String, val player: UUID, val tick: Long,
        val dimension: String, val bottom: Int, val y: Double, val natural: Boolean)
    private const val number = "(-?[0-9]+(?:\\.[0-9]+)?)"
    private val before = Regex("BC_DEEPVOID_BEFORE run=(\\S+) nonce=(\\S+) case=(above|disabled|entry) uuid=(\\S+) tick=([0-9]+) dimension=(\\S+) bottom=(-?[0-9]+) threshold=$number y=$number mode=survival natural=(true|false) void_enabled=(true|false) other_enabled=(true|false) end_enabled=(true|false)(?:\\r?\\n|$)")
    private val after = Regex("BC_DEEPVOID_AFTER run=(\\S+) nonce=(\\S+) case=(above|disabled|entry) uuid=(\\S+) tick=([0-9]+) dimension=(\\S+) bottom=(-?[0-9]+) y=$number natural=(true|false) mode=survival(?:\\r?\\n|$)")
    fun parseBefore(line: String): Before {
        val g = requireNotNull(before.find(line)) { "malformed native Deep Void BEFORE observation" }.groupValues
        return Before(g[1], UUID.fromString(g[2]), g[3], UUID.fromString(g[4]), g[5].toLong(), g[6],
            g[7].toInt(), g[8].toDouble(), g[9].toDouble(), g[10].toBooleanStrict(), g[11].toBooleanStrict(),
            g[12].toBooleanStrict(), g[13].toBooleanStrict())
    }
    fun parseAfter(line: String): After {
        val g = requireNotNull(after.find(line)) { "malformed native Deep Void AFTER observation" }.groupValues
        return After(g[1], UUID.fromString(g[2]), g[3], UUID.fromString(g[4]), g[5].toLong(), g[6],
            g[7].toInt(), g[8].toDouble(), g[9].toBooleanStrict())
    }
    fun requireNativeDecision(before: Before, after: After, run: String, nonce: UUID, player: UUID, kind: String) {
        require(kind in setOf("above", "disabled", "entry"))
        require(before.run == run && after.run == run && before.nonce == nonce && after.nonce == nonce &&
            before.player == player && after.player == player && before.kind == kind && after.kind == kind &&
            before.tick == after.tick) { "native Deep Void observations have mismatched run/player/nonce/tick" }
        require(before.dimension == "minecraft:overworld" && before.bottom < -64 && before.threshold.isFinite() &&
            before.y.isFinite() && after.y.isFinite() && before.threshold == before.bottom - 6.0 && !before.natural &&
            before.voidEnabled == (kind != "disabled") && abs(before.y - (before.threshold + if (kind == "above") 0.001 else -0.001)) < 0.000001) {
            "native Deep Void fixture did not observe the actual extended-bottom eligibility/boundary"
        }
        val entry = kind == "entry"
        require(after.natural == entry && after.dimension == if (entry) "the_deep_void:deep_void" else before.dimension) {
            "native Deep Void decision/transfer failed for $kind: $after"
        }
        if (!entry) require(after.bottom == before.bottom) { "negative control changed its native source level" }
    }
}
