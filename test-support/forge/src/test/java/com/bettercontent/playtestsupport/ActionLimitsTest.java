package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure boundary tests: authored only, no test/world execution authorized by this file. */
final class ActionLimitsTest {
    @Test void miningEstimateRequiresARealProgressWindowAndPickupDeadline() {
        assertEquals(22, ActionLimits.digTicks(0.05f, 40));
        assertThrows(IllegalArgumentException.class, () -> ActionLimits.digTicks(0, 40));
        assertThrows(IllegalArgumentException.class, () -> ActionLimits.digTicks(Float.NaN, 40));
        assertThrows(IllegalArgumentException.class, () -> ActionLimits.digTicks(0.05f, 32));
        assertThrows(IllegalArgumentException.class, () -> ActionLimits.digTicks(0.000001f, 72000));
        assertEquals(12, ActionLimits.digTicks(0.1f, 72000));
    }
    @Test void worldSampleCannotExpandBeyondThreeBoundedVerticalSlices() {
        assertEquals(3, ActionLimits.sampleCells(0));
        assertEquals(243, ActionLimits.sampleCells(4));
        assertThrows(IllegalArgumentException.class, () -> ActionLimits.sampleCells(-1));
        assertThrows(IllegalArgumentException.class, () -> ActionLimits.sampleCells(5));
    }
}
