package com.bettercontent.tests.release

import java.nio.file.Files
import java.nio.file.Path

/** The existing manifest is the sole graph. Only completed owning-gate records supply bytes. */
internal fun providerInputView(
    manifest: ActiveModManifest,
    consumer: ActiveMod,
    completed: Map<String, BuiltMod>,
    workspace: Path,
    destination: Path,
    mode: ProviderInputMode = ProviderInputMode.QUALIFIED,
    checkActive: () -> Unit = {},
): ProviderInputView {
    require(manifest.mods.single { it.repository == consumer.repository } == consumer) {
        "consumer differs from the admitted inventory"
    }
    val required = sourceVerificationClosure(manifest, listOf(consumer.repository))
        .filterNot { it.repository == consumer.repository }.flatMap { provider ->
            val item = requireNotNull(completed[provider.repository]) {
                "required provider has not completed its owning gates: ${provider.repository}"
            }
            require(item.definition == provider) { "completed provider differs from admitted inventory" }
            val qualified = item.mode != "DEFERRED_UNQUALIFIED" && mode == ProviderInputMode.QUALIFIED
            val origin = "commit=${item.commit};artifact=${provider.artifact};sha256=${item.sha256}"
            listOf(ProviderArtifactInput(provider.repository, item.jar, provider.artifact, item.sha256,
                origin, qualified)) + item.supportHashes.toSortedMap().map { (relative, hash) ->
                require(relative in provider.supportArtifacts) { "undeclared provider support input: $relative" }
                ProviderArtifactInput(provider.repository,
                    workspace.resolve("mod_source/${provider.repository}").resolve(relative),
                    "support/${provider.repository}/$relative", hash, "$origin;support=$relative", qualified)
            }
        }
    Files.createDirectories(destination.parent)
    return ProviderInputs.snapshot(destination, required, mode, checkActive)
}

/** Existing native-provider helper property aliases take precedence over BC_CUSTOM_MOD_JAR_DIR.
 * Bind those public aliases to the SAME view too, so ambient Gradle properties cannot escape it.
 * Authority: Water/Physiology gradle/native-compat-providers.gradle and Physiology Water binding.
 * These are invocation ABI aliases, not another graph or an alternative dependency inventory.
 */
internal fun providerInputProperties(view: ProviderInputView): Map<String, String> {
    ProviderInputs.verify(view)
    val aliases = mapOf("better-compat-fixes" to "compatFixesRuntimeJar",
        "better-gameplay-notices" to "gameplayNoticesRuntimeJar",
        "better-drinking-water" to "drinkingWaterRuntimeJar")
    return Files.readAllLines(view.manifest).drop(1).map { it.split('\t') }
        .filter { fields -> Path.of(fields[0]).nameCount == 1 && fields[0].endsWith(".jar") && fields[2] in aliases }
        .associate { fields -> aliases.getValue(fields[2]) to view.directory.resolve(fields[0]).toString() }
}

/** A direct internal build without dependencies still receives a real, sealed empty view. */
internal fun emptyProviderInputView(evidence: Path, mod: ActiveMod, skipTests: Boolean): ProviderInputView {
    require(mod.dependsOn.isEmpty()) { "dependent build requires an admitted immutable provider view" }
    val destination = evidence.resolve("provider-inputs/${mod.repository}")
    Files.createDirectories(destination.parent)
    return ProviderInputs.snapshot(destination, emptyList(),
        if (skipTests) ProviderInputMode.EXPLICIT_UNTESTED else ProviderInputMode.QUALIFIED)
}
