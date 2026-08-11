package com.delivery.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Model dung chung cho bang don hang o ca man khach lan man tai xe. */
public class OrderTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Ma", "Trang thai", "Diem lay", "Diem giao", "Gia", "Tai xe"};
    private final List<JsonObject> rows = new ArrayList<>();

    public void setAll(JsonArray arr) {
        rows.clear();
        for (JsonElement el : arr) rows.add(el.getAsJsonObject());
        fireTableDataChanged();
    }

    public void upsert(JsonObject order) {
        long id = order.get("id").getAsLong();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).get("id").getAsLong() == id) {
                rows.set(i, order);
                fireTableRowsUpdated(i, i);
                return;
            }
        }
        rows.add(0, order);
        fireTableRowsInserted(0, 0);
    }

    public void removeById(long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).get("id").getAsLong() == id) {
                rows.remove(i);
                fireTableRowsDeleted(i, i);
                return;
            }
        }
    }

    public JsonObject at(int row) {
        return (row < 0 || row >= rows.size()) ? null : rows.get(row);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int c) { return COLS[c]; }

    @Override
    public Object getValueAt(int r, int c) {
        JsonObject o = rows.get(r);
        switch (c) {
            case 0: return o.get("id").getAsLong();
            case 1: return o.get("status").getAsString();
            case 2: return str(o, "pickupAddr");
            case 3: return str(o, "dropoffAddr");
            case 4: return o.get("price").getAsDouble();
            case 5: return o.has("driverId") ? o.get("driverId").getAsLong() : "-";
            default: return "";
        }
    }

    private String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }
}
