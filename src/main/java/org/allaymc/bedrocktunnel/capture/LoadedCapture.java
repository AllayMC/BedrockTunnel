package org.allaymc.bedrocktunnel.capture;

import java.util.List;

public record LoadedCapture(CaptureSummary summary, List<CaptureEntry> entries) {
}
