package org.allaymc.bedrocktunnel.capture;

import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;

public enum FlowDirection {
    CLIENT_TO_SERVER("Client -> Server", PacketRecipient.SERVER),
    SERVER_TO_CLIENT("Server -> Client", PacketRecipient.CLIENT);

    private final String displayName;
    private final PacketRecipient recipient;

    FlowDirection(String displayName, PacketRecipient recipient) {
        this.displayName = displayName;
        this.recipient = recipient;
    }

    public PacketRecipient recipient() {
        return recipient;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
