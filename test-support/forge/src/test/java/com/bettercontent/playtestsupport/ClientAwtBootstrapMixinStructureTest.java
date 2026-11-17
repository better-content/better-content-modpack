package com.bettercontent.playtestsupport;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Source-only structural regression; does not load client/AWT classes or run a Minecraft client. */
final class ClientAwtBootstrapMixinStructureTest {
    @Test void clientOnlyMixinInjectsExactMainHeadAndCallsOnlyCOptInHelper() throws Exception {
        var loader = ClientAwtBootstrapMixinStructureTest.class.getClassLoader();
        try (InputStream config = loader.getResourceAsStream("better_playtest_native_support.mixins.json")) {
            assertNotNull(config);
            var object = JsonParser.parseReader(new InputStreamReader(config, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(object.getAsJsonArray("mixins").toString().contains("ClientAwtBootstrapMixin"));
            assertEquals("[\"ClientAwtBootstrapMixin\",\"ClientPreKeybindUseGateMixin\",\"RealContainerScreenObserverMixin\",\"RealGameModeContainerClickObserverMixin\"]",
                    object.getAsJsonArray("client").toString());
        }
        AtomicBoolean target = new AtomicBoolean(), exactMethod = new AtomicBoolean(), head = new AtomicBoolean(),
                required = new AtomicBoolean(), helperCall = new AtomicBoolean();
        String path = "com/bettercontent/playtestsupport/mixin/ClientAwtBootstrapMixin.class";
        try (InputStream bytes = loader.getResourceAsStream(path)) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitArray(String name) {
                            if (!name.equals("value")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String field, Object value) {
                                    if (value instanceof Type type &&
                                            "net.minecraft.client.main.Main".equals(type.getClassName())) target.set(true);
                                }
                            };
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                        String signature, String[] exceptions) {
                    if (!name.equals("bc$beforeClientMain")) return null;
                    assertTrue((access & Opcodes.ACC_STATIC) != 0);
                    assertEquals("([Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", descriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        private int injectCount, atCount;
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!descriptor.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            assertEquals(1, ++injectCount);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String field, Object value) {
                                    if (field.equals("require") && value.equals(1)) required.set(true);
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
                                        @Override public void visit(String item, Object value) {
                                            if ("main([Ljava/lang/String;)V".equals(value)) exactMethod.set(true);
                                        }
                                    };
                                }
                                private AnnotationVisitor atVisitor() {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String field, Object value) {
                                            if (field.equals("value") && value.equals("HEAD")) head.set(true);
                                        }
                                    };
                                }
                            };
                        }
                        @Override public void visitEnd() {
                            assertEquals(1, injectCount);
                            assertEquals(1, atCount);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                              String descriptor, boolean isInterface) {
                            assertEquals(Opcodes.INVOKESTATIC, opcode);
                            assertEquals("com/bettercontent/playtestsupport/client/ClientAwtHeadlessBootstrap", owner);
                            assertEquals("beforeMain", method);
                            assertEquals("()V", descriptor);
                            helperCall.set(true);
                        }
                    };
                }
            }, 0);
        }
        assertTrue(target.get() && exactMethod.get() && head.get() && required.get() && helperCall.get());
    }
}
