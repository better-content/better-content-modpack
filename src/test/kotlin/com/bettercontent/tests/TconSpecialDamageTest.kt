package com.bettercontent.tests

import com.bettercontent.tests.balance.TconSpecialDamage as Damage
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.objectweb.asm.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.abs

/** Native resource/bytecode contracts and scalar math; no Forge launch or hook-execution verdict. */
@Tag("fast")
class TconSpecialDamageTest {
    private val mapper = jacksonObjectMapper()
    private val root = Path.of(System.getProperty("bc.repo.root", "."))
    private val catalogue = mapper.readTree(root.resolve("kubejs/config/tcon_edps_catalogue.json").toFile())
    private val armor = catalogue["armor_canon"]
    private val canon = Damage.Canon(listOf("trash", "elite", "boss").map {
        Damage.ArmorBucket(armor["weights"][it].asDouble(), armor["armor"][it].asDouble())
    }, armor["toughness"].asDouble())
    private val reference = Damage.Model(Damage.Kind.ORDINARY, 3.0)
    private fun pinnedModJar(filename: String): Path = Path.of(System.getProperty("user.home"), ".cache/bc/packwiz-downloads/mods", filename)

    private data class Facts(
        val ops: MutableList<Int> = mutableListOf(),
        val fields: MutableList<String> = mutableListOf(),
        val calls: MutableList<String> = mutableListOf(),
        val constants: MutableList<Any> = mutableListOf(),
        val handles: MutableList<String> = mutableListOf(),
    )
    private fun facts(jar: ZipFile, entry: String): Map<String, Facts> {
        val methods = linkedMapOf<String, Facts>()
        val bytes = jar.getInputStream(jar.getEntry(entry)).use { it.readBytes() }
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, desc: String, sig: String?, exceptions: Array<out String>?): MethodVisitor {
                val result = methods.getOrPut(name + desc) { Facts() }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInsn(opcode: Int) { result.ops += opcode }
                    override fun visitLdcInsn(value: Any) { result.constants += value }
                    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) { result.fields += "$owner.$name" }
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) { result.calls += "$owner.$name" }
                    override fun visitInvokeDynamicInsn(name: String, descriptor: String, handle: Handle, vararg args: Any) {
                        result.handles += args.filterIsInstance<Handle>().map { it.owner + "." + it.name }
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return methods
    }
    private fun Map<String, Facts>.method(name: String) = filterKeys { it.startsWith("$name(") }.values.single()

    @Test fun pinnedNativePierceAndLegacyPiercingSemanticsMatchSolverInputs() {
        val path = pinnedModJar("TConstruct-1.20.1-3.11.2.166.jar")
        val sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        assertEquals("94d07ec0ff45c49f5908beff7acb9fbec43fdde5", sha1)
        val pin = Files.readString(root.resolve("mods/tinkers-construct.pw.toml"))
        assertTrue(pin.contains(sha1), "Active Packwiz pin must match the inspected native implementation")
        ZipFile(path.toFile()).use { jar ->
            val modules = jar.getInputStream(jar.getEntry("data/tconstruct/tinkering/modifiers/pierce.json")).use { mapper.readTree(it)["modules"] }
            val bonus = modules.single { it["type"].asText() == "tconstruct:stat_boost" }
            assertEquals(0.5, bonus["each_level"].asDouble())
            assertEquals("add", bonus["operation"].asText())
            assertEquals("tconstruct:attack_damage", bonus["stat"].asText())
            val effect = modules.single { it["type"].asText() == "tconstruct:mob_effect" }
            assertTrue(effect["apply_before_melee"].asBoolean())
            assertEquals(2.0, effect["level"]["per_level"].asDouble())
            assertEquals("tconstruct:pierce", effect["effect"].asText())
            val apply = facts(jar, "slimeknights/tconstruct/library/modifiers/modules/combat/MobEffectModule.class").method("applyEffect")
            assertTrue("java/lang/Math.round" in apply.calls)
            assertTrue(Opcodes.ICONST_1 in apply.ops && Opcodes.ISUB in apply.ops, "Amplifier is computed level minus one")
            val effects = facts(jar, "slimeknights/tconstruct/shared/TinkerEffects.class")
            assertTrue(effects.method("<clinit>").handles.any { it.endsWith("lambda\$static\$5") })
            val pierce = effects.method("lambda\$static\$5")
            assertTrue(-1.0 in pierce.constants)
            assertTrue(pierce.fields.any { it.endsWith("Attributes.f_22284_") })
            assertTrue(pierce.fields.any { it.endsWith("Operation.ADDITION") })
            val build = facts(jar, "slimeknights/tconstruct/library/tools/stat/FloatToolStat.class")
                .filterKeys { it.startsWith("build(") && it.endsWith("Ljava/lang/Float;") }.values.single()
            assertEquals(listOf("base", "add", "percent", "multiply"), build.fields.map { it.substringAfterLast('.') })
            assertEquals(listOf(Opcodes.FADD, Opcodes.FCONST_1, Opcodes.FADD, Opcodes.FMUL, Opcodes.FMUL, Opcodes.ARETURN), build.ops)
            val legacy = facts(jar, "slimeknights/tconstruct/tools/modifiers/upgrades/melee/PiercingModifier.class")
            assertTrue(-0.5f in legacy.method("addToolStats").constants)
            assertTrue(legacy.method("afterMeleeHit").calls.any { it.endsWith("IToolStackView.getMultiplier") })
            assertTrue(legacy.method("afterMeleeHit").calls.any { it.endsWith("ToolAttackUtil.attackEntitySecondary") })
            val bypass = jar.getInputStream(jar.getEntry("data/minecraft/tags/damage_type/bypasses_armor.json")).use { mapper.readTree(it)["values"] }
            assertTrue(bypass.any { it.asText() == "tconstruct:piercing" })
        }
    }

    @Test fun armorCanonAgreesWithRetainedProfileAndArmorFormulaHasKnownBoundaries() {
        val profile = mapper.readTree(root.resolve("docs/tcon_combat_profile.json").toFile())
        assertEquals(profile["edps_weights"], armor["weights"])
        listOf("trash", "elite", "boss").forEach { assertEquals(profile["armor_canon"][it], armor["armor"][it]) }
        assertEquals(0.0, canon.toughness)
        assertEquals(10.0, Damage.afterArmor(10.0, 0.0, 0.0))
        assertEquals(2.0, Damage.afterArmor(10.0, 100.0, 0.0), 1e-12)
        assertTrue(Damage.afterArmor(10.0, 10.0, 20.0) < Damage.afterArmor(10.0, 10.0, 0.0))
    }

    @Test fun specialModelsSolveAllApprovedTiersAgainstTheCanonWithoutReachData() {
        val models = listOf(
            Damage.Model(Damage.Kind.PIERCE, 3.0, 1) to 1.2,
            Damage.Model(Damage.Kind.PIERCE, 3.0, 2) to 0.85,
            Damage.Model(Damage.Kind.LEGACY_PIERCING, 3.0, 1) to 1.8,
            Damage.Model(Damage.Kind.FIXED, 1.0) to 1.75,
        )
        for ((model, speed) in models) for (tier in listOf(80.0, 93.333333, 100.0, 120.0)) {
            val multiplier = Damage.solve(tier, speed, model, canon)
            val expected = Damage.expectedHit(reference, 1.0, canon) * 1.6 * tier / 100.0
            assertEquals(expected, Damage.expectedHit(model, multiplier, canon) * speed, 0.000005)
        }
        assertEquals(1.0, Damage.solve(100.0, 1.6, reference, canon))
        // The zero-head fixed model is materially independent and reproduces the legacy Chisel solve.
        assertEquals(2.471201, Damage.solve(100.0, 1.75, Damage.Model(Damage.Kind.FIXED, 1.0), canon, scale = 0.9))
        assertEquals(2.744270, Damage.solve(100.0, 1.75, Damage.Model(Damage.Kind.FIXED, 1.0), canon))
    }

    @Test fun nativeModelsExplicitlyDoNotEndorseTheLegacyPiercingCatalogueMath() {
        val rows = catalogue["tools"].toList() + catalogue["weapons"].toList()
        for (row in rows.filter { it["model"].asText() in setOf("pierce", "legacy_piercing") }) {
            val level = if (row["model"].asText() == "pierce") (row["innate_damage"].asDouble() * 2).toInt() else 1
            val kind = if (row["model"].asText() == "pierce") Damage.Kind.PIERCE else Damage.Kind.LEGACY_PIERCING
            val native = Damage.expectedHit(Damage.Model(kind, 3.0, level), row["multiplier"].asDouble(), canon) * row["speed"].asDouble()
            val oldTarget = Damage.expectedHit(reference, 1.0, canon) * 1.6 * 0.9 * row["tier"].asDouble() / 100.0
            assertTrue(abs(native - oldTarget) > 0.01, "Do not mistake old catalogue coefficients for native model proof")
        }
    }

    @Test fun alternateInverseRatioPreservesRawDamageAcrossMaterialsAndChargeWithoutChangingFlight() {
        for (old in listOf(0.581538, 0.847059, 1.26, 1.32, 1.374545)) {
            for (new in listOf(old * 0.7, old, old * 1.8)) for (base in listOf(3.0, 8.0, 16.0)) {
                for (charge in listOf(0.2, 0.75, 1.0)) {
                    val coefficient = Damage.preservingAlternateCoefficient(1.5, old, new)
                    assertEquals(base * old * 1.5 * charge, base * new * coefficient * charge, 1e-12)
                }
            }
        }
        // Preserving pre-mitigation damage also preserves the same downstream armor operation.
        val coefficient = Damage.preservingAlternateCoefficient(1.0, 0.847059, 1.7)
        assertEquals(Damage.afterArmor(8.0 * 0.847059, 3.0, 0.0), Damage.afterArmor(8.0 * 1.7 * coefficient, 3.0, 0.0), 1e-12)
    }

    @Test fun nativeAlternatePathsExposeCouplingButDoNotProvideADamageOnlyJsonCoefficient() {
        ZipFile(pinnedModJar("TConstruct-1.20.1-3.11.2.166.jar").toFile()).use { jar ->
            val thrown = facts(jar, "slimeknights/tconstruct/tools/entity/ThrownTool.class").values.single {
                it.calls.any { call -> call.endsWith("ToolAttackContext\$Builder.baseDamage") }
            }
            assertTrue(thrown.fields.any { it.endsWith("ToolStats.ATTACK_DAMAGE") })
            assertTrue(thrown.fields.any { it.endsWith("ThrownTool.multiplier") })
            assertTrue(Opcodes.FMUL in thrown.ops)
            val throwing = facts(jar, "slimeknights/tconstruct/tools/modules/interaction/ThrowingModule.class").method("onStoppedUsing")
            assertTrue(throwing.fields.any { it.endsWith("ToolStats.VELOCITY") }, "Velocity participates in damage AND trajectory, not damage-only compensation")
            val bonk = facts(jar, "slimeknights/tconstruct/tools/modifiers/ability/sling/BonkingModifier.class")
            assertTrue(1.5f in bonk.method("getMeleeDamage").constants)
            assertTrue(Opcodes.FMUL in bonk.method("getMeleeDamage").ops)
            assertTrue(bonk.method("beforeReleaseUsing").calls.any { it.endsWith("ToolAttackContext\$Builder.applyAttributes") })
            var warPickSignature: String? = null
            val tools = jar.getInputStream(jar.getEntry("slimeknights/tconstruct/tools/TinkerTools.class")).use { it.readBytes() }
            ClassReader(tools).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    if (name == "warPick") warPickSignature = signature
                    return null
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            assertTrue(warPickSignature?.contains("ModifiableCrossbowItem") == true)
            val crossbow = facts(jar, "slimeknights/tconstruct/library/tools/item/ranged/ModifiableCrossbowItem.class").values
            assertTrue(crossbow.any { method -> method.fields.any { it.endsWith("ToolStats.PROJECTILE_DAMAGE") } })
            assertFalse(crossbow.any { method -> method.fields.any { it.endsWith("ToolStats.ATTACK_DAMAGE") } })
            val war = jar.getInputStream(jar.getEntry("data/tconstruct/tinkering/modifiers/war_charge.json")).use { mapper.readTree(it)["modules"] }
            assertEquals("tconstruct:velocity", war.single { it["type"].asText() == "tconstruct:conditional_stat" }["stat"].asText())
        }
    }

    @Test fun allAlternateTargetsAndCurrentChassisCoefficientsRemainFrozen() {
        val expected = mapOf(
            "tinkers_things:blockram" to (100 to 0.847059),
            "construct_arsenal:buckler" to (100 to 1.32),
            "tinkers_katanas:fuma_shuriken" to (100 to 0.581538),
            "tconstruct:javelin" to (105 to 1.374545),
            "tconstruct:battlesign" to (100 to 1.26),
            "tconstruct:war_pick" to (100 to 1.26),
        )
        val rows = (catalogue["tools"].toList() + catalogue["weapons"].toList()).filter { it.has("alternate") }
        assertEquals(expected.keys, rows.map { it["item"].asText() }.toSet())
        rows.forEach { row ->
            val item = row["item"].asText()
            assertEquals(expected.getValue(item).first, row["alternate"]["tier"].asInt())
            assertEquals(expected.getValue(item).second, row["multiplier"].asDouble())
            val (ns, name) = item.split(":", limit = 2)
            val modules = mapper.readTree(root.resolve("kubejs/data/$ns/tinkering/tool_definitions/$name.json").toFile())["modules"]
            assertEquals(expected.getValue(item).second, modules.single { it["type"].asText() == "tconstruct:multiply_stats" }
                .get("multipliers").get("tconstruct:attack_damage").asDouble())
        }
        val chisel = mapper.readTree(root.resolve("kubejs/data/tinkers_things/tinkering/tool_definitions/chisel.json").toFile())["modules"]
        assertFalse(chisel.any { it["type"].asText() == "tconstruct:part_stats" })
        assertEquals(1.0, chisel.single { it["type"].asText() == "tconstruct:base_stats" }
            .get("stats").get("tconstruct:attack_damage").asDouble())
        val minotaur = (catalogue["tools"].toList() + catalogue["weapons"].toList()).single { it["item"].asText() == "tconstruct:minotaur_axe" }
        assertTrue(minotaur["exception"].asText().contains("+7 sprint charge"))
    }

    @Test fun invalidModelCanonAndCompensationInputsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { Damage.Model(Damage.Kind.LEGACY_PIERCING, 0.1, 1) }
        assertThrows(IllegalArgumentException::class.java) { Damage.Model(Damage.Kind.PIERCE, 3.0) }
        assertThrows(IllegalArgumentException::class.java) { Damage.Canon(listOf(Damage.ArmorBucket(0.5, 3.0)), 0.0) }
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Damage.preservingAlternateCoefficient(1.0, 1.0, bad) }
            assertThrows(IllegalArgumentException::class.java) { Damage.solve(100.0, bad, reference, canon) }
        }
    }
}
