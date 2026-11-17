package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN v3 producer decision regression: invoke the SAME classifier as actual AWAIT, not handmade complete receipts. */
final class PassivePhysicalDigCaptureContractTest {
    private static PassivePhysicalDig.Capture captured(String variant, String drop, boolean harvest) {
        int packets = PassivePhysicalDig.INSTANT.equals(variant) ? 1 : 2;
        // These are actual-callback shape counts, not the reviewed expected counts.
        return new PassivePhysicalDig.Capture(variant, drop, harvest,
                1, packets - 1, 0, packets, packets, packets, 1, 1, true, 1,
                true, true, null, 70, 100);
    }
    private static void contradiction(JsonObject decision, String... mismatches) {
        assertTrue(decision.get("observation_complete").getAsBoolean(), "complete actual facts must remain complete");
        assertFalse(decision.get("reviewed_expectation_matches").getAsBoolean());
        assertFalse(decision.has("unsupported_reason"), "behavior contradiction is not a technical capture gap");
        var actual = decision.getAsJsonArray("expectation_mismatches");
        assertEquals(mismatches.length, actual.size());
        for (int i = 0; i < mismatches.length; i++) assertEquals(mismatches[i], actual.get(i).getAsString());
    }
    @Test void unexpectedStoredHarvestRemainsCompleteObservedFact() {
        contradiction(PassivePhysicalDig.classifyCapture(captured(PassivePhysicalDig.INSTANT,
                PassivePhysicalDig.PICKUP, false), PassivePhysicalDig.INSTANT, PassivePhysicalDig.PICKUP, true),
                "harvest_result");
    }
    @Test void actualInstantAgainstReviewedProgressiveRemainsComplete() {
        contradiction(PassivePhysicalDig.classifyCapture(captured(PassivePhysicalDig.INSTANT,
                PassivePhysicalDig.PICKUP, true), PassivePhysicalDig.PROGRESS, PassivePhysicalDig.PICKUP, true),
                "packet_variant");
    }
    @Test void unexpectedFullyPickedUpSourceAgainstReviewedNegativeRemainsComplete() {
        contradiction(PassivePhysicalDig.classifyCapture(captured(PassivePhysicalDig.PROGRESS,
                PassivePhysicalDig.PICKUP, false), PassivePhysicalDig.PROGRESS, PassivePhysicalDig.NO_DROP, false),
                "drop_outcome");
    }
    @Test void actualAbsentSourceAgainstReviewedPositiveRemainsCompleteAndMismatchHarvest() {
        contradiction(PassivePhysicalDig.classifyCapture(captured(PassivePhysicalDig.INSTANT,
                PassivePhysicalDig.NO_DROP, false), PassivePhysicalDig.INSTANT, PassivePhysicalDig.PICKUP, true),
                "drop_outcome", "harvest_result");
    }
    @Test void producerUsedClassificationExactlyMatchesSharedUnrunConsumerProjectionFixtures() throws Exception {
        try (var source = getClass().getClassLoader().getResourceAsStream("physical-dig-v3-contradictions.json")) {
            assertNotNull(source);
            var bundle = JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("bc.playtest_passive_packet_dig_producer_projection_fixtures.v3", bundle.get("schema").getAsString());
            assertTrue(bundle.get("synthetic_unrun_not_runtime_evidence").getAsBoolean());
            assertEquals(4, bundle.getAsJsonArray("cases").size());
            for (var element : bundle.getAsJsonArray("cases")) {
                var fixture = element.getAsJsonObject();
                String variant = fixture.get("actual_packet_variant").getAsString();
                String actualDrop = fixture.get("actual_drop_outcome").isJsonNull() ? null :
                        fixture.get("actual_drop_outcome").getAsString();
                Boolean actualHarvest = fixture.get("actual_stored_can_harvest").isJsonNull() ? null :
                        fixture.get("actual_stored_can_harvest").getAsBoolean();
                boolean missing = fixture.has("missing_harvest_callback") && fixture.get("missing_harvest_callback").getAsBoolean();
                int packets = PassivePhysicalDig.INSTANT.equals(variant) ? 1 : 2;
                // Actual callback-shaped counts, then the actual production AWAIT projection, never a handwritten receipt.
                var captured = new PassivePhysicalDig.Capture(variant, actualDrop, actualHarvest,
                        1, packets - 1, 0, packets, packets, packets, 1, 1, true,
                        missing ? 0 : 1, !missing, !missing, null, 100, 100);
                var produced = PassivePhysicalDig.classifyCapture(captured,
                        fixture.get("expected_packet_variant").getAsString(),
                        fixture.get("expected_drop_outcome").getAsString(),
                        fixture.get("expected_harvest_result").getAsBoolean());
                assertEquals(fixture.getAsJsonObject("decision"), produced, fixture.get("id").getAsString());
            }
        }
    }
    @Test void missingCallbackOverflowAndUnfinishedTerminalRemainCaptureFailuresWithoutBehaviorVerdict() {
        int p = 1;
        var missing = new PassivePhysicalDig.Capture(PassivePhysicalDig.INSTANT, PassivePhysicalDig.PICKUP,
                false, p, 0, 0, p, p, p, 1, 1, true, 0, true, true, null, 70, 100);
        var unsupported = new PassivePhysicalDig.Capture(PassivePhysicalDig.INSTANT, PassivePhysicalDig.PICKUP,
                false, p, 0, 0, p, p, p, 1, 1, true, 1, true, true, "packet_dig_trace_cap_exceeded", 70, 100);
        var unfinished = new PassivePhysicalDig.Capture(PassivePhysicalDig.INSTANT, null,
                false, p, 0, 0, p, p, p, 1, 1, true, 1, false, false, null, 70, 100);
        for (var capture : new PassivePhysicalDig.Capture[]{missing, unsupported, unfinished}) {
            JsonObject decision = PassivePhysicalDig.classifyCapture(capture,
                    PassivePhysicalDig.INSTANT, PassivePhysicalDig.NO_DROP, false);
            assertFalse(decision.get("observation_complete").getAsBoolean());
            assertFalse(decision.get("reviewed_expectation_matches").getAsBoolean());
            assertEquals(0, decision.getAsJsonArray("expectation_mismatches").size());
            assertTrue(decision.has("unsupported_reason"));
        }
    }
}
