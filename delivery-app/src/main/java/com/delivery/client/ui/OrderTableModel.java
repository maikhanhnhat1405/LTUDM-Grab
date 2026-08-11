package com.delivery.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Model dung chung cho bang don hang o ca man khach lan man tai xe. */
public class OrderTableModel extends AbstractTableModel {

    public static final int COL_ID = 0, COL_STATUS = 1, COL_PICKUP = 2,
                            COL_DROPOFF = 3, COL_PRICE = 4, COL_DRIVER = 5;

    private static final String[] COLS = {"Mã", "Trạng thái", "Điểm lấy", "Điểm giao", "Giá", "Tài xế"};

    private final List<JsonObject> rows = new ArrayList<>();
    /** driverId -> ten, gom nhat tu cac PUSH co kem driverName. */
    private final Map<Long, String> driverNames = new HashMap<>();

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

    /** Vi tri dong cua don, -1 neu khong co trong bang. */
    public int rowOf(long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).get("id").getAsLong() == id) return i;
        }
        return -1;
    }

    public JsonObject byId(long id) {
        int r = rowOf(id);
        return r < 0 ? null : rows.get(r);
    }

    public void rememberDriverName(long driverId, String name) {
        if (name != null && !name.isBlank()) driverNames.put(driverId, name);
    }

    /** Ten tai xe cua don, hoac null neu don chua co tai xe. */
    public String driverNameOf(JsonObject order) {
        if (order == null || !order.has("driverId") || order.get("driverId").isJsonNull()) return null;
        long id = order.get("driverId").getAsLong();
        return driverNames.getOrDefault(id, "Tài xế #" + id);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int c) { return COLS[c]; }

    @Override
    public Object getValueAt(int r, int c) {
        JsonObject o = rows.get(r);
        switch (c) {
            case COL_ID:      return "#" + o.get("id").getAsLong();
            case COL_STATUS:  return o.get("status").getAsString();   // StatusRenderer tu doi sang tieng Viet
            case COL_PICKUP:  return str(o, "pickupAddr");
            case COL_DROPOFF: return str(o, "dropoffAddr");
            case COL_PRICE:   return Theme.money(o.get("price").getAsDouble());
            case COL_DRIVER:  {
                String n = driverNameOf(o);
                return n == null ? "—" : n;
            }
            default: return "";
        }
    }

    private String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }
}
