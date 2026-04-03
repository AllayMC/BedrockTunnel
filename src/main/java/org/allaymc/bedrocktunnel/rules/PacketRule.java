package org.allaymc.bedrocktunnel.rules;

import org.allaymc.bedrocktunnel.capture.CapturedPacket;

public record PacketRule(DirectionMatch direction, String packetType) {
    public PacketRule {
        packetType = packetType == null || packetType.isBlank() ? "UNKNOWN" : packetType;
    }

    public boolean matches(CapturedPacket packet) {
        return direction.matches(packet.direction()) && packetType.equals(packet.packetType());
    }
}
