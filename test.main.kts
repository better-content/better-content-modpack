#!/usr/bin/env kotlin

import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
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
