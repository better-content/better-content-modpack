package com.bettercontent.tests

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Fixture-only derivation. Never edits the packaged production XML or a repository config. */
object FixtureAuditLogging {
    private const val EMPTY_LOGBEGONE = "[logbegone]\n  regex = []\n  phrases = []\n"
    private const val APPENDER = "FixtureRawAudit"
    private const val PATTERN = "[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg{nolookups}%n%ex{full}"
    private val nativeRecord = Regex("(?m)^\\[\\d{2}:\\d{2}:\\d{2}] \\[[^\\r\\n]+/(?:INFO|WARN|ERROR|FATAL)] \\[[^\\r\\n]+]: .+")

    class Launch internal constructor(
        val configuration: Path,
        val auditLog: Path,
        private val expected: Map<Path, String>,
    ) {
        val jvmArgument: String get() = "-Dlog4j.configurationFile=${configuration.toAbsolutePath()}"
        val hashes: Map<String, String> get() = expected.mapKeys { it.key.toString() }

        fun assertStable() {
            expected.forEach { (path, hash) ->
                requireNoSymlinks(path)
                require(Files.isRegularFile(path, NOFOLLOW_LINKS) && sha256(Files.readAllBytes(path)) == hash) {
                    "fixture logging configuration drift: $path"
                }
            }
        }

        /** Invoke only after native readiness; an absent, empty or non-native sink fails closed. */
        fun requireNativeRecords() {
            assertStable()
            require(Files.isRegularFile(auditLog, NOFOLLOW_LINKS)) { "native audit log missing: $auditLog" }
            require(nativeRecord.containsMatchIn(Files.readString(auditLog))) { "native audit log has no native records: $auditLog" }
        }
    }

    /** A new immutable XML and audit filename for every process start, including restarts. */
    fun prepare(fixtureRoot: Path, launchLabel: String): Launch {
        require(launchLabel.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid fixture launch label" }
        val root = fixtureRoot.toAbsolutePath().normalize()
        requireNoSymlinks(root)
        require(!Files.exists(root.resolve(".git"), NOFOLLOW_LINKS)) { "logging patch requires an extracted fixture, not a checkout" }
        require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "missing extracted fixture: $root" }
        val xml = root.resolve("config/better-content-log4j2.xml")
        val logbegone = root.resolve("config/logbegone.toml")
        listOf(xml, logbegone).forEach {
            requireNoSymlinks(it)
            require(Files.isRegularFile(it, NOFOLLOW_LINKS)) { "missing packaged logging config: $it" }
        }
        val originalXml = Files.readAllBytes(xml)
        // Parse and validate before changing any config.
        val document = parse(originalXml)
        val configuration = document.documentElement
        require(configuration.tagName == "Configuration") { "expected Log4j Configuration" }
        require(configuration.children().all { it.tagName in setOf("Properties", "Appenders", "Loggers") }) { "unsupported global Log4j filtering or configuration" }
        val appenders = configuration.singleChild("Appenders")
        val loggers = configuration.singleChild("Loggers")
        require(loggers.attributes.length == 0) { "unsupported logger-container attributes" }
        val nodes = loggers.children()
        require(nodes.all { it.tagName in setOf("Root", "Logger") }) { "unsupported native logger type" }
        val scoped = nodes.filter { it.tagName == "Logger" }
        val scopedNames = scoped.map { it.getAttribute("name") }
        require(scopedNames.toSet().size == scopedNames.size) { "duplicate playtest logger" }
        scoped.forEach { child ->
            val name = child.getAttribute("name")
            require(name.matches(Regex("bc\\.playtest(?:\\.[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_-]*)*)?"))) {
                "unsupported scoped logger: $name"
            }
            require(child.attributeNames() == setOf("name", "level", "additivity") &&
                child.getAttribute("additivity") == "true" && child.children().isEmpty()) {
                "playtest loggers must propagate without filters or private appenders"
            }
            val level = child.getAttribute("level")
            require(level.uppercase() in setOf("TRACE", "DEBUG", "INFO") ||
                (name == "bc.playtest" && level == "\${sys:bc.playtest.level:-DEBUG}")) {
                "unsupported playtest logger level"
            }
        }
        val logger = loggers.singleChild("Root")
        require(logger.attributeNames() == setOf("level") && logger.getAttribute("level").equals("info", true)) { "native root must remain INFO without routing overrides" }
        require(logger.children().all { it.tagName in setOf("Filters", "AppenderRef") }) { "unsupported root configuration" }
        val presentation = appenders.children()
        require(presentation.isNotEmpty() && presentation.none { it.getAttribute("name") == APPENDER }) { "invalid presentation appenders" }
        val names = presentation.map { it.getAttribute("name") }
        require(names.toSet().size == names.size && names.none(String::isBlank)) { "duplicate/empty appender name" }
        val refs = logger.children().filter { it.tagName == "AppenderRef" }
        require(refs.map { it.getAttribute("ref") }.toSet() == names.toSet() && refs.all { it.attributes.length == 1 && it.children().isEmpty() }) { "unsupported native appender references" }
        require(logger.children().count { it.tagName == "Filters" } <= 1) { "duplicate root filters" }
        // Root's packet marker rule belongs to presentation, not to the unfiltered audit branch.
        logger.children().singleOrNull { it.tagName == "Filters" }?.let { filters ->
            presentation.forEach { appender ->
                val target = appender.children().singleOrNull { it.tagName == "Filters" }
                    ?: document.createElement("Filters").also { appender.appendChild(it) }
                filters.children().forEach { target.appendChild(it.cloneNode(true)) }
            }
            logger.removeChild(filters)
        }
        val auditDirectory = root.resolve(".fixture-audit")
        Files.createDirectories(auditDirectory)
        require(!Files.isSymbolicLink(auditDirectory)) { "symlink audit directory" }
        val backup = auditDirectory.resolve("logbegone.original.toml")
        if (Files.exists(backup, NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(backup, NOFOLLOW_LINKS) && Files.readString(logbegone) == EMPTY_LOGBEGONE) { "fixture LogBegone drift" }
            validateLogBegone(Files.readString(backup))
        } else {
            validateLogBegone(Files.readString(logbegone))
            Files.write(backup, Files.readAllBytes(logbegone), CREATE_NEW)
        }
        val id = "$launchLabel-${UUID.randomUUID()}"
        val audit = auditDirectory.resolve("$id.log")
        val generated = auditDirectory.resolve("$id.xml")
        val file = document.createElement("File").apply {
            setAttribute("name", APPENDER)
            setAttribute("fileName", audit.toString())
            setAttribute("append", "false")
            setAttribute("immediateFlush", "true")
            appendChild(document.createElement("PatternLayout").apply {
                setAttribute("pattern", PATTERN)
                setAttribute("alwaysWriteExceptions", "true")
            })
        }
        appenders.appendChild(file)
        logger.appendChild(document.createElement("AppenderRef").apply { setAttribute("ref", APPENDER) })
        val output = ByteArrayOutputStream()
        TransformerFactory.newInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            .newTransformer().transform(DOMSource(document), StreamResult(output))
        parse(output.toByteArray())
        Files.write(generated, output.toByteArray(), CREATE_NEW)
        Files.writeString(logbegone, EMPTY_LOGBEGONE)
        return Launch(generated, audit, listOf(xml, backup, logbegone, generated).associateWith { sha256(Files.readAllBytes(it)) })
            .also { it.assertStable() }
    }

    private fun validateLogBegone(text: String) {
        // Deliberately narrow packaged TOML shape; reject unexpected options rather than deleting them.
        val shape = Regex("(?s)\\s*\\[logbegone]\\s*regex\\s*=\\s*\\[(.*?)]\\s*phrases\\s*=\\s*\\[(.*?)]\\s*")
        val match = requireNotNull(shape.matchEntire(text)) { "unexpected packaged LogBegone structure" }
        val string = "\"(?:[^\"\\\\\\p{Cntrl}]|\\\\(?:[\"\\\\btnfr]|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8}))*\""
        val array = Regex("\\s*(?:$string(?:\\s*,\\s*$string)*\\s*,?)?\\s*")
        require(match.groupValues.drop(1).all(array::matches)) { "LogBegone lists must contain only valid TOML basic strings" }
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun Element.attributeNames(): Set<String> = (0 until attributes.length).map { attributes.item(it).nodeName }.toSet()
    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.singleChild(name: String): Element = children().filter { it.tagName == name }.singleOrNull()
        ?: throw IllegalArgumentException("expected exactly one $name")
    private fun requireNoSymlinks(path: Path) {
        var current: Path? = path.toAbsolutePath()
        while (current != null) {
            require(!Files.isSymbolicLink(current)) { "symlink fixture logging path: $current" }
            current = current.parent
        }
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
