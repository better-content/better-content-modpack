package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** Pure pinned BLOCKS palette index regression; no world, native hook or fixture generator execution. */
final class BlockMutationOwnershipTest {
    @Test void all4096IndicesRoundTripWithoutAliasing() {
        HashSet<Integer> indices = new HashSet<>();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int index = BlockMutationOwnership.index(x, y, z);
            assertTrue(indices.add(index));
            assertEquals(x, BlockMutationOwnership.x(index));
            assertEquals(y, BlockMutationOwnership.y(index));
            assertEquals(z, BlockMutationOwnership.z(index));
        }
        assertEquals(4096, indices.size());
        assertEquals(0, BlockMutationOwnership.index(0, 0, 0));
        assertEquals(4095, BlockMutationOwnership.index(15, 15, 15));
    }
}
