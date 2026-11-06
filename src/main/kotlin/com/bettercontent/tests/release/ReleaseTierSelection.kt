package com.bettercontent.tests.release

/** Release-time tier selection is separate from the acceptance rules inside each runner. */
internal data class ReleaseTierSelection(val tiers: List<String>) {
    companion object {
        private val order = listOf("smoke", "overworld-tele-soak", "tele-soak")

        fun parse(value: String, obsoleteMinutes: Int): ReleaseTierSelection {
            require(value.isNotBlank()) { "runtime tier selection must be explicit (use none for package-only)" }
            val selected = if (value == "none") emptyList() else value.split(',')
            require(selected.distinct().size == selected.size && selected.all { it in order } && selected == order.filter { it in selected }) {
                "runtime tiers must be none or an ordered, non-repeated subset of smoke,overworld-tele-soak,tele-soak"
            }
            require(obsoleteMinutes == 0) { "ordinary Soak and --soak-minutes are no longer supported" }
            return ReleaseTierSelection(selected)
        }
    }

    fun status(receipts: Map<String, Any?> = emptyMap()): Map<String, String> =
        listOf("smoke", "overworld-tele-soak", "tele-soak", "visual").associateWith { tier ->
            if (tier in receipts) "passed" else "not_run"
        }
}
