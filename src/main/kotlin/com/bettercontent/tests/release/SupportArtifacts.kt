package com.bettercontent.tests.release

import com.bettercontent.tests.Hashes
import java.nio.file.Files
import java.nio.file.Path

/** Required, gate-produced auxiliary inputs; never silently adopt a leftover development JAR. */
internal fun supportArtifactHashes(repository: Path, mod: ActiveMod, allowMissing: Boolean = false): Map<String, String> =
    mod.supportArtifacts.associateWith { relative ->
        val name = Path.of(relative)
        require(!name.isAbsolute && name.startsWith("build") && name.none { it.toString() == ".." }) {
            "support artifacts must be declared inside the owning build root"
        }
        val path = repository.resolve(name)
        var ancestor: Path? = path
        while (ancestor != null && ancestor.startsWith(repository)) {
            require(!Files.isSymbolicLink(ancestor)) { "support artifact has a symlink ancestor: $ancestor" }
            ancestor = ancestor.parent
        }
        if (Files.isRegularFile(path)) Hashes.sha256(path)
        else if (allowMissing) "missing" else error("missing gate-produced support artifact: $path")
    }
