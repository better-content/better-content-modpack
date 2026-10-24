import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Direct Groovy/JDK controls; no Gradle invocation or Minecraft initialization.
assert args.length >= 3: 'support-root retained-fixture-parent ForgeGradle-jar [named-SDK-jars...] required'
def root = new File(args[0]).canonicalFile
def fixtures = Files.createTempDirectory(new File(args[1]).toPath(), 'sdk-contract-').toFile()
def contract = new GroovyClassLoader(getClass().classLoader).parseClass(new File(root, 'src/nativeTiming/contracts/NativeTimingSdkContract.groovy'))
def writeZip = { String name, Map<String, byte[]> entries ->
    File file = new File(fixtures, name)
    new ZipOutputStream(new FileOutputStream(file)).withCloseable { zip ->
        entries.each { path, bytes -> zip.putNextEntry(new ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
    }
    file
}
def reject = { Closure action ->
    try { action(); throw new AssertionError('Expected fail-closed rejection') }
    catch (IllegalStateException expected) { }
}
String target = 'net/minecraft/gametest/framework/GameTestInfo.class'
byte[] bytes = [1, 2, 3] as byte[]
def pins = [(target): [MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()]]
def good = writeZip('good.jar', [(target): bytes])
contract.verifySdk([good], pins)
reject { contract.verifySdk([], pins) }
reject { contract.verifySdk([good, writeZip('duplicate.jar', [(target): bytes])], pins) }
reject { contract.verifySdk([writeZip('mutation.jar', [(target): [4] as byte[]])], pins) }
contract.verifyArchive(writeZip('production.jar', ['example/Production.class': bytes]))
['bc/nonshipping/nativetiming/NativeTimingObserver.class', 'bc/nonshipping/nativetiming/NativeTimingObserver.java',
 'bc_native_timing.mixins.json', 'nested/bc_native_timing.mixins.json', 'src/nativeTiming/contracts/NativeTimingSdkContract.groovy'].eachWithIndex { path, index ->
    reject { contract.verifyArchive(writeZip('leak-' + index + '.jar', [(path): bytes])) }
}
if (args.length > 3) {
    String hook = new File(root, 'gradle/native-test-timing.gradle').getText('UTF-8')
    String pinned = hook.substring(hook.indexOf('def reviewed = [') + 'def reviewed = '.length(), hook.indexOf('    def abi ='))
    Map reviewed = new GroovyShell().evaluate(pinned)
    args.drop(3).each { sdk -> contract.verifySdk([new File(sdk)], reviewed) }
}
// Resolve the REAL ForgeGradle classes (no SDK/native initialization). Verify attachment API,
// not a permissive mock which would accept a misspelled DSL method.
def forge = new URLClassLoader([new File(args[2]).toURI().toURL()] as URL[], getClass().classLoader)
def mod = forge.loadClass('net.minecraftforge.gradle.common.util.ModConfig')
def run = forge.loadClass('net.minecraftforge.gradle.common.util.RunConfig')
def sourceSet = Class.forName('org.gradle.api.tasks.SourceSet')
assert mod.getMethod('source', sourceSet).returnType == Void.TYPE
assert mod.getMethod('getSources').returnType == List
assert run.getMethod('getMods').returnType.name == 'org.gradle.api.NamedDomainObjectContainer'
assert run.getMethod('property', String, String).returnType == Void.TYPE
assert run.getMethod('getTaskName').returnType == String
try { mod.getMethod('sources', sourceSet); throw new AssertionError('Wrong singular ABI accepted') }
catch (NoSuchMethodException expected) { }
// Activation exits BEFORE all Gradle/project access when disabled.
String script = 'import org.gradle.api.GradleException\n' + new File(root, 'gradle/native-test-timing.gradle').getText('UTF-8')
if (System.getenv('BC_TIMING_NATIVE') != 'true') {
    new GroovyShell(new Binding()).evaluate(script)
} else {
    // Inert callback registration only: no fake Forge API or source/task execution is substituted.
    Map installed = [:]
    def extra = new Expando(has: { String key -> installed.containsKey(key) }, set: { String key, value -> installed[key] = value })
    List callbacks = []
    def project = new Expando(extensions: new Expando(extraProperties: extra, findByName: { String key -> null }),
            plugins: new Expando(hasPlugin: { String id -> false }),
            afterEvaluate: { Closure callback -> callbacks.add(callback) })
    def shell = new GroovyShell(new Binding([project: project, buildscript: [sourceFile: new File(root, 'gradle/native-test-timing.gradle')]]))
    shell.evaluate(script)
    assert installed.bcNativeTimingInstalled == true && callbacks.size() == 1
    shell.evaluate(script)
    assert callbacks.size() == 1: 'duplicate application must be inert'
    callbacks.first().call() // no Forge extension -> no invented native/mod/task
    installed.clear()
    project.plugins = new Expando(hasPlugin: { String id -> true })
    try { shell.evaluate(script); throw new AssertionError('Late Forge attachment accepted') }
    catch (org.gradle.api.GradleException expected) { assert expected.message.contains('before the Forge plugin') }
}
println 'NativeTimingSdkContractTest PASS (inert SDK/negative ABI/archive/activation-order controls only); retained: ' + fixtures
