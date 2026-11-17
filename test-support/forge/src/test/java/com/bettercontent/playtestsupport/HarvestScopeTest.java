package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure structural policy only. Does not assert Mixin application or a gameplay harvest verdict. */
final class HarvestScopeTest {
    @Test void exactlyOneScopedNativeCheckIsRequired() {
        assertTrue(NativeDig.harvestScopeComplete(1, false, false));
        assertFalse(NativeDig.harvestScopeComplete(0, false, false));
        assertFalse(NativeDig.harvestScopeComplete(2, false, false));
        assertFalse(NativeDig.harvestScopeComplete(64, false, false));
        assertFalse(NativeDig.harvestScopeComplete(1, true, false));
        assertFalse(NativeDig.harvestScopeComplete(1, false, true));
    }
}
