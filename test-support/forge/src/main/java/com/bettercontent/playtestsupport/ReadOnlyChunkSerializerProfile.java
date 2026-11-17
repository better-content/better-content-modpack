package com.bettercontent.playtestsupport;

import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.EnumSet;
import java.util.Iterator;

/** Exact pinned class/descriptor caller profile; not a class-name or namespace trust rule. */
final class ReadOnlyChunkSerializerProfile {
    private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.of(
            Option.RETAIN_CLASS_REFERENCE, Option.SHOW_REFLECT_FRAMES, Option.SHOW_HIDDEN_FRAMES));
    private static final int MAX_FRAMES = 32;
    private static final String SECTIONS = "()[Lnet/minecraft/world/level/chunk/LevelChunkSection;";
    private static final String STATES = "()Lnet/minecraft/world/level/chunk/PalettedContainer;";
    private static final String SECTION = "(I)Lnet/minecraft/world/level/chunk/LevelChunkSection;";
    private static final String WRITE = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;";
    private static final String TICK_CHUNK = "(Lnet/minecraft/world/level/chunk/LevelChunk;I)V";
    private static final String CHUNK_SIZE = "(Lnet/minecraft/world/level/chunk/LevelChunk;)I";
    private static final String CHUNK_DATA = "(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/level/chunk/LevelChunk;)V";
    private static final Class<?> BIOME_PACKET = ClientboundChunksBiomesPacket.ChunkBiomeData.class;
    private ReadOnlyChunkSerializerProfile() {}

    /** Call only AFTER the watched identity filter. Stop at the first matching getter; never allocate a full stack. */
    static boolean readOnly(String accessor) {
        if (!accessor.equals("getSections") && !accessor.equals("getStates")) return false;
        return WALKER.walk(stream -> {
            Iterator<StackFrame> frames = stream.limit(MAX_FRAMES).iterator();
            while (frames.hasNext()) {
                StackFrame getter = frames.next();
                // If a similarly named shadow/wrapper appears first, refuse rather than skip towards a trusted frame.
                if (!getter.getMethodName().equals(accessor)) continue;
                if (!frames.hasNext()) return false;
                StackFrame caller = frames.next();
                return exactPair(accessor, getter.getDeclaringClass(), getter.getMethodName(), getter.getDescriptor(),
                        caller.getDeclaringClass(), caller.getMethodName(), caller.getDescriptor());
            }
            return false; // truncated/missing getter cannot establish a direct callsite
        });
    }

    /** Pure exact-identity/descriptor decision used by the live StackWalker and unrun negative regressions. */
    static boolean exactPair(String accessor, Class<?> getterClass, String getterMethod, String getterDescriptor,
                             Class<?> callerClass, String callerMethod, String callerDescriptor) {
        if (accessor.equals("getSections") && getterClass == ChunkAccess.class &&
                getterMethod.equals("getSections") && getterDescriptor.equals(SECTIONS))
            return (callerClass == ChunkSerializer.class && callerMethod.equals("write") &&
                    callerDescriptor.equals(WRITE)) || (callerClass == ChunkAccess.class &&
                    callerMethod.equals("getSection") && callerDescriptor.equals(SECTION)) ||
                    (callerClass == ServerLevel.class && callerMethod.equals("tickChunk") &&
                    callerDescriptor.equals(TICK_CHUNK)) ||
                    ((callerClass == ClientboundLevelChunkPacketData.class || callerClass == BIOME_PACKET) &&
                    ((callerMethod.equals("calculateChunkSize") && callerDescriptor.equals(CHUNK_SIZE)) ||
                     (callerMethod.equals("extractChunkData") && callerDescriptor.equals(CHUNK_DATA))));
        return accessor.equals("getStates") && getterClass == LevelChunkSection.class &&
                getterMethod.equals("getStates") && getterDescriptor.equals(STATES) &&
                callerClass == ChunkSerializer.class && callerMethod.equals("write") && callerDescriptor.equals(WRITE);
    }
}
