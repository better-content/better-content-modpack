package com.bettercontent.playtestsupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Nonshipping, opt-in reporter; only the live GameTest registry and callbacks produce its evidence. */
public final class RepositoryNativeReporter {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static boolean installed;
    private final Path root;
    private final String runId;
    private final String repository;
    private List<String> registered = List.of();
    private final Map<String, String> outcomes = new LinkedHashMap<>();
    private boolean started;

    private RepositoryNativeReporter(Path root, String runId, String repository) {
        this.root = root; this.runId = runId; this.repository = repository;
    }

    /** NativeSupport calls this even when its mailbox is disabled. No property means no registration. */
    public static synchronized void install() {
        if (installed) return;
        String requested = System.getProperty("bc.playtest.repositoryReport", "");
        if (requested.isBlank()) return;
        String run = System.getProperty("bc.playtest.repositoryRunId", "");
        String repo = System.getProperty("bc.playtest.repository", "");
        if (!run.matches("[a-z0-9][a-z0-9_.-]{0,127}") || !repo.matches("[a-z0-9][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("GameTest reporting requires explicit run ID and repository");
        Path path = Path.of(requested).toAbsolutePath().normalize();
        if (!path.isAbsolute() || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IllegalArgumentException("GameTest report directory must exist and be ordinary");
        for (String name : List.of("registration.json", "outcomes.json", "junit.xml"))
            if (Files.exists(path.resolve(name), LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("GameTest report target already exists: " + name);
        MinecraftForge.EVENT_BUS.register(new RepositoryNativeReporter(path, run, repo));
        installed = true;
    }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer))
            throw new IllegalStateException("Repository native reporter requires actual GameTestServer");
        if (started) throw new IllegalStateException("Duplicate GameTest server startup");
        started = true;
        registered = GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::getTestName).sorted().toList();
        if (registered.isEmpty() || registered.size() != new HashSet<>(registered).size())
            throw new IllegalStateException("Empty or duplicate actual GameTest registration");
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("schema", "bc.repository_native_registration.v1");
        identity.put("run_id", runId); identity.put("repository", repository);
        identity.put("captured_at", Instant.now().toString());
        identity.put("source", "GameTestRegistry.getAllTestFunctions");
        identity.put("registered_test_ids", registered);
        identity.put("test_class_names", GameTestRegistry.getAllTestClassNames().stream().sorted().toList());
        identity.put("source_class_mapping", "not_provided_by_public_api");
        try { publish("registration.json", JSON.toJson(identity)); }
        catch (IOException failure) { throw new IllegalStateException("Cannot publish actual GameTest registration", failure); }
        TestReporter logging = new LogTestReporter();
        GlobalTestReporter.replaceWith(new TestReporter() {
            @Override public void onTestSuccess(GameTestInfo info) { logging.onTestSuccess(info); record(info, "passed"); }
            @Override public void onTestFailed(GameTestInfo info) { logging.onTestFailed(info); record(info, "failed"); }
            @Override public void finish() { finishReport(); logging.finish(); }
        });
        LOG.info("GameTest repository report runId={} repository={} registered={}", runId, repository, registered.size());
    }

    private synchronized void record(GameTestInfo info, String status) {
        String name = info.getTestName();
        if (!registered.contains(name) || outcomes.containsKey(name))
            throw new IllegalStateException("Unregistered or repeated native test callback: " + name);
        outcomes.put(name, status);
    }

    private synchronized void finishReport() {
        if (!started || !outcomes.keySet().equals(new HashSet<>(registered)))
            throw new IllegalStateException("GameTest callbacks do not cover complete observed registration");
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("schema", "bc.repository_native_outcomes.v1");
        receipt.put("run_id", runId); receipt.put("repository", repository);
        receipt.put("registered_test_ids", registered);
        receipt.put("outcomes", outcomes);
        receipt.put("finished_at", Instant.now().toString());
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        long failures = outcomes.values().stream().filter("failed"::equals).count();
        xml.append("<testsuite name=\"GameTest\" tests=\"").append(registered.size())
            .append("\" failures=\"").append(failures).append("\" errors=\"0\" skipped=\"0\" timestamp=\"")
            .append(Instant.now()).append("\">\n");
        for (String id : registered) {
            int split = id.lastIndexOf('.');
            String owner = split > 0 ? id.substring(0, split) : repository;
            String method = split > 0 ? id.substring(split + 1) : id;
            xml.append("  <testcase classname=\"").append(escape(owner)).append("\" name=\"")
                .append(escape(method)).append("\">");
            if ("failed".equals(outcomes.get(id))) xml.append("<failure message=\"native GameTest failed\"/>");
            xml.append("</testcase>\n");
        }
        xml.append("</testsuite>\n");
        try {
            publish("outcomes.json", JSON.toJson(receipt));
            publish("junit.xml", xml.toString());
        } catch (IOException failure) { throw new IllegalStateException("Cannot publish actual native GameTest outcomes", failure); }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private void publish(String name, String content) throws IOException {
        Path target = root.resolve(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Cannot overwrite native report: " + target);
        Path pending = Files.createTempFile(root, ".repository-", ".partial");
        try {
            Files.writeString(pending, content + "\n", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(pending); }
    }
}
