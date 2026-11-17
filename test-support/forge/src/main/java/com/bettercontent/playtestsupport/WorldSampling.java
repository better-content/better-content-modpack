package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Passive loaded-only world/climate/tree-candidate observation; no chunk generation or fixture placement. */
final class WorldSampling {
    private WorldSampling() {}
    static JsonObject sample(MinecraftServer server, JsonObject p) {
        Protocol.only(p, "dimension", "x", "y", "z", "radius");
        ResourceLocation id = ResourceLocation.tryParse(Protocol.string(p, "dimension"));
        if (id == null) throw new IllegalArgumentException("invalid dimension");
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) throw new IllegalArgumentException("dimension not running");
        int x = coordinate(p, "x"), y = coordinate(p, "y"), z = coordinate(p, "z");
        long rawRadius = Protocol.number(p, "radius");
        if (rawRadius < 0 || rawRadius > 4) throw new IllegalArgumentException("radius must be 0..4");
        int radius = (int) rawRadius;
        ActionLimits.sampleCells(radius);
        BlockPos center = new BlockPos(x, y, z);
        if (!level.isInWorldBounds(center) || !loaded(level, center))
            throw new IllegalArgumentException("center must be an already loaded in-bounds block");
        JsonObject result = new JsonObject();
        result.addProperty("dimension", id.toString()); result.addProperty("x", x);
        result.addProperty("y", y); result.addProperty("z", z);
        result.addProperty("radius", radius); result.addProperty("game_time", level.getGameTime());
        result.addProperty("world_spawn_x", level.getSharedSpawnPos().getX());
        result.addProperty("world_spawn_y", level.getSharedSpawnPos().getY());
        result.addProperty("world_spawn_z", level.getSharedSpawnPos().getZ());
        result.addProperty("center_biome", biomeId(level, center));
        result.addProperty("base_temperature", level.getBiome(center).value().getBaseTemperature());
        result.addProperty("downfall", level.getBiome(center).value().getModifiedClimateSettings().downfall());
        result.addProperty("precipitation", level.getBiome(center).value().getPrecipitationAt(center).name());
        result.addProperty("raining_at_center", level.isRainingAt(center));
        JsonArray samples = new JsonArray();
        int skipped = 0, logs = 0, leaves = 0;
        // Three adjacent vertical slices expose nearby canopy/root candidates without pretending
        // to classify whole trees or proving that the site's biome was naturally generated.
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = center.offset(dx, dy, dz);
                if (!level.isInWorldBounds(pos) || !loaded(level, pos)) { skipped++; continue; }
                BlockState state = level.getBlockState(pos);
                JsonObject row = new JsonObject();
                row.addProperty("x", pos.getX()); row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
                row.addProperty("biome", biomeId(level, pos));
                row.addProperty("block_id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
                row.addProperty("log_tag", state.is(BlockTags.LOGS));
                row.addProperty("leaves_tag", state.is(BlockTags.LEAVES));
                if (state.is(BlockTags.LOGS)) logs++;
                if (state.is(BlockTags.LEAVES)) leaves++;
                samples.add(row);
            }
        result.add("loaded_samples", samples); result.addProperty("unloaded_positions", skipped);
        result.addProperty("log_tag_blocks", logs); result.addProperty("leaves_tag_blocks", leaves);
        result.addProperty("tree_classification", "unverified_candidate_blocks_only");
        return result;
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    private static int coordinate(JsonObject p, String key) {
        long value = Protocol.number(p, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("coordinate overflow " + key);
        return (int) value;
    }
    private static String biomeId(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.location().toString())
                .orElseThrow(() -> new IllegalStateException("effective biome has no registry key"));
    }
}
