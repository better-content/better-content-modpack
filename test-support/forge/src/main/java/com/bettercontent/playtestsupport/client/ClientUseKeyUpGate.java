package com.bettercontent.playtestsupport.client;

/** Pure temporal proof: first client-use end alone is never enough to complete a physical release.
 * A real GLFW RELEASE callback AND an observed up-state must be drained before the next vanilla
 * handleKeybinds invocation. This does not infer server consumption or synthesize input.
 */
final class ClientUseKeyUpGate {
    private long firstEndTick = -1, drainedBeforeKeybindTick = -1;
    void firstUseEnded(long tick) {
        if (tick < 0 || firstEndTick >= 0 || drainedBeforeKeybindTick >= 0)
            throw new IllegalStateException("duplicate or invalid first client use end");
        firstEndTick = tick;
    }
    void observedReleaseBeforeKeybinds(long tick, boolean actualReleaseCallback,
                                        boolean mappingDown, boolean physicalInputDown) {
        if (firstEndTick < 0 || tick < firstEndTick || drainedBeforeKeybindTick >= 0 ||
                !actualReleaseCallback || mappingDown || physicalInputDown)
            throw new IllegalStateException("genuine key-up not drained before next use handling");
        drainedBeforeKeybindTick = tick;
    }
    void requireSafeToHandleNextUse(boolean mappingDown, boolean physicalInputDown) {
        if (firstEndTick >= 0 && (drainedBeforeKeybindTick < firstEndTick || mappingDown || physicalInputDown))
            throw new IllegalStateException("second use could start before genuine OS release callback");
    }
    boolean drained() { return firstEndTick >= 0 && drainedBeforeKeybindTick >= firstEndTick; }
    long firstEndTick() { return firstEndTick; }
    long drainedBeforeKeybindTick() { return drainedBeforeKeybindTick; }
}
