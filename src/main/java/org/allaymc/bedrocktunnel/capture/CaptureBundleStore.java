package org.allaymc.bedrocktunnel.capture;

import org.allaymc.bedrocktunnel.BedrockTunnelJson;
import org.allaymc.bedrocktunnel.BedrockTunnelPaths;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CaptureBundleStore {
    private static final String MANIFEST_FILE = "capture.json";
    private static final String INDEX_FILE = "packets.jsonl.gz";
    private static final String LEGACY_INDEX_FILE = "packets.jsonl";
    private static final ObjectWriter RECORD_WRITER = BedrockTunnelJson.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT);

    private final Path sessionDirectory;

    public CaptureBundleStore(Path rootDirectory, String sessionId) throws IOException {
        this.sessionDirectory = rootDirectory.resolve(sessionId);
        Files.createDirectories(sessionDirectory);
    }

    public Path sessionDirectory() {
        return sessionDirectory;
    }

    public Path rawPathFor(long sequence) {
        return rawPathForSequence(sequence);
    }

    public Path jsonPathFor(long sequence) {
        return jsonPathForSequence(sequence);
    }

    public void writeIndex(CaptureSummary summary, List<StoredPacketRecord> records) throws IOException {
        Path index = sessionDirectory.resolve(INDEX_FILE);
        writeCompressedRecords(index, records);

        Path manifest = sessionDirectory.resolve(MANIFEST_FILE);
        writeAtomically(manifest, temp -> Files.writeString(
                temp,
                BedrockTunnelJson.MAPPER.writeValueAsString(summary),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        ));
    }

    public static Path rootDirectory() {
        return BedrockTunnelPaths.capturesDirectory();
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
        Path index = Files.exists(directory.resolve(INDEX_FILE)) ? directory.resolve(INDEX_FILE) : directory.resolve(LEGACY_INDEX_FILE);
        if (Files.exists(index)) {
            try (BufferedReader reader = openIndexReader(index)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    StoredPacketRecord record = BedrockTunnelJson.MAPPER.readValue(line, StoredPacketRecord.class);
                    byte[] rawBytes = record.rawBytes() != null ? record.rawBytes() : Files.readAllBytes(directory.resolve(record.rawPath()));
                    String jsonText = record.jsonText() != null ? record.jsonText() : Files.readString(directory.resolve(record.jsonPath()), StandardCharsets.UTF_8);
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
                            record.rawPath() == null ? rawPathForSequence(record.sequence()) : Path.of(record.rawPath()),
                            record.jsonPath() == null ? jsonPathForSequence(record.sequence()) : Path.of(record.jsonPath()),
                            record.state(),
                            record.breakpointHit(),
                            record.queuedWhilePaused()
                    );
                    for (int replayIndex = 0; replayIndex < record.replayCount(); replayIndex++) {
                        entry.incrementReplayCount();
                    }
                    entries.add(entry);
                }
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

    private static BufferedReader openIndexReader(Path index) throws IOException {
        if (index.getFileName().toString().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(index)), StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(index, StandardCharsets.UTF_8);
    }

    private void writeCompressedRecords(Path index, List<StoredPacketRecord> records) throws IOException {
        writeAtomically(index, temp -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)),
                    StandardCharsets.UTF_8
            ))) {
                for (StoredPacketRecord record : records) {
                    writer.write(RECORD_WRITER.writeValueAsString(record));
                    writer.newLine();
                }
            }
        });
    }

    private static void writeAtomically(Path target, AtomicWrite writer) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            writer.write(temp);
            moveReplacing(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path rawPathForSequence(long sequence) {
        return Path.of("payloads", "%06d.bin".formatted(sequence));
    }

    private static Path jsonPathForSequence(long sequence) {
        return Path.of("json", "%06d.json".formatted(sequence));
    }

    @FunctionalInterface
    private interface AtomicWrite {
        void write(Path path) throws IOException;
    }
}
