package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Observes only native scoped records; does not replace gameplay, strict-log or performance oracles. */
object PlaytestLoggingAudit {
    data class Totals(val attempted: Long = 0, val emitted: Long = 0, val suppressed: Long = 0, val overflow: Long = 0)
    data class Report(val records: Long, val detailRecords: Long, val nativeUtf8Bytes: Long,
        val activations: Map<String, Set<String>>, val summaries: Map<String, Totals>, val findings: List<String>)
    private data class Parsed(val fields: Map<String, String>, val errors: List<String>)
    /** Linear scanner: repeated escaped/quoted content must not consume regex recursion stack. */
    private fun parseFields(body: String, truncated: Boolean): Parsed {
        val fields = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()
        var cursor = 0
        fun keyChar(c: Char) = c.isLetterOrDigit() || c in "_.:-"
        fun malformed(): Parsed { errors += "malformed playtest field boundary/tail"; return Parsed(fields, errors) }
        while (cursor < body.length) {
            while (cursor < body.length && body[cursor] == ' ') cursor++
            if (cursor == body.length) break
            val start = cursor
            while (cursor < body.length && keyChar(body[cursor])) cursor++
            if (cursor == start || cursor == body.length || body[cursor] != '=') {
                if (truncated && cursor == body.length && cursor > start) break
                return malformed()
            }
            val key = body.substring(start, cursor++)
            val valueStart = cursor
            if (cursor == body.length) return if (truncated) Parsed(fields, errors) else malformed()
            if (body[cursor] == '"') {
                cursor++
                var closed = false
                while (cursor < body.length) {
                    val c = body[cursor++]
                    if (c == '\\') { if (cursor < body.length) cursor++ }
                    else if (c == '"') { closed = true; break }
                }
                if (!closed) return if (truncated) Parsed(fields, errors) else malformed()
            } else {
                while (cursor < body.length && !body[cursor].isWhitespace() && body[cursor] != '"') cursor++
                if (cursor == valueStart) return malformed()
            }
            val value = body.substring(valueStart, cursor)
            if (fields.put(key, value) != null) errors += "duplicate playtest field: $key"
            if (cursor < body.length) {
                if (body[cursor++] != ' ') return malformed()
            }
        }
        return Parsed(fields, errors)
    }
    /** Bounded samples for a fresh TNT/buried fixture; never a substitute for its native world oracles. */
    fun tntBuriedCausality(paths: Collection<Path>): Map<String, Any> {
        val selected = setOf("scalable_tnt" to "tnt.prime", "scalable_tnt" to "tnt.explosion_handoff",
            "better_buried_encounters" to "sealed_monster.removal_watch", "better_buried_encounters" to "sealed_monster.release")
        val samples = mutableListOf<Map<String, String>>()
        for (path in paths.distinct()) {
            Files.newBufferedReader(path).useLines { lines -> lines.forEach { text ->
                if (samples.size >= 256) return@forEach
                val record = LogPolicy.parseRecord(text) ?: return@forEach
                if (record.severity != "DEBUG" || !record.message.startsWith("BC_PLAYTEST ") || record.message.length > 2048) return@forEach
                val parsed = parseFields(record.message.removePrefix("BC_PLAYTEST "), false)
                val f = parsed.fields
                if (parsed.errors.isEmpty() && f["schema"] == "bc.playtest.v1" && f["side"] == "server" &&
                    (f["mod"] to f["event"]) in selected && record.logger?.startsWith("bc.playtest.${f["mod"]}.") == true &&
                    canonicalUuid(f["process"]) && f["tick"]?.toLongOrNull()?.let { it >= 0 } == true &&
                    f["dimension"] == "minecraft:overworld") samples += f.toMap()
            } }
        }
        fun entityKey(f: Map<String, String>) = listOf(f["process"], f["dimension"], f["subject"])
        fun originKey(f: Map<String, String>) = listOf(f["process"], f["dimension"], f["pos"])
        fun atOrigin(f: Map<String, String>) = f["pos"]?.matches(Regex("-?[0-9]+,-?[0-9]+,-?[0-9]+")) == true
        val primed = samples.filter { it["event"] == "tnt.prime" && it["result"] == "inserted" &&
            it["reason"] == "native_insertion" && canonicalUuid(it["subject"]) }
        val explosions = samples.filter { it["event"] == "tnt.explosion_handoff" && it["result"] == "handed_off" &&
            it["reason"] in setOf("authored_power", "bounded_power") }
        val linkedTnt = primed.filter { start -> explosions.any { end -> entityKey(start) == entityKey(end) &&
            end.getValue("tick").toLong() >= start.getValue("tick").toLong() } }.map { it.getValue("subject") }.toSet()
        val admitted = samples.filter { it["event"] == "sealed_monster.removal_watch" && it["result"] == "admitted" &&
            it["reason"] == "watch_registered" && atOrigin(it) }
        val removal = samples.filter { it["event"] == "sealed_monster.removal_watch" &&
            ((it["result"] == "consumed" && it["reason"] == "native_callback") ||
             (it["result"] == "dispatched" && it["reason"] == "observed_block_removal")) && atOrigin(it) }
        val releases = samples.filter { it["event"] == "sealed_monster.release" && it["result"] == "inserted" &&
            it["reason"] == "native_insertion" && canonicalUuid(it["subject"]) && atOrigin(it) }
        val linkedRoots = releases.filter { release -> admitted.any { originKey(it) == originKey(release) &&
            it.getValue("tick").toLong() <= release.getValue("tick").toLong() } && removal.any { originKey(it) == originKey(release) } }
            .map { it.getValue("pos") }.toSet()
        val expired = samples.filter { it["event"] == "sealed_monster.removal_watch" && it["result"] == "expired" &&
            it["reason"] == "timeout" && atOrigin(it) && admitted.any { start -> originKey(start) == originKey(it) &&
                start.getValue("tick").toLong() <= it.getValue("tick").toLong() } }.map { it.getValue("pos") }.toSet()
        require(linkedTnt.isNotEmpty()) { "no native DEBUG prime-to-explosion handoff linked by process/dimension/entity UUID; scoped DEBUG is required for logging acceptance" }
        require(linkedRoots.isNotEmpty()) { "no native DEBUG watch-to-insertion linked by process/dimension/origin and actual created mob UUID" }
        require(expired.isNotEmpty()) { "no native DEBUG admitted-to-expired withdrawal/TTL sample" }
        return mapOf("sample_limit" to 256, "sampled_details" to samples.size, "prime_handoff_subjects" to linkedTnt,
            "watch_release_origins" to linkedRoots, "expired_origins" to expired,
            "scope" to "representative causal logging only; existing native effect, rollback, TTL and deferred applicability oracles remain authoritative")
    }

    private fun canonicalUuid(value: String?): Boolean = value != null &&
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    private val required = setOf("schema", "mod", "event", "side", "process", "result", "reason")
    private val privateKeys = setOf("seed", "player_name", "chat", "address", "ip", "topic", "consumer_id", "payload", "nbt", "full_inventory")

    fun scan(paths: Collection<Path>, expectedSources: Map<String, String> = emptyMap(), requireActivation: Boolean = false): Report {
        var records = 0L; var details = 0L; var bytes = 0L
        val activations = linkedMapOf<String, MutableSet<String>>()
        val findings = mutableListOf<String>()
        val summaries = linkedMapOf<String, Totals>()
        val statusKeys = mutableSetOf<Pair<String, String>>()
        paths.distinct().forEach { path ->
            var statuses = 0
            if (!Files.isRegularFile(path)) { findings += "$path: missing raw audit"; return@forEach }
            Files.newBufferedReader(path).useLines { lines -> lines.forEachIndexed { index, text ->
                val native = LogPolicy.parseRecord(text) ?: return@forEachIndexed
                val logger = native.logger ?: return@forEachIndexed
                if (!logger.startsWith("bc.playtest.")) return@forEachIndexed
                records++; bytes += text.toByteArray(Charsets.UTF_8).size + 1
                if (native.severity in setOf("DEBUG", "TRACE")) details++
                fun fail(why: String) { findings += "$path:${index + 1}: $why" }
                val message = native.message
                if (!message.startsWith("BC_PLAYTEST ")) { fail("scoped record lacks playtest envelope"); return@forEachIndexed }
                if (message.length > 2048) { fail("playtest record exceeds producer bound"); return@forEachIndexed }
                val truncated = message.length == 2048 && message.endsWith(" truncated=true")
                val body = message.removePrefix("BC_PLAYTEST ").let { if (truncated) it.removeSuffix(" truncated=true") else it }
                val parsed = parseFields(body, truncated)
                parsed.errors.forEach(::fail)
                val fields = parsed.fields
                if (required.any { fields[it].isNullOrBlank() || fields[it] == "\"\"" }) fail("missing required playtest field")
                if (fields["schema"] != "bc.playtest.v1") fail("unknown playtest schema")
                val mod = fields["mod"] ?: ""
                if (!logger.startsWith("bc.playtest.$mod.")) fail("logger/mod identity mismatch")
                if (expectedSources.isNotEmpty() && mod !in expectedSources) fail("undeclared playtest mod")
                if (fields.keys.any { it in privateKeys }) fail("private default playtest field")
                val process = fields["process"] ?: ""
                if (runCatching { UUID.fromString(process).toString() == process }.getOrDefault(false).not()) fail("noncanonical process identity")
                if (fields["event"] == "logging.status") {
                    statuses++
                    if (!statusKeys.add(process to mod)) fail("duplicate per-process activation")
                    activations.getOrPut(mod) { linkedSetOf() }.add(process)
                    if (native.severity != "INFO") fail("activation severity must be INFO")
                    if (expectedSources[mod]?.let { it != fields["source"] } == true) fail("activation source does not match candidate")
                    for ((key, value) in mapOf("detail_per_minute" to "120", "burst" to "20", "max_keys" to "256", "max_record" to "2048", "summary_seconds" to "30")) {
                        if (fields[key] != value) fail("activation producer contract mismatch: $key")
                    }
                }
                if (fields["event"] == "logging.summary") {
                    val attempted = fields["attempted"]?.toLongOrNull()
                    val emitted = fields["emitted"]?.toLongOrNull()
                    val suppressed = fields["suppressed"]?.toLongOrNull()
                    val overflow = fields["overflow"]?.toLongOrNull()
                    val keys = fields["keys"]?.toIntOrNull()
                    if (attempted == null || emitted == null || suppressed == null || overflow == null || keys == null ||
                        attempted < 0 || emitted < 0 || suppressed < 0 || overflow < 0 || overflow > attempted ||
                        keys !in 0..256 || emitted > attempted || suppressed != attempted - emitted) fail("invalid aggregation conservation/bounds")
                    else {
                        val old = summaries.getOrDefault(mod, Totals())
                        runCatching { Totals(Math.addExact(old.attempted, attempted), Math.addExact(old.emitted, emitted),
                            Math.addExact(old.suppressed, suppressed), Math.addExact(old.overflow, overflow)) }
                            .onSuccess { summaries[mod] = it }.onFailure { fail("summary total overflow") }
                    }
                }
            } }
            if (requireActivation && statuses == 0) findings += "$path: native ready launch has no playtest activation"
        }
        return Report(records, details, bytes, activations, summaries, findings)
    }
}
