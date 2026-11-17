package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Registry membership and bounded public metadata are independent observations, never acquisition proof. */
final class EffectiveDomainCapture {
    private static final int MAX_ROW_CHARS = 65_536;
    private EffectiveDomainCapture() {}

    static EffectiveInventory.Domain items() {
        return registry("items", ForgeRegistries.ITEMS::getKeys, id -> {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) throw new IllegalStateException("unresolved item");
            JsonObject row = row(id, item.getClass().getName());
            row.addProperty("max_stack", item.getMaxStackSize());
            row.addProperty("max_damage", item.getMaxDamage());
            FoodProperties food = item.getFoodProperties(new ItemStack(item), null);
            if (food != null) {
                row.addProperty("nutrition", food.getNutrition());
                row.addProperty("saturation_modifier", food.getSaturationModifier());
                row.addProperty("always_edible", food.canAlwaysEat());
            }
            return row;
        }, true, "effective_forge_item_registry", "Static public metadata only; no natural acquisition or context-dependent food effect proof.");
    }

    static EffectiveInventory.Domain blocks() {
        return registry("blocks", ForgeRegistries.BLOCKS::getKeys, id -> {
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null) throw new IllegalStateException("unresolved block");
            JsonObject row = row(id, block.getClass().getName());
            row.addProperty("loot_table", block.getLootTable().toString());
            ResourceLocation item = ForgeRegistries.ITEMS.getKey(block.asItem());
            if (item == null) throw new IllegalStateException("block item registry identity absent");
            row.addProperty("item_id", item.toString());
            return row;
        }, true, "effective_forge_block_registry", "Static block metadata; no generation or obtainable-drop proof.");
    }

    static EffectiveInventory.Domain entities() {
        return registry("entity_types", ForgeRegistries.ENTITY_TYPES::getKeys, id -> {
            var entity = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (entity == null) throw new IllegalStateException("unresolved entity type");
            return row(id, entity.getClass().getName());
        }, true, "effective_forge_entity_registry", "Identity/class only; no reachable spawn proof.");
    }

    static EffectiveInventory.Domain fluids() {
        return registry("fluids", ForgeRegistries.FLUIDS::getKeys, id -> {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid == null) throw new IllegalStateException("unresolved fluid");
            JsonObject row = row(id, fluid.getClass().getName());
            var type = fluid.getFluidType();
            if (type == null) throw new IllegalStateException("fluid type absent");
            row.addProperty("fluid_type_class", type.getClass().getName());
            row.addProperty("static_density", type.getDensity());
            row.addProperty("static_viscosity", type.getViscosity());
            row.addProperty("static_temperature", type.getTemperature());
            return row;
        }, false, "effective_forge_fluid_registry_static_FluidType_getters",
                "Static FluidType values only; dynamic state, recipes, purity, container behavior and acquisition unverified.");
    }

    static EffectiveInventory.Domain equipment() {
        String source = "effective_forge_items_ArmorItem_TieredItem_public_metadata";
        String limitation = "Only ArmorItem/TieredItem static metadata; Curios/custom equipment, durability and actual gate semantics unverified.";
        final List<ResourceLocation> ids;
        try { ids = ForgeRegistries.ITEMS.getKeys().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList(); }
        catch (Exception error) { return unavailable(source, limitation, error); }
        List<ResourceLocation> selected = new ArrayList<>(); List<String> classificationFailures = new ArrayList<>();
        for (ResourceLocation id : ids) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) throw new IllegalStateException("unresolved item during equipment classification");
                if (item instanceof ArmorItem || item instanceof TieredItem) selected.add(id);
            } catch (Exception error) {
                JsonObject failed = row(id, "unknown_due_to_classification_error");
                failed.addProperty("metadata_complete", false);
                failed.addProperty("unsupported_reason", "equipment_classification:" + error.getClass().getSimpleName());
                classificationFailures.add(failed.toString());
            }
        }
        var measured = registry("equipment", () -> selected, id -> {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) throw new IllegalStateException("unresolved equipment item");
            JsonObject row = row(id, item.getClass().getName());
            if (item instanceof ArmorItem armor) {
                row.addProperty("kind", "armor");
                row.addProperty("armor_slot", armor.getType().getSlot().getName());
                row.addProperty("defense", armor.getDefense());
                row.addProperty("toughness", armor.getToughness());
                row.addProperty("material_class", armor.getMaterial().getClass().getName());
            } else if (item instanceof TieredItem tiered) {
                row.addProperty("kind", "tiered_item");
                row.addProperty("tier_level", tiered.getTier().getLevel());
                row.addProperty("tier_class", tiered.getTier().getClass().getName());
            } else throw new IllegalStateException("equipment classification drift");
            return row;
        }, false, source, limitation);
        List<String> rows = new ArrayList<>(measured.rows()); rows.addAll(classificationFailures);
        return new EffectiveInventory.Domain(rows, measured.membershipComplete() && classificationFailures.isEmpty(),
                false, selected.size() + classificationFailures.size(), measured.unsupportedRows() + classificationFailures.size(),
                source, limitation);
    }

    static EffectiveInventory.Domain configuredFeatures(RegistryAccess access) {
        return dynamic("configured_features", () -> access.registryOrThrow(Registries.CONFIGURED_FEATURE),
                feature -> {
                    JsonObject row = new JsonObject();
                    row.addProperty("feature_class", feature.feature().getClass().getName());
                    row.addProperty("configuration_class", feature.config().getClass().getName());
                    return row;
                }, "effective_dynamic_configured_feature_registry",
                "Membership and runtime feature/config classes only; configured parameters and generated occurrences unverified.");
    }

    static EffectiveInventory.Domain placedFeatures(RegistryAccess access) {
        return dynamic("placed_features", () -> access.registryOrThrow(Registries.PLACED_FEATURE),
                feature -> {
                    JsonObject row = new JsonObject();
                    row.addProperty("placement_modifiers", feature.placement().size());
                    row.addProperty("configured_feature_holder", feature.feature().unwrapKey()
                            .map(key -> key.location().toString()).orElse("unkeyed_inline"));
                    return row;
                }, "effective_dynamic_placed_feature_registry",
                "Membership and public placement count/feature holder only; exact placement, biome reachability and generation unverified.");
    }

    private static <T> EffectiveInventory.Domain dynamic(String name, Supplier<Registry<T>> registry,
            Function<T, JsonObject> metadata, String source, String limitation) {
        return registry(name, () -> registry.get().entrySet().stream().map(Map.Entry::getKey)
                .map(ResourceKey::location).toList(), id -> {
            T value = registry.get().get(id);
            if (value == null) throw new IllegalStateException("dynamic registry value absent");
            JsonObject row = EffectiveDomainCapture.row(id, value.getClass().getName());
            for (var entry : metadata.apply(value).entrySet()) row.add(entry.getKey(), entry.getValue());
            return row;
        }, false, source, limitation);
    }

    static EffectiveInventory.Domain registry(String name, Supplier<Collection<ResourceLocation>> ids,
            Function<ResourceLocation, JsonObject> metadata, boolean definitionCanBeComplete,
            String source, String limitation) {
        final List<ResourceLocation> keys;
        try { keys = ids.get().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList(); }
        catch (Exception error) { return unavailable(source, limitation, error); }
        List<String> rows = new ArrayList<>(); int failures = 0;
        for (ResourceLocation id : keys) {
            JsonObject row;
            try {
                row = metadata.apply(id);
                if (!row.has("id") || !id.toString().equals(row.get("id").getAsString()) ||
                        row.toString().length() > MAX_ROW_CHARS)
                    throw new IllegalStateException("metadata row identity/size invalid");
                row.addProperty("metadata_complete", true);
            } catch (Exception error) {
                failures++;
                row = row(id, "unknown_due_to_getter_error");
                row.addProperty("metadata_complete", false);
                row.addProperty("unsupported_reason", "public_metadata_getter:" + error.getClass().getSimpleName());
            }
            rows.add(row.toString());
        }
        boolean membership = keys.size() == rows.size() && keys.stream().distinct().count() == keys.size();
        return new EffectiveInventory.Domain(rows, membership, definitionCanBeComplete && failures == 0,
                keys.size(), failures, source, limitation);
    }

    private static EffectiveInventory.Domain unavailable(String source, String limitation, Exception error) {
        JsonObject unsupported = new JsonObject(); unsupported.addProperty("metadata_complete", false);
        unsupported.addProperty("unsupported_reason", "membership_enumeration:" + error.getClass().getSimpleName());
        return new EffectiveInventory.Domain(List.of(unsupported.toString()), false, false,
                -1, 1, source, limitation);
    }

    private static JsonObject row(ResourceLocation id, String providerClass) {
        JsonObject row = new JsonObject(); row.addProperty("id", id.toString());
        row.addProperty("provider_class", providerClass); return row;
    }
}
