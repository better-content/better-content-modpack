package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure model only. Does not open a window, launch Minecraft, Robot or issue an OS key. UNRUN. */
class ClientWalkSafetyTest {
    private static void sample(ClientWalkSafety gate, long tick, double x, double y, double z, double yaw,
                               boolean grounded, boolean inFluid, boolean sprinting, boolean strafing) {
        gate.observe(tick, x, y, z, yaw, true, grounded, false, inFluid,
            false, sprinting, false, false, false, strafing, false, false, true);
    }
    @Test void stationaryYawThenBoundedGenuineForwardProgressIsOnlySupportedEnvelope() {
        ClientWalkSafety gate = new ClientWalkSafety(1.5, 65, 2.5, 359, 12);
        sample(gate, 10, 1.5, 65, 2.5, 359, true, false, false, false);
        sample(gate, 11, 1.5, 65, 2.8, -1, true, false, false, false);
        assertEquals(2, gate.samples()); assertEquals(0, ClientWalkSafety.yawDifference(359, -1));
        assertThrows(IllegalStateException.class,
            () -> sample(gate, 11, 1.5, 65, 3.0, 359, true, false, false, false));
    }
    @Test void unknownTerrainJumpSwimStrafeSprintRotationAndTeleportAlwaysFailClosed() {
        for (int issue = 0; issue < 9; issue++) {
            ClientWalkSafety gate = new ClientWalkSafety(1.5, 65, 2.5, 0, 12);
            sample(gate, 10, 1.5, 65, 2.5, 0, true, false, false, false);
            final int kind = issue;
            assertThrows(IllegalStateException.class, () -> gate.observe(11,
                kind == 6 ? 99 : 1.5, kind == 5 ? 66 : 65, 2.8, kind == 4 ? 90 : 0,
                true, kind != 0, kind == 1, kind == 2, false, kind == 3,
                false, false, kind == 7, kind == 8, false, false, true));
        }
    }
    @Test void malformedPositionYawOrUnboundedHoldIsNeverAccepted() {
        assertThrows(IllegalArgumentException.class, () -> new ClientWalkSafety(Double.NaN, 65, 2, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> new ClientWalkSafety(1, 65, 2, 0, 61));
        ClientWalkSafety gate = new ClientWalkSafety(1, 65, 2, 0, 4);
        assertThrows(IllegalStateException.class,
            () -> sample(gate, 2, Double.POSITIVE_INFINITY, 65, 2, 0, true, false, false, false));
    }
}
