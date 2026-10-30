package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** Keep an indexed view of nonfatal messages as well as the complete raw logs. */
internal fun recordLogDiagnostics(directory: Path, logs: Map<String, Path>): Map<String, Any> {
    val pattern = Regex("""(?:\[[^]\r\n]*/|\s)(WARN|ERROR|FATAL)\b""")
    val mapper = jacksonObjectMapper()
    val output = directory.resolve("log-diagnostics.jsonl")
    val counts = linkedMapOf<String, MutableMap<String, Int>>()
    Files.newBufferedWriter(output).use { writer ->
        logs.forEach { (name, log) ->
            val levels = linkedMapOf("WARN" to 0, "ERROR" to 0, "FATAL" to 0)
            counts[name] = levels
            if (Files.isRegularFile(log)) Files.newBufferedReader(log).use { reader ->
                var number = 0L
                while (true) {
                    val line = reader.readLine() ?: break
                    number++
                    val level = pattern.find(line)?.groupValues?.get(1) ?: continue
                    levels[level] = levels.getValue(level) + 1
                    writer.write(mapper.writeValueAsString(mapOf(
                        "log" to name, "line" to number, "level" to level, "text" to line)))
                    writer.newLine()
                }
            }
        }
    }
    return mapOf("path" to output.toString(), "counts" to counts,
        "raw_logs_retained" to logs.mapValues { (_, path) -> Files.isRegularFile(path) })
}
