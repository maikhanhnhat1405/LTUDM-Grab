package com.delivery.client.ui;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Panel hiển thị chi tiết MỘT đơn hàng.
 *
 * Dùng cho cả khách và tài xế - hiện thông tin pickup/dropoff/giá/tài xế,
 * các nút hành động phụ thuộc role + trạng thái, và khung chat dưới.
 */
public class OrderDetailPanel extends JPanel {

    private final JLabel orderIdLabel = Theme.bold("Đơn #—");
    private final JLabel statusLabel = Theme.muted("—");
    private final JLabel pickupLabel = Theme.label("");
    private final JLabel dropoffLabel = Theme.label("");
    private final JLabel priceLabel = Theme.bold("");
    private final JLabel driverLabel = Theme.muted("Chưa có tài xế");
    private final JLabel noteLabel = Theme.muted("");

    private final JPanel actionsPanel = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JComponent placeholder;

    public OrderDetailPanel(Runnable onOrderSelected) {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Placeholder khi chưa chọn đơn
        placeholder = buildPlaceholder();

        // Header: số đơn + trạng thái
        JPanel header = Theme.transparent(new BorderLayout(12, 0));
        header.add(orderIdLabel, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        // Main: chi tiết địa chỉ, giá, tài xế
        JPanel main = buildMainInfo();

        // Actions: các nút bấm (sẽ add động)

        add(header, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
        add(actionsPanel, BorderLayout.SOUTH);

        showPlaceholder();
    }

    private JComponent buildPlaceholder() {
        JLabel l = Theme.muted("Chọn một đơn trong danh sách để xem chi tiết");
        l.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel p = Theme.transparent(new BorderLayout());
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMainInfo() {
        JPanel grid = Theme.transparent(new GridLayout(4, 1, 0, 12));
        grid.setBorder(new EmptyBorder(8, 0, 12, 0));

        JPanel pickup = new JPanel(new BorderLayout(8, 2));
        pickup.setOpaque(false);
        pickup.add(new JLabel("📍"), BorderLayout.WEST);
        pickup.add(Theme.muted("Lấy:"), BorderLayout.NORTH);
        pickup.add(pickupLabel, BorderLayout.CENTER);

        JPanel dropoff = new JPanel(new BorderLayout(8, 2));
        dropoff.setOpaque(false);
        dropoff.add(new JLabel("📍"), BorderLayout.WEST);
        dropoff.add(Theme.muted("Giao:"), BorderLayout.NORTH);
        dropoff.add(dropoffLabel, BorderLayout.CENTER);

        JPanel price = Theme.transparent(new BorderLayout());
        price.add(Theme.muted("Giá:"), BorderLayout.NORTH);
        price.add(priceLabel, BorderLayout.CENTER);

        JPanel driver = Theme.transparent(new BorderLayout());
        driver.add(Theme.muted("Tài xế:"), BorderLayout.NORTH);
        driver.add(driverLabel, BorderLayout.CENTER);

        grid.add(pickup);
        grid.add(dropoff);
        grid.add(price);
        grid.add(driver);

        return grid;
    }

    private void showPlaceholder() {
        removeAll();
        add(placeholder, BorderLayout.CENTER);
        actionsPanel.removeAll();
        revalidate();
        repaint();
    }

    /**
     * Load chi tiết đơn hàng vào panel.
     *
     * @param order đơn hàng (JSON)
     * @param driverName tên tài xế nếu có, null nếu chưa có
     * @param actions danh sách các JButton cần hiện (nút Huỷ, Nhận, v.v)
     */
    public void showOrder(JsonObject order, String driverName, JButton... actions) {
        if (order == null) {
            showPlaceholder();
            return;
        }

        long id = order.get("id").getAsLong();
        String status = order.get("status").getAsString();

        orderIdLabel.setText("Đơn #" + id);
        orderIdLabel.setForeground(Theme.TEXT);

        statusLabel.setText(Theme.statusLabel(status));
        statusLabel.setForeground(Theme.statusColor(status));

        pickupLabel.setText(order.get("pickupAddr").getAsString());
        dropoffLabel.setText(order.get("dropoffAddr").getAsString());
        priceLabel.setText(Theme.money(order.get("price").getAsDouble()));
        priceLabel.setForeground(Theme.BRAND);

        if (driverName != null && order.has("driverId") && !order.get("driverId").isJsonNull()) {
            driverLabel.setText(driverName);
            driverLabel.setForeground(Theme.TEXT);
        } else {
            driverLabel.setText("Chưa có tài xế");
            driverLabel.setForeground(Theme.MUTED);
        }

        if (order.has("note") && !order.get("note").isJsonNull()) {
            String note = order.get("note").getAsString();
            noteLabel.setText(note.isEmpty() ? "(không có ghi chú)" : note);
        }

        removeAll();
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainInfo(), BorderLayout.CENTER);

        actionsPanel.removeAll();
        for (JButton btn : actions) {
            actionsPanel.add(btn);
        }
        add(actionsPanel, BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private JPanel buildHeader() {
        JPanel header = Theme.transparent(new BorderLayout(12, 0));
        header.add(orderIdLabel, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        return header;
    }
}
