package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;

/** The observed larger recipe-semantic domain gets a bounded exception, not a global inventory bypass. */
final class InventoryByteCapTest {
    @Test void measuredRostersRetainSortedDistinctIdsInSidecarRows() {
        assertEquals(List.of("{\"id\":\"minecraft:oak_log\"}", "{\"id\":\"minecraft:stick\"}"),
                NativeSupport.measuredRosterRows(List.of("minecraft:stick", "minecraft:oak_log", "minecraft:stick")));
    }

    @Test void onlyExactRecipeSemanticsNameHasTheMeasuredLargerBudget() {
        assertEquals(50_331_648, NativeSupport.inventoryByteCap("recipes_semantics.jsonl"));
        assertEquals(16_777_216, NativeSupport.inventoryByteCap("recipes.jsonl"));
        assertEquals(16_777_216, NativeSupport.inventoryByteCap("recipes_wire.jsonl"));
        assertEquals(16_777_216, NativeSupport.inventoryByteCap("recipes_semantics.jsonl.extra"));
    }
}
