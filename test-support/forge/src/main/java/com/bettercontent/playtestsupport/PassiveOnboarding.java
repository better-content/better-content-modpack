package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

/** Read-only pinned provider handling, pre-commit raw column, committed site and applied final state. */
final class PassiveOnboarding {
    static final String ARM = "player.observe_onboarding.arm", AWAIT = "player.observe_onboarding.await";
    static final int MAX_DEADLINE_TICKS = 12000;
    static final String PROVIDER = "better_spawns:FinalizeSelectionPacket", MODE = "none";
    private static final String PACKET_CLASS = "com.bettercontent.betterspawns.network.FinalizeSelectionPacket";
    private static final String EVENT_CLASS = "com.bettercontent.betterspawns.integration.PlayerStartFinalizedEvent";
    private static final String INTEGRATION_CLASS = "com.bettercontent.betterspawns.integration.OnboardingIntegration";
    private static final int MAX_COLUMN_HEIGHT = 384, MAX_COLUMN_BYTES = 128 * 1024;
    final PassiveObserverBinding binding;
    final String providerArtifactSha256, providerConfigSha256;
    final JsonObject requestBounds, actorAtArm;
    final long armTick;
    private final Bounds reviewed;
    private Site requested, committed;
    private JsonObject actorAtRequest, terrainPreCommit, persistedAtEvent, persistedFinal,
            vanillaRespawnSite, actualFinalActor, siteAfter;
    private long packetTick = -1, finalizationEventTick = -1, handlerReturnTick = -1, finalTick = -1;
    private int packetCount, finalizationCount, handlerReturnCount;
    private String failure, actualKit, finalMode;
    private boolean finalComplete;

    private record Site(String dimension, int x, int y, int z) {
        String wire() { return dimension + "@" + x + "," + y + "," + z; }
        JsonObject json() {
            JsonObject row = new JsonObject(); row.addProperty("dimension", dimension);
            row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z); return row;
        }
        static Site parse(String wire) {
            int at = wire.indexOf('@');
            if (at <= 0 || at != wire.lastIndexOf('@')) throw new IllegalArgumentException("malformed committed site");
            String[] coords = wire.substring(at + 1).split(",", -1);
            if (coords.length != 3) throw new IllegalArgumentException("committed site coordinates malformed");
            for (String coord : coords)
                if (!coord.matches("-?(0|[1-9][0-9]{0,9})")) throw new IllegalArgumentException("committed site coordinate invalid");
            Site site = new Site(wire.substring(0, at), Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
            if (!site.wire().equals(wire)) throw new IllegalArgumentException("noncanonical committed site");
            return site;
        }
    }
    private record Bounds(String dimension, int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        boolean contains(Site site) {
            return dimension.equals(site.dimension) && site.x >= xMin && site.x <= xMax &&
                    site.y >= yMin && site.y <= yMax && site.z >= zMin && site.z <= zMax;
        }
        JsonObject json() {
            JsonObject row = new JsonObject(); row.addProperty("dimension", dimension);
            row.addProperty("x_min", xMin); row.addProperty("x_max", xMax);
            row.addProperty("y_min", yMin); row.addProperty("y_max", yMax);
            row.addProperty("z_min", zMin); row.addProperty("z_max", zMax); return row;
        }
        static Bounds parse(JsonObject p, ServerPlayer actor) {
            if (!p.has("request_bounds") || !p.get("request_bounds").isJsonObject())
                throw new IllegalArgumentException("typed reviewed request bounds required");
            JsonObject area = p.getAsJsonObject("request_bounds");
            Protocol.only(area, "dimension", "x_min", "x_max", "y_min", "y_max", "z_min", "z_max");
            String dimension = Protocol.string(area, "dimension");
            int xMin = Math.toIntExact(Protocol.number(area, "x_min")), xMax = Math.toIntExact(Protocol.number(area, "x_max"));
            int yMin = Math.toIntExact(Protocol.number(area, "y_min")), yMax = Math.toIntExact(Protocol.number(area, "y_max"));
            int zMin = Math.toIntExact(Protocol.number(area, "z_min")), zMax = Math.toIntExact(Protocol.number(area, "z_max"));
            long width = (long) xMax - xMin + 1, height = (long) yMax - yMin + 1, depth = (long) zMax - zMin + 1;
            if (!dimension.equals(actor.serverLevel().dimension().location().toString()) ||
                    !boundedArea(width, height, depth) ||
                    yMin < actor.serverLevel().getMinBuildHeight() || yMax >= actor.serverLevel().getMaxBuildHeight() ||
                    !new Bounds(dimension, xMin, xMax, yMin, yMax, zMin, zMax).contains(
                            new Site(dimension, actor.getBlockX(), actor.getBlockY(), actor.getBlockZ())))
                throw new IllegalArgumentException("request area not reviewed, bounded, same dimension and actor-centered");
            return new Bounds(dimension, xMin, xMax, yMin, yMax, zMin, zMax);
        }
    }
    private PassiveOnboarding(PassiveObserverBinding binding, JsonObject p, Bounds reviewed,
                              JsonObject actorAtArm, long tick) {
        this.binding = binding; this.reviewed = reviewed; this.requestBounds = reviewed.json();
        this.actorAtArm = actorAtArm; this.armTick = tick;
        providerArtifactSha256 = Protocol.string(p, "provider_artifact_sha256");
        providerConfigSha256 = Protocol.string(p, "provider_config_sha256");
    }
    static PassiveOnboarding arm(Protocol.Request request, ServerPlayer actor, JsonObject p, long tick) {
        Protocol.only(p, "player_uuid", "dimension", "observer_id", "expected_provider", "expected_mode",
                "request_bounds", "provider_artifact_sha256", "provider_config_sha256",
                "deadline_ticks", "witness_context");
        if (!ARM.equals(request.driver()) || actor.connection == null ||
                actor.gameMode.getGameModeForPlayer() != GameType.SPECTATOR ||
                !PROVIDER.equals(Protocol.string(p, "expected_provider")) ||
                !MODE.equals(Protocol.string(p, "expected_mode")) || !ModList.get().isLoaded("better_spawns"))
            throw new IllegalArgumentException("pinned Better Spawns NONE spectator onboarding required");
        for (String hash : List.of(Protocol.string(p, "provider_artifact_sha256"),
                Protocol.string(p, "provider_config_sha256")))
            if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("reviewed provider pin invalid");
        Bounds area = Bounds.parse(p, actor);
        try {
            if (completed(actor) || !"NONE".equals(cachedMode()))
                throw new IllegalArgumentException("onboarding not pending in pinned NONE selection mode");
        } catch (ReflectiveOperationException unsupported) {
            throw new IllegalStateException("pinned Better Spawns read-only provider unavailable", unsupported);
        }
        PassiveObserverBinding binding = PassiveObserverBinding.arm(request, p, actor.getUUID(),
                actor.serverLevel().dimension().location().toString(), tick, MAX_DEADLINE_TICKS);
        return new PassiveOnboarding(binding, p, area, actorBlock(actor), tick);
    }
    void packetBefore(ServerPlayer actor, Object packet, long tick) {
        if (!binding.actorId.equals(actor.getUUID())) return;
        packetCount++;
        if (packetCount != 1 || tick > binding.deadlineTick || !PACKET_CLASS.equals(packet.getClass().getName())) {
            fail("duplicate_or_unpinned_finalize_packet"); return;
        }
        try {
            String mode = (String) field(packet, "selectionMode"), classId = (String) field(packet, "classId");
            Object purchases = field(packet, "embarkPurchases");
            String dim = (String) field(packet, "dimensionId");
            int x = (Integer) field(packet, "x"), y = (Integer) field(packet, "y"), z = (Integer) field(packet, "z");
            Site site = new Site(dim, x, y, z);
            JsonObject currentActor = actorBlock(actor);
            if (!MODE.equals(mode) || !classId.isEmpty() || !(purchases instanceof List<?> list) || !list.isEmpty() ||
                    !reviewed.contains(site) || !binding.dimension.equals(currentActor.get("dimension").getAsString()) ||
                    !withinOne(site, currentActor) || completed(actor) ||
                    actor.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                fail("packet_mode_request_bounds_or_current_actor_mismatch"); return;
            }
            // Freeze actual first request and actor BEFORE provider validation or site preparation.
            requested = site; actorAtRequest = currentActor;
            terrainPreCommit = captureColumn(actor.serverLevel(), site);
            packetTick = tick;
        } catch (Exception unreadable) { fail("pinned_finalize_packet_or_loaded_column_unavailable:" + unreadable.getClass().getSimpleName()); }
    }
    void finalized(ServerPlayer actor, Object actualEvent, long tick) {
        if (!binding.actorId.equals(actor.getUUID())) return;
        finalizationCount++;
        if (finalizationCount != 1 || packetCount != 1 || packetTick < armTick ||
                !EVENT_CLASS.equals(actualEvent.getClass().getName()) || terrainPreCommit == null) {
            fail("unattributed_or_duplicate_finalization_event"); return;
        }
        try {
            String kit = (String) actualEvent.getClass().getMethod("getKitId").invoke(actualEvent);
            String siteText = (String) actualEvent.getClass().getMethod("getSpawnId").invoke(actualEvent);
            Site site = Site.parse(siteText);
            JsonObject persisted = persistentSite(actor);
            if (!"spawn_only".equals(kit) || !sameColumn(site, requested) || !completed(actor) ||
                    !site.json().equals(persisted) || !siteText.equals(startingSite(actor))) {
                fail("genuine_finalization_not_committed_to_requested_column_and_persistence"); return;
            }
            committed = site; persistedAtEvent = persisted; actualKit = kit; finalizationEventTick = tick;
        } catch (Exception unreadable) { fail("pinned_finalization_event_read_failed:" + unreadable.getClass().getSimpleName()); }
    }
    void packetAfter(ServerPlayer actor, long tick) {
        if (!binding.actorId.equals(actor.getUUID())) return;
        handlerReturnCount++;
        if (handlerReturnCount != 1 || packetCount != 1 || finalizationCount != 1 ||
                finalizationEventTick < packetTick) {
            fail("finalize_handler_return_without_exact_committed_event"); return;
        }
        handlerReturnTick = tick;
    }
    void tick(ServerPlayer actor, long tick) {
        if (failure != null) return;
        if (actor == null || actor.connection == null ||
                !binding.dimension.equals(actor.serverLevel().dimension().location().toString())) {
            fail("onboarding_actor_disconnected_or_changed_dimension"); return;
        }
        GameType mode = actor.gameMode.getGameModeForPlayer();
        if (earlySurvivalWithoutHandler(handlerReturnTick, mode.getName())) {
            fail("early_survival_transition_without_finalize_handler"); return;
        }
        if (tick > binding.deadlineTick) { fail("onboarding_deadline_elapsed_or_finalize_missing"); return; }
        if (handlerReturnTick < 0 || mode != GameType.SURVIVAL || finalTick >= 0) return;
        try {
            if (committed == null || !binding.dimension.equals(committed.dimension)) {
                fail("committed_site_absent_or_wrong_dimension"); return;
            }
            finalComplete = completed(actor);
            persistedFinal = persistentSite(actor);
            vanillaRespawnSite = vanillaSite(actor);
            actualFinalActor = actorFinal(actor);
            siteAfter = siteAfter(actor.serverLevel(), committed);
            if (!finalComplete || !committed.json().equals(persistedFinal) ||
                    !committed.json().equals(vanillaRespawnSite) ||
                    !sameBlock(committed, actualFinalActor) ||
                    !siteAfter.get("marker_base_observed").getAsBoolean() ||
                    !siteAfter.get("feet_head_air_observed").getAsBoolean()) {
                fail("final_survival_actor_site_marker_clearance_or_persistence_mismatch"); return;
            }
            finalMode = mode.getName(); finalTick = tick; // server END after actual provider work, not packet HEAD.
        } catch (Exception unreadable) { fail("final_onboarding_state_read_failed:" + unreadable.getClass().getSimpleName()); }
    }
    void fail(String reason) { if (failure == null) failure = reason; }
    boolean terminal() { return failure != null || finalTick >= 0; }
    static boolean earlySurvivalWithoutHandler(long handlerReturnTick, String actualMode) {
        return handlerReturnTick < 0 && !"spectator".equals(actualMode);
    }
    static boolean validChain(long arm, long packet, long finalized, long returned, long applied,
                              int packets, int finalizations, int returns, boolean completed, String mode) {
        return packets == 1 && finalizations == 1 && returns == 1 && packet >= arm && finalized >= packet &&
                returned >= finalized && applied >= returned && completed && "survival".equals(mode);
    }
    boolean qualifiedNarrow() { return failure == null && validChain(armTick, packetTick, finalizationEventTick,
            handlerReturnTick, finalTick, packetCount, finalizationCount, handlerReturnCount, finalComplete, finalMode) &&
            requested != null && committed != null && terrainPreCommit != null &&
            terrainPreCommit.get("column_complete").getAsBoolean() && persistedAtEvent != null &&
            committed.json().equals(persistedAtEvent) && committed.json().equals(persistedFinal) &&
            committed.json().equals(vanillaRespawnSite) && sameColumn(committed, requested) &&
            sameBlock(committed, actualFinalActor) && "spawn_only".equals(actualKit); }
    JsonObject receipt() {
        JsonObject row = binding.echo(); row.addProperty("schema", "bc.playtest_passive_onboarding.v1");
        row.addProperty("expected_provider", PROVIDER); row.addProperty("expected_mode", MODE);
        row.add("request_bounds", requestBounds.deepCopy()); row.add("actor_at_arm", actorAtArm.deepCopy());
        if (requested != null) row.add("requested_site", requested.json());
        if (actorAtRequest != null) row.add("actor_at_request", actorAtRequest.deepCopy());
        if (terrainPreCommit != null) row.add("terrain_pre_commit", terrainPreCommit.deepCopy());
        if (committed != null) row.add("actual_committed_site", committed.json());
        if (persistedAtEvent != null) row.add("persisted_site_at_event", persistedAtEvent.deepCopy());
        if (persistedFinal != null) row.add("persisted_site", persistedFinal.deepCopy());
        if (vanillaRespawnSite != null) row.add("vanilla_respawn_site", vanillaRespawnSite.deepCopy());
        if (actualFinalActor != null) row.add("actual_final_actor", actualFinalActor.deepCopy());
        if (siteAfter != null) row.add("site_after", siteAfter.deepCopy());
        row.addProperty("provider_artifact_sha256_controller_declared", providerArtifactSha256);
        row.addProperty("provider_config_sha256_controller_declared", providerConfigSha256);
        row.addProperty("provider_artifact_certified_by_native", false);
        row.addProperty("arm_tick", armTick); row.addProperty("packet_handling_tick", packetTick);
        row.addProperty("finalization_event_tick", finalizationEventTick);
        row.addProperty("packet_handler_return_tick", handlerReturnTick);
        row.addProperty("final_applied_state_tick", finalTick);
        row.addProperty("packet_count", packetCount); row.addProperty("finalization_event_count", finalizationCount);
        row.addProperty("packet_handler_return_count", handlerReturnCount);
        row.addProperty("packet_source", "pinned_FinalizeSelectionPacket_Companion.handle_lambda0/actual_enqueued_server_work");
        row.addProperty("packet_transport_origin_proven_by_native", false);
        row.addProperty("finalization_source", "BetterSpawns.PlayerStartFinalizedEvent/Forge_bus_after_persistent_commit");
        row.addProperty("final_source", "ServerTickEvent.END/actual_game_mode_and_persistent_site");
        row.addProperty("terrain_semantics", "raw_loaded_column_only_independent_known_vanilla_block_oracle_required");
        if (actualKit != null) row.addProperty("actual_kit", actualKit);
        if (finalMode != null) row.addProperty("final_mode", finalMode);
        row.addProperty("final_onboarding_complete", finalComplete);
        row.addProperty("narrow_event_chain_complete", qualifiedNarrow());
        if (failure != null) row.addProperty("unsupported_reason", failure);
        return row;
    }
    static boolean boundedArea(long width, long height, long depth) {
        return width >= 1 && width <= 5 && height >= 1 && height <= 16 && depth >= 1 && depth <= 5 &&
                width * height * depth <= 400;
    }
    static boolean requestNearActor(int x, int y, int z, int actorX, int actorY, int actorZ) {
        return Math.abs((long) x - actorX) <= 1 && Math.abs((long) y - actorY) <= 1 &&
                Math.abs((long) z - actorZ) <= 1;
    }
    static int scanTopY(int requestY, int minBuild, int maxBuild) {
        if (maxBuild <= minBuild + 3) throw new IllegalArgumentException("invalid build height");
        return Math.min(Math.max(requestY, minBuild + 1), maxBuild - 3);
    }
    static JsonObject parseProviderSite(String source) { return Site.parse(source).json(); }
    private static boolean withinOne(Site site, JsonObject actor) {
        return requestNearActor(site.x, site.y, site.z, actor.get("block_x").getAsInt(),
                actor.get("block_y").getAsInt(), actor.get("block_z").getAsInt());
    }
    static boolean sameRequestedColumn(String actualSite, String dimension, int x, int z) {
        try {
            Site actual = Site.parse(actualSite);
            return actual.dimension.equals(dimension) && actual.x == x && actual.z == z;
        } catch (RuntimeException malformed) { return false; }
    }
    private static boolean sameColumn(Site actual, Site request) {
        return request != null && sameRequestedColumn(actual.wire(), request.dimension, request.x, request.z);
    }
    private static boolean sameBlock(Site site, JsonObject actor) {
        return actor != null && site.dimension.equals(actor.get("dimension").getAsString()) &&
                site.x == actor.get("block_x").getAsInt() && site.y == actor.get("block_y").getAsInt() &&
                site.z == actor.get("block_z").getAsInt();
    }
    private static JsonObject actorBlock(ServerPlayer actor) {
        JsonObject row = new JsonObject(); row.addProperty("dimension", actor.serverLevel().dimension().location().toString());
        row.addProperty("block_x", actor.getBlockX()); row.addProperty("block_y", actor.getBlockY()); row.addProperty("block_z", actor.getBlockZ());
        return row;
    }
    private static JsonObject actorFinal(ServerPlayer actor) {
        JsonObject row = actorBlock(actor);
        row.addProperty("x", actor.getX()); row.addProperty("y", actor.getY()); row.addProperty("z", actor.getZ());
        row.addProperty("mode", actor.gameMode.getGameModeForPlayer().getName()); return row;
    }
    private static JsonObject captureColumn(ServerLevel level, Site site) {
        int min = level.getMinBuildHeight(), max = level.getMaxBuildHeight();
        if (max <= min || (long) max - min > MAX_COLUMN_HEIGHT ||
                !level.getChunkSource().hasChunk(site.x >> 4, site.z >> 4))
            throw new IllegalStateException("requested column unloaded or outside bounded height");
        JsonArray rows = new JsonArray();
        for (int y = min; y < max; y++) rows.add(rawState(level, new BlockPos(site.x, y, site.z)));
        JsonObject result = new JsonObject(); result.addProperty("dimension", site.dimension);
        result.addProperty("x", site.x); result.addProperty("z", site.z);
        result.addProperty("min_build_height", min); result.addProperty("max_build_height", max);
        result.addProperty("scan_top_y", scanTopY(site.y, min, max));
        result.addProperty("loaded_only_no_chunk_forcing", true);
        boolean complete = columnComplete(rows, min, max);
        if (!complete) throw new IllegalStateException("precommit column rows missing/nonsequential");
        result.addProperty("column_complete", true);
        result.addProperty("material_classification", "none_native_raw_only"); result.add("columns", rows);
        if (result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_COLUMN_BYTES)
            throw new IllegalStateException("bounded precommit column byte limit exceeded");
        return result;
    }
    static boolean columnComplete(JsonArray rows, int min, int max) {
        if (max <= min || (long) max - min > MAX_COLUMN_HEIGHT || rows.size() != max - min) return false;
        for (int index = 0; index < rows.size(); index++)
            if (!rows.get(index).isJsonObject() || !rows.get(index).getAsJsonObject().has("y") ||
                    rows.get(index).getAsJsonObject().get("y").getAsInt() != min + index)
                return false;
        return true;
    }
    private static JsonObject rawState(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos); JsonObject row = new JsonObject(), properties = new JsonObject();
        row.addProperty("y", pos.getY());
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) throw new IllegalStateException("unregistered raw block state");
        row.addProperty("block_id", id.toString());
        state.getProperties().stream().sorted(Comparator.comparing(Property::getName)).forEach(property ->
                properties.addProperty(property.getName(), state.getValue(property).toString()));
        row.add("properties", properties); return row;
    }
    private static JsonObject siteAfter(ServerLevel level, Site site) {
        if (site.y <= level.getMinBuildHeight() || site.y >= level.getMaxBuildHeight() - 1)
            throw new IllegalStateException("resolved site clearance outside build bounds");
        if (!level.getChunkSource().hasChunk(site.x >> 4, site.z >> 4))
            throw new IllegalStateException("resolved site chunk unloaded");
        JsonObject row = new JsonObject();
        JsonObject base = rawState(level, new BlockPos(site.x, site.y - 1, site.z));
        JsonObject feet = rawState(level, new BlockPos(site.x, site.y, site.z));
        JsonObject head = rawState(level, new BlockPos(site.x, site.y + 1, site.z));
        row.add("base", base); row.add("feet", feet); row.add("head", head);
        row.addProperty("loaded_only_no_chunk_forcing", true);
        row.addProperty("marker_base_observed", "better_spawns:respawn_marker".equals(base.get("block_id").getAsString()));
        row.addProperty("feet_head_air_observed", "minecraft:air".equals(feet.get("block_id").getAsString()) &&
                "minecraft:air".equals(head.get("block_id").getAsString()));
        return row;
    }
    private static JsonObject persistentSite(ServerPlayer actor) {
        CompoundTag data = actor.getPersistentData();
        String prefix = "better_spawns:respawn_";
        if (!data.contains(prefix + "dim", Tag.TAG_STRING) || !data.contains(prefix + "x", Tag.TAG_INT) ||
                !data.contains(prefix + "y", Tag.TAG_INT) || !data.contains(prefix + "z", Tag.TAG_INT))
            throw new IllegalStateException("provider persistence tags missing/wrong type");
        return new Site(data.getString(prefix + "dim"), data.getInt(prefix + "x"),
                data.getInt(prefix + "y"), data.getInt(prefix + "z")).json();
    }
    private static JsonObject vanillaSite(ServerPlayer actor) {
        BlockPos pos = actor.getRespawnPosition();
        if (pos == null || actor.getRespawnDimension() == null)
            throw new IllegalStateException("vanilla respawn target absent");
        return new Site(actor.getRespawnDimension().location().toString(), pos.getX(), pos.getY(), pos.getZ()).json();
    }
    private static Object field(Object packet, String name) throws ReflectiveOperationException {
        Field field = packet.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(packet);
    }
    private static boolean completed(ServerPlayer actor) throws ReflectiveOperationException {
        Class<?> provider = Class.forName(INTEGRATION_CLASS);
        return (boolean) provider.getMethod("hasCompletedOnboarding", ServerPlayer.class).invoke(null, actor);
    }
    private static String startingSite(ServerPlayer actor) throws ReflectiveOperationException {
        Class<?> provider = Class.forName(INTEGRATION_CLASS);
        return (String) provider.getMethod("getStartingSite", ServerPlayer.class).invoke(null, actor);
    }
    private static String cachedMode() throws ReflectiveOperationException {
        Class<?> provider = Class.forName("com.bettercontent.betterspawns.embark.SelectionDataRepository");
        Field cached = provider.getDeclaredField("cached"); cached.setAccessible(true);
        Object data = cached.get(null);
        if (data == null) throw new IllegalStateException("pinned Better Spawns selection data not yet loaded");
        Object mode = data.getClass().getMethod("getMode").invoke(data);
        return ((Enum<?>) mode).name();
    }
}
