package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;

import java.util.Set;
import java.util.UUID;

/** Controller-declared lineage, checked against actual request/actor but NOT independently certified by native. */
final class NativeWitnessContext {
    static final String DRIVER = "player.dig.witness_v1";
    static final String TRACE_SCHEMA = "bc.playtest_acquisition_trace.v2";
    private static final Set<String> FIELDS = Set.of("campaign_id", "lineage_id", "epoch_id",
            "native_session_id", "branch_id", "case_id", "trial_id", "source", "purpose",
            "action_ordinal", "request_sequence", "actor_uuid", "capability_id");
    final JsonObject declared;
    final String boundTargetSha256;
    final String actualSession, actualCase;
    final long actualSequence;
    final UUID actualActor;

    private NativeWitnessContext(JsonObject declared, String boundTargetSha256,
                                 Protocol.Request request, UUID actualActor) {
        this.declared = declared.deepCopy(); this.boundTargetSha256 = boundTargetSha256;
        this.actualSession = request.run(); this.actualCase = request.caseId();
        this.actualSequence = request.sequence(); this.actualActor = actualActor;
    }

    static NativeWitnessContext parse(Protocol.Request request, JsonObject parameters, UUID actualActor) {
        if (!DRIVER.equals(request.driver())) throw new IllegalArgumentException("not witness dig action");
        Protocol.only(parameters, "player_uuid", "dimension", "x", "y", "z", "face",
                "witness_context", "bound_target_sha256");
        String target = Protocol.string(parameters, "bound_target_sha256");
        if (!target.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("bound target digest invalid");
        JsonObject context = validatedContext(request, parameters, actualActor);
        return new NativeWitnessContext(context, target, request, actualActor);
    }

    /** Same closed controller-declared context on passive actions, without a fictitious target digest. */
    static JsonObject validatedContext(Protocol.Request request, JsonObject parameters, UUID actualActor) {
        if (parameters.get("witness_context") == null || !parameters.get("witness_context").isJsonObject())
            throw new IllegalArgumentException("typed witness context required");
        JsonObject context = parameters.getAsJsonObject("witness_context");
        if (!context.keySet().equals(FIELDS)) throw new IllegalArgumentException("witness context fields invalid");
        String campaign = Protocol.string(context, "campaign_id");
        String lineage = Protocol.string(context, "lineage_id");
        String epoch = Protocol.string(context, "epoch_id");
        String session = Protocol.string(context, "native_session_id");
        String branch = Protocol.string(context, "branch_id");
        String trial = Protocol.string(context, "trial_id");
        String actor = Protocol.string(context, "actor_uuid");
        String purpose = Protocol.string(context, "purpose");
        if (!campaign.matches("campaign-[0-9a-f-]{36}") ||
                !validUuid(campaign.substring("campaign-".length())) ||
                !validUuid(lineage) || !epoch.matches("epoch-[0-9]{5}") ||
                !session.matches("epoch-[0-9a-f-]{36}") || !validUuid(session.substring("epoch-".length())) ||
                !branch.matches("[A-Za-z0-9._:-]{1,128}") ||
                !trial.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") ||
                !(purpose.equals("diagnostic") || purpose.equals("qualification")) ||
                !Protocol.string(context, "source").equals("server") ||
                !session.equals(request.run()) || !Protocol.string(context, "case_id").equals(request.caseId()) ||
                !actor.equals(actualActor.toString()) || !actor.equals(Protocol.string(parameters, "player_uuid")) ||
                !Protocol.string(context, "capability_id").equals(request.driver()) ||
                Protocol.number(context, "request_sequence") != request.sequence() ||
                Protocol.number(context, "action_ordinal") <= 0)
            throw new IllegalArgumentException("controller-declared witness context differs from request/actual actor");
        return context.deepCopy();
    }

    private static boolean validUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException error) { return false; }
    }
}
