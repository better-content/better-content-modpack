package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;

/** Client-only, opt-in native mailbox. It never loads in the dedicated server distribution. */
@Mod.EventBusSubscriber(modid = "better_playtest_native_support", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientNativeSupport {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Set<String> BASE_DRIVERS = Set.of("client.snapshot", "client.framebuffer", "client.emi_index",
            "client.emi_hover", "client.emi_page", "client.crosshair", "client.input_key", "client.input_click",
            "client.menu_slot.observe_v1");
    private static final Set<String> AUTHENTIC_DRIVERS = Set.of("client.input_hold", "client.input_release", "client.input_status",
            "client.input_point", "client.menu_slot.point_v1");
    // Capabilities describe static support, not readiness of A's post-launch immutable binding.
    private static final Set<String> DRIVERS = java.util.stream.Stream.concat(BASE_DRIVERS.stream(),
        AUTHENTIC_DRIVERS.stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final int MAX_REQUEST_BYTES = 1_048_576, MAX_OUTPUT_BYTES = 1_048_576;
    private static final String RUN = System.getProperty("bc.playtest.client.runId", "");
    private static final String TOKEN = System.getProperty("bc.playtest.client.token", "");
    private static final String DIRECTORY = System.getProperty("bc.playtest.client.directory", "");
    private static Path directory;
    private static long requestSequence = 1, observationSequence = 1, tick;
    private static boolean initialized, stopped;
    private ClientNativeSupport() {}

    @SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DIRECTORY.isBlank()) return;
        if (stopped) { RobotInputBridge.closeOnExit(); return; }
        final Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("client native mailbox must run on client thread");
        try {
            if (!initialized) initialize();
            if (stopped) return;
            tick++;
            RobotInputBridge.heartbeat(client, tick); // Also runs when no mailbox request is pending.
            if (requestSequence > 100_000) throw new IllegalStateException("client request limit reached");
            Path next = directory.resolve("requests/%012d.json".formatted(requestSequence));
            if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) return;
            if (!Files.isRegularFile(next, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(next) ||
                    Files.size(next) < 1 || Files.size(next) > MAX_REQUEST_BYTES) throw new IllegalStateException("client request unsafe");
            JsonObject row = JsonParser.parseString(Files.readString(next)).getAsJsonObject();
            Protocol.Request request = Protocol.parse(row, RUN, TOKEN, requestSequence);
            // Do not block the client tick or release a duration-based food before first use ends.
            // This exact reviewed release request stays pending while the separate wall/native caps remain active.
            if (request.driver().equals("client.input_release") && RobotInputBridge.deferRelease(request.parameters()))
                return;
            JsonObject started = new JsonObject();
            started.addProperty("native_tick", tick);
            emit(request, "started", started);
            try {
                if (!DRIVERS.contains(request.driver())) throw new IllegalArgumentException("unreviewed client action");
                if (RobotInputBridge.inputPending() && (request.driver().equals("client.input_hold") ||
                        request.driver().equals("client.input_key") || request.driver().equals("client.input_click")))
                    throw new IllegalStateException("callback injection or overlapping physical press forbidden during held input");
                JsonObject result = dispatch(client, request);
                if (request.driver().startsWith("client.input_") ||
                        request.driver().equals("client.menu_slot.point_v1")) recordInput(request, result);
                emit(request, "completed", result);
            } catch (Exception | Error failure) {
                JsonObject failed = new JsonObject();
                failed.addProperty("reason", failure.getClass().getSimpleName() + ": " + failure.getMessage());
                emit(request, "failed", failed);
                stopped = true; // Never skip or retry a failed client request in this epoch.
                RobotInputBridge.closeOnExit();
            }
            requestSequence++;
        } catch (Exception | Error failure) {
            stopped = true;
            try { RobotInputBridge.closeOnExit(); } catch (RuntimeException | Error release) { failure.addSuppressed(release); }
            throw new IllegalStateException("client native observation stopped closed: " + failure.getMessage(), failure);
        }
    }

    /** Called ONLY by B's read-only Mixin at the ORIGINAL AbstractContainerScreen.render HEAD.
     * Capture failures MUST NOT change gameplay/rendering: missing original frame stays unsupported. */
    public static void observedOriginalContainerRenderBegin(Minecraft client, AbstractContainerScreen<?> screen) {
        if (DIRECTORY.isBlank() || stopped || !initialized) return;
        try { ClientMenuSlotObservation.originalRenderBegin(client, screen); }
        catch (RuntimeException failure) { ClientMenuSlotObservation.failSoftRenderCapture(); }
    }

    /** At ORIGINAL renderSlot FIRST Slot.getItem INVOKE_ASSIGN: the in-flight local5, never a second getter.
     * Source whitelist/drag checks in C helper may leave this item unavailable without mutating the GUI. */
    public static void observedOriginalRenderedSlotItem(Minecraft client, AbstractContainerScreen<?> screen,
            Slot slot, int originalX, int originalY, ItemStack originalInFlight,
            boolean clickedSlotClear, boolean draggingItemEmpty, boolean notSplitting,
            boolean notQuickCrafting, boolean quickCraftSlotsEmpty, boolean snapbackItemEmpty) {
        if (DIRECTORY.isBlank() || stopped || !initialized) return;
        try { ClientMenuSlotObservation.originalRenderedSlotItem(client, screen, slot,
            originalX, originalY, originalInFlight, clickedSlotClear, draggingItemEmpty,
            notSplitting, notQuickCrafting, quickCraftSlotsEmpty, snapbackItemEmpty); }
        catch (RuntimeException failure) { ClientMenuSlotObservation.failSoftRenderCapture(); }
    }

    /** Called ONLY by B's read-only Mixin at the ORIGINAL AbstractContainerScreen.render RETURN. */
    public static void observedOriginalContainerRender(Minecraft client, AbstractContainerScreen<?> screen) {
        if (DIRECTORY.isBlank() || stopped || !initialized) return;
        try { ClientMenuSlotObservation.originalRender(client, screen); }
        catch (RuntimeException | Error failure) { stopped = true; RobotInputBridge.closeOnExit(); throw failure; }
    }

    /** Called ONLY by B's read-only Mixin at the ORIGINAL slotClicked HEAD. */
    public static void observedOriginalContainerSlotClick(Minecraft client, AbstractContainerScreen<?> screen,
            Slot slot, int slotId, int button, net.minecraft.world.inventory.ClickType type) {
        if (DIRECTORY.isBlank() || stopped || !initialized) return;
        try { ClientMenuSlotObservation.originalSlotClick(client, screen, slot, slotId, button, type); }
        catch (RuntimeException | Error failure) { stopped = true; RobotInputBridge.closeOnExit(); throw failure; }
    }

    /** Original gameMode send INVOKE only; the server must separately witness the decoded packet. */
    public static void observedOriginalGameModeInventoryClick(Minecraft client, int containerId,
            int slotId, int button, net.minecraft.world.inventory.ClickType type,
            net.minecraft.world.entity.player.Player player) {
        if (DIRECTORY.isBlank() || stopped || !initialized) return;
        try { ClientMenuSlotObservation.originalGameModeClick(client, containerId, slotId, button, type, player); }
        catch (RuntimeException | Error failure) { stopped = true; RobotInputBridge.closeOnExit(); throw failure; }
    }

    public static void beforeRealUseKeybinds(Minecraft client) {
        if (DIRECTORY.isBlank() || stopped || !initialized || !RobotInputBridge.inputPending()) return;
        if (!client.isSameThread()) throw new IllegalStateException("pre-keybind OS release must run on client thread");
        try { RobotInputBridge.beforeRealUseKeybinds(client, tick); }
        catch (RuntimeException | Error failure) {
            stopped = true;
            try { RobotInputBridge.closeOnExit(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            // Aborted epoch, never a completed release/status or server consumption claim.
            throw new IllegalStateException("authentic in-world input key-up not drained before vanilla keybind handling", failure);
        }
    }

    @SubscribeEvent public static void onPhysicalKey(InputEvent.Key event) {
        if (RobotInputBridge.inputPending())
            RobotInputBridge.onKey(Minecraft.getInstance(), event.getKey(), event.getAction());
    }
    @SubscribeEvent public static void onPhysicalMouse(InputEvent.MouseButton.Post event) {
        if (RobotInputBridge.inputPending())
            RobotInputBridge.onMouse(Minecraft.getInstance(), event.getButton(), event.getAction());
    }

    private static void initialize() throws Exception {
        initialized = true;
        if (!RUN.matches("[A-Za-z0-9._:-]{1,128}") || !TOKEN.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("client run/token absent or malformed");
        directory = Path.of(DIRECTORY).toAbsolutePath().normalize();
        if (!directory.isAbsolute() || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(directory) || !Files.isDirectory(directory.resolve("requests"), LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(directory.resolve("requests")) ||
                Files.exists(directory.resolve("capabilities.json"), LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(directory.resolve("observations.jsonl"), LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("client mailbox not a fresh precreated owned directory");
        Runtime.getRuntime().addShutdownHook(new Thread(RobotInputBridge::closeOnExit, "bc-playtest-physical-input-release"));
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("schema", "bc.playtest_capabilities.v2");
        capabilities.addProperty("run_id", RUN);
        capabilities.addProperty("token", TOKEN);
        capabilities.addProperty("source", "client");
        JsonArray actions = new JsonArray(); DRIVERS.stream().sorted().forEach(actions::add);
        capabilities.add("supported_drivers", actions);
        capabilities.addProperty("actual_client", true);
        Path pending = directory.resolve(".capabilities.pending");
        Files.writeString(pending, JSON.toJson(capabilities), StandardOpenOption.CREATE_NEW);
        Files.move(pending, directory.resolve("capabilities.json"), StandardCopyOption.ATOMIC_MOVE);
    }

    private static JsonObject dispatch(Minecraft client, Protocol.Request request) throws Exception {
        JsonObject args = request.parameters();
        return switch (request.driver()) {
            case "client.snapshot" -> { Protocol.only(args); yield snapshot(client); }
            case "client.framebuffer" -> framebuffer(client, args, request.sequence());
            case "client.emi_index" -> {
                Protocol.only(args);
                JsonObject observed = snapshot(client);
                EmiPublicObservation.capture(directory, request.sequence()).entrySet().forEach(e -> observed.add(e.getKey(), e.getValue()));
                yield observed;
            }
            case "client.emi_hover" -> {
                JsonObject observed = snapshot(client);
                EmiGuiObservation.hover(client, args).entrySet().forEach(e -> observed.add(e.getKey(), e.getValue()));
                yield observed;
            }
            case "client.crosshair" -> {
                Protocol.only(args);
                JsonObject observed = snapshot(client);
                ClientCrosshairObservation.capture(client).entrySet().forEach(e -> observed.add(e.getKey(), e.getValue()));
                yield observed;
            }
            case "client.emi_page" -> {
                JsonObject observed = snapshot(client);
                EmiGuiObservation.page(client, args).entrySet().forEach(e -> observed.add(e.getKey(), e.getValue()));
                yield observed;
            }
            case "client.input_key" -> key(client, args);
            case "client.input_click" -> click(client, args);
            case "client.input_point" -> ClientGuiPointer.move(client, request);
            case "client.menu_slot.observe_v1" -> ClientMenuSlotObservation.observe(client, request);
            case "client.menu_slot.point_v1" -> ClientMenuSlotObservation.point(client, request);
            case "client.input_hold" -> {
                if (request.timeout() < Protocol.number(args, "max_ticks") + 20)
                    throw new IllegalArgumentException("authentic input hold exceeds action timeout");
                yield RobotInputBridge.begin(client, request, tick);
            }
            case "client.input_release" -> RobotInputBridge.end(client, args, tick);
            case "client.input_status" -> RobotInputBridge.status(client, args, tick);
            default -> throw new IllegalArgumentException("unregistered client action");
        };
    }

    /** Everything here is read from the actual joined client; no server receipt can impersonate these fields. */
    static String runId() { return RUN; }
    static Path mailboxDirectory() {
        if (!initialized || stopped || directory == null) throw new IllegalStateException("client mailbox not available");
        return directory;
    }
    static long nativeTick() { return tick; } // Called only by observed Forge callbacks on the actual client thread.

    static JsonObject snapshot(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null)
            throw new IllegalStateException("client not in joined world");
        JsonObject data = new JsonObject();
        data.addProperty("captured_at", Instant.now().toString());
        data.addProperty("player_uuid", client.player.getUUID().toString());
        data.addProperty("selected_slot", client.player.getInventory().selected);
        data.addProperty("dimension", client.level.dimension().location().toString());
        data.addProperty("x", client.player.getX()); data.addProperty("y", client.player.getY()); data.addProperty("z", client.player.getZ());
        data.addProperty("yaw", client.player.getYRot()); data.addProperty("pitch", client.player.getXRot());
        data.addProperty("on_ground", client.player.onGround());
        data.addProperty("swimming", client.player.isSwimming()); data.addProperty("in_fluid", client.player.isInWater() || client.player.isInLava());
        data.addProperty("passenger", client.player.isPassenger()); data.addProperty("sprinting", client.player.isSprinting());
        data.addProperty("screen_class", client.screen == null ? null : client.screen.getClass().getName());
        data.addProperty("screen_title", client.screen == null ? null : client.screen.getTitle().getString());
        data.addProperty("menu_type", client.screen instanceof AbstractContainerScreen<?> menu ? menu.getMenu().getClass().getName() : null);
        JsonArray slots = new JsonArray();
        if (client.screen instanceof AbstractContainerScreen<?> menu) {
            if (menu.getMenu().slots.size() > 512) throw new IllegalStateException("oversized client slot roster");
            for (Slot slot : menu.getMenu().slots) slots.add(stack(slot.getItem(), slot.index));
        }
        data.add("menu_slots", slots);
        JsonArray inventory = new JsonArray();
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++)
            inventory.add(stack(client.player.getInventory().getItem(i), i));
        data.add("inventory", inventory);
        data.addProperty("screen_width", client.getWindow().getScreenWidth());
        data.addProperty("screen_height", client.getWindow().getScreenHeight());
        data.addProperty("gui_scale", client.getWindow().getGuiScale());
        return data;
    }

    /** Passive menu click receipt MUST NOT walk result slots: TCon LazyResultSlot.getItem
     * can run matches/assemble and change its cache merely by being read. */
    static JsonObject snapshotWithoutMenuItems(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null ||
                !(client.screen instanceof AbstractContainerScreen<?> menu))
            throw new IllegalStateException("physical menu input requires actual joined container");
        JsonObject data = new JsonObject();
        data.addProperty("captured_at", Instant.now().toString());
        data.addProperty("player_uuid", client.player.getUUID().toString());
        data.addProperty("dimension", client.level.dimension().location().toString());
        data.addProperty("screen_class", menu.getClass().getName());
        data.addProperty("menu_class", menu.getMenu().getClass().getName());
        data.addProperty("container_id", menu.getMenu().containerId);
        data.addProperty("state_id", menu.getMenu().getStateId());
        data.addProperty("selected_slot", client.player.getInventory().selected);
        data.addProperty("screen_width", client.getWindow().getScreenWidth());
        data.addProperty("screen_height", client.getWindow().getScreenHeight());
        data.addProperty("gui_scale", client.getWindow().getGuiScale());
        data.addProperty("menu_item_snapshot_omitted", true);
        return data;
    }

    private static JsonObject stack(ItemStack item, int slot) {
        JsonObject row = new JsonObject(); row.addProperty("slot", slot);
        row.addProperty("item_id", item.isEmpty() ? "minecraft:air" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()).toString());
        row.addProperty("count", item.getCount());
        String nbt = item.save(new net.minecraft.nbt.CompoundTag()).toString();
        row.addProperty("stack_nbt", nbt);
        row.addProperty("stack_snbt_sha256", sha256(nbt.getBytes(StandardCharsets.UTF_8)));
        return row;
    }

    private static JsonObject framebuffer(Minecraft client, JsonObject args, long seq) throws Exception {
        Protocol.only(args, "profile");
        String profile = Protocol.string(args, "profile");
        int width, height, scale;
        if (profile.equals("1280x720@2")) { width = 1280; height = 720; scale = 2; }
        else if (profile.equals("1920x1080@3")) { width = 1920; height = 1080; scale = 3; }
        else throw new IllegalArgumentException("unreviewed GUI capture profile");
        if (client.getWindow().getScreenWidth() != width || client.getWindow().getScreenHeight() != height ||
                client.getWindow().getGuiScale() != scale || client.screen == null || client.player == null)
            throw new IllegalStateException("window/gui scale or joined screen differs from requested profile");
        Path image = directory.resolve("frame-%012d.png".formatted(seq));
        if (Files.exists(image, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("frame already exists");
        try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            if (pixels.getWidth() != width || pixels.getHeight() != height) throw new IllegalStateException("framebuffer size differs from window");
            pixels.writeToFile(image);
        }
        if (!Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS) || Files.size(image) < 100 || Files.size(image) > 64_000_000)
            throw new IllegalStateException("unreadable or oversized real framebuffer PNG");
        JsonObject data = snapshot(client);
        data.addProperty("image_path", image.toString());
        data.addProperty("image_sha256", sha256(Files.readAllBytes(image)));
        data.addProperty("image_bytes", Files.size(image));
        data.addProperty("capture_method", "Minecraft Screenshot.takeScreenshot(mainRenderTarget)");
        return data;
    }

    private static JsonObject key(Minecraft client, JsonObject args) {
        Protocol.only(args, "key", "modifiers");
        int key = Math.toIntExact(Protocol.number(args, "key"));
        int mods = Math.toIntExact(Protocol.number(args, "modifiers"));
        boolean openInventory = key == GLFW.GLFW_KEY_E && client.screen == null;
        if ((!openInventory && (client.screen == null || key < 256 || key > 265)) || mods != 0)
            throw new IllegalArgumentException("client key must be reviewed inventory-open or visible-GUI navigation callback");
        String before = client.screen == null ? "no_screen" : client.screen.getClass().getName();
        client.keyboardHandler.keyPress(client.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, mods);
        client.keyboardHandler.keyPress(client.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, mods);
        JsonObject result = snapshot(client);
        result.addProperty("callback", "KeyboardHandler.keyPress");
        result.addProperty("evidence_role", "diagnostic_direct_callback_injection_NOT_authentic_OS_input");
        result.addProperty("before_screen", before);
        result.addProperty("key", key);
        return result;
    }

    private static JsonObject click(Minecraft client, JsonObject args) {
        Protocol.only(args, "x", "y", "button");
        int x = Math.toIntExact(Protocol.number(args, "x"));
        int y = Math.toIntExact(Protocol.number(args, "y"));
        int button = Math.toIntExact(Protocol.number(args, "button"));
        if (client.screen == null || x < 0 || x >= client.getWindow().getGuiScaledWidth() ||
                y < 0 || y >= client.getWindow().getGuiScaledHeight() || button < 0 || button > 1)
            throw new IllegalArgumentException("click outside visible GUI or unsupported button");
        Screen screen = client.screen;
        boolean handled = screen.mouseClicked(x, y, button); // same UI event callback as MouseHandler dispatch.
        screen.mouseReleased(x, y, button);
        JsonObject result = snapshot(client);
        result.addProperty("callback", "Screen.mouseClicked/mouseReleased");
        result.addProperty("evidence_role", "diagnostic_direct_callback_injection_NOT_authentic_OS_input");
        result.addProperty("before_screen", screen.getClass().getName());
        result.addProperty("handled", handled);
        result.addProperty("x", x); result.addProperty("y", y); result.addProperty("button", button);
        return result;
    }

    private static void recordInput(Protocol.Request request, JsonObject result) throws Exception {
        Path log = directory.resolve("input-events.jsonl");
        if (Files.exists(log, LinkOption.NOFOLLOW_LINKS) &&
                (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(log)))
            throw new IllegalStateException("client input log was replaced or linked");
        JsonObject row = new JsonObject();
        row.addProperty("schema", "bc.playtest_client_input.v1");
        row.addProperty("run_id", RUN); row.addProperty("token", TOKEN);
        row.addProperty("request_sequence", request.sequence()); row.addProperty("tick", tick);
        row.addProperty("action", request.driver()); row.addProperty("time", Instant.now().toString());
        row.add("parameters", request.parameters()); row.add("observed_result", result.deepCopy());
        Files.writeString(log, JSON.toJson(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        result.addProperty("input_log", log.toString());
        result.addProperty("input_log_sha256", sha256(Files.readAllBytes(log)));
    }

    private static void emit(Protocol.Request request, String kind, JsonObject payload) throws Exception {
        JsonObject row = new JsonObject();
        row.addProperty("schema", "bc.playtest_observation.v2"); row.addProperty("run_id", RUN);
        row.addProperty("case_id", request.caseId()); row.addProperty("token", TOKEN);
        row.addProperty("sequence", observationSequence++); row.addProperty("request_sequence", request.sequence());
        row.addProperty("driver", request.driver()); row.addProperty("source", "client");
        row.addProperty("kind", kind); row.addProperty("tick", tick); row.add("payload", payload);
        String line = JSON.toJson(row) + "\n";
        if (line.getBytes(StandardCharsets.UTF_8).length > MAX_OUTPUT_BYTES) throw new IllegalStateException("client observation too large");
        Path output = directory.resolve("observations.jsonl");
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) &&
                (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output)))
            throw new IllegalStateException("client observation log was replaced or linked");
        Files.writeString(output, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append("%02x".formatted(value & 255));
            return hex.toString();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
