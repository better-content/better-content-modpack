package com.bettercontent.playtestsupport;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN source structure; Mixin application and real packet/drop delivery still require independent runtime controls. */
final class PassivePacketDigStructureTest {
    @Test void commonMixinTargetsAreActualPacketAndGameModeOnlyAndNoAutonomousDriver() throws Exception {
        var loader = getClass().getClassLoader();
        try (var in = loader.getResourceAsStream("better_playtest_native_support.mixins.json")) {
            assertNotNull(in);
            var config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.getAsJsonArray("mixins").toString().contains("RealPacketDigObserverMixin"));
            assertTrue(config.getAsJsonArray("mixins").toString().contains("RealGameModeDigObserverMixin"));
            assertTrue(config.getAsJsonArray("mixins").toString().contains("RealNetworkPacketIngressMixin"));
            assertFalse(config.getAsJsonArray("client").toString().contains("RealPacketDigObserverMixin"));
        }
        assertTrue(Protocol.DRIVERS.contains(PassivePhysicalDig.ARM));
        assertTrue(Protocol.DRIVERS.contains(PassivePhysicalDig.AWAIT));
        assertFalse(Protocol.mayMutate(PassivePhysicalDig.ARM));
        assertFalse(Protocol.mayMutate(PassivePhysicalDig.AWAIT));
        assertEquals(Set.of("handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V"),
                methods("RealPacketDigObserverMixin"));
        assertEquals(Set.of("channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"),
                methods("RealNetworkPacketIngressMixin"));
        assertEquals(Set.of("handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V",
                "destroyBlock(Lnet/minecraft/core/BlockPos;)Z"), methods("RealGameModeDigObserverMixin"));
    }
    private static Set<String> methods(String name) throws Exception {
        Set<String> seen = new HashSet<>();
        try (InputStream in = PassivePacketDigStructureTest.class.getClassLoader().getResourceAsStream(
                "com/bettercontent/playtestsupport/mixin/" + name + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                              String signature, String[] exceptions) {
                    if (!method.startsWith("bc$")) return null;
                    assertTrue((access & Opcodes.ACC_STATIC) == 0);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public org.objectweb.asm.AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!annotation.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            return new org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object value) {
                                    if (key.equals("require")) assertEquals(1, value);
                                    if (key.equals("cancellable")) assertEquals(false, value);
                                }
                                @Override public org.objectweb.asm.AnnotationVisitor visitArray(String key) {
                                    if (!key.equals("method")) return null;
                                    return new org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String key, Object value) { seen.add(String.valueOf(value)); }
                                    };
                                }
                            };
                        }
                    };
                }
            }, 0);
        }
        return seen;
    }
}
