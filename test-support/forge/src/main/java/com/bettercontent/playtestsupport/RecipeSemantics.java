package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Bounded read-only effective recipe semantics; incomplete families remain visible, never inferred from wire bytes. */
final class RecipeSemantics {
    record Result(JsonObject row, boolean complete) {}
    private static final int MAX_ALTERNATIVES = 256, MAX_SLOTS = 81, MAX_JSON_CHARS = 16_384;
    private static final String ALLOY_CLASS = "slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe";
    private static final String CASTING_CLASS = "slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe";
    private static final String TCON_SHA256 = "653b49d73481a1325ba78adc7273dee6c765a51bafdf264a7510576d6ef91c43";
    private static final String MANTLE_SHA256 = "6052e47c3981064bd5213728eaa3c5418289f6f85b07a1cd4dd1505239248838";
    private RecipeSemantics() {}

    static final class ProviderPins {
        private boolean checked;
        private String failure;
        private JsonArray identities;
        void requirePinned() throws Exception {
            if (!checked) {
                checked = true;
                try {
                    identities = new JsonArray();
                    identities.add(verifyJar("tconstruct", "TConstruct-1.20.1-3.11.2.166.jar", "3.11.2.166", TCON_SHA256));
                    identities.add(verifyJar("mantle", "Mantle-1.20.1-1.11.104.jar", "1.11.104", MANTLE_SHA256));
                } catch (Exception error) { failure = error.getClass().getSimpleName() + ":" + error.getMessage(); }
            }
            if (failure != null) throw new IllegalStateException("reviewed build-71 TConstruct/Mantle public API pin mismatch: " + failure);
        }
        JsonArray identities() { return identities.deepCopy(); }
        private static JsonObject verifyJar(String modId, String filename, String version, String expected) throws Exception {
            var fileInfo = ModList.get().getModFileById(modId);
            if (fileInfo == null) throw new IllegalStateException("loaded mod absent: " + modId);
            Path installed = fileInfo.getFile().getFilePath();
            if (Files.isSymbolicLink(installed)) throw new IOException("loaded mod file is symlink: " + modId);
            Path path = installed.toRealPath();
            if (!filename.equals(path.getFileName().toString()) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) < 1 || Files.size(path) > 50_000_000)
                throw new IOException("loaded mod file name/size unsafe or not reviewed: " + modId);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            if (!actual.equals(expected)) throw new IllegalStateException("loaded mod SHA-256 differs: " + modId);
            JsonObject identity = new JsonObject(); identity.addProperty("mod_id", modId);
            identity.addProperty("version", version); identity.addProperty("artifact", filename);
            identity.addProperty("actual_jar_sha256", actual);
            identity.addProperty("identity_source", "loaded_ModList_ModFile_path_SHA256_verified_against_build71_server_artifact");
            return identity;
        }
    }

    static boolean withinAlternativeBudget(int count) { return count >= 0 && count <= MAX_ALTERNATIVES; }
    static boolean withinSlotBudget(int count) { return count > 0 && count <= MAX_SLOTS; }
    static boolean withinValueBudget(String value) { return value != null && value.length() <= MAX_JSON_CHARS; }
    static String boundedExportErrorDetail(String detail) {
        return detail == null ? null : detail.substring(0, Math.min(160, detail.length()));
    }
    static boolean ingredientIsPublicVanilla(boolean vanilla) { return vanilla; }

    /** Exact serializer AND implementation allowlist. Unknown/special/dynamic recipes never inherit vanilla semantics. */
    static String family(String serializer, String implementation, String id, boolean special) {
        // TConstruct's pinned ICommonRecipe reports isSpecial=true to suppress recipe-book
        // behavior; the exact public model below is still exportable under verified jar pins.
        if (id.startsWith("kubejs:hand_workshop/") && "tconstruct:alloy".equals(serializer)
                && ALLOY_CLASS.equals(implementation)) return "tconstruct_alloy";
        if (id.startsWith("kubejs:hand_workshop/") && Set.of("tconstruct:casting_table", "tconstruct:casting_basin")
                .contains(serializer) && CASTING_CLASS.equals(implementation)) return "tconstruct_casting";
        if (special) return "unsupported_special_or_dynamic";
        if ("minecraft:crafting_shaped".equals(serializer)
                && ShapedRecipe.class.getName().equals(implementation)) return "shaped";
        if ("minecraft:crafting_shapeless".equals(serializer)
                && ShapelessRecipe.class.getName().equals(implementation)) return "shapeless";
        if (Set.of("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking")
                .contains(serializer) && ("net.minecraft.world.item.crafting." + switch (serializer) {
                    case "minecraft:smelting" -> "SmeltingRecipe";
                    case "minecraft:blasting" -> "BlastingRecipe";
                    case "minecraft:smoking" -> "SmokingRecipe";
                    default -> "CampfireCookingRecipe";
                }).equals(implementation)) return serializer.substring("minecraft:".length());
        if ("minecraft:stonecutting".equals(serializer)
                && StonecutterRecipe.class.getName().equals(implementation)) return "stonecutting";
        if ("minecraft:smithing_transform".equals(serializer) || "minecraft:smithing_trim".equals(serializer))
            return "unsupported_smithing_private_ingredients";
        return "unsupported_unmapped_serializer_or_provider";
    }

    static Result capture(Recipe<?> recipe, RegistryAccess access, ProviderPins pins) {
        JsonObject row = new JsonObject(); row.addProperty("id", recipe.getId().toString());
        String impl = recipe.getClass().getName(); row.addProperty("provider_class", impl);
        row.addProperty("source_class", impl);
        try {
            ResourceLocation serializerId = ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer());
            if (serializerId == null) throw new IllegalStateException("serializer registry identity absent");
            String serializer = serializerId.toString(); row.addProperty("serializer", serializer);
            row.addProperty("serializer_class", recipe.getSerializer().getClass().getName());
            ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            row.addProperty("type", typeId == null ? "unregistered" : typeId.toString());
            boolean special = recipe.isSpecial(); row.addProperty("special", special);
            String family = family(serializer, impl, recipe.getId().toString(), special);
            row.addProperty("family", family);
            if (family.startsWith("unsupported_")) return unsupported(row, family);
            if (family.startsWith("tconstruct_")) {
                pins.requirePinned();
                JsonArray artifacts = pins.identities();
                artifacts.get(0).getAsJsonObject().addProperty("public_recipe_source_class", impl);
                artifacts.get(1).getAsJsonObject().addProperty("public_fluid_ingredient_source_class",
                        "slimeknights.mantle.recipe.ingredient.FluidIngredient");
                row.add("provider_artifacts", artifacts);
                return family.equals("tconstruct_alloy") ? alloy(recipe, row) : casting(recipe, row, serializer);
            }
            row.addProperty("group", recipe.getGroup());
            if (recipe instanceof ShapedRecipe shaped) {
                int width = shaped.getWidth(), height = shaped.getHeight();
                if (width < 1 || height < 1 || width > 9 || height > 9 || !withinSlotBudget(width * height)
                        || shaped.getIngredients().size() != width * height)
                    return unsupported(row, "invalid_or_oversized_shaped_dimensions");
                row.addProperty("width", width); row.addProperty("height", height);
                row.addProperty("mirrored_allowed", true);
                row.addProperty("category", shaped.category().toString());
                row.addProperty("show_notification", shaped.showNotification());
                row.addProperty("station", width > 2 || height > 2 ? "minecraft:crafting_table" :
                        "minecraft:player_inventory_or_crafting_table");
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                row.addProperty("category", shapeless.category().toString());
                row.addProperty("ingredient_order_matters", false);
                row.addProperty("station", shapeless.getIngredients().size() > 4 ? "minecraft:crafting_table" :
                        "minecraft:player_inventory_or_crafting_table");
            } else if (recipe instanceof AbstractCookingRecipe cooking) {
                row.addProperty("station", switch (family) {
                    case "smelting" -> "minecraft:furnace";
                    case "blasting" -> "minecraft:blast_furnace";
                    case "smoking" -> "minecraft:smoker";
                    default -> "minecraft:campfire";
                });
                row.addProperty("category", cooking.category().toString());
                row.addProperty("cooking_time_ticks", cooking.getCookingTime());
                row.addProperty("experience", cooking.getExperience());
                row.addProperty("heat_requirement", "station_heat_or_fuel_runtime; recipe_temperature_not_declared");
            } else if (recipe instanceof StonecutterRecipe) row.addProperty("station", "minecraft:stonecutter");
            else return unsupported(row, "allowlisted_serializer_with_unexpected_class");
            row.addProperty("tool_requirement", "not_declared_by_vanilla_recipe_interface; ingredient_remainders_unverified");
            row.add("inputs", inputs(recipe.getIngredients()));
            row.add("output", item(recipe.getResultItem(access)));
            return finish(row);
        } catch (Exception error) {
            // A typed diagnostic, never a recovered definition or a completeness upgrade.
            String detail = boundedExportErrorDetail(error.getMessage());
            if (detail != null) row.addProperty("export_error_detail", detail);
            return unsupported(row, "public_semantic_export_error:" + error.getClass().getSimpleName());
        }
    }

    private static Result alloy(Recipe<?> recipe, JsonObject row) throws Exception {
        Object inputValue = invoke(recipe, "getInputs");
        if (!(inputValue instanceof List<?> inputs) || inputs.isEmpty() || inputs.size() > MAX_SLOTS)
            return unsupported(row, "alloy_input_list_missing_or_oversized");
        JsonArray json = new JsonArray();
        for (Object entry : inputs) {
            JsonObject one = fluidIngredient(invoke(entry, "fluid"));
            one.addProperty("catalyst", (Boolean) invoke(entry, "catalyst")); json.add(one);
        }
        row.add("fluid_inputs", json); row.add("output", fluidStack((FluidStack) invoke(recipe, "getOutput"))); row.addProperty("temperature", (Integer) invoke(recipe, "getTemperature"));
        row.addProperty("station", "tconstruct:alloying"); row.addProperty("station_reachability", "unverified");
        row.addProperty("tool_requirement", "not_declared_by_public_alloy_recipe");
        // Public getOutput gives a resolved preview. FluidOutput's source tag/alternatives
        // have no public getter on AlloyRecipe; do not mislabel this as a complete definition.
        return unsupported(row, "alloy_output_definition_not_public; resolved_output_preview_only");
    }
    private static Result casting(Recipe<?> recipe, JsonObject row, String serializer) throws Exception {
        row.addProperty("station", serializer); row.addProperty("station_reachability", "unverified");
        JsonObject cast = ingredient((Ingredient) invoke(recipe, "getCast"), 0);
        cast.addProperty("consumed", (Boolean) invoke(recipe, "isConsumed"));
        row.add("cast", cast);
        row.add("fluid_input", fluidIngredient(invoke(recipe, "getFluid")));
        Object result = invoke(recipe, "getResult");
        if (invoke(result, "getTag") != null) return unsupported(row, "casting_dynamic_output_tag_not_exhaustively_exported");
        JsonElement definition = (JsonElement) invokePublic(result, "serialize", new Class<?>[]{boolean.class}, false);
        requireBounded(definition.toString()); row.add("output_definition", definition);
        row.add("output", item((ItemStack) invoke(recipe, "getOutput")));
        row.addProperty("cooling_time_ticks", (Integer) invoke(recipe, "getCoolingTime"));
        row.addProperty("heat_requirement", "fluid_processing_station; recipe_temperature_not_declared");
        return finish(row);
    }
    private static JsonArray inputs(List<Ingredient> ingredients) {
        if (!withinSlotBudget(ingredients.size()))
            throw new IllegalStateException("ingredient slot list missing_or_oversized");
        JsonArray array = new JsonArray();
        for (int i = 0; i < ingredients.size(); i++) array.add(ingredient(ingredients.get(i), i));
        return array;
    }
    private static JsonObject ingredient(Ingredient input, int index) {
        if (input == null || !ingredientIsPublicVanilla(input.isVanilla()))
            throw new IllegalStateException("custom ingredient predicate not exhaustively public");
        JsonObject row = new JsonObject(); row.addProperty("slot", index);
        JsonElement raw = input.toJson(); requireBounded(raw.toString()); row.add("raw_ingredient", raw);
        ItemStack[] alternatives = input.getItems();
        if (!withinAlternativeBudget(alternatives.length)) throw new IllegalStateException("ingredient alternatives oversized");
        JsonArray choices = new JsonArray();
        List<JsonObject> rows = new ArrayList<>();
        for (ItemStack stack : alternatives) rows.add(item(stack));
        rows.stream().sorted(Comparator.comparing(JsonObject::toString)).forEach(choices::add);
        row.add("alternatives", choices); row.addProperty("empty", input.isEmpty());
        if (!input.isEmpty() && alternatives.length == 0) throw new IllegalStateException("nonempty ingredient has no resolved alternatives");
        return row;
    }
    private static JsonObject fluidIngredient(Object input) throws Exception {
        JsonObject row = new JsonObject();
        JsonElement definition = (JsonElement) invoke(input, "serialize");
        requireBounded(definition.toString()); row.add("raw_ingredient", definition);
        Object value = invoke(input, "getFluids");
        if (!(value instanceof List<?> fluids) || fluids.isEmpty() || fluids.size() > MAX_ALTERNATIVES)
            throw new IllegalStateException("fluid ingredient alternatives absent_or_oversized");
        JsonArray alternatives = new JsonArray();
        List<JsonObject> rows = new ArrayList<>();
        for (Object fluid : fluids) rows.add(fluidStack((FluidStack) fluid));
        rows.stream().sorted(Comparator.comparing(JsonObject::toString)).forEach(alternatives::add);
        row.add("alternatives", alternatives);
        return row;
    }
    private static JsonObject item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalStateException("empty static item output/alternative");
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) throw new IllegalStateException("unregistered item in effective recipe");
        JsonObject row = new JsonObject(); row.addProperty("kind", "item");
        row.addProperty("item", key.toString()); row.addProperty("count", stack.getCount());
        if (stack.getTag() != null) { String nbt = stack.getTag().toString(); requireBounded(nbt); row.addProperty("nbt", nbt); }
        // Raw full saved-stack SNBT includes Count/ForgeCaps; NOT the separately proposed
        // canonical typed playerdata stack identity hash or natural-acquisition proof.
        String saved = stack.save(new CompoundTag()).toString(); requireBounded(saved);
        row.addProperty("stack_nbt", saved);
        return row;
    }
    private static JsonObject fluidStack(FluidStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalStateException("empty fluid output/alternative");
        Fluid fluid = stack.getFluid(); ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id == null) throw new IllegalStateException("unregistered fluid in effective recipe");
        JsonObject row = new JsonObject(); row.addProperty("kind", "fluid");
        row.addProperty("fluid", id.toString()); row.addProperty("amount", stack.getAmount());
        if (stack.getTag() != null) { String nbt = stack.getTag().toString(); requireBounded(nbt); row.addProperty("nbt", nbt); }
        return row;
    }
    private static Object invoke(Object source, String method) throws Exception {
        return invokePublic(source, method, new Class<?>[0]);
    }
    /** Pinned Mantle returns package-private implementation objects of public ingredient/output APIs.
     * Invoke only their genuinely public inherited declaration, never setAccessible or a hidden member. */
    static Object invokePublic(Object source, String method, Class<?>[] signature, Object... arguments) throws Exception {
        if (source == null) throw new IllegalStateException("missing public recipe value");
        Method accessor = source.getClass().getMethod(method, signature);
        if (!Modifier.isPublic(accessor.getDeclaringClass().getModifiers())) {
            Class<?> parent = source.getClass().getSuperclass();
            while (parent != null && !Modifier.isPublic(parent.getModifiers())) parent = parent.getSuperclass();
            if (parent == null) throw new IllegalAccessException("no public recipe API for " + method);
            accessor = parent.getMethod(method, signature);
        }
        return accessor.invoke(source, arguments);
    }
    private static void requireBounded(String jsonOrNbt) {
        if (!withinValueBudget(jsonOrNbt)) throw new IllegalStateException("semantic value exceeds bounded size");
    }
    private static Result finish(JsonObject row) {
        row.addProperty("semantics_complete", true);
        row.addProperty("provenance", "effective_server_recipe_public_semantics_not_natural_acquisition");
        requireBounded(row.toString());
        return new Result(row, true);
    }
    private static Result unsupported(JsonObject row, String reason) {
        row.addProperty("semantics_complete", false); row.addProperty("unsupported_reason", reason);
        row.addProperty("provenance", "effective_server_recipe_partial_only_not_natural_acquisition");
        if (!withinValueBudget(row.toString())) {
            JsonObject bounded = new JsonObject();
            for (String key : List.of("id", "type", "serializer", "provider_class", "source_class", "family"))
                if (row.has(key)) bounded.add(key, row.get(key));
            bounded.addProperty("semantics_complete", false);
            bounded.addProperty("unsupported_reason", "semantic_row_oversized; " + reason);
            bounded.addProperty("provenance", "effective_server_recipe_partial_only_not_natural_acquisition");
            return new Result(bounded, false);
        }
        return new Result(row, false);
    }
}
