package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.suiteMapper
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Source-only negative/positive wire checks; no world, test or fixture was executed. */
class VanillaReopenPersistenceTest {
    private val actor = "00000000-0000-0000-0000-000000000027"
    private val digest = "a".repeat(64)
    private val savedStack = SealedInventoryStack(5, "minecraft:stone", 3,
        STACK_NBT_HASH_ALGORITHM, digest, STACK_CONTENT_HASH_ALGORITHM, "b".repeat(64))
    private val sealed = SealedPlayerInventory("onboarding", actor, BoundFile("/unused/playerdata.dat", digest),
        listOf(savedStack), 0, listOf(savedStack))
    private fun snapshot(): JsonNode = suiteMapper.valueToTree(mapOf(
        "uuid" to actor, "mode" to "survival", "synthetic_scale_participant" to false,
        "inventory_slots_v1" to mapOf(
            "schema" to "bc.playtest_vanilla_inventory_slots.v1",
            "source" to "actual_server_player_vanilla_inventory_0_40",
            "external_capabilities_included" to false, "sealed_playerdata_proven_by_native" to false,
            "slots" to (0..40).map { slot ->
                mapOf<String, Any?>("slot" to slot, "empty" to (slot != 5),
                    "item" to if (slot == 5) "minecraft:stone" else null,
                    "count" to if (slot == 5) 3 else 0,
                    "stack_canonical_algorithm" to if (slot == 5) STACK_NBT_HASH_ALGORITHM else null,
                    "stack_canonical_sha256" to if (slot == 5) digest else null)
            })))
    @Test fun `all explicit vanilla slots and independently saved mode agree`() {
        verifyLiveVanillaSavedSlots(sealed, snapshot(), actor)
    }
    @Test fun `missing or forged empty and Count drift cannot pass fresh load`() {
        val missing = snapshot().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (missing.path("inventory_slots_v1").path("slots") as com.fasterxml.jackson.databind.node.ArrayNode).remove(40)
        assertThrows(IllegalArgumentException::class.java) { verifyLiveVanillaSavedSlots(sealed, missing, actor) }
        val forged = snapshot().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (forged.path("inventory_slots_v1").path("slots")[4] as com.fasterxml.jackson.databind.node.ObjectNode)
            .put("stack_canonical_sha256", digest)
        assertThrows(IllegalArgumentException::class.java) { verifyLiveVanillaSavedSlots(sealed, forged, actor) }
        val drift = snapshot().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (drift.path("inventory_slots_v1").path("slots")[5] as com.fasterxml.jackson.databind.node.ObjectNode)
            .put("count", 4)
        assertThrows(IllegalArgumentException::class.java) { verifyLiveVanillaSavedSlots(sealed, drift, actor) }
    }
    @Test fun `live mode cannot substitute for sealed Survival and actor must match`() {
        val changed = snapshot().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        changed.put("mode", "creative")
        assertThrows(IllegalArgumentException::class.java) { verifyLiveVanillaSavedSlots(sealed, changed, actor) }
        assertThrows(IllegalArgumentException::class.java) {
            verifyLiveVanillaSavedSlots(sealed, snapshot(), "00000000-0000-0000-0000-000000000028")
        }
    }
}
