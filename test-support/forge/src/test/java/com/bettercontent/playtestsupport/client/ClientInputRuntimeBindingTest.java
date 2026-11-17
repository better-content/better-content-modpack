package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Pure bytes/context checks; no X11, process lookup, Robot, world or fixture generator. Unrun. */
class ClientInputRuntimeBindingTest {
    private static final String RUN = "client-123e4567-e89b-12d3-a456-426614174000";
    private static final String HASH = "a".repeat(64);
    private static String row() {
        return """
            {"schema":"bc.playtest_client_input_runtime.v1","purpose":"diagnostic",
             "campaign_id":"campaign-001","lineage_id":"lineage-001","epoch_id":"epoch-00001",
             "client_session_id":"%s","candidate_sha256":"%s","support_sha256":"%s",
             "profile":"1280x720@2","display":"127.0.0.1:12345","xauthority_path":"/home/dev/world/Xauthority",
             "xauthority_sha256":"%s","host_xvfb_pid":9021,"host_xvfb_birth_epoch_millis":1794818910000,
             "host_xvfb_executable":"/usr/bin/Xvfb","host_xvfb_args_sha256":"%s",
             "host_client_pid":9022,"host_client_birth_epoch_millis":1794818910001,
             "host_client_nspid":[9022,17]}
            """.formatted(RUN, HASH, HASH, HASH, HASH);
    }
    private static ClientInputRuntimeBinding parse(String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return ClientInputRuntimeBinding.parse(bytes, ClientNativeSupport.sha256(bytes), RUN,
            "1280x720@2", "127.0.0.1:12345", "/home/dev/world/Xauthority", 17);
    }
    @Test void boundHostClaimsAndActualNamespacePidAreDistinct() throws Exception {
        var binding = parse(row());
        assertEquals(17, binding.namespacePid);
        assertEquals(9022, binding.hostClientPid);
        assertEquals(9021, binding.hostXvfbPid);
        assertEquals("epoch-00001", binding.epochId);
    }
    @Test void wrongNamespaceOrReplayedBirthCannotPass() {
        assertThrows(IllegalStateException.class, () -> parse(row().replace("[9022,17]", "[9022,18]")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("[9022,17]", "[9023,17]")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("1794818910000", "0")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("1794818910001", "0")));
    }
    @Test void tamperedBindingCookieProfileSessionContextAndFieldsCannotPass() {
        assertThrows(IllegalStateException.class, () -> parse(row().replace("/home/dev/world/Xauthority", "/other/cookie")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("1280x720@2", "1920x1080@3")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace(RUN, "client-foreign")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("epoch-00001", "epoch-bad")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("lineage-001", "")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("\"purpose\":\"diagnostic\",", "\"purpose\":\"diagnostic\",\"purpose\":\"diagnostic\",")));
        assertThrows(IllegalStateException.class, () -> parse(row().replace("\"schema\":", "\"extra\":true,\"schema\":")));
    }
    @Test void trailingBytesAndInvalidUtf8CannotSubstituteTheBoundCopy() {
        assertThrows(Exception.class, () -> parse(row() + "{}"));
        byte[] valid = row().getBytes(StandardCharsets.UTF_8);
        byte[] invalid = java.util.Arrays.copyOf(valid, valid.length + 1);
        invalid[invalid.length - 1] = (byte) 0xff;
        assertThrows(IllegalStateException.class, () -> ClientInputRuntimeBinding.parse(invalid,
            ClientNativeSupport.sha256(invalid), RUN, "1280x720@2", "127.0.0.1:12345", "/home/dev/world/Xauthority", 17));
    }
    @Test void hashAndBytesAreNotInterchangeable() {
        byte[] bytes = row().getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> ClientInputRuntimeBinding.parse(bytes, "b".repeat(64), RUN,
            "1280x720@2", "127.0.0.1:12345", "/home/dev/world/Xauthority", 17));
    }
}
