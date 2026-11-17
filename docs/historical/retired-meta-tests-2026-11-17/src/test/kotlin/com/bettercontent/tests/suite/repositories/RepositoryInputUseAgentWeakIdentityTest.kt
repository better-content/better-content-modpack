package com.bettercontent.tests.suite.repositories

import java.lang.ref.WeakReference
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Unrun source regression: compiles the nonshipping observer only during a separately approved
 * future TEST phase; no helper JAR, Forge, worker or real world is created by authoring this. */
class RepositoryInputUseAgentWeakIdentityTest {
    @TempDir lateinit var temporary: Path
    @Test fun `identity IDs ignore ClassLoader equals and never recycle retired weak keys`() {
        val source = Path.of("scripts/playtest-repository-input-use/RepositoryInputUseAgent.java")
        val classes = Files.createDirectory(temporary.resolve("observer-classes"))
        assertEquals(0, requireNotNull(ToolProvider.getSystemJavaCompiler()).run(null, null, null,
            "--release", "17", "-d", classes.toString(), source.toString()))
        URLClassLoader(arrayOf(classes.toUri().toURL()), null).use { observer ->
            val agent = observer.loadClass("com.bettercontent.playtest.repositoryinput.RepositoryInputUseAgent")
            val id = agent.getDeclaredMethod("loaderId", ClassLoader::class.java).apply { trySetAccessible() }
            val table = agent.getDeclaredField("loaderIds").apply { trySetAccessible() }
            fun loader() = object : ClassLoader(null) {
                override fun equals(other: Any?) = other is ClassLoader // hostile equality override
                override fun hashCode() = 1
            }
            val first = loader(); val second = loader(); val third = loader()
            val one = id.invoke(null, first) as Int
            val two = id.invoke(null, second) as Int
            assertTrue(one > 0 && two > one, "distinct loaders with equal hash/equals require distinct IDs")
            assertEquals(one, id.invoke(null, first))
            val refs = (table.get(null) as Map<*, *>).keys
            assertFalse(refs.any { it is ClassLoader }, "observer must not retain loader keys strongly")
            val retired = refs.filterIsInstance<WeakReference<*>>().single { it.get() === first }
            retired.clear(); assertTrue(retired.enqueue()) // deterministic queue drain; no GC timing assumption
            val three = id.invoke(null, third) as Int
            assertTrue(three > two && (table.get(null) as Map<*, *>).size == 2,
                "retirement must drop metadata, never reuse a prior loader ID")
        }
    }
}
