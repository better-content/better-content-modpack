package com.bettercontent.playtest.repositoryinput;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Nonshipping agent: records actual TEST JVM/JavaExec worker process and class code sources.
 * No recipe/gameplay mutation, Gradle parent PID substitution, network or test execution here. */
public final class RepositoryInputUseAgent {
    private RepositoryInputUseAgent() { }
    public static void premain(String configFile, Instrumentation instrumentation) throws Exception {
        Path config = Path.of(configFile).toAbsolutePath().normalize();
        if (!Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(config))
            throw new IOException("Input-use controller binding missing");
        Properties p = new Properties();
        try (var input = Files.newInputStream(config)) { p.load(input); }
        for (String key : List.of("output", "task", "run_id", "repository", "reviewed_plan_sha256",
                "producer_sha256")) {
            if (p.getProperty(key, "").isBlank()) throw new IOException("Missing input-use binding: " + key);
        }
        // The controller independently records and rechecks the complete config file digest.
        Path output = Path.of(p.getProperty("output")).toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output))
            throw new IOException("Input-use result already exists");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { publish(p, output, instrumentation); }
            catch (Throwable failure) { System.err.println("Repository input-use worker failed closed: " + failure); }
        }, "repository-input-use-observer"));
    }
    private static void publish(Properties p, Path output, Instrumentation instrumentation) throws Exception {
        ProcessHandle current = ProcessHandle.current();
        Instant birth = current.info().startInstant().orElseThrow(() -> new IOException("Worker birth missing"));
        String command = current.info().command().orElseThrow(() -> new IOException("Worker executable missing"));
        Path executable = Path.of(command).toRealPath();
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Worker executable not a regular file");
        Set<Path> codes = new HashSet<>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (type == null || type.getProtectionDomain() == null || type.getProtectionDomain().getCodeSource() == null ||
                type.getProtectionDomain().getCodeSource().getLocation() == null) continue;
            URI uri = type.getProtectionDomain().getCodeSource().getLocation().toURI();
            if (!"file".equals(uri.getScheme())) continue;
            codes.add(Path.of(uri).toRealPath());
            if (codes.size() > 4096) throw new IOException("Unbounded test-worker class code-source roster");
        }
        List<String> entries = new ArrayList<>();
        for (Path path : codes.stream().sorted(Comparator.comparing(Path::toString)).toList()) entries.add(describe(path));
        Set<Path> classPath = new HashSet<>();
        for (String element : System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (!element.isBlank()) classPath.add(Path.of(element).toAbsolutePath().toRealPath());
        }
        if (classPath.isEmpty() || classPath.size() > 4096) throw new IOException("Missing or unbounded actual JVM classpath");
        List<String> classPathEntries = new ArrayList<>();
        for (Path path : classPath.stream().sorted(Comparator.comparing(Path::toString)).toList())
            classPathEntries.add(describe(path));
        String json = "{\"schema\":\"bc.repository_input_use_worker.v2\"," +
            "\"scope\":\"source-only-not-packaged-runtime\"," +
            field("run_id", p.getProperty("run_id")) + "," + field("repository", p.getProperty("repository")) + "," +
            field("task", p.getProperty("task")) + "," + field("reviewed_plan_sha256", p.getProperty("reviewed_plan_sha256")) + "," +
            field("producer_sha256", p.getProperty("producer_sha256")) + "," +
            "\"observed_process_pid\":" + current.pid() + "," + field("observed_process_start", birth.toString()) + "," +
            field("captured_at", Instant.now().toString()) + "," +
            field("java_executable", executable.toString()) + "," + field("java_executable_sha256", sha256(executable)) + "," +
            field("java_version", System.getProperty("java.version")) + "," +
            "\"code_sources\":[" + String.join(",", entries) + "]," +
            "\"class_path\":[" + String.join(",", classPathEntries) + "]}\n";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8_388_608) throw new IOException("Input-use worker report too large");
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static String describe(Path path) throws Exception {
        if (Files.isSymbolicLink(path)) throw new IOException("Linked code source");
        boolean file = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        if (!file && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsupported classpath/code source");
        return "{\"path\":" + quote(path.toString()) + ",\"sha256\":" + (file ? quote(sha256(path)) : "null") +
            ",\"kind\":" + quote(file ? "file" : "directory") + "}";
    }
    private static String field(String key, String value) { return quote(key) + ":" + quote(value); }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private static String sha256(Path file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
        }
        return java.util.HexFormat.of().formatHex(hash.digest());
    }
}
