package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Màn hình tài xế - card-based UI.
 *
 * Layout:
 * - Top: Header bar
 * - Center: 2 hàng
 *   - Hàng 1: Danh sách đơn chờ (trái) + Danh sách đơn của tôi (phải)
 *   - Hàng 2: Chi tiết đơn đã chọn + Nút hành động
 * - Bottom: Log
 */
public class DriverFrame extends JFrame {

    private final ClientConnection conn;
    private final OrderListPanel pendingList;
    private final OrderListPanel myList;
    private final OrderDetailPanel orderDetail;
    private final HeaderBar header;

    private final JTextArea logArea = new JTextArea(2, 20);
    private final JButton acceptBtn = Theme.primary("Nhận đơn");
    private final JButton pickedBtn = Theme.ghost("Đã lấy");
    private final JButton deliverBtn = Theme.ghost("Đang giao");
    private final JButton doneBtn = Theme.primary("Hoàn thành");
    private final JButton chatBtn = Theme.ghost("💬 Chat");

    public DriverFrame(ClientConnection conn) {
        super("Delivery App — Tài xế · " + conn.fullName);
        this.conn = conn;

        this.pendingList = new OrderListPanel(o -> {});
        this.myList = new OrderListPanel(this::onOrderSelected);
        this.orderDetail = new OrderDetailPanel(null);
        this.header = new HeaderBar("Nhận đơn và cập nhật hành trình", conn.fullName, "Tài xế", this::exitApp);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(header, BorderLayout.NORTH);

        JPanel body = Theme.transparent(new BorderLayout(0, 12));
        body.setBorder(new EmptyBorder(12, 14, 14, 14));
        body.add(buildListsSection(), BorderLayout.NORTH);
        body.add(buildDetailSection(), BorderLayout.CENTER);
        body.add(buildLogSection(), BorderLayout.SOUTH);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        conn.setPushListener(this::onPush);
        conn.setOnDisconnect(() -> {
            header.setConnected(false);
            log("Mất kết nối tới server, đang tự thử lại...");
        });
        conn.setOnConnectionStatus(status -> header.setStatus(false, status));
        conn.setOnReconnected(() -> {
            header.setConnected(true);
            log("Đã nối lại server, đang làm mới dữ liệu...");
            loadPending(); loadMine();
        });

        UiKit.onClosing(this, this::exitApp);
        UiKit.lockSize(this, UiKit.MAIN);
        loadPending();
        loadMine();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JComponent buildListsSection() {
        acceptBtn.addActionListener(e -> acceptOrder());
        acceptBtn.setEnabled(false);

        JPanel pendingActions = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 0, 6));
        pendingActions.add(acceptBtn);

        JPanel pending = new JPanel(new BorderLayout(0, 8));
        pending.setOpaque(false);
        pending.add(pendingList, BorderLayout.CENTER);
        pending.add(pendingActions, BorderLayout.SOUTH);

        pendingList.addListSelectionListener(e -> {
            acceptBtn.setEnabled(pendingList.getSelected() != null);
        });

        JPanel left = Theme.card("📋 Đơn đang chờ", pending);
        left.setPreferredSize(new Dimension(380, 0));

        myList.addListSelectionListener(e -> {
            JsonObject o = myList.getSelected();
            if (o != null) onOrderSelected(o);
            syncStatusButtons();
        });

        JPanel right = Theme.card("🚗 Đơn của tôi", myList);

        JPanel split = Theme.transparent(new BorderLayout(10, 0));
        split.add(left, BorderLayout.WEST);
        split.add(right, BorderLayout.CENTER);
        split.setPreferredSize(new Dimension(0, 180));
        return split;
    }

    private JComponent buildDetailSection() {
        JPanel left = Theme.card("Chi tiết đơn", orderDetail);

        pickedBtn.addActionListener(e -> updateStatus("PICKED_UP"));
        deliverBtn.addActionListener(e -> updateStatus("DELIVERING"));
        doneBtn.addActionListener(e -> updateStatus("COMPLETED"));
        chatBtn.addActionListener(e -> openChat());
        syncStatusButtons();

        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actions.add(chatBtn);
        actions.add(pickedBtn);
        actions.add(deliverBtn);
        actions.add(doneBtn);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        right.add(actions, BorderLayout.CENTER);

        JPanel split = Theme.transparent(new BorderLayout(10, 0));
        split.add(left, BorderLayout.CENTER);
        split.add(right, BorderLayout.EAST);
        return split;
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

    private void syncStatusButtons() {
        JsonObject o = myList.getSelected();
        String status = o == null ? null : o.get("status").getAsString();
        pickedBtn.setEnabled("ACCEPTED".equals(status));
        deliverBtn.setEnabled("PICKED_UP".equals(status));
        doneBtn.setEnabled("DELIVERING".equals(status));
    }

    private void onOrderSelected(JsonObject order) {
        orderDetail.showOrder(order, "Khách #" + order.get("customerId").getAsLong(), doneBtn, deliverBtn, pickedBtn);
    }

    private void openChat() {
        JsonObject o = myList.getSelected();
        if (o == null) {
            log("Chọn một đơn trước đã");
            return;
        }
        ChatDialog.open(this, conn, o.get("id").getAsLong());
    }

    private void acceptOrder() {
        JsonObject o = pendingList.getSelected();
        if (o == null) return;
        long id = o.get("id").getAsLong();

        conn.request(Message.request(MessageType.ORDER_ACCEPT).put("orderId", id),
                resp -> {
                    JsonObject order = resp.getData().getAsJsonObject("order");
                    pendingList.removeById(id);
                    myList.upsert(order);
                    onOrderSelected(order);
                    log("✓ Đã nhận đơn #" + id);
                },
                err -> {
                    pendingList.removeById(id);
                    log("✗ Không nhận được đơn #" + id + ": " + err.str("message"));
                });
    }

    private void updateStatus(String status) {
        JsonObject o = myList.getSelected();
        if (o == null) return;
        long id = o.get("id").getAsLong();

        conn.request(Message.request(MessageType.ORDER_UPDATE_STATUS)
                        .put("orderId", id)
                        .put("status", status),
                resp -> {
                    myList.upsert(resp.getData().getAsJsonObject("order"));
                    onOrderSelected(myList.getSelected());
                    log("→ Đơn #" + id + " " + Theme.statusLabel(status));
                },
                err -> log("✗ Không đổi được trạng thái: " + err.str("message")));
    }

    private void loadPending() {
        conn.request(Message.request(MessageType.ORDER_LIST_PENDING),
                resp -> pendingList.setAll(resp.getData().getAsJsonArray("orders")),
                err -> log("✗ Lỗi tải đơn chờ: " + err.str("message")));
    }

    private void loadMine() {
        conn.request(Message.request(MessageType.ORDER_LIST_MINE),
                resp -> myList.setAll(resp.getData().getAsJsonArray("orders")),
                err -> log("✗ Lỗi tải đơn của tôi: " + err.str("message")));
    }

    private void onPush(Message push) {
        switch (push.getType()) {
            case MessageType.PUSH_NEW_ORDER: {
                JsonObject o = push.getData().getAsJsonObject("order");
                pendingList.upsert(o);
                log("🔔 CÓ ĐƠN MỚI: " + o.get("pickupAddr").getAsString() + " → " + o.get("dropoffAddr").getAsString());
                break;
            }
            case MessageType.PUSH_ORDER_TAKEN: {
                long id = push.lng("orderId");
                pendingList.removeById(id);
                log("⚠ Đơn #" + id + " đã có người nhận");
                break;
            }
            case MessageType.PUSH_ORDER_STATUS: {
                JsonObject o = push.getData().getAsJsonObject("order");
                myList.upsert(o);
                if (myList.getSelected() != null && myList.getSelected().get("id").getAsLong() == o.get("id").getAsLong()) {
                    onOrderSelected(o);
                }
                log("→ Đơn #" + o.get("id").getAsLong() + " " + Theme.statusLabel(o.get("status").getAsString()));
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
