package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic exception-shape controls only; actual registry assertions run in the two callers. */
final class PlainJvmRegistryBootstrapTest {
    private static ExceptionInInitializerError fixture(String missingMethod) {
        RuntimeException cause = new RuntimeException(
            "Error computing listener list for net.minecraftforge.network.NetworkEvent",
            new NoSuchMethodException(missingMethod));
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("net.minecraftforge.eventbus.api.EventListenerHelper", "computeListenerList", "EventListenerHelper.java", 63),
            new StackTraceElement("net.minecraftforge.network.NetworkConstants", "<clinit>", "NetworkConstants.java", 43)
        });
        ExceptionInInitializerError boundary = new ExceptionInInitializerError(cause);
        boundary.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("net.minecraftforge.network.NetworkHooks", "init", "NetworkHooks.java", 52)
        });
        return boundary;
    }

    @Test void acceptsOnlyExactPinnedNetworkConstructorAndCausalFrames() {
        assertTrue(PlainJvmRegistryBootstrap.knownNetworkBoundary(
            fixture("net.minecraftforge.network.NetworkEvent.<init>()")));
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(
            fixture("net.minecraftforge.network.NetworkEventOther.<init>()")));
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(
            fixture("net.minecraftforge.network.NetworkEvent.<init>(java.lang.String)")));
        var unrelated = fixture("net.minecraftforge.network.NetworkEvent.<init>()");
        unrelated.setStackTrace(new StackTraceElement[0]);
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(unrelated));
        var wrongCauseFrames = fixture("net.minecraftforge.network.NetworkEvent.<init>()");
        wrongCauseFrames.getCause().setStackTrace(new StackTraceElement[0]);
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(wrongCauseFrames));
    }

    @Test void doesNotSuppressOtherBootstrapFailures() {
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(new ExceptionInInitializerError("unknown")));
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(
            new ExceptionInInitializerError(new IllegalArgumentException("registry not ready"))));
        assertFalse(PlainJvmRegistryBootstrap.knownNetworkBoundary(new ExceptionInInitializerError(
            new NoSuchMethodException("net.minecraftforge.network.NetworkEvent.<init>()"))));
    }
}
