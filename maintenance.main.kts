#!/usr/bin/env kotlin

import kotlin.system.exitProcess

val root = __FILE__.canonicalFile.parentFile
val (mode, apply) = when {
    args.toList() == listOf("audit") -> "audit" to false
    args.toList() == listOf("prune", "--apply") -> "prune" to true
    args.size == 3 && args[0] == "prune" && args[1] == "--resume" -> "resume" to true
    else -> {
        System.err.println("usage: ./maintenance.main.kts <audit|prune --apply|prune --resume TRANSACTION_ID>")
        exitProcess(2)
    }
}

val command = listOf(
    root.resolve("gradlew").absolutePath,
    "--no-daemon",
    "--quiet",
    "workspaceMaintenance",
    "-PmaintenanceMode=$mode",
    "-PmaintenanceApply=$apply",
) + if (mode == "resume") listOf("-PmaintenanceTransaction=" + args[2]) else emptyList()
val process = ProcessBuilder(command).directory(root).inheritIO().start()
exitProcess(process.waitFor())
