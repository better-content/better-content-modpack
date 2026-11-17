package com.bettercontent.playtestsupport.client;

/** Pure bounded lifecycle for one physically pressed key/mouse button. No callback injection is an input source. */
final class HeldInputState implements AutoCloseable {
    interface Device { void press(); void release(); }
    enum Kind { USE_MOUSE, USE_KEY, INVENTORY_KEY, ONBOARDING_CHORD }
    final Kind kind;
    final long sequence, window, startedTick;
    final int code, maxTicks;
    private final Device device;
    private boolean pressed, released, pressCallback, releaseCallback;
    private boolean modifierPressCallback, modifierReleaseCallback;
    private long releaseTick = -1;
    private int heldStateTicks;

    HeldInputState(Kind kind, long sequence, long window, int code, long startedTick, int maxTicks, Device device) {
        if (kind == null || device == null || sequence < 1 || window == 0 || startedTick < 0 || code < 0 ||
                maxTicks < 2 || maxTicks > 200 || (kind == Kind.INVENTORY_KEY && maxTicks > 8) ||
                (kind == Kind.ONBOARDING_CHORD && maxTicks > 40))
            throw new IllegalArgumentException("unbounded or unbound physical input");
        this.kind = kind; this.sequence = sequence; this.window = window; this.code = code;
        this.startedTick = startedTick; this.maxTicks = maxTicks; this.device = device;
    }
    synchronized void begin() {
        if (pressed || released) throw new IllegalStateException("physical input already consumed");
        // Mark before OS call so a partial Robot press is always followed by release on failure.
        pressed = true;
        try { device.press(); } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    synchronized void callback(long actualWindow, int action, int actualCode) {
        if (actualWindow != window || actualCode != code || !pressed) return;
        if (action == 1 && !released) pressCallback = true; // GLFW_PRESS
        if (action == 0 && released) releaseCallback = true; // GLFW_RELEASE after Robot release
    }
    synchronized void modifierCallback(int action) {
        if (!pressed || kind != Kind.ONBOARDING_CHORD) return;
        if (action == 1 && !released) modifierPressCallback = true;
        if (action == 0 && released) modifierReleaseCallback = true;
    }
    synchronized void observe(long nativeTick, boolean joined, boolean focused, boolean screenValid, boolean physicallyDown) {
        if (!pressed || released) return;
        if (nativeTick < startedTick || nativeTick - startedTick >= maxTicks ||
                !joined || !focused || !screenValid) {
            close();
            throw new IllegalStateException("held physical input expired, disconnected, unfocused or changed screen");
        }
        if (pressCallback && (kind != Kind.ONBOARDING_CHORD || modifierPressCallback) && physicallyDown) heldStateTicks++;
        if (nativeTick - startedTick >= 5 && (!pressCallback || heldStateTicks == 0)) {
            close();
            throw new IllegalStateException("physical input did not reach joined client callback and held state");
        }
    }
    synchronized void release(long expectedSequence, long nativeTick) {
        if (!pressed || released || expectedSequence != sequence || nativeTick < startedTick) {
            close();
            throw new IllegalStateException("foreign/duplicate physical input release");
        }
        releaseTick = nativeTick;
        close(); // Always physically release, even when callback/state evidence is absent.
        if (!pressCallback || heldStateTicks == 0)
            throw new IllegalStateException("physical input callback/held state not observed before release");
    }
    synchronized void verifyReleased(long nativeTick, boolean joined, boolean focused, boolean physicallyDown) {
        if (!pressed || !released || releaseTick < 0 || nativeTick < releaseTick ||
                nativeTick - releaseTick > 10 || !joined || !focused || physicallyDown || !releaseCallback ||
                (kind == Kind.ONBOARDING_CHORD && (!modifierPressCallback || !modifierReleaseCallback)))
            throw new IllegalStateException("physical release not observed on the same focused joined client");
    }
    synchronized boolean isPressed() { return pressed && !released; }
    synchronized boolean pressCallback() { return pressCallback; }
    synchronized boolean releaseCallback() { return releaseCallback; }
    synchronized boolean modifierPressCallback() { return modifierPressCallback; }
    synchronized boolean modifierReleaseCallback() { return modifierReleaseCallback; }
    synchronized int heldStateTicks() { return heldStateTicks; }
    synchronized long releaseTick() { return releaseTick; }
    @Override public synchronized void close() {
        if (pressed && !released) {
            released = true; // Idempotent across callback, failure, disconnect and shutdown hook.
            try { device.release(); }
            catch (RuntimeException | Error first) {
                try { device.release(); } catch (RuntimeException | Error second) { first.addSuppressed(second); }
                throw first;
            }
        }
    }
}
