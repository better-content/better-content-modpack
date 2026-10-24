package bc.nonshipping.junittiming;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

final class JunitBaselines {
    static final List<String> KEYS = List.of("scope", "units", "clock", "revision", "input_id", "profile",
            "environment_id", "cache_id", "build_root", "task_path", "class_name", "name", "duration_boundary");
    private final Map<String,Object> catalog;
    JunitBaselines(Map<String,Object> catalog) { this.catalog = catalog; }
    Map<String,Object> decision(Map<String,Object> fields) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("configured_timeout_ns", null); result.put("expected_runtime_ns", null);
        result.put("timeout_policy", "UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED");
        result.put("selection_reason", "no_exact_comparable_junit_platform_baseline");
        result.put("successful_sample_count", 0);
        if (!"VALID".equals(catalog.get("status"))) return result;
        Map<String,Object> identity = new LinkedHashMap<>();
        for (String key : KEYS) {
            Object value = fields.get(key);
            if (!(value instanceof String text) || text.isBlank() || text.equalsIgnoreCase("UNKNOWN")) return result;
            identity.put(key, value);
        }
        Map<?,?> selected = null;
        for (Object item : (List<?>)catalog.get("groups")) {
            if (!(item instanceof Map<?,?> group)) throw new IllegalArgumentException("Invalid catalog group");
            if (!identity.equals(group.get("identity"))) continue;
            if (selected != null) throw new IllegalArgumentException("Ambiguous JUnit baseline");
            selected = group;
        }
        if (selected == null) return result;
        long cap = integer(selected.get("effective_timeout_ns"));
        long count = integer(selected.get("successful_sample_count"));
        Map<?,?> rational = (Map<?,?>)selected.get("expected_runtime_rational");
        long numerator = integer(rational.get("numerator")), denominator = integer(rational.get("denominator"));
        if (cap <= 0 || count <= 0 || numerator <= 0 || (denominator != 1 && denominator != 2) ||
                !BigInteger.valueOf(numerator).multiply(BigInteger.TWO).equals(BigInteger.valueOf(cap).multiply(BigInteger.valueOf(denominator))))
            throw new IllegalArgumentException("Not positive exact 2x median");
        result.put("configured_timeout_ns", cap);
        result.put("expected_runtime_ns", denominator == 1 ? numerator : null);
        result.put("expected_runtime_rational", Map.of("numerator", numerator, "denominator", denominator));
        result.put("timeout_policy", "EXACT_2X_EMPIRICAL_MEDIAN");
        result.put("successful_sample_count", count);
        result.put("baseline_sources", catalog.get("sources")); result.put("baseline_samples", selected.get("samples"));
        result.put("selection_reason", "exact_comparable_junit_platform_baseline");
        return result;
    }
    private static long integer(Object value) { return new BigDecimal(value.toString()).longValueExact(); }
}
