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
    }
    systemProperty("bc.repo.root", layout.projectDirectory.asFile.absolutePath)
    val testTemp = layout.buildDirectory.dir("tmp/tests")
    systemProperty("java.io.tmpdir", testTemp.get().asFile.absolutePath)
    doFirst { testTemp.get().asFile.mkdirs() }
}

tasks.test {
    description = "Runs fast, Minecraft-free test-harness checks."
    useJUnitPlatform { includeTags("fast") }
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
            require(!System.getenv("BC_PACK_TEST_LOCK_TOKEN").isNullOrBlank()) {
                "pack-level Gradle tasks must be invoked through ./test.main.kts so the shared runner mutex is held"
            }
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
    args(
        layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("releaseJobs").orElse("2").get(),
        providers.gradleProperty("releaseSkipTests").orElse("false").get(),
    )
}

tasks.register<JavaExec>("workspaceMaintenance") {
    group = "maintenance"
    description = "Audits or prunes superseded pack evidence and distribution staging."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.maintenance.WorkspaceMaintenanceKt")
    args(
        layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("maintenanceMode").orElse("audit").get(),
        providers.gradleProperty("maintenanceApply").orElse("false").get(),
    )
}
