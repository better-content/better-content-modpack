package com.bettercontent.playtestsupport;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN pure mechanical controls + selected pinned 47.4.22 vanilla bytecode; no tool/world construction. */
final class PhysicalToolBreakStructureTest {
    @Test void intactWearMustHaveZeroCallbacksAndRetainObjectButMaxDamageBreakOneAndEmpty() {
        assertTrue(PhysicalToolWitness.supportedReturn(0, 0, 0, "nonempty", true));
        assertTrue(PhysicalToolWitness.supportedReturn(1, 1, 1, "empty", false));
        assertTrue(PhysicalToolWitness.supportedReturn(1, 1, 1, "empty", true)); // mutated original count0 also empty
        assertTrue(PhysicalToolWitness.supportedReturn(0, 0, 0, "empty", true)); // same original became empty, zero callback: complete contradiction
        assertFalse(PhysicalToolWitness.supportedReturn(0, 0, 0, "empty", false)); // replaced object has unknown cause
        assertTrue(PhysicalToolWitness.supportedReturn(1, 1, 1, "nonempty", true)); // complete anomalous callback, D judges
        assertTrue(PhysicalToolWitness.supportedReturn(2, 2, 2, "empty", false)); // two fully bound callbacks: complete contradiction
        assertTrue(PhysicalToolWitness.supportedReturn(2, 1, 1, "empty", false)); // two callback heads inside one real Consumer
        assertFalse(PhysicalToolWitness.supportedReturn(5, 5, 5, "empty", false)); // bounded overflow
        assertFalse(PhysicalToolWitness.supportedReturn(2, 0, 0, "empty", false)); // callback without actual cause
        assertFalse(PhysicalToolWitness.supportedReturn(1, 1, 0, "empty", false));
        assertFalse(PhysicalToolWitness.supportedReturn(0, 1, 1, "nonempty", true));
        assertFalse(PhysicalToolWitness.supportedReturn(0, 0, 0, "nonempty", false));
    }
    @Test void sharedSyntheticShapeFixturesEqualActualProductionCaptureClassifier() throws Exception {
        try (var source = getClass().getClassLoader().getResourceAsStream("physical-tool-v5-capture-shapes.json")) {
            assertNotNull(source);
            var fixture = JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("bc.playtest_physical_tool_capture_shapes.v5", fixture.get("schema").getAsString());
            assertTrue(fixture.get("synthetic_unrun_not_runtime_evidence").getAsBoolean());
            assertEquals(9, fixture.getAsJsonArray("cases").size());
            for (var value : fixture.getAsJsonArray("cases")) {
                var testCase = value.getAsJsonObject();
                boolean actual = PhysicalToolWitness.supportedReturn(testCase.get("callbacks").getAsInt(),
                        testCase.get("enters").getAsInt(), testCase.get("exits").getAsInt(),
                        testCase.get("after_kind").getAsString(), testCase.get("same_original_object").getAsBoolean());
                assertEquals(testCase.get("capture_complete_shape").getAsBoolean(), actual, testCase.get("id").getAsString());
            }
        }
    }
    @Test void selectedPinnedActualItemConsumerPrecedesShrinkAndHandWrapperOnlyDelegatesToEquipmentSlot() throws Exception {
        List<String> item = calls("net/minecraft/world/item/ItemStack.class", "hurtAndBreak",
                "(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V");
        int cb = item.indexOf("java/util/function/Consumer.accept(Ljava/lang/Object;)V");
        int shrink = item.indexOf("net/minecraft/world/item/ItemStack.shrink(I)V");
        int clear = item.indexOf("net/minecraft/world/item/ItemStack.setDamageValue(I)V");
        assertEquals(1, item.stream().filter("java/util/function/Consumer.accept(Ljava/lang/Object;)V"::equals).count());
        assertTrue(cb >= 0 && shrink > cb && clear > shrink);
        List<String> wrapper = calls("net/minecraft/world/entity/LivingEntity.class", "broadcastBreakEvent",
                "(Lnet/minecraft/world/InteractionHand;)V");
        assertEquals(List.of("net/minecraft/world/entity/LivingEntity.broadcastBreakEvent(Lnet/minecraft/world/entity/EquipmentSlot;)V"), wrapper);
        List<String> actual = calls("net/minecraft/world/entity/LivingEntity.class", "broadcastBreakEvent",
                "(Lnet/minecraft/world/entity/EquipmentSlot;)V");
        assertEquals(1, actual.stream().filter("net/minecraft/world/level/Level.broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"::equals).count());
        List<String> destroy = calls("net/minecraft/server/level/ServerPlayerGameMode.class", "destroyBlock",
                "(Lnet/minecraft/core/BlockPos;)Z");
        int mine = destroy.indexOf("net/minecraft/world/item/ItemStack.mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V");
        assertTrue(mine > 0 && destroy.indexOf("net/minecraft/world/item/ItemStack.isEmpty()Z") > mine);
        List<String> digger = calls("net/minecraft/world/item/DiggerItem.class", "mineBlock",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z");
        assertEquals(1, digger.stream().filter("net/minecraft/world/item/ItemStack.hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"::equals).count());
        List<String> diggerCallback = calls("net/minecraft/world/item/DiggerItem.class", "lambda$mineBlock$1",
                "(Lnet/minecraft/world/entity/LivingEntity;)V");
        assertEquals(List.of("net/minecraft/world/entity/LivingEntity.broadcastBreakEvent(Lnet/minecraft/world/entity/EquipmentSlot;)V"), diggerCallback);
    }
    private static List<String> calls(String resource, String method, String descriptor) throws Exception {
        List<String> calls = new ArrayList<>(); int[] matches = {0};
        try (var input = PhysicalToolBreakStructureTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    if (!name.equals(method) || !desc.equals(descriptor)) return null;
                    matches[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String called, String signature, boolean iface) {
                            calls.add(owner + "." + called + signature);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, matches[0], resource + "." + method + descriptor);
        return calls;
    }
}
