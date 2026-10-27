package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

class TeleSoakLifecycleTest {
    private class Clock : SessionClock {
        var now = 0L
        override fun nanos() = now
        override fun sleep(duration: Duration) { now += duration.toNanos() }
    }

    private class Driver(val clock: Clock, val dimensions: MutableList<String> = mutableListOf(
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")) : TeleSoakDriver {
        var current = "minecraft:overworld"
        var connection = true
        var serverStarts = 0
        var clientJoins = 0
        var heartbeats = 0
        var saves = 0
        var teleports = 0
        var failTpsIn: String? = null
        var tpsProbeLatency = Duration.ZERO
        var rosterProbeLatency = Duration.ZERO
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun startServer() { serverStarts++ }
        override fun awaitServerReady() { clock.now += Duration.ofMinutes(3).toNanos() }
        override fun connectClient() { clientJoins++ }
        override fun awaitClientJoined() { clock.now += Duration.ofMinutes(2).toNanos() }
        override fun heartbeat() { ensureConnected(); heartbeats++ }
        override fun ensureConnected() { check(connection) { "disconnected" } }
        override fun disconnectClient() { ensureConnected(); connection = false }
        override fun saveAndStopServer() { saves++ }
        override fun validate() { check(saves == 1) }
        override fun registeredDimensions(progress: () -> Unit): List<String> {
            repeat(rosterProbeLatency.seconds.toInt()) {
                clock.sleep(Duration.ofSeconds(1))
                progress()
            }
            return dimensions.toList()
        }
        override fun teleportTo(dimension: String) { ensureConnected(); current = dimension; teleports++ }
        override fun verifyArrival(dimension: String, progress: () -> Unit) { check(current == dimension); progress() }
        override fun ensureInDimension(dimension: String) { check(current == dimension) }
        override fun currentTps(): Double {
            clock.now += tpsProbeLatency.toNanos()
            return if (current == failTpsIn) 18.9 else 19.8
        }
        override fun teleSoakEvent(name: String, fields: Map<String, Any>) { events += name to fields }
    }

    @Test fun verifiedDwellAndThreeFullVariedCircuitsRemainOneClientLifecycle() {
        val clock = Clock(); val driver = Driver(clock)
        runLifecycle(SessionRequest(SessionTier.TELE_SOAK), driver, clock)
        assertEquals(1, driver.serverStarts)
        assertEquals(1, driver.clientJoins)
        assertEquals(1, driver.saves)
        assertEquals(12, driver.teleports)
        assertTrue(driver.heartbeats >= 80)
        assertFalse(driver.connection)
        val dwells = driver.events.filter { it.first == "dwell_completed" }
        assertEquals(driver.dimensions.toSet(), dwells.map { it.second.getValue("dimension") }.toSet())
        assertTrue(dwells.all { (it.second.getValue("actual_seconds") as Double) >= 300.0 })
        assertEquals(12, driver.events.count { it.first == "tps_stable" })
        assertEquals(3, driver.events.count { it.first == "circuit_completed" })
        assertEquals(3, driver.events.filter { it.first == "circuit_started" }
            .map { it.second.getValue("route") }.toSet().size, "each complete circuit should vary its order")
        assertTrue(driver.events.filter { it.first == "tps_sample" }.all { (it.second.getValue("tps") as Double) >= 19.0 })
    }

    @Test fun unstableTpsCannotBeCountedTowardDwellOrRelease() {
        val clock = Clock(); val driver = Driver(clock).apply { failTpsIn = "minecraft:the_nether" }
        val failure = assertThrows(IllegalStateException::class.java) { runTeleSoak(driver, clock) }
        assertTrue(failure.message!!.contains("failed to recover"))
        assertFalse(driver.events.any { it.first == "tele_soak_complete" })
        assertFalse(driver.events.any { it.first == "dwell_completed" && it.second["dimension"] == "minecraft:the_nether" })
    }

    @Test fun slowRegistryDiscoveryKeepsOneClientResponsiveBeforeDwell() {
        val clock = Clock(); val driver = Driver(clock).apply { rosterProbeLatency = Duration.ofSeconds(31) }
        runTeleSoak(driver, clock)
        assertTrue(driver.heartbeats >= 4, "live registry probes must service connection heartbeats")
        assertEquals(1, driver.events.count { it.first == "dimension_roster" })
    }

    @Test fun delayedProbesCannotMasqueradeAsThirtyContinuousGoodSeconds() {
        val clock = Clock(); val driver = Driver(clock).apply { tpsProbeLatency = Duration.ofSeconds(3) }
        assertThrows(IllegalStateException::class.java) { runTeleSoak(driver, clock) }
        assertFalse(driver.events.any { it.first == "tps_stable" })
        assertFalse(driver.events.any { it.first == "dwell_completed" })
    }

    @Test fun aChangedOrIncompleteRosterFailsClosed() {
        val clock = Clock()
        assertThrows(IllegalArgumentException::class.java) {
            runTeleSoak(Driver(clock, mutableListOf("minecraft:overworld", "minecraft:overworld")), clock)
        }
        // Reject missing Overworld rather than silently interpreting a partial registry as complete.
        assertThrows(IllegalArgumentException::class.java) {
            runTeleSoak(Driver(Clock(), mutableListOf("minecraft:the_end")), Clock())
        }
    }

    @Test fun receiptCannotPromoteIncompleteOrFailedEvidence() {
        val clock = Clock(); val driver = Driver(clock)
        val receipt = TeleSoakReceipt()
        val observed = object : TeleSoakDriver by driver {
            override fun teleSoakEvent(name: String, fields: Map<String, Any>) {
                receipt.record(name, fields)
                driver.teleSoakEvent(name, fields)
            }
        }
        runTeleSoak(observed, clock)
        assertEquals(true, receipt.result(true)["scenario_completed"])
        assertEquals(false, receipt.result(false)["scenario_completed"])
        assertEquals(3, (receipt.result(true)["dimension_results"] as List<*>).size)
        assertEquals(false, TeleSoakReceipt().result(true)["scenario_completed"])
    }

    @Test fun lostPlayerDuringCircuitCannotPassOrReconnect() {
        val clock = Clock(); val driver = Driver(clock)
        val base = object : TeleSoakDriver by driver {
            override fun teleportTo(dimension: String) {
                driver.teleportTo(dimension)
                if (driver.teleports == 4) driver.connection = false
            }
        }
        assertThrows(IllegalStateException::class.java) { runTeleSoak(base, clock) }
        assertFalse(driver.events.any { it.first == "tele_soak_complete" })
        assertEquals(0, driver.clientJoins, "a protocol failure must not reconnect the client")
    }
}
