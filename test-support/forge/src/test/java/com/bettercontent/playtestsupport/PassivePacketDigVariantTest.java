package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN pure v2 closed branch matrix; no real packet, Minecraft launch or gameplay outcome claim. */
final class PassivePacketDigVariantTest {
    @Test void eachPacketTimingHasExactlyPositivePickupOrFailedHarvestNoDropNegative() {
        for (String variant : new String[]{PassivePhysicalDig.INSTANT, PassivePhysicalDig.PROGRESS}) {
            assertTrue(PassivePhysicalDig.supportedBranch(variant, PassivePhysicalDig.PICKUP, true));
            assertTrue(PassivePhysicalDig.supportedBranch(variant, PassivePhysicalDig.NO_DROP, false));
            assertFalse(PassivePhysicalDig.supportedBranch(variant, PassivePhysicalDig.PICKUP, false));
            assertFalse(PassivePhysicalDig.supportedBranch(variant, PassivePhysicalDig.NO_DROP, true));
            assertFalse(PassivePhysicalDig.supportedBranch(variant, "unknown", false));
        }
        assertFalse(PassivePhysicalDig.supportedBranch("unknown", PassivePhysicalDig.PICKUP, true));
        assertEquals("player.observe_dig.arm_v5", PassivePhysicalDig.ARM);
        assertEquals("player.observe_dig.await_v5", PassivePhysicalDig.AWAIT);
        assertFalse(Protocol.mayMutate(PassivePhysicalDig.ARM));
    }
}
