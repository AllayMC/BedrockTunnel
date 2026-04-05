package org.allaymc.bedrocktunnel;

import org.allaymc.bedrocktunnel.codec.SupportedCodec;
import org.allaymc.bedrocktunnel.rules.PacketControlMode;
import org.allaymc.bedrocktunnel.rules.PacketRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

public final class UserSettingsStore {
    private static final Path DEFAULT_PATH = Path.of("settings.json");

    private final Path path;

    public UserSettingsStore() {
        this(DEFAULT_PATH);
    }

    public UserSettingsStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public Settings load(List<SupportedCodec> codecs) {
        Settings defaults = Settings.defaults(codecs);
        if (!Files.isRegularFile(path)) {
            return defaults;
        }

        try {
            Settings loaded = BedrockTunnelJson.MAPPER.readValue(path.toFile(), Settings.class);
            return sanitize(loaded, defaults, codecs);
        } catch (IOException exception) {
            return defaults;
        }
    }

    public void save(Settings settings) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    BedrockTunnelJson.MAPPER.writeValueAsString(settings),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save user settings", exception);
        }
    }

    private static Settings sanitize(Settings loaded, Settings defaults, List<SupportedCodec> codecs) {
        if (loaded == null) {
            return defaults;
        }

        CodecSelection selectedCodec = loaded.selectedCodec();
        CodecSelection codec = selectedCodec == null ? defaults.selectedCodec() : codecs.stream()
                .filter(candidate -> candidate.protocolVersion() == selectedCodec.protocolVersion()
                        && candidate.netEase() == selectedCodec.netEase())
                .findFirst()
                .map(candidate -> new CodecSelection(candidate.protocolVersion(), candidate.netEase()))
                .orElse(defaults.selectedCodec());

        return new Settings(
                blankToDefault(loaded.listenHost(), defaults.listenHost()),
                validPortOrDefault(loaded.listenPort(), defaults.listenPort()),
                blankToDefault(loaded.targetHost(), defaults.targetHost()),
                validPortOrDefault(loaded.targetPort(), defaults.targetPort()),
                codec,
                loaded.controlMode() == null ? defaults.controlMode() : loaded.controlMode(),
                loaded.blockRules(),
                loaded.breakpointRules()
        );
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int validPortOrDefault(int value, int defaultValue) {
        return value >= 1 && value <= 65535 ? value : defaultValue;
    }

    public record Settings(
            String listenHost,
            int listenPort,
            String targetHost,
            int targetPort,
            CodecSelection selectedCodec,
            PacketControlMode controlMode,
            List<PacketRule> blockRules,
            List<PacketRule> breakpointRules
    ) {
        public Settings {
            listenHost = listenHost == null || listenHost.isBlank() ? "0.0.0.0" : listenHost;
            listenPort = listenPort <= 0 ? 19134 : listenPort;
            targetHost = targetHost == null || targetHost.isBlank() ? "127.0.0.1" : targetHost;
            targetPort = targetPort <= 0 ? 19132 : targetPort;
            controlMode = controlMode == null ? PacketControlMode.BLACKLIST : controlMode;
            blockRules = blockRules == null ? List.of() : List.copyOf(blockRules);
            breakpointRules = breakpointRules == null ? List.of() : List.copyOf(breakpointRules);
        }

        public static Settings defaults(List<SupportedCodec> codecs) {
            SupportedCodec codec = codecs.getFirst();
            return new Settings(
                    "0.0.0.0",
                    19134,
                    "127.0.0.1",
                    19132,
                    new CodecSelection(codec.protocolVersion(), codec.netEase()),
                    PacketControlMode.BLACKLIST,
                    List.of(),
                    List.of()
            );
        }
    }

    public record CodecSelection(int protocolVersion, boolean netEase) {
    }
}
