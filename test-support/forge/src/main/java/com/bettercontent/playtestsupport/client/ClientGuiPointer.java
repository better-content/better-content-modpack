package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.file.Path;

/** One physical OS pointer move only; hover is separately observed after a real rendered frame. */
final class ClientGuiPointer {
    private ClientGuiPointer() {}
    static int rawCenter(int gui, int guiSize, int rawSize) {
        if (guiSize < 1 || rawSize < 1 || gui < 0 || gui >= guiSize || rawSize % guiSize != 0)
            throw new IllegalArgumentException("unreviewed GUI coordinate/profile scaling");
        int raw = Math.toIntExact(Math.round((gui + 0.5) * rawSize / (double) guiSize));
        if (raw < 0 || raw >= rawSize || (int) Math.floor(raw * guiSize / (double) rawSize) != gui)
            throw new IllegalStateException("real pointer coordinate cannot resolve to one reviewed rendered pixel");
        return raw;
    }
    static JsonObject move(Minecraft client, Protocol.Request request) throws Exception {
        ClientAwtHeadlessBootstrap.requireReady();
        if (RobotInputBridge.inputPending() || client.player == null || client.level == null ||
                client.getConnection() == null || client.screen == null ||
                !client.screen.getClass().getName().equals("net.minecraft.client.gui.screens.inventory.InventoryScreen"))
            throw new IllegalStateException("physical pointer move requires a released joined Survival inventory GUI");
        JsonObject args = request.parameters();
        Protocol.only(args, "profile", "player_uuid", "dimension", "gui_x", "gui_y", "input_runtime_sha256");
        String profile = RobotInputBridge.validatedProfile(client, Protocol.string(args, "profile"));
        ClientInputRuntimeBinding binding = ClientInputRuntimeBinding.capture(ClientNativeSupport.mailboxDirectory(),
            Protocol.string(args, "input_runtime_sha256"), request.run(), profile,
            System.getenv("DISPLAY"), System.getenv("XAUTHORITY"), ProcessHandle.current().pid());
        if (!ClientNativeSupport.sha256(RobotInputBridge.boundedAuthority(Path.of(binding.authorityPath))).equals(binding.authoritySha) ||
                !Protocol.string(args, "player_uuid").equals(client.player.getUUID().toString()) ||
                !Protocol.string(args, "dimension").equals(client.level.dimension().location().toString()) ||
                client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL)
            throw new IllegalStateException("GUI pointer actor or sandbox-local binding is not the reviewed Survival actor");
        long window = client.getWindow().getWindow(), x11 = GLFWNativeX11.glfwGetX11Window(window);
        if (window == 0 || x11 == 0 || GLFWNativeX11.glfwGetX11Display() == 0 ||
                GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == 0 ||
                GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == 0)
            throw new IllegalStateException("GUI pointer target is not the focused private X11 window");
        int x = Math.toIntExact(Protocol.number(args, "gui_x")), y = Math.toIntExact(Protocol.number(args, "gui_y"));
        int guiWidth = client.getWindow().getGuiScaledWidth(), guiHeight = client.getWindow().getGuiScaledHeight();
        if (x < 0 || y < 0 || x >= guiWidth || y >= guiHeight ||
                (long) guiWidth * guiHeight > 4_000_000)
            throw new IllegalArgumentException("reviewed GUI pixel outside actual rendered profile");
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
            .getDefaultConfiguration().getBounds();
        int rawWidth = client.getWindow().getScreenWidth(), rawHeight = client.getWindow().getScreenHeight();
        int[] px = new int[1], py = new int[1], w = new int[1], h = new int[1];
        GLFW.glfwGetWindowPos(window, px, py); GLFW.glfwGetWindowSize(window, w, h);
        if (bounds.x != 0 || bounds.y != 0 || bounds.width != rawWidth || bounds.height != rawHeight ||
                px[0] != 0 || py[0] != 0 || w[0] != rawWidth || h[0] != rawHeight)
            throw new IllegalStateException("GUI pointer requires a full private Xvfb display/window");
        int rawX = rawCenter(x, guiWidth, rawWidth);
        int rawY = rawCenter(y, guiHeight, rawHeight);
        if (rawX < 0 || rawX >= rawWidth || rawY < 0 || rawY >= rawHeight)
            throw new IllegalStateException("GUI coordinate conversion overflow or out of framebuffer");
        new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice())
            .mouseMove(rawX, rawY); // Physical X11 pointer only. No Screen.mouseMoved/input callback injection.
        JsonObject row = ClientNativeSupport.snapshot(client);
        row.addProperty("evidence_role", "authentic_robot_os_pointer_NOT_direct_callback_injection");
        row.addProperty("physical_pointer_move_issued", true);
        row.addProperty("gui_x", x); row.addProperty("gui_y", y);
        row.addProperty("raw_x", rawX); row.addProperty("raw_y", rawY);
        row.addProperty("profile", profile); row.addProperty("input_runtime_sha256", binding.sha256);
        row.addProperty("client_namespace_pid", binding.namespacePid);
        row.addProperty("controller_claim_host_xvfb_pid", binding.hostXvfbPid);
        row.addProperty("controller_claim_host_xvfb_birth_epoch_millis", binding.hostXvfbBirthMillis);
        row.addProperty("controller_claim_host_client_pid", binding.hostClientPid);
        row.addProperty("controller_claim_host_client_birth_epoch_millis", binding.hostClientBirthMillis);
        row.addProperty("client_session_id", binding.clientSession);
        row.addProperty("epoch_id", binding.epochId); row.addProperty("purpose", binding.purpose);
        row.addProperty("display", binding.display); row.addProperty("xauthority_sha256", binding.authoritySha);
        row.addProperty("glfw_window", window); row.addProperty("x11_window", x11);
        return row;
    }
}
