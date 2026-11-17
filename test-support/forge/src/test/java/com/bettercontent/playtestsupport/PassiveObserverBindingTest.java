package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure closed arm/await binding; real packet/provider callbacks require separately authorized runtime. */
final class PassiveObserverBindingTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SESSION = "epoch-22222222-2222-4222-8222-222222222222";
    private static final String ID = "33333333-3333-4333-8333-333333333333";
    private Protocol.Request request(String driver, int sequence) {
        return new Protocol.Request(SESSION, "food_case", "a".repeat(64), sequence, driver, 1200, new JsonObject());
    }
    private JsonObject context(String driver, int sequence, int ordinal) {
        JsonObject c = new JsonObject(); c.addProperty("campaign_id", "campaign-44444444-4444-4444-8444-444444444444");
        c.addProperty("lineage_id", "55555555-5555-4555-8555-555555555555");
        c.addProperty("epoch_id", "epoch-00001"); c.addProperty("native_session_id", SESSION);
        c.addProperty("branch_id", "real-client"); c.addProperty("case_id", "food_case");
        c.addProperty("trial_id", "one"); c.addProperty("source", "server"); c.addProperty("purpose", "qualification");
        c.addProperty("action_ordinal", ordinal); c.addProperty("request_sequence", sequence);
        c.addProperty("actor_uuid", ACTOR.toString()); c.addProperty("capability_id", driver);
        return c;
    }
    private JsonObject arm() {
        JsonObject p = new JsonObject(); p.addProperty("player_uuid", ACTOR.toString());
        p.addProperty("dimension", "minecraft:overworld"); p.addProperty("observer_id", ID);
        p.addProperty("deadline_ticks", 300); p.add("witness_context", context(PassiveItemUse.ARM, 2, 4)); return p;
    }
    private JsonObject await() {
        JsonObject p = new JsonObject(); p.addProperty("player_uuid", ACTOR.toString());
        p.addProperty("dimension", "minecraft:overworld"); p.addProperty("observer_id", ID);
        p.addProperty("arm_request_sequence", 2); p.addProperty("arm_action_ordinal", 4);
        p.add("witness_context", context(PassiveItemUse.AWAIT, 3, 6)); return p;
    }
    @Test void exactArmAndLaterAwaitRetainOneImmutableContextAndFiniteDeadline() {
        var armed = PassiveObserverBinding.arm(request(PassiveItemUse.ARM, 2), arm(), ACTOR,
                "minecraft:overworld", 100, 7200);
        assertEquals(400, armed.deadlineTick);
        assertDoesNotThrow(() -> armed.await(request(PassiveItemUse.AWAIT, 3), await(), ACTOR, "minecraft:overworld"));
        assertFalse(armed.echo().get("context_certified_by_native").getAsBoolean());
    }
    @Test void forgedEpochActorProviderSequenceOrAwaitObserverNeverBinds() {
        JsonObject bad = arm(); bad.getAsJsonObject("witness_context").addProperty("epoch_id", "epoch-NOTNUMBER");
        assertThrows(IllegalArgumentException.class, () -> PassiveObserverBinding.arm(request(PassiveItemUse.ARM, 2),
                bad, ACTOR, "minecraft:overworld", 100, 7200));
        var armed = PassiveObserverBinding.arm(request(PassiveItemUse.ARM, 2), arm(), ACTOR,
                "minecraft:overworld", 100, 7200);
        JsonObject wrongId = await(); wrongId.addProperty("observer_id", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> armed.await(request(PassiveItemUse.AWAIT, 3), wrongId,
                ACTOR, "minecraft:overworld"));
        JsonObject wrongBranch = await(); wrongBranch.getAsJsonObject("witness_context").addProperty("branch_id", "alien");
        assertThrows(IllegalArgumentException.class, () -> armed.await(request(PassiveItemUse.AWAIT, 3), wrongBranch,
                ACTOR, "minecraft:overworld"));
        JsonObject stale = await(); stale.getAsJsonObject("witness_context").addProperty("action_ordinal", 4);
        assertThrows(IllegalArgumentException.class, () -> armed.await(request(PassiveItemUse.AWAIT, 3), stale,
                ACTOR, "minecraft:overworld"));
        assertThrows(IllegalArgumentException.class, () -> armed.await(request(PassiveItemUse.AWAIT, 3), await(),
                UUID.randomUUID(), "minecraft:overworld"));
        JsonObject tooLong = arm(); tooLong.addProperty("deadline_ticks", 7201);
        assertThrows(IllegalArgumentException.class, () -> PassiveObserverBinding.arm(request(PassiveItemUse.ARM, 2),
                tooLong, ACTOR, "minecraft:overworld", 100, 7200));
    }
    @Test void actualPacketEventAndPostCommitTimelinesRejectMissingAndEarlySuccess() {
        assertTrue(PassiveItemUse.allowedStart(1, 1, 3, 2, false));
        assertFalse(PassiveItemUse.allowedStart(1, 1, 3, 2, true)); // Forge cancellation
        assertFalse(PassiveItemUse.allowedStart(1, 0, -1, 2, false)); // no real packet
        assertFalse(PassiveItemUse.allowedStart(1, 1, 1, 2, false)); // arm after callback
        assertTrue(PassiveItemUse.validChain(2, 3, 3, 24, 24, 1, 1, 1));
        assertFalse(PassiveItemUse.validChain(2, -1, 3, 24, 24, 0, 1, 1));
        assertFalse(PassiveItemUse.validChain(2, 3, 1, 24, 24, 1, 1, 1)); // arm-after-event
        assertFalse(PassiveItemUse.validChain(2, 3, 3, -1, 24, 1, 1, 0));
        assertFalse(PassiveItemUse.validChain(2, 3, 3, 24, 24, 2, 1, 1)); // auto-repeat
        assertFalse(PassiveOnboarding.earlySurvivalWithoutHandler(-1, "spectator"));
        assertTrue(PassiveOnboarding.earlySurvivalWithoutHandler(-1, "survival"));
        assertFalse(PassiveOnboarding.earlySurvivalWithoutHandler(141, "survival"));
        assertTrue(PassiveOnboarding.validChain(2, 4, 140, 141, 150, 1, 1, 1, true, "survival"));
        assertFalse(PassiveOnboarding.validChain(2, 4, -1, 141, 150, 1, 0, 1, true, "survival"));
        assertFalse(PassiveOnboarding.validChain(2, 4, 140, -1, 150, 1, 1, 0, true, "survival"));
        assertFalse(PassiveOnboarding.validChain(2, 4, 140, 141, 150, 1, 1, 1, false, "survival"));
        assertFalse(PassiveOnboarding.validChain(2, 4, 140, 141, 150, 1, 1, 1, true, "spectator"));
    }
}
