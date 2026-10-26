import org.gradle.api.tasks.testing.Test
import java.io.File

plugins {
    kotlin("jvm") version "2.2.21"
    application
}

// Automatic scalar timing for direct harness calls and source facades. Comparable successful
// observations select exact2x owned deadlines; UNKNOWN keeps explicit legacy safety caps.
extra["bcTimingJunitEnabled"] = true
apply(from = "gradle/test-timing.init.gradle")
val pythonTimingEnvironment = mapOf(
    "BC_TIMING_PYTHON" to "true",
    "BC_TIMING_FILE" to gradle.extensions.extraProperties.get("bcTimingFile").toString(),
    "PYTHONPATH" to (layout.projectDirectory.dir("scripts/timing-python").asFile.absolutePath +
        (System.getenv("PYTHONPATH")?.takeIf(String::isNotEmpty)?.let { File.pathSeparator + it } ?: "")),
)

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("org.ow2.asm:asm:9.7.1")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.apache.logging.log4j:log4j-api:2.19.0")
    testImplementation("org.apache.logging.log4j:log4j-core:2.19.0")
    testImplementation("org.slf4j:slf4j-api:2.0.1")
}

// Test-only portable producer reference: prove the parser consumes the actual shared emitter.
// No mod provider/dependency or helper class is added to the production harness artifact.
val playtestReference by tasks.registering(Copy::class) {
    from(layout.projectDirectory.file("../mod_source/better-gameplay-notices/src/main/java/com/bettercontent/gameplaynotices/diagnostics/PlaytestLog.java"))
    into(layout.buildDirectory.dir("generated/sources/playtest-reference/java"))
}
sourceSets.test { java.srcDir(playtestReference) }

tasks.withType<Test>().configureEach {
    // Pack suites create run-scoped evidence and exercise external processes. Reusing a
    // previous Test result would create a false green run with no current evidence.
    outputs.upToDateWhen { false }
    useJUnitPlatform()
    environment(pythonTimingEnvironment)
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    systemProperty("bc.repo.root", layout.projectDirectory.asFile.absolutePath)
    // Focused integration fixtures launch this exact immutable distribution, with private caches.
    systemProperty("bc.retool.gradle.home", gradle.gradleHomeDir!!.absolutePath)
    val testTemp = layout.buildDirectory.dir("tmp/tests")
    systemProperty("java.io.tmpdir", testTemp.get().asFile.absolutePath)
    systemProperty("junit.jupiter.tempdir.cleanup.mode.default", "NEVER")
    doFirst { testTemp.get().asFile.mkdirs() }
}

tasks.test {
    description = "Runs fast, Minecraft-free test-harness checks."
    useJUnitPlatform { includeTags("fast") }
    doFirst {
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_testing_session.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_bounded_facade.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_bounded_timing.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_timing_budget.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_timing_report.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_python_timing.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_prepare_dev_inputs.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_prepare_forge_installer.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_prepare_native_fml.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_probe_side_parity.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_check_documentation.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_ci_boundaries.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_debug_coverage.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_workspace_coordination.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_candidate_routing.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_food_progression.py") }
        exec { commandLine("node", "scripts/test_plant_drinks.mjs") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/test_wild_crop_forage.py") }
        exec { environment(pythonTimingEnvironment); commandLine("python3", "-B", "scripts/prepare_dev_inputs.py") }
    }
}

fun registerPackTest(name: String, tag: String, descriptionText: String) =
    tasks.register<Test>(name) {
        group = "verification"
        description = descriptionText
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { includeTags(tag) }
        shouldRunAfter(tasks.test)
        doFirst {
            exec { commandLine("python3", "-B", "scripts/runner_lock.py") }
        }
    }

val candidateTest = registerPackTest("candidateTest", "candidate", "Validates the exact packaged client/server ZIP pair.")
val serverTest = registerPackTest("serverTest", "server", "Runs dedicated-server runtime-data and lifecycle scenarios.")
val multiplayerTest = registerPackTest("multiplayerTest", "multiplayer", "Runs the packaged dedicated-client join and GUI scenario.")
val singleplayerTest = registerPackTest("singleplayerTest", "singleplayer", "Runs the packaged fresh single-player scenario.")
val gameplayTest = registerPackTest("gameplayTest", "gameplay", "Runs observed gameplay, restart/soak and native shader acceptance.")
val interactionTest = registerPackTest("interactionTest", "interactions", "Runs independently selectable native interaction families.")

serverTest.configure { shouldRunAfter(candidateTest) }
multiplayerTest.configure { shouldRunAfter(serverTest) }
singleplayerTest.configure { shouldRunAfter(multiplayerTest) }
gameplayTest.configure { shouldRunAfter(singleplayerTest) }
interactionTest.configure { shouldRunAfter(gameplayTest) }

tasks.register("modpackTest") {
    group = "verification"
    description = "Runs all granular pack-level suites against the existing candidate pair."
    dependsOn(tasks.test, candidateTest, serverTest, multiplayerTest, singleplayerTest, gameplayTest, interactionTest)
}

// Internal host entry for test-control source only: no release/package/session effects.
tasks.register<JavaExec>("qualifySourceControl") {
    group = "verification"
    description = "Qualifies a requested source dependency closure through the common owning gates."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.release.SourceVerificationKt")
    systemProperty("bc.gradle.home", gradle.gradleHomeDir!!.absolutePath)
    args(layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("sourceControlRunId").orElse("").get(),
        providers.gradleProperty("sourceControlRepositories").orElse("").get())
}

tasks.register<JavaExec>("prepareFreshDist") {
    group = "distribution"
    description = "Validates active mod repositories, deploys fresh JARs, and packages one candidate pair."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.release.ReleasePipelineKt")
    systemProperty("bc.gradle.home", gradle.gradleHomeDir!!.absolutePath)
    args(
        layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("releaseJobs").orElse("2").get(),
        providers.gradleProperty("releaseSkipTests").orElse("false").get(),
        providers.gradleProperty("releaseForceRebuild").orElse("false").get(),
    )
}
