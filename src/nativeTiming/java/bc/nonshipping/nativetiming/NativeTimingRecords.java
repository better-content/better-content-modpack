package bc.nonshipping.nativetiming;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Scalar-only observation state. Never holds native objects, callbacks or Throwables. */
public final class NativeTimingRecords {
    // FileChannel locks are process-wide on Linux. Serialize same-JVM writers too.
    private static final Object APPEND_MONITOR = new Object();
    private record Observation(Map<String, Object> fields, long started) {}
    private final Map<String, Observation> active = new LinkedHashMap<>();
    private final java.util.Set<String> completed = new java.util.HashSet<>();
    private final Path sink;
    private final Path health;
    private final Map<String, Object> context;
    private final LongSupplier clock;
    private final String invocation = UUID.randomUUID().toString();
    private boolean failed;
    private boolean closed;
    private long startedCount;
    private long terminalCount;

    public NativeTimingRecords(Path sink, Path health, Map<String, Object> context, LongSupplier clock) {
        this.sink = checked(sink);
        this.health = checked(health);
        this.context = new LinkedHashMap<>(context);
        this.clock = clock;
    }

    public synchronized String begin(String name, String structure, int timeoutTicks,
                                     boolean required, boolean flaky, int maxAttempts, int requiredSuccesses) throws IOException {
        if (closed) throw new IllegalStateException("Native timing already closed");
        String id = UUID.randomUUID().toString();
        Map<String, Object> fields = new LinkedHashMap<>(context);
        fields.put("schema", "bc.timing.v1");
        fields.put("operation_id", id);
        fields.put("invocation_id", invocation);
        fields.put("scope", "native_test");
        fields.put("name", name);
        fields.put("structure_name", structure);
        fields.put("result_semantics", "native_attempt_not_reporter_verdict");
        fields.put("measurement_boundary", "startExecution_stopwatch_start_to_terminal_listener_including_setup");
        fields.put("units", "nanoseconds");
        fields.put("clock", "monotonic");
        fields.put("configured_timeout_ns", null);
        fields.put("timeout_scope", "native_test");
        fields.put("configured_timeout_ticks", timeoutTicks);
        fields.put("expected_runtime_ns", null);
        fields.put("timeout_policy", "UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED");
        fields.put("required", required);
        fields.put("flaky", flaky);
        fields.put("max_attempts", maxAttempts);
        fields.put("required_successes", requiredSuccesses);
        long started = clock.getAsLong();
        active.put(id, new Observation(fields, started));
        startedCount++;
        append(sink, event(fields, "start", "RUNNING", null, null, null));
        return id;
    }

    public synchronized void end(String id, String status, long nativeDurationMs, String exceptionType) throws IOException {
        Observation observation = active.get(id);
        // Native duplicate notifications must not create duplicate terminals or another start.
        if (observation == null) {
            if (completed.contains(id)) return;
            failed = true;
            throw new IllegalStateException("Native terminal without an observed start");
        }
        if (!status.equals("SUCCEEDED") && !status.equals("FAILED") && !status.equals("TIMED_OUT"))
            throw new IllegalArgumentException("Invalid native outcome");
        long elapsed = Math.max(0L, clock.getAsLong() - observation.started());
        append(sink, event(observation.fields(), "terminal", status, elapsed, nativeDurationMs, exceptionType));
        active.remove(id);
        completed.add(id);
        terminalCount++;
    }

    public synchronized void recordingFailed() { failed = true; }

    /** Best effort at JVM shutdown; SIGKILL leaves a missing health receipt, never acceptance. */
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        boolean interrupted = !active.isEmpty();
        for (Observation observation : active.values()) {
            append(sink, event(observation.fields(), "terminal", "INTERRUPTED",
                    Math.max(0L, clock.getAsLong() - observation.started()), null, null));
            terminalCount++;
        }
        active.clear();
        completed.clear();
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("schema", "bc.native_timing_health.v1");
        receipt.put("invocation_id", invocation);
        receipt.put("healthy", !failed && !interrupted);
        receipt.put("interrupted", interrupted);
        receipt.put("started_count", startedCount);
        receipt.put("terminal_count", terminalCount);
        // CREATE_NEW prevents accepting stale/foreign receipts. This is not a test verdict.
        checked(health);
        Files.createDirectories(health.getParent());
        byte[] bytes = (json(receipt) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(health, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeFully(channel, bytes);
            channel.force(true);
        }
    }

    private static Map<String, Object> event(Map<String, Object> fields, String event, String status,
                                           Long elapsed, Long nativeMs, String exceptionType) {
        Map<String, Object> record = new LinkedHashMap<>(fields);
        record.put("event", event);
        record.put("status", status);
        record.put("duration_ns", elapsed);
        record.put("native_duration_ms", nativeMs);
        record.put("native_duration_source", "GameTestInfo.getRunTime_Stopwatch_elapsed_milliseconds");
        record.put("native_duration_precision", "milliseconds_truncated");
        record.put("exception_type", exceptionType);
        record.put("observed_at", Instant.now().toString());
        return record;
    }

    private static Path checked(Path path) {
        if (!path.isAbsolute() || !path.equals(path.normalize()))
            throw new IllegalArgumentException("Native timing path must be absolute and normalized");
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Native timing symlink path");
        }
        return path;
    }

    private static void append(Path path, Map<String, Object> record) throws IOException {
        synchronized (APPEND_MONITOR) {
            checked(path);
            Files.createDirectories(path.getParent());
            byte[] bytes = (json(record) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = channel.lock()) {
                channel.position(channel.size());
                writeFully(channel, bytes);
                channel.force(true);
            }
        }
    }

    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    // Only scalar values and maps are supported; deliberately no generic object/Throwable serialization.
    static String json(Map<String, ?> values) {
        StringBuilder out = new StringBuilder("{");
        for (var entry : values.entrySet()) {
            if (out.length() > 1) out.append(',');
            quote(out, entry.getKey());
            out.append(':');
            Object value = entry.getValue();
            if (value == null || value instanceof Boolean || value instanceof Number) out.append(value);
            else if (value instanceof String text) quote(out, text);
            else throw new IllegalArgumentException("Non-scalar timing field");
        }
        return out.append('}').toString();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
