package org.allaymc.bedrocktunnel.codec;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v291.Bedrock_v291;
import org.cloudburstmc.protocol.bedrock.codec.v313.Bedrock_v313;
import org.cloudburstmc.protocol.bedrock.codec.v332.Bedrock_v332;
import org.cloudburstmc.protocol.bedrock.codec.v340.Bedrock_v340;
import org.cloudburstmc.protocol.bedrock.codec.v354.Bedrock_v354;
import org.cloudburstmc.protocol.bedrock.codec.v361.Bedrock_v361;
import org.cloudburstmc.protocol.bedrock.codec.v388.Bedrock_v388;
import org.cloudburstmc.protocol.bedrock.codec.v389.Bedrock_v389;
import org.cloudburstmc.protocol.bedrock.codec.v390.Bedrock_v390;
import org.cloudburstmc.protocol.bedrock.codec.v407.Bedrock_v407;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.codec.v422.Bedrock_v422;
import org.cloudburstmc.protocol.bedrock.codec.v428.Bedrock_v428;
import org.cloudburstmc.protocol.bedrock.codec.v431.Bedrock_v431;
import org.cloudburstmc.protocol.bedrock.codec.v440.Bedrock_v440;
import org.cloudburstmc.protocol.bedrock.codec.v448.Bedrock_v448;
import org.cloudburstmc.protocol.bedrock.codec.v465.Bedrock_v465;
import org.cloudburstmc.protocol.bedrock.codec.v471.Bedrock_v471;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v486.Bedrock_v486;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v534.Bedrock_v534;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v545.Bedrock_v545;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v557.Bedrock_v557;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.codec.v567.Bedrock_v567;
import org.cloudburstmc.protocol.bedrock.codec.v568.Bedrock_v568;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v582.Bedrock_v582;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v630_netease.Bedrock_v630_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v686_netease.Bedrock_v686_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v766_netease.Bedrock_v766_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
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
            supported(Bedrock_v291.CODEC),
            supported(Bedrock_v313.CODEC),
            supported(Bedrock_v332.CODEC),
            supported(Bedrock_v340.CODEC),
            supported(Bedrock_v354.CODEC),
            supported(Bedrock_v361.CODEC),
            supported(Bedrock_v388.CODEC),
            supported(Bedrock_v389.CODEC),
            supported(Bedrock_v390.CODEC),
            supported(Bedrock_v407.CODEC),
            supported(Bedrock_v408.CODEC),
            supported(Bedrock_v419.CODEC),
            supported(Bedrock_v422.CODEC),
            supported(Bedrock_v428.CODEC),
            supported(Bedrock_v431.CODEC),
            supported(Bedrock_v440.CODEC),
            supported(Bedrock_v448.CODEC),
            supported(Bedrock_v465.CODEC),
            supported(Bedrock_v471.CODEC),
            supported(Bedrock_v475.CODEC),
            supported(Bedrock_v486.CODEC),
            supported(Bedrock_v503.CODEC),
            supported(Bedrock_v527.CODEC),
            supported(Bedrock_v534.CODEC),
            supported(Bedrock_v544.CODEC),
            supported(Bedrock_v545.CODEC),
            supported(Bedrock_v554.CODEC),
            supported(Bedrock_v557.CODEC),
            supported(Bedrock_v560.CODEC),
            supported(Bedrock_v567.CODEC),
            supported(Bedrock_v568.CODEC),
            supported(Bedrock_v575.CODEC),
            supported(Bedrock_v582.CODEC),
            supported(Bedrock_v589.CODEC),
            supported(Bedrock_v594.CODEC),
            supported(Bedrock_v618.CODEC),
            supported(Bedrock_v622.CODEC),
            supported(Bedrock_v630.CODEC),
            supported(Bedrock_v630_NetEase.CODEC, true),
            supported(Bedrock_v649.CODEC),
            supported(Bedrock_v662.CODEC),
            supported(Bedrock_v671.CODEC),
            supported(Bedrock_v685.CODEC),
            supported(Bedrock_v686.CODEC),
            supported(Bedrock_v686_NetEase.CODEC, true),
            supported(Bedrock_v712.CODEC),
            supported(Bedrock_v729.CODEC),
            supported(Bedrock_v748.CODEC),
            supported(Bedrock_v766.CODEC),
            supported(Bedrock_v766_NetEase.CODEC, true),
            supported(Bedrock_v776.CODEC),
            supported(Bedrock_v786.CODEC),
            supported(Bedrock_v800.CODEC),
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
