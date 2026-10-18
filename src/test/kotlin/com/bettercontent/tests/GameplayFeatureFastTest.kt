package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Synthetic fail-closed contract checks, explicitly not native runtime acceptance. */
class GameplayFeatureFastTest {
    private val mapper = jacksonObjectMapper()
    @Test fun bridgeRejectsDifferentRunNoncePlayerSequenceAndAction() {
        val good = mapper.readTree("""{"schema":"bc.gameplay_client_observation.v1","run":"current","nonce":"n","player":"p","sequence":1,"action":"capture","label":"frame"}""")
        GameplayFeatureContract.requireBinding(good, "client", "current", "n", "p", 1, "capture", "frame")
        for (field in listOf("schema", "run", "nonce", "player", "action", "label")) {
            val bad = good.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put(field, "foreign")
            assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireBinding(bad, "client", "current", "n", "p", 1, "capture", "frame") }
        }
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireBinding(good, "client", "current", "n", "p", 2, "capture", "frame") }
    }
    @Test fun readinessRequiresActualSingleDispatchAndNativeParticlesNotAudioClaim() {
        val good = mapper.readTree("""{"sounds_dropped":0,"native_sources_dropped":0,"sound_dispatch_events":[{"id":"better_survival_physiology:aspect.impact"}],"native_source_events":[{"id":"better_survival_physiology:aspect.impact","channel_playing_on_native_event_thread":true,"resolved_asset_path":"better_survival_physiology:sounds/fixture_impact.ogg"}],"native_particle_counts":{"minecraft:dust":9}}""")
        GameplayFeatureContract.requireRising(good)
        val dispatchedOnly = good.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        dispatchedOnly.putArray("native_source_events")
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireRising(dispatchedOnly) }
        val empty = mapper.readTree("""{"sounds_dropped":0,"sound_dispatch_events":[],"native_particle_counts":{"minecraft:dust":9}}""")
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireRising(empty) }
        val noParticles = mapper.readTree("""{"sounds_dropped":0,"sound_dispatch_events":[{"id":"better_survival_physiology:aspect.impact"}],"native_particle_counts":{}}""")
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireRising(noParticles) }
    }
    @Test fun nativeDayNightUsesActualClassificationNotVisitLabelsOrClockOffsets() {
        val first = mapper.readTree("""{"native_day":true,"native_night":false,"day_time":1000}""")
        val alsoDay = mapper.readTree("""{"native_day":true,"native_night":false,"day_time":19000}""")
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireOppositeDayNight(first, alsoDay) }
        val night = mapper.readTree("""{"native_day":false,"native_night":true,"day_time":19000}""")
        GameplayFeatureContract.requireOppositeDayNight(first, night)
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.nativeDay(mapper.readTree("""{"day_time":19000}""")) }
    }
    @Test fun shaderCaptureRejectsDisabledFallbackWrongPackAndNativeErrors() {
        val valid = mapper.readTree("""{"third_party_shader_state":{"enabled":true,"in_use":true,"fallback":false,"stored_error":false,"shadow_pass":false,"native_pack_present":true,"native_pack_name":"ComplementaryReimagined_r5.8.1.zip","pack_version":"5.8.1","pack_sha256":"${"a".repeat(64)}","verdict":"ACTIVE_NATIVE_SHADERPACK"}}""")
        GameplayFeatureContract.requireActiveShader(valid)
        for ((field, value) in listOf("enabled" to false, "in_use" to false, "fallback" to true, "stored_error" to true, "native_pack_present" to false)) {
            val bad = valid.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            (bad.path("third_party_shader_state") as com.fasterxml.jackson.databind.node.ObjectNode).put(field, value)
            assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireActiveShader(bad) }
        }
        val foreign = valid.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (foreign.path("third_party_shader_state") as com.fasterxml.jackson.databind.node.ObjectNode).put("native_pack_name", "different.zip")
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireActiveShader(foreign) }
    }
    @Test fun finalizedNativeWaveRequiresRealPcmSignal() {
        val directory = java.nio.file.Path.of(System.getProperty("user.home"), ".tmp", "gameplay-audio-fast", java.util.UUID.randomUUID().toString())
        java.nio.file.Files.createDirectories(directory)
        val dataBytes = 22050 * 2 * 2
        val buffer = java.nio.ByteBuffer.allocate(44 + dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36 + dataBytes); buffer.put("WAVEfmt ".toByteArray()); buffer.putInt(16)
        buffer.putShort(1); buffer.putShort(2); buffer.putInt(22050); buffer.putInt(22050 * 4); buffer.putShort(4); buffer.putShort(16)
        buffer.put("data".toByteArray()); buffer.putInt(dataBytes)
        val silent = directory.resolve("silent.wav"); java.nio.file.Files.write(silent, buffer.array())
        assertThrows(IllegalArgumentException::class.java) { GameplayWaveContract.inspect(silent) }
        buffer.putShort(44, 1000)
        val signal = directory.resolve("signal.wav"); java.nio.file.Files.write(signal, buffer.array())
        org.junit.jupiter.api.Assertions.assertEquals(1L, GameplayWaveContract.inspect(signal)["nonzero_samples"])
        buffer.putInt(4, 0)
        val unclosed = directory.resolve("unclosed.wav"); java.nio.file.Files.write(unclosed, buffer.array())
        assertThrows(IllegalArgumentException::class.java) { GameplayWaveContract.inspect(unclosed) }
    }
    @Test fun cuttingRejectsOnlyFinalBreakDebrisWithoutIntermediateNativeProgress() {
        val debrisOnly = mapper.readTree("""{"cut_calls_dropped":0,"native_cut_calls":[{"progress_after_native_call":0,"nanos":1}],"native_particle_counts":{"submitted:net.minecraft.client.particle.TerrainParticle":64}}""")
        assertThrows(IllegalArgumentException::class.java) { GameplayFeatureContract.requireCut(debrisOnly) }
    }
}
