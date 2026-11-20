package com.bettercontent.playtestsupport;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/** Nonshipping server-thread mailbox; no game registrations, permissions, commands or implicit fixtures. */
@Mod("better_playtest_native_support")
public final class NativeSupport {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_REQUEST = 1_048_576, MAX_ROWS = 100_000, MAX_INVENTORY_BYTES = 16_777_216;
    // Build 81's effective recipe semantics are 37,756,555 bytes; leave all other domain caps unchanged.
    private static final int MAX_RECIPE_SEMANTICS_BYTES = 50_331_648;
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static volatile NativeSupport instance;
    private record DropScope(BlockPos pos, boolean matched) {}
    /** Actual server-thread packet only; network-thread HEAD pass is ignored. Never sends or consumes a packet. */
    public static void onRealClientUsePacket(ServerPlayer player, InteractionHand hand) {
        NativeSupport support = instance;
        if (support == null || support.itemObserver == null || support.server == null ||
                !support.server.isSameThread() || player == null) return;
        try {
            if (support.itemObserver.binding.actorId.equals(player.getUUID())) support.nativeActionEverMutated = true;
            support.itemObserver.packet(player, hand, support.tick);
        }
        catch (Throwable capture) { support.itemObserver.fail("real_packet_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    /** Optional pinned Kotlin enqueued handler read hooks; fail-soft and never alter packet control flow. */
    public static void onBetterSpawnsFinalizeBefore(NetworkEvent.Context context, Object packet) {
        NativeSupport support = instance;
        if (support == null || support.onboardingObserver == null || support.server == null ||
                !support.server.isSameThread()) return;
        try {
            ServerPlayer actor = context.getSender();
            if (actor != null) {
                if (support.onboardingObserver.binding.actorId.equals(actor.getUUID())) support.nativeActionEverMutated = true;
                support.onboardingObserver.packetBefore(actor, packet, support.tick);
            }
        } catch (Throwable capture) {
            support.onboardingObserver.fail("pinned_packet_hook_failed:" + capture.getClass().getSimpleName());
        }
    }
    public static void onBetterSpawnsFinalizeAfter(NetworkEvent.Context context, Object packet) {
        NativeSupport support = instance;
        if (support == null || support.onboardingObserver == null || support.server == null ||
                !support.server.isSameThread()) return;
        try {
            ServerPlayer actor = context.getSender();
            if (actor != null) support.onboardingObserver.packetAfter(actor, support.tick);
        } catch (Throwable capture) {
            support.onboardingObserver.fail("pinned_packet_return_hook_failed:" + capture.getClass().getSimpleName());
        }
    }
    /** Netty decoded inbound read-only identity, paired with the later actual server-thread handler. */
    public static void realNetworkPacketIngress(net.minecraft.network.Connection connection,
                                                net.minecraft.network.protocol.Packet<?> packet) {
        NativeSupport s = instance;
        PassivePhysicalDig observed = s == null ? null : s.physicalDig;
        if (observed == null || !(packet instanceof net.minecraft.network.protocol.game.ServerboundPlayerActionPacket action)) return;
        try { observed.ingress(connection, action); }
        catch (Throwable error) { observed.fail("decoded_remote_ingress_capture_failed:" + error.getClass().getSimpleName()); }
    }
    /** Passive actual inbound server-thread packet/game-mode path. Never calls or constructs gameplay actions. */
    public static void realPacketDigBefore(ServerPlayer actor,
                                           net.minecraft.network.protocol.game.ServerboundPlayerActionPacket packet) {
        NativeSupport s = instance;
        if (s == null || s.physicalDig == null || s.server == null || !s.server.isSameThread()) return;
        try {
            if (actor != null && actor.getUUID().equals(s.physicalDig.binding.actorId) &&
                    packet.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)
                s.nativeActionEverMutated = true; // physical action may affect world; never claim native cleanup restored it.
            s.physicalDig.packetBefore(actor, packet, s.tick);
        } catch (Throwable error) { s.physicalDig.fail("real_packet_head_failed:" + error.getClass().getSimpleName()); }
    }
    public static void realPacketDigAfter(ServerPlayer actor,
                                          net.minecraft.network.protocol.game.ServerboundPlayerActionPacket packet) {
        NativeSupport s = instance;
        if (s == null || s.physicalDig == null || s.server == null || !s.server.isSameThread()) return;
        try { s.physicalDig.packetAfter(actor, packet, s.tick); }
        catch (Throwable error) { s.physicalDig.fail("real_packet_return_failed:" + error.getClass().getSimpleName()); }
    }
    public static void realGameModeDigAction(ServerPlayer actor, BlockPos pos,
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action,
            net.minecraft.core.Direction face, int sequence) {
        NativeSupport s = instance;
        if (s == null || s.physicalDig == null || s.server == null || !s.server.isSameThread()) return;
        try { s.physicalDig.gameMode(actor, pos, action, face, sequence, s.tick); }
        catch (Throwable error) { s.physicalDig.fail("real_gamemode_action_failed:" + error.getClass().getSimpleName()); }
    }
    public static void realGameModeDestroyBefore(ServerPlayer actor, BlockPos pos) {
        NativeSupport s = instance;
        if (s == null || s.physicalDig == null || s.server == null || !s.server.isSameThread()) return;
        try { s.physicalDig.destroyBefore(actor, pos, s.tick); }
        catch (Throwable error) { s.physicalDig.fail("real_gamemode_destroy_head_failed:" + error.getClass().getSimpleName()); }
    }
    public static void realGameModeDestroyAfter(ServerPlayer actor, BlockPos pos, boolean result) {
        NativeSupport s = instance;
        if (s == null || s.physicalDig == null || s.server == null || !s.server.isSameThread()) return;
        try { s.physicalDig.destroyAfter(actor, pos, result, s.tick); }
        catch (Throwable error) { s.physicalDig.fail("real_gamemode_destroy_return_failed:" + error.getClass().getSimpleName()); }
    }
    /** Optional pinned water provider callback HEAD/RETURN; never invokes provider behavior. */
    public static void onWaterAutoProviderBefore(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        NativeSupport support = instance;
        if (support == null || support.waterAutoObserver == null || support.server == null ||
                !support.server.isSameThread() || !(event.player instanceof ServerPlayer actor)) return;
        if (support.waterAutoObserver.binding.actorId.equals(actor.getUUID()) &&
                event.phase == net.minecraftforge.event.TickEvent.Phase.END && actor.tickCount % 10 == 0)
            support.nativeActionEverMutated = true; // real auto-hydration may change source/thirst; no baseline-restored claim.
        support.waterAutoObserver.before(event, actor, support.tick);
    }
    public static void onWaterAutoProviderAfter(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        NativeSupport support = instance;
        if (support == null || support.waterAutoObserver == null || support.server == null ||
                !support.server.isSameThread() || !(event.player instanceof ServerPlayer actor)) return;
        support.waterAutoObserver.after(event, actor, support.tick);
    }
    private static final ThreadLocal<Deque<DropScope>> DROP_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class MutationFrame {
        final net.minecraft.world.level.chunk.LevelChunk chunk;
        final BlockPos pos;
        final BlockState old;
        final BlockMutationScope.Position key;
        boolean paletteSeen;
        MutationFrame(net.minecraft.world.level.chunk.LevelChunk chunk, BlockPos pos, BlockState old,
                      BlockMutationScope.Position key) {
            this.chunk = chunk; this.pos = pos.immutable(); this.old = old; this.key = key;
        }
    }
    private static final ThreadLocal<Deque<MutationFrame>> MUTATION_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    /** Pinned ORIGINAL MinecraftServer.stopServer→removeTicketsOnClosing INVOKE, before drain ticks/unload/save. */
    public static void shutdownDrainEntry(net.minecraft.server.MinecraftServer actualServer) {
        NativeSupport s = instance;
        if (s == null || s.server != actualServer || s.stopped) return;
        try {
            if (s.mutationOwnership != null) s.mutationOwnership.shutdownDrainEntry(actualServer, s.tick);
            if (s.blockBirthTrace != null) s.blockBirthTrace.ownership.shutdownDrainEntry(actualServer, s.tick);
        } catch (Throwable error) {
            if (s.blockMutationTrace != null) s.blockMutationTrace.failedScope("shutdown_drain_entry_capture_failed:" + error.getClass().getSimpleName());
            if (s.blockBirthTrace != null) s.blockBirthTrace.fail("shutdown_drain_entry_capture_failed:" + error.getClass().getSimpleName());
        }
    }
    /** Pinned ORIGINAL MinecraftServer.stopServer RETURN, after saveAllChunks; STOP event seals separately. */
    public static void shutdownDrainReturn(net.minecraft.server.MinecraftServer actualServer) {
        NativeSupport s = instance;
        if (s == null || s.server != actualServer || s.stopped) return;
        try {
            if (s.mutationOwnership != null) s.mutationOwnership.shutdownDrainReturn(actualServer, s.tick);
            if (s.blockBirthTrace != null) s.blockBirthTrace.ownership.shutdownDrainReturn(actualServer, s.tick);
        } catch (Throwable error) {
            if (s.blockMutationTrace != null) s.blockMutationTrace.failedScope("shutdown_drain_return_capture_failed:" + error.getClass().getSimpleName());
            if (s.blockBirthTrace != null) s.blockBirthTrace.fail("shutdown_drain_return_capture_failed:" + error.getClass().getSimpleName());
        }
    }
    /** Read-only pre-world-load scope: captures the actual palette write, never invokes a policy or write. */
    public static void blockMutationEnter(net.minecraft.world.level.chunk.LevelChunk chunk, BlockPos pos) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        try {
            if (!(chunk.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
            BlockMutationScope.Position key = new BlockMutationScope.Position(
                    level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
            boolean relevant = support.blockMutationTrace.scope.positionSet.contains(key);
            if (relevant && !level.getServer().isSameThread())
                support.blockMutationTrace.failedScope("off_server_thread_chunk_mutation");
            if (relevant && (support.mutationOwnership == null || !support.mutationOwnership.watchedChunk(chunk)))
                support.blockMutationTrace.failedScope("watched_chunk_write_before_load_registration");
            BlockState old = relevant ? chunk.getBlockState(pos) : null;
            if (relevant) {
                support.blockMutationTrace.initialState(key, old);
                if (!support.blockMutationTrace.matchesObservedState(key, old))
                    support.blockMutationTrace.failedScope("unobserved_state_drift_at_mutation_entry");
            }
            MUTATION_SCOPES.get().push(new MutationFrame(chunk, pos, old, relevant ? key : null));
        } catch (Throwable failed) {
            support.blockMutationTrace.failedScope("mutation_entry_capture_failed:" + failed.getClass().getSimpleName());
        }
    }
    /** The delegated section setter must NOT double-count the already-captured private palette write. */
    public static void blockPaletteWritten(net.minecraft.world.level.chunk.LevelChunkSection section,
                                           int x, int y, int z, BlockState prior, BlockState requested) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        try {
            Deque<MutationFrame> stack = MUTATION_SCOPES.get();
            BlockMutationOwnership.Owner owner = support.mutationOwnership.forSection(section);
            if (owner == null) {
                if (!stack.isEmpty() && stack.peek().key != null)
                    support.blockMutationTrace.failedScope("section_write_without_registered_owner");
                return;
            }
            BlockMutationScope.Position position = owner.atIndex(BlockMutationOwnership.index(x, y, z));
            if (position == null) return;
            BlockState actual = section.getBlockState(x, y, z);
            if (!stack.isEmpty() && position.equals(stack.peek().key)) stack.peek().paletteSeen = true;
            if (!support.blockMutationTrace.matchesObservedState(position, actual)) {
                support.blockMutationTrace.failedScope("section_write_without_private_palette_event");
                support.blockMutationTrace.record(position, prior, actual, support.tick,
                        "LevelChunkSection_palette_fallback_incomplete");
            }
        } catch (Throwable failed) {
            support.blockMutationTrace.failedScope("palette_capture_failed:" + failed.getClass().getSimpleName());
        }
    }
    private record DirectSet(net.minecraft.world.level.chunk.PalettedContainer<?> container, int index,
                             BlockState old) {}
    private static final ThreadLocal<Deque<DirectSet>> DIRECT_SETS = ThreadLocal.withInitial(ArrayDeque::new);
    private record ReadBefore(net.minecraft.world.level.chunk.PalettedContainer<?> container,
                              Map<BlockMutationScope.Position, BlockState> states) {}
    private static final ThreadLocal<Deque<ReadBefore>> CONTAINER_READS = ThreadLocal.withInitial(ArrayDeque::new);
    /** Lower-level actual private palette mutation, one row regardless of public wrapper delegation. */
    public static void blockContainerGetAndSet(net.minecraft.world.level.chunk.PalettedContainer<?> container,
                                               int index, Object prior) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        try { support.recordPrivatePalette(container, index, prior, "PalettedContainer.getAndSet_private_index_return"); }
        catch (Throwable failed) { support.blockMutationTrace.failedScope("private_palette_getandset_failed:" + failed.getClass().getSimpleName()); }
    }
    public static void blockContainerSetBefore(net.minecraft.world.level.chunk.PalettedContainer<?> container, int index) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        try {
            BlockMutationOwnership.Owner owner = support.mutationOwnership.forContainer(container);
            BlockState prior = owner != null && owner.atIndex(index) != null ? (BlockState) container.get(
                    BlockMutationOwnership.x(index), BlockMutationOwnership.y(index), BlockMutationOwnership.z(index)) : null;
            DIRECT_SETS.get().push(new DirectSet(container, index, prior));
        } catch (Throwable failed) { support.blockMutationTrace.failedScope("private_palette_set_before_failed:" + failed.getClass().getSimpleName()); }
    }
    public static void blockContainerSetAfter(net.minecraft.world.level.chunk.PalettedContainer<?> container, int index) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        Deque<DirectSet> stack = DIRECT_SETS.get();
        if (stack.isEmpty()) { support.blockMutationTrace.failedScope("private_palette_set_missing_head"); return; }
        try {
            DirectSet before = stack.pop();
            if (before.container() != container || before.index() != index) {
                support.blockMutationTrace.failedScope("private_palette_set_scope_mismatch"); stack.clear(); return;
            }
            support.recordPrivatePalette(container, index, before.old(), "PalettedContainer.set_private_index_return");
        } catch (Throwable failed) { support.blockMutationTrace.failedScope("private_palette_set_after_failed:" + failed.getClass().getSimpleName()); }
        finally { if (stack.isEmpty()) DIRECT_SETS.remove(); }
    }
    private void recordPrivatePalette(net.minecraft.world.level.chunk.PalettedContainer<?> container,
                                      int index, Object prior, String path) {
        BlockMutationOwnership.Owner owner = mutationOwnership.forContainer(container);
        if (owner == null) return; // unrelated biome or as-yet-unowned chunk, never invented ownership
        BlockMutationScope.Position position = owner.atIndex(index);
        if (position == null) return;
        if (!(prior instanceof BlockState old)) { blockMutationTrace.failedScope("private_palette_prior_not_block_state"); return; }
        if (!mutationOwnership.active(owner)) return;
        Object observed = container.get(BlockMutationOwnership.x(index), BlockMutationOwnership.y(index),
                BlockMutationOwnership.z(index));
        if (!(observed instanceof BlockState actual)) { blockMutationTrace.failedScope("private_palette_new_not_block_state"); return; }
        blockMutationTrace.record(position, old, actual, tick, path);
        Deque<MutationFrame> frames = MUTATION_SCOPES.get();
        if (!frames.isEmpty() && position.equals(frames.peek().key)) frames.peek().paletteSeen = true;
    }
    public static void blockContainerReadBefore(net.minecraft.world.level.chunk.PalettedContainer<?> container) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        try {
            BlockMutationOwnership.Owner owner = support.mutationOwnership.forContainer(container);
            Map<BlockMutationScope.Position, BlockState> prior = new LinkedHashMap<>();
            if (owner != null && support.mutationOwnership.active(owner))
                for (BlockMutationScope.Position position : owner.positions)
                    prior.put(position, (BlockState) container.get(position.x() & 15, position.y() & 15, position.z() & 15));
            CONTAINER_READS.get().push(new ReadBefore(container, prior));
        } catch (Throwable failed) { support.blockMutationTrace.failedScope("palette_read_before_failed:" + failed.getClass().getSimpleName()); }
    }
    public static void blockContainerReadAfter(net.minecraft.world.level.chunk.PalettedContainer<?> container) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        Deque<ReadBefore> stack = CONTAINER_READS.get();
        if (stack.isEmpty()) { support.blockMutationTrace.failedScope("palette_read_missing_head"); return; }
        try {
            ReadBefore before = stack.pop();
            if (before.container() != container) { support.blockMutationTrace.failedScope("palette_read_scope_mismatch"); stack.clear(); return; }
            BlockMutationOwnership.Owner owner = support.mutationOwnership.forContainer(container);
            if (owner != null && support.mutationOwnership.active(owner))
                for (var entry : before.states().entrySet())
                    support.blockMutationTrace.record(entry.getKey(), entry.getValue(),
                            (BlockState) container.get(entry.getKey().x() & 15, entry.getKey().y() & 15,
                                    entry.getKey().z() & 15), support.tick, "PalettedContainer.read_full_replacement");
        } catch (Throwable failed) { support.blockMutationTrace.failedScope("palette_read_after_failed:" + failed.getClass().getSimpleName()); }
        finally { if (stack.isEmpty()) CONTAINER_READS.remove(); }
    }
    public static void blockSectionsExposed(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        NativeSupport support = instance;
        if (support == null || support.mutationOwnership == null || !(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk)) return;
        try {
            // O(1) watched identity filter BEFORE bounded StackWalker; do not inspect unwatched chunks.
            if (!support.mutationOwnership.watchedChunk(levelChunk) || support.mutationOwnership.internalRead()) return;
            if (ReadOnlyChunkSerializerProfile.readOnly("getSections")) {
                if (!(levelChunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
                    support.blockMutationTrace.failedScope("serializer_sections_read_off_server_thread");
                return;
            }
            support.mutationOwnership.exposedSections(levelChunk);
        } catch (Throwable failure) {
            support.blockMutationTrace.failedScope("section_array_exposure_check_failed:" + failure.getClass().getSimpleName());
        }
    }
    public static void blockStatesExposed(net.minecraft.world.level.chunk.LevelChunkSection section,
                                          net.minecraft.world.level.chunk.PalettedContainer<?> states) {
        NativeSupport support = instance;
        if (support != null && support.mutationOwnership != null) {
            try {
                if (!support.mutationOwnership.watchedSection(section)) return;
                if (ReadOnlyChunkSerializerProfile.readOnly("getStates")) {
                    // A serializer read is not an external mutable escape; identity drift remains a hard failure.
                    var owner = support.mutationOwnership.forSection(section);
                    if (owner.states != states)
                        support.blockMutationTrace.failedScope("serializer_section_states_identity_drift");
                    if (!(owner.chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
                        support.blockMutationTrace.failedScope("serializer_states_read_off_server_thread");
                    return;
                }
                support.mutationOwnership.exposedStates(section, states);
            }
            catch (Throwable failure) {
                support.blockMutationTrace.failedScope("container_exposure_check_failed:" + failure.getClass().getSimpleName());
            }
        }
    }
    public static void blockMutationExit(net.minecraft.world.level.chunk.LevelChunk chunk, BlockPos pos) {
        NativeSupport support = instance;
        if (support == null || support.blockMutationTrace == null) return;
        Deque<MutationFrame> stack = MUTATION_SCOPES.get();
        if (stack.isEmpty()) return;
        try {
            MutationFrame frame = stack.peek();
            if (frame.chunk != chunk || !frame.pos.equals(pos)) {
                support.blockMutationTrace.failedScope("mutation_exit_scope_mismatch"); stack.clear(); return;
            }
            stack.pop();
            if (frame.key != null && !support.blockMutationTrace.matchesObservedState(frame.key, chunk.getBlockState(pos)))
                support.blockMutationTrace.failedScope(frame.paletteSeen ?
                        "unobserved_state_drift_after_palette_event" : "block_changed_without_scoped_palette_event");
        } catch (Throwable failed) {
            support.blockMutationTrace.failedScope("mutation_exit_capture_failed:" + failed.getClass().getSimpleName());
        } finally {
            if (stack.isEmpty()) MUTATION_SCOPES.remove();
        }
    }
    /** Actual block destruction progress RETURN under the server-invoked START; never queries it twice. */
    public static void realDestroyProgressReturned(BlockState state, net.minecraft.world.entity.player.Player player,
                                                    net.minecraft.world.level.BlockGetter world, BlockPos pos, float result) {
        NativeSupport support = instance;
        if (support == null || (support.pendingDig == null && support.physicalDig == null) || support.server == null ||
                !support.server.isSameThread() || !(player instanceof ServerPlayer actor)) return;
        try {
            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            boolean direct = false;
            for (int i = 0; i + 1 < frames.length; i++) {
                StackTraceElement current = frames[i];
                if (!current.getClassName().equals("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase") ||
                        !current.getMethodName().equals("getDestroyProgress")) continue;
                StackTraceElement caller = frames[i + 1];
                direct = caller.getClassName().equals("net.minecraft.server.level.ServerPlayerGameMode") &&
                        caller.getMethodName().equals("handleBlockBreakAction");
                break;
            }
            if (support.pendingDig != null)
                support.pendingDig.progressReturned(actor, pos, state, result, support.tick,
                        direct && world == actor.serverLevel());
            else support.physicalDig.progressReturned(actor, pos, state, result, support.tick,
                    direct && world == actor.serverLevel());
        } catch (Throwable failure) {
            if (support.pendingDig != null) support.pendingDig.progressObserverFailed();
            if (support.physicalDig != null) support.physicalDig.fail("actual_progress_observer_failed:" + failure.getClass().getSimpleName());
        }
    }
    /** Actual Forge harvest gate RETURN, never an additional Forge policy invocation. */
    public static void harvestCheckReturn(net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                          net.minecraft.world.entity.player.Player player,
                                          net.minecraft.world.level.block.state.BlockState state, boolean result) {
        NativeSupport support = instance;
        if (support == null || (support.pendingDig == null && support.physicalDig == null) || support.server == null
                || !support.server.isSameThread() || !(player instanceof ServerPlayer actor)) return;
        try {
            if (level == actor.serverLevel()) {
                if (support.pendingDig != null) support.pendingDig.harvestReturned(actor, pos, state, result, support.tick);
                else support.physicalDig.harvestReturned(actor, pos, state, result, support.tick);
            }
        } catch (Throwable ignored) {
            if (support.pendingDig != null) support.pendingDig.harvestObserverError();
            if (support.physicalDig != null) support.physicalDig.fail("actual_harvest_observer_failed");
        }
    }
    /** An observer failure only marks attribution incomplete; it cannot alter a harvest decision. */
    public static void harvestObserverFailed() {
        try {
            NativeSupport support = instance;
            if (support != null && support.server != null && support.server.isSameThread()) {
                if (support.pendingDig != null) support.pendingDig.harvestObserverError();
                if (support.physicalDig != null) support.physicalDig.fail("actual_harvest_local_capture_unavailable");
            }
        } catch (Throwable ignored) { /* no production hook side effects */ }
    }
    /** Read-only actual ItemStack.hurtAndBreak Consumer.accept boundary, before callback/shrink. */
    public static void onToolBreakCauseBefore(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        NativeSupport support = instance;
        if (support == null || support.physicalDig == null || support.server == null ||
                !support.server.isSameThread() || !(entity instanceof ServerPlayer actor)) return;
        try { support.physicalDig.toolCauseBefore(stack, actor, support.tick); }
        catch (Throwable capture) { support.physicalDig.fail("physical_tool_cause_before_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    /** Read-only balance of the same actual Consumer.accept, AFTER the callback and BEFORE shrink. */
    public static void onToolBreakCauseAfter(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        NativeSupport support = instance;
        if (support == null || support.physicalDig == null || support.server == null ||
                !support.server.isSameThread() || !(entity instanceof ServerPlayer actor)) return;
        try { support.physicalDig.toolCauseAfter(stack, actor, support.tick); }
        catch (Throwable capture) { support.physicalDig.fail("physical_tool_cause_after_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    /** Actual EquipmentSlot overload only; the InteractionHand wrapper delegates once to this overload. */
    public static void onToolEquipmentBreak(net.minecraft.world.entity.LivingEntity entity,
                                            net.minecraft.world.entity.EquipmentSlot slot) {
        NativeSupport support = instance;
        if (support == null || support.physicalDig == null || support.server == null ||
                !support.server.isSameThread() || !(entity instanceof ServerPlayer actor)) return;
        try { support.physicalDig.toolBreakCallback(actor, slot, support.tick); }
        catch (Throwable capture) { support.physicalDig.fail("physical_equipment_break_capture_failed:" + capture.getClass().getSimpleName()); }
    }
    /** Actual Player.actuallyHurt override; all callbacks observe unchanged vanilla values on server thread. */
    public static void incomingHitHead(net.minecraft.world.entity.player.Player target,
                                       net.minecraft.world.damagesource.DamageSource source, float original) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(target instanceof ServerPlayer actor)) return;
        try { s.incomingHit.begin(actor, source, original, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_head_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingBeforeArmor(net.minecraft.world.entity.player.Player target,
                                           net.minecraft.world.damagesource.DamageSource source, float actual) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(target instanceof ServerPlayer actor)) return;
        try { s.incomingHit.beforeArmor(actor, source, actual, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_pre_armor_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingArmorOperands(net.minecraft.world.entity.LivingEntity entity,
                                             net.minecraft.world.damagesource.DamageSource source,
                                             float amount, float armor, float toughness) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(entity instanceof ServerPlayer actor)) return;
        try { s.incomingHit.actualArmorOperands(actor, source, amount, armor, toughness, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_actual_armor_operands_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingArmorCaptureUnavailable(net.minecraft.world.entity.LivingEntity entity, String reason) {
        NativeSupport s = instance;
        if (s != null && s.incomingHit != null && s.server != null && s.server.isSameThread() &&
                entity instanceof ServerPlayer actor && s.incomingHit.binding.actorId.equals(actor.getUUID()))
            s.incomingHit.fail(reason);
    }
    public static void incomingBeforeMagic(net.minecraft.world.entity.player.Player target,
                                           net.minecraft.world.damagesource.DamageSource source, float actual) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(target instanceof ServerPlayer actor)) return;
        try { s.incomingHit.beforeMagic(actor, source, actual, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_pre_magic_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingMagicHead(net.minecraft.world.entity.LivingEntity entity,
                                         net.minecraft.world.damagesource.DamageSource source) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(entity instanceof ServerPlayer actor)) return;
        try { s.incomingHit.magicBegin(actor, source); }
        catch (Throwable error) { s.incomingHit.fail("incoming_magic_head_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingMagicReturnBranch(net.minecraft.world.entity.LivingEntity entity,
                                                 net.minecraft.world.damagesource.DamageSource source,
                                                 String branch, float value) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(entity instanceof ServerPlayer actor)) return;
        try { s.incomingHit.magicReturnBranch(actor, source, branch, value, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_magic_return_branch_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingEnchantmentInvoke(net.minecraft.world.entity.LivingEntity entity,
                                                  net.minecraft.world.damagesource.DamageSource source) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(entity instanceof ServerPlayer actor)) return;
        try { s.incomingHit.enchantmentInvoke(actor, source, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_enchantment_invoke_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingMagicReturn(net.minecraft.world.entity.LivingEntity entity,
                                           net.minecraft.world.damagesource.DamageSource source, float actual) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(entity instanceof ServerPlayer actor)) return;
        try { s.incomingHit.magicEnd(actor, source, actual, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_magic_return_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingEnchantmentReturn(net.minecraft.world.damagesource.DamageSource source, int result) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread()) return;
        try { s.incomingHit.enchantmentReturn(source, result, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_enchantment_return_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingBeforeDamageHook(net.minecraft.world.entity.player.Player target, float actual) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(target instanceof ServerPlayer actor)) return;
        try { s.incomingHit.beforeDamageHook(actor, s.incomingHit.activeSource(), actual, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_pre_damage_hook_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingAfterDamageHook(net.minecraft.world.entity.player.Player target, float actual) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(target instanceof ServerPlayer actor)) return;
        try { s.incomingHit.afterDamageHook(actor, s.incomingHit.activeSource(), actual, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_post_damage_hook_capture_failed:" + error.getClass().getSimpleName()); }
    }
    public static void incomingHitReturn(net.minecraft.world.entity.player.Player target,
                                         net.minecraft.world.damagesource.DamageSource source) {
        NativeSupport s = instance;
        if (s == null || s.incomingHit == null || s.server == null || !s.server.isSameThread() ||
                !(target instanceof ServerPlayer actor)) return;
        try { s.incomingHit.returned(actor, source, s.tick); }
        catch (Throwable error) { s.incomingHit.fail("incoming_player_return_capture_failed:" + error.getClass().getSimpleName()); }
    }
    /** Called only by the required nonshipping native Block.popResource read-only observer. */
    public static void popResourceEnter(net.minecraft.world.level.Level level, BlockPos pos, ItemStack stack) {
        NativeSupport support = instance;
        if (support == null || (support.pendingDig == null && support.physicalDig == null) ||
                support.server == null || !support.server.isSameThread()) return;
        boolean match;
        if (support.pendingDig != null) {
            NativeDig dig = support.pendingDig;
            match = !stack.isEmpty() && dig.breakEvent && !dig.breakCanceled && dig.breakTick == support.tick
                    && dig.pos.equals(pos) && level.dimension().location().toString().equals(dig.dimension);
        } else match = !stack.isEmpty() && support.physicalDig.dropScoped(pos,
                level.dimension().location().toString());
        DROP_SCOPES.get().push(new DropScope(pos.immutable(), match));
    }
    public static void nativeChunkTick(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.world.level.chunk.LevelChunk chunk) {
        NativeSupport support = instance;
        if (support != null && support.pendingScale != null && support.server != null
                && support.server.isSameThread()) support.pendingScale.chunkTick(level, chunk);
    }
    public static void popResourceExit() {
        NativeSupport support = instance;
        if (support == null || (support.pendingDig == null && support.physicalDig == null) ||
                support.server == null || !support.server.isSameThread()) return;
        Deque<DropScope> scopes = DROP_SCOPES.get();
        if (scopes.isEmpty()) throw new IllegalStateException("native popResource scope RETURN without HEAD");
        scopes.pop(); if (scopes.isEmpty()) DROP_SCOPES.remove();
    }
    private final String run = System.getProperty("bc.playtest.runId", "");
    private final String token = System.getProperty("bc.playtest.token", "");
    private final Path directory;
    private MinecraftServer server;
    private long nextRequest = 1, nextOutput = 1, tick = 0;
    private boolean stopped;
    private Protocol.Request waiting;
    private long waitEnd, waitDeadline;
    private Protocol.Request pendingUse;
    private UUID pendingUsePlayer;
    private InteractionHand pendingUseHand;
    private JsonObject pendingUseBaseline;
    private boolean pendingUseFinished;
    private OwnedFixture fixture;
    private boolean fixtureEverMutated, nativeActionEverMutated;
    private NativeDig pendingDig;
    private PassiveItemUse itemObserver;
    private PassiveCraftEvent craftObserver;
    private Protocol.Request pendingCraftAwait;
    private long pendingCraftDeadline;
    private PassiveOnboarding onboardingObserver;
    private PassiveWaterAuto waterAutoObserver;
    private volatile PassivePhysicalDig physicalDig;
    private PassiveIncomingHit incomingHit;
    private Protocol.Request pendingWaterAwait, pendingPhysicalDigAwait, pendingIncomingHitAwait;
    private long pendingWaterDeadline, pendingPhysicalDigDeadline, pendingIncomingHitDeadline;
    private Protocol.Request pendingPassiveAwait;
    private long pendingPassiveAwaitDeadline;
    private SyntheticScaleCohort pendingScale, activeScale;
    private BlockMutationTrace blockMutationTrace;
    private BlockMutationOwnership mutationOwnership;
    private BlockBirthTrace blockBirthTrace;
    private JsonObject birthPreActionPrefix;
    private JsonObject mutationPreActionPrefix;
    private long mutationPrefixRequest = -1;
    private long serverTickStartNanos;
    private record Login(long tick, String dimension, double x, double y, double z,
                         boolean playerDataPreexists, String biome, String ground, boolean feetAir,
                         boolean headAir, boolean groundSolid) {}
    private final Map<UUID, Login> observedLogins = new LinkedHashMap<>();
    private record Lifecycle(long deathTick, String cause, boolean canceled, long cloneTick, boolean wasDeath,
                             long respawnTick) {}
    private final Map<UUID, Lifecycle> lifecycles = new LinkedHashMap<>();
    private final Map<UUID, EntitySnapshot.Death> entityDeaths = new LinkedHashMap<>();

    public NativeSupport() {
        RepositoryNativeReporter.install(); // Independent opt-in GameTest registration/result reporter.
        String path = System.getProperty("bc.playtest.directory", "");
        directory = path.isBlank() ? null : Path.of(path).toAbsolutePath().normalize();
        if (directory == null || !directory.isAbsolute() || !run.matches("[A-Za-z0-9._:-]{1,128}")
                || !token.matches("[0-9a-fA-F]{64}")) {
            if (System.getProperty(BlockMutationScope.FILE_PROPERTY) != null ||
                    System.getProperty(BlockMutationScope.SHA_PROPERTY) != null ||
                    System.getProperty(BlockBirthScope.FILE_PROPERTY) != null ||
                    System.getProperty(BlockBirthScope.SHA_PROPERTY) != null ||
                    System.getProperty(RuntimeClassOrigins.CANDIDATE_PROPERTY) != null)
                throw new IllegalStateException("mutation scope requires active native mailbox before world load");
            LOG.warn("Playtest support inactive: directory/runId/token missing or malformed");
            return;
        }
        try {
            String candidateHash = System.getProperty(RuntimeClassOrigins.CANDIDATE_PROPERTY);
            if (candidateHash != null && !candidateHash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("prelaunch selected candidate SHA for class origins invalid");
            BlockMutationScope mutationScope = BlockMutationScope.open(directory, run);
            BlockBirthScope birthScope = BlockBirthScope.open(directory, run);
            if (mutationScope != null && birthScope != null &&
                    (mutationScope.scopeId.equals(birthScope.scopeId) ||
                            !mutationScope.epochId.equals(birthScope.epochId) ||
                            !mutationScope.campaignId.equals(birthScope.campaignId) ||
                            !mutationScope.lineageId.equals(birthScope.lineageId)))
                throw new IllegalArgumentException("present-origin and birth scope identities incompatible");
            if (mutationScope != null) {
                blockMutationTrace = new BlockMutationTrace(mutationScope, directory);
                mutationOwnership = new BlockMutationOwnership(blockMutationTrace);
            }
            if (birthScope != null) {
                blockBirthTrace = new BlockBirthTrace(birthScope, directory);
                BlockBirthHooks.install(blockBirthTrace);
            }
        } catch (Exception invalidScope) {
            throw new IllegalStateException("immutable prelaunch block mutation scope invalid; refusing server", invalidScope);
        }
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void start(ServerStartedEvent event) {
        server = event.getServer();
        if (blockMutationTrace != null && !MUTATION_SCOPES.get().isEmpty()) {
            blockMutationTrace.failedScope("mutation_scope_unbalanced_during_world_load");
            MUTATION_SCOPES.remove();
        }
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(directory.resolve("requests"), LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory.resolve("observations.jsonl"))
                    || Files.exists(directory.resolve("capabilities.json"), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("mailbox must be existing ordinary fresh directory");
            JsonObject capabilities = new JsonObject();
            capabilities.addProperty("schema", "bc.playtest_capabilities.v2");
            capabilities.addProperty("run_id", run);
            capabilities.addProperty("token", token);
            capabilities.add("supported_drivers", strings(Protocol.DRIVERS.stream().sorted().toList()));
            capabilities.addProperty("scale_contract_version", ScaleWireContract.VERSION);
            if (System.getProperty(RuntimeClassOrigins.CANDIDATE_PROPERTY) != null) {
                if (blockMutationTrace == null && blockBirthTrace == null)
                    throw new IOException("class origins require immutable stage1 or stage2 prelaunch scope to bind epoch");
                String epoch = blockMutationTrace != null ? blockMutationTrace.scope.epochId : blockBirthTrace.scope.epochId;
                String scopeId = blockMutationTrace != null ? blockMutationTrace.scope.scopeId : blockBirthTrace.scope.scopeId;
                capabilities.add("runtime_class_origins", RuntimeClassOrigins.capture(directory, run, epoch, scopeId));
            }
            capabilities.add("unsupported_sources", strings(List.of("client_render", "client_input", "real_login_proof", "external_mod_capabilities", "fake_player_scale")));
            atomic(directory.resolve("capabilities.json"), JSON.toJson(capabilities).getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) { stopped = true; LOG.error("Playtest mailbox startup failed closed", failure); }
    }
    @SubscribeEvent public void mutationChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
        if (mutationOwnership != null) try { mutationOwnership.loaded(chunk); }
        catch (Throwable failed) { blockMutationTrace.failedScope("watched_chunk_load_capture_failed:" + failed.getClass().getSimpleName()); }
        if (blockBirthTrace != null) blockBirthTrace.load(chunk, event.isNewChunk(), tick);
    }
    @SubscribeEvent public void mutationChunkUnload(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
        if (mutationOwnership != null) try { mutationOwnership.unloaded(chunk); }
        catch (Throwable failed) { blockMutationTrace.failedScope("watched_chunk_unload_capture_failed:" + failed.getClass().getSimpleName()); }
        if (blockBirthTrace != null) blockBirthTrace.unload(chunk);
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) {
        if (fixture != null) {
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(fixture.playerId);
            if (owner != null) {
                try {
                    JsonObject result = fixture.restore(owner);
                    if (result.get("cleanup_complete").getAsBoolean()) fixture = null;
                    else LOG.error("Fixture restoration left owned residuals on server stop; world is NOT clean");
                } catch (Exception failure) { LOG.error("Fixture could not restore on server stop; world is NOT clean", failure); }
            } else LOG.error("Fixture owner disconnected before server stop; world is NOT proven clean");
        }
        if (activeScale != null) {
            try { activeScale.cleanup(); activeScale = null; }
            catch (Exception failure) { LOG.error("Synthetic scale cohort remains on server stop", failure); }
        }
        if (physicalDig != null) {
            physicalDig.tool.serverStop(); // callback exception cannot leave this server thread's cause frame stale
            physicalDig.fail("server_stopped_before_passive_packet_terminal");
        }
        if (incomingHit != null) incomingHit.fail("server_stopped_before_actual_incoming_hit_terminal");
        DROP_SCOPES.remove(); MUTATION_SCOPES.remove(); DIRECT_SETS.remove(); CONTAINER_READS.remove();
        if (blockMutationTrace != null) {
            try {
                blockMutationTrace.shutdownDrainStatus(mutationOwnership.shutdownStatus(tick));
                blockMutationTrace.closeAtStop(tick);
            }
            catch (IOException failure) { LOG.error("Mutation stream terminal missing; natural ancestry unsupported", failure); }
        }
        if (blockBirthTrace != null) {
            try { blockBirthTrace.closeAtStop(tick); }
            catch (IOException failure) { LOG.error("Birth or postbirth mutation terminal missing; genesis unsupported", failure); }
        }
        BlockBirthHooks.clear();
        if (instance == this) instance = null;
        server = null; stopped = true;
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (server != null && event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && player.connection != null) {
            if (observedLogins.size() >= 256) observedLogins.remove(observedLogins.keySet().iterator().next());
            BlockPos feet = player.blockPosition(), ground = feet.below();
            var level = player.serverLevel();
            Path data = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
            boolean existed = Files.isRegularFile(data, LinkOption.NOFOLLOW_LINKS);
            boolean loaded = level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4);
            observedLogins.put(player.getUUID(), new Login(tick, level.dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), existed,
                    loaded ? level.getBiome(feet).unwrapKey().map(k -> k.location().toString()).orElse("unkeyed") : "unloaded",
                    loaded ? ForgeRegistries.BLOCKS.getKey(level.getBlockState(ground).getBlock()).toString() : "unloaded",
                    loaded && level.getBlockState(feet).isAir(),
                    loaded && level.getBlockState(feet.above()).isAir(),
                    loaded && level.getBlockState(ground).isSolidRender(level, ground)));
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void deathObserved(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster hostile) {
            entityDeaths.put(hostile.getUUID(), new EntitySnapshot.Death(
                    ForgeRegistries.ENTITY_TYPES.getKey(hostile.getType()).toString(),
                    hostile.level().dimension().location().toString(), tick, event.getSource().getMsgId(), event.isCanceled()));
            if (entityDeaths.size() > 256) entityDeaths.remove(entityDeaths.keySet().iterator().next());
        }
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            Lifecycle previous = lifecycles.get(player.getUUID());
            lifecycles.put(player.getUUID(), new Lifecycle(tick, event.getSource().getMsgId(), event.isCanceled(),
                    previous == null ? -1 : previous.cloneTick(), previous != null && previous.wasDeath(),
                    previous == null ? -1 : previous.respawnTick()));
            if (lifecycles.size() > 256) lifecycles.remove(lifecycles.keySet().iterator().next());
        }
    }
    @SubscribeEvent public void cloneObserved(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            Lifecycle previous = lifecycles.get(player.getUUID());
            lifecycles.put(player.getUUID(), new Lifecycle(previous == null ? -1 : previous.deathTick(),
                    previous == null ? "unobserved" : previous.cause(), previous != null && previous.canceled(),
                    tick, event.isWasDeath(), previous == null ? -1 : previous.respawnTick()));
        }
    }
    @SubscribeEvent public void respawnObserved(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            Lifecycle previous = lifecycles.get(player.getUUID());
            lifecycles.put(player.getUUID(), new Lifecycle(previous == null ? -1 : previous.deathTick(),
                    previous == null ? "unobserved" : previous.cause(), previous != null && previous.canceled(),
                    previous == null ? -1 : previous.cloneTick(), previous != null && previous.wasDeath(), tick));
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void joinedDrop(EntityJoinLevelEvent event) {
        if ((pendingDig == null && physicalDig == null) ||
                !(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item) ||
                server == null || !server.isSameThread()) return;
        DropScope scope = DROP_SCOPES.get().peek();
        if (physicalDig != null && (event.loadedFromDisk() || scope == null || !scope.matched())) {
            try { physicalDig.unscopedJoin(item, tick); }
            catch (Throwable failure) { physicalDig.fail("unscoped_item_join_check_failed:" + failure.getClass().getSimpleName()); }
            return;
        }
        if (event.loadedFromDisk()) return;
        if (pendingDig != null) {
            if (scope == null || !scope.matched() || !event.getLevel().dimension().location().toString().equals(pendingDig.dimension)) {
                pendingDig.unscopedJoin(); return;
            }
            try { pendingDig.recordDrop(item, scope.pos(), !event.isCanceled(), tick); }
            catch (Throwable failure) {
                if (pendingDig.trace != null) pendingDig.trace.unsupported("drop_observer_failed:" + failure.getClass().getSimpleName());
                pendingDig.scopeAborted();
            }
        } else if (scope != null && scope.matched()) {
            try { physicalDig.drop(item, scope.pos(), !event.isCanceled(), tick); }
            catch (Throwable failure) { physicalDig.fail("packet_path_drop_capture_failed:" + failure.getClass().getSimpleName()); }
        }
    }
    @SubscribeEvent public void syntheticPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && pendingScale != null
                && event.player instanceof ServerPlayer player) pendingScale.playerTick(player);
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void pickupBefore(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && server != null && server.isSameThread()) {
            if (pendingDig != null) {
                try { pendingDig.recordPickupBefore(player, event.getItem(), tick, event.isCanceled()); }
                catch (Throwable failure) {
                    if (pendingDig.trace != null) pendingDig.trace.unsupported("pickup_pre_observer_failed:" + failure.getClass().getSimpleName());
                }
            } else if (physicalDig != null) {
                try { physicalDig.pickupBefore(player, event.getItem(), event.isCanceled(), tick); }
                catch (Throwable failure) { physicalDig.fail("packet_path_pickup_pre_failed:" + failure.getClass().getSimpleName()); }
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void pickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && server != null && server.isSameThread()) {
            if (pendingDig != null) {
                try { pendingDig.recordPickup(player, event.getOriginalEntity(), event.getStack(), tick); }
                catch (Throwable failure) {
                    if (pendingDig.trace != null) pendingDig.trace.unsupported("pickup_post_observer_failed:" + failure.getClass().getSimpleName());
                }
            } else if (physicalDig != null) {
                try { physicalDig.pickupAfter(player, event.getOriginalEntity(), event.getStack(), tick); }
                catch (Throwable failure) { physicalDig.fail("packet_path_pickup_post_failed:" + failure.getClass().getSimpleName()); }
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR)
    public void craftedObserved(PlayerEvent.ItemCraftedEvent event) {
        if (craftObserver != null && server != null && server.isSameThread() &&
                event.getEntity() instanceof ServerPlayer actor) {
            try {
                if (craftObserver.binding.actorId.equals(actor.getUUID())) nativeActionEverMutated = true;
                craftObserver.crafted(actor, event, tick);
            } catch (Throwable capture) {
                craftObserver.fail("craft_observer_failed:" + capture.getClass().getSimpleName());
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void breakObserved(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (pendingDig != null) pendingDig.recordBreak(player, event.getPos(), event.isCanceled(), tick);
            else if (physicalDig != null && server != null && server.isSameThread())
                try { physicalDig.breakEvent(player, event.getPos(), event.getState(), event.isCanceled(), tick); }
                catch (Throwable failure) { physicalDig.fail("packet_path_break_event_failed:" + failure.getClass().getSimpleName()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void passiveUseStart(LivingEntityUseItemEvent.Start event) {
        if (itemObserver != null && server != null && server.isSameThread() &&
                event.getEntity() instanceof ServerPlayer actor) {
            try {
                if (itemObserver.binding.actorId.equals(actor.getUUID())) nativeActionEverMutated = true;
                itemObserver.start(actor, event, tick);
            }
            catch (Throwable capture) { itemObserver.fail("use_start_observer_failed:" + capture.getClass().getSimpleName()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void passiveUseStop(LivingEntityUseItemEvent.Stop event) {
        if (itemObserver != null && server != null && server.isSameThread() &&
                event.getEntity() instanceof ServerPlayer actor)
            itemObserver.stop(actor, tick);
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public void passiveUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (itemObserver != null && server != null && server.isSameThread() &&
                event.getEntity() instanceof ServerPlayer actor) {
            try { itemObserver.finish(actor, event, tick); }
            catch (Throwable capture) { itemObserver.fail("use_finish_observer_failed:" + capture.getClass().getSimpleName()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR)
    public void passiveOnboardingEvent(PlayerEvent event) {
        if (onboardingObserver == null || server == null || !server.isSameThread() ||
                !event.getClass().getName().equals("com.bettercontent.betterspawns.integration.PlayerStartFinalizedEvent") ||
                !(event.getEntity() instanceof ServerPlayer actor)) return;
        try {
            if (onboardingObserver.binding.actorId.equals(actor.getUUID())) nativeActionEverMutated = true;
            onboardingObserver.finalized(actor, event, tick);
        }
        catch (Throwable capture) { onboardingObserver.fail("finalization_event_observer_failed:" + capture.getClass().getSimpleName()); }
    }
    @SubscribeEvent public void finish(LivingEntityUseItemEvent.Finish event) {
        if (pendingUse != null && event.getEntity() instanceof ServerPlayer player
                && player.getUUID().equals(pendingUsePlayer)) pendingUseFinished = true;
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (server == null || stopped) return;
        if (event.phase == TickEvent.Phase.START) {
            if (mutationOwnership != null) mutationOwnership.tickDuringDrain();
            if (blockBirthTrace != null) blockBirthTrace.ownership.tickDuringDrain();
            serverTickStartNanos = System.nanoTime(); return;
        }
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (itemObserver != null) {
            try { itemObserver.tick(server.getPlayerList().getPlayer(itemObserver.binding.actorId), tick); }
            catch (Throwable capture) { itemObserver.fail("item_tick_observer_failed:" + capture.getClass().getSimpleName()); }
        }
        if (craftObserver != null) {
            try { craftObserver.tick(server.getPlayerList().getPlayer(craftObserver.binding.actorId), tick); }
            catch (Throwable capture) { craftObserver.fail("craft_tick_observer_failed:" + capture.getClass().getSimpleName()); }
        }
        if (onboardingObserver != null) {
            try { onboardingObserver.tick(server.getPlayerList().getPlayer(onboardingObserver.binding.actorId), tick); }
            catch (Throwable capture) { onboardingObserver.fail("onboarding_tick_observer_failed:" + capture.getClass().getSimpleName()); }
        }
        if (physicalDig != null) {
            try {
                ServerPlayer actor = server.getPlayerList().getPlayer(physicalDig.binding.actorId);
                if (actor == null) physicalDig.fail("bound_real_player_disconnected");
                else physicalDig.tick(actor, tick);
            } catch (Throwable capture) { physicalDig.fail("packet_path_tick_observer_failed:" + capture.getClass().getSimpleName()); }
            finally { physicalDig.toolTickEnd(); } // callback throw cannot retain a cause into another tick
        }
        if (incomingHit != null) {
            try {
                ServerPlayer actor = server.getPlayerList().getPlayer(incomingHit.binding.actorId);
                if (actor == null) incomingHit.fail("incoming_hit_bound_actor_disconnected");
                else incomingHit.serverEnd(actor, tick);
            } catch (Throwable capture) { incomingHit.fail("incoming_hit_server_end_failed:" + capture.getClass().getSimpleName()); }
        }
        // RETURN injection cannot execute when a wrapped native method throws. Never carry its scope to a later tick.
        if (!DROP_SCOPES.get().isEmpty()) {
            if (pendingDig != null) pendingDig.scopeAborted();
            if (physicalDig != null) physicalDig.scopeAborted();
            DROP_SCOPES.remove();
        }
        if (blockMutationTrace != null && !MUTATION_SCOPES.get().isEmpty()) {
            blockMutationTrace.failedScope("mutation_scope_unbalanced_at_tick_end");
            MUTATION_SCOPES.remove();
        }
        if (blockMutationTrace != null && (!DIRECT_SETS.get().isEmpty() || !CONTAINER_READS.get().isEmpty())) {
            blockMutationTrace.failedScope("private_palette_or_read_scope_unbalanced_at_tick_end");
            DIRECT_SETS.remove(); CONTAINER_READS.remove();
        }
        if (pendingPassiveAwait != null) {
            Protocol.Request current = pendingPassiveAwait;
            boolean item = PassiveItemUse.AWAIT.equals(current.driver());
            boolean terminal = item ? itemObserver.terminal() : onboardingObserver.terminal();
            if (!terminal && tick >= pendingPassiveAwaitDeadline) {
                if (item) itemObserver.fail("await_tick_budget_exceeded");
                else onboardingObserver.fail("await_tick_budget_exceeded");
                terminal = true;
            }
            if (terminal) {
                JsonObject receipt = item ? itemObserver.receipt() : onboardingObserver.receipt();
                boolean qualified = item ? itemObserver.qualifiedNarrow() : onboardingObserver.qualifiedNarrow();
                try {
                    observe(current, qualified ? "completed" : "failed", receipt);
                    pendingPassiveAwait = null;
                    if (item) itemObserver = null; else onboardingObserver = null;
                    nextRequest++;
                } catch (IOException failure) { stopped = true; LOG.error("Passive await terminal could not be persisted", failure); }
            }
            return;
        }
        if (pendingCraftAwait != null) {
            Protocol.Request current = pendingCraftAwait;
            if (!craftObserver.terminal() && tick >= pendingCraftDeadline)
                craftObserver.fail("craft_await_tick_budget_exceeded");
            if (craftObserver.terminal()) {
                try {
                    observe(current, craftObserver.observedNarrow() ? "completed" : "failed", craftObserver.receipt());
                    pendingCraftAwait = null; craftObserver = null; nextRequest++;
                } catch (IOException failure) {
                    stopped = true; LOG.error("Craft diagnostic await terminal could not be persisted", failure);
                }
            }
            return;
        }
        if (pendingWaterAwait != null) {
            Protocol.Request current = pendingWaterAwait;
            if (!waterAutoObserver.terminal() && tick >= pendingWaterDeadline)
                waterAutoObserver.fail("water_auto_await_tick_budget_exceeded");
            if (waterAutoObserver.terminal()) {
                try {
                    ServerPlayer actor = server.getPlayerList().getPlayer(waterAutoObserver.binding.actorId);
                    if (actor == null) {
                        JsonObject missing = new JsonObject(); missing.addProperty("reason", "water_auto_actor_disconnected_during_bound_await");
                        observe(current, "failed", missing);
                    } else {
                        JsonObject receipt = waterAutoObserver.await(current, current.parameters(), actor, tick);
                        observe(current, receipt.get("observation_complete").getAsBoolean() ? "completed" : "failed", receipt);
                    }
                    pendingWaterAwait = null; waterAutoObserver = null; nextRequest++;
                } catch (Exception failure) { stopped = true; LOG.error("Water auto await terminal could not be persisted", failure); }
            }
            return;
        }
        if (pendingIncomingHitAwait != null) {
            Protocol.Request current = pendingIncomingHitAwait;
            if (!incomingHit.terminal() && tick >= pendingIncomingHitDeadline)
                incomingHit.fail("incoming_hit_await_tick_budget_exceeded");
            if (incomingHit.terminal()) {
                try {
                    ServerPlayer actor = server.getPlayerList().getPlayer(incomingHit.binding.actorId);
                    if (actor == null) {
                        JsonObject missing = new JsonObject(); missing.addProperty("reason", "incoming_hit_actor_disconnected");
                        missing.add("incoming_hit_trace_file", incomingHitTraceFile(incomingHit));
                        observe(current, "failed", missing);
                    } else {
                        JsonObject receipt = incomingHit.await(current, current.parameters(), actor, tick);
                        receipt.add("incoming_hit_trace_file", incomingHitTraceFile(incomingHit));
                        observe(current, receipt.get("observation_complete").getAsBoolean() ? "completed" : "failed", receipt);
                    }
                    pendingIncomingHitAwait = null; incomingHit = null; nextRequest++;
                } catch (Exception failure) { stopped = true; LOG.error("Incoming hit terminal cannot persist", failure); }
            }
            return;
        }
        if (pendingPhysicalDigAwait != null) {
            Protocol.Request current = pendingPhysicalDigAwait;
            if (!physicalDig.terminal() && tick >= pendingPhysicalDigDeadline)
                physicalDig.fail("packet_dig_await_tick_budget_exceeded");
            if (physicalDig.terminal()) {
                try {
                    ServerPlayer actor = server.getPlayerList().getPlayer(physicalDig.binding.actorId);
                    if (actor == null) {
                        JsonObject missing = new JsonObject();
                        missing.addProperty("reason", "packet_dig_actor_disconnected_during_bound_await");
                        missing.add("packet_dig_trace_file", packetDigTraceFile(physicalDig));
                        observe(current, "failed", missing);
                    } else {
                        JsonObject receipt = physicalDig.await(current, current.parameters(), actor, tick);
                        receipt.add("packet_dig_trace_file", packetDigTraceFile(physicalDig));
                        observe(current, receipt.get("observation_complete").getAsBoolean() ? "completed" : "failed", receipt);
                    }
                    pendingPhysicalDigAwait = null; physicalDig = null; nextRequest++;
                } catch (Exception failure) { stopped = true; LOG.error("Packet dig await terminal could not be persisted", failure); }
            }
            return;
        }
        if (pendingScale != null) {
            SyntheticScaleCohort cohort = pendingScale;
            boolean completedWriteAttempted = false;
            try {
                if (cohort.wallExpired()) throw new IllegalStateException("synthetic scale 900s wall deadline exceeded");
                cohort.driveSyntheticTransport();
                if (cohort.shouldSample(tick))
                    cohort.sample(tick, serverTickStartNanos > 0 ? System.nanoTime() - serverTickStartNanos : -1);
                if (cohort.done(tick)) {
                    JsonObject result = cohort.receipt();
                    result.add("samples_file", scaleFile(cohort));
                    if (!result.get("conformant").getAsBoolean()) {
                        result.addProperty("reason", "native cohort tick/roster/time conformance failed");
                        finishScaleFailure(cohort, result); return;
                    }
                    completedWriteAttempted = true;
                    observe(cohort.request, "completed", result);
                    pendingScale = null; nextRequest++;
                }
            } catch (Exception failure) {
                if (completedWriteAttempted) {
                    try { cohort.cleanup(); activeScale = null; pendingScale = null; }
                    catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                    stopped = true; LOG.error("Scale completed terminal write failed; not emitting a second terminal", failure);
                } else {
                    JsonObject payload = new JsonObject(); payload.addProperty("reason", safeReason(failure));
                    finishScaleFailure(cohort, payload);
                }
            }
            return;
        }
        if (pendingDig != null) {
            NativeDig dig = pendingDig;
            ServerPlayer actor = server.getPlayerList().getPlayer(dig.playerId);
            try {
                if (actor == null || actor instanceof FakePlayer || actor.connection == null
                        || !actor.serverLevel().dimension().location().toString().equals(dig.dimension))
                    throw new IllegalStateException("native dig actor disconnected or changed dimension");
                if (!dig.stopSent && tick >= dig.stopAt) {
                    dig.sendStop(actor);
                    dig.stopSent = true; dig.stopTick = tick;
                }
                if (dig.stopSent && tick >= dig.stopTick + 10) {
                    if (dig.trace != null) try { dig.trace.terminalCheck(actor, tick); }
                    catch (Throwable capture) { dig.trace.unsupported("terminal_observer_error:" + capture.getClass().getSimpleName()); }
                    JsonObject receipt = dig.receipt(actor, tick);
                    if (dig.trace != null) receipt.add("acquisition_trace_file", acquisitionTraceFile(dig));
                    observe(dig.request, "completed", receipt);
                    pendingDig = null; DROP_SCOPES.remove(); nextRequest++;
                } else if (tick >= dig.deadline) throw new IllegalStateException("native dig tick deadline exceeded");
            } catch (Exception failure) {
                if (actor != null && !dig.stopSent) try {
                    actor.gameMode.handleBlockBreakAction(dig.pos,
                            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                            dig.face, actor.serverLevel().getMaxBuildHeight(), Math.toIntExact(dig.request.sequence()));
                } catch (Exception abortFailure) { failure.addSuppressed(abortFailure); }
                JsonObject payload = new JsonObject(); payload.addProperty("reason", safeReason(failure));
                if (dig.trace != null && actor != null &&
                        actor.serverLevel().dimension().location().toString().equals(dig.dimension))
                    try { dig.trace.terminalCheck(actor, tick); }
                    catch (Throwable capture) { dig.trace.unsupported("terminal_observer_error:" + capture.getClass().getSimpleName()); }
                if (dig.trace != null && dig.traceFilePointer == null) {
                    try { acquisitionTraceFile(dig); }
                    catch (Exception retention) { payload.addProperty("trace_retention_error", safeReason(retention)); }
                }
                if (actor != null && actor.serverLevel().dimension().location().toString().equals(dig.dimension)) {
                    JsonObject partial = dig.receipt(actor, tick);
                    if (dig.traceFilePointer != null) partial.add("acquisition_trace_file", dig.traceFilePointer.deepCopy());
                    payload.add("partial_observation", partial);
                }
                try { observe(dig.request, "failed", payload); pendingDig = null; DROP_SCOPES.remove(); nextRequest++; }
                catch (IOException writeFailure) { stopped = true; LOG.error("Native dig terminal cannot be persisted", writeFailure); }
            }
            return;
        }
        if (pendingUse != null) {
            Protocol.Request current = pendingUse;
            ServerPlayer player = server.getPlayerList().getPlayer(pendingUsePlayer);
            try {
                if (player == null || player instanceof FakePlayer || player.connection == null)
                    throw new IllegalStateException("item user disconnected");
                if (pendingUseFinished) {
                    ItemStack after = player.getItemInHand(pendingUseHand);
                    pendingUseBaseline.addProperty("item_after", item(after));
                    pendingUseBaseline.addProperty("count_after", after.getCount());
                    pendingUseBaseline.addProperty("food_after", player.getFoodData().getFoodLevel());
                    pendingUseBaseline.addProperty("consumption_completed", true);
                    observe(current, "completed", pendingUseBaseline);
                    pendingUse = null; pendingUseBaseline = null; nextRequest++;
                } else if (tick >= waitDeadline || !player.isUsingItem()) {
                    JsonObject failure = new JsonObject();
                    failure.addProperty("reason", tick >= waitDeadline ? "item finish deadline exceeded" : "item use canceled before native finish");
                    failure.add("start_observation", pendingUseBaseline);
                    observe(current, "failed", failure);
                    pendingUse = null; pendingUseBaseline = null; nextRequest++;
                }
            } catch (Exception failure) { stopped = true; LOG.error("Cannot persist item use terminal", failure); }
            return;
        }
        if (waiting != null) {
            if (tick >= waitEnd || tick >= waitDeadline) {
                Protocol.Request current = waiting; waiting = null;
                try { JsonObject payload = new JsonObject(); payload.addProperty("ticks_observed", tick - (waitEnd - Protocol.number(current.parameters(), "ticks")));
                    if (tick >= waitDeadline && tick < waitEnd) { payload.addProperty("reason", "tick deadline exceeded"); observe(current, "failed", payload); }
                    else observe(current, "completed", payload);
                    nextRequest++;
                } catch (Exception failure) { stopped = true; LOG.error("Cannot persist wait terminal", failure); }
            }
            return;
        }
        Path requestPath = directory.resolve("requests").resolve(String.format(Locale.ROOT, "%012d.json", nextRequest));
        if (!Files.exists(requestPath, LinkOption.NOFOLLOW_LINKS)) return;
        Protocol.Request request = null;
        try {
            if (!Files.isRegularFile(requestPath, LinkOption.NOFOLLOW_LINKS) || Files.size(requestPath) > MAX_REQUEST)
                throw new IOException("oversized/non-regular request");
            byte[] bytes = Files.readAllBytes(requestPath);
            if (bytes.length > MAX_REQUEST) throw new IOException("oversized request");
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) throw new IOException("nonobject request");
            request = Protocol.parse(element.getAsJsonObject(), run, token, nextRequest);
            observe(request, "started", new JsonObject());
            long deadline = Math.addExact(tick, request.timeout());
            if (tick >= deadline) throw new IOException("tick deadline reached");
            JsonObject result = dispatch(request);
            if (waiting != null || pendingUse != null || pendingDig != null || pendingScale != null ||
                    pendingPassiveAwait != null || pendingWaterAwait != null ||
                    pendingPhysicalDigAwait != null || pendingIncomingHitAwait != null) return;
            if (tick >= deadline) throw new IOException("tick deadline reached");
            observe(request, "completed", result);
            nextRequest++;
        } catch (Exception failure) {
            if (request != null) {
                try { JsonObject body = new JsonObject(); body.addProperty("reason", safeReason(failure));
                    observe(request, "failed", body); nextRequest++;
                } catch (Exception writeFailure) { LOG.error("Cannot persist terminal observation", writeFailure); stopped = true; }
            } else {
                // Invalid envelope cannot be attributed to a valid case: halt rather than forge a terminal receipt.
                stopped = true;
            }
            LOG.error("Playtest request failed closed at sequence {}", nextRequest, failure);
        }
    }
    private JsonObject dispatch(Protocol.Request request) throws Exception {
        if ((itemObserver != null || onboardingObserver != null) &&
                !PassiveItemUse.AWAIT.equals(request.driver()) && !PassiveOnboarding.AWAIT.equals(request.driver()))
            throw new IllegalStateException("armed passive observer permits only its corresponding await on native mailbox");
        if (craftObserver != null && !PassiveCraftEvent.AWAIT.equals(request.driver()))
            throw new IllegalStateException("armed craft observer permits only its corresponding await on native mailbox");
        if (waterAutoObserver != null && !PassiveWaterAuto.AWAIT.equals(request.driver()) &&
                !request.driver().equals("wait.ticks"))
            throw new IllegalStateException("armed water observer permits only bounded wait or corresponding await");
        if (physicalDig != null && !PassivePhysicalDig.AWAIT.equals(request.driver()) &&
                !request.driver().equals("wait.ticks"))
            throw new IllegalStateException("armed real packet dig permits only bounded wait or corresponding await");
        if (incomingHit != null && !PassiveIncomingHit.AWAIT.equals(request.driver()))
            throw new IllegalStateException("armed incoming hit permits only matching bounded pending await");
        // Natural interactions can mutate world/player state without fixture.begin. Never
        // let absence of an owned fixture falsely prove a clean cross-case baseline.
        if (Protocol.mayMutate(request.driver())) fixtureEverMutated = true;
        JsonObject p = request.parameters();
        if (mutationOwnership != null) mutationOwnership.audit();
        mutationPreActionPrefix = blockMutationTrace == null ? null : blockMutationTrace.preActionPrefix();
        birthPreActionPrefix = blockBirthTrace == null ? null : blockBirthTrace.beforeAction();
        mutationPrefixRequest = request.sequence();
        if (fixture != null) {
            fixture.requireOwner(request);
            if (p.has("player_uuid") && !fixture.playerId.toString().equals(Protocol.string(p, "player_uuid")))
                throw new IllegalArgumentException("fixture case cannot target another player");
        }
        return switch (request.driver()) {
            case "inventory.discover" -> { Protocol.only(p); yield discover(request.sequence()); }
            case "server.snapshot" -> { Protocol.only(p); yield serverSnapshot(); }
            case "world.sample" -> WorldSampling.sample(server, p);
            case "world.entry" -> { Protocol.only(p, "player_uuid"); yield worldEntry(realPlayer(p)); }
            case "player.snapshot" -> { Protocol.only(p, "player_uuid"); yield playerSnapshot(realPlayer(p)); }
            case "player.lifecycle_snapshot" -> { Protocol.only(p, "player_uuid"); yield lifecycleSnapshot(realPlayer(p)); }
            case "entity.snapshot" -> { yield EntitySnapshot.read(server, p, entityDeaths.get(UUID.fromString(Protocol.string(p, "entity_uuid")))); }
            case "block_mutation.control_positive_v1" -> Stage1RealMutationControls.positive(request, p,
                    realPlayer(p), blockMutationTrace, mutationOwnership, mutationPreActionPrefix,
                    fixture == null && blockBirthTrace == null && activeScale == null && pendingScale == null &&
                    pendingUse == null && pendingDig == null && itemObserver == null && onboardingObserver == null &&
                    waterAutoObserver == null && physicalDig == null && incomingHit == null);
            case "block_mutation.control_expose_sections_v1" -> Stage1RealMutationControls.exposeSections(request, p,
                    realPlayer(p), blockMutationTrace, mutationOwnership, mutationPreActionPrefix,
                    fixture == null && blockBirthTrace == null && activeScale == null && pendingScale == null &&
                    pendingUse == null && pendingDig == null && itemObserver == null && onboardingObserver == null &&
                    waterAutoObserver == null && physicalDig == null && incomingHit == null);
            case "block_mutation.control_io_append_failure_v1" -> Stage1RealMutationControls.ioAppendFailure(request, p,
                    realPlayer(p), blockMutationTrace, mutationOwnership, mutationPreActionPrefix,
                    fixture == null && blockBirthTrace == null && activeScale == null && pendingScale == null &&
                    pendingUse == null && pendingDig == null && itemObserver == null && onboardingObserver == null &&
                    waterAutoObserver == null && physicalDig == null && incomingHit == null);
            case "block_mutation.control_expose_states_v1" -> {
                Protocol.only(p, "scope_id", "selector_id", "dimension", "x", "y", "z");
                String selectorId = Protocol.string(p, "selector_id");
                if (!selectorId.matches("[A-Za-z0-9._:-]{1,128}"))
                    throw new IllegalArgumentException("reviewed negative selector malformed");
                if (fixture != null || blockMutationTrace == null || mutationOwnership == null ||
                        !"diagnostic".equals(blockMutationTrace.scope.purpose) || blockBirthTrace != null)
                    throw new IllegalStateException("external mutable getter negative requires isolated diagnostic stage1 scope");
                if (!blockMutationTrace.scope.scopeId.equals(Protocol.string(p, "scope_id")) ||
                        mutationPreActionPrefix == null || mutationPreActionPrefix.get("scope_failure").getAsBoolean() ||
                        mutationPreActionPrefix.get("overflow").getAsBoolean() ||
                        mutationPreActionPrefix.get("io_failure").getAsBoolean())
                    throw new IllegalStateException("negative control scope not pristine at action boundary");
                long x = Protocol.number(p, "x"), y = Protocol.number(p, "y"), z = Protocol.number(p, "z");
                if (x < -30_000_000 || x > 30_000_000 || y < -2048 || y > 2048 ||
                        z < -30_000_000 || z > 30_000_000)
                    throw new IllegalArgumentException("negative control position outside bounded scope");
                BlockMutationScope.Position selected = new BlockMutationScope.Position(
                        Protocol.string(p, "dimension"), (int) x, (int) y, (int) z);
                if (!blockMutationTrace.scope.positionSet.contains(selected))
                    throw new IllegalArgumentException("negative control position not in immutable reviewed diagnostic scope");
                ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(selected.dimension())));
                if (level == null) throw new IllegalStateException("negative control level not loaded");
                var chunk = level.getChunkSource().getChunkNow(selected.x() >> 4, selected.z() >> 4);
                if (chunk == null || !mutationOwnership.watchedChunk(chunk))
                    throw new IllegalStateException("negative control actual full chunk not watched and loaded");
                int index = chunk.getSectionIndex(selected.y());
                if (index < 0 || index >= chunk.getSectionsCount())
                    throw new IllegalStateException("negative control watched section outside runtime height");
                var section = chunk.getSection(index);
                if (!mutationOwnership.watchedSection(section))
                    throw new IllegalStateException("negative control section identity not watched");
                // Mark the ONE expected actual diagnostic exposure only after the precise
                // watched owner was checked; neither this arm nor the observer writes anything.
                blockMutationTrace.armExpectedDiagnosticExposure("mutable_state_container_exposed");
                // Intentionally exercise the real external mutable getter ONCE. No writes, no retained alias.
                section.getStates();
                JsonObject outcome = blockMutationTrace.preActionPrefix();
                if (!outcome.get("scope_failure").getAsBoolean() ||
                        !"mutable_state_container_exposed".equals(Protocol.string(outcome, "failure_reason")))
                    throw new IllegalStateException("negative control did not trigger exact expected real-path failure");
                JsonObject receipt = new JsonObject();
                receipt.addProperty("schema", "bc.playtest_mutation_external_getter_negative.v1");
                receipt.addProperty("scope_id", blockMutationTrace.scope.scopeId);
                receipt.addProperty("selector_id", selectorId);
                receipt.addProperty("selector_reviewed_by_native", false);
                receipt.addProperty("epoch_id", blockMutationTrace.scope.epochId);
                receipt.addProperty("native_session_id", run);
                receipt.addProperty("source_checkpoint_sha256", blockMutationTrace.scope.sourceCheckpointSha256);
                receipt.addProperty("dimension", selected.dimension());
                receipt.addProperty("x", selected.x()); receipt.addProperty("y", selected.y()); receipt.addProperty("z", selected.z());
                receipt.addProperty("real_callsite", "LevelChunkSection.getStates() from native diagnostic action");
                receipt.addProperty("expected_scope_failure", "mutable_state_container_exposed");
                receipt.add("resulting_prefix", outcome);
                receipt.addProperty("natural_origin_proven_by_native", false);
                receipt.addProperty("negative_control_certifies_candidate_provider_by_native", false);
                yield receipt;
            }
            case "target.observe.loaded_v1" -> {
                if (fixture != null) throw new IllegalStateException("natural-target diagnostic cannot run inside authored fixture");
                yield loadedTargetFile(request, LoadedTargetObservation.capture(request, realPlayer(p), p, tick));
            }
            case "player.water_snapshot" -> { Protocol.only(p, "player_uuid", "hand"); yield WaterSnapshot.read(realPlayer(p), Protocol.string(p, "hand")); }
            case "player.incoming_damage.arm_v2" -> {
                if (incomingHit != null || physicalDig != null || waterAutoObserver != null ||
                        itemObserver != null || craftObserver != null || onboardingObserver != null || fixture != null || pendingDig != null)
                    throw new IllegalStateException("incoming hit observer needs no fixture or other observer");
                incomingHit = PassiveIncomingHit.arm(request, p, realPlayer(p), tick);
                JsonObject arm = incomingHit.binding.echo(); arm.addProperty("schema", "bc.playtest_passive_incoming_hit_arm.v2");
                arm.add("actual_loaded_source_and_actor", incomingHit.armState.deepCopy());
                arm.addProperty("natural_threat_proven_by_native", false);
                yield arm;
            }
            case "player.incoming_damage.await_v2" -> {
                if (incomingHit == null || pendingIncomingHitAwait != null)
                    throw new IllegalStateException("no unawaited incoming hit arm");
                ServerPlayer actor = realPlayer(p);
                incomingHit.binding.await(request, p, actor.getUUID(), actor.serverLevel().dimension().location().toString());
                pendingIncomingHitAwait = request;
                pendingIncomingHitDeadline = Math.min(Math.addExact(tick, request.timeout()), incomingHit.binding.deadlineTick);
                yield new JsonObject();
            }
            case "player.observe_dig.arm_v5" -> {
                if (physicalDig != null || waterAutoObserver != null || itemObserver != null ||
                        craftObserver != null || onboardingObserver != null || fixture != null || pendingDig != null)
                    throw new IllegalStateException("packet dig observation requires no other observer/fixture/dig");
                physicalDig = PassivePhysicalDig.arm(request, p, realPlayer(p), tick);
                JsonObject receipt = physicalDig.binding.echo();
                receipt.addProperty("schema", "bc.playtest_passive_packet_dig_arm.v5");
                receipt.addProperty("expected_packet_variant", physicalDig.packetVariant);
                receipt.addProperty("expected_drop_outcome", physicalDig.dropOutcome);
                receipt.addProperty("expected_harvest_result", physicalDig.expectedHarvest);
                receipt.add("arm_block", physicalDig.armBlock.deepCopy());
                receipt.addProperty("vanilla_tool_hotbar_slot", physicalDig.tool.selectedSlot);
                receipt.add("arm_tool", physicalDig.tool.arm.deepCopy());
                receipt.addProperty("selector_id", physicalDig.selector);
                receipt.addProperty("bound_target_sha256", physicalDig.boundTargetSha);
                receipt.addProperty("crosshair_projection_sha256", physicalDig.crosshairSha);
                receipt.addProperty("natural_origin_proven_by_native", false);
                yield receipt;
            }
            case "player.observe_dig.await_v5" -> {
                if (physicalDig == null || pendingPhysicalDigAwait != null)
                    throw new IllegalStateException("no unawaited passive real packet dig");
                ServerPlayer actor = realPlayer(p);
                physicalDig.binding.await(request, p, actor.getUUID(), actor.serverLevel().dimension().location().toString());
                pendingPhysicalDigAwait = request;
                pendingPhysicalDigDeadline = Math.min(Math.addExact(tick, request.timeout()), physicalDig.binding.deadlineTick);
                yield new JsonObject();
            }
            case "player.water_auto_snapshot_v1" -> {
                Protocol.only(p, "player_uuid", "dimension", "provider_artifact_sha256");
                yield WaterAutoSnapshot.read(realPlayer(p), Protocol.string(p, "dimension"),
                        Protocol.string(p, "provider_artifact_sha256"));
            }
            case "player.water_auto_observe.arm_v1" -> {
                if (waterAutoObserver != null || itemObserver != null || craftObserver != null ||
                        onboardingObserver != null || physicalDig != null || fixture != null)
                    throw new IllegalStateException("water observation requires no fixture or other observer");
                waterAutoObserver = PassiveWaterAuto.arm(request, p, realPlayer(p), tick);
                JsonObject row = waterAutoObserver.binding.echo();
                row.addProperty("schema", "bc.playtest_passive_water_auto_arm.v1");
                row.addProperty("provider_artifact_sha256", waterAutoObserver.providerSha);
                row.add("arm_snapshot", waterAutoObserver.armSnapshot.deepCopy());
                row.addProperty("armed_before_provider_tick", true);
                row.addProperty("natural_hydration_proven_by_native", false);
                yield row;
            }
            case "player.water_auto_observe.await_v1" -> {
                if (waterAutoObserver == null || pendingWaterAwait != null)
                    throw new IllegalStateException("no unawaited bound water auto arm");
                ServerPlayer actor = realPlayer(p);
                waterAutoObserver.binding.await(request, p, actor.getUUID(), actor.serverLevel().dimension().location().toString());
                pendingWaterAwait = request;
                pendingWaterDeadline = Math.min(Math.addExact(tick, request.timeout()), waterAutoObserver.binding.deadlineTick);
                yield new JsonObject();
            }
            case "player.select_item" -> { nativeActionEverMutated = true; yield InventorySelection.select(realPlayer(p), p); }
            case "player.attack" -> { nativeActionEverMutated = true; yield NativeAttack.attack(realPlayer(p), p); }
            case "player.interact" -> { Protocol.only(p, "player_uuid", "hand", "target");
                nativeActionEverMutated = true; yield interact(realPlayer(p), p); }
            case "player.use_item" -> { Protocol.only(p, "player_uuid", "hand");
                nativeActionEverMutated = true; yield useItem(request, realPlayer(p), p); }
            case "player.break_block" -> { Protocol.only(p, "player_uuid", "dimension", "x", "y", "z");
                nativeActionEverMutated = true; yield breakBlock(realPlayer(p), p); }
            case "player.observe_craft.arm_v1" -> {
                if (craftObserver != null || itemObserver != null || onboardingObserver != null ||
                        waterAutoObserver != null || physicalDig != null || incomingHit != null ||
                        pendingDig != null || fixture != null)
                    throw new IllegalStateException("craft diagnostic requires an unmodified observer-free origin");
                craftObserver = PassiveCraftEvent.arm(request, realPlayer(p), p, tick);
                JsonObject arm = craftObserver.binding.echo(); arm.addProperty("schema", "bc.playtest_passive_craft_arm.v1");
                arm.addProperty("armed_before_input", true);
                arm.addProperty("source_ancestry_proven_by_native", false);
                yield arm;
            }
            case "player.observe_craft.await_v1" -> {
                if (craftObserver == null || pendingCraftAwait != null)
                    throw new IllegalStateException("no unawaited bound craft diagnostic arm");
                Protocol.only(p, "observer_id", "arm_request_sequence", "arm_action_ordinal",
                        "player_uuid", "dimension", "witness_context");
                ServerPlayer actor = server.getPlayerList().getPlayer(craftObserver.binding.actorId);
                craftObserver.binding.await(request, p,
                    actor == null ? craftObserver.binding.actorId : actor.getUUID(),
                    actor == null ? craftObserver.binding.dimension : actor.serverLevel().dimension().location().toString());
                if (actor == null) craftObserver.fail("craft_actor_disconnected_before_await");
                pendingCraftAwait = request;
                pendingCraftDeadline = Math.min(Math.addExact(tick, request.timeout()), craftObserver.binding.deadlineTick);
                yield new JsonObject();
            }
            case "player.observe_item_use.arm" -> {
                if (itemObserver != null || craftObserver != null || onboardingObserver != null || waterAutoObserver != null || physicalDig != null)
                    throw new IllegalStateException("passive observer already active");
                itemObserver = PassiveItemUse.arm(request, realPlayer(p), p, tick, fixture);
                JsonObject arm = itemObserver.binding.echo(); arm.addProperty("armed_before_input", true);
                arm.addProperty("schema", "bc.playtest_passive_item_use_arm.v1");
                arm.addProperty("acquisition_origin", itemObserver.fixtureId == null ?
                        "preexisting_unattributed" : "authored_fixture_not_natural");
                arm.addProperty("natural_acquisition_proven_by_native", false);
                if (itemObserver.fixtureId != null) arm.addProperty("fixture_id", itemObserver.fixtureId);
                yield arm;
            }
            case "player.observe_onboarding.arm" -> {
                if (fixture != null || onboardingObserver != null || itemObserver != null || craftObserver != null ||
                        waterAutoObserver != null || physicalDig != null)
                    throw new IllegalStateException("passive observer requires no fixture or pre-existing observer");
                onboardingObserver = PassiveOnboarding.arm(request, realPlayer(p), p, tick);
                JsonObject arm = onboardingObserver.binding.echo(); arm.addProperty("armed_before_input", true);
                arm.addProperty("schema", "bc.playtest_passive_onboarding_arm.v1");
                arm.add("request_bounds", onboardingObserver.requestBounds.deepCopy());
                arm.add("actor_at_arm", onboardingObserver.actorAtArm.deepCopy());
                yield arm;
            }
            case "player.observe_item_use.await", "player.observe_onboarding.await" -> {
                boolean item = PassiveItemUse.AWAIT.equals(request.driver());
                PassiveObserverBinding binding = item ?
                        (itemObserver == null ? null : itemObserver.binding) :
                        (onboardingObserver == null ? null : onboardingObserver.binding);
                if (binding == null) throw new IllegalArgumentException("no corresponding armed passive observer");
                Protocol.only(p, "observer_id", "arm_request_sequence", "arm_action_ordinal",
                        "player_uuid", "dimension", "witness_context");
                ServerPlayer actor = server.getPlayerList().getPlayer(binding.actorId);
                binding.await(request, p, actor == null ? binding.actorId : actor.getUUID(),
                        actor == null ? binding.dimension : actor.serverLevel().dimension().location().toString());
                if (actor == null) {
                    if (item) itemObserver.fail("item_actor_disconnected_before_await");
                    else onboardingObserver.fail("onboarding_actor_disconnected_before_await");
                }
                pendingPassiveAwait = request;
                pendingPassiveAwaitDeadline = Math.min(Math.addExact(tick, request.timeout()), binding.deadlineTick);
                yield new JsonObject();
            }
            case "player.dig", "player.dig.witness_v1" -> { nativeActionEverMutated = true;
                if (NativeWitnessContext.DRIVER.equals(request.driver()) && fixture != null)
                    throw new IllegalStateException("acquisition witness cannot run inside authored fixture");
                ServerPlayer actor = realPlayer(p);
                NativeDig dig = NativeDig.start(request, actor, p, tick);
                pendingDig = dig;
                try { dig.sendStart(actor); }
                catch (Exception error) {
                    try { dig.abortAfterFailedStart(actor); }
                    catch (Exception abortFailure) { error.addSuppressed(abortFailure); }
                    pendingDig = null; DROP_SCOPES.remove(); throw error;
                }
                yield new JsonObject(); }
            case "block.snapshot" -> { Protocol.only(p, "dimension", "x", "y", "z"); yield blockSnapshot(p); }
            case "wait.ticks" -> { Protocol.only(p, "ticks"); long duration = Protocol.number(p, "ticks");
                if (duration < 1 || duration > request.timeout()) throw new IllegalArgumentException("wait exceeds deadline");
                waiting = request; waitEnd = Math.addExact(tick, duration); waitDeadline = Math.addExact(tick, request.timeout());
                yield new JsonObject(); }
            case "fixture.begin" -> { Protocol.only(p, "fixture_id", "player_uuid", "item_id", "count", "hand", "food_level", "saturation");
                yield beginFixture(request, p, false); }
            case "fixture.begin_water" -> { Protocol.only(p, "fixture_id", "player_uuid", "item_id", "count", "hand", "food_level", "saturation", "thirst_level", "quenched_level", "preserve_held");
                yield beginFixture(request, p, true); }
            case "fixture.restore" -> { Protocol.only(p, "fixture_id"); yield restoreFixture(request, p); }
            case "fixture.cleanup" -> { Protocol.only(p); yield fixture == null ? emptyCleanup() : restoreFixture(request, null); }
            case "scale.begin" -> { if (activeScale != null || fixture != null)
                    throw new IllegalStateException("scale cohort requires no active cohort or owned fixture");
                activeScale = SyntheticScaleCohort.begin(server, request, p, tick);
                pendingScale = activeScale; yield new JsonObject(); }
            case "scale.cleanup" -> { Protocol.only(p);
                if (activeScale == null || pendingScale != null) throw new IllegalStateException("scale cohort missing or still measuring");
                if (!activeScale.request.run().equals(request.run()) || !activeScale.request.caseId().equals(request.caseId())
                        || !activeScale.request.token().equals(request.token()))
                    throw new IllegalStateException("scale cleanup belongs to another case/token");
                JsonObject result = activeScale.cleanup(); activeScale = null; yield result; }
            default -> throw new IllegalArgumentException("unsupported driver: " + request.driver());
        };
    }
    private ServerPlayer realPlayer(JsonObject p) {
        String id = Protocol.string(p, "player_uuid");
        UUID uuid = UUID.fromString(id);
        if (!uuid.toString().equals(id)) throw new IllegalArgumentException("noncanonical player_uuid");
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null || player.isRemoved() || player.connection == null || player instanceof FakePlayer
                || (activeScale != null && activeScale.contains(player))
                || server.getPlayerList().getPlayer(uuid) != player)
            throw new IllegalArgumentException("registered real player required");
        return player;
    }
    private JsonObject serverSnapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("dimension", server.overworld().dimension().location().toString());
        result.addProperty("game_time", server.overworld().getGameTime());
        result.addProperty("server_tick", server.getTickCount());
        result.addProperty("seed", server.overworld().getSeed());
        result.addProperty("seed_source", "server.overworld().getSeed()");
        JsonArray players = new JsonArray();
        server.getPlayerList().getPlayers().stream().sorted(Comparator.comparing(p -> p.getUUID().toString()))
                .forEach(p -> players.add(playerCore(p)));
        result.add("players", players);
        result.add("supported_drivers", strings(Protocol.DRIVERS.stream().sorted().toList()));
        return result;
    }
    private JsonObject playerCore(ServerPlayer player) {
        JsonObject row = new JsonObject();
        row.addProperty("uuid", player.getUUID().toString()); row.addProperty("name", player.getGameProfile().getName());
        row.addProperty("dimension", player.level().dimension().location().toString());
        row.addProperty("x", player.getX()); row.addProperty("y", player.getY()); row.addProperty("z", player.getZ());
        GameType mode = player.gameMode.getGameModeForPlayer();
        row.addProperty("mode", mode.getName()); row.addProperty("health", player.getHealth());
        row.addProperty("synthetic_scale_participant", activeScale != null && activeScale.contains(player));
        return row;
    }
    private JsonObject worldEntry(ServerPlayer player) {
        Login login = observedLogins.get(player.getUUID());
        if (login == null) throw new IllegalStateException("native real-player login not observed by this process");
        JsonObject row = playerCore(player);
        row.addProperty("login_observed", true);
        row.addProperty("login_tick", login.tick()); row.addProperty("login_dimension", login.dimension());
        row.addProperty("login_x", login.x()); row.addProperty("login_y", login.y()); row.addProperty("login_z", login.z());
        row.addProperty("playerdata_preexists_at_login", login.playerDataPreexists());
        row.addProperty("login_biome", login.biome()); row.addProperty("login_ground_block", login.ground());
        row.addProperty("login_feet_air", login.feetAir()); row.addProperty("login_head_air", login.headAir());
        row.addProperty("login_ground_solid", login.groundSolid());
        row.addProperty("current_tick", tick);
        row.addProperty("first_join_status", login.playerDataPreexists() ? "prior_playerdata_seen" : "no_prior_playerdata_file_observed");
        row.addProperty("spawn_x", player.serverLevel().getSharedSpawnPos().getX());
        row.addProperty("spawn_y", player.serverLevel().getSharedSpawnPos().getY());
        row.addProperty("spawn_z", player.serverLevel().getSharedSpawnPos().getZ());
        row.addProperty("current_chunk_loaded", player.serverLevel().getChunkSource().hasChunk(player.chunkPosition().x, player.chunkPosition().z));
        row.addProperty("native_login_source", "PlayerEvent.PlayerLoggedInEvent");
        return row;
    }
    private JsonObject lifecycleSnapshot(ServerPlayer player) {
        Lifecycle state = lifecycles.get(player.getUUID());
        JsonObject row = playerCore(player);
        row.addProperty("death_observed", state != null && state.deathTick() >= 0);
        row.addProperty("death_tick", state == null ? -1 : state.deathTick());
        row.addProperty("death_cause", state == null ? "unobserved" : state.cause());
        row.addProperty("death_canceled", state != null && state.canceled());
        row.addProperty("clone_observed", state != null && state.cloneTick() >= 0);
        row.addProperty("clone_tick", state == null ? -1 : state.cloneTick());
        row.addProperty("clone_was_death", state != null && state.wasDeath());
        row.addProperty("respawn_observed", state != null && state.respawnTick() >= 0);
        row.addProperty("respawn_tick", state == null ? -1 : state.respawnTick());
        row.addProperty("native_event_source", "LivingDeathEvent/PlayerEvent.Clone/PlayerRespawnEvent");
        return row;
    }
    private JsonObject playerSnapshot(ServerPlayer player) {
        MovementSnapshotContract.requireFinite(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.fallDistance);
        JsonObject row = playerCore(player);
        row.addProperty("yaw", player.getYRot()); row.addProperty("pitch", player.getXRot());
        row.addProperty("on_ground", player.onGround()); row.addProperty("swimming", player.isSwimming());
        row.addProperty("passenger", player.isPassenger()); row.addProperty("sprinting", player.isSprinting());
        row.addProperty("fall_distance", player.fallDistance);
        row.addProperty("server_tick", server.getTickCount());
        row.addProperty("server_monotonic_nanos", System.nanoTime());
        row.addProperty("food_level", player.getFoodData().getFoodLevel());
        ItemStack heldStack = player.getMainHandItem();
        InventorySelection.Identity heldIdentity = InventorySelection.identity(heldStack);
        row.addProperty("held_item", item(heldStack));
        row.addProperty("held_damage", heldStack.getDamageValue());
        row.addProperty("held_count", heldStack.getCount());
        row.addProperty("held_stack_sha256", heldIdentity.snbtSha256());
        row.addProperty("held_stack_canonical_sha256", heldIdentity.canonicalSha256());
        row.addProperty("held_stack_canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
        row.addProperty("selected_slot", player.getInventory().selected);
        row.addProperty("food_exhaustion", player.getFoodData().getExhaustionLevel());
        row.addProperty("saturation", player.getFoodData().getSaturationLevel());
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            JsonObject entry = new JsonObject(); entry.addProperty("slot", slot);
            entry.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
            entry.addProperty("count", stack.getCount());
            InventorySelection.Identity identity = stack == heldStack ? heldIdentity : InventorySelection.identity(stack);
            entry.addProperty("stack_sha256", identity.snbtSha256());
            entry.addProperty("stack_canonical_sha256", identity.canonicalSha256());
            entry.addProperty("stack_canonical_algorithm", CanonicalStackIdentity.ALGORITHM);
            inventory.add(entry);
        }
        row.add("inventory", inventory);
        row.add("inventory_slots_v1", VanillaInventorySlots.capture(player));
        JsonArray effects = new JsonArray();
        player.getActiveEffects().forEach(effect -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()).toString());
            entry.addProperty("duration", effect.getDuration()); entry.addProperty("amplifier", effect.getAmplifier());
            effects.add(entry);
        });
        row.add("effects", effects);
        return row;
    }
    private JsonObject beginFixture(Protocol.Request request, JsonObject p, boolean provisionThirst) throws Exception {
        if (fixture != null) throw new IllegalStateException("overlapping fixture refused");
        ServerPlayer player = realPlayer(p);
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || player.isUsingItem()
                || player.containerMenu != player.inventoryMenu)
            throw new IllegalArgumentException("idle Survival player with closed container required");
        String fixtureId = Protocol.string(p, "fixture_id");
        if (!fixtureId.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid fixture_id");
        String handValue = Protocol.string(p, "hand");
        InteractionHand hand = hand(p);
        String itemId = Protocol.string(p, "item_id");
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) throw new IllegalArgumentException("unknown item_id");
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null || "minecraft:air".equals(itemId)) throw new IllegalArgumentException("air item not provisionable");
        long count = Protocol.number(p, "count"), foodLevel = Protocol.number(p, "food_level");
        if (count < 1 || count > Math.min(64, item.getDefaultInstance().getMaxStackSize()) || foodLevel < 0 || foodLevel > 20)
            throw new IllegalArgumentException("invalid count/food_level");
        JsonElement saturationValue = p.get("saturation");
        if (saturationValue == null || !saturationValue.isJsonPrimitive() || !saturationValue.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("saturation must be numeric");
        float saturation = saturationValue.getAsFloat();
        if (!Float.isFinite(saturation) || saturation < 0 || saturation > foodLevel)
            throw new IllegalArgumentException("invalid saturation");
        int thirstLevel = -1, quenchedLevel = -1;
        boolean preserveHeld = provisionThirst;
        if (preserveHeld) {
            JsonElement preserve = p.get("preserve_held");
            if (preserve == null || !preserve.isJsonPrimitive() || !preserve.getAsJsonPrimitive().isBoolean()
                    || !preserve.getAsBoolean()) throw new IllegalArgumentException("water fixture must preserve preexisting held source");
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty() || !itemId.equals(item(held)) || held.getCount() != count)
                throw new IllegalArgumentException("water fixture requires already held exact source item/count");
        }
        if (provisionThirst) {
            thirstLevel = Math.toIntExact(Protocol.number(p, "thirst_level"));
            quenchedLevel = Math.toIntExact(Protocol.number(p, "quenched_level"));
            if (thirstLevel < 0 || thirstLevel > 20 || quenchedLevel < 0 || quenchedLevel > thirstLevel)
                throw new IllegalArgumentException("invalid Thirst fixture target");
        }
        OwnedFixture owned = new OwnedFixture(request, fixtureId, player, handValue, itemId, (int) count,
                thirstLevel, quenchedLevel, preserveHeld);
        fixture = owned; // Retain the exact original before any mutation, including on a later failure.
        fixtureEverMutated = true;
        try {
            if (!preserveHeld) player.setItemInHand(hand, new ItemStack(item, (int) count));
            player.getFoodData().setFoodLevel((int) foodLevel);
            player.getFoodData().setSaturation(saturation);
            if (provisionThirst) ThirstFixture.provision(player, thirstLevel, quenchedLevel);
            player.inventoryMenu.broadcastChanges();
            return owned.beginReceipt(player);
        } catch (Exception failure) {
            try {
                if (owned.restore(player).get("cleanup_complete").getAsBoolean()) fixture = null;
                else failure.addSuppressed(new IllegalStateException("fixture begin rollback left owned residuals"));
            } catch (Exception restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw failure;
        }
    }
    private JsonObject restoreFixture(Protocol.Request request, JsonObject p) throws ReflectiveOperationException {
        if (fixture == null) throw new IllegalStateException("no owned fixture to restore");
        fixture.requireOwner(request);
        if (p != null && !fixture.fixtureId.equals(Protocol.string(p, "fixture_id")))
            throw new IllegalArgumentException("fixture_id mismatch");
        ServerPlayer player = server.getPlayerList().getPlayer(fixture.playerId);
        if (player == null || player instanceof FakePlayer || player.connection == null)
            throw new IllegalStateException("fixture owner disconnected; cannot claim cleanup");
        JsonObject restored = fixture.restore(player);
        if (restored.get("cleanup_complete").getAsBoolean()) fixture = null;
        return restored;
    }
    private JsonObject emptyCleanup() {
        JsonObject result = new JsonObject(); result.addProperty("owned_residuals", 0);
        result.addProperty("cleanup_complete", true);
        result.addProperty("vanilla_restored", true);
        result.addProperty("baseline_restored", !fixtureEverMutated && !nativeActionEverMutated); // Native gameplay may leave unobserved side effects.
        result.add("fixture_mutations", new JsonArray()); return result;
    }
    private JsonObject blockSnapshot(JsonObject parameters) {
        String dimension = Protocol.string(parameters, "dimension");
        ResourceLocation key = new ResourceLocation(dimension);
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, key));
        if (level == null) throw new IllegalArgumentException("dimension unavailable");
        BlockPos pos = blockPos(parameters);
        if (!level.isInWorldBounds(pos) || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
            throw new IllegalArgumentException("block not already loaded/in bounds");
        JsonObject row = new JsonObject(); row.addProperty("dimension", dimension);
        row.addProperty("x", pos.getX()); row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
        row.add("block_state", block(level.getBlockState(pos)));
        return row;
    }
    private JsonObject breakBlock(ServerPlayer player, JsonObject parameters) {
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL)
            throw new IllegalArgumentException("Survival player required");
        if (!Protocol.string(parameters, "dimension").equals(player.level().dimension().location().toString()))
            throw new IllegalArgumentException("wrong dimension");
        BlockPos pos = blockPos(parameters);
        if (!player.serverLevel().hasChunkAt(pos) || !player.serverLevel().isInWorldBounds(pos)
                || !player.serverLevel().getWorldBorder().isWithinBounds(pos)
                || player.server.isUnderSpawnProtection(player.serverLevel(), pos, player)
                || !player.serverLevel().mayInteract(player, pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 25)
            throw new IllegalArgumentException("unloaded/protected/unreachable target");
        BlockState before = player.serverLevel().getBlockState(pos);
        ItemStack held = player.getMainHandItem().copy();
        int food = player.getFoodData().getFoodLevel();
        boolean destroyed = player.gameMode.destroyBlock(pos);
        JsonObject row = playerCore(player);
        row.addProperty("x", pos.getX()); row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
        row.add("before_block_state", block(before)); row.add("after_block_state", block(player.serverLevel().getBlockState(pos)));
        row.addProperty("held_item_before", item(held)); row.addProperty("held_damage_before", held.getDamageValue());
        row.addProperty("held_item_after", item(player.getMainHandItem()));
        row.addProperty("held_damage_after", player.getMainHandItem().getDamageValue());
        row.addProperty("destroyed", destroyed);
        row.addProperty("food_before", food); row.addProperty("food_after", player.getFoodData().getFoodLevel());
        row.add("fixture_mutations", new JsonArray()); return row;
    }
    private static BlockPos blockPos(JsonObject p) {
        return new BlockPos(position(p, "x"), position(p, "y"), position(p, "z"));
    }
    private JsonObject interact(ServerPlayer player, JsonObject parameters) {
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) throw new IllegalArgumentException("spectator unsupported");
        JsonElement targetElement = parameters.get("target");
        if (targetElement == null || !targetElement.isJsonObject()) throw new IllegalArgumentException("target must be object");
        JsonObject target = targetElement.getAsJsonObject();
        Protocol.only(target, "type", "dimension", "x", "y", "z", "face");
        if (!"block".equals(Protocol.string(target, "type"))) throw new IllegalArgumentException("only block interactions supported");
        String dimension = Protocol.string(target, "dimension");
        if (!dimension.equals(player.level().dimension().location().toString())) throw new IllegalArgumentException("wrong dimension");
        int x = position(target, "x"), y = position(target, "y"), z = position(target, "z");
        BlockPos pos = new BlockPos(x, y, z);
        if (!player.serverLevel().hasChunkAt(pos) || !player.serverLevel().isInWorldBounds(pos)
                || !player.serverLevel().getWorldBorder().isWithinBounds(pos)
                || player.server.isUnderSpawnProtection(player.serverLevel(), pos, player)
                || !player.serverLevel().mayInteract(player, pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 25)
            throw new IllegalArgumentException("target unloaded, protected, outside bounds or unreachable");
        Direction face = Direction.byName(Protocol.string(target, "face"));
        if (face == null) throw new IllegalArgumentException("invalid face");
        InteractionHand hand = hand(parameters);
        if (!player.mayUseItemAt(pos.relative(face), face, player.getItemInHand(hand)))
            throw new IllegalArgumentException("item use not permitted");
        BlockState before = player.serverLevel().getBlockState(pos);
        int foodBefore = player.getFoodData().getFoodLevel();
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        InteractionResult outcome = player.gameMode.useItemOn(player, player.serverLevel(),
                player.getItemInHand(hand), hand, new BlockHitResult(hit, face, pos, false));
        JsonObject row = playerCore(player);
        row.addProperty("hand", hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand");
        row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z);
        row.addProperty("face", face.getName()); row.addProperty("interaction_result", outcome.name());
        row.add("before_block_state", block(before));
        row.add("after_block_state", block(player.serverLevel().getBlockState(pos)));
        row.addProperty("food_before", foodBefore); row.addProperty("food_after", player.getFoodData().getFoodLevel());
        row.add("fixture_mutations", new JsonArray());
        return row;
    }
    private JsonObject useItem(Protocol.Request request, ServerPlayer player, JsonObject parameters) {
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || player.isUsingItem())
            throw new IllegalArgumentException("idle Survival player required");
        InteractionHand hand = hand(parameters);
        ItemStack before = player.getItemInHand(hand).copy();
        if (before.isEmpty()) throw new IllegalArgumentException("held item required");
        int foodBefore = player.getFoodData().getFoodLevel();
        InteractionResult result = player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand);
        ItemStack after = player.getItemInHand(hand);
        JsonObject row = playerCore(player);
        row.addProperty("hand", hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand");
        row.addProperty("interaction_result", result.name());
        row.addProperty("item_before", item(before)); row.addProperty("count_before", before.getCount());
        row.addProperty("item_after", item(after)); row.addProperty("count_after", after.getCount());
        row.addProperty("food_before", foodBefore); row.addProperty("food_after", player.getFoodData().getFoodLevel());
        row.addProperty("consumption_completed", false);
        row.add("fixture_mutations", new JsonArray());
        if (player.isUsingItem()) {
            pendingUse = request; pendingUsePlayer = player.getUUID(); pendingUseHand = hand;
            pendingUseBaseline = row; pendingUseFinished = false;
            waitDeadline = Math.addExact(tick, request.timeout());
        }
        return row;
    }
    private static String item(ItemStack stack) { return stack.isEmpty() ? "minecraft:air" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(); }
    private static InteractionHand hand(JsonObject p) {
        return switch (Protocol.string(p, "hand")) {
            case "main_hand" -> InteractionHand.MAIN_HAND;
            case "off_hand" -> InteractionHand.OFF_HAND;
            default -> throw new IllegalArgumentException("invalid hand");
        };
    }
    private static int position(JsonObject p, String name) {
        long value = Protocol.number(p, name);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid position " + name);
        return (int) value;
    }
    private static JsonObject block(BlockState state) {
        JsonObject object = new JsonObject(), properties = new JsonObject();
        object.addProperty("id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
        for (Property<?> property : state.getProperties()) properties.addProperty(property.getName(), state.getValue(property).toString());
        object.add("properties", properties);
        return object;
    }
    private void finishScaleFailure(SyntheticScaleCohort cohort, JsonObject failure) {
        try {
            failure.add("cleanup", cohort.cleanup());
            activeScale = null;
        } catch (Exception cleanup) {
            failure.addProperty("cleanup_error", safeReason(cleanup));
            failure.addProperty("owned_residuals_unknown", true);
            LOG.error("Failed synthetic scale retained for stop-time cleanup", cleanup);
        }
        try { observe(cohort.request, "failed", failure); pendingScale = null; nextRequest++; }
        catch (IOException writeFailure) { stopped = true; LOG.error("Scale failure terminal unavailable", writeFailure); }
    }
    private JsonObject loadedTargetFile(Protocol.Request request, JsonObject census) throws Exception {
        Path targets = directory.resolve("targets");
        if (!Files.exists(targets, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(targets);
        if (!Files.isDirectory(targets, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(targets))
            throw new IOException("unsafe target evidence directory");
        String relative = String.format(Locale.ROOT, "targets/%012d.json", request.sequence());
        byte[] bytes = (JSON.toJson(census) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LoadedTargetObservation.MAX_BYTES) throw new IOException("target census byte cap exceeded");
        atomic(directory.resolve(relative), bytes);
        JsonObject file = new JsonObject(); file.addProperty("path", relative);
        file.addProperty("sha256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        file.addProperty("bytes", bytes.length);
        file.addProperty("records", census.get("candidate_count").getAsInt());
        JsonObject receipt = new JsonObject(); receipt.addProperty("schema", LoadedTargetObservation.SCHEMA);
        receipt.add("witness_context", census.get("witness_context").deepCopy());
        receipt.addProperty("selector_sha256", census.get("selector_sha256").getAsString());
        receipt.addProperty("complete", census.get("complete").getAsBoolean());
        if (census.has("incomplete_reason")) receipt.addProperty("incomplete_reason", census.get("incomplete_reason").getAsString());
        receipt.add("census_file", file); return receipt;
    }
    private JsonObject scaleFile(SyntheticScaleCohort cohort) throws Exception {
        Path scale = directory.resolve("scale");
        if (!Files.exists(scale, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(scale);
        if (!Files.isDirectory(scale, LinkOption.NOFOLLOW_LINKS)) throw new IOException("unsafe scale evidence directory");
        String relative = String.format(Locale.ROOT, "scale/%012d.jsonl", cohort.request.sequence());
        byte[] bytes = (String.join("\n", cohort.samples()) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_INVENTORY_BYTES) throw new IOException("scale raw evidence byte cap exceeded");
        atomic(directory.resolve(relative), bytes);
        JsonObject file = new JsonObject(); file.addProperty("path", relative);
        file.addProperty("sha256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        file.addProperty("bytes", bytes.length); file.addProperty("records", cohort.samples().size());
        return file;
    }
    private JsonObject incomingHitTraceFile(PassiveIncomingHit hit) throws Exception {
        Path folder = directory.resolve("incoming-hit");
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(folder);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(folder))
            throw new IOException("incoming-hit output folder unsafe");
        String relative = String.format(Locale.ROOT, "incoming-hit/%012d.jsonl", hit.binding.arm.sequence());
        List<String> rows = hit.rows();
        if (rows.size() > 64) throw new IOException("incoming-hit row cap exceeded");
        byte[] bytes = (rows.isEmpty() ? "" : String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1_000_000) throw new IOException("incoming-hit byte cap exceeded");
        atomic(directory.resolve(relative), bytes);
        JsonObject pointer = new JsonObject(); pointer.addProperty("schema", PassiveIncomingHit.TRACE);
        pointer.addProperty("path", relative); pointer.addProperty("bytes", bytes.length);
        pointer.addProperty("sha256", BlockMutationScope.sha256(bytes)); pointer.addProperty("records", rows.size());
        pointer.addProperty("arm_request_sequence", hit.binding.arm.sequence());
        pointer.addProperty("actual_native_session_id", run);
        pointer.addProperty("natural_threat_proven_by_native", false);
        return pointer;
    }
    private JsonObject packetDigTraceFile(PassivePhysicalDig dig) throws Exception {
        Path folder = directory.resolve("packet-dig");
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(folder);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(folder))
            throw new IOException("packet-dig output folder unsafe");
        String relative = String.format(Locale.ROOT, "packet-dig/%012d.jsonl", dig.binding.arm.sequence());
        List<String> rows = dig.rows();
        if (rows.size() > 256) throw new IOException("packet-dig row cap exceeded");
        byte[] bytes = (rows.isEmpty() ? "" : String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16_000_000) throw new IOException("packet-dig byte cap exceeded");
        atomic(directory.resolve(relative), bytes);
        JsonObject pointer = new JsonObject(); pointer.addProperty("schema", PassivePhysicalDig.TRACE_SCHEMA);
        pointer.addProperty("path", relative); pointer.addProperty("bytes", bytes.length);
        pointer.addProperty("sha256", BlockMutationScope.sha256(bytes));
        pointer.addProperty("records", rows.size());
        pointer.addProperty("arm_request_sequence", dig.binding.arm.sequence());
        pointer.addProperty("actual_native_session_id", run);
        pointer.addProperty("natural_acquisition_proven_by_native", false);
        return pointer;
    }
    private JsonObject acquisitionTraceFile(NativeDig dig) throws Exception {
        if (dig.traceFilePointer != null) return dig.traceFilePointer.deepCopy();
        Path traces = directory.resolve("acquisition");
        if (!Files.exists(traces, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(traces);
        if (!Files.isDirectory(traces, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(traces))
            throw new IOException("unsafe acquisition trace directory");
        List<String> rows = dig.trace.rows();
        if (rows.size() > NativeAcquisitionTrace.MAX_ROWS) throw new IOException("acquisition trace row cap exceeded");
        String relative = String.format(Locale.ROOT, "acquisition/%012d.jsonl", dig.request.sequence());
        byte[] bytes = (String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > NativeAcquisitionTrace.MAX_BYTES) throw new IOException("acquisition trace byte cap exceeded");
        atomic(directory.resolve(relative), bytes);
        JsonObject pointer = new JsonObject(); pointer.addProperty("schema", NativeWitnessContext.TRACE_SCHEMA);
        pointer.addProperty("path", relative);
        pointer.addProperty("sha256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        pointer.addProperty("bytes", bytes.length); pointer.addProperty("records", rows.size());
        dig.traceFilePointer = pointer;
        return pointer.deepCopy();
    }
    private JsonObject discover(long requestSequence) throws Exception {
        JsonArray files = new JsonArray(), gaps = new JsonArray();
        JsonObject domains = new JsonObject(), membership = new JsonObject(), definitions = new JsonObject();
        JsonObject measuredRosters = new JsonObject();
        var capturedDomains = EffectiveInventory.capture(server);
        for (var entry : capturedDomains.entrySet()) {
            String name = entry.getKey(); EffectiveInventory.Domain domain = entry.getValue();
            JsonObject file = inventoryFile(requestSequence, name + ".jsonl", domain.rows());
            file.addProperty("domain", name); file.addProperty("complete", domain.complete());
            file.addProperty("membership_complete", domain.membershipComplete());
            file.addProperty("definition_complete", domain.definitionComplete());
            file.addProperty("expected_members", domain.expectedMembers());
            file.addProperty("observed_rows", domain.rows().size());
            file.addProperty("unsupported_rows", domain.unsupportedRows());
            file.addProperty("source", domain.source()); file.addProperty("limitation", domain.limitation());
            List<String> ids = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (String text : domain.rows()) {
                JsonObject row = JsonParser.parseString(text).getAsJsonObject();
                if (!row.has("id")) continue;
                String id = row.get("id").getAsString();
                if (seen.add(id)) ids.add(id); // Domain membership flag retains duplicate evidence; roster cannot.
            }
            JsonObject rosterFile = inventoryFile(requestSequence, "roster_" + name + ".jsonl", measuredRosterRows(ids));
            rosterFile.addProperty("domain", name);
            measuredRosters.add(name, rosterFile);
            files.add(file); domains.addProperty(name, domain.complete());
            membership.addProperty(name, domain.membershipComplete());
            definitions.addProperty(name, domain.definitionComplete());
            if (!domain.membershipComplete()) gaps.add("domain:" + name + ":membership");
            if (!domain.definitionComplete()) gaps.add("domain:" + name + ":definition");
        }
        JsonObject result = new JsonObject();
        result.add("files", files); result.add("complete_by_domain", domains);
        result.add("membership_by_domain", membership); result.add("definition_by_domain", definitions);
        result.add("measured_domain_rosters", measuredRosters);
        result.add("effective_deep_void_resources", EffectiveDeepVoidResources.capture(server));
        result.addProperty("seed", server.overworld().getSeed());
        result.addProperty("seed_source", "server.overworld().getSeed()");
        result.addProperty("player_count", server.getPlayerCount());
        JsonArray roster = new JsonArray(); server.getPlayerList().getPlayers().stream()
                .map(player -> player.getUUID().toString()).sorted().forEach(roster::add);
        result.add("player_uuids", roster);
        result.add("supported_drivers", strings(Protocol.DRIVERS.stream().sorted().toList()));
        List<String> unsupportedProviders = List.of("provider:advancements", "provider:client_resources",
                "provider:unknown_mod_specific_sources", "provider:live_world_generation",
                "provider:natural_acquisition", "provider:runtime_created_recipes");
        unsupportedProviders.forEach(gaps::add);
        result.add("gaps", gaps);
        // Measured per-domain inventory is not the reviewed whole-pack roster. This
        // computed predicate can become true only after every essential provider exists.
        result.addProperty("complete", EffectiveInventory.globallyComplete(capturedDomains, unsupportedProviders));
        return result;
    }
    static List<String> measuredRosterRows(List<String> ids) {
        return ids.stream().distinct().sorted().map(id -> {
            JsonObject row = new JsonObject(); row.addProperty("id", id); return row.toString();
        }).toList();
    }
    static int inventoryByteCap(String name) {
        return name.equals("recipes_semantics.jsonl") ? MAX_RECIPE_SEMANTICS_BYTES : MAX_INVENTORY_BYTES;
    }
    private JsonObject inventoryFile(long requestSequence, String name, List<String> rows) throws Exception {
        if (rows.size() > MAX_ROWS) throw new IOException("inventory row cap exceeded: " + name);
        List<String> sortedRows = new ArrayList<>(rows);
        Collections.sort(sortedRows);
        byte[] bytes = (String.join("\n", sortedRows) + "\n").getBytes(StandardCharsets.UTF_8);
        int byteCap = inventoryByteCap(name);
        if (bytes.length > byteCap) throw new IOException("inventory byte cap exceeded: " + name + " (" + bytes.length + " > " + byteCap + ")");
        Path inventory = directory.resolve("inventory");
        if (!Files.exists(inventory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(inventory);
        if (!Files.isDirectory(inventory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("inventory path unsafe");
        String prefix = String.format(Locale.ROOT, "%012d", requestSequence);
        Path batch = inventory.resolve(prefix);
        if (!Files.exists(batch, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(batch);
        if (!Files.isDirectory(batch, LinkOption.NOFOLLOW_LINKS)) throw new IOException("inventory batch unsafe");
        String path = "inventory/" + prefix + "/" + name;
        atomic(directory.resolve(path), bytes);
        JsonObject file = new JsonObject(); file.addProperty("path", path);
        file.addProperty("sha256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        file.addProperty("bytes", bytes.length); file.addProperty("records", rows.size()); return file;
    }
    private void observe(Protocol.Request request, String kind, JsonObject payload) throws IOException {
        JsonObject row = new JsonObject();
        row.addProperty("schema", "bc.playtest_observation.v2");
        row.addProperty("run_id", run); row.addProperty("case_id", request.caseId()); row.addProperty("token", token);
        row.addProperty("sequence", nextOutput); row.addProperty("request_sequence", request.sequence());
        row.addProperty("driver", request.driver()); row.addProperty("source", "server"); row.addProperty("kind", kind);
        row.addProperty("tick", tick);
        if (!"started".equals(kind) && mutationPrefixRequest == request.sequence() && mutationPreActionPrefix != null)
            payload.add("block_mutation_pre_action_prefix", mutationPreActionPrefix.deepCopy());
        if (!"started".equals(kind) && mutationPrefixRequest == request.sequence() && birthPreActionPrefix != null)
            payload.add("block_birth_pre_action_prefix", birthPreActionPrefix.deepCopy());
        row.add("payload", payload);
        byte[] bytes = (JSON.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REQUEST) throw new IOException("observation line cap exceeded");
        Path log = directory.resolve("observations.jsonl");
        if (Files.isSymbolicLink(log)) throw new IOException("observation path changed");
        try (var channel = java.nio.channels.FileChannel.open(log, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            channel.write(ByteBuffer.wrap(bytes)); channel.force(true);
        }
        nextOutput++;
    }
    private static void atomic(Path path, byte[] bytes) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("refusing to overwrite output " + path.getFileName());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) throw new IOException("stale partial output");
        Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(temporary); }
    }
    private static String safeReason(Exception failure) {
        String message = String.valueOf(failure.getMessage()).replaceAll("[\\r\\n]", " ");
        return failure.getClass().getSimpleName() + ":" + message.substring(0, Math.min(160, message.length()));
    }
    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray(); values.forEach(array::add); return array;
    }
    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
}
