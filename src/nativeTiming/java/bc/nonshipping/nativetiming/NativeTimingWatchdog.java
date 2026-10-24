package bc.nonshipping.nativetiming;

import java.util.concurrent.locks.LockSupport;

/** Own-JVM daemon. State is the scalar-only recorder; no native object/callback/world reference. */
public final class NativeTimingWatchdog {
    public static final int WALL_TIMEOUT_EXIT = 124;
    // Independent fixed shutdown containment, NOT added to any test's exact 2x case budget.
    public static final long CLEANUP_GRACE_NS = 2_000_000_000L;
    private NativeTimingWatchdog() { }
    public static void start(NativeTimingRecords records) {
        if (!records.claimWatchdog()) return; // UNKNOWN has original tick cap and no wall watchdog.
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    if (records.expireDeadlines()) { terminate(records); return; }
                } catch (Throwable error) {
                    records.recordingFailed();
                    System.err.println("BC_NATIVE_TIMING_WATCHDOG_FAILED " + error.getClass().getName());
                    if (records.wallTimedOut()) terminate(records);
                    return; // Never throw into native callbacks or replace their original errors.
                }
                LockSupport.parkNanos(1_000_000L); // scheduling granularity, never added to the cap
            }
        }, "bc-native-timing-wall-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
    private static void terminate(NativeTimingRecords records) {
        try { records.writeWallTimeoutHealth(); }
        catch (Throwable error) {
            records.recordingFailed();
            System.err.println("BC_NATIVE_TIMING_TIMEOUT_HEALTH_FAILED " + error.getClass().getName());
        }
        // Persisted intent precedes this thread. Do not exit under the recorder monitor. Ordinary
        // shutdown hooks censor other pending attempts; neither worlds nor output files are deleted.
        Thread shutdown = new Thread(() -> System.exit(WALL_TIMEOUT_EXIT), "bc-native-timing-owned-exit");
        shutdown.setDaemon(true);
        long cleanupStarted = System.nanoTime();
        shutdown.start();
        while (System.nanoTime() - cleanupStarted < CLEANUP_GRACE_NS)
            LockSupport.parkNanos(Math.max(1L, CLEANUP_GRACE_NS - (System.nanoTime() - cleanupStarted)));
        try { records.writeEscalation(); }
        catch (Throwable error) { System.err.println("BC_NATIVE_TIMING_ESCALATION_HEALTH_FAILED " + error.getClass().getName()); }
        finally { Runtime.getRuntime().halt(WALL_TIMEOUT_EXIT); } // Only this owned JVM, after fixed cleanup grace.
    }
}
