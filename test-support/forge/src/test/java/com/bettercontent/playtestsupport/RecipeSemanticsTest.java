package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure policy tests. Authoring/compilation does not authorize running tests or a world. */
final class RecipeSemanticsTest {
    private static final String VANILLA = "net.minecraft.world.item.crafting.";
    @Test void exactVanillaProviderFamiliesAndPrimitiveIdsAreTyped() {
        assertEquals("shaped", RecipeSemantics.family("minecraft:crafting_shaped", VANILLA + "ShapedRecipe",
                "kubejs:hand_workshop/hand_crank", false));
        assertEquals("shapeless", RecipeSemantics.family("minecraft:crafting_shapeless", VANILLA + "ShapelessRecipe",
                "kubejs:primitive/rock_hand_axe", false));
        for (var pair : java.util.Map.of("smelting", "SmeltingRecipe", "blasting", "BlastingRecipe",
                "smoking", "SmokingRecipe", "campfire_cooking", "CampfireCookingRecipe").entrySet())
            assertEquals(pair.getKey(), RecipeSemantics.family("minecraft:" + pair.getKey(), VANILLA + pair.getValue(),
                    "kubejs:hand_workshop/grout/test", false));
        assertEquals("stonecutting", RecipeSemantics.family("minecraft:stonecutting", VANILLA + "StonecutterRecipe",
                "minecraft:stone_bricks", false));
    }
    @Test void subclassesSpecialSmithingAndUnknownRecipesAreNotFlattenedIntoVanillaSemantics() {
        assertEquals("unsupported_unmapped_serializer_or_provider", RecipeSemantics.family("minecraft:crafting_shaped",
                "other.mod.MatchingOverride", "other:shaped", false));
        assertEquals("unsupported_special_or_dynamic", RecipeSemantics.family("minecraft:crafting_shapeless",
                VANILLA + "ShapelessRecipe", "minecraft:dynamic", true));
        assertEquals("unsupported_smithing_private_ingredients", RecipeSemantics.family("minecraft:smithing_trim",
                VANILLA + "SmithingTrimRecipe", "minecraft:trim", false));
        assertEquals("unsupported_unmapped_serializer_or_provider", RecipeSemantics.family("other:unknown",
                "other.mod.Recipe", "other:unknown", false));
    }
    @Test void exactPinnedHandWorkshopTconstructProviderOnlyAndOversizeRejects() {
        // Reviewed Mantle ICommonRecipe isSpecial=true for its book policy, even for exact
        // pinned TCon alloy/casting models; unknown special recipes still fail above.
        assertEquals("tconstruct_alloy", RecipeSemantics.family("tconstruct:alloy",
                "slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe",
                "kubejs:hand_workshop/alloying/andesite_alloy_iron", true));
        assertEquals("tconstruct_casting", RecipeSemantics.family("tconstruct:casting_basin",
                "slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe",
                "kubejs:hand_workshop/casting/andesite_alloy_iron_melter", true));
        assertEquals("unsupported_unmapped_serializer_or_provider", RecipeSemantics.family("tconstruct:alloy",
                "slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe", "other:alloy", false));
        assertEquals("unsupported_unmapped_serializer_or_provider", RecipeSemantics.family("tconstruct:casting_basin",
                "other.mod.HiddenCastingOverride", "kubejs:hand_workshop/casting/test", false));
        assertTrue(RecipeSemantics.ingredientIsPublicVanilla(true));
        assertTrue(RecipeSemantics.withinAlternativeBudget(256));
        assertFalse(RecipeSemantics.withinAlternativeBudget(257));
        assertTrue(RecipeSemantics.withinSlotBudget(81));
        assertFalse(RecipeSemantics.withinSlotBudget(82));
        assertTrue(RecipeSemantics.withinValueBudget("x".repeat(16_384)));
        assertFalse(RecipeSemantics.withinValueBudget("x".repeat(16_385)));
        assertFalse(RecipeSemantics.ingredientIsPublicVanilla(false));
    }
}
