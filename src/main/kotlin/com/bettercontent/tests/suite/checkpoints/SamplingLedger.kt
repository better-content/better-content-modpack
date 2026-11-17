package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.Hashes
import com.fasterxml.jackson.databind.node.ObjectNode
import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.idPattern
import com.bettercontent.tests.suite.resourcePattern
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Instant

/** Append-only campaign-wide sampling history, deliberately outside every restored epoch/checkpoint tree. */
internal data class SamplingClaim(val sequence: Long, val sha256: String, val kind: String,
    val caseId: String, val attempt: Int?, val dimension: String?, val chunkX: Int?, val chunkZ: Int?,
    val footprintRadiusChunks: Int?)

/** A compact reserved rectangle, not one journal record per sampled chunk. */
private data class SiteFootprint(val dimension: String, val xMin: Long, val xMax: Long,
    val zMin: Long, val zMax: Long) {
    fun intersects(other: SiteFootprint): Boolean = dimension == other.dimension &&
        xMin <= other.xMax && other.xMin <= xMax && zMin <= other.zMax && other.zMin <= zMax
}

private const val MAX_SITE_CHUNK = 1_874_999L // Conservative Minecraft +/-29,999,984-block world bounds.
private fun footprint(dimension: String, x: Int, z: Int, radius: Int): SiteFootprint {
    require(radius in 0..32) { "sampling footprint radius must be in 0..32 chunks" }
    val xMin = x.toLong() - radius; val xMax = x.toLong() + radius
    val zMin = z.toLong() - radius; val zMax = z.toLong() + radius
    require(xMin >= -MAX_SITE_CHUNK && xMax <= MAX_SITE_CHUNK &&
        zMin >= -MAX_SITE_CHUNK && zMax <= MAX_SITE_CHUNK) { "sampling footprint crosses world bounds" }
    return SiteFootprint(dimension, xMin, xMax, zMin, zMax)
}

private data class LedgerEvent(val schema: String, val sequence: Long, val kind: String,
    val campaignId: String, val lineageId: String, val candidate: Map<String, String>,
    val supportSha256: String, val configurationSha256: String,
    val caseId: String, val dimension: String?, val chunkX: Int?, val chunkZ: Int?,
    val attempt: Int?, val recordedAt: String, val previousSha256: String, val sha256: String,
    val footprintRadiusChunks: Int? = null)

internal class SamplingLedger(campaignRoot: Path, private val bindings: CheckpointBindings) {
    private val campaign = campaignRoot.toRealPath()
    private val file = campaign.resolve("sampling-ledger.jsonl")
    private val anchors = campaign.resolve("sampling-ledger-anchors")
    private val lockFile = campaign.resolve("sampling-ledger.lock")
    private val genesis = "0".repeat(64)

    init {
        require(bindings.campaignId.isNotBlank() && bindings.lineageId.isNotBlank() &&
            bindings.candidate.keys == setOf("client_sha256", "server_sha256") &&
            (bindings.candidate.values + bindings.supportSha256 + bindings.configurationSha256).all(shaPattern::matches))
    }

    fun claimSite(dimension: String, chunkX: Int, chunkZ: Int, caseId: String,
        footprintRadiusChunks: Int = 0): SamplingClaim {
        require(resourcePattern.matches(dimension) && idPattern.matches(caseId))
        footprint(dimension, chunkX, chunkZ, footprintRadiusChunks)
        return claim("site", caseId, dimension, chunkX, chunkZ, footprintRadiusChunks)
    }

    fun claimThreatAttempt(caseId: String): SamplingClaim {
        require(idPattern.matches(caseId))
        return claim("threat_attempt", caseId, null, null, null, null)
    }

    /** Explicit first-campaign initialization, never implicit during qualification or claim. */
    fun initialize(): BoundFile {
        require(!Files.exists(file, NOFOLLOW_LINKS) && !Files.exists(anchors, NOFOLLOW_LINKS) &&
            !Files.exists(lockFile, NOFOLLOW_LINKS)) { "sampling ledger already initialized or partial; never reset" }
        Files.createDirectory(anchors)
        Files.writeString(file, "", CREATE_NEW)
        Files.writeString(lockFile, "", CREATE_NEW)
        return audit()
    }

    /** Replays every record and immutable sequence anchor; no reset, branch filter or rewind API. */
    fun verify(): List<SamplingClaim> = readValidated().map { it.claim() }

    fun audit(): BoundFile {
        verify()
        return BoundFile(file.toString(), Hashes.sha256(file))
    }

    private fun claim(kind: String, caseId: String, dimension: String?, x: Int?, z: Int?,
        radius: Int?): SamplingClaim =
        FileChannel.open(lockFile, WRITE).use { channel -> channel.lock().use {
            val history = readValidated()
            require(history.size < 100_000) { "sampling ledger size limit reached" }
            val attempt = if (kind == "threat_attempt") {
                val used = history.count { it.kind == kind && it.caseId == caseId }
                require(used < 3) { "threat attempt limit reached for $caseId" }
                used + 1
            } else {
                val reserved = footprint(requireNotNull(dimension), requireNotNull(x), requireNotNull(z),
                    requireNotNull(radius))
                require(history.none { it.kind == "site" && reserved.intersects(
                    footprint(requireNotNull(it.dimension), requireNotNull(it.chunkX), requireNotNull(it.chunkZ),
                        it.footprintRadiusChunks ?: 0)) }) {
                    "sampling footprint overlaps a reserved site in this campaign (including restored branches)"
                }
                null
            }
            require(Files.isDirectory(anchors, NOFOLLOW_LINKS)) { "sampling anchors absent" }
            val previous = history.lastOrNull()?.sha256 ?: genesis
            val base = LedgerEvent("bc.playtest_sampling.v1", history.size.toLong() + 1, kind,
                bindings.campaignId, bindings.lineageId, bindings.candidate, bindings.supportSha256,
                bindings.configurationSha256, caseId, dimension, x, z, attempt, Instant.now().toString(), previous, "", radius)
            val next = base.copy(sha256 = digest(base))
            val line = (suiteMapper.writeValueAsString(next) + "\n").toByteArray(Charsets.UTF_8)
            FileChannel.open(file, CREATE, WRITE, APPEND).use { out ->
                val bytes = ByteBuffer.wrap(line)
                while (bytes.hasRemaining()) out.write(bytes)
                out.force(true)
            }
            val anchor = anchors.resolve("%012d.sha256".format(next.sequence))
            Files.writeString(anchor, next.sha256 + "\n", CREATE_NEW)
            // A crash after journal append but before anchor creation remains an explicit corrupt gap.
            require(readValidated().last() == next) { "ledger append failed audit" }
            next.claim()
        } }

    private fun readValidated(): List<LedgerEvent> {
        require(Files.exists(file, NOFOLLOW_LINKS) && Files.isRegularFile(lockFile, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(lockFile) && (Files.getAttribute(lockFile, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1) {
            "uninitialized, linked or removed sampling ledger"
        }
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
            (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
            Files.size(file) in 0L..50_000_000L && Files.isDirectory(anchors, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(anchors)) { "missing, linked or oversized append-only sampling ledger" }
        val raw = Files.readString(file)
        require(raw.isEmpty() || raw.endsWith('\n')) { "incomplete journal append; do not resume or rewrite" }
        val rows = raw.lineSequence().filter(String::isNotBlank).toList()
        require(rows.size <= 100_000)
        val events = rows.map { suiteMapper.readValue(it, LedgerEvent::class.java) }
        require(Files.list(anchors).use { it.count() } == events.size.toLong()) { "missing or extra immutable sequence anchor" }
        var previous = genesis
        var instant: Instant? = null
        val sites = mutableListOf<SiteFootprint>()
        val attempts = mutableMapOf<String, Int>()
        events.forEachIndexed { index, event ->
            // Pre-footprint v1 rows had no radius key. Preserve their exact original hash-chain bytes
            // while treating their single-center site as radius zero; do not rewrite any journal row.
            val legacy = !suiteMapper.readTree(rows[index]).has("footprint_radius_chunks")
            require(event.schema == "bc.playtest_sampling.v1" && event.sequence == index.toLong() + 1 &&
                event.campaignId == bindings.campaignId && event.lineageId == bindings.lineageId &&
                event.candidate == bindings.candidate && event.supportSha256 == bindings.supportSha256 &&
                event.configurationSha256 == bindings.configurationSha256 &&
                event.previousSha256 == previous && shaPattern.matches(event.sha256) && event.sha256 == digest(event, legacy)) {
                "sampling chain/bindings corrupt at sequence ${index + 1}"
            }
            val time = Instant.parse(event.recordedAt)
            require((instant?.let { !time.isBefore(it) } ?: true) && !time.isAfter(Instant.now()))
            instant = time
            require(idPattern.matches(event.caseId))
            when (event.kind) {
                "site" -> {
                    require(event.dimension != null && resourcePattern.matches(event.dimension) &&
                        event.chunkX != null && event.chunkZ != null && event.attempt == null &&
                        (legacy || event.footprintRadiusChunks != null)) { "site footprint missing across restored branches" }
                    val reserved = footprint(event.dimension, event.chunkX, event.chunkZ, event.footprintRadiusChunks ?: 0)
                    require(sites.none(reserved::intersects)) { "overlapping reserved site across restored branches" }
                    sites += reserved
                }
                "threat_attempt" -> {
                    val attempt = requireNotNull(event.attempt) { "missing threat attempt index" }
                    require(event.dimension == null && event.chunkX == null && event.chunkZ == null &&
                        event.footprintRadiusChunks == null &&
                        attempt == (attempts[event.caseId] ?: 0) + 1 && attempt in 1..3) {
                        "invalid/extra threat attempt"
                    }
                    attempts[event.caseId] = attempt
                }
                else -> error("unknown sampling event kind: ${event.kind}")
            }
            val anchor = anchors.resolve("%012d.sha256".format(event.sequence))
            require(Files.isRegularFile(anchor, NOFOLLOW_LINKS) && !Files.isSymbolicLink(anchor) &&
                (Files.getAttribute(anchor, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1 &&
                Files.readString(anchor) == event.sha256 + "\n") { "immutable sequence anchor differs" }
            previous = event.sha256
        }
        return events
    }

    private fun digest(event: LedgerEvent, legacy: Boolean = false): String {
        val canonical = suiteMapper.valueToTree<ObjectNode>(event.copy(sha256 = ""))
        if (legacy) canonical.remove("footprint_radius_chunks")
        return MessageDigest.getInstance("SHA-256").digest(suiteMapper.writeValueAsBytes(canonical))
            .joinToString("") { "%02x".format(it) }
    }

    private fun LedgerEvent.claim() = SamplingClaim(sequence, sha256, kind, caseId, attempt, dimension,
        chunkX, chunkZ, footprintRadiusChunks ?: if (kind == "site") 0 else null)
}
