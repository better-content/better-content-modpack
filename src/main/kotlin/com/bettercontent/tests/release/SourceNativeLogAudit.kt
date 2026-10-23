package com.bettercontent.tests.release

import com.bettercontent.tests.CandidateBinding
import com.bettercontent.tests.Hashes
import com.bettercontent.tests.LogPolicy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.zip.GZIPInputStream

/**
 * Failure-visibility gate for the known SDK's current native output roots, after each source
 * verification invocation and before staging/annotation/checkpoint/deployment. This never supplies
 * native readiness, test counts, profile applicability or gameplay proof; those remain owning gates.
 * Prior outputs have already been retained outside the repository before a clean invocation.
 */
internal fun auditSourceNativeLogs(repository: Path, evidence: Path, staging: Path? = null) {
    require(repository.isAbsolute && evidence.isAbsolute) { "source native audit requires absolute repository/evidence paths" }
    CandidateBinding.requireOrdinary(repository)
    CandidateBinding.requireOrdinary(evidence)
    require(!evidence.startsWith(repository)) { "source native audit evidence must be outside the repository" }
    val roots = sourceVerificationProjects(repository).flatMap { project ->
        listOf(project.resolve("build/gametest"), project.resolve("run-gametest"), project.resolve("run-gametest-validation"))
    }
    val logs = linkedSetOf<Path>()
    for (root in roots) {
        CandidateBinding.requireOrdinary(root)
        if (!Files.exists(root, NOFOLLOW_LINKS)) continue
        require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "native output root is not a directory: $root" }
        Files.walk(root).use { paths ->
            paths.peek { path ->
                // A linked instance/log directory must not silently hide failed operations from walk().
                require(!Files.isSymbolicLink(path)) { "symlink in native output tree: $path" }
            }.filter { path ->
                path.parent?.fileName?.toString() == "logs" &&
                    (path.fileName.toString().endsWith(".log") || path.fileName.toString().endsWith(".log.gz"))
            }.forEach { logs.add(it) }
        }
    }
    val compatibility = staging?.let { com.bettercontent.tests.Oc2rCompatibilityEvidence.source(repository, it, logs) }
    Files.createDirectories(evidence)
    val rows = logs.sorted().map { original ->
        CandidateBinding.requireOrdinary(original)
        require(Files.isRegularFile(original, NOFOLLOW_LINKS)) { "native log is not an ordinary file: $original" }
        val before = Hashes.sha256(original)
        val audited = if (original.fileName.toString().endsWith(".gz")) {
            val expanded = evidence.resolve("expanded").resolve(repository.relativize(original).toString() + ".decoded.log")
            CandidateBinding.requireOrdinary(expanded)
            Files.createDirectories(expanded.parent)
            GZIPInputStream(Files.newInputStream(original)).use { input ->
                Files.newOutputStream(expanded, CREATE_NEW).use { output -> input.copyTo(output) }
            }
            expanded
        } else original
        val findings = LogPolicy.findings(listOf(audited), compatibility)
        require(Hashes.sha256(original) == before) { "native log changed during source audit: $original" }
        mapOf("original" to original.toString(), "sha256" to before, "bytes" to Files.size(original),
            "audited" to audited.toString(), "findings" to findings.map { mapOf("line" to it.line, "text" to it.text) })
    }
    val failed = rows.any { (it.getValue("findings") as List<*>).isNotEmpty() }
    val report = mapOf("schema" to "bc.source_native_log_audit.v1", "repository" to repository.toString(),
        "status" to if (failed) "failed" else if (rows.isEmpty()) "no-native-logs-observed" else "clean",
        "native_readiness_claim" to false, "oc2r_compatibility_bound" to (compatibility != null), "logs" to rows)
    Files.write(evidence.resolve("report.json"), jacksonObjectMapper().writeValueAsBytes(report), CREATE_NEW)
    require(!failed) { "source native log audit found failed operations; see ${evidence.resolve("report.json")}" }
}
