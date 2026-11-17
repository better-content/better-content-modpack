import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import java.security.MessageDigest

plugins { kotlin("jvm") version "2.2.21" }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// Ordinary implementation-local checks. Neither runtime tier nor release depends on this task.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    val temp = layout.buildDirectory.dir("tmp/tests")
    systemProperty("java.io.tmpdir", temp.get().asFile.absolutePath)
    systemProperty("bc.repo.root", layout.projectDirectory.asFile.absolutePath)
    doFirst { temp.get().asFile.mkdirs() }
}

// Test-phase ONLY: native Java's production serializer emits bytes; a separate Kotlin test consumes
// those exact bytes. No compile/prepare task depends on either producer or consumer.
tasks.named<Test>("test") { useJUnitPlatform { excludeTags("emi-producer-consumer") } }
val emiFixture = layout.projectDirectory.file("test-support/forge/build/emi-contract/${java.util.UUID.randomUUID()}/emi-public-000000000001.json").asFile
val emiNativeProducer = tasks.register<Exec>("emiNativeProducer") {
    group = "verification"
    description = "Runs the focused native production EMI serializer JUnit fixture only with separate human test approval."
    workingDir = layout.projectDirectory.dir("test-support/forge").asFile
    doFirst {
        require(!emiFixture.parentFile.exists() && !emiFixture.exists()) { "EMI producer would reuse old fixture" }
        require(emiFixture.parentFile.mkdirs()) { "cannot create fresh owned EMI fixture directory" }
    }
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    commandLine("flock", "/home/dev/.tmp/better-content-gradle.lock", "./gradlew", "--no-daemon", "--max-workers=2",
        "--no-build-cache", "--rerun-tasks", "emiProducerContractTest", "-PemiContractOutput=${emiFixture.absolutePath}")
}
tasks.register<Test>("emiProducerConsumerContract") {
    group = "verification"
    description = "Explicit cross-language production serializer -> Kotlin consumer regression; never invoked by compile tasks."
    dependsOn(emiNativeProducer, tasks.named("testClasses"))
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("emi-producer-consumer") }
    systemProperty("bc.emi.contract.output", emiFixture.absolutePath)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

tasks.register<JavaExec>("packSession") {
    group = "verification"
    description = "Runs one Smoke, Soak or separate Tele Soak session on existing packaged artifacts; invoke through test.main.kts."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.PackSessionKt")
    args(layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("sessionTier").orElse("smoke").get(),
        providers.gradleProperty("sessionMinutes").orElse("0").get(),
        providers.gradleProperty("sessionClient").orElse("").get(),
        providers.gradleProperty("sessionServer").orElse("").get(),
        providers.gradleProperty("sessionDiagnoseFrom").orElse("").get())
}

tasks.register("preparePlaytestValidator") {
    group = "build"
    description = "Compile the evidence validator and record its classpath/source identity; does not run tests or package a distribution."
    dependsOn(tasks.classes)
    doLast {
        val root = layout.projectDirectory.asFile
        val target = layout.buildDirectory.dir("playtest-validator").get().asFile.apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        val sources = root.resolve("src/main").walkTopDown().filter { it.isFile }.toList() +
            listOf(root.resolve("build.gradle.kts"), root.resolve("test.main.kts"))
        sources.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach {
            digest.update((it.relativeTo(root).invariantSeparatorsPath + "\n").toByteArray(Charsets.UTF_8))
            digest.update(it.readBytes())
        }
        target.resolve("classpath.txt").writeText(sourceSets.main.get().runtimeClasspath.asPath + "\n")
        target.resolve("sources.sha256").writeText(digest.digest().joinToString("") { "%02x".format(it) } + "\n")
    }
}

tasks.register<JavaExec>("playtestSuite") {
    group = "verification"
    description = "Explicit playtest discovery/campaign or evidence-only qualification; invoke through test.main.kts. Does not run repository tests or package mods."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.suite.SuiteMainKt")
    args(layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("suiteAction").orElse("").get(),
        providers.gradleProperty("suiteManifest").orElse("").get(),
        providers.gradleProperty("suiteClient").orElse("").get(),
        providers.gradleProperty("suiteServer").orElse("").get(),
        providers.gradleProperty("suiteCases").orElse("").get(),
        providers.gradleProperty("suiteReceipts").orElse("").get(),
        providers.gradleProperty("suiteOutput").orElse("").get())
}

tasks.register<JavaExec>("prepareFreshDist") {
    group = "distribution"
    description = "Fresh release with explicitly selected optional runtime tiers; unselected tiers remain not_run."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.release.ReleasePipelineKt")
    args(layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("releaseJobs").orElse("2").get(),
        providers.gradleProperty("releaseSoakMinutes").orElse("0").get(),
        providers.gradleProperty("releaseUseBundledMods").orElse("false").get(),
        providers.gradleProperty("releaseRuntimeTiers").orElse("none").get())
    providers.gradleProperty("releaseFreshSourcePlan").orNull?.let { args(it) }
}

tasks.register<JavaExec>("validateFreshVisualTier") {
    group = "verification"
    description = "Validates a separate, optional independent in-world visual review for a packaged exact pair."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.release.ReleasePipelineKt")
    args("--visual-tier", layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("releaseVisualPackage").get(),
        providers.gradleProperty("releaseVisualReview").get())
}

tasks.register<JavaExec>("finalizeFreshDist") {
    group = "distribution"
    description = "Finalizes an already sealed immutable fresh-source pair after independent approved human or agent in-world visual review; does not package or start a server."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.release.ReleasePipelineKt")
    args("--finalize-visual", layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("releaseVisualPending").get(),
        providers.gradleProperty("releaseVisualReview").get())
}
