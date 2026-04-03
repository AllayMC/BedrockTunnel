package org.allaymc.bedrocktunnel.capture;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.ByteBufUtil;
import org.allaymc.bedrocktunnel.BedrockTunnelJson;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PacketFormatter {
    private PacketFormatter() {
    }

    public static String toJson(BedrockPacket packet) {
        try {
            JsonNode node = BedrockTunnelJson.MAPPER.valueToTree(packet);
            if (!node.isMissingNode() && !node.isNull()) {
                return BedrockTunnelJson.MAPPER.writeValueAsString(node);
            }
        } catch (IllegalArgumentException | IOException ignored) {
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("packetType", packet.getPacketType().name());
        fallback.put("packetClass", packet.getClass().getName());
        fallback.put("description", packet.toString());
        if (packet instanceof UnknownPacket unknownPacket) {
            fallback.put("packetId", unknownPacket.getPacketId());
            fallback.put("payloadHex", unknownPacket.getPayload() == null ? "" : ByteBufUtil.hexDump(unknownPacket.getPayload()));
        }
        try {
            return BedrockTunnelJson.MAPPER.writeValueAsString(fallback);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize packet " + packet.getClass().getName(), exception);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 4);
        for (int offset = 0; offset < bytes.length; offset += 16) {
            builder.append("%08X  ".formatted(offset));
            int rowEnd = Math.min(offset + 16, bytes.length);
            for (int index = offset; index < offset + 16; index++) {
                if (index < rowEnd) {
                    builder.append("%02X ".formatted(bytes[index] & 0xFF));
                } else {
                    builder.append("   ");
                }
                if (index == offset + 7) {
                    builder.append(' ');
                }
            }
            builder.append(" |");
            for (int index = offset; index < rowEnd; index++) {
                int value = bytes[index] & 0xFF;
                builder.append(value >= 32 && value <= 126 ? (char) value : '.');
            }
            builder.append('|').append(System.lineSeparator());
        }
        return builder.toString();
    }

    public static String toSummary(CaptureEntry entry) {
        CapturedPacket packet = entry.packet();
        return """
                Sequence: %d
                Timestamp: %s
                Relative Time: %d ms
                Direction: %s
                Packet Type: %s
                Packet ID: %d
                Protocol Version: %d
                Sender Sub-Client: %d
                Target Sub-Client: %d
                Header Length: %d
                Byte Length: %d
                State: %s
                Breakpoint Hit: %s
                Queued While Paused: %s
                Replay Count: %d

                %s
                """.formatted(
                packet.sequence(),
                packet.capturedAt(),
                packet.relativeTimeMillis(),
                packet.direction(),
                packet.packetType(),
                packet.packetId(),
                packet.protocolVersion(),
                packet.senderSubClientId(),
                packet.targetSubClientId(),
                packet.headerLength(),
                packet.byteLength(),
                entry.state(),
                entry.breakpointHit(),
                entry.queuedWhilePaused(),
                entry.replayCount(),
                packet.description()
        );
    }
}
