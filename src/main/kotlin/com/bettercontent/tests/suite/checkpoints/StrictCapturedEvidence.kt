package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.shaPattern
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Capture exactly one immutable byte buffer before any parsing; no symlinks, coercion or trailing JSON. */
internal fun capturedEvidenceBytes(ref: BoundFile, maxBytes: Int): ByteArray {
    val path = Path.of(ref.path).toAbsolutePath().normalize()
    require(ref.path == path.toString() && ref.sha256.matches(shaPattern) && maxBytes in 2..67_108_864 &&
        Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
    var parent = path.parent
    while (parent != null) { require(!Files.isSymbolicLink(parent)); parent = parent.parent }
    val before = Files.size(path)
    require(before in 2..maxBytes.toLong())
    val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(maxBytes + 1) }
    require(bytes.size.toLong() == before && Files.isRegularFile(path, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(path) && Files.size(path) == before &&
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) == ref.sha256) {
        "captured evidence changed, exceeded budget or failed its SHA binding"
    }
    return bytes
}

internal fun strictCapturedJson(bytes: ByteArray): JsonNode {
    require(bytes.isNotEmpty() && !(bytes.size >= 3 && bytes[0] == 0xef.toByte() &&
        bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()))
    // Reject malformed UTF-8 even when a parser would silently replace invalid code points.
    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
    return suiteMapper.factory.createParser(bytes).use { parser ->
        val document = requireNotNull(suiteMapper.readTree<JsonNode>(parser))
        require(parser.nextToken() == null) { "trailing JSON after captured evidence" }
        document
    }
}

internal fun strictCapturedLines(ref: BoundFile, maxBytes: Int = 16_777_216,
    maxRows: Int = 8192, maxLine: Int = 131_072): List<JsonNode> {
    val bytes = capturedEvidenceBytes(ref, maxBytes)
    require(bytes.last() == '\n'.code.toByte() && maxRows in 1..100_000 && maxLine in 2..1_048_576)
    val result = ArrayList<JsonNode>()
    var start = 0
    for (i in bytes.indices) if (bytes[i] == '\n'.code.toByte()) {
        require(result.size < maxRows && i > start && i - start <= maxLine)
        result.add(strictCapturedJson(bytes.copyOfRange(start, i)))
        start = i + 1
    }
    require(start == bytes.size)
    return result
}
