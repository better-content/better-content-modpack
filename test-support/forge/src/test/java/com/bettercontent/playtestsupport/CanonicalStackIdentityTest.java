package com.bettercontent.playtestsupport;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Authored, unrun codec regressions; no player/world or acquisition assertion. */
class CanonicalStackIdentityTest {
    private CompoundTag stack() {
        CompoundTag value = new CompoundTag();
        value.putString("id", "minecraft:stone"); value.putByte("Count", (byte) 1);
        return value;
    }

    @Test void explicitTypedWireVector() throws Exception {
        // Compound type, two sorted entries: Count (BYTE 1), id (STRING minecraft:stone).
        byte[] payload = HexFormat.of().parseHex("0a0000000200000005436f756e740101000000026964080000000f6d696e6563726166743a73746f6e65");
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update("bc.playtest.stack-nbt.v1\0".getBytes(StandardCharsets.US_ASCII));
        expected.update(payload);
        assertEquals(HexFormat.of().formatHex(expected.digest()), CanonicalStackIdentity.sha256(stack()));
    }

    @Test void contentDigestHasSeparateDomainAndExcludesOnlyTopLevelCountAndSlot() throws Exception {
        CompoundTag value = stack();
        byte[] payload = HexFormat.of().parseHex("0a00000001000000026964080000000f6d696e6563726166743a73746f6e65");
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update("bc.playtest.stack-content.v1\0".getBytes(StandardCharsets.US_ASCII));
        expected.update(payload);
        String content = HexFormat.of().formatHex(expected.digest());
        assertEquals(content, CanonicalStackIdentity.contentSha256(value));
        String full = CanonicalStackIdentity.sha256(value);
        value.putByte("Count", (byte) 2); value.putByte("Slot", (byte) 5);
        assertEquals(content, CanonicalStackIdentity.contentSha256(value));
        assertNotEquals(full, CanonicalStackIdentity.sha256(value));
        assertEquals((byte) 2, value.getByte("Count")); // no source mutation
        CompoundTag nested = new CompoundTag(); nested.putByte("Count", (byte) 1);
        value.put("ForgeCaps", nested);
        assertNotEquals(content, CanonicalStackIdentity.contentSha256(value));
        String withCapability = CanonicalStackIdentity.contentSha256(value);
        value.getCompound("ForgeCaps").putByte("Count", (byte) 2);
        assertNotEquals(withCapability, CanonicalStackIdentity.contentSha256(value));
        value.putInt("Count", 2);
        assertThrows(IllegalArgumentException.class, () -> CanonicalStackIdentity.contentSha256(value));
    }

    @Test void onlyRootSlotIsExcludedAndInputIsNotMutated() {
        CompoundTag original = stack(); String hash = CanonicalStackIdentity.sha256(original);
        original.putByte("Slot", (byte) 2);
        assertEquals(hash, CanonicalStackIdentity.sha256(original));
        assertEquals(2, original.getByte("Slot"));
        CompoundTag nested = new CompoundTag(); nested.putByte("Slot", (byte) 2); original.put("tag", nested);
        assertNotEquals(hash, CanonicalStackIdentity.sha256(original));
        String withNested = CanonicalStackIdentity.sha256(original);
        nested.putByte("Slot", (byte) 3);
        assertNotEquals(withNested, CanonicalStackIdentity.sha256(original));
    }

    @Test void countTypeAndMalformedUnicodeCannotAlias() {
        CompoundTag value = stack(); String one = CanonicalStackIdentity.sha256(value);
        value.putByte("Count", (byte) 2);
        assertNotEquals(one, CanonicalStackIdentity.sha256(value));
        value.putInt("Count", 1);
        assertThrows(IllegalArgumentException.class, () -> CanonicalStackIdentity.sha256(value));
        value.putByte("Count", (byte) 1); value.putString("bad", String.valueOf((char) 0xd800));
        assertThrows(IllegalArgumentException.class, () -> CanonicalStackIdentity.sha256(value));
    }
}
