package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@Tag("fast")
class MalumAcquisitionGraphContractTest {
    private val root = Path.of(System.getProperty("bc.repo.root"))
    private val mapper = jacksonObjectMapper()

    @Test
    fun `required roots and native alternates are represented with explicit unresolved gates`() {
        val graph = graph()
        assertEquals("bc.malum_acquisition_graph.v1", graph.path("schema").asText())
        assertTrue(graph.path("malum08LegacyRoutesExcluded").asBoolean())
        assertTrue(graph.path("graphSemantics").asText().contains("OR across routes"))
        assertTrue(graph.path("graphSemantics").asText().contains("OR of acceptable inputs"))

        val routes = graph.path("routes").associateBy { it.path("id").asText() }
        val required = graph.path("requiredOutcomes").map { it.asText() }.toSet()
        val represented = routes.values.flatMap { route -> route.path("outputs").map { it.asText() } }.toSet()
        assertTrue(represented.containsAll(required), "Every required outcome needs at least one documented source route")

        val legacy = routes.values.filter { it.path("malum08Legacy").asBoolean() }
        assertEquals(
            setOf("malum.legacy.soulstone_ore", "malum.legacy.brilliant_ore", "malum.legacy.cthonic_gold_ore", "malum.legacy.natural_quartz_ore"),
            legacy.map { it.path("id").asText() }.toSet(),
        )
        assertTrue(legacy.all { it.path("status").asText() == "excluded-by-MALUM-08" })

        // Separate producers are OR alternatives. Within one producer, every input group is needed.
        assertTrue(routes.keys.containsAll(setOf("cthonic_gold.deep_ore", "cthonic_gold.ritual")))
        val ritual = routes.getValue("cthonic_gold.ritual")
        assertEquals(4, ritual.path("requiresAnyOf").size())
        assertTrue(routes.getValue("soulstone.crushed_from_raw").path("outputs").any { it.asText() == "malum:crushed_soulstone" })
        assertTrue(routes.getValue("malum.hex_ash.spirit_infusion").path("requiresAnyOf").any { group -> group.any { it.asText() == "better_content_economy:work_spirit" } })
        assertTrue(routes.getValue("malum.crude_scythe").path("excludedByEconomy").asBoolean())
        assertEquals(
            setOf("data/malum/recipes/create/crushing/crush_raw_soulstone.json"),
            routes.getValue("soulstone.crushed_from_raw").path("pinnedProviderResources").map { it.asText() }.toSet(),
        )
        assertEquals(
            setOf(
                "data/malum/recipes/crude_scythe.json",
                "data/malum/tags/items/soul_hunter_weapon.json",
                "data/malum/tags/items/scythe.json",
                "com/sammy/malum/core/handlers/SoulDataHandler.class",
            ),
            routes.getValue("malum.crude_scythe").path("pinnedProviderResources").map { it.asText() }.toSet(),
        )
        assertEquals(
            setOf(
                "data/malum/spirit_data/entity/skeleton.json",
                "com/sammy/malum/core/handlers/SpiritHarvestHandler.class",
                "com/sammy/malum/core/listeners/SpiritDataReloadListener.class",
            ),
            routes.getValue("malum.spirit_harvest_on_exposed_death").path("pinnedProviderResources").map { it.asText() }.toSet(),
        )
        assertEquals(
            setOf("data/malum/structures/weeping_well.nbt", "data/malum/loot_tables/blocks/primordial_soup.json"),
            routes.getValue("weeping_well.native_structure").path("pinnedProviderResources").map { it.asText() }.toSet(),
        )

        val unresolved = graph.path("unresolvedRoots").map { it.asText() }.toSet()
        assertEquals(setOf("malum:cursed_grit"), unresolved)
        assertFalse(unresolved.contains("bloodmagic:blankslate"), "Blank Slate has a conditional LP source route")
        assertTrue(graph.path("externalRoots").any { it.path("id").asText() == "the_deep_void:dimension_access" })
    }

    @Test
    fun `source reachability excludes legacy veins and closes the declared roots`() {
        val graph = graph()
        val reachable = reachableWithoutLegacy(graph)
        val required = graph.path("requiredOutcomes").map { it.asText() }.toSet()
        assertEquals(emptySet<String>(), required - reachable)
        assertTrue(reachable.containsAll(setOf(
            "better_content_economy:ordinary_spirits", "better_content_economy:work_spirit", "better_content_economy:control_spirit", "malum:hex_ash",
            "malum:runewood_planks", "malum:processed_soulstone", "malum:spirit_altar",
            "malum:brilliant_stone", "malum:cthonic_gold", "malum:natural_quartz",
            "malum:soulwood_growth", "malum:primordial_soup",
        )))

        // The altar path is conditional on positive-level final-death heart-fragment access.
        assertTrue(reachable.contains("bloodmagic:blankslate"))
        assertTrue(reachable.contains("bloodmagic:altar_lp_capability"))
        assertTrue(reachable.contains("rpg_stats:heart_block_with_fragment"))
        assertTrue(graph.path("knownConditionalRoots").map { it.asText() }.containsAll(setOf(
            "player:positive_xp_level", "player:confirmed_final_death",
        )))
        assertFalse(reachable.contains("malum:soulstone_ore"), "The MALUM-08 legacy Soulstone vein must not contribute to reachability")
    }

    @Test
    fun `documented routes cite checked-in source or exact pinned resources and graph has no cycles`() {
        val graph = graph()
        val activeRoutes = graph.path("routes").filterNot { it.path("malum08Legacy").asBoolean() || it.path("excludedByEconomy").asBoolean() }
        val dependencyEdges = mutableMapOf<String, MutableSet<String>>()
        for (route in activeRoutes) {
            assertTrue(route.path("evidence").isArray && route.path("evidence").size() > 0,
                "${route.path("id").asText()} must carry source or provider-pin evidence")
            for (evidence in route.path("evidence")) {
                val reference = evidence.asText()
                if (reference.startsWith("Minecraft ")) continue
                assertTrue(Files.isRegularFile(root.resolve(reference).normalize()), "Missing evidence for ${route.path("id").asText()}: $reference")
            }
            if (route.has("pinnedProviderResources")) {
                assertTrue(route.path("pinnedProviderResources").size() > 0)
                assertTrue(route.path("evidence").any { it.asText() == "mods/malum.pw.toml" },
                    "Pinned resource list requires the matching exact provider pin")
            }
            val outputs = route.path("outputs").map { it.asText() }
            for (group in route.path("requiresAnyOf")) {
                for (input in group) {
                    for (output in outputs) dependencyEdges.getOrPut(input.asText()) { mutableSetOf() }.add(output)
                }
            }
        }

        val components = stronglyConnectedComponents(dependencyEdges)
        assertTrue(components.none { component ->
            component.size > 1 || component.any { node -> node in dependencyEdges[node].orEmpty() }
        }, "Acquisition cycle(s) found: ${components.filter { it.size > 1 }}")
    }

    private fun reachableWithoutLegacy(graph: com.fasterxml.jackson.databind.JsonNode): MutableSet<String> {
        val roots = graph.path("externalRoots").associate { it.path("id").asText() to it.path("status").asText() }
        val reachable = roots.filterValues { it != "unresolved-root" }.keys.toMutableSet()
        val enabledRoutes = graph.path("routes").filterNot { it.path("malum08Legacy").asBoolean() || it.path("excludedByEconomy").asBoolean() }
        var changed: Boolean
        do {
            changed = false
            for (route in enabledRoutes) {
                val groups = route.path("requiresAnyOf")
                if (groups.all { group -> group.any { it.asText() in reachable } }) {
                    for (output in route.path("outputs")) changed = reachable.add(output.asText()) || changed
                }
            }
        } while (changed)
        return reachable
    }

    private fun graph() = mapper.readTree(root.resolve("docs/malum_acquisition_graph.json").toFile())

    private fun stronglyConnectedComponents(edges: Map<String, Set<String>>): List<Set<String>> {
        val nodes = (edges.keys + edges.values.flatten()).toSet()
        var nextIndex = 0
        val indices = mutableMapOf<String, Int>()
        val lowLinks = mutableMapOf<String, Int>()
        val stack = ArrayDeque<String>()
        val onStack = mutableSetOf<String>()
        val result = mutableListOf<Set<String>>()

        fun visit(node: String) {
            indices[node] = nextIndex
            lowLinks[node] = nextIndex++
            stack.addLast(node)
            onStack.add(node)
            for (next in edges[node].orEmpty()) {
                if (next !in indices) {
                    visit(next)
                    lowLinks[node] = minOf(lowLinks.getValue(node), lowLinks.getValue(next))
                } else if (next in onStack) {
                    lowLinks[node] = minOf(lowLinks.getValue(node), indices.getValue(next))
                }
            }
            if (lowLinks.getValue(node) == indices.getValue(node)) {
                val component = mutableSetOf<String>()
                do {
                    val member = stack.removeLast()
                    onStack.remove(member)
                    component.add(member)
                } while (member != node)
                result.add(component)
            }
        }

        for (node in nodes) if (node !in indices) visit(node)
        return result
    }
}
