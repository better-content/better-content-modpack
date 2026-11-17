package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit effective-source domains. Completeness applies per domain, not to arbitrary mod integrations. */
final class EffectiveInventory {
    record Domain(List<String> rows, boolean membershipComplete, boolean definitionComplete,
                  int expectedMembers, int unsupportedRows, String source, String limitation) {
        Domain {
            rows = List.copyOf(rows);
            if (membershipComplete) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                boolean valid = expectedMembers == rows.size();
                for (String text : rows) {
                    try {
                        JsonObject row = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                        valid &= row.has("id") && row.get("id").isJsonPrimitive()
                                && row.get("id").getAsJsonPrimitive().isString()
                                && seen.add(row.get("id").getAsString());
                    } catch (Exception error) { valid = false; }
                }
                membershipComplete = valid;
            }
        }
        Domain(List<String> rows, boolean definitionComplete, String source, String limitation) {
            this(rows, true, definitionComplete, rows.size(), 0, source, limitation);
        }
        boolean complete() { return membershipComplete && definitionComplete && unsupportedRows == 0; }
    }
    private EffectiveInventory() {}

    static boolean globallyComplete(Map<String, Domain> domains, List<String> unsupportedProviders) {
        return !domains.isEmpty() && unsupportedProviders.isEmpty() &&
                domains.values().stream().allMatch(Domain::complete);
    }

    static Map<String, Domain> capture(MinecraftServer server) {
        Map<String, Domain> domains = new LinkedHashMap<>();
        domains.put("items", EffectiveDomainCapture.items());
        domains.put("blocks", EffectiveDomainCapture.blocks());
        domains.put("entity_types", EffectiveDomainCapture.entities());
        domains.put("fluids", EffectiveDomainCapture.fluids());
        domains.put("equipment", EffectiveDomainCapture.equipment());
        domains.put("configured_features", EffectiveDomainCapture.configuredFeatures(server.registryAccess()));
        domains.put("placed_features", EffectiveDomainCapture.placedFeatures(server.registryAccess()));
        List<String> recipes = new ArrayList<>(), recipeWire = new ArrayList<>(), recipeSemantics = new ArrayList<>();
        int[] wireErrors = {0}, semanticGaps = {0}, definitionGaps = {0}, identityGaps = {0};
        RecipeSemantics.ProviderPins providerPins = new RecipeSemantics.ProviderPins();
        server.getRecipeManager().getRecipes().forEach(recipe -> {
            String id = null;
            try { id = recipe.getId().toString(); }
            catch (Exception error) { identityGaps[0]++; }
            JsonObject row = new JsonObject();
            if (id != null) row.addProperty("id", id);
            try {
                ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                ResourceLocation serializer = ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer());
                if (type == null || serializer == null || id == null)
                    throw new IllegalStateException("recipe registry identity absent");
                row.addProperty("type", type.toString()); row.addProperty("serializer", serializer.toString());
                com.google.gson.JsonArray ingredients = new com.google.gson.JsonArray();
                recipe.getIngredients().forEach(ingredient -> ingredients.add(ingredient.toJson()));
                row.add("effective_ingredients", ingredients);
                var output = recipe.getResultItem(server.registryAccess());
                ResourceLocation result = output.isEmpty() ? new ResourceLocation("minecraft", "air") :
                        ForgeRegistries.ITEMS.getKey(output.getItem());
                if (result == null) throw new IllegalStateException("recipe output registry identity absent");
                row.addProperty("result_item", result.toString());
                row.addProperty("result_count", output.getCount());
                row.addProperty("special", recipe.isSpecial());
                if (row.toString().length() > 65_536) throw new IllegalStateException("recipe public metadata oversized");
                row.addProperty("metadata_complete", true);
            } catch (Exception error) {
                definitionGaps[0]++;
                // Do not retain a partial ingredient/result claim after a failed getter.
                JsonObject failed = new JsonObject(); if (id != null) failed.addProperty("id", id);
                failed.addProperty("metadata_complete", false);
                failed.addProperty("unsupported_reason", "recipe_public_getter:" + error.getClass().getSimpleName());
                row = failed;
            }
            recipes.add(row.toString());
            try {
                if (id == null) throw new IllegalStateException("recipe identity absent");
                RecipeSemantics.Result semantics = RecipeSemantics.capture(recipe, server.registryAccess(), providerPins);
                recipeSemantics.add(semantics.row().toString());
                if (!semantics.complete()) semanticGaps[0]++;
            } catch (Exception error) {
                semanticGaps[0]++;
                JsonObject unsupported = new JsonObject(); if (id != null) unsupported.addProperty("id", id);
                unsupported.addProperty("semantics_complete", false);
                unsupported.addProperty("unsupported_reason", "recipe_semantics:" + error.getClass().getSimpleName());
                recipeSemantics.add(unsupported.toString());
            }
            JsonObject wire = new JsonObject(); if (id != null) wire.addProperty("id", id);
            ByteBuf raw = null;
            try {
                var serializer = recipe.getSerializer();
                ResourceLocation key = ForgeRegistries.RECIPE_SERIALIZERS.getKey(serializer);
                if (key == null || id == null) throw new IllegalStateException("recipe serializer identity absent");
                wire.addProperty("serializer", key.toString());
                raw = Unpooled.buffer(256, 1_048_576);
                @SuppressWarnings({"rawtypes", "unchecked"}) net.minecraft.world.item.crafting.RecipeSerializer unsafeSerializer = serializer;
                unsafeSerializer.toNetwork(new FriendlyByteBuf(raw), recipe);
                int count = raw.writerIndex();
                byte[] bytes = ByteBufUtil.getBytes(raw, 0, count);
                wire.addProperty("bytes", count);
                wire.addProperty("base64", java.util.Base64.getEncoder().encodeToString(bytes));
                wire.addProperty("sha256", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                if (wire.toString().length() > 1_400_000) throw new IllegalStateException("recipe wire row oversized");
                wire.addProperty("wire_complete", true);
            } catch (Exception error) {
                wireErrors[0]++;
                JsonObject failed = new JsonObject(); if (id != null) failed.addProperty("id", id);
                failed.addProperty("wire_complete", false);
                failed.addProperty("unsupported_reason", "recipe_wire:" + error.getClass().getSimpleName());
                wire = failed;
            } finally { if (raw != null) raw.release(); }
            recipeWire.add(wire.toString());
        });
        int recipeCount = server.getRecipeManager().getRecipes().size();
        domains.put("recipes", new Domain(recipes, recipes.size() == recipeCount && identityGaps[0] == 0,
                false, recipeCount, definitionGaps[0], "effective_server_recipe_manager",
                "Public ingredient/result view; custom serializer fields need typed adapter. Getter failures are per-row; full semantic definition unavailable."));
        domains.put("recipes_wire", new Domain(recipeWire, recipeWire.size() == recipes.size(), wireErrors[0] == 0,
                recipes.size(), wireErrors[0], "effective_RecipeSerializer_toNetwork",
                "Exact post-reload serializer network bytes, NOT guaranteed reversible full server-side semantics or natural craft acquisition; errors explicitly recorded."));
        domains.put("recipes_semantics", new Domain(recipeSemantics, recipeSemantics.size() == recipes.size(),
                !recipeSemantics.isEmpty() && semanticGaps[0] == 0, recipes.size(), semanticGaps[0],
                "effective_server_recipe_manager_pinned_public_recipe_api",
                "Per-row semantics_complete/unsupported_reason are authoritative; special, dynamic, smithing-private, unknown serializers and unrecoverable output definitions remain incomplete. Station/fuel/tool reachability and natural acquisition are NOT proven."));
        domains.put("item_tags", safeDomain(() -> tagDomain(BuiltInRegistries.ITEM, "effective_item_tag_registry",
                "Tag membership does not prove item acquisition."), "effective_item_tag_registry"));
        domains.put("block_tags", safeDomain(() -> tagDomain(BuiltInRegistries.BLOCK, "effective_block_tag_registry",
                "Tag membership does not prove generated blocks."), "effective_block_tag_registry"));
        try {
        var biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        List<String> biomes = new ArrayList<>(); int biomeGaps = 0;
        for (var entry : biomeRegistry.entrySet()) {
            JsonObject row = new JsonObject(); row.addProperty("id", entry.getKey().location().toString());
            try {
                var biome = entry.getValue();
                row.addProperty("base_temperature", biome.getBaseTemperature());
                row.addProperty("downfall", biome.getModifiedClimateSettings().downfall());
                row.addProperty("metadata_complete", true);
            } catch (Exception error) {
                biomeGaps++; row = failed(row, "biome_public_getter", error);
            }
            biomes.add(row.toString());
        }
        domains.put("biomes", new Domain(biomes, biomes.size() == biomeRegistry.entrySet().size(), biomeGaps == 0,
                biomeRegistry.entrySet().size(), biomeGaps, "effective_dynamic_biome_registry",
                "Biome metadata only; generated biome distribution unverified."));
        } catch (Exception error) { domains.put("biomes", unavailable("effective_dynamic_biome_registry", error)); }
        try {
        List<String> dimensions = new ArrayList<>(); int dimensionGaps = 0;
        for (var level : server.getAllLevels()) {
            JsonObject row = new JsonObject(); row.addProperty("id", level.dimension().location().toString());
            try {
                row.addProperty("min_y", level.getMinBuildHeight()); row.addProperty("max_y", level.getMaxBuildHeight());
                row.addProperty("sea_level", level.getSeaLevel()); row.addProperty("metadata_complete", true);
            } catch (Exception error) {
                dimensionGaps++; row = failed(row, "level_public_getter", error);
            }
            dimensions.add(row.toString());
        }
        domains.put("dimensions", new Domain(dimensions, dimensionGaps == 0,
                dimensionGaps == 0, dimensions.size(), dimensionGaps, "running_server_level_roster",
                "Only active loaded dimensions, not every possible registered dimension."));
        } catch (Exception error) { domains.put("dimensions", unavailable("running_server_level_roster", error)); }
        try {
        List<String> loot = new ArrayList<>(); int lootGaps = 0;
        var manager = server.getLootData();
        var lootKeys = manager.getKeys(LootDataType.TABLE);
        for (ResourceLocation id : lootKeys) {
            JsonObject row = new JsonObject(); row.addProperty("id", id.toString());
            try {
                LootTable table = manager.getElement(new LootDataId<>(LootDataType.TABLE, id));
                if (table == null) throw new IllegalStateException("loot table unresolved");
                row.add("effective_definition", LootDataType.TABLE.parser().toJsonTree(table));
                if (row.toString().length() > 65_536) throw new IllegalStateException("loot definition oversized");
                row.addProperty("metadata_complete", true);
            } catch (Exception error) {
                lootGaps++; row = failed(row, "loot_public_serializer", error);
            }
            loot.add(row.toString());
        }
        domains.put("loot_tables", new Domain(loot, loot.size() == lootKeys.size(), lootGaps == 0,
                lootKeys.size(), lootGaps, "effective_server_loot_data",
                "Loot definitions do not prove chests/contexts spawn or are reachable."));
        } catch (Exception error) { domains.put("loot_tables", unavailable("effective_server_loot_data", error)); }
        return domains;
    }

    private static Domain safeDomain(java.util.function.Supplier<Domain> capture, String source) {
        try { return capture.get(); }
        catch (Exception error) { return unavailable(source, error); }
    }
    private static Domain unavailable(String source, Exception error) {
        JsonObject row = new JsonObject(); row.addProperty("metadata_complete", false);
        row.addProperty("unsupported_reason", "membership_enumeration:" + error.getClass().getSimpleName());
        return new Domain(List.of(row.toString()), false, false, -1, 1, source,
                "Membership could not be enumerated; no rows may be treated as a complete roster.");
    }

    private static <T> Domain tagDomain(Registry<T> registry, String source, String limitation) {
        var keys = registry.getTagNames().sorted(Comparator.comparing(tag -> tag.location().toString())).toList();
        List<String> rows = new ArrayList<>(); int gaps = 0;
        for (var tag : keys) {
            JsonObject row = new JsonObject(); row.addProperty("id", tag.location().toString());
            try {
                var named = registry.getTag(tag).orElseThrow(() -> new IllegalStateException("tag unresolved"));
                List<String> ids = named.stream().map(holder -> registry.getKey(holder.value()))
                        .map(ResourceLocation::toString).sorted().toList();
                com.google.gson.JsonArray values = new com.google.gson.JsonArray(); ids.forEach(values::add);
                row.add("values", values);
                if (row.toString().length() > 65_536) throw new IllegalStateException("tag row oversized");
                row.addProperty("metadata_complete", true);
            } catch (Exception error) {
                gaps++; row = failed(row, "tag_public_getter", error);
            }
            rows.add(row.toString());
        }
        return new Domain(rows, rows.size() == keys.size(), gaps == 0, keys.size(), gaps, source, limitation);
    }

    private static JsonObject failed(JsonObject partial, String source, Exception error) {
        JsonObject row = new JsonObject();
        if (partial.has("id")) row.add("id", partial.get("id"));
        row.addProperty("metadata_complete", false);
        row.addProperty("unsupported_reason", source + ":" + error.getClass().getSimpleName());
        return row;
    }
}
