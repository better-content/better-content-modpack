package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure request/context binding regression; authored/compiled only, never runtime attestation. */
final class NativeWitnessContextTest {
    private static final String SESSION = "epoch-11111111-1111-4111-8111-111111111111";
    private static final UUID ACTOR = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private Protocol.Request request() {
        return new Protocol.Request(SESSION, "primitive", "a".repeat(64), 2,
                NativeWitnessContext.DRIVER, 1200, new JsonObject());
    }
    private JsonObject parameters() {
        JsonObject p = new JsonObject(); p.addProperty("player_uuid", ACTOR.toString());
        p.addProperty("dimension", "minecraft:overworld"); p.addProperty("x", 1);
        p.addProperty("y", 64); p.addProperty("z", 2); p.addProperty("face", "up");
        p.addProperty("bound_target_sha256", "b".repeat(64));
        JsonObject context = new JsonObject(); context.addProperty("campaign_id", "campaign-33333333-3333-4333-8333-333333333333");
        context.addProperty("lineage_id", "44444444-4444-4444-8444-444444444444");
        context.addProperty("epoch_id", "epoch-00001"); context.addProperty("native_session_id", SESSION);
        context.addProperty("branch_id", "branch.one"); context.addProperty("case_id", "primitive");
        context.addProperty("trial_id", "one"); context.addProperty("source", "server");
        context.addProperty("purpose", "diagnostic"); context.addProperty("action_ordinal", 3);
        context.addProperty("request_sequence", 2); context.addProperty("actor_uuid", ACTOR.toString());
        context.addProperty("capability_id", NativeWitnessContext.DRIVER);
        p.add("witness_context", context); return p;
    }
    @Test void exactNewActionEchoesControllerContextWithoutNativeLineageCertification() {
        var witness = NativeWitnessContext.parse(request(), parameters(), ACTOR);
        assertEquals(SESSION, witness.declared.get("native_session_id").getAsString());
        assertEquals("b".repeat(64), witness.boundTargetSha256);
        assertEquals("bc.playtest_acquisition_trace.v2", NativeWitnessContext.TRACE_SCHEMA);
    }
    @Test void rejectsEnvelopeActorEpochSessionPurposeAndUnknownFieldSpoof() {
        JsonObject p = parameters();
        p.getAsJsonObject("witness_context").addProperty("request_sequence", 3);
        JsonObject sequence = p; assertThrows(IllegalArgumentException.class,
                () -> NativeWitnessContext.parse(request(), sequence, ACTOR));
        p = parameters(); p.getAsJsonObject("witness_context").addProperty("actor_uuid", UUID.randomUUID().toString());
        JsonObject actor = p; assertThrows(IllegalArgumentException.class, () -> NativeWitnessContext.parse(request(), actor, ACTOR));
        p = parameters(); p.getAsJsonObject("witness_context").addProperty("native_session_id", "epoch-55555555-5555-4555-8555-555555555555");
        JsonObject session = p; assertThrows(IllegalArgumentException.class, () -> NativeWitnessContext.parse(request(), session, ACTOR));
        p = parameters(); p.getAsJsonObject("witness_context").addProperty("purpose", "promotion");
        JsonObject purpose = p; assertThrows(IllegalArgumentException.class, () -> NativeWitnessContext.parse(request(), purpose, ACTOR));
        p = parameters(); p.getAsJsonObject("witness_context").addProperty("invented_success", true);
        JsonObject unknown = p; assertThrows(IllegalArgumentException.class, () -> NativeWitnessContext.parse(request(), unknown, ACTOR));
        p = parameters(); p.addProperty("bound_target_sha256", "NOT-A-HASH");
        JsonObject target = p; assertThrows(IllegalArgumentException.class, () -> NativeWitnessContext.parse(request(), target, ACTOR));
        Protocol.Request legacy = new Protocol.Request(SESSION, "primitive", "a".repeat(64), 2,
                "player.dig", 1200, new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> NativeWitnessContext.parse(legacy, parameters(), ACTOR));
    }
}
