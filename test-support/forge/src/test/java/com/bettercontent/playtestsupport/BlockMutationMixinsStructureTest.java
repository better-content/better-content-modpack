package com.bettercontent.playtestsupport;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Structural only; neither mixin is applied, no world/block mutation test is executed. */
final class BlockMutationMixinsStructureTest {
    @Test void exactPaletteAndChunkHooksAreRequiredAndServerOnly() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream config = loader.getResourceAsStream("better_playtest_native_support.mixins.json")) {
            assertNotNull(config);
            var object = JsonParser.parseReader(new InputStreamReader(config, StandardCharsets.UTF_8)).getAsJsonObject();
            var common = object.getAsJsonArray("mixins").toString();
            var client = object.getAsJsonArray("client").toString();
            assertTrue(common.contains("LevelChunkMutationObserverMixin"));
            assertTrue(common.contains("LevelChunkPaletteMutationObserverMixin"));
            assertFalse(client.contains("LevelChunkMutationObserverMixin"));
            assertFalse(client.contains("LevelChunkPaletteMutationObserverMixin"));
        }
        assertEquals(Set.of("HEAD", "RETURN"), hooks(loader, "LevelChunkMutationObserverMixin",
                "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"));
        assertEquals(Set.of("RETURN"), hooks(loader, "LevelChunkPaletteMutationObserverMixin",
                "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"));
    }
    private static Set<String> hooks(ClassLoader loader, String name, String exactTarget) throws Exception {
        Set<String> observed = new HashSet<>();
        try (InputStream bytes = loader.getResourceAsStream("com/bettercontent/playtestsupport/mixin/" + name + ".class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                         String signature, String[] exceptions) {
                    if (!name.equals("bc$blockMutationEnter") && !name.equals("bc$blockMutationExit") &&
                            !name.equals("bc$paletteMutation")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        private boolean method, require;
                        private String point;
                        private int injectCount, atCount;
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!descriptor.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            assertEquals(1, ++injectCount);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String field, Object value) {
                                    if (field.equals("require") && value.equals(1)) require = true;
                                    if (field.equals("cancellable")) assertEquals(false, value);
                                }
                                @Override public AnnotationVisitor visitArray(String field) {
                                    if (field.equals("at")) return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public AnnotationVisitor visitAnnotation(String key, String type) {
                                            assertNull(key);
                                            assertEquals("Lorg/spongepowered/asm/mixin/injection/At;", type);
                                            assertEquals(1, ++atCount);
                                            return atVisitor();
                                        }
                                    };
                                    if (!field.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String key, Object value) {
                                            if (exactTarget.equals(value)) method = true;
                                        }
                                    };
                                }
                                private AnnotationVisitor atVisitor() {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String key, Object value) {
                                            if (key.equals("value")) point = String.valueOf(value);
                                        }
                                    };
                                }
                            };
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                              String descriptor, boolean isInterface) {
                            assertFalse(method.equals("setBlockState"), "observer must never invoke a gameplay write");
                        }
                        @Override public void visitEnd() {
                            assertEquals(1, injectCount);
                            assertEquals(1, atCount);
                            assertTrue(method && require, "required exact pinned injection missing");
                            assertNotNull(point);
                            assertTrue(observed.add(point), "duplicate hook must not be hidden by set deduplication");
                        }
                    };
                }
            }, 0);
        }
        return observed;
    }
}
