package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Màn hình khách hàng - card-based UI.
 *
 * Layout:
 * - Top: Header bar
 * - Center: Hàng 1 (Form tạo đơn)
 *          Hàng 2 (Danh sách đơn trái + Chi tiết đơn phải)
 * - Bottom: Log
 */
public class CustomerFrame extends JFrame {

    private final ClientConnection conn;
    private final OrderListPanel orderList;
    private final OrderDetailPanel orderDetail;
    private final HeaderBar header;

    private final JTextField pickup = Theme.field(20);
    private final JTextField dropoff = Theme.field(20);
    private final JTextField note = Theme.field(20);
    private final JTextField price = Theme.field(12);
    private final JTextArea logArea = new JTextArea(2, 20);

    private final JButton cancelBtn = Theme.danger("Huỷ đơn");
    private final JButton chatBtn = Theme.ghost("💬 Chat");

    public CustomerFrame(ClientConnection conn) {
        super("Delivery App — Khách hàng · " + conn.fullName);
        this.conn = conn;

        this.orderList = new OrderListPanel(this::onOrderSelected);
        this.orderDetail = new OrderDetailPanel(null);
        this.header = new HeaderBar("Đặt đơn và theo dõi hành trình", conn.fullName, "Khách hàng", this::exitApp);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(header, BorderLayout.NORTH);

        JPanel body = Theme.transparent(new BorderLayout(0, 12));
        body.setBorder(new EmptyBorder(12, 14, 14, 14));
        body.add(buildFormSection(), BorderLayout.NORTH);
        body.add(buildOrdersSection(), BorderLayout.CENTER);
        body.add(buildLogSection(), BorderLayout.SOUTH);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        price.setText("30000");
        conn.setPushListener(this::onPush);
        conn.setOnDisconnect(() -> {
            header.setConnected(false);
            log("Mất kết nối tới server, đang tự thử lại...");
        });
        conn.setOnConnectionStatus(status -> header.setStatus(false, status));
        conn.setOnReconnected(() -> {
            header.setConnected(true);
            log("Đã nối lại server, đang làm mới dữ liệu...");
            loadOrders();
        });

        UiKit.onClosing(this, this::exitApp);
        UiKit.lockSize(this, UiKit.MAIN);
        loadOrders();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JComponent buildFormSection() {
        JPanel fields = Theme.transparent(new GridLayout(1, 4, 10, 0));
        fields.add(Theme.labeledField("ĐIỂM LẤY", pickup));
        fields.add(Theme.labeledField("ĐIỂM GIAO", dropoff));
        fields.add(Theme.labeledField("GIÁ (VNĐ)", price));

        JPanel notePanel = Theme.transparent(new BorderLayout());
        notePanel.add(Theme.labeledField("GHI CHÚ", note), BorderLayout.CENTER);

        JButton createBtn = Theme.primary("Đặt đơn ngay");
        createBtn.addActionListener(e -> createOrder());

        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(createBtn);

        JPanel form = Theme.transparent(new BorderLayout(10, 0));
        form.add(fields, BorderLayout.CENTER);
        form.add(notePanel, BorderLayout.EAST);
        form.add(actions, BorderLayout.SOUTH);

        return Theme.card("Đặt đơn mới", form);
    }

    private JComponent buildOrdersSection() {
        JPanel left = Theme.card("Danh sách đơn", orderList);
        left.setPreferredSize(new Dimension(380, 0));

        // Tab: Chi tiết + Bản đồ
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.setFont(Theme.FONT_BOLD);

        MapPanel map = new MapPanel();
        orderList.addListSelectionListener(e -> {
            JsonObject o = orderList.getSelected();
            if (o != null) {
                map.clearAll();
                if (o.has("pickupLat")) {
                    map.setPickup(o.get("pickupLat").getAsDouble(), o.get("pickupLng").getAsDouble());
                }
                if (o.has("dropoffLat")) {
                    map.setDropoff(o.get("dropoffLat").getAsDouble(), o.get("dropoffLng").getAsDouble());
                }
            }
        });

        rightTabs.addTab("📋 Chi tiết", orderDetail);
        rightTabs.addTab("🗺️ Bản đồ", map);

        JPanel rightBottom = buildActionsPanel();
        rightBottom.setPreferredSize(new Dimension(0, 60));

        JPanel right = Theme.transparent(new BorderLayout(0, 8));
        right.add(rightTabs, BorderLayout.CENTER);
        right.add(rightBottom, BorderLayout.SOUTH);

        JPanel split = Theme.transparent(new BorderLayout(10, 0));
        split.add(left, BorderLayout.WEST);
        split.add(right, BorderLayout.CENTER);
        return split;
    }

    private JPanel buildActionsPanel() {
        cancelBtn.setEnabled(false);
        chatBtn.addActionListener(e -> openChat());

        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.add(chatBtn);
        actions.add(cancelBtn);

        return actions;
    }

    private JComponent buildLogSection() {
        logArea.setEditable(false);
        logArea.setFont(Theme.SMALL);
        logArea.setForeground(Theme.MUTED);
        logArea.setBackground(Theme.CARD);
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane logScroll = Theme.scroll(logArea);
        logScroll.setPreferredSize(new Dimension(0, 50));
        return Theme.card("Nhật ký", logScroll);
    }

    // ------------------------------------------------------------------
    // Nghiệp vụ
    // ------------------------------------------------------------------

    private void createOrder() {
        String from = pickup.getText().trim();
        String to = dropoff.getText().trim();
        if (from.isEmpty() || to.isEmpty()) {
            log("Nhập đủ điểm lấy và điểm giao");
            return;
        }
        double p;
        try {
            p = Double.parseDouble(price.getText().trim());
        } catch (NumberFormatException e) {
            log("Giá không hợp lệ");
            return;
        }

        conn.request(Message.request(MessageType.ORDER_CREATE)
                        .put("pickupAddr", from)
                        .put("dropoffAddr", to)
                        .put("note", note.getText().trim())
                        .put("price", p),
                resp -> {
                    JsonObject o = resp.getData().getAsJsonObject("order");
                    orderList.upsert(o);
                    onOrderSelected(o);
                    pickup.setText("");
                    dropoff.setText("");
                    note.setText("");
                    price.setText("30000");
                    log("✓ Đã tạo đơn #" + o.get("id").getAsLong());
                },
                err -> log("✗ Tạo đơn thất bại: " + err.str("message")));
    }

    private void onOrderSelected(JsonObject order) {
        String driver = null;
        if (order.has("driverId") && !order.get("driverId").isJsonNull()) {
            driver = "Tài xế #" + order.get("driverId").getAsLong();
        }
        cancelBtn.setEnabled(canCancel(order.get("status").getAsString()));
        orderDetail.showOrder(order, driver, cancelBtn);
    }

    private boolean canCancel(String status) {
        return "PENDING".equals(status) || "ACCEPTED".equals(status) || "PICKED_UP".equals(status);
    }

    private void openChat() {
        JsonObject o = orderList.getSelected();
        if (o == null) {
            log("Chọn một đơn trước đã");
            return;
        }
        if (!o.has("driverId") || o.get("driverId").isJsonNull()) {
            log("Đơn chưa có tài xế để chat");
            return;
        }
        ChatDialog.open(this, conn, o.get("id").getAsLong());
    }

    private void loadOrders() {
        conn.request(Message.request(MessageType.ORDER_LIST_MINE),
                resp -> {
                    orderList.setAll(resp.getData().getAsJsonArray("orders"));
                },
                err -> log("✗ Không tải được danh sách: " + err.str("message")));
    }

    private void onPush(Message push) {
        switch (push.getType()) {
            case MessageType.PUSH_ORDER_STATUS: {
                JsonObject o = push.getData().getAsJsonObject("order");
                long id = o.get("id").getAsLong();
                orderList.upsert(o);
                JsonObject sel = orderList.getSelected();
                if (sel != null && sel.get("id").getAsLong() == id) {
                    onOrderSelected(o);
                }
                String extra = push.str("driverName") != null ? " · " + push.str("driverName") : "";
                log("→ Đơn #" + id + " " + Theme.statusLabel(o.get("status").getAsString()) + extra);
                break;
            }
            case MessageType.PUSH_CHAT_MESSAGE: {
                JsonObject m = push.getData().getAsJsonObject("message");
                if (!ChatDialog.route(m)) {
                    log("💬 Tin mới (đơn #" + m.get("orderId").getAsLong() + ")");
                }
                break;
            }
            default:
                log("? " + push.getType());
        }
    }

    private void log(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void exitApp() {
        if (!UiKit.confirm(this, "Thoát ứng dụng?")) return;
        ChatDialog.closeAll();
        conn.close();
        dispose();
        System.exit(0);
    }
}
