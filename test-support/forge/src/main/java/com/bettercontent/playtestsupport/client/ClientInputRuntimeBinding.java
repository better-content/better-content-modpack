package com.bettercontent.playtestsupport.client;

import com.bettercontent.playtestsupport.Protocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Controller-attested host identities; the sandboxed client verifies only its LOCAL namespace/process/display. */
final class ClientInputRuntimeBinding {
    static final String FILE = "client-input-runtime.json";
    private static final Set<String> FIELDS = Set.of("schema", "purpose", "campaign_id", "lineage_id", "epoch_id",
        "client_session_id", "candidate_sha256", "support_sha256", "profile", "display", "xauthority_path",
        "xauthority_sha256", "host_xvfb_pid", "host_xvfb_birth_epoch_millis", "host_xvfb_executable",
        "host_xvfb_args_sha256", "host_client_pid", "host_client_birth_epoch_millis", "host_client_nspid");
    final String sha256, display, authorityPath, authoritySha, profile, purpose, epochId, clientSession;
    final long namespacePid, hostXvfbPid, hostXvfbBirthMillis, hostClientPid, hostClientBirthMillis;

    private ClientInputRuntimeBinding(JsonObject row, String sha, long localPid) {
        sha256 = sha;
        display = Protocol.string(row, "display"); authorityPath = Protocol.string(row, "xauthority_path");
        authoritySha = Protocol.string(row, "xauthority_sha256"); profile = Protocol.string(row, "profile");
        purpose = Protocol.string(row, "purpose"); epochId = Protocol.string(row, "epoch_id");
        clientSession = Protocol.string(row, "client_session_id"); namespacePid = localPid;
        hostXvfbPid = Protocol.number(row, "host_xvfb_pid");
        hostXvfbBirthMillis = Protocol.number(row, "host_xvfb_birth_epoch_millis");
        hostClientPid = Protocol.number(row, "host_client_pid");
        hostClientBirthMillis = Protocol.number(row, "host_client_birth_epoch_millis");
    }
    static ClientInputRuntimeBinding capture(Path clientMailbox, String expectedSha, String run, String profile,
            String localDisplay, String localAuthority, long localPid) throws IOException {
        // The controller creates this OUTSIDE runtime-writable roots, after both process identities exist.
        // Never accept an action-provided path or a symlink into a runtime-writable subtree.
        Path evidence = clientMailbox.toAbsolutePath().normalize().getParent();
        if (evidence == null || !clientMailbox.getFileName().toString().equals("client-native") ||
                !evidence.getFileName().toString().equals("evidence") || evidence.getParent() == null ||
                evidence.getParent().getParent() == null || evidence.getParent().getParent().getFileName() == null ||
                !evidence.getParent().getParent().getFileName().toString().equals("epochs") ||
                !Files.isDirectory(evidence.getParent().getParent(), LinkOption.NOFOLLOW_LINKS) ||
                !Files.isDirectory(evidence.getParent(), LinkOption.NOFOLLOW_LINKS) ||
                !Files.isDirectory(evidence, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(evidence))
            throw new IllegalStateException("unowned fixed client input binding directory");
        Path file = evidence.resolve(FILE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
            throw new IllegalStateException("missing or linked immutable controller input binding");
        try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(16_385);
            if (bytes.length < 1 || bytes.length > 16_384) throw new IllegalStateException("input binding exceeds byte bound");
            ClientInputRuntimeBinding binding = parse(bytes, expectedSha, run, profile, localDisplay, localAuthority, localPid);
            if (!binding.epochId.equals(evidence.getParent().getFileName().toString()))
                throw new IllegalStateException("controller input binding belongs to a different epoch");
            return binding;
        }
    }
    static ClientInputRuntimeBinding parse(byte[] bytes, String expectedSha, String run, String profile,
            String localDisplay, String localAuthority, long localPid) throws IOException {
        if (bytes.length < 1 || bytes.length > 16_384 || expectedSha == null ||
                !expectedSha.matches("[0-9a-f]{64}") || !ClientNativeSupport.sha256(bytes).equals(expectedSha))
            throw new IllegalStateException("immutable controller input binding hash/length mismatch");
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(text.getBytes(StandardCharsets.UTF_8), bytes))
            throw new IllegalStateException("input binding is not canonical UTF-8");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            rejectDuplicateKeys(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalStateException("trailing input binding data");
        }
        var parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) throw new IllegalStateException("input binding is not an object");
        JsonObject row = parsed.getAsJsonObject();
        if (!row.keySet().equals(FIELDS) || !Protocol.string(row, "schema").equals("bc.playtest_client_input_runtime.v1") ||
                !Protocol.string(row, "client_session_id").equals(run) ||
                !Protocol.string(row, "profile").equals(profile) || !Protocol.string(row, "display").equals(localDisplay) ||
                !Protocol.string(row, "xauthority_path").equals(localAuthority) ||
                !Protocol.string(row, "display").matches("127\\.0\\.0\\.1:[0-9]{1,5}") ||
                !Protocol.string(row, "purpose").matches("diagnostic|qualification") ||
                !Protocol.string(row, "epoch_id").matches("epoch-[0-9]{5}") ||
                !Protocol.string(row, "campaign_id").matches("[A-Za-z0-9._:-]{1,128}") ||
                !Protocol.string(row, "lineage_id").matches("[A-Za-z0-9._:-]{1,128}") ||
                !Protocol.string(row, "candidate_sha256").matches("[0-9a-f]{64}") ||
                !Protocol.string(row, "support_sha256").matches("[0-9a-f]{64}") ||
                !Protocol.string(row, "xauthority_sha256").matches("[0-9a-f]{64}") ||
                !Protocol.string(row, "host_xvfb_executable").endsWith("/Xvfb") ||
                !Protocol.string(row, "host_xvfb_args_sha256").matches("[0-9a-f]{64}") ||
                !(profile.equals("1280x720@2") || profile.equals("1920x1080@3")) ||
                localPid <= 0) throw new IllegalStateException("controller input binding context/profile mismatch");
        long xvfbPid = Protocol.number(row, "host_xvfb_pid");
        long xvfbBirth = Protocol.number(row, "host_xvfb_birth_epoch_millis");
        long clientPid = Protocol.number(row, "host_client_pid");
        long clientBirth = Protocol.number(row, "host_client_birth_epoch_millis");
        if (xvfbPid <= 1 || clientPid <= 1 || xvfbPid == clientPid || xvfbBirth <= 0 || clientBirth <= 0 ||
                !row.get("host_client_nspid").isJsonArray())
            throw new IllegalStateException("unbound controller host process identity");
        JsonArray pidMap = row.getAsJsonArray("host_client_nspid");
        if (pidMap.size() < 2 || pidMap.size() > 8 || pidNumber(pidMap, 0) != clientPid ||
                pidNumber(pidMap, pidMap.size() - 1) != localPid)
            throw new IllegalStateException("client namespace PID does not match host controller NSpid mapping");
        for (int i = 0; i < pidMap.size(); i++) if (pidNumber(pidMap, i) <= 0)
            throw new IllegalStateException("invalid namespace PID mapping");
        return new ClientInputRuntimeBinding(row, expectedSha, localPid);
    }
    private static long pidNumber(JsonArray array, int index) {
        JsonObject wrapper = new JsonObject(); wrapper.add("pid", array.get(index)); return Protocol.number(wrapper, "pid");
    }
    private static void rejectDuplicateKeys(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalStateException("duplicate controller binding field");
                    rejectDuplicateKeys(reader);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); while (reader.hasNext()) rejectDuplicateKeys(reader); reader.endArray();
            }
            case NULL -> reader.nextNull();
            case BOOLEAN -> reader.nextBoolean();
            case NUMBER, STRING -> reader.nextString();
            default -> throw new IllegalStateException("malformed controller runtime binding");
        }
    }
}
