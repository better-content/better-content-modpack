package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ProtocolTest {
    private final String token = "a".repeat(64);
    private JsonObject request() {
        JsonObject row = new JsonObject();
        row.addProperty("schema", "bc.playtest_action.v2"); row.addProperty("run_id", "r1");
        row.addProperty("case_id", "case_1"); row.addProperty("token", token);
        row.addProperty("sequence", 1); row.addProperty("driver", "server.snapshot");
        row.addProperty("timeout_ticks", 40); row.add("parameters", new JsonObject());
        return row;
    }
    @Test void bindsEnvelopeToSequenceRunAndToken() {
        assertEquals("server.snapshot", Protocol.parse(request(), "r1", token, 1).driver());
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse(request(), "r2", token, 1));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse(request(), "r1", token, 2));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse(request(), "r1", "b".repeat(64), 1));
    }
    @Test void rejectsMalformedAndAdditionalFields() {
        JsonObject row = request(); row.addProperty("command", "op @a");
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse(row, "r1", token, 1));
        row.remove("command"); row.addProperty("timeout_ticks", 72001);
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse(row, "r1", token, 1));
        row.addProperty("timeout_ticks", 40); row.addProperty("sequence", 1.5);
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse(row, "r1", token, 1));
    }
    @Test void signedCoordinatesAreStrictIntegersButActionsMustMatchExactParameterKeys() {
        JsonObject coordinates = new JsonObject(); coordinates.addProperty("x", -32);
        assertEquals(-32, Protocol.number(coordinates, "x"));
        coordinates.addProperty("x", "-32");
        assertThrows(IllegalArgumentException.class, () -> Protocol.number(coordinates, "x"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.only(coordinates));
    }
    @Test void allNaturalInteractionsDirtyBaselineEvenWithoutProvisioning() {
        for (String driver : Set.of("player.interact", "player.use_item", "player.break_block", "player.dig.witness_v1", "fixture.begin", "fixture.restore", "future.action"))
            assertTrue(Protocol.mayMutate(driver), driver);
        for (String driver : Set.of("inventory.discover", "server.snapshot", "player.snapshot", "block.snapshot", "wait.ticks", "fixture.cleanup", "player.observe_item_use.arm", "player.observe_item_use.await",
                "player.observe_onboarding.arm", "player.observe_onboarding.await"))
            assertFalse(Protocol.mayMutate(driver), driver);
    }
    @Test void staticDescriptorListsExactlyImplementedActionIdentifiers() throws Exception {
        try (var input = getClass().getResourceAsStream("/META-INF/bc-playtest-support.json")) {
            assertNotNull(input);
            JsonObject descriptor = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("bc.playtest_support.v2", descriptor.get("schema").getAsString());
            assertEquals("bc.playtest_action.v2", descriptor.get("protocol").getAsString());
            Set<String> advertised = descriptor.getAsJsonArray("supported_drivers").asList().stream()
                    .map(value -> value.getAsString()).collect(Collectors.toSet());
            assertEquals(Protocol.DRIVERS, advertised);
            assertEquals(Protocol.DRIVERS.size(), descriptor.getAsJsonArray("supported_drivers").size());
            Set<String> client = descriptor.getAsJsonObject("client_observation").getAsJsonArray("supported_drivers")
                    .asList().stream().map(value -> value.getAsString()).collect(Collectors.toSet());
            assertTrue(client.containsAll(Set.of("client.input_hold", "client.input_release", "client.input_status")));
        }
    }
}
