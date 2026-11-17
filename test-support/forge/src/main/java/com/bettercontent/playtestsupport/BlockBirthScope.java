package com.bettercontent.playtestsupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Separate controller-signed PRELAUNCH watchlist for parent-absent chunk birth, never a natural verdict. */
final class BlockBirthScope {
    static final String SCHEMA = "bc.playtest_block_birth_scope.v1";
    static final String FILE_PROPERTY = "bc.playtest.blockBirthScopeFile";
    static final String SHA_PROPERTY = "bc.playtest.blockBirthScopeSha256";
    static final int MAX_CHUNKS = 64, MAX_POSITIONS = 64;
    record ChunkKey(String dimension, int chunkX, int chunkZ) implements Comparable<ChunkKey> {
        @Override public int compareTo(ChunkKey next) {
            int dim = dimension.compareTo(next.dimension);
            if (dim != 0) return dim;
            int x = Integer.compare(chunkX, next.chunkX);
            return x != 0 ? x : Integer.compare(chunkZ, next.chunkZ);
        }
    }
    final String scopeId, configSha256, campaignId, lineageId, epochId, nativeSessionId,
            purpose, sourceCheckpointId, sourceCheckpointSha256, parentAbsenceSidecarSha256;
    final List<ChunkKey> chunkKeys;
    final List<BlockMutationScope.Position> positions;
    final Set<ChunkKey> chunkSet;
    final Path sourceFile;
    private BlockBirthScope(JsonObject row, String hash, List<ChunkKey> chunks,
                            List<BlockMutationScope.Position> positions, Path sourceFile) {
        scopeId = Protocol.string(row, "scope_id"); configSha256 = hash;
        campaignId = Protocol.string(row, "campaign_id"); lineageId = Protocol.string(row, "lineage_id");
        epochId = Protocol.string(row, "epoch_id"); nativeSessionId = Protocol.string(row, "native_session_id");
        purpose = Protocol.string(row, "purpose");
        sourceCheckpointId = Protocol.string(row, "source_checkpoint_id");
        sourceCheckpointSha256 = Protocol.string(row, "source_checkpoint_sha256");
        parentAbsenceSidecarSha256 = Protocol.string(row, "parent_absence_sidecar_sha256");
        chunkKeys = List.copyOf(chunks); chunkSet = Set.copyOf(chunks); this.positions = List.copyOf(positions);
        this.sourceFile = sourceFile;
    }
    static BlockBirthScope open(Path mailbox, String expectedSession) throws IOException {
        String file = System.getProperty(FILE_PROPERTY), sha = System.getProperty(SHA_PROPERTY);
        if (file == null && sha == null) return null;
        if (file == null || sha == null || file.isBlank() || sha.isBlank())
            throw new IOException("birth scope requires exact paired immutable prelaunch properties");
        Path path;
        try { path = Path.of(file); } catch (RuntimeException error) { throw new IOException("birth scope path invalid", error); }
        if (!path.isAbsolute() || !path.normalize().equals(path) || mailbox == null || path.startsWith(mailbox) ||
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IOException("birth scope must be ordinary absolute non-mailbox file");
        for (Path parent = path.getParent(); parent != null; parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw new IOException("symlink in birth scope path");
        if (Files.size(path) > BlockMutationScope.MAX_CONFIG_BYTES) throw new IOException("birth scope too large");
        byte[] bytes = Files.readAllBytes(path);
        return parse(bytes, sha, expectedSession, path);
    }
    static BlockBirthScope parse(byte[] bytes, String expectedSha, String expectedSession, Path path) {
        if (bytes.length < 2 || bytes.length > BlockMutationScope.MAX_CONFIG_BYTES ||
                !expectedSha.matches("[0-9a-f]{64}") || !expectedSha.equals(BlockMutationScope.sha256(bytes)))
            throw new IllegalArgumentException("birth config changed or SHA mismatch");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception invalid) { throw new IllegalArgumentException("birth config non-UTF8", invalid); }
        BlockMutationScope.rejectDuplicateKeys(text);
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("birth config not object");
        JsonObject row = parsed.getAsJsonObject();
        Protocol.only(row, "schema", "scope_id", "campaign_id", "lineage_id", "epoch_id", "native_session_id",
                "purpose", "source", "source_checkpoint_id", "source_checkpoint_sha256",
                "parent_absence_sidecar_sha256", "chunk_keys", "positions", "max_events", "max_bytes");
        if (!SCHEMA.equals(Protocol.string(row, "schema")) ||
                !"controller_prelaunch".equals(Protocol.string(row, "source")) ||
                !uuid(Protocol.string(row, "scope_id"), "scope-") ||
                !uuid(Protocol.string(row, "campaign_id"), "campaign-") ||
                !uuid(Protocol.string(row, "lineage_id"), "") ||
                !Protocol.string(row, "epoch_id").matches("epoch-[0-9]{5}") ||
                !uuid(Protocol.string(row, "native_session_id"), "epoch-") ||
                !Protocol.string(row, "native_session_id").equals(expectedSession) ||
                !Set.of("qualification", "diagnostic").contains(Protocol.string(row, "purpose")) ||
                !Protocol.string(row, "source_checkpoint_id").matches("[A-Za-z0-9._:-]{1,128}") ||
                !Protocol.string(row, "source_checkpoint_sha256").matches("[0-9a-f]{64}") ||
                !Protocol.string(row, "parent_absence_sidecar_sha256").matches("[0-9a-f]{64}") ||
                Protocol.number(row, "max_events") != BlockMutationScope.MAX_EVENTS ||
                Protocol.number(row, "max_bytes") != BlockMutationScope.MAX_BYTES)
            throw new IllegalArgumentException("birth lifecycle/parent absence/limits invalid");
        if (!row.get("chunk_keys").isJsonArray() || row.getAsJsonArray("chunk_keys").size() < 1 ||
                row.getAsJsonArray("chunk_keys").size() > MAX_CHUNKS || !row.get("positions").isJsonArray() ||
                row.getAsJsonArray("positions").size() < 1 || row.getAsJsonArray("positions").size() > MAX_POSITIONS)
            throw new IllegalArgumentException("birth chunks/positions outside reviewed caps");
        List<ChunkKey> chunks = new ArrayList<>(); ChunkKey priorChunk = null;
        for (JsonElement cell : row.getAsJsonArray("chunk_keys")) {
            if (!cell.isJsonObject()) throw new IllegalArgumentException("birth chunk key not object");
            JsonObject key = cell.getAsJsonObject(); Protocol.only(key, "dimension", "chunk_x", "chunk_z");
            String dim = dimension(key, "dimension");
            long x = Protocol.number(key, "chunk_x"), z = Protocol.number(key, "chunk_z");
            if (x < -1_875_000 || x > 1_875_000 || z < -1_875_000 || z > 1_875_000)
                throw new IllegalArgumentException("birth chunk outside world bounds");
            ChunkKey chunk = new ChunkKey(dim, (int) x, (int) z);
            if (priorChunk != null && priorChunk.compareTo(chunk) >= 0)
                throw new IllegalArgumentException("birth chunks unsorted or repeated");
            chunks.add(chunk); priorChunk = chunk;
        }
        Set<ChunkKey> present = new HashSet<>(); List<BlockMutationScope.Position> positions = new ArrayList<>();
        BlockMutationScope.Position prior = null;
        for (JsonElement cell : row.getAsJsonArray("positions")) {
            if (!cell.isJsonObject()) throw new IllegalArgumentException("birth position not object");
            JsonObject key = cell.getAsJsonObject(); Protocol.only(key, "dimension", "x", "y", "z");
            String dim = dimension(key, "dimension");
            long x = Protocol.number(key, "x"), y = Protocol.number(key, "y"), z = Protocol.number(key, "z");
            if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000 || y < -2048 || y > 2048)
                throw new IllegalArgumentException("birth position outside world bounds");
            BlockMutationScope.Position pos = new BlockMutationScope.Position(dim, (int) x, (int) y, (int) z);
            ChunkKey owner = new ChunkKey(dim, ((int) x) >> 4, ((int) z) >> 4);
            if (!chunks.contains(owner) || prior != null && prior.compareTo(pos) >= 0)
                throw new IllegalArgumentException("birth position outside declared sorted distinct chunks");
            present.add(owner); positions.add(pos); prior = pos;
        }
        if (present.size() != chunks.size()) throw new IllegalArgumentException("birth key lacks actual watched position");
        return new BlockBirthScope(row, expectedSha, chunks, positions, path);
    }
    private static String dimension(JsonObject key, String field) {
        String value = Protocol.string(key, field);
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null || !parsed.toString().equals(value) ||
                !Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end").contains(value))
            throw new IllegalArgumentException("unsupported nonvanilla birth dimension");
        return value;
    }
    private static boolean uuid(String value, String prefix) {
        if (!value.startsWith(prefix)) return false;
        try { String id = value.substring(prefix.length()); return UUID.fromString(id).toString().equals(id); }
        catch (RuntimeException invalid) { return false; }
    }
}
