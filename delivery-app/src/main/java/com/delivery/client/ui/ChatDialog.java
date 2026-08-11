package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog chat cho một đơn hàng.
 * Mỗi đơn một dialog riêng - lưu trong OPEN để route PUSH về đúng dialog.
 */
public class ChatDialog extends JDialog {

    private static final Map<Long, ChatDialog> OPEN = new HashMap<>();

    private final long orderId;
    private final ClientConnection conn;
    private final ChatPanel chat;

    public static void open(Window owner, ClientConnection conn, long orderId) {
        ChatDialog d = OPEN.get(orderId);
        if (d == null) {
            d = new ChatDialog(owner, conn, orderId);
            OPEN.put(orderId, d);
        }
        d.setVisible(true);
        d.toFront();
    }

    public static boolean route(JsonObject msg) {
        long orderId = msg.get("orderId").getAsLong();
        ChatDialog d = OPEN.get(orderId);
        if (d == null) return false;
        d.chat.handlePush(msg);
        return true;
    }

    public static void closeAll() {
        for (ChatDialog d : new java.util.ArrayList<>(OPEN.values())) d.dispose();
        OPEN.clear();
    }

    private ChatDialog(Window owner, ClientConnection conn, long orderId) {
        super(owner, "Chat - Đơn #" + orderId, ModalityType.MODELESS);
        this.conn = conn;
        this.orderId = orderId;
        this.chat = new ChatPanel(conn);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(chat, BorderLayout.CENTER);
        setContentPane(root);

        UiKit.onClosing(this, this::dispose);
        UiKit.bindEscape(getRootPane(), this::dispose);
        setPreferredSize(new Dimension(500, 500));
        setLocationRelativeTo(owner);

        chat.showOrder(null, null);
    }

    @Override
    public void dispose() {
        OPEN.remove(orderId);
        super.dispose();
    }
}
