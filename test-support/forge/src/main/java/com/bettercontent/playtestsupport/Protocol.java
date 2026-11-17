package com.bettercontent.playtestsupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure, fail-closed validation of the v2 request envelope. */
public final class Protocol {
    private Protocol() {}
    public static final Set<String> DRIVERS = Set.of("inventory.discover", "server.snapshot", "player.snapshot",
            "player.interact", "player.use_item", "player.break_block", "block.snapshot", "wait.ticks", "fixture.begin", "fixture.restore", "fixture.cleanup", "world.sample", "world.entry", "player.water_snapshot", "player.dig", "player.dig.witness_v1", "player.observe_item_use.arm", "player.observe_item_use.await", "player.observe_onboarding.arm", "player.observe_onboarding.await", "player.attack", "player.lifecycle_snapshot", "entity.snapshot", "fixture.begin_water", "scale.begin", "scale.cleanup", "player.select_item", "target.observe.loaded_v1");
    private static final Set<String> READ_ONLY_ACTIONS = Set.of("inventory.discover", "server.snapshot",
            "player.snapshot", "player.lifecycle_snapshot", "player.water_snapshot", "entity.snapshot",
            "world.sample", "world.entry", "block.snapshot", "target.observe.loaded_v1", "wait.ticks", "fixture.cleanup",
            "player.observe_item_use.arm", "player.observe_item_use.await",
            "player.observe_onboarding.arm", "player.observe_onboarding.await");
    /** Unknown/new actions are conservatively dirty until explicitly reviewed as observation-only. */
    public static boolean mayMutate(String driver) { return !READ_ONLY_ACTIONS.contains(driver); }
    private static final Set<String> FIELDS = Set.of("schema", "run_id", "case_id", "token", "sequence", "driver", "timeout_ticks", "parameters");
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9._:-]{1,128}");
    private static final Pattern TOKEN = Pattern.compile("[0-9a-fA-F]{64}");
    public record Request(String run, String caseId, String token, long sequence, String driver, int timeout, JsonObject parameters) {}
    public static Request parse(JsonObject object, String expectedRun, String expectedToken, long expectedSequence) {
        if (!object.keySet().equals(FIELDS) || !"bc.playtest_action.v2".equals(string(object, "schema")))
            throw new IllegalArgumentException("invalid request schema/fields");
        String run = string(object, "run_id"), caseId = string(object, "case_id"), token = string(object, "token");
        String driver = string(object, "driver");
        if (!ID.matcher(run).matches() || !ID.matcher(caseId).matches() || !TOKEN.matcher(token).matches()
                || !run.equals(expectedRun) || !token.equals(expectedToken))
            throw new IllegalArgumentException("run/token/case mismatch");
        long sequence = number(object, "sequence"), timeout = number(object, "timeout_ticks");
        if (sequence != expectedSequence || sequence < 1 || timeout < 1 || timeout > 72000)
            throw new IllegalArgumentException("sequence/deadline mismatch");
        if (!object.get("parameters").isJsonObject()) throw new IllegalArgumentException("parameters must be object");
        return new Request(run, caseId, token, sequence, driver, (int) timeout, object.getAsJsonObject("parameters"));
    }
    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("missing/string field " + key);
        return value.getAsString();
    }
    public static long number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("missing/integer field " + key);
        String raw = value.getAsString();
        if (!raw.matches("-?(?:0|[1-9][0-9]{0,17})")) throw new IllegalArgumentException("invalid integer " + key);
        try { return Long.parseLong(raw); } catch (NumberFormatException bad) { throw new IllegalArgumentException("integer overflow " + key); }
    }
    public static void only(JsonObject object, String... allowed) {
        if (!object.keySet().equals(Set.of(allowed))) throw new IllegalArgumentException("invalid parameters");
    }
}
