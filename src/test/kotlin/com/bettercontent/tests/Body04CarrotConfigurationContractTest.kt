package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

/** Authored policy and pinned native bytecode contracts, not a runtime food census. */
@Tag("fast")
class Body04CarrotConfigurationContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val nativeConfig = "com/cazsius/solcarrot/SOLCarrotConfig"
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    @Test
    fun `open food filters preserve the explicitly agreed progression settings`() {
        // Parse the complete TOML with the authored standard-library contract, not substring
        // matching that could accept duplicate keys, commented settings or a stale census.
        val process = ProcessBuilder("python3", "-B", "scripts/test_food_progression.py")
            .directory(root.toFile()).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "Food policy check timed out")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.exitValue(), output)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS), "Food policy process did not exit")
            }
        }
    }

    @Test
    fun `empty filters use native eligibility without a curated food census`() {
        val path = pinnedModJar("solcarrot-1.20.1-1.15.1.jar")
        val pin = Files.readString(root.resolve("mods/spice-of-life-carrot-edition.pw.toml"))
        val digest = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
        assertTrue(pin.contains("filename = \"${path.fileName}\""))
        assertTrue(pin.contains("hash-format = \"sha1\""))
        assertTrue(pin.contains("hash = \"$digest\""), "Inspect only the active hash-pinned native archive")
        ZipFile(path.toFile()).use { jar ->
            // Exact branch shape: a nonempty whitelist selects its matcher; an empty
            // whitelist falls through to the inverted blacklist matcher. Empty blacklist
            // has no matches, so arbitrary namespaces remain allowed without an item census.
            assertEquals(listOf(
                "CALL net/minecraftforge/registries/IForgeRegistry.getKey",
                "CALL java/util/Objects.requireNonNull",
                "CALL net/minecraft/resources/ResourceLocation.toString",
                "CALL $nativeConfig.hasWhitelist",
                "JUMP ${Opcodes.IFEQ}",
                "FIELD whitelist",
                "CALL net/minecraftforge/common/ForgeConfigSpec\$ConfigValue.get",
                "CALL $nativeConfig.matchesAnyPattern",
                "INSN ${Opcodes.IRETURN}",
                "FIELD blacklist",
                "CALL net/minecraftforge/common/ForgeConfigSpec\$ConfigValue.get",
                "CALL $nativeConfig.matchesAnyPattern",
                "JUMP ${Opcodes.IFNE}",
                "INSN ${Opcodes.ICONST_1}",
                "JUMP ${Opcodes.GOTO}",
                "INSN ${Opcodes.ICONST_0}",
                "INSN ${Opcodes.IRETURN}",
            ), trace(jar, nativeConfig, "isAllowed"))
            val whitelist = trace(jar, nativeConfig, "hasWhitelist")
            assertTrue(whitelist.contains("FIELD whitelist"))
            assertTrue(whitelist.contains("CALL java/util/List.isEmpty"))
            val patterns = trace(jar, nativeConfig, "matchesAnyPattern")
            assertTrue(patterns.contains("CALL java/util/Iterator.hasNext"))
            assertEquals(listOf("INSN ${Opcodes.ICONST_0}", "INSN ${Opcodes.IRETURN}"), patterns.takeLast(2),
                "An empty pattern collection rejects no items")
            val hearty = trace(jar, nativeConfig, "isHearty")
            assertTrue(hearty.contains("CALL net/minecraft/world/item/Item.m_41473_"))
            assertTrue(hearty.contains("FIELD minimumFoodValue"))
            assertTrue(hearty.contains("JUMP ${Opcodes.IF_ICMPLT}"), "Native minimum-food-value gate remains")
            val count = trace(jar, nativeConfig, "shouldCount")
            assertEquals(listOf("CALL $nativeConfig.isHearty", "CALL $nativeConfig.isAllowed"),
                count.filter { it.startsWith("CALL ") })
            val tracker = trace(jar, "com/cazsius/solcarrot/tracking/FoodTracker", "onFoodEaten")
            assertTrue(tracker.contains("CALL net/minecraft/world/item/Item.m_41472_"), "Native edible-item gate remains")
            assertTrue(tracker.contains("CALL com/cazsius/solcarrot/tracking/FoodList.addFood"))
            assertTrue(tracker.contains("CALL com/cazsius/solcarrot/tracking/MaxHealthHandler.updateFoodHPModifier"))
        }
    }

    private fun trace(jar: ZipFile, owner: String, method: String): List<String> {
        val result = mutableListOf<String>()
        ClassReader(jar.getInputStream(jar.getEntry("$owner.class")).use { it.readBytes() })
            .accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, name: String, descriptor: String,
                    signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (name != method) return null
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                            descriptor: String, isInterface: Boolean) { result += "CALL $owner.$name" }
                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                            if (owner == "$nativeConfig\$Server") result += "FIELD $name"
                        }
                        override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) { result += "JUMP $opcode" }
                        override fun visitInsn(opcode: Int) {
                            if (opcode in setOf(Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.IRETURN)) result += "INSN $opcode"
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertTrue(result.isNotEmpty(), "Missing native method $owner.$method")
        return result
    }
}
