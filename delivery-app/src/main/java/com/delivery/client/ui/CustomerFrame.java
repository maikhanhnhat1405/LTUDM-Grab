package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Man hinh khach hang.
 *
 * Bo cuc: thanh tieu de o tren, cot trai la nghiep vu (tao don + danh sach don
 * + nhat ky), cot phai la khung chat NHUNG SAN trong cua so - chon don nao thi
 * chat cua don do hien ngay ben canh, khong phai mo them cua so nao ca.
 */
public class CustomerFrame extends JFrame {

    private final ClientConnection conn;
    private final OrderTableModel model = new OrderTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea logArea = new JTextArea(4, 20);

    private final JTextField pickup = Theme.field(16);
    private final JTextField dropoff = Theme.field(16);
    private final JTextField note = Theme.field(16);
    private final JTextField price = Theme.field(8);

    private final JButton cancelBtn = Theme.danger("Huỷ đơn");
    private final ChatPanel chat;
    private final HeaderBar header;

    public CustomerFrame(ClientConnection conn) {
        super("Delivery App — Khách hàng · " + conn.fullName);
        this.conn = conn;
        this.chat = new ChatPanel(conn);
        this.header = new HeaderBar("Đặt đơn và theo dõi hành trình", conn.fullName, "Khách hàng", this::exitApp);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(header, BorderLayout.NORTH);

        JPanel body = Theme.transparent(new BorderLayout(14, 0));
        body.setBorder(new EmptyBorder(14, 16, 16, 16));
        body.add(buildLeft(), BorderLayout.CENTER);

        JPanel chatCard = Theme.card("Trò chuyện với tài xế", chat);
        chatCard.setPreferredSize(new Dimension(UiKit.CHAT_WIDTH, 0));
        body.add(chatCard, BorderLayout.EAST);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        price.setText("30000");
        conn.setPushListener(this::onPush);
        conn.setOnDisconnect(() -> {
            header.setConnected(false);
            log("Mất kết nối tới server");
        });

        // Dong man hinh chinh = thoat ung dung, nhung phai hoi truoc va dong
        // socket tu te roi moi thoat.
        UiKit.onClosing(this, this::exitApp);
        UiKit.lockSize(this, UiKit.MAIN);
        loadOrders();
    }

    // ------------------------------------------------------------------
    // Dung giao dien
    // ------------------------------------------------------------------

    private JComponent buildLeft() {
        JPanel col = Theme.transparent(new BorderLayout(0, 14));
        col.add(Theme.card("Tạo đơn mới", buildOrderForm()), BorderLayout.NORTH);
        col.add(Theme.card("Đơn của tôi", buildOrderTable()), BorderLayout.CENTER);
        col.add(Theme.card("Nhật ký hoạt động", buildLog()), BorderLayout.SOUTH);
        return col;
    }

    private JComponent buildOrderForm() {
        JPanel grid = Theme.transparent(new GridLayout(2, 2, 12, 10));
        grid.add(Theme.labeledField("ĐIỂM LẤY", pickup));
        grid.add(Theme.labeledField("ĐIỂM GIAO", dropoff));
        grid.add(Theme.labeledField("GHI CHÚ", note));
        grid.add(Theme.labeledField("GIÁ (VNĐ)", price));

        JButton createBtn = Theme.primary("Đặt đơn");
        createBtn.addActionListener(e -> createOrder());
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.add(createBtn);

        JPanel p = Theme.transparent(new BorderLayout(0, 12));
        p.add(grid, BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildOrderTable() {
        Theme.styleTable(table);
        table.getColumnModel().getColumn(OrderTableModel.COL_STATUS)
             .setCellRenderer(new Theme.StatusRenderer());
        setWidths(60, 110, 190, 190, 100, 120);

        // Chon don nao -> chat va cac nut deu bam theo don do.
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) syncSelection();
        });

        JButton refresh = Theme.ghost("Làm mới");
        refresh.addActionListener(e -> loadOrders());
        cancelBtn.addActionListener(e -> cancelOrder());
        cancelBtn.setEnabled(false);

        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(refresh);
        actions.add(cancelBtn);

        JPanel p = Theme.transparent(new BorderLayout(0, 10));
        p.add(Theme.scroll(table), BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private void setWidths(int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private JComponent buildLog() {
        logArea.setEditable(false);
        logArea.setFont(Theme.SMALL);
        logArea.setForeground(Theme.MUTED);
        logArea.setBackground(Theme.CARD);
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane sp = Theme.scroll(logArea);
        sp.setPreferredSize(new Dimension(0, 92));
        return sp;
    }

    // ------------------------------------------------------------------
    // Nghiep vu
    // ------------------------------------------------------------------

    /** Dong bo trang thai nut + khung chat theo don dang duoc chon. */
    private void syncSelection() {
        JsonObject o = model.at(table.getSelectedRow());
        chat.showOrder(o, model.driverNameOf(o));
        cancelBtn.setEnabled(o != null && canCancel(o.get("status").getAsString()));
    }

    private boolean canCancel(String status) {
        return "PENDING".equals(status) || "ACCEPTED".equals(status) || "PICKED_UP".equals(status);
    }

    private void createOrder() {
        String from = pickup.getText().trim();
        String to = dropoff.getText().trim();
        if (from.isEmpty() || to.isEmpty()) {
            log("Nhập đủ điểm lấy và điểm giao trước đã");
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
                    model.upsert(o);
                    select(o.get("id").getAsLong());
                    note.setText("");
                    log("Đã tạo đơn #" + o.get("id").getAsLong() + ", đang chờ tài xế nhận...");
                },
                err -> log("Tạo đơn thất bại: " + err.str("message")));
    }

    private void cancelOrder() {
        JsonObject o = model.at(table.getSelectedRow());
        if (o == null) return;
        long id = o.get("id").getAsLong();
        if (!UiKit.confirm(this, "Huỷ đơn #" + id + "?")) return;

        conn.request(Message.request(MessageType.ORDER_UPDATE_STATUS)
                        .put("orderId", id)
                        .put("status", "CANCELLED"),
                resp -> {
                    model.upsert(resp.getData().getAsJsonObject("order"));
                    syncSelection();
                    log("Đã huỷ đơn #" + id);
                },
                err -> log("Không huỷ được: " + err.str("message")));
    }

    private void loadOrders() {
        conn.request(Message.request(MessageType.ORDER_LIST_MINE),
                resp -> {
                    model.setAll(resp.getData().getAsJsonArray("orders"));
                    if (model.getRowCount() > 0 && table.getSelectedRow() < 0) {
                        table.setRowSelectionInterval(0, 0);
                    }
                },
                err -> log("Không tải được danh sách: " + err.str("message")));
    }

    private void select(long orderId) {
        int row = model.rowOf(orderId);
        if (row >= 0) table.setRowSelectionInterval(row, row);
    }

    /** Xu ly moi thu server tu day xuong (da chay san tren EDT). */
    private void onPush(Message push) {
        switch (push.getType()) {
            case MessageType.PUSH_ORDER_STATUS: {
                JsonObject o = push.getData().getAsJsonObject("order");
                long id = o.get("id").getAsLong();
                if (push.str("driverName") != null && o.has("driverId")) {
                    model.rememberDriverName(o.get("driverId").getAsLong(), push.str("driverName"));
                }
                model.upsert(o);
                if (chat.currentOrderId() == id) syncSelection();   // cap nhat lai phu de + nut
                String extra = push.str("driverName") != null ? " · tài xế " + push.str("driverName") : "";
                log("Đơn #" + id + " → " + Theme.statusLabel(o.get("status").getAsString()) + extra);
                break;
            }
            case MessageType.PUSH_CHAT_MESSAGE: {
                JsonObject m = push.getData().getAsJsonObject("message");
                if (!chat.handlePush(m)) {
                    log("Tin nhắn mới ở đơn #" + m.get("orderId").getAsLong() + ": " + m.get("content").getAsString());
                }
                break;
            }
            default:
                log("PUSH: " + push.getType());
        }
    }

    private void log(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void exitApp() {
        if (!UiKit.confirm(this, "Thoát ứng dụng?")) return;
        conn.close();
        dispose();
        System.exit(0);
    }
}
