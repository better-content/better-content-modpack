package com.bettercontent.playtestsupport.client;

/** Pure fail-closed first duration-use transition; only a trigger for OS release, never server completion proof. */
final class ClientUseReleaseGate {
    private long startTick = -1, endTick = -1;
    private boolean ended;
    boolean observe(long tick, boolean realPressAndHeldState, boolean clientUsingItem) {
        if (ended) throw new IllegalStateException("repeat client use after first end");
        if (tick < 0 || !realPressAndHeldState) return false;
        if (clientUsingItem && startTick < 0) startTick = tick;
        if (!clientUsingItem && startTick >= 0) {
            if (tick <= startTick) throw new IllegalStateException("client use ended before distinct held interval");
            endTick = tick; ended = true;
            return true; // Caller MUST physically release before any next client tick or action.
        }
        return false;
    }
    boolean started() { return startTick >= 0; }
    boolean ended() { return ended; }
    long startTick() { return startTick; }
    long endTick() { return endTick; }
}
