package com.bettercontent.tests.release

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Qualification is supplied by the owning DAG's completed dependency record, never inferred from bytes. */
internal enum class ProviderInputMode { QUALIFIED, EXPLICIT_UNTESTED }

internal data class ProviderArtifactInput(
    val owner: String,
    val source: Path,
    val relativePath: String,
    val expectedSha256: String,
    val provenance: String,
    val qualified: Boolean,
)

internal data class ProviderInputView(
    val directory: Path,
    val manifest: Path,
    val digest: String,
    val mode: ProviderInputMode,
)

/**
 * A small byte boundary for the existing provider DAG. The caller supplies the complete required
 * inventory (including transitive/runtime/support inputs); this utility does not discover providers,
 * select receipts, or promote untested artifacts. Build and audit must consume the returned directory.
 * All failed/interrupted destinations and partial files are deliberately retained.
 */
internal object ProviderInputs {
    private const val MANIFEST = "provider-inputs.tsv"
    private const val DIGEST = "provider-inputs.sha256"
    private val hashPattern = Regex("[a-f0-9]{64}")

    fun snapshot(
        destination: Path,
        required: List<ProviderArtifactInput>,
        mode: ProviderInputMode = ProviderInputMode.QUALIFIED,
        checkActive: () -> Unit = {},
    ): ProviderInputView {
        val directory = destination.toAbsolutePath().normalize()
        safeAncestors(directory)
        val entries = required.toList().sortedBy { it.relativePath }
        val names = mutableSetOf<String>()
        entries.forEach { input ->
            text(input.owner); text(input.provenance)
            require(hashPattern.matches(input.expectedSha256)) { "invalid expected input hash" }
            require(mode == ProviderInputMode.EXPLICIT_UNTESTED || input.qualified) {
                "unqualified provider input: ${input.owner}/${input.relativePath}"
            }
            val relative = relative(input.relativePath)
            require(listOf(MANIFEST, DIGEST).none { relative.startsWith(it) }) { "reserved input name" }
            require(names.add(relative.toString())) { "provider input collision: $relative" }
        }
        val paths = names.map { Path.of(it) }
        require(paths.none { a -> paths.any { b -> a != b && b.startsWith(a) } }) { "input path collision" }
        checkActive()
        Files.createDirectory(directory) // Fresh candidate only; never merge or reuse a partial view.
        entries.forEach { input ->
            checkActive()
            val target = directory.resolve(relative(input.relativePath))
            Files.createDirectories(target.parent)
            copyVerified(input.source, target, input.expectedSha256, checkActive)
            readOnly(target, false)
        }
        val bytes = buildString {
            append("bc.provider_inputs.v1\t").append(mode.name).append('\n')
            entries.forEach { input ->
                append(input.relativePath).append('\t').append(input.expectedSha256).append('\t')
                    .append(input.owner).append('\t').append(input.qualified).append('\t')
                    .append(input.provenance).append('\n')
            }
        }.toByteArray(Charsets.UTF_8)
        val manifest = directory.resolve(MANIFEST)
        checkActive()
        writeForced(manifest, bytes)
        val inputDigest = digest(bytes)
        val digestFile = directory.resolve(DIGEST)
        writeForced(digestFile, "$inputDigest  $MANIFEST\n".toByteArray(Charsets.UTF_8))
        readOnly(manifest, false)
        readOnly(digestFile, false)
        // Recheck all exact copied bytes before sealing, not just the source's pre-copy identity.
        entries.forEach { require(sha256(directory.resolve(it.relativePath)) == it.expectedSha256) }
        Files.walk(directory).use { stream ->
            stream.filter { Files.isDirectory(it, NOFOLLOW_LINKS) }
                .sorted(Comparator.reverseOrder()).forEach { readOnly(it, true); forceDirectory(it) }
        }
        checkActive()
        return ProviderInputView(directory, manifest, inputDigest, mode)
    }

    /**
     * Revalidate exactly the captured sealed view, not current global stages or new qualifications.
     * Callers running a subprocess must suppress verification failures onto its original Throwable
     * on a failed invocation, rather than replacing that original failure in an unguarded finally.
     */
    fun verify(view: ProviderInputView) {
        val directory = view.directory.toAbsolutePath().normalize()
        val manifest = directory.resolve(MANIFEST)
        val companion = directory.resolve(DIGEST)
        require(view.manifest == manifest && hashPattern.matches(view.digest)) { "invalid captured input view" }
        safeAncestors(directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "missing provider input directory" }
        safeAncestors(manifest); safeAncestors(companion)
        attributes(manifest); attributes(companion)
        val bytes = Files.readAllBytes(manifest)
        require(digest(bytes) == view.digest) { "captured provider input manifest changed" }
        val contents = bytes.toString(Charsets.UTF_8)
        require(contents.toByteArray(Charsets.UTF_8).contentEquals(bytes) && contents.endsWith('\n')) {
            "invalid provider input manifest encoding"
        }
        val lines = contents.dropLast(1).split('\n')
        require(lines.first() == "bc.provider_inputs.v1\t${view.mode.name}") { "provider input schema/mode changed" }
        val expected = linkedMapOf<Path, String>()
        val directories = mutableSetOf(directory)
        lines.drop(1).forEach { line ->
            val fields = line.split('\t')
            require(fields.size == 5 && hashPattern.matches(fields[1]) && fields[3] in setOf("true", "false")) {
                "invalid provider input manifest entry"
            }
            text(fields[2]); text(fields[4])
            val name = relative(fields[0])
            require(listOf(MANIFEST, DIGEST).none { name.startsWith(it) }) { "reserved provider input entry" }
            require(expected.put(directory.resolve(name), fields[1]) == null) { "duplicate provider input entry" }
            var parent = directory.resolve(name).parent
            while (parent != directory) {
                directories.add(parent)
                parent = parent.parent
            }
        }
        require(expected.keys.map { directory.relativize(it).toString() } ==
            expected.keys.map { directory.relativize(it).toString() }.sorted()) { "noncanonical provider input order" }
        require(expected.keys.none { it in directories }) { "provider input file/directory collision" }
        expected[manifest] = view.digest
        expected[companion] = digest("${view.digest}  $MANIFEST\n".toByteArray(Charsets.UTF_8))
        val seen = mutableSetOf<Path>()
        // Files.walk does not follow symlinks. Every entry must be an explicitly bound regular
        // file or a necessary ancestor directory; even empty unrelated directories are rejected.
        Files.walk(directory).use { paths ->
            paths.forEach { path ->
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                when {
                    attributes.isDirectory -> require(path in directories) { "unbound provider input directory: $path" }
                    attributes.isRegularFile -> {
                        val hash = expected[path] ?: error("unbound provider input file: $path")
                        require(sha256(path) == hash) { "consumed provider input changed: $path" }
                        seen.add(path)
                    }
                    else -> error("symlink/special provider input entry: $path")
                }
            }
        }
        require(seen == expected.keys) { "missing bound provider input" }
        require(sha256(manifest) == view.digest && sha256(companion) == expected[companion]) {
            "provider input manifest/digest changed during verification"
        }
    }

    /**
     * Atomic, no-overwrite publication of fully forced/hashed bytes after the caller's strict gate.
     * Linux renameat2(RENAME_NOREPLACE) moves the private non-JAR partial to the final name; unlike
     * JDK ATOMIC_MOVE it cannot replace a raced target. Failed partials and helper logs are retained.
     * Unsupported kernels/filesystems fail closed, with no hard-link or non-atomic copy fallback.
     */
    fun publish(
        source: Path,
        target: Path,
        expectedSha256: String,
        strictGate: () -> Unit,
        checkActive: () -> Unit = {},
    ): Path {
        require(hashPattern.matches(expectedSha256)) { "invalid publication hash" }
        val final = target.toAbsolutePath().normalize()
        safeAncestors(final)
        require(!Files.exists(final, NOFOLLOW_LINKS)) { "publication target already exists: $final" }
        checkActive()
        val partial = final.resolveSibling(".${final.fileName}.${UUID.randomUUID()}.partial")
        copyVerified(source, partial, expectedSha256, checkActive)
        strictGate() // Preserve the exact original exception; no observer/wrapper verdict substitution.
        checkActive()
        safeAncestors(final)
        safeAncestors(partial)
        require(sha256(partial) == expectedSha256) { "publication partial changed after strict gate" }
        readOnly(partial, false)
        forceDirectory(final.parent)
        checkActive()
        renameNoReplace(partial, final, checkActive)
        forceDirectory(final.parent)
        return final
    }

    // This Linux-specific lane already requires Python. -I -S ignores ambient hooks/site imports;
    // the constant helper does not spawn descendants, open a shell, or mutate anything but rename.
    private val renameScript = """
        import ctypes, errno, os, sys
        libc = ctypes.CDLL(None, use_errno=True)
        rename = libc.renameat2
        rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
        rename.restype = ctypes.c_int
        if rename(-100, os.fsencode(sys.argv[1]), -100, os.fsencode(sys.argv[2]), 1) != 0:
            code = ctypes.get_errno()
            print(str(code) + ': ' + os.strerror(code), file=sys.stderr)
            sys.exit(17 if code == errno.EEXIST else 1)
    """.trimIndent()

    private fun renameNoReplace(partial: Path, final: Path, checkActive: () -> Unit) {
        val log = partial.resolveSibling("${partial.fileName}.rename.log")
        // Reserve the retained log first, so ProcessBuilder never truncates another writer's log.
        Files.createFile(log)
        val process = ProcessBuilder("python3", "-I", "-S", "-c", renameScript,
            partial.toString(), final.toString())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start()
        var failure: Throwable? = null
        try {
            // This is a helper safety cap, not an empirically selected test/runtime deadline.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                checkActive()
                check(System.nanoTime() < deadline) { "atomic publication helper timed out; see $log" }
            }
            when (process.exitValue()) {
                0 -> Unit
                17 -> throw FileAlreadyExistsException(final.toString())
                else -> error("atomic no-replace publication failed; partial retained: $partial; see $log")
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            // No shell or descendant producer exists. Bound forced termination/reaping separately,
            // preserving the caller's exact original Throwable and interrupted status.
            val interrupted = Thread.interrupted()
            try {
                if (process.isAlive) process.destroyForcibly()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    val cleanup = IllegalStateException("publication helper cleanup incomplete: pid=${process.pid()}; $log")
                    if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
                }
            } catch (cleanup: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            } finally {
                if (interrupted || failure is InterruptedException) Thread.currentThread().interrupt()
            }
        }
    }

    private fun copyVerified(source: Path, target: Path, expected: String, checkActive: () -> Unit) {
        val src = source.toAbsolutePath().normalize()
        safeAncestors(src)
        val before = attributes(src)
        require(sha256(src) == expected) { "source input hash mismatch: $src" }
        safeAncestors(target)
        FileChannel.open(src, READ, NOFOLLOW_LINKS).use { input ->
            FileChannel.open(target, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { output ->
                val buffer = ByteBuffer.allocate(64 * 1024)
                while (true) {
                    checkActive()
                    buffer.clear()
                    if (input.read(buffer) < 0) break
                    buffer.flip()
                    while (buffer.hasRemaining()) output.write(buffer)
                }
                output.force(true)
            }
        }
        safeAncestors(src)
        val after = attributes(src)
        require(before.fileKey() == after.fileKey() && before.size() == after.size() &&
            before.lastModifiedTime() == after.lastModifiedTime() && sha256(src) == expected) {
            "source input changed while copying: $src"
        }
        safeAncestors(target)
        require(sha256(target) == expected) { "copied input hash mismatch: $target" }
    }

    private fun attributes(path: Path): BasicFileAttributes =
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS).also {
            require(it.isRegularFile) { "provider input is not a regular file: $path" }
        }

    private fun safeAncestors(path: Path) {
        var part: Path? = path.toAbsolutePath()
        while (part != null) {
            require(!Files.isSymbolicLink(part)) { "symlink provider path: $part" }
            part = part.parent
        }
    }

    private fun relative(value: String): Path {
        text(value)
        val path = Path.of(value)
        require(!path.isAbsolute && value == path.normalize().toString() &&
            path.nameCount > 0 && path.none { it.toString() in setOf("..", ".", "") } &&
            '\\' !in value) { "unsafe provider input path: $value" }
        return path
    }

    private fun text(value: String) {
        require(value.isNotBlank() && value.none { it.isISOControl() }) { "blank/unsafe input metadata" }
    }

    private fun readOnly(path: Path, directory: Boolean) {
        val permissions = mutableSetOf(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ)
        if (directory) permissions.addAll(setOf(PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE))
        Files.setPosixFilePermissions(path, permissions)
    }

    private fun writeForced(path: Path, bytes: ByteArray) {
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }

    private fun sha256(path: Path): String {
        attributes(path)
        val hash = MessageDigest.getInstance("SHA-256")
        FileChannel.open(path, READ, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.allocate(64 * 1024)
            while (true) {
                buffer.clear()
                if (channel.read(buffer) < 0) break
                buffer.flip()
                hash.update(buffer)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
