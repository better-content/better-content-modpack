package com.bettercontent.playtestsupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UNRUN pinned original-bytecode / source regression; not a shutdown, unload, world or Mixin delivery test. */
final class ShutdownDrainObserverStructureTest {
    @Test void originalStopServerDrainsAfterTicketRemovalBeforeSaveAndActualReturn() throws Exception {
        List<String> original = calls("net/minecraft/server/MinecraftServer.class", "stopServer", "()V");
        String tickets = "net/minecraft/server/level/ServerChunkCache.removeTicketsOnClosing()V";
        String cacheTick = "net/minecraft/server/level/ServerChunkCache.tick(Ljava/util/function/BooleanSupplier;Z)V";
        String save = "net/minecraft/server/MinecraftServer.saveAllChunks(ZZZ)Z";
        assertEquals(1, original.stream().filter(tickets::equals).count());
        assertEquals(1, original.stream().filter(cacheTick::equals).count());
        assertEquals(1, original.stream().filter(save::equals).count());
        assertTrue(original.indexOf(tickets) < original.indexOf(cacheTick) &&
                original.indexOf(cacheTick) < original.indexOf(save), "real unload drain must precede save");
        List<String> enter = calls("com/bettercontent/playtestsupport/mixin/RealShutdownDrainObserverMixin.class",
                "bc$shutdownDrainEntry", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        List<String> returned = calls("com/bettercontent/playtestsupport/mixin/RealShutdownDrainObserverMixin.class",
                "bc$shutdownDrainReturn", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        assertEquals(List.of("com/bettercontent/playtestsupport/NativeSupport.shutdownDrainEntry(Lnet/minecraft/server/MinecraftServer;)V"), enter);
        assertEquals(List.of("com/bettercontent/playtestsupport/NativeSupport.shutdownDrainReturn(Lnet/minecraft/server/MinecraftServer;)V"), returned);
    }
    @Test void realChunkMapUnloadEventPostsBeforeSaveCallback() throws Exception {
        String resource = "net/minecraft/server/level/ChunkMap.class";
        List<String> unloading = calls(resource, "lambda$scheduleUnload$14", null);
        String event = "net/minecraftforge/eventbus/api/IEventBus.post(Lnet/minecraftforge/eventbus/api/Event;)Z";
        String save = "net/minecraft/server/level/ChunkMap.save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z";
        assertEquals(1, unloading.stream().filter(event::equals).count());
        assertEquals(1, unloading.stream().filter(save::equals).count());
        assertTrue(unloading.indexOf(event) < unloading.indexOf(save), "real Forge unload is before save");
    }
    private static List<String> calls(String resource, String method, String descriptor) throws Exception {
        List<String> list = new ArrayList<>(); int[] count = {0};
        try (var stream = ShutdownDrainObserverStructureTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                              String signature, String[] exceptions) {
                    if (!name.equals(method) || (descriptor != null && !desc.equals(descriptor))) return null;
                    count[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                              String desc, boolean iface) {
                            list.add(owner + "." + called + desc);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, count[0], resource + ":" + method); return list;
    }
}
