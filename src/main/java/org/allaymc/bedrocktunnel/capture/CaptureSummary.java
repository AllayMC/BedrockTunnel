package org.allaymc.bedrocktunnel.capture;

import java.time.Instant;

public record CaptureSummary(
        String sessionId,
        Instant startedAt,
        Instant endedAt,
        String listenAddress,
        String targetAddress,
        int protocolVersion,
        String minecraftVersion,
        long packetCount,
        long totalBytes
) {
}
