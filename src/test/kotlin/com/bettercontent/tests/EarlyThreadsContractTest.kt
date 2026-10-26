package com.bettercontent.tests

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class EarlyThreadsContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val guides = root.parent.resolve("mod_source/better-discovery-guides")
    private val mapper = ObjectMapper()
    private fun json(path: Path) = mapper.readTree(Files.readString(path))

    @Test fun `primitive previews reference all six exact recipes without duplicating NBT`() {
        val cards = json(guides.resolve("authoring/discoveries.json")).path("cards")
        val axe = cards.first { it.path("id").asText() == "first_hand_axe" }
        val refs = axe.path("recipe_items").map { it.path("recipe").asText() }
        val expected = listOf("rock_hand_axe", "flint_hand_axe", "bone_hand_axe",
            "rock_butcher_knife", "flint_butcher_knife", "bone_butcher_knife")
        assertEquals(expected.map { "kubejs:primitive/$it" }, refs)
        val nbts = expected.map { name ->
            val recipe = json(root.resolve("kubejs/data/kubejs/recipes/primitive/$name.json"))
            assertEquals(if (name.endsWith("hand_axe")) "tconstruct:hand_axe" else "additionalweaponry:butcher_knife",
                recipe.path("result").path("item").asText())
            val nbt = recipe.path("result").path("nbt").asText()
            assertTrue(nbt.contains("tic_materials:"), name)
            assertTrue(nbt.contains("tic_modifiers:"), name)
            assertTrue(nbt.contains("tconstruct:" + name.substringBefore('_')), name)
            nbt
        }
        assertEquals(6, nbts.toSet().size)
        axe.path("recipe_items").forEach { assertEquals(setOf("recipe"), it.fieldNames().asSequence().toSet()) }
        val roots = json(root.resolve("kubejs/data/kubejs/recipes/primitive/canvas_from_hanging_roots.json"))
        assertEquals("minecraft:hanging_roots", roots.path("key").path("R").path("item").asText())
        assertEquals("farmersdelight:canvas", roots.path("result").path("item").asText())
    }

    @Test fun `authored and packaged roster agree on 48 cards and revised teaching`() {
        val authored = json(guides.resolve("authoring/discoveries.json")).path("cards")
        val packaged = json(guides.resolve("src/main/resources/data/better_discovery_guides/threads/catalogue.json"))
        assertEquals("bc.better_discovery_guides.cards.v4", packaged.path("schema").asText())
        assertEquals(48, authored.size())
        val rows = packaged.path("threads")
        assertEquals(authored.map { it.path("id").asText() }.toSet(), rows.map { it.path("id").asText() }.toSet())
        val cuts = setOf("spirits_are_materials", "underground_pressure_builds", "returned_traces", "deaths_door")
        assertTrue(rows.none { it.path("id").asText() in cuts })
        authored.forEach { card ->
            val runtime = rows.first { it.path("id") == card.path("id") }
            for (field in listOf("title", "short_title", "event", "cause", "action", "discovery_routes", "recipe_items"))
                assertEquals(card.path(field), runtime.path(field), card.path("id").asText() + ":" + field)
        }
        val backpack = rows.first { it.path("id").asText() == "make_backpack" }
        assertEquals("crafting_table_crafted", backpack.path("discovery_routes")[0].path("type").asText())
        assertEquals("first", backpack.path("discovery_routes")[0].path("value").asText())
        val font = rows.first { it.path("id").asText() == "font_trip_time_budget" }
        assertEquals("Race the Font’s Clock", font.path("title").asText())
        assertEquals("Font Clock", font.path("short_title").asText())
    }

    @Test fun `EMI routes real native stack and referenced recipe and aspirations remain out of scope`() {
        val dispatch = Files.readString(guides.resolve("src/main/java/com/bettercontent/betterdiscoveryguides/ThreadRecipeItems.java"))
        assertTrue(dispatch.contains("getRecipe(new ResourceLocation(id))"))
        assertTrue(dispatch.contains("EmiApi.displayRecipe(recipe)"))
        assertTrue(dispatch.contains("EmiApi.displayUses(dev.emi.emi.api.stack.EmiStack.of(item))"))
        val resolve = Files.readString(guides.resolve("src/main/java/com/bettercontent/betterdiscoveryguides/ThreadRecipeReference.java"))
        assertTrue(resolve.contains("recipes.byKey(id)"))
        assertTrue(resolve.contains("r.getResultItem(access)"))
        val doc = Files.readString(root.resolve("docs/aspirational-features.md"))
        assertTrue(doc.startsWith("# Aspirational features"))
        for (name in listOf("Water bottle", "Grappling hook", "Climbing")) assertTrue(doc.contains("**$name:**"))
        assertTrue(doc.contains("Exact triggers, copy and scenes remain unchosen"))
        assertFalse(Files.exists(root.resolve("docs/deferred-features.md")))
    }
}
