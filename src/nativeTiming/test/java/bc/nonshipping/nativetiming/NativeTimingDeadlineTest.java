package bc.nonshipping.nativetiming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Inert scalar deadline controls, no GameTestInfo/world or inferred tick-to-wall conversion. */
public final class NativeTimingDeadlineTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]), "deadline-controls-");
        Map<String, Object> context = new LinkedHashMap<>();
        for (String key : List.of("revision", "input_id", "profile", "environment_id", "cache_id")) context.put(key, "inert");
        Map<String, Object> identity = new LinkedHashMap<>(context);
        identity.putAll(Map.of("scope", "native_test", "units", "nanoseconds", "clock", "monotonic", "name", "control", "structure_name", "example:control"));
        identity.putAll(Map.of("result_semantics", "native_attempt_not_reporter_verdict", "required", true, "flaky", false, "max_attempts", 1, "required_successes", 1,
                "duration_boundary", "startExecution_stopwatch_start_to_terminal_listener_including_setup"));
        NativeTimingBaselines baselines = new NativeTimingBaselines(Map.of("status", "VALID", "sources", List.of(), "groups", List.of(Map.of("identity", identity,
                "samples", List.of(), "successful_sample_count", 1, "effective_timeout_ns", 200L, "expected_runtime_rational", Map.of("numerator", 100, "denominator", 1)))));
        AtomicLong clock = new AtomicLong(1000);
        NativeTimingRecords records = new NativeTimingRecords(root.resolve("records.jsonl"), root.resolve("health.json"), context, clock::get, baselines);
        String id = records.begin("control", "example:control", 9999, true, false, 1, 1);
        clock.set(1199); check(!records.expireDeadlines(), "before exact cap");
        clock.set(1200); check(records.expireDeadlines(), "at exact 2x cap");
        records.end(id, "SUCCEEDED", 0, null); // Original native terminal cannot overwrite wall timeout.
        records.close();
        var lines = Files.readAllLines(root.resolve("records.jsonl"));
        check(lines.size() == 2 && lines.get(1).contains("\"status\":\"TIMED_OUT\""), "one censored terminal");
        check(lines.get(1).contains("\"native_duration_ms\":null"), "no native time invented by watchdog");
        check(lines.get(0).contains("\"configured_timeout_ticks\":9999"), "authored tick cap unchanged");
        String health = Files.readString(root.resolve("health.json"));
        check(health.contains("\"healthy\":false") && health.contains("\"wall_timed_out\":true"), "expiry unhealthy");
        clock.set(2000);
        NativeTimingRecords unknown = new NativeTimingRecords(root.resolve("unknown.jsonl"), root.resolve("unknown.health"), context, clock::get, baselines);
        unknown.begin("different", "example:control", 1, true, false, 1, 1);
        clock.set(Long.MAX_VALUE); check(!unknown.expireDeadlines(), "unknown baseline never becomes a wall cap"); unknown.close();
        clock.set(4000);
        NativeTimingRecords late = new NativeTimingRecords(root.resolve("late.jsonl"), root.resolve("late.health"), context, clock::get, baselines);
        String lateId = late.begin("control", "example:control", 9999, true, false, 1, 1);
        clock.set(4300); late.end(lateId, "SUCCEEDED", 1, null); late.close();
        check(Files.readAllLines(root.resolve("late.jsonl")).get(1).contains("\"status\":\"TIMED_OUT\""), "late terminal cannot race scheduled deadline");
        clock.set(5000);
        NativeTimingRecords failure = new NativeTimingRecords(root.resolve("failure.jsonl"), root.resolve("failure.health"), context, clock::get, baselines);
        String failureId = failure.begin("control", "example:control", 9999, true, false, 1, 1);
        clock.set(5300); failure.end(failureId, "FAILED", 0, "original.failure.Marker");
        check(!failure.expireDeadlines(), "already-delivered original failure wins even when watchdog descheduled");
        failure.close();
        check(Files.readAllLines(root.resolve("failure.jsonl")).get(1).contains("\"status\":\"FAILED\""), "original failed result preserved");
        System.out.println("NativeTimingDeadlineTest PASS (inert exact cap/late terminal/UNKNOWN); retained: " + root);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
