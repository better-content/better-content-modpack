package com.bettercontent.playtestsupport.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Only pinned EMI 1.1.24 public EmiApi, EmiRecipeManager, EmiRecipe and EmiStack methods; never internal cache access. */
final class EmiPublicObservation {
    // The closed v2 wire requires explicit null recipe/projection identities, not omitted keys.
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    // Pinned EMI 1.1.24 provides no recipe ID for its public synthetic ingredient recipes.
    // All other anonymous providers remain unsupported: never omit or invent an ordinal ID.
    private static final String SYNTHETIC_INGREDIENT = "dev.emi.emi.recipe.EmiSyntheticIngredientRecipe";
    private static final String FUEL = "dev.emi.emi.recipe.EmiFuelRecipe";
    private static final String INFO = "dev.emi.emi.api.recipe.EmiInfoRecipe";
    private EmiPublicObservation() {}

    private static String resource(Object id, String source) {
        if (id == null || id.toString().length() > 512 || !RESOURCE.matcher(id.toString()).matches())
            throw new IllegalStateException(source + " lacks a canonical public resource ID");
        return id.toString();
    }

    /** Pinned public projection, never a synthetic recipe ID. Every occurrence stays in the multiset. */
    static JsonObject projectRecipe(Object recipe, String provider, Method recipeId, Method recipeCategory, Method recipeOutputs,
            Method categoryId, Method stackId, Method recipeInputs, Method ingredientStacks) throws Exception {
        if (recipe == null || provider == null || !provider.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
            throw new IllegalStateException("EMI recipe provider absent or malformed");
        String category = resource(categoryId.invoke(recipeCategory.invoke(recipe)), "EMI recipe category");
        Object outputs = recipeOutputs.invoke(recipe);
        if (!(outputs instanceof List<?> out) || out.size() > 100_000)
            throw new IllegalStateException("EMI recipe lacks bounded public output list");
        if (out.isEmpty() && !provider.equals(FUEL) && !provider.equals(INFO) && !provider.equals(SYNTHETIC_INGREDIENT))
            throw new IllegalStateException("unsupported empty-output EMI recipe provider " + provider);
        List<String> outputIds = new ArrayList<>(out.size());
        for (Object stack : out) outputIds.add(resource(stackId.invoke(stack), "EMI recipe output"));
        Collections.sort(outputIds);
        Object id = recipeId.invoke(recipe);
        List<String> inputIds = new ArrayList<>();
        if (id == null) {
            if (!provider.equals(SYNTHETIC_INGREDIENT) || !category.equals("emi:ingredient"))
                throw new IllegalStateException("unsupported anonymous EMI recipe provider " + provider);
            Object inputs = recipeInputs.invoke(recipe);
            if (!(inputs instanceof List<?> ingredients) || ingredients.isEmpty() || ingredients.size() > 100_000)
                throw new IllegalStateException("anonymous EMI ingredient recipe lacks public inputs");
            for (Object ingredient : ingredients) {
                Object stacks = ingredientStacks.invoke(ingredient);
                if (!(stacks instanceof List<?> alternatives) || alternatives.isEmpty() ||
                        alternatives.size() > 100_000 || inputIds.size() + alternatives.size() > 100_000)
                    throw new IllegalStateException("anonymous EMI ingredient recipe has empty public alternatives");
                for (Object stack : alternatives) inputIds.add(resource(stackId.invoke(stack), "EMI ingredient alternative"));
            }
            Collections.sort(inputIds);
        }
        JsonObject row = new JsonObject();
        row.addProperty("identity_kind", id == null ? "anonymous_public_projection" : "public_recipe_id");
        if (id == null) row.add("recipe_id", com.google.gson.JsonNull.INSTANCE);
        else row.addProperty("recipe_id", resource(id, "EMI recipe"));
        row.addProperty("provider_class", provider);
        row.addProperty("category_id", category);
        JsonArray outputArray = new JsonArray(); outputIds.forEach(outputArray::add); row.add("output_ids", outputArray);
        JsonArray inputArray = new JsonArray(); inputIds.forEach(inputArray::add); row.add("input_alternative_ids", inputArray);
        // Public stack IDs alone cannot distinguish NBT/amount variants; do not claim recipe identity.
        if (id == null) row.addProperty("projection_sha256", ClientNativeSupport.sha256(
            JSON.toJson(List.of("bc.playtest_emi_public_projection.v1", provider, category, inputIds, outputIds))
                .getBytes(StandardCharsets.UTF_8)));
        else row.add("projection_sha256", com.google.gson.JsonNull.INSTANCE);
        if (JSON.toJson(row).length() > 32_768)
            throw new IllegalStateException("EMI public recipe projection exceeds bounded row size");
        return row;
    }

    static String recipeSortKey(JsonObject row) {
        return row.get("identity_kind").getAsString() + "|" + row.get("recipe_id") + "|" +
            row.get("provider_class").getAsString() + "|" + row.get("category_id").getAsString() + "|" +
            row.get("input_alternative_ids") + "|" + row.get("output_ids");
    }

    /** Exactly the same bounded bytes are used by capture and by test-phase producer fixtures. */
    static byte[] serializeInventory(String emiSha, List<String> indexIds, List<JsonObject> recipeRecords) {
        if (!emiSha.matches("[0-9a-f]{64}") || indexIds.isEmpty() || recipeRecords.isEmpty() ||
                indexIds.size() > 100_000 || recipeRecords.size() > 100_000)
            throw new IllegalStateException("EMI projection absent or outside reviewed budget");
        List<String> items = new ArrayList<>(indexIds);
        List<JsonObject> recipes = new ArrayList<>(recipeRecords);
        Collections.sort(items);
        recipes.sort(Comparator.comparing(EmiPublicObservation::recipeSortKey));
        JsonObject inventory = new JsonObject();
        inventory.addProperty("schema", "bc.playtest_emi_public_index.v2");
        inventory.addProperty("emi_jar_sha256", emiSha);
        JsonArray ids = new JsonArray(); items.forEach(ids::add); inventory.add("index_ids", ids);
        JsonArray entries = new JsonArray(); recipes.forEach(entries::add); inventory.add("recipes", entries);
        byte[] bytes = (JSON.toJson(inventory) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32_000_000) throw new IllegalStateException("EMI public index exceeds bounded export budget");
        return bytes;
    }

    static String requirePinnedJar() throws Exception {
        String expected = System.getProperty("bc.playtest.client.emiSha256", "");
        if (!expected.matches("[0-9a-f]{64}")) throw new IllegalStateException("reviewed EMI JAR SHA absent");
        var modFile = ModList.get().getModFileById("emi");
        if (modFile == null || modFile.getFile() == null) throw new IllegalStateException("EMI mod not loaded from a reviewed file");
        Path jar = modFile.getFile().getFilePath().toRealPath();
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(jar) ||
                Files.size(jar) < 1 || Files.size(jar) > 50_000_000 ||
                !ClientNativeSupport.sha256(Files.readAllBytes(jar)).equals(expected))
            throw new IllegalStateException("loaded EMI public API does not match reviewed candidate artifact");
        return expected;
    }
    static JsonObject capture(Path directory, long sequence) throws Exception {
        String expected = requirePinnedJar();
        Class<?> api = Class.forName("dev.emi.emi.api.EmiApi");
        Object manager = api.getMethod("getRecipeManager").invoke(null);
        if (manager == null) throw new IllegalStateException("EMI recipe manager not loaded");
        Class<?> managerApi = Class.forName("dev.emi.emi.api.recipe.EmiRecipeManager");
        Class<?> recipeApi = Class.forName("dev.emi.emi.api.recipe.EmiRecipe");
        Class<?> categoryApi = Class.forName("dev.emi.emi.api.recipe.EmiRecipeCategory");
        Method categoryId = categoryApi.getMethod("getId");
        Class<?> stackApi = Class.forName("dev.emi.emi.api.stack.EmiStack");
        Method stackId = stackApi.getMethod("getId");
        Method recipeId = recipeApi.getMethod("getId"), recipeCategory = recipeApi.getMethod("getCategory"),
            recipeOutputs = recipeApi.getMethod("getOutputs"), recipeInputs = recipeApi.getMethod("getInputs");
        Class<?> ingredientApi = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
        Method ingredientStacks = ingredientApi.getMethod("getEmiStacks");
        Object stackList = api.getMethod("getIndexStacks").invoke(null);
        Object recipeList = managerApi.getMethod("getRecipes").invoke(manager);
        if (!(stackList instanceof List<?> stacks) || !(recipeList instanceof List<?> recipes) ||
                stacks.isEmpty() || recipes.isEmpty() || stacks.size() > 100_000 || recipes.size() > 100_000)
            throw new IllegalStateException("missing/oversized actual EMI public index or recipes");
        List<String> items = new ArrayList<>(stacks.size());
        for (Object stack : stacks) {
            items.add(resource(stackId.invoke(stack), "EMI index stack"));
        }
        List<JsonObject> recipesOut = new ArrayList<>(recipes.size());
        for (Object recipe : recipes)
            recipesOut.add(projectRecipe(recipe, recipe.getClass().getName(), recipeId, recipeCategory, recipeOutputs,
                categoryId, stackId, recipeInputs, ingredientStacks));
        byte[] bytes = serializeInventory(expected, items, recipesOut);
        Path file = directory.resolve("emi-public-%012d.json".formatted(sequence));
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
        JsonObject result = new JsonObject();
        result.addProperty("complete", true);
        result.addProperty("source", "pinned dev.emi.emi.api.EmiApi:getIndexStacks/getRecipeManager");
        result.addProperty("index_count", items.size()); result.addProperty("recipe_count", recipesOut.size());
        result.addProperty("file", file.toString());
        result.addProperty("file_sha256", ClientNativeSupport.sha256(bytes));
        result.addProperty("file_bytes", bytes.length);
        result.addProperty("emi_jar_sha256", expected);
        return result;
    }
}
