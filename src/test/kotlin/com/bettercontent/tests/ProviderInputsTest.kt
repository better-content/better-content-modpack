package com.bettercontent.tests

import com.bettercontent.tests.release.ProviderArtifactInput
import com.bettercontent.tests.release.ProviderInputMode
import com.bettercontent.tests.release.ProviderInputs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Tag("fast")
class ProviderInputsTest {
    // Retain success and failure fixtures, including partials; do not use auto-deleting @TempDir.
    private fun root(): Path = Files.createTempDirectory(
        Files.createDirectories(Path.of("/home/dev/.tmp/provider-inputs-20261023-pX")), "case-")
    private fun hash(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun input(source: Path, name: String = "provider.jar", qualified: Boolean = true) =
        ProviderArtifactInput("provider", source, name, hash(source), "completed-stage-id", qualified)
    private fun source(root: Path): Path = Files.writeString(root.resolve("source.jar"), "qualified original bytes")

    @Test fun exactViewIgnoresMutableUnrelatedAndPartialGlobalPublication() {
        val root = root()
        val global = Files.createDirectory(root.resolve("global"))
        val src = Files.writeString(global.resolve("provider.jar"), "initial bytes")
        val support = Files.writeString(global.resolve("support.json"), "gate support")
        val view = ProviderInputs.snapshot(root.resolve("view"), listOf(input(src), input(support, "support/proof.json")))
        Files.writeString(src, "changed global publication")
        Files.writeString(global.resolve("unrelated.jar"), "unrelated")
        Files.writeString(global.resolve(".failed.partial"), "partial")
        assertEquals("initial bytes", Files.readString(view.directory.resolve("provider.jar")))
        assertEquals("gate support", Files.readString(view.directory.resolve("support/proof.json")))
        assertFalse(Files.exists(view.directory.resolve("unrelated.jar")))
        assertEquals(view.digest, hash(view.manifest))
        assertEquals("${view.digest}  provider-inputs.tsv\n",
            Files.readString(view.directory.resolve("provider-inputs.sha256")))
        assertTrue(Files.readString(view.manifest).contains("QUALIFIED"))
        assertFalse(Files.getPosixFilePermissions(view.directory.resolve("provider.jar"))
            .contains(PosixFilePermission.OWNER_WRITE))
        assertFalse(Files.isSameFile(src, view.directory.resolve("provider.jar")))
        assertThrows(Exception::class.java) { ProviderInputs.snapshot(view.directory, emptyList()) }
    }

    @Test fun mismatchMissingAndUnqualifiedInputsFailClosed() {
        val root = root(); val src = source(root); val entry = input(src)
        Files.writeString(src, "changed")
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("mismatch"), listOf(entry))
        }
        assertThrows(Exception::class.java) {
            ProviderInputs.snapshot(root.resolve("missing"), listOf(entry.copy(source = root.resolve("missing.jar"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("unqualified"), listOf(input(src, qualified = false)))
        }
        val untested = ProviderInputs.snapshot(root.resolve("untested"), listOf(input(src, qualified = false)),
            ProviderInputMode.EXPLICIT_UNTESTED)
        val manifest = Files.readString(untested.manifest)
        assertTrue(manifest.contains("EXPLICIT_UNTESTED")); assertTrue(manifest.contains("\tfalse\t"))
    }

    @Test fun symlinksUnsafePathsAndCollisionsAreRejected() {
        val root = root(); val src = source(root)
        val link = Files.createSymbolicLink(root.resolve("link.jar"), src)
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("linked"), listOf(input(src).copy(source = link)))
        }
        val directoryLink = Files.createSymbolicLink(root.resolve("linked-root"), root)
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(directoryLink.resolve("view"), listOf(input(src)))
        }
        listOf("../escape.jar", "/absolute.jar", "a/../b.jar", "a\\b.jar", "provider-inputs.tsv", "a//b.jar").forEachIndexed { i, name ->
            assertThrows(IllegalArgumentException::class.java) {
                ProviderInputs.snapshot(root.resolve("unsafe-$i"), listOf(input(src, name)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("collision"), listOf(input(src), input(src)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("nested-collision"), listOf(input(src, "a"), input(src, "a/b")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("special"), listOf(input(src).copy(source = root)))
        }
    }

    @Test fun changesDuringCopyAndCancellationRetainEvidence() {
        val root = root(); val src = source(root); val entry = input(src)
        val checks = AtomicInteger()
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.snapshot(root.resolve("changed"), listOf(entry), checkActive = {
                if (checks.incrementAndGet() == 3) Files.writeString(src, "mutated during copy")
            })
        }
        val failure = java.util.concurrent.CancellationException("cancel fixture")
        val calls = AtomicInteger()
        val actual = assertThrows(java.util.concurrent.CancellationException::class.java) {
            ProviderInputs.snapshot(root.resolve("cancelled"), listOf(input(src)), checkActive = {
                if (calls.incrementAndGet() == 3) throw failure
            })
        }
        assertSame(failure, actual)
        assertTrue(Files.isDirectory(root.resolve("cancelled")))
        assertFalse(Files.exists(root.resolve("cancelled/provider-inputs.tsv")))
    }

    @Test fun publicationIsGatedAtomicNoOverwriteAndRetainsPartials() {
        val root = root(); val src = source(root); val target = root.resolve("published.jar")
        val result = ProviderInputs.publish(src, target, hash(src), strictGate = {
            assertFalse(Files.exists(target))
            Files.list(root).use { paths ->
                assertTrue(paths.anyMatch { it.fileName.toString().endsWith(".partial") })
            }
        })
        assertEquals(target, result); assertEquals(hash(src), hash(target))
        assertEquals(1, (Files.getAttribute(target, "unix:nlink") as Number).toInt())
        Files.list(root).use { paths ->
            assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".partial") })
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.publish(src, target, hash(src), strictGate = { fail("must not gate existing final") })
        }
        val original = IllegalStateException("strict failure")
        val failedTarget = root.resolve("failed.jar")
        assertSame(original, assertThrows(IllegalStateException::class.java) {
            ProviderInputs.publish(src, failedTarget, hash(src), strictGate = { throw original })
        })
        assertFalse(Files.exists(failedTarget))
        Files.list(root).use { paths -> assertTrue(paths.anyMatch { it.fileName.toString().startsWith(".failed.jar.") }) }
    }

    @Test fun concurrentAndForeignPublicationNeverOverwrite() {
        val root = root(); val src = source(root); val target = root.resolve("race.jar")
        val ready = CountDownLatch(2); val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit<Boolean> {
                    try {
                        ProviderInputs.publish(src, target, hash(src), strictGate = {
                            ready.countDown(); check(go.await(10, TimeUnit.SECONDS))
                        })
                        true
                    } catch (_: java.nio.file.FileAlreadyExistsException) { false }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown()
            assertEquals(1, futures.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(hash(src), hash(target))
        } finally { go.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)) }
        val foreign = root.resolve("foreign.jar")
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            ProviderInputs.publish(src, foreign, hash(src), strictGate = { Files.writeString(foreign, "other writer") })
        }
        assertEquals("other writer", Files.readString(foreign))
    }

    @Test fun publicationRejectsMismatchAndPostGateMutation() {
        val root = root(); val src = source(root); val expected = hash(src)
        Files.writeString(src, "mismatched source")
        val mismatch = root.resolve("mismatch.jar")
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.publish(src, mismatch, expected, strictGate = { fail("hash mismatch must not gate") })
        }
        assertFalse(Files.exists(mismatch))
        val changed = root.resolve("changed.jar")
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.publish(src, changed, hash(src), strictGate = {
                Files.list(root).use { paths ->
                    val partial = paths.filter { it.fileName.toString().startsWith(".changed.jar.") }.findFirst().orElseThrow()
                    Files.writeString(partial, "tampered after copy")
                }
            })
        }
        assertFalse(Files.exists(changed))
    }

    @Test fun independentQualifiedSourceStagesAndTransitiveMissingInput() {
        val root = root(); val direct = source(root)
        val transitive = Files.writeString(root.resolve("transitive.jar"), "independently qualified")
        val inventory = listOf(input(direct).copy(provenance = "source-run/direct/completed"),
            input(transitive, "transitive.jar").copy(owner = "transitive", provenance = "source-run/transitive/completed"))
        val view = ProviderInputs.snapshot(root.resolve("source-view"), inventory)
        assertEquals("independently qualified", Files.readString(view.directory.resolve("transitive.jar")))
        val missing = inventory[1].copy(source = root.resolve("unproduced.jar"))
        assertThrows(Exception::class.java) {
            ProviderInputs.snapshot(root.resolve("missing-transitive"), listOf(inventory[0], missing))
        }
        val untested = ProviderInputs.snapshot(root.resolve("source-untested"), inventory, ProviderInputMode.EXPLICIT_UNTESTED)
        assertNotEquals(view.digest, untested.digest)
    }

    private fun writable(path: Path) {
        Files.setPosixFilePermissions(path, Files.getPosixFilePermissions(path) + PosixFilePermission.OWNER_WRITE)
    }

    @Test fun verifyAcceptsSealedViewAndRejectsConsumedByteMutation() {
        val root = root(); val src = source(root)
        val view = ProviderInputs.snapshot(root.resolve("verify"), listOf(input(src, "support/nested/proof.json")))
        ProviderInputs.verify(view)
        val copied = view.directory.resolve("support/nested/proof.json")
        writable(copied); Files.writeString(copied, "mutated by owning Gradle code")
        assertThrows(IllegalArgumentException::class.java) { ProviderInputs.verify(view) }
        // Unrelated global edits are not consulted by verify either.
        val clean = ProviderInputs.snapshot(root.resolve("clean"), listOf(input(src)))
        Files.writeString(src, "new global bytes")
        ProviderInputs.verify(clean)
    }

    @Test fun verifyRejectsUnboundFileAndUnnecessaryDirectory() {
        val root = root(); val src = source(root)
        val fileView = ProviderInputs.snapshot(root.resolve("extra-file"), listOf(input(src)))
        writable(fileView.directory)
        Files.writeString(fileView.directory.resolve("unrelated.jar"), "unbound")
        assertThrows(Exception::class.java) { ProviderInputs.verify(fileView) }
        val dirView = ProviderInputs.snapshot(root.resolve("extra-directory"), listOf(input(src)))
        writable(dirView.directory)
        Files.createDirectory(dirView.directory.resolve("unnecessary-empty-directory"))
        assertThrows(IllegalArgumentException::class.java) { ProviderInputs.verify(dirView) }
    }

    @Test fun verifyRejectsChangedBindingsMissingEntriesAndSymlinks() {
        val root = root(); val src = source(root)
        val view = ProviderInputs.snapshot(root.resolve("metadata"), listOf(input(src)))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderInputs.verify(view.copy(mode = ProviderInputMode.EXPLICIT_UNTESTED))
        }
        val companion = view.directory.resolve("provider-inputs.sha256")
        writable(companion); Files.writeString(companion, "forged digest companion")
        assertThrows(IllegalArgumentException::class.java) { ProviderInputs.verify(view) }
        val manifestView = ProviderInputs.snapshot(root.resolve("manifest"), listOf(input(src)))
        writable(manifestView.manifest); Files.writeString(manifestView.manifest, "forged manifest")
        assertThrows(IllegalArgumentException::class.java) { ProviderInputs.verify(manifestView) }
        val missing = ProviderInputs.snapshot(root.resolve("missing-entry"), listOf(input(src)))
        writable(missing.directory)
        // Move instead of delete, retaining the consumed artifact as diagnostic evidence.
        Files.move(missing.directory.resolve("provider.jar"), root.resolve("retained-missing-entry.jar"))
        assertThrows(IllegalArgumentException::class.java) { ProviderInputs.verify(missing) }
        val linked = ProviderInputs.snapshot(root.resolve("linked-entry"), listOf(input(src)))
        writable(linked.directory)
        Files.move(linked.directory.resolve("provider.jar"), root.resolve("retained-linked-entry.jar"))
        Files.createSymbolicLink(linked.directory.resolve("provider.jar"), src)
        assertThrows(Exception::class.java) { ProviderInputs.verify(linked) }
        ProviderInputs.verify(ProviderInputs.snapshot(root.resolve("empty-view"), emptyList()))
    }

    @Test fun cancelledPublicationHasPartialButNoFinal() {
        val root = root(); val src = source(root); val final = root.resolve("cancelled.jar")
        val cancel = java.util.concurrent.CancellationException("cancel before visibility")
        var gated = false
        assertSame(cancel, assertThrows(java.util.concurrent.CancellationException::class.java) {
            ProviderInputs.publish(src, final, hash(src), strictGate = { gated = true },
                checkActive = { if (gated) throw cancel })
        })
        assertFalse(Files.exists(final))
        Files.list(root).use { paths -> assertTrue(paths.anyMatch { it.fileName.toString().endsWith(".partial") }) }
    }
}
