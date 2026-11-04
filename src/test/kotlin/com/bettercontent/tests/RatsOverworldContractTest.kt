package com.bettercontent.tests

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.time.Duration
import java.nio.file.Files
import java.nio.file.Path

/** Static fail-closed boundary; native generated-world and recipe-registry tests are separate. */
@Tag("fast")
class RatsOverworldContractTest {
    private val root = Path.of("").toAbsolutePath()
    private val mapper = jacksonObjectMapper()
    private fun text(path: String) = Files.readString(root.resolve(path))
    private fun json(path: String) = mapper.readTree(root.resolve(path).toFile())
    private fun hasRetiredId(path: Path) = Files.newBufferedReader(path).useLines { lines ->
        lines.any { it.contains("better_ratlantis_logistics") }
    }
    private fun assertQuarantinedPolicy(path: Path) {
        // The retained audit catalogue is large; do not materialize it repeatedly in the test heap.
        var policySeen = false
        var migrationSeen = false
        mapper.factory.createParser(path.toFile()).use { parser ->
            assertEquals(JsonToken.START_OBJECT, parser.nextToken())
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val key = parser.currentName
                parser.nextToken()
                when (key) {
                    "qualification" -> { assertEquals("unqualified", parser.text); policySeen = true }
                    "migration" -> {
                        assertEquals(JsonToken.START_OBJECT, parser.currentToken())
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            val field = parser.currentName
                            parser.nextToken()
                            if (field == "qualification") {
                                assertEquals("unqualified", parser.text)
                                migrationSeen = true
                            }
                            parser.skipChildren()
                        }
                    }
                }
                parser.skipChildren()
            }
        }
        assertTrue(policySeen && migrationSeen, "both quarantine declarations are mandatory")
    }

    @Test fun retiredModCannotBeStagedOrUsedByPackProductionSources(@TempDir temp: Path) {
        assertFalse(Files.exists(root.resolve("mods/better-ratlantis-logistics-0.1.0.jar")))
        assertTrue(Files.exists(root.resolve("mods/rats.pw.toml")))
        assertFalse(text("gradle/active-custom-mods.json").contains("better-ratlantis-logistics"))
        for (area in listOf("kubejs/server_scripts", "kubejs/startup_scripts", "kubejs/config", "defaultconfigs")) {
            Files.walk(root.resolve(area)).use { files ->
                files.filter { Files.isRegularFile(it) && (it.toString().endsWith(".js") || it.toString().endsWith(".json") || it.toString().endsWith(".toml")) }
                    .forEach {
                        if (it == root.resolve("kubejs/config/culinary_policy.json") &&
                            hasRetiredId(it)) {
                            // Retained discovery records are audit data, not installed recipes.
                            // Fail closed if this policy is ever qualified with the retired IDs.
                            assertQuarantinedPolicy(it)
                        } else assertFalse(hasRetiredId(it), "stale custom mod reference: $it")
                    }
            }
        }
        // Execute the real policy consumer with the real retained audit policy. Any recipe
        // read/write would fail, so the exception above cannot hide active installation.
        if (hasRetiredId(root.resolve("kubejs/config/culinary_policy.json"))) Commands.run(listOf("node", "-e", """
            const fs = require('fs'), vm = require('vm');
            const policy = JSON.parse(fs.readFileSync('kubejs/config/culinary_policy.json', 'utf8'));
            let invoked = false, warned = false;
            const forbidden = () => { throw new Error('unqualified policy reached recipe installation'); };
            const event = new Proxy({}, { get: forbidden });
            vm.runInNewContext(fs.readFileSync('kubejs/server_scripts/progression/83_culinary_policy.js', 'utf8'), {
                Platform: { isLoaded: () => true },
                JsonIO: { read: () => policy },
                console: { warn: message => { if (!message.includes('UNQUALIFIED')) throw Error(message); warned = true; } },
                ServerEvents: { recipes: handler => { invoked = true; handler(event); } }
            });
            if (!invoked || !warned) throw new Error('quarantine branch was not exercised');
        """.trimIndent()), root, temp.resolve("culinary-quarantine.log"), timeout = Duration.ofSeconds(15))
        assertFalse(text("kubejs/server_scripts/progression/85_dimension_font_ecology.js").contains("ratlantis_libation"))
        assertFalse(text("kubejs/startup_scripts/progression/20_transition_items.js").contains("ratlantis_font_grout"))
    }

    @Test fun meteorRecipeGateHasNoRemovedComponentOrExtraSkyStone(@TempDir temp: Path) {
        val recipe = text("kubejs/server_scripts/progression/20_meteor_logistics.js")
        assertTrue(recipe.contains("8x prettypipes:pipe"))
        assertTrue(recipe.contains("ae2:certus_quartz_crystal"))
        assertTrue(recipe.contains("ae2:sky_stone_block"))
        assertFalse(recipe.contains("rats:oratchalcum_ingot"))
        assertFalse(recipe.contains("rats:gem_of_ratlantis"))
        // Three durable devices consume one Sky Stone each; first pipes consume none.
        // Exercise both the Manufacturing and fallback registrations, not raw text counts.
        Commands.run(listOf("node", "-e", """
            const fs = require('fs'), vm = require('vm');
            for (const manufacturing of [false, true]) {
                const recipes = new Map();
                const record = data => ({ id: id => {
                    if (recipes.has(id)) throw Error('duplicate recipe: ' + id);
                    recipes.set(id, data);
                }});
                const event = {
                    remove: () => {},
                    shaped: (result, pattern, key) => record({result, pattern, key}),
                    shapeless: (result, ingredients) => record({result, ingredients}),
                    custom: data => record(data)
                };
                vm.runInNewContext(fs.readFileSync('kubejs/server_scripts/progression/20_meteor_logistics.js', 'utf8'), {
                    Platform: {isLoaded: id => id === 'better_manufacturing' && manufacturing},
                    ServerEvents: {recipes: callback => callback(event)}
                });
                const item = value => typeof value === 'string' ? value : value && value.item;
                const count = data => data.ingredients
                    ? data.ingredients.filter(value => item(value) === 'ae2:sky_stone_block').length
                    : data.pattern.join('').split('').filter(symbol => item(data.key[symbol]) === 'ae2:sky_stone_block').length;
                for (const id of ['prettypipes:item_terminal', 'prettypipes:pressurizer', 'kubejs:meteor_logistics/ae2_energy_acceptor']) {
                    if (!recipes.has(id) || count(recipes.get(id)) !== 1) throw Error('one finite Sky Stone required: ' + id);
                }
                if (count(recipes.get('kubejs:meteor_logistics/pipe_batch')) !== 0) throw Error('first pipes gained a Sky Stone gate');
            }
        """.trimIndent()), root, temp.resolve("meteor-recipes.log"), timeout = Duration.ofSeconds(15))
        assertFalse(text("kubejs/server_scripts/progression/70_transition_components.js").contains("rats:oratchalcum_ingot"))
        assertFalse(text("kubejs/server_scripts/compat/retained/refactor__balance__63_fonts_hexerei_occultism_chalk.js").contains("rats:gem_of_ratlantis"))
        assertTrue(text("kubejs/server_scripts/policy/dimension_access.js").contains("player access is retired"))
    }

    @Test fun ordinaryRatsRemainWhileRatlantisRehomingIsRetired() {
        assertTrue(text("config/rats-common.toml").contains("\"Ratlantis Datapack Enabled by Default\" = false"))
        assertTrue(text("kubejs/server_scripts/policy/dimension_access.js").contains("event.remove({ output: 'rats:chunky_cheese_token' })"))
        val archaeology = text("kubejs/server_scripts/progression/21_rat_archeology.js")
        assertTrue(archaeology.contains("minecraft:amethyst_shard"))
        assertTrue(archaeology.contains("rats:cheese"))
        assertFalse(archaeology.contains("rats:gem_of_ratlantis"))
        assertFalse(archaeology.contains("rats:marbled_cheese"))
        assertTrue(archaeology.contains("kubejs:rat_archeology/archeologist_hat"))
        assertFalse(archaeology.contains("minecraft:iron_pickaxe"))
        assertFalse(archaeology.contains("minecraft:iron_shovel"))
        assertFalse(Files.exists(root.resolve("kubejs/server_scripts/progression/23_rat_coastal_fishing.js")))
        assertFalse(Files.exists(root.resolve("kubejs/data/kubejs/worldgen/placed_feature/rat_oratchalcum_ore.json")))
        for (id in listOf("rat_oratchalcum_deep", "rat_gem_deep")) {
            assertFalse(Files.exists(root.resolve("kubejs/data/kubejs/worldgen/configured_feature/$id.json")))
        }
        for (id in listOf("dutchrat_ship", "baron_runway")) {
            assertFalse(Files.exists(root.resolve("kubejs/data/rats/tags/worldgen/biome/has_structure/$id.json")))
        }
        val quarry = json("kubejs/data/rats/recipes/rat_quarry.json")
        assertEquals("rats:rat_quarry", quarry.path("result").path("item").asText())
        assertEquals("better_manufacturing:part_variant", quarry.path("type").asText())
        assertEquals("tconstruct:pick_head", quarry.path("key").path("H").path("item").asText())
        assertEquals("forge:storage_blocks/blue_cheese", quarry.path("key").path("B").path("tag").asText())
        assertEquals("forge:crafting_tables", quarry.path("key").path("C").path("tag").asText())
        assertEquals("forge:gems/diamond", quarry.path("key").path("D").path("tag").asText())
        assertEquals(8, quarry.path("parts")[0].path("slot").asInt())
        for (fish in listOf("chorus_crab", "end_glow", "voidbiter")) {
            val restrictions = json("kubejs/data/starcatcher/starcatcher/fish/$fish.json").path("world_restrictions")
            assertEquals("minecraft:overworld", restrictions.path("dimensions")[0].asText())
            assertTrue(restrictions.path("biomes").size() > 0)
        }
    }
}
