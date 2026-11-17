package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Compile-artifact contract only: does NOT prove runtime Mixin application or harvest behavior. */
class HarvestObserverBytecodeTest {
    @Test void capturesCallerLocalWithoutCallingHarvestPolicyAgain() throws Exception {
        ClassNode node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/com/bettercontent/playtestsupport/mixin/NativeHarvestGateObserverMixin.class")) {
            assertNotNull(input); new ClassReader(input).accept(node, 0);
        }
        AnnotationNode mixin = annotations(node.visibleAnnotations, node.invisibleAnnotations)
            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        assertEquals(List.of(Type.getObjectType("net/minecraft/server/level/ServerPlayerGameMode")), value(mixin, "value"));
        var observer = node.methods.stream().filter(m -> m.name.equals("bc$actualHarvestGate")).findFirst().orElseThrow();
        AnnotationNode inject = annotations(observer.visibleAnnotations, observer.invisibleAnnotations)
            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        assertEquals(0, value(inject, "require"));
        assertEquals("CAPTURE_FAILSOFT", ((String[]) value(inject, "locals"))[1]);
        AnnotationNode at = (AnnotationNode) ((List<?>) value(inject, "at")).get(0);
        assertEquals("INVOKE", value(at, "value"));
        assertEquals("Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V", value(at, "target"));
        for (var instruction : observer.instructions) if (instruction instanceof MethodInsnNode call) {
            assertFalse(call.name.equals("canHarvestBlock") || call.name.equals("hasCorrectToolForDrops") ||
                call.name.equals("doPlayerHarvestCheck"), "observer must not invoke gameplay harvest policy again");
        }
    }
    private static Stream<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        return Stream.concat(visible == null ? Stream.empty() : visible.stream(),
            invisible == null ? Stream.empty() : invisible.stream());
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals(key)) return annotation.values.get(index + 1);
        throw new AssertionError("annotation field missing: " + key);
    }
}
