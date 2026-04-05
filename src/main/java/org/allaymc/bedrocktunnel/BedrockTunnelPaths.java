package org.allaymc.bedrocktunnel;

import java.nio.file.Path;
import java.util.Locale;

public final class BedrockTunnelPaths {
    private static final String APP_DIRECTORY_NAME = "BedrockTunnel";
    private static final String HOME_PROPERTY = "bedrocktunnel.home";

    private BedrockTunnelPaths() {
    }

    public static Path dataDirectory() {
        String configuredHome = System.getProperty(HOME_PROPERTY);
        if (configuredHome != null && !configuredHome.isBlank()) {
            return Path.of(configuredHome);
        }

        Path userHome = Path.of(System.getProperty("user.home"));
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, APP_DIRECTORY_NAME);
            }
            return userHome.resolve("AppData").resolve("Local").resolve(APP_DIRECTORY_NAME);
        }
        if (osName.contains("mac")) {
            return userHome.resolve("Library").resolve("Application Support").resolve(APP_DIRECTORY_NAME);
        }

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome).resolve(APP_DIRECTORY_NAME);
        }
        return userHome.resolve(".local").resolve("share").resolve(APP_DIRECTORY_NAME);
    }

    public static Path settingsFile() {
        return dataDirectory().resolve("settings.json");
    }

    public static Path capturesDirectory() {
        return dataDirectory().resolve("captures");
    }
}
