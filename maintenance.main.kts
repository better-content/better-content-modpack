#!/usr/bin/env kotlin

import kotlin.system.exitProcess

// Cleanup intentionally does not start Gradle: its caches are disposable targets.
// The installed Kotlin SDK remains an operating input, never a cleanup target.
val root = __FILE__.canonicalFile.parentFile
val command = listOf("python3", "-B", root.resolve("scripts/workspace-maintenance.py").absolutePath) + args
exitProcess(ProcessBuilder(command).directory(root).inheritIO().start().waitFor())
