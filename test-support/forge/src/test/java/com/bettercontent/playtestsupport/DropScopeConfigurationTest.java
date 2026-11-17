package com.bettercontent.playtestsupport;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Source/config boundary checks; authored only, no runtime/native assertion. */
final class DropScopeConfigurationTest {
    @Test void mixinIsRequiredServerSideAndScopedToDropHarvestAndChunkTickObservers() throws Exception {
        try (var stream = getClass().getResourceAsStream("/better_playtest_native_support.mixins.json")) {
            assertNotNull(stream);
            var configuration = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(configuration.get("required").getAsBoolean());
            assertEquals("better_playtest_native_support.refmap.json", configuration.get("refmap").getAsString());
            assertEquals(List.of("BlockPopResourceObserverMixin", "NativeHarvestGateObserverMixin",
                    "ServerLevelNativeChunkTickObserverMixin", "RealClientUsePacketObserverMixin",
                    "BetterSpawnsFinalizeObserverMixin", "LevelChunkMutationObserverMixin",
                    "LevelChunkPaletteMutationObserverMixin", "PalettedContainerMutationObserverMixin",
                    "ChunkAccessSectionsExposureMixin", "LevelChunkBirthObserverMixin",
                    "WaterAutoProviderTickObserverMixin", "RealPacketDigObserverMixin",
                    "RealGameModeDigObserverMixin", "RealDestroyProgressObserverMixin",
                    "RealNetworkPacketIngressMixin", "RealToolHurtBreakCauseObserverMixin",
                    "RealEquipmentBreakObserverMixin", "RealIncomingPlayerDamageObserverMixin",
                    "RealIncomingArmorOperandsObserverMixin", "RealIncomingMagicObserverMixin",
                    "RealIncomingEnchantmentObserverMixin", "RealShutdownDrainObserverMixin"),
                    configuration.getAsJsonArray("mixins").asList().stream()
                    .map(value -> value.getAsString()).toList());
            assertEquals(List.of("ClientAwtBootstrapMixin", "ClientPreKeybindUseGateMixin",
                            "RealContainerScreenObserverMixin", "RealGameModeContainerClickObserverMixin"),
                    configuration.getAsJsonArray("client").asList().stream().map(value -> value.getAsString()).toList());
            assertEquals(1, configuration.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        }
    }
    @Test void descriptorDeclaresBothRequiredMappedTargetsAndBoundedActions() throws Exception {
        try (var stream = getClass().getResourceAsStream("/META-INF/bc-playtest-support.json")) {
            assertNotNull(stream);
            var descriptor = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("net.minecraft.world.level.block.Block.popResource(Level,BlockPos,ItemStack)",
                    descriptor.get("required_mixin_target").getAsString());
            assertEquals("net.minecraft.server.level.ServerLevel.tickChunk(LevelChunk,int)",
                    descriptor.get("required_chunk_tick_target").getAsString());
            var actions = descriptor.getAsJsonArray("supported_drivers").asList().stream().map(v -> v.getAsString()).toList();
            assertTrue(actions.containsAll(List.of("player.dig", "player.select_item", "scale.begin", "scale.cleanup",
                    PassiveIncomingHit.ARM, PassiveIncomingHit.AWAIT, Stage1RealMutationControls.POSITIVE,
                    Stage1RealMutationControls.NEGATIVE_SECTIONS, Stage1RealMutationControls.NEGATIVE_IO)));
            var client = descriptor.getAsJsonObject("client_observation");
            assertNotNull(client);
            assertEquals("client", client.get("source").getAsString());
            assertEquals("bc.playtest_action.v2", client.get("protocol").getAsString());
            var clientActions = client.getAsJsonArray("supported_drivers").asList().stream()
                    .map(v -> v.getAsString()).toList();
            assertEquals(List.of("client.snapshot", "client.framebuffer", "client.emi_index",
                    "client.emi_hover", "client.emi_page", "client.input_key", "client.input_click",
                    "client.input_point", "client.menu_slot.observe_v1", "client.menu_slot.point_v1",
                    "client.input_hold", "client.input_release", "client.input_status"), clientActions);
            var holdKinds = client.getAsJsonArray("supported_input_hold_kinds").asList().stream()
                    .map(value -> value.getAsString()).toList();
            assertEquals(List.of("use", "inventory", "onboarding", "walk_forward",
                    "emi_mouse_left", "emi_mouse_right", "container_slot_left"), holdKinds);
            assertTrue(clientActions.stream().noneMatch(actions::contains));
        }
    }
    @Test void readOnlyObserversAreWhitelistedButUnknownActionsAreDirty() {
        for (String readOnly : List.of("inventory.discover", "server.snapshot", "world.sample", "world.entry",
                "player.snapshot", "player.water_snapshot", "player.water_auto_snapshot_v1",
                "player.water_auto_observe.arm_v1", "player.water_auto_observe.await_v1",
                "player.observe_dig.arm_v5", "player.observe_dig.await_v5",
                PassiveIncomingHit.ARM, PassiveIncomingHit.AWAIT,
                "block_mutation.control_expose_states_v1", Stage1RealMutationControls.NEGATIVE_SECTIONS,
                "player.lifecycle_snapshot", "entity.snapshot", "wait.ticks"))
            assertFalse(Protocol.mayMutate(readOnly), readOnly);
        assertTrue(Protocol.mayMutate("player.dig"));
        assertTrue(Protocol.mayMutate("fixture.begin_water"));
        assertTrue(Protocol.mayMutate("scale.begin"));
        assertTrue(Protocol.mayMutate("scale.cleanup"));
        assertTrue(Protocol.mayMutate("player.select_item"));
        assertTrue(Protocol.mayMutate("future.unreviewed_action"));
    }
}
