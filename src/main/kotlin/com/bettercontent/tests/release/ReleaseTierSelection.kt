package com.bettercontent.tests.release

/** Release-time tier selection is separate from the acceptance rules inside each runner. */
internal data class ReleaseTierSelection(val tiers: List<String>, val soakMinutes: Int) {
    companion object {
        private val order = listOf("smoke", "soak", "tele-soak")

        fun parse(value: String, soakMinutes: Int): ReleaseTierSelection {
            require(value.isNotBlank()) { "runtime tier selection must be explicit (use none for package-only)" }
            val selected = if (value == "none") emptyList() else value.split(',')
            require(selected.distinct().size == selected.size && selected.all { it in order } && selected == order.filter { it in selected }) {
                "runtime tiers must be none or an ordered, non-repeated subset of smoke,soak,tele-soak"
            }
            require(if ("soak" in selected) soakMinutes in 1..10080 else soakMinutes == 0) {
                "--soak-minutes 1..10080 is required exactly when Soak is selected"
            }
            return ReleaseTierSelection(selected, soakMinutes)
        }
    }

    fun status(receipts: Map<String, Any?> = emptyMap()): Map<String, String> =
        listOf("smoke", "soak", "tele-soak", "visual").associateWith { tier ->
            if (tier in receipts) "passed" else "not_run"
        }
}
