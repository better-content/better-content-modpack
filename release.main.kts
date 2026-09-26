#!/usr/bin/env kotlin

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
var jobs = 2
var skipTests = false
var target: String? = null
var retryOf: String? = null
var index = 0
while (index < args.size) {
    when (args[index]) {
        "--jobs" -> {
            if (index + 1 >= args.size) {
                System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]]")
                exitProcess(2)
            }
            jobs = args[index + 1].toIntOrNull() ?: 0
            index += 2
        }
        "--skip-tests" -> {
            skipTests = true
            index++
        }
        "--target" -> {
            target = args.getOrNull(index + 1) ?: error("--target needs a scenario")
            index += 2
        }
        "--retry-of" -> {
            retryOf = args.getOrNull(index + 1) ?: error("--retry-of needs a run ID")
            index += 2
        }
        else -> {
            System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]]")
            exitProcess(2)
        }
    }
}
if (jobs !in 1..4) {
    System.err.println("release jobs must be between 1 and 4")
    exitProcess(2)
}
val validTarget = target == null || target in setOf("join", "fonts", "dimensions", "world-save") ||
    (target!!.startsWith("font:") && target!!.removePrefix("font:") in setOf("ratlantis", "bumblezone", "aether", "nether")) ||
    (target!!.startsWith("dimension:") && Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(target!!.removePrefix("dimension:")) &&
        target!!.removePrefix("dimension:") !in setOf("rats:ratlantis", "the_bumblezone:the_bumblezone", "aether:the_aether", "minecraft:the_nether"))
if (!validTarget || args.count { it == "--jobs" } > 1 || args.count { it == "--skip-tests" } > 1 ||
    args.count { it == "--target" } > 1 || args.count { it == "--retry-of" } > 1 ||
    (skipTests && target != null) || (retryOf != null && target == null) ||
    (retryOf != null && (!Regex("\\d{8}T\\d{6}Z-\\d+").matches(retryOf!!) ||
        !root.resolve("generated/test-evidence/$retryOf").isDirectory))) {
    System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests|--target TARGET [--retry-of RUN_ID]]")
    exitProcess(2)
}

if (System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val command = mutableListOf(
        root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "release", if (skipTests) "release-skip-tests" else target?.let { "target:$it" } ?: "dist", "--", __FILE__.absolutePath,
    )
    command.addAll(args)
    exitProcess(ProcessBuilder(command).directory(root).inheritIO().start().waitFor())
}

val runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    .format(java.time.Instant.now()) + "-" + ProcessHandle.current().pid()
val evidence = root.resolve("generated/test-evidence/$runId")
evidence.mkdirs()
evidence.resolve("release-request.txt").writeText(
    "run_id=$runId\njobs=$jobs\ntier=${if (target == null) "dist" else "targeted"}\ntarget=${target ?: ""}\nretry_of=${retryOf ?: ""}\nskip_tests=$skipTests\ncommand=./release.main.kts --jobs $jobs${if (skipTests) " --skip-tests" else ""}${target?.let { " --target $it" } ?: ""}\nstarted_at=${java.time.Instant.now()}\n",
)

fun run(vararg command: String): Int = ProcessBuilder(*command)
    .directory(root)
    .inheritIO()
    .apply { environment()["BC_TEST_RUN_ID"] = runId; target?.let { environment()["BC_RELEASE_TARGET"] = it } }
    .start()
    .waitFor()

val preparationCommand = mutableListOf(
    root.resolve("gradlew").absolutePath,
    "--no-daemon",
    "prepareFreshDist",
    "-PreleaseJobs=$jobs",
)
if (skipTests) preparationCommand += "-PreleaseSkipTests=true"
val preparation = run(*preparationCommand.toTypedArray())
if (preparation != 0) {
    println("release run: $runId")
    println("evidence: ${evidence.absolutePath}")
    exitProcess(preparation)
}

val testArgs = mutableListOf(root.resolve("test.main.kts").absolutePath, if (target == null || target == "join") "dist" else "debug")
target?.let { testArgs += listOf("--target", it) }
retryOf?.let { testArgs += listOf("--retry-of", it) }
val tests = if (skipTests) 0 else ProcessBuilder(testArgs)
    .directory(root)
    .inheritIO()
    .start()
    .waitFor()
println("release run: $runId")
println("evidence: ${evidence.absolutePath}")
if (skipTests) println("tests: skipped by explicit request")
exitProcess(tests)
