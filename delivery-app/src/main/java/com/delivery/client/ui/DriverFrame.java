package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

public class DriverFrame extends JFrame {

    private final ClientConnection conn;

    private final OrderTableModel pendingModel = new OrderTableModel();
    private final OrderTableModel myModel = new OrderTableModel();
    private final JTable pendingTable = new JTable(pendingModel);
    private final JTable myTable = new JTable(myModel);
    private final JTextArea logArea = new JTextArea(6, 60);

    public DriverFrame(ClientConnection conn) {
        super("Tai xe - " + conn.fullName);
        this.conn = conn;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // ----- don dang cho -----
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Don dang cho"));
        JButton acceptBtn = new JButton("Nhan don");
        JButton reloadBtn = new JButton("Lam moi");
        acceptBtn.addActionListener(e -> acceptOrder());
        reloadBtn.addActionListener(e -> loadPending());
        JPanel leftBtns = new JPanel();
        leftBtns.add(acceptBtn);
        leftBtns.add(reloadBtn);
        left.add(new JScrollPane(pendingTable), BorderLayout.CENTER);
        left.add(leftBtns, BorderLayout.SOUTH);

        // ----- don cua toi -----
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("Don cua toi"));
        JPanel rightBtns = new JPanel();
        rightBtns.add(statusButton("Da lay hang", "PICKED_UP"));
        rightBtns.add(statusButton("Dang giao", "DELIVERING"));
        rightBtns.add(statusButton("Hoan thanh", "COMPLETED"));
        JButton chat = new JButton("Chat");
        chat.addActionListener(e -> {
            JsonObject o = myModel.at(myTable.getSelectedRow());
            if (o == null) { log("Chon 1 don da"); return; }
            ChatDialog.open(this, conn, o.get("id").getAsLong());
        });
        rightBtns.add(chat);
        right.add(new JScrollPane(myTable), BorderLayout.CENTER);
        right.add(rightBtns, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, left, right);
        split.setResizeWeight(0.5);

        logArea.setEditable(false);
        add(split, BorderLayout.CENTER);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        conn.setPushListener(this::onPush);
        conn.setOnDisconnect(() -> log("!! Mat ket noi toi server"));

        setSize(900, 700);
        setLocationRelativeTo(null);
        loadPending();
        loadMine();
    }

    private JButton statusButton(String label, String status) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            JsonObject o = myModel.at(myTable.getSelectedRow());
            if (o == null) { log("Chon 1 don da"); return; }
            conn.request(Message.request(MessageType.ORDER_UPDATE_STATUS)
                            .put("orderId", o.get("id").getAsLong())
                            .put("status", status),
                    resp -> {
                        JsonObject updated = resp.getData().getAsJsonObject("order");
                        myModel.upsert(updated);
                        log("Don #" + updated.get("id").getAsLong() + " -> " + status);
                    },
                    err -> log("Khong doi duoc trang thai: " + err.str("message")));
        });
        return b;
    }

    private void acceptOrder() {
        JsonObject o = pendingModel.at(pendingTable.getSelectedRow());
        if (o == null) { log("Chon 1 don trong danh sach cho"); return; }
        long orderId = o.get("id").getAsLong();

        conn.request(Message.request(MessageType.ORDER_ACCEPT).put("orderId", orderId),
                resp -> {
                    JsonObject order = resp.getData().getAsJsonObject("order");
                    pendingModel.removeById(orderId);
                    myModel.upsert(order);
                    log("Da nhan don #" + orderId);
                },
                err -> {
                    // Truong hop kinh dien: 2 tai xe bam cung luc, ta la nguoi thua
                    pendingModel.removeById(orderId);
                    log("Khong nhan duoc don #" + orderId + ": " + err.str("message"));
                });
    }

    private void loadPending() {
        conn.request(Message.request(MessageType.ORDER_LIST_PENDING),
                resp -> pendingModel.setAll(resp.getData().getAsJsonArray("orders")),
                err -> log("Loi tai don cho: " + err.str("message")));
    }

    private void loadMine() {
        conn.request(Message.request(MessageType.ORDER_LIST_MINE),
                resp -> myModel.setAll(resp.getData().getAsJsonArray("orders")),
                err -> log("Loi tai don cua toi: " + err.str("message")));
    }

    private void onPush(Message push) {
        switch (push.getType()) {
            case MessageType.PUSH_NEW_ORDER: {
                JsonObject o = push.getData().getAsJsonObject("order");
                pendingModel.upsert(o);
                log("CO DON MOI #" + o.get("id").getAsLong() + ": " +
                        o.get("pickupAddr").getAsString() + " -> " + o.get("dropoffAddr").getAsString());
                break;
            }
            case MessageType.PUSH_ORDER_TAKEN: {
                long id = push.lng("orderId");
                pendingModel.removeById(id);
                log("Don #" + id + " da co tai xe khac nhan");
                break;
            }
            case MessageType.PUSH_ORDER_STATUS: {
                JsonObject o = push.getData().getAsJsonObject("order");
                myModel.upsert(o);
                log("Don #" + o.get("id").getAsLong() + " -> " + o.get("status").getAsString());
                break;
            }
            case MessageType.PUSH_CHAT_MESSAGE: {
                if (!ChatDialog.route(push)) {
                    JsonObject m = push.getData().getAsJsonObject("message");
                    log("Tin nhan moi (don #" + m.get("orderId").getAsLong() + "): " + m.get("content").getAsString());
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
}
