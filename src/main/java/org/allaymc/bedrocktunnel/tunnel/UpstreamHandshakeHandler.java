package org.allaymc.bedrocktunnel.tunnel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NetEaseCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketSignal;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.NetEaseEncryptionUtils;
import org.jose4j.jws.JsonWebSignature;

public final class UpstreamHandshakeHandler implements BedrockPacketHandler {
    private static final Logger LOGGER = LogManager.getLogger(UpstreamHandshakeHandler.class);

    private final TunnelServerSession session;
    private final TunnelController controller;
    private final TunnelRuntime runtime;

    UpstreamHandshakeHandler(TunnelServerSession session, TunnelController controller, TunnelRuntime runtime) {
        this.session = session;
        this.controller = controller;
        this.runtime = runtime;
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        int clientProtocol = packet.getProtocolVersion();
        if (clientProtocol != runtime.config().codec().protocolVersion()) {
            session.setCodec(BedrockCompat.disconnectCompat(clientProtocol));
            session.disconnect("BedrockTunnel is configured for v%d / %s, but the client uses v%d."
                    .formatted(runtime.config().codec().protocolVersion(), runtime.config().codec().displayVersion(), clientProtocol), false);
            return PacketSignal.HANDLED;
        }

        session.setCodec(runtime.config().codec().codec());
        TunnelController.applyFallbackCodecState(session.getPeer().getCodecHelper());
        NetworkSettingsPacket networkSettings = new NetworkSettingsPacket();
        networkSettings.setCompressionThreshold(0);
        networkSettings.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        session.sendPacketImmediately(networkSettings);
        if (runtime.config().codec().netEase()) {
            session.getPeer().setCompression(new SimpleCompressionStrategy(new NetEaseCompression()));
        } else {
            session.setCompression(PacketCompressionAlgorithm.ZLIB);
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        try {
            ChainValidationResult chain = validateLogin(packet);
            ChainValidationResult.IdentityClaims claims = chain.identityClaims();

            JsonWebSignature clientJwt = new JsonWebSignature();
            clientJwt.setCompactSerialization(packet.getClientJwt());
            clientJwt.setKey(claims.parsedIdentityPublicKey());
            if (!clientJwt.verifySignature()) {
                throw new IllegalStateException("Client JWT signature verification failed");
            }

            runtime.setIdentityData(claims.extraData);
            runtime.setRawIdentityClaims(chain.rawIdentityClaims());
            runtime.setSkinJson(clientJwt.getUnverifiedPayload());
            controller.connectDownstream(runtime);
            return PacketSignal.HANDLED;
        } catch (Exception exception) {
            LOGGER.error("Unable to complete upstream login", exception);
            session.disconnect("Unable to initialize the downstream tunnel.", false);
            return PacketSignal.HANDLED;
        }
    }

    private ChainValidationResult validateLogin(LoginPacket packet) throws Exception {
        if (runtime.config().codec().netEase() && packet.getAuthPayload() instanceof CertificateChainPayload chainPayload) {
            return NetEaseEncryptionUtils.validateChain(chainPayload);
        }
        return EncryptionUtils.validatePayload(packet.getAuthPayload());
    }

    @Override
    public void onDisconnect(String reason) {
        controller.handleRemoteDisconnect(runtime, reason);
    }
}
