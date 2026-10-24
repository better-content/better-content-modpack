package bc.nonshipping.nativetiming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Own-JVM subprocess negatives; no Minecraft/Gradle/provider execution. */
public final class NativeTimingWatchdogTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) { child(Path.of(args[0]), args[1]); return; }
        Path root = Files.createTempDirectory(Path.of(args[0]), "watchdog-controls-");
        for (String mode : List.of("normal", "hanging-shutdown", "failure-first", "unknown")) {
            Path fixture = Files.createDirectory(root.resolve(mode));
            long started = System.nanoTime();
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                    "-cp", System.getProperty("java.class.path"), NativeTimingWatchdogTest.class.getName(), fixture.toString(), mode)
                    .redirectErrorStream(true).redirectOutput(fixture.resolve("child.log").toFile()).start();
            if (!child.waitFor(8, TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(3, TimeUnit.SECONDS); throw new AssertionError("owned child did not terminate: " + mode); }
            long elapsed = System.nanoTime() - started;
            int expected = mode.equals("normal") || mode.equals("hanging-shutdown") ? 124 : mode.equals("failure-first") ? 7 : 0;
            check(child.exitValue() == expected, mode + " exit " + child.exitValue());
            String health = Files.readString(fixture.resolve("health.json"));
            List<String> lines = Files.readAllLines(fixture.resolve("records.jsonl"));
            if (expected == 124) {
                check(health.contains("\"healthy\":false") && health.contains("\"wall_timed_out\":true"), mode + " fail-closed health");
                check(lines.stream().anyMatch(s -> s.contains("\"status\":\"TIMED_OUT\"") && s.contains("\"native_duration_ms\":null")), mode + " honest wall terminal");
                check(Files.readString(fixture.resolve("health.json.wall-timeout.json")).contains("\"cleanup_grace_ns\":2000000000"), "durable separate cleanup intent");
                check(lines.stream().anyMatch(s -> s.contains("\"status\":\"INTERRUPTED\"")), mode + " pending shutdown censor");
            } else {
                check(health.contains("\"healthy\":true"), mode + " paired recorder health (not native acceptance)");
                check(!Files.exists(fixture.resolve("health.json.wall-timeout.json")), mode + " no wall termination");
            }
            if (mode.equals("failure-first")) check(lines.get(1).contains("\"exception_type\":\"original.failure.Marker\""), "original delivered failure identity wins");
            check(Files.exists(fixture.resolve("health.json.escalation.json")) == mode.equals("hanging-shutdown"), mode + " escalation only for hanging shutdown");
            if (mode.equals("hanging-shutdown")) check(elapsed >= NativeTimingWatchdog.CLEANUP_GRACE_NS, "cleanup grace independent from case budget");
        }
        System.out.println("NativeTimingWatchdogTest PASS (ordinary exit/hanging shutdown/original failure/UNKNOWN); retained: " + root);
    }
    private static void child(Path root, String mode) throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        for (String key : List.of("revision", "input_id", "profile", "environment_id", "cache_id")) context.put(key, "inert");
        Map<String, Object> identity = new LinkedHashMap<>(context);
        identity.putAll(Map.of("scope", "native_test", "units", "nanoseconds", "clock", "monotonic", "name", "control", "structure_name", "example:control"));
        identity.putAll(Map.of("result_semantics", "native_attempt_not_reporter_verdict", "required", true, "flaky", false, "max_attempts", 1, "required_successes", 1,
                "duration_boundary", "startExecution_stopwatch_start_to_terminal_listener_including_setup"));
        NativeTimingBaselines baselines = new NativeTimingBaselines(Map.of("status", "VALID", "sources", List.of(), "groups", List.of(Map.of("identity", identity,
                "samples", List.of(), "successful_sample_count", 1, "effective_timeout_ns", 200_000_000L, "expected_runtime_rational", Map.of("numerator", 100_000_000L, "denominator", 1)))));
        NativeTimingRecords records = new NativeTimingRecords(root.resolve("records.jsonl"), root.resolve("health.json"), context, System::nanoTime, baselines);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { records.close(); } catch (Exception error) { throw new AssertionError(error); } }));
        if (mode.equals("hanging-shutdown")) Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { Thread.sleep(30_000); } catch (InterruptedException ignored) { } }));
        String id = records.begin(mode.equals("unknown") ? "unknown" : "control", "example:control", 4321, true, false, 1, 1);
        if (mode.equals("failure-first")) {
            NativeTimingWatchdog.start(records);
            records.end(id, "FAILED", 0, "original.failure.Marker");
            Thread.sleep(350); System.exit(7);
        }
        NativeTimingWatchdog.start(records);
        if (mode.equals("unknown")) {
            check(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.getName().equals("bc-native-timing-wall-watchdog")), "UNKNOWN starts no watchdog");
            Thread.sleep(350); records.end(id, "SUCCEEDED", 0, null); return;
        }
        records.begin("pending_unknown", "example:control", 4321, true, false, 1, 1);
        Thread.sleep(30_000); throw new AssertionError("deadline not enforced");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
