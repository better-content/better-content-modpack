#!/usr/bin/env kotlin

import kotlin.system.exitProcess

// Kotlin's script cache can retain __FILE__ from a byte-identical copied checkout.
// The release runner supplies its owning checkout explicitly for exact-pair evidence.
val root = System.getenv("BC_PACK_TEST_ROOT")?.let { java.io.File(it).canonicalFile }
    ?: __FILE__.canonicalFile.parentFile

// Additive authoring/qualification facade. Existing lifecycle commands below retain their contracts.
if (args.firstOrNull() in setOf("campaign", "qualify", "discover", "convert-suite", "inspect-suite")) {
    val action = args[0]
    fun suiteUsage(): Nothing {
        System.err.println("usage: ./test.main.kts campaign --manifest PATH --client ZIP --server ZIP [--cases ID,ID]\n       ./test.main.kts qualify --manifest PATH --receipts PATH\n       ./test.main.kts discover --manifest PATH --client ZIP --server ZIP --output DIRECTORY\n       ./test.main.kts convert-suite --manifest V1_PATH --output NEW_JSON\n       ./test.main.kts inspect-suite --client ZIP --server ZIP --output NEW_JSON")
        exitProcess(2)
    }
    val accepted = when (action) {
        "campaign" -> setOf("--manifest", "--client", "--server", "--cases")
        "discover" -> setOf("--manifest", "--client", "--server", "--output")
        "convert-suite" -> setOf("--manifest", "--output")
        "inspect-suite" -> setOf("--client", "--server", "--output")
        else -> setOf("--manifest", "--receipts")
    }
    val required = if (action == "campaign") accepted - "--cases" else accepted
    val values = linkedMapOf<String, String>()
    var cursor = 1
    while (cursor < args.size) {
        val key = args[cursor]
        if (key !in accepted || key in values || cursor + 1 >= args.size || args[cursor + 1].isBlank() || args[cursor + 1].startsWith("--")) suiteUsage()
        values[key] = args[cursor + 1]
        cursor += 2
    }
    if (!values.keys.containsAll(required)) suiteUsage()
    val child = if (action in setOf("qualify", "convert-suite", "inspect-suite")) {
        // Evidence validation must not silently build anything. Prepare this classpath separately.
        val prepared = root.resolve("build/playtest-validator")
        val classpath = prepared.resolve("classpath.txt")
        val fingerprint = prepared.resolve("sources.sha256")
        if (!classpath.isFile || !fingerprint.isFile) {
            System.err.println("Qualification requires a separately compiled validator: ./gradlew preparePlaytestValidator (no tests). No build was started.")
            exitProcess(2)
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val sources = root.resolve("src/main").walkTopDown().filter { it.isFile }.toList() +
            listOf(root.resolve("build.gradle.kts"), root.resolve("test.main.kts"))
        sources.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach {
            digest.update((it.relativeTo(root).invariantSeparatorsPath + "\n").toByteArray(Charsets.UTF_8))
            digest.update(it.readBytes())
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != fingerprint.readText().trim()) {
            System.err.println("Prepared qualification validator is stale; compile it separately. No build was started.")
            exitProcess(2)
        }
        mutableListOf(java.io.File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", classpath.readText().trim(), "com.bettercontent.tests.suite.SuiteMainKt", root.absolutePath,
            action, values["--manifest"]?.let { java.io.File(it).canonicalPath }.orEmpty(),
            values["--client"]?.let { java.io.File(it).canonicalPath }.orEmpty(),
            values["--server"]?.let { java.io.File(it).canonicalPath }.orEmpty(), "",
            values["--receipts"]?.let { java.io.File(it).canonicalPath }.orEmpty(),
            values["--output"]?.let { java.io.File(it).canonicalPath }.orEmpty())
    } else {
        mutableListOf(root.resolve("gradlew").absolutePath, "--no-daemon", "--max-workers=2", "playtestSuite", "-PsuiteAction=$action").apply {
            mapOf("--manifest" to "suiteManifest", "--client" to "suiteClient", "--server" to "suiteServer").forEach { (flag, property) ->
                values[flag]?.let { add("-P$property=${java.io.File(it).canonicalPath}") }
            }
            values["--cases"]?.let { add("-PsuiteCases=$it") }
            values["--output"]?.let { add("-PsuiteOutput=${java.io.File(it).canonicalPath}") }
        }
    }
    exitProcess(ProcessBuilder(listOf(root.resolve("pack-test-lock.main.kts").absolutePath, "run", "test", action, "--") + child)
        .directory(root).inheritIO().start().waitFor())
}

fun usage(): Nothing {
    System.err.println("usage: ./test.main.kts smoke [--client ZIP --server ZIP] [--diagnose-from FAILED_RUN_JSON]\n       ./test.main.kts overworld-tele-soak [--client ZIP --server ZIP]\n       ./test.main.kts tele-soak [--client ZIP --server ZIP] [--diagnose-from FAILED_RUN_JSON]")
    exitProcess(2)
}
val tier = args.firstOrNull()?.takeIf { it in setOf("smoke", "overworld-tele-soak", "tele-soak") } ?: usage()
val options = linkedMapOf<String, String>()
var index = 1
while (index < args.size) {
    val option = args[index]
    if (option !in setOf("--client", "--server", "--diagnose-from") || option in options || index + 1 >= args.size) usage()
    options[option] = args[index + 1]
    index += 2
}
if (("--client" in options) != ("--server" in options)) usage()
if (tier == "overworld-tele-soak" && "--diagnose-from" in options) usage()
if (options.values.any { it.isBlank() }) usage()
// Standing full-pack EULA/world approval is recorded in AGENTS.md; no per-run grant is needed.
val command = mutableListOf(root.resolve("gradlew").absolutePath, "--no-daemon", "--max-workers=2", "packSession",
    "-PsessionTier=$tier", "-PsessionMinutes=0")
options["--client"]?.let { command += "-PsessionClient=${java.io.File(it).canonicalPath}" }
options["--server"]?.let { command += "-PsessionServer=${java.io.File(it).canonicalPath}" }
options["--diagnose-from"]?.let { command += "-PsessionDiagnoseFrom=${java.io.File(it).canonicalPath}" }
// The lock wrapper validates inherited tokens as well as acquiring fresh locks.
val status = ProcessBuilder(listOf(root.resolve("pack-test-lock.main.kts").absolutePath, "run", "test", tier, "--") + command)
    .directory(root).inheritIO().start().waitFor()
exitProcess(status)
