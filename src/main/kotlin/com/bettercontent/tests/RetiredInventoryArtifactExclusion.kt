package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Reject both retired and native disposable support, even if renamed. */
object RetiredInventoryArtifactExclusion {
    private val supportIds = listOf("better_journal_test_support", "better_native_inventory_test_support", "better_runtime_test_support")
    private fun forbiddenName(name: String) = supportIds.any { name.contains(it, ignoreCase = true) } ||
        listOf("better-journal-test-support", "better-native-inventory-test-support", "better-runtime-test-support").any { name.contains(it, ignoreCase = true) }
    private fun forbiddenClass(name: String) = name.startsWith("com/bettercontent/journaltestsupport/") ||
        name.startsWith("com/bettercontent/nativeinventorytestsupport/") ||
        name.startsWith("com/bettercontent/runtimetestsupport/") ||
        name.startsWith("com/bettercontent/betterjournalinventory/JournalMenu") ||
        name.startsWith("com/bettercontent/betterjournalinventory/JournalInventoryScreen") ||
        name.startsWith("com/bettercontent/betterjournalinventory/Storage") ||
        name.startsWith("com/bettercontent/betterjournalinventory/Portable")

    fun validate(candidate: Path) {
        ZipFile(candidate.toFile()).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                require(!forbiddenName(entry.name)) { "candidate contains disposable support: ${entry.name}" }
                if (entry.name.endsWith(".jar")) zip.getInputStream(entry).use { input ->
                    ZipInputStream(input).use { jar ->
                        while (true) {
                            val nested = jar.nextEntry ?: break
                            require(!forbiddenClass(nested.name)) { "candidate contains retired backend or fixture class: ${entry.name}/${nested.name}" }
                            if (nested.name == "META-INF/mods.toml") {
                                val toml = jar.readBytes().toString(Charsets.UTF_8)
                                require(supportIds.none { it in toml }) { "candidate contains renamed disposable support: ${entry.name}" }
                            }
                        }
                    }
                }
                if (entry.name.endsWith(".toml") || entry.name.endsWith("manifest.json")) {
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    require(!forbiddenName(text)) { "candidate manifest references disposable support: ${entry.name}" }
                }
            }
        }
    }

    fun validateDirectory(mods: Path) {
        Files.list(mods).use { files -> files.filter { Files.isRegularFile(it) }.forEach { file ->
            require(!forbiddenName(file.fileName.toString())) { "fixture unexpectedly already contains disposable support" }
            if (file.toString().endsWith(".jar")) ZipFile(file.toFile()).use { jar ->
                jar.getEntry("META-INF/mods.toml")?.let { entry ->
                    val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }
                    require(supportIds.none { it in text }) { "fixture contains renamed disposable support" }
                }
            }
        } }
    }
}
