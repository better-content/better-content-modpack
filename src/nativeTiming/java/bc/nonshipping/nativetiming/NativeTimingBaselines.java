package bc.nonshipping.nativetiming;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One validated bootstrap snapshot; only JSON scalar metadata, never native object references. */
public final class NativeTimingBaselines {
    private static final List<String> KEYS = List.of("scope", "units", "clock", "revision", "input_id",
            "profile", "environment_id", "cache_id", "name", "structure_name", "result_semantics",
            "required", "flaky", "max_attempts", "required_successes", "duration_boundary");
    private final Map<String, Object> catalog;
    public NativeTimingBaselines(Map<String, Object> catalog) { this.catalog = catalog; }
    public static NativeTimingBaselines unknown() { return new NativeTimingBaselines(Map.of()); }

    public Map<String, Object> decision(Map<String, Object> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured_timeout_ns", null);
        result.put("expected_runtime_ns", null);
        result.put("expected_runtime_rational", null);
        result.put("successful_sample_count", 0);
        result.put("baseline_sources", catalog.getOrDefault("sources", List.of()));
        result.put("baseline_samples", List.of());
        result.put("selection_method", "empirical_median_all_comparable_successes");
        result.put("selection_frozen_before_invocation", true);
        result.put("legacy_safety_cap_ns", null); // Authored native tick cap stays independently in ticks.
        result.put("timeout_policy", "UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED");
        result.put("selection_reason", "no_exact_comparable_native_baseline");
        if (!"VALID".equals(catalog.get("status"))) return result;
        Map<String, Object> identity = new LinkedHashMap<>();
        for (String key : KEYS) {
            Object value = key.equals("duration_boundary") ? fields.get("measurement_boundary") : fields.get(key);
            if (value == null || value instanceof String text && (text.isBlank() || text.equalsIgnoreCase("UNKNOWN"))) return result;
            identity.put(key, value);
        }
        Object found = null;
        if (!(catalog.get("groups") instanceof List<?> groups)) throw new IllegalArgumentException("Invalid catalog groups");
        for (Object entry : groups) {
            if (!(entry instanceof Map<?, ?> group) || !(group.get("identity") instanceof Map<?, ?> candidate))
                throw new IllegalArgumentException("Invalid native baseline identity");
            boolean exact = candidate.keySet().equals(identity.keySet());
            for (var field : identity.entrySet()) exact &= equalScalar(field.getValue(), candidate.get(field.getKey()));
            if (!exact) continue;
            if (found != null) throw new IllegalArgumentException("Ambiguous native baseline");
            found = entry;
        }
        if (found == null) return result;
        Map<?, ?> group = (Map<?, ?>) found;
        long cap = integer(group.get("effective_timeout_ns"));
        long count = integer(group.get("successful_sample_count"));
        if (!(group.get("expected_runtime_rational") instanceof Map<?, ?> rational)) throw new IllegalArgumentException("Missing rational median");
        long numerator = integer(rational.get("numerator"));
        long denominator = integer(rational.get("denominator"));
        if (cap <= 0 || count <= 0 || numerator <= 0 || (denominator != 1 && denominator != 2) ||
                !java.math.BigInteger.valueOf(numerator).multiply(java.math.BigInteger.TWO)
                .equals(java.math.BigInteger.valueOf(cap).multiply(java.math.BigInteger.valueOf(denominator))))
            throw new IllegalArgumentException("Native baseline is not exact positive 2x median");
        result.put("configured_timeout_ns", cap);
        result.put("expected_runtime_ns", denominator == 1 ? numerator : null);
        result.put("expected_runtime_rational", Map.of("numerator", numerator, "denominator", denominator));
        result.put("timeout_policy", "EXACT_2X_EMPIRICAL_MEDIAN");
        result.put("successful_sample_count", count);
        result.put("baseline_sources", catalog.get("sources"));
        result.put("baseline_samples", group.get("samples"));
        result.put("selection_reason", "exact_comparable_native_baseline");
        return result;
    }
    private static long integer(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("Noninteger native baseline");
        return new BigDecimal(value.toString()).longValueExact();
    }
    private static boolean equalScalar(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        return left.equals(right);
    }
}
