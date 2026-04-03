package org.allaymc.bedrocktunnel.tunnel;

import org.allaymc.bedrocktunnel.codec.SupportedCodec;

import java.net.InetSocketAddress;

public record TunnelStartConfig(
        String listenHost,
        int listenPort,
        String targetHost,
        int targetPort,
        SupportedCodec codec
) {
    public InetSocketAddress listenAddress() {
        return new InetSocketAddress(listenHost, listenPort);
    }

    public InetSocketAddress targetAddress() {
        return new InetSocketAddress(targetHost, targetPort);
    }

    public String listenLabel() {
        return listenHost + ":" + listenPort;
    }

    public String targetLabel() {
        return targetHost + ":" + targetPort;
    }
}
