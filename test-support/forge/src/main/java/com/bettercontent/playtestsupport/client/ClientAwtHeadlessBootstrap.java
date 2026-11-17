package com.bettercontent.playtestsupport.client;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Client-only, nonshipping bootstrap called by the opt-in Main.main HEAD mixin AFTER Main.<clinit>.
 * Vanilla Main sets java.awt.headless=true in <clinit>; correcting it later in the client tick
 * cannot repair GraphicsEnvironment's already cached headless state. Never reset AWT internals.
 */
public final class ClientAwtHeadlessBootstrap {
    private static volatile boolean ready;
    private ClientAwtHeadlessBootstrap() {}

    /** Pure predicate: absent client mailbox properties leave the normal game untouched. */
    static boolean reviewedOptIn(String run, String token, String directory) {
        if (run == null || token == null || directory == null ||
                !run.matches("[A-Za-z0-9._:-]{1,128}") || !token.matches("[0-9a-f]{64}")) return false;
        try {
            Path path = Path.of(directory);
            return path.isAbsolute() && path.normalize().equals(path) &&
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
        } catch (RuntimeException invalid) { return false; }
    }

    /** This exact public method is invoked only at Main.main HEAD by the client-only mixin. */
    public static void beforeMain() {
        String run = System.getProperty("bc.playtest.client.runId", "");
        String token = System.getProperty("bc.playtest.client.token", "");
        String directory = System.getProperty("bc.playtest.client.directory", "");
        if (run.isEmpty() && token.isEmpty() && directory.isEmpty()) return;
        if (ready || !reviewedOptIn(run, token, directory))
            throw new IllegalStateException("invalid or repeated opt-in client AWT bootstrap");
        // Main.<clinit> already executed; opt in before ANY client AWT/Robot initialization.
        System.setProperty("java.awt.headless", "false");
        if (GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("AWT headless state was cached before test-only Main bootstrap");
        ready = true;
    }

    /** Robot is not constructed merely because vanilla client code changed a system property. */
    static void requireReady() {
        if (!ready || !"false".equals(System.getProperty("java.awt.headless")) ||
                GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("genuine client input requires early nonheadless AWT bootstrap");
    }
}
