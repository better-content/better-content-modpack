package com.bettercontent.tests

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/** Verification only: typed native disk observations, never initialization or repair. */
internal object BlightLocusPersistence {
    const val SEED = 2345578283189886729L
    const val CLAIM = -8589934592L
    const val CLAIM_FILE = "data/better_malum_dynamic_trees_blight_loci.dat"
    const val CAPTURED_RUN = "20261008T153906Z-2347363"
    const val CAPTURED_LEVEL_SHA256 = "f97013b62705f011e965e7941976a39579111249255f9e165bd02fc8b88d4680"

    data class Observation(val seed: Long, val claims: Set<Long>) {
        fun evidence(phase: String): Map<String, Any> = mapOf(
            "phase" to phase, "seed" to seed, "claims" to claims.sorted(),
            "captured_run" to CAPTURED_RUN, "captured_level_sha256" to CAPTURED_LEVEL_SHA256,
            "expected_cell" to listOf(-2, 0), "expected_chunk" to listOf(-48, 17),
            "expected_claim" to CLAIM, "site_basis" to "derived_from_unchanged_policy_not_captured_stack_locals",
            "native_event_cardinality_observed" to false,
        )
    }

    fun read(world: Path, previous: Set<Long> = emptySet()): Observation {
        val level = readCompound(world.resolve("level.dat"))
        val seedTag = requireNotNull(level.compound("Data").compound("WorldGenSettings")["seed"]) { "native seed missing" }
        require(seedTag.type == 4) { "native world seed must be TAG_Long" }
        val seed = seedTag.value as Long
        require(seed == SEED) { "native seed differs: $seed != $SEED" }
        val claimsTag = requireNotNull(readCompound(world.resolve(CLAIM_FILE)).compound("data")["attempted_cells"]) { "native attempted_cells missing" }
        require(claimsTag.type == 9) { "native attempted_cells must be TAG_List" }
        val list = claimsTag.value as NbtList
        require(list.kind == 4) { "native attempted_cells elements must be TAG_Long" }
        val longs = list.values.map { it as Long }
        val claims = longs.toSet()
        require(longs.size == claims.size) { "duplicate native claim keys" }
        require(CLAIM in claims) { "derived target locus was not naturally claimed" }
        require(claims.containsAll(previous)) { "native claim keys lost: ${previous - claims}" }
        return Observation(seed, claims)
    }

    private data class Tag(val type: Int, val value: Any)
    private data class NbtList(val kind: Int, val values: List<Any>)
    private const val MAX_BYTES = 64 * 1024 * 1024
    private const val MAX_ELEMENTS = 1_000_000

    private fun readCompound(path: Path): Map<String, Tag> {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "native NBT missing/nonregular: $path" }
        require(Files.size(path) <= MAX_BYTES) { "compressed NBT exceeds bound: $path" }
        val bytes = Files.newInputStream(path).use { source ->
            GZIPInputStream(source).use { it.readNBytes(MAX_BYTES + 1) }
        }
        require(bytes.size <= MAX_BYTES) { "decompressed NBT exceeds bound: $path" }
        val reader = Reader(DataInputStream(ByteArrayInputStream(bytes)))
        return reader.root()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Tag>.compound(name: String): Map<String, Tag> {
        val tag = requireNotNull(this[name]) { "native $name missing" }
        require(tag.type == 10) { "native $name must be TAG_Compound" }
        return tag.value as Map<String, Tag>
    }

    private class Reader(private val input: DataInputStream) {
        private var elements = 0

        @Suppress("UNCHECKED_CAST")
        fun root(): Map<String, Tag> {
            require(input.readUnsignedByte() == 10) { "native NBT root must be TAG_Compound" }
            input.readUTF()
            val result = payload(10, 0) as Map<String, Tag>
            require(input.read() == -1) { "trailing native NBT payload" }
            return result
        }

        private fun length(): Int = input.readInt().also {
            require(it in 0..MAX_ELEMENTS) { "native NBT length out of bounds: $it" }
        }

        private fun payload(type: Int, depth: Int): Any {
            require(depth <= 64 && ++elements <= MAX_ELEMENTS) { "native NBT complexity exceeds bound" }
            return when (type) {
                1 -> input.readByte()
                2 -> input.readShort()
                3 -> input.readInt()
                4 -> input.readLong()
                5 -> input.readFloat()
                6 -> input.readDouble()
                7 -> ByteArray(length()).also(input::readFully)
                8 -> input.readUTF()
                9 -> {
                    val kind = input.readUnsignedByte()
                    val size = length()
                    require(kind in 0..12 && (kind != 0 || size == 0)) { "invalid native NBT list kind" }
                    NbtList(kind, List(size) { payload(kind, depth + 1) })
                }
                10 -> {
                    val result = linkedMapOf<String, Tag>()
                    while (true) {
                        val kind = input.readUnsignedByte()
                        if (kind == 0) break
                        require(kind in 1..12) { "invalid native NBT tag kind" }
                        val name = input.readUTF()
                        require(!result.containsKey(name)) { "duplicate native NBT compound key: $name" }
                        result[name] = Tag(kind, payload(kind, depth + 1))
                    }
                    result
                }
                11 -> IntArray(length()) { input.readInt() }
                12 -> LongArray(length()) { input.readLong() }
                else -> error("invalid native NBT payload kind $type")
            }
        }
    }
}
