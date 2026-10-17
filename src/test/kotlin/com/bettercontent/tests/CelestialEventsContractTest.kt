package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Pack ownership/data contracts, not substitutes for native moon/travel acceptance. */
class CelestialEventsContractTest {
    private val root = Path.of(System.getProperty("user.dir"))
    private val mapper = jacksonObjectMapper()

    @Test fun `celestial is upstream of Font and Commerce without a dependency cycle`() {
        val mods = mapper.readTree(root.resolve("gradle/active-custom-mods.json").toFile())["mods"]
            .associateBy { it["repository"].asText() }
        val celestial = mods.getValue("better-celestial-events")
        assertEquals("better_celestial_events", celestial["modId"].asText())
        assertEquals("better-celestial-events-0.1.0.jar", celestial["artifact"].asText())
        assertTrue(celestial.path("dependsOn").isMissingNode || celestial.path("dependsOn").isEmpty)
        for (owner in listOf("better-dimension-fonts", "better-spirit-commerce")) {
            assertTrue(mods.getValue(owner).path("dependsOn").any { it.asText() == "better-celestial-events" })
        }
    }

    @Test fun `Thin Veil habitats are explicit nonoverlapping selections`() {
        fun values(kind: String): Set<String> {
            val tag = mapper.readTree(root.resolve("kubejs/data/better_celestial_events/tags/worldgen/biome/thin_veil_$kind.json").toFile())
            assertFalse(tag.path("replace").asBoolean())
            val entries = tag["values"].map { if (it.isTextual) it.asText() else it["id"].asText() }
            assertEquals(entries.size, entries.toSet().size)
            assertTrue(entries.all { it.matches(Regex("[a-z_]+:[a-z_]+")) })
            return entries.toSet()
        }
        val forest = values("forest")
        val swamp = values("swamp")
        assertTrue("minecraft:forest" in forest)
        assertTrue("minecraft:swamp" in swamp)
        assertTrue(forest.intersect(swamp).isEmpty())
    }
}
