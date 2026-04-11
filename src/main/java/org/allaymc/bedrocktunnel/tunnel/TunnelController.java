package org.allaymc.bedrocktunnel.tunnel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.allaymc.bedrocktunnel.capture.CaptureBundleStore;
import org.allaymc.bedrocktunnel.capture.CaptureEntry;
import org.allaymc.bedrocktunnel.capture.CaptureSummary;
import org.allaymc.bedrocktunnel.capture.CapturedPacket;
import org.allaymc.bedrocktunnel.capture.FlowDirection;
import org.allaymc.bedrocktunnel.capture.HistoryCapture;
import org.allaymc.bedrocktunnel.capture.LoadedCapture;
import org.allaymc.bedrocktunnel.capture.PacketFormatter;
import org.allaymc.bedrocktunnel.capture.PacketState;
import org.allaymc.bedrocktunnel.capture.PacketStatistics;
import org.allaymc.bedrocktunnel.capture.StoredPacketRecord;
import org.allaymc.bedrocktunnel.rules.RuleSet;
import org.allaymc.bedrocktunnel.ui.MainFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.definition.DefinitionRegistry;
import org.cloudburstmc.protocol.bedrock.definition.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemRegistryPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;

import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TunnelController {
    private static final Logger LOGGER = LogManager.getLogger(TunnelController.class);
    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;
    private static final int REQUEST_NETWORK_SETTINGS_MIN_PROTOCOL = Bedrock_v554.CODEC.getProtocolVersion();
    private static final DefinitionRegistry<BlockDefinition> UNKNOWN_BLOCK_DEFINITIONS = new UnknownBlockDefinitionRegistry();
    private static final DefinitionRegistry<ItemDefinition> UNKNOWN_ITEM_DEFINITIONS = new UnknownItemDefinitionRegistry();

    private final Object stateLock = new Object();
    private final ScheduledExecutorService backgroundExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bedrock-tunnel-bg");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong sequenceCounter = new AtomicLong();
    private final List<CaptureEntry> entries = new ArrayList<>();
    private final BedrockPong advertisement = new BedrockPong()
            .edition("MCPE")
            .motd("BedrockTunnel")
            .playerCount(0)
            .maximumPlayerCount(1)
            .subMotd("MITM Packet Tunnel")
            .gameType("Creative")
            .nintendoLimited(false);

    private volatile MainFrame frame;
    private volatile TunnelRuntime runtime;
    private volatile RuleSet ruleSet = RuleSet.DEFAULT;
    private volatile PacketStatistics statistics = new PacketStatistics();
    private volatile ScheduledFuture<?> persistFuture;
    private volatile PausedContext pausedContext;

    public void attachFrame(MainFrame frame) {
        this.frame = frame;
        refreshHistoryList();
    }

    static void applyFallbackCodecState(BedrockCodecHelper helper) {
        helper.setBlockDefinitions(UNKNOWN_BLOCK_DEFINITIONS);
        helper.setItemDefinitions(UNKNOWN_ITEM_DEFINITIONS);
    }

    public void shutdown() {
        stopCapture();
        backgroundExecutor.shutdown();
    }

    public void startCapture(TunnelStartConfig config) {
        Objects.requireNonNull(config, "config");
        backgroundExecutor.execute(() -> doStartCapture(config));
    }

    public void stopCapture() {
        backgroundExecutor.execute(this::doStopCapture);
    }

    public void updateRuleSet(RuleSet ruleSet) {
        this.ruleSet = ruleSet == null ? RuleSet.DEFAULT : ruleSet;
    }

    public void replay(CaptureEntry entry) {
        if (entry != null) {
            backgroundExecutor.execute(() -> doReplay(entry));
        }
    }

    public void forwardPaused() {
        backgroundExecutor.execute(() -> doResolvePaused(true));
    }

    public void dropPaused() {
        backgroundExecutor.execute(() -> doResolvePaused(false));
    }

    public void resumePaused() {
        backgroundExecutor.execute(this::doResumePaused);
    }

    public void loadHistory(HistoryCapture historyCapture) {
        if (historyCapture != null) {
            backgroundExecutor.execute(() -> doLoadHistory(historyCapture));
        }
    }

    public void refreshHistoryList() {
        backgroundExecutor.execute(() -> {
            try {
                List<HistoryCapture> history = CaptureBundleStore.listHistory();
                onEdt(() -> {
                    if (frame != null) {
                        frame.setHistory(history);
                    }
                });
            } catch (IOException exception) {
                showError("Unable to load capture history.", exception);
            }
        });
    }

    public void handlePacket(TunnelRuntime runtime, TunnelServerSession session, FlowDirection direction, BedrockPacketWrapper wrapper) {
        if (runtime.upstreamSession() != session) {
            return;
        }
        handlePacket(runtime, direction, wrapper);
    }

    public void handlePacket(TunnelRuntime runtime, TunnelClientSession session, FlowDirection direction, BedrockPacketWrapper wrapper) {
        if (runtime.downstreamSession() != session) {
            return;
        }
        handlePacket(runtime, direction, wrapper);
    }

    private void handlePacket(TunnelRuntime runtime, FlowDirection direction, BedrockPacketWrapper wrapper) {
        if (runtime != this.runtime || runtime.isStopping()) {
            return;
        }

        Instant captureStartedAt = runtime.startedAt();
        CaptureBundleStore captureStore = runtime.store();
        BedrockPacket packet = wrapper.getPacket();
        syncCodecHelperState(runtime, direction, packet);
        byte[] rawBytes = ByteBufUtil.getBytes(wrapper.getPacketBuffer(), wrapper.getPacketBuffer().readerIndex(), wrapper.getPacketBuffer().readableBytes(), false);
        CapturedPacket capturedPacket = new CapturedPacket(
                sequenceCounter.incrementAndGet(),
                Instant.now(),
                System.currentTimeMillis() - captureStartedAt.toEpochMilli(),
                direction,
                wrapper.getPacketId(),
                packet.getPacketType().name(),
                runtime.config().codec().protocolVersion(),
                wrapper.getSenderSubClientId(),
                wrapper.getTargetSubClientId(),
                wrapper.getHeaderLength(),
                packet.toString(),
                PacketFormatter.toJson(packet),
                rawBytes
        );

        CaptureEntry entry;
        boolean forwardImmediately = false;

        synchronized (stateLock) {
            Path rawPath = captureStore.rawPathFor(capturedPacket.sequence());
            Path jsonPath = captureStore.jsonPathFor(capturedPacket.sequence());

            if (pausedContext != null) {
                entry = addEntryLocked(captureStore, new CaptureEntry(capturedPacket, rawPath, jsonPath, PacketState.QUEUED, false, true));
                pausedContext.queuedPackets.addLast(new PendingPacket(entry));
            } else if (ruleSet.isBlocked(capturedPacket)) {
                entry = addEntryLocked(captureStore, new CaptureEntry(capturedPacket, rawPath, jsonPath, PacketState.BLOCKED, false, false));
            } else if (ruleSet.hitsBreakpoint(capturedPacket)) {
                entry = addEntryLocked(captureStore, new CaptureEntry(capturedPacket, rawPath, jsonPath, PacketState.PAUSED, true, false));
                pausedContext = new PausedContext(new PendingPacket(entry));
            } else {
                entry = addEntryLocked(captureStore, new CaptureEntry(capturedPacket, rawPath, jsonPath, PacketState.FORWARDED, false, false));
                forwardImmediately = true;
            }
        }

        if (forwardImmediately) {
            sendRawPacket(runtime, entry);
        }

        if (entry.state() == PacketState.PAUSED) {
            onEdt(() -> {
                if (frame != null) {
                    frame.setPausedEntry(entry, false);
                    frame.selectEntry(entry);
                }
            });
        }
    }

    public void handleUpstreamClosed(TunnelRuntime runtime, TunnelServerSession session, String reason) {
        backgroundExecutor.execute(() -> doHandleUpstreamClosed(runtime, session, reason));
    }

    public void handleDownstreamClosed(TunnelRuntime runtime, TunnelClientSession session, String reason) {
        backgroundExecutor.execute(() -> doHandleDownstreamClosed(runtime, session, reason));
    }

    public void connectDownstream(TunnelRuntime runtime) {
        backgroundExecutor.execute(() -> doConnectDownstream(runtime));
    }

    private void doConnectDownstream(TunnelRuntime runtime) {
        if (runtime != this.runtime || runtime.isStopping()) {
            return;
        }

        Channel channel = new Bootstrap()
                .group(runtime.eventLoopGroup())
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, runtime.config().codec().codec().getRaknetProtocolVersion())
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void preInitChannel(Channel channel) throws Exception {
                        super.preInitChannel(channel);
                        if (runtime.config().codec().netEase()) {
                            channel.pipeline().replace(
                                    CompressionCodec.NAME,
                                    CompressionCodec.NAME,
                                    new CompressionCodec(new SimpleCompressionStrategy(new NoopCompression()), false)
                            );
                        }
                    }

                    @Override
                    protected void initPacketCodec(Channel channel) throws Exception {
                        if (runtime.config().codec().netEase()) {
                            channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
                            return;
                        }
                        super.initPacketCodec(channel);
                    }

                    @Override
                    public TunnelClientSession createSession0(BedrockPeer peer, int subClientId) {
                        return new TunnelClientSession(peer, subClientId, TunnelController.this, runtime);
                    }

                    @Override
                    protected void initSession(BedrockClientSession session) {
                        TunnelClientSession tunnelSession = (TunnelClientSession) session;
                        runtime.setDownstreamSession(tunnelSession);
                        tunnelSession.setCodec(runtime.config().codec().codec());
                        tunnelSession.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);
                        applyFallbackCodecState(tunnelSession.getPeer().getCodecHelper());
                        tunnelSession.setPacketHandler(new DownstreamHandshakeHandler(tunnelSession, TunnelController.this, runtime));
                    }
                })
                .connect(runtime.config().targetAddress())
                .awaitUninterruptibly()
                .channel();

        if (!channel.isActive()) {
            throw new IllegalStateException("Unable to connect to " + runtime.config().targetLabel());
        }

        TunnelClientSession downstream = runtime.downstreamSession();
        if (downstream == null || !downstream.isConnected()) {
            throw new IllegalStateException("Downstream session was not initialized");
        }

        if (supportsRequestNetworkSettings(runtime.config().codec().protocolVersion())) {
            RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
            request.setProtocolVersion(runtime.config().codec().protocolVersion());
            downstream.sendPacketImmediately(request);
            LOGGER.info(
                    "Connected to downstream {} using {}, requested network settings",
                    runtime.config().targetLabel(),
                    runtime.config().codec().displayVersion()
            );
        } else {
            sendDownstreamLogin(downstream, runtime, "legacy protocol without RequestNetworkSettings");
        }

        onEdt(() -> {
            if (frame != null) {
                frame.setStatusText("Connected to target " + runtime.config().targetLabel() + ", completing login...");
            }
        });
    }

    public void onTunnelEstablished(TunnelRuntime runtime) {
        if (runtime != this.runtime || runtime.isStopping()) {
            return;
        }

        onEdt(() -> {
            if (frame != null) {
                frame.setStatusText("Live tunnel active");
                frame.setLiveMode(true, pausedContext != null, pausedContext != null && pausedContext.decisionMade);
            }
        });
    }

    static boolean supportsRequestNetworkSettings(int protocolVersion) {
        return protocolVersion >= REQUEST_NETWORK_SETTINGS_MIN_PROTOCOL;
    }

    private void sendDownstreamLogin(TunnelClientSession downstream, TunnelRuntime runtime, String reason) {
        if (!runtime.markDownstreamLoginSent()) {
            LOGGER.warn("Skipping downstream login for {} because it was already sent", runtime.config().targetLabel());
            return;
        }
        var loginPacket = LoginForgery.forgeLogin(runtime);
        LOGGER.info(
                "Sending downstream login to {} using {}: reason={}, loginPayload={}",
                runtime.config().targetLabel(),
                runtime.config().codec().displayVersion(),
                reason,
                loginPacket.getAuthPayload().getClass().getSimpleName()
        );
        downstream.sendPacketImmediately(loginPacket);
    }

    private void doStartCapture(TunnelStartConfig config) {
        if (runtime != null && !runtime.isStopping()) {
            showError("A live tunnel is already running.", null);
            return;
        }

        clearEntries();
        pausedContext = null;

        try {
            TunnelRuntime runtime = new TunnelRuntime(config);
            this.runtime = runtime;

            advertisement.protocolVersion(config.codec().protocolVersion())
                    .version(config.codec().minecraftVersion())
                    .ipv4Port(config.listenPort())
                    .ipv6Port(config.listenPort())
                    .serverId(System.nanoTime());

            Channel serverChannel = new ServerBootstrap()
                    .group(runtime.eventLoopGroup())
                    .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                    .option(RakChannelOption.RAK_ADVERTISEMENT, advertisement.toByteBuf())
                    .childHandler(new BedrockServerInitializer() {
                        @Override
                        protected void preInitChannel(Channel channel) throws Exception {
                            super.preInitChannel(channel);
                            Integer rakVersion = channel.config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
                            if (runtime.config().codec().netEase() && rakVersion != null && rakVersion == 8) {
                                channel.pipeline().replace(
                                        CompressionCodec.NAME,
                                        CompressionCodec.NAME,
                                        new CompressionCodec(new SimpleCompressionStrategy(new NoopCompression()), false)
                                );
                            }
                        }

                        @Override
                        protected void initPacketCodec(Channel channel) throws Exception {
                            Integer rakVersion = channel.config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
                            if (runtime.config().codec().netEase() && rakVersion != null && rakVersion == 8) {
                                channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
                                return;
                            }
                            super.initPacketCodec(channel);
                        }

                        @Override
                        public TunnelServerSession createSession0(BedrockPeer peer, int subClientId) {
                            return new TunnelServerSession(peer, subClientId, TunnelController.this, runtime);
                        }

                        @Override
                        protected void initSession(BedrockServerSession session) {
                            TunnelServerSession tunnelSession = (TunnelServerSession) session;
                            TunnelServerSession current = runtime.upstreamSession();
                            if (current == null || !current.isConnected()) {
                                runtime.setUpstreamSession(tunnelSession);
                                applyFallbackCodecState(tunnelSession.getPeer().getCodecHelper());
                                tunnelSession.setPacketHandler(new UpstreamHandshakeHandler(tunnelSession, TunnelController.this, runtime));
                            } else {
                                tunnelSession.setPacketHandler(new RejectingUpstreamHandler(tunnelSession));
                            }
                        }
                    })
                    .bind(config.listenAddress())
                    .awaitUninterruptibly()
                    .channel();

            runtime.setServerChannel(serverChannel);
            onEdt(() -> {
                if (frame != null) {
                    frame.clearEntries();
                    frame.setStatusText("Listening on " + config.listenLabel());
                    frame.setLiveMode(true, false, false);
                }
            });
        } catch (Exception exception) {
            TunnelRuntime failedRuntime = this.runtime;
            if (failedRuntime != null) {
                failedRuntime.stop();
                this.runtime = null;
            }
            showError("Unable to start the Bedrock tunnel.", exception);
        }
    }

    private void doStopCapture() {
        TunnelRuntime runtime = this.runtime;
        if (runtime == null) {
            return;
        }

        resolveRemainingPausedPackets();
        flushPersistNow(runtime);
        runtime.stop();
        flushPersistNow(runtime);
        this.runtime = null;

        onEdt(() -> {
            if (frame != null) {
                frame.setStatusText("Capture stopped");
                frame.setLiveMode(false, false, false);
                frame.updateStatistics(statistics.snapshot());
            }
        });
        refreshHistoryList();
    }

    private void doHandleUpstreamClosed(TunnelRuntime runtime, TunnelServerSession session, String reason) {
        if (runtime != this.runtime || runtime.isStopping() || runtime.upstreamSession() != session) {
            return;
        }

        runtime.setUpstreamSession(null);
        runtime.resetConnectionState();

        TunnelClientSession downstream = runtime.downstreamSession();
        if (downstream != null) {
            runtime.setDownstreamSession(null);
            if (downstream.isConnected()) {
                downstream.close("Upstream disconnected");
            }
        }

        onActiveTunnelClosed(runtime, disconnectMessage("Client", reason));
    }

    private void doHandleDownstreamClosed(TunnelRuntime runtime, TunnelClientSession session, String reason) {
        if (runtime != this.runtime || runtime.isStopping() || runtime.downstreamSession() != session) {
            return;
        }

        runtime.setDownstreamSession(null);
        runtime.resetConnectionState();

        TunnelServerSession upstream = runtime.upstreamSession();
        if (upstream != null) {
            runtime.setUpstreamSession(null);
            if (upstream.isConnected()) {
                upstream.close("Downstream disconnected");
            }
        }

        onActiveTunnelClosed(runtime, disconnectMessage("Target", reason));
    }

    private void doReplay(CaptureEntry entry) {
        TunnelRuntime runtime = this.runtime;
        if (runtime == null || runtime.isStopping()) {
            return;
        }

        sendRawPacket(runtime, entry);
        synchronized (stateLock) {
            entry.incrementReplayCount();
            statistics.recordReplay(entry);
        }
        onEdt(() -> {
            if (frame != null) {
                frame.updateEntry(entry);
                frame.updateStatistics(statistics.snapshot());
            }
        });
        schedulePersist();
    }

    private void doResolvePaused(boolean forward) {
        PausedContext paused = pausedContext;
        TunnelRuntime runtime = this.runtime;
        if (paused == null || runtime == null || paused.decisionMade) {
            return;
        }

        paused.decisionMade = true;
        CaptureEntry entry = paused.breakpointPacket.entry;
        PacketState oldState = entry.state();
        PacketState newState = forward ? PacketState.FORWARDED : PacketState.DROPPED;
        entry.setState(newState);
        statistics.recordStateChange(oldState, newState);
        if (forward) {
            sendRawPacket(runtime, entry);
        }
        onEdt(() -> {
            if (frame != null) {
                frame.updateEntry(entry);
                frame.updateStatistics(statistics.snapshot());
                frame.setPausedEntry(entry, true);
            }
        });
        schedulePersist();
    }

    private void doResumePaused() {
        PausedContext paused = pausedContext;
        TunnelRuntime runtime = this.runtime;
        if (paused == null || runtime == null || !paused.decisionMade) {
            return;
        }

        while (!paused.queuedPackets.isEmpty()) {
            PendingPacket pendingPacket = paused.queuedPackets.removeFirst();
            CaptureEntry entry = pendingPacket.entry;
            PacketState oldState = entry.state();
            entry.setState(PacketState.FORWARDED);
            statistics.recordStateChange(oldState, PacketState.FORWARDED);
            sendRawPacket(runtime, entry);
            onEdt(() -> {
                if (frame != null) {
                    frame.updateEntry(entry);
                }
            });
        }

        pausedContext = null;
        onEdt(() -> {
            if (frame != null) {
                frame.setLiveMode(true, false, false);
                frame.updateStatistics(statistics.snapshot());
            }
        });
        schedulePersist();
    }

    private void doLoadHistory(HistoryCapture historyCapture) {
        if (runtime != null && !runtime.isStopping()) {
            showError("Stop the live tunnel before opening capture history.", null);
            return;
        }

        try {
            LoadedCapture loadedCapture = CaptureBundleStore.load(historyCapture.directory());
            synchronized (stateLock) {
                entries.clear();
                entries.addAll(loadedCapture.entries());
                statistics = PacketStatistics.fromEntries(entries);
                pausedContext = null;
            }
            onEdt(() -> {
                if (frame != null) {
                    frame.showHistory(loadedCapture.entries(), loadedCapture.summary(), statistics.snapshot());
                }
            });
        } catch (IOException exception) {
            showError("Unable to open the selected capture history.", exception);
        }
    }

    private void sendRawPacket(TunnelRuntime runtime, CaptureEntry entry) {
        BedrockPacket packet = createRawPacket(entry.packet());
        if (entry.packet().direction() == FlowDirection.CLIENT_TO_SERVER) {
            TunnelClientSession downstream = runtime.downstreamSession();
            if (downstream != null && downstream.isConnected()) {
                downstream.getPeer().sendPacket(entry.packet().senderSubClientId(), entry.packet().targetSubClientId(), packet);
            }
        } else {
            TunnelServerSession upstream = runtime.upstreamSession();
            if (upstream != null && upstream.isConnected()) {
                upstream.getPeer().sendPacket(entry.packet().senderSubClientId(), entry.packet().targetSubClientId(), packet);
            }
        }
    }

    private UnknownPacket createRawPacket(CapturedPacket packet) {
        UnknownPacket rawPacket = new UnknownPacket();
        rawPacket.setPacketId(packet.packetId());
        rawPacket.setPayload(Unpooled.wrappedBuffer(packet.bodyBytes()));
        return rawPacket;
    }

    private void syncCodecHelperState(TunnelRuntime runtime, FlowDirection direction, BedrockPacket packet) {
        if (direction != FlowDirection.SERVER_TO_CLIENT) {
            return;
        }

        TunnelClientSession downstreamSession = runtime.downstreamSession();
        TunnelServerSession upstreamSession = runtime.upstreamSession();
        if (downstreamSession == null || upstreamSession == null) {
            return;
        }

        var downstreamHelper = downstreamSession.getPeer().getCodecHelper();
        var upstreamHelper = upstreamSession.getPeer().getCodecHelper();

        if (packet instanceof StartGamePacket startGamePacket) {
            if (startGamePacket.getBlockPalette() != null && !startGamePacket.getBlockPalette().isEmpty()) {
                DefinitionRegistry<BlockDefinition> blockDefinitions = new PaletteBlockDefinitionRegistry(
                        startGamePacket.getBlockPalette(),
                        startGamePacket.isBlockNetworkIdsHashed()
                );
                downstreamHelper.setBlockDefinitions(blockDefinitions);
                upstreamHelper.setBlockDefinitions(blockDefinitions);
            }

            if (!startGamePacket.getItemDefinitions().isEmpty()) {
                DefinitionRegistry<ItemDefinition> itemDefinitions = createItemDefinitions(startGamePacket.getItemDefinitions());
                downstreamHelper.setItemDefinitions(itemDefinitions);
                upstreamHelper.setItemDefinitions(itemDefinitions);
            }
            return;
        }

        if (packet instanceof ItemRegistryPacket itemRegistryPacket && !itemRegistryPacket.getItems().isEmpty()) {
            DefinitionRegistry<ItemDefinition> itemDefinitions = createItemDefinitions(itemRegistryPacket.getItems());
            downstreamHelper.setItemDefinitions(itemDefinitions);
            upstreamHelper.setItemDefinitions(itemDefinitions);
        }
    }

    private static DefinitionRegistry<ItemDefinition> createItemDefinitions(List<ItemDefinition> items) {
        var builder = SimpleDefinitionRegistry.<ItemDefinition>builder();
        boolean hasAir = false;
        for (ItemDefinition item : items) {
            builder.add(item);
            hasAir |= item.runtimeId() == 0;
        }
        if (!hasAir) {
            builder.add(ItemDefinition.AIR);
        }
        return builder.build();
    }

    private static int hashBlockDefinition(NbtMap block) {
        if ("minecraft:unknown".equals(block.getString("name"))) {
            return -2;
        }

        NbtMapBuilder statesBuilder = NbtMap.builder();
        statesBuilder.putAll(new TreeMap<>(block.getCompound("states")));

        NbtMap tag = NbtMap.builder()
                .putString("name", block.getString("name"))
                .putCompound("states", statesBuilder.build())
                .build();

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             NBTOutputStream outputStream = NbtUtils.createWriterLE(stream)) {
            outputStream.writeTag(tag);
            return fnv1a32(stream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to hash block definition " + block.getString("name"), exception);
        }
    }

    private static int fnv1a32(byte[] data) {
        int hash = FNV1_32_INIT;
        for (byte value : data) {
            hash ^= value & 0xff;
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }

    private CaptureEntry addEntryLocked(CaptureBundleStore captureStore, CaptureEntry entry) {
        entries.add(entry);
        statistics.recordNewEntry(entry);
        backgroundExecutor.execute(() -> {
            try {
                captureStore.savePacketAssets(entry);
            } catch (IOException exception) {
                LOGGER.error("Unable to save packet assets", exception);
            }
        });
        onEdt(() -> {
            if (frame != null) {
                frame.addEntry(entry);
                frame.updateStatistics(statistics.snapshot());
            }
        });
        schedulePersist();
        return entry;
    }

    private void clearEntries() {
        synchronized (stateLock) {
            entries.clear();
            statistics = new PacketStatistics();
            sequenceCounter.set(0);
        }
        onEdt(() -> {
            if (frame != null) {
                frame.clearEntries();
                frame.updateStatistics(statistics.snapshot());
            }
        });
    }

    private void schedulePersist() {
        TunnelRuntime runtime = this.runtime;
        if (runtime == null) {
            return;
        }

        ScheduledFuture<?> future = persistFuture;
        if (future != null) {
            future.cancel(false);
        }
        List<StoredPacketRecord> records = snapshotStoredPackets();
        if (records.isEmpty()) {
            persistFuture = null;
            return;
        }
        CaptureBundleStore captureStore = runtime.store();
        String sessionId = runtime.sessionId();
        Instant startedAt = runtime.startedAt();
        persistFuture = backgroundExecutor.schedule(() -> {
            try {
                writeIndex(captureStore, buildSummary(runtime, sessionId, startedAt, null, statistics.snapshot()), snapshotStoredPackets());
            } catch (IOException exception) {
                LOGGER.error("Unable to persist capture index", exception);
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    private void flushPersistNow(TunnelRuntime runtime) {
        flushPersistNow(runtime, runtime.store(), runtime.sessionId(), runtime.startedAt(), runtime.isStopping() ? runtime.endedAt() : null);
    }

    private void flushPersistNow(TunnelRuntime runtime, CaptureBundleStore captureStore, String sessionId, Instant startedAt, Instant endedAt) {
        ScheduledFuture<?> future = persistFuture;
        if (future != null) {
            future.cancel(false);
        }
        List<StoredPacketRecord> records = snapshotStoredPackets();
        if (records.isEmpty()) {
            persistFuture = null;
            return;
        }
        try {
            writeIndex(captureStore, buildSummary(runtime, sessionId, startedAt, endedAt, statistics.snapshot()), records);
        } catch (IOException exception) {
            LOGGER.error("Unable to flush capture index", exception);
        }
        persistFuture = null;
    }

    private List<StoredPacketRecord> snapshotStoredPackets() {
        synchronized (stateLock) {
            return entries.stream().map(StoredPacketRecord::from).toList();
        }
    }

    private CaptureSummary buildSummary(
            TunnelRuntime runtime,
            String sessionId,
            Instant startedAt,
            Instant endedAt,
            PacketStatistics.Snapshot snapshot
    ) {
        return new CaptureSummary(
                sessionId,
                startedAt,
                endedAt,
                runtime.config().listenLabel(),
                runtime.config().targetLabel(),
                runtime.config().codec().protocolVersion(),
                runtime.config().codec().displayVersion(),
                snapshot.totalPackets(),
                snapshot.totalBytes()
        );
    }

    private void writeIndex(CaptureBundleStore captureStore, CaptureSummary summary, List<StoredPacketRecord> records) throws IOException {
        captureStore.writeIndex(summary, records);
    }

    private void resolveRemainingPausedPackets() {
        PausedContext paused = pausedContext;
        if (paused == null) {
            return;
        }
        PacketState oldState = paused.breakpointPacket.entry.state();
        paused.breakpointPacket.entry.setState(PacketState.DROPPED);
        statistics.recordStateChange(oldState, PacketState.DROPPED);
        for (PendingPacket queuedPacket : paused.queuedPackets) {
            PacketState queuedState = queuedPacket.entry.state();
            queuedPacket.entry.setState(PacketState.DROPPED);
            statistics.recordStateChange(queuedState, PacketState.DROPPED);
        }
        pausedContext = null;
    }

    private void onActiveTunnelClosed(TunnelRuntime runtime, String statusText) {
        resolveRemainingPausedPackets();
        CaptureBundleStore captureStore = runtime.store();
        String sessionId = runtime.sessionId();
        Instant startedAt = runtime.startedAt();
        Instant endedAt = Instant.now();
        boolean hasEntries = !snapshotStoredPackets().isEmpty();

        if (hasEntries) {
            flushPersistNow(runtime, captureStore, sessionId, startedAt, endedAt);
            refreshHistoryList();
        } else {
            ScheduledFuture<?> future = persistFuture;
            if (future != null) {
                future.cancel(false);
            }
        }

        clearEntries();
        try {
            runtime.rotateCapture();
        } catch (IOException exception) {
            showError("Unable to prepare the next capture session.", exception);
            doStopCapture();
            return;
        }

        onEdt(() -> {
            if (frame != null) {
                frame.setStatusText(statusText);
                frame.setLiveMode(true, false, false);
            }
        });
    }

    private String disconnectMessage(String side, String reason) {
        if (reason == null || reason.isBlank()) {
            return side + " disconnected. Listening for the next client...";
        }
        return side + " disconnected: " + reason;
    }

    private void onEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private void showError(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
        onEdt(() -> {
            if (frame != null) {
                frame.showError(message, throwable);
            }
        });
    }

    private static final class PendingPacket {
        private final CaptureEntry entry;

        private PendingPacket(CaptureEntry entry) {
            this.entry = entry;
        }
    }

    private static final class PausedContext {
        private final PendingPacket breakpointPacket;
        private final ArrayDeque<PendingPacket> queuedPackets = new ArrayDeque<>();
        private boolean decisionMade;

        private PausedContext(PendingPacket breakpointPacket) {
            this.breakpointPacket = breakpointPacket;
        }
    }

    private static final class PaletteBlockDefinitionRegistry implements DefinitionRegistry<BlockDefinition> {
        private final Map<Integer, BlockDefinition> definitions;

        private PaletteBlockDefinitionRegistry(List<NbtMap> palette, boolean hashed) {
            this.definitions = new HashMap<>(palette.size());
            int runtimeId = 0;
            for (NbtMap definition : palette) {
                int id = hashed ? hashBlockDefinition(definition) : runtimeId++;
                this.definitions.put(id, new PaletteBlockDefinition(id, definition));
            }
        }

        @Override
        public BlockDefinition getDefinition(int runtimeId) {
            return definitions.get(runtimeId);
        }

        @Override
        public boolean isRegistered(BlockDefinition definition) {
            return definitions.get(definition.runtimeId()) == definition;
        }
    }

    private record PaletteBlockDefinition(int runtimeId, NbtMap tag) implements BlockDefinition {
    }

    private static final class UnknownBlockDefinitionRegistry implements DefinitionRegistry<BlockDefinition> {
        @Override
        public BlockDefinition getDefinition(int runtimeId) {
            return new UnknownBlockDefinition(runtimeId);
        }

        @Override
        public boolean isRegistered(BlockDefinition definition) {
            return true;
        }
    }

    private record UnknownBlockDefinition(int runtimeId) implements BlockDefinition {
    }

    private static final class UnknownItemDefinitionRegistry implements DefinitionRegistry<ItemDefinition> {
        @Override
        public ItemDefinition getDefinition(int runtimeId) {
            return new UnknownItemDefinition(runtimeId);
        }

        @Override
        public boolean isRegistered(ItemDefinition definition) {
            return true;
        }
    }

    private record UnknownItemDefinition(int runtimeId) implements ItemDefinition {
        @Override
        public String identifier() {
            return "bedrocktunnel:unknown_" + runtimeId;
        }

        @Override
        public boolean componentBased() {
            return false;
        }
    }
}
