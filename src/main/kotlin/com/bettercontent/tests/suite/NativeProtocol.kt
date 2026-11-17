package com.bettercontent.tests.suite

import com.fasterxml.jackson.databind.JsonNode
import com.bettercontent.tests.Hashes
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.time.Duration

internal const val PROTOCOL_LINE_LIMIT = 1024 * 1024
internal data class NativeRequest(val schema: String, val runId: String, val caseId: String, val token: String,
    val sequence: Long, val driver: String, val timeoutTicks: Int, val parameters: JsonNode)
internal data class NativeObservation(val schema: String, val runId: String, val caseId: String, val token: String,
    val sequence: Long, val requestSequence: Long, val driver: String, val source: String,
    val kind: String, val tick: Long, val payload: JsonNode)

/** Incremental bounded UTF-8 reader. An incomplete final line is never evidence. */
internal class NativeObservationReader(private val file: Path) {
    private var offset = 0L
    private val pending = ByteArrayOutputStream()
    fun readAvailable(): List<NativeObservation> {
        if (!Files.exists(file)) return emptyList()
        require(!Files.isSymbolicLink(file) && Files.isRegularFile(file)) { "unsafe observation file" }
        require(Files.size(file) >= offset) { "native observation log was truncated" }
        val observations = mutableListOf<NativeObservation>()
        FileChannel.open(file, StandardOpenOption.READ).use { channel ->
            channel.position(offset)
            val bytes = ByteBuffer.allocate(8192)
            // Bounded batch even if an untrusted producer keeps appending.
            val end = channel.size()
            while (channel.position() < end && observations.size < 256) {
                bytes.clear(); bytes.limit(minOf(bytes.capacity().toLong(), end - channel.position()).toInt())
                val read = channel.read(bytes)
                if (read <= 0) break
                bytes.flip()
                while (bytes.hasRemaining()) {
                    val byte = bytes.get().toInt() and 255
                    offset++
                    if (byte == 10) {
                        require(pending.size() > 0) { "empty native observation line" }
                        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(pending.toByteArray())).toString()
                        pending.reset()
                        observations += suiteMapper.readValue(text, NativeObservation::class.java)
                    } else {
                        require(pending.size() < PROTOCOL_LINE_LIMIT) { "native observation exceeds line budget" }
                        pending.write(byte)
                    }
                }
            }
        }
        return observations
    }
    fun requireComplete() { require(pending.size() == 0) { "partial native observation at completion" } }
}

/** Pure state machine: replay, cross-run messages and server-only client proofs are rejected. */
internal class NativeExchangeValidator {
    private var lastSequence = 0L
    private var lastTick = 0L
    private var started = false
    private var terminal = false
    private var current: NativeRequest? = null
    fun begin(request: NativeRequest) {
        require(current == null || terminal) { "previous native request is not terminal" }
        require(request.schema == "bc.playtest_action.v2" && request.sequence > 0 &&
            request.timeoutTicks in 1..72000 && request.parameters.isObject &&
            request.runId.matches(idPattern) && request.caseId.matches(idPattern) &&
            request.driver.matches(idPattern) && request.token.matches(shaPattern)) { "invalid native request" }
        require(current == null || request.sequence == current!!.sequence + 1) { "request sequence gap/replay" }
        current = request; started = false; terminal = false
    }
    fun accept(observation: NativeObservation, requiredSource: String = "server"): Boolean {
        val request = requireNotNull(current) { "unsolicited observation" }
        require(observation.schema == "bc.playtest_observation.v2" && observation.runId == request.runId &&
            observation.caseId == request.caseId && observation.token == request.token &&
            observation.requestSequence == request.sequence && observation.driver == request.driver) { "unbound native observation" }
        require(observation.sequence == lastSequence + 1 && observation.tick >= lastTick && observation.tick >= 0) { "replayed/reordered native observation" }
        require(observation.source in setOf("server", "client") && observation.source == requiredSource) { "native observation has wrong source" }
        require(observation.payload.isObject && !terminal) { "duplicate terminal/invalid observation payload" }
        when (observation.kind) {
            "started" -> { require(!started) { "duplicate native start" }; started = true }
            "sample" -> require(started) { "sample before native start" }
            "completed", "failed" -> { require(started) { "terminal before native start" }; terminal = true }
            else -> error("unknown native observation kind")
        }
        lastSequence = observation.sequence; lastTick = observation.tick
        return terminal
    }
}

internal class NativeActionFailure(val observation: NativeObservation) : IllegalStateException(
    "native action ${observation.driver} failed: ${observation.payload.path("reason").asText("no reason supplied")}")
private class NativeControllerTimeout(driver: String) : IllegalStateException("native action $driver timed out; raw observations retained")

/** No implicit process launch or retry. All raw responses remain in the run-owned mailbox. */
internal class NativeMailbox(val directory: Path, val runId: String,
    val token: String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }) {
    private val requests = directory.resolve("requests")
    private val reader = NativeObservationReader(directory.resolve("observations.jsonl"))
    private val validator = NativeExchangeValidator()
    private var sequence = 0L
    init {
        require(runId.matches(idPattern) && token.matches(shaPattern))
        Files.createDirectories(directory.parent)
        Files.createDirectory(directory)
        Files.createDirectory(requests)
    }
    fun javaArguments(): List<String> = listOf("-Dbc.playtest.directory=${directory.toAbsolutePath()}",
        "-Dbc.playtest.runId=$runId", "-Dbc.playtest.token=$token")
    @Volatile private var aborted = false
    /** Controller reserves the ACTUAL next sequence before journaling a witness context. Never infer it from observations. */
    @Synchronized fun reserveSequence(): Long {
        require(!aborted)
        return ++sequence
    }
    fun execute(caseId: String, driver: String, parameters: JsonNode, timeout: Duration,
        source: String = "server", ensureAlive: () -> Unit = {}, requestTimeoutTicks: Int? = null,
        allocatedSequence: Long? = null): NativeObservation {
        require(!aborted && !timeout.isNegative && !timeout.isZero && timeout <= Duration.ofHours(1))
        // Native request tick allowance is independent of the controller's monotonic wall deadline.
        require(requestTimeoutTicks == null || (requestTimeoutTicks in 1..72_000 &&
            (driver != "scale.begin" || requestTimeoutTicks >= 20_040)))
        val deadline = Math.addExact(System.nanoTime(), timeout.toNanos())
        val ticks = requestTimeoutTicks ?: (timeout.toMillis() / 50).coerceIn(1, 72000).toInt()
        val actualSequence = if (allocatedSequence == null) reserveSequence() else allocatedSequence.also {
            require(it > 0 && it == sequence && !aborted) { "foreign/unallocated native request sequence" }
        }
        val request = NativeRequest("bc.playtest_action.v2", runId, caseId, token, actualSequence, driver, ticks, parameters)
        validator.begin(request)
        require(reader.readAvailable().isEmpty()) { "unsolicited native output before request" }
        val bytes = suiteMapper.writeValueAsBytes(request)
        require(bytes.size <= PROTOCOL_LINE_LIMIT) { "request exceeds budget" }
        val target = requests.resolve("%012d.json".format(actualSequence))
        val pending = requests.resolve(".%012d.pending".format(actualSequence))
        Files.write(pending, bytes, StandardOpenOption.CREATE_NEW)
        Files.writeString(directory.resolve("requests.jsonl"), String(bytes, Charsets.UTF_8) + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE)
        while (true) {
            check(!aborted) { "native mailbox poisoned after controller failure" }
            if (System.nanoTime() >= deadline) throw NativeControllerTimeout(driver)
            ensureAlive()
            var result: NativeObservation? = null
            reader.readAvailable().forEach { observation ->
                if (validator.accept(observation, source)) result = observation
            }
            result?.let { terminal ->
                reader.requireComplete()
                if (terminal.kind == "failed") throw NativeActionFailure(terminal)
                return terminal
            }
            if (System.nanoTime() >= deadline) throw NativeControllerTimeout(driver)
            Thread.sleep(50)
        }
    }

    /** Synchronous serialization/file IO and native polling share ONE controller deadline from dispatch. */
    fun executeWithWallDeadline(caseId: String, driver: String, parameters: JsonNode, wall: Duration, ticks: Int,
        source: String = "server", ensureAlive: () -> Unit, allocatedSequence: Long? = null,
        dispatchNanos: Long = System.nanoTime()): NativeObservation {
        require(!aborted && !wall.isNegative && !wall.isZero && wall <= Duration.ofHours(1) && ticks in 1..72_000 &&
            (driver != "scale.begin" || (wall <= Duration.ofSeconds(900) && ticks >= 20_040)))
        val start = dispatchNanos
        require(System.nanoTime() >= start) { "controller monotonic dispatch timestamp cannot be in the future" }
        val task = java.util.concurrent.FutureTask {
            execute(caseId, driver, parameters, wall, source = source, ensureAlive = ensureAlive,
                requestTimeoutTicks = ticks, allocatedSequence = allocatedSequence)
        }
        Thread(task, "bounded-native-controller-${runId}").apply { isDaemon = true }.start()
        try {
            val remaining = wall.toNanos() - (System.nanoTime() - start)
            if (remaining <= 0L) throw java.util.concurrent.TimeoutException("scale controller expired during thread startup")
            return task.get(remaining, java.util.concurrent.TimeUnit.NANOSECONDS)
        } catch (error: java.util.concurrent.TimeoutException) {
            aborted = true
            task.cancel(true)
            throw IllegalStateException("$driver controller wall deadline exceeded; mailbox poisoned, evidence retained", error)
        } catch (error: java.util.concurrent.ExecutionException) {
            val cause = error.cause ?: error
            if (cause is NativeControllerTimeout) aborted = true
            throw cause
        } catch (error: InterruptedException) {
            aborted = true
            task.cancel(true)
            Thread.currentThread().interrupt()
            throw IllegalStateException("$driver controller interrupted; mailbox poisoned, evidence retained", error)
        }
    }
    fun boundObservationFile(): BoundFile {
        reader.requireComplete()
        val file = directory.resolve("observations.jsonl")
        return BoundFile(file.toAbsolutePath().toString(), Hashes.sha256(file))
    }
}
