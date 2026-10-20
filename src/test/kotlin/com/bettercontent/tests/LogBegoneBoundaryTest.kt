package com.bettercontent.tests

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.apache.logging.log4j.message.SimpleMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.objectweb.asm.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Executes pinned native predicate/filter bytecode, substituting only Forge config-value reads. No Forge launch. */
@Tag("fast")
class LogBegoneBoundaryTest {
    private val root = Path.of(System.getProperty("bc.repo.root", "."))
    private fun pinnedModJar(filename: String): Path =
        Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)
    private val nativeOwner = "mod/azure/logbegone/LogBegoneMod"
    private val isolatedOwner = "com/bettercontent/tests/IsolatedNativeLogBegonePredicate"

    private class Loader : ClassLoader(LogBegoneBoundaryTest::class.java.classLoader) {
        fun define(bytes: ByteArray): Class<*> = defineClass(null, bytes, 0, bytes.size)
    }

    @Test fun pinnedNativeEmptyListsCannotDenyNonNullMessagesAtRootOrPresentationBoundary() {
        val filename = "Log-Begone-Forge-1.20.1-1.0.8.jar"
        val path = pinnedModJar("Log-Begone-Forge-1.20.1-1.0.8.jar")
        val sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        assertEquals("3b9f1c7fea5e0bdbc6f06429e147bd0a7c08663d", sha1)
        val pin = Files.readString(root.resolve("mods/log-begone.pw.toml"))
        assertTrue(pin.contains("filename = \"$filename\""))
        assertTrue(pin.contains("hash-format = \"sha1\""))
        assertTrue(pin.contains("hash = \"$sha1\""))
        ZipFile(path.toFile()).use { jar ->
            fun bytes(entry: String) = jar.getInputStream(requireNotNull(jar.getEntry(entry))).use { it.readBytes() }
            val writer = ClassWriter(0)
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, isolatedOwner, null, "java/lang/Object", null)
            listOf("phrases", "regex").forEach {
                writer.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, it, "Ljava/util/List;", null, null).visitEnd()
            }
            val substitutions = mutableListOf<String>()
            ClassReader(bytes("$nativeOwner.class")).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (name != "shouldFilterMessage") return null
                    assertEquals("(Ljava/lang/String;)Z", descriptor)
                    return object : MethodVisitor(Opcodes.ASM9, writer.visitMethod(access, name, descriptor, signature, exceptions)) {
                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                            when {
                                owner == "mod/azure/logbegone/LogBegoneConfig" && name == "COMMON" -> {
                                    assertEquals(Opcodes.GETSTATIC, opcode)
                                    substitutions += "COMMON"
                                }
                                owner == "mod/azure/logbegone/LogBegoneConfig\$Common" && name in setOf("phrases", "regex") -> {
                                    assertEquals(Opcodes.GETFIELD, opcode)
                                    substitutions += name
                                    super.visitFieldInsn(Opcodes.GETSTATIC, isolatedOwner, name, "Ljava/util/List;")
                                }
                                else -> error("unexpected native predicate field: $owner.$name")
                            }
                        }
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            if (owner == "net/minecraftforge/common/ForgeConfigSpec\$ConfigValue" && name == "get") {
                                substitutions += "get"
                            } else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }, 0)
            writer.visitEnd()
            assertEquals(listOf("COMMON", "phrases", "get", "COMMON", "regex", "get"), substitutions)
            val loader = Loader()
            val predicate = loader.define(writer.toByteArray())
            val phrases = predicate.getField("phrases")
            val regex = predicate.getField("regex")
            val shouldFilter = predicate.getMethod("shouldFilterMessage", String::class.java)
            phrases.set(null, emptyList<String>())
            regex.set(null, emptyList<String>())

            // Native Log4j adapter is executed unchanged except the owner of its predicate call.
            val adapterWriter = ClassWriter(0)
            var predicateCalls = 0
            ClassReader(bytes("mod/azure/logbegone/JavaUtilLog4jFilter.class")).accept(object : ClassVisitor(Opcodes.ASM9, adapterWriter) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            if (owner == nativeOwner && name == "shouldFilterMessage") {
                                predicateCalls++
                                super.visitMethodInsn(opcode, isolatedOwner, name, descriptor, isInterface)
                            } else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }, 0)
            assertEquals(2, predicateCalls, "Both JUL and Log4j adapters must share the inspected predicate")
            val filter = loader.define(adapterWriter.toByteArray()).getConstructor().newInstance() as Filter
            listOf("", "Empty height range:", "Better Caves expected a ServerLevel, but got:DhLitWorldGenRegion",
                "Liquid regions will not be generated for this chunk. This may be fatal!",
                "Recipe count failed to load registry", "Mod 'cold_sweat' took 3.5 s to run a deferred task.",
                "benign compatibility warning", "exception\n\tat native.fixture(Frame.java:1)").forEach { message ->
                assertEquals(false, shouldFilter.invoke(null, message), message)
                listOf(Level.INFO, Level.WARN, Level.ERROR, Level.FATAL).forEach { level ->
                    val event = Log4jLogEvent.newBuilder().setLoggerName("native.fixture").setLevel(level)
                        .setMessage(SimpleMessage(message)).build()
                    assertEquals(Filter.Result.NEUTRAL, filter.filter(event), "$level $message")
                }
            }
            // Positive controls ensure the actual native branch logic was retained, not stubbed out.
            phrases.set(null, listOf("Empty height range:"))
            assertEquals(true, shouldFilter.invoke(null, "Empty height range: 0"))
            phrases.set(null, emptyList<String>())
            regex.set(null, listOf(".*This may be fatal!"))
            assertEquals(true, shouldFilter.invoke(null, "Liquid regions. This may be fatal!"))
            regex.set(null, emptyList<String>())
            assertEquals(true, shouldFilter.invoke(null, null), "Native null-input behavior remains filtered; real formatted events are non-null")
        }
    }
}
