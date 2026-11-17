package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Scoped native Survival melee against an already-present hostile; does not spawn or grant loot. */
final class NativeAttack {
    private NativeAttack() {}
    static JsonObject attack(ServerPlayer player, JsonObject p) {
        Protocol.only(p, "player_uuid", "target_uuid", "target_type");
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL)
            throw new IllegalArgumentException("Survival actor required");
        UUID id = UUID.fromString(Protocol.string(p, "target_uuid"));
        if (!id.toString().equals(Protocol.string(p, "target_uuid"))) throw new IllegalArgumentException("noncanonical target UUID");
        Entity entity = player.serverLevel().getEntity(id);
        if (!(entity instanceof Monster target) || !target.isAlive() || target.isRemoved())
            throw new IllegalArgumentException("loaded living hostile target required");
        String type = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        if (!type.equals(Protocol.string(p, "target_type"))) throw new IllegalArgumentException("target entity type mismatch");
        if (player.distanceToSqr(target) > 9 || !player.hasLineOfSight(target))
            throw new IllegalArgumentException("target outside normal reach or sight");
        float targetHealth = target.getHealth(), playerHealth = player.getHealth();
        int heldDamage = player.getMainHandItem().getDamageValue();
        player.attack(target); // Real Player.attack: no synthetic damage, invulnerability bypass or entity spawn.
        JsonObject row = new JsonObject(); row.addProperty("player_uuid", player.getUUID().toString());
        row.addProperty("target_uuid", id.toString()); row.addProperty("target_type", type);
        row.addProperty("target_health_before", targetHealth);
        row.addProperty("target_health_after", target.getHealth());
        row.addProperty("target_alive_after", target.isAlive());
        row.addProperty("player_health_before", playerHealth);
        row.addProperty("player_health_after", player.getHealth());
        row.addProperty("held_damage_before", heldDamage);
        row.addProperty("held_damage_after", player.getMainHandItem().getDamageValue());
        row.addProperty("attack_source", "ServerPlayer.attack");
        row.add("fixture_mutations", new JsonArray());
        return row;
    }
}
