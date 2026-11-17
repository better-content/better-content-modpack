package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN pinned regression: native duration must use only the actual START return, never repeat gameplay policy. */
final class NativeDigActualProgressStructureTest {
    @Test void nativeStartNeverCallsDestroyProgressAndPinnedGameModeDoes() throws Exception {
        List<String> nativeCalls = calls("com/bettercontent/playtestsupport/NativeDig.class", "start", null);
        assertTrue(nativeCalls.stream().noneMatch(call -> call.contains("getDestroyProgress")));
        List<String> gameCalls = calls("net/minecraft/server/level/ServerPlayerGameMode.class",
                "handleBlockBreakAction", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V");
        assertEquals(2, gameCalls.stream().filter(call -> call.equals("net/minecraft/world/level/block/state/BlockState.getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F")).count());
        List<String> observer = calls("com/bettercontent/playtestsupport/mixin/RealDestroyProgressObserverMixin.class",
                "bc$actualDestroyProgress", null);
        assertTrue(observer.stream().noneMatch(call -> call.contains("getDestroyProgress")));
        assertTrue(observer.stream().anyMatch(call -> call.contains("NativeSupport.realDestroyProgressReturned")));
    }
    private static List<String> calls(String resource, String method, String signature) throws Exception {
        List<String> found = new ArrayList<>();
        try (InputStream in = NativeDigActualProgressStructureTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                              String generic, String[] exceptions) {
                    if (!name.equals(method) || signature != null && !signature.equals(descriptor)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean iface) {
                            found.add(owner + "." + name + desc);
                        }
                    };
                }
            }, 0);
        }
        return found;
    }
}
