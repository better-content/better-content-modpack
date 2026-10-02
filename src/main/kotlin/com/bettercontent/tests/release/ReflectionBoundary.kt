package com.bettercontent.tests.release

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines

data class ReflectionAllowance(val repository: String, val sourcePath: String)

private val sourcePatterns = listOf(
    Regex("""java\.lang\.reflect"""),
    Regex("""kotlin\.reflect"""),
    Regex("""\bClass\s*\.\s*forName\s*\("""),
    Regex("""\.\s*getDeclared(?:Field|Fields|Method|Methods|Constructor|Constructors|Classes|Annotations?)\s*\("""),
    Regex("""\.\s*get(?:Field|Fields|Method|Methods|Constructor|Constructors|Classes|RecordComponents|Annotations?)\s*\("""),
    Regex("""\.\s*(?:isAnnotationPresent|getSuperclass|getInterfaces|getTypeParameters|getEnclosingClass|getDeclaringClass|getNestHost|getNestMembers)\s*\("""),
    Regex("""\.\s*(?:setAccessible|trySetAccessible)\s*\("""),
    Regex("""\bProxy\s*\.\s*newProxyInstance\s*\("""),
    Regex("""\.\s*loadClass\s*\("""),
    Regex("""\bMethodHandles\b|\bVarHandle\b|\bsun\.misc\.Unsafe\b"""),
    Regex("""\.javaClass\.(?:methods|declaredMethods|fields|declaredFields)\b"""),
    Regex("""::class\.(?:members|memberFunctions|memberProperties)\b"""),
)

internal fun readReflectionAllowlist(path: Path): Set<ReflectionAllowance> {
    val allowances = path.readLines().mapIndexedNotNull { index, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
        val fields = line.split('\t')
        require(fields.size == 2 && fields.none(String::isBlank)) {
            "invalid reflection allowlist entry at ${path.fileName}:${index + 1}"
        }
        require(!fields[1].startsWith("/") && Path.of(fields[1]).none { it.toString() == ".." }) {
            "reflection allowlist path must remain repository-relative: ${fields[1]}"
        }
        ReflectionAllowance(fields[0], fields[1].replace('\\', '/'))
    }
    require(allowances.size == allowances.toSet().size) { "duplicate reflection allowlist entry" }
    return allowances.toSet()
}

internal fun sourceReflectionViolations(
    workspace: Path,
    repositories: Collection<String>,
    allowances: Set<ReflectionAllowance>,
): List<String> = repositories.sorted().flatMap { repositoryName ->
    val repository = workspace.resolve("mod_source").resolve(repositoryName)
    val sourceRoot = repository.resolve("src")
    if (!Files.isDirectory(sourceRoot)) return@flatMap emptyList()
    Files.walk(sourceRoot).use { stream ->
        stream.filter(Path::isRegularFile)
            .filter { it.extension in setOf("java", "kt", "kts") }
            .sorted()
            .flatMap { source ->
                val relative = repository.relativize(source).toString().replace('\\', '/')
                if (ReflectionAllowance(repositoryName, relative) in allowances) return@flatMap java.util.stream.Stream.empty()
                sourceReflectionMatches(source).map { (line, pattern) ->
                    "$repositoryName\t$relative:$line\t$pattern"
                }.stream()
            }.toList()
    }
}

internal fun unusedReflectionAllowances(
    workspace: Path,
    allowances: Set<ReflectionAllowance>,
): List<ReflectionAllowance> = allowances.filter { allowance ->
    val source = workspace.resolve("mod_source").resolve(allowance.repository).resolve(allowance.sourcePath)
    !Files.isRegularFile(source) || sourceReflectionMatches(source).isEmpty()
}.sortedWith(compareBy(ReflectionAllowance::repository, ReflectionAllowance::sourcePath))

private fun sourceReflectionMatches(source: Path): List<Pair<Int, String>> {
    val code = sourceCodeLines(source.readLines()).joinToString("\n")
    return sourcePatterns.flatMap { pattern ->
        pattern.findAll(code).map { match ->
            val line = code.substring(0, match.range.first).count { character -> character == '\n' } + 1
            line to pattern.pattern
        }
    }.distinct().sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
}

private fun sourceCodeLines(lines: List<String>): List<String> {
    var blockComment = false
    var tripleString = false
    return lines.map { line ->
        val code = StringBuilder(line.length)
        var index = 0
        var quoted = false
        var character = false
        var escaped = false
        while (index < line.length) {
            val remaining = line.substring(index)
            when {
                blockComment && remaining.startsWith("*/") -> { blockComment = false; code.append("  "); index += 2 }
                blockComment -> { code.append(' '); index++ }
                tripleString && remaining.startsWith("\"\"\"") -> { tripleString = false; code.append("   "); index += 3 }
                tripleString -> { code.append(' '); index++ }
                !quoted && !character && remaining.startsWith("//") -> { code.append(" ".repeat(line.length - index)); index = line.length }
                !quoted && !character && remaining.startsWith("/*") -> { blockComment = true; code.append("  "); index += 2 }
                !quoted && !character && remaining.startsWith("\"\"\"") -> { tripleString = true; code.append("   "); index += 3 }
                !character && line[index] == '"' && !escaped -> { quoted = !quoted; code.append(' '); index++ }
                !quoted && line[index] == '\'' && !escaped -> { character = !character; code.append(' '); index++ }
                quoted || character -> {
                    escaped = line[index] == '\\' && !escaped
                    if (line[index] != '\\') escaped = false
                    code.append(' ')
                    index++
                }
                else -> { escaped = false; code.append(line[index]); index++ }
            }
        }
        code.toString()
    }
}

private data class RuntimeSourceIndex(
    val classPrefixes: Map<String, String>,
    val packages: Set<String>,
)

private fun runtimeSourceIndex(repository: Path): RuntimeSourceIndex {
    val main = repository.resolve("src/main")
    if (!Files.isDirectory(main)) return RuntimeSourceIndex(emptyMap(), emptySet())
    val prefixes = linkedMapOf<String, String>()
    val packages = linkedSetOf<String>()
    Files.walk(main).use { stream ->
        stream.filter(Path::isRegularFile)
            .filter { it.extension == "java" || it.extension == "kt" }
            .forEach { source ->
                val lines = source.readLines()
                val packageName = lines.firstNotNullOfOrNull { line ->
                    Regex("""^\s*package\s+([A-Za-z_][\w.]*)\s*;?""").find(line)?.groupValues?.get(1)
                } ?: return@forEach
                val internalPackage = packageName.replace('.', '/')
                packages += internalPackage
                val relative = repository.relativize(source).toString().replace('\\', '/')
                val internal = internalPackage + "/" + source.nameWithoutExtension
                prefixes[internal] = relative
                if (source.extension == "kt") prefixes[internal + "Kt"] = relative
                val declarations = sourceCodeLines(lines).flatMap { line ->
                    Regex("""\b(?:class|interface|enum|record|object)\s+([A-Za-z_][A-Za-z0-9_]*)""")
                        .findAll(line).map { it.groupValues[1] }.toList()
                }
                declarations.forEach { name -> prefixes["$internalPackage/$name"] = relative }
            }
    }
    return RuntimeSourceIndex(prefixes, packages)
}

internal fun bytecodeReflectionViolations(
    repositoryName: String,
    repository: Path,
    jarPath: Path,
    allowances: Set<ReflectionAllowance>,
): List<String> {
    val sourceIndex = runtimeSourceIndex(repository)
    val allowedSources = allowances.filter { it.repository == repositoryName }.map { it.sourcePath }.toSet()
    val violations = mutableListOf<String>()
    JarFile(jarPath.toFile()).use { jar ->
        jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }.forEach { entry ->
            val className = entry.name.removeSuffix(".class")
            val classPackage = className.substringBeforeLast('/', "")
            if (classPackage !in sourceIndex.packages) return@forEach
            val sourcePath = sourceIndex.classPrefixes.entries.firstOrNull { (prefix, _) ->
                className == prefix || className.startsWith("$prefix$")
            }?.value
            if (sourcePath != null && sourcePath in allowedSources) return@forEach
            jar.getInputStream(entry).use { input ->
                val reader = ClassReader(input)
                reader.accept(ReflectionClassVisitor(repositoryName, className, violations), ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        }
    }
    return violations.sorted()
}

private class ReflectionClassVisitor(
    private val repository: String,
    private val className: String,
    private val violations: MutableList<String>,
) : ClassVisitor(Opcodes.ASM9) {
    override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor? {
        if (descriptor != null && forbiddenDescriptor(descriptor)) record("field descriptor $name$descriptor")
        return null
    }

    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
        if (forbiddenDescriptor(descriptor)) record("method descriptor $name$descriptor")
        return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(opcode: Int, owner: String, calledName: String, calledDescriptor: String, isInterface: Boolean) {
                if (forbiddenCall(owner, calledName)) record("call $owner.$calledName$calledDescriptor")
            }

            override fun visitLdcInsn(value: Any?) {
                if (value is Type && forbiddenInternalName(value.internalName)) record("class literal ${value.internalName}")
            }
        }
    }

    private fun record(detail: String) {
        violations += "$repository\t$className\t$detail"
    }
}

private fun forbiddenDescriptor(descriptor: String): Boolean =
    descriptor.contains("Ljava/lang/reflect/") || descriptor.contains("Lkotlin/reflect/") ||
        descriptor.contains("Ljava/lang/invoke/VarHandle;") || descriptor.contains("Lsun/misc/Unsafe;")

private fun forbiddenInternalName(name: String): Boolean =
    name.startsWith("java/lang/reflect/") || name.startsWith("kotlin/reflect/") ||
        name == "java/lang/invoke/MethodHandles" || name.startsWith("java/lang/invoke/MethodHandles$") ||
        name == "java/lang/invoke/VarHandle" || name == "sun/misc/Unsafe"

private fun forbiddenCall(owner: String, name: String): Boolean = when {
    owner == "java/lang/Class" -> name in setOf(
        "forName", "getField", "getDeclaredField", "getMethod", "getDeclaredMethod",
        "getConstructor", "getDeclaredConstructor", "getClasses", "getDeclaredClasses",
        "getFields", "getDeclaredFields", "getMethods", "getDeclaredMethods",
        "getConstructors", "getDeclaredConstructors", "getRecordComponents",
        "getAnnotation", "getAnnotations", "getDeclaredAnnotation", "getDeclaredAnnotations",
        "isAnnotationPresent", "getSuperclass", "getInterfaces", "getTypeParameters",
        "getEnclosingClass", "getDeclaringClass", "getNestHost", "getNestMembers", "newInstance",
    )
    owner == "java/lang/ClassLoader" -> name == "loadClass"
    owner == "java/lang/reflect/AccessibleObject" -> name == "setAccessible" || name == "trySetAccessible"
    owner == "java/lang/reflect/Method" -> name == "invoke"
    owner == "java/lang/reflect/Constructor" -> name == "newInstance"
    owner == "java/lang/reflect/Field" -> name.startsWith("get") || name.startsWith("set")
    owner == "java/lang/reflect/Proxy" -> name == "newProxyInstance"
    forbiddenInternalName(owner) -> true
    else -> false
}
