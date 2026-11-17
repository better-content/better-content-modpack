package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Source-only structural gate; independent qualification evaluates TPS/recovery and the raw trace. */
final class SyntheticScaleMechanicsTest {
    @Test void initialSlowTerrainMustNotTurnValidNativeMeasurementIntoFailedTerminal() {
        // The native completion gate has no TPS/performance parameter. Early slow terrain
        // cannot turn an otherwise complete native measurement into a failed action.
        assertTrue(SyntheticScaleCohort.mechanicallyComplete(8, 8, 600, true, true, true));
        assertTrue(SyntheticScaleCohort.mechanicallyComplete(16, 16, 600, true, true, true));
        assertTrue(SyntheticScaleCohort.mechanicallyComplete(32, 32, 720, true, true, true));
    }
    @Test void missingOrBrokenNativeMeasurementsRemainFailures() {
        assertFalse(SyntheticScaleCohort.mechanicallyComplete(8, 7, 600, true, true, true));
        assertFalse(SyntheticScaleCohort.mechanicallyComplete(8, 8, 0, true, true, true));
        assertFalse(SyntheticScaleCohort.mechanicallyComplete(8, 8, 600, false, true, true));
        assertFalse(SyntheticScaleCohort.mechanicallyComplete(8, 8, 600, true, false, true));
        assertFalse(SyntheticScaleCohort.mechanicallyComplete(8, 8, 600, true, true, false));
    }
}
