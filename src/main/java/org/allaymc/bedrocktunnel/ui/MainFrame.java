package org.allaymc.bedrocktunnel.ui;

import org.allaymc.bedrocktunnel.ConsoleOutput;
import org.allaymc.bedrocktunnel.UserSettingsStore;
import org.allaymc.bedrocktunnel.capture.CaptureEntry;
import org.allaymc.bedrocktunnel.capture.CaptureSummary;
import org.allaymc.bedrocktunnel.capture.HistoryCapture;
import org.allaymc.bedrocktunnel.capture.PacketFormatter;
import org.allaymc.bedrocktunnel.capture.PacketState;
import org.allaymc.bedrocktunnel.capture.PacketStatistics;
import org.allaymc.bedrocktunnel.codec.SupportedCodec;
import org.allaymc.bedrocktunnel.rules.DirectionMatch;
import org.allaymc.bedrocktunnel.rules.PacketControlMode;
import org.allaymc.bedrocktunnel.rules.PacketRule;
import org.allaymc.bedrocktunnel.rules.RuleSet;
import org.allaymc.bedrocktunnel.tunnel.TunnelController;
import org.allaymc.bedrocktunnel.tunnel.TunnelStartConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainFrame extends JFrame {
    private final TunnelController controller;
    private final UserSettingsStore settingsStore;
    private final List<SupportedCodec> codecs;
    private final List<String> packetTypes;
    private final CaptureTableModel captureTableModel = new CaptureTableModel();
    private final RuleTableModel blockRuleTableModel = new RuleTableModel();
    private final RuleTableModel breakpointRuleTableModel = new RuleTableModel();
    private final PacketStatsTableModel packetStatsTableModel = new PacketStatsTableModel();
    private final DefaultListModel<HistoryCapture> historyListModel = new DefaultListModel<>();
    private final TableRowSorter<CaptureTableModel> captureSorter = new TableRowSorter<>(captureTableModel);
    private final Map<Long, String> hexCache = new HashMap<>();

    private final JTextField listenHostField = new JTextField("0.0.0.0", 10);
    private final JTextField listenPortField = new JTextField("19134", 6);
    private final JTextField targetHostField = new JTextField("127.0.0.1", 14);
    private final JTextField targetPortField = new JTextField("19132", 6);
    private final JComboBox<SupportedCodec> codecBox;
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton replayButton = new JButton("Replay");
    private final JButton forwardButton = new JButton("Forward");
    private final JButton dropButton = new JButton("Drop");
    private final JButton resumeButton = new JButton("Resume");
    private final JButton openConsoleButton = new JButton("Open Console");
    private final JLabel statusLabel = new JLabel("Idle");

    private final JComboBox<Object> filterDirectionBox = new JComboBox<>(new Object[]{"Any", DirectionMatch.CLIENT_TO_SERVER, DirectionMatch.SERVER_TO_CLIENT});
    private final JComboBox<Object> filterStateBox = new JComboBox<>();
    private final JComboBox<String> filterPacketTypeBox;
    private final JTextField filterKeywordField = new JTextField(16);

    private final JTextArea summaryArea = createTextArea();
    private final JTextArea jsonArea = createTextArea();
    private final JTextArea hexArea = createTextArea();

    private final JComboBox<DirectionMatch> ruleDirectionBox = new JComboBox<>(DirectionMatch.values());
    private final JComboBox<String> rulePacketTypeBox;
    private final JComboBox<PacketControlMode> ruleModeBox = new JComboBox<>(PacketControlMode.values());

    private final JLabel totalPacketsLabel = new JLabel("0");
    private final JLabel totalBytesLabel = new JLabel("0");
    private final JLabel replayCountLabel = new JLabel("0");
    private final JLabel breakpointCountLabel = new JLabel("0");

    private final JTable captureTable = new JTable(captureTableModel);
    private final JList<HistoryCapture> historyList = new JList<>(historyListModel);

    private boolean liveMode;
    private boolean paused;
    private boolean pausedActionChosen;
    private boolean applyingSettings;
    private boolean updatingRulePacketTypeBox;
    private boolean rulePacketTypeUpdatePending;
    private JFrame consoleFrame;
    private Runnable consoleUnsubscribe;

    public MainFrame(TunnelController controller, UserSettingsStore settingsStore, UserSettingsStore.Settings settings, List<SupportedCodec> codecs, List<String> packetTypes) {
        super("BedrockTunnel");
        this.controller = controller;
        this.settingsStore = settingsStore;
        this.codecs = List.copyOf(codecs);
        this.packetTypes = List.copyOf(packetTypes);
        this.codecBox = new JComboBox<>(codecs.toArray(SupportedCodec[]::new));
        this.filterPacketTypeBox = new JComboBox<>(buildPacketChoices(this.packetTypes));
        this.rulePacketTypeBox = new JComboBox<>(this.packetTypes.toArray(String[]::new));

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1500, 920));
        setLayout(new BorderLayout(8, 8));
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomTabs(), BorderLayout.SOUTH);

        configureCaptureTable();
        configureFilters();
        configureButtons();
        configureStats();
        configureRulePacketTypeBox();
        applySettings(settings);
        applyRules();
        configurePersistence();

        pack();
        setLocationRelativeTo(null);
        refreshActionButtons();
    }

    public void setStatusText(String text) {
        statusLabel.setText(text);
    }

    public void setHistory(List<HistoryCapture> history) {
        historyListModel.clear();
        history.forEach(historyListModel::addElement);
    }

    public void clearEntries() {
        captureTableModel.clear();
        summaryArea.setText("");
        jsonArea.setText("");
        hexArea.setText("");
        hexCache.clear();
    }

    public void addEntry(CaptureEntry entry) {
        captureTableModel.addEntry(entry);
        refreshActionButtons();
    }

    public void updateEntry(CaptureEntry entry) {
        captureTableModel.updateEntry(entry);
        if (selectedEntry() != null && selectedEntry().packet().sequence() == entry.packet().sequence()) {
            showEntry(entry);
        }
        refreshActionButtons();
    }

    public void selectEntry(CaptureEntry entry) {
        int modelRow = captureTableModel.indexOf(entry);
        if (modelRow < 0) {
            return;
        }
        int viewRow = captureSorter.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            captureTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            captureTable.scrollRectToVisible(captureTable.getCellRect(viewRow, 0, true));
        }
    }

    public void updateStatistics(PacketStatistics.Snapshot statistics) {
        totalPacketsLabel.setText(Long.toString(statistics.totalPackets()));
        totalBytesLabel.setText(Long.toString(statistics.totalBytes()));
        replayCountLabel.setText(Long.toString(statistics.totalReplays()));
        breakpointCountLabel.setText(Long.toString(statistics.breakpointHits()));
        packetStatsTableModel.setRows(statistics.packetTypes());
    }

    public void setPausedEntry(CaptureEntry entry, boolean actionChosen) {
        paused = true;
        pausedActionChosen = actionChosen;
        selectEntry(entry);
        refreshActionButtons();
    }

    public void setLiveMode(boolean live, boolean paused, boolean actionChosen) {
        this.liveMode = live;
        this.paused = paused;
        this.pausedActionChosen = actionChosen;
        refreshActionButtons();
    }

    public void showHistory(List<CaptureEntry> entries, CaptureSummary summary, PacketStatistics.Snapshot statistics) {
        liveMode = false;
        paused = false;
        pausedActionChosen = false;
        captureTableModel.setEntries(entries);
        hexCache.clear();
        summaryArea.setText("");
        jsonArea.setText("");
        hexArea.setText("");
        updateStatistics(statistics);
        setStatusText("Viewing history: " + summary.targetAddress() + " / " + summary.minecraftVersion());
        refreshActionButtons();
    }

    public void showError(String message, Throwable throwable) {
        String detail = throwable == null ? message : message + System.lineSeparator() + throwable.getMessage();
        JOptionPane.showMessageDialog(this, detail, "BedrockTunnel", JOptionPane.ERROR_MESSAGE);
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        panel.add(buildConnectionPanel(), BorderLayout.NORTH);
        panel.add(buildControlPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridy = 0;

        addField(panel, constraints, 0, "Listen Host", listenHostField, 0.22);
        addField(panel, constraints, 2, "Listen Port", listenPortField, 0.0);
        addField(panel, constraints, 4, "Target Host", targetHostField, 0.30);
        addField(panel, constraints, 6, "Target Port", targetPortField, 0.0);
        addField(panel, constraints, 8, "Version", codecBox, 0.12);
        return panel;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel();
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(replayButton);
        buttons.add(forwardButton);
        buttons.add(dropButton);
        buttons.add(resumeButton);
        buttons.add(openConsoleButton);
        panel.add(buttons, BorderLayout.WEST);

        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private JSplitPane buildCenterPanel() {
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        left.add(buildFilterPanel(), BorderLayout.NORTH);
        left.add(new JScrollPane(captureTable), BorderLayout.CENTER);

        JTabbedPane detailTabs = new JTabbedPane();
        detailTabs.addTab("Summary", new JScrollPane(summaryArea));
        detailTabs.addTab("JSON", new JScrollPane(jsonArea));
        detailTabs.addTab("Hex", new JScrollPane(hexArea));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailTabs);
        splitPane.setResizeWeight(0.68);
        return splitPane;
    }

    private JTabbedPane buildBottomTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setPreferredSize(new Dimension(1200, 270));
        tabs.addTab("Rules", buildRulesPanel());
        tabs.addTab("Stats", buildStatsPanel());
        tabs.addTab("History", buildHistoryPanel());
        return tabs;
    }

    private JPanel buildFilterPanel() {
        JPanel panel = new JPanel();
        panel.add(new JLabel("Direction"));
        panel.add(filterDirectionBox);
        panel.add(new JLabel("State"));
        panel.add(filterStateBox);
        panel.add(new JLabel("Packet"));
        panel.add(filterPacketTypeBox);
        panel.add(new JLabel("Keyword"));
        panel.add(filterKeywordField);
        return panel;
    }

    private JPanel buildRulesPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel();
        top.add(new JLabel("Mode"));
        top.add(ruleModeBox);
        top.add(new JLabel("Direction"));
        top.add(ruleDirectionBox);
        top.add(new JLabel("Packet"));
        top.add(rulePacketTypeBox);
        JButton addBlockButton = new JButton("Add Block");
        JButton addBreakpointButton = new JButton("Add Breakpoint");
        top.add(addBlockButton);
        top.add(addBreakpointButton);
        panel.add(top, BorderLayout.NORTH);

        JTable blockTable = new JTable(blockRuleTableModel);
        JTable breakpointTable = new JTable(breakpointRuleTableModel);
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.gridx = 0;
        constraints.gridy = 0;
        center.add(wrapWithTitle("Block Rules", new JScrollPane(blockTable)), constraints);
        constraints.gridx = 1;
        center.add(wrapWithTitle("Breakpoint Rules", new JScrollPane(breakpointTable)), constraints);
        panel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton removeBlockButton = new JButton("Remove Block");
        JButton removeBreakpointButton = new JButton("Remove Breakpoint");
        bottom.add(removeBlockButton);
        bottom.add(removeBreakpointButton);
        panel.add(bottom, BorderLayout.SOUTH);

        addBlockButton.addActionListener(event -> {
            addRule(blockRuleTableModel);
        });
        addBreakpointButton.addActionListener(event -> {
            addRule(breakpointRuleTableModel);
        });
        removeBlockButton.addActionListener(event -> {
            int row = blockTable.getSelectedRow();
            if (row >= 0) {
                blockRuleTableModel.removeRow(row);
                applyRules();
            }
        });
        removeBreakpointButton.addActionListener(event -> {
            int row = breakpointTable.getSelectedRow();
            if (row >= 0) {
                breakpointRuleTableModel.removeRow(row);
                applyRules();
            }
        });
        ruleModeBox.addActionListener(event -> applyRules());
        return panel;
    }

    private JPanel buildStatsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel counters = new JPanel();
        counters.add(new JLabel("Packets"));
        counters.add(totalPacketsLabel);
        counters.add(Box.createHorizontalStrut(20));
        counters.add(new JLabel("Bytes"));
        counters.add(totalBytesLabel);
        counters.add(Box.createHorizontalStrut(20));
        counters.add(new JLabel("Replays"));
        counters.add(replayCountLabel);
        counters.add(Box.createHorizontalStrut(20));
        counters.add(new JLabel("Breakpoints"));
        counters.add(breakpointCountLabel);
        panel.add(counters, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(packetStatsTableModel)), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(historyList), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton openButton = new JButton("Open Selected");
        buttons.add(refreshButton);
        buttons.add(openButton);
        panel.add(buttons, BorderLayout.SOUTH);
        refreshButton.addActionListener(event -> controller.refreshHistoryList());
        openButton.addActionListener(event -> controller.loadHistory(historyList.getSelectedValue()));
        return panel;
    }

    private void configureCaptureTable() {
        captureTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        captureTable.setAutoCreateRowSorter(false);
        captureTable.setRowSorter(captureSorter);
        captureTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showEntry(selectedEntry());
                refreshActionButtons();
            }
        });
    }

    private void configureFilters() {
        filterStateBox.addItem("Any");
        for (PacketState state : PacketState.values()) {
            filterStateBox.addItem(state);
        }

        filterDirectionBox.addActionListener(event -> applyFilters());
        filterStateBox.addActionListener(event -> applyFilters());
        filterPacketTypeBox.addActionListener(event -> applyFilters());
        filterKeywordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applyFilters();
            }
        });
    }

    private void configureButtons() {
        startButton.addActionListener(event -> {
            try {
                TunnelStartConfig config = parseStartConfig();
                persistSettings();
                controller.startCapture(config);
            } catch (RuntimeException exception) {
                showError("Unable to start capture. Check the connection fields.", exception);
            }
        });
        stopButton.addActionListener(event -> controller.stopCapture());
        replayButton.addActionListener(event -> controller.replay(selectedEntry()));
        forwardButton.addActionListener(event -> controller.forwardPaused());
        dropButton.addActionListener(event -> controller.dropPaused());
        resumeButton.addActionListener(event -> controller.resumePaused());
        openConsoleButton.addActionListener(event -> openConsoleWindow());
    }

    private void configureRulePacketTypeBox() {
        rulePacketTypeBox.setEditable(true);
        var editorComponent = rulePacketTypeBox.getEditor().getEditorComponent();
        if (!(editorComponent instanceof JTextComponent textComponent)) {
            return;
        }

        textComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleRulePacketTypeChoicesUpdate(textComponent);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleRulePacketTypeChoicesUpdate(textComponent);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleRulePacketTypeChoicesUpdate(textComponent);
            }
        });
    }

    private void configureStats() {
        updateStatistics(new PacketStatistics.Snapshot(0, 0, 0, 0, Map.of(), List.of()));
    }

    private void configurePersistence() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                persistSettings();
                closeConsoleWindow();
            }
        });
    }

    private void applyFilters() {
        captureSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends CaptureTableModel, ? extends Integer> row) {
                CaptureEntry entry = captureTableModel.entryAt(row.getIdentifier());
                Object direction = filterDirectionBox.getSelectedItem();
                if (direction instanceof DirectionMatch match && !match.matches(entry.packet().direction())) {
                    return false;
                }

                Object state = filterStateBox.getSelectedItem();
                if (state instanceof PacketState packetState && entry.state() != packetState) {
                    return false;
                }

                Object packetType = filterPacketTypeBox.getSelectedItem();
                if (packetType instanceof String type && !"Any".equals(type) && !type.equals(entry.packet().packetType())) {
                    return false;
                }

                String keyword = filterKeywordField.getText().trim().toLowerCase(Locale.ROOT);
                if (keyword.isBlank()) {
                    return true;
                }

                return entry.packet().packetType().toLowerCase(Locale.ROOT).contains(keyword)
                        || entry.packet().description().toLowerCase(Locale.ROOT).contains(keyword)
                        || entry.packet().jsonText().toLowerCase(Locale.ROOT).contains(keyword)
                        || hexText(entry).toLowerCase(Locale.ROOT).contains(keyword);
            }
        });
    }

    private void applyRules() {
        controller.updateRuleSet(currentRuleSet());
        if (!applyingSettings) {
            persistSettings();
        }
    }

    private RuleSet currentRuleSet() {
        return new RuleSet(
                (PacketControlMode) ruleModeBox.getSelectedItem(),
                blockRuleTableModel.rules(),
                breakpointRuleTableModel.rules()
        );
    }

    private void applySettings(UserSettingsStore.Settings settings) {
        applyingSettings = true;
        try {
            listenHostField.setText(settings.listenHost());
            listenPortField.setText(Integer.toString(settings.listenPort()));
            targetHostField.setText(settings.targetHost());
            targetPortField.setText(Integer.toString(settings.targetPort()));
            ruleModeBox.setSelectedItem(settings.controlMode());
            blockRuleTableModel.setRules(settings.blockRules());
            breakpointRuleTableModel.setRules(settings.breakpointRules());
            codecBox.setSelectedItem(findCodec(settings.selectedCodec()));
        } finally {
            applyingSettings = false;
        }
    }

    private SupportedCodec findCodec(UserSettingsStore.CodecSelection selection) {
        return codecs.stream()
                .filter(codec -> codec.protocolVersion() == selection.protocolVersion() && codec.netEase() == selection.netEase())
                .findFirst()
                .orElse(codecs.getFirst());
    }

    private void persistSettings() {
        settingsStore.save(new UserSettingsStore.Settings(
                listenHostField.getText().trim(),
                parsePortOrDefault(listenPortField.getText(), 19132),
                targetHostField.getText().trim(),
                parsePortOrDefault(targetPortField.getText(), 19132),
                new UserSettingsStore.CodecSelection(selectedCodec().protocolVersion(), selectedCodec().netEase()),
                (PacketControlMode) ruleModeBox.getSelectedItem(),
                blockRuleTableModel.rules(),
                breakpointRuleTableModel.rules()
        ));
    }

    private SupportedCodec selectedCodec() {
        SupportedCodec codec = (SupportedCodec) codecBox.getSelectedItem();
        return codec == null ? codecs.getFirst() : codec;
    }

    private int parsePortOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private void openConsoleWindow() {
        if (consoleFrame != null && consoleFrame.isDisplayable()) {
            consoleFrame.setVisible(true);
            consoleFrame.toFront();
            consoleFrame.requestFocus();
            return;
        }

        ConsolePanel consolePanel = new ConsolePanel();
        consolePanel.setEditable(false);

        JFrame frame = new JFrame("BedrockTunnel Console");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(consolePanel), BorderLayout.CENTER);
        frame.setSize(1100, 480);
        frame.setLocationRelativeTo(this);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (consoleUnsubscribe != null) {
                    consoleUnsubscribe.run();
                    consoleUnsubscribe = null;
                }
                consoleFrame = null;
            }
        });

        consoleUnsubscribe = ConsoleOutput.addListener(text -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (frame.isDisplayable()) {
                consolePanel.appendANSI(text);
                consolePanel.setCaretPosition(consolePanel.getDocument().getLength());
            }
        }));
        consoleFrame = frame;
        frame.setVisible(true);
    }

    private void closeConsoleWindow() {
        if (consoleFrame != null) {
            consoleFrame.dispose();
            consoleFrame = null;
        } else if (consoleUnsubscribe != null) {
            consoleUnsubscribe.run();
            consoleUnsubscribe = null;
        }
    }

    private TunnelStartConfig parseStartConfig() {
        String targetHost = targetHostField.getText().trim();
        if (targetHost.isEmpty()) {
            throw new IllegalArgumentException("Target host is required");
        }
        return new TunnelStartConfig(
                listenHostField.getText().trim(),
                Integer.parseInt(listenPortField.getText().trim()),
                targetHost,
                Integer.parseInt(targetPortField.getText().trim()),
                (SupportedCodec) codecBox.getSelectedItem()
        );
    }

    private CaptureEntry selectedEntry() {
        int row = captureTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return captureTableModel.entryAt(captureTable.convertRowIndexToModel(row));
    }

    private void showEntry(CaptureEntry entry) {
        if (entry == null) {
            summaryArea.setText("");
            jsonArea.setText("");
            hexArea.setText("");
            return;
        }
        summaryArea.setText(PacketFormatter.toSummary(entry));
        jsonArea.setText(entry.packet().jsonText());
        hexArea.setText(hexText(entry));
    }

    private String hexText(CaptureEntry entry) {
        return hexCache.computeIfAbsent(entry.packet().sequence(), ignored -> PacketFormatter.toHex(entry.packet().rawBytes()));
    }

    private void refreshActionButtons() {
        CaptureEntry selected = selectedEntry();
        startButton.setEnabled(!liveMode);
        stopButton.setEnabled(liveMode);
        replayButton.setEnabled(liveMode && selected != null && selected.state() != PacketState.PAUSED && selected.state() != PacketState.QUEUED);
        forwardButton.setEnabled(liveMode && paused && !pausedActionChosen);
        dropButton.setEnabled(liveMode && paused && !pausedActionChosen);
        resumeButton.setEnabled(liveMode && paused && pausedActionChosen);
    }

    private JPanel wrapWithTitle(String title, JScrollPane scrollPane) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setAlignmentX(LEFT_ALIGNMENT);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(4));
        panel.add(scrollPane);
        return panel;
    }

    private static JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return textArea;
    }

    private static void addField(JPanel panel, GridBagConstraints constraints, int gridX, String label, java.awt.Component component, double weightx) {
        constraints.gridx = gridX;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = gridX + 1;
        constraints.weightx = weightx;
        constraints.fill = weightx > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        panel.add(component, constraints);
    }

    private void addRule(RuleTableModel tableModel) {
        try {
            String packetType = selectedRulePacketType();
            tableModel.addRule(new PacketRule((DirectionMatch) ruleDirectionBox.getSelectedItem(), packetType));
            rulePacketTypeBox.getEditor().setItem(packetType);
            applyRules();
        } catch (RuntimeException exception) {
            showError("Unable to add rule. Select a valid packet type.", exception);
        }
    }

    private String selectedRulePacketType() {
        Object item = rulePacketTypeBox.getEditor().getItem();
        String query = item == null ? "" : item.toString().trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Packet type is required");
        }

        for (String packetType : packetTypes) {
            if (packetType.equalsIgnoreCase(query)) {
                return packetType;
            }
        }
        String lowered = query.toLowerCase(Locale.ROOT);
        for (String packetType : packetTypes) {
            if (packetType.toLowerCase(Locale.ROOT).contains(lowered)) {
                return packetType;
            }
        }
        throw new IllegalArgumentException("Unknown packet type: " + query);
    }

    private void updateRulePacketTypeChoices(JTextComponent textComponent) {
        if (updatingRulePacketTypeBox) {
            return;
        }

        String text = textComponent.getText();
        String query = text.trim().toLowerCase(Locale.ROOT);
        var model = new DefaultComboBoxModel<String>();
        for (String packetType : packetTypes) {
            if (query.isEmpty() || packetType.toLowerCase(Locale.ROOT).contains(query)) {
                model.addElement(packetType);
            }
        }

        updatingRulePacketTypeBox = true;
        try {
            rulePacketTypeBox.setModel(model);
            rulePacketTypeBox.setEditable(true);
            rulePacketTypeBox.getEditor().setItem(text);
            if (rulePacketTypeBox.getEditor().getEditorComponent() instanceof JTextComponent editorTextComponent) {
                editorTextComponent.setCaretPosition(text.length());
            }
            if (rulePacketTypeBox.isShowing() && textComponent.isFocusOwner() && model.getSize() > 0 && !query.isEmpty()) {
                rulePacketTypeBox.showPopup();
            } else {
                rulePacketTypeBox.hidePopup();
            }
        } finally {
            updatingRulePacketTypeBox = false;
        }
    }

    private void scheduleRulePacketTypeChoicesUpdate(JTextComponent textComponent) {
        if (updatingRulePacketTypeBox || rulePacketTypeUpdatePending) {
            return;
        }
        rulePacketTypeUpdatePending = true;
        javax.swing.SwingUtilities.invokeLater(() -> {
            rulePacketTypeUpdatePending = false;
            updateRulePacketTypeChoices(textComponent);
        });
    }

    private static String[] buildPacketChoices(List<String> packetTypes) {
        String[] values = new String[packetTypes.size() + 1];
        values[0] = "Any";
        for (int index = 0; index < packetTypes.size(); index++) {
            values[index + 1] = packetTypes.get(index);
        }
        return values;
    }
}
