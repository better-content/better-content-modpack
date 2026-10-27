import org.gradle.api.tasks.testing.Test

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

tasks.register<JavaExec>("packSession") {
    group = "verification"
    description = "Runs one Smoke/Soak session on existing packaged artifacts; invoke through test.main.kts."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.PackSessionKt")
    args(layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("sessionTier").orElse("smoke").get(),
        providers.gradleProperty("sessionMinutes").orElse("0").get(),
        providers.gradleProperty("sessionClient").orElse("").get(),
        providers.gradleProperty("sessionServer").orElse("").get())
}

tasks.register<JavaExec>("prepareFreshDist") {
    group = "distribution"
    description = "Prepares once and requires Smoke on the exact ZIP pair before ready; optional Soak."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.bettercontent.tests.release.ReleasePipelineKt")
    args(layout.projectDirectory.asFile.absolutePath,
        providers.gradleProperty("releaseJobs").orElse("2").get(),
        providers.gradleProperty("releaseSoakMinutes").orElse("0").get(),
        providers.gradleProperty("releaseUseBundledMods").orElse("false").get())
}
