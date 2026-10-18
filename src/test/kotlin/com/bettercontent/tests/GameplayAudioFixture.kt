package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlin.math.abs

/** Gameplay-only native OpenAL Soft WAVE backend. Does not replace/patch sound engine or contexts. */
fun gameplayAudioEnvironment(evidence: EvidenceRun, log: Path): Map<String, String> {
    if (evidence.tier != "debug" || evidence.target != "gameplay") return emptyMap()
    val directory = evidence.directory.resolve("gameplay-native-audio")
    Files.createDirectories(directory)
    val name = log.fileName.toString().replace(Regex("[^A-Za-z0-9_.-]"), "_") + "-" + UUID.randomUUID()
    val wave = directory.resolve("native-$name.wav").toAbsolutePath().normalize()
    val config = directory.resolve("native-$name.conf").toAbsolutePath().normalize()
    val nativeLog = directory.resolve("native-$name.openal.log").toAbsolutePath().normalize()
    require(!wave.toString().contains('\n') && !wave.toString().contains('\r'))
    Files.writeString(config, """
        [general]
        drivers = wave
        frequency = 22050
        channels = stereo
        sample-type = int16
        [wave]
        file = $wave
    """.trimIndent() + "\n", StandardOpenOption.CREATE_NEW)
    Files.writeString(directory.resolve("native-$name.binding.json"), jacksonObjectMapper().writeValueAsString(mapOf(
        "schema" to "bc.native_audio_capture.v1", "run" to evidence.config.runId, "launch_log" to log.toString(),
        "wave" to wave.toString(), "config" to config.toString(), "native_log" to nativeLog.toString(),
        "backend" to "wave", "physical_audibility" to "UNKNOWN")), StandardOpenOption.CREATE_NEW)
    evidence.event("gameplay_native_audio_requested", mapOf("backend" to "wave", "config" to config.toString(),
        "wave" to wave.toString(), "native_log" to nativeLog.toString(), "physical_audibility" to "UNKNOWN"))
    return mapOf("ALSOFT_DRIVERS" to "wave", "ALSOFT_CONF" to config.toString(), "ALSOFT_LOGFILE" to nativeLog.toString(), "ALSOFT_LOGLEVEL" to "3")
}

/** Call only after owned clients exit; WAVE backend finalizes RIFF lengths on device close. */
fun gameplayAudioVerifyClosed(evidence: EvidenceRun) {
    if (evidence.tier != "debug" || evidence.target != "gameplay") return
    val directory = evidence.directory.resolve("gameplay-native-audio")
    require(Files.isDirectory(directory)) { "requested native audio capture was never configured" }
    val mapper = jacksonObjectMapper()
    val bindings = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().endsWith(".binding.json") }.toList() }
    require(bindings.isNotEmpty()) { "requested native audio capture has no launch binding" }
    for (binding in bindings) {
        val node = mapper.readTree(binding.toFile())
        require(node.path("run").asText() == evidence.config.runId)
        val wave = Path.of(node.path("wave").asText()).toAbsolutePath().normalize()
        require(wave.parent == directory.toAbsolutePath().normalize() && !Files.isSymbolicLink(wave))
        val result = GameplayWaveContract.inspect(wave)
        evidence.event("gameplay_native_audio_captured", result + mapOf("path" to wave.toString(), "sha256" to Hashes.sha256(wave),
            "backend" to "wave", "mixer_verdict" to "CAPTURED_MIXER", "physical_audibility" to "UNKNOWN",
            "cue_specific_waveform_verdict" to "UNKNOWN: aggregate native mixer signal is not isolated readiness-cue identification"))
    }
}

object GameplayWaveContract {
    /** Streaming bounded analysis, not a perceptual/human listening oracle. */
    fun inspect(path: Path): Map<String, Any> {
        require(Files.isRegularFile(path) && Files.size(path) >= 44) { "native WAVE output missing; no fallback-to-null acceptance" }
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            fun bytes(position: Long, count: Int): ByteBuffer {
                val buffer = ByteBuffer.allocate(count).order(ByteOrder.LITTLE_ENDIAN)
                while (buffer.hasRemaining()) require(channel.read(buffer, position + buffer.position()) > 0) { "truncated native WAVE" }
                return buffer.flip() as ByteBuffer
            }
            fun ascii(buffer: ByteBuffer, count: Int) = ByteArray(count).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val header = bytes(0, 12)
            require(ascii(header, 4) == "RIFF"); val declared = Integer.toUnsignedLong(header.int)
            require(ascii(header, 4) == "WAVE" && declared + 8 == channel.size()) { "native WAVE header not finalized at device close" }
            var position = 12L; var format = 0; var channels = 0; var rate = 0; var bits = 0; var dataAt = -1L; var dataLength = 0L
            var chunks = 0
            while (position + 8 <= channel.size()) {
                require(++chunks <= 256) { "unbounded WAVE chunk table" }
                val chunk = bytes(position, 8); val kind = ascii(chunk, 4); val length = Integer.toUnsignedLong(chunk.int)
                require(position + 8 + length <= channel.size()) { "WAVE chunk exceeds finalized file" }
                if (kind == "fmt ") {
                    require(length in 16..65536)
                    val fmt = bytes(position + 8, length.toInt())
                    format = fmt.short.toInt() and 65535; channels = fmt.short.toInt() and 65535; rate = fmt.int
                    fmt.int; fmt.short; bits = fmt.short.toInt() and 65535
                    if (format == 65534) { require(length >= 40); format = fmt.getShort(24).toInt() and 65535 }
                }
                if (kind == "data") { require(dataAt == -1L); dataAt = position + 8; dataLength = length }
                position += 8 + length + length % 2
            }
            require(format == 1 && bits == 16 && channels == 2 && rate == 22050) { "native WAVE format differs from declared fixture" }
            require(dataAt >= 0 && dataLength >= rate.toLong() * channels * 2) { "native WAVE has less than one second PCM" }
            // Scan at most 64 MiB. Early nonzero signal establishes capture, not the identity or audibility of any cue.
            val scanLength = minOf(dataLength, 64L * 1024 * 1024)
            var offset = 0L; var nonzero = 0L; var peak = 0
            while (offset < scanLength) {
                val count = minOf(65536L, scanLength - offset).toInt() and -2
                val pcm = bytes(dataAt + offset, count)
                while (pcm.remaining() >= 2) {
                    val value = abs(pcm.short.toInt()); if (value != 0) nonzero++; peak = maxOf(peak, value)
                }
                offset += count
            }
            require(nonzero > 0 && peak > 0) { "native WAVE contains no observed mixer signal in bounded analysis" }
            return mapOf("sample_rate" to rate, "channels" to channels, "bits" to bits,
                "pcm_bytes" to dataLength, "frames" to dataLength / (channels * 2), "scanned_bytes" to scanLength,
                "nonzero_samples" to nonzero, "peak_absolute_sample" to peak)
        }
    }
}
