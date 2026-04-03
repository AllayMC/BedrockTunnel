package org.allaymc.bedrocktunnel.codec;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v819_netease.Bedrock_v819_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;

public final class CodecRegistry {
    private static final List<SupportedCodec> SUPPORTED = List.of(
            supported(Bedrock_v818.CODEC),
            supported(Bedrock_v819.CODEC),
            supported(Bedrock_v819_NetEase.CODEC, true),
            supported(Bedrock_v827.CODEC),
            supported(Bedrock_v844.CODEC),
            supported(Bedrock_v859.CODEC),
            supported(Bedrock_v860.CODEC),
            supported(Bedrock_v898.CODEC),
            supported(Bedrock_v924.CODEC),
            supported(Bedrock_v944.CODEC)
    );

    private static final List<String> PACKET_TYPES = List.of(BedrockPacketType.class.getFields()).stream()
            .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == BedrockPacketType.class)
            .map(CodecRegistry::packetTypeName)
            .sorted()
            .toList();

    private CodecRegistry() {
    }

    public static List<SupportedCodec> supportedCodecs() {
        return SUPPORTED;
    }

    public static SupportedCodec defaultCodec() {
        return SUPPORTED.getLast();
    }

    public static SupportedCodec byProtocolVersion(int protocolVersion) {
        return SUPPORTED.stream()
                .filter(codec -> codec.protocolVersion() == protocolVersion)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported protocol version: " + protocolVersion));
    }

    public static List<String> packetTypes() {
        return PACKET_TYPES;
    }

    public static List<SupportedCodec> sortedDescending() {
        return SUPPORTED.stream()
                .sorted(Comparator.comparingInt(SupportedCodec::protocolVersion).reversed())
                .toList();
    }

    private static SupportedCodec supported(BedrockCodec codec) {
        return supported(codec, false);
    }

    private static SupportedCodec supported(BedrockCodec codec, boolean netEase) {
        return new SupportedCodec(codec.getProtocolVersion(), codec.getMinecraftVersion(), netEase, codec);
    }

    private static String packetTypeName(Field field) {
        try {
            return ((BedrockPacketType) field.get(null)).name();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read packet type " + field.getName(), exception);
        }
    }
}
