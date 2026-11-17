package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.GameType;
import com.google.gson.JsonArray;
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
    private final ClientUseKeyUpGate keyUpGate = new ClientUseKeyUpGate();
    private final ClientWalkSafety walkSafety;
    private final JsonArray walkSamples = new JsonArray();
    private JsonObject emiHover; // Frozen read-only actual rendered sidebar target, before OS press.
    private final ClientAttackReleaseGate attackGate;
    private final JsonArray attackSamples = new JsonArray();
    private final String attackBoundTargetSha256;
    private boolean attackAutoReleased;
    private boolean autoReleased;

    private RobotInputBridge(Minecraft client, Protocol.Request request, long tick, String profile,
            String playerUuid, String dimension, String holdId, HeldInputState.Kind kind, KeyMapping mapping,
            int glfwCode, int awtCode, int maxTicks, ClientInputRuntimeBinding binding, Path mailbox,
            String attackProjection, String attackBoundTargetSha256) throws AWTException {
        ClientAwtHeadlessBootstrap.requireReady(); // Before Robot/GraphicsEnvironment or any OS press.
        this.binding = binding; this.mailbox = mailbox;
        this.profile = profile; this.playerUuid = playerUuid; this.dimension = dimension; this.holdId = holdId;
        this.glfwWindow = client.getWindow().getWindow();
        this.x11Window = GLFWNativeX11.glfwGetX11Window(glfwWindow);
        this.clientPid = ProcessHandle.current().pid();
        this.mapping = mapping;
        this.attackGate = kind == HeldInputState.Kind.ATTACK_MOUSE ? new ClientAttackReleaseGate(attackProjection) : null;
        this.attackBoundTargetSha256 = attackBoundTargetSha256;
        this.walkSafety = kind == HeldInputState.Kind.WALK_FORWARD ?
            new ClientWalkSafety(client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), maxTicks) : null;
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
        if (kind != HeldInputState.Kind.WALK_FORWARD && kind != HeldInputState.Kind.ATTACK_MOUSE &&
                kind != HeldInputState.Kind.EMI_MOUSE_LEFT && kind != HeldInputState.Kind.EMI_MOUSE_RIGHT &&
                kind != HeldInputState.Kind.CONTAINER_SLOT_LEFT)
            robot.mouseMove(x[0] + w[0] / 2, y[0] + h[0] / 2);
        // A walking route never moves the pointer or mutates yaw, even through a physical mouse warp.
        validateWindow(client, true); // Focus can change while AWT moves the pointer.
        HeldInputState.Device input = new HeldInputState.Device() {
            public void press() {
                if (kind == HeldInputState.Kind.ATTACK_MOUSE || kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT)
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                else if (kind == HeldInputState.Kind.USE_MOUSE) robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                else if (kind == HeldInputState.Kind.EMI_MOUSE_LEFT) robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                else if (kind == HeldInputState.Kind.EMI_MOUSE_RIGHT) robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                else if (kind == HeldInputState.Kind.ONBOARDING_CHORD) {
                    robot.keyPress(KeyEvent.VK_SHIFT); robot.keyPress(awtCode);
                } else robot.keyPress(awtCode);
            }
            public void release() {
                if (kind == HeldInputState.Kind.ATTACK_MOUSE || kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT)
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                else if (kind == HeldInputState.Kind.USE_MOUSE) robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                else if (kind == HeldInputState.Kind.EMI_MOUSE_LEFT) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                else if (kind == HeldInputState.Kind.EMI_MOUSE_RIGHT) robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                else if (kind == HeldInputState.Kind.ONBOARDING_CHORD) {
                    try { robot.keyRelease(awtCode); } finally { robot.keyRelease(KeyEvent.VK_SHIFT); }
                } else robot.keyRelease(awtCode);
            }
        };
        this.state = new HeldInputState(kind, request.sequence(), glfwWindow, glfwCode, tick, maxTicks, input);
    }

    // Host Xvfb PID/birth lives in a sibling PID namespace: only A's host controller can inspect it.

    static String validatedProfile(Minecraft client, String profile) {
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
    static byte[] boundedAuthority(Path file) throws IOException {
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
        if (state.kind == HeldInputState.Kind.EMI_MOUSE_LEFT || state.kind == HeldInputState.Kind.EMI_MOUSE_RIGHT ||
                state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT)
            return GLFW.glfwGetMouseButton(glfwWindow, glfwCode()) == GLFW.GLFW_PRESS;
        if (state.kind == HeldInputState.Kind.ATTACK_MOUSE)
            return mapping.isDown() && GLFW.glfwGetMouseButton(glfwWindow, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        return mapping.isDown() && (state.kind != HeldInputState.Kind.ONBOARDING_CHORD || Screen.hasShiftDown());
    }
    private int glfwCode() { return state.kind == HeldInputState.Kind.EMI_MOUSE_LEFT ||
        state.kind == HeldInputState.Kind.ATTACK_MOUSE || state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT ?
        GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT; }
    private static boolean emiScreen(Minecraft client) {
        return client.screen != null && java.util.Set.of(
            "net.minecraft.client.gui.screens.inventory.InventoryScreen",
            "dev.emi.emi.screen.RecipeScreen").contains(client.screen.getClass().getName());
    }
    private void observeWalk(Minecraft client, long tick) {
        validateWindow(client, true);
        if (walkSafety == null || client.player == null || walkSamples.size() >= 61)
            throw new IllegalStateException("walking evidence absent or over bounded sample cap");
        var actor = client.player;
        boolean survival = client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SURVIVAL;
        boolean inFluid = actor.isInWater() || actor.isInLava();
        boolean jumping = client.options.keyJump.isDown();
        boolean strafing = client.options.keyLeft.isDown() || client.options.keyRight.isDown();
        boolean backing = client.options.keyDown.isDown();
        boolean interacting = client.options.keyUse.isDown() || client.options.keyAttack.isDown();
        walkSafety.observe(tick, actor.getX(), actor.getY(), actor.getZ(), actor.getYRot(),
            survival, actor.onGround(), actor.isSwimming(), inFluid, actor.isPassenger(),
            actor.isSprinting(), actor.isCrouching() || client.options.keyShift.isDown(),
            actor.getAbilities().flying || actor.isFallFlying(), jumping || client.options.keySprint.isDown(),
            strafing, backing, interacting, client.screen == null);
        JsonObject sample = new JsonObject(); sample.addProperty("native_tick", tick);
        sample.addProperty("x", actor.getX()); sample.addProperty("y", actor.getY());
        sample.addProperty("z", actor.getZ()); sample.addProperty("yaw", actor.getYRot());
        sample.addProperty("on_ground", actor.onGround()); sample.addProperty("swimming", actor.isSwimming());
        sample.addProperty("in_fluid", inFluid); sample.addProperty("passenger", actor.isPassenger());
        sample.addProperty("sprinting", actor.isSprinting()); sample.addProperty("crouching", actor.isCrouching());
        sample.addProperty("flying", actor.getAbilities().flying || actor.isFallFlying());
        sample.addProperty("jump_down", jumping); sample.addProperty("strafe_down", strafing);
        sample.addProperty("back_down", backing); sample.addProperty("interact_down", interacting);
        walkSamples.add(sample);
    }
    private void observeAttack(Minecraft client, long tick) {
        validateWindow(client, true);
        if (attackGate == null || !client.mouseHandler.isMouseGrabbed() || client.screen != null ||
                client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL ||
                attackSamples.size() >= 162)
            throw new IllegalStateException("bounded in-world held attack evidence unavailable");
        JsonObject sample = new JsonObject();
        sample.addProperty("native_tick", tick);
        sample.addProperty("same_existing_hit_result", ClientCrosshairObservation.sameCurrentTarget(client, attackGate.projection()));
        sample.addProperty("press_callback", state.pressCallback());
        sample.addProperty("held_state_ticks", state.heldStateTicks());
        sample.addProperty("real_glfw_left_down", GLFW.glfwGetMouseButton(glfwWindow, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
        sample.addProperty("attack_mapping_down", mapping.isDown());
        sample.addProperty("on_ground", client.player.onGround());
        sample.addProperty("x", client.player.getX()); sample.addProperty("y", client.player.getY());
        sample.addProperty("z", client.player.getZ());
        sample.addProperty("yaw", client.player.getYRot()); sample.addProperty("pitch", client.player.getXRot());
        attackSamples.add(sample);
    }
    /** HEAD of vanilla handleKeybinds: release genuine Robot button before another continueAttack opportunity.
     * A changed hitResult is NOT success. Server passive START/progress/STOP must independently qualify it. */
    private void beforeRealAttackKeybinds(Minecraft client, long tick) {
        validateWindow(client, true);
        if (attackGate == null || !client.mouseHandler.isMouseGrabbed() || client.screen != null)
            throw new IllegalStateException("physical attack left the owned in-world pointer context");
        boolean same = ClientCrosshairObservation.sameCurrentTarget(client, attackGate.projection());
        if (attackGate.observeBeforeKeybind(tick, same, state.isPressed(), state.pressCallback(), state.heldStateTicks())) {
            state.release(state.sequence, tick); // Robot mouse-up, never KeyMapping.setDown or injected GLFW callback.
            attackAutoReleased = true;
        }
        if (attackGate.targetLostTick() >= 0 && !attackGate.drained()) {
            long deadline = Math.addExact(System.nanoTime(), TimeUnit.MILLISECONDS.toNanos(75));
            do {
                GLFW.glfwPollEvents(); // Actual X11 event callback only; never synthesize button state.
                if (state.releaseCallback() && !mapping.isDown() &&
                        GLFW.glfwGetMouseButton(glfwWindow, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) break;
                if (System.nanoTime() >= deadline) break;
                GLFW.glfwWaitEventsTimeout(0.002);
            } while (System.nanoTime() < deadline);
            attackGate.drainBeforeKeybind(tick, !state.isPressed(), state.releaseCallback(),
                mapping.isDown(), GLFW.glfwGetMouseButton(glfwWindow, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
        }
        attackGate.requireSafe(mapping.isDown(),
            GLFW.glfwGetMouseButton(glfwWindow, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
    }
    private JsonObject evidence(Minecraft client) {
        JsonObject row = state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT ?
            ClientNativeSupport.snapshotWithoutMenuItems(client) : ClientNativeSupport.snapshot(client);
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
        row.addProperty("key_up_drained_before_next_use_handling", keyUpGate.drained());
        row.addProperty("first_use_end_before_keybind_tick", keyUpGate.firstEndTick());
        row.addProperty("key_up_drained_before_keybind_tick", keyUpGate.drainedBeforeKeybindTick());
        row.addProperty("client_spectator", client.player.isSpectator());
        if (state.kind == HeldInputState.Kind.EMI_MOUSE_LEFT || state.kind == HeldInputState.Kind.EMI_MOUSE_RIGHT)
            row.add("emi_pre_press_hover", emiHover.deepCopy());
        if (state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT)
            row.add("original_menu_slot_click_observation", ClientMenuSlotObservation.clickEvidencePartial());
        if (state.kind == HeldInputState.Kind.ATTACK_MOUSE) {
            row.addProperty("attack_support_scope", "one_frozen_in_world_block_vanilla_hitResult_only");
            row.addProperty("attack_pre_press_crosshair_projection_sha256", attackGate.projection());
            row.addProperty("attack_bound_target_sha256_unverified_by_client", attackBoundTargetSha256);
            row.addProperty("attack_target_loss_tick", attackGate.targetLostTick());
            row.addProperty("attack_key_up_drained_before_keybind_tick", attackGate.drainedBeforeKeybindTick());
            row.addProperty("attack_auto_released_on_target_loss", attackAutoReleased);
            row.add("attack_samples", attackSamples.deepCopy());
        }
        if (state.kind == HeldInputState.Kind.WALK_FORWARD) {
            row.addProperty("walk_support_scope", "ordinary_flat_ground_forward_only");
            row.add("walk_samples", walkSamples.deepCopy());
            row.addProperty("walk_start_yaw", walkSafety.startYaw());
        }
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
        ClientAwtHeadlessBootstrap.requireReady();
        if (active != null) throw new IllegalStateException("prior held input not released and independently observed");
        if (client.player == null || client.level == null || client.getConnection() == null)
            throw new IllegalStateException("physical input requires an actual joined client");
        JsonObject args = request.parameters();
        String kindName = Protocol.string(args, "kind");
        boolean emiMouse = kindName.equals("emi_mouse_left") || kindName.equals("emi_mouse_right");
        boolean attackMouse = kindName.equals("attack_block");
        boolean containerSlot = kindName.equals("container_slot_left");
        if (containerSlot) Protocol.only(args, "profile", "player_uuid", "dimension", "kind", "max_ticks", "hold_id",
            "input_runtime_sha256", "hover_projection_sha256");
        else if (emiMouse) Protocol.only(args, "profile", "player_uuid", "dimension", "kind", "max_ticks", "hold_id",
            "input_runtime_sha256", "emi_jar_sha256", "hover_projection_sha256");
        else if (attackMouse) Protocol.only(args, "profile", "player_uuid", "dimension", "kind", "max_ticks", "hold_id",
            "input_runtime_sha256", "crosshair_projection_sha256", "bound_target_sha256");
        else Protocol.only(args, "profile", "player_uuid", "dimension", "kind", "max_ticks", "hold_id", "input_runtime_sha256");
        String profile = validatedProfile(client, Protocol.string(args, "profile"));
        ClientInputRuntimeBinding binding = ClientInputRuntimeBinding.capture(ClientNativeSupport.mailboxDirectory(),
            Protocol.string(args, "input_runtime_sha256"), request.run(), profile,
            System.getenv("DISPLAY"), System.getenv("XAUTHORITY"), ProcessHandle.current().pid());
        if (!ClientNativeSupport.sha256(boundedAuthority(Path.of(binding.authorityPath))).equals(binding.authoritySha))
            throw new IllegalStateException("sandbox-local Xauthority digest differs from controller record");
        String playerUuid = Protocol.string(args, "player_uuid"), dimension = Protocol.string(args, "dimension");
        String holdId = Protocol.string(args, "hold_id");
        usedHoldIds.claim(holdId); // Even an invalid/rejected attempt cannot reuse an OS-input ID.
        int ticks = Math.toIntExact(Protocol.number(args, "max_ticks"));
        KeyMapping mapping;
        if (kindName.equals("use") && client.screen == null && !client.player.isSpectator() &&
                !client.player.isUsingItem())
            mapping = client.options.keyUse;
        else if (kindName.equals("inventory") && client.screen == null && !client.player.isSpectator())
            mapping = client.options.keyInventory;
        else if (kindName.equals("walk_forward") && client.screen == null &&
                client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SURVIVAL &&
                !client.player.isUsingItem())
            mapping = client.options.keyUp;
        else if (attackMouse && client.screen == null && client.mouseHandler.isMouseGrabbed() &&
                client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SURVIVAL &&
                !client.player.isUsingItem() && !client.player.isPassenger() && client.player.onGround() &&
                !client.player.getAbilities().flying && !client.player.isFallFlying() &&
                !client.options.keyUse.isDown() && !client.options.keyUp.isDown() &&
                !client.options.keyDown.isDown() && !client.options.keyLeft.isDown() &&
                !client.options.keyRight.isDown() && !client.options.keyJump.isDown())
            mapping = client.options.keyAttack;
        else if (containerSlot && client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&
                client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SURVIVAL)
            mapping = null; // Real GLFW OS mouse state, not a fabricated KeyMapping.
        else if (emiMouse && client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SURVIVAL &&
                "net.minecraft.client.gui.screens.inventory.InventoryScreen".equals(
                    client.screen == null ? null : client.screen.getClass().getName()))
            mapping = null; // Actual GLFW button state, never a fabricated KeyMapping for an EMI GUI.
        else if (kindName.equals("onboarding") && client.screen == null && client.player.isSpectator()) {
            boolean[] onboarding = onboardingState();
            if (!onboarding[0] || !onboarding[1] || !onboarding[2])
                throw new IllegalStateException("Better Spawns first-login spawn-only selection not pending");
            var matches = Arrays.stream(client.options.keyMappings).filter(k ->
                k.getName().equals("key.better_spawns.open_menu")).toList();
            if (matches.size() != 1) throw new IllegalStateException("production Better Spawns onboarding binding absent/ambiguous");
            mapping = matches.get(0);
        } else throw new IllegalArgumentException("held input requires reviewed joined in-world use/inventory/onboarding context");
        int mouseButton = (kindName.equals("emi_mouse_left") || attackMouse || containerSlot) ?
            GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if ((mapping != null && mapping.isDown()) || ((emiMouse || attackMouse || containerSlot) &&
                GLFW.glfwGetMouseButton(client.getWindow().getWindow(), mouseButton) == GLFW.GLFW_PRESS) ||
                (kindName.equals("onboarding") && Screen.hasShiftDown()))
            throw new IllegalStateException("prior key/button state not physically released");
        InputConstants.Key bound = mapping == null ? null : mapping.getKey();
        HeldInputState.Kind kind;
        int glfwCode, awtCode;
        if (containerSlot && ticks >= 2 && ticks <= 8) {
            kind = HeldInputState.Kind.CONTAINER_SLOT_LEFT;
            glfwCode = GLFW.GLFW_MOUSE_BUTTON_LEFT; awtCode = InputEvent.BUTTON1_DOWN_MASK;
        } else if (emiMouse && ticks >= 2 && ticks <= 8) {
            kind = kindName.equals("emi_mouse_left") ? HeldInputState.Kind.EMI_MOUSE_LEFT : HeldInputState.Kind.EMI_MOUSE_RIGHT;
            glfwCode = mouseButton; awtCode = kind == HeldInputState.Kind.EMI_MOUSE_LEFT ?
                InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
            EmiGuiObservation.requireReadOnlyNavigationBinding(kind == HeldInputState.Kind.EMI_MOUSE_RIGHT, mouseButton);
        } else if (attackMouse && bound.getType() == InputConstants.Type.MOUSE &&
                bound.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT && mapping.getKeyModifier().name().equals("NONE") &&
                ticks >= 4 && ticks <= 160 &&
                Protocol.string(args, "bound_target_sha256").matches("[0-9a-f]{64}")) {
            kind = HeldInputState.Kind.ATTACK_MOUSE; glfwCode = GLFW.GLFW_MOUSE_BUTTON_LEFT;
            awtCode = InputEvent.BUTTON1_DOWN_MASK;
        } else if (kindName.equals("onboarding") && bound.getType() == InputConstants.Type.KEYSYM &&
                bound.getValue() == GLFW.GLFW_KEY_K &&
                client.options.keyShift.getKey().getType() == InputConstants.Type.KEYSYM &&
                client.options.keyShift.getKey().getValue() == GLFW.GLFW_KEY_LEFT_SHIFT &&
                mapping.getKeyModifier().name().equals("NONE") && ticks <= 40) {
            kind = HeldInputState.Kind.ONBOARDING_CHORD; glfwCode = GLFW.GLFW_KEY_K; awtCode = KeyEvent.VK_K;
        } else if (kindName.equals("use") && bound.getType() == InputConstants.Type.MOUSE &&
                bound.getValue() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            kind = HeldInputState.Kind.USE_MOUSE; glfwCode = GLFW.GLFW_MOUSE_BUTTON_RIGHT; awtCode = InputEvent.BUTTON3_DOWN_MASK;
        } else if (kindName.equals("walk_forward") && bound.getType() == InputConstants.Type.KEYSYM &&
                bound.getValue() == GLFW.GLFW_KEY_W && ticks >= 4 && ticks <= 60 &&
                mapping.getKeyModifier().name().equals("NONE")) {
            kind = HeldInputState.Kind.WALK_FORWARD; glfwCode = GLFW.GLFW_KEY_W; awtCode = KeyEvent.VK_W;
        } else if ((kindName.equals("use") || kindName.equals("inventory")) &&
                bound.getType() == InputConstants.Type.KEYSYM) {
            kind = kindName.equals("use") ? HeldInputState.Kind.USE_KEY : HeldInputState.Kind.INVENTORY_KEY;
            glfwCode = bound.getValue(); awtCode = awtKeyboardCode(glfwCode);
        } else throw new IllegalArgumentException("unreviewed actual client input binding");
        String attackProjection = attackMouse ? Protocol.string(args, "crosshair_projection_sha256") : null;
        String attackTarget = attackMouse ? Protocol.string(args, "bound_target_sha256") : null;
        if (attackMouse && !ClientCrosshairObservation.sameCurrentTarget(client, attackProjection))
            throw new IllegalStateException("actual vanilla crosshair changed after independently reviewed pre-press observation");
        RobotInputBridge bridge = new RobotInputBridge(client, request, tick, profile, playerUuid, dimension,
            holdId, kind, mapping, glfwCode, awtCode, ticks, binding, ClientNativeSupport.mailboxDirectory(),
            attackProjection, attackTarget);
        bridge.validateWindow(client, true);
        if (kind == HeldInputState.Kind.WALK_FORWARD) bridge.observeWalk(client, tick);
        if (kind == HeldInputState.Kind.ATTACK_MOUSE) bridge.observeAttack(client, tick);
        if (containerSlot) ClientMenuSlotObservation.requireFreshHover(client,
            Protocol.string(args, "hover_projection_sha256"), playerUuid, dimension, profile, binding.sha256);
        if (emiMouse) {
            JsonObject read = new JsonObject(); read.addProperty("emi_jar_sha256", Protocol.string(args, "emi_jar_sha256"));
            bridge.emiHover = EmiGuiObservation.hover(client, read);
            if (!bridge.emiHover.get("projection_sha256").getAsString().equals(
                    Protocol.string(args, "hover_projection_sha256")))
                throw new IllegalStateException("reviewed EMI rendered target changed before actual OS mouse press");
        }
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
                active.state.kind == HeldInputState.Kind.ATTACK_MOUSE ||
                active.state.kind == HeldInputState.Kind.EMI_MOUSE_LEFT ||
                active.state.kind == HeldInputState.Kind.EMI_MOUSE_RIGHT ||
                active.state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT ||
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
        if (active == null || (active.state.kind != HeldInputState.Kind.USE_MOUSE &&
                active.state.kind != HeldInputState.Kind.ATTACK_MOUSE &&
                active.state.kind != HeldInputState.Kind.EMI_MOUSE_LEFT &&
                active.state.kind != HeldInputState.Kind.EMI_MOUSE_RIGHT &&
                active.state.kind != HeldInputState.Kind.CONTAINER_SLOT_LEFT) ||
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
                valid, (client.screen == null &&
                    (active.state.kind != HeldInputState.Kind.USE_MOUSE && active.state.kind != HeldInputState.Kind.ATTACK_MOUSE ||
                        client.mouseHandler.isMouseGrabbed())) || active.state.kind == HeldInputState.Kind.INVENTORY_KEY ||
                    ((active.state.kind == HeldInputState.Kind.EMI_MOUSE_LEFT ||
                        active.state.kind == HeldInputState.Kind.EMI_MOUSE_RIGHT) && emiScreen(client)) ||
                    (active.state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT &&
                        ClientMenuSlotObservation.sameActiveTarget(client)),
                valid && active.physicallyDown(client));
            if (active.state.kind == HeldInputState.Kind.WALK_FORWARD) active.observeWalk(client, tick);
            if (active.state.kind == HeldInputState.Kind.ATTACK_MOUSE) active.observeAttack(client, tick);
            if (active.state.kind == HeldInputState.Kind.USE_MOUSE || active.state.kind == HeldInputState.Kind.USE_KEY) {
                // Client-local use state is a release trigger, NOT proof of server consumption.
                // B's separately armed passive packet/Start/Finish/final observer decides completion.
                if (active.useGate.observe(tick, active.state.heldStateTicks() > 0, client.player.isUsingItem())) {
                    active.keyUpGate.firstUseEnded(tick);
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
    /** Invoked at Minecraft.handleKeybinds HEAD, after the preceding END and before the next eligible use.
     * Release comes from Robot only, then GLFW must drain the real OS callback and mapping up-state.
     * An undrained transition aborts the epoch BEFORE vanilla can repeat/empty-hand use; never cancel gameplay.
     */
    static synchronized void beforeRealUseKeybinds(Minecraft client, long tick) {
        if (active == null) return;
        if (active.state.kind == HeldInputState.Kind.ATTACK_MOUSE) {
            active.beforeRealAttackKeybinds(client, tick);
            return;
        }
        if (active.state.kind != HeldInputState.Kind.USE_MOUSE &&
                active.state.kind != HeldInputState.Kind.USE_KEY) return;
        active.validateWindow(client, true);
        if (active.state.isPressed() && active.useGate.started() && !active.useGate.ended() &&
                active.useGate.observe(tick, active.state.heldStateTicks() > 0, client.player.isUsingItem())) {
            active.keyUpGate.firstUseEnded(tick);
            active.state.release(active.state.sequence, tick);
            active.autoReleased = true;
        }
        if (active.useGate.ended()) {
            if (!active.autoReleased || active.state.releaseTick() < 0)
                throw new IllegalStateException("client use ended without physical Robot release");
            if (!active.keyUpGate.drained()) {
                long deadline = Math.addExact(System.nanoTime(), TimeUnit.MILLISECONDS.toNanos(75));
                do {
                    GLFW.glfwPollEvents(); // Dispatch actual X11 input callbacks; never synthesize KeyMapping state.
                    if (active.state.releaseCallback() && !active.mapping.isDown() && !active.physicallyDown(client)) break;
                    if (System.nanoTime() >= deadline) break;
                    GLFW.glfwWaitEventsTimeout(0.002); // Bounded main-thread OS event drain, not a gameplay tick.
                } while (System.nanoTime() < deadline);
                active.keyUpGate.observedReleaseBeforeKeybinds(tick, active.state.releaseCallback(),
                    active.mapping.isDown(), active.physicallyDown(client));
            }
            active.keyUpGate.requireSafeToHandleNextUse(active.mapping.isDown(), active.physicallyDown(client));
        }
    }
    /** A prepublished release request waits until first use end AND real OS key-up drained ahead of keybinds. */
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
        if (active.state.kind == HeldInputState.Kind.ATTACK_MOUSE)
            return active.state.isPressed() || !active.attackGate.drained();
        return (active.state.kind == HeldInputState.Kind.USE_MOUSE ||
            active.state.kind == HeldInputState.Kind.USE_KEY) &&
            (active.state.isPressed() || !active.keyUpGate.drained());
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
                        active.useGate.endTick() <= active.useGate.startTick() || active.state.releaseTick() < 0 ||
                        !active.keyUpGate.drained())
                    throw new IllegalStateException("use release not triggered by first actual client use end");
            } else if (active.state.kind == HeldInputState.Kind.ATTACK_MOUSE) {
                if (!active.attackAutoReleased || !active.attackGate.drained() || active.state.releaseTick() < 0)
                    throw new IllegalStateException("attack release was not automatically drained ahead of the next keybind");
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
            if (active.state.kind == HeldInputState.Kind.CONTAINER_SLOT_LEFT)
                ClientMenuSlotObservation.originalClickEvidence();
            if (active.state.kind == HeldInputState.Kind.ATTACK_MOUSE && !active.attackGate.drained())
                throw new IllegalStateException("attack status preceded OS mouse-up pre-keybind drain");
            if ((active.state.kind == HeldInputState.Kind.USE_MOUSE || active.state.kind == HeldInputState.Kind.USE_KEY) &&
                    !active.keyUpGate.drained())
                throw new IllegalStateException("use status preceded real pre-keybind OS callback drain");
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
