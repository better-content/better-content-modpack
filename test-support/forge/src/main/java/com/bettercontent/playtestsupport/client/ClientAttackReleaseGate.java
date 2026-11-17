package com.bettercontent.playtestsupport.client;

/** Pure fail-closed pre-keybind gate. It does not call vanilla attack/dig or choose a replacement target.
 * Target loss is NOT proof of break: B's actual inbound START/progress/STOP and server source chain decide that. */
final class ClientAttackReleaseGate {
    private final String frozenProjection;
    private long targetLostTick = -1, drainedBeforeKeybindTick = -1;
    ClientAttackReleaseGate(String frozenProjection) {
        if (frozenProjection == null || !frozenProjection.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("reviewed crosshair projection absent");
        this.frozenProjection = frozenProjection;
    }
    String projection() { return frozenProjection; }
    long targetLostTick() { return targetLostTick; }
    long drainedBeforeKeybindTick() { return drainedBeforeKeybindTick; }
    boolean drained() { return targetLostTick >= 0 && drainedBeforeKeybindTick >= targetLostTick; }
    boolean observeBeforeKeybind(long tick, boolean sameCurrent, boolean pressed, boolean pressCallback, int heldTicks) {
        if (tick < 0 || targetLostTick >= 0 && tick < targetLostTick)
            throw new IllegalStateException("nonmonotonic physical attack observation");
        if (sameCurrent) {
            if (targetLostTick >= 0) throw new IllegalStateException("lost attack target reappeared; no adaptive restart");
            return false;
        }
        if (targetLostTick < 0) {
            targetLostTick = tick;
            if (!pressed || !pressCallback || heldTicks < 1)
                throw new IllegalStateException("crosshair changed before a bounded physical held attack");
            return true; // Caller releases actual Robot button BEFORE vanilla reads keyAttack again.
        }
        return false;
    }
    void drainBeforeKeybind(long tick, boolean released, boolean releaseCallback, boolean mappingDown, boolean physicalDown) {
        if (targetLostTick < 0 || tick < targetLostTick || !released || !releaseCallback || mappingDown || physicalDown)
            throw new IllegalStateException("attack button not physically released/callback-drained before next keybind");
        drainedBeforeKeybindTick = tick;
    }
    void requireSafe(boolean mappingDown, boolean physicalDown) {
        if (targetLostTick >= 0 && (!drained() || mappingDown || physicalDown))
            throw new IllegalStateException("next vanilla attack opportunity forbidden until genuine OS button-up");
    }
}
