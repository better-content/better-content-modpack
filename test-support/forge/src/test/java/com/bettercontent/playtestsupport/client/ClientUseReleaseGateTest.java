package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure source regression only; no Robot/Forge/client/world process is started. */
class ClientUseReleaseGateTest {
    @Test void firstObservedHeldUseEndTriggersImmediateSingleRelease() {
        ClientUseReleaseGate gate = new ClientUseReleaseGate();
        assertFalse(gate.observe(11, false, true)); // Unattributed use cannot count.
        assertFalse(gate.observe(12, true, false)); // Not started yet.
        assertFalse(gate.observe(13, true, true));
        assertFalse(gate.observe(14, true, true));
        assertTrue(gate.observe(15, true, false));
        assertEquals(13, gate.startTick()); assertEquals(15, gate.endTick());
        assertThrows(IllegalStateException.class, () -> gate.observe(16, true, true));
    }
    @Test void missingActualHeldStateAndSameTickFinishNeverQualify() {
        ClientUseReleaseGate noPress = new ClientUseReleaseGate();
        assertFalse(noPress.observe(1, false, true));
        assertFalse(noPress.observe(2, false, false));
        assertFalse(noPress.started()); assertFalse(noPress.ended());
        ClientUseReleaseGate sameTick = new ClientUseReleaseGate();
        assertFalse(sameTick.observe(7, true, true));
        assertThrows(IllegalStateException.class, () -> sameTick.observe(7, true, false));
    }
}
