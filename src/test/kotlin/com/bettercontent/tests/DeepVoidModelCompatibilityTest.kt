package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@Tag("fast")
class DeepVoidModelCompatibilityTest {
    @Test
    fun deepVoidModelOverlaysUseMinecraft1201ElementRotations() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val mapper = jacksonObjectMapper()
        val allowed = setOf(-45.0, -22.5, 0.0, 22.5, 45.0)

        for (name in listOf("bone_helix", "bone_helix_new")) {
            val model = mapper.readTree(root.resolve("kubejs/assets/the_deep_void/models/custom/$name.json").toFile())
            val elements = model.path("elements")
            assertTrue(elements.isArray)
            for (element in elements) {
                val rotation = element.path("rotation")
                if (rotation.isMissingNode) continue
                assertTrue(rotation.path("axis").asText() in setOf("x", "y", "z"))
                assertTrue(rotation.path("angle").asDouble() in allowed)
            }
        }

        val hydra = mapper.readTree(root.resolve("kubejs/assets/the_deep_void/models/custom/falsehydrahead.json").toFile())
        for (element in hydra.path("elements")) assertTrue(element.path("rotation").isMissingNode)
    }

    @Test
    fun cataclysmSeastoneStairRecipeUsesRegisteredPluralBlockId() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val recipe = jacksonObjectMapper().readTree(root.resolve(
            "kubejs/data/cataclysm/recipes/stonecutting/azure_seastone_brick_stair_from_stonecutting.json"
        ).toFile())
        assertEquals("cataclysm:azure_seastone_brick_stairs", recipe.path("result").asText())
        assertEquals("minecraft:stonecutting", recipe.path("type").asText())
        assertEquals(2, recipe.path("ingredient").size())
    }

    @Test
    fun cataclysmIgnisShieldSoundEventHasItsRuntimeRegistryKey() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val sounds = jacksonObjectMapper().readTree(root.resolve("kubejs/assets/cataclysm/sounds.json").toFile())
        assertTrue(sounds.size() >= 179, "The Cataclysm sound overlay should retain the pinned event catalogue")
        assertEquals(sounds.path("ignis_shield_break"), sounds.path("ignisshieldbreak"))
        assertEquals("cataclysm:entity/ignis_shield01", sounds.path("ignisshieldbreak").path("sounds")[0].path("name").asText())
    }

    @Test
    fun cataclysmDiscPaletteMaskMatchesAllAnimatedFrames() {
        val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()
        val mask = ImageIO.read(root.resolve("kubejs/assets/amendments/textures/block/music_discs/music_disc_mask.png").toFile())
        assertEquals(16, mask.width)
        assertEquals(13 * 16, mask.height)
        for (y in 0 until 80) for (x in 0 until 16) {
            assertEquals(mask.getRGB(x, y), mask.getRGB(x, y + 128))
        }
        assertTrue(Files.exists(root.resolve("kubejs/assets/amendments/textures/block/music_discs/music_disc_mask.png.mcmeta")))
    }
}
