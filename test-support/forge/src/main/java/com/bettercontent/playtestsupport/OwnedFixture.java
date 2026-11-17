package com.bettercontent.playtestsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One request-owned, disposable real-player state snapshot. Never a source/acquisition receipt. */
final class OwnedFixture {
    final String fixtureId, ownerCase, ownerToken, hand, suppliedItem;
    final UUID playerId;
    final int suppliedCount;
    private final List<ItemStack> inventory;
    private final int selected;
    private final CompoundTag food, thirst;
    private final List<MobEffectInstance> effects;
    private final int foodBefore;
    private final float saturationBefore;
    private final int thirstBefore, quenchedBefore, thirstTarget, quenchedTarget;
    private final boolean preserveHeld;

    OwnedFixture(Protocol.Request owner, String fixtureId, ServerPlayer player, String hand, String item, int count,
                 int thirstTarget, int quenchedTarget, boolean preserveHeld) throws ReflectiveOperationException {
        this.fixtureId = fixtureId;
        ownerCase = owner.caseId(); ownerToken = owner.token(); playerId = player.getUUID();
        this.hand = hand; suppliedItem = item; suppliedCount = count;
        selected = player.getInventory().selected;
        inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            inventory.add(player.getInventory().getItem(i).copy());
        food = new CompoundTag(); player.getFoodData().addAdditionalSaveData(food);
        foodBefore = player.getFoodData().getFoodLevel(); saturationBefore = player.getFoodData().getSaturationLevel();
        effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        this.thirstTarget = thirstTarget; this.quenchedTarget = quenchedTarget;
        this.preserveHeld = preserveHeld;
        thirst = thirstTarget >= 0 ? ThirstFixture.snapshot(player) : null;
        if (thirst != null) { int[] levels = ThirstFixture.levels(player);
            thirstBefore = levels[0]; quenchedBefore = levels[1];
        } else { thirstBefore = quenchedBefore = -1; }
    }
    boolean provisionedItem() { return !preserveHeld; }
    void requireOwner(Protocol.Request request) {
        if (!ownerCase.equals(request.caseId()) || !ownerToken.equals(request.token()))
            throw new IllegalArgumentException("fixture belongs to another case/token");
    }
    JsonObject beginReceipt(ServerPlayer player) {
        JsonObject row = identity();
        row.addProperty("item_id", suppliedItem); row.addProperty("count", suppliedCount);
        row.addProperty("hand", hand);
        row.addProperty("food_before", foodBefore); row.addProperty("saturation_before", saturationBefore);
        row.addProperty("food_after", player.getFoodData().getFoodLevel());
        row.addProperty("saturation_after", player.getFoodData().getSaturationLevel());
        row.addProperty("provisioned_item", preserveHeld ? "none" : suppliedItem);
        row.addProperty("provisioned_count", preserveHeld ? 0 : suppliedCount);
        row.addProperty("source_origin", preserveHeld ? "preexisting_unattributed" : "authored_fixture_not_natural");
        JsonArray targets = new JsonArray(); if (!preserveHeld) targets.add(suppliedItem); row.add("provisioned_targets", targets);
        JsonArray mutations = new JsonArray(); if (!preserveHeld) mutations.add("inventory"); mutations.add("food");
        row.add("fixture_mutations", mutations);
        if (thirst != null) {
            try {
                int[] levels = ThirstFixture.levels(player);
                row.addProperty("thirst_before", thirstBefore); row.addProperty("quenched_before", quenchedBefore);
                row.addProperty("thirst_target", thirstTarget); row.addProperty("quenched_target", quenchedTarget);
                row.addProperty("thirst_after", levels[0]); row.addProperty("quenched_after", levels[1]);
                row.addProperty("capability_override_source", "pinned_Thirst_IThirst_author_fixture");
                mutations.add("thirst");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Thirst fixture receipt unavailable", failure); }
        }
        row.addProperty("acquisition_origin", preserveHeld ? "unverified_preexisting_source" : "authored_fixture_not_natural");
        return row;
    }
    JsonObject restore(ServerPlayer player) throws ReflectiveOperationException {
        if (!player.getUUID().equals(playerId) || player.isRemoved() || player.connection == null)
            throw new IllegalArgumentException("owner player is not connected");
        if (player.containerMenu != player.inventoryMenu || player.isUsingItem())
            throw new IllegalStateException("close container/finish item use before restoring fixture");
        JsonObject row = identity();
        row.addProperty("food_before_restore", player.getFoodData().getFoodLevel());
        row.addProperty("saturation_before_restore", player.getFoodData().getSaturationLevel());
        row.addProperty("hand_item_before_restore", itemId(player));
        // Restore all vanilla inventory compartments, not just the provisioned hand. This removes
        // container remainders produced by the action and returns every original stack and NBT.
        for (int i = 0; i < inventory.size(); i++) player.getInventory().setItem(i, inventory.get(i).copy());
        player.getInventory().selected = selected;
        player.getFoodData().readAdditionalSaveData(food.copy());
        player.removeAllEffects();
        for (MobEffectInstance effect : effects) player.addEffect(new MobEffectInstance(effect));
        player.inventoryMenu.broadcastChanges();
        boolean thirstMatches = thirst == null || ThirstFixture.restore(player, thirst);
        row.addProperty("restored_inventory_slots", inventory.size());
        row.addProperty("restored_effects", effects.size());
        row.addProperty("food_after_restore", player.getFoodData().getFoodLevel());
        row.addProperty("saturation_after_restore", player.getFoodData().getSaturationLevel());
        row.addProperty("hand_item_after_restore", itemId(player));
        int mismatchedSlots = 0;
        for (int i = 0; i < inventory.size(); i++)
            if (!ItemStack.matches(inventory.get(i), player.getInventory().getItem(i))) mismatchedSlots++;
        CompoundTag actualFood = new CompoundTag(); player.getFoodData().addAdditionalSaveData(actualFood);
        boolean foodMatches = food.equals(actualFood);
        List<String> originalEffects = effects.stream().map(OwnedFixture::effectTag).sorted().toList();
        List<String> currentEffects = player.getActiveEffects().stream().map(OwnedFixture::effectTag).sorted().toList();
        boolean effectsMatch = originalEffects.equals(currentEffects);
        boolean selectedMatches = player.getInventory().selected == selected;
        int residuals = mismatchedSlots + (foodMatches ? 0 : 1) + (effectsMatch ? 0 : 1)
                + (selectedMatches ? 0 : 1) + (thirstMatches ? 0 : 1);
        row.addProperty("inventory_mismatched_slots", mismatchedSlots);
        row.addProperty("food_restored", foodMatches);
        row.addProperty("effects_restored", effectsMatch);
        row.addProperty("selected_slot_restored", selectedMatches);
        row.addProperty("thirst_restored", thirstMatches);
        row.addProperty("owned_residuals", residuals);
        row.addProperty("cleanup_complete", residuals == 0);
        row.addProperty("vanilla_restored", residuals == 0);
        row.addProperty("baseline_restored", false); // External capabilities/world side effects cannot be verified here.
        JsonArray mutations = new JsonArray(); mutations.add("inventory_restore"); mutations.add("food_restore"); mutations.add("effects_restore");
        if (thirst != null) mutations.add("thirst_restore");
        row.add("fixture_mutations", mutations);
        row.addProperty("acquisition_origin", preserveHeld ? "unverified_preexisting_source" : "authored_fixture_not_natural");
        return row;
    }
    private static String effectTag(MobEffectInstance effect) {
        return effect.save(new CompoundTag()).toString();
    }
    private String itemId(ServerPlayer player) {
        ItemStack stack = hand.equals("main_hand") ? player.getMainHandItem() : player.getOffhandItem();
        return stack.isEmpty() ? "minecraft:air" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }
    private JsonObject identity() {
        JsonObject row = new JsonObject(); row.addProperty("fixture_id", fixtureId);
        row.addProperty("owner_case_id", ownerCase); row.addProperty("player_uuid", playerId.toString());
        return row;
    }
}
