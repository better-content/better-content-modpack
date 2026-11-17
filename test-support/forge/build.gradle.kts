import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID

plugins {
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
}
group = "com.bettercontent.test"
version = "0.1.0"
base { archivesName.set("better-playtest-native-support") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
// Existing real-path controls are loaded only by the explicit disposable GameTest run.
// Neither the ordinary JUnit classpath nor the support JAR includes this source set.
val nativeFixture by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
val nativeRunId = "native-${UUID.randomUUID()}"
val nativeFixtureBase = layout.buildDirectory.dir("native-gametest").get().asFile.canonicalFile
val nativeRunDirectory = nativeFixtureBase.resolve(nativeRunId)
val nativeReportDirectory = nativeRunDirectory.resolve("repository-report")
minecraft {
    mappings("official", "1.20.1")
    runs {
        create("gameTestServer") {
            workingDirectory(nativeRunDirectory)
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", "better_playtest_native_support")
            property("bc.playtest.repositoryReport", nativeReportDirectory.absolutePath)
            property("bc.playtest.repositoryRunId", nativeRunId)
            property("bc.playtest.repository", "native-support")
            mods {
                create("better_playtest_native_support") {
                    source(sourceSets.main.get())
                    source(nativeFixture)
                }
            }
            arg("--nogui")
        }
    }
}
tasks.matching { it.name == "runGameTestServer" }.configureEach {
    dependsOn(tasks.named(nativeFixture.classesTaskName))
    doFirst {
        check(nativeRunDirectory.canonicalFile.parentFile == nativeFixtureBase) { "Native fixture escaped owned build directory" }
        for (name in listOf("world", "logs", "repository-report", "native-fixture.txt")) {
            check(!Files.exists(nativeRunDirectory.resolve(name).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "Refusing reused native fixture: $nativeRunDirectory/$name"
            }
        }
        Files.createDirectories(nativeRunDirectory.toPath())
        Files.writeString(nativeRunDirectory.resolve("native-fixture.txt").toPath(),
            "run_id=$nativeRunId\nexpected_tests=2\nmode=existing-real-path-controls-only\n", StandardOpenOption.CREATE_NEW)
        Files.createDirectory(nativeReportDirectory.toPath())
        logger.lifecycle("Native GameTest fresh RunConfig directory=$nativeRunDirectory report=$nativeReportDirectory")
    }
}
mixin {
    add(sourceSets.main.get(), "better_playtest_native_support.refmap.json")
    config("better_playtest_native_support.mixins.json")
}
repositories { mavenCentral() }
dependencies {
    minecraft("net.minecraftforge:forge:1.20.1-47.4.22")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.named<Jar>("jar") {
    manifest.attributes["MixinConfigs"] = "better_playtest_native_support.mixins.json"
}
// The cross-language producer has no output path during ordinary unit runs and never executes on compile.
tasks.test { useJUnitPlatform { excludeTags("emi-producer-consumer") } }
tasks.register<Test>("emiProducerContractTest") {
    group = "verification"
    description = "Explicit test-phase production EMI serializer fixture; requires separate human test approval."
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("emi-producer-consumer") }
    systemProperty("bc.emi.contract.output", providers.gradleProperty("emiContractOutput").orElse("").get())
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
