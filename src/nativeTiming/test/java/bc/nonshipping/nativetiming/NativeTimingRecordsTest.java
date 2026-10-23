package bc.nonshipping.nativetiming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Inert JDK-only writer controls. NOT GameTests, native passes or empirical native baselines. */
public final class NativeTimingRecordsTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Path.of(args[0]).isAbsolute()) throw new IllegalArgumentException("Absolute retained fixture parent required");
        Path root = Files.createTempDirectory(Path.of(args[0]), "native-timing-controls-");
        AtomicLong clock = new AtomicLong(100);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("profile", "inert_control");
        context.put("enclosing_process_timeout_ns", null);
        Path sink = root.resolve("records.jsonl");
        NativeTimingRecords records = new NativeTimingRecords(sink, root.resolve("health.json"), context, clock::get);
        String id = records.begin("quoted\"\\\n\t\ud83c\udf33", "example:structure", 400, true, false, 1, 1);
        clock.set(350);
        records.end(id, "SUCCEEDED", 0, null);
        records.end(id, "SUCCEEDED", 0, null);
        String failed = records.begin("repeat", "example:structure", 400, true, true, 3, 2);
        clock.set(900);
        records.end(failed, "FAILED", 2, "java.lang.AssertionError");
        String retry = records.begin("repeat", "example:structure", 400, true, true, 3, 2);
        check(!retry.equals(failed), "retries/repeated names need distinct identity");
        clock.set(1200);
        records.end(retry, "TIMED_OUT", 3, "net.minecraft.gametest.framework.GameTestTimeoutException");
        records.close(); records.close();
        var lines = Files.readAllLines(sink);
        check(lines.size() == 6, "one start/terminal per attempt; duplicate terminal is idempotent");
        check(lines.get(1).contains("\"duration_ns\":250"), "real monotonic elapsed");
        check(lines.get(1).contains("\"native_duration_ms\":0"), "true native zero kept separate");
        check(lines.get(0).contains("\"configured_timeout_ticks\":400"), "native cap remains ticks");
        check(lines.get(0).contains("\"configured_timeout_ns\":null"), "no fabricated native wall cap");
        check(lines.get(0).contains("\"expected_runtime_ns\":null"), "configured cap is not expectation");
        check(lines.get(0).contains("\\ud83c\\udf33"), "surrogate escaping");
        check(Files.readString(root.resolve("health.json")).contains("\"healthy\":true"), "paired health");
        NativeTimingRecords interrupted = new NativeTimingRecords(root.resolve("interrupted.jsonl"), root.resolve("interrupted.health"), context, clock::get);
        interrupted.begin("unfinished", "example:structure", 9, true, false, 1, 1);
        clock.set(2000); interrupted.close();
        String censored = Files.readAllLines(root.resolve("interrupted.jsonl")).get(1);
        check(censored.contains("\"status\":\"INTERRUPTED\""), "unfinished observations censored");
        check(censored.contains("\"native_duration_ms\":null"), "no fabricated native terminal duration");
        check(Files.readString(root.resolve("interrupted.health")).contains("\"healthy\":false"), "interruption unhealthy");
        NativeTimingRecords empty = new NativeTimingRecords(root.resolve("empty.jsonl"), root.resolve("empty.health"), context, clock::get);
        empty.close();
        check(!Files.exists(root.resolve("empty.jsonl")), "zero cases invent no starts");
        check(Files.readString(root.resolve("empty.health")).contains("\"started_count\":0"), "zero-case health");
        NativeTimingRecords unhealthy = new NativeTimingRecords(root.resolve("bad.jsonl"), root.resolve("bad.health"), context, clock::get);
        unhealthy.recordingFailed(); unhealthy.close();
        check(Files.readString(root.resolve("bad.health")).contains("\"healthy\":false"), "recording failure fails health");
        Path target = Files.createDirectory(root.resolve("target"));
        Files.createSymbolicLink(root.resolve("symlink"), target);
        unsafe(() -> new NativeTimingRecords(root.resolve("symlink/data"), root.resolve("safe-health"), context, clock::get));
        unsafe(() -> new NativeTimingRecords(Path.of("relative"), root.resolve("safe-health"), context, clock::get));
        unsafe(() -> new NativeTimingRecords(root.resolve("a/../data"), root.resolve("safe-health"), context, clock::get));
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] threads = new Thread[4];
        for (int index = 0; index < 4; index++) {
            int writer = index;
            threads[index] = new Thread(() -> {
                try {
                    NativeTimingRecords concurrent = new NativeTimingRecords(root.resolve("concurrent.jsonl"), root.resolve("writer-" + writer + ".health"), context, System::nanoTime);
                    for (int sample = 0; sample < 25; sample++) {
                        String token = concurrent.begin("inert-" + writer + "-" + sample, "example:structure", 2, true, false, 1, 1);
                        concurrent.end(token, "SUCCEEDED", 0, null);
                    }
                    concurrent.close();
                } catch (Throwable failure) { error.compareAndSet(null, failure); }
            });
            threads[index].start();
        }
        for (Thread thread : threads) { thread.join(10000); check(!thread.isAlive(), "bounded writer termination"); }
        if (error.get() != null) throw new AssertionError("Concurrent writer", error.get());
        check(Files.readAllLines(root.resolve("concurrent.jsonl")).size() == 200, "concurrent line integrity");
        System.out.println("NativeTimingRecordsTest inert controls PASS; retained fixtures: " + root);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void unsafe(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsafe path accepted");
    }
}
