package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure geometry only; this test source does NOT create Robot, Xvfb, GUI, Minecraft or a screenshot. UNRUN. */
class ClientGuiPointerTest {
    @Test void reviewedProfilesMapOneRenderedPixelIntoOnlyItsOwnPrivateDisplay() {
        assertEquals(1, ClientGuiPointer.rawCenter(0, 640, 1280));
        assertEquals(1279, ClientGuiPointer.rawCenter(639, 640, 1280));
        assertEquals(2, ClientGuiPointer.rawCenter(0, 640, 1920));
        assertEquals(1919, ClientGuiPointer.rawCenter(639, 640, 1920));
        assertEquals(1, ClientGuiPointer.rawCenter(0, 360, 720));
        assertEquals(1079, ClientGuiPointer.rawCenter(359, 360, 1080));
    }
    @Test void pointerOverflowScalingDriftAndOutsideScreenFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> ClientGuiPointer.rawCenter(-1, 640, 1280));
        assertThrows(IllegalArgumentException.class, () -> ClientGuiPointer.rawCenter(640, 640, 1280));
        assertThrows(IllegalArgumentException.class, () -> ClientGuiPointer.rawCenter(1, 640, 1281));
        assertThrows(IllegalArgumentException.class, () -> ClientGuiPointer.rawCenter(1, 0, 1280));
    }
}
