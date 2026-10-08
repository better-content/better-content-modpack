package com.bettercontent.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object JournalCandidateExclusion {
    private fun forbiddenName(name: String) = name.contains("better-journal-test-support", ignoreCase = true) ||
        name.contains("better_journal_test_support", ignoreCase = true)

    fun validate(candidate: Path) {
        ZipFile(candidate.toFile()).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                require(!forbiddenName(entry.name)) { "candidate contains journal test support: ${entry.name}" }
                if (entry.name.endsWith(".jar")) zip.getInputStream(entry).use { input ->
                    ZipInputStream(input).use { jar ->
                        while (true) {
                            val nested = jar.nextEntry ?: break
                            if (nested.name == "META-INF/mods.toml") {
                                val toml = jar.readBytes().toString(Charsets.UTF_8)
                                require("better_journal_test_support" !in toml) { "candidate contains renamed journal support: ${entry.name}" }
                            }
                        }
                    }
                }
                if (entry.name.endsWith(".toml") || entry.name.endsWith("manifest.json")) {
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    require(!forbiddenName(text)) { "candidate manifest references journal support: ${entry.name}" }
                }
            }
        }
    }

    fun validateDirectory(mods: Path) {
        Files.list(mods).use { files -> files.filter { Files.isRegularFile(it) }.forEach { file ->
            require(!forbiddenName(file.fileName.toString())) { "fixture unexpectedly already contains journal support" }
            if (file.toString().endsWith(".jar")) ZipFile(file.toFile()).use { jar ->
                jar.getEntry("META-INF/mods.toml")?.let { entry ->
                    val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }
                    require("better_journal_test_support" !in text) { "fixture contains renamed journal support" }
                }
            }
        } }
    }
}
