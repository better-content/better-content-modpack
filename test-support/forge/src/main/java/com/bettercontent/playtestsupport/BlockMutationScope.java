package com.bettercontent.playtestsupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Closed controller-prelaunch lifecycle scope. Not a mailbox action or natural-origin verdict. */
final class BlockMutationScope {
    static final String SCHEMA = "bc.playtest_block_mutation_scope.v1";
    static final int MAX_CONFIG_BYTES = 1_048_576, MAX_POSITIONS = 4096, MAX_EVENTS = 100_000, MAX_BYTES = 16_777_216;
    static final String FILE_PROPERTY = "bc.playtest.blockMutationScopeFile";
    static final String SHA_PROPERTY = "bc.playtest.blockMutationScopeSha256";
    record Position(String dimension, int x, int y, int z) implements Comparable<Position> {
        @Override public int compareTo(Position next) {
            int d = dimension.compareTo(next.dimension);
            if (d != 0) return d;
            int xx = Integer.compare(x, next.x);
            if (xx != 0) return xx;
            int yy = Integer.compare(y, next.y);
            return yy != 0 ? yy : Integer.compare(z, next.z);
        }
    }
    final String scopeId, configSha256, campaignId, lineageId, epochId, nativeSessionId,
            purpose, sourceCheckpointId, sourceCheckpointSha256;
    final List<Position> positions;
    final Set<Position> positionSet;
    final Path sourceFile;
    private BlockMutationScope(JsonObject config, String sha, List<Position> positions, Path file) {
        this.scopeId = Protocol.string(config, "scope_id"); this.configSha256 = sha;
        this.campaignId = Protocol.string(config, "campaign_id");
        this.lineageId = Protocol.string(config, "lineage_id"); this.epochId = Protocol.string(config, "epoch_id");
        this.nativeSessionId = Protocol.string(config, "native_session_id"); this.purpose = Protocol.string(config, "purpose");
        this.sourceCheckpointId = Protocol.string(config, "source_checkpoint_id");
        this.sourceCheckpointSha256 = Protocol.string(config, "source_checkpoint_sha256");
        this.positions = List.copyOf(positions); this.positionSet = Set.copyOf(positions); this.sourceFile = file;
    }
    static BlockMutationScope open(Path nativeMailbox, String expectedSession) throws IOException {
        String pathProperty = System.getProperty(FILE_PROPERTY), shaProperty = System.getProperty(SHA_PROPERTY);
        if (pathProperty == null && shaProperty == null) return null; // existing diagnostic workflows remain usable
        if (pathProperty == null || shaProperty == null || pathProperty.isBlank() || shaProperty.isBlank())
            throw new IOException("mutation scope requires both immutable startup properties");
        Path path;
        try { path = Path.of(pathProperty); }
        catch (RuntimeException invalid) { throw new IOException("mutation scope path invalid", invalid); }
        if (!path.isAbsolute() || !path.normalize().equals(path) ||
                nativeMailbox == null || path.startsWith(nativeMailbox) ||
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IOException("mutation scope must be a separate ordinary absolute file");
        for (Path parent = path.getParent(); parent != null; parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw new IOException("symlink in mutation scope path");
        if (Files.size(path) > MAX_CONFIG_BYTES) throw new IOException("mutation scope file oversized");
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("mutation scope file changed/oversized");
        return parse(bytes, shaProperty, expectedSession, path);
    }
    static BlockMutationScope parse(byte[] bytes, String expectedSha, String expectedSession, Path path) {
        if (bytes.length < 2 || bytes.length > MAX_CONFIG_BYTES || !expectedSha.matches("[0-9a-f]{64}") ||
                !expectedSha.equals(sha256(bytes))) throw new IllegalArgumentException("immutable mutation scope SHA mismatch");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException malformed) { throw new IllegalArgumentException("non-UTF8 mutation scope", malformed); }
        rejectDuplicateKeys(text);
        JsonElement element = JsonParser.parseString(text);
        if (!element.isJsonObject()) throw new IllegalArgumentException("mutation scope must be object");
        JsonObject root = element.getAsJsonObject();
        Protocol.only(root, "schema", "scope_id", "campaign_id", "lineage_id", "epoch_id", "native_session_id",
                "purpose", "source", "source_checkpoint_id", "source_checkpoint_sha256", "positions", "max_events", "max_bytes");
        if (!SCHEMA.equals(Protocol.string(root, "schema")) ||
                !"controller_prelaunch".equals(Protocol.string(root, "source")) ||
                !canonicalUuidWithPrefix(Protocol.string(root, "scope_id"), "scope-") ||
                !canonicalUuidWithPrefix(Protocol.string(root, "campaign_id"), "campaign-") ||
                !canonicalUuidWithPrefix(Protocol.string(root, "lineage_id"), "") ||
                !Protocol.string(root, "epoch_id").matches("epoch-[0-9]{5}") ||
                !canonicalUuidWithPrefix(Protocol.string(root, "native_session_id"), "epoch-") ||
                !Protocol.string(root, "native_session_id").equals(expectedSession) ||
                !(Protocol.string(root, "purpose").equals("qualification") ||
                        Protocol.string(root, "purpose").equals("diagnostic")) ||
                !Protocol.string(root, "source_checkpoint_id").matches("[A-Za-z0-9._:-]{1,128}") ||
                !Protocol.string(root, "source_checkpoint_sha256").matches("[0-9a-f]{64}") ||
                Protocol.number(root, "max_events") != MAX_EVENTS || Protocol.number(root, "max_bytes") != MAX_BYTES)
            throw new IllegalArgumentException("mutation scope lifecycle/limits invalid");
        if (!root.get("positions").isJsonArray() || root.getAsJsonArray("positions").size() < 1 ||
                root.getAsJsonArray("positions").size() > MAX_POSITIONS)
            throw new IllegalArgumentException("bounded reviewed positions required");
        List<Position> positions = new ArrayList<>();
        Position prior = null;
        for (JsonElement item : root.getAsJsonArray("positions")) {
            if (!item.isJsonObject()) throw new IllegalArgumentException("nonobject mutation position");
            JsonObject p = item.getAsJsonObject();
            Protocol.only(p, "dimension", "x", "y", "z");
            String dimension = Protocol.string(p, "dimension");
            ResourceLocation parsed = ResourceLocation.tryParse(dimension);
            if (parsed == null || !parsed.toString().equals(dimension)) throw new IllegalArgumentException("invalid dimension");
            long x = Protocol.number(p, "x"), y = Protocol.number(p, "y"), z = Protocol.number(p, "z");
            if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000 || y < -2048 || y > 2048)
                throw new IllegalArgumentException("mutation position out of native bounds");
            Position position = new Position(dimension, (int) x, (int) y, (int) z);
            if (prior != null && prior.compareTo(position) >= 0)
                throw new IllegalArgumentException("mutation positions must be sorted unique");
            positions.add(position); prior = position;
        }
        return new BlockMutationScope(root, expectedSha, positions, path);
    }
    static BlockMutationScope fromBirth(BlockBirthScope birth) {
        JsonObject config = new JsonObject();
        config.addProperty("scope_id", birth.scopeId);
        config.addProperty("campaign_id", birth.campaignId);
        config.addProperty("lineage_id", birth.lineageId);
        config.addProperty("epoch_id", birth.epochId);
        config.addProperty("native_session_id", birth.nativeSessionId);
        config.addProperty("purpose", birth.purpose);
        config.addProperty("source_checkpoint_id", birth.sourceCheckpointId);
        config.addProperty("source_checkpoint_sha256", birth.sourceCheckpointSha256);
        return new BlockMutationScope(config, birth.configSha256, birth.positions, birth.sourceFile);
    }
    /** Gson's tree parser otherwise silently accepts duplicate keys; forbid them before interpreting the signed bytes. */
    static void rejectDuplicateKeys(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            readUnique(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing scope JSON");
        } catch (IOException malformed) { throw new IllegalArgumentException("invalid scope JSON", malformed); }
    }
    private static void readUnique(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); Set<String> keys = new HashSet<>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key)) throw new IllegalArgumentException("duplicate scope JSON key: " + key);
                    readUnique(reader);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) readUnique(reader);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("invalid scope JSON token");
        }
    }
    private static boolean canonicalUuidWithPrefix(String value, String prefix) {
        if (!value.startsWith(prefix)) return false;
        try { String uuid = value.substring(prefix.length()); return UUID.fromString(uuid).toString().equals(uuid); }
        catch (RuntimeException invalid) { return false; }
    }
    static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) builder.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return builder.toString();
        } catch (NoSuchAlgorithmException missing) { throw new IllegalStateException(missing); }
    }
}
