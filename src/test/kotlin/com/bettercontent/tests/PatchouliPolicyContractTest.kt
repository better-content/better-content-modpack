package com.bettercontent.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class PatchouliPolicyContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root")).toAbsolutePath().normalize()

    @Test
    fun `books do not request recipes removed by pack policy`() {
        val overrides = listOf(
            "kubejs/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/source/alchemical_sourcelink.json" to
                "ars_nouveau:alchemical_sourcelink",
            "kubejs/assets/pneumaticcraft/patchouli_books/book/en_us/entries/tools/minigun_ammo.json" to
                "pneumaticcraft:gun_ammo_potion_crafting",
        )

        overrides.forEach { (relative, removedRecipe) ->
            val contents = Files.readString(root.resolve(relative))
            assertTrue(contents.contains("This recipe is disabled in Better Content."), relative)
            assertFalse(contents.contains("\"recipe\": \"$removedRecipe\""), relative)
        }
    }
}
