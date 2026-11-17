package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.ArrayDeque

internal data class DiagnosticEvent(val source: String, val firstLine: Long, val lastLine: Long,
    val severity: String, val headline: String, val exception: String)
internal data class DiagnosticSignature(val signature: String, val severity: String, val headline: String,
    val exception: String, val count: Long, val occurrences: List<DiagnosticEvent>, val reviewState: String = "unreviewed")

private val severityPattern = Regex("(?:\\[[^]\\r\\n]*/|\\s)(WARN|ERROR|FATAL)\\b")
private val recordStart = Regex("^(?:\\[\\d{2}:\\d{2}:\\d{2}[^]]*]|\\d{4}-\\d{2}-\\d{2}[ T])")
private val exceptionPattern = Regex("(?:Caused by: )?([A-Za-z_$][A-Za-z0-9_.$]*(?:Exception|Error))(?::|$)")

/** Stream complete record spans. A warning flood must not require loading the log into heap. */
private fun visitDiagnosticEvents(source: String, lines: Sequence<String>, emit: (DiagnosticEvent) -> Unit) {
    var start = 0L; var end = 0L; var severity = ""; var headline = ""; var exception = ""
    fun flush() { if (severity.isNotEmpty()) emit(DiagnosticEvent(source, start, end, severity, headline, exception)); severity = "" }
    lines.forEachIndexed { index, line ->
        val number = index.toLong() + 1
        val match = severityPattern.find(line)
        if (recordStart.containsMatchIn(line) || match != null) {
            flush()
            if (match != null) {
                start = number; end = number; severity = match.groupValues[1]
                headline = line.take(4096); exception = exceptionPattern.find(line)?.groupValues?.get(1).orEmpty()
            }
        } else if (severity.isNotEmpty()) {
            end = number
            if (exception.isEmpty()) exception = exceptionPattern.find(line)?.groupValues?.get(1).orEmpty()
        }
    }
    flush()
}

/** Small-input helper for unit contracts; production log processing uses the streaming visitor. */
internal fun collectDiagnosticEvents(source: String, lines: Sequence<String>): List<DiagnosticEvent> =
    mutableListOf<DiagnosticEvent>().also { result -> visitDiagnosticEvents(source, lines, result::add) }

private fun normalizedDiagnosticKey(event: DiagnosticEvent): String {
    // Preserve recipe IDs, error codes and quantities. Only volatile identity is removed.
    val head = event.headline.replace(Regex("^\\[[^]]+]"), "[time]")
        .replace(Regex("\\b[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\b"), "<uuid>")
        .replace(Regex("\\b(?:pid|PID)=\\d+\\b"), "pid=<pid>")
    return "${event.severity}\n$head\n${event.exception}"
}
private fun diagnosticDigest(key: String): String = MessageDigest.getInstance("SHA-256")
    .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun diagnosticSignatures(events: List<DiagnosticEvent>): List<DiagnosticSignature> =
    events.groupBy(::normalizedDiagnosticKey).map { (key, rows) ->
        DiagnosticSignature(diagnosticDigest(key), rows.first().severity, rows.first().headline, rows.first().exception, rows.size.toLong(), rows)
    }.sortedBy { it.signature }

private class SignatureAccumulator(val key: String, val first: DiagnosticEvent) {
    var count = 0L
    private val firstOccurrences = mutableListOf<DiagnosticEvent>()
    private val lastOccurrences = ArrayDeque<DiagnosticEvent>()
    fun add(event: DiagnosticEvent) {
        count++
        if (firstOccurrences.size < 16) firstOccurrences += event else {
            if (lastOccurrences.size == 16) lastOccurrences.removeFirst()
            lastOccurrences.addLast(event)
        }
    }
    fun summary() = DiagnosticSignature(diagnosticDigest(key), first.severity, first.headline, first.exception,
        count, firstOccurrences + lastOccurrences.toList())
}

internal data class LogSignatureSummary(val signatures: List<DiagnosticSignature>, val unindexedEvents: Long)

/** Read-only recomputation for evidence validation; never writes or approves a review. */
internal fun computeLogSignatures(logs: Map<String, Path>): LogSignatureSummary {
    val groups = linkedMapOf<String, SignatureAccumulator>()
    var unindexed = 0L
    logs.forEach { (name, path) ->
        if (Files.isRegularFile(path)) Files.newBufferedReader(path).use { reader ->
            visitDiagnosticEvents(name, reader.lineSequence()) { event ->
                val key = normalizedDiagnosticKey(event)
                val group = groups[key] ?: if (groups.size < 1024)
                    SignatureAccumulator(key, event).also { groups[key] = it } else null
                if (group == null) unindexed++ else group.add(event)
            }
        }
    }
    return LogSignatureSummary(groups.values.map { it.summary() }.sortedBy { it.signature }, unindexed)
}

internal fun recordLogSignatures(directory: Path, logs: Map<String, Path>): Path {
    val summary = computeLogSignatures(logs)
    val output = directory.resolve("log-signatures.json")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), mapOf(
        "schema" to "bc.log_signatures.v1", "status" to "unreviewed", "signatures" to summary.signatures,
        "signature_limit" to 1024, "occurrence_sample_limit" to 32, "unindexed_events" to summary.unindexedEvents, "complete" to (summary.unindexedEvents == 0L),
        "raw_logs" to logs.mapValues { (_, path) -> path.toString() },
        "acceptance_policy" to "Severity alone is not failure. Review impact and harmful flooding. An incomplete index needs further raw-log review; all raw logs remain retained."))
    return output
}
