package com.bettercontent.playtestsupport;

import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN pinned-bytecode read-only exception; rejects anonymous namespace-wide serializer trust. */
final class ReadOnlyChunkSerializerProfileTest {
    private static final String SECTIONS = "()[Lnet/minecraft/world/level/chunk/LevelChunkSection;";
    private static final String STATES = "()Lnet/minecraft/world/level/chunk/PalettedContainer;";
    private static final String SECTION = "(I)Lnet/minecraft/world/level/chunk/LevelChunkSection;";
    private static final String WRITE = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;";
    private static final String TICK = "(Lnet/minecraft/world/level/chunk/LevelChunk;I)V";
    private static final String SIZE = "(Lnet/minecraft/world/level/chunk/LevelChunk;)I";
    private static final String DATA = "(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/level/chunk/LevelChunk;)V";
    @Test void onlyExactActualClassAndDescriptorDirectCallsCanAvoidMutableEscape() throws Exception {
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ChunkSerializer.class, "write", WRITE));
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getStates", LevelChunkSection.class, "getStates", STATES,
                ChunkSerializer.class, "write", WRITE));
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ChunkAccess.class, "getSection", SECTION));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getStates", LevelChunkSection.class, "getStates", STATES,
                ChunkSerializer.class, "read", WRITE));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getStates", LevelChunkSection.class, "getStates", STATES,
                ChunkSerializer.class, "write", "(Ljava/lang/Object;)V"));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", "()Ljava/lang/Object;",
                ChunkSerializer.class, "write", WRITE));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                Object.class, "write", WRITE)); // a wrapper cannot borrow the trusted name
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ChunkAccess.class, "getSection", "()Ljava/lang/Object;"));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("unknown", ChunkAccess.class, "getSections", SECTIONS,
                ChunkSerializer.class, "write", WRITE));
        assertFalse(ReadOnlyChunkSerializerProfile.readOnly("getSections")); // test frame has no actual getter
        byte[] original;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "net/minecraft/world/level/chunk/storage/ChunkSerializer.class")) {
            assertNotNull(stream); original = stream.readAllBytes();
        }
        var isolated = new ClassLoader(ChunkSerializer.class.getClassLoader()) {
            Class<?> duplicate(byte[] bytes) {
                return defineClass("net.minecraft.world.level.chunk.storage.ChunkSerializer", bytes, 0, bytes.length);
            }
        };
        Class<?> shadow = isolated.duplicate(original);
        assertEquals(ChunkSerializer.class.getName(), shadow.getName());
        assertNotSame(ChunkSerializer.class, shadow);
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                shadow, "write", WRITE)); // same binary name, DIFFERENT loaded Class identity
    }
    @Test void pinnedNormalTickAndOutboundPacketArrayReadsAreOnlyExactDirectServerThreadCandidates() throws Exception {
        String getter = "net/minecraft/world/level/chunk/LevelChunk.getSections" + SECTIONS;
        for (var profile : List.of(
                new String[]{"net/minecraft/server/level/ServerLevel.class", "tickChunk", TICK},
                new String[]{"net/minecraft/network/protocol/game/ClientboundLevelChunkPacketData.class", "calculateChunkSize", SIZE},
                new String[]{"net/minecraft/network/protocol/game/ClientboundLevelChunkPacketData.class", "extractChunkData", DATA},
                new String[]{"net/minecraft/network/protocol/game/ClientboundChunksBiomesPacket$ChunkBiomeData.class", "calculateChunkSize", SIZE},
                new String[]{"net/minecraft/network/protocol/game/ClientboundChunksBiomesPacket$ChunkBiomeData.class", "extractChunkData", DATA})) {
            List<String> callsites = calls(profile[0], profile[1], profile[2]);
            assertEquals(1, callsites.stream().filter(getter::equals).count(), profile[0] + "." + profile[1]);
            verifyDirectArrayLocalOnlyFeedsLengthOrSectionRead(profile[0], profile[1], profile[2],
                    profile[1].equals("tickChunk") ? 8 : 2);
        }
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ServerLevel.class, "tickChunk", TICK));
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ClientboundLevelChunkPacketData.class, "calculateChunkSize", SIZE));
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ClientboundLevelChunkPacketData.class, "extractChunkData", DATA));
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ClientboundChunksBiomesPacket.ChunkBiomeData.class, "calculateChunkSize", SIZE));
        assertTrue(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ClientboundChunksBiomesPacket.ChunkBiomeData.class, "extractChunkData", DATA));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ServerLevel.class, "tickChunk", "(Ljava/lang/Object;I)V"));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", LevelChunk.class, "getSections", SECTIONS,
                ServerLevel.class, "tickChunk", TICK));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ServerLevel.class, "lambda$tickChunk$0", TICK));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                ClientboundLevelChunkPacketData.class, "extractChunkData", SIZE));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getSections", ChunkAccess.class, "getSections", SECTIONS,
                Object.class, "calculateChunkSize", SIZE));
        assertFalse(ReadOnlyChunkSerializerProfile.exactPair("getStates", LevelChunkSection.class, "getStates", STATES,
                ServerLevel.class, "tickChunk", TICK));
    }
    @Test void pinnedSerializerWriteHasExactlyOneDirectGetterPerKindAndStatesFeedOnlyCodec() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/level/chunk/storage/ChunkSerializer.class");
        assertNotNull(resource);
        List<String> calls = new ArrayList<>(); int[] writes = {0};
        try (resource) {
            new ClassReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                              String signature, String[] exceptions) {
                    if (!name.equals("write") || !descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;")) return null;
                    writes[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                              String desc, boolean iface) {
                            if (desc.contains("[Lnet/minecraft/world/level/chunk/LevelChunkSection;") &&
                                    !(owner.equals("net/minecraft/world/level/chunk/ChunkAccess") && method.equals("getSections")))
                                fail("serializer passes mutable section array through an unreviewed call");
                            calls.add(owner + "." + method + desc);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, writes[0]);
        String sections = "net/minecraft/world/level/chunk/ChunkAccess.getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;";
        String states = "net/minecraft/world/level/chunk/LevelChunkSection.getStates()Lnet/minecraft/world/level/chunk/PalettedContainer;";
        assertEquals(1, calls.stream().filter(sections::equals).count());
        assertEquals(1, calls.stream().filter(states::equals).count());
        int i = calls.indexOf(states);
        assertTrue(i >= 0 && i + 1 < calls.size());
        assertEquals("com/mojang/serialization/Codec.encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", calls.get(i + 1));
        List<String> internal = calls("net/minecraft/world/level/chunk/ChunkAccess.class", "getSection", SECTION);
        assertEquals(1, internal.stream().filter(sections::equals).count());
    }
    /** Pinned original-bytecode negative: exposing array as argument, field, return or write fails this review. */
    private static void verifyDirectArrayLocalOnlyFeedsLengthOrSectionRead(String resource, String method,
                                                                          String descriptor, int local) throws Exception {
        int[] reviewed = {0};
        try (var input = ReadOnlyChunkSerializerProfileTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                              String signature, String[] exceptions) {
                    if (!name.equals(method) || !desc.equals(descriptor)) return null;
                    reviewed[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        boolean seenGetter, storeNext, arrayOnStack, arrayIndexOnStack;
                        int reads;
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                              String desc, boolean iface) {
                            assertFalse(storeNext || arrayOnStack, "mutable section array passed to method");
                            if (called.equals("getSections") && desc.equals(SECTIONS)) {
                                assertEquals("net/minecraft/world/level/chunk/LevelChunk", owner);
                                seenGetter = true; storeNext = true;
                            }
                        }
                        @Override public void visitVarInsn(int opcode, int slot) {
                            if (storeNext) {
                                assertEquals(Opcodes.ASTORE, opcode);
                                assertEquals(local, slot);
                                storeNext = false; return;
                            }
                            if (arrayOnStack) {
                                assertEquals(Opcodes.ILOAD, opcode, "array may only be indexed before AALOAD");
                                assertFalse(arrayIndexOnStack);
                                arrayIndexOnStack = true; return;
                            }
                            if (seenGetter && slot == local && opcode == Opcodes.ALOAD) { arrayOnStack = true; reads++; }
                            else assertFalse(seenGetter && slot == local && opcode == Opcodes.ASTORE,
                                    "array local overwritten after initial getter");
                        }
                        @Override public void visitInsn(int opcode) {
                            if (arrayOnStack) {
                                assertTrue(opcode == (arrayIndexOnStack ? Opcodes.AALOAD : Opcodes.ARRAYLENGTH),
                                        "mutable section array must feed only ARRAYLENGTH or AALOAD");
                                arrayOnStack = false; arrayIndexOnStack = false;
                            } else if (seenGetter) assertNotEquals(Opcodes.AASTORE, opcode, "unknown section array write");
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                            assertFalse(storeNext || arrayOnStack, "mutable array escaped into field access");
                        }
                        @Override public void visitEnd() {
                            assertFalse(storeNext || arrayOnStack);
                            assertEquals(2, reads, "exactly length and section iteration reads");
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, reviewed[0]);
    }
    private static List<String> calls(String resource, String method, String descriptor) throws Exception {
        List<String> calls = new ArrayList<>();
        try (var input = ReadOnlyChunkSerializerProfileTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals(method) || !desc.equals(descriptor)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String called, String signature, boolean iface) {
                            calls.add(owner + "." + called + signature);
                        }
                    };
                }
            }, 0);
        }
        return calls;
    }
}
