package org.allaymc.bedrocktunnel.tunnel;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import org.allaymc.bedrocktunnel.capture.CaptureBundleStore;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;

import java.io.IOException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class TunnelRuntime {
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean downstreamLoginSent = new AtomicBoolean();
    private final String sessionId;
    private final CaptureBundleStore store;
    private final TunnelStartConfig config;
    private final Instant startedAt = Instant.now();
    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final KeyPair proxyKeyPair = EncryptionUtils.createKeyPair();
    private volatile Instant endedAt;
    private volatile Channel serverChannel;
    private volatile TunnelServerSession upstreamSession;
    private volatile TunnelClientSession downstreamSession;
    private volatile ChainValidationResult.IdentityData identityData;
    private volatile Map<String, Object> rawIdentityClaims;
    private volatile String skinJson;

    TunnelRuntime(TunnelStartConfig config) throws IOException {
        this.config = config;
        this.sessionId = CaptureBundleStore.sessionIdFor(config.targetHost(), config.targetPort());
        this.store = new CaptureBundleStore(CaptureBundleStore.rootDirectory(), sessionId);
    }

    public String sessionId() {
        return sessionId;
    }

    public CaptureBundleStore store() {
        return store;
    }

    public TunnelStartConfig config() {
        return config;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public NioEventLoopGroup eventLoopGroup() {
        return eventLoopGroup;
    }

    public KeyPair proxyKeyPair() {
        return proxyKeyPair;
    }

    public Channel serverChannel() {
        return serverChannel;
    }

    public void setServerChannel(Channel serverChannel) {
        this.serverChannel = serverChannel;
    }

    public TunnelServerSession upstreamSession() {
        return upstreamSession;
    }

    public void setUpstreamSession(TunnelServerSession upstreamSession) {
        this.upstreamSession = upstreamSession;
    }

    public TunnelClientSession downstreamSession() {
        return downstreamSession;
    }

    public void setDownstreamSession(TunnelClientSession downstreamSession) {
        this.downstreamSession = downstreamSession;
    }

    public ChainValidationResult.IdentityData identityData() {
        return identityData;
    }

    public void setIdentityData(ChainValidationResult.IdentityData identityData) {
        this.identityData = identityData;
    }

    public Map<String, Object> rawIdentityClaims() {
        return rawIdentityClaims;
    }

    public void setRawIdentityClaims(Map<String, Object> rawIdentityClaims) {
        this.rawIdentityClaims = rawIdentityClaims;
    }

    public String skinJson() {
        return skinJson;
    }

    public void setSkinJson(String skinJson) {
        this.skinJson = skinJson;
    }

    public boolean isStopping() {
        return stopping.get();
    }

    public boolean markDownstreamLoginSent() {
        return downstreamLoginSent.compareAndSet(false, true);
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        endedAt = Instant.now();

        if (downstreamSession != null && downstreamSession.isConnected()) {
            downstreamSession.close("Tunnel stopped");
        }
        if (upstreamSession != null && upstreamSession.isConnected()) {
            upstreamSession.close("Tunnel stopped");
        }
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
    }
}
