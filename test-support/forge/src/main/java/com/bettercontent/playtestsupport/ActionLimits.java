package com.bettercontent.playtestsupport;

/** Pure safety budgets shared by the native actions; no Minecraft side effects. */
public final class ActionLimits {
    private ActionLimits() {}
    public static long digTicks(float progressPerTick, int timeoutTicks) {
        if (!Float.isFinite(progressPerTick) || progressPerTick <= 0 || progressPerTick > 1)
            throw new IllegalArgumentException("invalid native progress");
        if (timeoutTicks < 1 || timeoutTicks > 72000) throw new IllegalArgumentException("invalid deadline");
        long duration = Math.max(2, (long) Math.ceil(1.0 / progressPerTick) + 2L);
        if (duration + 10L >= timeoutTicks) throw new IllegalArgumentException("native mining plus pickup observation exceeds deadline");
        return duration;
    }
    public static int sampleCells(int radius) {
        if (radius < 0 || radius > 4) throw new IllegalArgumentException("radius must be 0..4");
        int side = radius * 2 + 1;
        return side * side * 3;
    }
}
