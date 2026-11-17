package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

/** Loaded-only target census, not selection, natural ancestry, or acquisition evidence. Server thread only. */
final class LoadedTargetObservation {
    static final String DRIVER = "target.observe.loaded_v1";
    static final String SCHEMA = "bc.playtest_loaded_target_census.v1";
    static final int MAX_BLOCK_POSITIONS = 4096, MAX_CANDIDATES = 4096, MAX_BYTES = 16_777_216;
    record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int distance) {
        Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ || distance < 1 || distance > 2048 ||
                    minX < -30_000_000 || maxX > 30_000_000 || minZ < -30_000_000 || maxZ > 30_000_000 ||
                    minY < -2048 || maxY > 2048)
                throw new IllegalArgumentException("reviewed bounds invalid");
        }
        long positions() {
            try { return Math.multiplyExact(Math.multiplyExact((long) maxX - minX + 1,
                    (long) maxY - minY + 1), (long) maxZ - minZ + 1); }
            catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
        }
        boolean entityExtentValid() { return maxX - minX < 128 && maxY - minY < 128 && maxZ - minZ < 128; }
        int minChunkX() { return minX >> 4; }
        int maxChunkX() { return maxX >> 4; }
        int minChunkZ() { return minZ >> 4; }
        int maxChunkZ() { return maxZ >> 4; }
    }
    private LoadedTargetObservation() {}
    private static final class CensusLimit extends RuntimeException { private static final long serialVersionUID = 1L; }

    static Bounds parseBounds(JsonObject json) {
        Protocol.only(json, "min_x", "max_x", "min_y", "max_y", "min_z", "max_z", "maximum_distance_blocks");
        return new Bounds(integer(json, "min_x"), integer(json, "max_x"), integer(json, "min_y"),
                integer(json, "max_y"), integer(json, "min_z"), integer(json, "max_z"),
                integer(json, "maximum_distance_blocks"));
    }
    private static int integer(JsonObject object, String key) {
        long value = Protocol.number(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("integer bounds " + key);
        return (int) value;
    }
    static boolean sizeValid(Bounds bounds, String kind) {
        return switch (kind) {
            case "block" -> bounds.positions() <= MAX_BLOCK_POSITIONS;
            case "living_entity" -> bounds.entityExtentValid();
            default -> false;
        };
    }
    static boolean canAppend(int existingBytes, int rowBytes, int priorCount, int cap) {
        return priorCount < cap && (long) existingBytes + rowBytes + 4096 <= MAX_BYTES;
    }
    static boolean complete(boolean allChunksLoaded, boolean overflow, boolean error) {
        return allChunksLoaded && !overflow && !error;
    }
    static JsonObject capture(Protocol.Request request, ServerPlayer actor, JsonObject parameters, long tick) {
        Protocol.only(parameters, "player_uuid", "dimension", "bounds", "kind", "resource_id",
                "selector_sha256", "max_candidates", "witness_context");
        JsonObject context = NativeWitnessContext.validatedContext(request, parameters, actor.getUUID());
        String dimension = Protocol.string(parameters, "dimension");
        if (!dimension.equals(actor.serverLevel().dimension().location().toString()))
            throw new IllegalArgumentException("actor in different dimension");
        String digest = Protocol.string(parameters, "selector_sha256");
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("selector digest invalid");
        String kind = Protocol.string(parameters, "kind"), resource = Protocol.string(parameters, "resource_id");
        ResourceLocation id = ResourceLocation.tryParse(resource);
        if (id == null || !id.toString().equals(resource)) throw new IllegalArgumentException("invalid exact target resource id");
        if (!parameters.get("bounds").isJsonObject()) throw new IllegalArgumentException("reviewed bounds object required");
        Bounds bounds = parseBounds(parameters.getAsJsonObject("bounds"));
        if (!sizeValid(bounds, kind)) throw new IllegalArgumentException("reviewed scan size/kind exceeds native bound");
        int cap = integer(parameters, "max_candidates");
        if (cap < 1 || cap > MAX_CANDIDATES) throw new IllegalArgumentException("candidate cap invalid");
        ServerLevel level = actor.serverLevel();
        if (bounds.minY < level.getMinBuildHeight() || bounds.maxY >= level.getMaxBuildHeight())
            throw new IllegalArgumentException("bounds outside actual build height");
        JsonObject result = new JsonObject();
        result.addProperty("schema", SCHEMA);
        result.add("witness_context", context);
        result.addProperty("selector_sha256", digest);
        result.addProperty("dimension", dimension);
        result.addProperty("kind", kind);
        result.addProperty("resource_id", resource);
        result.add("bounds", parameters.getAsJsonObject("bounds").deepCopy());
        result.addProperty("max_candidates", cap);
        result.addProperty("query_tick", tick);
        result.addProperty("query_monotonic_nanos", System.nanoTime());
        JsonObject position = new JsonObject();
        position.addProperty("uuid", actor.getUUID().toString());
        position.addProperty("x", actor.getX()); position.addProperty("y", actor.getY()); position.addProperty("z", actor.getZ());
        result.add("actor_at_query", position);
        result.addProperty("natural_origin_proven_by_native", false);
        result.addProperty("natural_acquisition_proven_by_native", false);
        JsonArray rows = new JsonArray(); result.add("candidates", rows);
        // Validate every chunk before reading ANY candidate. getChunkNow returns only already-loaded LevelChunks.
        int loaded = 0;
        for (int cx = bounds.minChunkX(); cx <= bounds.maxChunkX(); cx++) {
            for (int cz = bounds.minChunkZ(); cz <= bounds.maxChunkZ(); cz++) {
                boolean present;
                try { present = level.getChunkSource().getChunkNow(cx, cz) != null; }
                catch (Exception error) {
                    result.addProperty("complete", false);
                    result.addProperty("incomplete_reason", "chunk_query_failed:" + error.getClass().getSimpleName());
                    result.addProperty("chunks_checked", loaded + 1);
                    result.addProperty("candidate_count", 0);
                    result.addProperty("query_end_tick", tick);
                    result.addProperty("query_end_monotonic_nanos", System.nanoTime());
                    return result;
                }
                if (!present) {
                    result.addProperty("complete", false);
                    result.addProperty("incomplete_reason", "queried_chunk_not_loaded");
                    result.addProperty("unloaded_chunk_x", cx); result.addProperty("unloaded_chunk_z", cz);
                    result.addProperty("chunks_checked", loaded + 1);
                    result.addProperty("candidate_count", 0);
                    result.addProperty("query_end_tick", tick);
                    result.addProperty("query_end_monotonic_nanos", System.nanoTime());
                    return result;
                }
                loaded++;
            }
        }
        result.addProperty("chunks_checked", loaded);
        int bytes = result.toString().getBytes(StandardCharsets.UTF_8).length;
        String reason = null;
        try {
            if (kind.equals("block")) {
                outer: for (int x = bounds.minX; x <= bounds.maxX; x++)
                    for (int z = bounds.minZ; z <= bounds.maxZ; z++)
                        for (int y = bounds.minY; y <= bounds.maxY; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (!resource.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString())) continue;
                            JsonObject row = new JsonObject();
                            row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z);
                            row.addProperty("observed_tick", tick);
                            row.addProperty("observed_monotonic_nanos", System.nanoTime());
                            row.addProperty("actor_distance_blocks", Math.sqrt(actor.distanceToSqr(Vec3.atCenterOf(pos))));
                            row.add("block_state", state(state));
                            int rowBytes = row.toString().getBytes(StandardCharsets.UTF_8).length;
                            if (!canAppend(bytes, rowBytes, rows.size(), cap)) { reason = "candidate_or_byte_cap_exceeded"; break outer; }
                            rows.add(row); bytes += rowBytes + 1;
                        }
            } else {
                // World entity index is already loaded; bounded box and cap are independent of controller selection.
                AABB box = new AABB(bounds.minX, bounds.minY, bounds.minZ,
                        (double) bounds.maxX + 1, (double) bounds.maxY + 1, (double) bounds.maxZ + 1);
                // Enumerate loaded index into a capped list, never materialize an unbounded getEntitiesOfClass result.
                var entities = new ArrayList<LivingEntity>();
                boolean entityOverflow = false;
                try {
                    level.getEntities().get(box, e -> {
                        if (e instanceof LivingEntity living &&
                                resource.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString())) {
                            if (entities.size() >= cap) throw new CensusLimit();
                            entities.add(living);
                        }
                    });
                } catch (CensusLimit limit) { entityOverflow = true; }
                // Event ordering is NOT inferred from this roster. Sort only for reproducible raw census serialization.
                entities.sort(Comparator.comparing(e -> e.getUUID().toString()));
                for (LivingEntity entity : entities) {
                    JsonObject row = new JsonObject();
                    row.addProperty("uuid", entity.getUUID().toString());
                    row.addProperty("x", entity.getX()); row.addProperty("y", entity.getY()); row.addProperty("z", entity.getZ());
                    row.addProperty("observed_tick", tick);
                    row.addProperty("observed_monotonic_nanos", System.nanoTime());
                    row.addProperty("actor_distance_blocks", Math.sqrt(actor.distanceToSqr(entity)));
                    row.addProperty("alive", entity.isAlive()); row.addProperty("removed", entity.isRemoved());
                    row.addProperty("health", entity.getHealth()); row.addProperty("max_health", entity.getMaxHealth());
                    int rowBytes = row.toString().getBytes(StandardCharsets.UTF_8).length;
                    if (!canAppend(bytes, rowBytes, rows.size(), cap)) { reason = "candidate_or_byte_cap_exceeded"; break; }
                    rows.add(row); bytes += rowBytes + 1;
                }
                if (entityOverflow && reason == null) reason = "candidate_cap_exceeded";
            }
        } catch (Exception error) {
            reason = "query_failed:" + error.getClass().getSimpleName();
        }
        result.addProperty("candidate_count", rows.size());
        result.addProperty("query_end_tick", tick);
        result.addProperty("query_end_monotonic_nanos", System.nanoTime());
        result.addProperty("complete", complete(true, reason != null, false));
        if (reason != null) result.addProperty("incomplete_reason", reason);
        return result;
    }
    private static JsonObject state(BlockState state) {
        JsonObject row = new JsonObject(), properties = new JsonObject();
        row.addProperty("id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
        state.getProperties().stream().sorted(Comparator.comparing(Property::getName))
                .forEach(property -> properties.addProperty(property.getName(), state.getValue(property).toString()));
        row.add("properties", properties);
        return row;
    }
}
