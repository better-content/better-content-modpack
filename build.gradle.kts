import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.2.21"
    application
}

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
}

tasks.withType<Test>().configureEach {
    // Pack suites create run-scoped evidence and exercise external processes. Reusing a
    // previous Test result would create a false green run with no current evidence.
    outputs.upToDateWhen { false }
    useJUnitPlatform()
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    systemProperty("bc.repo.root", layout.projectDirectory.asFile.absolutePath)
    val testTemp = layout.buildDirectory.dir("tmp/tests")
    systemProperty("java.io.tmpdir", testTemp.get().asFile.absolutePath)
    systemProperty("junit.jupiter.tempdir.cleanup.mode.default", "NEVER")
    doFirst { testTemp.get().asFile.mkdirs() }
}

tasks.test {
    description = "Runs fast, Minecraft-free test-harness checks."
    useJUnitPlatform { includeTags("fast") }
    doFirst {
        exec { commandLine("python3", "-B", "scripts/test_testing_session.py") }
        exec { commandLine("python3", "-B", "scripts/test_bounded_facade.py") }
        exec { commandLine("python3", "-B", "scripts/test_prepare_dev_inputs.py") }
        exec { commandLine("python3", "-B", "scripts/test_ci_boundaries.py") }
        exec { commandLine("python3", "-B", "scripts/test_workspace_coordination.py") }
        exec { commandLine("python3", "-B", "scripts/test_candidate_routing.py") }
        exec { commandLine("python3", "-B", "scripts/prepare_dev_inputs.py") }
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

serverTest.configure { shouldRunAfter(candidateTest) }
multiplayerTest.configure { shouldRunAfter(serverTest) }
singleplayerTest.configure { shouldRunAfter(multiplayerTest) }

tasks.register("modpackTest") {
    group = "verification"
    description = "Runs all granular pack-level suites against the existing candidate pair."
    dependsOn(tasks.test, candidateTest, serverTest, multiplayerTest, singleplayerTest)
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
