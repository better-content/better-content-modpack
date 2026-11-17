package com.bettercontent.playtestsupport;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraftforge.gametest.GameTestHolder;

/** UNRUN disposable-world real-path controls. Later A must bind exact runtime source scope and replay rows.
 * These methods alone do NOT establish complete observer delivery, natural source or safe shared-world use.
 */
@GameTestHolder("better_playtest_native_support")
public final class BlockMutationRealPathControls {
    private BlockMutationRealPathControls() {}
    @GameTest(template = "empty")
    public static void fullChunkAndDirectSectionRestoration(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        LevelChunk chunk = helper.getLevel().getChunkAt(pos);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        int x = pos.getX() & 15, y = pos.getY() & 15, z = pos.getZ() & 15;
        BlockState before = chunk.getBlockState(pos);
        try {
            chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
            chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false); // two transient transitions, not one final-equality check
            if (!section.getBlockState(x, y, z).is(Blocks.STONE)) helper.fail("chunk path did not reach stone");
            section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
            section.setBlockState(x, y, z, Blocks.STONE.defaultBlockState(), false);
            if (!section.getBlockState(x, y, z).is(Blocks.STONE)) helper.fail("direct section path did not restore stone");
        } finally {
            chunk.setBlockState(pos, before, false);
            section.recalcBlockCounts();
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void directContainerWritesAndWholeReadReplacementAreExplicitlyExposureBound(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        LevelChunk chunk = helper.getLevel().getChunkAt(pos);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        PalettedContainer<BlockState> container = section.getStates(); // exposed mutable handle: scope must disclose it
        int x = pos.getX() & 15, y = pos.getY() & 15, z = pos.getZ() & 15;
        BlockState before = section.getBlockState(x, y, z);
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            container.set(x, y, z, Blocks.STONE.defaultBlockState());
            container.getAndSet(x, y, z, Blocks.AIR.defaultBlockState());
            container.getAndSetUnchecked(x, y, z, Blocks.STONE.defaultBlockState());
            if (!section.getBlockState(x, y, z).is(Blocks.STONE)) helper.fail("direct container indexed writes missing");
            LevelChunkSection replacement = new LevelChunkSection(helper.getLevel().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.BIOME));
            replacement.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
            replacement.write(packet);
            section.read(packet); // PalettedContainer.read swaps whole palette data, not one getAndSet event
            if (!section.getBlockState(x, y, z).isAir()) helper.fail("whole palette replacement missing");
        } finally {
            packet.release();
            chunk.setBlockState(pos, before, false);
            section.recalcBlockCounts();
        }
        helper.succeed();
    }
}
