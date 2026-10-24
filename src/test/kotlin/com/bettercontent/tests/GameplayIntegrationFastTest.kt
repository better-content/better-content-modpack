package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Wiring and SDK-input contracts only; these never substitute for native gameplay acceptance. */
@Tag("fast")
class GameplayIntegrationFastTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
    private val diagnostics = root.parent.resolve("mod_source/better-runtime-diagnostics")
    private fun text(path: Path) = Files.readString(path)

    @Test fun fullDebugAndPartialGameplayShareTheSameNativeScenario() {
        val facade = text(root.resolve("test.main.kts"))
        require(facade.contains("target == \"gameplay\" -> \"gameplayAcceptance\""))
        require(facade.contains("target == \"gameplay\" -> \"GameplayRuntimeTest\""))
        require(text(root.resolve("release.main.kts")).contains("\"gameplay\""))
        val scenario = text(root.resolve("src/test/kotlin/com/bettercontent/tests/GameplayRuntimeTest.kt"))
        require(scenario.contains("@Tag(\"gameplay\")") && scenario.contains("EvidenceExtension(\"gameplay\")"))
        require(scenario.contains("@EnabledIfEnvironmentVariable(named = \"BC_TEST_TIER\", matches = \"debug\")"))
        require(facade.contains("listOf(\"server\", \"multiplayer\", \"singleplayer\", \"gameplay\", \"interactions\")"))
        require(facade.contains("scripts/debug_coverage.py"))
    }

    @Test fun nativeFixtureSdkPinsMatchActualPackAndDeclareEveryProvider() {
        val build = text(diagnostics.resolve("build.gradle.kts"))
        for ((name, project, file) in listOf(
            Triple("starcatcher", 1357603, 7582886), Triple("curios", 309927, 6418456),
            Triple("diet", 443570, 4813904), Triple("dynamictrees", 252818, 7502488),
            Triple("oculus", 581495, 6020952))) {
            val pin = text(root.resolve("mods/$name.pw.toml"))
            require(Regex("(?m)^project-id\\s*=\\s*$project\\s*$").containsMatchIn(pin))
            require(Regex("(?m)^file-id\\s*=\\s*$file\\s*$").containsMatchIn(pin))
            require(build.contains("-$project:$file\"")) { "Diagnostic SDK differs from actual $name pin" }
        }
        val manifest = jacksonObjectMapper().readTree(root.resolve("gradle/active-custom-mods.json").toFile())
        val entry = manifest.path("mods").single { it.path("repository").asText() == "better-runtime-diagnostics" }
        val providers = entry.path("dependsOn").map { it.asText() }.toSet()
        require(providers.containsAll(setOf("better-spawns", "better-survival-physiology", "better-wild-fruit-trees", "better-spirit-commerce")))
        require(build.contains("bettercontent.fixture.") && build.contains("BC_CUSTOM_MOD_JAR_DIR"))
    }

    @Test fun reservedMixinNamespaceContainsOnlyRegisteredTopLevelMixins() {
        val config = jacksonObjectMapper().readTree(diagnostics.resolve("src/testSupport/resources/better_runtime_test_support.mixins.json").toFile())
        val reserved = "com.bettercontent.runtimetestsupport.mixin"
        require(config.path("package").asText() == reserved)
        val worldMixins = listOf("ExplosionRadiusAccess", "BuriedTrackerObservationMixin", "BuriedSpawnerObservationMixin", "NativeEngineObservationMixin", "NativeCraterObservationMixin")
        require(config.path("mixins").map { it.asText() } ==
            listOf("diagnostics.NonfiniteRotationMixin", "diagnostics.UntamedSpeciesObservationMixin") + worldMixins.map { "gameplay.$it" })
        val registered = (config.path("mixins").map { it.asText() } + config.path("client").map { it.asText() }).map { "$reserved.$it" }
        require(registered.size == registered.toSet().size)
        require(!config.path("plugin").asText().startsWith("$reserved."))
        val actual = mutableSetOf<String>()
        for (sourceSet in listOf("testSupport", "testSupportPure")) {
            val sourceRoot = diagnostics.resolve("src/$sourceSet/java")
            Files.walk(sourceRoot).use { paths ->
                for (path in paths.filter { it.toString().endsWith(".java") }.toList()) {
                    val source = text(path)
                    val namespace = Regex("(?m)^package ([\\w.]+);").find(source)!!.groupValues[1]
                    val type = path.fileName.toString().removeSuffix(".java")
                    val qualified = "$namespace.$type"
                    val mixin = Regex("@Mixin\\s*\\(").containsMatchIn(source)
                    val inside = namespace == reserved || namespace.startsWith("$reserved.")
                    require(inside == mixin) { "Only Mixin types may occupy reserved namespace: $path" }
                    require(sourceRoot.relativize(path).toString().replace('\\', '/') == qualified.replace('.', '/') + ".java")
                    if (inside) {
                        require(!Regex("@Mod\\s*\\(").containsMatchIn(source))
                        require(!source.contains("implements IMixinConfigPlugin"))
                        require(Regex("(?m)^public (?:abstract )?(?:class|interface) $type\\b").containsMatchIn(source))
                        actual.add(qualified)
                    }
                }
            }
        }
        require(actual == registered.toSet()) { "Every reserved source must be registered exactly once" }
        val entrypoint = text(diagnostics.resolve("src/testSupport/java/com/bettercontent/runtimetestsupport/RuntimeTestSupport.java"))
        require(entrypoint.contains("package com.bettercontent.runtimetestsupport;"))
        require(entrypoint.contains("@Mod("))
        val probe = text(diagnostics.resolve("src/testSupport/java/com/bettercontent/runtimetestsupport/gameplay/WorldHooksInteractionWorldProbe.java"))
        require(probe.contains("import $reserved.gameplay.ExplosionRadiusAccess;"))
        require(!probe.contains("@Mixin("))
        val plugin = text(diagnostics.resolve("src/testSupport/java/com/bettercontent/runtimetestsupport/diagnostics/NonfiniteRotationMixinPlugin.java"))
        require(plugin.contains("String owner=\"$reserved.gameplay.\";"))
        for (name in worldMixins) require(plugin.contains("owner+\"$name\""))
    }

    @Test fun gameplayClientHooksAreExplicitlyDebugGatedAndIsolated() {
        val mapper = jacksonObjectMapper()
        val config = mapper.readTree(diagnostics.resolve("src/testSupport/resources/better_runtime_test_support.mixins.json").toFile())
        val mixinPackage = "com.bettercontent.runtimetestsupport.mixin"
        val clientMixins = listOf("diagnostics.GameplayParticleMixin", "diagnostics.GameplayCutMixin", "diagnostics.GameplayCutDriverMixin")
        require(config.path("package").asText() == mixinPackage)
        require(config.path("plugin").asText() == "com.bettercontent.runtimetestsupport.diagnostics.NonfiniteRotationMixinPlugin")
        require(config.path("client").map { it.asText() } == clientMixins)
        val plugin = text(diagnostics.resolve("src/testSupport/java/com/bettercontent/runtimetestsupport/diagnostics/NonfiniteRotationMixinPlugin.java"))
        require(plugin.contains("if (!enabled) return false;"))
        require(plugin.contains("if (!requested || !\"debug\".equals(mode)) return false;"))
        require(plugin.contains("Boolean.getBoolean(\"bc.pack_test.gameplay_client\")"))
        for (name in clientMixins) require(plugin.contains("$mixinPackage.$name"))
        require(Regex("""com\.bettercontent\.runtimetestsupport\.mixin\.diagnostics\.Gameplay[A-Za-z]+Mixin""")
            .findAll(plugin).map { it.value }.toSet() == clientMixins.map { "$mixinPackage.$it" }.toSet())
        require(plugin.indexOf("if (!enabled) return false;") < plugin.indexOf("if (gameplayMixin(target, mixin,"))
        val build = text(diagnostics.resolve("build.gradle.kts"))
        require(text(root.resolve(".packwizignore")).lineSequence().any { it.trim() == "/workspace_artifacts/" })
        require(build.contains("Diagnostic supplement leaked into production"))
        require(build.contains("cancellableTargets == listOf(\"stopDestroyBlock\")"))
        val fixtures = text(root.resolve("src/test/kotlin/com/bettercontent/tests/RuntimeFixtures.kt"))
        require(fixtures.contains(") + gameplayAudioEnvironment(evidence, log)"))
    }
}
