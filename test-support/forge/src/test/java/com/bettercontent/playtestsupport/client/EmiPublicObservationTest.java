package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure reflective projection contracts: no Forge client, EMI index or screenshot is started. */
class EmiPublicObservationTest {
    public record Stack(String id) { public String getId() { return id; } }
    public record Ingredient(List<Stack> alternatives) {
        public List<Stack> getEmiStacks() { return alternatives; }
    }
    public record Category(String id) { public String getId() { return id; } }
    public record Recipe(String id, Category category, List<Stack> outputs, List<Ingredient> inputs) {
        public String getId() { return id; }
        public Category getCategory() { return category; }
        public List<Stack> getOutputs() { return outputs; }
        public List<Ingredient> getInputs() { return inputs; }
    }
    private static final String FUEL = "dev.emi.emi.recipe.EmiFuelRecipe";
    private static final String SYNTHETIC = "dev.emi.emi.recipe.EmiSyntheticIngredientRecipe";
    private static final Method ID, CATEGORY, OUTPUTS, CATEGORY_ID, STACK_ID, INPUTS, STACKS;
    static {
        try {
            ID = Recipe.class.getMethod("getId"); CATEGORY = Recipe.class.getMethod("getCategory");
            OUTPUTS = Recipe.class.getMethod("getOutputs"); CATEGORY_ID = Category.class.getMethod("getId");
            STACK_ID = Stack.class.getMethod("getId"); INPUTS = Recipe.class.getMethod("getInputs");
            STACKS = Ingredient.class.getMethod("getEmiStacks");
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static JsonObject project(String provider, Recipe recipe) throws Exception {
        return EmiPublicObservation.projectRecipe(recipe, provider, ID, CATEGORY, OUTPUTS, CATEGORY_ID,
            STACK_ID, INPUTS, STACKS);
    }
    @Test void realPinnedFuelPublicShapeRetainsAnEmptyOutputEntry() throws Exception {
        Recipe fuel = new Recipe("emi:fuel_coal", new Category("emi:fuel"), List.of(),
            List.of(new Ingredient(List.of(new Stack("minecraft:coal")))));
        JsonObject row = project(FUEL, fuel);
        assertEquals("public_recipe_id", row.get("identity_kind").getAsString());
        assertEquals("emi:fuel_coal", row.get("recipe_id").getAsString());
        assertEquals(0, row.getAsJsonArray("output_ids").size());
        // Production serialization, not an independently retyped JSON example.
        byte[] bytes = EmiPublicObservation.serializeInventory("a".repeat(64),
            List.of("minecraft:stone", "minecraft:stone"), List.of(row, row));
        JsonObject document = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("bc.playtest_emi_public_index.v2", document.get("schema").getAsString());
        assertEquals(2, document.getAsJsonArray("index_ids").size());
        assertEquals(2, document.getAsJsonArray("recipes").size());
        assertEquals(row, document.getAsJsonArray("recipes").get(0));
        assertTrue(document.getAsJsonArray("recipes").get(0).getAsJsonObject()
            .get("projection_sha256").isJsonNull());
    }
    @Test void publicSyntheticIngredientAlternativesProduceDeterministicAnonymousIdentity() throws Exception {
        Recipe left = new Recipe(null, new Category("emi:ingredient"), List.of(),
            List.of(new Ingredient(List.of(new Stack("minecraft:stone"), new Stack("minecraft:granite")))));
        Recipe right = new Recipe(null, new Category("emi:ingredient"), List.of(),
            List.of(new Ingredient(List.of(new Stack("minecraft:granite"), new Stack("minecraft:stone")))));
        JsonObject row = project(SYNTHETIC, left);
        assertEquals(row, project(SYNTHETIC, right));
        assertEquals("anonymous_public_projection", row.get("identity_kind").getAsString());
        assertTrue(row.get("recipe_id").isJsonNull());
        assertTrue(row.get("projection_sha256").getAsString().matches("[0-9a-f]{64}"));
        assertEquals("minecraft:granite", row.getAsJsonArray("input_alternative_ids").get(0).getAsString());
        Recipe distinct = new Recipe(null, left.category(), List.of(),
            List.of(new Ingredient(List.of(new Stack("minecraft:coal")))));
        assertTrue(!row.equals(project(SYNTHETIC, distinct)));
        JsonObject serialized = JsonParser.parseString(new String(EmiPublicObservation.serializeInventory("a".repeat(64),
            List.of("minecraft:stone"), List.of(row, row)), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, serialized.getAsJsonArray("recipes").size());
        assertEquals(row, serialized.getAsJsonArray("recipes").get(0));
        assertTrue(serialized.getAsJsonArray("recipes").get(0).getAsJsonObject().get("recipe_id").isJsonNull());
        // Independent declared projection input: enabling explicit null wire fields must not
        // change the existing anonymous identity hash, whose projection list contains no nulls.
        String expectedProjection = "[\"bc.playtest_emi_public_projection.v1\","
            + "\"dev.emi.emi.recipe.EmiSyntheticIngredientRecipe\",\"emi:ingredient\","
            + "[\"minecraft:granite\",\"minecraft:stone\"],[]]";
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(expectedProjection.getBytes(StandardCharsets.UTF_8))), row.get("projection_sha256").getAsString());
    }
    /** Runs only in the explicit test-phase bridge, never as a compile-time fixture generator. */
    @Test @Tag("emi-producer-consumer") void producerConsumerFixture() throws Exception {
        String requested = System.getProperty("bc.emi.contract.output");
        if (requested == null || requested.isBlank()) throw new IllegalStateException("explicit EMI test-phase output property required");
        Path output = Path.of(requested).toAbsolutePath().normalize();
        Path nativeBuild = Path.of("build").toAbsolutePath().normalize();
        if (!output.startsWith(nativeBuild) || !output.toString().endsWith(".json") ||
                !Files.isDirectory(output.getParent()) || Files.exists(output))
            throw new IllegalStateException("EMI producer output must be fresh under native build");
        Path envelopeFile = output.resolveSibling(output.getFileName() + ".envelope.json");
        if (Files.exists(envelopeFile)) throw new IllegalStateException("EMI producer envelope already exists");
        JsonObject fuel = project(FUEL, new Recipe("emi:fuel_coal", new Category("emi:fuel"), List.of(),
            List.of(new Ingredient(List.of(new Stack("minecraft:coal"))))));
        JsonObject shaped = project("dev.emi.emi.recipe.EmiShapedRecipe", new Recipe("minecraft:stone",
            new Category("minecraft:crafting"), List.of(new Stack("minecraft:stone")), List.of()));
        JsonObject synthetic = project(SYNTHETIC, new Recipe(null, new Category("emi:ingredient"), List.of(),
            List.of(new Ingredient(List.of(new Stack("minecraft:stone"), new Stack("minecraft:granite"))))));
        String sha = "a".repeat(64);
        byte[] bytes = EmiPublicObservation.serializeInventory(sha,
            List.of("minecraft:stone", "minecraft:stone"), List.of(fuel, fuel, shaped, synthetic));
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema", "bc.playtest_emi_contract_envelope.v1");
        envelope.addProperty("source", "native_test_production_public_projection_seam_not_live_client");
        envelope.addProperty("file", output.toString());
        envelope.addProperty("sha256", ClientNativeSupport.sha256(bytes));
        envelope.addProperty("bytes", bytes.length);
        envelope.addProperty("index_count", 2);
        envelope.addProperty("recipe_count", 4);
        envelope.addProperty("emi_jar_sha256", sha);
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW);
        Files.writeString(envelopeFile, envelope.toString() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    @Test void unknownProvidersAndMalformedPublicIdsFailClosedRatherThanDisappearing() {
        Recipe fuel = new Recipe("emi:fuel_coal", new Category("emi:fuel"), List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> project("external.UnknownFuel", fuel));
        assertThrows(IllegalStateException.class, () -> project(FUEL, new Recipe(null, fuel.category(), List.of(), List.of())));
        assertThrows(IllegalStateException.class, () -> project(FUEL,
            new Recipe("not:a valid id", fuel.category(), List.of(), List.of())));
        assertThrows(IllegalStateException.class, () -> project(SYNTHETIC,
            new Recipe(null, new Category("emi:ingredient"), List.of(), List.of())));
        assertThrows(IllegalStateException.class, () -> project(SYNTHETIC,
            new Recipe(null, new Category("emi:ingredient"), List.of(),
                List.of(new Ingredient(List.of(new Stack("invalid stack id")))))));
        assertThrows(IllegalStateException.class, () -> project("external.UnknownAnonymous",
            new Recipe(null, new Category("emi:ingredient"), List.of(),
                List.of(new Ingredient(List.of(new Stack("minecraft:stone")))))));
    }
}
