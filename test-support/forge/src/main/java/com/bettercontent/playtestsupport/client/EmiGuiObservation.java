package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Read-only pinned EMI 1.1.24 UI observation. Never calls EmiApi display/open/search mutation. */
final class EmiGuiObservation {
    private static final Gson JSON = new Gson();
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final String INVENTORY = "net.minecraft.client.gui.screens.inventory.InventoryScreen";
    private static final String RECIPE_SCREEN = "dev.emi.emi.screen.RecipeScreen";
    private static final String TAG = "dev.emi.emi.api.stack.TagEmiIngredient";
    private EmiGuiObservation() {}

    private static String id(Object value) {
        if (value == null || !RESOURCE.matcher(value.toString()).matches() || value.toString().length() > 512)
            throw new IllegalStateException("rendered EMI ingredient/recipe lacks canonical public ID");
        return value.toString();
    }
    private static Object call(Object receiver, String name) throws Exception {
        return receiver.getClass().getMethod(name).invoke(receiver);
    }
    private static Object api(String name, Class<?>[] types, Object... args) throws Exception {
        return Class.forName("dev.emi.emi.api.EmiApi").getMethod(name, types).invoke(null, args);
    }
    private static String sha(JsonObject row) {
        return ClientNativeSupport.sha256(JSON.toJson(row).getBytes(StandardCharsets.UTF_8));
    }
    static JsonObject hover(Minecraft client, JsonObject parameters) throws Exception {
        Protocol.only(parameters, "emi_jar_sha256");
        String expected = Protocol.string(parameters, "emi_jar_sha256");
        if (!expected.equals(EmiPublicObservation.requirePinnedJar()) || client.screen == null ||
                !INVENTORY.equals(client.screen.getClass().getName()))
            throw new IllegalStateException("pinned EMI and real vanilla inventory screen required for visible hover");
        double[] px = new double[1], py = new double[1];
        long window = client.getWindow().getWindow();
        GLFW.glfwGetCursorPos(window, px, py);
        if (!Double.isFinite(px[0]) || !Double.isFinite(py[0]) || px[0] < 0 || py[0] < 0 ||
                px[0] >= client.getWindow().getScreenWidth() || py[0] >= client.getWindow().getScreenHeight())
            throw new IllegalStateException("physical X11 pointer outside actual joined GUI window");
        int x = (int) Math.floor(px[0] * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth());
        int y = (int) Math.floor(py[0] * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight());
        Class<?> manager = Class.forName("dev.emi.emi.screen.EmiScreenManager");
        if (manager.getField("lastMouseX").getInt(null) != x || manager.getField("lastMouseY").getInt(null) != y)
            throw new IllegalStateException("EMI rendered pointer was not refreshed after actual OS move");
        Object atPointer = api("getHoveredStack", new Class<?>[] {int.class, int.class, boolean.class}, x, y, false);
        Object lastRendered = api("getHoveredStack", new Class<?>[] {boolean.class}, false);
        JsonObject pointer = ingredient(atPointer), rendered = ingredient(lastRendered);
        if (!pointer.equals(rendered)) throw new IllegalStateException("physical pointer and last actually rendered EMI element differ");
        pointer.addProperty("screen_class", INVENTORY);
        pointer.addProperty("gui_x", x); pointer.addProperty("gui_y", y);
        pointer.addProperty("emi_jar_sha256", expected);
        pointer.addProperty("source", "pinned EmiApi.getHoveredStack(actual_pointer)/last_rendered");
        pointer.addProperty("projection_sha256", sha(pointer));
        return pointer;
    }
    static JsonObject ingredient(Object interaction) throws Exception {
        if (interaction == null || !interaction.getClass().getName().equals(
                "dev.emi.emi.screen.EmiScreenManager$SidebarEmiStackInteraction") ||
                !Boolean.TRUE.equals(call(interaction, "isClickable")) ||
                Boolean.TRUE.equals(call(interaction, "isEmpty")) || call(interaction, "getRecipeContext") != null)
            throw new IllegalStateException("rendered EMI element is unavailable, nonclickable or contextual");
        Object stack = call(interaction, "getStack");
        if (stack == null || Boolean.TRUE.equals(call(stack, "isEmpty")))
            throw new IllegalStateException("actual hovered EMI ingredient absent");
        Object raw = call(stack, "getEmiStacks");
        if (!(raw instanceof List<?> members) || members.isEmpty() || members.size() > 128 ||
                !Long.valueOf(1).equals(call(stack, "getAmount")))
            throw new IllegalStateException("hovered EMI ingredient has unsupported amount or member count");
        List<String> ids = new ArrayList<>(members.size());
        for (Object member : members) {
            if (member == null || !Long.valueOf(1).equals(call(member, "getAmount")) ||
                    !Boolean.FALSE.equals(call(member, "hasNbt")))
                throw new IllegalStateException("NBT/amount variant unavailable in reviewed EMI ID-only projection");
            ids.add(id(call(member, "getId")));
        }
        Collections.sort(ids);
        JsonObject row = new JsonObject(); JsonArray alternatives = new JsonArray(); ids.forEach(alternatives::add);
        row.add("member_ids", alternatives);
        row.addProperty("source_interaction_class", interaction.getClass().getName());
        if (TAG.equals(stack.getClass().getName())) {
            Object key = stack.getClass().getField("key").get(stack);
            row.addProperty("hover_kind", "tag");
            row.addProperty("hover_id", id(call(key, "location")));
        } else if (members.size() == 1 && stack == members.get(0) &&
                stack.getClass().getName().startsWith("dev.emi.emi.api.stack.")) {
            row.addProperty("hover_kind", "stack");
            row.addProperty("hover_id", ids.get(0));
        } else throw new IllegalStateException("unknown EMI ingredient provider/shape is unsupported");
        return row;
    }
    static void requireReadOnlyNavigationBinding(boolean uses, int button) throws Exception {
        if (Boolean.TRUE.equals(api("isCheatMode", new Class<?>[0])))
            throw new IllegalStateException("EMI cheat mode cannot be used for a read-only navigation trial");
        Class<?> config = Class.forName("dev.emi.emi.config.EmiConfig");
        if (config.getField("editMode").getBoolean(null) || config.getField("devMode").getBoolean(null))
            throw new IllegalStateException("EMI edit/dev modes may change sidebar interaction semantics");
        Object binding = config.getField(uses ? "viewUses" : "viewRecipes").get(null);
        Object keys = binding.getClass().getField("boundKeys").get(binding);
        if (!(keys instanceof List<?> list) || list.isEmpty() || list.size() > 4 ||
                list.stream().noneMatch(key -> {
                    try {
                        return ((InputConstants.Key) call(key, "key")).getType() == InputConstants.Type.MOUSE &&
                            ((InputConstants.Key) call(key, "key")).getValue() == button &&
                            Integer.valueOf(0).equals(call(key, "modifiers"));
                    } catch (Exception failure) { return false; }
                })) throw new IllegalStateException("pinned EMI mouse navigation binding absent/remapped/modified");
    }
    static JsonObject page(Minecraft client, JsonObject parameters) throws Exception {
        Protocol.only(parameters, "emi_jar_sha256");
        String expected = Protocol.string(parameters, "emi_jar_sha256");
        if (!expected.equals(EmiPublicObservation.requirePinnedJar()) || client.screen == null ||
                !RECIPE_SCREEN.equals(client.screen.getClass().getName()))
            throw new IllegalStateException("actual pinned EMI recipe screen not open");
        Class<?> screen = client.screen.getClass();
        // Pinned, checked internal rendering state: public EMI API has no current-page recipe roster.
        Field currentPage = screen.getDeclaredField("currentPage"); currentPage.setAccessible(true);
        Object raw = currentPage.get(client.screen);
        if (!(raw instanceof List<?> groups) || groups.isEmpty() || groups.size() > 32)
            throw new IllegalStateException("visible recipe page absent or oversized");
        JsonArray visible = new JsonArray();
        for (Object group : groups) {
            if (group == null || !group.getClass().getName().equals("dev.emi.emi.screen.WidgetGroup"))
                throw new IllegalStateException("unreviewed EMI rendered group class");
            Class<?> type = group.getClass();
            int x = type.getField("x").getInt(group), y = type.getField("y").getInt(group);
            int width = type.getField("width").getInt(group), height = type.getField("height").getInt(group);
            Object recipe = type.getField("recipe").get(group);
            Object widgets = type.getField("widgets").get(group);
            if (recipe == null || !(widgets instanceof List<?> list) || list.isEmpty() || list.size() > 512 ||
                    x < 0 || y < 0 || width < 1 || height < 1 ||
                    (long) x + width > client.getWindow().getGuiScaledWidth() ||
                    (long) y + height > client.getWindow().getGuiScaledHeight())
                throw new IllegalStateException("unrendered/anonymous/out-of-window EMI recipe group");
            JsonObject row = new JsonObject(); row.addProperty("recipe_id", id(call(recipe, "getId")));
            row.addProperty("category_id", id(call(call(recipe, "getCategory"), "getId")));
            row.addProperty("x", x); row.addProperty("y", y);
            row.addProperty("width", width); row.addProperty("height", height);
            row.addProperty("widget_count", list.size()); visible.add(row);
        }
        JsonObject result = new JsonObject(); result.addProperty("screen_class", RECIPE_SCREEN);
        result.addProperty("emi_jar_sha256", expected);
        result.addProperty("source", "pinned RecipeScreen.currentPage/WidgetGroup.recipe rendered UI state");
        result.add("visible_recipes", visible);
        result.addProperty("projection_sha256", sha(result));
        return result;
    }
}
