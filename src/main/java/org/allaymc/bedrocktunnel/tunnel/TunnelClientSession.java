package org.allaymc.bedrocktunnel.tunnel;

import org.allaymc.bedrocktunnel.capture.FlowDirection;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.PacketSignal;

final class TunnelClientSession extends BedrockClientSession {
    private final TunnelController controller;
    private final TunnelRuntime runtime;

    TunnelClientSession(BedrockPeer peer, int subClientId, TunnelController controller, TunnelRuntime runtime) {
        super(peer, subClientId);
        this.controller = controller;
        this.runtime = runtime;
    }

    @Override
    protected void onPacket(BedrockPacketWrapper wrapper) {
        if (packetHandler != null && packetHandler.handlePacket(wrapper.getPacket()) == PacketSignal.HANDLED) {
            return;
        }
        controller.handlePacket(runtime, FlowDirection.SERVER_TO_CLIENT, wrapper);
    }

    @Override
    protected void onClose() {
        super.onClose();
        controller.handleSessionClosed(runtime);
    }
}
