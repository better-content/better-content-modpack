package com.bettercontent.tests

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Source-level recipe contract; native recipe-registry acceptance remains separate. */
@Tag("fast")
class GroutRecipeContractTest {
    private val root = Path.of("").toAbsolutePath()
    private fun text(path: String) = Files.readString(root.resolve(path))

    @Test fun distinctOverworldRockPairsProduceOrdinaryGrout(@TempDir temp: Path) {
        Commands.run(listOf("node", "-e", """
            const fs = require('fs'), vm = require('vm');
            const tags = new Map(), recipes = new Map(), removals = [];
            const record = data => ({id: id => {
                if (recipes.has(id)) throw Error('duplicate recipe ' + id);
                recipes.set(id, data);
            }});
            vm.runInNewContext(fs.readFileSync('kubejs/server_scripts/progression/10_hand_workshop.js', 'utf8'), {
                Platform: {isLoaded: () => false},
                ServerEvents: {
                    tags: (type, fn) => {
                        if (type !== 'item') throw Error(type);
                        fn({add: (id, values) => tags.set(id, values)});
                    },
                    recipes: fn => fn({
                        remove: filter => removals.push(filter),
                        shaped: (result, pattern, key) => record({result, pattern, key}),
                        shapeless: (result, ingredients) => record({result, ingredients}),
                        smelting: (result, input) => record({result, input, type: 'smelting'}),
                        blasting: (result, input) => record({result, input, type: 'blasting'}),
                        custom: recipe => record(recipe)
                    })
                }
            });
            const families = ['gabbro', 'slate', 'phyllite', 'quartzite', 'schist'];
            if (tags.size !== families.length) throw Error('missing stone family tag');
            let count = 0;
            for (let i = 0; i < families.length; i++) {
                for (let j = i + 1; j < families.length; j++) {
                    const left = families[i], right = families[j];
                    const recipe = recipes.get('kubejs:hand_workshop/grout/' + left + '_' + right);
                    if (!recipe || recipe.result !== '2x tconstruct:grout' ||
                        recipe.ingredients.length !== 2 ||
                        recipe.ingredients[0] !== '#kubejs:refractory_stone/' + left ||
                        recipe.ingredients[1] !== '#kubejs:refractory_stone/' + right)
                        throw Error('invalid distinct-family grout recipe ' + left + '_' + right);
                    count++;
                }
            }
            if (count !== 10 || [...recipes.keys()].filter(id => id.startsWith('kubejs:hand_workshop/grout/')).length !== 12)
                throw Error('unexpected grout recipe count');
            for (const [suffix, type] of [['seared_brick', 'smelting'], ['seared_brick_blasting', 'blasting']]) {
                const recipe = recipes.get('kubejs:hand_workshop/grout/' + suffix);
                if (!recipe || recipe.type !== type || recipe.result !== 'tconstruct:seared_brick' || recipe.input !== 'tconstruct:grout')
                    throw Error('missing grout firing route ' + type);
            }
            if (!removals.some(filter => filter.output === 'tconstruct:grout') ||
                !removals.some(filter => filter.input === 'tconstruct:grout'))
                throw Error('native clay and processing defaults not replaced');
            if ([...recipes.values()].some(recipe => JSON.stringify(recipe).includes('font_grout') ||
                JSON.stringify(recipe).includes('refractory_compound')))
                throw Error('retired grout intermediate still has a recipe');
        """.trimIndent()), root, temp.resolve("grout-recipes.log"), timeout = Duration.ofSeconds(15))
    }

    @Test fun retiredCustomItemsAreNotRegisteredOrRequired() {
        val startup = text("kubejs/startup_scripts/progression/20_transition_items.js")
        val hover = text("kubejs/config/hover_annotations.json")
        val policy = jacksonObjectMapper().readTree(root.resolve("kubejs/config/crafting_policy.json").toFile())
        for (id in listOf("refractory_compound", "nether_font_grout", "aether_font_grout", "bumblezone_font_grout")) {
            assertFalse(startup.contains(id), "retired item still registered: $id")
            assertFalse(hover.contains(id), "retired item still annotated: $id")
            assertFalse(policy.toString().contains(id), "retired item still required: $id")
        }
        assertTrue(policy.path("capability_roots").any { root ->
            root.path("capability").asText() == "tconstruct.seared_metallurgy" &&
                root.path("required_proofs").map { it.asText() } == listOf("tconstruct:grout", "tconstruct:seared_brick")
        })
    }
}
