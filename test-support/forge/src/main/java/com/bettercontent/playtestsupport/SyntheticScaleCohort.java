package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.NetworkHooks;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synthetic registered normal ServerPlayer lifecycle, explicitly NOT an authenticated real client. */
final class SyntheticScaleCohort {
    private static final long CLEANUP_DEADLINE_NANOS = 120_000_000_000L;
    private static final long MAX_SESSION_WALL_NANOS = ScaleWireContract.HARD_DEADLINE_NANOS;
    private static final int MAX_FOOTPRINT_CHUNKS = 529;
    record Site(int x, int y, int z, int chunkX, int chunkZ) {}
    static final class Participant {
        final ServerPlayer player;
        final Connection connection;
        final EmbeddedChannel channel;
        final Site site;
        int playerTicks, chunkTicks, footprintChunkTickEvents, syntheticKeepaliveAcks, syntheticTeleportAcks;
        final Set<Long> distinctFootprintChunksTicked = new HashSet<>();
        final SortedMap<Long, ChunkCoverage> coverage = new TreeMap<>();
        List<ChunkCoverage> orderedCoverage = List.of();
        int loadedChunks, tickingChunks;
        final Queue<Long> pendingKeepalives;
        final Queue<Integer> pendingTeleports;
        final AtomicBoolean ackQueueOverflow;
        Participant(ServerPlayer player, Connection connection, EmbeddedChannel channel, Site site,
                    Queue<Long> pendingKeepalives, Queue<Integer> pendingTeleports, AtomicBoolean ackQueueOverflow) {
            this.player = player; this.connection = connection; this.channel = channel; this.site = site;
            this.pendingKeepalives = pendingKeepalives; this.pendingTeleports = pendingTeleports;
            this.ackQueueOverflow = ackQueueOverflow;
        }
    }
    private static final class ChunkCoverage {
        final int x, z;
        int loadedSamples, blockTickingSamples, entityTickingSamples, naturalSpawningSamples, tickEligibleSamples, tickEvents;
        int previousSampleTickEvents;
        ChunkCoverage(int x, int z) { this.x = x; this.z = z; }
    }
    final Protocol.Request request;
    final ServerLevel level;
    final int cohort;
    final String ledgerSha256;
    final long startTick, startWallNanos;
    private long setupCompletedWallNanos, setupCompletedTick;
    final List<Participant> participants = new ArrayList<>();
    final List<String> tickRows = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Participant> footprintChunks = new Long2ObjectOpenHashMap<>();
    private long lastNanos, lastGameTime;
    private long lastSampleTick;
    private int samples;
    private long windowWallNanos, windowGameTicks;
    private final JsonArray tpsWindows = new JsonArray();
    private boolean timingComplete = true, heapComplete = true, gcCompleteAll = true, tpsThresholdMet = true;

    private SyntheticScaleCohort(Protocol.Request request, ServerLevel level, int cohort,
                                 String ledgerSha256, long startTick) {
        this.request = request; this.level = level; this.cohort = cohort;
        this.ledgerSha256 = ledgerSha256; this.startTick = startTick;
        this.startWallNanos = System.nanoTime();
        lastNanos = startWallNanos; lastGameTime = level.getGameTime();
    }
    static SyntheticScaleCohort begin(MinecraftServer server, Protocol.Request request, JsonObject p, long tick) {
        Protocol.only(p, "cohort", "dimension", "view_distance", "simulation_distance", "capture_horizon_millis", "sites", "ledger_sha256");
        String ledger = Protocol.string(p, "ledger_sha256");
        if (!ledger.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("external fresh-terrain ledger digest required");
        int count = Math.toIntExact(Protocol.number(p, "cohort"));
        if (count != 8 && count != 16 && count != 32) throw new IllegalArgumentException("cohort must be 8/16/32");
        if (Protocol.number(p, "view_distance") != 10 || Protocol.number(p, "simulation_distance") != 10
                || server.getPlayerList().getViewDistance() != 10 || server.getPlayerList().getSimulationDistance() != 10)
            throw new IllegalArgumentException("production server view/simulation distance must both equal ten");
        if (Protocol.number(p, "capture_horizon_millis") != ScaleWireContract.CAPTURE_HORIZON_MILLIS
                || request.timeout() < ScaleWireContract.MAX_SAMPLES * 20 + 40)
            throw new IllegalArgumentException("scale requires explicit setup-inclusive 810s horizon and sufficient request deadline");
        ResourceLocation id = ResourceLocation.tryParse(Protocol.string(p, "dimension"));
        if (id == null) throw new IllegalArgumentException("invalid dimension");
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) throw new IllegalArgumentException("dimension not running");
        if (!server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalArgumentException("exact cohort requires empty player roster before setup");
        if (!p.get("sites").isJsonArray() || p.getAsJsonArray("sites").size() != count)
            throw new IllegalArgumentException("one externally declared site required per participant");
        List<Site> sites = new ArrayList<>();
        for (var siteValue : p.getAsJsonArray("sites")) {
            if (!siteValue.isJsonObject()) throw new IllegalArgumentException("site must be object");
            JsonObject site = siteValue.getAsJsonObject(); Protocol.only(site, "x", "y", "z");
            int x = coordinate(site, "x"), y = coordinate(site, "y"), z = coordinate(site, "z");
            if (y < level.getMinBuildHeight() + 2 || y >= level.getMaxBuildHeight() - 2)
                throw new IllegalArgumentException("site y outside safe build bounds");
            Site next = new Site(x, y, z, x >> 4, z >> 4);
            for (Site prior : sites)
                if (Math.abs(next.chunkX() - prior.chunkX()) < 32
                        && Math.abs(next.chunkZ() - prior.chunkZ()) < 32)
                    throw new IllegalArgumentException("site footprints overlap (less than 32 chunks apart)");
            sites.add(next);
        }
        SyntheticScaleCohort cohort = new SyntheticScaleCohort(request, level, count, ledger, tick);
        try {
            for (int index = 0; index < count; index++) {
                if (cohort.wallExpired()) throw new IllegalStateException("synthetic scale setup exceeded 900s wall deadline");
                cohort.join(server, sites.get(index), index);
            }
            if (cohort.wallExpired()) throw new IllegalStateException("synthetic scale setup exceeded 900s wall deadline");
            // The independent recovery clock starts at startWallNanos, BEFORE cohort joins.
            // Reset only the post-setup sampling interval, never erase setup wall time.
            cohort.lastNanos = System.nanoTime();
            cohort.setupCompletedWallNanos = cohort.lastNanos;
            cohort.setupCompletedTick = server.getTickCount();
            cohort.lastGameTime = level.getGameTime();
            for (Participant participant : cohort.participants)
                for (ChunkCoverage coverage : participant.coverage.values())
                    coverage.previousSampleTickEvents = coverage.tickEvents;
            return cohort;
        } catch (RuntimeException | Error failure) {
            try { cohort.cleanup(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private void join(MinecraftServer server, Site site, int index) {
        UUID uuid = UUID.nameUUIDFromBytes(("bc.playtest.scale." + request.run() + "." + request.caseId()
                + "." + request.token() + "." + request.sequence() + "." + cohort + "." + index)
                .getBytes(StandardCharsets.UTF_8));
        for (LevelResource folder : List.of(LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR,
                LevelResource.PLAYER_ADVANCEMENTS_DIR)) {
            Path file = server.getWorldPath(folder).resolve(uuid + (folder == LevelResource.PLAYER_DATA_DIR ? ".dat" : ".json"));
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    || (folder == LevelResource.PLAYER_DATA_DIR
                    && Files.exists(file.resolveSibling(file.getFileName() + "_old"), LinkOption.NOFOLLOW_LINKS)))
                throw new IllegalStateException("synthetic UUID already has persistent state");
        }
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        Queue<Long> pendingKeepalives = new ConcurrentLinkedQueue<>();
        Queue<Integer> pendingTeleports = new ConcurrentLinkedQueue<>();
        AtomicBoolean ackQueueOverflow = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override protected void initChannel(Channel ch) {
                ch.pipeline().addLast("bc_scale_sink", new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                        if (message instanceof ClientboundKeepAlivePacket keepalive) {
                            if (pendingKeepalives.size() < 64) pendingKeepalives.add(keepalive.getId());
                            else ackQueueOverflow.set(true);
                        } else if (message instanceof ClientboundPlayerPositionPacket teleport) {
                            if (pendingTeleports.size() < 64) pendingTeleports.add(teleport.getId());
                            else ackQueueOverflow.set(true);
                        }
                        ReferenceCountUtil.release(message); promise.setSuccess();
                    }
                });
                ch.pipeline().addLast("packet_handler", (ChannelHandler) connection);
            }
        });
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(uuid, "BCScale" + String.format(Locale.ROOT, "%02d", index)));
        try {
            if (connection.channel() != channel || !channel.isActive() || channel.pipeline().get("packet_handler") != connection)
                throw new IllegalStateException("synthetic transport not bound");
            NetworkHooks.registerServerLoginChannel(connection,
                    new ClientIntentionPacket("localhost", 0, ConnectionProtocol.LOGIN));
            if (NetworkHooks.getConnectionType(() -> connection) != ConnectionType.MODDED)
                throw new IllegalStateException("synthetic Forge connection metadata absent");
            connection.setProtocol(ConnectionProtocol.PLAY);
            channel.runPendingTasks(); channel.checkException();
            server.getPlayerList().placeNewPlayer(connection, player);
            if (!server.getConnection().getConnections().contains(connection))
                server.getConnection().getConnections().add(connection);
            player.connection.handleClientInformation(new ServerboundClientInformationPacket(
                    "en_us", 10, ChatVisiblity.FULL, true, 0x7f, HumanoidArm.RIGHT, false, false));
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level, site.x() + .5, site.y(), site.z() + .5, 0, 0);
            if (server.getPlayerList().getPlayer(uuid) != player || player instanceof FakePlayer)
                throw new IllegalStateException("normal ServerPlayer roster registration absent");
            Participant participant = new Participant(player, connection, channel, site,
                    pendingKeepalives, pendingTeleports, ackQueueOverflow);
            participants.add(participant);
            // The pinned isChunkInRange(10) predicate includes cardinal offsets 11.
            for (int dx = -11; dx <= 11; dx++) for (int dz = -11; dz <= 11; dz++) {
                int cx = site.chunkX() + dx, cz = site.chunkZ() + dz;
                // Forge 1.20.1 engine range, not the enclosing square's unticketed corners.
                if (!ChunkMap.isChunkInRange(cx, cz, site.chunkX(), site.chunkZ(), 10)) continue;
                long key = ChunkPos.asLong(cx, cz);
                if (footprintChunks.put(key, participant) != null)
                    throw new IllegalStateException("simulation-distance-10 footprints overlap");
                participant.coverage.put(key, new ChunkCoverage(cx, cz));
            }
            if (participant.coverage.isEmpty() || participant.coverage.size() > MAX_FOOTPRINT_CHUNKS)
                throw new IllegalStateException("engine geometric footprint roster invalid");
            participant.orderedCoverage = participant.coverage.values().stream()
                    .sorted(Comparator.comparingInt((ChunkCoverage c) -> c.x).thenComparingInt(c -> c.z)).toList();
        } catch (RuntimeException | Error failure) {
            try { if (server.getPlayerList().getPlayers().contains(player)) server.getPlayerList().remove(player); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { server.getConnection().getConnections().remove(connection); channel.finishAndReleaseAll(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            for (LevelResource folder : List.of(LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR,
                    LevelResource.PLAYER_ADVANCEMENTS_DIR)) {
                Path path = server.getWorldPath(folder).resolve(uuid + (folder == LevelResource.PLAYER_DATA_DIR ? ".dat" : ".json"));
                try {
                    if (Files.isSymbolicLink(path)) throw new IOException("synthetic state path replaced by symlink");
                    Files.deleteIfExists(path);
                    if (folder == LevelResource.PLAYER_DATA_DIR)
                        Files.deleteIfExists(path.resolveSibling(path.getFileName() + "_old"));
                } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
    boolean contains(ServerPlayer player) {
        for (Participant participant : participants) if (participant.player == player) return true;
        return false;
    }
    void playerTick(ServerPlayer player) {
        for (Participant participant : participants) if (participant.player == player) { participant.playerTicks++; return; }
    }
    void chunkTick(ServerLevel actual, LevelChunk chunk) {
        if (actual != level) return;
        long key = chunk.getPos().toLong();
        Participant participant = footprintChunks.get(key);
        if (participant == null) return;
        participant.footprintChunkTickEvents++;
        participant.distinctFootprintChunksTicked.add(key);
        ChunkCoverage coverage = participant.coverage.get(key);
        if (coverage == null) throw new IllegalStateException("unrostered footprint tick");
        coverage.tickEvents++;
        if (participant.site.chunkX() == chunk.getPos().x && participant.site.chunkZ() == chunk.getPos().z)
            participant.chunkTicks++;
    }
    void driveSyntheticTransport() {
        // These acknowledgments preserve the synthetic normal PlayerList lifecycle only.
        // They are not evidence of network delivery, RTT, or a human client.
        for (Participant participant : participants) {
            if (!participant.channel.isOpen() || !participant.channel.isActive()) continue;
            participant.channel.runPendingTasks(); participant.channel.checkException();
        }
        for (Participant participant : participants) {
            if (participant.connection.getPacketListener() != participant.player.connection) continue;
            Long keepalive;
            while ((keepalive = participant.pendingKeepalives.poll()) != null) {
                participant.player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepalive));
                participant.syntheticKeepaliveAcks++;
            }
            Integer teleport;
            while ((teleport = participant.pendingTeleports.poll()) != null) {
                participant.player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleport));
                participant.syntheticTeleportAcks++;
            }
        }
    }
    boolean wallExpired() { return System.nanoTime() - startWallNanos > MAX_SESSION_WALL_NANOS; }
    boolean shouldSample(long tick) { return tick > startTick && (tick - startTick) % 20 == 0; }
    void sample(long tick, long serverTickDurationNanos) {
        if (samples >= ScaleWireContract.MAX_SAMPLES) throw new IllegalStateException("scale sample cap exceeded");
        long now = System.nanoTime(), gameTime = level.getGameTime();
        JsonObject row = new JsonObject();
        ScaleWireContract.sampleClock(row, tick, now, startWallNanos, setupCompletedWallNanos,
                setupCompletedTick, lastNanos, gameTime, lastGameTime);
        if (serverTickDurationNanos >= 0) row.addProperty("server_tick_start_to_end_nanos", serverTickDurationNanos);
        else { row.addProperty("server_tick_timing_complete", false); timingComplete = false; }
        windowWallNanos += Math.max(0, now - lastNanos);
        windowGameTicks += gameTime - lastGameTime;
        if (windowWallNanos >= 30_000_000_000L) {
            double tps = windowGameTicks * 1_000_000_000.0 / windowWallNanos;
            JsonObject window = new JsonObject(); window.addProperty("wall_nanos", windowWallNanos);
            window.addProperty("game_ticks", windowGameTicks); window.addProperty("tps", tps);
            window.addProperty("end_sample_tick", tick); tpsWindows.add(window);
            if (tps < 19.0) tpsThresholdMet = false;
            windowWallNanos = 0; windowGameTicks = 0;
        }
        long usedHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        if (usedHeap < 0) heapComplete = false;
        row.addProperty("heap_metrics_complete", usedHeap >= 0);
        if (usedHeap >= 0) row.addProperty("heap_used", usedHeap);
        long gcCount = 0, gcMillis = 0; boolean gcComplete = true;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount(), millis = gc.getCollectionTime();
            if (count < 0 || millis < 0) gcComplete = false;
            else { gcCount += count; gcMillis += millis; }
        }
        row.addProperty("gc_metrics_complete", gcComplete);
        gcCompleteAll &= gcComplete;
        if (gcComplete) { row.addProperty("gc_count_total", gcCount); row.addProperty("gc_millis_total", gcMillis); }
        JsonArray actors = new JsonArray();
        for (Participant p : participants) {
            int loaded = 0, blockTicking = 0;
            byte[] innerReady = new byte[(p.orderedCoverage.size() + 7) / 8];
            byte[] intervalTicks = new byte[innerReady.length];
            for (int index = 0; index < p.orderedCoverage.size(); index++) {
                ChunkCoverage coverage = p.orderedCoverage.get(index);
                long key = ChunkPos.asLong(coverage.x, coverage.z);
                ChunkPos position = new ChunkPos(coverage.x, coverage.z);
                var source = level.getChunkSource();
                var distance = source.chunkMap.getDistanceManager();
                // These lookups do not create tickets, load chunks or invoke tickChunk.
                boolean chunkLoaded = source.getChunkNow(coverage.x, coverage.z) != null;
                if (chunkLoaded) { coverage.loadedSamples++; loaded++; }
                boolean blockReady = distance.inBlockTickingRange(key) && source.isPositionTicking(key);
                if (blockReady) { coverage.blockTickingSamples++; blockTicking++; }
                boolean entityReady = distance.inEntityTickingRange(key);
                if (entityReady) coverage.entityTickingSamples++;
                // ServerChunkCache.tickChunks: natural spawn eligibility plus <128-block
                // player-to-chunk-center distance, OR an independently forced tick ticket.
                double dx = coverage.x * 16.0 + 8.0 - p.player.getX();
                double dz = coverage.z * 16.0 + 8.0 - p.player.getZ();
                boolean natural = level.isNaturalSpawningAllowed(position);
                if (natural) coverage.naturalSpawningSamples++;
                if (chunkLoaded && blockReady && entityReady && natural)
                    innerReady[index >> 3] |= (byte) (1 << (index & 7));
                if (ScaleWireContract.fullIntervalTicks(coverage.tickEvents,
                        coverage.previousSampleTickEvents, gameTime - lastGameTime))
                    intervalTicks[index >> 3] |= (byte) (1 << (index & 7));
                coverage.previousSampleTickEvents = coverage.tickEvents;
                if (blockReady && (distance.shouldForceTicks(key)
                        || (natural && !p.player.isSpectator()
                        && dx * dx + dz * dz < 16_384.0))) coverage.tickEligibleSamples++;
            }
            p.loadedChunks = loaded; p.tickingChunks = blockTicking;
            JsonObject actor = new JsonObject(); actor.addProperty("uuid", p.player.getUUID().toString());
            actor.addProperty("footprint_interval_masks_base64", ScaleWireContract.intervalMasks(innerReady, intervalTicks));
            actor.addProperty("chunk_x", p.site.chunkX()); actor.addProperty("chunk_z", p.site.chunkZ());
            actor.addProperty("player_ticks", p.playerTicks); actor.addProperty("native_chunk_ticks", p.chunkTicks);
            // Full-interval mask replaces redundant cumulative footprint columns in
            // every raw row; those counts remain present in the terminal receipt.
            actor.addProperty("footprint_loaded_chunks", loaded);
            actor.addProperty("footprint_block_ticking_chunks", blockTicking);
            actor.addProperty("registered", level.getServer().getPlayerList().getPlayer(p.player.getUUID()) == p.player);
            actor.addProperty("location_chunk_matches", p.player.chunkPosition().x == p.site.chunkX()
                    && p.player.chunkPosition().z == p.site.chunkZ());
            actors.add(actor);
        }
        row.add("actors", actors);
        tickRows.add(row.toString()); samples++; lastSampleTick = tick;
        lastNanos = now; lastGameTime = gameTime;
    }
    boolean done(long tick) {
        return lastSampleTick == tick && lastNanos - startWallNanos >= ScaleWireContract.CAPTURE_HORIZON_NANOS;
    }
    static boolean mechanicallyComplete(int participants, int rosterSize, int sampleRows,
                                        boolean timing, boolean heap, boolean gc) {
        return participants == rosterSize && participants > 0 && sampleRows > 0 && timing && heap && gc;
    }
    JsonObject receipt() {
        JsonObject row = new JsonObject(); row.addProperty("cohort", cohort);
        row.addProperty("roster_size", level.getServer().getPlayerList().getPlayers().size());
        row.addProperty("simulation_distance", level.getServer().getPlayerList().getSimulationDistance());
        row.addProperty("view_distance", level.getServer().getPlayerList().getViewDistance());
        ScaleWireContract.receiptClock(row, startWallNanos, setupCompletedWallNanos,
                setupCompletedTick, lastNanos, lastSampleTick, System.nanoTime(), tickRows.size());
        row.add("tps_30s_windows", tpsWindows);
        row.addProperty("tps_threshold_19_met", tpsThresholdMet && tpsWindows.size() >= 20);
        row.addProperty("tps_threshold_19_met_is_diagnostic_only", true);
        row.addProperty("site_ledger_sha256", ledgerSha256);
        row.addProperty("site_ledger_verification", "external_unverified_by_native");
        row.addProperty("terrain_freshness_proven_by_native", false);
        row.addProperty("server_tick_timing_complete", timingComplete);
        row.addProperty("heap_metrics_complete", heapComplete);
        row.addProperty("gc_metrics_complete", gcCompleteAll);
        row.addProperty("actor_type", "synthetic_registered_ServerPlayer_embedded_transport_NOT_real_login");
        JsonArray actors = new JsonArray();
        // Initial generation may depress TPS before recovery. Native completion proves only
        // valid measurement mechanics; an independent oracle evaluates <=120s recovery and >=600s dwell.
        boolean conformant = participants.size() == cohort && mechanicallyComplete(participants.size(),
                level.getServer().getPlayerList().getPlayers().size(), tickRows.size(),
                timingComplete, heapComplete, gcCompleteAll);
        for (Participant p : participants) {
            JsonObject actor = new JsonObject(); actor.addProperty("uuid", p.player.getUUID().toString());
            actor.addProperty("site_x", p.site.x()); actor.addProperty("site_y", p.site.y()); actor.addProperty("site_z", p.site.z());
            actor.addProperty("chunk_x", p.site.chunkX()); actor.addProperty("chunk_z", p.site.chunkZ());
            actor.addProperty("player_ticks", p.playerTicks); actor.addProperty("native_chunk_ticks", p.chunkTicks);
            actor.addProperty("footprint_chunk_tick_events", p.footprintChunkTickEvents);
            actor.addProperty("footprint_distinct_chunks_ticked", p.distinctFootprintChunksTicked.size());
            JsonArray view = new JsonArray();
            int eligible = 0, observed = 0;
            int missingViewLoads = 0, missingInnerEntity = 0, missingInnerBlock = 0;
            int missingInnerNatural = 0, missingInnerTicks = 0, missingEligibleTicks = 0;
            for (ChunkCoverage coverage : p.orderedCoverage) {
                if (coverage.tickEligibleSamples > 0) { eligible++; if (coverage.tickEvents > 0) observed++; }
                // All view-range chunks must load. The fixed <128-block subset must
                // reach engine entity/block-ticking readiness and actual tickChunk;
                // outer view chunks are not required to invoke tickChunk.
                boolean inner = ScaleWireContract.spawnProximity(coverage.x, coverage.z, p.site.x(), p.site.z());
                // Compact fixed-order tuple keeps the whole cohort within the 1 MiB
                // mailbox line cap; every candidate remains independently inspectable.
                view.add(ScaleWireContract.footprintTuple(coverage.x, coverage.z,
                        coverage.loadedSamples, coverage.blockTickingSamples,
                        coverage.entityTickingSamples, coverage.naturalSpawningSamples,
                        coverage.tickEligibleSamples, coverage.tickEvents, inner));
                if (coverage.loadedSamples == 0) missingViewLoads++;
                if (inner) {
                    if (coverage.entityTickingSamples == 0) missingInnerEntity++;
                    if (coverage.blockTickingSamples == 0) missingInnerBlock++;
                    if (coverage.naturalSpawningSamples == 0) missingInnerNatural++;
                    if (coverage.tickEligibleSamples == 0 || coverage.tickEvents == 0) missingInnerTicks++;
                }
                if (coverage.tickEligibleSamples > 0 && coverage.tickEvents == 0) missingEligibleTicks++;
            }
            actor.add("footprint_view_chunks", view);
            actor.addProperty("footprint_view_tuple_schema", "chunk_x,chunk_z,loaded_samples,block_ticking_samples,entity_ticking_samples,natural_spawning_samples,tick_eligible_samples,tick_events,spawn_proximity_expected_0_or_1");
            actor.addProperty("footprint_geometric_chunks_count", p.coverage.size());
            actor.addProperty("footprint_tick_eligible_chunks", eligible);
            actor.addProperty("footprint_tick_observed_chunks", observed);
            actor.addProperty("footprint_loaded_chunks", p.loadedChunks);
            actor.addProperty("footprint_block_ticking_chunks", p.tickingChunks);
            actor.addProperty("missing_view_loaded_chunks", missingViewLoads);
            actor.addProperty("missing_inner_entity_ticking_chunks", missingInnerEntity);
            actor.addProperty("missing_inner_block_ticking_chunks", missingInnerBlock);
            actor.addProperty("missing_inner_natural_spawning_chunks", missingInnerNatural);
            actor.addProperty("missing_inner_tick_events_chunks", missingInnerTicks);
            actor.addProperty("missing_eligible_tick_events_chunks", missingEligibleTicks);
            if (missingViewLoads + missingInnerEntity + missingInnerBlock + missingInnerNatural
                    + missingInnerTicks + missingEligibleTicks > 0)
                actor.addProperty("footprint_unsupported_condition", "engine_eligible_footprint_not_captured");
            conformant &= missingViewLoads + missingInnerEntity + missingInnerBlock + missingInnerNatural
                    + missingInnerTicks + missingEligibleTicks == 0 && observed == eligible;
            actor.addProperty("synthetic_keepalive_acks", p.syntheticKeepaliveAcks);
            actor.addProperty("synthetic_teleport_acks", p.syntheticTeleportAcks);
            actor.addProperty("synthetic_ack_queue_overflow", p.ackQueueOverflow.get());
            boolean registered = level.getServer().getPlayerList().getPlayer(p.player.getUUID()) == p.player;
            boolean location = p.player.chunkPosition().x == p.site.chunkX() && p.player.chunkPosition().z == p.site.chunkZ();
            actor.addProperty("registered", registered); actor.addProperty("location_chunk_matches", location);
            actor.addProperty("transport_active", p.channel.isActive() && p.channel.isOpen());
            conformant &= registered && location && !p.ackQueueOverflow.get()
                    && actor.get("transport_active").getAsBoolean()
                    && p.syntheticKeepaliveAcks > 0 && p.syntheticTeleportAcks > 0
                    && p.playerTicks > 0 && p.chunkTicks > 0;
            actors.add(actor);
        }
        row.add("actors", actors); row.addProperty("conformant", conformant);
        row.addProperty("real_login_proven", false); row.addProperty("fakeplayer_factory_used", false);
        row.addProperty("latency_type", "local_server_tick_duration_not_network_latency");
        row.addProperty("observer_overhead_unmeasured", true);
        row.addProperty("fixedscale_qualification", "independent_external_ledger_and_runtime_oracle_required");
        row.addProperty("tick_observer", "required_ServerLevel.tickChunk_HEAD_native_mixin");
        return row;
    }
    List<String> samples() { return List.copyOf(tickRows); }
    JsonObject cleanup() throws IOException {
        long begin = System.nanoTime(); int residuals = 0;
        IOException firstFailure = null;
        JsonArray released = new JsonArray();
        for (int i = participants.size() - 1; i >= 0; i--) {
            Participant p = participants.get(i);
            try {
                level.getServer().getConnection().getConnections().remove(p.connection);
                if (level.getServer().getPlayerList().getPlayers().contains(p.player))
                    level.getServer().getPlayerList().remove(p.player);
                if (!p.player.isRemoved()) p.player.discard();
                p.channel.finishAndReleaseAll();
                for (LevelResource folder : List.of(LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR,
                        LevelResource.PLAYER_ADVANCEMENTS_DIR)) {
                    Path path = level.getServer().getWorldPath(folder)
                            .resolve(p.player.getUUID() + (folder == LevelResource.PLAYER_DATA_DIR ? ".dat" : ".json"));
                    if (Files.isSymbolicLink(path)) throw new IOException("synthetic player state path replaced by symlink");
                    Files.deleteIfExists(path);
                    if (folder == LevelResource.PLAYER_DATA_DIR)
                        Files.deleteIfExists(path.resolveSibling(path.getFileName() + "_old"));
                }
                if (level.getServer().getPlayerList().getPlayer(p.player.getUUID()) != null || p.channel.isOpen()) residuals++;
                released.add(p.player.getUUID().toString());
            } catch (Exception failure) {
                residuals++;
                IOException itemFailure = new IOException("synthetic participant cleanup failed for " + p.player.getUUID(), failure);
                if (firstFailure == null) firstFailure = itemFailure;
                else firstFailure.addSuppressed(itemFailure);
            }
            if (System.nanoTime() - begin >= CLEANUP_DEADLINE_NANOS && firstFailure == null)
                firstFailure = new IOException("synthetic cleanup exceeded 120 seconds");
        }
        if (firstFailure != null) throw firstFailure;
        participants.clear(); footprintChunks.clear();
        JsonObject row = new JsonObject(); row.addProperty("released", released.size()); row.add("released_uuids", released);
        long now = System.nanoTime();
        long elapsedMillis = Math.max(0, (now - begin) / 1_000_000L);
        boolean withinDeadline = elapsedMillis <= 120_000;
        row.addProperty("owned_residuals", residuals);
        row.addProperty("session_wall_millis_from_cohort_begin", Math.max(0, (now - startWallNanos) / 1_000_000L));
        row.addProperty("cleanup_wall_millis_from_cleanup_begin", elapsedMillis);
        row.addProperty("cleanup_within_120_seconds", withinDeadline);
        row.addProperty("cleanup_complete", residuals == 0 && withinDeadline);
        row.addProperty("baseline_restored", false); row.addProperty("real_login_proven", false);
        return row;
    }
    private static int coordinate(JsonObject p, String field) {
        long value = Protocol.number(p, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("coordinate overflow " + field);
        return (int) value;
    }
}
