package org.allaymc.bedrocktunnel.capture;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PacketStatistics {
    private final EnumMap<PacketState, Long> stateCounts = new EnumMap<>(PacketState.class);
    private final Map<String, TypeCounter> byType = new HashMap<>();
    private long totalPackets;
    private long totalBytes;
    private long totalReplays;
    private long breakpointHits;

    public PacketStatistics() {
        for (PacketState state : PacketState.values()) {
            stateCounts.put(state, 0L);
        }
    }

    public void recordNewEntry(CaptureEntry entry) {
        totalPackets++;
        totalBytes += entry.packet().byteLength();
        stateCounts.compute(entry.state(), (ignored, count) -> count == null ? 1L : count + 1);
        if (entry.breakpointHit()) {
            breakpointHits++;
        }
        byType.computeIfAbsent(entry.packetType(), ignored -> new TypeCounter())
                .add(entry.packet().byteLength());
    }

    public void recordStateChange(PacketState oldState, PacketState newState) {
        if (oldState == newState) {
            return;
        }
        stateCounts.compute(oldState, (ignored, count) -> count == null ? 0L : Math.max(0L, count - 1));
        stateCounts.compute(newState, (ignored, count) -> count == null ? 1L : count + 1);
    }

    public void recordReplay(CaptureEntry entry) {
        totalReplays++;
        byType.computeIfAbsent(entry.packetType(), ignored -> new TypeCounter()).replays++;
    }

    public Snapshot snapshot() {
        Map<PacketState, Long> states = new EnumMap<>(stateCounts);
        List<TypeStat> types = byType.entrySet().stream()
                .map(entry -> new TypeStat(entry.getKey(), entry.getValue().count, entry.getValue().bytes, entry.getValue().replays))
                .sorted(Comparator.comparingLong(TypeStat::count).reversed().thenComparing(TypeStat::packetType))
                .toList();
        return new Snapshot(totalPackets, totalBytes, totalReplays, breakpointHits, states, types);
    }

    public static PacketStatistics fromEntries(List<CaptureEntry> entries) {
        PacketStatistics statistics = new PacketStatistics();
        for (CaptureEntry entry : entries) {
            statistics.recordNewEntry(entry);
            for (int index = 0; index < entry.replayCount(); index++) {
                statistics.recordReplay(entry);
            }
        }
        return statistics;
    }

    public record TypeStat(String packetType, long count, long bytes, long replays) {
    }

    public record Snapshot(
            long totalPackets,
            long totalBytes,
            long totalReplays,
            long breakpointHits,
            Map<PacketState, Long> stateCounts,
            List<TypeStat> packetTypes
    ) {
    }

    private static final class TypeCounter {
        private long count;
        private long bytes;
        private long replays;

        private void add(long size) {
            count++;
            bytes += size;
        }
    }
}
