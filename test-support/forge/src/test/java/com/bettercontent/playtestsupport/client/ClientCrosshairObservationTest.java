package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Projection model only: no client, world, OS mouse, screenshot or raycast is run. */
class ClientCrosshairObservationTest {
    private static final String PLAYER = "123e4567-e89b-42d3-a456-426614174000";
    @Test void domainSeparatedActualHitResultProjectionFreezesPositionFaceAndHitLocation() {
        var frozen = ClientCrosshairObservation.target(PLAYER, "minecraft:overworld", 3, 64, -2,
            "north", 3.5, 64.5, -2.0);
        var same = ClientCrosshairObservation.target(PLAYER, "minecraft:overworld", 3, 64, -2,
            "north", 3.5, 64.5, -2.0);
        var shifted = ClientCrosshairObservation.target(PLAYER, "minecraft:overworld", 4, 64, -2,
            "north", 4.5, 64.5, -2.0);
        var face = ClientCrosshairObservation.target(PLAYER, "minecraft:overworld", 3, 64, -2,
            "east", 3.5, 64.5, -2.0);
        assertEquals(ClientCrosshairObservation.projectionSha256(frozen), ClientCrosshairObservation.projectionSha256(same));
        assertNotEquals(ClientCrosshairObservation.projectionSha256(frozen), ClientCrosshairObservation.projectionSha256(shifted));
        assertNotEquals(ClientCrosshairObservation.projectionSha256(frozen), ClientCrosshairObservation.projectionSha256(face));
        assertEquals(64, ClientCrosshairObservation.projectionSha256(frozen).length());
    }
    @Test void illegalFaceNonfiniteOrForeignHitCannotBecomeReviewedTarget() {
        assertThrows(IllegalArgumentException.class, () -> ClientCrosshairObservation.target(PLAYER,
            "minecraft:overworld", 3, 64, -2, "side", 3.5, 64.5, -2));
        assertThrows(IllegalArgumentException.class, () -> ClientCrosshairObservation.target(PLAYER,
            "minecraft:overworld", 3, 64, -2, "north", Double.NaN, 64.5, -2));
        assertThrows(IllegalArgumentException.class, () -> ClientCrosshairObservation.target(PLAYER,
            "minecraft:overworld", 3, 64, -2, "north", 300, 64.5, -2));
    }
}
