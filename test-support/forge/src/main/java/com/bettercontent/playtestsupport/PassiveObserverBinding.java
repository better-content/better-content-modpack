package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;

import java.util.Set;
import java.util.UUID;

/** Closed controller-declared passive arm/await lineage; native only checks echoes against request/actor. */
final class PassiveObserverBinding {
    private static final Set<String> SHARED = Set.of("campaign_id", "lineage_id", "epoch_id",
            "native_session_id", "branch_id", "case_id", "trial_id", "source", "purpose", "actor_uuid");
    final String observerId, dimension;
    final UUID actorId;
    final Protocol.Request arm;
    final JsonObject declared;
    final long armOrdinal, deadlineTick;

    private PassiveObserverBinding(Protocol.Request arm, JsonObject p, UUID actor, String dimension,
                                   JsonObject declared, long deadlineTick) {
        this.arm = arm; this.actorId = actor; this.dimension = dimension;
        this.observerId = Protocol.string(p, "observer_id"); this.declared = declared.deepCopy();
        this.armOrdinal = Protocol.number(declared, "action_ordinal"); this.deadlineTick = deadlineTick;
    }
    static PassiveObserverBinding arm(Protocol.Request request, JsonObject p, UUID actor, String dimension,
                                      long tick, int maxDeadlineTicks) {
        if (!Protocol.string(p, "player_uuid").equals(actor.toString()) ||
                !Protocol.string(p, "dimension").equals(dimension))
            throw new IllegalArgumentException("passive arm actor/dimension differs from joined player");
        String id = Protocol.string(p, "observer_id");
        if (!id.equals(UUID.fromString(id).toString())) throw new IllegalArgumentException("observer ID must be canonical UUID");
        long interval = Protocol.number(p, "deadline_ticks");
        if (interval < 1 || interval > maxDeadlineTicks) throw new IllegalArgumentException("observer deadline outside reviewed bound");
        JsonObject context = NativeWitnessContext.validatedContext(request, p, actor);
        return new PassiveObserverBinding(request, p, actor, dimension, context, Math.addExact(tick, interval));
    }
    void await(Protocol.Request request, JsonObject p, UUID actor, String actualDimension) {
        if (!observerId.equals(Protocol.string(p, "observer_id")) ||
                Protocol.number(p, "arm_request_sequence") != arm.sequence() ||
                Protocol.number(p, "arm_action_ordinal") != armOrdinal ||
                !actorId.equals(actor) || !actorId.toString().equals(Protocol.string(p, "player_uuid")) ||
                !dimension.equals(actualDimension) || !dimension.equals(Protocol.string(p, "dimension")) ||
                request.sequence() <= arm.sequence())
            throw new IllegalArgumentException("await not bound to armed observer/actor/dimension");
        JsonObject context = NativeWitnessContext.validatedContext(request, p, actor);
        if (Protocol.number(context, "action_ordinal") <= armOrdinal)
            throw new IllegalArgumentException("await must occur after armed ordinal");
        for (String name : SHARED)
            if (!declared.get(name).equals(context.get(name)))
                throw new IllegalArgumentException("passive arm/await lineage mismatch: " + name);
    }
    JsonObject echo() {
        JsonObject row = new JsonObject(); row.addProperty("observer_id", observerId);
        row.addProperty("arm_request_sequence", arm.sequence()); row.addProperty("arm_action_ordinal", armOrdinal);
        row.addProperty("actor_uuid", actorId.toString()); row.addProperty("dimension", dimension);
        row.addProperty("deadline_tick", deadlineTick);
        row.add("controller_declared_arm_context", declared.deepCopy());
        row.addProperty("context_certified_by_native", false);
        return row;
    }
}
