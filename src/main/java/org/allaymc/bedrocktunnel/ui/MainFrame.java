package org.allaymc.bedrocktunnel.ui;

import io.netty.buffer.ByteBufUtil;
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
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

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
import javax.swing.SwingWorker;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.imageio.ImageIO;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MainFrame extends JFrame {
    private static final String ANY_PACKET = "Any";

    private final TunnelController controller;
    private final UserSettingsStore settingsStore;
    private final List<SupportedCodec> codecs;
    private final List<String> packetTypes;
    private final CaptureTableModel captureTableModel = new CaptureTableModel();
    private final RuleTableModel ruleTableModel = new RuleTableModel();
    private final PacketStatsTableModel packetStatsTableModel = new PacketStatsTableModel();
    private final DefaultListModel<HistoryCapture> historyListModel = new DefaultListModel<>();
    private final TableRowSorter<CaptureTableModel> captureSorter = new TableRowSorter<>(captureTableModel);
    private final Map<Long, String> hexCache = new HashMap<>();
    private final Map<Long, String> keywordHexCache = new ConcurrentHashMap<>();
    private final Timer keywordFilterTimer = new Timer(180, event -> startKeywordSearch());

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
    private final RSyntaxTextArea jsonArea = createJsonArea();
    private final JTextArea hexArea = createTextArea();

    private final JComboBox<DirectionMatch> ruleDirectionBox = new JComboBox<>(DirectionMatch.values());
    private final JComboBox<RuleTableModel.RuleType> ruleTypeBox = new JComboBox<>(RuleTableModel.RuleType.values());
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
    private boolean updatingFilterPacketTypeBox;
    private boolean filterPacketTypeUpdatePending;
    private boolean updatingRulePacketTypeBox;
    private boolean rulePacketTypeUpdatePending;
    private String activeKeyword = "";
    private Set<Long> keywordMatchSequences = Set.of();
    private SwingWorker<KeywordSearchResult, Void> keywordSearchWorker;
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
        this.keywordFilterTimer.setRepeats(false);
        stabilizePacketTypeBoxWidth(filterPacketTypeBox);
        stabilizePacketTypeBoxWidth(rulePacketTypeBox);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImage(loadWindowIcon());
        setPreferredSize(new Dimension(1500, 920));
        setLayout(new BorderLayout(8, 8));
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildMainSplitPane(), BorderLayout.CENTER);

        configureCaptureTable();
        configureFilters();
        configureButtons();
        configureStats();
        configureFilterPacketTypeBox();
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
        jsonArea.setCaretPosition(0);
        hexArea.setText("");
        hexCache.clear();
        keywordHexCache.clear();
        resetKeywordSearchState();
        refreshKeywordFilterForCurrentData();
    }

    public void addEntry(CaptureEntry entry) {
        updateKeywordMatch(entry);
        captureTableModel.addEntry(entry);
        refreshActionButtons();
    }

    public void updateEntry(CaptureEntry entry) {
        updateKeywordMatch(entry);
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
        keywordHexCache.clear();
        summaryArea.setText("");
        jsonArea.setText("");
        jsonArea.setCaretPosition(0);
        hexArea.setText("");
        resetKeywordSearchState();
        updateStatistics(statistics);
        setStatusText("Viewing history: " + summary.targetAddress() + " / " + summary.minecraftVersion());
        refreshKeywordFilterForCurrentData();
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

    private JSplitPane buildMainSplitPane() {
        JTabbedPane bottomTabs = buildBottomTabs();
        bottomTabs.setMinimumSize(new Dimension(0, 140));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildCenterPanel(), bottomTabs);
        splitPane.setResizeWeight(0.72);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
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
        top.add(new JLabel("Type"));
        top.add(ruleTypeBox);
        top.add(new JLabel("Direction"));
        top.add(ruleDirectionBox);
        top.add(new JLabel("Packet"));
        top.add(rulePacketTypeBox);
        JButton addRuleButton = new JButton("Add Rule");
        top.add(addRuleButton);
        panel.add(top, BorderLayout.NORTH);

        JTable ruleTable = new JTable(ruleTableModel);
        ruleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(ruleTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton removeRuleButton = new JButton("Remove Selected");
        bottom.add(removeRuleButton);
        panel.add(bottom, BorderLayout.SOUTH);

        addRuleButton.addActionListener(event -> addRule());
        removeRuleButton.addActionListener(event -> {
            int row = ruleTable.getSelectedRow();
            if (row >= 0) {
                ruleTableModel.removeRow(row);
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
        JTable statsTable = new JTable(packetStatsTableModel);
        statsTable.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(statsTable), BorderLayout.CENTER);
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
                handleKeywordFilterChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                handleKeywordFilterChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                handleKeywordFilterChanged();
            }
        });
    }

    private void configureFilterPacketTypeBox() {
        filterPacketTypeBox.setEditable(true);
        var editorComponent = filterPacketTypeBox.getEditor().getEditorComponent();
        if (!(editorComponent instanceof JTextComponent textComponent)) {
            return;
        }

        textComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleFilterPacketTypeChoicesUpdate(textComponent);
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleFilterPacketTypeChoicesUpdate(textComponent);
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleFilterPacketTypeChoicesUpdate(textComponent);
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
                keywordFilterTimer.stop();
                cancelKeywordSearch();
                persistSettings();
                closeConsoleWindow();
            }
        });
    }

    private void applyFilters() {
        List<PacketRule> hideRules = ruleTableModel.rulesOfType(RuleTableModel.RuleType.HIDE);
        String packetTypeQuery = selectedFilterPacketType();
        String keyword = currentKeyword();
        boolean keywordReady = !keyword.isBlank() && keyword.equals(activeKeyword);
        captureSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends CaptureTableModel, ? extends Integer> row) {
                CaptureEntry entry = captureTableModel.entryAt(row.getIdentifier());
                if (hideRules.stream().anyMatch(rule -> rule.matches(entry.packet()))) {
                    return false;
                }

                Object direction = filterDirectionBox.getSelectedItem();
                if (direction instanceof DirectionMatch match && !match.matches(entry.packet().direction())) {
                    return false;
                }

                Object state = filterStateBox.getSelectedItem();
                if (state instanceof PacketState packetState && entry.state() != packetState) {
                    return false;
                }

                if (!packetTypeQuery.isBlank()
                        && !ANY_PACKET.equalsIgnoreCase(packetTypeQuery)
                        && !containsIgnoreCase(entry.packet().packetType(), packetTypeQuery)) {
                    return false;
                }

                if (keyword.isBlank()) {
                    return true;
                }
                if (!keywordReady) {
                    return true;
                }
                return keywordMatchSequences.contains(entry.packet().sequence());
            }
        });
    }

    private void applyRules() {
        controller.updateRuleSet(currentRuleSet());
        applyFilters();
        if (!applyingSettings) {
            persistSettings();
        }
    }

    private RuleSet currentRuleSet() {
        return new RuleSet(
                (PacketControlMode) ruleModeBox.getSelectedItem(),
                ruleTableModel.rulesOfType(RuleTableModel.RuleType.BLOCK),
                ruleTableModel.rulesOfType(RuleTableModel.RuleType.BREAKPOINT)
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
            ruleTableModel.setRules(combineRuleRows(settings));
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
                ruleTableModel.rulesOfType(RuleTableModel.RuleType.BLOCK),
                ruleTableModel.rulesOfType(RuleTableModel.RuleType.BREAKPOINT),
                ruleTableModel.rulesOfType(RuleTableModel.RuleType.HIDE)
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
        frame.setIconImage(getIconImage());
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
            jsonArea.setCaretPosition(0);
            hexArea.setText("");
            return;
        }
        summaryArea.setText(PacketFormatter.toSummary(entry));
        jsonArea.setText(entry.packet().jsonText());
        jsonArea.setCaretPosition(0);
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

    private static RSyntaxTextArea createJsonArea() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setEditable(false);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setHighlightCurrentLine(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        applyJsonTheme(textArea);
        return textArea;
    }

    private static void applyJsonTheme(RSyntaxTextArea textArea) {
        try (InputStream inputStream = MainFrame.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {
            if (inputStream == null) {
                return;
            }
            Theme.load(inputStream).apply(textArea);
        } catch (IOException ignored) {
        }
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

    private void stabilizePacketTypeBoxWidth(JComboBox<String> comboBox) {
        comboBox.setPrototypeDisplayValue(longestPacketType());
        Dimension size = comboBox.getPreferredSize();
        comboBox.setPreferredSize(size);
        comboBox.setMinimumSize(size);
    }

    private String longestPacketType() {
        String longest = ANY_PACKET;
        for (String packetType : packetTypes) {
            if (packetType.length() > longest.length()) {
                longest = packetType;
            }
        }
        return longest;
    }

    private void addRule() {
        try {
            String packetType = selectedRulePacketType();
            ruleTableModel.addRule(
                    (RuleTableModel.RuleType) ruleTypeBox.getSelectedItem(),
                    new PacketRule((DirectionMatch) ruleDirectionBox.getSelectedItem(), packetType)
            );
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

    private String selectedFilterPacketType() {
        Object item = filterPacketTypeBox.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    private void handleKeywordFilterChanged() {
        if (currentKeyword().isBlank()) {
            keywordFilterTimer.stop();
            cancelKeywordSearch();
            activeKeyword = "";
            keywordMatchSequences = Set.of();
            applyFilters();
            return;
        }
        keywordFilterTimer.restart();
        applyFilters();
    }

    private void refreshKeywordFilterForCurrentData() {
        if (currentKeyword().isBlank()) {
            applyFilters();
            return;
        }
        startKeywordSearch();
    }

    private void resetKeywordSearchState() {
        keywordFilterTimer.stop();
        cancelKeywordSearch();
        activeKeyword = "";
        keywordMatchSequences = Set.of();
    }

    private void startKeywordSearch() {
        String keyword = currentKeyword();
        if (keyword.isBlank()) {
            activeKeyword = "";
            keywordMatchSequences = Set.of();
            applyFilters();
            return;
        }

        cancelKeywordSearch();
        List<CaptureEntry> entries = captureTableModel.entriesSnapshot();
        String normalizedHexKeyword = normalizeHexQuery(keyword);

        keywordSearchWorker = new SwingWorker<>() {
            @Override
            protected KeywordSearchResult doInBackground() {
                Set<Long> matches = new HashSet<>();
                for (CaptureEntry entry : entries) {
                    if (isCancelled()) {
                        return null;
                    }
                    if (matchesKeyword(entry, keyword, normalizedHexKeyword)) {
                        matches.add(entry.packet().sequence());
                    }
                }
                return new KeywordSearchResult(keyword, matches);
            }

            @Override
            protected void done() {
                if (keywordSearchWorker != this) {
                    return;
                }
                keywordSearchWorker = null;
                if (isCancelled()) {
                    return;
                }
                try {
                    KeywordSearchResult result = get();
                    if (result == null || !result.keyword().equals(currentKeyword())) {
                        return;
                    }
                    activeKeyword = result.keyword();
                    keywordMatchSequences = result.matches();
                    applyFilters();
                } catch (Exception ignored) {
                }
            }
        };
        keywordSearchWorker.execute();
    }

    private void cancelKeywordSearch() {
        if (keywordSearchWorker != null) {
            keywordSearchWorker.cancel(true);
            keywordSearchWorker = null;
        }
    }

    private void updateKeywordMatch(CaptureEntry entry) {
        String keyword = currentKeyword();
        if (keyword.isBlank() || !keyword.equals(activeKeyword)) {
            return;
        }
        if (matchesKeyword(entry, keyword, normalizeHexQuery(keyword))) {
            keywordMatchSequences.add(entry.packet().sequence());
        } else {
            keywordMatchSequences.remove(entry.packet().sequence());
        }
    }

    private boolean matchesKeyword(CaptureEntry entry, String keyword, String normalizedHexKeyword) {
        return containsIgnoreCase(entry.packet().packetType(), keyword)
                || containsIgnoreCase(entry.packet().description(), keyword)
                || containsIgnoreCase(entry.packet().jsonText(), keyword)
                || matchesHexKeyword(entry, normalizedHexKeyword);
    }

    private boolean matchesHexKeyword(CaptureEntry entry, String normalizedHexKeyword) {
        if (normalizedHexKeyword == null) {
            return false;
        }
        return keywordHexCache.computeIfAbsent(entry.packet().sequence(), ignored -> ByteBufUtil.hexDump(entry.packet().rawBytes()))
                .contains(normalizedHexKeyword);
    }

    private String currentKeyword() {
        return filterKeywordField.getText().trim();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        int needleLength = needle.length();
        int maxIndex = haystack.length() - needleLength;
        for (int index = 0; index <= maxIndex; index++) {
            if (haystack.regionMatches(true, index, needle, 0, needleLength)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeHexQuery(String keyword) {
        StringBuilder builder = new StringBuilder(keyword.length());
        for (int index = 0; index < keyword.length(); index++) {
            char character = keyword.charAt(index);
            int digit = Character.digit(character, 16);
            if (digit >= 0) {
                builder.append(Character.forDigit(digit, 16));
                continue;
            }
            if (Character.isWhitespace(character) || character == '-' || character == ':') {
                continue;
            }
            return null;
        }
        return builder.length() >= 2 ? builder.toString() : null;
    }

    private void updateFilterPacketTypeChoices(JTextComponent textComponent) {
        if (updatingFilterPacketTypeBox) {
            return;
        }

        String text = textComponent.getText();
        String query = text.trim().toLowerCase(Locale.ROOT);
        var model = new DefaultComboBoxModel<String>();
        model.addElement(ANY_PACKET);
        for (String packetType : packetTypes) {
            if (query.isEmpty() || packetType.toLowerCase(Locale.ROOT).contains(query)) {
                model.addElement(packetType);
            }
        }

        updatingFilterPacketTypeBox = true;
        try {
            filterPacketTypeBox.setModel(model);
            filterPacketTypeBox.setEditable(true);
            filterPacketTypeBox.getEditor().setItem(text);
            if (filterPacketTypeBox.getEditor().getEditorComponent() instanceof JTextComponent editorTextComponent) {
                editorTextComponent.setCaretPosition(text.length());
            }
            if (filterPacketTypeBox.isShowing() && textComponent.isFocusOwner() && model.getSize() > 1 && !query.isEmpty()) {
                filterPacketTypeBox.showPopup();
            } else {
                filterPacketTypeBox.hidePopup();
            }
        } finally {
            updatingFilterPacketTypeBox = false;
        }
    }

    private void scheduleFilterPacketTypeChoicesUpdate(JTextComponent textComponent) {
        if (updatingFilterPacketTypeBox || filterPacketTypeUpdatePending) {
            return;
        }
        filterPacketTypeUpdatePending = true;
        javax.swing.SwingUtilities.invokeLater(() -> {
            filterPacketTypeUpdatePending = false;
            updateFilterPacketTypeChoices(textComponent);
        });
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
        values[0] = ANY_PACKET;
        for (int index = 0; index < packetTypes.size(); index++) {
            values[index + 1] = packetTypes.get(index);
        }
        return values;
    }

    private static List<RuleTableModel.RuleRow> combineRuleRows(UserSettingsStore.Settings settings) {
        List<RuleTableModel.RuleRow> rows = new java.util.ArrayList<>();
        settings.blockRules().forEach(rule -> rows.add(new RuleTableModel.RuleRow(RuleTableModel.RuleType.BLOCK, rule)));
        settings.breakpointRules().forEach(rule -> rows.add(new RuleTableModel.RuleRow(RuleTableModel.RuleType.BREAKPOINT, rule)));
        settings.hideRules().forEach(rule -> rows.add(new RuleTableModel.RuleRow(RuleTableModel.RuleType.HIDE, rule)));
        return rows;
    }

    private record KeywordSearchResult(String keyword, Set<Long> matches) {
    }

    private static Image loadWindowIcon() {
        try {
            return ImageIO.read(Objects.requireNonNull(
                    MainFrame.class.getResource("/app-icon.png"),
                    "Missing app icon resource"
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load app icon", exception);
        }
    }
}
