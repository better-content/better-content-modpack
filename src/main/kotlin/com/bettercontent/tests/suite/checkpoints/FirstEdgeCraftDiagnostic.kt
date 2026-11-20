package com.bettercontent.tests.suite.checkpoints

import com.fasterxml.jackson.databind.JsonNode

/** Diagnostic composition only. A scoped pickup is not proof the block was naturally generated;
 * an event is not proof of physical input or saved/reopened inventory. Never a campaign verdict. */
internal data class FirstEdgeCraftDiagnostic(val consistent: Boolean, val reason: String) {
    val qualificationEligible: Boolean get() = false
}

internal fun compareFirstEdgeCraft(pickup: CapturedScopedPickup, arm: JsonNode,
    receipt: JsonNode): FirstEdgeCraftDiagnostic {
    val valid = runCatching {
        val actor = pickup.declaredContext.actorUuid
        require(pickup.boundTarget.source == TargetSource.NATURAL_UNMODIFIED_BLOCK &&
            pickup.boundTarget.resourceId == "minecraft:oak_log" &&
            pickup.sources.size == 1 && pickup.sources.single().sourceStack.itemId == "minecraft:oak_log" &&
            pickup.sources.single().sourceStack.count == 1L &&
            pickup.sources.single().acquiredStacks.size == 1 &&
            pickup.sources.single().acquiredStacks.single().itemId == "minecraft:oak_log" &&
            pickup.sources.single().acquiredStacks.single().count == 1L)
        val source = pickup.sources.single().sourceStack
        fun wire(node: JsonNode, location: String? = null): NativeWireStack {
            require(node.isObject && node.fieldNames().asSequence().toSet() == setOf("item_id", "count",
                "canonical_algorithm", "canonical_sha256", "content_algorithm", "content_sha256",
                "provider_scope") + (if (location == null) emptySet() else setOf(location)))
            require(node.path("canonical_algorithm").asText() == "bc.playtest.stack-nbt.v1" &&
                node.path("content_algorithm").asText() == "bc.playtest.stack-content.v1" &&
                node.path("count").isIntegralNumber && node.path("count").canConvertToLong())
            return NativeWireStack(node.path("item_id").asText(), node.path("count").asLong(),
                node.path("canonical_sha256").asText(), node.path("content_sha256").asText(),
                node.path("provider_scope").asText())
        }
        fun inventory(node: JsonNode): Map<Int, NativeWireStack> {
            require(node.isArray && node.size() <= 64)
            val slots = linkedMapOf<Int, NativeWireStack>()
            node.forEach { row ->
                val slot = row.path("slot")
                require(slot.isIntegralNumber && slot.canConvertToInt() && slot.asInt() in 0..63)
                require(slots.putIfAbsent(slot.asInt(), wire(row, "slot")) == null)
            }
            return slots
        }
        val origin = pickup.declaredContext
        val craftContext = arm.path("controller_declared_arm_context")
        require(origin.purpose == "diagnostic" && craftContext.isObject &&
            craftContext.path("campaign_id").asText() == origin.campaignId &&
            craftContext.path("lineage_id").asText() == origin.lineageId &&
            craftContext.path("epoch_id").asText() == origin.epochId &&
            craftContext.path("native_session_id").asText() == origin.nativeSessionId &&
            craftContext.path("branch_id").asText() == origin.branchId &&
            craftContext.path("case_id").asText() == origin.caseId &&
            craftContext.path("trial_id").asText() == origin.trialId &&
            craftContext.path("actor_uuid").asText() == origin.actorUuid &&
            craftContext.path("source").asText() == "server" &&
            craftContext.path("purpose").asText() == "diagnostic" &&
            craftContext.path("capability_id").asText() == "player.observe_craft.arm_v1" &&
            craftContext.path("action_ordinal").isIntegralNumber &&
            craftContext.path("action_ordinal").asLong() > origin.actionOrdinal &&
            craftContext.path("request_sequence").isIntegralNumber &&
            craftContext.path("request_sequence").asLong() > origin.requestSequence)
        require(arm.path("schema").asText() == "bc.playtest_passive_craft_arm.v1" &&
            arm.path("actor_uuid").asText() == actor &&
            arm.path("dimension").asText() == pickup.boundTarget.dimension &&
            arm.path("observer_id").isTextual &&
            arm.path("arm_request_sequence").isIntegralNumber &&
            arm.path("arm_action_ordinal").isIntegralNumber &&
            arm.path("arm_action_ordinal").asLong() == craftContext.path("action_ordinal").asLong() &&
            arm.path("arm_request_sequence").asLong() == craftContext.path("request_sequence").asLong() &&
            arm.path("armed_before_input").isBoolean && arm.path("armed_before_input").asBoolean() &&
            arm.path("source_ancestry_proven_by_native").isBoolean &&
            !arm.path("source_ancestry_proven_by_native").asBoolean())
        require(receipt.path("schema").asText() == "bc.playtest_passive_craft_event.v1" &&
            receipt.path("actor_uuid") == arm.path("actor_uuid") &&
            receipt.path("dimension") == arm.path("dimension") &&
            receipt.path("observer_id") == arm.path("observer_id") &&
            receipt.path("arm_request_sequence") == arm.path("arm_request_sequence") &&
            receipt.path("arm_action_ordinal") == arm.path("arm_action_ordinal") &&
            receipt.path("controller_declared_arm_context") == arm.path("controller_declared_arm_context") &&
            receipt.path("context_certified_by_native").isBoolean &&
            !receipt.path("context_certified_by_native").asBoolean() &&
            receipt.path("event_source").asText() == "PlayerEvent.ItemCraftedEvent/MONITOR" &&
            receipt.path("event_count").isIntegralNumber && receipt.path("event_count").asInt(-1) == 1 &&
            receipt.path("narrow_event_observed").isBoolean && receipt.path("narrow_event_observed").asBoolean() &&
            receipt.path("physical_client_click_bound").isBoolean &&
            !receipt.path("physical_client_click_bound").asBoolean() &&
            receipt.path("source_ancestry_proven_by_native").isBoolean &&
            !receipt.path("source_ancestry_proven_by_native").asBoolean() &&
            receipt.path("saved_reopened_acquisition_proven").isBoolean &&
            !receipt.path("saved_reopened_acquisition_proven").asBoolean() &&
            !receipt.has("unsupported_reason"))
        val armed = receipt.path("arm_tick"); val event = receipt.path("event_tick")
        val applied = receipt.path("applied_tick")
        require(listOf(armed, event, applied).all { it.isIntegralNumber && it.canConvertToLong() } &&
            armed.asLong() <= event.asLong() && event.asLong() <= applied.asLong() &&
            receipt.path("expected_output_id").asText() == "minecraft:oak_planks" &&
            receipt.path("expected_output_count").isIntegralNumber && receipt.path("expected_output_count").asInt(-1) == 4)
        val prior = inventory(receipt.path("inventory_before"))
        val after = inventory(receipt.path("inventory_after_tick"))
        require(prior == pickup.finalVanillaSlots)
        val grid = receipt.path("grid_at_event")
        require(grid.isArray && grid.size() == 1)
        val gridRow = grid[0]
        require(gridRow.path("grid_slot").isIntegralNumber && gridRow.path("grid_slot").asInt(-1) in 0..8)
        val ingredient = wire(gridRow, "grid_slot")
        require(ingredient.itemId == source.itemId && ingredient.sameContent(source) && ingredient.count == 1L)
        val output = wire(receipt.path("output_at_event"))
        require(output.itemId == "minecraft:oak_planks" && output.count == 4L)
        val cursor = wire(receipt.path("cursor_after_tick"))
        require(cursor == output)
        val gainedSlots = pickup.finalVanillaSlots.filter { (slot, stack) ->
            val beforePickup = pickup.initialVanillaSlots[slot]
            stack.sameContent(source) &&
                (beforePickup == null && stack.count == 1L ||
                    beforePickup != null && beforePickup.sameContent(stack) &&
                        stack.count == beforePickup.count + 1L &&
                        stack.canonicalSha256 != beforePickup.canonicalSha256)
        }
        require(gainedSlots.size == 1)
        val expectedAfter = prior.toMutableMap()
        // This narrow first edge consumes one log from one pre-existing vanilla slot, never an
        // external backpack/Curios slot, a fixture grant, or a merged unobserved output.
        val spent = gainedSlots.entries.filter { (slot, stack) ->
            val remaining = after[slot]
            stack.count == 1L && remaining == null ||
                stack.count > 1L && remaining != null && remaining.sameContent(stack) &&
                    remaining.count == stack.count - 1L && remaining.canonicalSha256 != stack.canonicalSha256
        }
        require(spent.size == 1)
        val (spentSlot, spentStack) = spent.single()
        if (spentStack.count == 1L) expectedAfter.remove(spentSlot)
        else expectedAfter[spentSlot] = requireNotNull(after[spentSlot])
        require(after == expectedAfter && after.values.none { it.itemId == "minecraft:oak_planks" })
        true
    }.getOrDefault(false)
    return FirstEdgeCraftDiagnostic(valid, if (valid)
        "one scoped pickup content matched a same-dimension passive craft event, acquired vanilla slot debit and cursor; continuous object ancestry, physical click, natural block origin and saved/reopened proof still missing"
    else "first-edge scoped pickup/craft event custody incomplete or contradictory")
}
