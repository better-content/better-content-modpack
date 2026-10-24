package bc.nonshipping.junittiming;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Service-loaded observer. Never wraps test invocation, results or the original Gradle reporter. */
public final class JunitTimingListener implements TestExecutionListener {
    private JunitTimingRecords records;
    private ScheduledExecutorService watchdog;
    private ScheduledFuture<?> watchdogTask;
    private static Map<String,Object> workerBootstrap;
    private static int activePlans;
    private static volatile boolean workerBroken;
    private boolean planActive;
    private final Map<String,Map<String,Object>> pending = new LinkedHashMap<>();
    private volatile boolean broken;

    @Override public synchronized void testPlanExecutionStarted(TestPlan plan) {
        observe(() -> {
            Map<String,Object> config = bootstrapOnce();
            @SuppressWarnings("unchecked") Map<String,Object> baseContext = (Map<String,Object>)config.get("context");
            Map<String,Object> context = new LinkedHashMap<>(baseContext);
            boolean owningPlan = admitPlan(); planActive = true;
            context.put("gradle_owned_plan", owningPlan);
            context.put("plan_role", owningPlan ? "GRADLE_OWNED" : "ENCLOSED_ENGINE");
            @SuppressWarnings("unchecked") Map<String,Object> catalog = (Map<String,Object>)config.get("catalog");
            Path health = Path.of((String)config.get("health_dir")).resolve(ProcessHandle.current().pid() + "-" + UUID.randomUUID() + ".json");
            records = new JunitTimingRecords(Path.of((String)config.get("sink")), health, context, new JunitBaselines(catalog), System::nanoTime);
            for (TestIdentifier root : plan.getRoots()) for (TestIdentifier test : plan.getDescendants(root))
                if (test.isTest()) pending.put(test.getUniqueId(), metadata(test));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> observe(() -> records.close()), "bc-junit-timing-shutdown"));
            watchdog = Executors.newSingleThreadScheduledExecutor(action -> {
                Thread thread = new Thread(action, "bc-junit-own-worker-deadline"); thread.setDaemon(true); return thread;
            });
            watchdogTask = null; // UNKNOWN-only plans never schedule a watchdog.
        });
    }
    @Override public synchronized void dynamicTestRegistered(TestIdentifier test) {
        if (test.isTest()) observe(() -> pending.put(test.getUniqueId(), metadata(test)));
    }
    @Override public synchronized void executionStarted(TestIdentifier test) {
        if (test.isTest()) observe(() -> {
            pending.remove(test.getUniqueId()); records.begin(metadata(test));
            if (records.hasDeadlines() && watchdogTask == null) {
                // Polling latency is observation overhead, never a padded selected cap.
                watchdogTask = watchdog.scheduleWithFixedDelay(() -> {
                    try { if (records.expire()) terminateOwningWorker(); }
                    catch (Throwable error) {
                        records.recordingFailed(); broken = true;
                        if (records.timeoutClaimed()) terminateOwningWorker();
                    }
                }, 0, 1, TimeUnit.MILLISECONDS);
            }
        });
    }
    @Override public synchronized void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (test.isTest()) observe(() -> {
            String status = switch (result.getStatus()) { case SUCCESSFUL -> "SUCCESS"; case FAILED -> "FAILURE"; case ABORTED -> "SKIPPED"; };
            records.end(test.getUniqueId(), status, result.getStatus().name(),
                    result.getThrowable().map(error -> error.getClass().getName()).orElse(null));
            if (records.timeoutClaimed()) watchdog.execute(JunitTimingListener::terminateOwningWorker);
            if (!records.hasDeadlines() && watchdogTask != null) {
                watchdogTask.cancel(false); watchdogTask = null;
            }
        });
    }
    @Override public synchronized void executionSkipped(TestIdentifier test, String reason) {
        observe(() -> {
            if (test.isTest()) {
                pending.remove(test.getUniqueId()); records.skip(metadata(test), reason);
            } else {
                // Skipped containers produce no per-leaf callbacks. Retain scalar planned IDs only.
                String prefix = test.getUniqueId() + "/";
                var skipped = pending.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
                for (String key : skipped) records.skip(pending.remove(key), reason);
            }
        });
    }
    @Override public synchronized void testPlanExecutionFinished(TestPlan plan) {
        try {
            observe(() -> {
                if (watchdog != null) watchdog.shutdown();
                if (broken || workerBroken || !pending.isEmpty()) records.recordingFailed();
                records.close(); pending.clear();
            });
        } finally {
            if (planActive) { planActive = false; releasePlan(); }
        }
    }
    // Gradle owns the outer plan. A test may launch another real Platform plan:
    // observe every case there too, but do not invent extra original Gradle callbacks.
    // Scalar admission only: no TestPlan/TestIdentifier/test/result references retained.
    private static synchronized boolean admitPlan() { return activePlans++ == 0; }
    private static synchronized void releasePlan() {
        if (activePlans < 1) throw new IllegalStateException("Unbalanced JUnit plan admission");
        activePlans--;
    }
    private static synchronized Map<String,Object> bootstrapOnce() throws Exception {
        if (workerBootstrap == null) {
            Path bootstrap = JunitTimingRecords.checked(Path.of(System.getProperty("bc.junit.timing.bootstrap")));
            if (Files.size(bootstrap) > 64L * 1024 * 1024) throw new IllegalArgumentException("Bootstrap exceeds 64 MiB");
            Map<String,Object> config = ScalarJson.object(Files.readString(bootstrap));
            if (!"bc.junit_timing_bootstrap.v1".equals(config.get("schema"))) throw new IllegalArgumentException("Bootstrap ABI");
            workerBootstrap = config;
        }
        return workerBootstrap;
    }
    private static void terminateOwningWorker() {
        // Case expiry is already durable. This separate fixed cleanup grace never changes its cap.
        Thread shutdown = new Thread(() -> System.exit(124), "bc-junit-expired-worker-exit");
        shutdown.setDaemon(true); shutdown.start();
        long cleanupStarted = System.nanoTime();
        while (System.nanoTime() - cleanupStarted < JunitTimingRecords.CLEANUP_GRACE_NS)
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        Runtime.getRuntime().halt(124); // Approved last resort, own worker only, after shutdown-hook grace.
    }
    private static Map<String,Object> metadata(TestIdentifier test) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("unique_id", test.getUniqueId()); data.put("name", test.getLegacyReportingName());
        data.put("display_name", test.getDisplayName()); data.put("parent_unique_id", test.getParentId().orElse(null));
        String className = "UNKNOWN";
        var source = test.getSource().orElse(null);
        if (source instanceof MethodSource method) className = method.getClassName();
        else if (source instanceof ClassSource type) className = type.getClassName();
        data.put("class_name", className); data.put("execution_thread_id", Thread.currentThread().getId());
        return data;
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private void observe(Action action) {
        // JUnit logs/swallow listener errors. Never replace an original engine exception: health guard fails closed instead.
        try { action.run(); }
        catch (Throwable error) { broken = true; workerBroken = true; if (records != null) records.recordingFailed(); }
    }
}
