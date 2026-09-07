#!/usr/bin/env kotlin

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
var jobs = 2
var skipTests = false
var index = 0
while (index < args.size) {
    when (args[index]) {
        "--jobs" -> {
            if (index + 1 >= args.size) {
                System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests]")
                exitProcess(2)
            }
            jobs = args[index + 1].toIntOrNull() ?: 0
            index += 2
        }
        "--skip-tests" -> {
            skipTests = true
            index++
        }
        else -> {
            System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests]")
            exitProcess(2)
        }
    }
}
if (jobs !in 1..4) {
    System.err.println("release jobs must be between 1 and 4")
    exitProcess(2)
}
if (args.count { it == "--jobs" } > 1 || args.count { it == "--skip-tests" } > 1) {
    System.err.println("usage: ./release.main.kts [--jobs 1..4] [--skip-tests]")
    exitProcess(2)
}

if (System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
    val command = mutableListOf(
        root.resolve("pack-test-lock.main.kts").absolutePath,
        "run", "release", if (skipTests) "release-skip-tests" else "all", "--", __FILE__.absolutePath,
    )
    command.addAll(args)
    exitProcess(ProcessBuilder(command).directory(root).inheritIO().start().waitFor())
}

val runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    .format(java.time.Instant.now()) + "-" + ProcessHandle.current().pid()
val evidence = root.resolve("generated/test-evidence/$runId")
evidence.mkdirs()
evidence.resolve("release-request.txt").writeText(
    "run_id=$runId\njobs=$jobs\nskip_tests=$skipTests\ncommand=./release.main.kts --jobs $jobs${if (skipTests) " --skip-tests" else ""}\nstarted_at=${java.time.Instant.now()}\n",
)

fun run(vararg command: String): Int = ProcessBuilder(*command)
    .directory(root)
    .inheritIO()
    .apply { environment()["BC_TEST_RUN_ID"] = runId }
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

val tests = if (skipTests) 0 else run(root.resolve("test.main.kts").absolutePath, "all")
println("release run: $runId")
println("evidence: ${evidence.absolutePath}")
if (skipTests) println("tests: skipped by explicit request")
exitProcess(tests)
