package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** Pure temporal and pinned-bytecode regressions only; no AWT/GLFW calls, GUI, Minecraft or tests run here. */
class ClientUseKeyUpGateTest {
    @Test void firstUseEndCannotReachNextHandleKeybindsUntilRealReleaseCallbackAndMappingUp() {
        for (int postConsumptionHeldCount : new int[] { 3, 0 }) { // stack>1; then empty-hand follow-up risk
            var use = new ClientUseReleaseGate(); var keyUp = new ClientUseKeyUpGate();
            assertFalse(use.observe(10, true, true));
            assertFalse(use.observe(41, true, true));
            assertTrue(use.observe(42, true, false));
            keyUp.firstUseEnded(42);
            assertThrows(IllegalStateException.class, () -> keyUp.requireSafeToHandleNextUse(true, true),
                "the next vanilla use handler would act on remaining=" + postConsumptionHeldCount);
            assertThrows(IllegalStateException.class,
                () -> keyUp.observedReleaseBeforeKeybinds(42, false, false, false)); // Property change is not a callback.
            assertThrows(IllegalStateException.class,
                () -> keyUp.observedReleaseBeforeKeybinds(42, true, true, true)); // Callback alone, still held.
            assertFalse(keyUp.drained());
            keyUp.observedReleaseBeforeKeybinds(42, true, false, false);
            assertTrue(keyUp.drained());
            assertEquals(42, keyUp.drainedBeforeKeybindTick());
            assertDoesNotThrow(() -> keyUp.requireSafeToHandleNextUse(false, false));
            assertThrows(IllegalStateException.class, () -> keyUp.requireSafeToHandleNextUse(true, true));
            assertThrows(IllegalStateException.class, () -> use.observe(43, true, true),
                "repeat client use is never silently accepted after first end");
        }
    }
    @Test void endTickAndCallbackCannotBeReplayedOrMovedBehindNextHandler() {
        var gate = new ClientUseKeyUpGate();
        assertThrows(IllegalStateException.class, () -> gate.observedReleaseBeforeKeybinds(3, true, false, false));
        gate.firstUseEnded(5);
        assertThrows(IllegalStateException.class, () -> gate.firstUseEnded(6));
        assertThrows(IllegalStateException.class, () -> gate.observedReleaseBeforeKeybinds(4, true, false, false));
        gate.observedReleaseBeforeKeybinds(5, true, false, false);
        assertThrows(IllegalStateException.class, () -> gate.observedReleaseBeforeKeybinds(6, true, false, false));
    }
    private ClassNode compiled(String resource) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream source = getClass().getResourceAsStream(resource)) {
            assertNotNull(source, "pinned bytecode missing: " + resource);
            new ClassReader(source).accept(node, 0);
        }
        return node;
    }
    private List<String> calls(String resource, String methodName) throws Exception {
        var method = compiled(resource).methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        return StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(i -> i instanceof MethodInsnNode).map(i -> ((MethodInsnNode) i).name).toList();
    }
    @Test void pinnedMinecraftTickOrderPlacesVanillaUseHandlingBeforeNativeEndTick() throws Exception {
        var tick = calls("/net/minecraft/client/Minecraft.class", "tick");
        int start = tick.indexOf("onPreClientTick");
        int handling = tick.indexOf("handleKeybinds");
        int end = tick.indexOf("onPostClientTick");
        assertTrue(start >= 0 && handling > start && end > handling);
        assertEquals(1, tick.stream().filter("handleKeybinds"::equals).count());
        var nativeBridge = calls("/com/bettercontent/playtestsupport/client/RobotInputBridge.class", "beforeRealUseKeybinds");
        assertTrue(nativeBridge.indexOf("release") >= 0 && nativeBridge.indexOf("glfwPollEvents") > nativeBridge.indexOf("release"));
        assertTrue(nativeBridge.indexOf("observedReleaseBeforeKeybinds") > nativeBridge.indexOf("glfwPollEvents"));
        assertTrue(nativeBridge.indexOf("requireSafeToHandleNextUse") > nativeBridge.indexOf("observedReleaseBeforeKeybinds"));
    }
}
