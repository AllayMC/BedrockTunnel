package org.allaymc.bedrocktunnel.ui;

import org.allaymc.bedrocktunnel.capture.PacketStatistics;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class PacketStatsTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Packet Type", "Count", "Bytes", "Replays"};

    private final List<PacketStatistics.TypeStat> rows = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> String.class;
            case 1, 2, 3 -> Long.class;
            default -> Object.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        PacketStatistics.TypeStat row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.packetType();
            case 1 -> row.count();
            case 2 -> row.bytes();
            case 3 -> row.replays();
            default -> "";
        };
    }

    public void setRows(List<PacketStatistics.TypeStat> stats) {
        rows.clear();
        rows.addAll(stats);
        fireTableDataChanged();
    }
}
