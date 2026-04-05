package org.allaymc.bedrocktunnel.ui;

import org.allaymc.bedrocktunnel.rules.PacketRule;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class RuleTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Type", "Direction", "Packet Type"};

    private final List<RuleRow> rules = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rules.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        RuleRow rule = rules.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> rule.type();
            case 1 -> rule.rule().direction();
            case 2 -> rule.rule().packetType();
            default -> "";
        };
    }

    public void addRule(RuleType type, PacketRule rule) {
        int row = rules.size();
        rules.add(new RuleRow(type, rule));
        fireTableRowsInserted(row, row);
    }

    public void setRules(List<RuleRow> rules) {
        this.rules.clear();
        this.rules.addAll(rules);
        fireTableDataChanged();
    }

    public void removeRow(int row) {
        if (row < 0 || row >= rules.size()) {
            return;
        }
        rules.remove(row);
        fireTableRowsDeleted(row, row);
    }

    public List<RuleRow> rows() {
        return List.copyOf(rules);
    }

    public List<PacketRule> rulesOfType(RuleType type) {
        return rules.stream()
                .filter(rule -> rule.type() == type)
                .map(RuleRow::rule)
                .toList();
    }

    enum RuleType {
        BLOCK("Block"),
        BREAKPOINT("Breakpoint"),
        HIDE("Hide");

        private final String displayName;

        RuleType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    record RuleRow(RuleType type, PacketRule rule) {
    }
}
