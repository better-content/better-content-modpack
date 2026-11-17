package com.bettercontent.playtestsupport.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.charset.StandardCharsets;

/** Read ONLY Minecraft.hitResult already supplied by vanilla rendering. NEVER pick/raycast/choose a block. */
final class ClientCrosshairObservation {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String DOMAIN = "bc.playtest.client-crosshair-hitresult.v1\n";
    private ClientCrosshairObservation() {}

    static JsonObject capture(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null || client.screen != null ||
                client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL ||
                !client.mouseHandler.isMouseGrabbed() || !(client.hitResult instanceof BlockHitResult hit) ||
                hit.getType() != HitResult.Type.BLOCK)
            throw new IllegalStateException("joined Survival vanilla block hitResult/mouse grab absent");
        var pos = hit.getBlockPos(); var location = hit.getLocation(); var eye = client.player.getEyePosition();
        if (Math.abs((long) pos.getX()) > 30_000_000 || Math.abs((long) pos.getZ()) > 30_000_000 ||
                pos.getY() < client.level.getMinBuildHeight() || pos.getY() >= client.level.getMaxBuildHeight() ||
                !Double.isFinite(location.x) || !Double.isFinite(location.y) || !Double.isFinite(location.z) ||
                !Double.isFinite(eye.x) || !Double.isFinite(eye.y) || !Double.isFinite(eye.z) ||
                eye.distanceToSqr(location) > 36.0 || eye.distanceToSqr(location) < 0)
            throw new IllegalStateException("vanilla hitResult is outside bounded real client geometry");
        JsonObject target = target(client.player.getUUID().toString(), client.level.dimension().location().toString(),
            pos.getX(), pos.getY(), pos.getZ(), hit.getDirection().getName(),
            location.x, location.y, location.z);
        JsonObject row = new JsonObject();
        row.addProperty("crosshair_source", "Minecraft.hitResult/BlockHitResult/read_only_existing_render_target");
        row.addProperty("crosshair_projection_sha256", projectionSha256(target));
        row.add("crosshair", target);
        row.addProperty("crosshair_is_natural_bound_target", false);
        row.addProperty("crosshair_packet_transport_proven", false);
        return row;
    }
    /** Direct projection only; no world block-state query, target search or new raycast. */
    static JsonObject target(String player, String dimension, int x, int y, int z, String face,
                             double hitX, double hitY, double hitZ) {
        if (player == null || !java.util.UUID.fromString(player).toString().equals(player) ||
                !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ||
                Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 ||
                y < -2048 || y > 2048 || !java.util.Set.of("north", "south", "east", "west", "up", "down").contains(face) ||
                !Double.isFinite(hitX) || !Double.isFinite(hitY) || !Double.isFinite(hitZ) ||
                Math.abs(hitX - x) > 1.00001 || Math.abs(hitY - y) > 1.00001 || Math.abs(hitZ - z) > 1.00001)
            throw new IllegalArgumentException("unsupported immutable in-world crosshair projection");
        JsonObject row = new JsonObject(); row.addProperty("player_uuid", player); row.addProperty("dimension", dimension);
        row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z); row.addProperty("face", face);
        row.addProperty("hit_x", hitX); row.addProperty("hit_y", hitY); row.addProperty("hit_z", hitZ);
        return row;
    }
    static String projectionSha256(JsonObject target) {
        if (target == null || target.size() != 9) throw new IllegalArgumentException("crosshair projection not canonical");
        return ClientNativeSupport.sha256((DOMAIN + JSON.toJson(target)).getBytes(StandardCharsets.UTF_8));
    }
    static boolean sameCurrentTarget(Minecraft client, String frozenProjection) {
        try { return projectionSha256(capture(client).getAsJsonObject("crosshair")).equals(frozenProjection); }
        catch (RuntimeException unsupportedOrLost) { return false; }
    }
}
