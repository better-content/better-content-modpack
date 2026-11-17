plugins {
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
}
group = "com.bettercontent.test"
version = "0.1.0"
base { archivesName.set("better-playtest-native-support") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
minecraft { mappings("official", "1.20.1") }
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
    from(layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")) {
        rename { "better_playtest_native_support.refmap.json" }
    }
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
