package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Independent loaded-hostile read. Death source must be separately observed, not inferred from absence. */
final class EntitySnapshot {
    record Death(String type, String dimension, long tick, String source, boolean canceled) {}
    private EntitySnapshot() {}
    static JsonObject read(MinecraftServer server, JsonObject p, Death death) {
        Protocol.only(p, "entity_uuid", "target_type", "dimension");
        UUID id = UUID.fromString(Protocol.string(p, "entity_uuid"));
        if (!id.toString().equals(Protocol.string(p, "entity_uuid"))) throw new IllegalArgumentException("noncanonical entity UUID");
        String expectedType = Protocol.string(p, "target_type");
        String dimension = Protocol.string(p, "dimension");
        var key = net.minecraft.resources.ResourceLocation.tryParse(dimension);
        if (key == null) throw new IllegalArgumentException("invalid dimension");
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, key));
        if (level == null) throw new IllegalArgumentException("dimension not running");
        Entity entity = level.getEntity(id);
        JsonObject row = new JsonObject(); row.addProperty("entity_uuid", id.toString());
        row.addProperty("target_type", expectedType); row.addProperty("dimension", dimension);
        row.addProperty("death_observed", death != null && !death.canceled());
        row.addProperty("death_tick", death == null ? -1 : death.tick());
        row.addProperty("death_source", death == null ? "unobserved" : death.source());
        if (entity == null) {
            if (death != null && (!death.type().equals(expectedType) || !death.dimension().equals(dimension)))
                throw new IllegalArgumentException("recorded death entity type/dimension mismatch");
            row.addProperty("present", false);
            row.addProperty("absence_reason", death != null && !death.canceled() ? "native_death_event_seen" : "unknown_unload_or_removal");
            return row;
        }
        if (!(entity instanceof Monster target)) throw new IllegalArgumentException("loaded target is not a hostile");
        String actualType = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        if (!expectedType.equals(actualType)) throw new IllegalArgumentException("target entity type mismatch");
        row.addProperty("present", true); row.addProperty("alive", target.isAlive());
        row.addProperty("health", target.getHealth());
        row.addProperty("x", target.getX()); row.addProperty("y", target.getY()); row.addProperty("z", target.getZ());
        return row;
    }
}
