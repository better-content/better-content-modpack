package bc.nonshipping.junittiming;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic clock/race and concurrent durable-writer controls; no test engine invocation. */
public final class JunitTimingRecordsControl {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Files.createDirectories(root);
        Map<String,Object> context = new LinkedHashMap<>();
        for (String key : List.of("revision", "input_id", "profile", "environment_id", "cache_id", "build_root", "task_path")) context.put(key, "control");
        context.put("task_nonce", "control");
        Map<String,Object> metadata = Map.of("unique_id", "[case:1]", "name", "case", "class_name", "control.Cases");
        Map<String,Object> identity = new LinkedHashMap<>(context); identity.remove("task_nonce");
        identity.putAll(Map.of("scope", "junit_test", "units", "nanoseconds", "clock", "monotonic", "duration_boundary", JunitTimingRecords.BOUNDARY, "name", "case", "class_name", "control.Cases"));
        Map<String,Object> group = Map.of("identity", identity, "effective_timeout_ns", 101,
            "expected_runtime_rational", Map.of("numerator", 101, "denominator", 2), "successful_sample_count", 2, "samples", List.of());
        JunitBaselines baseline = new JunitBaselines(Map.of("status", "VALID", "groups", List.of(group), "sources", List.of()));
        AtomicLong clock = new AtomicLong(100);
        JunitTimingRecords early = new JunitTimingRecords(root.resolve("early.jsonl"), root.resolve("early-health.json"), context, baseline, clock::get);
        early.begin(metadata); clock.set(200); early.end("[case:1]", "FAILURE", "FAILED", "original.Error");
        clock.set(202); if (early.expire()) throw new AssertionError("Original failure lost race"); early.close();
        JunitTimingRecords late = new JunitTimingRecords(root.resolve("late.jsonl"), root.resolve("late-health.json"), context, baseline, clock::get);
        late.begin(metadata); clock.addAndGet(101); late.end("[case:1]", "SUCCESS", "SUCCESSFUL", null);
        if (!late.timeoutClaimed()) throw new AssertionError("Late callback faked success");
        String events = Files.readString(root.resolve("late.jsonl"));
        if (!events.contains("TIMED_OUT") || events.contains("\"status\":\"SUCCESS\"")) throw new AssertionError(events);
        if (Files.readString(root.resolve("late-health.json")).contains("\"healthy\":true")) throw new AssertionError("Timeout health");
        Map<String,Object> unknown = new LinkedHashMap<>(context); unknown.put("revision", "UNKNOWN");
        if (baseline.decision(new LinkedHashMap<>(identity) {{ put("revision", "UNKNOWN"); }}).get("configured_timeout_ns") != null) throw new AssertionError("Unknown cap");
        try { new JunitBaselines(Map.of("status", "VALID", "groups", List.of(group, group))).decision(identity); throw new AssertionError("Ambiguity accepted"); }
        catch (IllegalArgumentException expected) { }
        JunitTimingRecords concurrent = new JunitTimingRecords(root.resolve("concurrent.jsonl"), root.resolve("concurrent-health.json"), context,
                new JunitBaselines(Map.of()), System::nanoTime);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                try {
                    String id = "id" + index;
                    concurrent.begin(Map.of("unique_id", id, "name", "case" + index, "class_name", "control.Cases"));
                    concurrent.end(id, "SUCCESS", "SUCCESSFUL", null);
                } catch (Exception error) { throw new RuntimeException(error); }
            }));
        }
        for (Future<?> future : futures) future.get(); pool.shutdown(); concurrent.close();
        List<String> lines = Files.readAllLines(root.resolve("concurrent.jsonl"));
        if (lines.size() != 80) throw new AssertionError("Lost concurrent records");
        for (String line : lines) ScalarJson.object(line);
        try { ScalarJson.object("{\"a\":1,\"a\":2}"); throw new AssertionError("Duplicate JSON key"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS deterministic failure/deadline races, odd median, unknown/ambiguous catalog, 40 concurrent pairs, duplicate JSON rejection");
    }
}
