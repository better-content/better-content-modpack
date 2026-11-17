package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure source regression for real actor movement field serialization; tests are not executed by agent. */
final class MovementSnapshotContractTest {
    @Test void finiteServerActorCoordinatesAndOrientationRequired() {
        assertDoesNotThrow(() -> MovementSnapshotContract.requireFinite(-0.125, 65.0, 12.75, -180f, 80f, 0.0f));
        assertThrows(IllegalStateException.class,
                () -> MovementSnapshotContract.requireFinite(Double.NaN, 65, 0, 0, 0, 0));
        assertThrows(IllegalStateException.class,
                () -> MovementSnapshotContract.requireFinite(0, Double.POSITIVE_INFINITY, 0, 0, 0, 0));
        assertThrows(IllegalStateException.class,
                () -> MovementSnapshotContract.requireFinite(0, 65, 0, Float.NEGATIVE_INFINITY, 0, 0));
        assertThrows(IllegalStateException.class,
                () -> MovementSnapshotContract.requireFinite(0, 65, 0, 0, Float.NaN, 0));
        assertThrows(IllegalStateException.class,
                () -> MovementSnapshotContract.requireFinite(0, 65, 0, 0, 0, Float.NaN));
    }
}
