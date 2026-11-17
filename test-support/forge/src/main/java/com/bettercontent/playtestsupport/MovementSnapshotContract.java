package com.bettercontent.playtestsupport;

/** Fail closed on nonfinite movement state before serializing a real server actor snapshot. */
final class MovementSnapshotContract {
    private MovementSnapshotContract() {}
    static void requireFinite(double x, double y, double z, float yaw, float pitch, float fallDistance) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) ||
                !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(fallDistance))
            throw new IllegalStateException("player movement snapshot has nonfinite actor state");
    }
}
