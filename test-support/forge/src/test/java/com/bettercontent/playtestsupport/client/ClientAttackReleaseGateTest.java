package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure model only: tests authored, not executed without separate human permission. */
class ClientAttackReleaseGateTest {
    private static final String TARGET = "a".repeat(64);
    @Test void realTargetLossRequiresCallbackHeldThenPhysicalReleaseAheadOfNextAttack() {
        ClientAttackReleaseGate gate = new ClientAttackReleaseGate(TARGET);
        assertFalse(gate.observeBeforeKeybind(10, true, true, true, 1));
        assertTrue(gate.observeBeforeKeybind(11, false, true, true, 2));
        assertThrows(IllegalStateException.class, () -> gate.requireSafe(true, true));
        assertThrows(IllegalStateException.class, () -> gate.drainBeforeKeybind(11, true, true, true, false));
        gate.drainBeforeKeybind(11, true, true, false, false);
        assertTrue(gate.drained());
        gate.requireSafe(false, false);
        assertFalse(gate.observeBeforeKeybind(12, false, false, true, 2));
        assertThrows(IllegalStateException.class, () -> gate.observeBeforeKeybind(13, true, false, true, 2));
    }
    @Test void missBeforeRealHeldPressOrUndrainedReleaseAbortsNoSecondTarget() {
        ClientAttackReleaseGate premature = new ClientAttackReleaseGate(TARGET);
        assertThrows(IllegalStateException.class, () -> premature.observeBeforeKeybind(4, false, true, false, 0));
        assertThrows(IllegalStateException.class, () -> premature.requireSafe(true, true));
        ClientAttackReleaseGate missingRelease = new ClientAttackReleaseGate(TARGET);
        assertTrue(missingRelease.observeBeforeKeybind(5, false, true, true, 2));
        assertThrows(IllegalStateException.class, () -> missingRelease.drainBeforeKeybind(5, false, true, false, false));
        assertThrows(IllegalStateException.class, () -> missingRelease.drainBeforeKeybind(5, true, false, false, false));
        assertThrows(IllegalStateException.class, () -> missingRelease.drainBeforeKeybind(5, true, true, false, true));
    }
}
