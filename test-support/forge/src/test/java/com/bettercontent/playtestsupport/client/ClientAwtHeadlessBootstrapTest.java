package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** Pure opt-in and compiled bytecode contracts only; never calls beforeMain, initializes AWT or instantiates Robot. */
class ClientAwtHeadlessBootstrapTest {
    @TempDir Path mailbox;
    private final String token = "a".repeat(64);

    @Test void onlyExistingOwnedAbsoluteMailboxAndExistingClientIdentityCanOptIn() throws Exception {
        assertTrue(ClientAwtHeadlessBootstrap.reviewedOptIn("run:client", token, mailbox.toString()));
        assertFalse(ClientAwtHeadlessBootstrap.reviewedOptIn("", token, mailbox.toString()));
        assertFalse(ClientAwtHeadlessBootstrap.reviewedOptIn("run:client", "invalid", mailbox.toString()));
        assertFalse(ClientAwtHeadlessBootstrap.reviewedOptIn("run:client", token, "relative"));
        assertFalse(ClientAwtHeadlessBootstrap.reviewedOptIn("run:client", token, mailbox.resolve("missing").toString()));
        Path link = mailbox.resolve("link");
        Files.createSymbolicLink(link, mailbox);
        assertFalse(ClientAwtHeadlessBootstrap.reviewedOptIn("run:client", token, link.toString()));
    }

    private static ClassNode read(String resource) throws Exception {
        ClassNode result = new ClassNode();
        try (var input = ClientAwtHeadlessBootstrapTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing pinned compiled class: " + resource);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }
    @Test void mappedVanillaMainClinitForcesHeadlessAndOurEarlyHookMustUndoItBeforeCachedProbe() throws Exception {
        // Structural only: successful bytecode inspection does NOT prove a mixin is applied at runtime.
        var vanilla = read("/net/minecraft/client/main/Main.class");
        var init = vanilla.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        var instructions = StreamSupport.stream(init.instructions.spliterator(), false).toList();
        assertTrue(instructions.stream().anyMatch(i -> i instanceof LdcInsnNode ldc &&
                "java.awt.headless".equals(ldc.cst)));
        assertTrue(instructions.stream().anyMatch(i -> i instanceof MethodInsnNode call &&
                call.owner.equals("java/lang/System") && call.name.equals("setProperty") &&
                call.desc.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")));
        assertTrue(instructions.stream().anyMatch(i -> i instanceof LdcInsnNode ldc && "true".equals(ldc.cst)));

        var helper = read("/com/bettercontent/playtestsupport/client/ClientAwtHeadlessBootstrap.class");
        var before = helper.methods.stream().filter(m -> m.name.equals("beforeMain") && m.desc.equals("()V"))
                .findFirst().orElseThrow();
        var compiled = StreamSupport.stream(before.instructions.spliterator(), false).toList();
        int property = -1, probe = -1;
        for (int index = 0; index < compiled.size(); index++) {
            var i = compiled.get(index);
            if (i instanceof MethodInsnNode call && call.owner.equals("java/lang/System") &&
                    call.name.equals("setProperty")) property = index;
            if (i instanceof MethodInsnNode call && call.owner.equals("java/awt/GraphicsEnvironment") &&
                    call.name.equals("isHeadless")) probe = index;
        }
        assertTrue(property >= 0 && probe > property, "must set false before first AWT cached-state probe");
        assertTrue(compiled.subList(0, property).stream().anyMatch(i -> i instanceof LdcInsnNode ldc &&
                "false".equals(ldc.cst)));
        assertTrue(compiled.subList(property + 1, probe).stream().noneMatch(i -> i instanceof MethodInsnNode call &&
                call.owner.equals("java/awt/GraphicsEnvironment")));
    }
    @Test void bridgeRequiresEarlyBootstrapBeforeAnyPhysicalRobotConstruction() throws Exception {
        var bridge = read("/com/bettercontent/playtestsupport/client/RobotInputBridge.class");
        var constructor = bridge.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        List<?> instructions = StreamSupport.stream(constructor.instructions.spliterator(), false).toList();
        int guard = -1, robot = -1;
        for (int index = 0; index < instructions.size(); index++) {
            var i = instructions.get(index);
            if (i instanceof MethodInsnNode call && call.owner.endsWith("/ClientAwtHeadlessBootstrap") &&
                    call.name.equals("requireReady")) guard = index;
            if (i instanceof TypeInsnNode allocation && allocation.desc.equals("java/awt/Robot")) robot = index;
        }
        assertTrue(guard >= 0 && robot > guard, "never allocate Robot before proving nonheadless bootstrap");
    }
}
