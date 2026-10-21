package com.bettercontent.tests.release

import com.bettercontent.tests.CandidateBinding
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.attribute.BasicFileAttributes

/** Existing Forge SDK output layouts, not permission to classify arbitrary directories as disposable. */
private val sourceOutputNames = setOf("build", "run-gametest", "run-gametest-validation")

/**
 * Called under the whole Forge-cache lock before verification. Old native instances are retained;
 * compiled build trees are moved only when the existing verification command already requires clean.
 * There is no delete/copy-and-delete fallback. Cross-device or unsafe layouts fail before Gradle.
 */
internal fun retainSourceOutputsForVerification(repository: Path, evidence: Path, tracked: Set<String>, includeBuild: Boolean = true): Map<Path, Path> {
    require(repository.isAbsolute && evidence.isAbsolute) { "source retention requires absolute repository/evidence paths" }
    CandidateBinding.requireOrdinary(repository)
    CandidateBinding.requireOrdinary(evidence)
    require(!evidence.startsWith(repository)) { "source output retention must be outside the source repository" }
    val projects = sourceVerificationProjects(repository)
    val destination = evidence.resolve("prior-source-outputs").resolve(repository.fileName.toString())
    val names = sourceOutputNames.filter { includeBuild || it != "build" }
    val moves = projects.flatMap { project -> names.map(project::resolve) }
        .filter { Files.exists(it, NOFOLLOW_LINKS) }.sortedBy { repository.relativize(it).toString() }
        .associateWith { destination.resolve(repository.relativize(it)) }
    val receipt = evidence.resolve("retained-source-outputs.tsv")
    CandidateBinding.requireOrdinary(receipt)
    require(!Files.exists(receipt, NOFOLLOW_LINKS) || Files.isRegularFile(receipt, NOFOLLOW_LINKS)) { "retention receipt is not a regular file" }
    // Validate the complete operation before moving anything. Authored inputs and links never become outputs.
    for ((source, target) in moves) {
        CandidateBinding.requireOrdinary(source)
        CandidateBinding.requireOrdinary(target)
        require(Files.isDirectory(source, NOFOLLOW_LINKS)) { "source output is not an ordinary directory: $source" }
        val relative = repository.relativize(source).toString().replace('\\', '/')
        require(tracked.none { it == relative || it.startsWith("$relative/") }) { "refusing to relocate tracked/authored source input: $source" }
        require(!Files.exists(target, NOFOLLOW_LINKS)) { "retained source output already exists: $target" }
        var ancestor = target.parent
        while (!Files.exists(ancestor, NOFOLLOW_LINKS)) ancestor = requireNotNull(ancestor.parent)
        require(Files.isDirectory(ancestor, NOFOLLOW_LINKS) && Files.getFileStore(source) == Files.getFileStore(ancestor)) {
            "source retention requires an ordinary same-filesystem destination: $target"
        }
    }
    for ((source, target) in moves) {
        Files.createDirectories(target.parent)
        CandidateBinding.requireOrdinary(target.parent)
        Files.move(source, target, ATOMIC_MOVE)
        Files.writeString(receipt, "$source\t$target\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
    }
    return moves
}

/** Same bounded SDK project discovery for retention and the post-verification native log audit. */
internal fun sourceVerificationProjects(repository: Path): Set<Path> {
    CandidateBinding.requireOrdinary(repository)
    val projects = linkedSetOf(repository)
    Files.walkFileTree(repository, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (dir != repository) {
                val name = dir.fileName.toString()
                if (name.startsWith(".") || name in sourceOutputNames || name == "run" || name.startsWith("run-") ||
                    name in setOf("src", "gradle", "docs", "generated", "history", "workspace_artifacts")) return FileVisitResult.SKIP_SUBTREE
            }
            if (Files.isRegularFile(dir.resolve("build.gradle")) || Files.isRegularFile(dir.resolve("build.gradle.kts"))) projects.add(dir)
            return FileVisitResult.CONTINUE
        }
    })
    return projects
}
