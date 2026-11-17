package com.bettercontent.playtestsupport;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Unrun structural source: optional provider callback HEAD/RETURN only, no policy call or fake tick. */
final class WaterAutoObservationStructureTest {
    @Test void providerObserverIsOptionalCommonReadOnlyAndNotAnAutonomousAction() throws Exception {
        var loader = getClass().getClassLoader();
        try (var in = loader.getResourceAsStream("better_playtest_native_support.mixins.json")) {
            assertNotNull(in);
            var config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.getAsJsonArray("mixins").toString().contains("WaterAutoProviderTickObserverMixin"));
            assertFalse(config.getAsJsonArray("client").toString().contains("WaterAutoProviderTickObserverMixin"));
        }
        Set<String> hooks = new HashSet<>();
        try (InputStream in = loader.getResourceAsStream("com/bettercontent/playtestsupport/mixin/WaterAutoProviderTickObserverMixin.class")) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    if (!name.startsWith("bc$")) return null;
                    assertTrue((access & Opcodes.ACC_STATIC) != 0);
                    return new MethodVisitor(Opcodes.ASM9) {
                        String target, at;
                        boolean optional;
                        int injectCount, atCount;
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (!desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            assertEquals(1, ++injectCount);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object value) {
                                    if (key.equals("require")) optional = Integer.valueOf(0).equals(value);
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
                                        @Override public void visit(String key, Object value) {
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
                            assertTrue(optional && target != null && at != null);
                            assertTrue(hooks.add(target + "@" + at), "duplicate injection must not be deduplicated");
                        }
                    };
                }
            }, 0);
        }
        assertEquals(Set.of("onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V@HEAD",
                "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V@RETURN"), hooks);
        for (String id : Set.of(PassiveWaterAuto.SNAPSHOT, PassiveWaterAuto.ARM, PassiveWaterAuto.AWAIT)) {
            assertTrue(Protocol.DRIVERS.contains(id));
            assertFalse(Protocol.mayMutate(id));
        }
    }
}
