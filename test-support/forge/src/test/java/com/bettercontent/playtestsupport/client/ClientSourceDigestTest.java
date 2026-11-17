package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure source helper only; does not start Forge, render a screenshot, or grant GUI qualification. */
class ClientSourceDigestTest {
    @Test void exactSha256MatchesBoundClientArtifactFormat() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ClientNativeSupport.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }
}
