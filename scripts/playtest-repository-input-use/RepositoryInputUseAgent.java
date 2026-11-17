package com.bettercontent.playtest.repositoryinput;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.jar.JarFile;

/** Nonshipping, read-only TEST/JavaExec worker probe. V3 is a bounded class-definition-stage
 * observation, NOT a syscall audit, final post-transform byte proof, or packaged-runtime probe.
 * Unsupported Forge union/secure-JAR sources and uncontrolled transformers fail closed. */
public final class RepositoryInputUseAgent {
    private static final int MAX_CLASSES = 65_536, MAX_FILES = 65_536, MAX_CONFIG = 16_384;
    private static final int MAX_REPORT = 33_554_432, MAX_STREAM = 67_108_864, MAX_EVENT = 4096;
    // The separate controller-created MAP_SHARED health page tracks attempted/committed frames
    // and sticky observer errors through process exit. Only the controller can accept it.
    private static final List<ClassRow> classes = new ArrayList<>();
    private static final Set<String> unsupported = new HashSet<>();
    private static final ReferenceQueue<ClassLoader> retiredLoaders = new ReferenceQueue<>();
    private static final Map<LoaderRef, Integer> loaderIds = new HashMap<>();
    private static final AtomicInteger nextLoaderId = new AtomicInteger(1), bootstrap = new AtomicInteger();
    /** Weak reference uses identity semantics, never user-defined ClassLoader.equals/hashCode.
     * IDs are monotonic and never recycled even after an unloaded loader is collected. */
    private static final class LoaderRef extends WeakReference<ClassLoader> {
        final int identityHash;
        LoaderRef(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {
            super(loader, queue); identityHash = System.identityHashCode(loader);
        }
        @Override public int hashCode() { return identityHash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LoaderRef ref)) return false;
            ClassLoader live = get();
            return live != null && live == ref.get();
        }
    }
    /** Each frame is an independently bounded, forced, newline-delimited JSON record. This
     * writer is deliberately NEVER closed by our shutdown hook: other hooks can define classes.
     * The controller may capture it only after the exact owned worker process has exited. */
    // Offsets are fixed by the controller/worker v1 page contract; 8-byte counters aligned.
    private static void healthIncrement(int offset) {
        long value = healthPage.getLong(offset);
        if (value == Long.MAX_VALUE) throw new IllegalStateException("Health counter exhausted");
        healthPage.putLong(offset, value + 1L);
    }
    private static void initHealth(Path path, String nonce, String configSha) throws Exception {
        if (!System.getProperty("os.name", "").equals("Linux") ||
            !System.getProperty("os.arch", "").equals("amd64") ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) ||
            Files.size(path) != 4096) throw new IOException("Unsupported worker shared health mapping platform/file");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            healthPage = channel.map(FileChannel.MapMode.READ_WRITE, 0, 4096);
        }
        byte[] header = new byte[128];
        healthPage.get(0, header);
        String magic = new String(header, 0, 16, StandardCharsets.US_ASCII);
        String owner = new String(header, 16, 36, StandardCharsets.US_ASCII);
        String binding = new String(header, 64, 64, StandardCharsets.US_ASCII);
        if (!"BC_REPO_HEALTH1!".equals(magic) || !nonce.equals(owner) || !configSha.equals(binding) ||
            healthPage.getInt(184) != 0 || healthPage.getLong(144) != 0 || healthPage.getLong(152) != 0 ||
            healthPage.getLong(160) != 0 || healthPage.getLong(168) != 0 || healthPage.getLong(176) != 0)
            throw new IOException("Altered/previously used controller health page");
        healthPage.putLong(128, ProcessHandle.current().pid());
        healthPage.putLong(136, ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli());
        healthPage.putInt(184, 1);
        // No per-definition health page force: MAP_SHARED is retained/read AFTER process exit.
    }
    private static void append(String body) throws IOException {
        if (streamChannel == null || streamFailed) {
            if (healthPage != null) healthIncrement(160);
            throw new IOException("Worker event collector unavailable");
        }
        String line = "{\"sequence\":" + streamSequence + "," + body + "}\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_EVENT || streamLength + bytes.length > MAX_STREAM || streamSequence >= 65_540) {
            healthIncrement(168); healthIncrement(160);
            streamFailed = true; throw new IOException("Worker event stream bounded budget exhausted");
        }
        healthIncrement(144); // record attempt BEFORE the primary append
        try {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) {
                if (streamChannel.write(data) <= 0) throw new IOException("No progress appending worker frame");
            }
            streamLength += bytes.length; streamSequence++;
            healthIncrement(152); // committed only after the complete frame is in the stream
        } catch (Throwable failure) {
            healthIncrement(160); streamFailed = true;
            if (failure instanceof IOException io) throw io;
            throw new IOException("Collector callback/health failure", failure);
        }
    }
    private static void incomplete(String reason) {
        if (healthPage != null) healthIncrement(160); // before metadata allocation/mutation
        unsupported.add(reason);
        if (streamChannel != null && !streamFailed) try {
            append("\"kind\":\"error\"," + field("reason", reason));
        } catch (IOException failure) { streamFailed = true; }
    }
    private static int loaderId(ClassLoader loader) {
        Reference<? extends ClassLoader> stale;
        while ((stale = retiredLoaders.poll()) != null) loaderIds.remove(stale);
        Integer prior = loaderIds.get(new LoaderRef(loader, null));
        if (prior != null) return prior;
        int next = nextLoaderId.getAndIncrement();
        if (next > 65_536) { incomplete("loader-identity-limit"); return 0; }
        loaderIds.put(new LoaderRef(loader, retiredLoaders), next);
        return next;
    }
    private static List<Entry> initialClassPath = List.of(), initialModulePath = List.of(), initialJdk = List.of();
    private static List<Entry> initialAgents = List.of(), initialTaskInputs = List.of();
    private static String initialTaskPolicy;
    private static String initialJarPolicy, initialJarVersion;
    private static Set<String> jdkModuleNames = Set.of();
    private static int initialJarRuntimeFeature;
    private static Path taskRoster;
    private static String taskRosterSha;
    private static final Set<String> unionSources = new HashSet<>();
    private static final Set<Module> openedUnionModules = new HashSet<>();
    private static FileChannel streamChannel;
    private static MappedByteBuffer healthPage;
    private static Path healthPath;
    private static String healthNonce;
    private static Path streamPath;
    private static long streamLength;
    private static int streamSequence;
    private static boolean streamFailed, hookReportWritten;
    private static String streamNonce;
    private static String unionHandler, unionHandlerSha;
    private static Instrumentation observerInstrumentation;
    private record ClassRow(String name, int loader, String path, String bytesSha256, String selectedEntry) { }
    private record Entry(Path path, String kind, String sha, int files, long sizeBytes) { }
    private RepositoryInputUseAgent() { }

    public static void premain(String argument, Instrumentation instrumentation) throws Exception {
        int split = argument == null ? -1 : argument.lastIndexOf('@');
        if (split < 1 || !argument.substring(split + 1).matches("[0-9a-f]{64}"))
            throw new IOException("Worker config must have controller-pinned SHA-256");
        Path config = Path.of(argument.substring(0, split)).toAbsolutePath().normalize();
        byte[] binding = capture(config, MAX_CONFIG);
        String configSha = sha256(binding);
        if (!configSha.equals(argument.substring(split + 1))) throw new IOException("Worker config changed before premain");
        Properties p = properties(binding);
        Set<String> keys = new HashSet<>(Set.of("output", "event_stream", "health_page", "health_nonce", "task", "run_id", "repository", "reviewed_plan_sha256", "producer_sha256"));
        if (p.containsKey("union_access_profile")) {
            keys.addAll(Set.of("union_handler", "union_handler_sha256", "union_access_profile"));
            if (!"securejarhandler-2.1.10-single-base-open-v1".equals(p.getProperty("union_access_profile")) ||
                !p.getProperty("union_handler_sha256", "").matches("[0-9a-f]{64}"))
                throw new IOException("Unreviewed union module access profile");
            unionHandler = Path.of(p.getProperty("union_handler")).toRealPath().toString();
            unionHandlerSha = p.getProperty("union_handler_sha256");
            if (!sha256(capture(Path.of(unionHandler), 268_435_456)).equals(unionHandlerSha))
                throw new IOException("Union handler bytes differ from reviewed profile");
        }
        if (!p.stringPropertyNames().equals(keys)) throw new IOException("Worker config has unknown/missing properties");
        observerInstrumentation = instrumentation;
        for (String key : p.stringPropertyNames()) if (p.getProperty(key).isBlank()) throw new IOException("Blank worker config " + key);
        Path output = Path.of(p.getProperty("output")).toAbsolutePath().normalize();
        streamPath = Path.of(p.getProperty("event_stream")).toAbsolutePath().normalize();
        healthPath = Path.of(p.getProperty("health_page")).toAbsolutePath().normalize();
        healthNonce = p.getProperty("health_nonce");
        if (!healthNonce.matches("[0-9a-f-]{36}")) throw new IOException("Unbound health nonce");
        initHealth(healthPath, healthNonce, configSha);
        if (!Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS) || Files.exists(output, LinkOption.NOFOLLOW_LINKS) ||
            !streamPath.getParent().equals(output.getParent()) || streamPath.equals(output) ||
            Files.exists(streamPath, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Worker report/event stream not in distinct fresh regular paths");
        String rosterBinding = System.getProperty("bc.repository.task.roster");
        int rosterSplit = rosterBinding == null ? -1 : rosterBinding.lastIndexOf('@');
        if (rosterSplit < 1 || !rosterBinding.substring(rosterSplit + 1).matches("[0-9a-f]{64}"))
            throw new IOException("Missing pre-worker task input roster binding");
        taskRoster = Path.of(rosterBinding.substring(0, rosterSplit)).toAbsolutePath().normalize();
        taskRosterSha = rosterBinding.substring(rosterSplit + 1);
        byte[] rosterBytes = capture(taskRoster, 1_048_576);
        if (!sha256(rosterBytes).equals(taskRosterSha)) throw new IOException("Pre-worker task input roster changed");
        initialTaskInputs = taskEntries(rosterBytes);
        initialTaskPolicy = taskPolicy(rosterBytes);
        initialJarPolicy = System.getProperty("jdk.util.jar.enableMultiRelease", "<unset>");
        initialJarVersion = System.getProperty("jdk.util.jar.version", "<unset>");
        initialJarRuntimeFeature = JarFile.runtimeVersion().feature();
        if (!Set.of("<unset>", "true", "false", "force").contains(initialJarPolicy) ||
            initialJarRuntimeFeature < 8 || initialJarRuntimeFeature > Runtime.version().feature())
            unsupported.add("unreviewed-multi-release-jar-policy");
        initialAgents = agentEntries(ManagementFactory.getRuntimeMXBean().getInputArguments());
        initialClassPath = entries(System.getProperty("java.class.path", ""));
        initialModulePath = entries(System.getProperty("jdk.module.path", System.getProperty("java.module.path", "")));
        Path homeAtStart = Path.of(System.getProperty("java.home")).toRealPath();
        initialJdk = List.of(entry(Path.of(ProcessHandle.current().info().command().orElseThrow())),
            entry(homeAtStart.resolve("lib/modules")), entry(homeAtStart.resolve("lib/server/libjvm.so")),
            entry(homeAtStart.resolve("release")));
        jdkModuleNames = java.lang.module.ModuleFinder.ofSystem().findAll().stream()
            .map(reference -> reference.descriptor().name()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (Class<?> initial : instrumentation.getAllLoadedClasses()) {
            if (initial == null) { incomplete("unknown-pre-agent-class"); continue; }
            if (initial.getClassLoader() == null) { bootstrap.incrementAndGet(); continue; }
            // Our own premain may already have loaded its supporting types. It does not count as
            // an observed test class; other pre-agent definitions have no captured bytes.
            if (!initial.getName().startsWith("com.bettercontent.playtest.repositoryinput."))
                incomplete("pre-agent-nonbootstrap-class");
        }
        streamChannel = FileChannel.open(streamPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        streamNonce = java.util.UUID.randomUUID().toString();
        synchronized (classes) {
            append("\"kind\":\"start\"," + field("nonce", streamNonce) + "," +
                field("config_sha256", configSha) + ",\"pid\":" + ProcessHandle.current().pid() + "," +
                field("birth", ProcessHandle.current().info().startInstant().orElseThrow().toString()));
            for (String reason : unsupported) append("\"kind\":\"error\"," + field("reason", reason));
        }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redef,
                                              ProtectionDomain domain, byte[] bytes) {
                synchronized (classes) {
                    if (redef != null) incomplete("redefinition-or-retransformation");
                    if (name == null || bytes == null) { incomplete("unresolved-definition-identity"); return null; }
                    if (classes.size() >= MAX_CLASSES) { incomplete("class-definition-limit"); return null; }
                    try {
                        Path source;
                        String selected;
                        boolean jrt = module != null && module.isNamed() &&
                            module.getLayer() == ModuleLayer.boot() && jdkModuleNames.contains(module.getName());
                        if (jrt) {
                            source = Path.of(System.getProperty("java.home")).toRealPath().resolve("lib/modules");
                            selected = module.getName() + "/" + name + ".class";
                        } else {
                            if (loader == null) {
                                healthIncrement(176); incomplete("unknown-bootstrap-class-source"); return null;
                            }
                            if (domain == null || domain.getCodeSource() == null ||
                                domain.getCodeSource().getLocation() == null) {
                                incomplete("unresolved-nonbootstrap-code-source"); return null;
                            }
                            URI uri = domain.getCodeSource().getLocation().toURI();
                            // Never interpret union:/jar:/securejar: URI text as a file path.
                            if ("union".equals(uri.getScheme())) {
                                source = unionBacking(uri, name + ".class", bytes);
                                if (source == null) return null;
                                unionSources.add(source.toString());
                            } else if ("file".equals(uri.getScheme())) source = Path.of(uri).toRealPath();
                            else { incomplete("non-file-code-source:" + uri.getScheme()); return null; }
                            selected = selectedEntry(source, name + ".class");
                        }
                        int identity = loader == null ? -1 : loaderId(loader);
                        if (identity == 0) return null;
                        ClassRow row = new ClassRow(name + ".class", identity, source.toString(), sha256(bytes), selected);
                        append("\"kind\":\"definition\"," + field("name", row.name()) + ",\"loader_id\":" +
                            row.loader() + "," + field("source", row.path()) + "," + field("bytes_sha256", row.bytesSha256()) +
                            "," + field("selected_entry", row.selectedEntry()));
                        classes.add(row);
                    } catch (Throwable error) { incomplete("unresolvable-code-source-or-observer-callback"); }
                }
                return null; // never alter a definition; records the pre-next-transformer byte stage
            }
        }, false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { publish(p, output, configSha); }
            catch (Throwable failure) {
                try { healthIncrement(160); } catch (Throwable ignored) { /* page unreadable => controller rejects */ }
                System.err.println("Repository input-use worker failed closed: " + failure);
            }
        }, "repository-input-use-observer"));
    }

    /** Exact-version read-only private metadata profile. redefineModule changes accessibility, is
     * explicitly reviewed and emitted below; it never rewrites provider fields/class bytes.
     * Multiple backings are ambiguous: no replay of arbitrary, possibly stateful filters. */
    private static Path unionBacking(URI uri, String member, byte[] stageBytes) throws Exception {
        if (unionHandler == null) { incomplete("union-profile-not-enabled"); return null; }
        if (hookReportWritten) { incomplete("late-union-access-missing-hook-module-metadata"); return null; }
        Path unionRoot = Path.of(uri); // actual installed NIO provider API, NOT parsing URI text as a file path
        var fs = unionRoot.getFileSystem();
        if (!"union".equals(fs.provider().getScheme()) ||
            !"cpw.mods.niofs.union.UnionFileSystemProvider".equals(fs.provider().getClass().getName()) ||
            !"cpw.mods.niofs.union.UnionFileSystem".equals(fs.getClass().getName()) ||
            !"cpw.mods.securejarhandler".equals(fs.getClass().getModule().getName())) {
            incomplete("unreviewed-union-provider"); return null;
        }
        var origin = fs.getClass().getProtectionDomain().getCodeSource();
        if (origin == null || origin.getLocation() == null ||
            !"file".equals(origin.getLocation().toURI().getScheme()) ||
            !Path.of(origin.getLocation().toURI()).toRealPath().toString().equals(unionHandler) ||
            !sha256(capture(Path.of(unionHandler), 268_435_456)).equals(unionHandlerSha)) {
            incomplete("unbound-union-provider-binary"); return null;
        }
        Module target = fs.getClass().getModule();
        if (!openedUnionModules.contains(target)) {
            if (openedUnionModules.size() >= 16 || !observerInstrumentation.isModifiableModule(target)) {
                incomplete("unopenable-union-module"); return null;
            }
            observerInstrumentation.redefineModule(target, Set.of(), Map.of(),
                Map.of("cpw.mods.niofs.union", Set.of(RepositoryInputUseAgent.class.getModule())), Set.of(), Map.of());
            openedUnionModules.add(target);
        }
        Method basePaths = fs.getClass().getDeclaredMethod("getBasePaths");
        basePaths.setAccessible(true);
        Object roster = basePaths.invoke(fs);
        if (!(roster instanceof List<?> bases) || bases.size() != 1 || !(bases.get(0) instanceof Path base) ||
            base.getFileSystem() != FileSystems.getDefault()) {
            incomplete("ambiguous-union-backing-roster"); return null;
        }
        Path primary = (Path) fs.getClass().getMethod("getPrimaryPath").invoke(fs);
        if (!primary.equals(base)) { incomplete("union-primary-and-sole-base-disagree"); return null; }
        Path selected = base.toRealPath();
        Path unionMember = unionRoot.resolve(member).normalize();
        if (unionMember.getFileSystem() != fs || !unionMember.startsWith(unionRoot) ||
            !Files.isRegularFile(unionMember, LinkOption.NOFOLLOW_LINKS)) {
            incomplete("unresolved-union-member"); return null;
        }
        byte[] fromProvider;
        try (var stream = Files.newInputStream(unionMember)) { fromProvider = stream.readNBytes(16_777_217); }
        if (fromProvider.length == 0 || fromProvider.length > 16_777_216 ||
            !sha256(fromProvider).equals(sha256(stageBytes))) {
            incomplete("union-member-stage-bytes-differ"); return null;
        }
        return selected; // independently recheck exact physical class entry at shutdown/consumer
    }

    private static Properties properties(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        Set<String> found = new HashSet<>();
        for (String line : text.split("\\r?\\n", -1)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) continue;
            if (line.endsWith("\\")) throw new IOException("Continued property lines unsupported");
            Properties one = new Properties();
            one.load(new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.ISO_8859_1)));
            if (one.size() != 1 || !found.add(one.stringPropertyNames().iterator().next()))
                throw new IOException("Duplicate/ambiguous worker property");
        }
        Properties all = new Properties(); all.load(new ByteArrayInputStream(bytes));
        if (!all.stringPropertyNames().equals(found)) throw new IOException("Worker config parser discrepancy");
        return all;
    }

    private static void publish(Properties p, Path output, String configSha) throws Exception {
        ProcessHandle self = ProcessHandle.current();
        Instant birth = self.info().startInstant().orElseThrow(() -> new IOException("Worker birth missing"));
        Path java = Path.of(self.info().command().orElseThrow(() -> new IOException("Worker executable missing"))).toRealPath();
        Path home = Path.of(System.getProperty("java.home")).toRealPath();
        Path modules = home.resolve("lib/modules"), vm = home.resolve("lib/server/libjvm.so"), release = home.resolve("release");
        List<Entry> jdk = List.of(entry(java), entry(modules), entry(vm), entry(release));
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<Entry> agents = agentEntries(args);
        byte[] rosterBytes = capture(taskRoster, 1_048_576);
        List<Entry> taskInputs = taskEntries(rosterBytes);
        String taskPolicy = taskPolicy(rosterBytes);
        long agentCount = args.stream().filter(arg -> arg.startsWith("-javaagent:") || arg.startsWith("-agentpath:") ||
                arg.startsWith("-agentlib:")).count();
        synchronized (classes) {
            if (!initialAgents.equals(agents)) unsupported.add("instrumentation-agent-bytes-changed-during-worker");
            if (!initialJarPolicy.equals(System.getProperty("jdk.util.jar.enableMultiRelease", "<unset>")) ||
                !initialJarVersion.equals(System.getProperty("jdk.util.jar.version", "<unset>")) ||
                initialJarRuntimeFeature != JarFile.runtimeVersion().feature())
                unsupported.add("multi-release-jar-policy-changed-during-worker");
            if (!sha256(rosterBytes).equals(taskRosterSha) || !initialTaskInputs.equals(taskInputs) ||
                !initialTaskPolicy.equals(taskPolicy))
                unsupported.add("task-input-roster-changed-during-worker");
            if (agentCount != 1 || args.stream().noneMatch(arg -> arg.startsWith("-javaagent:") && arg.endsWith("@" + configSha)))
                unsupported.add("other-agent-transformer-order-unproven");
            if (args.stream().anyMatch(arg -> arg.startsWith("-agentpath:") || arg.startsWith("-agentlib:")))
                unsupported.add("native-instrumentation-agent-unobserved");
            if (args.stream().anyMatch(arg -> arg.startsWith("--patch-module") || arg.startsWith("--upgrade-module-path")))
                unsupported.add("unreviewed-module-patching");
        }
        List<Entry> classPath = entries(System.getProperty("java.class.path", ""));
        List<Entry> modulePath = entries(System.getProperty("jdk.module.path", System.getProperty("java.module.path", "")));
        synchronized (classes) {
            if (!initialClassPath.equals(classPath)) unsupported.add("classpath-bytes-changed-during-worker");
            if (!initialModulePath.equals(modulePath)) unsupported.add("module-path-bytes-changed-during-worker");
            if (!initialJdk.equals(jdk)) unsupported.add("jdk-image-bytes-changed-during-worker");
            if (unionHandler != null && !sha256(capture(Path.of(unionHandler), 268_435_456)).equals(unionHandlerSha))
                unsupported.add("union-provider-binary-changed-during-worker");
            if (openedUnionModules.size() > 1) unsupported.add("multiple-union-provider-module-instances");
        }
        List<ClassRow> observed;
        List<String> problems;
        int snapshotSequence;
        synchronized (classes) {
            observed = List.copyOf(classes);
            snapshotSequence = streamSequence;
            if (observed.isEmpty()) unsupported.add("no-class-definitions-observed");
            if (streamFailed) unsupported.add("worker-event-stream-write-failed");
            Map<String, Entry> sources = new HashMap<>();
            for (Entry row : classPath) sources.put(row.path().toString(), row);
            for (Entry row : modulePath) sources.put(row.path().toString(), row);
            for (Entry row : taskInputs) sources.put(row.path().toString(), row);
            for (Entry row : jdk) if (row.path().equals(modules)) sources.put(row.path().toString(), row);
            for (String path : unionSources) sources.put(path, entry(Path.of(path)));
            for (ClassRow row : observed) {
                Entry source = sources.get(row.path());
                if (source == null) { unsupported.add("loaded-code-outside-class-or-module-path"); continue; }
                try {
                    byte[] original;
                    if (source.path().equals(modules)) {
                        original = jrtBytes(row.selectedEntry(), row.name());
                    } else if (source.kind().equals("directory")) {
                        if (!row.selectedEntry().equals(row.name())) throw new IOException("Directory class entry mismatch");
                        original = capture(source.path().resolve(row.name()), 16_777_216);
                    } else if (source.path().toString().endsWith(".jar")) {
                        try (JarFile jar = new JarFile(source.path().toFile(), true, JarFile.OPEN_READ, JarFile.runtimeVersion())) {
                            if (jar.size() > MAX_FILES) throw new IOException("Unbounded JAR entry count");
                            var item = jar.getJarEntry(row.name());
                            if (item == null || item.getSize() < 0 || item.getSize() > 16_777_216 ||
                                !item.getRealName().equals(row.selectedEntry())) throw new IOException("Selected JAR entry changed");
                            try (var stream = jar.getInputStream(item)) { original = stream.readNBytes(16_777_217); }
                        }
                    } else { unsupported.add("unknown-file-code-source-format"); continue; }
                    if (!sha256(original).equals(row.bytesSha256())) unsupported.add("transformed-or-changed-class-bytes");
                } catch (Exception error) { unsupported.add("unverifiable-loaded-class-bytes"); }
            }
            problems = unsupported.stream().sorted().toList();
        }
        Set<String> sourcePaths = new HashSet<>();
        for (ClassRow row : observed) sourcePaths.add(row.path());
        List<Entry> codeSources = new ArrayList<>();
        for (String path : sourcePaths) codeSources.add(entry(Path.of(path)));
        codeSources.sort(Comparator.comparing(e -> e.path().toString()));
        List<Entry> union = new ArrayList<>();
        for (String path : unionSources) union.add(entry(Path.of(path)));
        union.sort(Comparator.comparing(e -> e.path().toString()));
        String access = openedUnionModules.isEmpty() ? "[]" : "[{\"module\":\"cpw.mods.securejarhandler\"," +
            "\"package\":\"cpw.mods.niofs.union\",\"opened_to\":\"observer-unnamed-module\"," +
            field("handler_path", unionHandler) + "," + field("handler_sha256", unionHandlerSha) + "," +
            "\"module_instances\":" + openedUnionModules.size() + "}]";
        String json = "{\"schema\":\"bc.repository_input_use_worker.v3\",\"scope\":\"source-only-not-packaged-runtime\"," +
                "\"observation_scope\":\"class-definition-stage-only-resource-access-unobserved\"," +
                field("status", problems.isEmpty() ? "bounded-file-class-stage" : "incomplete") + "," +
                field("run_id", p.getProperty("run_id")) + "," + field("repository", p.getProperty("repository")) + "," +
                field("task", p.getProperty("task")) + "," + field("reviewed_plan_sha256", p.getProperty("reviewed_plan_sha256")) + "," +
                field("producer_sha256", p.getProperty("producer_sha256")) + "," + field("config_sha256", configSha) + "," +
                field("event_stream_path", streamPath.toString()) + "," + field("event_stream_nonce", streamNonce) + "," +
                field("health_page_path", healthPath.toString()) + "," + field("health_nonce", healthNonce) + "," +
                "\"snapshot_stream_sequence\":" + snapshotSequence + "," +
                "\"observed_process_pid\":" + self.pid() + "," + field("observed_process_start", birth.toString()) + "," +
                field("captured_at", Instant.now().toString()) + "," + field("java_executable", java.toString()) + "," +
                field("java_executable_sha256", sha256(capture(java, 268_435_456))) + "," +
                field("java_version", System.getProperty("java.version")) + "," +
                field("java_runtime_version", System.getProperty("java.runtime.version")) + "," +
                field("java_vendor", System.getProperty("java.vendor")) + "," +
                field("java_home", home.toString()) + "," + field("java_vm_name", System.getProperty("java.vm.name")) + "," +
                field("java_vm_version", System.getProperty("java.vm.version")) + "," +
                field("java_vm_vendor", System.getProperty("java.vm.vendor")) + "," +
                field("jar_mr_policy", initialJarPolicy) + "," + field("jar_version_override", initialJarVersion) + "," +
                "\"jar_runtime_feature\":" + initialJarRuntimeFeature + "," +
                "\"bootstrap_pre_agent\":" + bootstrap.get() + "," +
                "\"jvm_arguments\":" + strings(args) + ",\"unsupported\":" + strings(problems) + "," +
                "\"instrumentation_agents\":" + rows(agents) + "," +
                "\"module_access_changes\":" + access + ",\"union_sources\":" + rows(union) + "," +
                field("task_roster_path", taskRoster.toString()) + "," + field("task_roster_sha256", taskRosterSha) + "," +
                field("task_execution_policy", taskPolicy) + "," +
                "\"task_class_path\":" + rows(taskInputs) + ",\"jdk_components\":" + rows(jdk) + ",\"class_path\":" + rows(classPath) + "," +
                "\"module_path\":" + rows(modulePath) + ",\"code_sources\":" + rows(codeSources) + "," +
                "\"class_definitions\":[" + observed.stream().map(c -> "{\"name\":" + quote(c.name()) +
                    ",\"loader_id\":" + c.loader() + ",\"source\":" + quote(c.path()) +
                    ",\"bytes_sha256\":" + quote(c.bytesSha256()) + ",\"selected_entry\":" +
                    quote(c.selectedEntry()) + "}").collect(Collectors.joining(",")) + "]}\n";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REPORT) throw new IOException("Input-use report too large");
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        synchronized (classes) {
            append("\"kind\":\"hook_report\"," + field("report_sha256", sha256(bytes)) + "," +
                "\"snapshot_stream_sequence\":" + snapshotSequence);
            hookReportWritten = true;
        }
        // Leave event stream channel open; OTHER shutdown hooks may define/load additional classes.
    }

    private static byte[] jrtBytes(String selected, String name) throws Exception {
        int slash = selected.indexOf('/');
        if (slash < 1 || !jdkModuleNames.contains(selected.substring(0, slash)) ||
            !selected.substring(slash + 1).equals(name) || !name.matches("[A-Za-z0-9_$/-]+\\.class") ||
            name.contains("..")) throw new IOException("Unbound JRT class/module identity");
        Path path = java.nio.file.FileSystems.getFileSystem(URI.create("jrt:/"))
            .getPath("modules", selected.substring(0, slash), name);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(16_777_217);
            if (bytes.length == 0 || bytes.length > 16_777_216) throw new IOException("Unbounded JRT module class");
            return bytes;
        }
    }

    private static String selectedEntry(Path source, String name) throws Exception {
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) return name;
        if (!source.toString().endsWith(".jar")) throw new IOException("Unknown class-source format");
        try (JarFile jar = new JarFile(source.toFile(), true, JarFile.OPEN_READ, JarFile.runtimeVersion())) {
            if (jar.size() > MAX_FILES) throw new IOException("Unbounded JAR entry count");
            var item = jar.getJarEntry(name);
            if (item == null || item.getSize() < 0 || item.getSize() > 16_777_216)
                throw new IOException("No bounded selected runtime JAR class entry");
            return item.getRealName(); // effective selected base or META-INF/versions/N entry
        }
    }

    private static String taskPolicy(byte[] roster) throws IOException {
        String text = new String(roster, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(roster, text.getBytes(StandardCharsets.UTF_8)) || !text.endsWith("\n"))
            throw new IOException("Task roster requires canonical UTF-8 and terminal newline");
        String header = text.substring(0, text.indexOf('\n'));
        if (header.equals("policy\tTest\t1\t0")) return "Test/maxParallelForks=1/forkEvery=0";
        if (header.equals("policy\tJavaExec\tnot-applicable\tnot-applicable")) return "JavaExec/one-task-worker";
        throw new IOException("Unsupported ORIGINAL Gradle worker fork policy");
    }
    private static List<Entry> taskEntries(byte[] roster) throws Exception {
        String text = new String(roster, StandardCharsets.UTF_8);
        taskPolicy(roster);
        List<Entry> result = new ArrayList<>();
        for (String line : text.substring(text.indexOf('\n') + 1, text.length() - 1).split("\n", -1)) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 5 || !parts[1].matches("[0-9a-f]{64}") || result.size() >= 4096)
                throw new IOException("Malformed/unbounded task input roster");
            Path path = Path.of(parts[4]);
            if (!path.isAbsolute() || !path.normalize().equals(path))
                throw new IOException("Noncanonical task input");
            Entry actual = taskEntry(path);
            if (!actual.kind().equals(parts[0]) || !actual.sha().equals(parts[1]) ||
                !Integer.toString(actual.files()).equals(parts[2]) ||
                !Long.toString(actual.sizeBytes()).equals(parts[3]))
                throw new IOException("Changed/absent-to-present task input roster entry");
            result.add(actual);
        }
        return List.copyOf(result);
    }

    private static List<Entry> agentEntries(List<String> args) throws Exception {
        List<Entry> result = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("-javaagent:")) continue;
            String binding = arg.substring("-javaagent:".length());
            int options = binding.indexOf('=');
            Path path = Path.of(options < 0 ? binding : binding.substring(0, options)).toAbsolutePath().toRealPath();
            Entry observed = entry(path);
            if (!observed.kind().equals("file") || !observed.path().toString().endsWith(".jar"))
                throw new IOException("Instrumentation agent must be a bound regular JAR");
            result.add(observed); // ordered exactly as actual JVM -javaagent arguments
            if (result.size() > 16) throw new IOException("Unbounded instrumentation agents");
        }
        return List.copyOf(result);
    }
    private static List<Entry> entries(String raw) throws Exception {
        if (raw.isBlank()) return List.of();
        List<Entry> result = new ArrayList<>();
        for (String value : raw.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1)) {
            if (value.isBlank()) throw new IOException("Empty actual JVM class/module path entry");
            if (result.size() >= 4096) throw new IOException("Unbounded JVM path");
            result.add(entry(Path.of(value).toAbsolutePath().toRealPath())); // preserve JVM resolution order and repeats
        }
        return List.copyOf(result);
    }
    private static Entry taskEntry(Path path) throws Exception {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            for (Path ancestor = path.getParent(); ancestor != null; ancestor = ancestor.getParent())
                if (Files.isSymbolicLink(ancestor) || Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isDirectory(ancestor, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Linked/non-directory absent resource ancestor");
            return new Entry(path, "absent", sha256(("bc.repository_absent.v1:" + path)
                .getBytes(StandardCharsets.UTF_8)), 0, 0L);
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unknown classpath path state (not proven absent)");
        return entry(path);
    }
    private static Entry entry(Path source) throws Exception {
        if (!source.isAbsolute() || !source.normalize().equals(source) || Files.isSymbolicLink(source) ||
            !source.toRealPath().equals(source)) throw new IOException("Linked or ambiguous source");
        Path path = source;
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.size(path) > 268_435_456) throw new IOException("Unbounded JVM input file");
            byte[] original = capture(path, 268_435_456, true);
            if (Files.size(path) != original.length) throw new IOException("JVM file changed during bounded capture");
            return new Entry(path, "file", sha256(original), 1, original.length);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unknown JVM source kind");
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(path, 64)) {
            Iterator<Path> iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path child = iterator.next();
                if (child.equals(path)) continue;
                if (path.relativize(child).getNameCount() >= 64 && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Unbounded JVM directory depth");
                if (files.size() >= MAX_FILES) throw new IOException("Unbounded JVM directory inventory");
                files.add(child);
            }
        }
        files.sort(Comparator.naturalOrder());
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); int count = 0; long totalBytes = 0;
        for (Path file : files) {
            if (Files.isSymbolicLink(file)) throw new IOException("Linked JVM directory child");
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
            byte[] bytes = capture(file, 16_777_216, true);
            totalBytes += bytes.length;
            digest.update(path.relativize(file).toString().getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            digest.update(sha256(bytes).getBytes(StandardCharsets.US_ASCII)); digest.update((byte) 0); count++;
        }
        return new Entry(path, "directory", java.util.HexFormat.of().formatHex(digest.digest()), count, totalBytes);
    }
    private static byte[] capture(Path path, int limit) throws Exception {
        return capture(path, limit, false);
    }
    private static byte[] capture(Path path, int limit, boolean allowEmpty) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) ||
            (Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS) instanceof Number links && links.intValue() != 1))
            throw new IOException("Unknown or linked input");
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] result = in.readNBytes(limit + 1);
            if (result.length > limit || !allowEmpty && result.length == 0) throw new IOException("Unbounded/empty input");
            return result;
        }
    }
    private static String rows(List<Entry> entries) { return "[" + entries.stream().map(e -> "{\"path\":" + quote(e.path().toString()) +
            ",\"sha256\":" + quote(e.sha()) + ",\"kind\":" + quote(e.kind()) + ",\"files\":" + e.files() +
            ",\"size_bytes\":" + e.sizeBytes() + "}")
            .collect(java.util.stream.Collectors.joining(",")) + "]"; }
    private static String strings(List<String> values) { return "[" + values.stream().map(RepositoryInputUseAgent::quote)
            .collect(java.util.stream.Collectors.joining(",")) + "]"; }
    private static String field(String name, String value) { return quote(name) + ":" + quote(value); }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') result.append('\\').append(c);
            else if (c < 0x20) result.append(String.format("\\u%04x", (int) c)); else result.append(c);
        }
        return result.append('"').toString();
    }
    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
