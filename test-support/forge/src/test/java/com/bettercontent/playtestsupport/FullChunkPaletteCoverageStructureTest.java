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

/** Pure bytecode/config audit source; actual Mixin application and real-path controls are UNRUN. */
final class FullChunkPaletteCoverageStructureTest {
    @Test void innermostWriteReplacementAndExposureHooksAreRegisteredAndNoncancelable() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream config = loader.getResourceAsStream("better_playtest_native_support.mixins.json")) {
            assertNotNull(config);
            var root = JsonParser.parseReader(new InputStreamReader(config, StandardCharsets.UTF_8)).getAsJsonObject();
            var common = root.getAsJsonArray("mixins").toString();
            assertTrue(common.contains("PalettedContainerMutationObserverMixin"));
            assertTrue(common.contains("ChunkAccessSectionsExposureMixin"));
            assertTrue(common.contains("LevelChunkPaletteMutationObserverMixin"));
            assertTrue(common.contains("LevelChunkBirthObserverMixin"));
            assertFalse(root.getAsJsonArray("client").toString().contains("PalettedContainerMutationObserverMixin"));
        }
        assertEquals(Set.of("getAndSet(ILjava/lang/Object;)Ljava/lang/Object;@RETURN",
                "set(ILjava/lang/Object;)V@HEAD", "set(ILjava/lang/Object;)V@RETURN",
                "read(Lnet/minecraft/network/FriendlyByteBuf;)V@HEAD",
                "read(Lnet/minecraft/network/FriendlyByteBuf;)V@RETURN"),
                injects(loader, "PalettedContainerMutationObserverMixin"));
        assertEquals(Set.of("getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;@RETURN"),
                injects(loader, "ChunkAccessSectionsExposureMixin"));
        assertTrue(injects(loader, "LevelChunkPaletteMutationObserverMixin")
                .contains("getStates()Lnet/minecraft/world/level/chunk/PalettedContainer;@RETURN"));
        assertEquals(Set.of("<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V@RETURN",
                        "postProcessGeneration()V@RETURN"),
                injects(loader, "LevelChunkBirthObserverMixin"));
    }
    private static Set<String> injects(ClassLoader loader, String mixin) throws Exception {
        Set<String> rows = new HashSet<>();
        try (InputStream stream = loader.getResourceAsStream("com/bettercontent/playtestsupport/mixin/" + mixin + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                          String signature, String[] exceptions) {
                    if (!name.startsWith("bc$")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        String target, at;
                        boolean required;
                        int injectCount, atCount;
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (!desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            assertEquals(1, ++injectCount);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object value) {
                                    if (key.equals("require") && Integer.valueOf(1).equals(value)) required = true;
                                    if (key.equals("cancellable")) assertEquals(false, value);
                                }
                                @Override public AnnotationVisitor visitArray(String key) {
                                    if (key.equals("at")) return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public AnnotationVisitor visitAnnotation(String element, String type) {
                                            assertNull(element);
                                            assertEquals("Lorg/spongepowered/asm/mixin/injection/At;", type);
                                            assertEquals(1, ++atCount);
                                            return atVisitor();
                                        }
                                    };
                                    if (!key.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String item, Object value) {
                                            assertNull(target, "one exact injection target required");
                                            target = String.valueOf(value);
                                        }
                                    };
                                }
                                private AnnotationVisitor atVisitor() {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String key, Object value) {
                                            if (key.equals("value")) at = String.valueOf(value);
                                        }
                                    };
                                }
                            };
                        }
                        @Override public void visitEnd() {
                            assertEquals(1, injectCount);
                            assertEquals(1, atCount);
                            assertTrue(required && target != null && at != null);
                            assertTrue(rows.add(target + "@" + at), "duplicate injection must not be deduplicated");
                        }
                    };
                }
            }, 0);
        }
        return rows;
    }
}
