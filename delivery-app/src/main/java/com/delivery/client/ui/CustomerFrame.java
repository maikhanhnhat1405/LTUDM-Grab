package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

public class CustomerFrame extends JFrame {

    private final ClientConnection conn;
    private final OrderTableModel model = new OrderTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea logArea = new JTextArea(6, 60);

    private final JTextField pickup = new JTextField(18);
    private final JTextField dropoff = new JTextField(18);
    private final JTextField note = new JTextField(18);
    private final JTextField price = new JTextField("30000", 8);

    public CustomerFrame(ClientConnection conn) {
        super("Khach hang - " + conn.fullName);
        this.conn = conn;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // ----- form tao don -----
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Tao don moi"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 5, 3, 5);
        g.anchor = GridBagConstraints.WEST;
        int r = 0;
        addRow(form, g, r++, "Diem lay:", pickup);
        addRow(form, g, r++, "Diem giao:", dropoff);
        addRow(form, g, r++, "Ghi chu:", note);
        addRow(form, g, r++, "Gia (VND):", price);

        JButton createBtn = new JButton("Dat don");
        createBtn.addActionListener(e -> createOrder());
        g.gridx = 1; g.gridy = r;
        form.add(createBtn, g);

        // ----- bang don -----
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel actions = new JPanel();
        JButton refresh = new JButton("Lam moi");
        JButton chat = new JButton("Chat voi tai xe");
        JButton cancel = new JButton("Huy don");
        refresh.addActionListener(e -> loadOrders());
        chat.addActionListener(e -> openChat());
        cancel.addActionListener(e -> cancelOrder());
        actions.add(refresh);
        actions.add(chat);
        actions.add(cancel);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.setBorder(BorderFactory.createTitledBorder("Don cua toi"));
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(actions, BorderLayout.SOUTH);

        logArea.setEditable(false);

        add(form, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        conn.setPushListener(this::onPush);
        conn.setOnDisconnect(() -> log("!! Mat ket noi toi server"));

        setSize(820, 640);
        setLocationRelativeTo(null);
        loadOrders();
    }

    private void createOrder() {
        double p;
        try {
            p = Double.parseDouble(price.getText().trim());
        } catch (NumberFormatException e) {
            log("Gia khong hop le");
            return;
        }
        Message req = Message.request(MessageType.ORDER_CREATE)
                .put("pickupAddr", pickup.getText().trim())
                .put("dropoffAddr", dropoff.getText().trim())
                .put("note", note.getText().trim())
                .put("price", p);

        conn.request(req,
                resp -> {
                    JsonObject o = resp.getData().getAsJsonObject("order");
                    model.upsert(o);
                    log("Da tao don #" + o.get("id").getAsLong() + ", dang cho tai xe nhan...");
                },
                err -> log("Tao don that bai: " + err.str("message")));
    }

    private void cancelOrder() {
        JsonObject o = selected();
        if (o == null) return;
        conn.request(Message.request(MessageType.ORDER_UPDATE_STATUS)
                        .put("orderId", o.get("id").getAsLong())
                        .put("status", "CANCELLED"),
                resp -> {
                    model.upsert(resp.getData().getAsJsonObject("order"));
                    log("Da huy don #" + o.get("id").getAsLong());
                },
                err -> log("Khong huy duoc: " + err.str("message")));
    }

    private void openChat() {
        JsonObject o = selected();
        if (o == null) return;
        if (!o.has("driverId")) {
            log("Don chua co tai xe de chat");
            return;
        }
        ChatDialog.open(this, conn, o.get("id").getAsLong());
    }

    private JsonObject selected() {
        JsonObject o = model.at(table.getSelectedRow());
        if (o == null) log("Chon 1 don trong bang truoc da");
        return o;
    }

    private void loadOrders() {
        conn.request(Message.request(MessageType.ORDER_LIST_MINE),
                resp -> model.setAll(resp.getData().getAsJsonArray("orders")),
                err -> log("Khong tai duoc danh sach: " + err.str("message")));
    }

    /** Xu ly moi thu server tu day xuong (chay san tren EDT). */
    private void onPush(Message push) {
        switch (push.getType()) {
            case MessageType.PUSH_ORDER_STATUS: {
                JsonObject o = push.getData().getAsJsonObject("order");
                model.upsert(o);
                String extra = push.str("driverName") != null ? " (tai xe: " + push.str("driverName") + ")" : "";
                log("Don #" + o.get("id").getAsLong() + " -> " + o.get("status").getAsString() + extra);
                break;
            }
            case MessageType.PUSH_CHAT_MESSAGE: {
                if (!ChatDialog.route(push)) {
                    JsonObject m = push.getData().getAsJsonObject("message");
                    log("Tin nhan moi o don #" + m.get("orderId").getAsLong() + ": " + m.get("content").getAsString());
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

    private void addRow(JPanel form, GridBagConstraints g, int row, String label, Component field) {
        g.gridx = 0; g.gridy = row;
        form.add(new JLabel(label), g);
        g.gridx = 1;
        form.add(field, g);
    }
}
