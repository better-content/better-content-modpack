package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure model regressions only; never starts Robot, Xvfb, Minecraft or a keyboard/mouse session. */
class HeldInputStateTest {
    private static final class RecordingDevice implements HeldInputState.Device {
        int presses, releases;
        boolean failOnPress, failFirstRelease;
        @Override public void press() { presses++; if (failOnPress) throw new IllegalStateException("partial press"); }
        @Override public void release() {
            releases++; if (failFirstRelease && releases == 1) throw new IllegalStateException("first release failed");
        }
    }
    private static HeldInputState hold(RecordingDevice device) {
        return new HeldInputState(HeldInputState.Kind.USE_MOUSE, 17, 1234, 1, 100, 80, device);
    }
    @Test void authenticCallbackAndHeldStateThenPhysicalReleaseAreAllRequired() {
        RecordingDevice device = new RecordingDevice(); HeldInputState state = hold(device);
        state.begin(); state.callback(1234, 1, 1); state.observe(101, true, true, true, true);
        assertTrue(state.pressCallback()); assertEquals(1, state.heldStateTicks());
        state.release(17, 102); assertEquals(1, device.releases);
        assertThrows(IllegalStateException.class, () -> state.verifyReleased(103, true, true, false));
        state.callback(1234, 0, 1); state.verifyReleased(103, true, true, false);
        state.close(); assertEquals(1, device.releases);
    }
    @Test void foreignOrMissingCallbackNeverProvesAuthenticInputAndAlwaysReleases() {
        RecordingDevice device = new RecordingDevice(); HeldInputState state = hold(device);
        state.begin(); state.callback(4321, 1, 1); state.callback(1234, 1, 2);
        assertThrows(IllegalStateException.class, () -> state.observe(105, true, true, true, true));
        assertEquals(1, device.releases); assertFalse(state.pressCallback());
        assertThrows(IllegalStateException.class, () -> state.release(17, 106));
    }
    @Test void focusLossDisconnectScreenChangeAndExpiryReleaseOnEveryExit() {
        for (int cause = 0; cause < 4; cause++) {
            RecordingDevice device = new RecordingDevice(); HeldInputState state = hold(device);
            state.begin(); state.callback(1234, 1, 1);
            final int issue = cause;
            assertThrows(IllegalStateException.class, () -> state.observe(issue == 3 ? 180 : 101,
                issue != 0, issue != 1, issue != 2, true));
            assertEquals(1, device.releases);
        }
    }
    @Test void partialPressAndForeignReleaseCannotLeaveAnInputHeld() {
        RecordingDevice failure = new RecordingDevice(); failure.failOnPress = true;
        HeldInputState partial = hold(failure);
        assertThrows(IllegalStateException.class, partial::begin);
        assertEquals(1, failure.releases);
        RecordingDevice device = new RecordingDevice(); HeldInputState state = hold(device);
        state.begin(); assertThrows(IllegalStateException.class, () -> state.release(99, 101));
        assertEquals(1, device.releases);
        assertThrows(IllegalStateException.class, state::begin);
    }
    @Test void independentWallWatchdogCloseNeverImpersonatesQualifiedRelease() {
        RecordingDevice device = new RecordingDevice(); HeldInputState state = hold(device);
        state.begin(); state.callback(1234, 1, 1); state.observe(101, true, true, true, true);
        state.close(); state.callback(1234, 0, 1); // OS release attempted, but no planned release receipt.
        assertEquals(1, device.releases);
        assertThrows(IllegalStateException.class, () -> state.verifyReleased(102, true, true, false));
    }
    @Test void failedPhysicalReleaseIsRetriedBeforeFailingClosed() {
        RecordingDevice device = new RecordingDevice(); device.failFirstRelease = true;
        HeldInputState state = hold(device);
        state.begin();
        assertThrows(IllegalStateException.class, state::close);
        assertEquals(2, device.releases);
        state.close(); assertEquals(2, device.releases);
    }
    @Test void onboardingChordRequiresBothRealCallbacksAndHeldModifierStateThroughRelease() {
        RecordingDevice device = new RecordingDevice();
        HeldInputState chord = new HeldInputState(HeldInputState.Kind.ONBOARDING_CHORD,
            3, 444, 75, 200, 30, device);
        chord.begin(); chord.callback(444, 1, 75);
        chord.observe(201, true, true, true, false);
        assertEquals(0, chord.heldStateTicks());
        chord.modifierCallback(1); chord.observe(202, true, true, true, true);
        chord.release(3, 203); chord.callback(444, 0, 75);
        assertThrows(IllegalStateException.class, () -> chord.verifyReleased(204, true, true, false));
        chord.modifierCallback(0); chord.verifyReleased(204, true, true, false);
        assertEquals(1, device.releases);
    }
    @Test void onboardingChordWithoutModifierCallbackFailsAndReleases() {
        RecordingDevice device = new RecordingDevice();
        HeldInputState chord = new HeldInputState(HeldInputState.Kind.ONBOARDING_CHORD,
            3, 444, 75, 200, 30, device);
        chord.begin(); chord.callback(444, 1, 75);
        assertThrows(IllegalStateException.class, () -> chord.observe(205, true, true, true, true));
        assertEquals(1, device.releases);
    }
    @Test void rejectsUnlimitedHoldsAndMissingWindowIdentity() {
        RecordingDevice device = new RecordingDevice();
        assertThrows(IllegalArgumentException.class, () -> new HeldInputState(HeldInputState.Kind.USE_MOUSE,
            1, 1234, 1, 100, 201, device));
        assertThrows(IllegalArgumentException.class, () -> new HeldInputState(HeldInputState.Kind.INVENTORY_KEY,
            1, 1234, 69, 100, 9, device));
        assertThrows(IllegalArgumentException.class, () -> new HeldInputState(HeldInputState.Kind.ONBOARDING_CHORD,
            1, 1234, 75, 100, 41, device));
        assertThrows(IllegalArgumentException.class, () -> new HeldInputState(HeldInputState.Kind.USE_MOUSE,
            1, 0, 1, 100, 80, device));
    }
}
