package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure authoring regression; tests compiled but must not be run without human approval. */
final class EffectiveDomainCaptureTest {
    @Test void oneGetterFailureDoesNotEraseOtherMeasuredRegistryMembers() {
        var one = new ResourceLocation("example", "first");
        var broken = new ResourceLocation("example", "broken");
        var last = new ResourceLocation("example", "last");
        var domain = EffectiveDomainCapture.registry("example", () -> List.of(last, broken, one), id -> {
            if (id.equals(broken)) throw new IllegalStateException("unavailable");
            JsonObject row = new JsonObject(); row.addProperty("id", id.toString());
            row.addProperty("actual_value", 1); return row;
        }, true, "pinned_public_api", "not natural acquisition");
        assertEquals(3, domain.expectedMembers());
        assertEquals(3, domain.rows().size());
        assertEquals(1, domain.unsupportedRows());
        assertTrue(domain.membershipComplete());
        assertFalse(domain.definitionComplete());
        assertFalse(domain.complete());
        assertEquals(List.of("example:broken", "example:first", "example:last"), domain.rows().stream()
                .map(s -> JsonParser.parseString(s).getAsJsonObject().get("id").getAsString()).toList());
        var error = JsonParser.parseString(domain.rows().get(0)).getAsJsonObject();
        assertEquals("public_metadata_getter:IllegalStateException", error.get("unsupported_reason").getAsString());
        assertFalse(error.get("metadata_complete").getAsBoolean());
        var unrelated = EffectiveDomainCapture.registry("unrelated", () -> List.of(one), id -> {
            JsonObject row = new JsonObject(); row.addProperty("id", id.toString()); return row;
        }, true, "pinned_public_api", "metadata only");
        assertTrue(unrelated.complete());
    }

    @Test void duplicateOrUnenumerableRegistryNeverClaimsCompleteMembership() {
        var id = new ResourceLocation("example", "one");
        var duplicate = EffectiveDomainCapture.registry("duplicate", () -> List.of(id, id), key -> {
            JsonObject row = new JsonObject(); row.addProperty("id", key.toString()); return row;
        }, true, "registry", "metadata only");
        assertEquals(2, duplicate.rows().size());
        assertFalse(duplicate.membershipComplete());
        assertFalse(duplicate.complete());
        var absent = EffectiveDomainCapture.registry("absent", () -> {
            throw new IllegalStateException("registry unavailable");
        }, key -> new JsonObject(), true, "registry", "metadata only");
        assertEquals(-1, absent.expectedMembers());
        assertEquals(1, absent.unsupportedRows());
        assertFalse(absent.membershipComplete());
        assertFalse(absent.complete());
        assertEquals("membership_enumeration:IllegalStateException",
                JsonParser.parseString(absent.rows().get(0)).getAsJsonObject().get("unsupported_reason").getAsString());
    }

    @Test void wholeCatalogueCompletenessIsComputedNotAConstantOrOptimisticFlag() {
        var measured = new EffectiveInventory.Domain(List.of("{\"id\":\"example:one\"}"),
                true, true, 1, 0, "public_registry", "identity only");
        var unsupported = new EffectiveInventory.Domain(List.of("{\"id\":\"example:two\"}"),
                true, false, 1, 0, "unknown_provider", "missing semantics");
        assertTrue(EffectiveInventory.globallyComplete(java.util.Map.of("measured", measured), List.of()));
        assertFalse(EffectiveInventory.globallyComplete(java.util.Map.of("measured", measured),
                List.of("provider:natural_acquisition")));
        assertFalse(EffectiveInventory.globallyComplete(java.util.Map.of("measured", measured,
                "partial", unsupported), List.of()));
        assertFalse(EffectiveInventory.globallyComplete(java.util.Map.of(), List.of()));
    }

    @Test void semanticLimitAndMissingIdentityCannotBecomeMembershipPass() {
        var partial = new EffectiveInventory.Domain(List.of("{\"id\":\"example:one\"}"),
                true, false, 1, 0, "public_subset", "missing semantics");
        assertTrue(partial.membershipComplete());
        assertFalse(partial.complete());
        var missing = new EffectiveInventory.Domain(List.of("{\"metadata_complete\":true}"),
                true, true, 1, 0, "registry", "identity missing");
        assertFalse(missing.membershipComplete());
        assertFalse(missing.complete());
    }
}
