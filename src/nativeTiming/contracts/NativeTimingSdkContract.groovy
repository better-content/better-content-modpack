import java.security.MessageDigest
import java.util.zip.ZipFile

/** Inert archive/SDK contract shared by the opt-in hook and focused negative controls. */
class NativeTimingSdkContract {
    static void verifySdk(Collection<File> jars, Map reviewed) {
        def found = [:].withDefault { [] }
        jars.findAll { it.isFile() && it.name.endsWith('.jar') }.each { jar ->
            new ZipFile(jar).withCloseable { zip ->
                reviewed.each { name, hashes ->
                    def entry = zip.getEntry(name)
                    if (entry != null) {
                        def hash = zip.getInputStream(entry).withCloseable { input ->
                            MessageDigest.getInstance('SHA-256').digest(input.bytes).encodeHex().toString()
                        }
                        if (!hashes.contains(hash)) throw new IllegalStateException('Unreviewed native timing SDK ABI: ' + name)
                        found[name].add(jar.canonicalPath)
                    }
                }
            }
        }
        reviewed.keySet().each { name ->
            if (found[name].size() != 1) throw new IllegalStateException('Missing/ambiguous native timing SDK ABI: ' + name)
        }
    }
    static void verifyArchive(File archive) {
        new ZipFile(archive).withCloseable { zip ->
            zip.entries().each { entry ->
                if (entry.name.contains('bc/nonshipping/nativetiming/') ||
                        entry.name.endsWith('bc_native_timing.mixins.json') ||
                        entry.name.contains('src/nativeTiming/'))
                    throw new IllegalStateException('Nonshipping native timing leaked into archive: ' + archive + '!/' + entry.name)
            }
        }
    }
}
