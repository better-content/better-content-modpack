import java.io.File
import java.util.concurrent.TimeUnit

/** Public-facade controller budget, independent of the tighter scenario/source budgets. */
fun superviseFacade(root: File, command: List<String>, cwd: File = root, budgetSeconds: Long = 28800,
                    log: File? = null, captureLimitBytes: Long = 0, environment: Map<String, String> = emptyMap(), removedEnvironment: Set<String> = emptySet(), onStarted: (Process?) -> Unit = {}): Int {
    require(budgetSeconds in 1..28800)
    val args = mutableListOf("python3", "-B", root.resolve("scripts/bounded_facade.py").absolutePath,
        "--budget-seconds", budgetSeconds.toString(), "--cwd", cwd.absolutePath,
        "--parent-pid", ProcessHandle.current().pid().toString())
    log?.let { args += listOf("--log", it.absolutePath) }
    require(captureLimitBytes >= 0 && (captureLimitBytes == 0L || log != null))
    if (captureLimitBytes > 0) args += listOf("--capture-limit", captureLimitBytes.toString(), "--quiet-capture")
    args += "--"; args += command
    val process = ProcessBuilder(args).directory(root).inheritIO().apply { removedEnvironment.forEach { environment().remove(it) }; environment().putAll(environment) }.start()
    onStarted(process)
    var interrupted = false
    try {
        check(process.waitFor(budgetSeconds + 15, TimeUnit.SECONDS)) { "subprocess supervisor exceeded its own deadline; cleanup is unproved" }
        return process.exitValue()
    } catch (failure: Throwable) {
        interrupted = failure is InterruptedException
        process.destroy()
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                failure.addSuppressed(IllegalStateException("supervisor cleanup deadline exhausted; owned descendants require reconciliation"))
            }
        } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
        throw failure
    } finally { onStarted(null); if (interrupted) Thread.currentThread().interrupt() }
}

fun captureFacade(root: File, command: List<String>, cwd: File = root): Pair<Int, String> {
    val directory = File(System.getProperty("user.home"), ".tmp/bounded-commands").also { it.mkdirs() }
    val log = File.createTempFile("facade-", ".log", directory)
    val status = superviseFacade(root, command, cwd, 30, log, captureLimitBytes = 4 * 1024 * 1024)
    require(log.length() <= 4 * 1024 * 1024) { "facade capture exceeds bounded output limit: $log" }
    val output = log.readText()
    if (status == 0) log.delete()
    return status to output
}
