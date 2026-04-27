package org.allaymc.bedrocktunnel.capture;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        String jsonPath,
        byte[] rawBytes,
        String jsonText
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
                null,
                null,
                packet.rawBytes(),
                packet.jsonText()
        );
    }
}
