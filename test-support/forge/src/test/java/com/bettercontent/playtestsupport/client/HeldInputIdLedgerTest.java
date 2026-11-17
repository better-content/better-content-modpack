package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure one-use ID regression: no Robot, X11, Minecraft or runtime calls. Unrun. */
class HeldInputIdLedgerTest {
    @Test void repeatedAndMalformedHoldIdsFailAfterFirstClaim() {
        HeldInputIdLedger ledger = new HeldInputIdLedger();
        ledger.claim("food.1");
        assertThrows(IllegalArgumentException.class, () -> ledger.claim("food.1"));
        assertThrows(IllegalArgumentException.class, () -> ledger.claim("../other/epoch"));
        assertThrows(IllegalArgumentException.class, () -> ledger.claim(""));
        ledger.claim("food.2");
    }
    @Test void exactly4096UniqueBoundedHoldsPerSession() {
        HeldInputIdLedger ledger = new HeldInputIdLedger();
        for (int i = 0; i < 4096; i++) ledger.claim("hold." + i);
        assertThrows(IllegalArgumentException.class, () -> ledger.claim("hold.4096"));
    }
}
