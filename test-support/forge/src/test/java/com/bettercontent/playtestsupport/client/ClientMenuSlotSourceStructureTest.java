package com.bettercontent.playtestsupport.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source-only safeguards; NOT an OS-click, Forge Mixin application or decoded-packet runtime test. */
class ClientMenuSlotSourceStructureTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bettercontent/playtestsupport/client", name));
    }
    @Test void stagedOriginalRenderAndOsInputNeverCallTheGuiOrMenuDirectly() throws Exception {
        String observation = source("ClientMenuSlotObservation.java");
        String support = source("ClientNativeSupport.java");
        String robot = source("RobotInputBridge.java");
        assertTrue(support.contains("observedOriginalContainerRenderBegin(Minecraft client, AbstractContainerScreen<?> screen)"));
        assertTrue(support.contains("observedOriginalRenderedSlotItem(Minecraft client, AbstractContainerScreen<?> screen,"));
        assertTrue(support.contains("observedOriginalContainerRender(Minecraft client, AbstractContainerScreen<?> screen)"));
        assertTrue(support.contains("observedOriginalContainerSlotClick(Minecraft client, AbstractContainerScreen<?> screen,"));
        assertTrue(support.contains("observedOriginalGameModeInventoryClick(Minecraft client, int containerId,"));
        assertTrue(observation.contains("screen.getSlotUnderMouse()"));
        assertTrue(observation.contains("renderGeneration <= pointGeneration"));
        assertTrue(observation.contains("renderedHoveredSlot != slot"));
        assertTrue(observation.contains("GLFW.glfwGetCursorPos"));
        assertTrue(observation.contains(".mouseMove(rawX, rawY)"));
        assertTrue(observation.contains("slot.index != slotId"));
        assertTrue(observation.contains("slot.index != index"));
        assertTrue(observation.contains("container_slot_index\", slot.getContainerSlot()"));
        assertFalse(observation.contains("container_slot_index\", slot.index"));
        assertTrue(observation.contains("owning_container_class"));
        assertTrue(observation.contains("owning_container_first_menu_slot_index"));
        assertTrue(observation.contains("owning_container_is_player_inventory"));
        assertTrue(observation.contains("menu.slots.get(i).container == slot.container"));
        assertTrue(observation.contains("originalSlotClicks != 1 || originalGameModeSends != 1"));
        assertTrue(robot.contains("kindName.equals(\"container_slot_left\")"));
        assertTrue(robot.contains("ClientMenuSlotObservation.requireFreshHover"));
        assertTrue(robot.contains("kind != HeldInputState.Kind.CONTAINER_SLOT_LEFT)\n            robot.mouseMove"));
        assertTrue(robot.contains("robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)"));
        assertTrue(robot.contains("robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)"));
        for (String forbidden : new String[] {".mouseClicked(", ".mouseReleased(", ".slotClicked(",
                ".handleInventoryMouseClick(", ".send(new ServerboundContainerClickPacket"}) {
            assertFalse(observation.contains(forbidden), "observer injected original callback/packet: " + forbidden);
            assertFalse(robot.contains(forbidden), "OS bridge injected original callback/packet: " + forbidden);
        }
    }
    @Test void immutableRuntimeAndExactSlotContextAreNotOptional() throws Exception {
        String observation = source("ClientMenuSlotObservation.java");
        assertTrue(observation.contains("ClientInputRuntimeBinding.capture("));
        assertTrue(observation.contains("input_runtime_sha256"));
        assertTrue(observation.contains("first.menu() != first.screen().getMenu()"));
        assertTrue(observation.contains("menu.getStateId() != stateId"));
        assertTrue(observation.contains("renderGeneration <= first.generation()"));
        assertTrue(observation.contains("!pointed || hovered != null"));
        assertTrue(observation.contains("geometry_hover_complete\", next && itemCaptured"));
        assertTrue(observation.contains("result_item_unread_due_to_lazy_provider"));
        assertTrue(observation.contains("item_observation_complete\", itemCaptured"));
        assertTrue(observation.contains("original_renderSlot_first_Slot.getItem_return_local5_NOT_a_second_getter"));
        assertTrue(observation.contains("originalInFlight.save(new net.minecraft.nbt.CompoundTag())"));
        assertTrue(observation.contains("originalInFlight.isEmpty() ? \"minecraft:air\""));
        assertTrue(observation.contains("originalInFlight.getCount()"));
        assertTrue(observation.contains("inFlightGeneration == renderGeneration"));
        assertTrue(observation.contains("hovered.originalItemSha() == null"));
        assertTrue(observation.contains("original_render_slot_or_menu_state_drift"));
        assertTrue(observation.contains("original_drag_split_quickcraft_or_snapback_preview"));
        assertTrue(observation.contains("unreviewed_ghost_or_custom_slot_layout"));
        assertFalse(observation.contains("slot.getItem()"));
        assertFalse(observation.contains("canonical_stack_sha256"));
        assertFalse(observation.contains("framebuffer_sha256"));
    }
}
