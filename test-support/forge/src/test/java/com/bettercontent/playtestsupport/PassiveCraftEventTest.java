package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure guard tests only. Native event delivery and ancestry need disposable-world evidence. */
final class PassiveCraftEventTest {
    @Test void exactlyOneEventMustFollowArmBeforeDeadline() {
        assertTrue(PassiveCraftEvent.admissibleEvent(10, 10, 20, 1));
        assertTrue(PassiveCraftEvent.admissibleEvent(10, 20, 20, 1));
        assertFalse(PassiveCraftEvent.admissibleEvent(10, 9, 20, 1));
        assertFalse(PassiveCraftEvent.admissibleEvent(10, 21, 20, 1));
        assertFalse(PassiveCraftEvent.admissibleEvent(10, 11, 20, 0));
        assertFalse(PassiveCraftEvent.admissibleEvent(10, 11, 20, 2));
    }
    @Test void craftObservationIsReadOnlyAndScopedToDiagnostics() {
        assertTrue(Protocol.DRIVERS.contains(PassiveCraftEvent.ARM));
        assertTrue(Protocol.DRIVERS.contains(PassiveCraftEvent.AWAIT));
        assertFalse(Protocol.mayMutate(PassiveCraftEvent.ARM));
        assertFalse(Protocol.mayMutate(PassiveCraftEvent.AWAIT));
    }
}
