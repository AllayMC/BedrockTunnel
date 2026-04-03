package org.allaymc.bedrocktunnel.capture;

public record StoredPacketRecord(
        long sequence,
        String capturedAt,
        long relativeTimeMillis,
        FlowDirection direction,
        int packetId,
        String packetType,
        int protocolVersion,
        int senderSubClientId,
        int targetSubClientId,
        int headerLength,
        int byteLength,
        String description,
        PacketState state,
        boolean breakpointHit,
        boolean queuedWhilePaused,
        int replayCount,
        String rawPath,
        String jsonPath
) {
    public static StoredPacketRecord from(CaptureEntry entry) {
        CapturedPacket packet = entry.packet();
        return new StoredPacketRecord(
                packet.sequence(),
                packet.capturedAt().toString(),
                packet.relativeTimeMillis(),
                packet.direction(),
                packet.packetId(),
                packet.packetType(),
                packet.protocolVersion(),
                packet.senderSubClientId(),
                packet.targetSubClientId(),
                packet.headerLength(),
                packet.byteLength(),
                packet.description(),
                entry.state(),
                entry.breakpointHit(),
                entry.queuedWhilePaused(),
                entry.replayCount(),
                entry.rawPath().toString().replace('\\', '/'),
                entry.jsonPath().toString().replace('\\', '/')
        );
    }
}
