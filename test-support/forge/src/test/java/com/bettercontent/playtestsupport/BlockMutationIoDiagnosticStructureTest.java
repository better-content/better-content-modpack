package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN: first genuine failed append retains physical observed state, never grants raw-row credit. */
final class BlockMutationIoDiagnosticStructureTest {
    @Test void ioOnlyStopIsVersionedAndAdditionalObserverFailureIsExplicit() throws Exception {
        List<String> record = operations("record"), stop = operations("closeAtStop");
        assertEquals(2, record.stream().filter(s -> s.equals("call:java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")).count(),
                "successful append and failed append each retain true observed state; byte ledger is separate");
        assertEquals(1, record.stream().filter(s -> s.equals("call:java/io/ByteArrayOutputStream.writeBytes([B)V")).count());
        assertEquals(1, record.stream().filter(s -> s.equals("call:java/security/MessageDigest.update([B)V")).count());
        assertTrue(record.contains("field:additionalObserverFailure"), "changed observer after first failed append is not swallowed");
        assertTrue(stop.contains("literal:bc.playtest_block_mutation_status.v4"));
        assertTrue(stop.contains("literal:additional_observer_failure"));
    }
    private static List<String> operations(String name) throws Exception {
        List<String> result = new ArrayList<>();
        try (var bytes = BlockMutationIoDiagnosticStructureTest.class.getClassLoader().getResourceAsStream(
                "com/bettercontent/playtestsupport/BlockMutationTrace.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                    if (!method.equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String calledName, String descriptor, boolean interfaceCall) {
                            result.add("call:" + owner + "." + calledName + descriptor);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                            if (opcode == Opcodes.PUTFIELD) result.add("field:" + field);
                        }
                        @Override public void visitLdcInsn(Object value) { result.add("literal:" + value); }
                    };
                }
            }, 0);
        }
        return result;
    }
}
