package org.allaymc.bedrocktunnel.rules;

import org.allaymc.bedrocktunnel.capture.FlowDirection;

public enum DirectionMatch {
    ANY("Any"),
    CLIENT_TO_SERVER("Client -> Server"),
    SERVER_TO_CLIENT("Server -> Client");

    private final String displayName;

    DirectionMatch(String displayName) {
        this.displayName = displayName;
    }

    public boolean matches(FlowDirection direction) {
        return this == ANY || switch (this) {
            case CLIENT_TO_SERVER -> direction == FlowDirection.CLIENT_TO_SERVER;
            case SERVER_TO_CLIENT -> direction == FlowDirection.SERVER_TO_CLIENT;
            case ANY -> true;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
