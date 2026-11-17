package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN actual Player override/Forge stage control source, not a hurt/spawn/gameplay test. */
final class PassiveIncomingHitStructureTest {
    @Test void exactlyOneBoundActualPipelineAndMissingStagesNeverQualifyAsOneCompleteHit() {
        assertTrue(PassiveIncomingHit.nonnegativeDamageStage(0f));
        assertTrue(PassiveIncomingHit.nonnegativeDamageStage(2.25f));
        assertFalse(PassiveIncomingHit.nonnegativeDamageStage(-1f));
        assertFalse(PassiveIncomingHit.nonnegativeDamageStage(Float.NaN));
        assertFalse(PassiveIncomingHit.nonnegativeDamageStage(Float.POSITIVE_INFINITY));
        assertTrue(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, "after_helper", false, false));
        assertTrue(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 0, 0, 1, "nonpositive_before_helper", false, false));
        assertTrue(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 0, 0, 1, "bypass_effects", false, false));
        assertTrue(PassiveIncomingHit.completeCounts(1, 1, 1, 0, 1, 1, 1, 0, 0, 1, "bypass_enchantments", false, true));
        assertFalse(PassiveIncomingHit.completeCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, false, false));
        assertFalse(PassiveIncomingHit.completeCounts(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, "after_helper", false, false));
        assertFalse(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 1, 0, 1, "after_helper", false, false));
        assertFalse(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 0, 0, 1, "after_helper", false, false));
        assertFalse(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 0, 0, 0, null, false, false));
        assertFalse(PassiveIncomingHit.completeCounts(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, "after_helper", true, false));
    }
    @Test void pinnedActualServerPlayerPathIsPlayerOverrideNotLivingEntitySuperclass() throws Exception {
        List<String> player = calls("net/minecraft/world/entity/player/Player.class", "actuallyHurt",
                "(Lnet/minecraft/world/damagesource/DamageSource;F)V");
        String hookHurt = "net/minecraftforge/common/ForgeHooks.onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F";
        String armor = "net/minecraft/world/entity/player/Player.getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F";
        String magic = "net/minecraft/world/entity/player/Player.getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F";
        String hookDamage = "net/minecraftforge/common/ForgeHooks.onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F";
        String combat = "net/minecraft/world/damagesource/CombatTracker.recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V";
        for (String required : List.of(hookHurt, armor, magic, hookDamage, combat))
            assertEquals(1, player.stream().filter(required::equals).count(), required);
        assertTrue(player.indexOf(hookHurt) < player.indexOf(armor) && player.indexOf(armor) < player.indexOf(magic) &&
                player.indexOf(magic) < player.indexOf(hookDamage) && player.indexOf(hookDamage) < player.indexOf(combat));
        assertEquals(1, player.stream().filter("net/minecraft/world/entity/player/Player.setAbsorptionAmount(F)V"::equals).count());
        assertEquals(List.of("fstore3", "onLivingDamage", "fstore3"), playerFloatStoreThreeOrder(),
                "original f3 absorption STORE then unconditional ForgeHooks RETURN STORE; zero skips CombatTracker");
        List<String> inheritedMagic = calls("net/minecraft/world/entity/LivingEntity.class", "getDamageAfterMagicAbsorb",
                "(Lnet/minecraft/world/damagesource/DamageSource;F)F");
        assertEquals(1, inheritedMagic.stream().filter(
                "net/minecraft/world/item/enchantment/EnchantmentHelper.getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I"::equals).count());
        assertEquals(List.of("freturn", "freturn", "freturn", "helper", "freturn"),
                originalMagicHelperBranchOrder(),
                "only actual last RETURN branch follows original helper INVOKE; float zero cannot infer skip");
        List<String> inheritedArmor = calls("net/minecraft/world/entity/LivingEntity.class", "getDamageAfterArmorAbsorb",
                "(Lnet/minecraft/world/damagesource/DamageSource;F)F");
        String originalArmorOperands = "net/minecraft/world/damagesource/CombatRules.getDamageAfterAbsorb(FFF)F";
        assertEquals(1, inheritedArmor.stream().filter(originalArmorOperands::equals).count());
        String wear = "net/minecraft/world/entity/LivingEntity.hurtArmor(Lnet/minecraft/world/damagesource/DamageSource;F)V";
        assertTrue(inheritedArmor.contains(wear) && inheritedArmor.indexOf(wear) <
                inheritedArmor.indexOf(originalArmorOperands), "armor wear can precede actual CombatRules operand getter");
        List<String> helper = calls("net/minecraft/world/item/enchantment/EnchantmentHelper.class", "getDamageProtection",
                "(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I");
        assertFalse(helper.isEmpty());
        List<String> observer = calls("com/bettercontent/playtestsupport/mixin/RealIncomingArmorOperandsObserverMixin.class",
                "bc$actualArmorOperands", "(Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;Lnet/minecraft/world/damagesource/DamageSource;F)V");
        assertEquals(3, observer.stream().filter("org/spongepowered/asm/mixin/injection/invoke/arg/Args.get(I)Ljava/lang/Object;"::equals).count());
        assertTrue(observer.stream().noneMatch(call -> call.contains("Args.set(") ||
                call.contains("CombatRules.getDamageAfterAbsorb")), "read original arguments without writing/re-evaluating");
    }
    /** Both original FSTORE3 sites must bracket onLivingDamage; later CombatTracker may be skipped on zero. */
    private static List<String> playerFloatStoreThreeOrder() throws Exception {
        List<String> result = new ArrayList<>();
        try (var stream = PassiveIncomingHitStructureTest.class.getClassLoader().getResourceAsStream(
                "net/minecraft/world/entity/player/Player.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc,
                                                              String signature, String[] exceptions) {
                    if (!method.equals("actuallyHurt") || !desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)V")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitVarInsn(int opcode, int var) {
                            if (opcode == Opcodes.FSTORE && var == 3) result.add("fstore3");
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                              String desc, boolean iface) {
                            if (owner.equals("net/minecraftforge/common/ForgeHooks") && called.equals("onLivingDamage"))
                                result.add("onLivingDamage");
                        }
                    };
                }
            }, 0);
        }
        assertEquals(3, result.size());
        return result;
    }
    private static List<String> originalMagicHelperBranchOrder() throws Exception {
        List<String> result = new ArrayList<>();
        try (var stream = PassiveIncomingHitStructureTest.class.getClassLoader().getResourceAsStream(
                "net/minecraft/world/entity/LivingEntity.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc,
                                                              String signature, String[] exceptions) {
                    if (!method.equals("getDamageAfterMagicAbsorb") ||
                            !desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)F")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.FRETURN) result.add("freturn");
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                              String desc, boolean iface) {
                            if (owner.equals("net/minecraft/world/item/enchantment/EnchantmentHelper") &&
                                    called.equals("getDamageProtection")) result.add("helper");
                        }
                    };
                }
            }, 0);
        }
        return result;
    }
    private static List<String> calls(String resource, String name, String descriptor) throws Exception {
        List<String> found = new ArrayList<>(); int[] methods = {0};
        try (var stream = PassiveIncomingHitStructureTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc,
                                                              String signature, String[] exceptions) {
                    if (!method.equals(name) || !desc.equals(descriptor)) return null;
                    methods[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                              String desc, boolean iface) {
                            found.add(owner + "." + called + desc);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, methods[0], resource + ":" + name + descriptor);
        return found;
    }
}
