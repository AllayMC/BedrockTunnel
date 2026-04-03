package org.allaymc.bedrocktunnel.capture;

import org.allaymc.bedrocktunnel.BedrockTunnelJson;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class CaptureBundleStore {
    private static final String MANIFEST_FILE = "capture.json";
    private static final String INDEX_FILE = "packets.jsonl";

    private final Path rootDirectory;
    private final Path sessionDirectory;
    private final Path payloadDirectory;
    private final Path jsonDirectory;

    public CaptureBundleStore(Path rootDirectory, String sessionId) throws IOException {
        this.rootDirectory = rootDirectory;
        this.sessionDirectory = rootDirectory.resolve(sessionId);
        this.payloadDirectory = sessionDirectory.resolve("payloads");
        this.jsonDirectory = sessionDirectory.resolve("json");
        Files.createDirectories(payloadDirectory);
        Files.createDirectories(jsonDirectory);
    }

    public Path sessionDirectory() {
        return sessionDirectory;
    }

    public Path rawPathFor(long sequence) {
        return Path.of("payloads", "%06d.bin".formatted(sequence));
    }

    public Path jsonPathFor(long sequence) {
        return Path.of("json", "%06d.json".formatted(sequence));
    }

    public void savePacketAssets(CaptureEntry entry) throws IOException {
        Files.write(sessionDirectory.resolve(entry.rawPath()), entry.packet().rawBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(sessionDirectory.resolve(entry.jsonPath()), entry.packet().jsonText(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void writeIndex(CaptureSummary summary, List<StoredPacketRecord> records) throws IOException {
        Files.writeString(
                sessionDirectory.resolve(MANIFEST_FILE),
                BedrockTunnelJson.MAPPER.writeValueAsString(summary),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        List<String> lines = new ArrayList<>(records.size());
        for (StoredPacketRecord record : records) {
            lines.add(BedrockTunnelJson.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(record));
        }
        Files.write(sessionDirectory.resolve(INDEX_FILE), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static Path rootDirectory() {
        return Path.of("captures");
    }

    public static List<HistoryCapture> listHistory() throws IOException {
        Path root = rootDirectory();
        Files.createDirectories(root);
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(CaptureBundleStore::tryReadSummary)
                    .filter(history -> history != null)
                    .sorted(Comparator.comparing((HistoryCapture history) -> history.summary().startedAt()).reversed())
                    .toList();
        }
    }

    public static LoadedCapture load(Path directory) throws IOException {
        CaptureSummary summary = BedrockTunnelJson.MAPPER.readValue(directory.resolve(MANIFEST_FILE).toFile(), CaptureSummary.class);
        List<CaptureEntry> entries = new ArrayList<>();
        if (Files.exists(directory.resolve(INDEX_FILE))) {
            List<String> lines = Files.readAllLines(directory.resolve(INDEX_FILE), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                StoredPacketRecord record = BedrockTunnelJson.MAPPER.readValue(line, StoredPacketRecord.class);
                byte[] rawBytes = Files.readAllBytes(directory.resolve(record.rawPath()));
                String jsonText = Files.readString(directory.resolve(record.jsonPath()), StandardCharsets.UTF_8);
                CapturedPacket packet = new CapturedPacket(
                        record.sequence(),
                        Instant.parse(record.capturedAt()),
                        record.relativeTimeMillis(),
                        record.direction(),
                        record.packetId(),
                        record.packetType(),
                        record.protocolVersion(),
                        record.senderSubClientId(),
                        record.targetSubClientId(),
                        record.headerLength(),
                        record.description(),
                        jsonText,
                        rawBytes
                );
                CaptureEntry entry = new CaptureEntry(
                        packet,
                        Path.of(record.rawPath()),
                        Path.of(record.jsonPath()),
                        record.state(),
                        record.breakpointHit(),
                        record.queuedWhilePaused()
                );
                for (int index = 0; index < record.replayCount(); index++) {
                    entry.incrementReplayCount();
                }
                entries.add(entry);
            }
        }
        return new LoadedCapture(summary, entries);
    }

    public static String sessionIdFor(String targetHost, int targetPort) {
        String sanitized = (targetHost + "-" + targetPort).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return Instant.now().toString().replace(':', '-') + "-" + sanitized;
    }

    private static HistoryCapture tryReadSummary(Path directory) {
        Path manifest = directory.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            CaptureSummary summary = BedrockTunnelJson.MAPPER.readValue(manifest.toFile(), CaptureSummary.class);
            return new HistoryCapture(directory, summary);
        } catch (IOException ignored) {
            return null;
        }
    }
}
