package org.allaymc.bedrocktunnel.ui;

import org.allaymc.bedrocktunnel.rules.PacketRule;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class RuleTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Direction", "Packet Type"};

    private final List<PacketRule> rules = new ArrayList<>();

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
        PacketRule rule = rules.get(rowIndex);
        return columnIndex == 0 ? rule.direction() : rule.packetType();
    }

    public void addRule(PacketRule rule) {
        int row = rules.size();
        rules.add(rule);
        fireTableRowsInserted(row, row);
    }

    public void setRules(List<PacketRule> rules) {
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

    public List<PacketRule> rules() {
        return List.copyOf(rules);
    }
}
