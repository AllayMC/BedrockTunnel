package org.allaymc.bedrocktunnel.capture;

import java.time.Instant;
import java.util.Arrays;

public record CapturedPacket(
        long sequence,
        Instant capturedAt,
        long relativeTimeMillis,
        FlowDirection direction,
        int packetId,
        String packetType,
        int protocolVersion,
        int senderSubClientId,
        int targetSubClientId,
        int headerLength,
        String description,
        String jsonText,
        byte[] rawBytes
) {
    public int byteLength() {
        return rawBytes.length;
    }

    public byte[] bodyBytes() {
        return Arrays.copyOfRange(rawBytes, headerLength, rawBytes.length);
    }
}
