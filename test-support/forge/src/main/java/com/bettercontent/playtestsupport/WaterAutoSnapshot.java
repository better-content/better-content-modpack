package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/** Strict passive water/Curios state; never runs WaterSources.debit, WaterDrinking.sip or capability mutation. */
final class WaterAutoSnapshot {
    private static final String PROVIDER = "better_drinking_water";
    private static final String THIRST_CAP = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
    private static final String THIRST_API = "dev.ghen.thirst.foundation.common.capability.IThirst";
    private static final String CURIOS = "top.theillusivec4.curios.api.CuriosApi";
    private static final String HANDLER = "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler";
    private static final String RESERVE_KEY = "better_drinking_water:reserve";
    private WaterAutoSnapshot() {}
    static String requireProvider(String expected) throws Exception {
        if (expected == null || !expected.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("reviewed drinking-water provider artifact SHA required");
        var mod = ModList.get().getModFileById(PROVIDER);
        if (mod == null || mod.getFile() == null) throw new IllegalStateException("drinking-water provider is not loaded");
        Path jar = mod.getFile().getFilePath().toRealPath();
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(jar) ||
                Files.size(jar) < 1 || Files.size(jar) > 64_000_000 ||
                !BlockMutationScope.sha256(Files.readAllBytes(jar)).equals(expected))
            throw new IllegalStateException("selected loaded drinking-water artifact does not match reviewed SHA");
        return expected;
    }
    static JsonObject read(ServerPlayer actor, String expectedDim, String providerSha) throws Exception {
        if (actor == null || !actor.isAlive() || !actor.serverLevel().dimension().location().toString().equals(expectedDim) ||
                !actor.serverLevel().getServer().isSameThread())
            throw new IllegalStateException("water auto snapshot actor/dimension/server thread mismatch");
        requireProvider(providerSha);
        JsonObject row = new JsonObject();
        row.addProperty("schema", "bc.playtest_water_auto_snapshot.v1");
        row.addProperty("player_uuid", actor.getUUID().toString());
        row.addProperty("dimension", expectedDim);
        row.addProperty("provider_artifact_sha256", providerSha);
        row.addProperty("server_tick", actor.serverLevel().getGameTime());
        row.addProperty("server_monotonic_nanos", System.nanoTime());
        row.addProperty("player_tick_count", actor.tickCount);
        Class<?> capType = Class.forName(THIRST_CAP), apiType = Class.forName(THIRST_API);
        Object cap = capType.getField("PLAYER_THIRST").get(null);
        if (!(cap instanceof Capability<?> capability)) throw new IllegalStateException("Thirst capability signature mismatch");
        Object thirst = actor.getCapability(capability).resolve().orElseThrow(
                () -> new IllegalStateException("Thirst capability absent"));
        if (!apiType.isInstance(thirst)) throw new IllegalStateException("Thirst provider type mismatch");
        Method getThirst = apiType.getMethod("getThirst"), getQuenched = apiType.getMethod("getQuenched");
        if (getThirst.getReturnType() != int.class || getQuenched.getReturnType() != int.class)
            throw new IllegalStateException("Thirst getter changed");
        row.addProperty("thirst", (int) getThirst.invoke(thirst));
        row.addProperty("quenched", (int) getQuenched.invoke(thirst));
        Class<?> curios = Class.forName(CURIOS), handlerApi = Class.forName(HANDLER);
        Object lazy = curios.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, actor);
        if (!(lazy instanceof net.minecraftforge.common.util.LazyOptional<?> resolved))
            throw new IllegalStateException("Curios API LazyOptional changed");
        Object handler = resolved.resolve().orElseThrow(() -> new IllegalStateException("Curios inventory absent"));
        if (!handlerApi.isInstance(handler)) throw new IllegalStateException("Curios handler type mismatch");
        Method getStackHandler = handlerApi.getMethod("getStacksHandler", String.class);
        row.add("water_slots", slots(getStackHandler.invoke(handler, "water"), "water", true));
        row.add("empty_bottle_slots", slots(getStackHandler.invoke(handler, "empty_bottle"), "empty_bottle", false));
        row.add("better_backpack_slots", slots(getStackHandler.invoke(handler, "better_backpack"), "better_backpack", false));
        int vanillaSlots = actor.getInventory().getContainerSize();
        if (vanillaSlots != 41) throw new IllegalStateException("unknown vanilla 41-slot inventory shape");
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < vanillaSlots; slot++) {
            JsonObject cell = stack(actor.getInventory().getItem(slot)); cell.addProperty("slot", slot);
            inventory.add(cell);
        }
        row.add("vanilla_inventory", inventory);
        row.add("reserve", reserve(actor));
        row.addProperty("backpack_contents_complete", false);
        row.addProperty("untracked_dropped_vessel_entities_complete", false);
        row.addProperty("source_acquisition_proven_by_native", false);
        return row;
    }
    private static JsonObject slots(Object raw, String name, boolean mandatory) throws Exception {
        if (!(raw instanceof Optional<?> optional)) throw new IllegalStateException("Curios slot API changed");
        JsonObject row = new JsonObject(); row.addProperty("kind", name);
        row.addProperty("handler_present", optional.isPresent());
        JsonArray cells = new JsonArray();
        if (optional.isPresent()) {
            Object selected = optional.get();
            Object stacks = selected.getClass().getMethod("getStacks").invoke(selected);
            if (!(stacks instanceof IItemHandler handler)) throw new IllegalStateException("Curios stack handler API changed");
            int size = handler.getSlots();
            if (size < 0 || size > 16 || mandatory && size < 1)
                throw new IllegalStateException("Curios slot count outside reviewed 0..16 range");
            for (int i = 0; i < size; i++) {
                JsonObject cell = stack(handler.getStackInSlot(i)); cell.addProperty("slot", i); cells.add(cell);
            }
        } else if (mandatory) throw new IllegalStateException("Curios water source handler missing");
        row.add("slots", cells); return row;
    }
    private static JsonObject stack(ItemStack original) {
        JsonObject row = new JsonObject();
        if (original == null || original.isEmpty()) {
            row.addProperty("present", false); row.addProperty("count", 0);
            row.add("id", JsonNull.INSTANCE); row.add("stack_canonical_sha256", JsonNull.INSTANCE);
            row.add("stack_content_sha256", JsonNull.INSTANCE); return row;
        }
        ItemStack saved = original.copy();
        CompoundTag nbt = saved.save(new CompoundTag());
        if (nbt.toString().length() > 65_536) throw new IllegalStateException("water stack NBT row too large");
        var id = ForgeRegistries.ITEMS.getKey(saved.getItem());
        if (id == null || saved.getCount() < 1 || saved.getCount() > saved.getMaxStackSize())
            throw new IllegalStateException("water stack registry/count invalid");
        row.addProperty("present", true); row.addProperty("count", saved.getCount());
        row.addProperty("id", id.toString());
        row.addProperty("stack_canonical_sha256", CanonicalStackIdentity.sha256(nbt));
        row.addProperty("stack_content_sha256", CanonicalStackIdentity.contentSha256(nbt));
        return row;
    }
    private static JsonObject reserve(ServerPlayer actor) throws ReflectiveOperationException {
        // Entity.getPersistentData() lazily creates an empty CompoundTag, so do not call it in an observer.
        var storedField = net.minecraft.world.entity.Entity.class.getDeclaredField("persistentData");
        storedField.setAccessible(true);
        Object stored = storedField.get(actor);
        if (stored != null && !(stored instanceof CompoundTag))
            throw new IllegalStateException("player persistentData field type changed");
        CompoundTag root = stored == null ? null : (CompoundTag) stored;
        if (root == null || !root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            JsonObject absent = new JsonObject(); absent.addProperty("present", false); return absent;
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(RESERVE_KEY, Tag.TAG_COMPOUND)) {
            if (persisted.contains(RESERVE_KEY)) throw new IllegalStateException("unexpected reserve NBT type");
            JsonObject absent = new JsonObject(); absent.addProperty("present", false); return absent;
        }
        CompoundTag raw = persisted.getCompound(RESERVE_KEY).copy();
        if (raw.toString().length() > 16_384 || !raw.contains("version", Tag.TAG_INT) ||
                !raw.contains("thirst", Tag.TAG_INT) || !raw.contains("quenched", Tag.TAG_INT) ||
                !raw.contains("purity", Tag.TAG_INT) || !raw.contains("consumed", Tag.TAG_DOUBLE))
            throw new IllegalStateException("reserve NBT malformed or oversized");
        int version = raw.getInt("version"), thirst = raw.getInt("thirst"), quenched = raw.getInt("quenched"),
            purity = raw.getInt("purity");
        double consumed = raw.getDouble("consumed");
        if (version != 1 && version != 2 || thirst < 1 || thirst > 64 || quenched < 0 || quenched > 128 ||
                purity < 0 || purity > 3 || !Double.isFinite(consumed) || consumed < 0 || consumed >= 1 ||
                version == 2 && !raw.contains("beverage", Tag.TAG_COMPOUND) ||
                version == 1 && (raw.contains("beverage") || thirst > 20 || quenched > 20))
            throw new IllegalStateException("reserve NBT outside provider version/range");
        JsonObject row = new JsonObject(); row.addProperty("present", true);
        row.addProperty("version", version); row.addProperty("thirst", thirst); row.addProperty("quenched", quenched);
        row.addProperty("purity", purity); row.addProperty("consumed", consumed);
        row.addProperty("beverage_v2_present", version == 2);
        row.addProperty("beverage_v2_semantics_complete", false); // Package-private BeverageServing is not a public projection.
        return row;
    }
}
