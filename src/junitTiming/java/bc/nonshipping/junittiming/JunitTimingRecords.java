package bc.nonshipping.junittiming;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;

/** Thread-safe scalar-only state; test objects, descriptors and Throwables never escape callbacks. */
final class JunitTimingRecords {
    static final String BOUNDARY = "junit_platform_executionStarted_to_executionFinished";
    static final long CLEANUP_GRACE_NS = 2_000_000_000L;
    private static final Object APPEND_MONITOR = new Object();
    private record Active(Map<String,Object> fields, long started, Long cap) {}
    private final Map<String,Active> active = new LinkedHashMap<>();
    private final Path sink, health;
    private final Map<String,Object> context;
    private final JunitBaselines baselines;
    private final LongSupplier clock;
    private final String invocation = UUID.randomUUID().toString();
    private boolean failed, closed, timeoutClaimed;
    private long startedCount, terminalCount, skippedCount;
    JunitTimingRecords(Path sink, Path health, Map<String,Object> context, JunitBaselines baselines, LongSupplier clock) {
        this.sink = checked(sink); this.health = checked(health); this.context = new LinkedHashMap<>(context);
        this.baselines = baselines; this.clock = clock;
    }
    synchronized void begin(Map<String,Object> metadata) throws IOException { begin(metadata, true); }
    private void begin(Map<String,Object> metadata, boolean executed) throws IOException {
        if (closed) throw new IllegalStateException("Recorder closed");
        long started = clock.getAsLong();
        String unique = (String)metadata.get("unique_id");
        if (active.containsKey(unique)) throw new IllegalStateException("Duplicate start");
        Map<String,Object> fields = new LinkedHashMap<>(context);
        fields.putAll(metadata); fields.put("schema", "bc.timing.v1"); fields.put("scope", "junit_test");
        fields.put("operation_id", UUID.randomUUID().toString()); fields.put("invocation_id", invocation);
        fields.put("units", "nanoseconds"); fields.put("clock", "monotonic");
        // Enclosed Launcher executions are genuine cases, but cannot lend their
        // timing samples to a Gradle-owned plan (or vice versa).
        fields.put("duration_boundary", Boolean.FALSE.equals(context.get("gradle_owned_plan"))
                ? "junit_platform_enclosed_executionStarted_to_executionFinished" : BOUNDARY);
        fields.put("timeout_scope", "junit_test");
        fields.put("execution_observed", executed);
        fields.put("timeout_enforcement", executed ? "OWNING_JUNIT_WORKER_WATCHDOG" : "NOT_EXECUTED");
        fields.putAll((executed ? baselines : new JunitBaselines(Map.of())).decision(fields));
        active.put(unique, new Active(fields, started, (Long)fields.get("configured_timeout_ns")));
        startedCount++;
        append(sink, event(fields, "start", "RUNNING", null, null, null));
    }
    synchronized void end(String unique, String status, String engineStatus, String exceptionType) throws IOException {
        Active observation = active.get(unique);
        if (observation == null) throw new IllegalStateException("Terminal without start");
        // A delayed scheduler cannot turn an over-budget engine completion into a successful sample.
        if (observation.cap() != null && clock.getAsLong() - observation.started() >= observation.cap()) {
            expire(); return;
        }
        append(sink, event(observation.fields(), "terminal", status,
                Math.max(0, clock.getAsLong() - observation.started()), engineStatus, exceptionType));
        active.remove(unique); terminalCount++;
    }
    synchronized void skip(Map<String,Object> metadata, String reason) throws IOException {
        // Skips have no execution duration, are never successful baseline samples.
        Map<String,Object> skipped = new LinkedHashMap<>(metadata);
        skipped.put("skip_boundary", "junit_platform_executionSkipped_not_executed");
        begin(skipped, false);
        Active observation = active.get(metadata.get("unique_id"));
        Map<String,Object> fields = new LinkedHashMap<>(observation.fields()); fields.put("skip_reason", reason);
        append(sink, event(fields, "terminal", "SKIPPED", null, "SKIPPED", null));
        active.remove(metadata.get("unique_id")); terminalCount++; skippedCount++;
    }
    /** The callback and watchdog arbitrate under the same monitor: one terminal only. */
    synchronized boolean expire() throws IOException {
        if (closed) return false;
        long now = clock.getAsLong();
        String expired = null;
        for (var entry : active.entrySet()) {
            Active value = entry.getValue();
            if (value.cap() != null && now - value.started() >= value.cap()) { expired = entry.getKey(); break; }
        }
        if (expired == null) return false;
        failed = true;
        timeoutClaimed = true;
        Active value = active.get(expired);
        Map<String,Object> fields = new LinkedHashMap<>(value.fields());
        fields.put("race_precedence", "watchdog_won_before_engine_terminal_callback");
        fields.put("worker_shutdown_intent", "System.exit(124)_then_own_worker_Runtime.halt(124)_if_shutdown_hangs");
        fields.put("cleanup_grace_ns", CLEANUP_GRACE_NS);
        fields.put("cleanup_grace_scope", "post_case_timeout_worker_shutdown_not_test_budget");
        append(sink, event(fields, "terminal", "TIMED_OUT", Math.max(0, now - value.started()), null, null));
        active.remove(expired); terminalCount++;
        close(); // Durable pending INTERRUPTED records and unhealthy receipt precede worker termination.
        return true;
    }
    synchronized boolean hasDeadlines() { return active.values().stream().anyMatch(value -> value.cap() != null); }
    synchronized boolean timeoutClaimed() { return timeoutClaimed; }
    synchronized void recordingFailed() { failed = true; }
    synchronized void close() throws IOException {
        if (closed) return;
        boolean interrupted = !active.isEmpty();
        for (Active value : active.values()) {
            append(sink, event(value.fields(), "terminal", "INTERRUPTED", Math.max(0, clock.getAsLong() - value.started()), null, null));
            terminalCount++;
        }
        active.clear();
        Map<String,Object> receipt = new LinkedHashMap<>();
        receipt.put("schema", "bc.junit_timing_health.v1"); receipt.put("invocation_id", invocation);
        receipt.put("task_nonce", context.get("task_nonce")); receipt.put("task_path", context.get("task_path"));
        receipt.put("gradle_owned_plan", context.get("gradle_owned_plan")); receipt.put("plan_role", context.get("plan_role"));
        receipt.put("sink", sink.toString()); receipt.put("worker_pid", ProcessHandle.current().pid());
        receipt.put("healthy", !failed && !interrupted); receipt.put("interrupted", interrupted);
        receipt.put("started_count", startedCount); receipt.put("terminal_count", terminalCount); receipt.put("skipped_count", skippedCount);
        checked(health); Files.createDirectories(health.getParent());
        try (FileChannel channel = FileChannel.open(health, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            write(channel, ScalarJson.encode(receipt) + "\n"); channel.force(true);
        }
        closed = true;
    }
    private static Map<String,Object> event(Map<String,Object> fields, String event, String status, Long duration, String engine, String exception) {
        Map<String,Object> row = new LinkedHashMap<>(fields);
        row.put("event", event); row.put("status", status); row.put("duration_ns", duration);
        row.put("engine_status", engine); row.put("exception_type", exception); row.put("observed_at", Instant.now().toString());
        return row;
    }
    static Path checked(Path path) {
        if (!path.isAbsolute() || !path.normalize().equals(path)) throw new IllegalArgumentException("Absolute normalized path required");
        for (Path at = path; at != null; at = at.getParent()) if (Files.isSymbolicLink(at)) throw new IllegalArgumentException("Symlink evidence path");
        return path;
    }
    private static void append(Path path, Map<String,Object> row) throws IOException {
        synchronized (APPEND_MONITOR) {
            checked(path); Files.createDirectories(path.getParent());
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = channel.lock()) {
                channel.position(channel.size()); write(channel, ScalarJson.encode(row) + "\n"); channel.force(true);
            }
        }
    }
    private static void write(FileChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
