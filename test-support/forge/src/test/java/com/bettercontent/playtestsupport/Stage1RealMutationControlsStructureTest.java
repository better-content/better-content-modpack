package com.bettercontent.playtestsupport;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN source/ASM controls. Source shape is NOT actual disposable-world receipt or hook delivery. */
final class Stage1RealMutationControlsStructureTest {
    @BeforeAll static void bootstrapMinecraft() {
        PlainJvmRegistryBootstrap.initialize();
    }
    @Test void boundedDiagnosticStoneAirOnlyAndActionMutationClassification() {
        assertSame(Blocks.STONE.defaultBlockState(), Stage1RealMutationControls.opposite(Blocks.AIR.defaultBlockState()));
        assertSame(Blocks.AIR.defaultBlockState(), Stage1RealMutationControls.opposite(Blocks.STONE.defaultBlockState()));
        assertThrows(IllegalArgumentException.class,
                () -> Stage1RealMutationControls.opposite(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertThrows(IllegalArgumentException.class,
                () -> Stage1RealMutationControls.opposite(Blocks.CAVE_AIR.defaultBlockState()));
        assertTrue(Protocol.mayMutate(Stage1RealMutationControls.POSITIVE));
        assertTrue(Protocol.mayMutate(Stage1RealMutationControls.NEGATIVE_IO));
        assertFalse(Protocol.mayMutate(Stage1RealMutationControls.NEGATIVE_SECTIONS));
        assertFalse(Protocol.mayMutate("block_mutation.control_expose_states_v1"));
        assertEquals(BlockMutationScope.MAX_EVENTS, 100_000); // fixed cap; no small-config overflow producer
    }
    @Test void identicalRealClosedChannelFailureFromFallbackDoesNotImpersonatePrivateHook() {
        assertTrue(Stage1RealMutationControls.privateFailedAppendPath("PalettedContainer.getAndSet_private_index_return"));
        // Without the private Mixin, the original LevelChunkSection fallback also attempts a
        // production append, catches the SAME ClosedChannelException, and fails the scope.
        // It cannot generate the path-specific diagnostic terminal or any candidate PASS.
        assertFalse(Stage1RealMutationControls.privateFailedAppendPath("LevelChunkSection_palette_fallback_incomplete"));
        assertFalse(Stage1RealMutationControls.privateFailedAppendPath("PalettedContainer.set_private_index_return"));
        assertFalse(Stage1RealMutationControls.privateFailedAppendPath(null));
        assertFalse(Stage1RealMutationControls.privateFailedAppendPath(""));
    }
    @Test void originalPublicRoutesAndExternalSectionGetterAreActuallyInvokedByDiagnosticCode() throws Exception {
        List<String> positive = calls("positive");
        String chunk = "net/minecraft/world/level/chunk/LevelChunk.setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;";
        String section = "net/minecraft/world/level/chunk/LevelChunkSection.setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;";
        assertEquals(3, positive.stream().filter(chunk::equals).count(), "one delegated plus TWO transient public calls");
        assertEquals(1, positive.stream().filter(section::equals).count(), "one direct public section setter");
        List<String> dirtyMarks = positive.stream().filter(name -> name.endsWith(".setUnsaved(Z)V")).toList();
        assertEquals(1, dirtyMarks.size(), "one explicit diagnostic same-owner dirty mark, never passive observer mutation");
        assertTrue(positive.indexOf(section) < positive.indexOf(dirtyMarks.get(0)) &&
                positive.indexOf(dirtyMarks.get(0)) < positive.indexOf(
                        "com/bettercontent/playtestsupport/BlockMutationTrace.preActionPrefix()Lcom/google/gson/JsonObject;"),
                "actual state setter, explicit owner metadata mark, then forced truthful post-action prefix");
        assertTrue(positive.stream().noneMatch(name -> name.contains("PalettedContainer.set(") ||
                name.contains("BlockMutationTrace.failedScope(")), "no private write or assigned observer success");
        List<String> negative = calls("exposeSections");
        assertEquals(1, negative.stream().filter("net/minecraft/world/level/chunk/LevelChunk.getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"::equals).count());
        assertTrue(negative.stream().noneMatch(name -> name.contains(".setBlockState(") ||
                name.contains("BlockMutationTrace.failedScope(")));
        List<String> io = calls("ioAppendFailure");
        assertEquals(1, io.stream().filter(chunk::equals).count(), "exactly one real public write after channel closure");
        assertEquals(1, io.stream().filter("com/bettercontent/playtestsupport/BlockMutationTrace.closeOwnedChannelForDiagnosticOnly()V"::equals).count());
        assertTrue(io.indexOf("com/bettercontent/playtestsupport/BlockMutationTrace.closeOwnedChannelForDiagnosticOnly()V") <
                io.indexOf(chunk), "owned channel close before actual palette append");
        assertTrue(io.stream().noneMatch(name -> name.contains("BlockMutationTrace.failedScope(") ||
                name.contains("PalettedContainer.set(")), "no synthetic failure/alternate private write");
        assertEquals(2, io.stream().filter("com/bettercontent/playtestsupport/BlockMutationTrace.ioFailedObservedPath()Ljava/lang/String;"::equals).count(),
                "validate and emit the recorded failed production callback, never infer it from requested method");
    }
    @Test void originalAppendFailureCapturesItsOwnObservedPathNotAnActionAssignedValue() throws Exception {
        int[] stores = {0};
        try (var input = Stage1RealMutationControlsStructureTest.class.getClassLoader().getResourceAsStream(
                "com/bettercontent/playtestsupport/BlockMutationTrace.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                              String signature, String[] exceptions) {
                    if (!method.equals("record")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                            if (opcode == Opcodes.PUTFIELD && field.equals("ioFailedObservedPath") &&
                                    owner.equals("com/bettercontent/playtestsupport/BlockMutationTrace")) stores[0]++;
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, stores[0], "only production record writes failed-path witness");
    }
    private static List<String> calls(String name) throws Exception {
        List<String> result = new ArrayList<>(); int[] count = {0};
        try (var input = Stage1RealMutationControlsStructureTest.class.getClassLoader().getResourceAsStream(
                "com/bettercontent/playtestsupport/Stage1RealMutationControls.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                              String signature, String[] exceptions) {
                    if (!method.equals(name)) return null;
                    count[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                              String desc, boolean iface) {
                            result.add(owner + "." + called + desc);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, count[0]); return result;
    }
}
