package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Real OS events addressed only to the harness-owned, focused private Xvfb GLFW window. */
final class RobotInputBridge {
    private static RobotInputBridge active;
    private static final HeldInputIdLedger usedHoldIds = new HeldInputIdLedger();
    private static final ScheduledExecutorService WALL_WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "bc-client-input-wall-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private final ClientInputRuntimeBinding binding;
    private final Path mailbox;
    private final String profile, playerUuid, dimension, holdId;
    private final long glfwWindow, x11Window, clientPid;
    private final HeldInputState state;
    private final KeyMapping mapping;
    private final Robot robot;
    private ScheduledFuture<?> watchdog;
    private JsonObject onboardingKeyPress; // First real K callback context, before Better Spawns next-tick send.
    private final ClientUseReleaseGate useGate = new ClientUseReleaseGate();
    private boolean autoReleased;

    private RobotInputBridge(Minecraft client, Protocol.Request request, long tick, String profile,
            String playerUuid, String dimension, String holdId, HeldInputState.Kind kind, KeyMapping mapping,
            int glfwCode, int awtCode, int maxTicks, ClientInputRuntimeBinding binding, Path mailbox) throws AWTException {
        this.binding = binding; this.mailbox = mailbox;
        this.profile = profile; this.playerUuid = playerUuid; this.dimension = dimension; this.holdId = holdId;
        this.glfwWindow = client.getWindow().getWindow();
        this.x11Window = GLFWNativeX11.glfwGetX11Window(glfwWindow);
        this.clientPid = ProcessHandle.current().pid();
        this.mapping = mapping;
        validateWindow(client, true);
        this.robot = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice());
        robot.setAutoDelay(0); robot.setAutoWaitForIdle(false);
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
            .getDefaultConfiguration().getBounds();
        if (bounds.x != 0 || bounds.y != 0 || bounds.width != client.getWindow().getScreenWidth() ||
                bounds.height != client.getWindow().getScreenHeight())
            throw new IllegalStateException("AWT Robot is not bound to the private Xvfb framebuffer");
        int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
        GLFW.glfwGetWindowPos(glfwWindow, x, y); GLFW.glfwGetWindowSize(glfwWindow, w, h);
        if (x[0] < 0 || y[0] < 0 || w[0] != bounds.width || h[0] != bounds.height ||
                x[0] + w[0] > bounds.width || y[0] + h[0] > bounds.height)
            throw new IllegalStateException("focused GLFW window does not occupy the private display");
        // Center of the verified owned window; never click or press on another display/window.
        robot.mouseMove(x[0] + w[0] / 2, y[0] + h[0] / 2);
        validateWindow(client, true); // Focus can change while AWT moves the pointer.
        HeldInputState.Device input = new HeldInputState.Device() {
            public void press() {
                if (kind == HeldInputState.Kind.USE_MOUSE) robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                else if (kind == HeldInputState.Kind.ONBOARDING_CHORD) {
                    robot.keyPress(KeyEvent.VK_SHIFT); robot.keyPress(awtCode);
                } else robot.keyPress(awtCode);
            }
            public void release() {
                if (kind == HeldInputState.Kind.USE_MOUSE) robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                else if (kind == HeldInputState.Kind.ONBOARDING_CHORD) {
                    try { robot.keyRelease(awtCode); } finally { robot.keyRelease(KeyEvent.VK_SHIFT); }
                } else robot.keyRelease(awtCode);
            }
        };
        this.state = new HeldInputState(kind, request.sequence(), glfwWindow, glfwCode, tick, maxTicks, input);
    }

    // Host Xvfb PID/birth lives in a sibling PID namespace: only A's host controller can inspect it.

    private static String validatedProfile(Minecraft client, String profile) {
        int width, height, scale;
        if (profile.equals("1280x720@2")) { width = 1280; height = 720; scale = 2; }
        else if (profile.equals("1920x1080@3")) { width = 1920; height = 1080; scale = 3; }
        else throw new IllegalArgumentException("unreviewed authentic input profile");
        if (client.getWindow().getScreenWidth() != width || client.getWindow().getScreenHeight() != height ||
                client.getWindow().getGuiScale() != scale)
            throw new IllegalStateException("authentic input profile differs from joined GLFW window");
        return profile;
    }
    private static boolean[] onboardingState() {
        try {
            Class<?> type = Class.forName("com.bettercontent.betterspawns.client.ClassSelectionState");
            Object instance = type.getField("INSTANCE").get(null);
            boolean active = (boolean) type.getMethod("getActiveInCurrentWorld").invoke(instance);
            boolean needed = (boolean) type.getMethod("getSelectionRequired").invoke(instance);
            Object mode = type.getMethod("getSelectionMode").invoke(instance);
            return new boolean[] { active, needed, mode instanceof Enum<?> value && value.name().equals("NONE") };
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("production Better Spawns onboarding state unavailable", failure);
        }
    }
    private static int awtKeyboardCode(int glfwCode) {
        if (glfwCode >= GLFW.GLFW_KEY_A && glfwCode <= GLFW.GLFW_KEY_Z) return KeyEvent.VK_A + glfwCode - GLFW.GLFW_KEY_A;
        if (glfwCode >= GLFW.GLFW_KEY_0 && glfwCode <= GLFW.GLFW_KEY_9) return KeyEvent.VK_0 + glfwCode - GLFW.GLFW_KEY_0;
        if (glfwCode == GLFW.GLFW_KEY_SPACE) return KeyEvent.VK_SPACE;
        throw new IllegalArgumentException("actual client key binding has no reviewed AWT mapping");
    }
    private static byte[] boundedAuthority(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) ||
                !Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).getName().equals(System.getProperty("user.name")))
            throw new IOException("private Xauthority is linked or foreign-owned");
        try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = stream.readNBytes(4097);
            if (content.length < 1 || content.length > 4096) throw new IOException("Xauthority exceeds review bound");
            return content;
        }
    }
    private boolean localBindingStillValid() {
        try {
            String authority = System.getenv("XAUTHORITY");
            if (authority == null || !authority.equals(binding.authorityPath) ||
                    !binding.display.equals(System.getenv("DISPLAY")) ||
                    ProcessHandle.current().pid() != binding.namespacePid) return false;
            ClientInputRuntimeBinding captured = ClientInputRuntimeBinding.capture(mailbox, binding.sha256,
                binding.clientSession, profile, binding.display, authority, binding.namespacePid);
            return captured.sha256.equals(binding.sha256) &&
                ClientNativeSupport.sha256(boundedAuthority(Path.of(authority))).equals(binding.authoritySha);
        } catch (Exception failure) { return false; }
    }
    private void validateWindow(Minecraft client, boolean requireFocus) {
        if (client.player == null || client.level == null || client.getConnection() == null ||
            !client.player.getUUID().toString().equals(playerUuid) ||
            !client.level.dimension().location().toString().equals(dimension) ||
            client.getWindow().getWindow() != glfwWindow || GLFWNativeX11.glfwGetX11Display() == 0 ||
            GLFWNativeX11.glfwGetX11Window(glfwWindow) != x11Window || x11Window == 0 ||
            GLFW.glfwGetWindowAttrib(glfwWindow, GLFW.GLFW_VISIBLE) == 0 ||
            (requireFocus && GLFW.glfwGetWindowAttrib(glfwWindow, GLFW.GLFW_FOCUSED) == 0) ||
            ProcessHandle.current().pid() != clientPid || !localBindingStillValid())
            throw new IllegalStateException("owned client process/X11 GLFW window/focus/actor changed");
        validatedProfile(client, profile);
    }
    private boolean physicallyDown(Minecraft client) {
        return mapping.isDown() && (state.kind != HeldInputState.Kind.ONBOARDING_CHORD || Screen.hasShiftDown());
    }
    private JsonObject evidence(Minecraft client) {
        JsonObject row = ClientNativeSupport.snapshot(client);
        row.addProperty("evidence_role", "authentic_robot_os_input_NOT_direct_callback_injection");
        row.addProperty("profile", profile); row.addProperty("kind", state.kind.name().toLowerCase(java.util.Locale.ROOT));
        row.addProperty("client_namespace_pid", clientPid);
        row.addProperty("controller_claim_host_xvfb_pid", binding.hostXvfbPid);
        row.addProperty("controller_claim_host_xvfb_birth_epoch_millis", binding.hostXvfbBirthMillis);
        row.addProperty("controller_claim_host_client_pid", binding.hostClientPid);
        row.addProperty("controller_claim_host_client_birth_epoch_millis", binding.hostClientBirthMillis);
        row.addProperty("input_runtime_sha256", binding.sha256);
        row.addProperty("client_session_id", binding.clientSession);
        row.addProperty("epoch_id", binding.epochId); row.addProperty("purpose", binding.purpose);
        row.addProperty("glfw_window", glfwWindow); row.addProperty("x11_window", x11Window);
        row.addProperty("display", binding.display); row.addProperty("xauthority_sha256", binding.authoritySha);
        row.addProperty("hold_sequence", state.sequence); row.addProperty("hold_id", holdId);
        row.addProperty("held_state_ticks", state.heldStateTicks());
        row.addProperty("press_callback", state.pressCallback()); row.addProperty("release_callback", state.releaseCallback());
        row.addProperty("modifier_press_callback", state.modifierPressCallback());
        row.addProperty("modifier_release_callback", state.modifierReleaseCallback());
        row.addProperty("physical_input_down", physicallyDown(client));
        row.addProperty("held_max_ticks", state.maxTicks);
        row.addProperty("client_use_started", useGate.started());
        row.addProperty("client_use_ended", useGate.ended());
        row.addProperty("client_use_start_tick", useGate.startTick());
        row.addProperty("client_use_end_tick", useGate.endTick());
        row.addProperty("auto_released_on_first_client_use_end", autoReleased);
        row.addProperty("client_spectator", client.player.isSpectator());
        if (state.kind == HeldInputState.Kind.ONBOARDING_CHORD) {
            row.add("onboarding_key_press_context", onboardingKeyPress == null ?
                com.google.gson.JsonNull.INSTANCE : onboardingKeyPress.deepCopy());
            boolean[] onboarding = onboardingState();
            row.addProperty("onboarding_active", onboarding[0]);
            row.addProperty("onboarding_selection_required", onboarding[1]);
            row.addProperty("onboarding_mode_spawn_only", onboarding[2]);
        }
        return row;
    }
    static synchronized JsonObject begin(Minecraft client, Protocol.Request request, long tick) throws Exception {
        if (active != null) throw new IllegalStateException("prior held input not released and independently observed");
        if (client.player == null || client.level == null || client.getConnection() == null)
            throw new IllegalStateException("physical input requires an actual joined client");
        JsonObject args = request.parameters();
        Protocol.only(args, "profile", "player_uuid", "dimension", "kind", "max_ticks", "hold_id", "input_runtime_sha256");
        String profile = validatedProfile(client, Protocol.string(args, "profile"));
        ClientInputRuntimeBinding binding = ClientInputRuntimeBinding.capture(ClientNativeSupport.mailboxDirectory(),
            Protocol.string(args, "input_runtime_sha256"), request.run(), profile,
            System.getenv("DISPLAY"), System.getenv("XAUTHORITY"), ProcessHandle.current().pid());
        if (!ClientNativeSupport.sha256(boundedAuthority(Path.of(binding.authorityPath))).equals(binding.authoritySha))
            throw new IllegalStateException("sandbox-local Xauthority digest differs from controller record");
        String playerUuid = Protocol.string(args, "player_uuid"), dimension = Protocol.string(args, "dimension");
        String holdId = Protocol.string(args, "hold_id");
        usedHoldIds.claim(holdId); // Even an invalid/rejected attempt cannot reuse an OS-input ID.
        String kindName = Protocol.string(args, "kind");
        int ticks = Math.toIntExact(Protocol.number(args, "max_ticks"));
        KeyMapping mapping;
        if (kindName.equals("use") && client.screen == null && !client.player.isSpectator() &&
                !client.player.isUsingItem())
            mapping = client.options.keyUse;
        else if (kindName.equals("inventory") && client.screen == null && !client.player.isSpectator())
            mapping = client.options.keyInventory;
        else if (kindName.equals("onboarding") && client.screen == null && client.player.isSpectator()) {
            boolean[] onboarding = onboardingState();
            if (!onboarding[0] || !onboarding[1] || !onboarding[2])
                throw new IllegalStateException("Better Spawns first-login spawn-only selection not pending");
            var matches = Arrays.stream(client.options.keyMappings).filter(k ->
                k.getName().equals("key.better_spawns.open_menu")).toList();
            if (matches.size() != 1) throw new IllegalStateException("production Better Spawns onboarding binding absent/ambiguous");
            mapping = matches.get(0);
        } else throw new IllegalArgumentException("held input requires reviewed joined in-world use/inventory/onboarding context");
        if (mapping.isDown() || (kindName.equals("onboarding") && Screen.hasShiftDown()))
            throw new IllegalStateException("prior key/button state not physically released");
        InputConstants.Key bound = mapping.getKey();
        HeldInputState.Kind kind;
        int glfwCode, awtCode;
        if (kindName.equals("onboarding") && bound.getType() == InputConstants.Type.KEYSYM &&
                bound.getValue() == GLFW.GLFW_KEY_K &&
                client.options.keyShift.getKey().getType() == InputConstants.Type.KEYSYM &&
                client.options.keyShift.getKey().getValue() == GLFW.GLFW_KEY_LEFT_SHIFT &&
                mapping.getKeyModifier().name().equals("NONE") && ticks <= 40) {
            kind = HeldInputState.Kind.ONBOARDING_CHORD; glfwCode = GLFW.GLFW_KEY_K; awtCode = KeyEvent.VK_K;
        } else if (kindName.equals("use") && bound.getType() == InputConstants.Type.MOUSE &&
                bound.getValue() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            kind = HeldInputState.Kind.USE_MOUSE; glfwCode = GLFW.GLFW_MOUSE_BUTTON_RIGHT; awtCode = InputEvent.BUTTON3_DOWN_MASK;
        } else if ((kindName.equals("use") || kindName.equals("inventory")) &&
                bound.getType() == InputConstants.Type.KEYSYM) {
            kind = kindName.equals("use") ? HeldInputState.Kind.USE_KEY : HeldInputState.Kind.INVENTORY_KEY;
            glfwCode = bound.getValue(); awtCode = awtKeyboardCode(glfwCode);
        } else throw new IllegalArgumentException("unreviewed actual client input binding");
        RobotInputBridge bridge = new RobotInputBridge(client, request, tick, profile, playerUuid, dimension,
            holdId, kind, mapping, glfwCode, awtCode, ticks, binding, ClientNativeSupport.mailboxDirectory());
        bridge.validateWindow(client, true);
        bridge.state.begin(); active = bridge;
        // Independent wall cap even if the client tick/mailbox freezes; never report this release as qualified.
        bridge.watchdog = WALL_WATCHDOG.schedule(() -> {
            try { bridge.state.close(); } catch (RuntimeException | Error ignored) {
                // The controller also has a hard wall deadline and owns Xvfb process cleanup.
            }
        }, ticks * 100L, TimeUnit.MILLISECONDS);
        JsonObject receipt = bridge.evidence(client);
        receipt.addProperty("physical_press_issued", true);
        return receipt;
    }
    static synchronized void onKey(Minecraft client, int key, int action) {
        if (active == null || active.state.kind == HeldInputState.Kind.USE_MOUSE ||
                client.getWindow().getWindow() != active.glfwWindow) return;
        try {
            active.validateWindow(client, true);
            active.state.callback(active.glfwWindow, action, key);
            if (active.state.kind == HeldInputState.Kind.ONBOARDING_CHORD) {
                if (key == GLFW.GLFW_KEY_LEFT_SHIFT) active.state.modifierCallback(action);
                if (key == GLFW.GLFW_KEY_K && action == GLFW.GLFW_PRESS && active.onboardingKeyPress == null) {
                    JsonObject context = new JsonObject();
                    var block = client.player.blockPosition();
                    context.addProperty("dimension", client.level.dimension().location().toString());
                    context.addProperty("block_x", block.getX()); context.addProperty("block_y", block.getY());
                    context.addProperty("block_z", block.getZ());
                    context.addProperty("spectator", client.player.isSpectator());
                    context.addProperty("modifier_down", Screen.hasShiftDown());
                    context.addProperty("native_tick", ClientNativeSupport.nativeTick());
                    active.onboardingKeyPress = context;
                }
            }
        } catch (RuntimeException | Error failure) { closeOnExit(); throw failure; }
    }
    static synchronized void onMouse(Minecraft client, int button, int action) {
        if (active == null || active.state.kind != HeldInputState.Kind.USE_MOUSE ||
                client.getWindow().getWindow() != active.glfwWindow) return;
        try {
            active.validateWindow(client, true);
            active.state.callback(active.glfwWindow, action, button);
        } catch (RuntimeException | Error failure) { closeOnExit(); throw failure; }
    }
    static synchronized void heartbeat(Minecraft client, long tick) {
        if (active == null) return;
        if (active.state.isPressed()) {
            boolean valid = true;
            try { active.validateWindow(client, true); } catch (RuntimeException failure) { valid = false; }
            active.state.observe(tick, client.player != null && client.getConnection() != null,
                valid, (client.screen == null && (active.state.kind != HeldInputState.Kind.USE_MOUSE ||
                    client.mouseHandler.isMouseGrabbed())) || active.state.kind == HeldInputState.Kind.INVENTORY_KEY,
                valid && active.physicallyDown(client));
            if (active.state.kind == HeldInputState.Kind.USE_MOUSE || active.state.kind == HeldInputState.Kind.USE_KEY) {
                // Client-local use state is a release trigger, NOT proof of server consumption.
                // B's separately armed passive packet/Start/Finish/final observer decides completion.
                if (active.useGate.observe(tick, active.state.heldStateTicks() > 0, client.player.isUsingItem())) {
                    active.state.release(active.state.sequence, tick);
                    active.autoReleased = true;
                }
            }
        } else if (active.state.releaseTick() < 0) {
            throw new IllegalStateException("wall watchdog released physical input without qualifying callbacks");
        } else if (tick - active.state.releaseTick() > 10) {
            if (active.watchdog != null) active.watchdog.cancel(false);
            active = null;
            throw new IllegalStateException("physical release status not observed within bounded deadline");
        }
    }
    /** A prepublished release request waits on client ticks until genuine use ends; never block the client thread. */
    static synchronized boolean deferRelease(JsonObject args) {
        Protocol.only(args, "hold_id", "player_uuid", "dimension", "input_runtime_sha256");
        if (active == null) throw new IllegalStateException("no prior held input for deferred use release");
        if (!Protocol.string(args, "hold_id").equals(active.holdId) ||
                !Protocol.string(args, "player_uuid").equals(active.playerUuid) ||
                !Protocol.string(args, "dimension").equals(active.dimension) ||
                !Protocol.string(args, "input_runtime_sha256").equals(active.binding.sha256)) {
            closeOnExit();
            throw new IllegalStateException("foreign deferred physical release request");
        }
        return (active.state.kind == HeldInputState.Kind.USE_MOUSE ||
            active.state.kind == HeldInputState.Kind.USE_KEY) && active.state.isPressed();
    }
    static synchronized JsonObject end(Minecraft client, JsonObject args, long tick) {
        Protocol.only(args, "hold_id", "player_uuid", "dimension", "input_runtime_sha256");
        if (active == null) throw new IllegalStateException("no active physical input to release");
        try {
            active.validateWindow(client, true);
            if (!Protocol.string(args, "player_uuid").equals(active.playerUuid) ||
                    !Protocol.string(args, "dimension").equals(active.dimension) ||
                    !Protocol.string(args, "hold_id").equals(active.holdId) ||
                    !Protocol.string(args, "input_runtime_sha256").equals(active.binding.sha256))
                throw new IllegalStateException("foreign player/dimension/hold release");
            if (active.state.kind == HeldInputState.Kind.USE_MOUSE || active.state.kind == HeldInputState.Kind.USE_KEY) {
                if (!active.autoReleased || !active.useGate.started() || !active.useGate.ended() ||
                        active.useGate.endTick() <= active.useGate.startTick() || active.state.releaseTick() < 0)
                    throw new IllegalStateException("use release not triggered by first actual client use end");
            } else {
                if (active.state.kind == HeldInputState.Kind.ONBOARDING_CHORD &&
                        (active.onboardingKeyPress == null ||
                         !active.onboardingKeyPress.get("spectator").getAsBoolean() ||
                         !active.onboardingKeyPress.get("modifier_down").getAsBoolean() ||
                         !active.dimension.equals(active.onboardingKeyPress.get("dimension").getAsString())))
                    throw new IllegalStateException("onboarding key callback lacked real spectator Shift+K actor context");
                active.state.release(active.state.sequence, tick);
            }
            JsonObject row = active.evidence(client);
            row.addProperty("physical_release_issued", true);
            return row;
        } catch (RuntimeException failure) { active.state.close(); throw failure; }
    }
    static synchronized JsonObject status(Minecraft client, JsonObject args, long tick) {
        Protocol.only(args, "hold_id", "player_uuid", "dimension", "input_runtime_sha256");
        if (active == null) throw new IllegalStateException("no released physical input to adjudicate");
        try {
            active.validateWindow(client, true);
            if (!Protocol.string(args, "player_uuid").equals(active.playerUuid) ||
                !Protocol.string(args, "dimension").equals(active.dimension) ||
                !Protocol.string(args, "hold_id").equals(active.holdId) ||
                !Protocol.string(args, "input_runtime_sha256").equals(active.binding.sha256))
                throw new IllegalStateException("foreign physical input status");
            active.state.verifyReleased(tick, true, true, active.physicallyDown(client));
            JsonObject row = active.evidence(client);
            row.addProperty("physical_release_verified", true);
            if (active.watchdog != null) active.watchdog.cancel(false);
            active = null;
            return row;
        } catch (RuntimeException failure) { active.state.close(); throw failure; }
    }
    static synchronized boolean inputPending() { return active != null; }
    static synchronized void closeOnExit() {
        if (active != null) {
            try { active.state.close(); }
            finally {
                if (active.watchdog != null) active.watchdog.cancel(false);
                active = null;
            }
        }
    }
}
