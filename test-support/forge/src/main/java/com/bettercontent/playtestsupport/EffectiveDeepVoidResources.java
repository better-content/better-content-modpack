package com.bettercontent.playtestsupport;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Read-only effective ResourceManager observation, not a claim that a weapon was obtained or used. */
final class EffectiveDeepVoidResources {
    private static final List<String> PATHS = List.of("weapon_attributes/penitent_cross.json",
            "weapon_attributes/the_catcher.json");
    private EffectiveDeepVoidResources() {}

    static JsonObject capture(MinecraftServer server) throws Exception {
        JsonObject observed = new JsonObject();
        for (String path : PATHS) {
            ResourceLocation id = new ResourceLocation("the_deep_void", path);
            JsonObject row = new JsonObject();
            row.addProperty("resource", id.toString());
            var resource = server.getResourceManager().getResource(id);
            row.addProperty("available", resource.isPresent());
            if (resource.isPresent()) {
                row.addProperty("source_pack_id", resource.get().sourcePackId());
                try (InputStream input = resource.get().open()) {
                    byte[] bytes = input.readNBytes(65_537);
                    if (bytes.length > 65_536 || input.read() != -1)
                        throw new IOException("effective Deep Void resource exceeds 64 KiB: " + id);
                    row.addProperty("bytes", bytes.length);
                    row.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                }
            }
            observed.add(path, row);
        }
        return observed;
    }
}
