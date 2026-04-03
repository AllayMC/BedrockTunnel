package org.allaymc.bedrocktunnel.ui;

import org.allaymc.bedrocktunnel.capture.CaptureEntry;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class CaptureTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Seq", "+ms", "Direction", "Packet", "Id", "Bytes", "State", "BP", "Replay"};

    private final List<CaptureEntry> entries = new ArrayList<>();

    @Override
    public int getRowCount() {
        return entries.size();
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
        CaptureEntry entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.packet().sequence();
            case 1 -> entry.packet().relativeTimeMillis();
            case 2 -> entry.packet().direction();
            case 3 -> entry.packet().packetType();
            case 4 -> entry.packet().packetId();
            case 5 -> entry.packet().byteLength();
            case 6 -> entry.state();
            case 7 -> entry.breakpointHit();
            case 8 -> entry.replayCount();
            default -> "";
        };
    }

    public void clear() {
        int rowCount = entries.size();
        entries.clear();
        if (rowCount > 0) {
            fireTableRowsDeleted(0, rowCount - 1);
        }
    }

    public void setEntries(List<CaptureEntry> newEntries) {
        clear();
        entries.addAll(newEntries);
        if (!entries.isEmpty()) {
            fireTableRowsInserted(0, entries.size() - 1);
        }
    }

    public void addEntry(CaptureEntry entry) {
        int row = entries.size();
        entries.add(entry);
        fireTableRowsInserted(row, row);
    }

    public void updateEntry(CaptureEntry entry) {
        int row = indexOf(entry);
        if (row >= 0) {
            fireTableRowsUpdated(row, row);
        }
    }

    public CaptureEntry entryAt(int row) {
        return entries.get(row);
    }

    public int indexOf(CaptureEntry entry) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).packet().sequence() == entry.packet().sequence()) {
                return index;
            }
        }
        return -1;
    }
}
