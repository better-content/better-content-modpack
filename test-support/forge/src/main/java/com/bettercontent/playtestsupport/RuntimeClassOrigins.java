package com.bettercontent.playtestsupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded loader-selected ORIGINAL artifact provenance. Never claims Mixin-transformed runtime bytecode. */
final class RuntimeClassOrigins {
    static final String CANDIDATE_PROPERTY = "bc.playtest.runtimeClassOriginsCandidateSha256";
    static final String SCHEMA = "bc.playtest_runtime_class_origins.v1";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_JAR = 128_000_000, MAX_CLASS = 1_048_576, MAX_FILE = 1_048_576;
    private static final List<String> TARGETS = List.of(
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.server.level.ChunkMap", "net.minecraft.server.level.ServerLevel",
            "net.minecraft.server.level.ServerPlayerGameMode",
            "net.minecraft.server.network.ServerGamePacketListenerImpl", "net.minecraft.network.Connection",
            "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData",
            "net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket$ChunkBiomeData",
            "net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.player.Player",
            "net.minecraft.world.damagesource.CombatRules",
            "net.minecraft.world.item.ItemStack", "net.minecraft.world.item.enchantment.EnchantmentHelper",
            "net.minecraftforge.common.ForgeHooks",
            "net.minecraft.world.level.chunk.ChunkAccess",
            "net.minecraft.world.level.chunk.LevelChunk", "net.minecraft.world.level.chunk.LevelChunkSection",
            "net.minecraft.world.level.chunk.PalettedContainer",
            "net.minecraft.world.level.chunk.storage.ChunkSerializer",
            "com.bettercontent.playtestsupport.BlockMutationTrace",
            "com.bettercontent.playtestsupport.BlockMutationOwnership",
            "com.bettercontent.playtestsupport.mixin.LevelChunkMutationObserverMixin",
            "com.bettercontent.playtestsupport.mixin.LevelChunkPaletteMutationObserverMixin",
            "com.bettercontent.playtestsupport.mixin.PalettedContainerMutationObserverMixin",
            "com.bettercontent.playtestsupport.mixin.ChunkAccessSectionsExposureMixin",
            "com.bettercontent.playtestsupport.mixin.LevelChunkBirthObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealPacketDigObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealGameModeDigObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealDestroyProgressObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealNetworkPacketIngressMixin",
            "com.bettercontent.playtestsupport.mixin.RealToolHurtBreakCauseObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealEquipmentBreakObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealIncomingPlayerDamageObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealIncomingArmorOperandsObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealIncomingMagicObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealIncomingEnchantmentObserverMixin",
            "com.bettercontent.playtestsupport.mixin.RealShutdownDrainObserverMixin",
            "com.bettercontent.betterdrinkingwater.WaterBottleCurio");
    private RuntimeClassOrigins() {}
    static JsonObject capture(Path mailbox, String session, String epochId, String scopeId) throws IOException {
        String expected = System.getProperty(CANDIDATE_PROPERTY, "");
        if (!expected.matches("[0-9a-f]{64}") || !session.matches("[A-Za-z0-9._:-]{1,128}") ||
                epochId == null || !epochId.matches("epoch-[0-9]{5}") ||
                scopeId == null || !scopeId.matches("scope-[0-9a-f-]{36}"))
            throw new IOException("class-origin capture requires controller-prelaunch candidate, epoch, session and scope");
        Path folder = mailbox.resolve("runtime-origins");
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(folder);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(folder))
            throw new IOException("class origins folder unsafe");
        String relative = "runtime-origins/" + session + ".json";
        Path output = mailbox.resolve(relative);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("stale runtime class origins sidecar");
        JsonObject row = new JsonObject(); row.addProperty("schema", SCHEMA);
        row.addProperty("native_session_id", session);
        row.addProperty("epoch_id", epochId);
        row.addProperty("scope_id", scopeId);
        row.addProperty("candidate_declared_sha256", expected);
        row.addProperty("candidate_declaration_verified_by_native", false);
        JsonArray classes = new JsonArray(); Map<Path, String> jarHashes = new HashMap<>();
        boolean all = true;
        for (String binaryName : TARGETS) {
            JsonObject target = origin(binaryName, jarHashes);
            if (!target.get("complete").getAsBoolean()) all = false;
            classes.add(target);
        }
        row.add("classes", classes);
        row.addProperty("complete", all);
        row.addProperty("transformed_live_bytes_attested", false);
        row.addProperty("mixin_application_proven", false);
        row.addProperty("natural_origin_proven_by_native", false);
        byte[] bytes = (JSON.toJson(row) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE) throw new IOException("class origin source sidecar oversized");
        Files.write(output, bytes, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        JsonObject pointer = new JsonObject(); pointer.addProperty("schema", SCHEMA);
        pointer.addProperty("native_session_id", session);
        pointer.addProperty("epoch_id", epochId);
        pointer.addProperty("scope_id", scopeId);
        pointer.addProperty("candidate_declared_sha256", expected);
        pointer.addProperty("path", relative); pointer.addProperty("bytes", bytes.length);
        pointer.addProperty("sha256", BlockMutationScope.sha256(bytes));
        pointer.addProperty("complete", all); pointer.addProperty("transformed_live_bytes_attested", false);
        pointer.addProperty("mixin_application_proven", false);
        return pointer;
    }
    private static JsonObject origin(String binaryName, Map<Path, String> jarHashes) {
        JsonObject row = new JsonObject(); row.addProperty("binary_name", binaryName);
        row.addProperty("resource_origin_kind", "untransformed_class_entry_NOT_runtime_transform");
        row.addProperty("transformed_live_bytes_attested", false);
        try {
            Class<?> type = Class.forName(binaryName, false, RuntimeClassOrigins.class.getClassLoader());
            ClassLoader loader = type.getClassLoader();
            if (loader == null) throw new IOException("bootstrap loader origin cannot be disambiguated");
            row.addProperty("class_loader_binary_name", loader.getClass().getName());
            CodeSource code = type.getProtectionDomain() == null ? null : type.getProtectionDomain().getCodeSource();
            URL location = code == null ? null : code.getLocation();
            URL resource = type.getResource("/" + binaryName.replace('.', '/') + ".class");
            if (location == null || resource == null) throw new IOException("loaded class origin or resource URL absent");
            row.addProperty("code_source_uri", location.toExternalForm());
            row.addProperty("class_resource_uri", resource.toExternalForm());
            URI uri = location.toURI();
            if (!"file".equals(uri.getScheme()) || !"jar".equals(resource.getProtocol()))
                throw new IOException("nonfile_or_nonjar_ambiguous_class_origin");
            Path jar = Path.of(uri);
            if (!jar.isAbsolute() || !jar.normalize().equals(jar) || Files.isSymbolicLink(jar) ||
                    !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.size(jar) < 1 ||
                    Files.size(jar) > MAX_JAR)
                throw new IOException("class origin jar not bounded ordinary file");
            for (Path parent = jar.getParent(); parent != null; parent = parent.getParent())
                if (Files.isSymbolicLink(parent)) throw new IOException("symlink class origin parent");
            String entryName = binaryName.replace('.', '/') + ".class";
            byte[] entry;
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry zipEntry = zip.getEntry(entryName);
                if (zipEntry == null || zipEntry.getSize() < 1 || zipEntry.getSize() > MAX_CLASS)
                    throw new IOException("loaded class entry absent/oversized");
                try (var stream = zip.getInputStream(zipEntry)) { entry = stream.readNBytes(MAX_CLASS + 1); }
            }
            if (entry.length < 1 || entry.length > MAX_CLASS)
                throw new IOException("class entry read outside cap");
            byte[] loaderResource;
            try (var stream = type.getResourceAsStream("/" + entryName)) {
                if (stream == null) throw new IOException("loader-selected class resource unreadable");
                loaderResource = stream.readNBytes(MAX_CLASS + 1);
            }
            if (loaderResource.length != entry.length || !java.util.Arrays.equals(loaderResource, entry))
                throw new IOException("loader resource differs from original selected JAR class entry");
            String jarHash = jarHashes.get(jar);
            if (jarHash == null) {
                jarHash = BlockMutationScope.sha256(Files.readAllBytes(jar)); jarHashes.put(jar, jarHash);
            }
            row.addProperty("ordinary_artifact_path", jar.toString());
            row.addProperty("artifact_sha256", jarHash);
            row.addProperty("untransformed_class_resource_sha256", BlockMutationScope.sha256(entry));
            row.addProperty("complete", true);
        } catch (Exception | LinkageError error) {
            row.add("ordinary_artifact_path", JsonNull.INSTANCE);
            row.add("artifact_sha256", JsonNull.INSTANCE);
            row.add("untransformed_class_resource_sha256", JsonNull.INSTANCE);
            row.addProperty("complete", false);
            row.addProperty("unsupported_reason", "class_origin:" + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
        return row;
    }
}
