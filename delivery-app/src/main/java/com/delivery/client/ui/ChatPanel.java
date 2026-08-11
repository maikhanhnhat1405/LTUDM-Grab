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

/**
 * Khung chat NHUNG THANG vao man hinh chinh (khong con la cua so rieng nua).
 *
 * Chat luon bam theo don dang duoc chon trong bang: chon don nao thi doc
 * dung hoi thoai cua don do. Tin nhan cua minh nam ben PHAI (bong bong xanh),
 * tin cua doi phuong nam ben TRAI (bong bong xam).
 */
public class ChatPanel extends JPanel {

    /** Be rong toi da cua mot bong bong truoc khi chu bi xuong dong. */
    private static final int BUBBLE_MAX = 250;

    /** Anh 1x1 chi de do be ngang chuoi truoc khi component duoc hien thi. */
    private static final java.awt.image.BufferedImage PROBE =
            new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    private static final FontMetrics FM = PROBE.createGraphics().getFontMetrics(Theme.FONT);

    private final ClientConnection conn;

    private final JLabel titleLabel = Theme.bold("Trò chuyện");
    private final JLabel subLabel = Theme.muted("Chọn một đơn để bắt đầu");
    private final JPanel list = new JPanel();
    private final BottomStack viewport = new BottomStack();
    private final JScrollPane scroll;
    /** Dong chu gap thay cho danh sach khi chua co gi de hien. */
    private JComponent hint;
    private final JTextField input = Theme.field(18);
    private final JButton sendBtn = Theme.primary("Gửi");

    /** Don dang mo; 0 = chua chon don nao. */
    private long orderId;
    /** Nguoi gui cua bong bong ngay trên - de khoi lap ten lien tiep. */
    private long lastSender = -1;

    public ChatPanel(ClientConnection conn) {
        super(new BorderLayout(0, 10));
        this.conn = conn;
        setOpaque(false);

        // ----- tieu de -----
        JPanel head = Theme.transparent(new BorderLayout());
        JPanel texts = Theme.transparent(new GridLayout(2, 1));
        texts.add(titleLabel);
        texts.add(subLabel);
        head.add(texts, BorderLayout.WEST);
        head.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        // ----- danh sach bong bong -----
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(new EmptyBorder(6, 4, 6, 4));

        // Bong bong don xuong day khung (giong ung dung chat that), khi day thi cuon.
        viewport.add(list, BorderLayout.SOUTH);

        scroll = new JScrollPane(viewport);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(new Color(0xF7F9FA));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ----- o nhap -----
        input.addActionListener(e -> doSend());
        sendBtn.addActionListener(e -> doSend());
        JPanel bottom = Theme.transparent(new BorderLayout(8, 0));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(sendBtn, BorderLayout.EAST);

        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        showOrder(null, null);
    }

    // ------------------------------------------------------------------
    // Dieu khien tu frame cha
    // ------------------------------------------------------------------

    /**
     * Doi hoi thoai sang don duoc chon.
     *
     * @param order don dang chon, null neu bang khong co dong nao duoc chon
     * @param partnerName ten doi phuong hien thi o phu de, co the null
     */
    public void showOrder(JsonObject order, String partnerName) {
        long id = order == null ? 0 : order.get("id").getAsLong();
        boolean hasPartner = order != null && order.has("driverId") && !order.get("driverId").isJsonNull();

        if (id != orderId) {
            orderId = id;
            clearList();
        }

        if (order == null) {
            titleLabel.setText("Trò chuyện");
            subLabel.setText("Chọn một đơn để bắt đầu");
            setInputEnabled(false);
            placeholder("Chưa chọn đơn nào.");
            return;
        }

        titleLabel.setText("Đơn #" + id);
        if (!hasPartner) {
            subLabel.setText("Đơn chưa có tài xế nhận");
            setInputEnabled(false);
            placeholder("Khi có tài xế nhận đơn, bạn có thể nhắn tin ở đây.");
            return;
        }

        subLabel.setText(partnerName == null ? Theme.statusLabel(order.get("status").getAsString())
                : partnerName + " · " + Theme.statusLabel(order.get("status").getAsString()));
        setInputEnabled(true);
        loadHistory(id);
    }

    /**
     * Nhan PUSH_CHAT_MESSAGE tu frame cha.
     *
     * @return true neu tin nhan thuoc don dang mo (da hien len khung chat),
     *         false neu thuoc don khac - frame cha tu quyet dinh bao ra sao.
     */
    public boolean handlePush(JsonObject msg) {
        if (orderId == 0 || msg.get("orderId").getAsLong() != orderId) return false;
        append(msg);
        return true;
    }

    public long currentOrderId() { return orderId; }

    // ------------------------------------------------------------------
    // Ben trong
    // ------------------------------------------------------------------

    private void setInputEnabled(boolean b) {
        input.setEnabled(b);
        sendBtn.setEnabled(b);
        input.setBackground(b ? Theme.CARD : new Color(0xF1F3F5));
    }

    private void clearList() {
        list.removeAll();
        lastSender = -1;
        if (hint != null) {
            viewport.remove(hint);
            hint = null;
        }
        viewport.revalidate();
        viewport.repaint();
    }

    /** Loi nhac nam giua khung, thay cho danh sach bong bong. */
    private void placeholder(String text) {
        clearList();
        JLabel l = Theme.muted(text);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(new EmptyBorder(10, 20, 10, 20));
        hint = l;
        viewport.add(l, BorderLayout.CENTER);
        viewport.revalidate();
        viewport.repaint();
    }

    private void loadHistory(long id) {
        conn.request(Message.request(MessageType.CHAT_HISTORY).put("orderId", id),
                resp -> {
                    if (id != orderId) return;          // user da chon don khac truoc khi ket qua ve
                    clearList();
                    JsonArray arr = resp.getData().getAsJsonArray("messages");
                    if (arr.size() == 0) {
                        placeholder("Chưa có tin nhắn nào. Hãy gửi lời chào!");
                        return;
                    }
                    for (JsonElement el : arr) append(el.getAsJsonObject());
                },
                err -> placeholder("Không tải được lịch sử: " + err.str("message")));
    }

    private void doSend() {
        if (orderId == 0 || !input.isEnabled()) return;
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setText("");

        long sentOn = orderId;
        conn.request(Message.request(MessageType.CHAT_SEND)
                        .put("orderId", sentOn)
                        .put("content", text),
                resp -> {
                    if (sentOn == orderId) append(resp.getData().getAsJsonObject("message"));
                },
                err -> {
                    if (sentOn == orderId) systemLine("Không gửi được: " + err.str("message"));
                });
    }

    /** Them mot bong bong vao cuoi danh sach roi cuon xuong day. */
    private void append(JsonObject m) {
        boolean mine = m.get("senderId").getAsLong() == conn.userId;
        long sender = m.get("senderId").getAsLong();
        boolean repeated = sender == lastSender;
        lastSender = sender;

        String name = m.has("senderName") && !m.get("senderName").isJsonNull()
                ? m.get("senderName").getAsString()
                : "Người dùng " + sender;
        String time = m.has("createdAt") && !m.get("createdAt").isJsonNull()
                ? Theme.shortTime(m.get("createdAt").getAsString())
                : "";

        list.add(new Row(new Bubble(name, m.get("content").getAsString(), time, mine, repeated), mine));
        list.revalidate();
        list.repaint();
        scrollToBottom();
    }

    private void systemLine(String text) {
        JLabel l = Theme.muted(text);
        l.setForeground(Theme.DANGER);
        Row r = new Row(l, false);
        list.add(r);
        list.revalidate();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    /**
     * Khung chua danh sach bong bong. Mac dinh JViewport chi keo cao bang
     * noi dung, nen BorderLayout.SOUTH se khong co tac dung; implement
     * Scrollable de khi tin nhan con it thi khung van cao bang viewport
     * va bong bong duoc day xuong day.
     */
    private static class BottomStack extends JPanel implements Scrollable {
        BottomStack() {
            super(new BorderLayout());
            setOpaque(false);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            Container p = getParent();
            return p instanceof JViewport && p.getHeight() > getPreferredSize().height;
        }
    }

    // ------------------------------------------------------------------
    // Mot dong tin nhan: bong bong dat sat phai (cua minh) hoac sat trai
    // ------------------------------------------------------------------
    private static class Row extends JPanel {
        Row(Component bubble, boolean mine) {
            super(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 3));
            setOpaque(false);
            add(bubble);
        }
        /** BoxLayout se keo gian theo chieu cao neu khong chan lai o day. */
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Bong bong bo goc; mau va mau chu doi theo nguoi gui. */
    private static class Bubble extends JPanel {
        private final Color bg;
        private final boolean mine;

        Bubble(String sender, String text, String time, boolean mine, boolean repeated) {
            super(new BorderLayout(0, 2));
            this.mine = mine;
            this.bg = mine ? Theme.BUBBLE_MINE : Theme.BUBBLE_OTHER;
            setOpaque(false);
            setBorder(new EmptyBorder(7, 12, 6, 12));

            // Ten nguoi gui chi hien o tin dau tien cua moi luot noi, va chi ben trai
            if (!mine && !repeated) {
                JLabel who = new JLabel(sender);
                who.setFont(Theme.SMALL_BOLD);
                who.setForeground(Theme.BRAND_DARK);
                add(who, BorderLayout.NORTH);
            }

            JLabel body = new JLabel(html(text));
            body.setFont(Theme.FONT);
            body.setForeground(mine ? Color.WHITE : Theme.TEXT);
            add(body, BorderLayout.CENTER);

            if (!time.isEmpty()) {
                JLabel clock = new JLabel(time);
                clock.setFont(Theme.SMALL);
                clock.setForeground(mine ? new Color(0xD6F0E0) : Theme.MUTED);
                clock.setHorizontalAlignment(SwingConstants.RIGHT);
                add(clock, BorderLayout.SOUTH);
            }
        }

        /**
         * Tu ngat dong theo be ngang thuc do bang FontMetrics.
         * Khong dung style 'width' cua Swing HTML vi no chi la goi y - cau dai
         * van tran ra ngoai khung chat.
         */
        private static String html(String text) {
            StringBuilder out = new StringBuilder();
            for (String para : text.split("\n", -1)) {
                if (out.length() > 0) out.append("<br>");
                StringBuilder line = new StringBuilder();
                for (String word : para.split(" ")) {
                    for (String piece : hardSplit(word)) {          // tu don qua dai thi cat cung
                        String candidate = line.length() == 0 ? piece : line + " " + piece;
                        if (FM.stringWidth(candidate) > BUBBLE_MAX && line.length() > 0) {
                            out.append(esc(line.toString())).append("<br>");
                            line = new StringBuilder(piece);
                        } else {
                            line = new StringBuilder(candidate);
                        }
                    }
                }
                out.append(esc(line.toString()));
            }
            return "<html>" + out + "</html>";
        }

        /** Cat mot "tu" khong co dau cach thanh cac manh vua khung. */
        private static java.util.List<String> hardSplit(String word) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (char ch : word.toCharArray()) {
                if (cur.length() > 0 && FM.stringWidth(cur.toString() + ch) > BUBBLE_MAX) {
                    parts.add(cur.toString());
                    cur = new StringBuilder();
                }
                cur.append(ch);
            }
            parts.add(cur.toString());
            return parts;
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            int arc = 16;
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            // vat mot goc phia nguoi gui cho giong bong bong chat
            if (mine) g2.fillRect(getWidth() - arc, getHeight() - arc, arc, arc);
            else      g2.fillRect(0, getHeight() - arc, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
