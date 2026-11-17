package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN source structure: observer never performs a second lazy result-slot getter. */
final class RealContainerScreenObserverStructureTest {
    @Test void originalRenderIsBracketedAndInFlightResultIsFailSoftLocalOnly() throws Exception {
        String path = "com/bettercontent/playtestsupport/mixin/RealContainerScreenObserverMixin.class";
        List<String> called = new ArrayList<>(), injectionMethods = new ArrayList<>();
        List<String> injectionTargets = new ArrayList<>(), localPolicies = new ArrayList<>();
        try (var bytes = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.startsWith("bc$")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String calledName, String calledDesc, boolean interfaceCall) {
                            called.add(owner + "." + calledName + calledDesc);
                        }
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!"Lorg/spongepowered/asm/mixin/injection/Inject;".equals(descriptor)) return null;
                            injectionMethods.add(name);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object value) {
                                    if ("method".equals(key)) injectionTargets.add(value.toString());
                                }
                                @Override public AnnotationVisitor visitArray(String key) {
                                    if ("method".equals(key)) return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String ignored, Object value) {
                                            injectionTargets.add(value.toString());
                                        }
                                    };
                                    if (!"at".equals(key)) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public AnnotationVisitor visitAnnotation(String ignored, String desc) {
                                            return new AnnotationVisitor(Opcodes.ASM9) {
                                                @Override public void visit(String field, Object value) {
                                                    if ("value".equals(field) || "target".equals(field)) injectionTargets.add(value.toString());
                                                }
                                            };
                                        }
                                    };
                                }
                                @Override public void visitEnum(String key, String descriptor, String value) {
                                    if ("locals".equals(key)) localPolicies.add(value);
                                }
                            };
                        }
                    };
                }
            }, 0);
        }
        assertEquals(4, injectionMethods.size());
        assertTrue(injectionTargets.contains("renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V"));
        assertTrue(injectionTargets.contains("INVOKE_ASSIGN"));
        assertTrue(injectionTargets.contains("Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"));
        assertTrue(localPolicies.contains("CAPTURE_FAILSOFT"));
        assertEquals(1, called.stream().filter(s -> s.contains("ClientNativeSupport.observedOriginalRenderedSlotItem(")).count());
        assertEquals(1, called.stream().filter(s -> s.contains("ClientNativeSupport.observedOriginalContainerRenderBegin(")).count());
        assertEquals(1, called.stream().filter(s -> s.contains("ClientNativeSupport.observedOriginalContainerRender(")).count());
        assertFalse(called.stream().anyMatch(s -> s.contains("Slot.getItem(")), "no observer-initiated lazy recipe work");
    }
}
