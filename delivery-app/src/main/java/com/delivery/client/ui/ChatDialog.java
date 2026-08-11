package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Cua so chat cua MOT don hang.
 * Cac dialog dang mo duoc luu trong OPEN de dinh tuyen PUSH_CHAT_MESSAGE
 * ve dung cua so.
 */
public class ChatDialog extends JDialog {

    private static final Map<Long, ChatDialog> OPEN = new HashMap<>();

    private final long orderId;
    private final ClientConnection conn;
    private final JTextArea area = new JTextArea(16, 40);
    private final JTextField input = new JTextField(30);

    public static void open(Window owner, ClientConnection conn, long orderId) {
        ChatDialog d = OPEN.get(orderId);
        if (d == null) {
            d = new ChatDialog(owner, conn, orderId);
            OPEN.put(orderId, d);
        }
        d.setVisible(true);
        d.toFront();
    }

    /** Goi tu pushListener cua CustomerFrame / DriverFrame. */
    public static boolean route(Message push) {
        JsonObject m = push.getData().getAsJsonObject("message");
        long orderId = m.get("orderId").getAsLong();
        ChatDialog d = OPEN.get(orderId);
        if (d == null) return false;
        d.appendMessage(m);
        return true;
    }

    private ChatDialog(Window owner, ClientConnection conn, long orderId) {
        super(owner, "Chat - don #" + orderId, ModalityType.MODELESS);
        this.conn = conn;
        this.orderId = orderId;

        area.setEditable(false);
        area.setLineWrap(true);

        JButton send = new JButton("Gui");
        send.addActionListener(e -> doSend());
        getRootPane().setDefaultButton(send);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);

        setLayout(new BorderLayout(6, 6));
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);

        loadHistory();
    }

    private void loadHistory() {
        conn.request(Message.request(MessageType.CHAT_HISTORY).put("orderId", orderId),
                resp -> {
                    area.setText("");
                    JsonArray arr = resp.getData().getAsJsonArray("messages");
                    for (JsonElement el : arr) appendMessage(el.getAsJsonObject());
                },
                err -> area.setText("Khong tai duoc lich su: " + err.str("message")));
    }

    private void doSend() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setText("");
        conn.request(Message.request(MessageType.CHAT_SEND)
                        .put("orderId", orderId)
                        .put("content", text),
                resp -> appendMessage(resp.getData().getAsJsonObject("message")),
                err -> area.append("[loi gui] " + err.str("message") + "\n"));
    }

    private void appendMessage(JsonObject m) {
        String who = m.has("senderName") && !m.get("senderName").isJsonNull()
                ? m.get("senderName").getAsString()
                : ("user " + m.get("senderId").getAsLong());
        if (m.get("senderId").getAsLong() == conn.userId) who = "Ban";
        area.append(who + ": " + m.get("content").getAsString() + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }

    @Override
    public void dispose() {
        OPEN.remove(orderId);
        super.dispose();
    }
}
