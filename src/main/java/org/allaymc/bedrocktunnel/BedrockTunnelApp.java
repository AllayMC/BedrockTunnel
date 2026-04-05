package org.allaymc.bedrocktunnel;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import org.allaymc.bedrocktunnel.codec.CodecRegistry;
import org.allaymc.bedrocktunnel.tunnel.TunnelController;
import org.allaymc.bedrocktunnel.ui.MainFrame;

import java.awt.Taskbar;
import javax.swing.SwingUtilities;

public final class BedrockTunnelApp {
    private BedrockTunnelApp() {
    }

    public static void main(String[] args) {
        ConsoleOutput.install();
        FlatMacDarkLaf.setup();

        TunnelController controller = new TunnelController();
        UserSettingsStore settingsStore = new UserSettingsStore();
        Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdown, "bedrock-tunnel-shutdown"));

        SwingUtilities.invokeLater(() -> {
            var codecs = CodecRegistry.sortedDescending();
            MainFrame frame = new MainFrame(
                    controller,
                    settingsStore,
                    settingsStore.load(codecs),
                    codecs,
                    CodecRegistry.packetTypes()
            );
            controller.attachFrame(frame);
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(frame.getIconImage());
                }
            }
            frame.setVisible(true);
            frame.startCaptureFromCurrentSettings();
        });
    }
}
