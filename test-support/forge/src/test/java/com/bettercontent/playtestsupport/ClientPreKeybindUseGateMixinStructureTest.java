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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Pure bytecode/config regression source; never starts Minecraft, AWT, GLFW or an OS-input Robot. */
final class ClientPreKeybindUseGateMixinStructureTest {
    @Test void clientOnlyHookRunsExactlyBeforeVanillaHandleKeybindsWithoutCancellation() throws Exception {
        ClassLoader loader = ClientPreKeybindUseGateMixinStructureTest.class.getClassLoader();
        try (InputStream config = loader.getResourceAsStream("better_playtest_native_support.mixins.json")) {
            assertNotNull(config);
            var mixins = JsonParser.parseReader(new InputStreamReader(config, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(mixins.getAsJsonArray("mixins").toString().contains("ClientPreKeybindUseGateMixin"));
            assertEquals("[\"ClientAwtBootstrapMixin\",\"ClientPreKeybindUseGateMixin\",\"RealContainerScreenObserverMixin\",\"RealGameModeContainerClickObserverMixin\"]",
                    mixins.getAsJsonArray("client").toString());
        }
        AtomicBoolean target = new AtomicBoolean(), exactMethod = new AtomicBoolean(), head = new AtomicBoolean(),
                required = new AtomicBoolean();
        AtomicInteger helperCalls = new AtomicInteger();
        try (InputStream bytes = loader.getResourceAsStream(
                "com/bettercontent/playtestsupport/mixin/ClientPreKeybindUseGateMixin.class")) {
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
                                            "net.minecraft.client.Minecraft".equals(type.getClassName())) target.set(true);
                                }
                            };
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                        String signature, String[] exceptions) {
                    if (!name.equals("bc$beforeRealUseKeybinds")) return null;
                    assertEquals(0, access & Opcodes.ACC_STATIC);
                    assertEquals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", descriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        private int injectCount, atCount;
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!descriptor.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            assertEquals(1, ++injectCount);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String name, Object value) {
                                    if (name.equals("require") && value.equals(1)) required.set(true);
                                    if (name.equals("cancellable")) assertEquals(false, value);
                                }
                                @Override public AnnotationVisitor visitArray(String name) {
                                    if (name.equals("at")) return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public AnnotationVisitor visitAnnotation(String key, String type) {
                                            assertNull(key);
                                            assertEquals("Lorg/spongepowered/asm/mixin/injection/At;", type);
                                            assertEquals(1, ++atCount);
                                            return atVisitor();
                                        }
                                    };
                                    if (!name.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String field, Object value) {
                                            if ("handleKeybinds()V".equals(value)) exactMethod.set(true);
                                        }
                                    };
                                }
                                private AnnotationVisitor atVisitor() {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String name, Object value) {
                                            if (name.equals("value") && value.equals("HEAD")) head.set(true);
                                        }
                                    };
                                }
                            };
                        }
                        @Override public void visitEnd() {
                            assertEquals(1, injectCount);
                            assertEquals(1, atCount);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                              String descriptor, boolean isInterface) {
                            assertEquals(Opcodes.INVOKESTATIC, opcode);
                            assertEquals("com/bettercontent/playtestsupport/client/ClientNativeSupport", owner);
                            assertEquals("beforeRealUseKeybinds", name);
                            assertEquals("(Lnet/minecraft/client/Minecraft;)V", descriptor);
                            helperCalls.incrementAndGet();
                        }
                    };
                }
            }, 0);
        }
        assertTrue(target.get() && exactMethod.get() && head.get() && required.get());
        assertEquals(1, helperCalls.get());
    }
}
