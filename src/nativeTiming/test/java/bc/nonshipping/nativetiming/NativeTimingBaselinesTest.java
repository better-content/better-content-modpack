package bc.nonshipping.nativetiming;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure-JDK decision controls. Synthetic samples are not real native timing baselines. */
public final class NativeTimingBaselinesTest {
    public static void main(String[] args) {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("scope", "native_test", "units", "nanoseconds", "clock", "monotonic"));
        for (String key : List.of("revision", "input_id", "profile", "environment_id", "cache_id", "name", "structure_name", "result_semantics")) fields.put(key, key + "_control");
        fields.put("measurement_boundary", "startExecution_stopwatch_start_to_terminal_listener_including_setup");
        fields.put("required", true); fields.put("flaky", false); fields.put("max_attempts", 1); fields.put("required_successes", 1);
        Map<String, Object> identity = new LinkedHashMap<>(fields);
        identity.put("duration_boundary", identity.remove("measurement_boundary"));
        Map<String, Object> group = new LinkedHashMap<>(Map.of("identity", identity, "samples", List.of(Map.of("duration_ns", 101)),
                "successful_sample_count", 1, "effective_timeout_ns", 202L, "expected_runtime_rational", Map.of("numerator", 101L, "denominator", 1)));
        Map<String, Object> catalog = new LinkedHashMap<>(Map.of("status", "VALID", "sources", List.of(Map.of("path", "/inert/control")), "groups", List.of(group)));
        NativeTimingBaselines baselines = new NativeTimingBaselines(catalog);
        check(baselines.decision(fields).get("configured_timeout_ns").equals(202L), "exact median cap");
        check(baselines.decision(fields).get("expected_runtime_ns").equals(101L), "expected retained");
        for (String key : fields.keySet()) {
            Map<String, Object> changed = new LinkedHashMap<>(fields); changed.put(key, "different");
            check(baselines.decision(changed).get("configured_timeout_ns") == null, "mismatch " + key);
        }
        Map<String, Object> unknown = new LinkedHashMap<>(fields); unknown.put("cache_id", "UNKNOWN");
        check(baselines.decision(unknown).get("configured_timeout_ns") == null, "unknown identity");
        group.put("effective_timeout_ns", 203L); group.put("expected_runtime_rational", Map.of("numerator", 203L, "denominator", 2));
        Map<String, Object> half = baselines.decision(fields);
        check(half.get("configured_timeout_ns").equals(203L) && half.get("expected_runtime_ns") == null, "half nanosecond exact rational");
        check(NativeTimingRecords.json(half).contains("\"denominator\":2"), "nested scalar rational serialization");
        group.put("effective_timeout_ns", 204L);
        reject(() -> baselines.decision(fields));
        group.put("effective_timeout_ns", 203L);
        catalog.put("groups", List.of(group, group)); reject(() -> baselines.decision(fields));
        catalog.put("status", "UNKNOWN");
        check(baselines.decision(fields).get("configured_timeout_ns") == null, "invalid/incomplete snapshot unknown");
        check(NativeTimingBaselines.unknown().decision(fields).get("configured_timeout_ns") == null, "no invented cap");
        System.out.println("NativeTimingBaselinesTest PASS (inert exact/unknown/rational/negative controls)");
    }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("invalid baseline accepted"); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
