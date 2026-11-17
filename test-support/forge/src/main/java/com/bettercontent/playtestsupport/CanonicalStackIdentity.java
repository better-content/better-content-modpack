package com.bettercontent.playtestsupport;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Nonshipping identity codec, NOT an acquisition oracle. Old SNBT digests are not this format. */
final class CanonicalStackIdentity {
    static final String ALGORITHM = "bc.playtest.stack-nbt.v1";
    static final String CONTENT_ALGORITHM = "bc.playtest.stack-content.v1";
    private static final int MAX_NODES = 100_000, MAX_DEPTH = 64;
    private static final int MAX_ARRAY_BYTES = 16 * 1024 * 1024, MAX_STRING_BYTES = 1024 * 1024;
    private static final long MAX_WIRE_BYTES = 64L * 1024 * 1024;

    private CanonicalStackIdentity() {}

    /** Use the once-captured ItemStack.save compound; only its TOP-LEVEL Slot is excluded. */
    static String sha256(CompoundTag savedStack) { return digest(savedStack, ALGORITHM, false); }

    /** Exact count-independent typed content, not a replacement for Count-including sha256(). */
    static String contentSha256(CompoundTag savedStack) {
        return digest(savedStack, CONTENT_ALGORITHM, true);
    }

    private static String digest(CompoundTag savedStack, String algorithm, boolean omitTopLevelCount) {
        if (!savedStack.contains("id", Tag.TAG_STRING) || !savedStack.getString("id").matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ||
            !savedStack.contains("Count", Tag.TAG_BYTE) || savedStack.getByte("Count") <= 0 ||
            (savedStack.contains("Slot") && !savedStack.contains("Slot", Tag.TAG_BYTE)))
            throw new IllegalArgumentException("not a nonempty saved ItemStack");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            OutputStream sink = new OutputStream() {
                private long bytes;
                @Override public void write(int value) throws IOException {
                    if (++bytes > MAX_WIRE_BYTES) throw new IOException("canonical stack wire budget exceeded");
                    digest.update((byte) value);
                }
                @Override public void write(byte[] value, int offset, int length) throws IOException {
                    if (length < 0 || bytes + length > MAX_WIRE_BYTES) throw new IOException("canonical stack wire budget exceeded");
                    bytes += length; digest.update(value, offset, length);
                }
            };
            DataOutputStream output = new DataOutputStream(sink);
            output.write((algorithm + "\0").getBytes(StandardCharsets.US_ASCII));
            new Encoder(output, omitTopLevelCount).typed(savedStack, 0, true);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | GeneralSecurityException failure) {
            throw new IllegalArgumentException("invalid or oversized canonical stack identity", failure);
        }
    }

    private static byte[] utf8(String text) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes);
        if (bytes.length > MAX_STRING_BYTES) throw new IllegalArgumentException("canonical string budget exceeded");
        return bytes;
    }

    private static final class Encoder {
        private final DataOutputStream out;
        private int nodes;
        private final boolean omitTopLevelCount;
        Encoder(DataOutputStream out, boolean omitTopLevelCount) {
            this.out = out; this.omitTopLevelCount = omitTopLevelCount;
        }
        void typed(Tag tag, int depth, boolean root) throws IOException {
            out.writeByte(tag.getId()); payload(tag, depth, root);
        }
        void payload(Tag tag, int depth, boolean root) throws IOException {
            if (++nodes > MAX_NODES || depth > MAX_DEPTH) throw new IOException("canonical NBT structure budget exceeded");
            switch (tag.getId()) {
                case Tag.TAG_BYTE -> out.writeByte(((NumericTag) tag).getAsByte());
                case Tag.TAG_SHORT -> out.writeShort(((NumericTag) tag).getAsShort());
                case Tag.TAG_INT -> out.writeInt(((NumericTag) tag).getAsInt());
                case Tag.TAG_LONG -> out.writeLong(((NumericTag) tag).getAsLong());
                case Tag.TAG_FLOAT -> out.writeInt(Float.floatToRawIntBits(((NumericTag) tag).getAsFloat()));
                case Tag.TAG_DOUBLE -> out.writeLong(Double.doubleToRawLongBits(((NumericTag) tag).getAsDouble()));
                case Tag.TAG_BYTE_ARRAY -> {
                    byte[] value = ((ByteArrayTag) tag).getAsByteArray(); arrayBudget(value.length, 1);
                    out.writeInt(value.length); out.write(value);
                }
                case Tag.TAG_STRING -> string(tag.getAsString());
                case Tag.TAG_LIST -> {
                    ListTag list = (ListTag) tag;
                    if (list.size() > MAX_NODES || list.getElementType() < 0 || list.getElementType() > Tag.TAG_LONG_ARRAY ||
                        (list.getElementType() == Tag.TAG_END && !list.isEmpty())) throw new IOException("invalid list");
                    out.writeByte(list.getElementType()); out.writeInt(list.size());
                    for (Tag child : list) {
                        if (child.getId() != list.getElementType()) throw new IOException("heterogeneous list");
                        payload(child, depth + 1, false);
                    }
                }
                case Tag.TAG_COMPOUND -> {
                    CompoundTag compound = (CompoundTag) tag;
                    List<Key> keys = new ArrayList<>();
                    for (String key : compound.getAllKeys())
                        if ((!root || !key.equals("Slot")) && (!root || !omitTopLevelCount || !key.equals("Count")))
                            keys.add(new Key(key, utf8(key)));
                    if (keys.size() > MAX_NODES) throw new IOException("compound key budget exceeded");
                    keys.sort((left, right) -> Arrays.compareUnsigned(left.bytes, right.bytes));
                    out.writeInt(keys.size());
                    for (Key key : keys) {
                        out.writeInt(key.bytes.length); out.write(key.bytes);
                        Tag child = Objects.requireNonNull(compound.get(key.text));
                        if (child.getId() == Tag.TAG_END) throw new IOException("named end tag");
                        typed(child, depth + 1, false);
                    }
                }
                case Tag.TAG_INT_ARRAY -> {
                    int[] value = ((IntArrayTag) tag).getAsIntArray(); arrayBudget(value.length, 4);
                    out.writeInt(value.length); for (int element : value) out.writeInt(element);
                }
                case Tag.TAG_LONG_ARRAY -> {
                    long[] value = ((LongArrayTag) tag).getAsLongArray(); arrayBudget(value.length, 8);
                    out.writeInt(value.length); for (long element : value) out.writeLong(element);
                }
                default -> throw new IOException("unsupported or misplaced NBT type: " + tag.getId());
            }
        }
        private void string(String value) throws IOException { byte[] bytes = utf8(value); out.writeInt(bytes.length); out.write(bytes); }
        private void arrayBudget(int length, int elementBytes) throws IOException {
            if ((long) length * elementBytes > MAX_ARRAY_BYTES) throw new IOException("canonical array budget exceeded");
        }
        private record Key(String text, byte[] bytes) {}
    }
}
