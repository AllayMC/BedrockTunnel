package org.allaymc.bedrocktunnel.capture;

import java.nio.file.Path;

public final class CaptureEntry {
    private final CapturedPacket packet;
    private final Path rawPath;
    private final Path jsonPath;
    private final boolean breakpointHit;
    private final boolean queuedWhilePaused;
    private volatile PacketState state;
    private volatile int replayCount;

    public CaptureEntry(CapturedPacket packet, Path rawPath, Path jsonPath, PacketState state, boolean breakpointHit, boolean queuedWhilePaused) {
        this.packet = packet;
        this.rawPath = rawPath;
        this.jsonPath = jsonPath;
        this.state = state;
        this.breakpointHit = breakpointHit;
        this.queuedWhilePaused = queuedWhilePaused;
    }

    public CapturedPacket packet() {
        return packet;
    }

    public Path rawPath() {
        return rawPath;
    }

    public Path jsonPath() {
        return jsonPath;
    }

    public PacketState state() {
        return state;
    }

    public void setState(PacketState state) {
        this.state = state;
    }

    public boolean breakpointHit() {
        return breakpointHit;
    }

    public boolean queuedWhilePaused() {
        return queuedWhilePaused;
    }

    public int replayCount() {
        return replayCount;
    }

    public void incrementReplayCount() {
        replayCount++;
    }

    public String packetType() {
        return packet.packetType();
    }
}
