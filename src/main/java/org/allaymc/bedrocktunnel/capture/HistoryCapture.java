package org.allaymc.bedrocktunnel.capture;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record HistoryCapture(Path directory, CaptureSummary summary) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public String toString() {
        return "%s | %s | %s".formatted(
                FORMATTER.format(summary.startedAt()),
                summary.targetAddress(),
                summary.minecraftVersion()
        );
    }
}
