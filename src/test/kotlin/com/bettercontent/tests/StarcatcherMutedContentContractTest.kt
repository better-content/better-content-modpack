package com.bettercontent.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Pinned native data/bytecode inspection only; no Minecraft or saved-entity acceptance. */
@Tag("fast")
class StarcatcherMutedContentContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root", "."))
    private val mapper = jacksonObjectMapper()
    private val expected = setOf("charfish", "chorus_crab", "end_glow", "voidbiter")
    private val prefix = "data/starcatcher/starcatcher/fish/"
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)
    private fun strings(node: JsonNode): Set<String> = node.map { it.asText() }.toSet()
    private fun nativeDefinitions(jar: ZipFile): Map<String, JsonNode> = jar.entries().asSequence()
        .filter { it.name.startsWith(prefix) && it.name.endsWith(".json") }
        .associate { entry -> entry.name.removePrefix(prefix).removeSuffix(".json") to
            jar.getInputStream(entry).use { mapper.readTree(it) } }

    @Test fun allOriginallyEndOnlyCatchesRetainNativeRestrictionsAndAuthoredFields() {
        val path = pinnedModJar("starcatcher-2.2.1-FORGE-1.20.1.jar")
        val pin = Files.readString(root.resolve("mods/starcatcher.pw.toml"))
        val digest = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
        assertTrue(pin.contains("hash-format = \"sha1\""))
        assertTrue(pin.contains("hash = \"$digest\""), "Inspect only the active pinned native archive")
        ZipFile(path.toFile()).use { jar ->
            val native = nativeDefinitions(jar)
            val dimensionEnd = native.filterValues {
                strings(it.path("world_restrictions").path("dimensions")) == setOf("minecraft:the_end")
            }.keys
            assertEquals(expected, dimensionEnd, "Review the muted family when the upstream pin changes")
            // Exhaustively review equivalent biome-only selectors too; none exist in this pin.
            val biomeEnd = native.filterValues {
                val wr = it.path("world_restrictions")
                (strings(wr.path("biomes")) + strings(wr.path("biomes_tags")))
                    .any { id -> id == "minecraft:is_end" || id in setOf("minecraft:the_end",
                        "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands",
                        "minecraft:end_barrens") }
            }.keys
            assertTrue(expected.containsAll(biomeEnd), "Review additional End-biome catches: $biomeEnd")
            for (id in expected) {
                val upstream = native.getValue(id)
                val override = root.resolve("kubejs/$prefix$id.json")
                val effective = if (Files.exists(override)) mapper.readTree(override.toFile()) else upstream
                assertEquals(upstream["world_restrictions"], effective["world_restrictions"], id)
                assertEquals(setOf("minecraft:the_end"), strings(effective["world_restrictions"]["dimensions"]), id)
                // For the three authored overrides, all other fields already matched upstream.
                // Whole-tree equality protects bait, minigame, catch/bucket/entity IDs and guide flags.
                assertEquals(upstream, effective, "Preserve non-habitat fields for $id")
                assertTrue(effective["has_guide_entry"].asBoolean(), id)
                assertTrue(effective["base_chance"].asInt() > 0, "Do not mute by zero weight")
                assertNotNull(jar.getEntry("${prefix}$id.json"), "Muted identity must remain present in the pinned native registry")
            }
            val void = native.getValue("voidbiter")["world_restrictions"]
            assertEquals(setOf("minecraft:is_end"), strings(void["biomes_tags"]))
            assertEquals(setOf("minecraft:the_end"), strings(void["biomes_blacklist"]))
            // Direct native recipe/loot references would require a producer/consumer review.
            val directProducers = jar.entries().asSequence().filter {
                it.name.startsWith("data/") && ("/recipes/" in it.name || "/loot_tables/" in it.name) &&
                    it.name.endsWith(".json")
            }.filter { entry ->
                val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }
                expected.any { text.contains("starcatcher:$it") }
            }.map { it.name }.toList()
            assertTrue(directProducers.isEmpty(), "Review native acquisition references: $directProducers")
        }
    }

    @Test fun nativeFishingChecksDimensionBeforeOrdinaryBaitBonuses() {
        ZipFile(pinnedModJar("starcatcher-2.2.1-FORGE-1.20.1.jar").toFile()).use { jar ->
            val fish = "com/wdiscute/starcatcher/storage/FishProperties"
            val chanceCalls = calls(jar, fish, "getChance")
            val dimension = chanceCalls.indexOf("$fish.isDimensionCorrect")
            val bait = chanceCalls.indexOf("$fish.br")
            assertTrue(dimension >= 0 && bait > dimension, "Ordinary bait must not bypass dimension eligibility")
            assertTrue(calls(jar, fish, "isDimensionCorrect").any { it.endsWith("WorldRestrictions.dims") })
            assertTrue(calls(jar, "com/wdiscute/starcatcher/bob/FishingBobEntity", null)
                .contains("$fish.getChance"), "Native bobber must consume the checked fishing weight")
            assertTrue(calls(jar, fish, "getFPs").any { it.startsWith("net/minecraft/core/RegistryAccess.") },
                "Inspect effective dynamic-registry definitions, not a hardcoded catch catalogue")
        }
    }

    private fun calls(jar: ZipFile, owner: String, method: String?): List<String> {
        val calls = mutableListOf<String>()
        val bytes = jar.getInputStream(jar.getEntry("$owner.class")).use { it.readBytes() }
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (method != null && name != method) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                        descriptor: String, isInterface: Boolean) { calls += "$owner.$name" }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return calls
    }
}
