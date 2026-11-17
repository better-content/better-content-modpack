package com.bettercontent.playtestsupport.client;

/** Pure conservative flat-ground, forward-only movement envelope. Never moves or steers a player. */
final class ClientWalkSafety {
    private final double startX, startY, startZ, startYaw;
    private final int maxTicks;
    private double lastX, lastY, lastZ;
    private long lastTick = -1;
    private int samples;

    ClientWalkSafety(double x, double y, double z, double yaw, int maxTicks) {
        if (!finite(x, y, z, yaw) || Math.abs(yaw) > 36_000 || maxTicks < 4 || maxTicks > 60)
            throw new IllegalArgumentException("unreviewed flat walking start or tick cap");
        startX = lastX = x; startY = lastY = y; startZ = lastZ = z;
        startYaw = yaw; this.maxTicks = maxTicks;
    }
    static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    static double yawDifference(double a, double b) {
        return Math.abs(Math.IEEEremainder(a - b, 360.0));
    }
    void observe(long tick, double x, double y, double z, double yaw,
                 boolean survival, boolean grounded, boolean swimming, boolean inFluid,
                 boolean passenger, boolean sprinting, boolean crouching, boolean flying,
                 boolean jumping, boolean strafing, boolean backing, boolean interacting, boolean screenAbsent) {
        if (tick < 0 || (lastTick >= 0 && tick <= lastTick) || samples >= maxTicks + 1 ||
                !finite(x, y, z, yaw) || Math.abs(yaw) > 36_000 || !survival || !grounded || swimming || inFluid || passenger ||
                sprinting || crouching || flying || jumping || strafing || backing || !screenAbsent ||
                interacting || yawDifference(yaw, startYaw) > 5.0 || Math.abs(y - startY) > 0.25 ||
                Math.hypot(x - lastX, z - lastZ) > 0.55 ||
                Math.hypot(x - startX, z - startZ) > maxTicks * 0.35 + 0.25)
            throw new IllegalStateException("unsupported terrain/posture/yaw/teleport/sprint during reviewed walk");
        lastX = x; lastY = y; lastZ = z; lastTick = tick; samples++;
    }
    double startYaw() { return startYaw; }
    int samples() { return samples; }
}
