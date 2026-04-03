package org.allaymc.bedrocktunnel.codec;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;

public record SupportedCodec(int protocolVersion, String minecraftVersion, boolean netEase, BedrockCodec codec) {
    public String displayVersion() {
        return netEase ? minecraftVersion + " (NetEase)" : minecraftVersion;
    }

    @Override
    public String toString() {
        return displayVersion() + " / v" + protocolVersion;
    }
}
