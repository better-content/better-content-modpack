#!/usr/bin/env kotlin

import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
fun usage(): Nothing {
    System.err.println("usage: ./test.main.kts smoke [--client ZIP --server ZIP]\n       ./test.main.kts soak --minutes N [--client ZIP --server ZIP]")
    exitProcess(2)
}
val tier = args.firstOrNull()?.takeIf { it in setOf("smoke", "soak") } ?: usage()
val options = linkedMapOf<String, String>()
var index = 1
while (index < args.size) {
    val option = args[index]
    if (option !in setOf("--client", "--server", "--minutes") || option in options || index + 1 >= args.size) usage()
    options[option] = args[index + 1]
    index += 2
}
if (("--client" in options) != ("--server" in options)) usage()
val minutes = options["--minutes"]?.toLongOrNull()
if ((tier == "smoke" && "--minutes" in options) || (tier == "soak" && (minutes == null || minutes !in 1..10080))) usage()
if (options.values.any { it.isBlank() }) usage()
val command = mutableListOf(root.resolve("gradlew").absolutePath, "--no-daemon", "--max-workers=2", "packSession",
    "-PsessionTier=$tier", "-PsessionMinutes=${minutes ?: 0}")
options["--client"]?.let { command += "-PsessionClient=${java.io.File(it).canonicalPath}" }
options["--server"]?.let { command += "-PsessionServer=${java.io.File(it).canonicalPath}" }
// The lock wrapper validates inherited tokens as well as acquiring fresh locks.
val status = ProcessBuilder(listOf(root.resolve("pack-test-lock.main.kts").absolutePath, "run", "test", tier, "--") + command)
    .directory(root).inheritIO().start().waitFor()
exitProcess(status)
