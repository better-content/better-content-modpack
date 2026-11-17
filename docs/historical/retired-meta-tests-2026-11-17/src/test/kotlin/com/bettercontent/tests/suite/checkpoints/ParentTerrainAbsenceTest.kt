package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.BoundFile
import com.bettercontent.tests.suite.CaseImplementation
import com.bettercontent.tests.suite.SuiteCase
import com.bettercontent.tests.suite.suiteMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Pure parent-absence roster shape regressions. No Store/Minecraft/test execution authorized. */
class ParentTerrainAbsenceTest {
    private val parent = CheckpointRef("branch-input", BoundFile("/unrun/parent", "a".repeat(64)))
    private val pos = ScopedBlockPosition("minecraft:overworld", -1, 64, -1)
    private val selector = ReviewedTargetSelector("bc.playtest_target_selector.v1", "fresh.block",
        "b".repeat(64), "minecraft", "target.observe.loaded_v1", "minecraft:stone", parent.id,
        TargetBounds("minecraft:overworld", -1, -1, 64, 64, -1, -1, 4),
        TargetSource.NEWLY_GENERATED_TERRAIN, "loaded_block_nearest_center_then_xyz_v1",
        TargetSelectionOrder.NEAREST_THEN_POSITION, 30, 1)
    private fun spec(target: ReviewedTargetSelector) = SuiteCase("future.birth", emptyList(), "reviewed",
        "campaign", "", emptyList(), emptyList(), emptyList(), 1, 30, emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), CaseImplementation("authored", "", ""),
        targetBindings = listOf(suiteMapper.valueToTree(target)))
    @Test fun `negative block coordinates use floor-div chunk keys and source-only selector bounds`() {
        assertEquals(BirthChunkKey("minecraft:overworld", -1, -1), birthChunkKey(pos))
        assertEquals(listOf(pos), reviewedBirthPositions(listOf(spec(selector)), parent, "b".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) {
            reviewedBirthPositions(listOf(spec(selector.copy(catalogueSha256 = "c".repeat(64)))), parent,
                "b".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            birthChunkKey(pos.copy(dimension = "example:unknown_provider"))
        }
    }
    @Test fun `absent parent roster cannot silently carry unrelated or repeated watched keys`() {
        val key = BirthChunkKey("minecraft:overworld", -1, -1)
        val absent = ParentTerrainAbsence("bc.playtest_parent_terrain_absence.v1", parent, "d".repeat(64),
            listOf(key), listOf(pos), emptyList(), listOf("world/region/r.-1.-1.mca"))
        assertEquals(absent, suiteMapper.readValue(suiteMapper.writeValueAsString(absent),
            ParentTerrainAbsence::class.java))
        assertThrows(IllegalArgumentException::class.java) {
            ParentTerrainAbsence("bc.playtest_parent_terrain_absence.v1", parent, "d".repeat(64),
                listOf(key, key), listOf(pos), emptyList(), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParentTerrainAbsence("bc.playtest_parent_terrain_absence.v1", parent, "d".repeat(64),
                listOf(key, BirthChunkKey("minecraft:overworld", 0, 0)), listOf(pos),
                emptyList(), emptyList())
        }
    }
}
