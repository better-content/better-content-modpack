package bc.nonshipping.nativetiming;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestTimeoutException;

/** Attached to the existing source-native owning mod, never a separately registered mod. */
public final class NativeTimingObserver {
    private static NativeTimingRecords records;
    private static boolean recordingFailure;
    private NativeTimingObserver() {}

    public static void bootstrap() {
        // Initializes health for genuine zero-case parser-only GameTest servers; no case is invented.
        try { initialize(); }
        catch (Throwable error) { noteFailure(error, null); }
    }

    private static synchronized NativeTimingRecords initialize() {
        if (records != null) return records;
        Map<String, Object> context = new LinkedHashMap<>();
        for (String name : new String[] {"RUN_ID", "REVISION", "INPUT_ID", "PROFILE", "ENVIRONMENT_ID", "CACHE_ID"}) {
            String value = System.getenv("BC_TIMING_" + name);
            context.put(name.toLowerCase(Locale.ROOT), value == null || value.isBlank() ? "UNKNOWN" : value);
        }
        String cap = System.getenv("BC_TIMING_TIMEOUT_SECONDS");
        Long enclosing = cap == null || cap.isBlank() ? null :
                new BigDecimal(cap).multiply(new BigDecimal("1000000000")).longValueExact();
        if (enclosing != null && enclosing <= 0) throw new IllegalArgumentException("Invalid enclosing timeout");
        context.put("enclosing_process_timeout_ns", enclosing);
        context.put("task_path", System.getProperty("bc.nativeTiming.task", "UNKNOWN"));
        context.put("build_root", System.getProperty("bc.nativeTiming.buildRoot", "UNKNOWN"));
        NativeTimingRecords created = new NativeTimingRecords(
                Path.of(System.getProperty("bc.nativeTiming.file")),
                Path.of(System.getProperty("bc.nativeTiming.health")), context, System::nanoTime);
        if (recordingFailure) created.recordingFailed();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { created.close(); }
            catch (Throwable error) { noteFailure(error, null); }
        }, "bc-native-timing-shutdown"));
        records = created;
        return created;
    }

    /** Called only after the native stopwatch has started, before original test dispatch. */
    public static void started(GameTestInfo info) {
        try {
            NativeTimingRecords sink = initialize();
            String id = sink.begin(info.getTestName(), info.getStructureName(), info.getTimeoutTicks(),
                    info.isRequired(), info.isFlaky(), info.maxAttempts(), info.requiredSuccesses());
            // Listener stores UUID only, not info, the sink, a lambda capture or any world reference.
            info.addListener(new AttemptListener(id));
        } catch (Throwable error) { noteFailure(error, info.getError()); }
    }

    private static void terminal(String id, GameTestInfo info) {
        Throwable original = info.getError();
        try {
            String status = original instanceof GameTestTimeoutException ? "TIMED_OUT" :
                    original != null ? "FAILED" : "SUCCEEDED";
            initialize().end(id, status, info.getRunTime(), original == null ? null : original.getClass().getName());
        } catch (Throwable error) { noteFailure(error, original); }
    }

    private static synchronized void noteFailure(Throwable error, Throwable original) {
        recordingFailure = true;
        if (records != null) records.recordingFailed();
        // Do not throw into the native listener loop: every original reporter still gets invoked.
        // The post-task health guard is independently authoritative even without an original error.
        if (original != null && original != error) {
            try { original.addSuppressed(error); } catch (Throwable ignored) { /* health remains failed */ }
        }
        System.err.println("BC_NATIVE_TIMING_RECORDING_FAILED " + error.getClass().getName());
    }

    private static final class AttemptListener implements GameTestListener {
        private final String id;
        private AttemptListener(String id) { this.id = id; }
        @Override public void testStructureLoaded(GameTestInfo info) { /* no world observation */ }
        @Override public void testPassed(GameTestInfo info) { terminal(id, info); }
        @Override public void testFailed(GameTestInfo info) { terminal(id, info); }
    }
}
