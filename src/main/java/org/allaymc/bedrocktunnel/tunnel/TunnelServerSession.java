package org.allaymc.bedrocktunnel.tunnel;

import org.allaymc.bedrocktunnel.capture.FlowDirection;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.PacketSignal;

final class TunnelServerSession extends BedrockServerSession {
    private final TunnelController controller;
    private final TunnelRuntime runtime;

    TunnelServerSession(BedrockPeer peer, int subClientId, TunnelController controller, TunnelRuntime runtime) {
        super(peer, subClientId);
        this.controller = controller;
        this.runtime = runtime;
    }

    @Override
    protected void onPacket(BedrockPacketWrapper wrapper) {
        if (packetHandler != null && packetHandler.handlePacket(wrapper.getPacket()) == PacketSignal.HANDLED) {
            return;
        }
        controller.handlePacket(runtime, this, FlowDirection.CLIENT_TO_SERVER, wrapper);
    }

    @Override
    protected void onClose() {
        super.onClose();
        controller.handleUpstreamClosed(runtime, this, null);
    }
}
