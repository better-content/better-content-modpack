package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/** SOURCE-ONLY: original rendered Slot hover and real OS pointer. Not a recipe, click or packet witness.
 * B's read-only Mixin brackets original render and observes renderSlot's first in-flight getItem local. */
final class ClientMenuSlotObservation {
    private static final Gson JSON = new Gson();
    private static final String SCHEMA = "bc.playtest_client_menu_slot_observation.v1";
    private static long renderGeneration;
    private static AbstractContainerScreen<?> renderedScreen;
    private static AbstractContainerMenu renderedMenu;
    private static Slot renderedHoveredSlot;
    private static long renderedNanos;
    private static boolean originalRenderInFlight;
    private static long inFlightGeneration;
    private static int inFlightStateId;
    private static AbstractContainerScreen<?> inFlightScreen;
    private static AbstractContainerMenu inFlightMenu;
    private static ItemCapture pendingItem, renderedItem;
    private static Inspection first, hovered;
    // Only these PINNED original layouts have a reviewed visible Slot, not a ghost/picker/widget.
    private static final Set<String> KNOWN_SCREENS = Set.of(
        "net.minecraft.client.gui.screens.inventory.InventoryScreen",
        "net.minecraft.client.gui.screens.inventory.CraftingScreen",
        "slimeknights.tconstruct.tables.client.inventory.PartBuilderScreen",
        "slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen");
    private static final Set<String> KNOWN_SLOTS = Set.of("net.minecraft.world.inventory.Slot",
        "net.minecraft.world.inventory.ResultSlot",
        "slimeknights.tconstruct.tables.menu.slot.LazyResultSlot");
    private static final Set<String> KNOWN_MENUS = Set.of("net.minecraft.world.inventory.InventoryMenu",
        "net.minecraft.world.inventory.CraftingMenu",
        "slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu",
        "slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu");
    private static boolean pointed, hoverConsumed;
    private static int originalSlotClicks, originalGameModeSends;
    private static long pointGeneration;
    private ClientMenuSlotObservation() {}

    private record ItemCapture(long generation, AbstractContainerScreen<?> screen, AbstractContainerMenu menu,
        int stateId, Slot slot, int x, int y, JsonObject item, String issue) {}

    /** B Mixin render HEAD brackets only ORIGINAL renderSlot execution; no item getter here. */
    static synchronized void originalRenderBegin(Minecraft client, AbstractContainerScreen<?> screen) {
        originalRenderInFlight = client != null && screen != null && client.isSameThread() &&
            client.screen == screen && renderGeneration < Long.MAX_VALUE;
        inFlightScreen = originalRenderInFlight ? screen : null;
        inFlightMenu = originalRenderInFlight ? screen.getMenu() : null;
        inFlightStateId = originalRenderInFlight ? screen.getMenu().getStateId() : -1;
        inFlightGeneration = renderGeneration + 1;
        pendingItem = null;
    }

    static synchronized void failSoftRenderCapture() {
        originalRenderInFlight = false; pendingItem = null; renderedItem = null;
    }

    /** B Mixin observes the local returned from the FIRST ORIGINAL renderSlot Slot.getItem INVOKE_ASSIGN.
     * Extra getItem/recipe/matches/assemble calls are forbidden, including for EMPTY results. */
    static synchronized void originalRenderedSlotItem(Minecraft client, AbstractContainerScreen<?> screen,
        Slot slot, int x, int y, ItemStack originalInFlight, boolean clickedSlotClear,
        boolean draggingItemEmpty, boolean notSplitting, boolean notQuickCrafting,
        boolean quickCraftSlotsEmpty, boolean snapbackItemEmpty) {
        Inspection target = hovered != null && !hoverConsumed ? hovered : pointed ? first : null;
        if (!originalRenderInFlight || target == null || screen == null ||
                client == null || !client.isSameThread() || client.screen != screen ||
                screen != inFlightScreen || screen.getMenu() != inFlightMenu ||
                target.screen() != screen || target.menu() != inFlightMenu ||
                inFlightGeneration <= pointGeneration ||
                target.menuSlot() < 0 || target.menuSlot() >= inFlightMenu.slots.size() ||
                slot != inFlightMenu.slots.get(target.menuSlot())) return;
        String issue = null;
        if (pendingItem != null) issue = "duplicate_original_render_slot_item_capture";
        else if (!KNOWN_SCREENS.contains(screen.getClass().getName()) ||
                !KNOWN_MENUS.contains(inFlightMenu.getClass().getName()) ||
                !KNOWN_SLOTS.contains(slot.getClass().getName())) issue = "unreviewed_ghost_or_custom_slot_layout";
        else if (!clickedSlotClear || !draggingItemEmpty || !notSplitting || !notQuickCrafting ||
                !quickCraftSlotsEmpty || !snapbackItemEmpty) issue = "original_drag_split_quickcraft_or_snapback_preview";
        else if (originalInFlight == null || slot.index != target.menuSlot() ||
                x != slot.x || y != slot.y || screen.getMenu().getStateId() != inFlightStateId ||
                screen.getGuiLeft() != target.guiLeft() || screen.getGuiTop() != target.guiTop())
            issue = "original_render_slot_or_menu_state_drift";
        JsonObject item = null;
        if (issue == null) try {
            item = new JsonObject();
            item.addProperty("item_id", originalInFlight.isEmpty() ? "minecraft:air" :
                ForgeRegistries.ITEMS.getKey(originalInFlight.getItem()).toString());
            item.addProperty("count", originalInFlight.getCount()); // EMPTY Count0 is an actual observation.
            String snbt = originalInFlight.save(new net.minecraft.nbt.CompoundTag()).toString();
            item.addProperty("stack_snbt_sha256", ClientNativeSupport.sha256(snbt.getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException failure) {
            issue = "original_rendered_item_serialization_unavailable"; item = null;
        }
        pendingItem = new ItemCapture(inFlightGeneration, screen, inFlightMenu, inFlightStateId,
            slot, x, y, item, issue);
    }

    static synchronized void originalRender(Minecraft client, AbstractContainerScreen<?> screen) {
        if (client == null || screen == null || !client.isSameThread() || client.screen != screen) {
            failSoftRenderCapture(); renderedScreen = null; renderedMenu = null;
            renderedHoveredSlot = null; return; // Never alter original rendering on a capture miss.
        }
        if (renderGeneration == Long.MAX_VALUE) throw new IllegalStateException("render generation exhausted");
        renderGeneration++;
        renderedScreen = screen; renderedMenu = screen.getMenu();
        renderedHoveredSlot = screen.getSlotUnderMouse(); // Original public LAST-rendered hover, no second hit test.
        renderedNanos = System.nanoTime();
        renderedItem = originalRenderInFlight && inFlightScreen == screen && inFlightMenu == screen.getMenu() &&
            inFlightStateId == screen.getMenu().getStateId() && inFlightGeneration == renderGeneration ?
            pendingItem : null; // No HEAD/local injection or state drift: item remains UNKNOWN.
        originalRenderInFlight = false; pendingItem = null;
    }

    private record Inspection(String sha, String actor, String dimension, String profile, String runtimeSha,
        String screenClass, String menuClass, String menuType, int containerId, int stateId,
        int menuSlot, int guiX, int guiY, int guiLeft, int guiTop, long window, long generation,
        String originalItemSha, Slot slot,
        AbstractContainerScreen<?> screen, AbstractContainerMenu menu,
        ClientInputRuntimeBinding binding, long sequence) {}

    private static ClientInputRuntimeBinding binding(Minecraft client, String profile, String expectedSha,
        String actor, String dimension, String screenClass, String menuClass) throws Exception {
        ClientAwtHeadlessBootstrap.requireReady();
        if (!client.isSameThread() || client.player == null || client.level == null ||
                client.getConnection() == null || client.gameMode == null ||
                client.gameMode.getPlayerMode() != GameType.SURVIVAL ||
                !client.player.getUUID().toString().equals(actor) ||
                !client.level.dimension().location().toString().equals(dimension) ||
                !(client.screen instanceof AbstractContainerScreen<?> screen) ||
                !screen.getClass().getName().equals(screenClass) ||
                !screen.getMenu().getClass().getName().equals(menuClass) ||
                GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == 0 ||
                GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == 0 ||
                GLFWNativeX11.glfwGetX11Window(client.getWindow().getWindow()) == 0)
            throw new IllegalStateException("actual joined Survival container actor/menu/window/focus changed");
        RobotInputBridge.validatedProfile(client, profile);
        ClientInputRuntimeBinding captured = ClientInputRuntimeBinding.capture(ClientNativeSupport.mailboxDirectory(),
            expectedSha, ClientNativeSupport.runId(), profile, System.getenv("DISPLAY"),
            System.getenv("XAUTHORITY"), ProcessHandle.current().pid());
        if (!ClientNativeSupport.sha256(RobotInputBridge.boundedAuthority(Path.of(captured.authorityPath)))
                .equals(captured.authoritySha))
            throw new IllegalStateException("local private Xauthority changed");
        return captured;
    }

    private static int[] cursor(Minecraft client) {
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getWindow(), x, y);
        int rawWidth = client.getWindow().getScreenWidth(), rawHeight = client.getWindow().getScreenHeight();
        if (!Double.isFinite(x[0]) || !Double.isFinite(y[0]) || x[0] < 0 || y[0] < 0 ||
                x[0] >= rawWidth || y[0] >= rawHeight)
            throw new IllegalStateException("real GLFW pointer left owned window");
        return new int[] {(int) Math.floor(x[0] * client.getWindow().getGuiScaledWidth() / rawWidth),
            (int) Math.floor(y[0] * client.getWindow().getGuiScaledHeight() / rawHeight)};
    }
    private static String hash(JsonObject row) {
        return ClientNativeSupport.sha256(JSON.toJson(row).getBytes(StandardCharsets.UTF_8));
    }
    private static int firstMenuSlotForSameContainer(AbstractContainerMenu menu, Slot slot) {
        // Slot.container is a final object reference. This ordinal is a MENU-LOCAL identity alias,
        // NOT a saved BE/NBT slot or a global natural-origin identifier.
        for (int i = 0; i < menu.slots.size(); i++)
            if (menu.slots.get(i).container == slot.container) return i;
        throw new IllegalStateException("selected backing Container has no slot in actual menu");
    }
    private static int menuIndex(AbstractContainerMenu menu, Slot slot) {
        if (slot == null) return -1;
        int index = menu.slots.indexOf(slot);
        if (index < 0 || index >= menu.slots.size() || menu.slots.get(index) != slot)
            throw new IllegalStateException("original hovered Slot is not owned by current menu");
        return index;
    }
    static synchronized JsonObject observe(Minecraft client, Protocol.Request request) throws Exception {
        if (RobotInputBridge.inputPending())
            throw new IllegalStateException("cannot re-observe or retarget a menu while OS mouse is held");
        JsonObject args = request.parameters();
        Protocol.only(args, "profile", "player_uuid", "dimension", "reviewed_screen_class",
            "reviewed_menu_class", "reviewed_menu_type", "reviewed_container_id", "reviewed_state_id",
            "reviewed_slot_index", "gui_x", "gui_y", "input_runtime_sha256",
            "prior_observation_projection_sha256");
        String profile = Protocol.string(args, "profile"), actor = Protocol.string(args, "player_uuid");
        String dimension = Protocol.string(args, "dimension");
        String screenClass = Protocol.string(args, "reviewed_screen_class");
        String menuClass = Protocol.string(args, "reviewed_menu_class");
        String reviewedType = Protocol.string(args, "reviewed_menu_type");
        ClientInputRuntimeBinding runtime = binding(client, profile, Protocol.string(args, "input_runtime_sha256"),
            actor, dimension, screenClass, menuClass);
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.screen;
        AbstractContainerMenu menu = screen.getMenu();
        int containerId = Math.toIntExact(Protocol.number(args, "reviewed_container_id"));
        int stateId = Math.toIntExact(Protocol.number(args, "reviewed_state_id"));
        int index = Math.toIntExact(Protocol.number(args, "reviewed_slot_index"));
        int guiX = Math.toIntExact(Protocol.number(args, "gui_x"));
        int guiY = Math.toIntExact(Protocol.number(args, "gui_y"));
        if (menu.slots.size() < 1 || menu.slots.size() > 128 || index < 0 || index >= menu.slots.size() ||
                menu.containerId != containerId || menu.getStateId() != stateId ||
                menu.getType() == null || ForgeRegistries.MENU_TYPES.getKey(menu.getType()) == null ||
                !ForgeRegistries.MENU_TYPES.getKey(menu.getType()).toString().equals(reviewedType) ||
                !reviewedType.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ||
                client.getWindow().getWindow() == 0 || renderedScreen != screen || renderedMenu != menu ||
                renderGeneration < 1 || renderedNanos <= 0)
            throw new IllegalStateException("unknown, stale, unrendered or oversized actual menu");
        Slot slot = menu.slots.get(index);
        if (slot == null || slot.container == null || slot.index != index ||
                slot.getContainerSlot() < 0 || slot.getContainerSlot() > 1024)
            throw new IllegalStateException("menu packet Slot.index differs from bounded backing Container slot");
        if (!KNOWN_SCREENS.contains(screen.getClass().getName()) ||
                !KNOWN_MENUS.contains(menu.getClass().getName()) ||
                !KNOWN_SLOTS.contains(slot.getClass().getName()))
            throw new IllegalStateException("unknown ghost/widget or unreviewed original container layout");
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        long slotX = (long) left + slot.x, slotY = (long) top + slot.y;
        if (!slot.isActive() || slotX < 0 || slotY < 0 || slotX + 16 > client.getWindow().getGuiScaledWidth() ||
                slotY + 16 > client.getWindow().getGuiScaledHeight() ||
                guiX != slotX + 8 || guiY != slotY + 8 ||
                renderedHoveredSlot != null && menuIndex(menu, renderedHoveredSlot) < 0)
            throw new IllegalStateException("visible single slot or reviewed center pixel unavailable");
        int[] pointer = cursor(client);
        boolean next = !args.get("prior_observation_projection_sha256").isJsonNull();
        if (next && (first == null || !pointed || hovered != null ||
                !Protocol.string(args, "prior_observation_projection_sha256").equals(first.sha()) ||
                renderGeneration <= pointGeneration || renderGeneration <= first.generation() ||
                screen != first.screen() || menu != first.menu() || index != first.menuSlot() ||
                slot != first.slot() || containerId != first.containerId() || stateId != first.stateId() ||
                guiX != first.guiX() || guiY != first.guiY() ||
                left != first.guiLeft() || top != first.guiTop() ||
                !actor.equals(first.actor()) || !dimension.equals(first.dimension()) ||
                !profile.equals(first.profile()) || !reviewedType.equals(first.menuType()) ||
                !runtime.sha256.equals(first.runtimeSha()) ||
                pointer[0] != guiX || pointer[1] != guiY ||
                renderedHoveredSlot != slot || menuIndex(menu, renderedHoveredSlot) != index))
            throw new IllegalStateException("fresh original rendered hovered slot after physical pointer missing");
        if (!next && (pointed || first != null && !hoverConsumed))
            throw new IllegalStateException("prior menu slot projection was not consumed");
        JsonObject geometry = new JsonObject();
        geometry.addProperty("screen_class", screenClass); geometry.addProperty("menu_class", menuClass);
        geometry.addProperty("menu_type_id", reviewedType); geometry.addProperty("container_id", containerId);
        geometry.addProperty("state_id", stateId); geometry.addProperty("slot_index", index);
        geometry.addProperty("x", slotX); geometry.addProperty("y", slotY);
        geometry.addProperty("width", 16); geometry.addProperty("height", 16);
        String geometrySha = hash(geometry);
        JsonObject actualSlot = new JsonObject();
        actualSlot.addProperty("menu_slot_index", index); // Packet/menu global index; Slot.index == index.
        actualSlot.addProperty("container_slot_index", slot.getContainerSlot()); // Slot private final slot.
        actualSlot.addProperty("owning_container_class", slot.container.getClass().getName());
        actualSlot.addProperty("owning_container_first_menu_slot_index",
            firstMenuSlotForSameContainer(menu, slot));
        actualSlot.addProperty("owning_container_is_player_inventory", slot.container == client.player.getInventory());
        actualSlot.addProperty("slot_class", slot.getClass().getName());
        actualSlot.addProperty("x", slot.x); actualSlot.addProperty("y", slot.y);
        actualSlot.addProperty("active", slot.isActive());
        // First observe has no selected item; ONLY the post-point original renderSlot local5 is usable.
        // No arbitrary Slot.getItem or menu-wide traversal. A null remains UNKNOWN, never EMPTY.
        ItemCapture rendered = renderedItem;
        boolean itemCaptured = next && rendered != null && rendered.issue() == null && rendered.item() != null &&
            rendered.generation() == renderGeneration && rendered.screen() == screen &&
            rendered.menu() == menu && rendered.stateId() == stateId && rendered.slot() == slot &&
            rendered.x() == slot.x && rendered.y() == slot.y;
        actualSlot.add("item", itemCaptured ? rendered.item().deepCopy() : JsonNull.INSTANCE);
        actualSlot.addProperty("item_observation_complete", itemCaptured);
        actualSlot.addProperty("result_item_unread_due_to_lazy_provider",
            !itemCaptured && slot.getClass().getName().equals(
                "slimeknights.tconstruct.tables.menu.slot.LazyResultSlot"));
        JsonObject rect = new JsonObject(); rect.addProperty("x", slotX); rect.addProperty("y", slotY);
        rect.addProperty("width", 16); rect.addProperty("height", 16);
        JsonObject out = new JsonObject(); out.addProperty("schema", SCHEMA);
        out.addProperty("actor_uuid", actor); out.addProperty("dimension", dimension);
        out.addProperty("input_runtime_sha256_unverified_by_client", runtime.sha256);
        out.addProperty("client_session_id", runtime.clientSession); out.addProperty("epoch_id", runtime.epochId);
        out.addProperty("request_sequence", request.sequence());
        out.addProperty("actual_screen_class", screen.getClass().getName());
        out.addProperty("actual_menu_class", menu.getClass().getName());
        out.addProperty("actual_menu_type_id", reviewedType);
        out.addProperty("actual_container_id", menu.containerId); out.addProperty("actual_state_id", menu.getStateId());
        out.addProperty("actual_slot_count", menu.slots.size()); out.add("actual_slot", actualSlot);
        out.addProperty("actual_gui_left", left); out.addProperty("actual_gui_top", top);
        out.add("actual_slot_rect", rect);
        int hoveredIndex = menuIndex(menu, renderedHoveredSlot);
        if (hoveredIndex < 0) out.add("actual_hovered_menu_slot_index", JsonNull.INSTANCE);
        else out.addProperty("actual_hovered_menu_slot_index", hoveredIndex);
        out.addProperty("actual_cursor_gui_x", pointer[0]); out.addProperty("actual_cursor_gui_y", pointer[1]);
        out.addProperty("actual_glfw_window", client.getWindow().getWindow());
        out.addProperty("actual_window_focused", GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_FOCUSED) != 0);
        out.addProperty("actual_original_render_generation", renderGeneration);
        if (itemCaptured) out.addProperty("actual_original_render_item_generation", rendered.generation());
        else out.add("actual_original_render_item_generation", JsonNull.INSTANCE);
        out.addProperty("actual_original_render_item_source", itemCaptured ?
            "original_renderSlot_first_Slot.getItem_return_local5_NOT_a_second_getter" : "unavailable");
        out.addProperty("actual_monotonic_nanos", renderedNanos);
        out.addProperty("slot_geometry_sha256", geometrySha);
        out.addProperty("geometry_complete", true); // Exact actual menu Slot rect only, NOT recipe origin.
        out.addProperty("original_render_hover_complete", next); // Only fresh post-point original frame.
        out.addProperty("geometry_hover_complete", next && itemCaptured); // No drag/ghost/missing item.
        out.addProperty("observation_complete", next && itemCaptured); // UI ONLY; NOT server item origin.
        com.google.gson.JsonArray unsupported = new com.google.gson.JsonArray();
        if (!itemCaptured) unsupported.add(next && rendered != null && rendered.issue() != null ?
            rendered.issue() : "slot_item_not_captured_from_original_render_or_click_in_flight");
        out.add("unsupported_reasons", unsupported);
        out.addProperty("observation_projection_sha256", hash(out));
        Inspection current = new Inspection(out.get("observation_projection_sha256").getAsString(), actor, dimension,
            profile, runtime.sha256, screenClass, menuClass, reviewedType, containerId, stateId,
            index, guiX, guiY, left, top, client.getWindow().getWindow(), renderGeneration,
            itemCaptured ? hash(rendered.item()) : null, slot, screen, menu,
            runtime, request.sequence());
        if (next) { hovered = current; hoverConsumed = false; pointed = false;
            originalSlotClicks = 0; originalGameModeSends = 0; }
        else { first = current; hovered = null; hoverConsumed = false; }
        return out;
    }
    static synchronized JsonObject point(Minecraft client, Protocol.Request request) throws Exception {
        if (RobotInputBridge.inputPending())
            throw new IllegalStateException("cannot physically move pointer while OS mouse is held");
        JsonObject args = request.parameters();
        Protocol.only(args, "profile", "player_uuid", "dimension", "reviewed_screen_class",
            "reviewed_menu_class", "reviewed_menu_type", "reviewed_container_id", "reviewed_state_id",
            "reviewed_slot_index", "gui_x", "gui_y", "input_runtime_sha256", "observation_projection_sha256");
        if (first == null || pointed || hovered != null ||
                !Protocol.string(args, "observation_projection_sha256").equals(first.sha()) ||
                !Protocol.string(args, "player_uuid").equals(first.actor()) ||
                !Protocol.string(args, "dimension").equals(first.dimension()) ||
                !Protocol.string(args, "profile").equals(first.profile()) ||
                !Protocol.string(args, "input_runtime_sha256").equals(first.runtimeSha()) ||
                !Protocol.string(args, "reviewed_screen_class").equals(first.screenClass()) ||
                !Protocol.string(args, "reviewed_menu_class").equals(first.menuClass()) ||
                !Protocol.string(args, "reviewed_menu_type").equals(first.menuType()) ||
                Math.toIntExact(Protocol.number(args, "reviewed_container_id")) != first.containerId() ||
                Math.toIntExact(Protocol.number(args, "reviewed_state_id")) != first.stateId() ||
                Math.toIntExact(Protocol.number(args, "reviewed_slot_index")) != first.menuSlot() ||
                Math.toIntExact(Protocol.number(args, "gui_x")) != first.guiX() ||
                Math.toIntExact(Protocol.number(args, "gui_y")) != first.guiY() ||
                request.sequence() <= first.sequence())
            throw new IllegalStateException("physical pointer action differs from one reviewed rendered slot");
        ClientInputRuntimeBinding runtime = binding(client, first.profile(), first.runtimeSha(), first.actor(),
            first.dimension(), first.screenClass(), first.menuClass());
        if (client.screen != first.screen() || first.menu() != first.screen().getMenu() ||
                first.menu().containerId != first.containerId() || first.menu().getStateId() != first.stateId() ||
                first.menu().slots.get(first.menuSlot()) != first.slot() ||
                renderGeneration != first.generation() ||
                client.getWindow().getWindow() != first.window() || !runtime.clientSession.equals(first.binding().clientSession))
            throw new IllegalStateException("screen/menu/state/window/render changed before physical pointer move");
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
            .getDefaultConfiguration().getBounds();
        int[] px = new int[1], py = new int[1], w = new int[1], h = new int[1];
        GLFW.glfwGetWindowPos(first.window(), px, py); GLFW.glfwGetWindowSize(first.window(), w, h);
        if (bounds.x != 0 || bounds.y != 0 || bounds.width != client.getWindow().getScreenWidth() ||
                bounds.height != client.getWindow().getScreenHeight() || px[0] != 0 || py[0] != 0 ||
                w[0] != bounds.width || h[0] != bounds.height)
            throw new IllegalStateException("pointer action not bound to full private Xvfb window");
        int rawX = ClientGuiPointer.rawCenter(first.guiX(), client.getWindow().getGuiScaledWidth(),
            client.getWindow().getScreenWidth());
        int rawY = ClientGuiPointer.rawCenter(first.guiY(), client.getWindow().getGuiScaledHeight(),
            client.getWindow().getScreenHeight());
        // Mark consumed BEFORE OS effect; a failed move cannot silently retry the same projection.
        pointed = true; pointGeneration = renderGeneration;
        new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()).mouseMove(rawX, rawY);
        JsonObject out = new JsonObject(); out.addProperty("evidence_role", "authentic_robot_os_pointer_NOT_direct_callback_injection");
        out.addProperty("physical_pointer_move_issued", true);
        out.addProperty("observation_projection_sha256", first.sha());
        out.addProperty("actor_uuid", first.actor()); out.addProperty("dimension", first.dimension());
        out.addProperty("actual_screen_class", first.screenClass()); out.addProperty("actual_menu_class", first.menuClass());
        out.addProperty("actual_menu_type_id", first.menuType()); out.addProperty("actual_container_id", first.containerId());
        out.addProperty("actual_state_id", first.stateId()); out.addProperty("actual_menu_slot_index", first.menuSlot());
        out.addProperty("gui_x", first.guiX()); out.addProperty("gui_y", first.guiY());
        out.addProperty("raw_x", rawX); out.addProperty("raw_y", rawY);
        out.addProperty("actual_glfw_window", first.window());
        out.addProperty("input_runtime_sha256_unverified_by_client", runtime.sha256);
        out.addProperty("client_session_id", runtime.clientSession); out.addProperty("epoch_id", runtime.epochId);
        out.addProperty("client_namespace_pid", runtime.namespacePid);
        out.addProperty("controller_claim_host_xvfb_pid", runtime.hostXvfbPid);
        out.addProperty("controller_claim_host_client_pid", runtime.hostClientPid);
        return out;
    }
    static synchronized void requireFreshHover(Minecraft client, String projectionSha,
        String playerUuid, String dimension, String profile, String runtimeSha) throws Exception {
        if (hovered == null || hoverConsumed || !hovered.sha().equals(projectionSha) ||
                !hovered.actor().equals(playerUuid) || !hovered.dimension().equals(dimension) ||
                !hovered.profile().equals(profile) || !hovered.runtimeSha().equals(runtimeSha) ||
                client.screen != hovered.screen() || hovered.menu() != hovered.screen().getMenu() ||
                hovered.menu().containerId != hovered.containerId() ||
                hovered.menu().getStateId() != hovered.stateId() ||
                hovered.menu().slots.get(hovered.menuSlot()) != hovered.slot() ||
                client.getWindow().getWindow() != hovered.window() ||
                renderedScreen != hovered.screen() || renderedMenu != hovered.menu() ||
                renderGeneration < hovered.generation() || renderGeneration - hovered.generation() > 8 ||
                renderedNanos <= 0 || hovered.originalItemSha() == null || renderedItem == null ||
                renderedItem.issue() != null || renderedItem.generation() != renderGeneration ||
                renderedItem.item() == null || !hash(renderedItem.item()).equals(hovered.originalItemSha()) ||
                renderedHoveredSlot != hovered.menu().slots.get(hovered.menuSlot()) ||
                cursor(client)[0] != hovered.guiX() || cursor(client)[1] != hovered.guiY())
            throw new IllegalStateException("stale original-render hovered slot or altered OS pointer before physical press");
        hoverConsumed = true;
    }
    /** The two original callbacks only OBSERVE an active physical OS press; they never click. */
    static synchronized void originalSlotClick(Minecraft client, AbstractContainerScreen<?> screen,
            Slot slot, int slotId, int button, ClickType type) {
        if (hovered == null || !hoverConsumed) return;
        if (!client.isSameThread() || screen != hovered.screen() || client.screen != screen ||
                screen.getMenu() != hovered.menu() || slot == null || slotId != hovered.menuSlot() ||
                slot.index != slotId || hovered.menu().slots.get(slotId) != slot || button != 0 ||
                type != ClickType.PICKUP || originalSlotClicks != 0 || originalGameModeSends != 0 ||
                !sameActiveTarget(client))
            throw new IllegalStateException("original slotClicked does not match active OS left button and original hovered slot");
        originalSlotClicks++;
    }
    static synchronized void originalGameModeClick(Minecraft client, int containerId, int slotId,
            int button, ClickType type, Player player) {
        if (hovered == null || !hoverConsumed) return;
        if (!client.isSameThread() || client.player != player || player.containerMenu != hovered.menu() ||
                containerId != hovered.containerId() || slotId != hovered.menuSlot() || button != 0 ||
                type != ClickType.PICKUP || originalSlotClicks != 1 || originalGameModeSends != 0 ||
                client.screen != hovered.screen())
            throw new IllegalStateException("original gameMode send INVOKE does not match physical slot click");
        originalGameModeSends++;
    }
    static synchronized JsonObject originalClickEvidence() {
        JsonObject row = clickEvidencePartial();
        if (originalSlotClicks != 1 || originalGameModeSends != 1)
            throw new IllegalStateException("no one-to-one original GUI slotClicked/gameMode send INVOKE for OS click");
        return row;
    }
    static synchronized JsonObject clickEvidencePartial() {
        if (hovered == null || !hoverConsumed)
            throw new IllegalStateException("physical click had no reviewed original-render hover projection");
        JsonObject row = new JsonObject();
        row.addProperty("original_slot_clicked_count", originalSlotClicks);
        row.addProperty("original_game_mode_send_invoke_count_NOT_transport", originalGameModeSends);
        row.addProperty("physical_hover_projection_sha256", hovered.sha());
        row.addProperty("actual_menu_slot_index", hovered.menuSlot());
        row.addProperty("actual_container_id", hovered.containerId());
        row.addProperty("actual_glfw_window", hovered.window());
        return row;
    }
    static synchronized boolean sameActiveTarget(Minecraft client) {
        try {
            return hovered != null && hoverConsumed && client.screen == hovered.screen() &&
                hovered.menu() == hovered.screen().getMenu() &&
                hovered.menu().slots.get(hovered.menuSlot()) == hovered.slot() &&
                hovered.menu().containerId == hovered.containerId() &&
                (originalGameModeSends > 0 || hovered.menu().getStateId() == hovered.stateId()) &&
                client.getWindow().getWindow() == hovered.window() &&
                renderedScreen == hovered.screen() && renderedMenu == hovered.menu() &&
                renderedHoveredSlot == hovered.menu().slots.get(hovered.menuSlot()) &&
                cursor(client)[0] == hovered.guiX() && cursor(client)[1] == hovered.guiY();
        } catch (RuntimeException failure) { return false; }
    }
}
