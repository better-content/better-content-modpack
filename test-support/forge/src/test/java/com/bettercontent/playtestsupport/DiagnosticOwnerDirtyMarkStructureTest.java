package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN: pinned mapped original save guard requires diagnostic-only owner metadata mark. */
final class DiagnosticOwnerDirtyMarkStructureTest {
    @Test void chunkMapSaveSkipsCleanChunkBeforeOriginalSerialization() throws Exception {
        List<String> calls = calls("net/minecraft/server/level/ChunkMap", "save",
                "(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z");
        String guard = "net/minecraft/world/level/chunk/ChunkAccess.isUnsaved()Z";
        String serialize = "net/minecraft/world/level/chunk/storage/ChunkSerializer.write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;";
        assertTrue(calls.contains(guard)); assertTrue(calls.contains(serialize));
        assertTrue(calls.indexOf(guard) < calls.indexOf(serialize));
        assertTrue(calls.stream().anyMatch(name -> name.endsWith("ChunkAccess.setUnsaved(Z)V")));
    }
    @Test void originalSectionStateSetterDoesNotMarkOwningChunkUnsaved() throws Exception {
        List<String> calls = calls("net/minecraft/world/level/chunk/LevelChunkSection", "setBlockState",
                "(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;");
        assertFalse(calls.stream().anyMatch(name -> name.contains("setUnsaved(")));
    }
    private static List<String> calls(String owner, String method, String descriptor) throws Exception {
        List<String> calls = new ArrayList<>(); int[] matches = {0};
        try (var bytes = DiagnosticOwnerDirtyMarkStructureTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals(method) || !desc.equals(descriptor)) return null;
                    matches[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String target, String name, String desc, boolean interfaceCall) {
                            calls.add(target + "." + name + desc);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, matches[0]); return calls;
    }
}
